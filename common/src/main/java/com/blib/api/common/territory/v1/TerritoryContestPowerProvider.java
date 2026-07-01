package com.blib.api.common.territory.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

@FunctionalInterface
public interface TerritoryContestPowerProvider {

    int getPower(ServerLevel level, ChunkPos pos, ResourceLocation factionId);
}
