package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Client-only hook that tests a shot against an already rendered model pose. */
@FunctionalInterface
public interface LimbHitPredictionProvider {

    Optional<LimbHitPrediction> findNearest(Level level, Vec3 rayStart, Vec3 rayEnd);
}


