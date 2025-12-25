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
import net.minecraft.network.chat.Component;
import org.cyclops.cyclopscore.init.IModBase;
import org.cyclops.iconexporter.GeneralConfig;
import org.cyclops.iconexporter.export.EnvironmentExportUtil;
import org.cyclops.iconexporter.helpers.IIconExporterHelpers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command to validate exported data and check for common issues.
 */
public class CommandValidate implements Command<CommandSourceStack> {

    private final CommandBuildContext context;
    private final IModBase mod;
    private final IIconExporterHelpers helpers;

    public CommandValidate(CommandBuildContext context, IModBase mod, IIconExporterHelpers helpers) {
        this.context = context;
        this.mod = mod;
        this.helpers = helpers;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String modpackName = GeneralConfig.modpackName;
        String mcVersion;
        try {
            mcVersion = SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            mcVersion = "unknown";
        }
        String loader = detectLoader();

        File gameDir = Minecraft.getInstance().gameDirectory;
        File structuredRoot = EnvironmentExportUtil.resolveStructuredRoot(gameDir, modpackName, mcVersion, loader);

        Minecraft mc = Minecraft.getInstance();

        if (!structuredRoot.exists()) {
            mc.player.sendSystemMessage(Component.literal("No export data found. Run exports first."));
            return 0;
        }

        List<ValidationIssue> issues = new ArrayList<>();
        ValidationStats stats = new ValidationStats();

        // Check recipes directory
        File recipesDir = new File(structuredRoot, "recipes");
        if (recipesDir.exists() && recipesDir.isDirectory()) {
            validateRecipes(recipesDir, issues, stats);
        }

        // Check data directory
        File dataDir = new File(structuredRoot, "data");
        if (dataDir.exists() && dataDir.isDirectory()) {
            validateData(dataDir, issues, stats);
        }

        // Check meta directory
        File metaDir = new File(structuredRoot, "meta");
        if (metaDir.exists() && metaDir.isDirectory()) {
            validateMeta(metaDir, issues, stats);
        }

        // Check icons directory
        File iconsDir = new File(structuredRoot, "icons");
        if (iconsDir.exists() && iconsDir.isDirectory()) {
            validateIcons(iconsDir, issues, stats);
        }

        // Write validation report
        try {
            File logsDir = new File(structuredRoot, "logs");
            //noinspection ResultOfMethodCallIgnored
            logsDir.mkdirs();
            File reportFile = new File(logsDir, "validation_report.txt");

            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write("Export Validation Report\n");
                writer.write("========================\n\n");

                writer.write("Statistics:\n");
                writer.write("-----------\n");
                writer.write("Total recipes checked: " + stats.recipesChecked + "\n");
                writer.write("Total items checked: " + stats.itemsChecked + "\n");
                writer.write("Total blocks checked: " + stats.blocksChecked + "\n");
                writer.write("Total icons checked: " + stats.iconsChecked + "\n");
                writer.write("Total issues found: " + issues.size() + "\n\n");

                if (issues.isEmpty()) {
                    writer.write("No validation issues found! All exports appear to be valid.\n");
                } else {
                    writer.write("Issues Found:\n");
                    writer.write("-------------\n");

                    // Group issues by severity
                    long errors = issues.stream().filter(i -> i.severity == IssueSeverity.ERROR).count();
                    long warnings = issues.stream().filter(i -> i.severity == IssueSeverity.WARNING).count();
                    long infos = issues.stream().filter(i -> i.severity == IssueSeverity.INFO).count();

                    writer.write(String.format("Errors: %d, Warnings: %d, Info: %d\n\n", errors, warnings, infos));

                    // Write issues grouped by type
                    for (IssueType type : IssueType.values()) {
                        List<ValidationIssue> typeIssues = issues.stream()
                            .filter(i -> i.type == type)
                            .toList();

                        if (!typeIssues.isEmpty()) {
                            writer.write("\n" + type.name().replace("_", " ") + ":\n");
                            for (ValidationIssue issue : typeIssues) {
                                writer.write(String.format("  [%s] %s\n", issue.severity, issue.message));
                                if (issue.file != null) {
                                    writer.write("    File: " + issue.file + "\n");
                                }
                            }
                        }
                    }
                }

                writer.write("\n\nValidation completed successfully.\n");
            }

            mc.player.sendSystemMessage(Component.literal(
                "Validation complete. " + issues.size() + " issues found. See logs/validation_report.txt"));
        } catch (IOException e) {
            mc.player.sendSystemMessage(Component.literal("Failed to write validation report: " + e.getMessage()));
        }

