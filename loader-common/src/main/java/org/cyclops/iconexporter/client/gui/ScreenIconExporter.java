package org.cyclops.iconexporter.client.gui;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.opengl.GL11;
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
import org.cyclops.iconexporter.export.ExportCompletionUtil;
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
    private final boolean previousHideGui;

    public ScreenIconExporter(HolderLookup.Provider lookupProvider, int scaleImage, double scaleGui, IModBase mod, IIconExporterHelpers helpers) {
        super(Component.translatable("gui.itemexporter.name"));
        this.lookupProvider = lookupProvider;
        this.scaleImage = scaleImage;
        this.scaleGui = scaleGui;
        this.mod = mod;
        this.helpers = helpers;
        this.exportTasks = this.createExportTasks();

        // Hide GUI (HUD/action bar) during export to prevent artifacts in screenshots
        this.previousHideGui = Minecraft.getInstance().options.hideGui;
        Minecraft.getInstance().options.hideGui = true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        if (exportTasks.isEmpty()) {
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.itemexporter.finished"));

            // Write completion marker for Docker/automation
            try {
                File baseDir = new File(Minecraft.getInstance().gameDirectory, GeneralConfig.exportBaseDir);
                ExportCompletionUtil.writeCompletionMarker(baseDir, this.mod);
            } catch (Exception e) {
                this.mod.log("Failed to write completion marker: " + e.getMessage());
            }

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

    @Override
    public void removed() {
        super.removed();
        // Restore the previous hideGui setting
        Minecraft.getInstance().options.hideGui = this.previousHideGui;
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

        // Get dimensions to fill - use actual framebuffer pixels converted to GUI space to ensure
        // complete coverage. The screenshot captures framebuffer pixels, but fill() works in GUI coords.
        // We need to fill enough GUI units to cover the entire framebuffer area being captured.
        // Using max of GUI-scaled dimensions and scale size ensures coverage regardless of GUI scale.
        int guiScaledWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int guiScaledHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        // Add extra margin to ensure complete coverage - use scale size as minimum
        int fillWidth = Math.max(guiScaledWidth, this.scaleImage) + 100;
        int fillHeight = Math.max(guiScaledHeight, this.scaleImage) + 100;

        // Add fluids
        for (Map.Entry<ResourceKey<Fluid>, Fluid> fluidEntry : BuiltInRegistries.FLUID.entrySet()) {
            tasks.set(tasks.get() + 1);
            String baseFilename = ImageExportUtil.genBaseFilenameFromFluid(fluidEntry.getKey());
            exportTasks.add((guiGraphics) -> {
                taskProcessed.set(taskProcessed.get() + 1);
                signalStatus(tasks, taskProcessed);
                // Clear framebuffer to prevent world from showing through transparent parts
                clearFramebufferWithBackgroundColor();
                // Also fill with GUI graphics to ensure the render pipeline is in correct state
                guiGraphics.fill(0, 0, fillWidth, fillHeight, BACKGROUND_COLOR);
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
                    // Clear framebuffer to prevent world from showing through transparent parts
                    clearFramebufferWithBackgroundColor();
                    // Also fill with GUI graphics to ensure the render pipeline is in correct state
                    guiGraphics.fill(0, 0, fillWidth, fillHeight, BACKGROUND_COLOR);
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

    /**
     * Clears the framebuffer with the background color to ensure no world geometry
     * bleeds through transparent parts of items.
     * This is necessary because guiGraphics.fill() uses alpha blending which can
     * cause world pixels to show through when items have transparency.
     */
    private static void clearFramebufferWithBackgroundColor() {
        // Clear color buffer with our background color (RGB 254, 255, 255)
        RenderSystem.clearColor(254f / 255f, 1.0f, 1.0f, 1.0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
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
