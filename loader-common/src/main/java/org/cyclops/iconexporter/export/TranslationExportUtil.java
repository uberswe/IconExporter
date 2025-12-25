package org.cyclops.iconexporter.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import org.cyclops.cyclopscore.init.IModBase;

import java.io.File;
import java.io.FileWriter;

/**
 * Utility for exporting translation/language files.
 */
public final class TranslationExportUtil {

    private TranslationExportUtil() { }

    /**
     * Export the current language translations to JSON.
     * @param translationsDir Target directory for language files
     * @param mod The mod instance for error logging
     * @return true if export succeeded
     */
    public static boolean exportCurrentLanguage(File translationsDir, IModBase mod) {
        try {
            //noinspection ResultOfMethodCallIgnored
            translationsDir.mkdirs();

            String languageCode = Minecraft.getInstance().getLanguageManager().getSelected();
            File languageFile = new File(translationsDir, languageCode + ".json");

            JsonObject languageData = new JsonObject();

            // Add metadata
            JsonObject metadata = new JsonObject();
            metadata.addProperty("code", languageCode);
            languageData.add("_metadata", metadata);

            // In MC 1.21.1+, Language class no longer has a simple storage field
            // Instead, we need to build translations by querying all known keys
            // We'll export item/block translations which are the most useful
            try {
                Language currentLanguage = Language.getInstance();
                JsonObject translations = new JsonObject();

                // Export all item translations
                for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                    if (itemId != null) {
                        String translationKey = item.getDescriptionId();
                        String translatedName = currentLanguage.getOrDefault(translationKey);
                        // Only add if translation exists and differs from key
                        if (!translatedName.equals(translationKey)) {
                            translations.addProperty(translationKey, translatedName);
                        }
                    }
                }

                // Export all block translations
                for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                    net.minecraft.resources.ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                    if (blockId != null) {
                        String translationKey = block.getDescriptionId();
                        String translatedName = currentLanguage.getOrDefault(translationKey);
                        // Only add if translation exists and differs from key
                        if (!translatedName.equals(translationKey)) {
                            translations.addProperty(translationKey, translatedName);
                        }
                    }
                }

                // Export all entity type translations
                for (net.minecraft.world.entity.EntityType<?> entityType : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                    net.minecraft.resources.ResourceLocation entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                    if (entityId != null) {
                        String translationKey = entityType.getDescriptionId();
                        String translatedName = currentLanguage.getOrDefault(translationKey);
                        // Only add if translation exists and differs from key
                        if (!translatedName.equals(translationKey)) {
                            translations.addProperty(translationKey, translatedName);
                        }
                    }
                }

                languageData.add("translations", translations);

                if (mod != null) {
                    mod.log("Exported " + translations.size() + " translations using public API");
                }

            } catch (Exception e) {
                // If export fails, log error
                ErrorLogUtil.log("Could not export translations for " + languageCode + ": " + e.getMessage());
                if (mod != null) {
                    mod.log("Translation export error: " + e.getMessage());
                }
                e.printStackTrace();
            }

            // Write to file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(languageFile)) {
                writer.write(gson.toJson(languageData));
            }

            return true;
        } catch (Exception e) {
            ErrorLogUtil.log("Failed to export current language: " + e.getMessage());
            return false;
        }
    }

    /**
     * Export a simple translation key map for items and blocks only.
     * This is a lightweight alternative to full language exports.
     * @param translationsDir Target directory
     * @param mod The mod instance for error logging
     * @return Number of translation keys exported
     */
    public static int exportTranslationKeys(File translationsDir, IModBase mod) {
        int keyCount = 0;

        try {
            //noinspection ResultOfMethodCallIgnored
            translationsDir.mkdirs();

            File keysFile = new File(translationsDir, "translation_keys.json");
            JsonObject data = new JsonObject();

            Language language = Language.getInstance();

            // Build translation keys by querying all items and blocks
            try {
                JsonObject itemKeys = new JsonObject();
                JsonObject blockKeys = new JsonObject();

                // Export all item translations
                for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    String translationKey = item.getDescriptionId();
                    String translatedName = language.getOrDefault(translationKey);
                    if (!translatedName.equals(translationKey)) {
                        itemKeys.addProperty(translationKey, translatedName);
                        keyCount++;
                    }
                }

                // Export all block translations
                for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                    String translationKey = block.getDescriptionId();
                    String translatedName = language.getOrDefault(translationKey);
                    if (!translatedName.equals(translationKey)) {
                        blockKeys.addProperty(translationKey, translatedName);
                        keyCount++;
                    }
                }

                data.add("items", itemKeys);
                data.add("blocks", blockKeys);

                // Write to file
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (FileWriter writer = new FileWriter(keysFile)) {
                    writer.write(gson.toJson(data));
                }

            } catch (Exception e) {
                ErrorLogUtil.log("Failed to export translation keys: " + e.getMessage());
            }

        } catch (Exception e) {
            ErrorLogUtil.log("Failed to create translation keys file: " + e.getMessage());
        }

        return keyCount;
    }
}
