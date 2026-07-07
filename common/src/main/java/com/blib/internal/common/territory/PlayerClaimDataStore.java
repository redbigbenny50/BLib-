package com.blib.internal.common.territory;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.blib.api.common.storage.v1.DataStore;

@ApiStatus.Internal
public class PlayerClaimDataStore implements DataStore {

    public static final int BASE_CLAIM_SLOTS = 9;

    private static final String NBT_CLAIMS = "Claims";

    private static final String NBT_DIMENSION = "Dimension";

    private static final String NBT_CHUNK_X = "ChunkX";

    private static final String NBT_CHUNK_Z = "ChunkZ";

    private static final String NBT_OWNER = "Owner";

    private static final String NBT_EXTRA_SLOTS = "ExtraSlots";

    private static final String NBT_ALLIES = "Allies";

    private final Map<ClaimKey, UUID> ownersByClaim = new HashMap<>();

    private final Map<UUID, Integer> purchasedExtraSlots = new HashMap<>();

    private final Map<UUID, Set<UUID>> alliesByOwner = new HashMap<>();

    public boolean claim(ServerLevel level, ChunkPos chunk, UUID owner) {
        var key = ClaimKey.of(level.dimension(), chunk);
        if (ownersByClaim.containsKey(key)) {
            return false;
        }

        ownersByClaim.put(key, owner);
        return true;
    }

    public boolean unclaim(ServerLevel level, ChunkPos chunk, UUID owner) {
        var key = ClaimKey.of(level.dimension(), chunk);
        if (!owner.equals(ownersByClaim.get(key))) {
            return false;
        }

        ownersByClaim.remove(key);
        return true;
    }

    public @Nullable UUID remove(ServerLevel level, ChunkPos chunk) {
        return ownersByClaim.remove(ClaimKey.of(level.dimension(), chunk));
    }

    public @Nullable UUID ownerOf(ServerLevel level, ChunkPos chunk) {
        return ownersByClaim.get(ClaimKey.of(level.dimension(), chunk));
    }

    public int claimedCount(ServerLevel level, UUID owner) {
        var dimension = level.dimension().location().toString();
        var count = 0;

        for (var entry : ownersByClaim.entrySet()) {
            if (dimension.equals(entry.getKey().dimension()) && owner.equals(entry.getValue())) {
                count++;
            }
        }

        return count;
    }

    public int maxClaims(UUID owner) {
        return BASE_CLAIM_SLOTS + purchasedExtraSlots.getOrDefault(owner, 0);
    }

    public int purchasedExtraSlots(UUID owner) {
        return purchasedExtraSlots.getOrDefault(owner, 0);
    }

    public int nextBuyCost(UUID owner) {
        return 9 * (purchasedExtraSlots(owner) + 1);
    }

    public void addPurchasedSlot(UUID owner) {
        purchasedExtraSlots.merge(owner, 1, Integer::sum);
    }

    public boolean addAlly(UUID owner, UUID ally) {
        if (owner.equals(ally)) {
            return false;
        }
        return alliesByOwner.computeIfAbsent(owner, $owner -> new HashSet<>()).add(ally);
    }

    public boolean removeAlly(UUID owner, UUID ally) {
        var allies = alliesByOwner.get(owner);
        if (allies == null) {
            return false;
        }

        var changed = allies.remove(ally);
        if (allies.isEmpty()) {
            alliesByOwner.remove(owner);
        }
        return changed;
    }

    public boolean isAlly(UUID owner, UUID player) {
        var allies = alliesByOwner.get(owner);
        return allies != null && allies.contains(player);
    }

    public Set<UUID> allies(UUID owner) {
        var allies = alliesByOwner.get(owner);
        return allies == null ? Set.of() : Set.copyOf(allies);
    }

