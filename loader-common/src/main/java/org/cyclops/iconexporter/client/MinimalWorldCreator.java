package org.cyclops.iconexporter.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import org.cyclops.cyclopscore.init.IModBase;

import java.io.File;
import java.io.IOException;

/**
 * Creates a minimal Minecraft world structure that can be loaded.
 * This avoids the complexity of using Minecraft's world generation APIs.
 */
public class MinimalWorldCreator {

    private static final String WORLD_NAME = "iconexporter_temp";

    /**
     * Create a minimal world in the saves directory.
     * @param savesDir The saves directory (typically gameDir/saves)
     * @param mod The mod instance for logging
     * @return The created world directory
     * @throws IOException if world creation fails
     */
    public static File createMinimalWorld(File savesDir, IModBase mod) throws IOException {
        File worldDir = new File(savesDir, WORLD_NAME);

        // Create world directory
        if (!worldDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            worldDir.mkdirs();
        }

        // Create level.dat with minimal world data
        File levelDat = new File(worldDir, "level.dat");
        createLevelDat(levelDat, mod);

        if (mod != null) {
            mod.log("Created minimal world at: " + worldDir.getAbsolutePath());
        }

        return worldDir;
    }

    /**
     * Create a minimal level.dat file.
     */
    private static void createLevelDat(File levelDat, IModBase mod) throws IOException {
        CompoundTag root = new CompoundTag();
        CompoundTag data = new CompoundTag();

        // Basic world settings
        data.putString("LevelName", WORLD_NAME);
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putLong("Time", 1000L); // Daytime
        data.putLong("DayTime", 1000L);
        data.putLong("RandomSeed", 0L);
        data.putInt("version", 19133); // Data version for 1.21.1
        data.putInt("GameType", GameType.CREATIVE.getId());
        data.putInt("Difficulty", Difficulty.PEACEFUL.getId());
        data.putBoolean("DifficultyLocked", true);
        data.putBoolean("hardcore", false);
        data.putBoolean("allowCommands", true);
        data.putBoolean("initialized", true);

        // World spawn
        data.putInt("SpawnX", 0);
        data.putInt("SpawnY", 64);
        data.putInt("SpawnZ", 0);
        data.putFloat("SpawnAngle", 0.0f);

        // Game rules for export-friendly environment
        CompoundTag gameRules = new CompoundTag();
        gameRules.putString("doDaylightCycle", "false");
        gameRules.putString("doWeatherCycle", "false");
        gameRules.putString("doMobSpawning", "false");
        gameRules.putString("doFireTick", "false");
        gameRules.putString("announceAdvancements", "false");
        data.put("GameRules", gameRules);

        // World generation settings (flat world)
        CompoundTag worldGenSettings = new CompoundTag();
        worldGenSettings.putLong("seed", 0L);
        worldGenSettings.putBoolean("generate_features", false);
        worldGenSettings.putBoolean("bonus_chest", false);
        data.put("WorldGenSettings", worldGenSettings);

        // Data packs
        CompoundTag dataPackSettings = new CompoundTag();
        CompoundTag enabled = new CompoundTag();
        enabled.putString("0", "vanilla");
        dataPackSettings.put("Enabled", enabled);
        data.put("DataPacks", dataPackSettings);

        // Version info
        CompoundTag version = new CompoundTag();
        version.putInt("Id", 3953); // 1.21.1
        version.putString("Name", "1.21.1");
        version.putBoolean("Snapshot", false);
        data.put("Version", version);

        root.put("Data", data);

        // Write to file
        try {
            NbtIo.writeCompressed(root, levelDat.toPath());
            if (mod != null) {
                mod.log("Created level.dat for world: " + WORLD_NAME);
            }
        } catch (IOException e) {
            if (mod != null) {
                mod.log("Failed to write level.dat: " + e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Check if the temp world already exists.
     */
    public static boolean worldExists(File savesDir) {
        File worldDir = new File(savesDir, WORLD_NAME);
        File levelDat = new File(worldDir, "level.dat");
        return worldDir.exists() && levelDat.exists();
    }

    /**
     * Get the temp world directory.
     */
    public static File getWorldDir(File savesDir) {
        return new File(savesDir, WORLD_NAME);
    }

    /**
     * Get the world name.
     */
    public static String getWorldName() {
        return WORLD_NAME;
    }
}
