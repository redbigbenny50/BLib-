package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/** An oriented, server-side firearm hurtbox belonging to one gameplay limb. */
public record LimbHitboxVolume(
    ResourceLocation limbId,
    Vec3 center,
    Vec3 xAxis,
    Vec3 yAxis,
    Vec3 zAxis,
    Vec3 halfExtents,
    float healthDamageMultiplier,
    float limbDamageMultiplier,
    float limbDamageThreshold
) {

    private static final double EPSILON = 1.0E-7D;

    public LimbHitboxVolume {
        Objects.requireNonNull(limbId, "limbId");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(xAxis, "xAxis");
        Objects.requireNonNull(yAxis, "yAxis");
        Objects.requireNonNull(zAxis, "zAxis");
        Objects.requireNonNull(halfExtents, "halfExtents");
        if (halfExtents.x <= 0.0D || halfExtents.y <= 0.0D || halfExtents.z <= 0.0D) {
            throw new IllegalArgumentException("halfExtents must be positive");
        }
    }

    /** Returns the first point where a finite ray segment enters this oriented box. */
    public Optional<Vec3> clip(Vec3 rayStart, Vec3 rayEnd) {
        var direction = rayEnd.subtract(rayStart);
        var localStart = rayStart.subtract(center);
        var origin = new double[] { localStart.dot(xAxis), localStart.dot(yAxis), localStart.dot(zAxis) };
        var delta = new double[] { direction.dot(xAxis), direction.dot(yAxis), direction.dot(zAxis) };
        var extents = new double[] { halfExtents.x, halfExtents.y, halfExtents.z };
        var entry = 0.0D;
        var exit = 1.0D;

        for (var axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < EPSILON) {
                if (origin[axis] < -extents[axis] || origin[axis] > extents[axis]) {
                    return Optional.empty();
                }
                continue;
            }

            var inverse = 1.0D / delta[axis];
            var first = (-extents[axis] - origin[axis]) * inverse;
            var second = (extents[axis] - origin[axis]) * inverse;
            if (first > second) {
                var swap = first;
                first = second;
                second = swap;
            }
            entry = Math.max(entry, first);
            exit = Math.min(exit, second);
            if (entry > exit) {
                return Optional.empty();
            }
        }

        return Optional.of(rayStart.add(direction.scale(entry)));
    }

    public boolean hasLimbDamagePool() {
        return limbDamageMultiplier > 0.0F && limbDamageThreshold > 0.0F;
    }
}
