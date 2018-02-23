package com.squarebit.machinations.models;

import com.squarebit.machinations.engine.*;
import com.squarebit.machinations.parsers.DiceLexer;
import com.squarebit.machinations.parsers.DiceParser;
import com.squarebit.machinations.parsers.GameMLLexer;
import com.squarebit.machinations.parsers.GameMLParser;
import com.squarebit.machinations.specs.yaml.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MachinationsContextFactory {
    /**
     *
     */
    private class BuildingContext {
        private MachinationsContext machinations;
        private YamlSpec spec;
        private Map<AbstractElement, ElementSpec> elementSpec = new HashMap<>();
        private Map<Object, Object> buildContext = new HashMap<>();
        private Object currentObject;
    }

    /**
     *
     */
    private class ConnectionBuildContext {
        private AbstractNode from;
        private AbstractNode to;
        private String id;
        private GameMLParser.ResourceConnectionLabelContext labelContext;

        private DiceParser.ExpressionContext labelExpression;

        private String resourceName;

        public AbstractNode getFrom() {
            return from;
        }

        public ConnectionBuildContext setFrom(AbstractNode from) {
            this.from = from;
            return this;
        }

        public AbstractNode getTo() {
            return to;
        }

        public ConnectionBuildContext setTo(AbstractNode to) {
            this.to = to;
            return this;
        }

        public DiceParser.ExpressionContext getLabelExpression() {
            return labelExpression;
        }

        public ConnectionBuildContext setLabelExpression(DiceParser.ExpressionContext labelExpression) {
            this.labelExpression = labelExpression;
            return this;
        }

        public String getId() {
            return id;
        }

        public ConnectionBuildContext setId(String id) {
            this.id = id;
            return this;
        }

        public String getResourceName() {
            return resourceName;
        }

        public ConnectionBuildContext setResourceName(String resourceName) {
            this.resourceName = resourceName;
            return this;
        }
    }

    private class ModifierBuildContext {
        private AbstractNode owner;
        private AbstractElement target;
        private DiceParser.ArithmeticExpressionContext expression;

        public AbstractNode getOwner() {
            return owner;
        }

        public ModifierBuildContext setOwner(AbstractNode owner) {
            this.owner = owner;
            return this;
        }

        public AbstractElement getTarget() {
            return target;
        }

        public ModifierBuildContext setTarget(AbstractElement target) {
            this.target = target;
            return this;
        }

        public DiceParser.ArithmeticExpressionContext getExpression() {
            return expression;
        }

        public ModifierBuildContext setExpression(DiceParser.ArithmeticExpressionContext expression) {
            this.expression = expression;
            return this;
        }
    }

    private class TriggerBuildContext {
        private AbstractNode owner;
        private DiceParser.ExpressionContext expression;
        private AbstractElement target;

        public AbstractNode getOwner() {
            return owner;
        }

        public TriggerBuildContext setOwner(AbstractNode owner) {
            this.owner = owner;
            return this;
        }

        public DiceParser.ExpressionContext getExpression() {
            return expression;
        }

        public TriggerBuildContext setExpression(DiceParser.ExpressionContext expression) {
            this.expression = expression;
            return this;
        }

        public AbstractElement getTarget() {
            return target;
        }

        public TriggerBuildContext setTarget(AbstractElement target) {
            this.target = target;
            return this;
        }
    }

    private class ActivatorBuildContext {
        private AbstractNode owner;
        private AbstractNode target;
        private DiceParser.LogicalExpressionContext condition;

        public AbstractNode getOwner() {
            return owner;
        }

        public ActivatorBuildContext setOwner(AbstractNode owner) {
            this.owner = owner;
            return this;
        }

        public AbstractNode getTarget() {
            return target;
        }

        public ActivatorBuildContext setTarget(AbstractNode target) {
            this.target = target;
            return this;
        }

        public DiceParser.LogicalExpressionContext getCondition() {
            return condition;
        }

        public ActivatorBuildContext setCondition(DiceParser.LogicalExpressionContext condition) {
            this.condition = condition;
            return this;
        }
    }

    /**
     * From spec machinations context.
     *
     * @param spec the spec
     * @return the machinations context
     * @throws Exception the exception
     */
    public MachinationsContext fromSpec(YamlSpec spec) throws Exception {
        BuildingContext context = new BuildingContext();
        context.machinations = new MachinationsContext();
        context.spec = spec;

        context.machinations.getConfigs().setTimeMode(TimeMode.from(spec.getConfigs().getTimeMode()));

        this.createNode(context, spec);

        // Connections.
        this.createImplicitConnections(context);
        this.createExplicitConnections(context, spec);

        // Modifiers.
        this.createImplicitModifiers(context);
        this.createExplicitModifiers(context, spec);

        // Trigger
        this.createImplicitTrigger(context);
        this.createExplicitTrigger(context, spec);

        // Activator.
        this.createImplicitActivators(context);
        this.createExplicitActivators(context, spec);

        // --> Third pass.

        // Build connection flow expression.
        context.machinations.getElements().stream()
                .filter(e -> e instanceof ResourceConnection).map(e -> (ResourceConnection)e)
                .forEach(connection -> {
                    ConnectionBuildContext buildContext =
                            (ConnectionBuildContext)context.buildContext.get(connection);
                    context.currentObject = connection;
                    if (buildContext.labelExpression != null) {
                        connection.setFlowRateExpression(buildExpression(context, buildContext.labelExpression));
                    }
                });

        // Now commit the initial resources.
        context.machinations.getElements().stream()
                .filter(e -> e instanceof AbstractNode).map(e -> (AbstractNode)e)
                .forEach(n -> n.resources.commit());

        // Modifiers, triggers and activators.
        context.machinations.getElements().stream()
                .filter(e -> e instanceof AbstractNode).map(e -> (AbstractNode)e)
                .forEach(node -> {
                    node.getModifiers().forEach(modifier -> {
                        ModifierBuildContext buildContext =
                                (ModifierBuildContext)context.buildContext.get(modifier);
                        context.currentObject = modifier;

                        if (buildContext.expression != null) {
                            // modifier.setRateExpression(buildArithmetic(context, buildContext.expression));
                        }
                    });

                    node.getTriggers().forEach(trigger -> {
                        TriggerBuildContext buildContext =
                                (TriggerBuildContext)context.buildContext.get(trigger);
                        context.currentObject = trigger;

                        if (buildContext.expression != null) {
                            trigger.setLabelExpression(buildExpression(context, buildContext.expression));
                        }
                    });

                    node.getActivators().forEach(activator -> {
                        ActivatorBuildContext buildContext =
                                (ActivatorBuildContext)context.buildContext.get(activator);
                        context.currentObject = activator;

                        if (buildContext.condition != null) {
                            // activator.setConditionExpression(buildBoolean(context, buildContext.condition));
                        }
                    });
                });

        context.machinations.initializeIfNeeded();
        return context.machinations;
    }

    public Expression buildExpression(BuildingContext context,
                                      DiceParser.ExpressionContext expressionContext)
    {
        ParseTree decl = expressionContext.getChild(0);

        if (decl instanceof DiceParser.ArithmeticExpressionContext)
            return buildArithmetic(context, (GameMLParser.ArithmeticExpressionContext)decl);
        else if (decl instanceof DiceParser.LogicalExpressionContext)
            return buildBoolean(context, (GameMLParser.LogicalExpressionContext)decl);

        return null;
    }

    public LogicalExpression buildBoolean(BuildingContext context,
                                          GameMLParser.LogicalExpressionContext expressionContext)
    {
        ParseTree decl = expressionContext.getChild(0);

        if (decl instanceof DiceParser.UnaryLogicalExpressionContext) {
            return buildUnaryBoolean(context, (GameMLParser.UnaryLogicalExpressionContext)decl);
        }
        else if (decl instanceof DiceParser.LogicalAndExpressionContext) {
            return buildAnd(context, (GameMLParser.LogicalAndExpressionContext)decl);
        }
        else if (decl instanceof DiceParser.LogicalOrExpressionContext) {
            return buildOr(context, (GameMLParser.LogicalOrExpressionContext)decl);
        }

        return null;
    }

    private LogicalExpression buildAnd(BuildingContext context,
                                       GameMLParser.LogicalAndExpressionContext expressionContext)
    {
        LogicalExpression lhs = buildUnaryBoolean(context,
                (GameMLParser.UnaryLogicalExpressionContext)expressionContext.getChild(0));
        LogicalExpression rhs = buildBoolean(context,
                (GameMLParser.LogicalExpressionContext)expressionContext.getChild(2));
        return And.of(lhs, rhs);
    }

    private LogicalExpression buildOr(BuildingContext context,
                                      GameMLParser.LogicalOrExpressionContext expressionContext)
    {
        LogicalExpression lhs = buildUnaryBoolean(context,
                (GameMLParser.UnaryLogicalExpressionContext)expressionContext.getChild(0));
        LogicalExpression rhs = buildBoolean(context,
                (GameMLParser.LogicalExpressionContext)expressionContext.getChild(2));
        return Or.of(lhs, rhs);
    }

    private LogicalExpression buildUnaryBoolean(BuildingContext context,
                                                GameMLParser.UnaryLogicalExpressionContext expressionContext)
    {
        ParseTree decl = expressionContext.getChild(0);

        if (decl instanceof GameMLParser.RelationalExpressionContext) {
            return buildRelation(context, (GameMLParser.RelationalExpressionContext)decl);
        }
        else if (decl instanceof GameMLParser.LeftImplicitRelationalExpressionContext) {
            return buildLeftImplicitRelation(context, (GameMLParser.LeftImplicitRelationalExpressionContext)decl);
        }
        else if (decl instanceof GameMLParser.RightImplicitRelationalExpressionContext) {
            return buildRightImplicitRelation(context, (GameMLParser.RightImplicitRelationalExpressionContext)decl);
        }
        else if (decl instanceof GameMLParser.GroupLogicalExpressionContext) {
            return buildBoolean(context, (GameMLParser.LogicalExpressionContext)decl.getChild(1));
        }
        else if (decl instanceof TerminalNode) {
            Token opToken = ((TerminalNode)decl).getSymbol();
            LogicalExpression child =
                    buildBoolean(context, (GameMLParser.LogicalExpressionContext)expressionContext.getChild(1));

            if (opToken.getType() == DiceParser.NOT) {
                return Not.of(child);
            }
        }

        return null;
    }

    private LogicalExpression buildLeftImplicitRelation(BuildingContext context,
                                                        GameMLParser.LeftImplicitRelationalExpressionContext expressionContext)
    {
        AbstractNodeRef lhs = null;
        ArithmeticExpression rhs = null;

        if (context.currentObject instanceof Activator) {
            Activator activator = (Activator)context.currentObject;
            lhs = AbstractNodeRef.of(activator.getOwner());
        }
        else if (context.currentObject instanceof ResourceConnection) {
            GateConnection connection = (GateConnection)context.currentObject;
            lhs = AbstractNodeRef.of(connection.getFrom());
        }
        else if (context.currentObject instanceof  Trigger) {
            Trigger trigger = (Trigger)context.currentObject;
            lhs = AbstractNodeRef.of(trigger.getOwner());
        }

        rhs = buildArithmetic(context, (GameMLParser.ArithmeticExpressionContext)expressionContext.getChild(1));

        Token optoken = ((TerminalNode)expressionContext.getChild(0)).getSymbol();

        LogicalExpression expression = Comparison.of(opFromToken(optoken), lhs, rhs);

        if (lhs != null)
            lhs.setContext(new NodeEvaluationContext().setOwner(context.currentObject).setExpression(expression));

        return expression;
    }

    private LogicalExpression buildRightImplicitRelation(BuildingContext context,
                                                         GameMLParser.RightImplicitRelationalExpressionContext expressionContext)
    {
        ArithmeticExpression lhs = null;
        AbstractNodeRef rhs = null;

        if (context.currentObject instanceof Activator) {
            Activator activator = (Activator)context.currentObject;
            rhs = AbstractNodeRef.of(activator.getOwner());
        }
        else if (context.currentObject instanceof ResourceConnection) {
            GateConnection connection = (GateConnection)context.currentObject;
            rhs = AbstractNodeRef.of(connection.getFrom());
        }
        else if (context.currentObject instanceof  Trigger) {
            Trigger trigger = (Trigger)context.currentObject;
            rhs = AbstractNodeRef.of(trigger.getOwner());
        }

        lhs = buildArithmetic(context, (GameMLParser.ArithmeticExpressionContext)expressionContext.getChild(0));

        Token optoken = ((TerminalNode)expressionContext.getChild(1)).getSymbol();

        LogicalExpression expression = Comparison.of(opFromToken(optoken), lhs, rhs);

        if (rhs != null)
            rhs.setContext(new NodeEvaluationContext().setOwner(context.currentObject).setExpression(expression));

        return expression;
    }

    private LogicalExpression buildRelation(BuildingContext context,
                                            GameMLParser.RelationalExpressionContext expressionContext)
    {
        ArithmeticExpression lhs =
                buildArithmetic(context, (GameMLParser.ArithmeticExpressionContext)expressionContext.getChild(0));
        ArithmeticExpression rhs =
                buildArithmetic(context, (GameMLParser.ArithmeticExpressionContext)expressionContext.getChild(2));

        Token optoken = ((TerminalNode)expressionContext.getChild(1)).getSymbol();

        return Comparison.of(opFromToken(optoken), lhs, rhs);
    }

    private String opFromToken(Token token) {
        String op = "";

        switch (token.getType()) {
            case DiceParser.GT: op = Comparison.GT; break;
            case DiceParser.GTE: op = Comparison.GTE; break;
            case DiceParser.LT: op = Comparison.LT; break;
            case DiceParser.LTE: op = Comparison.LTE; break;
            case DiceParser.EQ: op = Comparison.EQ; break;
            case DiceParser.NEQ: op = Comparison.NEQ; break;
        }

        return op;
    }

    public ArithmeticExpression buildArithmetic(BuildingContext context,
                                                GameMLParser.ArithmeticExpressionContext expressionContext)
    {
        ParseTree decl = expressionContext.getChild(0);

        if (decl instanceof GameMLParser.UnaryArithmeticExpressionContext) {
            return buildUnaryArithmetic(context, (GameMLParser.UnaryArithmeticExpressionContext)decl);
        }
        else if (decl instanceof GameMLParser.AdditiveExpressionContext) {
            return buildAdditiveExpressionContext(context, (GameMLParser.AdditiveExpressionContext)decl);
        }
        else if (decl instanceof GameMLParser.MultiplicativeExpressionContext) {
            return buildMultiplicativeExpressionContext(context, (GameMLParser.MultiplicativeExpressionContext)decl);
        }

        return null;
    }

    private ArithmeticExpression buildUnaryArithmetic(BuildingContext context,
                                                      GameMLParser.UnaryArithmeticExpressionContext expressionContext) {

        ParseTree decl = expressionContext.getChild(0);

        if (decl instanceof TerminalNode) {
            Token token = ((TerminalNode)decl).getSymbol();

            if (token.getType() == GameMLParser.PLUS || token.getType() == GameMLParser.MINUS) {
                ArithmeticExpression child = buildArithmetic(
                        context, (GameMLParser.ArithmeticExpressionContext)expressionContext.getChild(1)
                );

                if (token.getType() == DiceParser.MINUS)
                    return Negation.of(child);
                else
                    return child;
            }
            else if (token.getType() == GameMLParser.INTEGER || token.getType() == GameMLParser.REAL) {
                return IntNumber.of(Integer.parseInt(token.getText()));
            }
            else if (token.getType() == GameMLParser.IDENTIFIER) {
                return null;
            }
        }

        return null;
    }

    private ArithmeticExpression buildNumber(DiceParser.NumberContext numberContext) {
        Token token = ((TerminalNode)numberContext.getChild(0)).getSymbol();

        if (token.getType() == DiceParser.INT)
            return IntNumber.of(Integer.parseInt(token.getText()));

        return IntNumber.of(1);
    }

    private ArithmeticExpression buildRandomNumber(DiceParser.RandomNumberContext context) {
        Token token = ((TerminalNode)context.getChild(0)).getSymbol();
        String text = token.getText();

        if (text.equals("D"))
            return DiceNumber.of(1, 6);
        else if (text.charAt(0) == 'D')
            return DiceNumber.of(1, Integer.parseInt(text.substring(1)));
        else if (text.charAt(text.length() - 1) == 'D')
            return DiceNumber.of(Integer.parseInt(text.substring(0, text.length() - 1)), 6);
        else {
            String[] parts = token.getText().split("D");
            return DiceNumber.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
    }

    private ArithmeticExpression buildProbableNumber(DiceParser.ProbableNumberContext context) {
        Token token = ((TerminalNode)context.getChild(0)).getSymbol();
        String text = token.getText();
        int percentage = Integer.parseInt(text.substring(0, text.length() - 1));
        return ProbableNumber.of(percentage * 1e-2f);
    }

    private ArithmeticExpression buildAdditiveExpressionContext(BuildingContext context,
                                                                GameMLParser.AdditiveExpressionContext expressionContext) {
        ArithmeticExpression lhs = buildUnaryArithmetic(
                context, (GameMLParser.UnaryArithmeticExpressionContext)expressionContext.getChild(0));
        ArithmeticExpression rhs = buildArithmetic(
                context,(GameMLParser.ArithmeticExpressionContext)expressionContext.getChild(2));

        Token token = ((TerminalNode)expressionContext.getChild(1)).getSymbol();

        if (token.getType() == DiceParser.PLUS)
            return Addition.of(lhs, rhs);
        else
            return Subtraction.of(lhs, rhs);
    }

    private ArithmeticExpression buildMultiplicativeExpressionContext(BuildingContext context,
                                                                      GameMLParser.MultiplicativeExpressionContext expressionContext) {
        ArithmeticExpression lhs = buildUnaryArithmetic(
                context, (GameMLParser.UnaryArithmeticExpressionContext)expressionContext.getChild(0));
        ArithmeticExpression rhs = buildArithmetic(
                context,(GameMLParser.ArithmeticExpressionContext)expressionContext.getChild(2));

        return Multiplication.of(lhs, rhs);
    }

    private void createNode(BuildingContext context, YamlSpec spec) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        // Build the node, first pass.
        spec.getNodes().forEach(nodeSpec -> {
            if (lastError.get() != null)
                return;

            String id = getOrCreateId(nodeSpec.getId());
            AbstractNode node = null;

            if (nodeSpec instanceof PoolSpec) {
                PoolSpec poolSpec = (PoolSpec)nodeSpec;
                Pool pool = new Pool();
                buildResourcesDecl(pool, poolSpec.getResources());
                buildCapacityDecl(pool, poolSpec.getCapacity());
                node = pool;
            }
            else if (nodeSpec instanceof GateSpec) {
                GateSpec gateSpec = (GateSpec)nodeSpec;
                Gate gate = new Gate();

                if (gateSpec.getDraw() != null) {
                    GameMLParser parser = getGameMLParser(gateSpec.getDraw());
                    gate.setDrawExpression(buildArithmetic(context, parser.arithmeticExpression()));
                }

                gate.setRandom(gateSpec.isRandom());
                node = gate;

            }
            else if (nodeSpec instanceof SourceSpec) {
                node = new Source();
            }
            else if (nodeSpec instanceof DrainSpec) {
                node = new Drain();
            }
            else if (nodeSpec instanceof EndSpec) {
                node = new End();
            }
            else if (nodeSpec instanceof TraderSpec) {
                node = new Trader();
            }
            else if (nodeSpec instanceof ConverterSpec) {
                ConverterSpec converterSpec = (ConverterSpec)nodeSpec;
                Converter converter = new Converter();
                node = converter;
            }

            if (node != null) {
                try {
                    node.machinations = context.machinations;
                    node.setName(nodeSpec.getName())
                            .setActivationMode(ActivationMode.from(nodeSpec.getActivationMode()))
                            .setFlowMode(FlowMode.from(nodeSpec.getFlowMode()))
                            .setId(id);

                    context.machinations.addElement(node);
                    context.elementSpec.put(node, nodeSpec);
                }
                catch (Exception ex) {
                    lastError.compareAndSet(null, ex);
                }
            }
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createExplicitActivators(BuildingContext context, YamlSpec spec) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        spec.getActivators().forEach(a -> {
            try {
                ActivatorBuildContext buildContext = getActivatorBuildContext(context, a);
                if (buildContext.owner == null)
                    throw new Exception(String.format("Owner node identifier is required for activator %s", a));
                createActivator(context, buildContext);
            }
            catch (Exception ex) {
                lastError.compareAndSet(null, ex);
            }
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createImplicitActivators(BuildingContext context) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        List<AbstractNode> nodes = context.machinations.getElements().stream()
                .filter(e -> e instanceof AbstractNode).map(e -> (AbstractNode)e).collect(Collectors.toList());

        nodes.forEach(node -> {
            NodeSpec spec = (NodeSpec)context.elementSpec.get(node);
            spec.getActivators().forEach(a -> {
                try {
                    ActivatorBuildContext buildContext = getActivatorBuildContext(context, a);
                    buildContext.owner = node;
                    createActivator(context, buildContext);
                }
                catch (Exception ex) {
                    lastError.compareAndSet(null, ex);
                }
            });
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createActivator(BuildingContext context, ActivatorBuildContext buildContext) {
        Activator activator = new Activator().setOwner(buildContext.owner).setTarget(buildContext.target)
                .setLabel(buildContext.condition.getText());
        buildContext.owner.getActivators().add(activator);
        context.buildContext.put(activator, buildContext);
    }

    private void createExplicitTrigger(BuildingContext context, YamlSpec spec) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        spec.getTriggers().forEach(t -> {
            try {
                TriggerBuildContext triggerBuildContext = getTriggerBuildContext(context, t);
                if (triggerBuildContext.owner == null)
                    throw new Exception(String.format("Owner node identifier is required for trigger %s", t));
                createTrigger(context, triggerBuildContext);
            }
            catch (Exception ex) {
                lastError.compareAndSet(null, ex);
            }
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createImplicitTrigger(BuildingContext context) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        List<AbstractNode> nodes = context.machinations.getElements().stream()
                .filter(e -> e instanceof AbstractNode).map(e -> (AbstractNode)e).collect(Collectors.toList());

        nodes.forEach(node -> {
            NodeSpec spec = (NodeSpec)context.elementSpec.get(node);
            spec.getTriggers().forEach(m -> {
                try {
                    TriggerBuildContext triggerBuildContext = getTriggerBuildContext(context, m);
                    triggerBuildContext.owner = node;
                    createTrigger(context, triggerBuildContext);
                }
                catch (Exception ex) {
                    lastError.compareAndSet(null, ex);
                }
            });
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createTrigger(BuildingContext context, TriggerBuildContext buildContext) {
        Trigger trigger = new Trigger()
                .setOwner(buildContext.owner)
                .setTarget(buildContext.target);
        buildContext.owner.getTriggers().add(trigger);
        context.buildContext.put(trigger, buildContext);
    }

    private void createExplicitModifiers(BuildingContext context, YamlSpec spec) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        spec.getModifiers().forEach(m -> {
            try {
                ModifierBuildContext modifierBuildContext = getModifierBuildContext(context, m);
                if (modifierBuildContext.owner == null)
                    throw new Exception(String.format("Owner node identifier is required for modifier %s", m));

                createModifier(context, modifierBuildContext);
            }
            catch (Exception ex) {
                lastError.compareAndSet(null, ex);
            }
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createImplicitModifiers(BuildingContext context) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        List<AbstractNode> nodes = context.machinations.getElements().stream()
                .filter(e -> e instanceof AbstractNode).map(e -> (AbstractNode)e).collect(Collectors.toList());

        nodes.forEach(node -> {
            NodeSpec spec = (NodeSpec)context.elementSpec.get(node);
            spec.getModifiers().forEach(m -> {
                try {
                    ModifierBuildContext modifierBuildContext = getModifierBuildContext(context, m);
                    modifierBuildContext.owner = node;
                    createModifier(context, modifierBuildContext);
                }
                catch (Exception ex) {
                    lastError.compareAndSet(null, ex);
                }
            });
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createModifier(BuildingContext context, ModifierBuildContext buildContext) throws Exception {
        Modifier modifier = new Modifier();
        modifier.setOwner(buildContext.owner).setTarget(buildContext.target).setLabel(buildContext.expression.getText());
        buildContext.owner.getModifiers().add(modifier);

        context.buildContext.putIfAbsent(modifier, buildContext);
    }

    private void createExplicitConnections(BuildingContext context, YamlSpec spec) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        spec.getConnections().forEach(c -> {
            try {
                ConnectionBuildContext connectionBuildContext = getConnectionBuildContext(context, c);
                if (connectionBuildContext.from == null)
                    throw new Exception(String.format("Source node identifier is required for connection %s", c));

                createConnection(context, connectionBuildContext);
            }
            catch (Exception ex) {
                lastError.compareAndSet(null, ex);
            }
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createImplicitConnections(BuildingContext context) throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();

        List<AbstractNode> nodes = context.machinations.getElements().stream()
                .filter(e -> e instanceof AbstractNode).map(e -> (AbstractNode)e).collect(Collectors.toList());

        nodes.forEach(node -> {
            NodeSpec spec = (NodeSpec)context.elementSpec.get(node);
            spec.getConnections().forEach(c -> {
                try {
                    ConnectionBuildContext connectionBuildContext = getConnectionBuildContext(context, c);
                    connectionBuildContext.from = node;
                    createConnection(context, connectionBuildContext);
                }
                catch (Exception ex) {
                    lastError.compareAndSet(null, ex);
                }
            });
        });

        if (lastError.get() != null)
            throw lastError.get();
    }

    private void createConnection(BuildingContext context, ConnectionBuildContext connectionBuildContext) throws Exception {
        ResourceConnection connection;

        if (connectionBuildContext.from instanceof Gate)
            connection = new GateConnection();
        else
            connection = new ResourceConnection();

        connection.setFrom(connectionBuildContext.from)
                .setTo(connectionBuildContext.to)
                .setId(getOrCreateId(connectionBuildContext.id));

        if (connectionBuildContext.labelContext != null)
        {
            GameMLParser.ResourceConnectionLabelContext labelContext = connectionBuildContext.labelContext;

            int next = 0;
            ParseTree decl = labelContext.getChild(next);

            if (decl instanceof GameMLParser.LogicalExpressionContext) {
                connection.setCondition(buildBoolean(context, (GameMLParser.LogicalExpressionContext)decl));
                next += 2;
                decl = labelContext.getChild(next);
            }

            if (decl instanceof GameMLParser.MultipliedProbableFlowRateContext) {
                connection.setFlowRate(buildFlowRate(context, (GameMLParser.MultipliedProbableFlowRateContext)decl));
                next += 1;
                decl = labelContext.getChild(next);
            }

            if (decl instanceof GameMLParser.ResourceNameContext) {
                connection.setResourceName(decl.getChild(1).getText());
            }
        }

//        if (connectionBuildContext.labelExpression != null)
//            connection.setLabel(connectionBuildContext.labelExpression.getText());

        context.machinations.addElement(connection);
        context.buildContext.put(connection, connectionBuildContext);

        connectionBuildContext.from.getOutgoingConnections().add(connection);
        connectionBuildContext.to.getIncomingConnections().add(connection);
    }

    private FlowRate buildFlowRate(BuildingContext context, GameMLParser.MultipliedProbableFlowRateContext flowRateContext) {
        FlowRate flowRate = new FlowRate();

        int next = 0;
        ParseTree decl = flowRateContext.getChild(next);

        if (decl instanceof TerminalNode && ((TerminalNode)decl).getSymbol().getType() == GameMLParser.INTEGER) {
            flowRate.setMultiplier(FixedInteger.of(Integer.parseInt(decl.getText())));
            next += 2;
            decl = flowRateContext.getChild(next);
        }

        if (decl instanceof GameMLParser.ProbabilityContext) {
            flowRate.setProbability(parsePercentage(decl.getText()));
            next += 2;
            decl = flowRateContext.getChild(next);
        }

        if (decl instanceof GameMLParser.IntervalFlowRateContext) {
            buildIntervalFlowRate(flowRate, (GameMLParser.IntervalFlowRateContext)decl);
        }

        return flowRate;
    }

    private float parsePercentage(String value) {
        int p = Integer.parseInt(value.substring(0, value.length() - 1));
        return (p / 100.0f);
    }

    private void buildIntervalFlowRate(FlowRate flowRate, GameMLParser.IntervalFlowRateContext flowRateContext) {
        int next = 0;
        ParseTree decl = flowRateContext.getChild(next);

        if (decl instanceof GameMLParser.IntegerExpressionContext) {
            flowRate.setValue(buildInteger((GameMLParser.IntegerExpressionContext)decl));
        }
        else if (decl instanceof TerminalNode && ((TerminalNode)decl).getSymbol().getType() == GameMLParser.ALL) {
            flowRate.setValue(MaxInteger.instance());
        }

        next += 2;
        decl = flowRateContext.getChild(next);
        if (decl != null) {
            flowRate.setInterval(buildInteger((GameMLParser.IntegerExpressionContext)decl));
        }
    }

    private IntegerExpression buildInteger(GameMLParser.IntegerExpressionContext expressionContext) {
        ParseTree decl = expressionContext.getChild(0);

        if (decl instanceof GameMLParser.UnaryIntegerExpressionContext)
            return buildUnaryInteger((GameMLParser.UnaryIntegerExpressionContext)decl);
        else if (decl instanceof GameMLParser.GroupIntegerExpressionContext)
            return buildInteger((GameMLParser.IntegerExpressionContext)decl.getChild(1));
        else if (decl instanceof GameMLParser.BinaryIntegerExpressionContext) {
            IntegerExpression lhs = buildUnaryInteger((GameMLParser.UnaryIntegerExpressionContext)decl.getChild(0));
            IntegerExpression rhs = buildInteger((GameMLParser.IntegerExpressionContext)decl.getChild(2));
            TerminalNode opToken = (TerminalNode)decl.getChild(1);

            if (opToken.getSymbol().getType() == GameMLParser.PLUS)
                return AdditiveIntegerExpression.of(lhs, rhs);
            else
                return SubtractiveIntegerExpression.of(lhs, rhs);
        }

        throw new RuntimeException("Shall not reach here");
    }

    private IntegerExpression buildUnaryInteger(GameMLParser.UnaryIntegerExpressionContext expressionContext) {
        TerminalNode decl = (TerminalNode)expressionContext.getChild(0);

        switch (decl.getSymbol().getType()) {
            case GameMLParser.INTEGER:
            case GameMLParser.REAL:
                return FixedInteger.of(Float.parseFloat(decl.getText()));
            default:
                return RandomInteger.parse(decl.getText());
        }
    }

    private <T extends AbstractElement> T fromIdentifier(BuildingContext context, ParseTree decl, Class clazz)
            throws Exception
    {
        AbstractElement instance = context.machinations.findById(decl.getText());

        if (instance == null || !clazz.isInstance(instance))
            throw new Exception(String.format("Unknown identifier %s", decl.getText()));

        return (T)instance;
    }

    private ConnectionBuildContext getConnectionBuildContext(BuildingContext context, String definition) throws Exception {
        ConnectionBuildContext buildContext = new ConnectionBuildContext();

        GameMLParser parser = getGameMLParser(definition);
        GameMLParser.ResourceConnectionContext resourceConnectionContext = parser.resourceConnection();

        int next = 0;
        ParseTree decl = resourceConnectionContext.getChild(next);

        if (decl instanceof TerminalNode && ((TerminalNode)decl).getSymbol().getType() == GameMLParser.IDENTIFIER) {
            buildContext.from = fromIdentifier(context, decl, AbstractNode.class);
            next += 2;
            decl = resourceConnectionContext.getChild(next);
        }

        if (decl instanceof GameMLParser.ResourceConnectionLabelContext) {
            buildContext.labelContext = (GameMLParser.ResourceConnectionLabelContext)decl;
            next += 2;
            decl = resourceConnectionContext.getChild(next);
        }

        if (decl instanceof TerminalNode && ((TerminalNode)decl).getSymbol().getType() == GameMLParser.TO) {
            next += 1;
            decl = resourceConnectionContext.getChild(next);
        }

        {
            buildContext.to = fromIdentifier(context, decl, AbstractNode.class);
            next += 1;
            decl = resourceConnectionContext.getChild(next);
        }

        if (decl instanceof GameMLParser.ResourceConnectionIdContext) {
            buildContext.id = decl.getChild(1).getText();
        }

        return buildContext;

//        DiceParser parser = getParser(definition);
//        ConnectionBuildContext buildContext = new ConnectionBuildContext();
//
//        ParseTree decl = parser.connectionDefinition().children.get(0);
//        int next = 0;
//        ParseTree nextDecl = decl.getChild(next);
//
//        if (decl instanceof DiceParser.ExplicitConnectionDefinitionContext) {
//            buildContext.from = (AbstractNode)context.machinations.findById(nextDecl.getText());
//            if (buildContext.from == null)
//                throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));
//
//            next += 2;
//        }
//
//        nextDecl = decl.getChild(next);
//        if (nextDecl instanceof DiceParser.ExpressionContext) {
//            buildContext.labelExpression = (DiceParser.ExpressionContext)nextDecl;
//            next += 1;
//        }
//
//        nextDecl = decl.getChild(next);
//        if (nextDecl instanceof DiceParser.ResourceNameContext) {
//            buildContext.resourceName = nextDecl.getChild(1).getText();
//            next += 1;
//        }
//
//        nextDecl = decl.getChild(next);
//        if (((TerminalNode)nextDecl).getSymbol().getType() == DiceParser.TO)
//            next += 1;
//
//        nextDecl = decl.getChild(next);
//        buildContext.to = (AbstractNode)context.machinations.findById(nextDecl.getText());
//        if (buildContext.to == null)
//            throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));
//
//        next += 2;
//        nextDecl = decl.getChild(next);
//        if (nextDecl != null) {
//            buildContext.id =  nextDecl.getText();
//        }
//
//        return buildContext;
    }

    private ModifierBuildContext getModifierBuildContext(BuildingContext context, String definition) throws Exception {
        DiceParser parser = getParser(definition);
        ModifierBuildContext buildContext = new ModifierBuildContext();

        ParseTree decl = parser.modifierDefinition();
        int next = 0;
        ParseTree nextDecl = decl.getChild(next);

        if (nextDecl instanceof TerminalNode) {
            buildContext.owner = (AbstractNode)context.machinations.findById(nextDecl.getText());
            if (buildContext.owner == null)
                throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));

            next += 2;
        }

        nextDecl = decl.getChild(next);
        buildContext.expression = (DiceParser.ArithmeticExpressionContext)nextDecl;
        next += 2;

        nextDecl = decl.getChild(next);
        buildContext.target = context.machinations.findById(nextDecl.getText());

        return buildContext;
    }

    private TriggerBuildContext getTriggerBuildContext(BuildingContext context, String definition) throws Exception {
        DiceParser parser = getParser(definition);
        TriggerBuildContext buildContext = new TriggerBuildContext();

        ParseTree decl = parser.triggerDefinition().children.get(0);
        int next = 0;
        ParseTree nextDecl = decl.getChild(next);

        if (decl instanceof DiceParser.ExplicitTriggerDefinitionContext) {
            buildContext.owner = (AbstractNode)context.machinations.findById(nextDecl.getText());
            if (buildContext.owner == null)
                throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));

            next += 2;
        }

        nextDecl = decl.getChild(next);
        if (nextDecl instanceof DiceParser.ExpressionContext) {
            buildContext.expression = (DiceParser.ExpressionContext)nextDecl;
            next += 2;
        }
        else if (((TerminalNode)nextDecl).getSymbol().getType() == DiceParser.TO)
            next += 1;

        nextDecl = decl.getChild(next);
        buildContext.target = context.machinations.findById(nextDecl.getText());
        if (buildContext.target == null)
            throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));

//        next += 2;
//        nextDecl = decl.getChild(next);
//        if (nextDecl != null) {
//            buildContext.id =  nextDecl.getText();
//        }
//
//        if (decl.getChildCount() == 3) {
//            buildContext.owner = (AbstractNode)context.machinations.findById(nextDecl.getText());
//            if (buildContext.owner == null)
//                throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));
//
//            next = 2;
//        }
//        else
//            next = 1;
//
//        nextDecl = decl.getChild(next);
//        buildContext.target = context.machinations.findById(nextDecl.getText());
//        if (buildContext.target == null)
//            throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));

        return buildContext;
    }

    private ActivatorBuildContext getActivatorBuildContext(BuildingContext context, String definition) throws Exception {
        DiceParser parser = getParser(definition);
        ActivatorBuildContext buildContext = new ActivatorBuildContext();

        ParseTree decl = parser.activatorDefinition();
        int next = 0;
        ParseTree nextDecl = decl.getChild(next);

        if (nextDecl instanceof TerminalNode) {
            buildContext.owner = (AbstractNode)context.machinations.findById(nextDecl.getText());
            if (buildContext.owner == null)
                throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));

            next = 2;
        }

        nextDecl = decl.getChild(next);
        buildContext.condition = (DiceParser.LogicalExpressionContext)nextDecl;
        next += 2;

        nextDecl = decl.getChild(next);
        buildContext.target = (AbstractNode)context.machinations.findById(nextDecl.getText());
        if (buildContext.target == null)
            throw new Exception(String.format("Unknown identifier %s", nextDecl.getText()));

        return buildContext;
    }

    private DiceParser getParser(String expression) {
        CharStream stream = new ANTLRInputStream(expression);
        TokenStream tokens = new CommonTokenStream(new DiceLexer(stream));

        DiceParser parser = new DiceParser(tokens);
        return parser;
    }

    private GameMLParser getGameMLParser(String expression) {
        CharStream stream = new ANTLRInputStream(expression);
        TokenStream tokens = new CommonTokenStream(new GameMLLexer(stream));
        GameMLParser parser = new GameMLParser(tokens);
        return parser;
    }

    private void buildResourcesDecl(AbstractNode node, String resourceExpression) {
//        Map<String, Integer> nodeResources = node.getResources();
//        nodeResources.clear();

        if (resourceExpression == null || resourceExpression.trim().equals(""))
            return;

        DiceParser parser = getParser(resourceExpression);
        DiceParser.ResourceExpressionContext context = parser.resourceExpression();
        context.children.forEach(c -> {
            if (c instanceof DiceParser.SingleResourceExpressionContext) {
                DiceParser.SingleResourceExpressionContext decl = (DiceParser.SingleResourceExpressionContext)c;

                int count = Integer.parseInt(decl.INT().getText());
                String name = decl.IDENTIFIER() != null ? decl.IDENTIFIER().getText().trim() :
                        MachinationsContext.DEFAULT_RESOURCE_NAME;

                node.resources.add(name, count);

//                if (nodeResources.putIfAbsent(name, count) != null)
//                    nodeResources.compute(name, (n, c0) -> (c0 + count));
            }
        });
    }

    private void buildCapacityDecl(AbstractNode node, String resourceExpression) {
        Map<String, Integer> capacity = node.getCapacity();
        capacity.clear();

        if (resourceExpression == null || resourceExpression.trim().equals(""))
            return;

        DiceParser parser = getParser(resourceExpression);
        DiceParser.ResourceExpressionContext context = parser.resourceExpression();
        context.children.forEach(c -> {
            if (c instanceof DiceParser.SingleResourceExpressionContext) {
                DiceParser.SingleResourceExpressionContext decl = (DiceParser.SingleResourceExpressionContext)c;

                int count = Integer.parseInt(decl.INT().getText());
                String name = decl.IDENTIFIER() != null ? decl.IDENTIFIER().getText().trim() :
                            MachinationsContext.DEFAULT_RESOURCE_NAME;

                if (capacity.putIfAbsent(name, count) != null)
                    capacity.compute(name, (n, c0) -> (c0 + count));
            }
        });
    }

    private String getOrCreateId(String id) {
        if (id != null && !id.equals(""))
            return id;
        else
            return ObjectId.get().toHexString();
    }
}
