package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Layout;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.culture.TownPlan;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Heart;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.PathPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A street is opened when it is needed, and the hall takes the middle.
 *
 * <p>The report this class exists for is a picture: fifteen buildings sitting
 * inside a complete three-hundred-block ring road, with empty grass inside it
 * and out. Both halves of that picture were built on purpose and neither was
 * right.
 *
 * <p>The road, because a town's whole plan of streets was owed on day one. A
 * seeded village had every stretch the plan drew walked out before its first
 * step — "the streets of Millbrook run 85 ways deep" — and a founded town
 * opened them by the clock in index order whether or not anything stood beside
 * them. What a town has actually built is now what it needs: see
 * {@code StreetDemand}.
 *
 * <p>The empty middle, because the camp post took plot zero on step one and was
 * also what every lane in the town radiated from, so the ground the arrangement
 * was drawn around held a signpost and a crossroads and the hall went up a
 * hundred blocks out. See {@code Heart}.
 *
 * <p>The ground is the recorded hillside rather than the sine waves, for the
 * reason {@link RealTerrainRoadsTest} gives at length: a road rule measured on
 * three sine waves is a road rule nothing can refuse.
 */
class OpenStreetsTest {

    /** Where every survey in this project puts its town. */
    private static final SimPos CENTER = new SimPos(16, 64, 80);

    /** Long enough for a town to grow past sixty buildings on this ground. */
    private static final int STEPS = 700;

    /** The sizes a town is looked at, which is where the difference shows. */
    private static final int[] MARKS = {15, 30, 60};

    /**
     * How much of its <em>carriageway</em> a young town may have opened.
     *
     * <p>The old rule opened every walkable stretch in the network — one a step
     * for a founded town, the whole lot at once for a seeded one — so what it
     * opened is exactly {@code planned - unwalkable}, which is what this is
     * measured against. A town of fifteen buildings standing inside its own
     * finished ring road is what a hundred per cent looks like.
     *
     * <p>Carriageway rather than whole network, and the distinction is not a
     * dodge. Most of a young town's network is <em>lanes</em>, and a lane exists
     * only because a door needed reaching — it is owed by construction and always
     * was, so counting tracks in would measure mostly the thing this change does
     * not touch.
     *
     * <p>Measured at fifteen buildings across the nine street arrangements, as
     * carriageway opened of carriageway planned: thorp and radial_concentric 55%,
     * bastide 58%, ring_streets 64%, crescents 67%, stronghold_streets 73%,
     * orc_ring 79%, green 84%, high_street and crossroads 86%. All of them were a
     * hundred before. The two at the top are not a failure of the rule but the
     * rule working: a high street and a crossroads are two enormous spines
     * through the middle of town, so nearly every stretch of them is on
     * somebody's way to the square, which is the second of the three reasons a
     * street is owed.
     */
    private static final double YOUNG_CEILING = 0.88;

    /**
     * How many standing buildings may be off the network at once, and why not none.
     *
     * <p>{@code PathPlanner.advance} joins one door a step, so a town that built
     * something this step has one building whose lane is planned next step. A
     * door with no clear line to any road waits longer than that — the network
     * spreads toward it — which measured as two, once, on the thorp at thirty
     * buildings. A fraction is the wrong measure at these sizes: one unjoined
     * building of fifteen is seven per cent and is simply the one in hand.
     */
    private static final int ADRIFT_AT_A_TIME = 2;

    /**
     * How many standing buildings may be off the network, as a fraction.
     *
     * <p>The ceiling {@code /civ info} reports against, and it is a figure about
     * a world rather than about one town: held against every grown town in this
     * fixture together, which is fourteen arrangements and about six hundred
     * buildings. Per town it is the wrong measure at these sizes — one unjoined
     * building of forty-two is two and a half per cent and is one thorp farm
     * whose lane has nowhere clear to run.
     *
     * <p>Measured after: <strong>1 of 600</strong>, which is under a fifth of
     * this. Opening fewer streets is only an improvement if the buildings that
     * are there still have roads.
     */
    private static final double UNJOINED_CEILING = 0.007;

