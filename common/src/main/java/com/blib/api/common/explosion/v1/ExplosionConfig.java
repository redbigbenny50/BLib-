package com.blib.api.common.explosion.v1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * @param blockSampleCountPerCycle upper bound on how many blocks one cycle may destroy. This is a ceiling, not a target
 *                                 — see {@link ExplosionProcessor} for the adaptive batch size that keeps a cycle
 *                                 inside its time budget.
 * @param maxMillisecondsPerCycle  how long a single cycle may spend on the server thread before the processor backs
 *                                 off. A tick is 50ms; leaving headroom for everything else the server has to do is
 *                                 what stops a large explosion from costing TPS.
 */
public record ExplosionConfig(
    int blockSampleCountPerCycle,
    Vec3 centerPosition,
    BlockPos centerBlockPosition,
    int cycleDelayInTicks,
    int maxMillisecondsPerCycle,
    Map<Direction, Integer> directionToRadiusMap
) {

    public int radius(Direction direction) {
        return directionToRadiusMap.get(direction);
    }

    public int largestRadius(Direction.Axis axis) {
        var leftDirection = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        var left = radius(leftDirection);
        var right = radius(leftDirection.getOpposite());

        return Math.max(left, right);
    }

    public int largestRadius(Direction.Plane plane) {
        return directionToRadiusMap.entrySet()
            .stream()
            .filter(entry -> plane.test(entry.getKey()))
            .map(Map.Entry::getValue)
            .max(Integer::compareTo)
            .orElseThrow();
    }

    public int largestRadius() {
        return directionToRadiusMap.values().stream().max(Integer::compareTo).orElseThrow();
    }
}
