package com.squarebit.machinations.machc;

import com.squarebit.machinations.machc.ast.GGraph;
import com.squarebit.machinations.machc.ast.GNode;
import com.squarebit.machinations.machc.ast.GUnit;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MachFrontendTests {
    @Test
    public void specs_001() throws Exception {
        MachFrontend frontend = new MachFrontend();
        GUnit unit = frontend.compileUnit(Utils.absoluteResourcePath("specs-001.mach"));
        assertThat(unit.findGraph("main")).isNotNull();

        GGraph graph = unit.findGraph("main");

        {
            GNode node = (GNode)graph.findField("p0");
            assertThat(node.getName()).isEqualTo("p0");
            assertThat(node.getModifier().isTransitive()).isFalse();
            assertThat(node.getModifier().isInterative()).isFalse();
            assertThat(node.getInitializer()).isNull();
        }

        {
            GNode node = (GNode)graph.findField("p1");
            assertThat(node.getName()).isEqualTo("p1");
            assertThat(node.getModifier().isTransitive()).isFalse();
            assertThat(node.getModifier().isInterative()).isFalse();

            assertThat(node.getInitializer()).isEqualTo(GNode.SOURCE_INITIALIZER);
        }

        {
            GNode node = (GNode)graph.findField("p2");
            assertThat(node.getName()).isEqualTo("p2");
            assertThat(node.getModifier().isTransitive()).isFalse();
            assertThat(node.getModifier().isInterative()).isFalse();

            assertThat(node.getInitializer()).isEqualTo(GNode.DRAIN_INITIALIZER);
        }
    }
}