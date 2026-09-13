package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Layout;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Beds;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.GoblinCamp;
import com.civilization.sim.settlement.Homes;
import com.civilization.sim.settlement.JobPlanner;
import com.civilization.sim.settlement.KingPlanner;
import com.civilization.sim.settlement.PopulationPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a goblin camp is, held to it.
 *
 * <p>Five claims, and every one of them is a thing the mod did <em>not</em> do
 * before: the camp is a shape of its own, it houses everybody who lives in it, it
 * never farms, it goes on foraging past the stage where a village stops, and it
 * comes apart when nobody is left in charge of it. Raiding is next door in
 * {@code GoblinRaidTest}, because it is the one part that needs two settlements.
 *
 * <p>Pure simulation throughout. The camp is grown, seeded and starved here
 * without a level, which is the whole reason {@code :common} may not import
 * Minecraft.
 */
class GoblinCampTest {

    private static final SimPos WHERE = new SimPos(0, 72, 0);

    /**
     * A world with ground in it and berries on the ground.
     *
     * <p>{@code forageableNear} is the one thing a camp's whole economy rests on
     * and no other fake in this suite answers it, because until now nothing
     * foraged past its first few stages. Generous rather than tight: what is
     * measured here is whether the camp <em>tries</em>, and a fixture that
     * starved it would measure the fixture.
     */
    private static final class Mire implements WorldBridge {
        @Override public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override public int surfaceHeight(SimPos pos) {
            return WHERE.y();
        }

        @Override public int groundHeight(SimPos pos) {
            return WHERE.y();
        }

        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                                        boolean surveyed, int facing) {
            BuildingSizes.Size size = BuildingSizes.of(blueprintId);
            return size == null
                    ? new Footprint(origin.y(), 3, 3, 3)
                    : new Footprint(origin.y(), size.width(), size.depth(), size.height());
        }

        @Override public int forageableNear(SimPos center, int radius) {
            return 64;
        }

        /** Nobody is watching, so every raid and every build resolves as arithmetic. */
        @Override public boolean playerWithin(SimPos pos, double radius) {
            return false;
        }

