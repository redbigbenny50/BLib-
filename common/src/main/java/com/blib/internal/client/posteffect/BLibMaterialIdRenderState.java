package com.blib.internal.client.posteffect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Render-thread material ID applied to entity draws, read by the patcher-injected {@code BlibMaterialId} uniform and
 * written into the {@code entityMaterialId} attachment at byte resolution.
 * <p>
 * The ID means nothing to BLib — it is an opaque per-draw tag a consumer's post-effect shader can branch on. A thermal
 * vision might use it for how warm a creature runs; something else might use it for a material response entirely
 * unrelated to heat.
 * <p>
 * Nested via a stack so a passenger drawn inside its mount's render restores the mount's ID rather than clearing to
 * zero. Single-threaded by design (vanilla render thread), matching {@link BLibHeldItemRenderState}.
 * <p>
 * ⚠ Setting this around a queued draw is not enough on its own: entity vertices go into a {@code MultiBufferSource} and
 * the uniform is only pushed when that batch is flushed, so an unflushed batch samples whichever ID happened to be
 * current at flush time. A consumer that needs per-entity accuracy must end the batch at the boundary — see the
 * background-entity lanes, which have the same requirement.
 */
@ApiStatus.Internal
public final class BLibMaterialIdRenderState {

    private static final int MAX_DEPTH = 64;

    private static final int[] STACK = new int[MAX_DEPTH];

    private static int depth;

    private BLibMaterialIdRenderState() {
        throw new UnsupportedOperationException();
    }

    public static void push(int materialId) {
        if (depth < MAX_DEPTH) {
            STACK[depth] = materialId & 0xFF;
        }

        depth++;
    }

    public static void pop() {
        if (depth > 0) {
            depth--;
        }
    }

    /** @return the innermost pushed ID, or 0 when nothing is in scope. */
    public static int current() {
        if (depth <= 0) {
            return 0;
        }

        return STACK[Math.min(depth, MAX_DEPTH) - 1];
    }
}
