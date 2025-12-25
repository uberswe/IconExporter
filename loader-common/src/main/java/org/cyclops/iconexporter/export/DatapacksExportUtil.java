package org.cyclops.iconexporter.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Utility for writing meta/datapacks.json with loader-specific datapack information.
 */
public final class DatapacksExportUtil {

    private DatapacksExportUtil() { }

    /**
     * Write datapacks.json array to the destination using the provided helpers.
     * @param destFile Target file for datapacks.json
     * @param helpers Loader-specific helpers to retrieve datapack list
     * @param mod The mod instance for error logging
     * @throws IOException if file writing fails
     */
    public static void writeDatapacksJson(File destFile, IIconExporterHelpers helpers, IModBase mod) throws IOException {
        // Ensure parent directories exist
        File parent = destFile.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        JsonArray arr = new JsonArray();

        try {
            List<IIconExporterHelpers.DatapackInfo> datapacks = helpers.getDatapackList();
            for (IIconExporterHelpers.DatapackInfo datapackInfo : datapacks) {
                try {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", datapackInfo.id);
                    obj.addProperty("description", datapackInfo.description);
                    obj.addProperty("source", datapackInfo.source);
                    arr.add(obj);
                } catch (Exception e) {
                    // Log error and skip this datapack
                    ErrorLogUtil.log("Failed to export datapack info for " + datapackInfo.id + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to retrieve datapack list: " + e.getMessage());
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(destFile)) {
            writer.write(gson.toJson(arr));
        } catch (IOException e) {
            if (mod != null) {
                try {
                    mod.log("Failed to write datapacks.json: " + e.getMessage());
                } catch (Throwable ignored) {
                    // Ignore logging failures
                }
            }
            throw e;
        }
    }
}
