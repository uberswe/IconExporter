package org.cyclops.iconexporter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.command.*;
import org.cyclops.iconexporter.export.*;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;


/**
 * Handles automatic export when joining a world if enabled in config.
 */
public class AutoExportHandler {

    private static boolean hasExported = false;

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
     * Called when the player joins a world. Triggers auto-export if enabled.
     */
    public static void onPlayerJoinWorld(Minecraft mc, ClientLevel level, LocalPlayer player, IModBase mod, IIconExporterHelpers helpers) {
        boolean autoExportEnabled = isAutoExportEnabled();

        if (mod != null) {
            mod.log("=".repeat(60));
            mod.log("AutoExportHandler.onPlayerJoinWorld called");
            mod.log("  hasExported: " + hasExported);
            mod.log("  autoExportOnStartup: " + autoExportEnabled + " (config: " + GeneralConfig.autoExportOnStartup + ", sysprop: " + System.getProperty("iconexporter.autoExportOnStartup") + ")");
            mod.log("  level: " + (level != null ? "present" : "null"));
            mod.log("  player: " + (player != null ? "present" : "null"));
            mod.log("=".repeat(60));
        }

        // Only run once per session
        if (hasExported) {
            if (mod != null) {
                mod.log("Auto-export: Skipping - already exported this session");
            }
            return;
        }

        // Check if auto-export is enabled (JVM property overrides config)
        if (!autoExportEnabled) {
            if (mod != null) {
                mod.log("Auto-export: Skipping - autoExportOnStartup is disabled");
            }
            return;
        }

        // Check if we have a valid world and player
        if (level == null || player == null) {
            if (mod != null) {
                mod.log("Auto-export: Skipping - world or player is null");
            }
            return;
        }

        hasExported = true;

        if (mod != null) {
            mod.log("Auto-export: Triggering automatic export...");
        }

        // Notify user about the delay
        int delaySeconds = GeneralConfig.autoExportDelay;
        if (player != null && delaySeconds > 0) {
            player.sendSystemMessage(Component.literal("[IconExporter] Auto-export will start in " + delaySeconds + " seconds..."));
        }

        // Run export asynchronously to avoid blocking world join
        new Thread(() -> {
            try {
                // Wait for configured delay to allow world and registries to fully load
                int delayMs = delaySeconds * 1000;
                if (mod != null) {
                    mod.log("Auto-export: Waiting " + delaySeconds + " seconds before starting export...");
                }
                Thread.sleep(delayMs);

                runAutoExport(mc, mod, helpers);
            } catch (Exception e) {
                if (mod != null) {
                    try {
                        mod.log("Auto-export failed: " + e.getMessage());
                    } catch (Throwable ignored) {
                        // Ignore logging failures
                    }
                }
                e.printStackTrace();
            }
        }, "IconExporter-AutoExport").start();
    }

    /**
     * Execute the full export pipeline using CommandExportAll.
     */
    private static void runAutoExport(Minecraft mc, IModBase mod, IIconExporterHelpers helpers) {
        try {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("[IconExporter] Starting auto-export..."));
                }

                // Use CommandExportAll to do all the exports with proper fallback logic
                try {
                    // Create a proper CommandBuildContext with registry access
                    // This is needed for component serialization in icon export
                    net.minecraft.commands.CommandBuildContext buildContext =
                        net.minecraft.commands.CommandBuildContext.simple(
                            mc.level.registryAccess(),
                            mc.level.enabledFeatures()
                        );

                    CommandExportAll exportAll = new CommandExportAll(buildContext, mod, helpers);

                    // Create minimal command context - most fields can be null since we're just executing, not parsing
                    var commandContext = new com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>(
                        null, // source
                        "", // input
                        java.util.Map.of(), // arguments
                        null, // command
                        null, // root node
                        java.util.List.of(), // nodes
                        null, // range
                        null, // child
                        null, // modifier
                        false // forked
                    );

                    exportAll.run(commandContext);

                } catch (Exception e) {
                    if (mod != null) {
                        mod.log("Auto-export failed: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            if (mod != null) {
                try {
                    mod.log("Auto-export: Fatal error - " + e.getMessage());
                } catch (Throwable ignored) {
                    // Ignore logging failures
                }
            }
            e.printStackTrace();
        }
    }

    /**
     * Reset the export flag (for testing purposes).
     */
    public static void reset() {
        hasExported = false;
    }
}
