package org.cyclops.iconexporter.export;

import org.cyclops.cyclopscore.init.IModBase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;

/**
 * Utility for signaling export completion to external orchestrators (e.g., Docker).
 *
 * This utility writes marker files that Docker entrypoint scripts can monitor
 * to detect when exports complete or fail.
 *
 * @author rubensworks
 */
public final class ExportCompletionUtil {

    private ExportCompletionUtil() {
        // Utility class
    }

    /**
     * Write a completion marker file to signal successful export.
     * Used by Docker containers to detect when export is done.
     *
     * @param baseDir The base export directory where the marker should be written
     * @param mod     The mod instance for logging
     */
    public static void writeCompletionMarker(File baseDir, IModBase mod) {
        File marker = new File(baseDir, ".export_complete");
        try (FileWriter writer = new FileWriter(marker)) {
            writer.write("Export completed at: " + Instant.now() + "\n");
            writer.write("Minecraft version: " + net.minecraft.SharedConstants.getCurrentVersion().getName() + "\n");
            if (mod != null) {
                mod.log("Completion marker written: " + marker.getAbsolutePath());
            }
        } catch (IOException e) {
            if (mod != null) {
                mod.log("Failed to write completion marker: " + e.getMessage());
            }
        }
    }

    /**
     * Write an error marker file to signal export failure.
     *
     * @param baseDir      The base export directory where the marker should be written
     * @param errorMessage The error message to include
     * @param mod          The mod instance for logging
     */
    public static void writeErrorMarker(File baseDir, String errorMessage, IModBase mod) {
        File marker = new File(baseDir, ".export_error");
        try (FileWriter writer = new FileWriter(marker)) {
            writer.write("Export failed at: " + Instant.now() + "\n");
            writer.write("Error: " + errorMessage + "\n");
            if (mod != null) {
                mod.log("Error marker written: " + marker.getAbsolutePath());
            }
        } catch (IOException e) {
            if (mod != null) {
                mod.log("Failed to write error marker: " + e.getMessage());
            }
        }
    }
}
