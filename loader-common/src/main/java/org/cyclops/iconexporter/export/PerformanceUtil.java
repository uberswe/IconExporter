package org.cyclops.iconexporter.export;

import org.cyclops.cyclopscore.init.IModBase;

/**
 * Utility for managing performance during export operations.
 * Helps with memory management and batching for large modpacks.
 */
public final class PerformanceUtil {

    private PerformanceUtil() { }

    /**
     * Suggested batch size for processing items/blocks in one iteration.
     * Prevents memory exhaustion in large modpacks.
     */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /**
     * Memory threshold (in MB) below which we should trigger garbage collection.
     */
    private static final long LOW_MEMORY_THRESHOLD_MB = 512;

    /**
     * Check if available memory is low and suggest garbage collection if needed.
     * This is a hint to the JVM and doesn't guarantee immediate collection.
     *
     * @param mod The mod instance for logging
     * @return true if memory was low and GC was suggested
     */
    public static boolean checkMemoryAndCleanup(IModBase mod) {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();

        long availableMemory = maxMemory - (totalMemory - freeMemory);
        long availableMB = availableMemory / (1024 * 1024);

        if (availableMB < LOW_MEMORY_THRESHOLD_MB) {
            if (mod != null) {
                try {
                    mod.log("Low memory detected (" + availableMB + " MB available), suggesting garbage collection");
                } catch (Throwable ignored) {
                }
            }
            System.gc();
            return true;
        }
        return false;
    }

    /**
     * Get memory usage statistics as a formatted string.
     * Useful for logging and debugging performance issues.
     *
     * @return Memory usage string in format "Used: X MB / Max: Y MB (Z% used)"
     */
    public static String getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        int percentUsed = (int) ((usedMemory * 100) / maxMemory);

        return String.format("Used: %d MB / Max: %d MB (%d%% used)", usedMemory, maxMemory, percentUsed);
    }

    /**
     * Calculate the optimal batch size based on available memory.
     * Reduces batch size when memory is constrained.
     *
     * @return Recommended batch size for current memory conditions
     */
    public static int getOptimalBatchSize() {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();

        long availableMemory = maxMemory - (totalMemory - freeMemory);
        long availableMB = availableMemory / (1024 * 1024);

        // Scale batch size based on available memory
        if (availableMB < 512) {
            return 25;  // Very low memory: small batches
        } else if (availableMB < 1024) {
            return 50;  // Low memory: medium batches
        } else if (availableMB < 2048) {
            return DEFAULT_BATCH_SIZE;  // Normal memory: default batches
        } else {
            return 200;  // High memory: large batches
        }
    }

    /**
     * Sleep for a short duration to yield CPU time.
     * Useful in tight loops to prevent CPU saturation.
     *
     * @param milliseconds Duration to sleep in milliseconds
     */
    public static void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Log progress information at regular intervals.
     * Prevents log spam while still providing useful feedback.
     *
     * @param current Current progress count
     * @param total Total items to process
     * @param mod The mod instance for logging
     * @param operationName Name of the operation (e.g., "Exporting items")
     */
    public static void logProgress(int current, int total, IModBase mod, String operationName) {
        // Log every 10%, but at least every 100 items, and always at start/end
        boolean shouldLog = current == 0
                         || current == total
                         || current % 100 == 0
                         || (current * 10 / total) != ((current - 1) * 10 / total);

        if (shouldLog && mod != null) {
            try {
                int percent = (current * 100) / total;
                mod.log(String.format("%s: %d/%d (%d%%) - %s",
                    operationName, current, total, percent, getMemoryStats()));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Check if we should pause exports due to low memory conditions.
     * This can be used in export loops to prevent out-of-memory errors.
     *
     * @param mod The mod instance for logging
     * @return true if exports should pause for memory cleanup
     */
    public static boolean shouldPauseForMemory(IModBase mod) {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();

        long availableMemory = maxMemory - (totalMemory - freeMemory);
        long availableMB = availableMemory / (1024 * 1024);

        // Pause if we have less than 256 MB available
        if (availableMB < 256) {
            if (mod != null) {
                try {
                    mod.log("Critical memory situation (" + availableMB + " MB), pausing exports for cleanup");
                } catch (Throwable ignored) {
                }
            }
            System.gc();
            pause(1000); // Wait 1 second for GC to complete
            return true;
        }
        return false;
    }
}
