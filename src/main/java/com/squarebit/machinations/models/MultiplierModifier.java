package com.squarebit.machinations.models;

import com.squarebit.machinations.engine.IntegerExpression;

/**
 * The multiplier modifier.
 */
public class MultiplierModifier extends Modifier {
    private IntegerExpression value;
    private int sign = 1;

    /**
     * Gets value.
     *
     * @return the value
     */
    public IntegerExpression getValue() {
        return value;
    }

    /**
     * Sets value.
     *
     * @param value the value
     * @return the value
     */
    public MultiplierModifier setValue(IntegerExpression value) {
        this.value = value;
        return this;
    }

    /**
     * Gets sign.
     *
     * @return the sign
     */
    public int getSign() {
        return sign;
    }

    /**
     * Sets sign.
     *
     * @param sign the sign
     * @return the sign
     */
    public MultiplierModifier setSign(int sign) {
        sign = sign < 0 ? -1 : 1;
        this.sign = sign;
        return this;
    }
}
