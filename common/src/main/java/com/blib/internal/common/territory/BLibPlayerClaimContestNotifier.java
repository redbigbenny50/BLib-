package com.blib.internal.common.territory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;

import com.blib.api.common.territory.v1.TerritoryContest;
import com.blib.api.common.territory.v1.TerritoryContestListener;
import com.blib.internal.common.faction.BLibFactionManager;

@ApiStatus.Internal
public class BLibPlayerClaimContestNotifier implements TerritoryContestListener {

    public static final BLibPlayerClaimContestNotifier INSTANCE = new BLibPlayerClaimContestNotifier();

    private static final long REMINDER_INTERVAL_TICKS = 600;

    private final HashMap<ReminderKey, Long> lastReminderTicks = new HashMap<>();

    private BLibPlayerClaimContestNotifier() {}

    @Override
    public void onContestStarted(ServerLevel level, TerritoryContest contest) {
        notifyParticipant(level, contest, contest.attacker(), true, startMessage(level, contest, contest.defender()));
        notifyParticipant(level, contest, contest.defender(), true, startMessage(level, contest, contest.attacker()));
    }

    @Override
    public void onContestResolved(ServerLevel level, TerritoryContest contest, ResourceLocation winner, ResourceLocation loser) {
        notifyParticipant(level, contest, winner, true, resolvedMessage(level, contest, true));
        notifyParticipant(level, contest, loser, true, resolvedMessage(level, contest, false));
        clearReminder(contest);
    }

    @Override
    public void onContestCancelled(ServerLevel level, TerritoryContest contest) {
        var message = Component.literal("The contest at your claim " + chunkLabel(contest) + " ended.")
            .withStyle(ChatFormatting.YELLOW);
        notifyParticipant(level, contest, contest.attacker(), true, message);
        notifyParticipant(level, contest, contest.defender(), true, message);
        clearReminder(contest);
    }

    public void tick(Level level) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || level.getGameTime() % 20 != 0) {
            return;
        }

        for (var contest : BLibTerritoryContestManager.INSTANCE.getContests(serverLevel)) {
            remindParticipant(serverLevel, contest, contest.attacker());
            remindParticipant(serverLevel, contest, contest.defender());
        }
    }

    public void clear(MinecraftServer server) {
        lastReminderTicks.clear();
    }

    private void remindParticipant(ServerLevel level, TerritoryContest contest, ResourceLocation factionId) {
        var owner = BLibPlayerClaimManager.getPlayerClaimOwner(factionId);
        if (owner == null) {
            return;
        }

        var player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }

        var key = new ReminderKey(owner, contest.dimension(), contest.chunkX(), contest.chunkZ());
        var now = level.getGameTime();
        var lastReminder = lastReminderTicks.getOrDefault(key, Long.MIN_VALUE);
        if (now - lastReminder < REMINDER_INTERVAL_TICKS) {
            return;
        }

        player.displayClientMessage(contestedActionbar(player), true);
        lastReminderTicks.put(key, now);
    }

    private void notifyParticipant(
        ServerLevel level,
        TerritoryContest contest,
        ResourceLocation factionId,
        boolean actionbar,
        Component message
    ) {
        var player = playerForFaction(level, factionId);
        if (player == null) {
            return;
        }

        player.sendSystemMessage(message);
        if (actionbar) {
            player.displayClientMessage(contestedActionbar(player), true);
            lastReminderTicks.put(
                new ReminderKey(player.getUUID(), contest.dimension(), contest.chunkX(), contest.chunkZ()),
                level.getGameTime()
            );
        }
    }

    private @Nullable ServerPlayer playerForFaction(ServerLevel level, ResourceLocation factionId) {
        var owner = BLibPlayerClaimManager.getPlayerClaimOwner(factionId);
        return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
    }

    private static Component startMessage(ServerLevel level, TerritoryContest contest, ResourceLocation opponent) {
        return Component.literal(
            "Your claim at chunk "
                + chunkLabel(contest)
                + " in "
                + level.dimension().location()
                + " is being contested by "
                + factionName(level, opponent)
                + "."
        ).withStyle(ChatFormatting.RED);
    }

    private static Component resolvedMessage(ServerLevel level, TerritoryContest contest, boolean won) {
        var text = won
            ? "Your claim at chunk " + chunkLabel(contest) + " in " + level.dimension().location() + " held."
            : "Your claim at chunk " + chunkLabel(contest) + " in " + level.dimension().location() + " was lost.";
        return Component.literal(text).withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static Component contestedActionbar(ServerPlayer player) {
        return Component.literal("CONTESTED - Claim: " + player.getGameProfile().getName()).withStyle(ChatFormatting.RED);
    }

    private static String factionName(ServerLevel level, ResourceLocation factionId) {
        var owner = BLibPlayerClaimManager.getPlayerClaimOwner(factionId);
        if (owner != null) {
            var player = level.getServer().getPlayerList().getPlayer(owner);
            return player == null ? "player claim" : player.getGameProfile().getName();
        }

        var faction = BLibFactionManager.INSTANCE.get(factionId);
        return faction == null ? factionId.toString() : faction.name();
    }

    private static String chunkLabel(TerritoryContest contest) {
        return contest.chunkX() + ", " + contest.chunkZ();
    }

    private void clearReminder(TerritoryContest contest) {
        lastReminderTicks.keySet()
            .removeIf(key -> key.dimension.equals(contest.dimension())
                && key.chunkX == contest.chunkX()
                && key.chunkZ == contest.chunkZ());
    }

    private record ReminderKey(UUID owner, ResourceLocation dimension, int chunkX, int chunkZ) {}
}
