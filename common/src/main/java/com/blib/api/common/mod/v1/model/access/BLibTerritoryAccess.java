package com.blib.api.common.mod.v1.model.access;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

import com.blib.api.common.mod.v1.BLibMod;
import com.blib.api.common.territory.v1.TerritoryContestManager;
import com.blib.api.common.territory.v1.TerritoryManager;
import com.blib.internal.common.territory.BLibPlayerClaimManager;
import com.blib.internal.common.territory.BLibTerritoryContestManager;
import com.blib.internal.common.territory.BLibTerritoryManager;

public class BLibTerritoryAccess implements TerritoryManager {

    private final BLibMod mod;

    @ApiStatus.Internal
    public BLibTerritoryAccess(BLibMod mod) {
        this.mod = mod;
    }

    @Override
    public boolean addClaim(ServerLevel level, ChunkPos pos, ResourceLocation factionId) {
        return BLibTerritoryManager.INSTANCE.addClaim(level, pos, factionId);
    }

    @Override
    public boolean removeClaim(ServerLevel level, ChunkPos pos, ResourceLocation factionId) {
        return BLibTerritoryManager.INSTANCE.removeClaim(level, pos, factionId);
    }

    @Override
    public boolean transferClaim(ServerLevel level, ChunkPos pos, ResourceLocation from, ResourceLocation to) {
        return BLibTerritoryManager.INSTANCE.transferClaim(level, pos, from, to);
    }

    @Override
    public Set<ResourceLocation> getClaimants(ServerLevel level, ChunkPos pos) {
        return BLibTerritoryManager.INSTANCE.getClaimants(level, pos);
    }

    @Override
    public boolean isClaimed(ServerLevel level, ChunkPos pos) {
        return BLibTerritoryManager.INSTANCE.isClaimed(level, pos);
    }

    @Override
    public boolean isClaimedBy(ServerLevel level, ChunkPos pos, ResourceLocation factionId) {
        return BLibTerritoryManager.INSTANCE.isClaimedBy(level, pos, factionId);
    }

    @Override
    public boolean isContested(ServerLevel level, ChunkPos pos) {
        return BLibTerritoryManager.INSTANCE.isContested(level, pos);
    }

    @Override
    public Set<ChunkPos> getAdjacentClaimedChunks(ServerLevel level, ChunkPos pos, ResourceLocation factionId) {
        return BLibTerritoryManager.INSTANCE.getAdjacentClaimedChunks(level, pos, factionId);
    }

    @Override
    public Set<ChunkPos> getChunks(ServerLevel level, ResourceLocation factionId) {
        return BLibTerritoryManager.INSTANCE.getChunks(level, factionId);
    }

    @Override
    public Set<ChunkPos> getAllContestedChunks(ServerLevel level) {
        return BLibTerritoryManager.INSTANCE.getAllContestedChunks(level);
    }

    @Override
    public Set<ChunkPos> getUnclaimedChunks(ServerLevel level, ChunkPos center, int radius) {
        return BLibTerritoryManager.INSTANCE.getUnclaimedChunks(level, center, radius);
    }

    @Override
    public boolean claimPlayerChunk(ServerLevel level, ChunkPos pos, UUID owner) {
        return BLibPlayerClaimManager.INSTANCE.claim(level, pos, owner);
    }

    @Override
    public boolean unclaimPlayerChunk(ServerLevel level, ChunkPos pos, UUID owner) {
        return BLibPlayerClaimManager.INSTANCE.unclaim(level, pos, owner);
    }

    @Override
    public boolean removePlayerClaim(ServerLevel level, ChunkPos pos) {
        return BLibPlayerClaimManager.INSTANCE.remove(level, pos);
    }

    @Override
    public @Nullable UUID getPlayerClaimOwner(ServerLevel level, ChunkPos pos) {
        return BLibPlayerClaimManager.INSTANCE.ownerOf(level, pos);
    }

    @Override
    public boolean isPlayerClaimed(ServerLevel level, ChunkPos pos) {
        return BLibPlayerClaimManager.INSTANCE.isClaimed(level, pos);
    }

    @Override
    public int getPlayerClaimCount(ServerLevel level, UUID owner) {
        return BLibPlayerClaimManager.INSTANCE.claimedCount(level, owner);
    }

    @Override
    public int getMaxPlayerClaims(MinecraftServer server, UUID owner) {
        return BLibPlayerClaimManager.INSTANCE.maxClaims(server, owner);
    }

    @Override
    public int getPurchasedPlayerClaimSlots(MinecraftServer server, UUID owner) {
        return BLibPlayerClaimManager.INSTANCE.purchasedExtraSlots(server, owner);
    }

    @Override
    public int getNextPlayerClaimSlotCost(MinecraftServer server, UUID owner) {
        return BLibPlayerClaimManager.INSTANCE.nextBuyCost(server, owner);
    }

    @Override
    public void addPurchasedPlayerClaimSlot(MinecraftServer server, UUID owner) {
        BLibPlayerClaimManager.INSTANCE.addPurchasedSlot(server, owner);
    }

    @Override
    public ResourceLocation getPlayerClaimFactionId(UUID owner) {
        return BLibPlayerClaimManager.playerClaimId(owner);
    }

    @Override
    public TerritoryContestManager contests() {
        return BLibTerritoryContestManager.INSTANCE;
    }
}
