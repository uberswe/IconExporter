package org.cyclops.iconexporter.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * Handles automatic world creation and loading for fully hands-free automation.
 *
 * This automatically creates and loads a minimal world when Minecraft starts,
 * eliminating all manual setup steps.
 *
 * Usage:
 * 1. Set autoExportOnStartup=true and autoCreateWorld=true in config
 * 2. Launch Minecraft
 * 3. World "iconexporter_temp" is created and loaded automatically
 * 4. Export runs automatically
 * 5. Future launches with -Diconexporter.quitAfterExport=true are fully hands-free
 */
public class WorldAutoLoader {

    private static boolean hasAttemptedCreate = false;
    private static boolean hasNavigatedToSingleplayer = false;
    private static int tickCounter = 0;
    private static boolean hasLoggedWaiting = false;
    private static int unknownScreenDismissAttempts = 0;
    private static long lastDismissAttempt = 0;
    private static final int MAX_DISMISS_ATTEMPTS = 20;  // Try dismissing up to 20 times
    private static final long DISMISS_COOLDOWN_MS = 500;  // Wait 500ms between attempts

    /**
     * Check if auto-export is enabled (JVM property overrides config).
     * Use -Diconexporter.autoExportOnStartup=true to enable via JVM args.
     */
    private static boolean isAutoExportEnabled() {
        String sysProp = System.getProperty("iconexporter.autoExportOnStartup");
        if (sysProp != null) {
            return "true".equalsIgnoreCase(sysProp);
        }
        return GeneralConfig.autoExportOnStartup;
    }

    /**
     * Check if auto-create world is enabled (JVM property overrides config).
     * Use -Diconexporter.autoCreateWorld=true to enable via JVM args.
     */
    private static boolean isAutoCreateWorldEnabled() {
        String sysProp = System.getProperty("iconexporter.autoCreateWorld");
        if (sysProp != null) {
            return "true".equalsIgnoreCase(sysProp);
        }
        return GeneralConfig.autoCreateWorld;
    }

    /**
     * Called every client tick. Polls for TitleScreen and SelectWorldScreen appearance.
     * This is more reliable than screen events or immediate checks.
     */
    public static void onClientTick(Minecraft mc, IModBase mod) {
        // Only check if auto-features are enabled (check JVM properties first, then config)
        if (!isAutoExportEnabled() || !isAutoCreateWorldEnabled()) {
            return;
        }

        // Stop checking once we've attempted to load the world
        if (hasAttemptedCreate) {
            return;
        }

        tickCounter++;

        // Log every 100 ticks (5 seconds at 20 TPS) for debugging
        if (tickCounter % 100 == 0) {
            if (mod != null) {
                mod.log("WorldAutoLoader tick check - screen: " + (mc.screen != null ? mc.screen.getClass().getSimpleName() : "null"));
                mod.log("  hasNavigatedToSingleplayer: " + hasNavigatedToSingleplayer + ", hasAttemptedCreate: " + hasAttemptedCreate);
            }
        }

        // Check if we're on an unknown screen (not TitleScreen, SelectWorldScreen, or null)
        // Some mods (like FancyMenu) show welcome screens that need to be dismissed
        if (mc.screen != null
            && !(mc.screen instanceof TitleScreen)
            && !(mc.screen instanceof SelectWorldScreen)
            && !hasNavigatedToSingleplayer) {

            // If we've exhausted all gentle dismiss attempts, bypass TitleScreen entirely
            // and go directly to SelectWorldScreen (FancyMenu may intercept TitleScreen)
            if (unknownScreenDismissAttempts >= MAX_DISMISS_ATTEMPTS) {
                String screenName = mc.screen.getClass().getSimpleName();
                if (mod != null) {
                    mod.log("Max dismiss attempts reached for " + screenName + " - bypassing to SelectWorldScreen directly");
                }
                try {
                    // Reset counter and mark navigation as done
                    unknownScreenDismissAttempts = 0;
                    hasNavigatedToSingleplayer = true;

                    // Go directly to SelectWorldScreen, skipping TitleScreen
                    // (FancyMenu hooks TitleScreen, not SelectWorldScreen)
                    Screen selectWorldScreen = new SelectWorldScreen(new TitleScreen());
                    mc.setScreen(selectWorldScreen);

                    if (mod != null) {
                        mod.log("Successfully bypassed to SelectWorldScreen");
                    }
                } catch (Exception e) {
                    if (mod != null) {
                        mod.log("Failed to bypass to SelectWorldScreen: " + e.getMessage());
                    }
                    // Fallback: try forcing TitleScreen anyway
                    try {
                        mc.setScreen(new TitleScreen());
                        if (mod != null) {
                            mod.log("Fallback: forced TitleScreen");
                        }
                    } catch (Exception e2) {
                        if (mod != null) {
                            mod.log("Fallback failed: " + e2.getMessage());
                        }
                    }
                }
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastDismissAttempt >= DISMISS_COOLDOWN_MS) {
                lastDismissAttempt = now;
                unknownScreenDismissAttempts++;

                String screenName = mc.screen.getClass().getSimpleName();
                if (mod != null) {
                    mod.log("Unknown screen detected: " + screenName + " - attempting to dismiss (attempt " + unknownScreenDismissAttempts + "/" + MAX_DISMISS_ATTEMPTS + ")");
                }

                // Try pressing Escape to dismiss the screen
                try {
                    mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);

                    // If that didn't work, also try closing the screen directly
                    if (mc.screen != null && !(mc.screen instanceof TitleScreen)) {
                        mc.screen.onClose();
                    }
                } catch (Exception e) {
                    if (mod != null) {
                        mod.log("Failed to dismiss screen: " + e.getMessage());
                    }
                }
            }
            return;  // Don't process further until we get to TitleScreen
        }

