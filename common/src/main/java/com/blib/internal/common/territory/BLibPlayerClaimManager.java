package com.blib.internal.common.territory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import com.blib.api.common.faction.v1.ClaimVisibility;
import com.blib.internal.common.faction.BLibFactionManager;
import com.blib.internal.common.storage.BLibDataStoreManager;
import com.blib.mod.BLib;
import com.blib.mod.common.registry.init.BLibFactionDataTypes;
import com.blib.mod.common.registry.init.BLibTerritoryDataStoreTypes;

@ApiStatus.Internal
public class BLibPlayerClaimManager {

    public static final BLibPlayerClaimManager INSTANCE = new BLibPlayerClaimManager();

    private BLibPlayerClaimManager() {}

    public boolean claim(ServerLevel level, ChunkPos chunk, UUID owner) {
        var store = getStore(level.getServer());

        if (store.claimedCount(level, owner) >= store.maxClaims(owner)) {
            return false;
        }

        if (!store.claim(level, chunk, owner)) {
            return false;
        }

        ensurePlayerClaimFaction(owner);
        BLibTerritoryManager.INSTANCE.addClaim(level, chunk, playerClaimId(owner));
        return true;
    }

    public boolean unclaim(ServerLevel level, ChunkPos chunk, UUID owner) {
        var store = getStore(level.getServer());

        if (!store.unclaim(level, chunk, owner)) {
            return false;
        }

        BLibTerritoryManager.INSTANCE.removeClaim(level, chunk, playerClaimId(owner));
        return true;
    }

    public boolean remove(ServerLevel level, ChunkPos chunk) {
        var owner = getStore(level.getServer()).remove(level, chunk);

        if (owner == null) {
            return false;
        }

        BLibTerritoryManager.INSTANCE.removeClaim(level, chunk, playerClaimId(owner));
        return true;
    }

    public boolean removeClaimByFaction(ServerLevel level, ChunkPos chunk, ResourceLocation factionId) {
        var owner = getPlayerClaimOwner(factionId);

        if (owner == null) {
            return false;
        }

        var currentOwner = ownerOf(level, chunk);
        if (!owner.equals(currentOwner)) {
            return false;
        }

        return remove(level, chunk);
    }

    public @Nullable UUID ownerOf(ServerLevel level, ChunkPos chunk) {
        return getStore(level.getServer()).ownerOf(level, chunk);
    }

    public boolean isClaimed(ServerLevel level, ChunkPos chunk) {
        return ownerOf(level, chunk) != null;
    }

    public int claimedCount(ServerLevel level, UUID owner) {
        return getStore(level.getServer()).claimedCount(level, owner);
    }

    public int maxClaims(MinecraftServer server, UUID owner) {
        return getStore(server).maxClaims(owner);
    }

    public int purchasedExtraSlots(MinecraftServer server, UUID owner) {
        return getStore(server).purchasedExtraSlots(owner);
    }

    public int nextBuyCost(MinecraftServer server, UUID owner) {
        return getStore(server).nextBuyCost(owner);
    }

    public void addPurchasedSlot(MinecraftServer server, UUID owner) {
        getStore(server).addPurchasedSlot(owner);
        ensurePlayerClaimFaction(owner);
    }

    public void syncAllTerritory(MinecraftServer server) {
        getStore(server).syncAllTerritory(server);
    }

    public boolean importClaim(MinecraftServer server, String dimension, int chunkX, int chunkZ, UUID owner) {
        ensurePlayerClaimFaction(owner);
        return getStore(server).importClaim(dimension, chunkX, chunkZ, owner);
    }

    public static ResourceLocation playerClaimId(UUID owner) {
        return BLib.MOD.resources().createLocation("player_claim/" + owner);
    }

    public static boolean isPlayerClaimId(ResourceLocation factionId) {
        return factionId.getNamespace().equals(BLib.MOD.id()) && factionId.getPath().startsWith("player_claim/");
    }

    private static @Nullable UUID getPlayerClaimOwner(ResourceLocation factionId) {
        if (!isPlayerClaimId(factionId)) {
            return null;
        }

        try {
            return UUID.fromString(factionId.getPath().substring("player_claim/".length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PlayerClaimDataStore getStore(MinecraftServer server) {
        return BLibDataStoreManager.INSTANCE.getGlobal(server, BLibTerritoryDataStoreTypes.PLAYER_CLAIMS);
    }

    private static void ensurePlayerClaimFaction(UUID owner) {
        var factionId = playerClaimId(owner);
        var existed = BLibFactionManager.INSTANCE.exists(factionId);
        var faction = BLibFactionManager.INSTANCE.getOrCreate(factionId, BLibFactionDataTypes.EMPTY);

        if (!existed) {
            faction.setName("Player Claim");
            faction.setColor(colorFor(owner));
        }

        faction.setClaimVisibility(ClaimVisibility.PUBLIC);
    }

    private static int colorFor(UUID owner) {
        var hash = owner.hashCode();
        return 0x404040 | (hash & 0xBFBFBF);
    }
}