    /**
     * How far from the square a hall may stand, and why it is a distance.
     *
     * <p>"On plot zero" is the claim for the arrangements that draw frontage near
     * their own middle, and it is asserted by counting rather than by refusing,
     * because three of them deliberately do not: {@code ring_streets},
     * {@code crossroads} and {@code bastide} draw a circus, a crossing and a
     * market place, all of them open ground, and their nearest offer is thirty to
     * fifty blocks out. Plot zero is still the nearest ground those arrangements
     * have — the plan's offers are taken nearest-first — so what holds across all
     * fourteen is a distance, and what is reported is how many took plot zero
     * exactly.
     *
     * <p>Seventy. Measured on this ground, the furthest any hall now stands from
     * its square is sixty-three blocks — the ring streets, whose circus is
     * forty-six blocks across before any frontage is offered at all and whose
     * plot zero this seed's ground refuses. Before, the same three towns put
     * their halls 106, 108 and 139 blocks out.
     */
    private static final double NEAR_THE_MIDDLE = 70;

    private static RecordedTerrain ground;

    private static synchronized RecordedTerrain ground() {
        if (ground == null) {
            ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        }
        return ground;
    }

    /**
     * What a town looked like at one of the sizes above.
     *
     * <p>Carriageways counted apart from the whole network, because they are the
     * thing the picture was about. A lane is a <em>consequence</em> of a door —
     * it exists because somebody had to reach one — so a town's tracks are owed
     * by construction and always were. A street is drawn from a plan of two
     * hundred and fifty-six, and a plan is what a village of fifteen has no
     * business having paved.
     */
    private record Look(int buildings, int planned, int opened, int unwalkable,
                        int unjoined, int streets, int streetsOpened,
                        int streetsUnwalkable) {

        int walkable() {
            return planned - unwalkable;
        }

        int walkableStreets() {
            return streets - streetsUnwalkable;
        }

        @Override
        public String toString() {
            return buildings + " buildings: " + opened + " of " + walkable()
                    + " walkable stretches opened, of which " + streetsOpened
                    + " of " + walkableStreets() + " carriageway (" + planned
                    + " planned, " + unjoined + " unjoined)";
        }
    }

    private static Settlement camp(String layoutId) {
        Settlement town = new Settlement(Settlement.Id.random(), "Millbrook", CENTER, 512);
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
        return town;
    }

    /** A town grown the whole way, and what it looked like on the way up. */
    private record Grown(Settlement town, List<Look> looks) {
    }

    /**
     * Grown once per arrangement and shared.
     *
     * <p>Six assertions about one town, and growing it costs nine hundred steps
     * on recorded ground. Grown per test it is fifteen minutes; grown once it is
     * one. The town is never mutated by an assertion, so sharing it is sharing a
     * measurement rather than state.
     */
    private static final Map<String, Grown> GROWN = new LinkedHashMap<>();

    private static synchronized Grown grown(String layoutId) {
        Grown known = GROWN.get(layoutId);
        if (known != null) {
            return known;
        }
        Settlement town = camp(layoutId);
        List<Look> looks = new ArrayList<>();
        int next = 0;
        for (int step = 1; step <= STEPS; step++) {
            town.stores().add(TownStores.WOOD, 8);
            town.stores().add(TownStores.STONE, 6);
            town.step(new SimContext(ground(), step, SimSettings.SANDBOX));
            if (next < MARKS.length && standing(town).size() >= MARKS[next]) {
                looks.add(look(town));
                next++;
            }
        }
        while (looks.size() < MARKS.length) {
            looks.add(look(town));   // this ground would not carry a bigger town
        }
        Grown made = new Grown(town, looks);
        GROWN.put(layoutId, made);
        return made;
    }

