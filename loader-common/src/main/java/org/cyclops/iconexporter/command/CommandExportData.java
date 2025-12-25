package org.cyclops.iconexporter.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.export.BlockDataExportUtil;
import org.cyclops.iconexporter.export.EnvironmentExportUtil;
import org.cyclops.iconexporter.export.ErrorLogUtil;
import org.cyclops.iconexporter.export.ItemDataExportUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.IOException;

/**
 * Command to export detailed item and block data to JSON files.
 */
public class CommandExportData implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandExportData(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
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
        File dataDir = EnvironmentExportUtil.resolveDataDir(gameDir, scale, modpackName, mcVersion, loader);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            mc.player.sendSystemMessage(Component.literal("Cannot export data: No world loaded"));
            return 0;
        }

        int itemSuccessCount = 0;
        int blockSuccessCount = 0;
        int itemErrorCount = 0;
        int blockErrorCount = 0;
        java.util.Map<String, Integer> blockErrorsByMod = new java.util.HashMap<>();
        java.util.List<String> failedBlocks = new java.util.ArrayList<>();

        // Export item data
        File itemsDir = new File(dataDir, "items");
        //noinspection ResultOfMethodCallIgnored
        itemsDir.mkdirs();

        for (Item item : BuiltInRegistries.ITEM) {
            try {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId == null) continue;

                // Create namespace directory
                File namespaceDir = new File(itemsDir, itemId.getNamespace());
                //noinspection ResultOfMethodCallIgnored
                namespaceDir.mkdirs();

                // Handle nested paths
                String path = itemId.getPath();
                File itemFile;
                if (path.contains("/")) {
                    String[] parts = path.split("/");
                    File currentDir = namespaceDir;
                    for (int i = 0; i < parts.length - 1; i++) {
                        currentDir = new File(currentDir, parts[i]);
                        //noinspection ResultOfMethodCallIgnored
                        currentDir.mkdirs();
                    }
                    itemFile = new File(currentDir, parts[parts.length - 1] + ".json");
                } else {
                    itemFile = new File(namespaceDir, path + ".json");
                }

                ItemDataExportUtil.writeItemDataJson(itemFile, item, mod);
                itemSuccessCount++;
            } catch (Exception e) {
                itemErrorCount++;
                ErrorLogUtil.log("Failed to export item data: " + e.getMessage());
            }
        }

        // Export block data
        File blocksDir = new File(dataDir, "blocks");
        //noinspection ResultOfMethodCallIgnored
        blocksDir.mkdirs();

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId == null) continue;

            try {

                // Create namespace directory
                File namespaceDir = new File(blocksDir, blockId.getNamespace());
                //noinspection ResultOfMethodCallIgnored
                namespaceDir.mkdirs();

                // Handle nested paths
                String path = blockId.getPath();
                File blockFile;
                if (path.contains("/")) {
                    String[] parts = path.split("/");
                    File currentDir = namespaceDir;
                    for (int i = 0; i < parts.length - 1; i++) {
                        currentDir = new File(currentDir, parts[i]);
                        //noinspection ResultOfMethodCallIgnored
                        currentDir.mkdirs();
                    }
                    blockFile = new File(currentDir, parts[parts.length - 1] + ".json");
                } else {
                    blockFile = new File(namespaceDir, path + ".json");
                }

                BlockDataExportUtil.writeBlockDataJson(blockFile, block, mod);
                blockSuccessCount++;
            } catch (Exception e) {
                blockErrorCount++;
                String namespace = blockId.getNamespace();
                blockErrorsByMod.merge(namespace, 1, Integer::sum);
                failedBlocks.add(blockId.toString());
                ErrorLogUtil.log("Failed to export block data for " + blockId + ": " + e.getMessage());
            }
        }

        // Write error log if any errors occurred
        try {
            if (GeneralConfig.useStructuredOutput && (itemErrorCount > 0 || blockErrorCount > 0)) {
                File structuredRoot = EnvironmentExportUtil.resolveStructuredRoot(gameDir, modpackName, mcVersion, loader);
                ErrorLogUtil.write(structuredRoot);
            }
        } catch (IOException e) {
            // Ignore errors writing error log
        }

        // Write block export summary report
        if (blockSuccessCount > 0 || blockErrorCount > 0) {
            try {
                File structuredRoot = EnvironmentExportUtil.resolveStructuredRoot(gameDir, modpackName, mcVersion, loader);
                File logsDir = new File(structuredRoot, "logs");
                //noinspection ResultOfMethodCallIgnored
                logsDir.mkdirs();
                File summaryFile = new File(logsDir, "block_export_summary.txt");

                try (java.io.FileWriter writer = new java.io.FileWriter(summaryFile)) {
                    writer.write("Block Export Summary\n");
                    writer.write("====================\n\n");
                    writer.write("Total blocks processed: " + (blockSuccessCount + blockErrorCount) + "\n");
                    writer.write("Successfully exported: " + blockSuccessCount + "\n");
                    writer.write("Failed exports: " + blockErrorCount + "\n");

                    if (blockErrorCount > 0) {
                        double errorPercentage = (blockErrorCount * 100.0 / (blockSuccessCount + blockErrorCount));
                        writer.write("Error rate: " + String.format("%.2f", errorPercentage) + "%\n\n");

                        writer.write("Failed blocks by namespace:\n");
                        writer.write("---------------------------\n");

                        // Sort by error count (descending)
                        blockErrorsByMod.entrySet().stream()
                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                            .forEach(entry -> {
                                try {
                                    writer.write(String.format("  %-30s : %4d blocks\n", entry.getKey(), entry.getValue()));
                                } catch (IOException e) {
                                    // Ignore write errors
                                }
                            });

                        writer.write("\nFailed blocks (first 50):\n");
                        writer.write("------------------------\n");
                        int limit = Math.min(50, failedBlocks.size());
                        for (int i = 0; i < limit; i++) {
                            writer.write("  " + failedBlocks.get(i) + "\n");
                        }
                        if (failedBlocks.size() > 50) {
                            writer.write("  ... and " + (failedBlocks.size() - 50) + " more\n");
                        }

                        writer.write("\nNote: Block export failures are often caused by blocks that require world\n");
                        writer.write("context for certain properties. Check the error log for detailed error messages.\n");
                    } else {
                        writer.write("\nAll blocks exported successfully!\n");
                    }
                }
            } catch (IOException e) {
                // Ignore errors writing summary
            }
        }

        // Send feedback to player
        int totalErrors = itemErrorCount + blockErrorCount;
        String feedbackMessage;
        if (totalErrors == 0) {
            feedbackMessage = "Exported data for " + itemSuccessCount + " items and " + blockSuccessCount + " blocks to: " + dataDir.getAbsolutePath();
        } else {
            if (blockErrorCount > 0) {
                feedbackMessage = String.format("Exported %d items and %d blocks (%d errors). See logs/block_export_summary.txt",
                    itemSuccessCount, blockSuccessCount, totalErrors);
            } else {
                feedbackMessage = "Exported " + itemSuccessCount + " items and " + blockSuccessCount + " blocks (" + totalErrors + " errors). Check error log.";
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

    public static LiteralArgumentBuilder<CommandSourceStack> make(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        return Commands.literal("exportdata")
                .executes(new CommandExportData(context, mod, helpers));
    }
}
