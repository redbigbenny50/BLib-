package com.blib.internal.client.territory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.blib.internal.client.territory.compat.XaeroWorldMapCompat;
import com.blib.mod.common.network.packet.S2CChunkClaimsSyncPayload;

@ApiStatus.Internal
public class ClientTerritoryCache {

    public static final ClientTerritoryCache INSTANCE = new ClientTerritoryCache();

    private final Map<ResourceLocation, Map<ChunkPos, List<ResourceLocation>>> factionsByDimension;

    private final Map<ResourceLocation, Map<ChunkPos, String>> playerOwnerNamesByDimension;

    private ClientTerritoryCache() {
        this.factionsByDimension = new HashMap<>();
        this.playerOwnerNamesByDimension = new HashMap<>();
    }

    public void updateChunk(
        ResourceLocation dimension,
        int chunkX,
        int chunkZ,
        List<ResourceLocation> factionIds,
        String playerOwnerName
    ) {
        var pos = new ChunkPos(chunkX, chunkZ);
        var factionsByChunk = factionsByDimension.computeIfAbsent(dimension, $ -> new HashMap<>());
        var playerOwnerNamesByChunk = playerOwnerNamesByDimension.computeIfAbsent(dimension, $ -> new HashMap<>());

        if (factionIds.isEmpty()) {
            factionsByChunk.remove(pos);
            playerOwnerNamesByChunk.remove(pos);

            if (factionsByChunk.isEmpty()) {
                factionsByDimension.remove(dimension);
            }
            if (playerOwnerNamesByChunk.isEmpty()) {
                playerOwnerNamesByDimension.remove(dimension);
            }
        } else {
            factionsByChunk.put(pos, List.copyOf(factionIds));
            if (playerOwnerName == null || playerOwnerName.isBlank()) {
                playerOwnerNamesByChunk.remove(pos);
            } else {
                playerOwnerNamesByChunk.put(pos, playerOwnerName);
            }
        }

        XaeroWorldMapCompat.invalidateChunk(chunkX, chunkZ);
    }

    public void updateChunks(ResourceLocation dimension, List<S2CChunkClaimsSyncPayload.Entry> entries) {
        for (var entry : entries) {
            updateChunk(dimension, entry.chunkX(), entry.chunkZ(), entry.factionIds(), entry.playerOwnerName());
        }
    }

    public void replaceArea(
        ResourceLocation dimension,
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ,
        List<S2CChunkClaimsSyncPayload.Entry> entries
    ) {
        var factionsByChunk = factionsByDimension.computeIfAbsent(dimension, $ -> new HashMap<>());
        var playerOwnerNamesByChunk = playerOwnerNamesByDimension.computeIfAbsent(dimension, $ -> new HashMap<>());
        factionsByChunk
            .keySet()
            .removeIf(pos -> pos.x >= minChunkX && pos.x <= maxChunkX && pos.z >= minChunkZ && pos.z <= maxChunkZ);
        playerOwnerNamesByChunk
            .keySet()
            .removeIf(pos -> pos.x >= minChunkX && pos.x <= maxChunkX && pos.z >= minChunkZ && pos.z <= maxChunkZ);

        for (var entry : entries) {
            var pos = new ChunkPos(entry.chunkX(), entry.chunkZ());

            if (entry.factionIds().isEmpty()) {
                factionsByChunk.remove(pos);
                playerOwnerNamesByChunk.remove(pos);
            } else {
                factionsByChunk.put(pos, List.copyOf(entry.factionIds()));
                if (entry.playerOwnerName() == null || entry.playerOwnerName().isBlank()) {
                    playerOwnerNamesByChunk.remove(pos);
                } else {
                    playerOwnerNamesByChunk.put(pos, entry.playerOwnerName());
                }
            }
        }

        if (factionsByChunk.isEmpty()) {
            factionsByDimension.remove(dimension);
        }
        if (playerOwnerNamesByChunk.isEmpty()) {
            playerOwnerNamesByDimension.remove(dimension);
        }

        XaeroWorldMapCompat.invalidateAll();
    }

    public List<ResourceLocation> getFactionIds(ResourceLocation dimension, ChunkPos pos) {
        return chunksForDimension(dimension).getOrDefault(pos, List.of());
    }

    public boolean isClaimed(ResourceLocation dimension, ChunkPos pos) {
        return chunksForDimension(dimension).containsKey(pos);
    }

    public boolean isContested(ResourceLocation dimension, ChunkPos pos) {
        var factions = chunksForDimension(dimension).get(pos);
        return factions != null && factions.size() > 1;
    }

    public String getPlayerOwnerName(ResourceLocation dimension, ChunkPos pos) {
        return playerOwnerNamesByDimension.getOrDefault(dimension, Map.of()).getOrDefault(pos, "");
    }

    public boolean hasContestedClaims() {
        for (var chunks : factionsByDimension.values()) {
            for (var factionIds : chunks.values()) {
                if (factionIds.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Read-only view of the underlying chunk → claimants map. Renderers iterate this each frame to draw claim overlays
     * / map cells. Mutations go through {@link #updateChunk}; the view doesn't support direct edits.
     */
    public Map<ChunkPos, List<ResourceLocation>> factionsByChunk(ResourceLocation dimension) {
        return Collections.unmodifiableMap(chunksForDimension(dimension));
    }

    /** Counts how many chunks in the cache list {@code factionId} as a claimant. Used by the inspector. */
    public int chunkCountForFaction(ResourceLocation dimension, ResourceLocation factionId) {
        var count = 0;
        for (var ids : chunksForDimension(dimension).values()) {
            if (ids.contains(factionId)) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        factionsByDimension.clear();
        playerOwnerNamesByDimension.clear();
    }

    private Map<ChunkPos, List<ResourceLocation>> chunksForDimension(ResourceLocation dimension) {
        return factionsByDimension.getOrDefault(dimension, Map.of());
    }
}
