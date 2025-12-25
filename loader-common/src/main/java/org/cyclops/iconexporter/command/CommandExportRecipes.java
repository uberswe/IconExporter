package org.cyclops.iconexporter.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.SharedConstants;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.export.EnvironmentExportUtil;
import org.cyclops.iconexporter.export.ErrorLogUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Command to export all recipes from RecipeManager to JSON files.
 */
public class CommandExportRecipes implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandExportRecipes(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        this.context = context;
        this.mod = mod;
        this.helpers = helpers;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Clear previous errors
        ErrorLogUtil.clear();

        String modpackName = GeneralConfig.modpackName;
        String mcVersion;
        try {
            mcVersion = SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            mcVersion = "unknown";
        }
        String loader = detectLoader();

        int scale = GeneralConfig.defaultScale;
        File gameDir = Minecraft.getInstance().gameDirectory;
        File recipesDir = EnvironmentExportUtil.resolveRecipesDir(gameDir, scale, modpackName, mcVersion, loader);

        // Get recipe manager
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            mc.player.sendSystemMessage(Component.literal("Cannot export recipes: No world loaded"));
            return 0;
        }

        RecipeManager recipeManager = mc.level.getRecipeManager();
        RegistryAccess registryAccess = mc.level.registryAccess();

        int successCount = 0;
        int errorCount = 0;
        int reflectionFallbackCount = 0;
        java.util.Map<String, Integer> fallbackCountsByType = new java.util.HashMap<>();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Export all recipes
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            try {
                ResourceLocation id = recipeHolder.id();
                Recipe<?> recipe = recipeHolder.value();

                // Create namespace directory
                File namespaceDir = new File(recipesDir, id.getNamespace());
                //noinspection ResultOfMethodCallIgnored
                namespaceDir.mkdirs();

                // Handle nested paths (e.g., "minecraft:food/bread" -> "food/bread.json")
                String path = id.getPath();
                File recipeFile;
                if (path.contains("/")) {
                    // Create subdirectories for nested paths
                    String[] parts = path.split("/");
                    File currentDir = namespaceDir;
                    for (int i = 0; i < parts.length - 1; i++) {
                        currentDir = new File(currentDir, parts[i]);
                        //noinspection ResultOfMethodCallIgnored
                        currentDir.mkdirs();
                    }
                    recipeFile = new File(currentDir, parts[parts.length - 1] + ".json");
                } else {
                    recipeFile = new File(namespaceDir, path + ".json");
                }

                JsonElement jsonElement = null;

                // Try multiple serialization approaches
                boolean serialized = false;
                var serializer = recipe.getSerializer();
                var ops = registryAccess.createSerializationContext(JsonOps.INSTANCE);

                // Approach 1: Try MapCodec.codec() (Minecraft 1.21+ standard)
                try {
                    var codecMethod = serializer.getClass().getMethod("codec");
                    Object codecObj = codecMethod.invoke(serializer);

                    // Check if it's a MapCodec
                    if (codecObj instanceof com.mojang.serialization.MapCodec) {
                        @SuppressWarnings("unchecked")
                        var mapCodec = (com.mojang.serialization.MapCodec<Object>) codecObj;
                        var fullCodec = mapCodec.codec();

                        var encodeResult = fullCodec.encodeStart(ops, recipe);
                        if (encodeResult.isSuccess()) {
                            JsonObject recipeJson = encodeResult.getOrThrow().getAsJsonObject();

                            // Add the type field if not present
                            if (!recipeJson.has("type")) {
                                recipeJson.addProperty("type", BuiltInRegistries.RECIPE_SERIALIZER
                                    .getKey(serializer).toString());
                            }

                            jsonElement = recipeJson;
                            serialized = true;
                        }
                    }
                    // If it's already a Codec, use it directly
                    else if (codecObj instanceof com.mojang.serialization.Codec) {
                        @SuppressWarnings("unchecked")
                        var codec = (com.mojang.serialization.Codec<Object>) codecObj;

                        var encodeResult = codec.encodeStart(ops, recipe);
                        if (encodeResult.isSuccess()) {
                            JsonObject recipeJson = encodeResult.getOrThrow().getAsJsonObject();

                            // Add the type field if not present
                            if (!recipeJson.has("type")) {
                                recipeJson.addProperty("type", BuiltInRegistries.RECIPE_SERIALIZER
                                    .getKey(serializer).toString());
                            }

                            jsonElement = recipeJson;
                            serialized = true;
                        }
                    }
                } catch (Exception e1) {
                    // Approach 1 failed, will try approach 2
                }

                // Approach 2: Try RecipeSerializer.toJson() if it exists (legacy/custom recipes)
                if (!serialized) {
                    try {
                        var toJsonMethod = serializer.getClass().getMethod("toJson", JsonObject.class, Object.class);
                        JsonObject recipeJson = new JsonObject();
                        toJsonMethod.invoke(serializer, recipeJson, recipe);

                        // Add the type field if not present
                        if (!recipeJson.has("type")) {
                            recipeJson.addProperty("type", BuiltInRegistries.RECIPE_SERIALIZER
                                .getKey(serializer).toString());
                        }

                        jsonElement = recipeJson;
                        serialized = true;
                    } catch (Exception e2) {
                        // Approach 2 failed, will use fallback
                    }
                }

                // Fallback: Create recipe data using reflection
                if (!serialized) {
                    reflectionFallbackCount++;

                    JsonObject fallbackJson = new JsonObject();
                    fallbackJson.addProperty("id", id.toString());

                    String recipeType;
                    try {
                        recipeType = BuiltInRegistries.RECIPE_SERIALIZER
                            .getKey(serializer).toString();
                        fallbackJson.addProperty("type", recipeType);
                    } catch (Exception e) {
                        recipeType = "unknown";
                        fallbackJson.addProperty("type", "unknown");
                    }

                    // Track fallback count by recipe type
                    fallbackCountsByType.merge(recipeType, 1, Integer::sum);

                    fallbackJson.addProperty("_export_note", "Recipe data extracted via reflection - codec serialization failed");

                    // Try to get result item
                    try {
                        var result = recipe.getResultItem(registryAccess);
                        if (!result.isEmpty()) {
                            JsonObject resultObj = new JsonObject();
                            resultObj.addProperty("id", BuiltInRegistries.ITEM
                                .getKey(result.getItem()).toString());
                            resultObj.addProperty("count", result.getCount());
                            fallbackJson.add("result", resultObj);
                        }
                    } catch (Exception ignored) {
                    }

                    // Extract ingredients using reflection
                    extractIngredientsViaReflection(fallbackJson, recipe);

                    // If this is a ShapedRecipe, extract pattern/key data
                    if (recipe instanceof ShapedRecipe shapedRecipe) {
                        extractShapedRecipeData(fallbackJson, shapedRecipe);
                    }

                    jsonElement = fallbackJson;
                }

                // Enhance shaped recipes with pattern/key data and grid dimensions
                if (jsonElement != null && jsonElement.isJsonObject()) {
                    JsonObject recipeJson = jsonElement.getAsJsonObject();

                    // Add source information (mod/datapack that adds this recipe)
                    addSourceInformation(recipeJson, id, helpers);

                    // Check if this is a shaped recipe and enhance it
                    if (recipe instanceof ShapedRecipe shapedRecipe) {
                        enhanceShapedRecipe(recipeJson, shapedRecipe, registryAccess);
                    } else {
                        // For custom recipe types, try to detect grid dimensions via reflection
                        detectGridDimensions(recipeJson, recipe);
                    }

                    // Enhance all ingredients with resolved items (for tag-based ingredients)
                    enhanceIngredientsWithResolvedItems(recipeJson, recipe);
                }

                // Write to file
                if (jsonElement != null) {
                    try (FileWriter writer = new FileWriter(recipeFile)) {
                        writer.write(gson.toJson(jsonElement));
                    }
                } else {
                    // This should never happen, but log it if it does
                    ErrorLogUtil.log("Recipe serialization failed completely for: " + id);
                }

                successCount++;
            } catch (Exception e) {
                errorCount++;
                ErrorLogUtil.log("Failed to export recipe " + recipeHolder.id() + ": " + e.getMessage());
            }
        }

        // Write error log if any errors occurred
        try {
            if (GeneralConfig.useStructuredOutput && errorCount > 0) {
                File structuredRoot = EnvironmentExportUtil.resolveStructuredRoot(gameDir, modpackName, mcVersion, loader);
                ErrorLogUtil.write(structuredRoot);
            }
        } catch (IOException e) {
            // Ignore errors writing error log
        }

        // Write recipe serializer summary report
        if (reflectionFallbackCount > 0) {
            try {
                File structuredRoot = EnvironmentExportUtil.resolveStructuredRoot(gameDir, modpackName, mcVersion, loader);
                File logsDir = new File(structuredRoot, "logs");
                //noinspection ResultOfMethodCallIgnored
                logsDir.mkdirs();
                File summaryFile = new File(logsDir, "recipe_serializer_summary.txt");

                try (FileWriter writer = new FileWriter(summaryFile)) {
                    writer.write("Recipe Serializer Fallback Summary\n");
                    writer.write("===================================\n\n");
                    writer.write("Total recipes exported: " + successCount + "\n");
                    writer.write("Recipes using reflection fallback: " + reflectionFallbackCount + "\n");
                    writer.write("Percentage using fallback: " + String.format("%.2f", (reflectionFallbackCount * 100.0 / successCount)) + "%\n\n");
                    writer.write("Fallback counts by recipe type:\n");
                    writer.write("--------------------------------\n");

                    // Sort by count (descending)
                    fallbackCountsByType.entrySet().stream()
                        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                        .forEach(entry -> {
                            try {
                                writer.write(String.format("  %-50s : %6d recipes\n", entry.getKey(), entry.getValue()));
                            } catch (IOException e) {
                                // Ignore write errors
                            }
                        });

                    writer.write("\nNote: Recipes using reflection fallback have full ingredient and pattern data\n");
                    writer.write("extracted via reflection, but codec-based serialization failed. This is\n");
                    writer.write("normal for custom mod recipe types that don't support standard serialization.\n");
                }
            } catch (IOException e) {
                // Ignore errors writing summary
            }
        }

        // Send feedback to player
        String feedbackMessage;
        if (errorCount == 0) {
            if (reflectionFallbackCount > 0) {
                feedbackMessage = String.format("Exported %d recipes (%d used reflection fallback). See logs/recipe_serializer_summary.txt",
                    successCount, reflectionFallbackCount);
            } else {
                feedbackMessage = "Exported " + successCount + " recipes to: " + recipesDir.getAbsolutePath();
            }
        } else {
            if (reflectionFallbackCount > 0) {
                feedbackMessage = String.format("Exported %d recipes (%d errors, %d reflection fallback). Check logs.",
                    successCount, errorCount, reflectionFallbackCount);
            } else {
                feedbackMessage = "Exported " + successCount + " recipes (" + errorCount + " errors). Check error log.";
            }
        }
        mc.player.sendSystemMessage(Component.literal(feedbackMessage));

        return 0;
    }

    private static String detectLoader() {
        if (classExists("net.fabricmc.loader.api.FabricLoader")) return "fabric";
        if (classExists("net.neoforged.fml.loading.FMLLoader")) return "neoforge";
        if (classExists("net.minecraftforge.fml.loading.FMLLoader")) return "forge";
        return "unknown";
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Add source information (mod/datapack) to the recipe JSON.
     */
    private static void addSourceInformation(JsonObject recipeJson, ResourceLocation recipeId, IIconExporterHelpers helpers) {
        try {
            String namespace = recipeId.getNamespace();

            // Add the namespace as the source
            recipeJson.addProperty("source_namespace", namespace);

            // Try to determine if this is from a mod or datapack
            String sourceType = "unknown";
            String sourceName = namespace;

            // Check if namespace matches a known mod
            if (helpers != null) {
                try {
                    var modList = helpers.getModList();
                    for (var modInfo : modList) {
                        if (modInfo.modId.equals(namespace)) {
                            sourceType = "mod";
                            sourceName = modInfo.displayName;
                            recipeJson.addProperty("source_mod_id", modInfo.modId);
                            recipeJson.addProperty("source_mod_name", modInfo.displayName);
                            recipeJson.addProperty("source_mod_version", modInfo.version);
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Failed to check mods, continue
                }

                // If not a mod, check datapacks
                if (sourceType.equals("unknown")) {
                    try {
                        var datapackList = helpers.getDatapackList();
                        for (var datapackInfo : datapackList) {
                            // Datapacks often use the namespace in their ID
                            if (datapackInfo.id.contains(namespace) || namespace.equals("minecraft")) {
                                sourceType = "datapack";
                                sourceName = datapackInfo.id;
                                recipeJson.addProperty("source_datapack_id", datapackInfo.id);
                                recipeJson.addProperty("source_datapack_description", datapackInfo.description);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // Failed to check datapacks, continue
                    }
                }
            }

            // Add the determined source type
            recipeJson.addProperty("source_type", sourceType);
            recipeJson.addProperty("source_name", sourceName);

        } catch (Exception e) {
            // Failed to add source info, continue without it
        }
    }

    /**
     * Enhance shaped recipe JSON with pattern and key fields if not already present.
     */
    private static void enhanceShapedRecipe(JsonObject recipeJson, ShapedRecipe shapedRecipe, RegistryAccess registryAccess) {
        try {
            // Add grid dimensions
            int width = shapedRecipe.getWidth();
            int height = shapedRecipe.getHeight();
            recipeJson.addProperty("grid_width", width);
            recipeJson.addProperty("grid_height", height);

            // The pattern and key data should already be in the JSON from codec serialization
            // If for some reason they're missing, we can try to reconstruct them using reflection
            if (!recipeJson.has("pattern") || !recipeJson.has("key")) {
                // Try to get ingredients via reflection to build pattern
                try {
                    var ingredientsField = ShapedRecipe.class.getDeclaredField("ingredients");
                    ingredientsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var ingredients = (java.util.List<Ingredient>) ingredientsField.get(shapedRecipe);

                    if (ingredients != null && !ingredients.isEmpty()) {
                        // Build pattern array
                        com.google.gson.JsonArray patternArray = new com.google.gson.JsonArray();
                        java.util.Map<Ingredient, Character> ingredientToChar = new java.util.HashMap<>();
                        char nextChar = 'A';

                        for (int row = 0; row < height; row++) {
                            StringBuilder rowPattern = new StringBuilder();
                            for (int col = 0; col < width; col++) {
                                int index = row * width + col;
                                if (index < ingredients.size()) {
                                    Ingredient ingredient = ingredients.get(index);
                                    if (!ingredient.isEmpty()) {
                                        // Assign or reuse character for this ingredient
                                        if (!ingredientToChar.containsKey(ingredient)) {
                                            ingredientToChar.put(ingredient, nextChar++);
                                        }
                                        rowPattern.append(ingredientToChar.get(ingredient));
                                    } else {
                                        rowPattern.append(' ');
                                    }
                                } else {
                                    rowPattern.append(' ');
                                }
                            }
                            patternArray.add(rowPattern.toString());
                        }

                        // Build key object
                        JsonObject keyObject = new JsonObject();
                        for (java.util.Map.Entry<Ingredient, Character> entry : ingredientToChar.entrySet()) {
                            Ingredient ingredient = entry.getKey();
                            char symbol = entry.getValue();
                            JsonObject ingredientObj = new JsonObject();

                            // Get all matching items
                            ItemStack[] items = ingredient.getItems();
                            if (items.length > 0) {
                                // Add the first item as the primary one
                                String itemId = BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString();
                                ingredientObj.addProperty("item", itemId);

                                // If there are multiple items, add them as resolved_items
                                if (items.length > 1) {
                                    com.google.gson.JsonArray resolvedItems = new com.google.gson.JsonArray();
                                    for (ItemStack stack : items) {
                                        String resolvedId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                        resolvedItems.add(resolvedId);
                                    }
                                    ingredientObj.add("resolved_items", resolvedItems);
                                }
                            }

                            keyObject.add(String.valueOf(symbol), ingredientObj);
                        }

                        // Only add if we don't have them already
                        if (!recipeJson.has("pattern")) {
                            recipeJson.add("pattern", patternArray);
                        }
                        if (!recipeJson.has("key")) {
                            recipeJson.add("key", keyObject);
                        }
                    }
                } catch (Exception ignored) {
                    // Reflection failed, that's okay - codec should have handled it
                }
            }
        } catch (Exception e) {
            // Failed to enhance, continue with original JSON
        }
    }

    /**
     * Enhance ingredients in the recipe JSON with resolved_items arrays for tag-based ingredients.
     */
    private static void enhanceIngredientsWithResolvedItems(JsonObject recipeJson, Recipe<?> recipe) {
        try {
            // Try to get ingredients from the recipe
            java.util.List<Ingredient> ingredients = new java.util.ArrayList<>();

            // Try reflection to get ingredients field
            try {
                var ingredientsField = recipe.getClass().getDeclaredField("ingredients");
                ingredientsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                var ingredientsList = (java.util.List<Ingredient>) ingredientsField.get(recipe);
                if (ingredientsList != null) {
                    ingredients.addAll(ingredientsList);
                }
            } catch (Exception e) {
                // No ingredients field, try other approaches
            }

            // If we found ingredients, enhance them in the JSON
            if (!ingredients.isEmpty() && recipeJson.has("ingredients")) {
                enhanceJsonIngredients(recipeJson.get("ingredients"), ingredients);
            }

            // Also check for key-based ingredients (shaped recipes)
            if (recipeJson.has("key")) {
                var keyObj = recipeJson.getAsJsonObject("key");
                for (String key : keyObj.keySet()) {
                    var ingredientObj = keyObj.get(key);
                    if (ingredientObj.isJsonObject()) {
                        enhanceJsonIngredient(ingredientObj.getAsJsonObject(), ingredients);
                    }
                }
            }
        } catch (Exception e) {
            // Failed to enhance ingredients, that's okay
        }
    }

    /**
     * Enhance a JSON ingredients array or object with resolved_items.
     */
    private static void enhanceJsonIngredients(JsonElement ingredientsJson, java.util.List<Ingredient> ingredients) {
        if (ingredientsJson.isJsonArray()) {
            var ingredientsArray = ingredientsJson.getAsJsonArray();
            for (int i = 0; i < Math.min(ingredientsArray.size(), ingredients.size()); i++) {
                var ingredientJson = ingredientsArray.get(i);
                if (ingredientJson.isJsonObject()) {
                    enhanceJsonIngredient(ingredientJson.getAsJsonObject(), ingredients.subList(i, i + 1));
                }
            }
        }
    }

    /**
     * Enhance a single JSON ingredient with resolved_items.
     */
    private static void enhanceJsonIngredient(JsonObject ingredientJson, java.util.List<Ingredient> ingredients) {
        try {
            // Find an ingredient that matches
            for (Ingredient ingredient : ingredients) {
                ItemStack[] items = ingredient.getItems();
                if (items.length > 1) {
                    // This is likely a tag-based ingredient, add resolved items
                    if (!ingredientJson.has("resolved_items")) {
                        com.google.gson.JsonArray resolvedItems = new com.google.gson.JsonArray();
                        for (ItemStack stack : items) {
                            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                            resolvedItems.add(itemId);
                        }
                        ingredientJson.add("resolved_items", resolvedItems);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Failed to enhance ingredient, continue
        }
    }

    /**
     * Extract ingredients from recipe using reflection (for recipes that fail codec serialization).
     */
    private static void extractIngredientsViaReflection(JsonObject recipeJson, Recipe<?> recipe) {
        try {
            // Try multiple common field names for ingredients
            String[] ingredientFieldNames = {"ingredients", "ingredient", "input", "inputs"};

            for (String fieldName : ingredientFieldNames) {
                try {
                    var field = recipe.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object ingredientsObj = field.get(recipe);

                    // Handle List<Ingredient>
                    if (ingredientsObj instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        var ingredientsList = (java.util.List<Ingredient>) ingredientsObj;
                        if (!ingredientsList.isEmpty()) {
                            com.google.gson.JsonArray ingredientsArray = new com.google.gson.JsonArray();

                            for (Ingredient ingredient : ingredientsList) {
                                if (!ingredient.isEmpty()) {
                                    JsonObject ingredientObj = serializeIngredient(ingredient);
                                    ingredientsArray.add(ingredientObj);
                                }
                            }

                            if (ingredientsArray.size() > 0) {
                                recipeJson.add("ingredients", ingredientsArray);
                                return; // Found ingredients, stop searching
                            }
                        }
                    }
                    // Handle single Ingredient
                    else if (ingredientsObj instanceof Ingredient) {
                        Ingredient ingredient = (Ingredient) ingredientsObj;
                        if (!ingredient.isEmpty()) {
                            recipeJson.add("ingredient", serializeIngredient(ingredient));
                            return; // Found ingredient, stop searching
                        }
                    }
                } catch (Exception ignored) {
                    // Field not found or wrong type, try next field name
                }
            }
        } catch (Exception e) {
            // Failed to extract ingredients via reflection
        }
    }

    /**
     * Serialize an Ingredient to JSON.
     */
    private static JsonObject serializeIngredient(Ingredient ingredient) {
        JsonObject ingredientObj = new JsonObject();
        ItemStack[] items = ingredient.getItems();

        if (items.length > 0) {
            // Add the first item as the primary one
            String itemId = BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString();
            ingredientObj.addProperty("item", itemId);

            // If there are multiple items, it's likely a tag - add all resolved items
            if (items.length > 1) {
                com.google.gson.JsonArray resolvedItems = new com.google.gson.JsonArray();
                for (ItemStack stack : items) {
                    String resolvedId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    resolvedItems.add(resolvedId);
                }
                ingredientObj.add("resolved_items", resolvedItems);
            }
        }

        return ingredientObj;
    }

    /**
     * Extract pattern/key data from ShapedRecipe using public API.
     * Inspired by EMI's approach - uses getIngredients() public method instead of reflection.
     */
    private static void extractShapedRecipeData(JsonObject recipeJson, ShapedRecipe shapedRecipe) {
        try {
            int width = shapedRecipe.getWidth();
            int height = shapedRecipe.getHeight();
            recipeJson.addProperty("grid_width", width);
            recipeJson.addProperty("grid_height", height);

            // Use public API method to get ingredients (EMI approach)
            var ingredients = shapedRecipe.getIngredients();

            if (ingredients != null && !ingredients.isEmpty()) {
                // Build pattern array and key mapping
                com.google.gson.JsonArray patternArray = new com.google.gson.JsonArray();
                JsonObject keyObject = new JsonObject();
                java.util.Map<String, Character> ingredientToChar = new java.util.HashMap<>();
                char nextChar = 'A';

                for (int row = 0; row < height; row++) {
                    StringBuilder rowPattern = new StringBuilder();
                    for (int col = 0; col < width; col++) {
                        int index = row * width + col;
                        if (index < ingredients.size()) {
                            Ingredient ingredient = ingredients.get(index);
                            if (!ingredient.isEmpty()) {
                                // Create a key for this ingredient based on its items
                                String ingredientKey = getIngredientKey(ingredient);

                                // Assign or reuse character for this ingredient
                                if (!ingredientToChar.containsKey(ingredientKey)) {
                                    ingredientToChar.put(ingredientKey, nextChar);
                                    // Add to key object
                                    keyObject.add(String.valueOf(nextChar), serializeIngredient(ingredient));
                                    nextChar++;
                                }
                                rowPattern.append(ingredientToChar.get(ingredientKey));
                            } else {
                                rowPattern.append(' ');
                            }
                        } else {
                            rowPattern.append(' ');
                        }
                    }
                    patternArray.add(rowPattern.toString());
                }

                recipeJson.add("pattern", patternArray);
                recipeJson.add("key", keyObject);
            }
        } catch (Exception e) {
            // Failed to extract shaped recipe data
            ErrorLogUtil.log("Failed to extract shaped recipe data: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Get a unique key for an ingredient based on its items (for pattern matching).
     */
    private static String getIngredientKey(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return "empty";

        // Use the first item's ID as the key, or all items if it's a tag
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < Math.min(items.length, 5); i++) { // Limit to avoid huge keys
            if (i > 0) key.append("|");
            key.append(BuiltInRegistries.ITEM.getKey(items[i].getItem()).toString());
        }
        return key.toString();
    }

    /**
     * Try to detect grid dimensions for custom recipe types via reflection.
     */
    private static void detectGridDimensions(JsonObject recipeJson, Recipe<?> recipe) {
        try {
            // Try to find width and height fields
            Integer width = null;
            Integer height = null;

            // Common field names for grid dimensions
            String[] widthFields = {"width", "recipeWidth", "gridWidth", "craftWidth"};
            String[] heightFields = {"height", "recipeHeight", "gridHeight", "craftHeight"};

            // Try to get width
            for (String fieldName : widthFields) {
                try {
                    var field = recipe.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(recipe);
                    if (value instanceof Integer) {
                        width = (Integer) value;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            // Try to get height
            for (String fieldName : heightFields) {
                try {
                    var field = recipe.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(recipe);
                    if (value instanceof Integer) {
                        height = (Integer) value;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            // Add grid dimensions if found and non-standard (not 3x3)
            if (width != null && height != null && (width != 3 || height != 3)) {
                recipeJson.addProperty("grid_width", width);
                recipeJson.addProperty("grid_height", height);
            }
        } catch (Exception e) {
            // Failed to detect dimensions, that's fine
        }
    }

    public static LiteralArgumentBuilder<CommandSourceStack> make(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        return Commands.literal("exportrecipes")
                .executes(new CommandExportRecipes(context, mod, helpers));
    }
}
