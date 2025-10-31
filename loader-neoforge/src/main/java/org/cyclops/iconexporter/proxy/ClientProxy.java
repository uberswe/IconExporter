package org.cyclops.iconexporter.proxy;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.cyclopscore.proxy.ClientProxyComponent;
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

    public ClientProxy() {
        super(new CommonProxy());

        // Register auto-export handler
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                AutoExportHandler.onPlayerJoinWorld(mc, mc.level, mc.player, IconExporter._instance, new IconExporterHelpersNeoForge());
            }
        });

        // Register world auto-loader (for title screen detection)
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
            WorldAutoLoader.onScreenOpen(Minecraft.getInstance(), IconExporter._instance);
        });

        // Register frame-based polling for TitleScreen detection
        // This is more reliable than screen events or immediate checks
        // Note: Using RenderFrameEvent.Post as an alternative to client tick for NeoForge
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> {
            WorldAutoLoader.onClientTick(Minecraft.getInstance(), IconExporter._instance);
        });
    }

    @Override
    public ModBase getMod() {
        return IconExporter._instance;
    }

}
