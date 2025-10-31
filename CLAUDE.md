Plea# CLAUDE.md

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

1. **`/iconexporter export [scale]`** - Opens export GUI
2. **`/iconexporter exportmetadata`** - Exports `icon-exports-metadata.json`
3. **`/iconexporter exportenv`** - Exports environment metadata to `meta/modpack.json`
4. **`/iconexporter exportrecipes`** - Stub for future recipe export

Commands are registered in each loader's `constructBaseCommand()` method.

### Export Pipeline

1. User runs `/iconexporter export [scale]`
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
  icons/
    minecraft__stone.png
  meta/
    modpack.json
  recipes/
    (future)
```

### Configuration (`GeneralConfig.java`)

Key config options:
- `defaultScale` (default: 32) - Image size in pixels
- `fileNameHashComponents` (default: false) - Hash components with MD5
- `useStructuredOutput` (default: false) - Enable multi-modpack structure
- `exportBaseDir` (default: "exports") - Base directory for exports
- `autoExportOnStartup` (default: false) - Auto-trigger on world join
- `modpackName` - Identifier for structured output

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

## Development Workflow

### Adding New Export Functionality
1. Add logic to `loader-common/src/main/java/org/cyclops/iconexporter/export/`
2. If platform-specific, add method to `IIconExporterHelpers` interface
3. Implement in all three loader helpers:
   - `IconExporterHelpersFabric`
   - `IconExporterHelpersForge`
   - `IconExporterHelpersNeoForge`
4. Update configuration in `GeneralConfig.java` if needed

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
