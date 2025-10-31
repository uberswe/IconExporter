This is a mod for exporting icons from Minecraft. I would like to customize this to automate exporting of icons and item/block information for different mods, datapacks and modpacks.

We want to export
- The modpack name
- The loader name
- The minecraft version
- All mods and mod versions
- All datapacks and version
- Every block and item icon
- Every block and item raw texture
- All recipe data
- Any block/item information that could be relevant when creating a wiki like attack damage or can it be waterlogged, does it get destroyed in fire. Store this as json.

All data should be exported in a folder structure allowing for exporting multiple modpacks. Information should be stored as json.

The goal is to automate this so that a modpack could be added and the client launched, opens a world automatically, exports everything and then closes.

Use try catch to handle errors and skip those items or blocks.