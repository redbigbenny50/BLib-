package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Supplies server-authoritative additive firearm hurtboxes for an entity. */
@FunctionalInterface
public interface LimbHitboxProvider {

    List<LimbHitboxVolume> getHitboxes(LivingEntity entity);
}
