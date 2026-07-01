package com.blib.internal.common.territory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import com.blib.api.common.faction.v1.RelationshipState;
import com.blib.api.common.territory.v1.TerritoryContest;
import com.blib.api.common.territory.v1.TerritoryContestListener;
import com.blib.api.common.territory.v1.TerritoryContestManager;
import com.blib.api.common.territory.v1.TerritoryContestPowerProvider;
import com.blib.internal.common.faction.BLibFactionManager;
import com.blib.internal.common.storage.BLibDataStoreManager;
import com.blib.mod.common.registry.init.BLibTerritoryDataStoreTypes;

@ApiStatus.Internal
public class BLibTerritoryContestManager implements TerritoryContestManager {

    public static final BLibTerritoryContestManager INSTANCE = new BLibTerritoryContestManager();

    private static final int TICK_INTERVAL = 100;

    private static final int MAX_TICK_DELTA = 10;

    private final ArrayList<TerritoryContestPowerProvider> powerProviders = new ArrayList<>();

    private final ArrayList<TerritoryContestListener> listeners = new ArrayList<>();

    private BLibTerritoryContestManager() {}

    @Override
    public boolean canContest(ResourceLocation attacker, ResourceLocation defender) {
        return !attacker.equals(defender)
            && BLibFactionManager.INSTANCE.getRelationship(attacker, defender) == RelationshipState.HOSTILE;
    }

    @Override
    public boolean startContest(
        ServerLevel level,
        ChunkPos pos,
        ResourceLocation attacker,
        ResourceLocation defender,
        ResourceLocation reason
    ) {
        if (!canContest(attacker, defender) || !BLibTerritoryManager.INSTANCE.isClaimedBy(level, pos, defender)) {
            return false;
        }

        var store = getStore(level);
        var contest = new TerritoryContest(
            level.dimension().location(),
            pos.x,
            pos.z,
            attacker,
            defender,
            0,
            reason,
            level.getGameTime()
        );

        if (!store.putIfAbsent(contest)) {
            return false;
        }

        BLibTerritoryManager.INSTANCE.addClaim(level, pos, attacker);
        for (var listener : listeners) {
            listener.onContestStarted(level, contest);
        }

        return true;
    }

    @Override
    public boolean cancelContest(ServerLevel level, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender) {
        var contest = getStore(level).remove(level, pos, attacker, defender);

        if (contest == null) {
            return false;
        }

        BLibTerritoryManager.INSTANCE.removeClaim(level, pos, attacker);
        for (var listener : listeners) {
            listener.onContestCancelled(level, contest);
        }
        return true;
    }

    @Override
    public boolean addProgress(ServerLevel level, ChunkPos pos, ResourceLocation attacker, ResourceLocation defender, int delta) {
        if (delta == 0) {
            return false;
        }

        var store = getStore(level);
        var previous = store.get(level, pos, attacker, defender);

        if (previous == null) {
            return false;
        }

        var progress = previous.progress() + delta;
        if (progress >= DEFAULT_RESOLUTION_PROGRESS) {
            return resolveContest(level, pos, attacker, defender, attacker);
        }

        if (progress <= -DEFAULT_RESOLUTION_PROGRESS) {
            return resolveContest(level, pos, attacker, defender, defender);
        }

        var current = new TerritoryContest(
            previous.dimension(),
            previous.chunkX(),
            previous.chunkZ(),
            previous.attacker(),
            previous.defender(),
            progress,
            previous.reason(),
            level.getGameTime()
        );
        store.put(current);

        for (var listener : listeners) {
            listener.onContestProgressChanged(level, previous, current);
        }
        return true;
    }

    @Override
    public boolean resolveContest(
        ServerLevel level,
        ChunkPos pos,
        ResourceLocation attacker,
        ResourceLocation defender,
        ResourceLocation winner
    ) {
        var store = getStore(level);
        var contest = store.remove(level, pos, attacker, defender);

        if (contest == null) {
            return false;
        }

        var loser = winner.equals(attacker) ? defender : attacker;
        if (!BLibPlayerClaimManager.INSTANCE.removeClaimByFaction(level, pos, loser)) {
            BLibTerritoryManager.INSTANCE.removeClaim(level, pos, loser);
        }

        for (var listener : listeners) {
            listener.onContestResolved(level, contest, winner, loser);
        }
        return true;
    }

    @Override
    public Optional<TerritoryContest> getContest(
        ServerLevel level,
        ChunkPos pos,
        ResourceLocation attacker,
        ResourceLocation defender
    ) {
        return Optional.ofNullable(getStore(level).get(level, pos, attacker, defender));
    }

    @Override
    public Collection<TerritoryContest> getContests(ServerLevel level) {
        return getStore(level).allFor(level);
    }

    @Override
    public int getPower(ServerLevel level, ChunkPos pos, ResourceLocation factionId) {
        var total = 0;
        for (var provider : powerProviders) {
            total += Math.max(0, provider.getPower(level, pos, factionId));
        }
        return total;
    }

    @Override
    public void registerPowerProvider(TerritoryContestPowerProvider provider) {
        powerProviders.add(provider);
    }

    @Override
    public void registerListener(TerritoryContestListener listener) {
        listeners.add(listener);
    }

    public void tick(Level level) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || level.getGameTime() % TICK_INTERVAL != 0) {
            return;
        }

        for (var contest : getContests(serverLevel)) {
            tickContest(serverLevel, contest);
        }
    }

    private void tickContest(ServerLevel level, TerritoryContest contest) {
        var chunk = contest.chunkPos();

        if (!BLibTerritoryManager.INSTANCE.isClaimedBy(level, chunk, contest.attacker())) {
            cancelContest(level, chunk, contest.attacker(), contest.defender());
            return;
        }

        if (!BLibTerritoryManager.INSTANCE.isClaimedBy(level, chunk, contest.defender())) {
            resolveContest(level, chunk, contest.attacker(), contest.defender(), contest.attacker());
            return;
        }

        var attackerPower = getPower(level, chunk, contest.attacker());
        var defenderPower = getPower(level, chunk, contest.defender());
        var difference = attackerPower - defenderPower;

        if (difference == 0) {
            return;
        }

        var delta = Math.max(-MAX_TICK_DELTA, Math.min(MAX_TICK_DELTA, difference));
        addProgress(level, chunk, contest.attacker(), contest.defender(), delta);
    }

    private static TerritoryContestDataStore getStore(ServerLevel level) {
        return BLibDataStoreManager.INSTANCE.getGlobal(level.getServer(), BLibTerritoryDataStoreTypes.TERRITORY_CONTESTS);
    }
}
