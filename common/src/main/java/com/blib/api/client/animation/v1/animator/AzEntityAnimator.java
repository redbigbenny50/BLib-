package com.blib.api.client.animation.v1.animator;

import net.minecraft.world.entity.Entity;

import java.util.UUID;

import com.blib.internal.client.animation.molang.AzEntityMolangQueries;
import com.blib.internal.client.animation.molang.AzMolangQueryContext;

/**
 * The {@code AzEntityAnimator} class extends {@link AzAnimator} to provide specialized animation management for
 * entities. This abstract class is designed to handle various animation-related requirements for entities in a game
 * framework, including the application of MoLang queries specific to entity-related properties such as position,
 * health, and motion state.
 *
 * @param <T> The type of entity this animator is designed to manage.
 */
/*
 * AzureLib 3.2.3 (ported Oct 31) - the entity queries moved into AzEntityMolangQueries, which reads the entity bound to
 * AzMolangQueryContext, so the arrow operator (t.pig->q.health) can point them at another entity.
 */
@SuppressWarnings("unused")
public abstract class AzEntityAnimator<T extends Entity> extends AzAnimator<UUID, T> {

    protected AzEntityAnimator() {
        super();
    }

    protected AzEntityAnimator(AzAnimatorConfig config) {
        super(config);
    }

    /**
     * Applies MoLang queries to the given entity. The entity queries themselves are defined in
     * {@link AzEntityMolangQueries} and read the entity bound here, so the arrow operator can point them at another
     * entity.
     *
     * @param entity       The entity being animated. It can be of any type extending {@code Entity}.
     * @param animTime     The time in seconds related to the current animation cycle.
     * @param partialTicks A partial tick value used to interpolate animations smoothly.
     */
    @Override
    protected void applyMolangQueries(T entity, double animTime, float partialTicks) {
        super.applyMolangQueries(entity, animTime, partialTicks);
        AzMolangQueryContext.INSTANCE.bind(entity, partialTicks);
    }
}
