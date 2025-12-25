package org.cyclops.iconexporter.export;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts metadata from mod JAR files.
 * Supports NeoForge (.toml), Forge (.toml), and Fabric (.json) mod formats.
 */
public final class ModMetadataExtractor {

    private ModMetadataExtractor() { }

    /**
     * Mod metadata extracted from JAR file.
     */
    public static class ModMetadata {
        public final String modId;
        public final String name;
        public final String version;
        public final String description;
        public final String author;
        public final String url;
        public final String logoPath;  // Path inside JAR
        public final boolean hasLogo;

        public ModMetadata(String modId, String name, String version, String description,
                         String author, String url, String logoPath) {
            this.modId = modId;
            this.name = name;
            this.version = version;
            this.description = description;
            this.author = author;
            this.url = url;
            this.logoPath = logoPath;
            this.hasLogo = logoPath != null && !logoPath.isEmpty();
        }
    }

    /**
     * Extract metadata from a mod JAR file.
     * Automatically detects the mod loader type (NeoForge/Forge/Fabric).
     *
     * @param jarFile The mod JAR file
     * @return Extracted metadata, or null if extraction fails
     */
    public static ModMetadata extractMetadata(File jarFile) {
        if (!jarFile.exists() || !jarFile.isFile() || !jarFile.getName().endsWith(".jar")) {
            return null;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            // Try NeoForge/Forge TOML format first
            JarEntry neoforgeEntry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            if (neoforgeEntry != null) {
                return extractFromToml(jar, neoforgeEntry);
            }

            JarEntry forgeEntry = jar.getJarEntry("META-INF/mods.toml");
            if (forgeEntry != null) {
                return extractFromToml(jar, forgeEntry);
            }

            // Try Fabric JSON format
            JarEntry fabricEntry = jar.getJarEntry("fabric.mod.json");
            if (fabricEntry != null) {
                return extractFromFabricJson(jar, fabricEntry);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract logo image from JAR file.
     *
     * @param jarFile The mod JAR file
     * @param logoPath Path to logo inside JAR (from metadata)
     * @return BufferedImage of the logo, or null if not found
     */
    public static BufferedImage extractLogo(File jarFile, String logoPath) {
        if (logoPath == null || logoPath.isEmpty()) {
            return null;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry logoEntry = jar.getJarEntry(logoPath);
            if (logoEntry == null) {
                // Try without leading slash
                logoPath = logoPath.startsWith("/") ? logoPath.substring(1) : "/" + logoPath;
                logoEntry = jar.getJarEntry(logoPath.startsWith("/") ? logoPath.substring(1) : logoPath);
            }

            if (logoEntry != null) {
                try (InputStream is = jar.getInputStream(logoEntry)) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception e) {
            // Failed to extract logo
        }

        return null;
    }

    /**
     * Extract metadata from NeoForge/Forge TOML file.
     */
    private static ModMetadata extractFromToml(JarFile jar, JarEntry tomlEntry) throws IOException {
        try (InputStream is = jar.getInputStream(tomlEntry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String modId = null;
            String name = null;
            String version = null;
            String description = null;
            String author = null;
            String url = null;
            String logoPath = null;

            // Simple TOML parser - looks for key="value" patterns
            Pattern pattern = Pattern.compile("^\\s*([a-zA-Z]+)\\s*=\\s*[\"']([^\"']*)[\"']");
            Pattern authorPattern = Pattern.compile("^\\s*authors\\s*=\\s*[\"']([^\"']*)[\"']");

            String line;
            boolean inModsSection = false;

            while ((line = reader.readLine()) != null) {
                // Check if we're in the [[mods]] section
                if (line.trim().startsWith("[[mods]]")) {
                    inModsSection = true;
                    continue;
                }

                // Check if we've left the [[mods]] section
                if (inModsSection && line.trim().startsWith("[[")) {
                    break;
                }

                if (inModsSection || modId == null) { // Also check outside mods section for some values
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String key = matcher.group(1);
                        String value = matcher.group(2);

                        switch (key) {
                            case "modId":
                                modId = value;
                                break;
                            case "displayName":
                                name = value;
                                break;
                            case "version":
                                version = value;
                                break;
                            case "description":
                                description = value;
                                break;
                            case "displayURL":
                                url = value;
                                break;
                            case "logoFile":
                                logoPath = value;
                                break;
                        }
                    }

                    // Special handling for authors (may or may not have quotes)
                    Matcher authorMatcher = authorPattern.matcher(line);
                    if (authorMatcher.find()) {
                        author = authorMatcher.group(1);
                    }
                }
            }

            if (modId != null) {
                return new ModMetadata(modId, name, version, description, author, url, logoPath);
            }
        }

        return null;
    }

    /**
     * Extract metadata from Fabric fabric.mod.json file.
     */
    private static ModMetadata extractFromFabricJson(JarFile jar, JarEntry jsonEntry) throws IOException {
        try (InputStream is = jar.getInputStream(jsonEntry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            String modId = getStringOrNull(json, "id");
            String name = getStringOrNull(json, "name");
            String version = getStringOrNull(json, "version");
            String description = getStringOrNull(json, "description");

            // Authors can be string or array
            String author = null;
            if (json.has("authors")) {
                if (json.get("authors").isJsonArray()) {
                    var authors = json.getAsJsonArray("authors");
                    if (authors.size() > 0) {
                        author = authors.get(0).getAsString();
                    }
                } else {
                    author = json.get("authors").getAsString();
                }
            }

            // Contact info for URL
            String url = null;
            if (json.has("contact")) {
                JsonObject contact = json.getAsJsonObject("contact");
                if (contact.has("homepage")) {
                    url = contact.get("homepage").getAsString();
                } else if (contact.has("sources")) {
                    url = contact.get("sources").getAsString();
                }
            }

            String logoPath = getStringOrNull(json, "icon");

            if (modId != null) {
                return new ModMetadata(modId, name, version, description, author, url, logoPath);
            }
        }

        return null;
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}
