package com.squarebit.machinations.machc.runtime;

import com.squarebit.machinations.machc.ast.GGraph;
import com.squarebit.machinations.machc.ast.GProgram;
import com.squarebit.machinations.machc.ast.GUnit;
import com.squarebit.machinations.machc.runtime.components.TInteger;
import com.squarebit.machinations.machc.runtime.components.TRuntimeGraph;
import com.squarebit.machinations.machc.runtime.components.TType;
import com.squarebit.machinations.machc.runtime.expressions.TObjectRef;
import com.squarebit.machinations.machc.runtime.instructions.Eval;
import com.squarebit.machinations.machc.runtime.instructions.Invoke;

import java.util.Stack;

public final class MachMachine {
    private final GProgram program;
    private final TypeRegistry typeRegistry = new TypeRegistry();
    private final Stack<FrameExecutionContext> executionStack = new Stack<>();

    /**
     * Creates a new machine instance.
     */
    private MachMachine(final GProgram program) {
        this.program = program;
        this.compile();
    }

    /**
     * Creates a new machine using given program specifications.
     * @param program the program specification.
     * @return a machine.
     */
    public static MachMachine from(final GProgram program) {
        MachMachine machine = new MachMachine(program);
        return machine;
    }

    /**
     * Gets a type using its name.
     *
     * @param typeName the type name
     * @return the type
     */
    public TType getType(String typeName) {
        return typeRegistry.getType(typeName);
    }

    /**
     * Compiles current program.
     */
    private void compile() {
        for (GUnit unit : program.getUnits()) {
            for (GGraph graph : unit.getGraphs()) {
                TType<TRuntimeGraph> type = new TType<>(graph.getId(), TType.GRAPH_TYPE, TRuntimeGraph.class);
                typeRegistry.registerType(type);
            }
        }

        Frame frame = buildBootImage();
        loadFrame(frame);
    }

    /**
     * Loads a frame by pushing its activation to the execution stack.
     *
     * @param frame the frame
     */
    public void loadFrame(Frame frame) {
        FrameActivation activation = frame.activate();
        FrameExecutionContext executionContext = new FrameExecutionContext(activation);
        this.executionStack.push(executionContext);
    }

    /**
     * Executes next instruction in the execution stack.
     */
    public void executeNext() {
        if (this.executionStack.isEmpty())
            return;

        FrameExecutionContext executionContext = null;
        FrameActivation activation = null;
        int nextInstruction = 0;
        Instruction[] instructions;

        Instruction instruction = null;

        while (!this.executionStack.isEmpty() && instruction == null) {
            executionContext = executionStack.peek();
            activation = executionContext.getActivation();
            nextInstruction = executionContext.getNextInstruction();
            instructions = activation.getInstructions();

            if (nextInstruction >= instructions.length) {
                executionStack.pop();
            }
            else {
                instruction = instructions[nextInstruction];
            }
        }

        if (instruction != null) {
            executeInstruction(instruction);
            executionContext.next();
        }
    }

    /**
     * Execute one instruction.
     * @param instruction the instruction
     */
    private void executeInstruction(Instruction instruction) {
        try {
            instruction.execute();
        }
        catch (Exception ex) {

        }
    }

    /**
     * Builds the frame that responsible for the main routine.
     * @return the boot frame
     */
    private Frame buildBootImage() {
        try {
            Frame.Builder builder = new Frame.Builder();

            Variable mainGraph = builder.createVariable().setType(TType.INTEGER_TYPE).build();

            builder.addInstruction(new Eval(new TObjectRef(new TInteger(10)), mainGraph));

            Invoke invoke = new Invoke(TType.INTEGER_TYPE.getMethods()[0], mainGraph, new Variable[0]);
            builder.addInstruction(invoke);

            return builder.build();
        }
        catch (Exception ex) {
            return null;
        }
    }
}
