package org.cyclops.iconexporter.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.cyclops.cyclopscore.init.IModBase;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility for exporting 3D model JSON files from resource packs.
 * This exports the actual model definitions that define block/item geometry.
 */
public final class ModelExportUtil {

    private ModelExportUtil() { }

    /**
     * Export 3D models for all items.
     * @param destDir Target directory for models (e.g., models/items/)
     * @param mod The mod instance for logging
     * @return Number of models exported
     */
    public static int exportItemModels(File destDir, IModBase mod) {
        int count = 0;
        Set<ResourceLocation> exportedModels = new HashSet<>();

        int totalItems = BuiltInRegistries.ITEM.size();
        int processedItems = 0;

        for (Item item : BuiltInRegistries.ITEM) {
            processedItems++;

            // Check memory and cleanup if needed
            if (processedItems % 100 == 0) {
                PerformanceUtil.checkMemoryAndCleanup(mod);
            }
            try {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId == null) continue;

                // Get the item model
                ResourceLocation modelLocation = ResourceLocation.fromNamespaceAndPath(
                        itemId.getNamespace(),
                        "models/item/" + itemId.getPath() + ".json"
                );

                // Export this model and all parent models
                count += exportModelChain(modelLocation, destDir, exportedModels, mod);
            } catch (Exception e) {
                // Log but continue with other items
                if (mod != null) {
                    try {
                        mod.log("Failed to export model for item " + BuiltInRegistries.ITEM.getKey(item) + ": " + e.getMessage());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        return count;
    }

    /**
     * Export 3D models for all blocks.
     * @param destDir Target directory for models (e.g., models/blocks/)
     * @param mod The mod instance for logging
     * @return Number of models exported
     */
    public static int exportBlockModels(File destDir, IModBase mod) {
        int count = 0;
        Set<ResourceLocation> exportedModels = new HashSet<>();

        int totalBlocks = BuiltInRegistries.BLOCK.size();
        int processedBlocks = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            processedBlocks++;

            // Check memory and cleanup if needed
            if (processedBlocks % 100 == 0) {
                PerformanceUtil.checkMemoryAndCleanup(mod);
            }
            try {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
                if (blockId == null) continue;

                // Get the block model
                ResourceLocation modelLocation = ResourceLocation.fromNamespaceAndPath(
                        blockId.getNamespace(),
                        "models/block/" + blockId.getPath() + ".json"
                );

                // Export this model and all parent models
                count += exportModelChain(modelLocation, destDir, exportedModels, mod);
            } catch (Exception e) {
                // Log but continue with other blocks
                if (mod != null) {
                    try {
                        mod.log("Failed to export model for block " + BuiltInRegistries.BLOCK.getKey(block) + ": " + e.getMessage());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        return count;
    }

    /**
     * Export a model and all its parent models recursively.
     * @param modelLocation The model location (with models/ prefix)
     * @param destDir Target directory
     * @param exportedModels Set of already exported models
     * @param mod The mod instance for logging
     * @return Number of models exported
     */
    private static int exportModelChain(ResourceLocation modelLocation, File destDir, Set<ResourceLocation> exportedModels, IModBase mod) {
        int count = 0;

        // Skip if already exported
        if (exportedModels.contains(modelLocation)) {
            return 0;
        }

        try {
            // Try to load the model file
            Optional<Resource> modelResource = Minecraft.getInstance().getResourceManager()
                    .getResource(modelLocation);

            if (modelResource.isEmpty()) {
                return 0;
            }

            // Read and parse the model JSON
            JsonObject modelJson;
            try (InputStream inputStream = modelResource.get().open()) {
                modelJson = com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8)
                ).getAsJsonObject();
            }

            // Export the model file
            if (exportModelFile(modelLocation, modelJson, destDir, mod)) {
                exportedModels.add(modelLocation);
                count++;
            }

            // Check for parent model and export it recursively
            if (modelJson.has("parent")) {
                String parent = modelJson.get("parent").getAsString();
                try {
                    ResourceLocation parentLocation = ResourceLocation.parse(parent);
                    ResourceLocation parentModelLocation = ResourceLocation.fromNamespaceAndPath(
                            parentLocation.getNamespace(),
                            "models/" + parentLocation.getPath() + ".json"
                    );

                    count += exportModelChain(parentModelLocation, destDir, exportedModels, mod);
                } catch (Exception e) {
                    // Invalid parent reference, skip
                }
            }
        } catch (Exception e) {
            // Failed to load or parse model
        }

        return count;
    }

    /**
     * Export a single model JSON file to disk.
     * @param modelLocation The model resource location (with models/ prefix)
     * @param modelJson The model JSON object
     * @param destDir Target directory
     * @param mod The mod instance for logging
     * @return true if successfully exported
     */
    private static boolean exportModelFile(ResourceLocation modelLocation, JsonObject modelJson, File destDir, IModBase mod) {
        try {
            // Strip "models/" prefix from the path
            String path = modelLocation.getPath();
            if (path.startsWith("models/")) {
                path = path.substring("models/".length());
            }

            // Remove .json extension if present
            if (path.endsWith(".json")) {
                path = path.substring(0, path.length() - 5);
            }

            // Create output directory structure
            File namespaceDir = new File(destDir, modelLocation.getNamespace());
            namespaceDir.mkdirs();

            File outputFile;
            if (path.contains("/")) {
                String[] parts = path.split("/");
                File currentDir = namespaceDir;
                for (int i = 0; i < parts.length - 1; i++) {
                    currentDir = new File(currentDir, parts[i]);
                    currentDir.mkdirs();
                }
                outputFile = new File(currentDir, parts[parts.length - 1] + ".json");
            } else {
                outputFile = new File(namespaceDir, path + ".json");
            }

            // Write the model JSON with pretty printing
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(gson.toJson(modelJson));
            }

            return true;
        } catch (Exception e) {
            if (mod != null) {
                try {
                    mod.log("Failed to export model file " + modelLocation + ": " + e.getMessage());
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
    }
}
