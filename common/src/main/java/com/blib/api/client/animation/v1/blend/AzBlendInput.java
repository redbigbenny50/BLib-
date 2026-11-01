package com.blib.api.client.animation.v1.blend;

/**
 * Supplies a blend space input each frame, e.g. the animatable's ground speed or aim pitch.
 *
 * @param <T> the animatable type
 */
@FunctionalInterface
public interface AzBlendInput<T> {

    double get(T animatable, float partialTick);
}
