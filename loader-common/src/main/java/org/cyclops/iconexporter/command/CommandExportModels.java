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
import org.cyclops.iconexporter.export.ModelExportUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;

/**
 * A command to export 3D model JSON files from resource packs.
 * @author rubensworks
 */
public class CommandExportModels implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandExportModels(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
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
            context.getSource().sendSystemMessage(Component.literal("[IconExporter] Exporting 3D models..."));
        }

        // Run export asynchronously to avoid blocking
        new Thread(() -> {
            try {
                int itemModelCount = 0;
                int blockModelCount = 0;

                // Export item models
                try {
                    File itemModelsDir = EnvironmentExportUtil.resolveModelsDir(gameDir, scale, modpackName, mcVersion, loader, "items");
                    itemModelCount = ModelExportUtil.exportItemModels(itemModelsDir, mod);
                    mod.log("Exported " + itemModelCount + " item models");
                } catch (Exception e) {
                    ErrorLogUtil.log("Failed to export item models: " + e.getMessage());
                    mod.log("Failed to export item models: " + e.getMessage());
                }

                // Export block models
                try {
                    File blockModelsDir = EnvironmentExportUtil.resolveModelsDir(gameDir, scale, modpackName, mcVersion, loader, "blocks");
                    blockModelCount = ModelExportUtil.exportBlockModels(blockModelsDir, mod);
                    mod.log("Exported " + blockModelCount + " block models");
                } catch (Exception e) {
                    ErrorLogUtil.log("Failed to export block models: " + e.getMessage());
                    mod.log("Failed to export block models: " + e.getMessage());
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
                final int finalItemCount = itemModelCount;
                final int finalBlockCount = blockModelCount;
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(
                                Component.literal("[IconExporter] Exported " + finalItemCount + " item models and " + finalBlockCount + " block models")
                        );
                    }
                });
            } catch (Exception e) {
                mod.log("Model export failed: " + e.getMessage());
                e.printStackTrace();
            }
        }, "IconExporter-ModelExport").start();

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
        return Commands.literal("exportmodels")
                .executes(new CommandExportModels(context, mod, helpers));
    }
}
