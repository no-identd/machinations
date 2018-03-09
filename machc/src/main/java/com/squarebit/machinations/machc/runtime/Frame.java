package com.squarebit.machinations.machc.runtime;

import com.squarebit.machinations.machc.runtime.components.TObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * An execution set, which includes runtime instructions and variables.
 */
public final class Frame {
    /**
     * Frame building helper.
     */
    public static class Builder {
        Frame parent;
        List<Variable> variables = new ArrayList<>();
        List<Instruction> instructions = new ArrayList<>();
        Variable methodReturnValue;

        /**
         * Sets the parent frame.
         *
         * @param parent the parent frame
         * @return the parent frame
         */
        public Builder setParent(Frame parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Create a variable builder.
         *
         * @return the variable builder.
         */
        public Variable.Builder createVariable() {
            Variable.Builder builder = new Variable.Builder(this);

            return builder;
        }

        /**
         * Add instruction builder.
         *
         * @param instruction the instruction
         * @return the builder
         */
        public Builder addInstruction(Instruction instruction) {
            instructions.add(instruction);
            return this;
        }

        /**
         * Build the frame.
         *
         * @return the frame
         */
        public Frame build() {
            Frame frame = new Frame();

            // Return value.
            this.methodReturnValue = new Variable.Builder(this).build();

            // Initializes the variable index.
            for (int i = 0; i < variables.size(); i++) {
                Variable variable = variables.get(i);
                variable.frame = frame;
                variable.index = i;
            }

            // Reinitialize instruction frame.
            for (int i = 0; i < instructions.size(); i++) {
                Instruction instruction = instructions.get(i);
                instruction.frame = frame;
            }

            frame.parent = parent;
            frame.variables = variables.toArray(new Variable[0]);
            frame.methodReturnValue = methodReturnValue;

            frame.instructions = instructions.toArray(new Instruction[0]);

            return frame;
        }
    }

    private Frame parent;
    private Variable[] variables;
    private Instruction[] instructions;

    // Named variable.
    private Variable methodReturnValue;

    private Stack<FrameActivation> activationStack = new Stack<>();
    private FrameActivation currentActivation = null;

    /**
     *
     */
    private Frame() {

    }

    /**
     * Gets parent.
     *
     * @return the parent
     */
    public Frame getParent() {
        return parent;
    }

    /**
     * Get variables variable [ ].
     *
     * @return the variable [ ]
     */
    public Variable[] getVariables() {
        return variables;
    }

    /**
     * Get instructions instruction [ ].
     *
     * @return the instruction [ ]
     */
    public Instruction[] getInstructions() {
        return instructions;
    }

    /**
     * Gets method return value variable.
     *
     * @return the method return value
     */
    public Variable getMethodReturnValue() {
        return methodReturnValue;
    }

    /**
     * Activates this frame and returns the frame activation object.
     *
     * @return the frame activation
     */
    public FrameActivation activate() {
        FrameActivation activation = new FrameActivation();

        if (parent != null)
            activation.parent = parent.currentActivation;

        activation.frame = this;
        activation.variableTable = new TObject[variables.length];

        this.currentActivation = activation;
        this.activationStack.push(activation);

        return activation;
    }

    /**
     * Deactivate a frame activation.
     * @param activation the activation.
     */
    void deactivate(FrameActivation activation) {
        if (activation == currentActivation) {
            if (!activationStack.isEmpty())
                currentActivation = activationStack.pop();
            else
                currentActivation = null;
        }
        else
            throw new RuntimeException("Invalid frame activation.");
    }

    /**
     * Gets current activation of this frame.
     *
     * @return the frame activation
     */
    public FrameActivation currentActivation() {
        return this.currentActivation;
    }
}
