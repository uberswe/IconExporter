package org.cyclops.iconexporter.proxy;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import org.cyclops.cyclopscore.init.ModBaseFabric;
import org.cyclops.cyclopscore.proxy.ClientProxyComponentFabric;
import org.cyclops.iconexporter.IconExporterFabric;
import org.cyclops.iconexporter.client.AutoExportHandler;
import org.cyclops.iconexporter.client.WorldAutoLoader;
import org.cyclops.iconexporter.helpers.IconExporterHelpersFabric;

/**
 * Proxy for the client side.
 *
 * @author rubensworks
 *
 */
public class ClientProxyFabric extends ClientProxyComponentFabric {

    public ClientProxyFabric() {
        super(new CommonProxyFabric());

        // Register auto-export handler
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                AutoExportHandler.onPlayerJoinWorld(mc, mc.level, mc.player, IconExporterFabric._instance, new IconExporterHelpersFabric());
            }
        });

        // Register world auto-loader (for title screen detection)
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            WorldAutoLoader.onScreenOpen(client, IconExporterFabric._instance);
        });

        // Register tick-based polling for TitleScreen detection
        // This is more reliable than screen events or immediate checks
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WorldAutoLoader.onClientTick(client, IconExporterFabric._instance);
        });
    }

    @Override
    public ModBaseFabric<?> getMod() {
        return IconExporterFabric._instance;
    }
}
