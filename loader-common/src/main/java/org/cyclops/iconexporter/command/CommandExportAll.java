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
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.client.gui.ScreenIconExporter;
import org.cyclops.iconexporter.export.ErrorLogUtil;
import org.cyclops.iconexporter.export.ExportCompletionUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;

/**
 * Command to export everything: environment, recipes, data, textures, models, translations, and icons.
 */
public class CommandExportAll implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandExportAll(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        this.context = context;
        this.mod = mod;
        this.helpers = helpers;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            mc.player.sendSystemMessage(Component.literal("Cannot export: No world loaded"));

            // Write error marker for Docker
            try {
                File baseDir = new File(mc.gameDirectory, GeneralConfig.exportBaseDir);
                ExportCompletionUtil.writeErrorMarker(baseDir, "No world loaded", this.mod);
            } catch (Exception ignored) { }

            return 0;
        }

        // Clear errors before starting
        ErrorLogUtil.clear();

        try {
            mc.player.sendSystemMessage(Component.literal("Starting full export pipeline..."));

        // 1. Export environment metadata (modpack.json, mods.json, datapacks.json)
        try {
            mc.player.sendSystemMessage(Component.literal("[1/8] Exporting environment metadata..."));
            new CommandExportEnv(this.context, this.mod, this.helpers).run(context);
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export environment: " + e.getMessage());
            mc.player.sendSystemMessage(Component.literal("  ⚠ Environment export failed"));
        }

        // 2. Export recipes
        try {
            mc.player.sendSystemMessage(Component.literal("[2/8] Exporting recipes..."));
            new CommandExportRecipes(this.context, this.mod, this.helpers).run(context);
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export recipes: " + e.getMessage());
            mc.player.sendSystemMessage(Component.literal("  ⚠ Recipe export failed"));
        }

        // 3. Export item/block data
        try {
            mc.player.sendSystemMessage(Component.literal("[3/8] Exporting item and block data..."));
            new CommandExportData(this.context, this.mod, this.helpers).run(context);
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export data: " + e.getMessage());
            mc.player.sendSystemMessage(Component.literal("  ⚠ Data export failed"));
        }

        // 4. Export textures
        try {
            mc.player.sendSystemMessage(Component.literal("[4/8] Exporting textures..."));
            new CommandExportTextures(this.context, this.mod, this.helpers).run(context);
        } catch (Exception e) {
            String errorMsg = "Failed to export textures: " + e.getClass().getName() + ": " + e.getMessage();
            ErrorLogUtil.log(errorMsg);
            mod.log(errorMsg);
            e.printStackTrace();
            mc.player.sendSystemMessage(Component.literal("  ⚠ Texture export failed: " + e.getMessage()));
        }

        // 5. Export models
        try {
            mc.player.sendSystemMessage(Component.literal("[5/8] Exporting models..."));
            new CommandExportModels(this.context, this.mod, this.helpers).run(context);
        } catch (Exception e) {
            String errorMsg = "Failed to export models: " + e.getClass().getName() + ": " + e.getMessage();
            ErrorLogUtil.log(errorMsg);
            mod.log(errorMsg);
            e.printStackTrace();
            mc.player.sendSystemMessage(Component.literal("  ⚠ Model export failed: " + e.getMessage()));
        }

        // 6. Export translations
        try {
            mc.player.sendSystemMessage(Component.literal("[6/8] Exporting translations..."));
            new CommandExportTranslations(this.context, this.mod, this.helpers).run(context);
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export translations: " + e.getMessage());
            mc.player.sendSystemMessage(Component.literal("  ⚠ Translation export failed"));
        }

        // 7. Wait for async exports to complete (textures and models run async)
        mc.player.sendSystemMessage(Component.literal("[7/8] Waiting for async exports to complete..."));
        try {
            // Give async exports time to complete
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 8. Export icons - opens GUI for rendering
        mc.player.sendSystemMessage(Component.literal("[8/8] Starting icon export (opening GUI)..."));
        ScreenIconExporter exporter = new ScreenIconExporter(
            this.context,
            GeneralConfig.defaultScale,
            Minecraft.getInstance().getWindow().getGuiScale(),
            this.mod,
            this.helpers
        );
        // Use execute() instead of submitAsync() to ensure the screen opens on the main thread
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(exporter));

            mc.player.sendSystemMessage(Component.literal("Full export pipeline started! Icon export will continue in GUI."));

        } catch (Exception e) {
            // Catastrophic failure - write error marker
            String errorMsg = "Export pipeline failed: " + e.getMessage();
            ErrorLogUtil.log(errorMsg);
            this.mod.log(errorMsg);
            e.printStackTrace();

            try {
                File baseDir = new File(mc.gameDirectory, GeneralConfig.exportBaseDir);
                ExportCompletionUtil.writeErrorMarker(baseDir, errorMsg, this.mod);
            } catch (Exception ignored) { }

            mc.player.sendSystemMessage(Component.literal("Export failed: " + e.getMessage()));
        }

        return 0;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> make(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        return Commands.literal("exportall")
                .executes(new CommandExportAll(context, mod, helpers));
    }
}
