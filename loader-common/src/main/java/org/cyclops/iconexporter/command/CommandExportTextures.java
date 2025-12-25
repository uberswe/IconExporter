package org.cyclops.iconexporter.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.export.EnvironmentExportUtil;
import org.cyclops.iconexporter.export.ErrorLogUtil;
import org.cyclops.iconexporter.export.TextureExportUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;

/**
 * A command to export raw texture files from resource packs.
 * @author rubensworks
 */
public class CommandExportTextures implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandExportTextures(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        this.context = context;
        this.mod = mod;
        this.helpers = helpers;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Clear previous errors
        ErrorLogUtil.clear();

        // Get environment info
        final String modpackName = GeneralConfig.modpackName;
        String mcVersionTemp;
        try {
            mcVersionTemp = SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            mcVersionTemp = "unknown";
        }
        final String mcVersion = mcVersionTemp;
        final String loader = detectLoader();
        final int scale = GeneralConfig.defaultScale;
        final File gameDir = Minecraft.getInstance().gameDirectory;

        // Notify start (only if source is available)
        if (context.getSource() != null) {
            context.getSource().sendSystemMessage(Component.literal("[IconExporter] Exporting raw textures..."));
        }

        // Run export asynchronously to avoid blocking
        new Thread(() -> {
            try {
                int itemTextureCount = 0;
                int blockTextureCount = 0;

                // Export item textures
                try {
                    File itemTexturesDir = EnvironmentExportUtil.resolveTexturesDir(gameDir, scale, modpackName, mcVersion, loader, "items");
                    itemTextureCount = TextureExportUtil.exportItemTextures(itemTexturesDir, mod);
                    mod.log("Exported " + itemTextureCount + " item textures");
                } catch (Exception e) {
                    ErrorLogUtil.log("Failed to export item textures: " + e.getMessage());
                    mod.log("Failed to export item textures: " + e.getMessage());
                }

                // Export block textures
                try {
                    File blockTexturesDir = EnvironmentExportUtil.resolveTexturesDir(gameDir, scale, modpackName, mcVersion, loader, "blocks");
                    blockTextureCount = TextureExportUtil.exportBlockTextures(blockTexturesDir, mod);
                    mod.log("Exported " + blockTextureCount + " block textures");
                } catch (Exception e) {
                    ErrorLogUtil.log("Failed to export block textures: " + e.getMessage());
                    mod.log("Failed to export block textures: " + e.getMessage());
                }

                // Write error log if any errors occurred
                try {
                    if (GeneralConfig.useStructuredOutput) {
                        File structuredRoot = EnvironmentExportUtil.resolveStructuredRoot(gameDir, modpackName, mcVersion, loader);
                        ErrorLogUtil.write(structuredRoot);
                    }
                } catch (Exception e) {
                    // Ignore errors writing error log
                }

                // Notify completion
                final int finalItemCount = itemTextureCount;
                final int finalBlockCount = blockTextureCount;
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(
                                Component.literal("[IconExporter] Exported " + finalItemCount + " item textures and " + finalBlockCount + " block textures")
                        );
                    }
                });
            } catch (Exception e) {
                mod.log("Texture export failed: " + e.getMessage());
                e.printStackTrace();
            }
        }, "IconExporter-TextureExport").start();

        return 0;
    }

    private static String detectLoader() {
        if (classExists("net.fabricmc.loader.api.FabricLoader")) return "fabric";
        if (classExists("net.neoforged.fml.loading.FMLLoader")) return "neoforge";
        if (classExists("net.minecraftforge.fml.loading.FMLLoader")) return "forge";
        return "unknown";
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static LiteralArgumentBuilder<CommandSourceStack> make(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        return Commands.literal("exporttextures")
                .executes(new CommandExportTextures(context, mod, helpers));
    }
}
