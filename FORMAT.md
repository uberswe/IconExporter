# IconExporter Data Format Specification

**Version:** 1.0
**Last Updated:** 2025-10-19
**Purpose:** Documentation for consuming IconExporter data in external applications

## Overview

IconExporter generates a comprehensive dataset about Minecraft modpacks, including metadata, recipes, item/block properties, textures, 3D models, and rendered icons. This document describes the structure and format of all exported data.

## Directory Structure

When `useStructuredOutput = true` (recommended), exports are organized as:

```
<gameDir>/exports/<modpack>/<mc_version>/<loader>/
├── meta/
│   ├── modpack.json          # Environment metadata
│   ├── mods.json             # List of all mods
│   └── datapacks.json        # List of all datapacks
├── icons/                    # Rendered PNG icons (16x16, 32x32, etc.)
│   ├── minecraft__diamond.png
│   ├── minecraft__stone.png
│   └── fluid__minecraft__water.png
├── textures/
│   ├── items/               # Raw item textures from resource packs
│   │   ├── minecraft/
│   │   │   ├── diamond.png
│   │   │   └── stone.png
│   │   └── modid/
│   └── blocks/              # Raw block textures from resource packs
│       └── minecraft/
│           ├── dirt.png
│           └── grass_block_top.png
├── models/
│   ├── items/               # Item model JSON definitions
│   │   └── minecraft/
│   │       ├── item/
│   │       │   └── diamond.json
│   │       └── block/
│   └── blocks/              # Block model JSON definitions
│       └── minecraft/
│           └── block/
│               └── stone.json
├── recipes/                 # All recipes organized by namespace
│   └── minecraft/
│       ├── diamond_sword.json
│       └── crafting_table.json
├── data/
│   ├── items/               # Detailed item metadata
│   │   └── minecraft/
│   │       ├── diamond.json
│   │       └── diamond_sword.json
│   └── blocks/              # Detailed block metadata
│       └── minecraft/
│           ├── stone.json
│           └── water.json
├── translations/            # Language/translation files
│   ├── en_us.json          # English (US) translations
│   └── translation_keys.json  # Filtered item/block translation keys
└── logs/
    └── errors.txt           # Export errors and warnings
```

**Path Variables:**
- `<gameDir>`: Minecraft instance directory
- `<modpack>`: From config (`modpackName`), e.g., "AllTheMods9"
- `<mc_version>`: Minecraft version, e.g., "1.21.1"
- `<loader>`: Mod loader type: "fabric", "forge", or "neoforge"

## File Formats

### 1. Environment Metadata (`meta/modpack.json`)

**Purpose:** Basic environment information about the modpack

**Schema:**
```json
{
  "modpack": "string",        // Modpack name
  "mc_version": "string",     // Minecraft version (e.g., "1.21.1")
  "loader": "string",         // Loader type: "fabric", "forge", "neoforge"
  "exported_at": "string"     // ISO-8601 timestamp
}
```

**Example:**
```json
{
  "modpack": "AllTheMods9",
  "mc_version": "1.21.1",
  "loader": "neoforge",
  "exported_at": "2025-10-19T14:30:00Z"
}
```

**Usage:**
- Display modpack information in your app
- Group exports by modpack/version/loader
- Show last export timestamp

---

### 2. Mods List (`meta/mods.json`)

**Purpose:** Complete list of installed mods with versions

**Schema:**
```json
[
  {
    "mod_id": "string",         // Unique mod identifier
    "version": "string",        // Mod version
    "display_name": "string"    // Human-readable name
  }
]
```

**Example:**
```json
[
  {
    "mod_id": "minecraft",
    "version": "1.21.1",
    "display_name": "Minecraft"
  },
  {
    "mod_id": "create",
    "version": "0.5.1",
    "display_name": "Create"
  }
]
```

**Usage:**
- Display installed mods list
- Filter items/blocks by mod
- Show mod attribution for items

---

### 3. Datapacks List (`meta/datapacks.json`)

**Purpose:** List of enabled datapacks

**Schema:**
```json
[
  {
    "id": "string",            // Datapack identifier
    "description": "string",   // Datapack description
    "source": "string"         // Source type (e.g., "file", "world")
  }
]
```

**Example:**
```json
[
  {
    "id": "file/custom_recipes",
    "description": "Custom crafting recipes",
    "source": "file"
  }
]
```

**Usage:**
- Show enabled datapacks
- Attribute custom recipes to datapacks

---

### 4. Items Data (`data/items/<namespace>/<path>.json`)

**Purpose:** Comprehensive metadata for each item

