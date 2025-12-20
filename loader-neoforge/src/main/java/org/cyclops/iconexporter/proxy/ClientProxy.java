package org.cyclops.iconexporter.proxy;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.cyclopscore.proxy.ClientProxyComponent;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.IconExporter;
import org.cyclops.iconexporter.client.AutoExportHandler;
import org.cyclops.iconexporter.client.WorldAutoLoader;
import org.cyclops.iconexporter.helpers.IconExporterHelpersNeoForge;

/**
 * Proxy for the client side.
 *
 * @author rubensworks
 *
 */
public class ClientProxy extends ClientProxyComponent {

    private static boolean lazyInitStarted = false;
    private static boolean eventHandlersRegistered = false;

    public ClientProxy() {
        super(new CommonProxy());
        // NO event registration during construction!
        // All event handlers are registered lazily after a world is loaded
        // to avoid affecting event bus ordering during mod initialization.
    }

    /**
     * Start the lazy initialization process.
     * This registers a minimal tick listener that waits for a world to load,
     * then registers the actual event handlers.
     * Called from commands or other deferred contexts.
     */
    public static void startLazyInitialization() {
        if (lazyInitStarted) {
            return;
        }
        lazyInitStarted = true;

        // Check if any auto-export features are enabled
        boolean autoExportEnabled = isAutoExportEnabled();
        boolean autoCreateWorldEnabled = isAutoCreateWorldEnabled();

        if (!autoExportEnabled && !autoCreateWorldEnabled) {
            // No auto-export features enabled, skip event registration entirely
            return;
        }

        // Register a single tick listener that waits for the game to be fully ready
        // This listener removes itself after registering the actual handlers
        NeoForge.EVENT_BUS.addListener(ClientProxy::onClientTickCheckReady);
    }

    /**
     * Tick handler that waits for the game to be ready, then registers event handlers.
     * Removes itself after successful registration.
     */
    private static void onClientTickCheckReady(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Wait until the game is past the loading screen
        if (mc.getOverlay() != null) {
            return; // Still loading
        }

        // Game is ready, register the actual handlers
        registerEventHandlersIfNeeded();
    }

    /**
     * Register event handlers if auto-export features are enabled.
     * Called after the game is fully loaded.
     */
    public static void registerEventHandlersIfNeeded() {
        if (eventHandlersRegistered) {
            return;
        }
        eventHandlersRegistered = true;

        // Check if any auto-export features are enabled
        boolean autoExportEnabled = isAutoExportEnabled();
        boolean autoCreateWorldEnabled = isAutoCreateWorldEnabled();

        if (!autoExportEnabled && !autoCreateWorldEnabled) {
            // No auto-export features enabled, skip event registration
            return;
        }

        IconExporter.clog("Registering IconExporter event handlers (auto-export features enabled)");

        // Register auto-export handler (only if auto-export is enabled)
        if (autoExportEnabled) {
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn logInEvent) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && mc.player != null) {
                    AutoExportHandler.onPlayerJoinWorld(mc, mc.level, mc.player, IconExporter._instance, new IconExporterHelpersNeoForge());
                }
            });
        }

        // Register world auto-loader handlers (only if auto-create world is enabled)
        if (autoCreateWorldEnabled) {
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening screenEvent) -> {
                WorldAutoLoader.onScreenOpen(Minecraft.getInstance(), IconExporter._instance);
            });

            // Register client tick for TitleScreen detection (less frequent than RenderFrameEvent)
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tickEvent) -> {
                WorldAutoLoader.onClientTick(Minecraft.getInstance(), IconExporter._instance);
            });
        }
    }

    /**
     * Check if auto-export is enabled (JVM property overrides config).
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
     */
    private static boolean isAutoCreateWorldEnabled() {
        String sysProp = System.getProperty("iconexporter.autoCreateWorld");
        if (sysProp != null) {
            return "true".equalsIgnoreCase(sysProp);
        }
        return GeneralConfig.autoCreateWorld;
    }

    @Override
    public ModBase getMod() {
        return IconExporter._instance;
    }

}
