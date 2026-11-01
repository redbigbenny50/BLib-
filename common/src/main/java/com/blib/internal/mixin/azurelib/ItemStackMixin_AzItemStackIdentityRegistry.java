package com.blib.internal.mixin.azurelib;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 🚨 Oct 29: this used to stamp a RANDOM animation id ({@code AZ_ID}) on every new stack of an identity item, the
 * moment the stack was constructed - so no two stacks were ever equal (no stacking, one storage entry per weapon). Ids
 * are now handed out only while an item is equipped and removed when it is not - see
 * {@link com.blib.internal.common.util.AzItemIdentity}. The class stays (empty) so the mixin config is unchanged.
 */
@Mixin(ItemStack.class)
public class ItemStackMixin_AzItemStackIdentityRegistry {}