**Schema:**
```json
{
  "id": "string",                    // Full item ID (namespace:path)
  "namespace": "string",             // Mod namespace (e.g., "minecraft")
  "path": "string",                  // Item path (e.g., "diamond_sword")
  "translation_key": "string",       // Localization key (e.g., "item.minecraft.diamond_sword")
  "display_name": "string",          // Human-readable name (translated, e.g., "Diamond Sword")
  "tooltip": ["string"],             // Tooltip lines (optional, includes attribute descriptions)
  "max_stack_size": number,          // Maximum stack size (1-99)
  "max_damage": number,              // Maximum durability (0 if not damageable)
  "durability": number,              // Same as max_damage
  "is_damageable": boolean,          // True if item can be damaged
  "enchantment_value": number,       // Enchantability (0-15+)
  "is_fireproof": boolean,           // True if immune to fire/lava
  "rarity": "string",                // "common", "uncommon", "rare", "epic"
  "is_complex": boolean,             // True if has complex crafting logic
  "food": {                          // Only present if item is edible
    "nutrition": number,             // Hunger points restored
    "saturation_modifier": number,   // Saturation modifier
    "can_always_eat": boolean,       // Can eat when not hungry
    "eat_seconds": number            // Time to consume in seconds
  },
  "attribute_modifiers": [           // Attribute modifiers (armor, weapons)
    {
      "attribute": "string",         // Attribute ID (e.g., "minecraft:generic.attack_damage")
      "attribute_name": "string",    // Attribute path (e.g., "attack_damage")
      "amount": number,              // Modifier value
      "operation": "string",         // "add_value", "add_multiplied_base", "add_multiplied_total"
      "slot": "string"               // Equipment slot (e.g., "mainhand", "feet")
    }
  ],
  "tags": [                          // Item tags (for grouping/filtering)
    "string"                         // Tag IDs (e.g., "minecraft:swords")
  ]
}
```

