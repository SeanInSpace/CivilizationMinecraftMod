package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.ForesterStand;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.LumberPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.Stand;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town in bare country goes and finds the wood.
 *
 * <p>The bug these fixtures were written for is the one that was measured and
 * then withdrawn. A lumber camp was ordered onto the next ring slot — the urgent
 * path has never judged the ground for anything but water — and a camp on a
 * meadow reads as felled out on the morning it opens. Treat that as a spent
 * producer, the way a cut-out mine is treated, and the next shortage orders
 * another camp onto the next meadow: a high-street town of eight buildings grown
 * four hundred steps came out with <strong>fourteen lumber camps and not one
 * extra log</strong>.
 *
 * <p>The decision taken instead is that a town <em>prospects</em>. The camp goes
 * where the trees are, out past the claim if it has to; a camp with saplings in
 * the ground is a camp waiting rather than a camp finished; and there is a cap,
 * because the honest end of prospecting is a town that does without.
 *
 * <p><strong>The ground and the wood are separate fakes on purpose.</strong> The
 * terrain is {@link RecordedTerrain} — real jagged ground out of the world every
 * survey in this project uses — and the wood on it is a map written here, because
 * a rule about choosing between two woods cannot be measured on a fake with no
 * wood in it. {@code WorldBridge.woodedness} answers nought with no world behind
 * it, which is why a watched town and an unwatched one read the same books: both
 * ask the bridge, and the bridge is the only thing that knows.
 */
class ProspectTest {

    private static final SimPos CENTER = new SimPos(0, 72, 0);

    /**
     * The ground of seed 8675309 with a wood written onto it.
     *
     * <p>Two answers and they are kept consistent, which the plain
     * {@link RecordedTerrain} is not: it reports a density for
     * {@code woodedness} and nought trees for {@code countTreesNear}, so a camp
     * on its "wooded" ground has nothing to fell. That incoherence is exactly
     * why the fourteen-camp report could count sheds and not logs.
     *
     * <p>The two are tied by {@link Stand#UNSURVEYED}'s own arithmetic: a claim
     * of radius {@code r} holds about {@code pi r^2} squares, a stand at
     * {@link ForesterStand#SPACING} stands one trunk to every twenty-five of
     * them, and ground that is {@code w} percent wooded holds {@code w} percent
     * of that. At a hundred percent and the default claim that is seventy-two
     * trees, which is {@link Stand#UNSURVEYED} exactly.
     */
    private static final class Woods implements WorldBridge {

        private final RecordedTerrain ground;
        private final ToIntFunction<SimPos> wood;

        Woods(RecordedTerrain ground, ToIntFunction<SimPos> wood) {
            this.ground = ground;
            this.wood = wood;
        }

        @Override public int surfaceHeight(SimPos pos) {
            return ground.surfaceHeight(pos);
        }

        @Override public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) {
            return false;   // unwatched, which is how towns actually grow
        }

        @Override public boolean standsInWater(SimPos pos, int radius) {
            return ground.standsInWater(pos, radius);
        }

        @Override public boolean isSiteSuitable(SimPos plot, int radius) {
            return ground.isSiteSuitable(plot, radius);
        }

        @Override public int siteFault(SimPos plot, int radius) {
            return ground.siteFault(plot, radius);
        }

