package com.pelig.replaytoworld;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds where a ReplayMod recording started, so a converted world can spawn the player there
 * instead of at a fixed point. The server sends the player's starting position in the first
 * absolute player position packet after the login packet, and the login packet holds the
 * dimension. Only the beginning of the recording is read.
 */
final class RrStartSpawn {

    record StartPos(double x, double y, double z, float yaw, float pitch, String dimension) {}

    private static final int MAX_PACKET_SIZE = 2_097_152;
    private static final int MAX_PACKETS = 50_000;

    private RrStartSpawn() {}

    static boolean isVanillaDimension(String dimension) {
        return "minecraft:overworld".equals(dimension)
                || "minecraft:the_nether".equals(dimension)
                || "minecraft:the_end".equals(dimension);
    }

    static StartPos read(
            Path recording,
            net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> codec,
            RegistryAccess registryAccess) {
        try (ZipFile zip = new ZipFile(recording.toFile())) {
            ZipEntry entry = zip.getEntry("recording.tmcpr");
            if (entry == null) return null;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(zip.getInputStream(entry), 1 << 16))) {
                String dimension = "minecraft:overworld";
                for (int i = 0; i < MAX_PACKETS; i++) {
                    try {
                        in.readInt();
                    } catch (EOFException e) {
                        return null;
                    }
                    int length;
                    try {
                        length = in.readInt();
                    } catch (EOFException e) {
                        return null;
                    }
                    if (length <= 0 || length > MAX_PACKET_SIZE) return null;
                    byte[] data = new byte[length];
                    try {
                        in.readFully(data);
                    } catch (EOFException e) {
                        return null;
                    }
                    try {
                        var pkt = codec.decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registryAccess));
                        if (pkt instanceof ClientboundLoginPacket lp) {
                            dimension = McprWorldConverter.parseDimension(lp.commonPlayerSpawnInfo().dimension().toString());
                        } else if (pkt instanceof ClientboundRespawnPacket rp) {
                            dimension = McprWorldConverter.parseDimension(rp.commonPlayerSpawnInfo().dimension().toString());
                        } else if (pkt instanceof ClientboundPlayerPositionPacket pp && pp.relatives().isEmpty()) {
                            Vec3 position = pp.change().position();
                            float yaw = pp.change().yRot();
                            float pitch = pp.change().xRot();
                            if (Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)) {
                                return new StartPos(position.x, position.y, position.z,
                                        Float.isFinite(yaw) ? yaw : 0f,
                                        Float.isFinite(pitch) ? pitch : 0f,
                                        dimension);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            ReplayToWorldMod.LOGGER.warn("[ReplayToWorld] Couldn't read the recording's start position from {}: {}",
                    recording.getFileName(), e.toString());
        }
        return null;
    }
}
