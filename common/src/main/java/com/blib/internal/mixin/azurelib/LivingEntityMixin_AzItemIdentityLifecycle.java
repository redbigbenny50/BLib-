package com.blib.internal.mixin.azurelib;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.common.util.AzItemIdentity;

/** Oct 29: identity items carry an animation id only while equipped - see {@link AzItemIdentity}. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin_AzItemIdentityLifecycle {

    @Inject(method = "tick", at = @At("HEAD"))
    private void blib$azItemIdentityLifecycle(CallbackInfo ci) {
        var self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide) {
            AzItemIdentity.tick(self);
        }
    }
}
