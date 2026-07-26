package com.blib.api.common.dismemberment.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Supplies collision cubes from an entity's real model and current server-side pose.
 * Implementations must be deterministic and may not use client-supplied transforms.
 */
@FunctionalInterface
public interface LimbCollisionAdapter<T extends LivingEntity> {

    List<Part> resolve(T entity);

    record Part(ResourceLocation limbId, LimbCollisionBox box) {}
}