    public void syncAllTerritory(MinecraftServer server) {
        for (var entry : ownersByClaim.entrySet()) {
            var key = entry.getKey();
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(key.dimension()));
            var level = server.getLevel(dimension);

            if (level != null) {
                BLibTerritoryManager.INSTANCE.addClaim(
                    level,
                    new ChunkPos(key.x(), key.z()),
                    BLibPlayerClaimManager.playerClaimId(entry.getValue())
                );
            }
        }
    }

    public boolean importClaim(String dimension, int chunkX, int chunkZ, UUID owner) {
        return ownersByClaim.putIfAbsent(new ClaimKey(dimension, chunkX, chunkZ), owner) == null;
    }

    @Override
    public void load(CompoundTag compoundTag) {
        ownersByClaim.clear();
        purchasedExtraSlots.clear();
        alliesByOwner.clear();

        if (compoundTag.contains(NBT_CLAIMS, Tag.TAG_LIST)) {
            var claimTags = compoundTag.getList(NBT_CLAIMS, Tag.TAG_COMPOUND);
            for (var i = 0; i < claimTags.size(); i++) {
                var claimTag = claimTags.getCompound(i);
                if (claimTag.hasUUID(NBT_OWNER)) {
                    ownersByClaim.put(
                        new ClaimKey(
                            claimTag.getString(NBT_DIMENSION),
                            claimTag.getInt(NBT_CHUNK_X),
                            claimTag.getInt(NBT_CHUNK_Z)
                        ),
                        claimTag.getUUID(NBT_OWNER)
                    );
                }
            }
        }

        if (compoundTag.contains(NBT_EXTRA_SLOTS, Tag.TAG_COMPOUND)) {
            var extraSlotsTag = compoundTag.getCompound(NBT_EXTRA_SLOTS);
            for (var key : extraSlotsTag.getAllKeys()) {
                try {
                    purchasedExtraSlots.put(UUID.fromString(key), Math.max(0, extraSlotsTag.getInt(key)));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed legacy rows.
                }
            }
        }

        if (compoundTag.contains(NBT_ALLIES, Tag.TAG_COMPOUND)) {
            var alliesTag = compoundTag.getCompound(NBT_ALLIES);
            for (var ownerKey : alliesTag.getAllKeys()) {
                try {
                    var owner = UUID.fromString(ownerKey);
                    var allyList = alliesTag.getList(ownerKey, Tag.TAG_INT_ARRAY);
                    var allies = new HashSet<UUID>();
                    for (var i = 0; i < allyList.size(); i++) {
                        allies.add(UUIDUtil.uuidFromIntArray(allyList.getIntArray(i)));
                    }
                    if (!allies.isEmpty()) {
                        alliesByOwner.put(owner, allies);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed legacy rows.
                }
            }
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        var claimTags = new ListTag();
        ownersByClaim.forEach((key, owner) -> {
            var claimTag = new CompoundTag();
            claimTag.putString(NBT_DIMENSION, key.dimension());
            claimTag.putInt(NBT_CHUNK_X, key.x());
            claimTag.putInt(NBT_CHUNK_Z, key.z());
            claimTag.putUUID(NBT_OWNER, owner);
            claimTags.add(claimTag);
        });
        compoundTag.put(NBT_CLAIMS, claimTags);

        var extraSlotsTag = new CompoundTag();
        purchasedExtraSlots.forEach((owner, slots) -> extraSlotsTag.putInt(owner.toString(), slots));
        compoundTag.put(NBT_EXTRA_SLOTS, extraSlotsTag);

        var alliesTag = new CompoundTag();
        alliesByOwner.forEach((owner, allies) -> {
            var allyList = new ListTag();
            for (var ally : allies) {
                allyList.add(new IntArrayTag(UUIDUtil.uuidToIntArray(ally)));
            }
            alliesTag.put(owner.toString(), allyList);
        });
        compoundTag.put(NBT_ALLIES, alliesTag);
    }

    private record ClaimKey(
        String dimension,
        int x,
        int z
    ) {

        static ClaimKey of(ResourceKey<Level> dimension, ChunkPos chunk) {
            return new ClaimKey(dimension.location().toString(), chunk.x, chunk.z);
        }
    }
}
