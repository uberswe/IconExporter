package org.cyclops.iconexporter.helpers;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.material.Fluid;
import org.cyclops.cyclopscore.helper.IModHelpersFabric;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author rubensworks
 */
public class IconExporterHelpersFabric extends IconExporterHelpersCommon {
    @Override
    public List<CreativeModeTab> getCreativeTabs() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.stream()
                .filter(tab -> !tab.getBackgroundTexture().equals(CreativeModeTab.createTextureLocation("item_search")))
                .toList();
    }

    @Override
    public String getFluidLocalName(Fluid fluid) {
        return FluidVariantAttributes.getName(FluidVariant.of(fluid)).getString();
    }

    @Override
    public void renderFluidSlot(GuiGraphics gui, Fluid fluid) {
        IModHelpersFabric.get().getGuiHelpers().renderFluidSlot(gui, FluidVariant.of(fluid), IModHelpersFabric.get().getFluidHelpers().getBucketVolume(), 0, 0);
    }

    @Override
    public List<ModInfo> getModList() {
        List<ModInfo> mods = new ArrayList<>();
        try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                try {
                    String modId = mod.getMetadata().getId();
                    String version = mod.getMetadata().getVersion().getFriendlyString();
                    String displayName = mod.getMetadata().getName();

                    // Get additional metadata
                    String description = mod.getMetadata().getDescription();

                    // Get author (may be multiple, take first)
                    String author = null;
                    if (!mod.getMetadata().getAuthors().isEmpty()) {
                        author = mod.getMetadata().getAuthors().iterator().next().getName();
                    }

                    // Get URL from contact info
                    String url = null;
                    if (mod.getMetadata().getContact().get("homepage").isPresent()) {
                        url = mod.getMetadata().getContact().get("homepage").get();
                    } else if (mod.getMetadata().getContact().get("sources").isPresent()) {
                        url = mod.getMetadata().getContact().get("sources").get();
                    }

                    // Get JAR file path
                    String jarPath = null;
                    try {
                        Path path = mod.getRootPaths().stream().findFirst().orElse(null);
                        if (path != null) {
                            // For JAR files, the root path points inside the JAR - we need the JAR itself
                            if (path.toString().contains("!")) {
                                // Extract JAR path from jar:file:/path/to/mod.jar!/
                                String pathStr = path.toString();
                                int jarEnd = pathStr.indexOf("!");
                                if (jarEnd > 0) {
                                    jarPath = pathStr.substring(0, jarEnd)
                                            .replace("jar:file:", "")
                                            .replace("file:", "");
                                }
                            } else {
                                jarPath = path.toAbsolutePath().toString();
                            }
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
            Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer(modId);
            if (modContainer.isPresent()) {
                ModContainer mod = modContainer.get();

                // Try to get the icon from mod metadata
                Optional<String> iconPath = mod.getMetadata().getIconPath(512); // Try largest size first
                if (iconPath.isEmpty()) {
                    iconPath = mod.getMetadata().getIconPath(256);
                }
                if (iconPath.isEmpty()) {
                    iconPath = mod.getMetadata().getIconPath(128);
                }
                if (iconPath.isEmpty()) {
                    iconPath = mod.getMetadata().getIconPath(64);
                }
                if (iconPath.isEmpty()) {
                    iconPath = mod.getMetadata().getIconPath(32);
                }

                if (iconPath.isPresent()) {
                    // Try to find the icon file
                    Path modPath = mod.findPath(iconPath.get()).orElse(null);
                    if (modPath != null && Files.exists(modPath)) {
                        return Files.newInputStream(modPath);
                    }
                }
            }
        } catch (Exception e) {
            // Failed to get logo, return null
        }
        return null;
    }
}