    private static List<Look> grow(String layoutId) {
        return grown(layoutId).looks();
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

    private static Look look(Settlement town) {
        PathNetwork paths = town.paths();
        int opened = 0;
        int unwalkable = 0;
        int streets = 0;
        int streetsOpened = 0;
        int streetsUnwalkable = 0;
        for (int i = 0; i < paths.segments().size(); i++) {
            boolean carriageway =
                    paths.segments().get(i).width() > PathNetwork.TRACK_WIDTH;
            if (carriageway) {
                streets++;
            }
            if (paths.isOpened(i)) {
                opened++;
                if (carriageway) {
                    streetsOpened++;
                }
            } else if (paths.isUnwalkable(i)) {
                unwalkable++;
                if (carriageway) {
                    streetsUnwalkable++;
                }
            }
        }
        int unjoined = 0;
        for (Building b : standing(town)) {
            if (!paths.hasJoined(b.origin())) {
                unjoined++;
            }
        }
        return new Look(standing(town).size(), paths.segments().size(),
                opened, unwalkable, unjoined, streets, streetsOpened,
                streetsUnwalkable);
    }

    // --- the streets ---

    @Test
    void ayoungTownHasNotPavedThePlanOfAnOldOne() {
        // The picture, measured. At fifteen buildings a town should be standing
        // on the roads it uses, not inside the ones a plan of two hundred and
        // fifty-six would want.
        StringBuilder report = new StringBuilder();
        List<String> tooMuch = new ArrayList<>();
        for (Layout layout : Layouts.all()) {
            List<Look> looks = grow(layout.id());
            Look young = looks.get(0);
            report.append("\n  ").append(layout.id()).append(" -> ").append(young);
            if (!Layouts.isStreetsFirst(layout) || young.walkableStreets() < 8) {
                // An arrangement with no streets has nothing to hold back. A
                // warren is knots of huts and a goblin camp a huddle round a
                // fire; every way in either is a track somebody wore, so a
                // hundred per cent is the right answer and not a failure.
                continue;
            }
            double share = young.streetsOpened() / (double) young.walkableStreets();
            if (share > YOUNG_CEILING) {
                tooMuch.add(layout.id() + " opened " + Math.round(share * 100)
                        + "% of its carriageway at " + young.buildings() + " buildings");
            }
        }
        System.out.println("A TOWN OF FIFTEEN:" + report);
        assertTrue(tooMuch.isEmpty(), tooMuch + report.toString());
    }

    @Test
    void andGoesOnOpeningThemAsItGrows() {
        // The other half, and the one this change could easily have broken: a
        // town that opens fewer streets early must still open them later. A
        // rule that only ever subtracts is a town whose roads stop.
        List<String> shrank = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        for (Layout layout : Layouts.all()) {
            List<Look> looks = grow(layout.id());
            report.append("\n  ").append(layout.id()).append(" -> ").append(looks);
            for (int i = 1; i < looks.size(); i++) {
                if (looks.get(i).opened() < looks.get(i - 1).opened()) {
                    shrank.add(layout.id() + " went from " + looks.get(i - 1).opened()
                            + " opened to " + looks.get(i).opened());
                }
            }
            Look first = looks.get(0);
            Look last = looks.get(looks.size() - 1);
            if (last.buildings() == first.buildings()) {
                // This arrangement never got past its first mark, so there is no
                // growth here to judge. The warren and the goblin camp both stop
                // at thirteen buildings on this ground -- the warren's inability
                // to carry a town is its own open item in GOALS -- and a road
                // rule must not be asserted against a town that is not growing.
                continue;
            }
            if (last.opened() <= first.opened()) {
                shrank.add(layout.id() + " opened nothing more as it grew: " + looks);
            }
        }
        System.out.println("AS A TOWN GROWS:" + report);
        assertTrue(shrank.isEmpty(), shrank + report.toString());
    }

    @Test
    void andEveryBuildingStandingIsStillOnTheNetwork() {
        // The ceiling /civ info reports against. Opening fewer streets is only
        // an improvement if the buildings that are there still have roads.
        List<String> adrift = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        int buildings = 0;
        int unjoined = 0;
        for (Layout layout : Layouts.all()) {
            List<Look> looks = grow(layout.id());
            for (Look look : looks) {
                if (look.buildings() > 0 && look.unjoined() > ADRIFT_AT_A_TIME) {
                    adrift.add(layout.id() + " at " + look);
                }
            }
            Look grown = looks.get(looks.size() - 1);
            buildings += grown.buildings();
            unjoined += grown.unjoined();
            report.append("\n  ").append(layout.id()).append(" -> ")
                    .append(grown.unjoined()).append(" of ").append(grown.buildings())
                    .append(" unjoined");
        }
        double share = buildings == 0 ? 0 : unjoined / (double) buildings;
        System.out.println("BUILDINGS OFF THE NETWORK: " + unjoined + " of " + buildings
                + report);
        assertTrue(adrift.isEmpty(), adrift.toString());
        assertTrue(share <= UNJOINED_CEILING,
                unjoined + " of " + buildings + " buildings stand off the network"
                        + report);
    }

    @Test
    void acircuitClosesWhenItsFrontageHasFilledUp() {
        // Rule three. A ring road with a gap in it where two houses happen not
        // to stand is worse than either a whole ring or no ring, so once every
        // stretch of a street that the plan drew frontage along has been opened,
        // the stretches between them are opened too.
        List<String> gapped = new ArrayList<>();
        for (Layout layout : Layouts.all()) {
            if (!Layouts.isStreetsFirst(layout)) {
                continue;
            }
            gapped.addAll(gapsInFilledStreets(grown(layout.id()).town()));
        }
        assertTrue(gapped.isEmpty(), gapped.toString());
    }

    /** Mirrors {@code PathPlanner}'s stretch numbering; see its pieceKey. */
    private static final int PIECES_TO_A_STREET = 4096;

    /**
     * Streets whose frontage is entirely open and which still have gaps.
     *
     * <p>Exactly rule three, read off the town from the outside: no street may
     * have every stretch a plan plot fronts opened while another stretch of the
     * same street stays shut for want of anybody wanting it.
     */
    private static List<String> gapsInFilledStreets(Settlement town) {
        PathNetwork paths = town.paths();
        TownPlan plan = town.arrangement().fullPlan(town.center());
        Map<Integer, List<Integer>> byStreet = new LinkedHashMap<>();
        for (int i = 0; i < paths.segments().size(); i++) {
            int key = paths.streetOf(i);
            if (key >= 0) {
                byStreet.computeIfAbsent(key / PIECES_TO_A_STREET,
                        s -> new ArrayList<>()).add(i);
            }
        }
        List<String> gapped = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> street : byStreet.entrySet()) {
            boolean anyFrontage = false;
            boolean allFrontageOpen = true;
            List<Integer> shut = new ArrayList<>();
            for (int index : street.getValue()) {
                boolean open = paths.isOpened(index) || paths.isUnwalkable(index);
                if (!open) {
                    shut.add(index);
                }
                if (!frontsAPlot(plan, street.getKey(), paths.segments().get(index))) {
                    continue;
                }
                anyFrontage = true;
                if (!open) {
                    allFrontageOpen = false;
                }
            }
            if (anyFrontage && allFrontageOpen && !shut.isEmpty()) {
                gapped.add(town.arrangement().id() + " street " + street.getKey()
                        + " has filled up and still has " + shut.size()
                        + " stretches shut");
            }
        }
        return gapped;
    }

