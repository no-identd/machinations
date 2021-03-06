package com.squarebit.machinations.machc.avm.runtime.nodes;

import com.squarebit.machinations.machc.avm.CoreModule;
import com.squarebit.machinations.machc.avm.TypeInfo;
import com.squarebit.machinations.machc.avm.runtime.TNode;

public class TDrainNode extends TNode {
    /**
     * Gets information of the type of this class.
     *
     * @return the {@link TypeInfo}
     */
    @Override
    public TypeInfo getTypeInfo() {
        return CoreModule.DRAIN_NODE_TYPE;
    }
}
