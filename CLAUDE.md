# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

IconExporter is a Minecraft mod that exports item and block icons to PNG files with metadata. It uses a **multi-loader architecture** supporting Fabric, Forge, and NeoForge through shared common code.

## Build Commands

### Building the Project
```bash
./gradlew build
```
Builds all loader variants (common, fabric, forge, neoforge). Output JARs are in `loader-*/build/libs/`.

### Running Tests
```bash
./gradlew test
./gradlew runGameTestServer  # Game tests (Minecraft environment)
./gradlew jacocoTestReport   # Generate coverage reports
```

### Code Quality
```bash
./gradlew spotlessCheck      # Check code formatting
./gradlew spotlessApply      # Auto-format code
```

### Building Specific Loaders
```bash
./gradlew :loader-fabric:build
./gradlew :loader-forge:build
./gradlew :loader-neoforge:build
./gradlew :loader-common:build
```

### Publishing (requires credentials)
```bash
./gradlew publish            # Maven (GitHub Packages)
./gradlew publishCurseForge  # CurseForge
./gradlew modrinth           # Modrinth
```

## Architecture

### Multi-Loader Pattern

The project uses a **shared-code architecture** with four modules:

- **`loader-common/`**: Platform-agnostic code (95% of functionality)
  - Commands, GUI, export logic, utilities
  - Defines `IIconExporterHelpers` interface for platform abstraction

- **`loader-fabric/`**: Fabric-specific entry point and helpers
  - `IconExporterFabric.java` - Mod initializer
  - `IconExporterHelpersFabric.java` - Fabric API implementations

- **`loader-forge/`**: Forge-specific entry point and helpers
  - `IconExporterForge.java` - `@Mod` entry point
  - `IconExporterHelpersForge.java` - Forge API implementations

- **`loader-neoforge/`**: NeoForge-specific entry point and helpers
  - `IconExporter.java` - `@Mod` entry point
  - `IconExporterHelpersNeoForge.java` - NeoForge API implementations

### Dependency: CyclopsCore

IconExporter heavily depends on **CyclopsCore** (a framework mod by the same author):
- Provides base mod infrastructure (`ModBaseVersionable`, `ModBaseFabric`, `ModBaseForge`)
- Configuration system (`ConfigHandlerCommon`, `DummyConfigCommon`)
- Proxy pattern (client/server separation)
- Command registration framework

**Important**: CyclopsCore credentials required in `~/.gradle/gradle.properties`:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```
Or set environment variables: `GITHUB_USER` and `GITHUB_TOKEN`.

### Platform Abstraction Layer

Common code calls loader-specific operations through `IIconExporterHelpers`:

```java
public interface IIconExporterHelpers {
    String componentsToString(HolderLookup.Provider, DataComponentPatch);
    List<CreativeModeTab> getCreativeTabs();
    String getFluidLocalName(Fluid fluid);
    void renderFluidSlot(GuiGraphics gui, Fluid fluid);
}
```

Each loader provides its own implementation for platform-specific APIs (Fabric's Transfer API vs Forge/NeoForge's FluidStack).

### Runtime Loader Detection

Common code detects the active loader at runtime:
```java
private static String detectLoader() {
    if (classExists("net.fabricmc.loader.api.FabricLoader")) return "fabric";
    if (classExists("net.neoforged.fml.loading.FMLLoader")) return "neoforge";
    if (classExists("net.minecraftforge.fml.loading.FMLLoader")) return "forge";
    return "unknown";
}
```
Used in `ScreenIconExporter`, `CommandExportEnv`, and `CommandExportRecipes`.

## Key Components

### Commands (all client-side only)

Located in `loader-common/src/main/java/org/cyclops/iconexporter/command/`:

1. **`/iconexporter export [scale]`** - Opens export GUI for rendered icons
2. **`/iconexporter exportall`** - **Exports EVERYTHING** (env, recipes, data, textures, models, translations, icons)
3. **`/iconexporter exportenv`** - Exports environment metadata (modpack.json, mods.json, datapacks.json)
4. **`/iconexporter exportrecipes`** - Exports all recipes to JSON files
5. **`/iconexporter exportdata`** - Exports detailed item and block data to JSON
6. **`/iconexporter exporttextures`** - Extracts raw texture PNG files from resource packs
7. **`/iconexporter exportmodels`** - Exports 3D model JSON definitions
8. **`/iconexporter exporttranslations`** - Exports translation keys and display names
9. **`/iconexporter exportmetadata`** - Exports icon metadata JSON (legacy)
10. **`/iconexporter validate`** - Validates icon exports against actual game items

Commands are registered in each loader's `constructBaseCommand()` method.

**Recommended workflow**: Use `/iconexporter exportall` to export everything in one command. This runs all export utilities sequentially with proper error handling.

### Export Pipeline Architecture

**Full Export Pipeline** (`CommandExportAll`):

The `/iconexporter exportall` command orchestrates all export operations in sequence:

1. **Environment Export** (`CommandExportEnv`) - Exports modpack.json, mods.json, datapacks.json
2. **Recipe Export** (`CommandExportRecipes`) - Exports all recipes with custom mod recipe fallback
3. **Data Export** (`CommandExportData`) - Exports detailed item/block metadata
4. **Texture Export** (`CommandExportTextures`) - Extracts raw PNG textures (runs async)
5. **Model Export** (`CommandExportModels`) - Exports 3D model JSON definitions (runs async)
6. **Translation Export** (`CommandExportTranslations`) - Exports translation data
7. **Wait for Async** - Waits 2 seconds for async exports to complete
8. **Icon Export** (`CommandExport`) - Opens GUI for rendered icon export

Each step has its own error handling, so failures in one export don't stop the pipeline.

**Icon Rendering Pipeline** (`ScreenIconExporter`):

1. User runs `/iconexporter export [scale]` (or triggered by `exportall`)
2. `ScreenIconExporter` opens and creates task queue:
   - Iterates all fluids in `BuiltInRegistries.FLUID`
   - Iterates all items from creative tabs (via helpers)
   - Creates `IExportTask` for each
3. **One task per render frame** (not per tick):
   - Renders item/fluid with background color RGB(254, 255, 255)
   - Takes screenshot via `Screenshot.takeScreenshot()`
   - Processes transparency and crops to scale
   - Saves PNG via `ImageExportUtil.exportImageFromScreenshot()`
   - Optionally saves component data to `.txt` file
4. Progress shown in action bar
5. Screen closes when queue empty

### Export Output Paths

**Legacy mode** (`useStructuredOutput = false`):
```
minecraft/icon-exports-x32/
  minecraft__stone.png
  fluid__minecraft__water.png
