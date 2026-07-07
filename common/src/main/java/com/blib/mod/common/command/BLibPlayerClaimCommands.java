package com.blib.mod.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

import com.blib.mod.BLib;

public final class BLibPlayerClaimCommands {

    private BLibPlayerClaimCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("claim")
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.literal("chunk").executes(ctx -> claimChunk(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("unclaim").executes(ctx -> unclaimChunk(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("buy").executes(ctx -> buySlot(ctx.getSource().getPlayerOrException())))
            .then(
                Commands.literal("color")
                    .then(
                        Commands.argument("hex", StringArgumentType.word())
                            .executes(ctx -> setColor(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "hex")))
                    )
            )
            .then(
                Commands.literal("ally")
                    .then(Commands.literal("list").executes(ctx -> listAllies(ctx.getSource().getPlayerOrException())))
                    .then(
                        Commands.literal("add")
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes(
                                        ctx -> addAlly(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"))
                                    )
                            )
                    )
                    .then(
                        Commands.literal("remove")
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes(
                                        ctx -> removeAlly(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"))
                                    )
                            )
                    )
            )
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
            Component.literal(
                "Bought one claim slot. Max claims: " + BLib.MOD.territory().getMaxPlayerClaims(server, player.getUUID()) + "."
            )
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
            Component.literal(
                "Next slot cost: " + BLib.MOD.territory().getNextPlayerClaimSlotCost(level.getServer(), player.getUUID()) + " diamonds."
            )
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

    private static int setColor(ServerPlayer player, String rawHex) {
        var color = parseColor(rawHex);
        if (color < 0) {
            player.sendSystemMessage(Component.literal("Use a hex color like #55AAFF or 55AAFF.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BLib.MOD.territory().setPlayerClaimColor(player.getUUID(), color);
        player.sendSystemMessage(
            Component.literal("Claim color set to #" + String.format("%06X", color) + ".").withStyle(ChatFormatting.GREEN)
        );
        return 1;
    }

    private static int addAlly(ServerPlayer owner, ServerPlayer ally) {
        if (owner.getUUID().equals(ally.getUUID())) {
            owner.sendSystemMessage(Component.literal("You already have access to your own claims.").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        var changed = BLib.MOD.territory().addPlayerClaimAlly(owner.getServer(), owner.getUUID(), ally.getUUID());
        owner.sendSystemMessage(
            Component.literal(
                changed
                    ? ally.getGameProfile().getName() + " can now build on your claims."
                    : ally.getGameProfile().getName() + " was already allied to your claims."
            ).withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY)
        );
        ally.sendSystemMessage(
            Component.literal(owner.getGameProfile().getName() + " allied you to their claims.").withStyle(ChatFormatting.GREEN)
        );
        return changed ? 1 : 0;
    }

    private static int removeAlly(ServerPlayer owner, ServerPlayer ally) {
        var changed = BLib.MOD.territory().removePlayerClaimAlly(owner.getServer(), owner.getUUID(), ally.getUUID());
        owner.sendSystemMessage(
            Component.literal(
                changed
                    ? ally.getGameProfile().getName() + " can no longer build on your claims."
                    : ally.getGameProfile().getName() + " was not allied to your claims."
            ).withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY)
        );
        if (changed) {
            ally.sendSystemMessage(
                Component.literal(owner.getGameProfile().getName() + " removed your access to their claims.")
                    .withStyle(ChatFormatting.YELLOW)
            );
        }
        return changed ? 1 : 0;
    }

    private static int listAllies(ServerPlayer owner) {
        var allies = BLib.MOD.territory().getPlayerClaimAllies(owner.getServer(), owner.getUUID());
        if (allies.isEmpty()) {
            owner.sendSystemMessage(Component.literal("No players are allied to your claims.").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        owner.sendSystemMessage(Component.literal("Claim allies:").withStyle(ChatFormatting.YELLOW));
        for (var ally : allies) {
            owner.sendSystemMessage(Component.literal(" - " + playerName(owner, ally)).withStyle(ChatFormatting.GRAY));
        }
        return allies.size();
    }

    private static String playerName(ServerPlayer requester, UUID playerId) {
        var online = requester.getServer().getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return requester.getServer().getProfileCache().get(playerId).map(profile -> profile.getName()).orElse(playerId.toString());
    }

    private static int parseColor(String rawHex) {
        var normalized = rawHex.startsWith("#") ? rawHex.substring(1) : rawHex;
        if (normalized.length() != 6) {
            return -1;
        }

        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
