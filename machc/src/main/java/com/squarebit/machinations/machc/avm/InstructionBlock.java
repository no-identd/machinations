package com.squarebit.machinations.machc.avm;

import com.squarebit.machinations.machc.avm.exceptions.VariableAlreadyExistedException;
import com.squarebit.machinations.machc.avm.instructions.Instruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An instruction block.
 */
public final class InstructionBlock implements Scope {
    private Scope parentScope;
    private List<VariableInfo> variables;
    private Map<String, VariableInfo> variableByName;
    private List<Instruction> instructions;

    private static int BLOCK_ID_GENERATOR = 0;
    private int id;

    /**
     * Instantiates a new Instruction block.
     */
    public InstructionBlock() {
        this.variables = new ArrayList<>();
        this.variableByName = new HashMap<>();
        this.instructions = new ArrayList<>();

        this.id = BLOCK_ID_GENERATOR++;
    }

    /**
     * Create a variable and adds it to this scope.
     *
     * @param name the variable name
     * @return the {@link VariableInfo} instance
     * @throws VariableAlreadyExistedException if a variable with the same name already existed in the scope
     */
    public VariableInfo createVariable(String name) throws VariableAlreadyExistedException {
        VariableInfo variableInfo = new VariableInfo().setName(name);
        addVariable(variableInfo);
        return variableInfo;
    }

    /**
     * Adds a variable to this scope.
     *
     * @param variableInfo the {@link VariableInfo} instance
     * @throws VariableAlreadyExistedException if a variable with the same name already existed in the scope
     */
    public void addVariable(VariableInfo variableInfo) throws VariableAlreadyExistedException {
        if (this.findVariable(variableInfo.getName()) != null)
            throw new VariableAlreadyExistedException(this, variableInfo.getName());

        variableInfo.setDeclaringScope(this).setIndex(getVariableCount());
        variables.add(variableInfo);
        variableByName.put(variableInfo.getName(), variableInfo);
    }

    /**
     * Creates a temporary variable.
     * @return a temporary {@link VariableInfo}
     * @throws VariableAlreadyExistedException
     */
    public VariableInfo createTempVar() {
        try {
            return createVariable(String.format("$__block_%d_temp__%d", this.id, getLocalVariableCount()));
        }
        catch (Exception exception) {
            return null;
        }
    }

    /**
     * Sets parent scope.
     *
     * @param parentScope the parent scope
     * @return the parent scope
     */
    public InstructionBlock setParentScope(Scope parentScope) {
        this.parentScope = parentScope;
        return this;
    }

    /**
     * Gets parent scope.
     *
     * @return parent scope, or null.
     */
    @Override
    public Scope getParentScope() {
        return this.parentScope;
    }

    /**
     * Finds a variable with given name in the scope.
     *
     * @param name the variable name
     * @return a {@link VariableInfo} instance, or null if not found.
     */
    @Override
    public VariableInfo findVariable(String name) {
        VariableInfo variableInfo = variableByName.getOrDefault(name, null);

        if (variableInfo == null && parentScope != null)
            return parentScope.findVariable(name);

        return variableInfo;
    }

    /**
     * Gets the number of variables declared from the root scope to current scope.
     *
     * @return the number of variable declared
     */
    @Override
    public int getVariableCount() {
        int parentVariableCount = parentScope == null ? 0 : parentScope.getVariableCount();
        return (parentVariableCount + variables.size());
    }

    /**
     * Gets the number of variables declared in this scope only.
     *
     * @return the number of variables declared in this scope.
     */
    @Override
    public int getLocalVariableCount() {
        return variables.size();
    }

    /**
     * Gets local variables.
     *
     * @return the local variables
     */
    public List<VariableInfo> getLocalVariables() {
        return variables;
    }

    /**
     * Gets the list of instructions
     * @return instruction list, in declaration order.
     */
    public List<Instruction> getInstructions() {
        return instructions;
    }

    /**
     * Adds an instruction to this block.
     *
     * @param instruction the {@link Instruction} instance
     */
    public Instruction emit(Instruction instruction) {
        instruction.setScope(this).setIndex(instructions.size());
        instructions.add(instruction);
        return instruction;
    }

    /**
     * Reindex variables.
     */
    public void reindexVariables() {
        int parentVariableCount = parentScope == null ? 0 : parentScope.getVariableCount();

        for (int i = 0; i < variables.size(); i++) {
            variables.get(i).setIndex(i + parentVariableCount);
        }
    }

    /**
     *
     */
    public void reindexInstructions() {
        for (int i = 0; i < instructions.size(); i++) {
            instructions.get(i).setIndex(i);
        }
    }
}