**Example:**
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
  "is_complex": false,
  "attribute_modifiers": [
    {
      "attribute": "minecraft:generic.attack_damage",
      "attribute_name": "attack_damage",
      "amount": 7.0,
      "operation": "add_value",
      "slot": "mainhand"
    },
    {
      "attribute": "minecraft:generic.attack_speed",
      "attribute_name": "attack_speed",
      "amount": -2.4,
      "operation": "add_value",
      "slot": "mainhand"
    }
  ],
  "tags": [
    "minecraft:swords",
    "minecraft:weapon",
    "minecraft:enchantable/sword"
  ]
}
```

**Usage:**
- Display item stats in tooltips
- Filter items by tags (e.g., all swords)
- Show attack damage, durability, enchantability
- Display food properties for edible items
- Show equipment bonuses

---

### 5. Blocks Data (`data/blocks/<namespace>/<path>.json`)

**Purpose:** Comprehensive metadata for each block

**Schema:**
```json
{
  "id": "string",                    // Full block ID
  "namespace": "string",             // Mod namespace
  "path": "string",                  // Block path
  "translation_key": "string",       // Localization key (e.g., "block.minecraft.stone")
  "display_name": "string",          // Human-readable name (translated, e.g., "Stone")
  "explosion_resistance": number,    // Resistance to explosions
  "destroy_speed": number,           // Base mining speed (hardness)
  "friction": number,                // Slipperiness (0.6 = normal, 0.98 = ice)
  "speed_factor": number,            // Movement speed multiplier (default 1.0)
  "jump_factor": number,             // Jump height multiplier (default 1.0)
  "light_emission": number,          // Light level emitted (0-15)
  "is_air": boolean,                 // True if air/void
  "has_collision": boolean,          // True if has collision box
  "is_solid_render": boolean,        // True if solid rendering
  "can_occlude": boolean,            // True if can hide adjacent faces
  "requires_correct_tool": boolean,  // True if needs proper tool to drop
  "sound_type": "string",            // Sound type ID
  "map_color": "string",             // Map display color
  "waterloggable": boolean,          // True if can be waterlogged
  "properties": {                    // Block state properties
    "property_name": [               // Possible values for each property
      "string"
    ]
  },
  "possible_states": number,         // Total number of possible states
  "default_state": {                 // Default property values
    "property_name": "string"
  },
  "tags": [                          // Block tags
    "string"
  ]
}
```

**Example:**
```json
{
  "id": "minecraft:oak_stairs",
  "namespace": "minecraft",
  "path": "oak_stairs",
  "translation_key": "block.minecraft.oak_stairs",
  "display_name": "Oak Stairs",
  "explosion_resistance": 3.0,
  "destroy_speed": 2.0,
  "friction": 0.6,
  "speed_factor": 1.0,
  "jump_factor": 1.0,
  "light_emission": 0,
  "is_air": false,
  "has_collision": true,
  "is_solid_render": false,
  "can_occlude": true,
  "requires_correct_tool": false,
  "sound_type": "minecraft:wood",
  "map_color": "minecraft:wood",
  "waterloggable": true,
  "properties": {
    "facing": ["north", "south", "east", "west"],
    "half": ["top", "bottom"],
    "shape": ["straight", "inner_left", "inner_right", "outer_left", "outer_right"],
    "waterlogged": ["true", "false"]
  },
  "possible_states": 80,
  "default_state": {
    "facing": "north",
    "half": "bottom",
    "shape": "straight",
    "waterlogged": "false"
  },
  "tags": [
    "minecraft:stairs",
    "minecraft:wooden_stairs",
    "minecraft:mineable/axe"
  ]
}
```

**Usage:**
- Display block properties (hardness, blast resistance)
- Show special properties (waterloggable, emits light)
- Filter blocks by tags
- Display all possible block states
- Show mining tool requirements

---

### 6. Recipes (`recipes/<namespace>/<path>.json`)

**Purpose:** Complete recipe data in Minecraft's native format

**Format:** Standard Minecraft recipe JSON (uses Recipe.CODEC serialization)

**Common Recipe Types:**
- `minecraft:crafting_shaped` - Shaped crafting
- `minecraft:crafting_shapeless` - Shapeless crafting
- `minecraft:smelting` - Furnace smelting
- `minecraft:blasting` - Blast furnace
- `minecraft:smoking` - Smoker
- `minecraft:campfire_cooking` - Campfire
- `minecraft:stonecutting` - Stonecutter
- Custom mod recipe types

**Example (Shaped Crafting):**
```json
{
  "type": "minecraft:crafting_shaped",
  "category": "equipment",
  "pattern": [
    " D ",
    " D ",
    " S "
  ],
  "key": {
    "D": {
      "item": "minecraft:diamond"
    },
    "S": {
      "item": "minecraft:stick"
    }
  },
  "result": {
    "id": "minecraft:diamond_sword",
    "count": 1
  }
}
```

**Example (Smelting):**
```json
{
  "type": "minecraft:smelting",
  "category": "misc",
  "cookingtime": 200,
  "experience": 0.1,
  "ingredient": {
    "item": "minecraft:iron_ore"
  },
  "result": {
    "id": "minecraft:iron_ingot"
  }
}
```

**Usage:**
- Display crafting recipes in wiki
- Show smelting/processing recipes
- Calculate recipe costs
- Build crafting calculators
- Show recipe chains

---

### 7. Icons (`icons/`)

**Purpose:** Rendered PNG images of items, blocks, and fluids

**Naming Convention:**
- Items/Blocks: `<namespace>__<path>.png`
- Items with components: `<namespace>__<path>__<hash>.png` (if `fileNameHashComponents = true`)
- Fluids: `fluid__<namespace>__<path>.png`
- Component data: `<name>__<hash>.txt` (contains full component data)

**Examples:**
```
minecraft__diamond.png
minecraft__diamond_sword.png
minecraft__stone.png
fluid__minecraft__water.png
create__mechanical_press__a3f2b1c4.png (with NBT hash)
create__mechanical_press__a3f2b1c4.txt (NBT data)
```

**Properties:**
- Format: PNG with transparency
- Size: Configurable (default 16x16 or 32x32)
- Background: Transparent (removed during export)

**Usage:**
- Display item/block icons in UI
- Show fluid icons
- Match icons to items using filename

---

### 8. Textures (`textures/items/` and `textures/blocks/`)

**Purpose:** Raw texture files extracted from resource packs

**Directory Structure:**
```
textures/
├── items/
│   └── <namespace>/
│       └── <path>.png         # Follows resource pack structure
└── blocks/
    └── <namespace>/
        └── <path>.png
```

**Examples:**
```
textures/items/minecraft/diamond.png
textures/items/minecraft/diamond_sword.png
textures/blocks/minecraft/dirt.png
textures/blocks/minecraft/grass_block_top.png
```

**Properties:**
- Format: PNG (original from resource pack)
- Size: Variable (typically 16x16, can be higher with resource packs)
- Contains: Raw, unrendered texture

**Usage:**
- Display original textures
- Use in custom renderers
- Show texture atlases
- Compare rendered vs raw textures

---

### 9. Models (`models/items/` and `models/blocks/`)

**Purpose:** 3D model JSON definitions from resource packs

**Directory Structure:**
```
models/
├── items/
│   └── <namespace>/
│       └── item/
│           └── <name>.json
└── blocks/
    └── <namespace>/
        └── block/
            └── <name>.json
