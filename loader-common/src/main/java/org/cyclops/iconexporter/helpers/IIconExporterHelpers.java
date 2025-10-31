package org.cyclops.iconexporter.helpers;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * @author rubensworks
 */
public interface IIconExporterHelpers {

    public String componentsToString(HolderLookup.Provider lookupProvider, DataComponentPatch components);

    public List<CreativeModeTab> getCreativeTabs();

    public String getFluidLocalName(Fluid fluid);

    public void renderFluidSlot(GuiGraphics gui, Fluid fluid);

    /**
     * Get a list of all loaded mods with their metadata.
     * @return List of mod information objects
     */
    public List<ModInfo> getModList();

    /**
     * Get a list of all loaded datapacks with their metadata.
     * @return List of datapack information objects
     */
    public List<DatapackInfo> getDatapackList();

    /**
     * Get mod logo/icon as an InputStream for a given mod ID.
     * @param modId The mod identifier
     * @return InputStream for the logo, or null if not available
     */
    public java.io.InputStream getModLogo(String modId);

    /**
     * Simple data class for mod information.
     */
    public static class ModInfo {
        public final String modId;
        public final String version;
        public final String displayName;
        public final String description;
        public final String author;
        public final String url;
        public final String jarFilePath; // Path to the mod's JAR file for metadata extraction

        public ModInfo(String modId, String version, String displayName) {
            this(modId, version, displayName, null, null, null, null);
        }

        public ModInfo(String modId, String version, String displayName, String description,
                      String author, String url, String jarFilePath) {
            this.modId = modId;
            this.version = version;
            this.displayName = displayName;
            this.description = description;
            this.author = author;
            this.url = url;
            this.jarFilePath = jarFilePath;
        }
    }

    /**
     * Simple data class for datapack information.
     */
    public static class DatapackInfo {
        public final String id;
        public final String description;
        public final String source;

        public DatapackInfo(String id, String description, String source) {
            this.id = id;
            this.description = description;
            this.source = source;
        }
    }

}
