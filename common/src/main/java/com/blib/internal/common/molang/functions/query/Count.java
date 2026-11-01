package com.blib.internal.common.molang.functions.query;

import com.blib.internal.common.molang.math.IValue;
import com.blib.internal.common.molang.math.functions.Function;
import com.blib.internal.common.molang.runtime.MolangSlot;

/**
 * {@code query.count(value, ...)}: counts the things passed to it. An array counts as its number of elements; anything
 * else counts as 1. {@code q.count(q.get_nearby_entities(8))} is the number of entities within 8 blocks.
 * <p>
 * Lives outside {@code com.blib.internal.common.molang.functions} so it is never folded at load time.
 * </p>
 */
public class Count extends Function {

    private final MolangSlot slot = new MolangSlot();

    public Count(IValue[] values, String name) throws Exception {
        super(values, name);
    }

    @Override
    public int getRequiredArguments() {
        return 1;
    }

    @Override
    public double get() {
        var count = 0;

        for (var arg : this.args) {
            MolangSlot.evaluate(arg, slot);
            var array = slot.array();
            count += array == null ? 1 : array.size();
            slot.clear();
        }

        return count;
    }
}
