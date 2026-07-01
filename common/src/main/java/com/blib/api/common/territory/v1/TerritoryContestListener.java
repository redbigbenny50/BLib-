package com.blib.api.common.territory.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface TerritoryContestListener {

    default void onContestStarted(ServerLevel level, TerritoryContest contest) {}

    default void onContestProgressChanged(ServerLevel level, TerritoryContest previous, TerritoryContest current) {}

    default void onContestResolved(
        ServerLevel level,
        TerritoryContest contest,
        ResourceLocation winner,
        ResourceLocation loser
    ) {}

    default void onContestCancelled(ServerLevel level, TerritoryContest contest) {}
}
