package org.cyclops.iconexporter.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;

/**
 * Parser for CurseForge/Modrinth modpack manifest files.
 * Extracts modpack metadata from manifest.json.
 */
public final class ManifestParser {

    private ManifestParser() { }

    /**
     * Modpack metadata extracted from manifest.json.
     */
    public static class ModpackMetadata {
        public final String name;
        public final String version;
        public final String author;
        public final String mcVersion;
        public final String loaderId;
        public final String loaderVersion;
        public final String description;
        public final String url;

        public ModpackMetadata(String name, String version, String author,
                             String mcVersion, String loaderId, String loaderVersion,
                             String description, String url) {
            this.name = name;
            this.version = version;
            this.author = author;
            this.mcVersion = mcVersion;
            this.loaderId = loaderId;
            this.loaderVersion = loaderVersion;
            this.description = description;
            this.url = url;
        }
    }

    /**
     * Try to find and parse a manifest.json file in the game directory or parent directories.
     * Looks for manifest.json in: gameDir, gameDir/.., gameDir/../..
     *
     * @param gameDir The Minecraft game directory
     * @return Parsed modpack metadata, or null if no manifest found
     */
    public static ModpackMetadata findAndParseManifest(File gameDir) {
        // Try multiple locations
        File[] searchLocations = {
            new File(gameDir, "manifest.json"),                    // In game dir
            new File(gameDir.getParentFile(), "manifest.json"),    // One level up
            new File(gameDir.getParentFile().getParentFile(), "manifest.json") // Two levels up
        };

        for (File manifestFile : searchLocations) {
            if (manifestFile.exists() && manifestFile.isFile()) {
                ModpackMetadata metadata = parseManifest(manifestFile);
                if (metadata != null) {
                    return metadata;
                }
            }
        }

        return null;
    }

    /**
     * Parse a manifest.json file.
     *
     * @param manifestFile The manifest.json file
     * @return Parsed modpack metadata, or null if parsing fails
     */
    public static ModpackMetadata parseManifest(File manifestFile) {
        try (FileReader reader = new FileReader(manifestFile)) {
            JsonObject manifest = JsonParser.parseReader(reader).getAsJsonObject();

            // Extract basic modpack info
            String name = getStringOrDefault(manifest, "name", null);
            String version = getStringOrDefault(manifest, "version", null);
            String author = getStringOrDefault(manifest, "author", null);

            // Extract Minecraft version
            String mcVersion = null;
            if (manifest.has("minecraft")) {
                JsonObject minecraft = manifest.getAsJsonObject("minecraft");
                mcVersion = getStringOrDefault(minecraft, "version", null);
            }

            // Extract mod loader info
            String loaderId = null;
            String loaderVersion = null;
            if (manifest.has("minecraft") && manifest.getAsJsonObject("minecraft").has("modLoaders")) {
                JsonArray modLoaders = manifest.getAsJsonObject("minecraft").getAsJsonArray("modLoaders");
                if (modLoaders.size() > 0) {
                    JsonObject primaryLoader = modLoaders.get(0).getAsJsonObject();
                    String loaderIdRaw = getStringOrDefault(primaryLoader, "id", null);
                    if (loaderIdRaw != null) {
                        // Parse "neoforge-21.1.209" into loader="neoforge" and version="21.1.209"
                        String[] parts = loaderIdRaw.split("-", 2);
                        loaderId = parts[0];
                        if (parts.length > 1) {
                            loaderVersion = parts[1];
                        }
                    }
                }
            }

            // Try to extract description (might not exist in all manifests)
            String description = getStringOrDefault(manifest, "description", null);

            // Try to extract URL (might not exist in all manifests)
            String url = getStringOrDefault(manifest, "projectUrl", null);
            if (url == null) {
                url = getStringOrDefault(manifest, "url", null);
            }

            // Only return metadata if we got at least a name
            if (name != null) {
                return new ModpackMetadata(name, version, author, mcVersion,
                        loaderId, loaderVersion, description, url);
            }

            return null;
        } catch (Exception e) {
            // Failed to parse manifest
            return null;
        }
    }

    /**
     * Get a string value from JSON object, or return default if not found.
     */
    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key)) {
            JsonElement element = obj.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            }
        }
        return defaultValue;
    }
}
