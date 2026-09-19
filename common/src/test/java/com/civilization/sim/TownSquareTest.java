package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Heart;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.work.Furnishings;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing stands on the town square, in any arrangement, ever.
 *
 * <p><strong>The report.</strong> A playtest of Wilbury — seed 8675309, a vale
 * town laid out as {@code ring_streets} — found the notice-board post at the
 * middle of the paved square walled in by cobblestone on all four faces from
 * y=106 to y=110. A cottage was standing on the square. {@code Heart}'s first
 * bullet has said since the class was written that the square "is not a plot and
 * never becomes one", and nothing anywhere enforced it.
 *
 * <p><strong>What was actually happening,</strong> measured by growing every
 * arrangement on the recorded ground of that same seed and recording where the
 * square was planned at every one of seven hundred steps:
 *
 * <pre>
 *   arrangement          places   under a building   square ended up
 *   thorp                  8            5             30 blocks out
 *   crossroads             6            0
 *   ring_streets           6            0             12 blocks out
 *   organic                6            1             34 blocks out
 *   ring                   5            3             31 blocks out
 *   crescents              5            3             28 blocks out
 *   high_street            4            1             23 blocks out
 *   radial_concentric      4            1             18 blocks out
 * </pre>
 *
 * <p>Two faults in one. The square <em>wandered</em> — up to eight different
 * places over one town's life — and it wandered <em>away from the middle</em>,
 * ending twenty to thirty-four blocks out in six of the eight. Both have the same
 * cause: nothing held the ground, so the dressing sited the square on whatever was
 * free at that moment, and the middle of a town is never free. The plots fill it,
 * and the roads <em>aim</em> at it, so every column round the hub is inside a
 * carriageway's clearance. Each time the square moved it left its paving and its
 * post behind in the world, and the town then built on the ground it had left.
 *
 * <p>After: one place per arrangement, at the middle, at its full thirteen
 * blocks, and nothing standing on any of them.
 *
 * <p>These are fitness functions in {@code LayoutFitnessTest}'s sense — every
 * arrangement has to pass, so a new one is held to the same bar without anybody
 * writing it a test.
 */
class TownSquareTest {

    /** The center every survey in this project uses, on the recorded ground. */
    private static final SimPos CENTER = new SimPos(16, 64, 80);

    private static final int STEPS = 700;

    private static RecordedTerrain ground;

    private static synchronized RecordedTerrain ground() {
        if (ground == null) {
            ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        }
        return ground;
    }

    /**
     * Every arrangement a culture can ask for, gathered rather than listed.
     *
     * <p>{@code LayoutFitnessTest}'s argument: a list that has to be remembered
     * is a list that misses the arrangement nobody remembered.
     */
    private static List<String> layouts() {
        Set<String> named = new LinkedHashSet<>();
        for (Culture culture : Culture.all()) {
            named.addAll(culture.layouts());
        }
        return List.copyOf(named);
    }

    /** A grown town, and every place it ever planned its square. */
    private record Grown(Settlement town, List<Furnishings.Furnishing> squares) {
    }

    /**
     * Grown once per arrangement and shared by every rule below.
     *
     * <p>Seven hundred steps of a whole settlement on recorded ground, times
     * fifteen arrangements, is not cheap and the rules ask different questions of
     * the same town. No assertion mutates it.
     */
    private static final Map<String, Grown> GROWN = new LinkedHashMap<>();

