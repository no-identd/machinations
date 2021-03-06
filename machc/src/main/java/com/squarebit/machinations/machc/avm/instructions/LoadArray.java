package com.squarebit.machinations.machc.avm.instructions;

import com.squarebit.machinations.machc.avm.VariableInfo;
import com.squarebit.machinations.machc.avm.runtime.TInteger;

/**
 * Loads an array item to a variable.
 */
public class LoadArray extends Instruction {
    private VariableInfo array;
    private TInteger itemIndex;
    private VariableInfo to;

    public LoadArray(VariableInfo array, TInteger itemIndex, VariableInfo to) {
        this.array = array;
        this.itemIndex = itemIndex;
        this.to = to;
    }

    public VariableInfo getArray() {
        return array;
    }

    public TInteger getItemIndex() {
        return itemIndex;
    }

    public VariableInfo getTo() {
        return to;
    }
}