        @Override public void log(String message) {
        }
    }

    /** A camp seeded the way world generation seeds one, with no world to read. */
    private static Settlement camp(int goblins) {
        return Founding.seeded(WHERE, "Gritmaw", SettlementStage.TOWN,
                BuildCatalog.DEFAULT, Culture.GOBLIN.id(), goblins,
                Culture.LAYOUT_GOBLIN_CAMP);
    }

    private static SimContext step(long at) {
        return new SimContext(new Mire(), at, SimSettings.SANDBOX);
    }

    // --- the shape ----------------------------------------------------------

    @Test
    void aCampIsItsOwnArrangementAndTheGoblinsBuildInIt() {
        assertSame(Layouts.GOBLIN_CAMP, Layouts.of(Culture.LAYOUT_GOBLIN_CAMP));
        assertTrue(Culture.GOBLIN.layouts().contains(Culture.LAYOUT_GOBLIN_CAMP));
        // And it is genuinely a different town from the warren it replaced at the
        // head of the list, which is the claim a second arrangement has to earn.
        Set<SimPos> warren = plots(Layouts.WARREN, 30);
        Set<SimPos> camp = plots(Layouts.GOBLIN_CAMP, 30);
        int shared = 0;
        for (SimPos plot : camp) {
            if (warren.contains(plot)) {
                shared++;
            }
        }
        assertTrue(shared < 5, "the camp and the warren are the same town: "
                + shared + " of 30 plots identical");
    }

    @Test
    void aSeededCampIsInsideATwentyBlockWalkOfItsFire() {
        // The user's own bar, at the size a world actually seeds: eight goblins,
        // and nothing in the camp more than a twenty-block walk from the middle.
        // Measured on the wider axis, because that is what a walk over ground
        // actually costs.
        assertTrue(reachOf(8) <= 20, "a camp of eight reaches " + reachOf(8)
                + " blocks from its middle, past the twenty-block walk it is meant to be");
        assertTrue(reachOf(6) <= 20, "a camp of six reaches " + reachOf(6) + " blocks");
    }

    @Test
    void aBigCampIsBiggerAndStillHuddles() {
        // Twelve does not fit inside twenty and cannot: twelve plots at an
        // eleven-block separation want about ten's worth of room in a
        // twenty-block circle, whatever arrangement lays them. So the bar for the
        // larger camps is that they stay a huddle -- the warren these people used
        // to build put its first outlying knot at fifty-two, on its own, before any
        // of the huts in it.
        assertTrue(reachOf(12) <= 26, "a camp of twelve reaches " + reachOf(12));
        assertTrue(reachOf(20) <= 30, "a big camp of twenty reaches " + reachOf(20));
        assertTrue(reachOf(20) > reachOf(8), "a big camp is no bigger than a small one");
    }

    /**
     * Every scrap of food in the camp, gone.
     *
     * <p>Three places hold it and {@code FoodPlanner.totalFood} counts all three:
     * the store, the pantry in every home, and what everybody is carrying. A
     * fixture that empties one of them has emptied none of them as far as the
     * foraging ceiling is concerned.
     */
    private static void starve(Settlement camp) {
        camp.setFoodStock(0);
        for (com.civilization.sim.person.Household family : camp.households()) {
            family.setPantry(0);
        }
        for (Person goblin : camp.residents()) {
            goblin.inventory().remove(com.civilization.sim.person.Foods.PROVISION,
                    Integer.MAX_VALUE);
        }
    }

    /** How far the first {@code plots} plots reach from the middle, wider axis. */
    private static int reachOf(int plots) {
        int furthest = 0;
        for (int i = 0; i < plots; i++) {
            SimPos plot = Layouts.GOBLIN_CAMP.plotFor(WHERE, i);
            furthest = Math.max(furthest, Math.max(
                    Math.abs(plot.x() - WHERE.x()), Math.abs(plot.z() - WHERE.z())));
        }
        return furthest;
    }

    @Test
    void theMiddleIsTheCampsAndTheYardAroundItIsOpen() {
        // Plot zero is the trampled middle, and nothing else is offered inside the
        // yard -- which is what makes a camp read as roofs round a space.
        assertEquals(WHERE, Layouts.GOBLIN_CAMP.plotFor(WHERE, 0));
        for (int i = 1; i < 40; i++) {
            SimPos plot = Layouts.GOBLIN_CAMP.plotFor(WHERE, i);
            double out = Math.hypot(plot.x() - WHERE.x(), plot.z() - WHERE.z());
            assertTrue(out >= Layout.MIN_PLOT_SEPARATION,
                    "plot " + i + " stands " + Math.round(out) + " blocks out, in the yard");
        }
    }

    @Test
    void twoCampsAreNotTheSameCampTwice() {
        SimPos elsewhere = new SimPos(4096, 72, -2048);
        int same = 0;
        for (int i = 0; i < 30; i++) {
            SimPos a = Layouts.GOBLIN_CAMP.plotFor(WHERE, i);
            SimPos b = Layouts.GOBLIN_CAMP.plotFor(elsewhere, i);
            if (a.x() - WHERE.x() == b.x() - elsewhere.x()
                    && a.z() - WHERE.z() == b.z() - elsewhere.z()) {
                same++;
            }
        }
        assertTrue(same < 8, "two camps came out " + same + " plots identical of 30");
        assertFalse(Layouts.GOBLIN_CAMP.isSameShapeEverywhere());
    }

    @Test
    void aCampHasNoStreetsAtAllAndSaysSo() {
        // Not an omission -- it is what a camp is, the same statement the warren
        // makes. So the frontage invariants in LayoutTest, which ask only of the
        // arrangements that draw roads, correctly have no opinion about this one.
        assertFalse(Layouts.isStreetsFirst(Layouts.GOBLIN_CAMP));
        assertEquals(0, Layouts.GOBLIN_CAMP.planFor(WHERE, 40).streets().size());
        assertEquals(0, Layouts.GOBLIN_CAMP.planFor(WHERE, 40).frontagePercent());
        assertSame(Layouts.GOBLIN_CAMP, Layouts.streetsFirst(Layouts.GOBLIN_CAMP));
    }

    // --- who lives in it ----------------------------------------------------

    @Test
    void aSeededCampHasABedForEveryGoblinInIt() {
        Settlement camp = camp(8);
        assertEquals(8, camp.population());
        int beds = 0;
        for (Building standing : camp.buildings()) {
            beds += Beds.countIn(standing.blueprintId());
        }
        assertTrue(beds >= camp.population(), "a camp of " + camp.population()
                + " goblins was seeded with " + beds + " bedrolls in it");
        // And the housing table agrees with the bed table, which is what actually
        // decides whether a family may move in.
        assertEquals(beds, PopulationPlanner.totalHousingCapacity(camp));
    }

    @Test
    void aSeededCampLivesInHovelsAndTentsAndNothingElse() {
        Settlement camp = camp(8);
        assertTrue(camp.countBuildings("civilization:hovel") > 0, "no hovel stands");
        assertTrue(camp.countBuildings("civilization:tent") > 0, "no tent stands");
        for (Building standing : camp.buildings()) {
            String base = BuildPlanner.baseIdOf(standing.blueprintId());
            if (Beds.countIn(base) <= 0) {
                continue;
            }
            assertTrue(base.equals("civilization:hovel") || base.equals("civilization:tent")
                            || base.equals("civilization:chieftain_hut"),
                    "goblins are sleeping in a " + base);
        }
    }

    @Test
    void aSeededCampHasExactlyOneLootPileAndItIsTheStore() {
        Settlement camp = camp(8);
        assertEquals(1, camp.countBuildings("civilization:loot_pile"),
                "a camp keeps everything it owns in one heap");
        assertEquals(BuildingRole.STORE, BuildingRole.of("civilization:loot_pile"));
        assertNotNull(camp.buildingWithRole(BuildingRole.STORE),
                "the store machinery cannot see the camp's own store");
        // And no storehouse, warehouse or granary beside it: all three are the
        // same heap for these people.
        assertEquals(0, camp.countBuildings("civilization:storehouse"));
        assertEquals(0, camp.countBuildings("civilization:granary"));
        assertEquals(0, camp.countBuildings("civilization:warehouse"));
    }

    @Test
    void aSeededCampHasAChieftainAndAShamanWithNamesOfItsOwn() {
        Settlement camp = camp(8);
        assertNotNull(camp.buildingWithRole(BuildingRole.HALL),
                "no chieftain hut stands, so nobody can be crowned");
        camp.step(step(1));

        Person chieftain = KingPlanner.king(camp);
        Person shaman = GoblinCamp.shaman(camp);
        assertNotNull(chieftain, "the chieftain hut stands and nobody took it");
        assertNotNull(shaman, "a camp of eight named no shaman");
        assertFalse(chieftain.id().equals(shaman.id()), "one goblin holds both titles");
        assertEquals("chieftain", KingPlanner.titleFor(camp));
        assertEquals(KingPlanner.CAMP_SEAT, KingPlanner.seatFor(camp));

        // Goblin names, not human ones. Checked against the people rather than
        // spelled out, so the pools can be edited without editing this.
        Set<String> goblinGiven = new HashSet<>(Culture.GOBLIN.givenNames());
        assertTrue(goblinGiven.contains(chieftain.name().split(" ")[0]),
                chieftain.name() + " is not a goblin's name");
        for (String human : Culture.NORMAN.familyNames()) {
            assertFalse(chieftain.name().contains(human),
                    chieftain.name() + " carries a human family name");
        }
    }

    @Test
    void aCampNeverCrownsItsShamanIntoTheEmptySeat() {
        // The succession skips anybody already wearing a title, which is what
        // stops a camp coming out of a mourning with a leader and no keeper.
        Settlement camp = camp(8);
        camp.step(step(1));
        Person chieftain = KingPlanner.king(camp);
        camp.removePerson(chieftain.id());
        for (long at = 2; at < KingPlanner.MOURNING_STEPS + 5; at++) {
            camp.step(step(at));
        }
        Person crowned = KingPlanner.king(camp);
        assertNotNull(crowned, "the camp never replaced its chieftain");
        assertNotSameGoblin(crowned, GoblinCamp.shaman(camp));
    }

    private static void assertNotSameGoblin(Person a, Person b) {
        if (a == null || b == null) {
            return;
        }
        assertFalse(a.id().equals(b.id()), "the shaman was crowned and the camp has no keeper");
    }

    // --- the economy --------------------------------------------------------

    @Test
    void noGoblinCampEverOrdersAFarm() {
        Settlement camp = camp(8);
        assertTrue(Homes.refuses(Culture.GOBLIN.id(), BuildPlanner.FARM));
        assertFalse(Homes.buildableBy(Culture.GOBLIN.id(), BuildPlanner.FARM));
        for (long at = 1; at <= 400; at++) {
            camp.step(step(at));
            for (com.civilization.sim.settlement.BuildTask task : camp.buildQueue()) {
                assertFalse(BuildPlanner.baseIdOf(task.blueprintId()).equals(BuildPlanner.FARM),
                        "a camp ordered a field at step " + at);
            }
        }
        assertEquals(0, camp.countBuildings(BuildPlanner.FARM));
        // Nor a mill to grind it in, nor a market to sell it at.
        assertEquals(0, camp.countBuildings("civilization:mill"));
        assertEquals(0, camp.countBuildings("civilization:market"));
    }

    @Test
    void aStarvingCampDoesNotShoveAFieldToTheHeadOfItsQueue() {
        // The famine rescue reaches past the catalog scan straight into the
        // catalog, so it needed the same filter -- left out, a camp starved behind
        // a field it could never build and logged about it every step.
        Settlement camp = camp(8);
        starve(camp);
        for (long at = 1; at <= 40; at++) {
            camp.step(step(at));
            for (com.civilization.sim.settlement.BuildTask task : camp.buildQueue()) {
                assertFalse(FoodPlanner.isSurvivalBuilding(task.blueprintId()),
                        "a starving camp ordered a " + task.blueprintId());
            }
        }
    }

    @Test
    void aCampGoesOnForagingPastTheStageAVillageStopsAt() {
        // The gate was "below VILLAGE", which is right for a party on its way to a
        // field and wrong for a camp that will never have one. A camp seeded at
        // TOWN is four stages past the old gate.
        Settlement camp = camp(8);
        assertSame(SettlementStage.TOWN, camp.stage());
        assertTrue(GoblinCamp.foragesForever(camp));
        assertFalse(GoblinCamp.foragesForever(
                Founding.seeded(WHERE, "Bellbrook", SettlementStage.TOWN,
                        BuildCatalog.DEFAULT, Culture.NORMAN.id(), 8)));

        // Emptied out first, and thoroughly. A seeded camp arrives with rations in
        // every pocket and a larder in every hovel, and foraging stops at a
        // ceiling counted over all of it -- so a camp handed a full pantry picks
        // nothing, correctly, and a test that only zeroed the store would be
        // measuring that.
        starve(camp);
        int picked = 0;
        for (long at = 1; at <= 60; at++) {
            camp.step(step(at));
            picked += camp.foragedRecently();
        }
        // Asked of what the camp actually picked rather than of what is in the
        // larder. The larder is picking minus eating, and eight goblins over
        // thirty steps eat more than a mire hands over -- so a comparison of
        // before and after measures the appetite and calls it a failure to forage.
        assertTrue(picked > 0,
                "a camp at TOWN picked nothing at all from a mire full of berries");
    }

    @Test
    void aCampFieldsForagersWhereAVillageFieldsFarmers() {
        Settlement camp = camp(8);
        assertSame(Profession.FORAGER, JobPlanner.foodTrade(camp));
        for (long at = 1; at <= 60; at++) {
            camp.step(step(at));
        }
        assertTrue(JobPlanner.count(camp, Profession.FORAGER) > 0,
                "a camp of eight put nobody in the bushes");
        assertEquals(0, JobPlanner.count(camp, Profession.FARMER),
                "a camp with no field named a farmer");
    }

    @Test
    void theShamanIsWorthAnArmfulOfForageAndTheCampNoticesWhenHeGoes() {
        Settlement camp = camp(8);
        camp.step(step(1));
        assertTrue(GoblinCamp.hasShaman(camp));
        assertEquals(GoblinCamp.FORAGE_CEILING_PER_MOUTH,
                GoblinCamp.forageCeilingPerMouth(camp));
        assertEquals(FoodPlanner.FORAGE_CEILING_PER_MOUTH,
                GoblinCamp.forageCeilingPerMouth(
                        Founding.seeded(WHERE, "Bellbrook", SettlementStage.TOWN,
                                BuildCatalog.DEFAULT, Culture.NORMAN.id(), 8)));
        // The ceiling has to clear the fed streak's own bar, or a camp is locked in
        // HOMESTEAD for ever with no sentry and no palisade.
        assertTrue(GoblinCamp.FORAGE_CEILING_PER_MOUTH
                        > com.civilization.sim.settlement.StagePlanner.FED_WINDOW_STEPS,
                "a camp can never be called fed, so it can never graduate");
    }

    // --- the scatter --------------------------------------------------------

    @Test
    void aCampWithNoChieftainAndNoShamanWalksAway() {
        Settlement camp = camp(8);
        camp.step(step(1));
        int before = camp.population();
        camp.removePerson(KingPlanner.king(camp).id());
        camp.removePerson(GoblinCamp.shaman(camp).id());
        camp.step(step(2));
        assertTrue(GoblinCamp.isScattering(camp),
                "both titles are gone and the camp is carrying on as normal");

        for (long at = 3; at < 60 && camp.hasLivingResidents(); at++) {
            camp.step(step(at));
        }
        assertFalse(camp.hasLivingResidents(),
                "the camp lost both its leaders and " + camp.population() + " goblins stayed");
        assertTrue(before > 0);
        assertTrue(camp.events().stream()
                        .anyMatch(event -> event.message().contains("scattered")),
                "the camp emptied and its own history does not say why");
        // The camp is left standing, not cleared away: a player can walk into it.
        assertTrue(camp.buildings().size() > 3,
                "the camp took its buildings with it when it left");
    }

    @Test
    void aCampWithAShamanAndNoChieftainHoldsTogether() {
        // Either title will do, which is the scatter rule stated the other way
        // round -- and it is what makes the shaman worth keeping alive.
        Settlement camp = camp(8);
        camp.step(step(1));
        camp.removePerson(KingPlanner.king(camp).id());
        camp.step(step(2));
        assertNotNull(GoblinCamp.shaman(camp), "the shaman went with the chieftain");
        assertFalse(GoblinCamp.isScattering(camp),
                "a camp with a keeper walked away anyway");
        for (long at = 3; at <= 30; at++) {
            camp.step(step(at));
        }
        assertTrue(camp.population() >= 5,
                "a camp that still had a shaman lost " + (8 - camp.population()) + " goblins");
    }

    @Test
    void aCampThatHasNeverNamedAnybodyDoesNotScatterOnItsFirstStep() {
        // The condition that keeps this from firing on a camp which simply has not
        // got round to a chieftain yet: a camp scatters when it has HAD one.
        Settlement camp = Founding.seeded(WHERE, "Grubfen", SettlementStage.HOMESTEAD,
                BuildCatalog.DEFAULT, Culture.GOBLIN.id(), 4, Culture.LAYOUT_GOBLIN_CAMP);
        assertNull(KingPlanner.seat(camp), "the fixture already has a chieftain hut");
        assertFalse(GoblinCamp.isScattering(camp));
        camp.step(step(1));
        assertEquals(4, camp.population(), "a camp with no seat yet threw itself away");
    }

    // --- the camp is not everybody else ------------------------------------

    @Test
    void nobodyElseBuildsTheCampsBuildingsAndTheCampBuildsNobodyElses() {
        for (String theirs : new String[] {"civilization:hovel", "civilization:tent",
                "civilization:loot_pile", "civilization:chieftain_hut"}) {
            assertTrue(Homes.buildableBy(Culture.GOBLIN.id(), theirs), theirs);
            assertFalse(Homes.buildableBy(Culture.NORMAN.id(), theirs),
                    "a Norman village raises a " + theirs);
            assertFalse(Homes.buildableBy(Culture.ORC.id(), theirs),
                    "a war camp raises a " + theirs);
        }
        for (String somebodyElses : new String[] {"civilization:cottage", "civilization:house",
                "civilization:hut", "civilization:great_hut", "civilization:town_hall"}) {
            assertFalse(Homes.buildableBy(Culture.GOBLIN.id(), somebodyElses),
                    "a camp raises a " + somebodyElses);
        }
    }

    @Test
    void everyCampBuildingIsSizedAndPinnedLikeEverythingElse() {
        for (String theirs : new String[] {"civilization:hovel", "civilization:tent",
                "civilization:loot_pile", "civilization:chieftain_hut"}) {
            BuildingSizes.Size size = BuildingSizes.of(theirs);
            assertNotNull(size, theirs + " has no declared size");
            assertTrue(BuildCatalog.DEFAULT.stream()
                            .anyMatch(type -> type.id().equals(theirs)),
                    theirs + " is drawn and is in no catalog");
            BuildingType row = BuildCatalog.DEFAULT.stream()
                    .filter(type -> type.id().equals(theirs)).findFirst().orElseThrow();
            assertEquals(BuildingSizes.plotSpanOf(theirs), row.plotSpan(),
                    theirs + " reserves ground that is not its own size");
        }
    }

    private static Set<SimPos> plots(Layout layout, int howMany) {
        Set<SimPos> out = new HashSet<>();
        for (int i = 0; i < howMany; i++) {
            out.add(layout.plotFor(WHERE, i));
        }
        return out;
    }

}
