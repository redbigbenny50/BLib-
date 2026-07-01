package com.blib.mod.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import com.blib.mod.BLib;

public final class BLibPlayerClaimCommands {

    private BLibPlayerClaimCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("claim")
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.literal("chunk").executes(ctx -> claimChunk(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("unclaim").executes(ctx -> unclaimChunk(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("buy").executes(ctx -> buySlot(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrException())));
    }

    private static int claimChunk(ServerPlayer player) {
        var level = player.serverLevel();
        var chunk = player.chunkPosition();
        var owner = BLib.MOD.territory().getPlayerClaimOwner(level, chunk);

        if (owner != null) {
            player.sendSystemMessage(Component.literal("This chunk is already claimed.").withStyle(ChatFormatting.RED));
            return 0;
        }

        var claimed = BLib.MOD.territory().getPlayerClaimCount(level, player.getUUID());
        var max = BLib.MOD.territory().getMaxPlayerClaims(level.getServer(), player.getUUID());
        if (claimed >= max) {
            player.sendSystemMessage(
                Component.literal("Claim limit reached: " + claimed + "/" + max + ". Use /claim buy.")
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        if (!BLib.MOD.territory().claimPlayerChunk(level, chunk, player.getUUID())) {
            player.sendSystemMessage(Component.literal("Unable to claim this chunk.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(
            Component.literal("Claimed chunk " + chunk.x + ", " + chunk.z + " (" + (claimed + 1) + "/" + max + ").")
                .withStyle(ChatFormatting.GREEN)
        );
        return 1;
    }

    private static int unclaimChunk(ServerPlayer player) {
        var level = player.serverLevel();
        var chunk = player.chunkPosition();
        var owner = BLib.MOD.territory().getPlayerClaimOwner(level, chunk);

        if (owner == null) {
            player.sendSystemMessage(Component.literal("This chunk is not claimed.").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        if (!owner.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("This chunk is claimed by someone else.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BLib.MOD.territory().unclaimPlayerChunk(level, chunk, player.getUUID());
        player.sendSystemMessage(Component.literal("Unclaimed chunk " + chunk.x + ", " + chunk.z + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int buySlot(ServerPlayer player) {
        var server = player.getServer();
        var cost = BLib.MOD.territory().getNextPlayerClaimSlotCost(server, player.getUUID());

        if (player.getInventory().countItem(Items.DIAMOND) < cost) {
            player.sendSystemMessage(
                Component.literal("You need " + cost + " diamonds to buy the next claim slot.")
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        player.getInventory().clearOrCountMatchingItems(stack -> stack.is(Items.DIAMOND), cost, player.inventoryMenu.getCraftSlots());
        BLib.MOD.territory().addPurchasedPlayerClaimSlot(server, player.getUUID());
        player.sendSystemMessage(
            Component.literal("Bought one claim slot. Max claims: " + BLib.MOD.territory().getMaxPlayerClaims(server, player.getUUID()) + ".")
                .withStyle(ChatFormatting.GREEN)
        );
        return 1;
    }

    private static int info(ServerPlayer player) {
        var level = player.serverLevel();
        var chunk = new ChunkPos(player.blockPosition());
        var owner = BLib.MOD.territory().getPlayerClaimOwner(level, chunk);
        var claimed = BLib.MOD.territory().getPlayerClaimCount(level, player.getUUID());
        var max = BLib.MOD.territory().getMaxPlayerClaims(level.getServer(), player.getUUID());

        player.sendSystemMessage(Component.literal("Claims: " + claimed + "/" + max).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(
            Component.literal("Next slot cost: " + BLib.MOD.territory().getNextPlayerClaimSlotCost(level.getServer(), player.getUUID()) + " diamonds.")
                .withStyle(ChatFormatting.YELLOW)
        );
        player.sendSystemMessage(
            Component.literal(
                owner == null
                    ? "Current chunk is unclaimed."
                    : owner.equals(player.getUUID()) ? "Current chunk is claimed by you." : "Current chunk is claimed."
            )
                .withStyle(owner == null ? ChatFormatting.GRAY : ChatFormatting.GREEN)
        );
        return 1;
    }
}
