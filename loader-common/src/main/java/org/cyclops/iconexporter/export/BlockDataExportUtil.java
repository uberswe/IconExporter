package org.cyclops.iconexporter.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.cyclops.cyclopscore.init.IModBase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Utility for exporting detailed block information to JSON files.
 */
public final class BlockDataExportUtil {

    private BlockDataExportUtil() { }

    /**
     * Export detailed information about a block to JSON.
     * @param destFile Target file for the block data
     * @param block The block to export data for
     * @param mod The mod instance for error logging
     * @throws IOException if file writing fails
     */
    public static void writeBlockDataJson(File destFile, Block block, IModBase mod) throws IOException {
        // Ensure parent directories exist
        File parent = destFile.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        JsonObject obj = new JsonObject();

        try {
            // Basic info
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            obj.addProperty("id", blockId.toString());
            obj.addProperty("namespace", blockId.getNamespace());
            obj.addProperty("path", blockId.getPath());

            // Translation/Display info
            try {
                String translationKey = block.getDescriptionId();
                obj.addProperty("translation_key", translationKey);

                // Get display name (translated to current language, usually English)
                ItemStack blockStack = new ItemStack(block);
                obj.addProperty("display_name", blockStack.getHoverName().getString());
            } catch (Exception e) {
                // Silently skip translation errors
            }

            // Get default block state for property inspection
            BlockState defaultState = block.defaultBlockState();

            // Physical properties
            obj.addProperty("explosion_resistance", block.getExplosionResistance());
            obj.addProperty("destroy_speed", block.defaultDestroyTime());
            obj.addProperty("friction", block.getFriction());
            obj.addProperty("speed_factor", block.getSpeedFactor());
            obj.addProperty("jump_factor", block.getJumpFactor());

            // Light properties
            obj.addProperty("light_emission", defaultState.getLightEmission());

            // Behavior flags
            obj.addProperty("has_collision", defaultState.blocksMotion());
            obj.addProperty("is_air", defaultState.isAir());

            // Properties that may require world context - wrap in try-catch
            try {
                obj.addProperty("is_solid_render", defaultState.isSolidRender(null, null));
            } catch (Exception e) {
                // Skip if solid render check requires world context
            }

            obj.addProperty("can_occlude", defaultState.canOcclude());
            obj.addProperty("requires_correct_tool", defaultState.requiresCorrectToolForDrops());

            // Sound type
            try {
                obj.addProperty("sound_type", defaultState.getSoundType().toString());
            } catch (Exception e) {
                // Skip if sound type requires world context
            }

            // Map color
            try {
                obj.addProperty("map_color", defaultState.getMapColor(null, null).toString());
            } catch (Exception e) {
                // Ignore if map color can't be determined
            }

            // Block states/properties
            if (!block.getStateDefinition().getProperties().isEmpty()) {
                JsonArray propertiesArray = new JsonArray();
                for (Property<?> property : block.getStateDefinition().getProperties()) {
                    JsonObject propObj = new JsonObject();
                    propObj.addProperty("name", property.getName());
                    propObj.addProperty("type", property.getClass().getSimpleName());

                    JsonArray valuesArray = new JsonArray();
                    for (Object value : property.getPossibleValues()) {
                        valuesArray.add(value.toString());
                    }
                    propObj.add("possible_values", valuesArray);

                    propertiesArray.add(propObj);
                }
                obj.add("properties", propertiesArray);
            }

            // Check for waterloggable
            boolean hasWaterlogged = block.getStateDefinition().getProperties().stream()
                    .anyMatch(prop -> prop.getName().equals("waterlogged"));
            obj.addProperty("is_waterloggable", hasWaterlogged);

            // Tags
            JsonArray tagsArray = new JsonArray();
            for (TagKey<Block> tag : block.builtInRegistryHolder().tags().toList()) {
                tagsArray.add(tag.location().toString());
            }
            if (tagsArray.size() > 0) {
                obj.add("tags", tagsArray);
            }

            // Block states count
            obj.addProperty("possible_states_count", block.getStateDefinition().getPossibleStates().size());

            // Default state properties
            if (!defaultState.getValues().isEmpty()) {
                JsonObject defaultStateObj = new JsonObject();
                for (Map.Entry<Property<?>, Comparable<?>> entry : defaultState.getValues().entrySet()) {
                    defaultStateObj.addProperty(entry.getKey().getName(), entry.getValue().toString());
                }
                obj.add("default_state", defaultStateObj);
            }

        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export block data for " + BuiltInRegistries.BLOCK.getKey(block) + ": " + e.getMessage());
        }

        // Write to file
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(destFile)) {
            writer.write(gson.toJson(obj));
        } catch (IOException e) {
            if (mod != null) {
                try {
                    mod.log("Failed to write block data JSON: " + e.getMessage());
                } catch (Throwable ignored) {
                    // Ignore logging failures
                }
            }
            throw e;
        }
    }
}
