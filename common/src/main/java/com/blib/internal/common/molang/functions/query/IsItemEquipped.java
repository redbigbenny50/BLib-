package com.blib.internal.common.molang.functions.query;

import com.blib.internal.common.molang.MolangQueryContext;
import com.blib.internal.common.molang.math.IValue;

/**
 * {@code query.is_item_equipped([hand])}: 1 if the hand holds an item. The hand is 0 or {@code 'main_hand'} (the
 * default), or 1 or {@code 'off_hand'}.
 */
public class IsItemEquipped extends ContextQueryFunction {

    public IsItemEquipped(IValue[] values, String name) throws Exception {
        super(values, name);
    }

    @Override
    protected double evaluate(MolangQueryContext context) {
        var hand = handArg(0, MAIN_HAND);
        return hand < 0 ? 0 : context.isItemEquipped(hand);
    }
}
