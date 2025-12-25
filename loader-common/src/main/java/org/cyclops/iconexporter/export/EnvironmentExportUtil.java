package org.cyclops.iconexporter.export;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;

/**
 * Utilities for writing environment and metadata exports.
 *
 * This scaffolding is intentionally minimal and safe:
 * - Respects GeneralConfig.useStructuredOutput and exportBaseDir.
 * - Keeps legacy fallback to icon-exports-x{scale} when structured output is disabled.
 * - Provides a helper to write meta/modpack.json.
 */
public final class EnvironmentExportUtil {

    private EnvironmentExportUtil() { }

    /**
     * Resolve the base directory for structured exports under the game directory.
     */
    public static File resolveStructuredBase(File gameDir) {
        return new File(gameDir, GeneralConfig.exportBaseDir);
    }

    /**
     * Resolve the structured root: exportBaseDir/pack/mc/loader
     */
    public static File resolveStructuredRoot(File gameDir, String modpackName, String mcVersion, String loaderId) {
        String pack = (modpackName == null || modpackName.isEmpty()) ? "unknown" : modpackName;
        String mc = (mcVersion == null || mcVersion.isEmpty()) ? "unknown" : mcVersion;
        String loader = (loaderId == null || loaderId.isEmpty()) ? "unknown" : loaderId;
        return new File(new File(new File(resolveStructuredBase(gameDir), pack), mc), loader);
    }

    /**
     * Resolve the file path for meta/modpack.json, creating parent directories as needed.
     * Falls back to legacy icon-exports-x{scale}/meta when structured output is disabled.
     */
    public static File resolveModpackMetaFile(File gameDir, int scale, String modpackName, String mcVersion, String loaderId) {
        if (GeneralConfig.useStructuredOutput) {
            File metaDir = new File(resolveStructuredRoot(gameDir, modpackName, mcVersion, loaderId), "meta");
            //noinspection ResultOfMethodCallIgnored
            metaDir.mkdirs();
            return new File(metaDir, "modpack.json");
        } else {
            File baseDir = new File(gameDir, "icon-exports-x" + scale);
            File metaDir = new File(baseDir, "meta");
            //noinspection ResultOfMethodCallIgnored
            metaDir.mkdirs();
            return new File(metaDir, "modpack.json");
        }
    }

    /**
     * Resolve the icons directory depending on structured/legacy mode.
     */
    public static File resolveIconsDir(File gameDir, int scale, String modpackName, String mcVersion, String loaderId) {
        if (GeneralConfig.useStructuredOutput) {
            File iconsDir = new File(resolveStructuredRoot(gameDir, modpackName, mcVersion, loaderId), "icons");
            //noinspection ResultOfMethodCallIgnored
            iconsDir.mkdirs();
            return iconsDir;
        } else {
            File baseDir = new File(gameDir, "icon-exports-x" + scale);
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdir();
            return baseDir;
        }
    }

    /**
     * Resolve the recipes directory depending on structured/legacy mode.
     */
    public static File resolveRecipesDir(File gameDir, int scale, String modpackName, String mcVersion, String loaderId) {
        if (GeneralConfig.useStructuredOutput) {
            File recipesDir = new File(resolveStructuredRoot(gameDir, modpackName, mcVersion, loaderId), "recipes");
            //noinspection ResultOfMethodCallIgnored
            recipesDir.mkdirs();
            return recipesDir;
        } else {
            File baseDir = new File(gameDir, "icon-exports-x" + scale);
            File recipesDir = new File(baseDir, "recipes");
            //noinspection ResultOfMethodCallIgnored
            recipesDir.mkdirs();
            return recipesDir;
        }
    }

    /**
     * Resolve the data directory depending on structured/legacy mode.
     */
    public static File resolveDataDir(File gameDir, int scale, String modpackName, String mcVersion, String loaderId) {
        if (GeneralConfig.useStructuredOutput) {
            File dataDir = new File(resolveStructuredRoot(gameDir, modpackName, mcVersion, loaderId), "data");
            //noinspection ResultOfMethodCallIgnored
            dataDir.mkdirs();
            return dataDir;
        } else {
            File baseDir = new File(gameDir, "icon-exports-x" + scale);
            File dataDir = new File(baseDir, "data");
            //noinspection ResultOfMethodCallIgnored
            dataDir.mkdirs();
            return dataDir;
        }
    }

