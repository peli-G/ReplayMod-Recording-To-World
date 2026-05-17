package com.pelig.replaytoworld;

import com.pelig.replaytoworld.mixin.ClientboundLevelChunkPacketDataAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.ChunkPos;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

public class McprWorldConverter {

    private static final int    DATA_VERSION = net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
    private static final String VERSION_NAME = net.minecraft.SharedConstants.getCurrentVersion().name();

    public static volatile boolean running = false;

    public static boolean start(Path mcprPath, RegistryAccess registryAccess, Minecraft mc) {
        if (running) return false;
        running = true;
        Thread t = new Thread(() -> {
            try {
                convert(mcprPath, registryAccess, mc);
            } catch (Exception e) {
                ReplayToWorldMod.LOGGER.error("[ReplayToWorld] Conversion failed", e);
                clearActionBar(mc);
                sendMessage(mc, "§c[ReplayToWorld] Failed: " + e.getMessage() + " — see log.");
            } finally {
                running = false;
            }
        }, "replay-to-world");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private static void convert(Path mcprPath, RegistryAccess registryAccess, Minecraft mc) throws Exception {
        if (!Files.exists(mcprPath)) {
            sendMessage(mc, "§c[ReplayToWorld] File not found: " + mcprPath);
            return;
        }

        sendMessage(mc, "§a[ReplayToWorld] Opening " + mcprPath.getFileName() + " ...");

        var gamePacketCodec = GameProtocols.CLIENTBOUND_TEMPLATE
                .bind(RegistryFriendlyByteBuf.decorator(registryAccess))
                .codec();

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 1 — Scan the tmcpr stream
        //
        // .tmcpr format: [4-byte BE timestamp][4-byte BE length][N bytes packet]
        // We decode every packet but store NOTHING for chunk packets beyond
        // their byte offset in the stream and (dim, chunkPos) key.
        // Last occurrence of each (dim, chunkPos) pair wins.
        //
        // Memory: O(unique chunks) — a few longs/strings per chunk, not
        // decoded PalettedContainers. For 20K chunks that's a few MB.
        // ═══════════════════════════════════════════════════════════════════
        sendMessage(mc, "§a[ReplayToWorld] Phase 1/2: Scanning packets...");

        // (dim, chunkPosLong) → byte offset of the winning packet's start
        Map<String, Map<Long, Long>> dimPosToWinnerOffset = new LinkedHashMap<>();
        String[] currentDimension = {"minecraft:overworld"};
        int packetCount = 0;

        try (ZipFile zip = new ZipFile(mcprPath.toFile())) {
            var tmcprEntry = zip.getEntry("recording.tmcpr");
            if (tmcprEntry == null) {
                sendMessage(mc, "§c[ReplayToWorld] No recording.tmcpr found inside " + mcprPath.getFileName());
                return;
            }

            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(zip.getInputStream(tmcprEntry), 1 << 20))) {

                long bytePos = 0;
                while (true) {
                    long packetStart = bytePos;

                    int timestamp;
                    try { timestamp = dis.readInt(); } catch (EOFException e) { break; }
                    bytePos += 4;

                    int length;
                    try { length = dis.readInt(); } catch (EOFException e) { break; }
                    bytePos += 4;

                    if (length <= 0 || length > 2_097_152) {
                        ReplayToWorldMod.LOGGER.warn(
                                "[ReplayToWorld] Suspicious packet length {} at offset {}, stopping", length, packetStart);
                        break;
                    }

                    byte[] data = new byte[length];
                    try { dis.readFully(data); } catch (EOFException e) { break; }
                    bytePos += length;
                    packetCount++;

                    if (packetCount % 10_000 == 0) {
                        setActionBar(mc, "Scanning packets", packetCount, -1);
                    }

                    var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registryAccess);
                    try {
                        var pkt = gamePacketCodec.decode(buf);

                        if (pkt instanceof ClientboundLevelChunkWithLightPacket cp) {
                            // Record the byte offset of this packet — don't keep cp itself
                            long chunkPosLong = ChunkPos.asLong(cp.getX(), cp.getZ());
                            dimPosToWinnerOffset
                                    .computeIfAbsent(currentDimension[0], k -> new LinkedHashMap<>())
                                    .put(chunkPosLong, packetStart);

                        } else if (pkt instanceof ClientboundLoginPacket lp) {
                            currentDimension[0] = parseDimension(lp.commonPlayerSpawnInfo().dimension().toString());

                        } else if (pkt instanceof ClientboundRespawnPacket rp) {
                            currentDimension[0] = parseDimension(rp.commonPlayerSpawnInfo().dimension().toString());
                        }

                    } catch (Exception ignored) {}
                }
            }
        }

        int totalChunks = dimPosToWinnerOffset.values().stream().mapToInt(Map::size).sum();
        sendMessage(mc, "§a[ReplayToWorld] Phase 1 done: scanned " + packetCount + " packets → "
                + totalChunks + " unique chunks across " + dimPosToWinnerOffset.size() + " dimension(s).");

        if (dimPosToWinnerOffset.isEmpty()) {
            sendMessage(mc, "§c[ReplayToWorld] No chunks found — nothing to export.");
            return;
        }

        // Build a flat set of winner offsets and a reverse map offset → dim
        Set<Long>         winnerOffsets    = new HashSet<>();
        Map<Long, String> winnerOffsetToDim = new HashMap<>();
        for (var dimEntry : dimPosToWinnerOffset.entrySet()) {
            for (long offset : dimEntry.getValue().values()) {
                winnerOffsets.add(offset);
                winnerOffsetToDim.put(offset, dimEntry.getKey());
            }
        }

        // Create output world
        String baseName = mcprPath.getFileName().toString()
                .replaceAll("\\.[^.]+$", "")
                .replaceAll("[\\\\/:*?\"<>|]", "_");
        Path savesDir = mc.gameDirectory.toPath().resolve("saves");
        Path worldPath = savesDir.resolve(baseName);
        if (Files.exists(worldPath)) {
            int i = 1;
            while (Files.exists(savesDir.resolve(baseName + "_" + i))) i++;
            worldPath = savesDir.resolve(baseName + "_" + i);
        }
        Files.createDirectories(worldPath);
        writeLevelDat(worldPath, baseName);

        var biomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME);
        var ops = registryAccess.createSerializationContext(NbtOps.INSTANCE);
        var blockCodec = PalettedContainer.codecRW(
                BlockState.CODEC,
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                Blocks.AIR.defaultBlockState());
        com.mojang.serialization.Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec =
                PalettedContainer.codecRO(
                        biomeRegistry.holderByNameCodec(),
                        Strategy.createForBiomes(biomeRegistry.asHolderIdMap()),
                        biomeRegistry.getOrThrow(Biomes.PLAINS));

        // Pre-create dimension directories
        Map<String, Path> dimPaths = new LinkedHashMap<>();
        for (String dimId : dimPosToWinnerOffset.keySet()) {
            Path dp = resolveDimPath(worldPath, dimId);
            Files.createDirectories(dp.resolve("region"));
            Files.createDirectories(dp.resolve("entities"));
            dimPaths.put(dimId, dp);
        }

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 2 — Re-read the stream, decode and write only winner packets
        //
        // For non-winners we call skipFully() — zero allocation, no decode.
        // For winners we decode once and write immediately; the object is
        // eligible for GC as soon as the loop advances.
        //
        // Peak heap: one decoded chunk packet at a time regardless of how
        // many chunks the replay contains.
        // ═══════════════════════════════════════════════════════════════════
        sendMessage(mc, "§a[ReplayToWorld] Phase 2/2: Writing chunks...");

        Map<String, Map<Long, RegionFileWriter>> regionWriters = new HashMap<>();
        Map<String, Map<Long, RegionFileWriter>> entityWriters = new HashMap<>();
        int written = 0, skipped = 0;

        try (ZipFile zip = new ZipFile(mcprPath.toFile())) {
            var tmcprEntry = zip.getEntry("recording.tmcpr");
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(zip.getInputStream(tmcprEntry), 1 << 20))) {

                long bytePos = 0;
                while (true) {
                    long packetStart = bytePos;

                    int timestamp;
                    try { timestamp = dis.readInt(); } catch (EOFException e) { break; }
                    bytePos += 4;

                    int length;
                    try { length = dis.readInt(); } catch (EOFException e) { break; }
                    bytePos += 4;

                    if (length <= 0 || length > 2_097_152) break;

                    if (!winnerOffsets.contains(packetStart)) {
                        // Not a winner — skip cheaply without allocating
                        skipFully(dis, length);
                        bytePos += length;
                        continue;
                    }

                    byte[] data = new byte[length];
                    try { dis.readFully(data); } catch (EOFException e) { break; }
                    bytePos += length;

                    if (written % 16 == 0) {
                        setActionBar(mc, "Writing chunks", written, totalChunks);
                    }

                    String dim = winnerOffsetToDim.get(packetStart);
                    var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registryAccess);
                    try {
                        var pkt = gamePacketCodec.decode(buf);
                        if (pkt instanceof ClientboundLevelChunkWithLightPacket cp) {
                            ChunkPos pos      = new ChunkPos(cp.getX(), cp.getZ());
                            long regionKey    = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
                            String regionName = "r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca";
                            Path dimPath      = dimPaths.get(dim);

                            CompoundTag chunkNbt  = buildChunkNbt(cp, ops, blockCodec, biomeCodec, registryAccess);
                            CompoundTag entityNbt = buildEntityChunkNbt(pos);

                            final Path dPath  = dimPath;
                            final String fDim = dim;
                            regionWriters.computeIfAbsent(fDim, k -> new HashMap<>())
                                    .computeIfAbsent(regionKey, k -> {
                                        try { return new RegionFileWriter(dPath.resolve("region").resolve(regionName)); }
                                        catch (Exception e) { throw new RuntimeException(e); }
                                    }).write(pos, chunkNbt);
                            entityWriters.computeIfAbsent(fDim, k -> new HashMap<>())
                                    .computeIfAbsent(regionKey, k -> {
                                        try { return new RegionFileWriter(dPath.resolve("entities").resolve(regionName)); }
                                        catch (Exception e) { throw new RuntimeException(e); }
                                    }).write(pos, entityNbt);

                            written++;
                        }
                    } catch (Exception e) {
                        ReplayToWorldMod.LOGGER.warn("[ReplayToWorld] Skipped chunk at offset {}: {}", packetStart, e.getMessage());
                        skipped++;
                    }
                }
            }
        }

        for (var m : regionWriters.values()) for (var w : m.values()) w.close();
        for (var m : entityWriters.values()) for (var w : m.values()) w.close();

        clearActionBar(mc);
        sendMessage(mc, "§a[ReplayToWorld] Done! " + written + " chunks written"
                + (skipped > 0 ? " (" + skipped + " skipped)" : "")
                + " across " + dimPosToWinnerOffset.size() + " dimension(s)"
                + " → saves/" + worldPath.getFileName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String parseDimension(String toString) {
        int slash = toString.indexOf('/');
        if (slash >= 0) return toString.substring(slash + 2, toString.length() - 1).trim();
        return "minecraft:overworld";
    }

    private static Path resolveDimPath(Path worldPath, String dimId) {
        return switch (dimId) {
            case "minecraft:overworld"  -> worldPath;
            case "minecraft:the_nether" -> worldPath.resolve("DIM-1");
            case "minecraft:the_end"    -> worldPath.resolve("DIM1");
            default -> {
                String[] parts = dimId.split(":", 2);
                yield worldPath.resolve("dimensions").resolve(parts[0]).resolve(parts[1]);
            }
        };
    }

    private static void skipFully(InputStream is, long n) throws IOException {
        while (n > 0) {
            long s = is.skip(n);
            if (s > 0) { n -= s; continue; }
            int chunk = (int) Math.min(n, 8192);
            byte[] discard = new byte[chunk];
            int r = is.read(discard, 0, chunk);
            if (r < 0) break;
            n -= r;
        }
    }

    // ── Chunk NBT ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static CompoundTag buildChunkNbt(
            ClientboundLevelChunkWithLightPacket packet,
            com.mojang.serialization.DynamicOps<Tag> ops,
            com.mojang.serialization.Codec<PalettedContainer<BlockState>> blockCodec,
            com.mojang.serialization.Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec,
            RegistryAccess registryAccess) throws Exception {

        int cx = packet.getX();
        int cz = packet.getZ();
        var chunkData = packet.getChunkData();

        byte[] rawBuffer  = ((ClientboundLevelChunkPacketDataAccessor)(Object) chunkData).rtw$getBuffer();
        List<?> beInfoList = ((ClientboundLevelChunkPacketDataAccessor)(Object) chunkData).rtw$getBlockEntitiesData();

        var rfbb = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(rawBuffer), registryAccess);

        int minSectionY  = -4;
        int sectionCount = 24;
        ListTag sections = new ListTag();

        var biomeRegistry  = registryAccess.lookupOrThrow(Registries.BIOME);
        Holder<Biome> defaultBiome = biomeRegistry.getOrThrow(Biomes.PLAINS);

        var lightData   = packet.getLightData();
        var skyMask     = lightData.getSkyYMask();
        var blockMask   = lightData.getBlockYMask();
        var emptySky    = lightData.getEmptySkyYMask();
        var emptyBlock  = lightData.getEmptyBlockYMask();
        var skyArrays   = lightData.getSkyUpdates();
        var blockArrays = lightData.getBlockUpdates();

        Map<Integer, byte[]> skyBySection   = new HashMap<>();
        Map<Integer, byte[]> blockBySection = new HashMap<>();
        int skyIdx = 0, blockIdx = 0;
        int totalLightSections = sectionCount + 2;
        for (int li = 0; li < totalLightSections; li++) {
            if (skyMask.get(li)   && skyIdx   < skyArrays.size())   skyBySection.put(li,   skyArrays.get(skyIdx++));
            if (blockMask.get(li) && blockIdx < blockArrays.size()) blockBySection.put(li, blockArrays.get(blockIdx++));
        }

        for (int si = 0; si < sectionCount && rfbb.isReadable(); si++) {
            int sectionY = si + minSectionY;
            CompoundTag sec = new CompoundTag();
            sec.putInt("Y", sectionY);

            rfbb.readShort();

            PalettedContainer<BlockState> states = new PalettedContainer<>(
                    Blocks.AIR.defaultBlockState(),
                    Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
            states.read(rfbb);

            PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(
                    defaultBiome,
                    Strategy.createForBiomes(biomeRegistry.asHolderIdMap()));
            biomes.read(rfbb);

            blockCodec.encodeStart(ops, states).result().ifPresent(t -> sec.put("block_states", t));
            biomeCodec.encodeStart(ops, biomes).result().ifPresent(t -> sec.put("biomes", t));

            int li = si + 1;
            byte[] skyArr   = skyBySection.get(li);
            byte[] blockArr = blockBySection.get(li);
            if (skyArr   != null) sec.putByteArray("SkyLight",   skyArr);
            else if (!emptySky.get(li))   sec.putByteArray("SkyLight",   new byte[2048]);
            if (blockArr != null) sec.putByteArray("BlockLight", blockArr);
            else if (!emptyBlock.get(li)) sec.putByteArray("BlockLight", new byte[2048]);

            sections.add(sec);
        }

        ListTag blockEntities = new ListTag();
        for (var beInfoRaw : beInfoList) {
            try {
                Class<?> beInfoClass = beInfoRaw.getClass();
                var fPackedXZ = beInfoClass.getDeclaredField("packedXZ"); fPackedXZ.setAccessible(true);
                var fY        = beInfoClass.getDeclaredField("y");         fY.setAccessible(true);
                var fType     = beInfoClass.getDeclaredField("type");      fType.setAccessible(true);
                var fTag      = beInfoClass.getDeclaredField("tag");       fTag.setAccessible(true);

                int packedXZ = (int) fPackedXZ.get(beInfoRaw);
                int beY      = (int) fY.get(beInfoRaw);
                var beType   = (net.minecraft.world.level.block.entity.BlockEntityType<?>) fType.get(beInfoRaw);
                var rawTag   = (CompoundTag) fTag.get(beInfoRaw);

                int worldX = cx * 16 + (packedXZ >> 4);
                int worldZ = cz * 16 + (packedXZ & 0xF);
                CompoundTag beTag = rawTag != null ? rawTag.copy() : new CompoundTag();
                beTag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(beType).toString());
                beTag.putInt("x", worldX); beTag.putInt("y", beY); beTag.putInt("z", worldZ);
                beTag.putByte("keepPacked", (byte) 0);
                blockEntities.add(beTag);
            } catch (Exception ignored) {}
        }

        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion",    DATA_VERSION);
        chunk.putInt("xPos",           cx);
        chunk.putInt("zPos",           cz);
        chunk.putInt("yPos",           minSectionY);
        chunk.putString("Status",      "full");
        chunk.putLong("LastUpdate",    0L);
        chunk.putLong("InhabitedTime", 0L);
        chunk.put("sections",          sections);
        chunk.put("block_entities",    blockEntities);
        chunk.put("Heightmaps",        new CompoundTag());
        chunk.putByte("isLightOn",     (byte) 1);
        return chunk;
    }

    private static CompoundTag buildEntityChunkNbt(ChunkPos pos) {
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", DATA_VERSION);
        chunk.putIntArray("Position", new int[]{pos.x, pos.z});
        chunk.put("Entities", new ListTag());
        return chunk;
    }

    // ── level.dat ─────────────────────────────────────────────────────────────

    private static void writeLevelDat(Path worldFolder, String worldName) throws IOException {
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
        spawn.put("pos", new net.minecraft.nbt.IntArrayTag(new int[]{8, -64, 8}));
        spawn.putFloat("pitch", 0.0f);
        spawn.putFloat("yaw",   0.0f);
        spawn.putString("dimension", "minecraft:overworld");
        data.put("spawn", spawn);

        CompoundTag version = new CompoundTag();
        version.putString("Name",   VERSION_NAME);
        version.putInt("Id",        DATA_VERSION);
        version.putString("Series", "main");
        version.putByte("Snapshot", (byte) 0);
        data.put("Version", version);

        CompoundTag gameRules = new CompoundTag();
        gameRules.putByte("minecraft:advance_time", (byte) 0);
        gameRules.putByte("minecraft:advance_weather", (byte) 0);
        gameRules.putByte("minecraft:allow_entering_nether_using_portals", (byte) 1);
        gameRules.putByte("minecraft:block_drops", (byte) 1);
        gameRules.putByte("minecraft:block_explosion_drop_decay", (byte) 1);
        gameRules.putByte("minecraft:command_block_output", (byte) 1);
        gameRules.putByte("minecraft:command_blocks_work", (byte) 1);
        gameRules.putByte("minecraft:drowning_damage", (byte) 1);
        gameRules.putByte("minecraft:elytra_movement_check", (byte) 1);
        gameRules.putByte("minecraft:ender_pearls_vanish_on_death", (byte) 1);
        gameRules.putByte("minecraft:entity_drops", (byte) 1);
        gameRules.putByte("minecraft:fall_damage", (byte) 1);
        gameRules.putByte("minecraft:fire_damage", (byte) 1);
        gameRules.putInt("minecraft:fire_spread_radius_around_player", 128);
        gameRules.putByte("minecraft:forgive_dead_players", (byte) 1);
        gameRules.putByte("minecraft:freeze_damage", (byte) 1);
        gameRules.putByte("minecraft:global_sound_events", (byte) 1);
        gameRules.putByte("minecraft:immediate_respawn", (byte) 0);
        gameRules.putByte("minecraft:keep_inventory", (byte) 0);
        gameRules.putByte("minecraft:limited_crafting", (byte) 0);
        gameRules.putByte("minecraft:lava_source_conversion", (byte) 0);
        gameRules.putByte("minecraft:locator_bar", (byte) 1);
        gameRules.putByte("minecraft:log_admin_commands", (byte) 1);
        gameRules.putInt("minecraft:max_block_modifications", 32768);
        gameRules.putInt("minecraft:max_command_forks", 65536);
        gameRules.putInt("minecraft:max_command_sequence_length", 65536);
        gameRules.putInt("minecraft:max_entity_cramming", 24);
        gameRules.putInt("minecraft:max_snow_accumulation_height", 1);
        gameRules.putByte("minecraft:mob_drops", (byte) 1);
        gameRules.putByte("minecraft:mob_explosion_drop_decay", (byte) 1);
        gameRules.putByte("minecraft:mob_griefing", (byte) 1);
        gameRules.putByte("minecraft:natural_health_regeneration", (byte) 1);
        gameRules.putByte("minecraft:player_movement_check", (byte) 1);
        gameRules.putInt("minecraft:players_nether_portal_creative_delay", 0);
        gameRules.putInt("minecraft:players_nether_portal_default_delay", 80);
        gameRules.putInt("minecraft:players_sleeping_percentage", 100);
        gameRules.putByte("minecraft:projectiles_can_break_blocks", (byte) 1);
        gameRules.putByte("minecraft:pvp", (byte) 1);
        gameRules.putByte("minecraft:raids", (byte) 1);
        gameRules.putInt("minecraft:random_tick_speed", 3);
        gameRules.putByte("minecraft:reduced_debug_info", (byte) 0);
        gameRules.putInt("minecraft:respawn_radius", 10);
        gameRules.putByte("minecraft:send_command_feedback", (byte) 1);
        gameRules.putByte("minecraft:show_advancement_messages", (byte) 1);
        gameRules.putByte("minecraft:show_death_messages", (byte) 1);
        gameRules.putByte("minecraft:spawn_mobs", (byte) 0);
        gameRules.putByte("minecraft:spawn_monsters", (byte) 0);
        gameRules.putByte("minecraft:spawn_patrols", (byte) 0);
        gameRules.putByte("minecraft:spawn_phantoms", (byte) 0);
        gameRules.putByte("minecraft:spawn_wandering_traders", (byte) 0);
        gameRules.putByte("minecraft:spawn_wardens", (byte) 0);
        gameRules.putByte("minecraft:spawner_blocks_work", (byte) 1);
        gameRules.putByte("minecraft:spectators_generate_chunks", (byte) 1);
        gameRules.putByte("minecraft:spread_vines", (byte) 1);
        gameRules.putByte("minecraft:tnt_explodes", (byte) 1);
        gameRules.putByte("minecraft:tnt_explosion_drop_decay", (byte) 0);
        gameRules.putByte("minecraft:universal_anger", (byte) 0);
        gameRules.putByte("minecraft:water_source_conversion", (byte) 1);
        data.put("game_rules", gameRules);

        CompoundTag dataPacks = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(StringTag.valueOf("vanilla"));
        dataPacks.put("Enabled", enabled);
        dataPacks.put("Disabled", new ListTag());
        data.put("DataPacks", dataPacks);

        CompoundTag dragonFight = new CompoundTag();
        dragonFight.putByte("PreviouslyKilled",   (byte) 0);
        dragonFight.putByte("NeedsStateScanning", (byte) 1);
        dragonFight.putByte("DragonKilled",       (byte) 0);
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
        worldGenSettings.put("dimensions", dimensions);
        data.put("WorldGenSettings", worldGenSettings);

        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        NbtIo.writeCompressed(root, worldFolder.resolve("level.dat"));
        Files.write(worldFolder.resolve("session.lock"),
                ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array());
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private static void sendMessage(Minecraft mc, String message) {
        ReplayToWorldMod.LOGGER.info(message);
        mc.execute(() -> {
            if (mc.gui != null) mc.gui.getChat().addMessage(Component.literal(message));
        });
    }

    private static void setActionBar(Minecraft mc, String stage, int done, int total) {
        String msg;
        if (total < 0) {
            msg = "§a[ReplayToWorld] §f" + stage + " §7— §f" + done + " packets...";
        } else {
            int clamped = Math.min(done, total);
            int pct     = clamped * 100 / total;
            int filled  = Math.max(0, Math.min(20, pct / 5));
            String bar  = "█".repeat(filled) + "░".repeat(20 - filled);
            msg = "§a[ReplayToWorld] §f" + stage + " §7[§a" + bar + "§7] §f"
                    + pct + "% §7(" + done + "/" + total + ")";
        }
        mc.execute(() -> {
            if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal(msg), false);
        });
    }

    private static void clearActionBar(Minecraft mc) {
        mc.execute(() -> {
            if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal(""), false);
        });
    }
}
