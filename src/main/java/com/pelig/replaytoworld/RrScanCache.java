package com.pelig.replaytoworld;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves each recording's scan result (which chunk positions it has, and where in the
 * recording each one is) so converting or combining the same recording again skips the scan.
 * Stored in .minecraft/replay-to-world-cache/. A cache file is only used if the recording's
 * path, size and modified time still match.
 */
final class RrScanCache {

    private static final int MAGIC = 0x52545753;
    private static final int VERSION = 1;
    private static final int MAX_FILES = 400;

    private RrScanCache() {}

    private static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("replay-to-world-cache");
    }

    private static Path fileFor(Path recording) throws Exception {
        String abs = recording.toAbsolutePath().normalize().toString();
        byte[] hash = MessageDigest.getInstance("SHA-1").digest(abs.getBytes(StandardCharsets.UTF_8));
        return dir().resolve(HexFormat.of().formatHex(hash) + ".scan");
    }

    static Map<String, Map<Long, Long>> load(Path recording) {
        try {
            Path file = fileFor(recording);
            if (!Files.isRegularFile(file)) return null;
            long size = Files.size(recording);
            long modified = Files.getLastModifiedTime(recording).toMillis();
            String abs = recording.toAbsolutePath().normalize().toString();
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
                if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;
                if (in.readLong() != size || in.readLong() != modified) return null;
                if (!in.readUTF().equals(abs)) return null;
                int dims = in.readInt();
                if (dims < 0 || dims > 4096) return null;
                Map<String, Map<Long, Long>> scan = new LinkedHashMap<>();
                for (int d = 0; d < dims; d++) {
                    String dim = in.readUTF();
                    int n = in.readInt();
                    if (n < 0) return null;
                    Map<Long, Long> positions = new LinkedHashMap<>(Math.max(16, (int) (n / 0.7f)));
                    for (int i = 0; i < n; i++) positions.put(in.readLong(), in.readLong());
                    scan.put(dim, positions);
                }
                if (in.readInt() != MAGIC) return null;
                return scan;
            }
        } catch (Exception e) {
            ReplayToWorldMod.LOGGER.warn("[ReplayToWorld] Ignoring saved scan for {}: {}", recording.getFileName(), e.toString());
            return null;
        }
    }

    static void save(Path recording, Map<String, Map<Long, Long>> scan) {
        try {
            Files.createDirectories(dir());
            Path file = fileFor(recording);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(Files.size(recording));
                out.writeLong(Files.getLastModifiedTime(recording).toMillis());
                out.writeUTF(recording.toAbsolutePath().normalize().toString());
                out.writeInt(scan.size());
                for (var dimEntry : scan.entrySet()) {
                    out.writeUTF(dimEntry.getKey());
                    out.writeInt(dimEntry.getValue().size());
                    for (var e : dimEntry.getValue().entrySet()) {
                        out.writeLong(e.getKey());
                        out.writeLong(e.getValue());
                    }
                }
                out.writeInt(MAGIC);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            prune();
        } catch (Exception e) {
            ReplayToWorldMod.LOGGER.warn("[ReplayToWorld] Couldn't save scan for {}: {}", recording.getFileName(), e.toString());
        }
    }

    private static void prune() {
        try (var stream = Files.list(dir())) {
            List<Path> files = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".scan")).toList());
            if (files.size() <= MAX_FILES) return;
            files.sort(Comparator.comparingLong(p -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }));
            for (int i = 0; i < files.size() - MAX_FILES; i++) Files.deleteIfExists(files.get(i));
        } catch (IOException ignored) {
        }
    }
}
