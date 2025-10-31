package org.cyclops.iconexporter.helpers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.cyclops.cyclopscore.helper.FluidHelpers;
import org.cyclops.cyclopscore.helper.IModHelpersNeoForge;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author rubensworks
 */
public class IconExporterHelpersNeoForge extends IconExporterHelpersCommon {
    @Override
    public List<CreativeModeTab> getCreativeTabs() {
        return CreativeModeTabRegistry.getSortedCreativeModeTabs();
    }

    @Override
    public String getFluidLocalName(Fluid fluid) {
        return fluid.getFluidType().getDescription().getString();
    }

    @Override
    public void renderFluidSlot(GuiGraphics gui, Fluid fluid) {
        IModHelpersNeoForge.get().getGuiHelpers().renderFluidSlot(gui, new FluidStack(fluid, FluidHelpers.BUCKET_VOLUME), 0, 0);
    }

    @Override
    public List<ModInfo> getModList() {
        List<ModInfo> mods = new ArrayList<>();
        try {
            for (IModInfo mod : ModList.get().getMods()) {
                try {
                    String modId = mod.getModId();
                    String version = mod.getVersion().toString();
                    String displayName = mod.getDisplayName();

                    // Get additional metadata
                    String description = mod.getDescription();
                    String author = null; // NeoForge doesn't provide easy access to authors
                    String url = mod.getConfig().getConfigElement("displayURL")
                            .map(Object::toString)
                            .orElse(null);

                    // Get JAR file path
                    String jarPath = null;
                    try {
                        IModFile modFile = mod.getOwningFile().getFile();
                        Path path = modFile.getFilePath();
                        if (path != null) {
                            jarPath = path.toAbsolutePath().toString();
                        }
                    } catch (Exception ignored) {
                        // Couldn't get JAR path, continue without it
                    }

                    mods.add(new ModInfo(modId, version, displayName, description, author, url, jarPath));
                } catch (Exception e) {
                    // Skip this mod and continue
                }
            }
        } catch (Exception e) {
            // Return whatever we collected so far
        }
        return mods;
    }

    @Override
    public List<DatapackInfo> getDatapackList() {
        List<DatapackInfo> datapacks = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getServer() != null) {
                for (Pack pack : mc.level.getServer().getPackRepository().getSelectedPacks()) {
                    try {
                        String id = pack.getId();
                        String description = pack.getDescription().getString();
                        String source = pack.getClass().getSimpleName();
                        datapacks.add(new DatapackInfo(id, description, source));
                    } catch (Exception e) {
                        // Skip this datapack and continue
                    }
                }
            }
        } catch (Exception e) {
            // Return whatever we collected so far
        }
        return datapacks;
    }

    @Override
    public InputStream getModLogo(String modId) {
        try {
            Optional<? extends net.neoforged.fml.ModContainer> modContainer = ModList.get().getModContainerById(modId);
            if (modContainer.isPresent()) {
                IModInfo modInfo = modContainer.get().getModInfo();

                // Try to get the logo from mod metadata
                Optional<String> logoFile = modInfo.getLogoFile();
                if (logoFile.isPresent() && !logoFile.get().isEmpty()) {
                    // Get the mod file
                    IModFile modFile = modInfo.getOwningFile().getFile();

                    // Try to find the logo file
                    Path logoPath = modFile.findResource(logoFile.get());
                    if (logoPath != null && Files.exists(logoPath)) {
                        return Files.newInputStream(logoPath);
                    }
                }
            }
        } catch (Exception e) {
            // Failed to get logo, return null
        }
        return null;
    }
}