        @Override public boolean isSiteLevelable(SimPos plot, int radius) {
            return ground.isSiteLevelable(plot, radius);
        }

        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return ground.materializeBlueprint(id, origin, surveyed, facing);
        }

        @Override public int woodedness(SimPos center, int radius) {
            return wood.applyAsInt(center);
        }

        @Override public int countTreesNear(SimPos center, int radius) {
            int squares = (int) (Math.PI * radius * radius)
                    / (ForesterStand.SPACING * ForesterStand.SPACING);
            return wood.applyAsInt(center) * squares / 100;
        }

        @Override public void log(String message) {
        }
    }

    /**
     * A bare ring the town can see across: 110 blocks.
     *
     * <p>Every plot the high-street plan offers is inside it, so the ordinary
     * siting has nothing but meadow to choose between, and the wood beyond is
     * inside {@link BuildPlanner#PROSPECT_REACH} of a young town's claim. This
     * is the ground the decision was taken for: a town that can reach the trees
     * if it goes and looks.
     */
    private static final int BARE_COUNTRY = 110;

    /**
     * A bare ring the town cannot: 400 blocks.
     *
     * <p>The same country with the wood pushed past anything a town may walk to,
     * however far its claim grows. There is no good answer here and the decision
     * says so — one camp, and the town does without. This is the ground the
     * withdrawn fix answered with fourteen sheds.
     */
    private static final int BARE_FOREVER = 400;

    /** How wooded the country beyond the bare ring is. */
    private static final int THE_WOOD = 60;

    private static RecordedTerrain recorded;

    private static synchronized RecordedTerrain recorded() {
        if (recorded == null) {
            recorded = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        }
        return recorded;
    }

    /** The fixture's country: bare where the town stands, woodland beyond. */
    private static Woods bareFor(int radius) {
        return new Woods(recorded(),
                at -> at.horizontalDistance(CENTER) < radius ? 0 : THE_WOOD);
    }

    /** A founding party on the recorded ground, with nothing in the stores. */
    private static Settlement camp(String layoutId) {
        Settlement town = new Settlement(Settlement.Id.random(), "Millbrook",
                new SimPos(CENTER.x(), recorded().surfaceHeight(CENTER), CENTER.z()),
                Founding.INITIAL_CLAIM);
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
                    Person.Id.random(), name, Profession.PIONEER, town.center()));
        }
        return town;
    }

    private static Settlement grow(String layoutId, WorldBridge ground, int steps) {
        Settlement town = camp(layoutId);
        for (int step = 1; step <= steps; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
    }

    private static int camps(Settlement town) {
        return town.buildingsWithRole(BuildingRole.LUMBER_CAMP).size();
    }

    /** Everything the camps have brought in: the town's stock plus what they hold. */
    private static int logs(Settlement town) {
        int held = town.woodStock();
        for (Building building : town.buildings()) {
            if (building.hasStores()) {
                held += building.stores().get(TownStores.WOOD);
            }
        }
        return held;
    }

    // --- the fourteen-camp fixture ---

    /**
     * The measurement the whole decision was made against, pinned.
     *
     * <p>A high-street town grown four hundred steps on the recorded ground of
     * seed 8675309, in country bare for {@link #BARE_COUNTRY} blocks around it
     * and wooded beyond. Measured on this ground:
     *
     * <ul>
     *   <li><strong>the withdrawn fix</strong> — a bare camp read as a spent one
     *       — <strong>14 lumber camps</strong> in a town of eight buildings, and
     *       <strong>8 logs</strong>, which is the founding stock and nothing
     *       felled at all;</li>
     *   <li><strong>before</strong>, on the rule as shipped: 8 buildings, 1
     *       camp, standing on a meadow, and the same <strong>8 logs</strong> —
     *       the town in bare country that never gets its timber back;</li>
     *   <li><strong>after</strong>: 11 buildings, <strong>1 camp</strong>,
     *       prospected out into the wood, and <strong>1,618 logs</strong>.</li>
     * </ul>
     *
     * <p>Three numbers are asserted and each is a different failure. The count,
     * because a row of sheds is what the withdrawn fix built; the timber,
     * because a town that keeps one camp and still has no wood has only failed
     * more tidily; and the camp's own ground, because one camp is the right
     * answer only if it is somewhere worth standing.
     */
    @Test
    void ahighStreetTownInBareCountryProspectsAndGetsItsTimber() {
        Woods ground = bareFor(BARE_COUNTRY);
        Settlement town = grow(Culture.LAYOUT_HIGH_STREET, ground, 400);

        int sheds = camps(town);
        int timber = logs(town);
        System.out.println("BARE FOR " + BARE_COUNTRY + ", high street, 400 steps: "
                + town.buildings().size() + " buildings, pop " + town.population()
                + ", " + sheds + " lumber camps, " + timber + " logs");

        assertEquals(1, sheds,
                "the town built " + sheds + " lumber camps where the withdrawn fix"
                        + " built fourteen and the shipped rule built one");
        // Eight hundred rather than a thousand since the public works stopped
        // starving each other (see PublicWorks.keepingUp). The town now actually
        // raises the street lamps it had always planned and never built, and a
        // lamp costs two timber apiece — so a few hundred logs that used to sit
        // on the shelves for ever are standing in the street instead. What this
        // assertion is for is unchanged and is nowhere near the line: before the
        // prospecting fix the same town finished with the eight logs it was
        // founded with and never felled a trunk at all.
        assertTrue(timber > 800,
                "the camp came in with " + timber + " logs; before this change the"
                        + " same town had the 8 it was founded with and never felled"
                        + " a trunk");
        for (Building shed : town.buildingsWithRole(BuildingRole.LUMBER_CAMP)) {
            int wooded = ground.woodedness(shed.origin(), BuildPlanner.PLOT_PROBE_RADIUS);
            assertTrue(wooded >= BuildPlanner.WOODED_ENOUGH,
                    "the camp at " + shed.origin() + " is standing on ground "
                            + wooded + " percent wooded, under the floor of "
                            + BuildPlanner.WOODED_ENOUGH);
        }
    }

    /**
     * And with the wood past what a town may walk to, it keeps its one camp and
     * does without.
     *
     * <p>The honest end of prospecting, and the outcome the decision explicitly
     * accepts. The same ground with the wood at {@link #BARE_FOREVER} rather
     * than {@link #BARE_COUNTRY}: the withdrawn fix answered this with fourteen
     * sheds, and the answer here is one shed and a line in the report.
     */
    @Test
    void andWithTheWoodOutOfReachItKeepsOneCampAndDoesWithout() {
        Woods ground = bareFor(BARE_FOREVER);
        Settlement town = grow(Culture.LAYOUT_HIGH_STREET, ground, 400);

        System.out.println("BARE FOR " + BARE_FOREVER + ", high street, 400 steps: "
                + town.buildings().size() + " buildings, pop " + town.population()
                + ", " + camps(town) + " lumber camps, " + logs(town) + " logs");

        assertEquals(1, camps(town),
                "with no wood in reach another shed does not make trees");
        for (Building shed : town.buildingsWithRole(BuildingRole.LUMBER_CAMP)) {
            assertTrue(shed.origin().horizontalDistance(town.center())
                            <= town.claimRadius() + BuildPlanner.PROSPECT_REACH,
                    "the camp at " + shed.origin() + " was sent past the "
                            + BuildPlanner.PROSPECT_REACH
                            + " blocks of prospecting a town is allowed");
        }
    }

    // --- the siting ---

    private static final BuildingType LUMBER_CAMP =
            new BuildingType("civilization:lumber_camp", 30, 1, 1, 0, 68, 0);

    /** A town with hands to spare and nothing standing, on flat readable ground. */
    private static Settlement plainTown(WorldBridge ground) {
        Settlement town = new Settlement(Settlement.Id.random(), "Testburg",
                new SimPos(0, 64, 0), Founding.INITIAL_CLAIM);
        town.setCatalog(List.of(LUMBER_CAMP));
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            town.addResident(new Person(Person.Id.random(), name,
                    name.equals("Ada") ? Profession.BUILDER : Profession.IDLER,
                    town.center()));
        }
        return town;
    }

    /** Flat, dry, readable ground with whatever wood the caller writes on it. */
    private static WorldBridge flat(ToIntFunction<SimPos> wood) {
        return new WorldBridge() {
            @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
            @Override public boolean isLoaded(SimPos pos) { return true; }
            @Override public int surfaceHeight(SimPos pos) { return 64; }
            @Override public boolean standsInWater(SimPos pos, int radius) { return false; }
            @Override public boolean isSiteSuitable(SimPos plot, int radius) { return true; }
            @Override public int siteFault(SimPos plot, int radius) { return SITE_FAULT_NONE; }
            @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                            boolean surveyed, int facing) {
                return new Footprint(origin.y(), 3, 3, 3);
            }
            @Override public int woodedness(SimPos center, int radius) {
                return wood.applyAsInt(center);
            }
            @Override public void log(String message) { }
        };
    }

    private static SimPos orderedCampAt(Settlement town, WorldBridge ground) {
        assertTrue(BuildPlanner.requestProducer(town, TownStores.WOOD, 5, ground),
                "the town had hands and no camp; it should have ordered one");
        return town.buildQueue().getFirst().origin();
    }

    @Test
    void thecampGoesToTheMostWoodedSlotRatherThanTheNearest() {
        // The whole siting change in one measurement. Every ring slot is
        // buildable, so the old rule takes the first one; the wood is to the
        // east, so the new rule walks past the first few.
        WorldBridge ground = flat(at -> at.x() > 20 ? 80 : 5);
        Settlement town = plainTown(ground);

        SimPos plot = orderedCampAt(town, ground);

        assertEquals(80, ground.woodedness(plot, BuildPlanner.PLOT_PROBE_RADIUS),
                "the camp at " + plot + " took a slot with five percent cover while"
                        + " a slot in the wood was on the same list");
    }

    @Test
    void abareSlotIsRefusedWhenAWoodStandsFurtherOut() {
        // The floor. Every plot the plan offers is dead bare; the only trees are
        // outside the claim, so the camp has to leave the ring altogether.
        WorldBridge ground = flat(
                at -> at.horizontalDistance(new SimPos(0, 64, 0)) > Founding.INITIAL_CLAIM
                        ? 70 : BuildPlanner.WOODED_ENOUGH - 1);
        Settlement town = plainTown(ground);

        SimPos plot = orderedCampAt(town, ground);

        assertTrue(ground.woodedness(plot, BuildPlanner.PLOT_PROBE_RADIUS)
                        >= BuildPlanner.WOODED_ENOUGH,
                "the camp took bare ground at " + plot + " with a wood in reach");
        assertFalse(plot.horizontalDistance(town.center()) <= Founding.INITIAL_CLAIM
                        && ground.woodedness(plot, BuildPlanner.PLOT_PROBE_RADIUS)
                                < BuildPlanner.WOODED_ENOUGH,
                "a slot under the floor was taken anyway");
    }

    @Test
    void theProspectingReachIsHonouredAndBounded() {
        // Both halves, because a reach with no bound is a town founding a second
        // village and a bound with no reach is the bug.
        int claim = Founding.INITIAL_CLAIM;

        // In reach: a wood just inside the far edge is found and taken.
        WorldBridge near = flat(at -> at.horizontalDistance(new SimPos(0, 64, 0))
                > claim + BuildPlanner.PROSPECT_REACH - BuildPlanner.PROSPECT_REACH / 2
                ? 70 : 0);
        Settlement reaching = plainTown(near);
        SimPos found = orderedCampAt(reaching, near);
        assertTrue(near.woodedness(found, BuildPlanner.PLOT_PROBE_RADIUS) > 0,
                "a wood inside the reach at " + found + " was not walked to");

        // Out of reach: a wood past the bound is not, and the town falls back to
        // the ordinary urgent walk rather than sending a camp to the horizon.
        WorldBridge far = flat(at -> at.horizontalDistance(new SimPos(0, 64, 0))
                > claim + BuildPlanner.PROSPECT_REACH * 4 ? 70 : 0);
        Settlement stuck = plainTown(far);
        SimPos settled = orderedCampAt(stuck, far);
        assertTrue(settled.horizontalDistance(stuck.center())
                        <= claim + BuildPlanner.PROSPECT_REACH,
                "the camp was sent " + Math.round(settled.horizontalDistance(stuck.center()))
                        + " blocks out, past the " + BuildPlanner.PROSPECT_REACH
                        + " blocks of prospecting a town is allowed");
    }

    @Test
    void aprospectedCampIsInsideTheClaimItStandsOn() {
        // A building outside the town's own territory is a building nothing
        // watches, nothing roads, and no woodland belt is staked around.
        WorldBridge ground = flat(
                at -> at.horizontalDistance(new SimPos(0, 64, 0)) > Founding.INITIAL_CLAIM
                        ? 70 : 0);
        Settlement town = plainTown(ground);

        SimPos plot = orderedCampAt(town, ground);

        assertTrue(town.contains(plot),
                "the camp at " + plot + " stands outside a claim of "
                        + town.claimRadius());
    }

    // --- a camp waiting for seed is not a camp finished ---

    @Test
    void acampWithSaplingsComingUpBlocksASecondCamp() {
        // The heart of it. A stand grows back and a seam does not, so a bare
        // camp with something in the ground is a camp waiting — and another shed
        // does not make a sapling mature any sooner.
        WorldBridge ground = flat(at -> 70);
        Settlement town = plainTown(ground);
        Building shed = new Building("civilization:lumber_camp", new SimPos(4, 64, 4), 1, true);
        Stand.recount(shed, 0, 0);
        Stand.plant(shed);
        town.addBuilding(shed);

        assertTrue(Stand.isBare(shed) || Stand.growing(shed) > 0);
        assertFalse(BuildPlanner.requestProducer(town, TownStores.WOOD,
                        Stand.growingSteps(SimSettings.SANDBOX) * 10L, ground,
                        SimSettings.SANDBOX),
                "a camp with saplings in the ground is a camp waiting, not a camp finished");
        assertTrue(town.buildQueue().isEmpty());
    }

    /** A town with enough people to keep two camps, so the cap is not what answers. */
    private static Settlement bigTown(WorldBridge ground) {
        Settlement town = plainTown(ground);
        while (town.population() < 2 * BuildPlanner.RESIDENTS_PER_LUMBER_CAMP) {
            town.addResident(new Person(Person.Id.random(),
                    "Hand" + town.population(), Profession.FARMER, town.center()));
        }
        return town;
    }

    @Test
    void andSoDoesACampCountedBareThisMorning() {
        // The other half of waiting: no saplings yet, but no time has passed
        // either. The jacks have not had a season to put seed down in.
        WorldBridge ground = flat(at -> 70);
        Settlement town = bigTown(ground);
        Building shed = new Building("civilization:lumber_camp", new SimPos(4, 64, 4), 1, true);
        Stand.recount(shed, 0, 100);
        town.addBuilding(shed);

        assertFalse(BuildPlanner.requestProducer(town, TownStores.WOOD, 101, ground,
                        SimSettings.SANDBOX),
                "counted bare one step ago is a lean season, not dead ground");

        // And past the growing window, with nothing ever coming up, it is dead
        // ground after all — which is the one case that orders another camp.
        assertTrue(BuildPlanner.requestProducer(town, TownStores.WOOD,
                        100 + Stand.growingSteps(SimSettings.SANDBOX), ground,
                        SimSettings.SANDBOX),
                "a camp bare for a whole growing season with nothing planted is"
                        + " standing on ground that grows nothing");
    }

    // --- the cap ---

    @Test
    void asmallTownKeepsOneCampAndLivesWithTheShortage() {
        // The bound the whole decision rests on, at its tightest. Four people
        // keep one camp, so a camp of theirs on dead ground is not replaced —
        // that is the "town in bare country" outcome, stated out loud rather
        // than turned into a row of sheds.
        WorldBridge ground = flat(at -> 70);
        Settlement town = plainTown(ground);
        long late = 100 + Stand.growingSteps(SimSettings.SANDBOX);
        Building shed = new Building("civilization:lumber_camp", new SimPos(4, 64, 4), 1, true);
        Stand.recount(shed, 0, 100);
        town.addBuilding(shed);

        assertEquals(1, BuildPlanner.lumberCampsAllowed(town),
                "four residents keep one camp");
        assertFalse(BuildPlanner.requestProducer(town, TownStores.WOOD, late, ground,
                        SimSettings.SANDBOX),
                "a second camp on four residents is a town building sheds");
        assertTrue(town.buildQueue().isEmpty());
        assertTrue(town.events().stream()
                        .anyMatch(e -> e.message().contains("the town does without")),
                "and a town that has given up on its timber says so");
        assertTrue(BuildPlanner.livesWithATimberShortage(town, late, SimSettings.SANDBOX),
                "which is what /civ info prints");
    }

    @Test
    void abiggerTownProspectsOnceMoreAndThenStops() {
        // Forty people keep two camps, so the first replacement is the town
        // going to look for new woodland — which is the whole point — and the
        // second is the town discovering the country is bare.
        WorldBridge ground = flat(at -> 70);
        Settlement town = bigTown(ground);
        long late = 100 + Stand.growingSteps(SimSettings.SANDBOX);
        assertEquals(2, BuildPlanner.lumberCampsAllowed(town));

        Building shed = new Building("civilization:lumber_camp", new SimPos(4, 64, 4), 1, true);
        Stand.recount(shed, 0, 100);
        town.addBuilding(shed);
        assertTrue(BuildPlanner.requestProducer(town, TownStores.WOOD, late, ground,
                SimSettings.SANDBOX), "the first replacement is the town prospecting");

        // The same town again with both camps already standing and both worked
        // out — a fresh one, because the order above is on the first one's queue
        // and a queued camp refuses the next ask on its own account.
        Settlement after = bigTown(ground);
        for (SimPos where : new SimPos[] {new SimPos(4, 64, 4), new SimPos(40, 64, 0)}) {
            Building worked = new Building("civilization:lumber_camp", where, 1, true);
            Stand.recount(worked, 0, 100);
            after.addBuilding(worked);
        }

        assertFalse(BuildPlanner.requestProducer(after, TownStores.WOOD, late, ground,
                        SimSettings.SANDBOX),
                "past the cap another shed does not make trees");
        assertTrue(BuildPlanner.livesWithATimberShortage(after, late, SimSettings.SANDBOX));
    }

    // --- the claim follows the camp ---

    @Test
    void theWoodlandClaimFollowsTheCampThatHasAWoodInIt() {
        // A town keeps one woodland claim. Staked on the first camp and never
        // moved, a camp prospected into the trees would count, fell and replant
        // on the bare ground it was raised to escape.
        WorldBridge ground = flat(at -> at.x() > 200 ? 90 : 0);
        Settlement town = plainTown(ground);
        Building bare = new Building("civilization:lumber_camp", new SimPos(4, 64, 4), 1, true);
        Stand.recount(bare, 0, 100);
        town.addBuilding(bare);
        long late = 100 + Stand.growingSteps(SimSettings.SANDBOX);
        town.step(new SimContext(ground, late, SimSettings.SANDBOX));
        assertEquals(bare.origin(), town.lumberArea().center(),
                "the only camp there is holds the claim");

        Building wooded = new Building("civilization:lumber_camp", new SimPos(240, 64, 0), 1, true);
        Stand.recount(wooded, 30, late);
        town.addBuilding(wooded);
        town.step(new SimContext(ground, late + 1, SimSettings.SANDBOX));

        assertEquals(wooded.origin(), town.lumberArea().center(),
                "the claim stayed on the bare camp while the trees were elsewhere");
        assertTrue(town.lumberArea().radius() >= LumberPlanner.DEFAULT_RADIUS);
    }
}
