package com.blib.api.common.territory.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public record TerritoryContest(
    ResourceLocation dimension,
    int chunkX,
    int chunkZ,
    ResourceLocation attacker,
    ResourceLocation defender,
    int progress,
    ResourceLocation reason,
    long lastUpdatedTick
) {

    public ChunkPos chunkPos() {
        return new ChunkPos(chunkX, chunkZ);
    }
}