    /**
     * Resolve the textures directory depending on structured/legacy mode.
     * @param subdirectory The subdirectory within textures (e.g., "items" or "blocks")
     */
    public static File resolveTexturesDir(File gameDir, int scale, String modpackName, String mcVersion, String loaderId, String subdirectory) {
        if (GeneralConfig.useStructuredOutput) {
            File texturesDir = new File(resolveStructuredRoot(gameDir, modpackName, mcVersion, loaderId), "textures");
            if (subdirectory != null && !subdirectory.isEmpty()) {
                texturesDir = new File(texturesDir, subdirectory);
            }
            //noinspection ResultOfMethodCallIgnored
            texturesDir.mkdirs();
            return texturesDir;
        } else {
            File baseDir = new File(gameDir, "icon-exports-x" + scale);
            File texturesDir = new File(baseDir, "textures");
            if (subdirectory != null && !subdirectory.isEmpty()) {
                texturesDir = new File(texturesDir, subdirectory);
            }
            //noinspection ResultOfMethodCallIgnored
            texturesDir.mkdirs();
            return texturesDir;
        }
    }

    /**
     * Resolve the models directory depending on structured/legacy mode.
     * @param subdirectory The subdirectory within models (e.g., "items" or "blocks")
     */
    public static File resolveModelsDir(File gameDir, int scale, String modpackName, String mcVersion, String loaderId, String subdirectory) {
        if (GeneralConfig.useStructuredOutput) {
            File modelsDir = new File(resolveStructuredRoot(gameDir, modpackName, mcVersion, loaderId), "models");
            if (subdirectory != null && !subdirectory.isEmpty()) {
                modelsDir = new File(modelsDir, subdirectory);
            }
            //noinspection ResultOfMethodCallIgnored
            modelsDir.mkdirs();
            return modelsDir;
        } else {
            File baseDir = new File(gameDir, "icon-exports-x" + scale);
            File modelsDir = new File(baseDir, "models");
            if (subdirectory != null && !subdirectory.isEmpty()) {
                modelsDir = new File(modelsDir, subdirectory);
            }
            //noinspection ResultOfMethodCallIgnored
            modelsDir.mkdirs();
            return modelsDir;
        }
    }

    /**
     * Resolve the translations directory depending on structured/legacy mode.
     */
    public static File resolveTranslationsDir(File gameDir, int scale, String modpackName, String mcVersion, String loaderId) {
        if (GeneralConfig.useStructuredOutput) {
            File translationsDir = new File(resolveStructuredRoot(gameDir, modpackName, mcVersion, loaderId), "translations");
            //noinspection ResultOfMethodCallIgnored
            translationsDir.mkdirs();
            return translationsDir;
        } else {
            File baseDir = new File(gameDir, "icon-exports-x" + scale);
            File translationsDir = new File(baseDir, "translations");
            //noinspection ResultOfMethodCallIgnored
            translationsDir.mkdirs();
            return translationsDir;
        }
    }

