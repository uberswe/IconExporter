## IconExporter

[![Build Status](https://github.com/CyclopsMC/IconExporter/workflows/CI/badge.svg)](https://github.com/CyclopsMC/IconExporter/actions?query=workflow%3ACI)
[![Coverage Status](https://coveralls.io/repos/github/CyclopsMC/IconExporter/badge.svg)](https://coveralls.io/github/CyclopsMC/IconExporter)
[![Crowdin](https://badges.crowdin.net/cyclopsmc-iconexporter/localized.svg)](https://crowdin.com/project/cyclopsmc-iconexporter)
[![Download](https://img.shields.io/static/v1?label=Maven&message=GitHub%20Packages&color=blue)](https://github.com/CyclopsMC/packages/packages/770001)
[![CurseForge](http://cf.way2muchnoise.eu/full_327048_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/iconexporter)
[![Discord](https://img.shields.io/discord/386052815128100865.svg?colorB=7289DA&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHYAAABWAgMAAABnZYq0AAAACVBMVEUAAB38%2FPz%2F%2F%2F%2Bm8P%2F9AAAAAXRSTlMAQObYZgAAAAFiS0dEAIgFHUgAAAAJcEhZcwAACxMAAAsTAQCanBgAAAAHdElNRQfhBxwQJhxy2iqrAAABoElEQVRIx7WWzdGEIAyGgcMeKMESrMJ6rILZCiiBg4eYKr%2Fd1ZAfgXFm98sJfAyGNwno3G9sLucgYGpQ4OGVRxQTREMDZjF7ILSWjoiHo1n%2BE03Aw8p7CNY5IhkYd%2F%2F6MtO3f8BNhR1QWnarCH4tr6myl0cWgUVNcfMcXACP1hKrGMt8wcAyxide7Ymcgqale7hN6846uJCkQxw6GG7h2MH4Czz3cLqD1zHu0VOXMfZjHLoYvsdd0Q7ZvsOkafJ1P4QXxrWFd14wMc60h8JKCbyQvImzlFjyGoZTKzohwWR2UzSONHhYXBQOaKKsySsahwGGDnb%2FiYPJw22sCqzirSULYy1qtHhXGbtgrM0oagBV4XiTJok3GoLoDNH8ooTmBm7ZMsbpFzi2bgPGoXWXME6XT%2BRJ4GLddxJ4PpQy7tmfoU2HPN6cKg%2BledKHBKlF8oNSt5w5g5o8eXhu1IOlpl5kGerDxIVT%2BztzKepulD8utXqpChamkzzuo7xYGk%2FkpSYuviLXun5bzdRf0Krejzqyz7Z3p0I1v2d6HmA07dofmS48njAiuMgAAAAASUVORK5CYII%3D)](https://discord.gg/9yDxubB)

A comprehensive Minecraft mod for exporting item icons, block data, recipes, textures, and models to structured JSON and PNG files. Perfect for creating modpack documentation, wikis, and automated content generation.

All stable releases (including deobfuscated builds) can be found on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/iconexporter/files).

[Development builds](https://github.com/CyclopsMC/packages/packages/) are hosted as GitHub packages.

## Features

- **Rendered Icon Export**: High-quality rendered images of all items, blocks, and fluids
- **Raw Texture Export**: Extract original PNG texture files from resource packs
- **3D Model Export**: Export block and item model JSON definitions
- **Recipe Export**: Complete recipe data in JSON format (with fallback for custom mod recipes)
- **Translation Export**: Export display names, translation keys, and language files for internationalization
- **Detailed Metadata**: Comprehensive item/block properties (attack damage, durability, waterloggable, tooltips, display names, etc.)
- **Modpack Information**: Automatic detection and export of mods, datapacks, and environment info
- **Multi-Loader Support**: Works on Fabric, Forge, and NeoForge
- **Automated Export**: Configure auto-export on world join for CI/CD pipelines
- **Structured Output**: Organized folder structure supporting multiple modpacks

## Usage

### Commands

All commands are accessed via `/iconexporter <subcommand>`:

| Command | Description |
|---------|-------------|
| `/iconexporter export [scale]` | Opens GUI to export rendered icons (default scale from config) |
| `/iconexporter exportenv` | Exports environment metadata (modpack.json, mods.json, datapacks.json) |
| `/iconexporter exportrecipes` | Exports all recipes to JSON files |
| `/iconexporter exportdata` | Exports detailed item and block information to JSON |
| `/iconexporter exporttextures` | Extracts raw texture PNG files from resource packs |
| `/iconexporter exportmodels` | Exports 3D model JSON definitions |
| `/iconexporter exportall` | **Exports EVERYTHING** - env, recipes, data, textures, models, and icons |
| `/iconexporter exportmetadata` | Exports icon metadata JSON |

### Configuration

Configuration is managed through CyclopsCore's config system.

#### Config File Location

**File name:** `iconexporter-client.toml`
**Location:** `<minecraft_instance>/config/iconexporter-client.toml`

The config file should auto-generate on first launch. If it doesn't appear:

1. Ensure CyclopsCore is installed (required dependency)
2. Launch the game once to trigger generation
3. Check `logs/latest.log` for any errors
4. Create the file manually using the template below

#### Config Template

If the config file doesn't auto-generate, create `config/iconexporter-client.toml` with:

```toml
[core]
    # The default image width in px to render at.
    defaultScale = 32

    # If the components should be hashed with MD5 when constructing the file name,
    # and if an auxiliary txt file should be created with the full components contents.
    fileNameHashComponents = false

    # Base directory (relative to game dir) for structured exports when enabled.
    exportBaseDir = "exports"

    # Use structured multi-modpack output directories instead of legacy icon-exports-x{scale}.
    useStructuredOutput = false

    # Automatically trigger export on client startup after entering a world.
    autoExportOnStartup = false

    # Automatically create and load a temporary world on startup (requires autoExportOnStartup=true).
    # Enables fully hands-free CI/CD automation with zero manual intervention.
    autoCreateWorld = false

    # Optional modpack name to include in structured export paths.
    modpackName = ""
```

#### Example Configurations

**For Manual Export (Recommended for most users):**
```toml
[core]
    defaultScale = 16
    fileNameHashComponents = true
    exportBaseDir = "exports"
    useStructuredOutput = true
    autoExportOnStartup = false
    modpackName = "MyModpack"
```

**For Automated CI/CD:**
```toml
[core]
    defaultScale = 16
    fileNameHashComponents = true
    exportBaseDir = "exports"
    useStructuredOutput = true
    autoExportOnStartup = true
    autoCreateWorld = true
    modpackName = "MyModpack"
```

### Automation

#### Auto-Export on World Join

Enable automatic export when joining a world:

1. Set `autoExportOnStartup = true` in config
2. Join any world
3. Exports run automatically in the background
4. Progress messages appear in chat

**What gets exported automatically:**
- ✅ Environment metadata (modpack.json, mods.json, datapacks.json)
- ✅ All recipes in JSON format
- ✅ Detailed item and block data
- ✅ Raw textures (PNG files from resource packs)
- ✅ 3D models (JSON definitions)
- ✅ Rendered icons (opens GUI automatically)

The export process waits for the level to fully load before starting. Icon rendering happens in a GUI that opens automatically and processes icons at ~20/second to prevent performance issues.

#### CI/CD Automation

IconExporter supports **fully hands-free automation** for CI/CD pipelines with zero manual intervention.

**Setup (One-Time Configuration):**

Configure auto-export and auto-create/load world in your config file:
```toml
[core]
    autoExportOnStartup = true
    autoCreateWorld = true
    useStructuredOutput = true
    modpackName = "YourModpack"
```

**Automated Operation:**

With these settings enabled, simply launch Minecraft:

```bash
# Launch Minecraft with auto-export and quit after completion
java -Diconexporter.quitAfterExport=true -jar minecraft.jar
```

**What happens automatically:**
1. Minecraft starts and displays title screen
2. IconExporter detects title screen and checks for `iconexporter_temp` world
3. If world doesn't exist, it's **created automatically** (minimal flat world)
4. World is **loaded automatically** using reflection-based API detection
5. Once player joins world, auto-export triggers
6. Exports environment metadata, recipes, data, textures, models, translations
7. Renders all icons (5-10 minutes for 10,000+ items)
8. **Game quits automatically** when complete

**No manual steps required** - the entire process is fully automated from start to finish!

**Notes:**
- The world loading uses reflection to support multiple Minecraft versions automatically
- If automatic world loading fails (rare), the mod will log instructions to load manually
- First run creates the world, subsequent runs reuse the existing world
- Perfect for GitHub Actions, Jenkins, or any CI system running Minecraft headlessly

This enables true CI/CD workflows where you can automatically generate updated item exports whenever your modpack changes.

## Output Structure

When `useStructuredOutput` is enabled, exports are organized as:

```
<gameDir>/exports/<modpack>/<mc_version>/<loader>/
├── meta/
│   ├── modpack.json          # Environment metadata
│   ├── mods.json             # All mods with versions
│   └── datapacks.json        # All datapacks
├── icons/                    # Rendered item/block icons (PNG)
├── textures/
│   ├── items/               # Raw item texture files
│   └── blocks/              # Raw block texture files
├── models/
│   ├── items/               # Item model JSON definitions
│   └── blocks/              # Block model JSON definitions
├── recipes/                 # All recipes (JSON, organized by namespace)
├── data/
│   ├── items/               # Detailed item information (JSON)
│   └── blocks/              # Detailed block information (JSON)
└── logs/
    └── errors.txt           # Export error log
```

### Example Output Files

#### meta/modpack.json
```json
{
  "modpack": "MyModpack",
  "mc_version": "1.21.1",
  "loader": "fabric",
  "exported_at": "2025-10-19T10:30:00Z"
}
```

#### meta/mods.json
```json
[
  {
    "mod_id": "minecraft",
    "version": "1.21.1",
    "display_name": "Minecraft"
  },
  {
    "mod_id": "fabric-api",
    "version": "0.100.0",
    "display_name": "Fabric API"
  }
]
```

#### data/items/minecraft/diamond_sword.json
```json
{
  "id": "minecraft:diamond_sword",
  "namespace": "minecraft",
  "path": "diamond_sword",
  "translation_key": "item.minecraft.diamond_sword",
  "display_name": "Diamond Sword",
  "tooltip": [
    "When in main hand:",
    " 7 Attack Damage",
    " 1.6 Attack Speed"
  ],
  "max_stack_size": 1,
  "max_damage": 1561,
  "durability": 1561,
  "is_damageable": true,
  "enchantment_value": 10,
  "is_fireproof": false,
  "rarity": "common",
  "attribute_modifiers": [
    {
      "attribute": "minecraft:generic.attack_damage",
      "amount": 7.0,
      "operation": "add_value",
      "slot": "mainhand"
    }
  ],
  "tags": [
    "minecraft:swords",
    "minecraft:weapon"
  ]
}
```

#### data/blocks/minecraft/water.json
```json
{
  "id": "minecraft:water",
  "namespace": "minecraft",
  "path": "water",
  "translation_key": "block.minecraft.water",
  "display_name": "Water",
  "explosion_resistance": 100.0,
  "destroy_speed": 100.0,
  "light_emission": 0,
  "is_air": false,
  "has_collision": false,
  "properties": {
    "level": ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16"]
  },
  "waterloggable": false
}
```

### Legacy Output

When `useStructuredOutput` is disabled, files are exported to:
```
<gameDir>/icon-exports-x<scale>/
```

## Error Handling

All export operations include comprehensive error handling:
- Individual item/block failures don't stop the export process
- Errors are logged to `logs/errors.txt` in the output directory
- In-game messages provide progress feedback
- Detailed stack traces in game logs for debugging

### Custom Mod Recipes

Many mods (like Create, Thermal, Mekanism) define custom recipe types that don't use Minecraft's standard codec serialization. IconExporter handles these gracefully:

- **Standard recipes** (crafting, smelting, etc.) export with full JSON data
- **Custom mod recipes** export with fallback data when codec serialization fails:
  - Recipe ID (`"id": "create:crushing/diamond"`)
  - Recipe type/serializer (`"type": "create:crushing"`) - or "unknown" if unavailable
  - Result item and count (if available)
  - Export note explaining it's a custom recipe type
  - Error message from codec for debugging

Example fallback export for Create's crushing wheel recipe:
```json
{
  "id": "create:mechanical_crafting/crushing_wheel",
  "type": "create:mechanical_crafting",
  "result": {
    "id": "create:crushing_wheel",
    "count": 2
  },
  "_export_note": "Custom recipe type - codec serialization failed, basic info only",
  "_error": "Cannot encode unpacked recipe"
}
```

This ensures **all recipes are exported** (even custom ones), though custom recipes may have limited data compared to standard Minecraft recipes.

### Known Export Limitations

**Banner Patterns**: Items with banner patterns (decorated banners, shields) may not include component data in filenames/txt files due to registry validation constraints. The items themselves export correctly as PNG icons.

**Component Serialization**: Some items with complex registry-backed components may fail component serialization. These items still export successfully, but without component suffix in filenames.

### Contributing
* Before submitting a pull request containing a new feature, please discuss this first with one of the lead developers.
* When fixing an accepted bug, make sure to declare this in the issue so that no duplicate fixes exist.
* All code must comply to our coding conventions, be clean and must be well documented.

### Issues
* All bug reports and other issues are appreciated. If the issue is a crash, please include the FULL Forge log.
* Before submission, first check for duplicates, including already closed issues since those can then be re-opened.

### Branching Strategy

For every major Minecraft version, two branches exist:

* `master-{mc_version}`: Latest (potentially unstable) development.
* `release-{mc_version}`: Latest stable release for that Minecraft version. This is also tagged with all mod releases.

### Building and setting up a development environment

This mod uses [Project Lombok](http://projectlombok.org/) -- an annotation processor that allows us you to generate constructors, getters and setters using annotations -- to speed up recurring tasks and keep part of our codebase clean at the same time. Because of this it is advised that you install a plugin for your IDE that supports Project Lombok. Should you encounter any weird errors concerning missing getter or setter methods, it's probably because your code has not been processed by Project Lombok's processor. A list of Project Lombok plugins can be found [here](http://projectlombok.org/download.html).

### License
All code and images are licenced under the [MIT License](https://github.com/CyclopsMC/IconExporter/blob/master-1.12/LICENSE.txt)
