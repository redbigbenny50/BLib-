package com.blib.internal.common.util;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

import com.blib.api.client.animation.v1.identity.AzIdentityRegistry;
import com.blib.mod.common.registry.init.BLibDataComponents;

/**
 * When an AzureLib identity item carries its animation id ({@code AZ_ID}).
 * <p>
 * 🚨 Oct 29 BUG FIX - identity items never stacked and each one filled its own slot in storage mods. The id used to be
 * stamped, RANDOM, on every new stack the moment it was constructed (ItemStackMixin_AzItemStackIdentityRegistry), so no
 * two stacks were ever equal: stackable model items would not stack anywhere, and identical weapons were listed one by
 * one in Refined Storage, each using up a storage type. It also caused the duplicate commissary trades and the chem
 * thrower sharing animations with a copied stack.
 * </p>
 * <p>
 * The id only exists so each item being USED can play its own animation. So now:
 * </p>
 * <ul>
 * <li>an identity item gets an id while an entity has it EQUIPPED (hands and armor - a player, a marine, a
 * yautja);</li>
 * <li>it loses the id everywhere else - in a player's inventory away from the hand, while any container screen is open
 * (so a held item moved straight into a chest or storage goes in clean), and as a dropped item.</li>
 * </ul>
 * <p>
 * Identical items away from use are therefore identical stacks again. Drawn without an id (inventory icons, items in
 * storage screens) they share one animator per item - see ItemStackMixin_AzItemAnimatorCache.
 * </p>
 */
public final class AzItemIdentity {

    private AzItemIdentity() {}

    public static boolean isIdentityItem(ItemStack stack) {
        return !stack.isEmpty() && AzIdentityRegistry.hasIdentity(stack.getItem());
    }

    /** Gives an equipped identity item its own id if it has none. */
    public static void ensure(ItemStack stack) {
        if (isIdentityItem(stack) && !stack.has(BLibDataComponents.AZ_ID.get())) {
            stack.set(BLibDataComponents.AZ_ID.get(), UUID.randomUUID());
        }
    }

    /** Takes the id off an identity item that is not in use, so it is equal to every other copy of itself again. */
    public static void strip(ItemStack stack) {
        if (isIdentityItem(stack) && stack.has(BLibDataComponents.AZ_ID.get())) {
            stack.remove(BLibDataComponents.AZ_ID.get());
        }
    }

    /** Server side, once per tick for every living entity. */
    public static void tick(LivingEntity entity) {
        if (entity instanceof Player player) {
            tickPlayer(player);
            return;
        }
        for (var slot : EquipmentSlot.values()) {
            ensure(entity.getItemBySlot(slot));
        }
    }

    private static void tickPlayer(Player player) {
        var inventory = player.getInventory();
        // A container screen (chest, storage terminal, machine) is open: nothing is in use, so nothing keeps an id.
        // A held stack moved straight from the hotbar into storage then goes in clean.
        var screenOpen = player.containerMenu != player.inventoryMenu;

        for (int i = 0; i < inventory.items.size(); i++) {
            var stack = inventory.items.get(i);
            if (i == inventory.selected && !screenOpen) {
                ensure(stack);
            } else {
                strip(stack);
            }
        }
        for (var stack : inventory.offhand) {
            if (screenOpen) {
                strip(stack);
            } else {
                ensure(stack);
            }
        }
        for (var stack : inventory.armor) {
            ensure(stack);
        }
        strip(player.containerMenu.getCarried());
    }
}
