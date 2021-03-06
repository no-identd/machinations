package com.squarebit.machinations;

import com.squarebit.machinations.engine.LogicalExpression;
import com.squarebit.machinations.engine.MaxInteger;
import com.squarebit.machinations.engine.RandomInteger;
import com.squarebit.machinations.models.*;
import com.squarebit.machinations.parsers.GameMLLexer;
import com.squarebit.machinations.parsers.GameMLParser;
import com.squarebit.machinations.specs.yaml.YamlSpec;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.junit.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class MachinationsFactoryTests {
    @Test
    public void should_load_elements_from_yaml_spec() throws Exception {
        String path = Utils.absoluteResourcePath("graphs/generic-elements.yaml");
        YamlSpec spec = YamlSpec.fromFile(path);
        MachinationsFactory factory = new MachinationsFactory();

        Machinations context = factory.fromSpec(spec);
        assertThat(context).isNotNull();

        {
            Element element = context.findById("p0");
            assertThat(element).isNotNull();
            assertThat(element instanceof Pool).isTrue();

            Pool pool = (Pool)element;
            assertThat(pool.getName()).isEqualTo("aaa");
            assertThat(pool.getActivationMode()).isEqualTo(ActivationMode.PASSIVE);
            assertThat(pool.getFlowMode()).isEqualTo(FlowMode.AUTOMATIC);

            assertThat(pool.getResourceCount("mana")).isEqualTo(125);
            assertThat(pool.getResourceCount("gold")).isEqualTo(30);

//            assertThat(pool.getResourceCapacity("mana")).isEqualTo(200);
//            assertThat(pool.getResourceCapacity("gold")).isEqualTo(-1);

            assertThat(pool.getOutgoingConnections().size()).isEqualTo(12);

            // assertThat(pool.getModifiers().size()).isEqualTo(1);

            {
                ValueModifier modifier = (ValueModifier)context.findById("m_p0_p1_0");

                assertThat(modifier.getTarget()).isEqualTo(context.findById("p1"));
                assertThat(modifier.getValue().eval()).isEqualTo(2);
                assertThat(modifier.getSign()).isEqualTo(1);
            }

            {
                ValueModifier modifier = (ValueModifier)context.findById("m_p0_p1_1");

                assertThat(modifier.getTarget()).isEqualTo(context.findById("p1"));
                assertThat(modifier.getValue().eval()).isEqualTo(2);
                assertThat(modifier.getSign()).isEqualTo(-1);
            }

            {
                IntervalModifier modifier = (IntervalModifier)context.findById("m_p0_p1_2");

                assertThat(modifier.getTarget()).isEqualTo(context.findById("p0_p2_1"));
                assertThat(modifier.getValue().eval()).isEqualTo(1);
                assertThat(modifier.getSign()).isEqualTo(1);
            }

            {
                ProbabilityModifier modifier = (ProbabilityModifier)context.findById("m_p0_p1_3");

                assertThat(modifier.getTarget()).isEqualTo(context.findById("p0_p2_2"));
                assertThat(modifier.getValue().eval()).isEqualTo(5);
                assertThat(modifier.getSign()).isEqualTo(1);
            }

            {
                MultiplierModifier modifier = (MultiplierModifier) context.findById("m_p0_p1_4");

                assertThat(modifier.getTarget()).isEqualTo(context.findById("p0_p2_5"));
                assertThat(modifier.getValue().eval()).isEqualTo(3);
                assertThat(modifier.getSign()).isEqualTo(-1);
            }

            // assertThat(pool.getTriggers().size()).isEqualTo(4);

//            assertThat(pool.getActivators().size()).isEqualTo(1);
        }

        {
            Element element = context.findById("p1");
            assertThat(element).isNotNull();
            assertThat(element instanceof Pool).isTrue();

            Pool pool = (Pool)element;
            assertThat(pool.getName()).isEqualTo("bbb");
            assertThat(pool.getActivationMode()).isEqualTo(ActivationMode.AUTOMATIC);
            assertThat(pool.getFlowMode()).isEqualTo(FlowMode.PUSH_ANY);
            assertThat(pool.getTotalResourceCount()).isEqualTo(0);

            assertThat(pool.getIncomingConnections().size()).isEqualTo(1);
        }

        {
            Element element = context.findById("p2");
            assertThat(element).isNotNull();
            assertThat(element instanceof Pool).isTrue();

            Pool pool = (Pool)element;
            assertThat(pool.getName()).isEqualTo("ccc");
            assertThat(pool.getActivationMode()).isEqualTo(ActivationMode.INTERACTIVE);
            assertThat(pool.getFlowMode()).isEqualTo(FlowMode.PULL_ALL);
            assertThat(pool.getTotalResourceCount()).isEqualTo(100);

            assertThat(pool.getOutgoingConnections().size()).isEqualTo(2);
            assertThat(pool.getIncomingConnections().size()).isEqualTo(10);
        }

        {
            Element element = context.findById("p3");
            assertThat(element).isNotNull();
            assertThat(element instanceof Pool).isTrue();

            Pool pool = (Pool)element;
            assertThat(pool.getName()).isEqualTo("ddd");
            assertThat(pool.getActivationMode()).isEqualTo(ActivationMode.STARTING_ACTION);
            assertThat(pool.getFlowMode()).isEqualTo(FlowMode.PUSH_ALL);
            assertThat(pool.getTotalResourceCount()).isEqualTo(200);

            assertThat(pool.getIncomingConnections().size()).isEqualTo(2);

            assertThat(pool.getModifiers().size()).isEqualTo(1);
            Modifier modifier = pool.getModifiers().stream().findFirst().get();
            assertThat(modifier.getTarget()).isEqualTo(context.findById("p4"));
        }

        {
            Element element = context.findById("p4");
            assertThat(element).isNotNull();
            assertThat(element instanceof Pool).isTrue();

            Pool pool = (Pool)element;
            assertThat(pool.getName()).isEqualTo("eee");
            assertThat(pool.getActivationMode()).isEqualTo(ActivationMode.PASSIVE);
            assertThat(pool.getFlowMode()).isEqualTo(FlowMode.PULL_ANY);
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p1");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p1"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(1);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(1);

            assertThat(flowRate.getProbability().eval()).isEqualTo(100);
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_0");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isTrue();
            {
                RandomInteger dice = (RandomInteger) flowRate.getValue();
                assertThat(dice.getTimes()).isEqualTo(2);
                assertThat(dice.getFaces()).isEqualTo(10);
            }

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(1);

            assertThat(flowRate.getProbability().eval()).isEqualTo(100);
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_1");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(1);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(4);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(1);

            assertThat(flowRate.getProbability().eval()).isEqualTo(100);
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_2");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(1);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(1);

            assertThat(flowRate.getProbability().eval()).isEqualTo(50);
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_3");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(1);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(3);

            assertThat(flowRate.getProbability().eval()).isEqualTo(50);
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_4");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(10);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(1);

            assertThat(flowRate.getProbability().eval()).isEqualTo(100);

            assertThat(connection.getResourceName()).isEqualTo("gold");
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_5");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(10);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(2);

            assertThat(flowRate.getProbability().eval()).isEqualTo(100);

            assertThat(connection.getResourceName()).isEqualTo("gold");
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_6");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(1);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(2);

            assertThat(flowRate.getProbability().eval()).isEqualTo(30);

            assertThat(connection.getResourceName()).isEqualTo("gold");
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_7");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue()).isEqualTo(MaxInteger.instance());

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(1);

            assertThat(flowRate.getProbability().eval()).isEqualTo(100);

            assertThat(connection.getResourceName()).isNullOrEmpty();
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_8");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue()).isEqualTo(MaxInteger.instance());

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(1);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(1);

            assertThat(flowRate.getProbability().eval()).isEqualTo(100);

            assertThat(connection.getResourceName()).isEqualTo("gold");
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p0_p2_9");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p0"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p2"));

            FlowRate flowRate = connection.getFlowRate();

            assertThat(flowRate.getValue().isRandom()).isFalse();
            assertThat(flowRate.getValue().eval()).isEqualTo(10);

            assertThat(flowRate.getInterval().isRandom()).isFalse();
            assertThat(flowRate.getInterval().eval()).isEqualTo(5);

            assertThat(flowRate.getMultiplier().isRandom()).isFalse();
            assertThat(flowRate.getMultiplier().eval()).isEqualTo(5);

            assertThat(flowRate.getProbability().eval()).isEqualTo(30);

            assertThat(connection.getResourceName()).isEqualTo("gold");
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p2_p3");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p2"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p3"));
        }

        {
            ResourceConnection connection = (ResourceConnection)context.findById("p2_p4");
            assertThat(connection.getFrom()).isEqualTo(context.findById("p2"));
            assertThat(connection.getTo()).isEqualTo(context.findById("p4"));
        }
    }

    @Test
    public void should_load_triggers() throws Exception {
        String path = Utils.absoluteResourcePath("graphs/generic-elements.yaml");
        YamlSpec spec = YamlSpec.fromFile(path);
        MachinationsFactory factory = new MachinationsFactory();

        Machinations context = factory.fromSpec(spec);
        assertThat(context).isNotNull();

        {
            Trigger trigger = (Trigger)context.findById("t_p0_0");
            assertThat(trigger.getTarget()).isEqualTo(context.findById("p1"));
            assertThat(trigger.getCondition().eval()).isTrue();

            assertThat(trigger.isUsingProbability()).isFalse();
            assertThat(trigger.getDistribution().eval()).isEqualTo(1);
        }

        {
            Trigger trigger = (Trigger)context.findById("t_p0_1");
            assertThat(trigger.getTarget()).isEqualTo(context.findById("p1"));
            assertThat(trigger.getCondition().eval()).isTrue();

            assertThat(trigger.isUsingProbability()).isFalse();
            assertThat(trigger.getDistribution().eval()).isEqualTo(1);
        }

        {
            Trigger trigger = (Trigger)context.findById("t_p0_2");
            assertThat(trigger.getTarget()).isEqualTo(context.findById("p1"));
            assertThat(trigger.getCondition().eval()).isTrue();

            assertThat(trigger.isUsingProbability()).isTrue();
            assertThat(trigger.getProbability().eval()).isEqualTo(50);
        }

        {
            Trigger trigger = (Trigger)context.findById("t_p0_3");
            assertThat(trigger.getTarget()).isEqualTo(context.findById("p1"));
            assertThat(trigger.getCondition().eval()).isTrue();

            assertThat(trigger.isUsingProbability()).isFalse();
            assertThat(trigger.getDistribution().eval()).isEqualTo(1);
        }

        {
            Trigger trigger = (Trigger)context.findById("t_p0_4");
            assertThat(trigger.getTarget()).isEqualTo(context.findById("p1"));
            assertThat(trigger.getCondition().eval()).isFalse();

            assertThat(trigger.isUsingProbability()).isTrue();
            assertThat(trigger.getProbability().eval()).isEqualTo(50);
        }
    }

    @Test
    public void should_load_activators() throws Exception {
        String path = Utils.absoluteResourcePath("graphs/generic-elements.yaml");
        YamlSpec spec = YamlSpec.fromFile(path);
        MachinationsFactory factory = new MachinationsFactory();

        Machinations context = factory.fromSpec(spec);
        assertThat(context).isNotNull();

        {
            Activator activator = (Activator)context.findById("a_p0_0");
            assertThat(activator.getCondition().eval()).isTrue();
        }

        {
            Activator activator = (Activator)context.findById("a_p0_1");
            assertThat(activator.getCondition().eval()).isFalse();
        }
    }

    @Test(expected = Exception.class)
    public void should_not_load_elements_with_same_id_from_yaml_spec() throws Exception {
        String path = Utils.absoluteResourcePath("graphs/duplicated-node-id.yaml");
        YamlSpec spec = YamlSpec.fromFile(path);
        MachinationsFactory factory = new MachinationsFactory();

        Machinations context = factory.fromSpec(spec);
    }

    private GameMLParser getParser(String decl) {
        CharStream stream = new ANTLRInputStream(decl);
        TokenStream tokens = new CommonTokenStream(new GameMLLexer(stream));
        GameMLParser parser = new GameMLParser(tokens);
        return parser;
    }

    private <T> T parse(String decl, Function<GameMLParser, T> consumer) {
        return consumer.apply(getParser(decl));
    }

    private LogicalExpression bool(String decl) throws Exception {
        MachinationsFactory factory = new MachinationsFactory();
        return factory.buildBoolean(null, parse(decl, GameMLParser::logicalExpression));
    }

    @Test
    public void should_construct_boolean_expressions() throws Exception {
        assertThat(bool("2 > 1").eval()).isTrue();
        assertThat(bool("2 >= 2").eval()).isTrue();
        assertThat(bool("2 < 3").eval()).isTrue();
        assertThat(bool("2 <= 2").eval()).isTrue();
        assertThat(bool("2 == 2").eval()).isTrue();
        assertThat(bool("2 != 3").eval()).isTrue();

        assertThat(bool("2 != 3 && 1 == 1").eval()).isTrue();
        assertThat(bool("2 != 3 && 1 != 1").eval()).isFalse();

        assertThat(bool("(((2 != 3)) && 1 == 1)").eval()).isTrue();

        assertThat(bool("(((2 != 3)) && 1 * 2 == 1)").eval()).isFalse();
        assertThat(bool("(((2 != 3)) || 1 * 2 == 1)").eval()).isTrue();
    }
}
