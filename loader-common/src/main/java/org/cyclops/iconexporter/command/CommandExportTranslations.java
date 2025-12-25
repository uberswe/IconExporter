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
import org.cyclops.iconexporter.export.EnvironmentExportUtil;
import org.cyclops.iconexporter.export.ErrorLogUtil;
import org.cyclops.iconexporter.export.TranslationExportUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.IOException;

/**
 * Command to export translation/language data.
 */
public class CommandExportTranslations implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandExportTranslations(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        this.context = context;
        this.mod = mod;
        this.helpers = helpers;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Clear previous errors
        ErrorLogUtil.clear();

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
        File translationsDir = EnvironmentExportUtil.resolveTranslationsDir(gameDir, scale, modpackName, mcVersion, loader);

        // Export current language
        boolean success = TranslationExportUtil.exportCurrentLanguage(translationsDir, this.mod);

        // Also export translation keys for items/blocks
        int keyCount = TranslationExportUtil.exportTranslationKeys(translationsDir, this.mod);

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
        if (success) {
            String languageCode = Minecraft.getInstance().getLanguageManager().getSelected();
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Exported translations (" + languageCode + ", " + keyCount + " item/block keys) to: " + translationsDir.getAbsolutePath()));
        } else {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Translation export failed. Check error log."));
        }

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
        return Commands.literal("exporttranslations")
                .executes(new CommandExportTranslations(context, mod, helpers));
    }
}
