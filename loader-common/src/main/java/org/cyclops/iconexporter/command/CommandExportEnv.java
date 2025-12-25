package org.cyclops.iconexporter.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.export.DatapacksExportUtil;
import org.cyclops.iconexporter.export.EnvironmentExportUtil;
import org.cyclops.iconexporter.export.ErrorLogUtil;
import org.cyclops.iconexporter.export.ModsExportUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.IOException;

/**
 * Command to export environment metadata (meta/modpack.json, meta/mods.json, meta/datapacks.json).
 */
public class CommandExportEnv implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandExportEnv(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        this.context = context;
        this.mod = mod;
        this.helpers = helpers;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Clear previous errors
        ErrorLogUtil.clear();

        // Gather environment
        String modpackName = GeneralConfig.modpackName;
        String mcVersion;
        try {
            mcVersion = SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            mcVersion = "unknown";
        }
        String loader = detectLoader();

        int scale = GeneralConfig.defaultScale;
        File gameDir = Minecraft.getInstance().gameDirectory;

        int successCount = 0;
        int totalCount = 3; // modpack.json, mods.json, datapacks.json

        // Export modpack.json
        try {
            File modpackFile = EnvironmentExportUtil.resolveModpackMetaFile(gameDir, scale, modpackName, mcVersion, loader);
            EnvironmentExportUtil.writeModpackJson(modpackFile, gameDir, modpackName, mcVersion, loader, this.mod, this.helpers);
            successCount++;
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export modpack.json: " + e.getMessage());
        }

        // Export mods.json
        try {
            File metaDir = EnvironmentExportUtil.resolveModpackMetaFile(gameDir, scale, modpackName, mcVersion, loader).getParentFile();
            File modsFile = new File(metaDir, "mods.json");
            ModsExportUtil.writeModsJson(modsFile, helpers, this.mod);
            successCount++;
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export mods.json: " + e.getMessage());
        }

        // Export datapacks.json
        try {
            File metaDir = EnvironmentExportUtil.resolveModpackMetaFile(gameDir, scale, modpackName, mcVersion, loader).getParentFile();
            File datapacksFile = new File(metaDir, "datapacks.json");
            DatapacksExportUtil.writeDatapacksJson(datapacksFile, helpers, this.mod);
            successCount++;
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export datapacks.json: " + e.getMessage());
        }

        // Write error log if any errors occurred
        try {
            if (GeneralConfig.useStructuredOutput) {
                File structuredRoot = EnvironmentExportUtil.resolveStructuredRoot(gameDir, modpackName, mcVersion, loader);
                ErrorLogUtil.write(structuredRoot);
            }
        } catch (IOException e) {
            // Ignore errors writing error log
        }

        // Send feedback to player
        if (successCount == totalCount) {
            File metaDir = EnvironmentExportUtil.resolveModpackMetaFile(gameDir, scale, modpackName, mcVersion, loader).getParentFile();
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Environment metadata exported to: " + metaDir.getAbsolutePath()));
        } else {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Environment export partially complete (" + successCount + "/" + totalCount + " files). Check error log."));
        }

        return 0;
    }

    private static String detectLoader() {
        // Order: Fabric, NeoForge, Forge
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
        return Commands.literal("exportenv")
                .executes(new CommandExportEnv(context, mod, helpers));
    }
}