```

**Standard Model Format:**
```json
{
  "parent": "string",              // Parent model (inheritance)
  "textures": {                    // Texture references
    "layer0": "string",            // Texture path (e.g., "minecraft:item/diamond")
    "particle": "string"
  },
  "elements": [                    // 3D geometry (only in parent models)
    {
      "from": [x, y, z],          // Start coordinates (0-16)
      "to": [x, y, z],            // End coordinates (0-16)
      "faces": {                  // Face definitions
        "north": {
          "texture": "string",
          "uv": [x1, y1, x2, y2]
        }
      }
    }
  ],
  "display": {                     // Display transforms
    "gui": {
      "rotation": [x, y, z],
      "translation": [x, y, z],
      "scale": [x, y, z]
    }
  }
}
```

**Example:**
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/diamond_sword"
  }
}
```

**Usage:**
- Render 3D models in web apps (Three.js, Babylon.js)
- Show texture mappings
- Display model inheritance chains
- Create custom item/block viewers

---

### 10. Error Log (`logs/errors.txt`)

**Purpose:** List of errors that occurred during export

**Format:** Plain text, one error per line

**Example:**
```
Failed to export recipe minecraft:invalid_recipe: Recipe codec error
Failed to export item data: Item minecraft:missing_item not found
Failed to export texture minecraft:custom/missing_texture.png: File not found
```

**Usage:**
- Show export warnings in admin panel
- Identify incomplete data
- Debug export issues

---

## Data Relationships

### Item ID → Files Mapping

For item `minecraft:diamond_sword`:
- **Icon**: `icons/minecraft__diamond_sword.png`
- **Data**: `data/items/minecraft/diamond_sword.json`
- **Texture**: `textures/items/minecraft/diamond_sword.png`
- **Model**: `models/items/minecraft/item/diamond_sword.json`

### Linking Items to Mods

Use the namespace to determine which mod an item belongs to:
```javascript
const itemId = "create:mechanical_press";
const namespace = itemId.split(":")[0]; // "create"

// Find mod in mods.json
const mod = mods.find(m => m.mod_id === namespace);
```

### Recipe → Item Linking

Recipes reference items by ID in `ingredient` and `result` fields:
```javascript
// Recipe references
{
  "ingredient": { "item": "minecraft:iron_ingot" },
  "result": { "id": "minecraft:iron_block" }
}

// Load corresponding item data
const ingredientData = await fetch(`data/items/minecraft/iron_ingot.json`);
const resultData = await fetch(`data/items/minecraft/iron_block.json`);
```

### Block States

For blocks with multiple states (e.g., `minecraft:oak_stairs`):
- Only ONE icon/texture exists (default state)
- `properties` field lists all possible values
- `default_state` shows which values are used for the icon
- Total combinations = product of all property value counts

---

## Implementation Tips

### Loading the Dataset

**1. Start with metadata:**
```javascript
const modpack = await fetch('meta/modpack.json').then(r => r.json());
const mods = await fetch('meta/mods.json').then(r => r.json());
```

**2. Build an index:**
```javascript
// Index items by ID for fast lookup
const itemsIndex = {};
// Scan data/items/ directory and load on-demand
```

**3. Lazy-load heavy data:**
- Don't load all recipes/items at startup
- Load icons/textures on-demand
- Use pagination for large lists

### Search and Filtering

**By mod:**
```javascript
const modItems = allItems.filter(item => item.namespace === "create");
```

**By tag:**
```javascript
const swords = allItems.filter(item =>
  item.tags?.includes("minecraft:swords")
);
```

**By property:**
```javascript
const tools = allItems.filter(item => item.max_damage > 0);
const food = allItems.filter(item => item.food !== undefined);
```

### Display Patterns

**Item card:**
- Icon from `icons/`
- Name from `path` (convert underscores to spaces, title case)
- Stats from `data/items/`
- Mod badge from `namespace` + `mods.json`

**Recipe viewer:**
- Load recipe JSON from `recipes/`
- Resolve ingredient/result IDs to icons
- Show crafting grid or processing UI
- Link to item details

**Wiki page:**
- Item icon and name
- Full stats table from JSON
- Tags list
- Recipes that use/produce this item
- Mod attribution

---

## Version Compatibility

**This format is for Minecraft 1.21+**

Changes in Minecraft versions may affect:
- Recipe format (new recipe types)
- Item/block properties (new attributes)
- Component system (replaces NBT in 1.20.5+)

**Handling multiple versions:**
- Use `mc_version` from `modpack.json`
- Store different exports in separate directories
- Version-specific logic in your app

