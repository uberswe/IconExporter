package org.cyclops.iconexporter.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Utility for writing meta/mods.json with loader-specific mod information.
 */
public final class ModsExportUtil {

    private ModsExportUtil() { }

    /**
     * Write mods.json array to the destination using the provided helpers.
     * Also exports mod logos/icons to meta/mod_icons/ directory.
     * @param destFile Target file for mods.json
     * @param helpers Loader-specific helpers to retrieve mod list
     * @param mod The mod instance for error logging
     * @throws IOException if file writing fails
     */
    public static void writeModsJson(File destFile, IIconExporterHelpers helpers, IModBase mod) throws IOException {
        // Ensure parent directories exist
        File parent = destFile.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        // Create mod_icons directory
        File modIconsDir = new File(parent, "mod_icons");
        //noinspection ResultOfMethodCallIgnored
        modIconsDir.mkdirs();

        JsonArray arr = new JsonArray();

        try {
            List<IIconExporterHelpers.ModInfo> mods = helpers.getModList();
            for (IIconExporterHelpers.ModInfo modInfo : mods) {
                try {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("mod_id", modInfo.modId);
                    obj.addProperty("version", modInfo.version);
                    obj.addProperty("display_name", modInfo.displayName);

                    // Try to extract and enhance metadata from JAR file if path is available
                    ModMetadataExtractor.ModMetadata jarMetadata = null;
                    if (modInfo.jarFilePath != null && !modInfo.jarFilePath.isEmpty()) {
                        File jarFile = new File(modInfo.jarFilePath);
                        if (jarFile.exists()) {
                            jarMetadata = ModMetadataExtractor.extractMetadata(jarFile);
                        }
                    }

                    // Add description (prefer JAR metadata, fall back to ModInfo)
                    String description = jarMetadata != null && jarMetadata.description != null
                            ? jarMetadata.description
                            : modInfo.description;
                    if (description != null && !description.isEmpty()) {
                        obj.addProperty("description", description);
                    }

                    // Add author (prefer JAR metadata, fall back to ModInfo)
                    String author = jarMetadata != null && jarMetadata.author != null
                            ? jarMetadata.author
                            : modInfo.author;
                    if (author != null && !author.isEmpty()) {
                        obj.addProperty("author", author);
                    }

                    // Add URL (prefer JAR metadata, fall back to ModInfo)
                    String url = jarMetadata != null && jarMetadata.url != null
                            ? jarMetadata.url
                            : modInfo.url;
                    if (url != null && !url.isEmpty()) {
                        obj.addProperty("url", url);
                    }

                    // Try to export mod logo (first from JAR, then from helpers)
                    String logoPath = exportModLogo(modInfo.modId, modInfo.jarFilePath, jarMetadata,
                            modIconsDir, helpers, mod);
                    if (logoPath != null) {
                        obj.addProperty("logo_path", logoPath);
                    }

                    arr.add(obj);
                } catch (Exception e) {
                    // Log error and skip this mod
                    ErrorLogUtil.log("Failed to export mod info for " + modInfo.modId + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to retrieve mod list: " + e.getMessage());
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(destFile)) {
            writer.write(gson.toJson(arr));
        } catch (IOException e) {
            if (mod != null) {
                try {
                    mod.log("Failed to write mods.json: " + e.getMessage());
                } catch (Throwable ignored) {
                    // Ignore logging failures
                }
            }
            throw e;
        }
    }

    /**
     * Export a mod's logo to the mod_icons directory.
     * Tries JAR extraction first, then falls back to helpers.
     * @param modId The mod identifier
     * @param jarFilePath Path to the mod's JAR file (may be null)
     * @param jarMetadata Extracted metadata from JAR (may be null)
     * @param modIconsDir The directory to save logos to
     * @param helpers Loader-specific helpers
     * @param mod The mod instance for logging
     * @return Relative path to the logo file, or null if no logo was exported
     */
    private static String exportModLogo(String modId, String jarFilePath,
                                       ModMetadataExtractor.ModMetadata jarMetadata,
                                       File modIconsDir, IIconExporterHelpers helpers, IModBase mod) {
        String extension = "png";
        File logoFile = new File(modIconsDir, modId + "." + extension);

        try {
            // Try extracting logo from JAR file first
            if (jarFilePath != null && jarMetadata != null && jarMetadata.hasLogo) {
                File jarFile = new File(jarFilePath);
                if (jarFile.exists()) {
                    BufferedImage logoImage = ModMetadataExtractor.extractLogo(jarFile, jarMetadata.logoPath);
                    if (logoImage != null) {
                        ImageIO.write(logoImage, "PNG", logoFile);
                        return "meta/mod_icons/" + modId + "." + extension;
                    }
                }
            }

            // Fall back to helpers method
            InputStream logoStream = helpers.getModLogo(modId);
            if (logoStream != null) {
                try (FileOutputStream out = new FileOutputStream(logoFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = logoStream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                logoStream.close();

                return "meta/mod_icons/" + modId + "." + extension;
            }
        } catch (Exception e) {
            // Failed to export logo, that's okay - just continue without it
            if (mod != null) {
                try {
                    mod.log("Failed to export logo for mod " + modId + ": " + e.getMessage());
                } catch (Throwable ignored) {
                    // Ignore logging failures
                }
            }
        }
        return null;
    }
}
