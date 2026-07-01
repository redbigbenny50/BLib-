package com.blib.internal.common.territory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.blib.api.common.storage.v1.DataStore;
import com.blib.api.common.territory.v1.TerritoryContest;

@ApiStatus.Internal
public class TerritoryContestDataStore implements DataStore {

    private static final String NBT_CONTESTS = "Contests";

    private static final String NBT_DIMENSION = "Dimension";

    private static final String NBT_CHUNK_X = "ChunkX";

    private static final String NBT_CHUNK_Z = "ChunkZ";

    private static final String NBT_ATTACKER = "Attacker";

    private static final String NBT_DEFENDER = "Defender";

    private static final String NBT_PROGRESS = "Progress";

    private static final String NBT_REASON = "Reason";

    private static final String NBT_LAST_UPDATED_TICK = "LastUpdatedTick";

    private final Map<String, TerritoryContest> contests = new HashMap<>();

    public boolean putIfAbsent(TerritoryContest contest) {
        return contests.putIfAbsent(key(contest), contest) == null;
    }

    public @Nullable TerritoryContest get(ServerLevel level, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender) {
        return contests.get(key(level.dimension().location(), pos, attacker, defender));
    }

    public void put(TerritoryContest contest) {
        contests.put(key(contest), contest);
    }

    public @Nullable TerritoryContest remove(ServerLevel level, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender) {
        return contests.remove(key(level.dimension().location(), pos, attacker, defender));
    }

    public Collection<TerritoryContest> allFor(ServerLevel level) {
        var dimension = level.dimension().location();
        var result = new ArrayList<TerritoryContest>();

        for (var contest : contests.values()) {
            if (dimension.equals(contest.dimension())) {
                result.add(contest);
            }
        }

        return List.copyOf(result);
    }

    @Override
    public void load(CompoundTag compoundTag) {
        contests.clear();

        if (!compoundTag.contains(NBT_CONTESTS, Tag.TAG_LIST)) {
            return;
        }

        var listTag = compoundTag.getList(NBT_CONTESTS, Tag.TAG_COMPOUND);
        for (var i = 0; i < listTag.size(); i++) {
            var contestTag = listTag.getCompound(i);
            var dimension = ResourceLocation.tryParse(contestTag.getString(NBT_DIMENSION));
            var attacker = ResourceLocation.tryParse(contestTag.getString(NBT_ATTACKER));
            var defender = ResourceLocation.tryParse(contestTag.getString(NBT_DEFENDER));
            var reason = ResourceLocation.tryParse(contestTag.getString(NBT_REASON));

            if (dimension == null || attacker == null || defender == null || reason == null) {
                continue;
            }

            var contest = new TerritoryContest(
                dimension,
                contestTag.getInt(NBT_CHUNK_X),
                contestTag.getInt(NBT_CHUNK_Z),
                attacker,
                defender,
                contestTag.getInt(NBT_PROGRESS),
                reason,
                contestTag.getLong(NBT_LAST_UPDATED_TICK)
            );
            contests.put(key(contest), contest);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        var listTag = new ListTag();

        for (var contest : contests.values()) {
            var contestTag = new CompoundTag();
            contestTag.putString(NBT_DIMENSION, contest.dimension().toString());
            contestTag.putInt(NBT_CHUNK_X, contest.chunkX());
            contestTag.putInt(NBT_CHUNK_Z, contest.chunkZ());
            contestTag.putString(NBT_ATTACKER, contest.attacker().toString());
            contestTag.putString(NBT_DEFENDER, contest.defender().toString());
            contestTag.putInt(NBT_PROGRESS, contest.progress());
            contestTag.putString(NBT_REASON, contest.reason().toString());
            contestTag.putLong(NBT_LAST_UPDATED_TICK, contest.lastUpdatedTick());
            listTag.add(contestTag);
        }

        compoundTag.put(NBT_CONTESTS, listTag);
    }

    private static String key(TerritoryContest contest) {
        return key(contest.dimension(), contest.chunkPos(), contest.attacker(), contest.defender());
    }

    private static String key(ResourceLocation dimension, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender) {
        return dimension + "|" + pos.x + "|" + pos.z + "|" + attacker + "|" + defender;
    }
}
