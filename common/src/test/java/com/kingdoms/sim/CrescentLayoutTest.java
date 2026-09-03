package com.kingdoms.sim;

import com.kingdoms.sim.culture.CrescentLayout;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Layout;
import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What makes the crescent town a crescent town.
 *
 * <p>{@code LayoutTest} already holds this arrangement to the rules every layout
 * keeps — determinism, one plot per piece of ground, nothing standing in the
 * road, frontage above ninety-five per cent. Those pass for a spine with straight
 * side turnings just as well as for one with lobes, which is the point of these:
 * the shared invariants cannot tell the difference between this arrangement and
 * the high street, and something has to, or the lanes get quietly straightened by
 * somebody tidying the arithmetic and nothing turns red.
 */
class CrescentLayoutTest {

    private static final SimPos CENTRE = new SimPos(0, 64, 0);

    /** A layout of this kind with an empty plan cache, as {@code LayoutTest} does. */
    private static CrescentLayout fresh() {
        return new CrescentLayout("crescents");
    }

    @Test
    void theLanesLeaveTheSpineAndComeBackToIt() {
        // The identity, stated as geometry. A lane that starts and ends on the
        // spine and bows a long way off it in between is a crescent; a lane that
        // starts on the spine and ends somewhere else is a side turning, which is
        // what the high street already has. Straightening these -- which is the
        // obvious tidy-up, since a half-circle is more arithmetic than a straight
        // run -- turns this arrangement into that one and would otherwise cost
        // nothing anybody could see.
        TownPlan plan = fresh().planFor(CENTRE, 140);
        int lanes = 0;
        for (TownPlan.Street street : plan.streets()) {
            if (street.kind() != TownPlan.Kind.LANE) {
                continue;
            }
            lanes++;
            assertTrue(Math.abs(street.from().x() - CENTRE.x()) <= 2,
                    "a crescent should leave the spine, not start "
                            + Math.abs(street.from().x() - CENTRE.x()) + " blocks off it");
            assertTrue(Math.abs(street.to().x() - CENTRE.x()) <= 2,
                    "a crescent should come back to the spine, not end "
                            + Math.abs(street.to().x() - CENTRE.x()) + " blocks off it");

            int bow = 0;
            int wrongSide = 0;
            int side = Integer.signum(midpoint(street).x() - CENTRE.x());
            for (SimPos point : street.path()) {
                int off = point.x() - CENTRE.x();
                bow = Math.max(bow, Math.abs(off));
                if (Integer.signum(off) == -side) {
                    wrongSide++;
                }
            }
            assertTrue(bow >= 40,
                    "a crescent bowing only " + bow + " blocks off the spine is a lay-by");
            assertEquals(0, wrongSide,
                    "a crescent bows to one side of the spine, not across it");

            // And it really is a loop, not a bow: the run of the lane is far
            // longer than the distance between its two ends.
            double mouths = Math.hypot(street.to().x() - street.from().x(),
                    street.to().z() - street.from().z());
            assertTrue(street.length() > mouths * 1.4,
                    "a lane " + Math.round(street.length()) + " blocks long between mouths "
                            + Math.round(mouths) + " apart has been straightened out");
        }
        assertTrue(lanes >= 4, "a town of 140 drew only " + lanes + " crescents");
    }

    @Test
    void theSpineIsDrawnInStretchesEvenThoughItIsStraight() {
        // Two points describe this spine exactly, and two points is the wrong
        // answer. PathPlanner routes and gives up on a street a stretch at a
        // time, deliberately -- routing whole streets once took a measured town
        // from sixty-two buildings to thirty-nine, because one ravine anywhere
        // along a thousand-block road condemned the road. A spine handed over as
        // a single run reinstates that exactly: one refusal and the town's main
        // road is abandoned, permanently, since a refused stretch is never
        // retried. The dev renderer trims the same way, by stretch, so a
        // two-point spine is dropped whole there as well and the lobes are drawn
        // with no road between them.
        //
        // So: the points are not describing the shape, they are the unit the
        // town gives up in, and a straight street needs them just as much as a
        // bent one does.
        TownPlan plan = fresh().planFor(CENTRE, 140);
        TownPlan.Street spine = plan.streets().get(0);
        assertEquals(TownPlan.Kind.SPINE, spine.kind(), "the spine should be street nought");
        assertTrue(spine.path().size() >= spine.length() / 16,
                "a spine " + Math.round(spine.length()) + " blocks long was drawn in "
                        + (spine.path().size() - 1) + " stretches");
        for (SimPos point : spine.path()) {
            assertEquals(CENTRE.x(), point.x(), "the spine is straight");
        }
    }

