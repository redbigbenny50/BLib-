package com.blib.api.common.territory.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.Optional;

public interface TerritoryContestManager {

    int DEFAULT_RESOLUTION_PROGRESS = 100;

    boolean canContest(ResourceLocation attacker, ResourceLocation defender);

    boolean startContest(
        ServerLevel level,
        ChunkPos pos,
        ResourceLocation attacker,
        ResourceLocation defender,
        ResourceLocation reason
    );

    boolean cancelContest(ServerLevel level, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender);

    boolean addProgress(ServerLevel level, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender, int delta);

    boolean resolveContest(ServerLevel level, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender, ResourceLocation winner);

    Optional<TerritoryContest> getContest(
        ServerLevel level,
        ChunkPos pos,
        ResourceLocation attacker,
        ResourceLocation defender
    );

    Collection<TerritoryContest> getContests(ServerLevel level);

    int getPower(ServerLevel level, ChunkPos pos, ResourceLocation factionId);

    void registerPowerProvider(TerritoryContestPowerProvider provider);

    void registerListener(TerritoryContestListener listener);
}
