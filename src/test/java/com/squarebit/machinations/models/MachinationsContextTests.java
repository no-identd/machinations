package com.squarebit.machinations.models;

import com.squarebit.machinations.Utils;
import com.squarebit.machinations.specs.yaml.YamlSpec;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MachinationsContextTests {
    @Test
    public void flow_01() throws Exception {
        String path = Utils.absoluteResourcePath("graphs/flow-01.yaml");
        YamlSpec spec = YamlSpec.fromFile(path);
        MachinationsContextFactory factory = new MachinationsContextFactory();
        MachinationsContext machinations = factory.fromSpec(spec);

        {
            machinations.simulateOneTimeStep();

            Pool p0 = (Pool) machinations.findById("p0");
            assertThat(p0.getTotalResourceCount()).isEqualTo(9);

            Pool p1 = (Pool) machinations.findById("p1");
            assertThat(p1.getTotalResourceCount()).isEqualTo(1);

            Pool p2 = (Pool) machinations.findById("p2");
            assertThat(p2.getTotalResourceCount()).isEqualTo(19);

            Pool p3 = (Pool) machinations.findById("p3");
            assertThat(p3.getTotalResourceCount()).isEqualTo(1);

            Pool p4 = (Pool) machinations.findById("p4");
            assertThat(p4.getTotalResourceCount()).isEqualTo(9);

            Pool p5 = (Pool) machinations.findById("p5");
            assertThat(p5.getTotalResourceCount()).isEqualTo(1);

            Pool p6 = (Pool) machinations.findById("p6");
            assertThat(p6.getTotalResourceCount()).isEqualTo(0);
        }

        {
            machinations.simulateOneTimeStep();

            Pool p0 = (Pool) machinations.findById("p0");
            assertThat(p0.getTotalResourceCount()).isEqualTo(8);

            Pool p1 = (Pool) machinations.findById("p1");
            assertThat(p1.getTotalResourceCount()).isEqualTo(2);

            Pool p2 = (Pool) machinations.findById("p2");
            assertThat(p2.getTotalResourceCount()).isEqualTo(18);

            Pool p3 = (Pool) machinations.findById("p3");
            assertThat(p3.getTotalResourceCount()).isEqualTo(2);

            Pool p4 = (Pool) machinations.findById("p4");
            assertThat(p4.getTotalResourceCount()).isEqualTo(8);

            Pool p5 = (Pool) machinations.findById("p5");
            assertThat(p5.getTotalResourceCount()).isEqualTo(1);

            Pool p6 = (Pool) machinations.findById("p6");
            assertThat(p6.getTotalResourceCount()).isEqualTo(1);

            Pool p7 = (Pool) machinations.findById("p7");
            assertThat(p7.getTotalResourceCount()).isEqualTo(8);

            Pool p8 = (Pool) machinations.findById("p8");
            assertThat(p8.getTotalResourceCount()).isEqualTo(2);

            Pool p9 = (Pool) machinations.findById("p9");
            assertThat(p9.getTotalResourceCount()).isEqualTo(0);
        }

        {
            machinations.simulateOneTimeStep();

            Pool p0 = (Pool) machinations.findById("p0");
            assertThat(p0.getTotalResourceCount()).isEqualTo(7);

            Pool p1 = (Pool) machinations.findById("p1");
            assertThat(p1.getTotalResourceCount()).isEqualTo(3);

            Pool p2 = (Pool) machinations.findById("p2");
            assertThat(p2.getTotalResourceCount()).isEqualTo(17);

            Pool p3 = (Pool) machinations.findById("p3");
            assertThat(p3.getTotalResourceCount()).isEqualTo(3);

            Pool p4 = (Pool) machinations.findById("p4");
            assertThat(p4.getTotalResourceCount()).isEqualTo(7);

            Pool p5 = (Pool) machinations.findById("p5");
            assertThat(p5.getTotalResourceCount()).isEqualTo(1);

            Pool p6 = (Pool) machinations.findById("p6");
            assertThat(p6.getTotalResourceCount()).isEqualTo(2);

            Pool p7 = (Pool) machinations.findById("p7");
            assertThat(p7.getTotalResourceCount()).isEqualTo(7);

            Pool p8 = (Pool) machinations.findById("p8");
            assertThat(p8.getTotalResourceCount()).isEqualTo(1);

            Pool p9 = (Pool) machinations.findById("p9");
            assertThat(p9.getTotalResourceCount()).isEqualTo(2);
        }
    }

    @Test
    public void should_support_synchronous_time() throws Exception {
        String path = Utils.absoluteResourcePath("graphs/fig-5-10.yaml");
        YamlSpec spec = YamlSpec.fromFile(path);
        MachinationsContextFactory factory = new MachinationsContextFactory();
        MachinationsContext machinations = factory.fromSpec(spec);

        Pool A = (Pool) machinations.findById("a");
        Pool B = (Pool) machinations.findById("b");
        Pool C = (Pool) machinations.findById("c");
        Pool D = (Pool) machinations.findById("d");

        {
            machinations.simulateOneTimeStep();

            assertThat(A.getResources().size()).isEqualTo(9);
            assertThat(B.getResources().size()).isEqualTo(1);
            assertThat(C.getResources().size()).isEqualTo(0);
            assertThat(D.getResources().size()).isEqualTo(0);
        }

        {
            machinations.simulateOneTimeStep();

            assertThat(A.getResources().size()).isEqualTo(8);
            assertThat(B.getResources().size()).isEqualTo(2);
            assertThat(C.getResources().size()).isEqualTo(0);
            assertThat(D.getResources().size()).isEqualTo(0);
        }

        {
            machinations.simulateOneTimeStep();

            assertThat(A.getResources().size()).isEqualTo(7);
            assertThat(B.getResources().size()).isEqualTo(1);
            assertThat(C.getResources().size()).isEqualTo(1);
            assertThat(D.getResources().size()).isEqualTo(1);
        }
    }

    @Test
    public void should_support_triggers() throws Exception {
        String path = Utils.absoluteResourcePath("graphs/flow-02.yaml");
        YamlSpec spec = YamlSpec.fromFile(path);
        MachinationsContextFactory factory = new MachinationsContextFactory();
        MachinationsContext machinations = factory.fromSpec(spec);

        Pool p0 = (Pool) machinations.findById("p0");
        Pool p1 = (Pool) machinations.findById("p1");
        Pool p2 = (Pool) machinations.findById("p2");
        Pool p3 = (Pool) machinations.findById("p3");
        Pool p4 = (Pool) machinations.findById("p4");
        Pool p5 = (Pool) machinations.findById("p5");
        ResourceConnection e45 = (ResourceConnection) machinations.findById("e45");

        {
            machinations.simulateOneTimeStep();

            assertThat(p2.getResources().size()).isEqualTo(9);
            assertThat(p3.getResources().size()).isEqualTo(1);

            assertThat(p4.getResources().size()).isEqualTo(9);
            assertThat(p5.getResources().size()).isEqualTo(1);
        }
    }
}