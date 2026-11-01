package com.blib.internal.mixin.azurelib;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.common.util.AzItemIdentity;

/** Oct 29: a dropped identity item is not in use, so it drops its animation id - see {@link AzItemIdentity}. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin_AzItemIdentityStrip {

    @Inject(method = "tick", at = @At("HEAD"))
    private void blib$stripAzItemIdentity(CallbackInfo ci) {
        var self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide) {
            AzItemIdentity.strip(self.getItem());
        }
    }
}
