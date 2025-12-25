package org.cyclops.iconexporter.export;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.cyclops.cyclopscore.init.IModBase;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility for extracting raw texture files from resource packs.
 */
public final class TextureExportUtil {

    private TextureExportUtil() { }

    /**
     * Export raw textures for all items.
     * @param destDir Target directory for textures (e.g., textures/items/)
     * @param mod The mod instance for logging
     * @return Number of textures exported
     */
    public static int exportItemTextures(File destDir, IModBase mod) {
        int count = 0;
        Set<ResourceLocation> exportedTextures = new HashSet<>();

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

                // Try to load the model file
                Optional<Resource> modelResource = Minecraft.getInstance().getResourceManager()
                        .getResource(modelLocation);

                if (modelResource.isPresent()) {
                    // Parse model JSON and extract textures
                    Set<ResourceLocation> textures = extractTexturesFromModel(modelResource.get());

                    for (ResourceLocation textureLocation : textures) {
                        if (exportedTextures.contains(textureLocation)) {
                            continue; // Already exported
                        }

                        // Export the texture with normalized path (always include /item/ subdirectory)
                        if (exportTexture(textureLocation, destDir, "item", mod)) {
                            exportedTextures.add(textureLocation);
                            count++;
                        }
                    }
                }
            } catch (Exception e) {
                // Log but continue with other items
                if (mod != null) {
                    try {
                        mod.log("Failed to export texture for item " + BuiltInRegistries.ITEM.getKey(item) + ": " + e.getMessage());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        return count;
    }

    /**
     * Export raw textures for all blocks.
     * @param destDir Target directory for textures (e.g., textures/blocks/)
     * @param mod The mod instance for logging
     * @return Number of textures exported
     */
    public static int exportBlockTextures(File destDir, IModBase mod) {
        int count = 0;
        Set<ResourceLocation> exportedTextures = new HashSet<>();

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

                // Try to load the model file
                Optional<Resource> modelResource = Minecraft.getInstance().getResourceManager()
                        .getResource(modelLocation);

                if (modelResource.isPresent()) {
                    // Parse model JSON and extract textures
                    Set<ResourceLocation> textures = extractTexturesFromModel(modelResource.get());

                    for (ResourceLocation textureLocation : textures) {
                        if (exportedTextures.contains(textureLocation)) {
                            continue; // Already exported
                        }

                        // Export the texture with normalized path (always include /block/ subdirectory)
                        if (exportTexture(textureLocation, destDir, "block", mod)) {
                            exportedTextures.add(textureLocation);
                            count++;
                        }
                    }
                }
            } catch (Exception e) {
                // Log but continue with other blocks
                if (mod != null) {
                    try {
                        mod.log("Failed to export texture for block " + BuiltInRegistries.BLOCK.getKey(block) + ": " + e.getMessage());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        return count;
    }

    /**
     * Extract texture references from a model JSON file.
     * @param modelResource The model resource
     * @return Set of texture locations
     */
    private static Set<ResourceLocation> extractTexturesFromModel(Resource modelResource) {
        Set<ResourceLocation> textures = new HashSet<>();

        try (InputStream inputStream = modelResource.open()) {
            // Parse the model JSON
            JsonObject modelJson = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8)
            ).getAsJsonObject();

            // Check for parent model (we need to follow the chain)
            if (modelJson.has("parent")) {
                String parent = modelJson.get("parent").getAsString();
                ResourceLocation parentLocation = ResourceLocation.parse(parent);
                ResourceLocation parentModelLocation = ResourceLocation.fromNamespaceAndPath(
                        parentLocation.getNamespace(),
                        "models/" + parentLocation.getPath() + ".json"
                );

                Optional<Resource> parentResource = Minecraft.getInstance().getResourceManager()
                        .getResource(parentModelLocation);

                if (parentResource.isPresent()) {
                    textures.addAll(extractTexturesFromModel(parentResource.get()));
                }
            }

            // Extract textures from this model
            if (modelJson.has("textures")) {
                JsonObject texturesObj = modelJson.getAsJsonObject("textures");
                for (String key : texturesObj.keySet()) {
                    JsonElement textureElement = texturesObj.get(key);
                    if (textureElement.isJsonPrimitive()) {
                        String texturePath = textureElement.getAsString();
                        // Skip texture variables (e.g., "#layer0")
                        if (!texturePath.startsWith("#")) {
                            try {
                                ResourceLocation textureLocation = ResourceLocation.parse(texturePath);
                                textures.add(textureLocation);
                            } catch (Exception e) {
                                // Invalid resource location, skip
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Failed to parse model, return empty set
        }

        return textures;
    }

    /**
     * Export a single texture to disk with normalized path structure.
     * @param textureLocation The texture resource location (without .png extension)
     * @param destDir Target directory
     * @param subdirectory The subdirectory to normalize to ("item" or "block")
     * @param mod The mod instance for logging
     * @return true if successfully exported
     */
    private static boolean exportTexture(ResourceLocation textureLocation, File destDir, String subdirectory, IModBase mod) {
        try {
            // Convert texture location to actual file path
            ResourceLocation textureFile = ResourceLocation.fromNamespaceAndPath(
                    textureLocation.getNamespace(),
                    "textures/" + textureLocation.getPath() + ".png"
            );

            // Try to load the texture file
            Optional<Resource> textureResource = Minecraft.getInstance().getResourceManager()
                    .getResource(textureFile);

            if (textureResource.isEmpty()) {
                return false;
            }

            // Read the texture as NativeImage
            try (InputStream inputStream = textureResource.get().open()) {
                NativeImage image = NativeImage.read(inputStream);

                // Normalize the path to always include the subdirectory (item/block)
                String path = textureLocation.getPath();

                // Remove existing item/ or block/ prefix if present
                if (path.startsWith("item/")) {
                    path = path.substring(5);
                } else if (path.startsWith("block/")) {
                    path = path.substring(6);
                }

                // Create output directory structure with normalized path
                // Structure: destDir/namespace/subdirectory/path.png
                File namespaceDir = new File(destDir, textureLocation.getNamespace());
                File subdirectoryDir = new File(namespaceDir, subdirectory);
                subdirectoryDir.mkdirs();

                File outputFile;
                if (path.contains("/")) {
                    String[] parts = path.split("/");
                    File currentDir = subdirectoryDir;
                    for (int i = 0; i < parts.length - 1; i++) {
                        currentDir = new File(currentDir, parts[i]);
                        currentDir.mkdirs();
                    }
                    outputFile = new File(currentDir, parts[parts.length - 1] + ".png");
                } else {
                    outputFile = new File(subdirectoryDir, path + ".png");
                }

                // Write the image
                image.writeToFile(outputFile);
                image.close();

                return true;
            }
        } catch (Exception e) {
            if (mod != null) {
                try {
                    mod.log("Failed to export texture " + textureLocation + ": " + e.getMessage());
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
    }
}
