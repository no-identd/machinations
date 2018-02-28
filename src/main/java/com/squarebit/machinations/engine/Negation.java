package com.squarebit.machinations.engine;

public class Negation extends ArithmeticExpression {
    private ArithmeticExpression value;

    /**
     * Gets value.
     *
     * @return the value
     */
    public ArithmeticExpression getValue() {
        return value;
    }

    /**
     * Sets value.
     *
     * @param value the value
     * @return the value
     */
    public Negation setValue(ArithmeticExpression value) {
        this.value = value;
        return this;
    }

    /**
     * Evaluates the expression and returns its result.
     *
     * @return integer result.
     */
    @Override
    public int eval() {
        return -this.value.eval();
    }

    /**
     * Of negate.
     *
     * @param expression the expression
     * @return the negate
     */
    public static Negation of(ArithmeticExpression expression) {
        return new Negation().setValue(expression);
    }
}
