package com.blib.internal.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxImpacts;

/** Limb hitboxes for listed melee weapons and projectiles; see {@link LimbHitboxImpacts}. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_LimbHitboxImpacts {

    /** A hit that lands on fully plated armour bounces off: no damage, no flinch, no knockback. */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void blib$deflectOffPlating(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (LimbHitboxImpacts.deflects((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float blib$scaleForLimb(float amount, DamageSource source) {
        return LimbHitboxImpacts.beforeHurt((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void blib$recordLimbDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LimbHitboxImpacts.afterHurt((LivingEntity) (Object) this, cir.getReturnValueZ());
    }
}
