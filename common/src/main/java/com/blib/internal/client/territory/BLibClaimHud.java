package com.blib.internal.client.territory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;

import com.blib.internal.client.faction.ClientFactionCache;
import com.blib.internal.client.territory.compat.XaeroWorldMapCompat;
import com.blib.internal.client.territory.compat.xaero.BLibChunkHighlighter;

@ApiStatus.Internal
public final class BLibClaimHud {

    private static final int HUD_UPDATE_INTERVAL_TICKS = 10;

    private static int ticksSinceHudUpdate = 0;

    private static boolean lastBlinkPhase = BLibChunkHighlighter.contestedBlinkPhase();

    public static void tick() {
        refreshContestedBlink();

        ticksSinceHudUpdate++;
        if (ticksSinceHudUpdate < HUD_UPDATE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceHudUpdate = 0;

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;

        if (player == null || level == null) {
            return;
        }

        var dimension = level.dimension().location();
        var chunk = new ChunkPos(player.blockPosition());
        var factionIds = ClientTerritoryCache.INSTANCE.getFactionIds(dimension, chunk);

        if (factionIds.isEmpty()) {
            return;
        }

        var playerOwnerName = ClientTerritoryCache.INSTANCE.getPlayerOwnerName(dimension, chunk);
        var label = playerOwnerName.isBlank()
            ? factionName(factionIds.getFirst())
            : playerOwnerName;

        if (label.isBlank()) {
            return;
        }

        var contested = factionIds.size() > 1;
        var message = contested
            ? Component.literal("CONTESTED - Claim: " + label).withStyle(ChatFormatting.RED)
            : Component.literal("Claim: " + label).withStyle(ChatFormatting.YELLOW);

        player.displayClientMessage(message, true);
    }

    private static void refreshContestedBlink() {
        var blinkPhase = BLibChunkHighlighter.contestedBlinkPhase();
        if (blinkPhase == lastBlinkPhase) {
            return;
        }
        lastBlinkPhase = blinkPhase;

        if (ClientTerritoryCache.INSTANCE.hasContestedClaims() && XaeroWorldMapCompat.isLoaded()) {
            BLibChunkHighlighter.invalidateAll();
        }
    }

    private static String factionName(net.minecraft.resources.ResourceLocation factionId) {
        var metadata = ClientFactionCache.INSTANCE.get(factionId);

        return metadata == null ? factionId.toString() : metadata.name();
    }

    private BLibClaimHud() {
        throw new UnsupportedOperationException();
    }
}