        return 0;
    }

    private void validateRecipes(File recipesDir, List<ValidationIssue> issues, ValidationStats stats) {
        try (Stream<Path> paths = Files.walk(recipesDir.toPath())) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(path -> {
                    stats.recipesChecked++;
                    File file = path.toFile();

                    // Check if file is empty
                    if (file.length() == 0) {
                        issues.add(new ValidationIssue(
                            IssueSeverity.ERROR,
                            IssueType.EMPTY_FILE,
                            "Recipe file is empty",
                            file.getAbsolutePath()
                        ));
                    }

                    // Check for very small files (likely missing data)
                    if (file.length() < 50) {
                        issues.add(new ValidationIssue(
                            IssueSeverity.WARNING,
                            IssueType.MISSING_DATA,
                            "Recipe file is suspiciously small (" + file.length() + " bytes)",
                            file.getAbsolutePath()
                        ));
                    }
                });
        } catch (IOException e) {
            issues.add(new ValidationIssue(
                IssueSeverity.ERROR,
                IssueType.DIRECTORY_ERROR,
                "Failed to scan recipes directory: " + e.getMessage(),
                recipesDir.getAbsolutePath()
            ));
        }
    }

    private void validateData(File dataDir, List<ValidationIssue> issues, ValidationStats stats) {
        File itemsDir = new File(dataDir, "items");
        File blocksDir = new File(dataDir, "blocks");

        if (itemsDir.exists()) {
            try (Stream<Path> paths = Files.walk(itemsDir.toPath())) {
                paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        stats.itemsChecked++;
                        File file = path.toFile();
                        if (file.length() == 0) {
                            issues.add(new ValidationIssue(
                                IssueSeverity.ERROR,
                                IssueType.EMPTY_FILE,
                                "Item data file is empty",
                                file.getAbsolutePath()
                            ));
                        }
                    });
            } catch (IOException e) {
                issues.add(new ValidationIssue(
                    IssueSeverity.ERROR,
                    IssueType.DIRECTORY_ERROR,
                    "Failed to scan items directory: " + e.getMessage(),
                    itemsDir.getAbsolutePath()
                ));
            }
        }

        if (blocksDir.exists()) {
            try (Stream<Path> paths = Files.walk(blocksDir.toPath())) {
                paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        stats.blocksChecked++;
                        File file = path.toFile();
                        if (file.length() == 0) {
                            issues.add(new ValidationIssue(
                                IssueSeverity.ERROR,
                                IssueType.EMPTY_FILE,
                                "Block data file is empty",
                                file.getAbsolutePath()
                            ));
                        }
                    });
            } catch (IOException e) {
                issues.add(new ValidationIssue(
                    IssueSeverity.ERROR,
                    IssueType.DIRECTORY_ERROR,
                    "Failed to scan blocks directory: " + e.getMessage(),
                    blocksDir.getAbsolutePath()
                ));
            }
        }
    }

    private void validateMeta(File metaDir, List<ValidationIssue> issues, ValidationStats stats) {
        // Check for required meta files
        File modpackFile = new File(metaDir, "modpack.json");
        if (!modpackFile.exists()) {
            issues.add(new ValidationIssue(
                IssueSeverity.WARNING,
                IssueType.MISSING_FILE,
                "Missing modpack.json metadata file",
                modpackFile.getAbsolutePath()
            ));
        }

        File modsFile = new File(metaDir, "mods.json");
        if (!modsFile.exists()) {
            issues.add(new ValidationIssue(
                IssueSeverity.WARNING,
                IssueType.MISSING_FILE,
                "Missing mods.json metadata file",
                modsFile.getAbsolutePath()
            ));
        }

        // Check mod_icons directory
        File modIconsDir = new File(metaDir, "mod_icons");
        if (modIconsDir.exists()) {
            long iconCount = 0;
            try (Stream<Path> paths = Files.walk(modIconsDir.toPath())) {
                iconCount = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".png"))
                    .count();
            } catch (IOException e) {
                // Ignore
            }

            if (iconCount == 0) {
                issues.add(new ValidationIssue(
                    IssueSeverity.INFO,
                    IssueType.MISSING_DATA,
                    "No mod icons found in meta/mod_icons/",
                    modIconsDir.getAbsolutePath()
                ));
            }
        }
    }

    private void validateIcons(File iconsDir, List<ValidationIssue> issues, ValidationStats stats) {
        try (Stream<Path> paths = Files.walk(iconsDir.toPath())) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".png"))
                .forEach(path -> {
                    stats.iconsChecked++;
                    File file = path.toFile();

                    // Check for very small icon files
                    if (file.length() < 100) {
                        issues.add(new ValidationIssue(
                            IssueSeverity.WARNING,
                            IssueType.CORRUPT_FILE,
                            "Icon file is suspiciously small (" + file.length() + " bytes)",
                            file.getAbsolutePath()
                        ));
                    }
                });
        } catch (IOException e) {
            issues.add(new ValidationIssue(
                IssueSeverity.ERROR,
                IssueType.DIRECTORY_ERROR,
                "Failed to scan icons directory: " + e.getMessage(),
                iconsDir.getAbsolutePath()
            ));
        }
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
        return Commands.literal("validate")
                .executes(new CommandValidate(context, mod, helpers));
    }

    // Helper classes
    private static class ValidationIssue {
        final IssueSeverity severity;
        final IssueType type;
        final String message;
        final String file;

        ValidationIssue(IssueSeverity severity, IssueType type, String message, String file) {
            this.severity = severity;
            this.type = type;
            this.message = message;
            this.file = file;
        }
    }

    private static class ValidationStats {
        int recipesChecked = 0;
        int itemsChecked = 0;
        int blocksChecked = 0;
        int iconsChecked = 0;
    }

    private enum IssueSeverity {
        ERROR, WARNING, INFO
    }

    private enum IssueType {
        EMPTY_FILE,
        MISSING_FILE,
        MISSING_DATA,
        CORRUPT_FILE,
        DIRECTORY_ERROR
    }
}