    @Test
    void theCrescentsAlternateSidesGoingUpTheSpine() {
        // A chain of lobes all on one side is a comb, and it grows lopsided --
        // the reason the high street alternates its lanes too. Checked in the
        // order somebody walking the spine meets them rather than the order the
        // plan drew them, because those are not the same order and only one of
        // them is what the town looks like.
        // Nested crescents share a station and so share a side, so the lanes are
        // gathered into stations first. Gathering has to be by position and not
        // by collapsing runs of the same side: a run of two same-sided stations
        // IS the fault, and folding it into one entry would leave a list that
        // alternates perfectly and a town that does not.
        TownPlan plan = fresh().planFor(CENTRE, 200);
        List<int[]> stations = new ArrayList<>(innermostLanes(plan));
        stations.sort((a, b) -> Integer.compare(a[0], b[0]));

        assertTrue(stations.size() >= 4,
                "only " + stations.size() + " stations along the spine to alternate");
        for (int i = 1; i < stations.size(); i++) {
            assertEquals(-stations.get(i - 1)[1], stations.get(i)[1],
                    "the stations at z " + stations.get(i - 1)[0] + " and "
                            + stations.get(i)[0]
                            + " hang off the same side of the spine");
        }
    }

    @Test
    void theGroundInsideEveryCrescentIsLeftOpen() {
        // The lens is the whole reason for looping a lane rather than running two
        // straight ones. It is also the first thing an ordinary layout would fill:
        // the spine offers frontage along its length, and a station's own inner
        // rank stands inside its lane, so without a rule the middle of every lobe
        // ends up with houses in it and the town is a chain of blobs rather than
        // a chain of greens.
        //
        // Measured against the innermost lane of each station, which is the one
        // whose lens is meant to be grass. The lens of an outer lane holds the
        // whole crescent it encloses, quite deliberately.
        for (int wanted : new int[] {64, 140, 256}) {
            TownPlan plan = fresh().planFor(CENTRE, wanted);
            for (int[] station : innermostLanes(plan)) {
                int zc = station[0];
                int side = station[1];
                int bow = station[2];
                // Inside the inner rank of houses, and clear of the spine's own
                // carriageway, which is the ground a person standing on the green
                // would call the green.
                double green = bow - 13 - Layout.DEFAULT_SPAN / 2.0 - 1;
                for (TownPlan.Plot plot : plan.plots()) {
                    int dx = plot.at().x() - CENTRE.x();
                    int dz = plot.at().z() - CENTRE.z() - zc;
                    if (side * dx <= 10) {
                        continue;   // on the spine or the far side of it
                    }
                    assertTrue(Math.hypot(dx, dz) >= green,
                            "a town of " + wanted + " put a plot at " + plot.at()
                                    + " on the green inside the crescent at z " + zc);
                }
            }
        }
    }

    /**
     * Every station's innermost lane, as its middle z, its side and its bow.
     *
     * <p>Read off the plan rather than off the layout's constants, so the test
     * still describes the town if the constants move.
     */
    private static List<int[]> innermostLanes(TownPlan plan) {
        List<int[]> found = new ArrayList<>();
        for (TownPlan.Street street : plan.streets()) {
            if (street.kind() != TownPlan.Kind.LANE) {
                continue;
            }
            SimPos mid = midpoint(street);
            int zc = mid.z() - plan.centre().z();
            int side = Integer.signum(mid.x() - plan.centre().x());
            int bow = Math.abs(mid.x() - plan.centre().x());
            int at = -1;
            for (int i = 0; i < found.size(); i++) {
                if (Math.abs(found.get(i)[0] - zc) < 8 && found.get(i)[1] == side) {
                    at = i;
                }
            }
            if (at < 0) {
                found.add(new int[] {zc, side, bow});
            } else if (bow < found.get(at)[2]) {
                found.set(at, new int[] {zc, side, bow});
            }
        }
        return found;
    }

    /** The furthest point of a lane from the spine, which is its middle. */
    private static SimPos midpoint(TownPlan.Street street) {
        return street.path().get(street.path().size() / 2);
    }

    @Test
    void aCrescentTownIsNotTheHighStreetWithBends() {
        // The claim the arrangement exists to make. Both are a spine with lanes
        // off it; if they came out the same shape there would be no reason to
        // have two.
        TownPlan crescents = fresh().planFor(CENTRE, 140);
        TownPlan high = Layouts.HIGH_STREET.planFor(CENTRE, 140);
        int looped = 0;
        for (TownPlan.Street street : high.streets()) {
            if (Math.abs(street.from().x() - CENTRE.x()) <= 2
                    && Math.abs(street.to().x() - CENTRE.x()) <= 2
                    && street.kind() == TownPlan.Kind.LANE) {
                looped++;
            }
        }
        assertEquals(0, looped, "the high street has grown loops and these are now one town");
        assertTrue(crescents.frontagePercent() >= 95,
                "the crescent town fronted only " + crescents.frontagePercent() + "%");
    }