    /**
     * Write meta/modpack.json with enhanced environment and modpack info.
     * Automatically detects and parses manifest.json if available.
     */
    public static void writeModpackJson(File file, File gameDir, String modpackName, String mcVersion, String loaderId, IModBase mod, IIconExporterHelpers helpers) throws IOException {
        JsonObject obj = new JsonObject();

        // Try to find and parse manifest.json
        ManifestParser.ModpackMetadata manifestData = ManifestParser.findAndParseManifest(gameDir);

        // Use manifest data if available, otherwise fall back to config/defaults
        String finalModpackName;
        if (manifestData != null && manifestData.name != null) {
            finalModpackName = manifestData.name;
            obj.addProperty("modpack", sanitizeForPath(manifestData.name));
            obj.addProperty("modpack_name", manifestData.name);
        } else {
            // Fall back to provided modpackName or config
            finalModpackName = (modpackName == null || modpackName.isEmpty()) ? "unknown" : modpackName;
            obj.addProperty("modpack", finalModpackName);

            // Legacy: Check config for display name
            if (GeneralConfig.modpackDisplayName != null && !GeneralConfig.modpackDisplayName.isEmpty()) {
                obj.addProperty("modpack_name", GeneralConfig.modpackDisplayName);
            }
        }

        // Add modpack version
        if (manifestData != null && manifestData.version != null) {
            obj.addProperty("modpack_version", manifestData.version);
        } else if (GeneralConfig.modpackVersion != null && !GeneralConfig.modpackVersion.isEmpty()) {
            obj.addProperty("modpack_version", GeneralConfig.modpackVersion);
        }

        // Add modpack description
        if (manifestData != null && manifestData.description != null) {
            obj.addProperty("modpack_description", manifestData.description);
        } else if (GeneralConfig.modpackDescription != null && !GeneralConfig.modpackDescription.isEmpty()) {
            obj.addProperty("modpack_description", GeneralConfig.modpackDescription);
        }

        // Add modpack author
        if (manifestData != null && manifestData.author != null) {
            obj.addProperty("modpack_author", manifestData.author);
        } else if (GeneralConfig.modpackAuthor != null && !GeneralConfig.modpackAuthor.isEmpty()) {
            obj.addProperty("modpack_author", GeneralConfig.modpackAuthor);
        }

        // Add modpack URL
        if (manifestData != null && manifestData.url != null) {
            obj.addProperty("modpack_url", manifestData.url);
        } else if (GeneralConfig.modpackUrl != null && !GeneralConfig.modpackUrl.isEmpty()) {
            obj.addProperty("modpack_url", GeneralConfig.modpackUrl);
        }

        // Version info - prefer manifest data
        String finalMcVersion = (manifestData != null && manifestData.mcVersion != null)
                ? manifestData.mcVersion
                : ((mcVersion == null || mcVersion.isEmpty()) ? "unknown" : mcVersion);
        obj.addProperty("mc_version", finalMcVersion);

        String finalLoaderId = (manifestData != null && manifestData.loaderId != null)
                ? manifestData.loaderId
                : ((loaderId == null || loaderId.isEmpty()) ? "unknown" : loaderId);
        obj.addProperty("loader", finalLoaderId);

        // Add loader version - prefer manifest data
        String loaderVersion = null;
        if (manifestData != null && manifestData.loaderVersion != null) {
            loaderVersion = manifestData.loaderVersion;
        } else {
            loaderVersion = detectLoaderVersion(finalLoaderId);
        }
        if (loaderVersion != null && !loaderVersion.isEmpty()) {
            obj.addProperty("loader_version", loaderVersion);
        }

        // Add counts
        try {
            // Get mod count
            if (helpers != null) {
                var modList = helpers.getModList();
                if (modList != null) {
                    obj.addProperty("total_mods", modList.size());
                }
            }

            // Get item count
            int itemCount = BuiltInRegistries.ITEM.size();
            obj.addProperty("total_items", itemCount);

            // Get block count
            int blockCount = BuiltInRegistries.BLOCK.size();
            obj.addProperty("total_blocks", blockCount);
        } catch (Exception e) {
            // Failed to get counts, that's okay - continue with what we have
        }

        // Export timestamp
        obj.addProperty("exported_at", Instant.now().toString());

        // Write with pretty printing
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(obj);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        } catch (IOException e) {
            if (mod != null) {
                try {
                    mod.log(e.getMessage());
                } catch (Throwable ignored) {
                    // Ignore logging failures
                }
            }
            throw e;
        }
    }

    /**
     * Detect the loader version based on the loader ID.
     */
    private static String detectLoaderVersion(String loaderId) {
        try {
            if ("fabric".equals(loaderId)) {
                // Try to get Fabric loader version
                if (classExists("net.fabricmc.loader.api.FabricLoader")) {
                    Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
                    Object instance = fabricLoaderClass.getMethod("getInstance").invoke(null);
                    Object modContainer = fabricLoaderClass.getMethod("getModContainer", String.class)
                            .invoke(instance, "fabricloader");
                    if (modContainer != null) {
                        Class<?> optionalClass = Class.forName("java.util.Optional");
                        Object metadata = optionalClass.getMethod("get").invoke(modContainer);
                        Class<?> modMetadataClass = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata");
                        Object version = modMetadataClass.getMethod("getVersion").invoke(metadata);
                        return version.toString();
                    }
                }
            } else if ("forge".equals(loaderId) || "neoforge".equals(loaderId)) {
                // Try to get Forge/NeoForge version
                String loaderClass = "forge".equals(loaderId)
                        ? "net.minecraftforge.fml.loading.FMLLoader"
                        : "net.neoforged.fml.loading.FMLLoader";
                if (classExists(loaderClass)) {
                    Class<?> fmlLoaderClass = Class.forName(loaderClass);
                    Object versionInfo = fmlLoaderClass.getMethod("versionInfo").invoke(null);
                    if (versionInfo != null) {
                        Class<?> versionInfoClass = versionInfo.getClass();
                        Object mcVersion = versionInfoClass.getMethod("mcVersion").invoke(versionInfo);
                        Object forgeVersion = versionInfoClass.getMethod("forgeVersion").invoke(versionInfo);
                        if (forgeVersion != null) {
                            return forgeVersion.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Failed to detect version, return null
        }
        return null;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Sanitize a modpack name for use in file paths.
     * Converts spaces to underscores and removes special characters.
     */
    private static String sanitizeForPath(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        // Convert to lowercase, replace spaces with underscores, remove special chars
        return name.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
}