```

**Structured mode** (`useStructuredOutput = true`):
```
minecraft/exports/MyModpack/1.21.1/fabric/
  icons/                    # Rendered item/block icons (PNG)
    minecraft__stone.png
  textures/
    items/                  # Raw item texture files
    blocks/                 # Raw block texture files
  models/
    items/                  # Item model JSON definitions
    blocks/                 # Block model JSON definitions
  recipes/                  # All recipes (JSON, organized by namespace)
    minecraft/
    create/
  data/
    items/                  # Detailed item information (JSON)
    blocks/                 # Detailed block information (JSON)
  meta/
    modpack.json            # Environment metadata
    mods.json               # All mods with versions
    datapacks.json          # All datapacks
  translations/
    items.json              # Item translation keys and display names
    blocks.json             # Block translation keys and display names
  logs/
    errors.txt              # Export error log
```

### Configuration (`GeneralConfig.java`)

Key config options:
- `defaultScale` (default: 32) - Image size in pixels
- `fileNameHashComponents` (default: false) - Hash components with MD5
- `useStructuredOutput` (default: false) - Enable multi-modpack structure
- `exportBaseDir` (default: "exports") - Base directory for exports
- `autoExportOnStartup` (default: false) - Auto-trigger export on world join
- `autoCreateWorld` (default: false) - Auto-create and load temp world for CI/CD
- `autoExportDelay` (default: 5) - Seconds to wait before auto-export starts
- `modpackName` - Identifier for structured output

### Auto-Export System

The mod includes a fully automated export system for CI/CD pipelines:

**Components**:
- `AutoExportHandler` - Triggers export when player joins world
- `WorldAutoLoader` - Creates and loads temp world automatically on title screen
- `MinimalWorldCreator` - Creates minimal flat world for export

**How it works**:
1. Set `autoExportOnStartup = true` and `autoCreateWorld = true` in config
2. Launch Minecraft
3. `WorldAutoLoader` detects `TitleScreen` and navigates to world selection
4. Creates `iconexporter_temp` world if it doesn't exist
5. Loads the world automatically using `WorldOpenFlows` API
6. `AutoExportHandler` detects world join and triggers export after delay
7. Runs full export pipeline via `CommandExportAll`
8. Game can quit automatically with `-Diconexporter.quitAfterExport=true` JVM flag

**For CI/CD**: This enables completely hands-free automation with zero manual steps.

## Code Conventions

### Lombok
The project uses **Project Lombok** for code generation. IDE plugin required:
- Generates constructors, getters, setters via annotations
- If you see "missing getter/setter" errors, ensure Lombok processor is enabled

### Code Formatting
- Uses Spotless with:
  - 4 spaces for indentation (no tabs)
  - Unix line endings
  - Remove unused imports
  - Trim trailing whitespace
- Pre-commit hook auto-installed via `updateGitHooks` task

### File Naming Conventions
- Export filenames escape special characters:
  - `:` → `__` (e.g., `minecraft__stone`)
  - `/` → `___`
- Components optionally hashed with MD5 if `fileNameHashComponents = true`

### Export Utilities

The `loader-common/src/main/java/org/cyclops/iconexporter/export/` package contains specialized export utilities:

- **`EnvironmentExportUtil`** - Exports modpack.json with loader/version info
- **`ModsExportUtil`** - Exports mods.json with all mod metadata
- **`DatapacksExportUtil`** - Exports datapacks.json listing all datapacks
- **`RecipeExportUtil`** - Exports recipes with custom mod recipe fallback
- **`ItemDataExportUtil`** - Exports detailed item metadata (damage, durability, etc.)
- **`BlockDataExportUtil`** - Exports block properties (hardness, light level, etc.)
- **`TextureExportUtil`** - Extracts raw PNG textures from resource packs (async)
- **`ModelExportUtil`** - Exports 3D model JSON definitions (async)
- **`TranslationExportUtil`** - Exports translation keys and display names
- **`ErrorLogUtil`** - Centralized error logging to `logs/errors.txt`
- **`ModMetadataExtractor`** - Platform-agnostic mod metadata extraction
- **`ManifestParser`** - Parses JAR manifests for mod info

**Key patterns**:
- All utilities use static methods for easy invocation
- Export to structured paths based on `GeneralConfig.useStructuredOutput`
- Errors logged to `ErrorLogUtil` don't stop export pipeline
- Async exports (textures/models) run on background threads

## Development Workflow

### Adding New Export Functionality
1. Add logic to `loader-common/src/main/java/org/cyclops/iconexporter/export/`
2. Create a new `*ExportUtil.java` utility class with static export methods
3. If platform-specific, add method to `IIconExporterHelpers` interface
4. Implement in all three loader helpers:
   - `IconExporterHelpersFabric`
   - `IconExporterHelpersForge`
   - `IconExporterHelpersNeoForge`
5. Create a new command in `command/` package that calls your utility
6. Register command in each loader's `constructBaseCommand()`
7. Add to `CommandExportAll` pipeline if it should be part of full export
8. Update configuration in `GeneralConfig.java` if needed

### Adding New Commands
1. Create command in `loader-common/.../command/`
2. Register in each loader's `constructBaseCommand()`:
   ```java
   @Override
   protected ILiteralArgumentBuilder constructBaseCommand() {
       ILiteralArgumentBuilder root = super.constructBaseCommand();
       if (isClientSide()) {
           root.then(CommandExport.make(getHelpers()));
           root.then(CommandYourNew.make(getHelpers()));
       }
       return root;
   }
   ```

### Testing Changes
Run game tests that execute in actual Minecraft environment:
```bash
./gradlew runGameTestServer
```
Note: `downloadAssets` can be slow, skip with `-x :loader-forge:downloadAssets` if needed.

## Branching Strategy

- **`master-{mc_version}`**: Development branch (potentially unstable)
- **`release-{mc_version}`**: Stable releases (tagged with versions)

Current branch: `master-1.21-lts` (for Minecraft 1.21.1)

## Error Handling

### Export Error Logging

All export operations use `ErrorLogUtil` for centralized error tracking:
- Individual item/block/recipe failures don't stop the export
- Errors are logged to `<export_dir>/logs/errors.txt`
- Stack traces include context (item ID, file path, etc.)
- Export commands show summary of errors in chat

### Custom Mod Recipes

Many mods define custom recipe types that don't use Minecraft's standard codec serialization. The recipe exporter handles these gracefully:

**Standard recipes** (crafting, smelting, etc.):
- Export with full JSON data via codec serialization

**Custom mod recipes** (Create, Thermal, Mekanism, etc.):
- Export with fallback data when codec fails:
  - Recipe ID (`"id": "create:crushing/diamond"`)
  - Recipe type/serializer (`"type": "create:crushing"`)
  - Result item and count (if available)
  - Export note explaining custom recipe type
  - Error message from codec for debugging

This ensures **all recipes are exported**, even if custom recipes have limited data.

### Known Export Limitations

**Banner Patterns**: Items with banner patterns may not include component data in filenames/txt files due to registry validation. PNG icons still export correctly.

**Component Serialization**: Items with complex registry-backed components may fail component serialization. Items still export, but without component suffix in filenames.

## Common Issues

### Missing CyclopsCore Dependency
**Error**: `Could not resolve org.cyclops.cyclopscore:...`

**Solution**: Add GitHub credentials to `~/.gradle/gradle.properties`:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

### Loader-Specific Code in Common Module
**Anti-pattern**: Importing `net.fabricmc.*`, `net.minecraftforge.*`, or `net.neoforged.*` in `loader-common/`

**Solution**:
1. Add method to `IIconExporterHelpers` interface
2. Implement in loader-specific helper classes
3. Call via helpers in common code

### Keystore Signing Errors
**Error**: `Could not set unknown property 'keyStore'`

**Cause**: Missing `secrets.properties` or signing environment variables

**Solution**: For local builds, signing is optional. CI handles this via secrets.

## Version Information

- **Java**: 21 (toolchain enforced)
- **Minecraft**: 1.21.1
- **Gradle**: 8.8 (wrapper)
- **Fabric Loom**: 1.7.4
- **NeoForge ModDev**: 0.1.110
- **CyclopsCore**: Defined in `gradle.properties` (`cyclopscore_version`)

## Additional Resources

- **CurseForge**: https://www.curseforge.com/minecraft/mc-mods/iconexporter
- **Issues**: https://github.com/CyclopsMC/IconExporter/issues
- **License**: MIT