    /**
     * The town as a picture, at two sizes, with the numbers a reviewer wants.
     *
     * <p>Printed rather than merely asserted because the failure this arrangement
     * is most likely to have is not one an assertion catches: a plan can keep
     * every rule, front every plot and still be a shape nobody would call a town.
     * Every siting fault this project has had was found by somebody looking at a
     * picture, and this is the cheapest picture there is.
     *
     * <p>The bars are the ones a spine layout is most likely to fail. Frontage,
     * because a curve refuses plots a straight street would take; and reach,
     * because a town that answers growth by lengthening its spine passes every
     * other rule while turning into a ribbon.
     */
    @Test
    void theTownReadsAsASpineWithLobes() {
        for (int wanted : new int[] {64, 140}) {
            TownPlan plan = fresh().planFor(CENTRE, wanted);
            int reach = 0;
            for (TownPlan.Plot plot : plan.plots()) {
                reach = Math.max(reach, (int) Math.round(Math.hypot(
                        plot.at().x() - CENTRE.x(), plot.at().z() - CENTRE.z())));
            }
            System.out.println();
            int wide = 0;
            int along = 0;
            double paved = 0;
            for (TownPlan.Plot plot : plan.plots()) {
                wide = Math.max(wide, Math.abs(plot.at().x() - CENTRE.x()));
                along = Math.max(along, Math.abs(plot.at().z() - CENTRE.z()));
            }
            for (TownPlan.Street street : plan.streets()) {
                paved += street.length();
            }
            System.out.println("crescents at " + wanted + " plots: "
                    + plan.frontagePercent() + "% fronting, "
                    + plan.streets().size() + " streets, furthest plot " + reach
                    + ", " + wide + " off the spine, " + along + " along it, "
                    + Math.round(paved) + " blocks of road");
            System.out.println(draw(plan));

            assertEquals(wanted, plan.size(), "the plan came up short");
            assertTrue(plan.frontagePercent() >= 95,
                    "only " + plan.frontagePercent() + "% of " + wanted + " fronted a street");
            assertTrue(reach >= 60 && reach <= 250,
                    "a town of " + wanted + " reached " + reach
                            + " blocks, which is not a town anybody walks across");
        }
    }

    /**
     * The plan as characters: a plot, a street, or open ground.
     *
     * <p>Two blocks of north-south to one of east-west, because a character is
     * about twice as tall as it is wide and a map drawn square comes out twice as
     * long as the town is.
     */
    private static String draw(TownPlan plan) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TownPlan.Plot plot : plan.plots()) {
            minX = Math.min(minX, plot.at().x());
            maxX = Math.max(maxX, plot.at().x());
            minZ = Math.min(minZ, plot.at().z());
            maxZ = Math.max(maxZ, plot.at().z());
        }
        int across = Math.max(2, (maxX - minX) / 72 + 1);
        int down = across * 2;
        int wide = (maxX - minX) / across + 1;
        int tall = (maxZ - minZ) / down + 1;
        char[][] map = new char[tall][wide];
        for (char[] row : map) {
            java.util.Arrays.fill(row, ' ');
        }

        for (TownPlan.Street street : plan.streets()) {
            List<SimPos> path = street.path();
            for (int i = 1; i < path.size(); i++) {
                SimPos a = path.get(i - 1);
                SimPos b = path.get(i);
                int steps = (int) Math.max(1, Math.hypot(b.x() - a.x(), b.z() - a.z()));
                for (int s = 0; s <= steps; s++) {
                    int x = a.x() + (b.x() - a.x()) * s / steps;
                    int z = a.z() + (b.z() - a.z()) * s / steps;
                    int col = (x - minX) / across;
                    int row = (z - minZ) / down;
                    if (row >= 0 && row < tall && col >= 0 && col < wide
                            && map[row][col] == ' ') {
                        map[row][col] = '.';
                    }
                }
            }
        }
        for (TownPlan.Plot plot : plan.plots()) {
            int col = (plot.at().x() - minX) / across;
            int row = (plot.at().z() - minZ) / down;
            map[row][col] = '#';
        }

        StringBuilder out = new StringBuilder();
        for (char[] row : map) {
            out.append(new String(row).replaceAll("\\s+$", "")).append('\n');
        }
        return out.toString();
    }

    @Test
    void thePlanFillsWithoutBeingAskedTwice() {
        // A design that comes up short is asked again at twice the size, and this
        // one answers that by nesting a third rank at every station -- a different
        // town, not a bigger one. So the plan is checked at the size every plan is
        // laid at: two crescents to a station, and the whole of a plan of 256
        // taken off them and the spine between.
        TownPlan plan = fresh().planFor(CENTRE, 256);
        int lanes = 0;
        for (TownPlan.Street street : plan.streets()) {
            if (street.kind() == TownPlan.Kind.LANE) {
                lanes++;
            }
        }
        assertEquals(2 * innermostLanes(plan).size(), lanes,
                "a plan of 256 should nest two crescents at each station, not "
                        + ((double) lanes / innermostLanes(plan).size()));
        assertEquals(256, plan.size(), "the plan came up short of the size it lays at");
        assertEquals(100, plan.frontagePercent(),
                "a plan of 256 left plots fronting nothing");
    }

    @Test
    void aCultureCanNameThisArrangement() {
        // The id is what a save carries. A layout the registry does not know by
        // the name a culture writes down becomes a village on reload, silently.
        assertEquals(Culture.LAYOUT_CRESCENTS, Layouts.CRESCENTS.id());
        assertEquals(Layouts.CRESCENTS, Layouts.of(Culture.LAYOUT_CRESCENTS));
    }
}