        // Check if we're on TitleScreen and haven't navigated yet
        if (mc.screen instanceof TitleScreen && !hasNavigatedToSingleplayer) {
            if (mod != null) {
                mod.log("TitleScreen detected on tick " + tickCounter + " - triggering auto-navigation");
            }

            // Trigger auto-navigation via the existing onScreenOpen method
            onScreenOpen(mc, mod);
        }

        // Check if we're on SelectWorldScreen and haven't attempted to load yet
        if (mc.screen instanceof SelectWorldScreen && !hasAttemptedCreate) {
            if (mod != null) {
                mod.log("SelectWorldScreen detected on tick " + tickCounter + " - triggering world load");
            }

            // Trigger world loading via the existing onScreenOpen method
            onScreenOpen(mc, mod);
        }
    }

    /**
     * Called when a screen is opened. Handles automatic navigation and world loading.
     */
    public static void onScreenOpen(Minecraft mc, IModBase mod) {
        boolean autoExport = isAutoExportEnabled();
        boolean autoCreate = isAutoCreateWorldEnabled();

        if (mod != null) {
            mod.log("WorldAutoLoader.onScreenOpen called - screen: " + (mc.screen != null ? mc.screen.getClass().getSimpleName() : "null"));
            mod.log("  autoExportOnStartup: " + autoExport + " (config: " + GeneralConfig.autoExportOnStartup + ", sysprop: " + System.getProperty("iconexporter.autoExportOnStartup") + ")");
            mod.log("  autoCreateWorld: " + autoCreate + " (config: " + GeneralConfig.autoCreateWorld + ", sysprop: " + System.getProperty("iconexporter.autoCreateWorld") + ")");
            mod.log("  hasNavigatedToSingleplayer: " + hasNavigatedToSingleplayer);
            mod.log("  hasAttemptedCreate: " + hasAttemptedCreate);
        }

        // Check if both auto-export and auto-create are enabled (JVM properties override config)
        if (!autoExport || !autoCreate) {
            if (mod != null) {
                mod.log("  -> Config check failed, returning");
            }
            return;
        }

        // Step 1: On TitleScreen, navigate to Singleplayer (SelectWorldScreen) - only once
        if (mc.screen instanceof TitleScreen && !hasNavigatedToSingleplayer) {
            hasNavigatedToSingleplayer = true;

            if (mod != null) {
                mod.log("Auto-create world enabled - navigating to world selection...");
            }

            // Navigate to Singleplayer screen immediately (not deferred)
            try {
                Screen selectWorldScreen = new SelectWorldScreen(mc.screen);
                mc.setScreen(selectWorldScreen);

                if (mod != null) {
                    mod.log("Navigated to world selection screen");
                }
            } catch (Exception e) {
                if (mod != null) {
                    mod.log("Failed to navigate to world selection: " + e.getMessage());
                }
                e.printStackTrace();
            }
            return; // Don't process further this tick
        }

        // Step 2: On SelectWorldScreen, create and load the world - only once
        // This triggers regardless of whether we auto-navigated or user clicked Singleplayer
        if (mc.screen instanceof SelectWorldScreen && !hasAttemptedCreate) {
            if (mod != null) {
                mod.log("  -> Screen is SelectWorldScreen and hasAttemptedCreate is false");
            }
            hasAttemptedCreate = true;

            if (mod != null) {
                mod.log("On world selection screen - checking for temp world...");
            }

            // Create and load world
            try {
                createAndLoadWorld(mc, mod);
            } catch (Exception e) {
                if (mod != null) {
                    mod.log("Failed to auto-create/load world: " + e.getMessage());
                }
                e.printStackTrace();
            }
        } else {
            if (mod != null) {
                mod.log("  -> Not triggering world load (screen instanceof SelectWorldScreen: " + (mc.screen instanceof SelectWorldScreen) + ", hasAttemptedCreate: " + hasAttemptedCreate + ")");
            }
        }
    }

    /**
     * Create world if it doesn't exist, then load it automatically.
     */
    private static void createAndLoadWorld(Minecraft mc, IModBase mod) {
        try {
            File savesDir = new File(mc.gameDirectory, "saves");
            //noinspection ResultOfMethodCallIgnored
            savesDir.mkdirs();

            boolean worldExisted = MinimalWorldCreator.worldExists(savesDir);

            // Create world if it doesn't exist
            if (!worldExisted) {
                if (mod != null) {
                    mod.log("Temp world not found, creating minimal world...");
                }

                MinimalWorldCreator.createMinimalWorld(savesDir, mod);

                if (mod != null) {
                    mod.log("=".repeat(60));
                    mod.log("WORLD CREATED: iconexporter_temp");
                    mod.log("Now loading world automatically...");
                    mod.log("=".repeat(60));
                }
            } else {
                if (mod != null) {
                    mod.log("Temp world 'iconexporter_temp' exists - loading automatically...");
                }
            }

            // Load the world automatically
            loadWorld(mc, mod);

        } catch (Exception e) {
            if (mod != null) {
                mod.log("Failed to create/load world: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    /**
     * Load the iconexporter_temp world using the openWorld API.
     * Defers execution by several ticks to ensure the SelectWorldScreen is fully initialized.
     */
    private static void loadWorld(Minecraft mc, IModBase mod) {
        String worldName = MinimalWorldCreator.getWorldName();

        if (mod != null) {
            mod.log("loadWorld() called - deferring to allow SelectWorldScreen to initialize...");
            mod.log("  World name: " + worldName);
        }

        // Defer the actual world loading by 20 ticks (1 second) to ensure:
        // 1. SelectWorldScreen is fully initialized
        // 2. World list is loaded
        // 3. We're on the correct thread
        new Thread(() -> {
            try {
                // Wait 1 second (20 ticks) for screen to initialize
                Thread.sleep(1000);

                // Execute on main thread
                mc.execute(() -> {
                    try {
                        if (mod != null) {
                            mod.log("Deferred world load starting...");
                            mod.log("  Creating WorldOpenFlows...");
                        }

                        WorldOpenFlows worldOpenFlows = mc.createWorldOpenFlows();

                        if (mod != null) {
                            mod.log("  WorldOpenFlows created: " + worldOpenFlows);
                            mod.log("  Calling openWorld()...");
                        }

                        // Use the simple openWorld(String, Runnable) method available in 1.21.1
                        worldOpenFlows.openWorld(worldName, () -> {
                            if (mod != null) {
                                mod.log("World '" + worldName + "' loaded successfully!");
                            }
                        });

                        if (mod != null) {
                            mod.log("  openWorld() call initiated (loading asynchronously)");
                        }

                    } catch (Exception e) {
                        if (mod != null) {
                            mod.log("Failed to load world: " + e.getClass().getName() + ": " + e.getMessage());
                            mod.log("Please load the world 'iconexporter_temp' manually");
                        }
                        e.printStackTrace();
                    }
                });

            } catch (InterruptedException e) {
                if (mod != null) {
                    mod.log("World load defer interrupted: " + e.getMessage());
                }
            }
        }, "IconExporter-WorldLoader").start();
    }

    /**
     * Reset the flags (for testing).
     */
    public static void reset() {
        hasAttemptedCreate = false;
        hasNavigatedToSingleplayer = false;
        tickCounter = 0;
        hasLoggedWaiting = false;
        unknownScreenDismissAttempts = 0;
        lastDismissAttempt = 0;
    }
}
