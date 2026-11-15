package com.pelig.replaytoworld;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

final class WorldLayout {

    private static final int DATA_VERSION = net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
    private static final String VERSION_NAME = net.minecraft.SharedConstants.getCurrentVersion().name();

    private WorldLayout() {}

    static Path dimensionPath(Path root, String dimId) {
        return switch (dimId) {
            case "minecraft:overworld"  -> root;
            case "minecraft:the_nether" -> root.resolve("DIM-1");
            case "minecraft:the_end"    -> root.resolve("DIM1");
            default -> {
                String[] parts = dimId.split(":", 2);
                yield root.resolve("dimensions").resolve(parts[0]).resolve(parts[1]);
            }
        };
    }

    static Path importDimensionPath(Path world, String dimId) {
        return dimensionPath(world, dimId);
    }

    static String importNotice(Path world) {
        return null;
    }

    static void writeLevelData(Path worldFolder, String worldName, RrStartSpawn.StartPos start) throws IOException {
        CompoundTag data = new CompoundTag();
        data.putInt("DataVersion",  DATA_VERSION);
        data.putString("LevelName", worldName);
        data.putLong("LastPlayed",  System.currentTimeMillis());
        data.putInt("version",      19133);
        data.putInt("GameType",     1);
        data.putByte("Difficulty",  (byte) 2);
        data.putByte("DifficultyLocked", (byte) 0);
        data.putByte("initialized", (byte) 1);
        data.putByte("hardcore",    (byte) 0);
        data.putByte("allowCommands", (byte) 1);
        data.putByte("WasModded",   (byte) 1);

        data.putLong("Time", 0L);
        data.putLong("DayTime", 0L);
        data.putByte("raining",   (byte) 0);
        data.putByte("thundering", (byte) 0);
        data.putInt("rainTime",   0);
        data.putInt("thunderTime", 0);
        data.putInt("clearWeatherTime", 0);
        data.putInt("WanderingTraderSpawnChance", 25);
        data.putInt("WanderingTraderSpawnDelay",  24000);

        CompoundTag spawn = new CompoundTag();
        int[] spawnPos = start != null
                ? new int[]{(int) Math.floor(start.x()), (int) Math.floor(start.y()), (int) Math.floor(start.z())}
                : new int[]{8, -64, 8};
        spawn.put("pos", new net.minecraft.nbt.IntArrayTag(spawnPos));
        spawn.putFloat("pitch", start != null ? start.pitch() : 0.0f);
        spawn.putFloat("yaw",   start != null ? start.yaw() : 0.0f);
        spawn.putString("dimension", start != null ? start.dimension() : "minecraft:overworld");
        data.put("spawn", spawn);

        if (start != null) {
            CompoundTag player = new CompoundTag();
            ListTag playerPos = new ListTag();
            playerPos.add(DoubleTag.valueOf(start.x()));
            playerPos.add(DoubleTag.valueOf(start.y()));
            playerPos.add(DoubleTag.valueOf(start.z()));
            player.put("Pos", playerPos);
            ListTag playerRotation = new ListTag();
            playerRotation.add(FloatTag.valueOf(start.yaw()));
            playerRotation.add(FloatTag.valueOf(start.pitch()));
            player.put("Rotation", playerRotation);
            player.putString("Dimension", start.dimension());
            player.putInt("playerGameType", 1);
            data.put("Player", player);
        }

        CompoundTag version = new CompoundTag();
        version.putString("Name",   VERSION_NAME);
        version.putInt("Id",        DATA_VERSION);
        version.putString("Series", "main");
        version.putByte("Snapshot", (byte) 0);
        data.put("Version", version);

        CompoundTag gameRules = new CompoundTag();
        for (var entry : com.pelig.replaytoworld.config.RrGameRuleConfig.load().entrySet()) {
            String value = entry.getValue();
            if (value.equals("true") || value.equals("false")) {
                gameRules.putByte(entry.getKey(), (byte) (value.equals("true") ? 1 : 0));
            } else {
                try {
                    gameRules.putInt(entry.getKey(), Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    gameRules.putByte(entry.getKey(), (byte) 1);
                }
            }
        }
        data.put("game_rules", gameRules);

        CompoundTag dataPacks = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(StringTag.valueOf("vanilla"));
        dataPacks.put("Enabled", enabled);
        dataPacks.put("Disabled", new ListTag());
        data.put("DataPacks", dataPacks);

        CompoundTag dragonFight = new CompoundTag();
        dragonFight.putByte("PreviouslyKilled",    (byte) 0);
        dragonFight.putByte("NeedsStateScanning",  (byte) 1);
        dragonFight.putByte("DragonKilled",        (byte) 0);
        data.put("DragonFight", dragonFight);

        data.put("CustomBossEvents", new CompoundTag());
        data.put("ScheduledEvents",  new ListTag());
        ListTag serverBrands = new ListTag();
        serverBrands.add(StringTag.valueOf("fabric"));
        data.put("ServerBrands", serverBrands);

        CompoundTag worldGenSettings = new CompoundTag();
        worldGenSettings.putLong("seed", 0L);
        worldGenSettings.putByte("generate_features", (byte) 0);
        worldGenSettings.putByte("bonus_chest",       (byte) 0);
        CompoundTag dimensions = new CompoundTag();
        CompoundTag overworld  = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        CompoundTag gen = new CompoundTag();
        gen.putString("type", "minecraft:flat");
        CompoundTag genSettings = new CompoundTag();
        genSettings.put("layers",  new ListTag());
        genSettings.putString("biome", "minecraft:plains");
        genSettings.putByte("features", (byte) 0);
        genSettings.putByte("lakes",    (byte) 0);
        gen.put("settings", genSettings);
        overworld.put("generator", gen);
        dimensions.put("minecraft:overworld", overworld);

        CompoundTag nether = new CompoundTag();
        nether.putString("type", "minecraft:the_nether");
        CompoundTag netherGen = new CompoundTag();
        netherGen.putString("type", "minecraft:flat");
        CompoundTag netherSettings = new CompoundTag();
        netherSettings.put("layers", new ListTag());
        netherSettings.putString("biome", "minecraft:nether_wastes");
        netherSettings.putByte("features", (byte) 0);
        netherSettings.putByte("lakes", (byte) 0);
        netherGen.put("settings", netherSettings);
        nether.put("generator", netherGen);
        dimensions.put("minecraft:the_nether", nether);

        CompoundTag end = new CompoundTag();
        end.putString("type", "minecraft:the_end");
        CompoundTag endGen = new CompoundTag();
        endGen.putString("type", "minecraft:flat");
        CompoundTag endSettings = new CompoundTag();
        endSettings.put("layers", new ListTag());
        endSettings.putString("biome", "minecraft:the_end");
        endSettings.putByte("features", (byte) 0);
        endSettings.putByte("lakes", (byte) 0);
        endGen.put("settings", endSettings);
        end.put("generator", endGen);
        dimensions.put("minecraft:the_end", end);

        worldGenSettings.put("dimensions", dimensions);
        data.put("WorldGenSettings", worldGenSettings);

        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        NbtIo.writeCompressed(root, worldFolder.resolve("level.dat"));
        Files.write(worldFolder.resolve("session.lock"),
                ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array());
    }
}
