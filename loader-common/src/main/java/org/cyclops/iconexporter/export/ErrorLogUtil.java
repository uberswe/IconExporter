package org.cyclops.iconexporter.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal error logging utility to accumulate errors and write them to disk.
 * This is scaffolding; integration will follow in subsequent steps.
 */
public final class ErrorLogUtil {

    private static final List<String> ERRORS = new ArrayList<>();

    private ErrorLogUtil() { }

    public static void log(String message) {
        if (message != null) {
            synchronized (ERRORS) {
                ERRORS.add(message);
            }
        }
    }

    public static void clear() {
        synchronized (ERRORS) {
            ERRORS.clear();
        }
    }

    /**
     * Write the accumulated errors to logs/errors.txt under the given base directory.
     */
    public static void write(File structuredBaseDir) throws IOException {
        File logsDir = new File(structuredBaseDir, "logs");
        //noinspection ResultOfMethodCallIgnored
        logsDir.mkdirs();
        File file = new File(logsDir, "errors.txt");
        try (FileWriter writer = new FileWriter(file)) {
            synchronized (ERRORS) {
                for (String line : ERRORS) {
                    writer.write(line);
                    writer.write(System.lineSeparator());
                }
            }
        }
    }
}
