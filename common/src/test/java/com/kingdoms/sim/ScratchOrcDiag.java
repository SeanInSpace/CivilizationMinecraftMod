package com.kingdoms.sim;

import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.OrcRingLayout;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.culture.Wander;
import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

class ScratchOrcDiag {

    private static void report(String what, Wander wander) {
        SimPos c = new SimPos(0, 64, 0);
        for (int wanted : new int[] {24, 64, 140}) {
            OrcRingLayout l = new OrcRingLayout("orc_ring", wander);
            TownPlan plan = l.planFor(c, wanted);
            java.util.Map<Integer, Integer> byStreet = new java.util.TreeMap<>();
            int reach = 0;
            int rim = 0;
            for (TownPlan.Plot p : plan.plots()) {
                byStreet.merge(p.street(), 1, Integer::sum);
                reach = Math.max(reach, Math.max(Math.abs(p.at().x()), Math.abs(p.at().z())));
                double r = c.horizontalDistance(p.at());
                if (r > 10 && r < 30) {
                    rim++;
                }
            }
            System.out.println("DIAG " + what + " n=" + wanted + " reach=" + reach
                    + " frontage=" + plan.frontagePercent() + "% rim=" + rim
                    + " streets=" + plan.streets().size() + " byStreet=" + byStreet);
        }
    }

    @Test
    void diag() {
        report("wander", ((OrcRingLayout) Layouts.ORC_RING).wander());
        report("straight", Wander.STRAIGHT);
    }
}
