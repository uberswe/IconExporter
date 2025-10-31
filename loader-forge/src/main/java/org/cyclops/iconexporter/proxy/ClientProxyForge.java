package org.cyclops.iconexporter.proxy;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.cyclops.cyclopscore.init.ModBaseForge;
import org.cyclops.cyclopscore.proxy.ClientProxyComponentForge;
import org.cyclops.iconexporter.IconExporterForge;
import org.cyclops.iconexporter.client.AutoExportHandler;
import org.cyclops.iconexporter.client.WorldAutoLoader;
import org.cyclops.iconexporter.helpers.IconExporterHelpersForge;

/**
 * Proxy for the client side.
 *
 * @author rubensworks
 *
 */
public class ClientProxyForge extends ClientProxyComponentForge {

    public ClientProxyForge() {
        super(new CommonProxyForge());

        // Register auto-export handler
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                AutoExportHandler.onPlayerJoinWorld(mc, mc.level, mc.player, IconExporterForge._instance, new IconExporterHelpersForge());
            }
        });

        // Register world auto-loader (for title screen detection)
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
            WorldAutoLoader.onScreenOpen(Minecraft.getInstance(), IconExporterForge._instance);
        });

        // Register tick-based polling for TitleScreen detection
        // This is more reliable than screen events or immediate checks
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                WorldAutoLoader.onClientTick(Minecraft.getInstance(), IconExporterForge._instance);
            }
        });
    }

    @Override
    public ModBaseForge<?> getMod() {
        return IconExporterForge._instance;
    }

}