    private static synchronized Grown grown(String layoutId) {
        Grown known = GROWN.get(layoutId);
        if (known != null) {
            return known;
        }
        Settlement town = new Settlement(Settlement.Id.random(), "Wilbury", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        for (Culture culture : Culture.all()) {
            if (culture.layouts().contains(layoutId)) {
                town.setCultureId(culture.id());
                break;
            }
        }
        town.setLayoutId(layoutId);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTER));
        }
        // Kept as the distinct squares the town ever planned, in the order it
        // planned them. A square that never moves leaves exactly one.
        List<Furnishings.Furnishing> squares = new ArrayList<>();
        for (int step = 1; step <= STEPS; step++) {
            town.stores().add(TownStores.WOOD, 8);
            town.stores().add(TownStores.STONE, 6);
            town.step(new SimContext(ground(), step, SimSettings.SANDBOX));
            Furnishings.Furnishing square = squareOf(town);
            if (square != null && !squares.contains(square)) {
                squares.add(square);
            }
        }
        Grown made = new Grown(town, squares);
        GROWN.put(layoutId, made);
        return made;
    }

    private static Furnishings.Furnishing squareOf(Settlement town) {
        for (Furnishings.Furnishing piece : Furnishings.pieces(town)) {
            if (piece.piece() == Furnishings.Piece.SQUARE) {
                return piece;
            }
        }
        return null;
    }

    private static List<Building> standing(Settlement town) {
        List<Building> out = new ArrayList<>();
        for (Building b : town.buildings()) {
            if (BuildPlanner.holdsGround(b.blueprintId())) {
                out.add(b);
            }
        }
        return out;
    }

    /**
     * The reported fault, as a rule: no wall stands over any paving the town laid.
     *
     * <p>Measured against every square the town ever planned rather than only the
     * one it holds now, because paving is written into the world and stays there.
     * A square the town moved off is still a square somebody is walking on, and a
     * cottage raised over it is still a notice-board post in a cobblestone box.
     *
     * <p>Walls and not plots, deliberately: this is the thing a player standing in
     * the square sees. The plan-level statement of the same rule is below.
     */
    @Test
    void noArrangementRaisesABuildingOnGroundItPaved() {
        List<String> found = new ArrayList<>();
        for (String layout : layouts()) {
            Grown grown = grown(layout);
            for (Furnishings.Furnishing square : grown.squares()) {
                for (Building b : standing(grown.town())) {
                    int[] half = BuildPlanner.wallsHalfOf(b.blueprintId(), b.facing(),
                            grown.town().catalog());
                    if (Math.abs(b.origin().x() - square.at().x()) <= half[0] + square.reach()
                            && Math.abs(b.origin().z() - square.at().z())
                                    <= half[1] + square.reach()) {
                        found.add(layout + ": " + b.blueprintId() + " at " + b.origin()
                                + " stands on the square at " + square.at()
                                + " (reach " + square.reach() + ")");
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), found.size() + " buildings on a town square:\n"
                + String.join("\n", found));
    }

    /**
     * The same rule as the planners state it: no plot claim touches the reserve.
     *
     * <p>The rule above is what a player sees; this is what the code promises, and
     * they are separate assertions on purpose. {@code Heart.onTheSquare} is the
     * reserve and {@code Settlement.isPlotFree} is the only thing that consults it,
     * so a siting path added later that does not go through {@code isPlotFree} —
     * and there have been several — turns this red without having to be remembered.
     */
    @Test
    void noArrangementSitesAPlotOnTheGroundTheSquareHolds() {
        List<String> found = new ArrayList<>();
        for (String layout : layouts()) {
            Grown grown = grown(layout);
            Settlement town = grown.town();
            for (Building b : standing(town)) {
                int span = BuildPlanner.plotSpanOf(b.blueprintId(), town.catalog());
                if (Heart.onTheSquare(town, b.origin(), span)) {
                    found.add(layout + ": " + b.blueprintId() + " at " + b.origin()
                            + " claims ground the square holds at "
                            + Heart.square(town));
                }
            }
        }
        assertTrue(found.isEmpty(), found.size() + " plots on the reserve:\n"
                + String.join("\n", found));
    }

    /**
     * A square is one place for the whole of a town's life.
     *
     * <p>The wandering itself, asserted apart from its consequence. Up to eight
     * places before; one now. It is stated as "one" rather than "few" because
     * {@code Heart.square} is a pure function of the arrangement and the center —
     * if it ever answers twice, something has gone back to asking the world.
     */
    @Test
    void aSquareIsOnePlaceForTheWholeOfATownsLife() {
        for (String layout : layouts()) {
            Grown grown = grown(layout);
            Set<SimPos> places = new LinkedHashSet<>();
            for (Furnishings.Furnishing square : grown.squares()) {
                places.add(square.at());
            }
            assertTrue(places.size() <= 1,
                    layout + " planned its square in " + places.size()
                            + " different places: " + places);
        }
    }

    /**
     * And it stands where the plan holds ground for it, at the size held.
     *
     * <p>The other half of the fault. A square that is never built on but sits
     * thirty blocks out in a field is not a town square, and that is what the
     * search produced in six of the eight arrangements that raise one.
     */
    @Test
    void aSquareStandsOnItsOwnReserveAtItsFullSize() {
        int raised = 0;
        for (String layout : layouts()) {
            Grown grown = grown(layout);
            if (grown.squares().isEmpty()) {
                continue;   // this people does not dress its middle; see FurnishingStyle
            }
            raised++;
            Furnishings.Furnishing square = grown.squares().get(0);
            assertEquals(Heart.squareGround(grown.town()), square.at(),
                    layout + " laid its square off the ground held for it");
            // Big enough to be a square, which is the rule theSquare states: a
            // town that cannot seat nine gets none, because a five-block square
            // is a gap between two houses. Not pinned at the full thirteen — a
            // hall standing exactly on the reserve's edge shaves a course off,
            // and that is argued at Heart.SQUARE_HELD.
            assertTrue(square.reach() >= Furnishings.SMALLEST_SQUARE,
                    layout + " laid a square of " + (2 * square.reach() + 1)
                            + " blocks, which is a gap between two houses");
        }
        assertTrue(raised >= SQUARE_RAISING_ARRANGEMENTS,
                "only " + raised + " arrangements raised a square, so this suite is"
                        + " no longer measuring what it was written for");
    }

    /**
     * How many of the arrangements dress their middle with a paved square.
     *
     * <p>A floor, not a count. The stronghold, the orc ring, the warren and the
     * goblin camp raise no square at all — that is their {@code FurnishingStyle}
     * and not a fault — and this is here so that a style change which quietly
     * switched the rest of them off could not turn the two rules above green by
     * leaving them nothing to measure.
     */
    private static final int SQUARE_RAISING_ARRANGEMENTS = 8;
}
