package com.blib.api.common.territory.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public interface TerritoryManager {

    boolean addClaim(ServerLevel level, ChunkPos pos, ResourceLocation factionId);

    boolean removeClaim(ServerLevel level, ChunkPos pos, ResourceLocation factionId);

    boolean transferClaim(ServerLevel level, ChunkPos pos, ResourceLocation from, ResourceLocation to);

    Set<ResourceLocation> getClaimants(ServerLevel level, ChunkPos pos);

    boolean isClaimed(ServerLevel level, ChunkPos pos);

    boolean isClaimedBy(ServerLevel level, ChunkPos pos, ResourceLocation factionId);

    boolean isContested(ServerLevel level, ChunkPos pos);

    Set<ChunkPos> getAdjacentClaimedChunks(ServerLevel level, ChunkPos pos, ResourceLocation factionId);

    Set<ChunkPos> getChunks(ServerLevel level, ResourceLocation factionId);

    Set<ChunkPos> getAllContestedChunks(ServerLevel level);

    Set<ChunkPos> getUnclaimedChunks(ServerLevel level, ChunkPos center, int radius);

    boolean claimPlayerChunk(ServerLevel level, ChunkPos pos, UUID owner);

    boolean unclaimPlayerChunk(ServerLevel level, ChunkPos pos, UUID owner);

    boolean removePlayerClaim(ServerLevel level, ChunkPos pos);

    @Nullable
    UUID getPlayerClaimOwner(ServerLevel level, ChunkPos pos);

    boolean isPlayerClaimed(ServerLevel level, ChunkPos pos);

    int getPlayerClaimCount(ServerLevel level, UUID owner);

    int getMaxPlayerClaims(MinecraftServer server, UUID owner);

    int getPurchasedPlayerClaimSlots(MinecraftServer server, UUID owner);

    int getNextPlayerClaimSlotCost(MinecraftServer server, UUID owner);

    void addPurchasedPlayerClaimSlot(MinecraftServer server, UUID owner);

    ResourceLocation getPlayerClaimFactionId(UUID owner);

    TerritoryContestManager contests();
}
