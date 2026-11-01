package com.blib.internal.mixin.azurelib;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

import com.blib.api.client.animation.v1.animator.AzAnimator;
import com.blib.api.client.animation.v1.animator.AzItemAnimator;
import com.blib.internal.client.animation.AzAnimatorAccessor;
import com.blib.internal.client.animation.cache.AzIdentifiableItemStackAnimatorCache;
import com.blib.internal.common.util.AzureLibUtil;
import com.blib.mod.common.registry.init.BLibDataComponents;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin_AzItemAnimatorCache implements AzAnimatorAccessor<UUID, ItemStack> {

    @Override
    public void setAnimator(@Nullable AzAnimator<UUID, ItemStack> animator) {
        var itemStack = AzureLibUtil.<ItemStack>self(this);
        // Stored for the view currently being rendered on this thread (DEFAULT outside rendering).
        AzIdentifiableItemStackAnimatorCache.getInstance()
            .add(
                cacheKey(itemStack),
                itemStack,
                com.blib.api.client.animation.v1.animator.AzItemAnimatorView.current(),
                (AzItemAnimator) animator
            );
    }

    @Override
    public @Nullable AzAnimator<UUID, ItemStack> getAnimatorOrNull() {
        var self = AzureLibUtil.<ItemStack>self(this);
        return AzIdentifiableItemStackAnimatorCache.getInstance()
            .getOrNull(cacheKey(self), com.blib.api.client.animation.v1.animator.AzItemAnimatorView.current());
    }

    /**
     * The stack's own id when it has one (an item in use); otherwise ONE shared key per item.
     * <p>
     * Oct 29: identity items only carry an id while equipped now (see AzItemIdentity), so the same item drawn as an
     * inventory icon, in a storage screen or on the ground has none. Without a key it would get a brand-new animator -
     * with its own deep-copied model - on every frame it is drawn. One shared animator per item avoids that; it is also
     * an improvement for animated items that never had an id at all, which rebuilt every frame before.
     */
    private static @Nullable UUID cacheKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var id = stack.getComponents().get(BLibDataComponents.AZ_ID.get());
        if (id != null) {
            return id;
        }
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return UUID.nameUUIDFromBytes(("blib:shared_item_animator:" + itemId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
