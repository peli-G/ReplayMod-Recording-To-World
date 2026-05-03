package com.pelig.replaytoworld.mixin;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ClientboundLevelChunkPacketData.class)
public interface ClientboundLevelChunkPacketDataAccessor {

    @Accessor("buffer")
    byte[] rtw$getBuffer();

    // BlockEntityInfo is private; use List<?> and access fields via reflection
    @Accessor("blockEntitiesData")
    List<?> rtw$getBlockEntitiesData();
}