---

## Performance Considerations

### Large Modpacks

A large modpack (e.g., All The Mods 9) may have:
- 20,000+ items
- 10,000+ recipes
- 5,000+ textures
- 50+ GB total export size

**Recommendations:**
- Use virtual scrolling for item lists
- Implement search/filter BEFORE rendering
- Lazy-load images (intersection observer)
- Cache loaded data in memory/IndexedDB
- Use CDN for icons if hosting publicly

### File Formats

All JSON files use **pretty-printing** (indented) for human readability.

**Optimization options:**
- Minify JSON for production
- Gzip compress (typically 70-80% reduction)
- Convert to binary format (MessagePack, CBOR)

---

## Example Use Cases

### 1. Wiki/Documentation Site
- Display all items with icons
- Show detailed stats and recipes
- Search and filter by mod/tag
- Link related items/recipes

### 2. Recipe Calculator
- Load all recipes
- Calculate crafting costs
- Show ingredient trees
- Optimize crafting paths

### 3. Modpack Comparison Tool
- Compare exports from different modpacks
- Show added/removed items
- Identify conflicts or duplicates

### 4. Interactive Item Browser
- Grid view with icons
- Click for details modal
- Tag-based filtering
- Mod-based grouping

---

## Common Issues

### Missing Icons
**Cause:** Item has complex NBT/components
**Solution:** Check for `<name>__<hash>.png` and corresponding `.txt` file

### Broken Textures
**Cause:** Texture not found in resource packs
**Solution:** Use fallback texture, check `errors.txt`

### Recipe Format Unknown
**Cause:** Custom mod recipe type
**Solution:** Use `type` field to identify, render as "custom recipe"

### Large Dataset Performance
**Cause:** Too many items loaded at once
**Solution:** Implement pagination, virtual scrolling, lazy loading

---

## Support and Questions

For questions about this format or the export tool:
- GitHub: https://github.com/CyclopsMC/IconExporter
- Discord: Check repository for invite link

For questions about using this data in your application, include:
- Modpack name and version
- Export settings used
- Specific files/formats causing issues
- Your tech stack (React, Vue, etc.)

---

## Appendix: Complete Type Definitions (TypeScript)

```typescript
// meta/modpack.json
interface ModpackMetadata {
  modpack: string;
  mc_version: string;
  loader: "fabric" | "forge" | "neoforge";
  exported_at: string; // ISO-8601
}

// meta/mods.json
interface ModInfo {
  mod_id: string;
  version: string;
  display_name: string;
}

// meta/datapacks.json
interface DatapackInfo {
  id: string;
  description: string;
  source: string;
}

// data/items/<namespace>/<path>.json
interface ItemData {
  id: string;
  namespace: string;
  path: string;
  max_stack_size: number;
  max_damage: number;
  durability: number;
  is_damageable: boolean;
  enchantment_value: number;
  is_fireproof: boolean;
  rarity: "common" | "uncommon" | "rare" | "epic";
  is_complex: boolean;
  food?: {
    nutrition: number;
    saturation_modifier: number;
    can_always_eat: boolean;
    eat_seconds: number;
  };
  attribute_modifiers?: Array<{
    attribute: string;
    attribute_name: string;
    amount: number;
    operation: "add_value" | "add_multiplied_base" | "add_multiplied_total";
    slot: string;
  }>;
  tags?: string[];
}

// data/blocks/<namespace>/<path>.json
interface BlockData {
  id: string;
  namespace: string;
  path: string;
  explosion_resistance: number;
  destroy_speed: number;
  friction: number;
  speed_factor: number;
  jump_factor: number;
  light_emission: number;
  is_air: boolean;
  has_collision: boolean;
  is_solid_render: boolean;
  can_occlude: boolean;
  requires_correct_tool: boolean;
  sound_type: string;
  map_color: string;
  waterloggable: boolean;
  properties: Record<string, string[]>;
  possible_states: number;
  default_state: Record<string, string>;
  tags?: string[];
}

// recipes/<namespace>/<path>.json (example types)
interface CraftingShapedRecipe {
  type: "minecraft:crafting_shaped";
  category: string;
  pattern: string[];
  key: Record<string, { item: string } | { tag: string }>;
  result: {
    id: string;
    count?: number;
  };
}

interface SmeltingRecipe {
  type: "minecraft:smelting" | "minecraft:blasting" | "minecraft:smoking";
  category: string;
  cookingtime: number;
  experience: number;
  ingredient: { item: string } | { tag: string };
  result: {
    id: string;
    count?: number;
  };
}
```

---

**End of Format Specification**
