package com.blib.api.common.explosion.v1;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class Explosion {

    public static ExplosionBuilder builder(ServerLevel level, Vec3 center) {
        return new ExplosionBuilder(level, center);
    }

    private final ExplosionCallbacks callbacks;

    private final ExplosionConfig config;

    private final ExplosionProcessor explosionProcessor;

    private final ServerLevel level;

    private final ExplosionBlockSamplerPredicate samplerPredicate;

    Explosion(
        ServerLevel level,
        ExplosionCallbacks callbacks,
        ExplosionConfig config,
        ExplosionBlockSamplerPredicate samplerPredicate
    ) {
        this.callbacks = callbacks;
        this.config = config;
        this.explosionProcessor = new ExplosionProcessor(this);
        this.level = level;
        this.samplerPredicate = samplerPredicate;
    }

    public void explode() {
        callbacks.onExplosionStart();
        explosionProcessor.process();
    }

    /**
     * An opaque snapshot of how far this explosion has carved, to be handed back to {@link #resume(int[])}.
     * <p>
     * Only progress is captured. The explosion itself, including its callbacks, has to be rebuilt by the caller before
     * resuming, because a callback is a lambda and cannot be stored.
     */
    public int[] saveState() {
        return explosionProcessor.saveState();
    }

    /**
     * Restarts an explosion that was interrupted mid-carve, from a snapshot taken by {@link #saveState()}.
     * <p>
     * Unlike {@link #explode()} this deliberately does NOT fire {@code onExplosionStart}. That callback is where the
     * one-off work of an explosion lives, such as damaging entities, applying knockback and spawning effects, and for a
     * resumed explosion all of it has already happened. Firing it again would re-run the whole event every time the
     * world reloaded. Keeping that decision here rather than in the caller means a caller cannot get it wrong.
     */
    public void resume(int[] cursorState) {
        explosionProcessor.restoreState(cursorState);
        explosionProcessor.process();
    }

    public ExplosionCallbacks callbacks() {
        return callbacks;
    }

    public ExplosionConfig config() {
        return config;
    }

    public ServerLevel level() {
        return level;
    }

    public ExplosionBlockSamplerPredicate samplerPredicate() {
        return samplerPredicate;
    }
}
