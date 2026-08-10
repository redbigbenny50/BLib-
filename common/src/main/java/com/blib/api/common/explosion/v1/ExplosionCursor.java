package com.blib.api.common.explosion.v1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Walks the expanding wall of blocks belonging to one face of an explosion.
 * <p>
 * The positions produced are identical to the original implementation; the arithmetic behind them is not. The wall's
 * corner used to be derived with three chained {@link BlockPos#relative} calls plus an {@link BlockPos#offset},
 * allocating four immutable {@code BlockPos} objects for every position considered — the overwhelming majority of which
 * are then rejected by the sampler predicate. A large explosion considers positions in the tens of millions, so that is
 * tens of millions of allocations spent on nothing.
 * <p>
 * The direction basis and the wall's maximum depth never change for a given cursor, so both are resolved once in the
 * constructor and the position is now plain integer arithmetic with a single allocation at the end.
 */
public class ExplosionCursor {

    /**
     * Number of ints {@link #saveState()} produces and {@link #restoreState(int[])} expects.
     */
    public static final int STATE_LENGTH = 3;

    private final Direction direction;

    /**
     * Sum of the unit vectors of the wall's normal and its two perpendiculars. Scaled by the current depth this gives
     * the wall's top-left corner relative to the explosion centre.
     */
    private final int cornerStepX;

    private final int cornerStepY;

    private final int cornerStepZ;

    private final int centerX;

    private final int centerY;

    private final int centerZ;

    private final int maxDepth;

    private int x;

    private int y;

    private int depth;

    public ExplosionCursor(Explosion explosion, Direction direction) {
        this.direction = direction;

        var perpendicular1 = getPerpendicularDirection1();
        var perpendicular2 = getPerpendicularDirection2();

        this.cornerStepX = direction.getStepX() + perpendicular1.getStepX() + perpendicular2.getStepX();
        this.cornerStepY = direction.getStepY() + perpendicular1.getStepY() + perpendicular2.getStepY();
        this.cornerStepZ = direction.getStepZ() + perpendicular1.getStepZ() + perpendicular2.getStepZ();

        var centerPos = explosion.config().centerBlockPosition();

        this.centerX = centerPos.getX();
        this.centerY = centerPos.getY();
        this.centerZ = centerPos.getZ();

        // getOrDefault rather than radius(): a direction the builder never set simply never expands,
        // where the old code dereferenced a null Integer the first time the wall was asked to move.
        this.maxDepth = explosion.config().directionToRadiusMap().getOrDefault(direction, 0);
    }

    public BlockPos next() {
        var posX = centerX + cornerStepX * depth + getXOffset();
        var posY = centerY + cornerStepY * depth + getYOffset();
        var posZ = centerZ + cornerStepZ * depth + getZOffset();

        advanceCursor();

        return new BlockPos(posX, posY, posZ);
    }

    private void advanceCursor() {
        x++;

        if (x > depth * 2) {
            x = 0;
            y++;
        }

        if (y > depth * 2) {
            y = 0;
            depth++;
        }
    }

    /**
     * This cursor's entire progress: where it sits on the current wall, and how far that wall has expanded.
     * <p>
     * Nothing else about a cursor needs storing. The direction basis, the explosion centre and the maximum depth are
     * all derived from the explosion config and rebuilt by the constructor.
     */
    public int[] saveState() {
        return new int[] { x, y, depth };
    }

    /**
     * Restores progress captured by {@link #saveState()}.
     */
    public void restoreState(int[] state) {
        if (state == null || state.length != STATE_LENGTH) {
            throw new IllegalArgumentException(
                "Expected " + STATE_LENGTH + " ints of cursor state, got " + (state == null ? "null" : state.length)
            );
        }

        this.x = state[0];
        this.y = state[1];
        this.depth = state[2];
    }

    public boolean canExpandFurther() {
        return depth < maxDepth;
    }

    private Direction getPerpendicularDirection1() {
        return switch (direction) {
            case NORTH, SOUTH -> Direction.WEST;
            case EAST, WEST -> Direction.UP;
            case UP, DOWN -> Direction.NORTH;
        };
    }

    private Direction getPerpendicularDirection2() {
        return switch (direction) {
            case NORTH, SOUTH -> Direction.UP;
            case EAST, WEST -> Direction.NORTH;
            case UP, DOWN -> Direction.WEST;
        };
    }

    private int getXOffset() {
        return switch (direction) {
            case NORTH, SOUTH -> x;
            case EAST, WEST -> 0;
            case UP, DOWN -> x;
        };
    }

    private int getYOffset() {
        return switch (direction) {
            case NORTH, SOUTH, EAST, WEST -> -y;
            case UP, DOWN -> 0;
        };
    }

    private int getZOffset() {
        return switch (direction) {
            case NORTH, SOUTH -> 0;
            case EAST, WEST -> x;
            case UP, DOWN -> y;
        };
    }
}
