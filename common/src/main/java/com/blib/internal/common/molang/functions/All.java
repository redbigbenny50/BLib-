package com.blib.internal.common.molang.functions;

import com.blib.internal.common.molang.math.IValue;
import com.blib.internal.common.molang.math.functions.Function;
import com.blib.internal.common.molang.runtime.MolangEquality;
import com.blib.internal.common.molang.runtime.MolangSlot;

/**
 * {@code query.all(value, a, b, ...)}: 1 if every argument after the first equals the first, else 0. Arguments may be
 * strings ({@code q.all(q.get_equipped_item_name, 'bow', 'crossbow')}), compared with Molang's {@code ==} rules.
 */
public class All extends Function {

    private final MolangSlot value = new MolangSlot();

    private final MolangSlot candidate = new MolangSlot();

    public All(IValue[] values, String name) throws Exception {
        super(values, name);
    }

    @Override
    public int getRequiredArguments() {
        return 2;
    }

    @Override
    public double get() {
        MolangSlot.evaluate(this.args[0], value);
        var result = 1;

        for (int i = 1; i < this.args.length; i++) {
            MolangSlot.evaluate(this.args[i], candidate);
            var equal = MolangEquality.equal(value, candidate);
            candidate.clear();

            if (!equal) {
                result = 0;
                break;
            }
        }

        value.clear();
        return result;
    }
}
