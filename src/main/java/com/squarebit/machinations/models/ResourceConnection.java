package com.squarebit.machinations.models;

import com.squarebit.machinations.engine.ArithmeticExpression;
import com.squarebit.machinations.engine.Expression;
import com.squarebit.machinations.engine.IntNumber;
import com.squarebit.machinations.engine.IntegerExpression;

public class ResourceConnection extends AbstractConnection {
    private AbstractNode from;
    private AbstractNode to;

    private FlowRate flowRate = new FlowRate();
    private String resourceName = null; // if null, any resource.

    public static final IntNumber DEFAULT_FLOW_RATE = IntNumber.of(1);
    private Expression flowRateExpression = DEFAULT_FLOW_RATE;

    /**
     * Gets from.
     *
     * @return the from
     */
    public AbstractNode getFrom() {
        return from;
    }

    /**
     * Sets from.
     *
     * @param from the from
     * @return the from
     */
    public ResourceConnection setFrom(AbstractNode from) {
        this.from = from;
        return this;
    }

    /**
     * Gets to.
     *
     * @return the to
     */
    public AbstractNode getTo() {
        return to;
    }

    /**
     * Sets to.
     *
     * @param to the to
     * @return the to
     */
    public ResourceConnection setTo(AbstractNode to) {
        this.to = to;
        return this;
    }

    /**
     * Gets flow rate.
     *
     * @return the flow rate
     */
    public FlowRate getFlowRate() {
        return flowRate;
    }

    /**
     * Sets flow rate.
     *
     * @param flowRate the flow rate
     * @return the flow rate
     */
    public ResourceConnection setFlowRate(FlowRate flowRate) {
        this.flowRate = flowRate;
        return this;
    }

    /**
     * Gets resource name.
     *
     * @return the resource name
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * Sets resource name.
     *
     * @param resourceName the resource name
     * @return the resource name
     */
    public ResourceConnection setResourceName(String resourceName) {
        this.resourceName = resourceName;
        return this;
    }

    /**
     * Gets flow rate expression.
     *
     * @return the flow rate expression
     */
    public Expression getFlowRateExpression() {
        return flowRateExpression;
    }

    /**
     * Sets flow rate expression.
     *
     * @param flowRateExpression the flow rate expression
     * @return the flow rate expression
     */
    public ResourceConnection setFlowRateExpression(Expression flowRateExpression) {
        this.flowRateExpression = flowRateExpression;
        return this;
    }

    public int getFlowRateValue() {
        if (flowRateExpression instanceof ArithmeticExpression) {
            return ((ArithmeticExpression)flowRateExpression).eval();
        }
        else
            return 0;
    }

    /**
     * Activates the resource connection and gets the required resource set should be passed on it.
     * @return the required resource set
     */
    public ResourceSet activate() {
        if (flowRateExpression instanceof ArithmeticExpression) {
            int amount = ((ArithmeticExpression)flowRateExpression).eval();
            return ResourceSet.of(this.resourceName, amount);
        }
        else
            return ResourceSet.empty();
    }
}
