package org.cyclops.iconexporter.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.cyclops.cyclopscore.init.IModBase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Utility for exporting detailed item information to JSON files.
 */
public final class ItemDataExportUtil {

    private ItemDataExportUtil() { }

    /**
     * Export detailed information about an item to JSON.
     * @param destFile Target file for the item data
     * @param item The item to export data for
     * @param mod The mod instance for error logging
     * @throws IOException if file writing fails
     */
    public static void writeItemDataJson(File destFile, Item item, IModBase mod) throws IOException {
        // Ensure parent directories exist
        File parent = destFile.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        JsonObject obj = new JsonObject();

        try {
            // Basic info
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            obj.addProperty("id", itemId.toString());
            obj.addProperty("namespace", itemId.getNamespace());
            obj.addProperty("path", itemId.getPath());

            // Create item stack to inspect properties
            ItemStack stack = new ItemStack(item);

            // Translation/Display info
            try {
                String translationKey = item.getDescriptionId();
                obj.addProperty("translation_key", translationKey);

                // Get display name (translated to current language, usually English)
                Component displayName = stack.getHoverName();
                obj.addProperty("display_name", displayName.getString());

                // Get tooltip lines with both translation key and text
                List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
                if (tooltip.size() > 1) { // First line is always the name, so check if there are additional lines
                    JsonArray tooltipArray = new JsonArray();
                    for (int i = 1; i < tooltip.size(); i++) { // Skip first line (name)
                        Component tooltipComponent = tooltip.get(i);
                        JsonObject tooltipObj = new JsonObject();

                        // Get the translated text
                        String text = tooltipComponent.getString();
                        tooltipObj.addProperty("text", text);

                        // Try to extract the translation key if this is a translatable component
                        try {
                            // Use reflection to check if this is a TranslatableContents
                            var contentsMethod = tooltipComponent.getClass().getMethod("getContents");
                            Object contents = contentsMethod.invoke(tooltipComponent);

                            if (contents != null) {
                                String contentsClassName = contents.getClass().getSimpleName();
                                if (contentsClassName.contains("Translatable")) {
                                    // Try to get the key field
                                    var keyField = contents.getClass().getDeclaredField("key");
                                    keyField.setAccessible(true);
                                    String key = (String) keyField.get(contents);
                                    if (key != null && !key.isEmpty()) {
                                        tooltipObj.addProperty("key", key);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // If we can't get the key, that's okay - we still have the text
                        }

                        tooltipArray.add(tooltipObj);
                    }
                    if (tooltipArray.size() > 0) {
                        obj.add("tooltip", tooltipArray);
                    }
                }
            } catch (Exception e) {
                // Silently skip translation errors
            }

            // Max stack size
            obj.addProperty("max_stack_size", stack.getMaxStackSize());

            // Durability
            int maxDamage = stack.getMaxDamage();
            if (maxDamage > 0) {
                obj.addProperty("max_damage", maxDamage);
                obj.addProperty("durability", maxDamage);
                obj.addProperty("is_damageable", true);
            } else {
                obj.addProperty("is_damageable", false);
            }

            // Enchantability
            obj.addProperty("enchantment_value", item.getEnchantmentValue());

            // Fireproof
            obj.addProperty("is_fireproof", item.components().has(DataComponents.FIRE_RESISTANT));

            // Rarity
            obj.addProperty("rarity", stack.getRarity().name().toLowerCase());

            // Food properties
            if (item.components().has(DataComponents.FOOD)) {
                FoodProperties food = item.components().get(DataComponents.FOOD);
                if (food != null) {
                    JsonObject foodObj = new JsonObject();
                    foodObj.addProperty("nutrition", food.nutrition());
                    foodObj.addProperty("saturation_modifier", food.saturation());
                    foodObj.addProperty("can_always_eat", food.canAlwaysEat());
                    foodObj.addProperty("eat_seconds", food.eatSeconds());
                    obj.add("food", foodObj);
                }
            }

            // Attribute modifiers (attack damage, attack speed, etc.)
            if (item.components().has(DataComponents.ATTRIBUTE_MODIFIERS)) {
                ItemAttributeModifiers attributeModifiers = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (attributeModifiers != null) {
                    JsonArray modifiersArray = new JsonArray();
                    for (ItemAttributeModifiers.Entry entry : attributeModifiers.modifiers()) {
                        JsonObject modObj = new JsonObject();
                        Holder<Attribute> attributeHolder = entry.attribute();
                        AttributeModifier modifier = entry.modifier();

                        // Get attribute name
                        ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(attributeHolder.value());
                        if (attrId != null) {
                            modObj.addProperty("attribute", attrId.toString());
                            modObj.addProperty("attribute_name", attrId.getPath());
                        }

                        modObj.addProperty("amount", modifier.amount());
                        modObj.addProperty("operation", modifier.operation().name().toLowerCase());
                        modObj.addProperty("slot", entry.slot().toString());

                        modifiersArray.add(modObj);
                    }
                    if (modifiersArray.size() > 0) {
                        obj.add("attribute_modifiers", modifiersArray);
                    }
                }
            }

            // Tags
            JsonArray tagsArray = new JsonArray();
            for (TagKey<Item> tag : item.builtInRegistryHolder().tags().toList()) {
                tagsArray.add(tag.location().toString());
            }
            if (tagsArray.size() > 0) {
                obj.add("tags", tagsArray);
            }

            // Craftable
            obj.addProperty("is_complex", item.isComplex());

        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export item data for " + BuiltInRegistries.ITEM.getKey(item) + ": " + e.getMessage());
        }

        // Write to file
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(destFile)) {
            writer.write(gson.toJson(obj));
        } catch (IOException e) {
            if (mod != null) {
                try {
                    mod.log("Failed to write item data JSON: " + e.getMessage());
                } catch (Throwable ignored) {
                    // Ignore logging failures
                }
            }
            throw e;
        }
    }
}
