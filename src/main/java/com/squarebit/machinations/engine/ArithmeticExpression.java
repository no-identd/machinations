package com.squarebit.machinations.engine;

public abstract class ArithmeticExpression extends Expression {
    /**
     * Evaluates the expression and returns its result.
     * @return integer result.
     */
    public abstract int eval();
}
