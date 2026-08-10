package com.blib.api.common.explosion.v1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.blib.api.common.server.v1.ServerScheduler;

/**
 * Drives an explosion one cycle at a time, each cycle deferred to the next server tick through {@link ServerScheduler}.
 * <p>
 * Two things changed here, neither of which alters the blocks an explosion destroys.
 * <p>
 * First, the cursors are held in a plain array walked with an indexed loop. The old code kept them in an
 * {@code EnumMap} and asked {@code canAnyWallMoveFurther()} — which built a stream and ran {@code anyMatch} — on every
 * single iteration of the sampling loop. For a large blast that is one stream allocation per candidate position, tens
 * of millions of them, purely to answer a question six boolean reads can answer.
 * <p>
 * Second, the batch size adapts. A fixed count is a guess about the hardware: the same number that finishes comfortably
 * on one machine blows the tick budget on another, and a blown tick budget is exactly what makes a big explosion feel
 * like a hang. The processor now measures each cycle, halves the batch when it overruns
 * {@link ExplosionConfig#maxMillisecondsPerCycle()} and doubles it back — never above the configured ceiling — when it
 * finishes comfortably inside. It starts deliberately small and climbs, so the first cycle can never be the expensive
 * one. The total work is unchanged; only its distribution across ticks is.
 */
public class ExplosionProcessor {

    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * Floor for the adaptive batch. Below this the explosion would crawl even on hardware that is simply having a bad
     * second, and progress matters more than perfect smoothness.
     */
    private static final int MINIMUM_BLOCKS_PER_CYCLE = 256;

    /**
     * Opening batch. Small enough that no machine can stall on the first cycle; the adaptation below raises it within a
     * handful of cycles if there is headroom.
     */
    private static final int INITIAL_BLOCKS_PER_CYCLE = 1024;

    private final Explosion explosion;

    private final ExplosionCursor[] cursors;

    private int blocksPerCycle;

    public ExplosionProcessor(Explosion explosion) {
        this.explosion = explosion;
        this.cursors = new ExplosionCursor[DIRECTIONS.length];

        for (var i = 0; i < DIRECTIONS.length; i++) {
            cursors[i] = new ExplosionCursor(explosion, DIRECTIONS[i]);
        }

        this.blocksPerCycle = Math.min(INITIAL_BLOCKS_PER_CYCLE, explosion.config().blockSampleCountPerCycle());
    }

    public void process() {
        processBatch(explosion.config().cycleDelayInTicks());
    }

    private void processBatch(int delayBetweenCycles) {
        var callbacks = explosion.callbacks();
        var config = explosion.config();
        var startedAtNanos = System.nanoTime();

        callbacks.onCycleStart();

        var blocksToSample = sampleBlocks(blocksPerCycle);

        for (var i = 0; i < blocksToSample.size(); i++) {
            callbacks.onBlockSample(explosion, blocksToSample.get(i));
        }

        callbacks.onCycleFinish(blocksToSample);

        adaptBatchSize(System.nanoTime() - startedAtNanos, config);

        if (canAnyWallMoveFurther()) {
            ServerScheduler.schedule(
                () -> processBatch(delayBetweenCycles),
                Duration.ofMillis(delayBetweenCycles * 50L)
            );
        } else {
            callbacks.onExplosionFinish();
        }
    }

    /**
     * Captures how far each wall has expanded, so an explosion interrupted by a world unload can pick up exactly where
     * it stopped rather than starting over.
     * <p>
     * Starting over is not an option for a caller whose block transform reads the block it is replacing: running twice
     * over the same ground transforms already-transformed blocks a second time.
     * <p>
     * Cursors are stored in {@link Direction#values()} order, which is fixed, so the array is positional and needs no
     * direction names alongside it. The adaptive batch size is deliberately NOT captured: it measures the machine
     * running right now rather than any progress, and it re-converges within a few cycles of resuming.
     */
    public int[] saveState() {
        var state = new int[cursors.length * ExplosionCursor.STATE_LENGTH];

        for (var i = 0; i < cursors.length; i++) {
            System.arraycopy(cursors[i].saveState(), 0, state, i * ExplosionCursor.STATE_LENGTH, ExplosionCursor.STATE_LENGTH);
        }

        return state;
    }

    /**
     * Restores progress captured by {@link #saveState()}.
     */
    public void restoreState(int[] state) {
        var expectedLength = cursors.length * ExplosionCursor.STATE_LENGTH;

        if (state == null || state.length != expectedLength) {
            throw new IllegalArgumentException(
                "Expected " + expectedLength + " ints of explosion state, got " + (state == null ? "null" : state.length)
            );
        }

        var cursorState = new int[ExplosionCursor.STATE_LENGTH];

        for (var i = 0; i < cursors.length; i++) {
            System.arraycopy(state, i * ExplosionCursor.STATE_LENGTH, cursorState, 0, ExplosionCursor.STATE_LENGTH);
            cursors[i].restoreState(cursorState);
        }
    }

    private void adaptBatchSize(long elapsedNanos, ExplosionConfig config) {
        var budgetNanos = config.maxMillisecondsPerCycle() * 1_000_000L;

        if (budgetNanos <= 0) {
            return;
        }

        if (elapsedNanos > budgetNanos) {
            blocksPerCycle = Math.max(MINIMUM_BLOCKS_PER_CYCLE, blocksPerCycle / 2);
        } else if (elapsedNanos * 2 < budgetNanos) {
            blocksPerCycle = Math.min(config.blockSampleCountPerCycle(), blocksPerCycle * 2);
        }
    }

    private List<BlockPos> sampleBlocks(int maxBlocksPerCycle) {
        var count = 0;
        var sampledBlocks = new ArrayList<BlockPos>(maxBlocksPerCycle);
        var samplerPredicate = explosion.samplerPredicate();

        while (count < maxBlocksPerCycle && canAnyWallMoveFurther()) {
            for (var cursor : cursors) {
                if (count >= maxBlocksPerCycle) {
                    break;
                }

                if (!cursor.canExpandFurther()) {
                    continue;
                }

                var pos = cursor.next();

                if (samplerPredicate.test(explosion, pos)) {
                    sampledBlocks.add(pos);
                    count++;
                }
            }
        }

        return sampledBlocks;
    }

    private boolean canAnyWallMoveFurther() {
        for (var cursor : cursors) {
            if (cursor.canExpandFurther()) {
                return true;
            }
        }

        return false;
    }
}
