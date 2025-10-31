package org.cyclops.iconexporter.client.gui;

import com.google.common.collect.Queues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.apache.commons.codec.digest.DigestUtils;
import org.cyclops.cyclopscore.datastructure.Wrapper;
import org.cyclops.cyclopscore.helper.IModHelpers;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;

/**
 * A temporary gui for exporting icons.
 *
 * For each tick it is opened, it will render one icon, take a screenshot, and write it to a file.
 *
 * @author rubensworks
 */
public class ScreenIconExporter extends Screen {

    // (a << 24) | (r << 16) | (g << 8) | b
    private static final int BACKGROUND_COLOR = IModHelpers.get().getBaseHelpers().RGBAToInt(254, 255, 255, 255); // -65537
    // (a << 24) | (b << 16) | (g << 8) | r
    private static final int BACKGROUND_COLOR_SHIFTED = (255 << 24) | (255 << 16) | (255 << 8) | 254; // For some reason, MC shifts around colors internally... (R seems to be moved from the 16th bit to the 0th bit)

    private final HolderLookup.Provider lookupProvider;
    private final int scaleImage;
    private final double scaleGui;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;
    private final Queue<IExportTask> exportTasks;

    public ScreenIconExporter(HolderLookup.Provider lookupProvider, int scaleImage, double scaleGui, IModBase mod, IIconExporterHelpers helpers) {
        super(Component.translatable("gui.itemexporter.name"));
        this.lookupProvider = lookupProvider;
        this.scaleImage = scaleImage;
        this.scaleGui = scaleGui;
        this.mod = mod;
        this.helpers = helpers;
        this.exportTasks = this.createExportTasks();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        if (exportTasks.isEmpty()) {
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.itemexporter.finished"));

            // Check if we should quit after export (for CI/automation)
            boolean shouldQuit = GeneralConfig.autoQuitAfterExport;

            // Also check the legacy system property for backwards compatibility
            String quitAfterExport = System.getProperty("iconexporter.quitAfterExport");
            if ("true".equalsIgnoreCase(quitAfterExport)) {
                shouldQuit = true;
            }

            if (shouldQuit) {
                this.mod.log("Icon export complete, quitting game as requested");
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().stop();
                });
            }
        } else {
            IExportTask task = exportTasks.poll();
            try {
                task.run(guiGraphics);
            } catch (IOException e) {
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.itemexporter.error"));
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void renderBlurredBackground(float p_330683_) {
        // Do nothing
    }

    public String serializeNbtTag(Tag tag) {
        if (GeneralConfig.fileNameHashComponents) {
            return DigestUtils.md5Hex(tag.toString());
        } else {
            return tag.toString();
        }
    }

    public Queue<IExportTask> createExportTasks() {
        float scaleModified = (float) (this.scaleImage / this.scaleGui);
        int scaleModifiedRounded = (int) Math.ceil(scaleModified);

        // Initialize our output folder (legacy or structured based on config)
        String modpackName = GeneralConfig.modpackName;
        String mcVersion;
        try {
            mcVersion = net.minecraft.SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            mcVersion = "unknown";
        }
        String loader = detectLoader();
        File baseDir = org.cyclops.iconexporter.export.EnvironmentExportUtil.resolveIconsDir(
                Minecraft.getInstance().gameDirectory,
                this.scaleImage,
                modpackName,
                mcVersion,
                loader
        );

        // Create a list of tasks
        Wrapper<Integer> tasks = new Wrapper<>(0);
        Wrapper<Integer> taskProcessed = new Wrapper<>(0);
        Queue<IExportTask> exportTasks = Queues.newArrayDeque();

        // Add fluids
        for (Map.Entry<ResourceKey<Fluid>, Fluid> fluidEntry : BuiltInRegistries.FLUID.entrySet()) {
            tasks.set(tasks.get() + 1);
            String baseFilename = ImageExportUtil.genBaseFilenameFromFluid(fluidEntry.getKey());
            exportTasks.add((guiGraphics) -> {
                taskProcessed.set(taskProcessed.get() + 1);
                signalStatus(tasks, taskProcessed);
                guiGraphics.fill(0, 0, scaleModifiedRounded, scaleModifiedRounded, BACKGROUND_COLOR);
                ItemRenderUtil.renderFluid(guiGraphics, fluidEntry.getValue(), scaleModified, this.helpers);
                ImageExportUtil.exportImageFromScreenshot(baseDir, baseFilename, this.scaleImage, BACKGROUND_COLOR_SHIFTED, this.mod);
            });
        }

        // Add items
        CreativeModeTabs.tryRebuildTabContents(
                Minecraft.getInstance().player.connection.enabledFeatures(),
                Minecraft.getInstance().options.operatorItemsTab().get(),
                Minecraft.getInstance().level.registryAccess()
        );
        for (CreativeModeTab creativeModeTab : this.helpers.getCreativeTabs()) {
            for (ItemStack itemStack : creativeModeTab.getDisplayItems()) {
                tasks.set(tasks.get() + 1);
                String baseFilename = ImageExportUtil.genBaseFilenameFromItem(lookupProvider, itemStack, this.mod, this.helpers);
                exportTasks.add((guiGraphics) -> {
                    taskProcessed.set(taskProcessed.get() + 1);
                    signalStatus(tasks, taskProcessed);
                    guiGraphics.fill(0, 0, scaleModifiedRounded, scaleModifiedRounded, BACKGROUND_COLOR);
                    ItemRenderUtil.renderItem(guiGraphics, itemStack, scaleModified);
                    ImageExportUtil.exportImageFromScreenshot(baseDir, baseFilename, this.scaleImage, BACKGROUND_COLOR_SHIFTED, this.mod);
                    if (!itemStack.getComponents().isEmpty() && GeneralConfig.fileNameHashComponents) {
                        ImageExportUtil.exportNbtFile(lookupProvider, baseDir, baseFilename, itemStack.getComponentsPatch(), this.mod, this.helpers);
                    }
                });
            }
        }

        return exportTasks;
    }

    protected void signalStatus(Wrapper<Integer> tasks, Wrapper<Integer> taskProcessed) {
        Minecraft.getInstance().player.displayClientMessage(Component.translatable("gui.itemexporter.status", taskProcessed.get(), tasks.get()), true);
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
}
