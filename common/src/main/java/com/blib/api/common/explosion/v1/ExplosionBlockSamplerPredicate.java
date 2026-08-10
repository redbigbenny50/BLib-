package com.blib.api.common.explosion.v1;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

@FunctionalInterface
public interface ExplosionBlockSamplerPredicate {

    boolean test(Explosion explosion, BlockPos pos);

    /**
     * The order of these two tests matters and is not cosmetic.
     * <p>
     * The cursors sweep cubic walls, but an explosion's shape is an ellipsoid, so a large blast offers up several times
     * more positions than it will ever destroy — for a 128-block radius, roughly 11.5 million considered against 3
     * million kept. Testing the block state first meant a chunk lookup for every one of those rejects. The distance
     * test is pure integer arithmetic on values the caller already has, so it now runs first and the state lookup only
     * happens for positions that are genuinely inside the blast.
     * <p>
     * The set of blocks that pass is unchanged.
     */
    ExplosionBlockSamplerPredicate DEFAULT = (explosion, pos) -> {
        var centerPos = explosion.config().centerBlockPosition();

        var x = pos.getX() - centerPos.getX();
        var y = pos.getY() - centerPos.getY();
        var z = pos.getZ() - centerPos.getZ();

        if (ExplosionUtil.getNormalizedDistance(explosion, x, y, z) > 1) {
            return false;
        }

        var state = explosion.level().getBlockState(pos);

        return !state.is(Blocks.AIR) && !state.is(Blocks.BEDROCK);
    };
}