    private static boolean frontsAPlot(TownPlan plan, int street,
                                       PathNetwork.Segment run) {
        for (TownPlan.Plot plot : plan.plots()) {
            if (plot.street() != street) {
                continue;
            }
            if (plot.at().horizontalDistance(run.nearestTo(plot.at())) <= 20) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aseededTownOwesOnlyTheStreetsItWouldHaveBuilt() {
        // A town the world wrote into existence pays for what a town that grew
        // would have built by now, and not for the whole plan. Both halves are
        // asserted: everything it owes is walked out on arrival, and there is
        // something left over that it does not.
        List<String> wrong = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        for (Layout layout : Layouts.all()) {
            RecordedTerrain terrain = ground();
            SimPos site = new SimPos(CENTER.x(), terrain.surfaceHeight(CENTER), CENTER.z());
            Settlement town = Founding.seeded(site, "Millbrook", SettlementStage.VILLAGE,
                    BuildCatalog.DEFAULT, Culture.NORMAN.id());
            town.setLayoutId(layout.id());
            for (Person resident : town.residents()) {
                resident.setEmbodied(true);
            }
            town.step(new SimContext(terrain, 1, SimSettings.SANDBOX));

            PathNetwork paths = town.paths();
            int owedAndShut = 0;
            int notOwed = 0;
            int opened = 0;
            for (int i = 0; i < paths.segments().size(); i++) {
                boolean owed = PathPlanner.owesStretch(town, i);
                if (paths.isOpened(i)) {
                    opened++;
                } else if (!paths.isUnwalkable(i)) {
                    if (owed) {
                        owedAndShut++;
                    } else {
                        notOwed++;
                    }
                }
            }
            report.append("\n  ").append(layout.id()).append(" -> ")
                    .append(paths.segments().size()).append(" planned, ")
                    .append(opened).append(" opened, ").append(notOwed)
                    .append(" left for later");
            if (owedAndShut > 0) {
                wrong.add(layout.id() + " arrived owing " + owedAndShut
                        + " stretches nobody had walked");
            }
            if (paths.segments().isEmpty()) {
                // Or the assertion above passes because there is nothing to pass
                // about. A seeded town arrives WITH its roads; see
                // WorldgenTownTest, which is where that rule lives.
                wrong.add(layout.id() + " arrived with no roads at all");
            }
        }
        System.out.println("SEEDED VILLAGES, on arrival:" + report);
        assertTrue(wrong.isEmpty(), wrong + report.toString());
    }

    // --- the middle ---

    @Test
    void theMiddleIsHeldForTheHall() {
        // Reserving the plot INDEX reserves no ground: a hall does not fit
        // between plot zero's neighbors in any arrangement in the mod. So the
        // ground is reserved, and this is the assertion that it stays reserved
        // however long a town grows without being able to afford a hall.
        List<String> built = new ArrayList<>();
        for (Layout layout : Layouts.all()) {
            Settlement town = grown(layout.id()).town();
            if (Heart.hallIsSpokenFor(town)) {
                continue;   // the hall has it, which is what the reserve was for
            }
            SimPos middle = Heart.hallGround(town);
            if (!town.isPlotFree(middle, Heart.SPAN, null)) {
                built.add(layout.id() + " built over the hall's ground at " + middle);
            }
        }
        assertTrue(built.isEmpty(), built.toString());
    }

    @Test
    void andTheCampPostIsAMarkerRatherThanTheFirstPlot() {
        // The easy half of the old fault and the one that made the hard half
        // impossible: a post is a sign, so it stands at the square's edge and
        // takes no plot at all.
        List<String> onAPlot = new ArrayList<>();
        for (Layout layout : Layouts.all()) {
            Settlement town = camp(layout.id());
            for (int step = 1; step <= 40; step++) {
                town.stores().add(TownStores.WOOD, 8);
                town.stores().add(TownStores.STONE, 6);
                town.step(new SimContext(ground(), step, SimSettings.SANDBOX));
            }
            for (Building b : town.buildings()) {
                if (!b.blueprintId().endsWith("camp_post")) {
                    continue;
                }
                SimPos plotZero = layout.plotFor(town.center(), 0);
                if (b.origin().x() == plotZero.x() && b.origin().z() == plotZero.z()) {
                    onAPlot.add(layout.id() + " put its camp post on plot zero");
                }
            }
        }
        assertTrue(onAPlot.isEmpty(), onAPlot.toString());
    }

    @Test
    void andAtownThatRaisesAHallRaisesItOnTheMiddle() {
        // The whole point of the reserve. Measured across every arrangement and
        // reported rather than merely asserted, because "the hall is on plot
        // zero" is a claim about fourteen different geometries and the three
        // that draw no frontage near their own middle -- ring_streets,
        // crossroads and bastide -- mean something different by it: for those
        // the square is the open middle and plot zero is the nearest ground the
        // arrangement has to it.
        List<String> tooFarOut = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        int raised = 0;
        int onPlotZero = 0;
        for (Layout layout : Layouts.all()) {
            Settlement town = grown(layout.id()).town();
            SimPos want = Heart.hallGround(town);
            Building hall = null;
            for (Building b : town.buildings()) {
                if (b.role() == BuildingRole.HALL) {
                    hall = b;
                    break;
                }
            }
            if (hall == null) {
                report.append("\n  ").append(layout.id()).append(" -> no hall yet");
                continue;
            }
            raised++;
            boolean onIt = hall.origin().x() == want.x() && hall.origin().z() == want.z();
            if (onIt) {
                onPlotZero++;
            }
            double out = town.center().horizontalDistance(hall.origin());
            report.append("\n  ").append(layout.id()).append(" -> hall at ")
                    .append(hall.origin()).append(onIt ? " ON plot zero" : " off plot zero")
                    .append(", ").append(Math.round(out)).append(" blocks out");
            if (out > NEAR_THE_MIDDLE) {
                tooFarOut.add(layout.id() + " raised its hall " + Math.round(out)
                        + " blocks from the middle");
            }
        }
        System.out.println("THE HALL, in a grown town: " + onPlotZero + " of " + raised
                + " on plot zero" + report);
        assertTrue(tooFarOut.isEmpty(), tooFarOut + report.toString());
    }
}
