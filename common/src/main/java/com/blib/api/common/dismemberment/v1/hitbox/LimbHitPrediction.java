package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Client-side identification of the rendered limb a local shot crossed.
 *
 * <p>This is deliberately not hitbox data: no model coordinates, transforms, or volume dimensions leave the client.
 * The server must independently validate the target and limb before applying gameplay damage.</p>
 */
public record LimbHitPrediction(int entityId, ResourceLocation limbId) {

    public LimbHitPrediction {
        if (entityId < 0) {
            throw new IllegalArgumentException("entityId must be non-negative");
        }
        Objects.requireNonNull(limbId, "limbId");
    }
}
