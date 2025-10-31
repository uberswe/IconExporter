package org.cyclops.iconexporter;

import org.cyclops.cyclopscore.config.ConfigurablePropertyCommon;
import org.cyclops.cyclopscore.config.ModConfigLocation;
import org.cyclops.cyclopscore.config.extendedconfig.DummyConfigCommon;
import org.cyclops.cyclopscore.init.IModBase;

/**
 * A config with general options for this mod.
 * @author rubensworks
 *
 */
public class GeneralConfig<M extends IModBase> extends DummyConfigCommon<M> {

    @ConfigurablePropertyCommon(category = "core", comment = "The default image width in px to render at.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static int defaultScale = 512;

    @ConfigurablePropertyCommon(category = "core", comment = "If the components should be hashed with MD5 when constructing the file name, and if an auxiliary txt file should be created with the full components contents.", isCommandable = true)
    public static boolean fileNameHashComponents = false;

    // New automation/structure config entries (scaffolding)
    @ConfigurablePropertyCommon(category = "core", comment = "Base directory (relative to game dir) for structured exports when enabled.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static String exportBaseDir = "exports";

    @ConfigurablePropertyCommon(category = "core", comment = "Use structured multi-modpack output directories instead of legacy icon-exports-x{scale}.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static boolean useStructuredOutput = false;

    @ConfigurablePropertyCommon(category = "core", comment = "Automatically trigger export on client startup after entering a world.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static boolean autoExportOnStartup = false;

    @ConfigurablePropertyCommon(category = "core", comment = "Automatically create and load a temporary world on startup (requires autoExportOnStartup=true). Enables fully hands-free CI/CD automation with zero manual intervention.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static boolean autoCreateWorld = false;

    @ConfigurablePropertyCommon(category = "core", comment = "Automatically quit the game after export completes (for CI/CD automation). Only takes effect when autoExportOnStartup=true.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static boolean autoQuitAfterExport = false;

    @ConfigurablePropertyCommon(category = "core", comment = "Delay in seconds before starting automatic export after world load. This gives the game time to fully initialize all registries and systems.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static int autoExportDelay = 30;

    @ConfigurablePropertyCommon(category = "core", comment = "Optional modpack name to include in structured export paths.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static String modpackName = "";

    // DEPRECATED: These config values are now automatically read from manifest.json if available.
    // They are kept for backward compatibility with modpacks that don't have a manifest.json file.

    @ConfigurablePropertyCommon(category = "core", comment = "[DEPRECATED - Auto-detected from manifest.json] Optional modpack display name (human-readable name for modpack metadata).", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static String modpackDisplayName = "";

    @ConfigurablePropertyCommon(category = "core", comment = "[DEPRECATED - Auto-detected from manifest.json] Optional modpack version.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static String modpackVersion = "";

    @ConfigurablePropertyCommon(category = "core", comment = "[DEPRECATED - Auto-detected from manifest.json] Optional modpack description.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static String modpackDescription = "";

    @ConfigurablePropertyCommon(category = "core", comment = "[DEPRECATED - Auto-detected from manifest.json] Optional modpack author.", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static String modpackAuthor = "";

    @ConfigurablePropertyCommon(category = "core", comment = "[DEPRECATED - Auto-detected from manifest.json] Optional modpack URL (e.g., CurseForge or Modrinth link).", isCommandable = true, configLocation = ModConfigLocation.CLIENT)
    public static String modpackUrl = "";

    public GeneralConfig(M mod) {
        super(mod, "general");
    }

}
