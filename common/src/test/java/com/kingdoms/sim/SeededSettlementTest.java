package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Founding;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.StagePlanner;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A town that skipped the road, and has to be indistinguishable from one that
 * walked it.
 *
 * <p>{@code Founding.party} is the only settlement this simulation has ever
 * made, and it is four people in a field. Everything a player can find at world
 * generation has to be the other thing — standing, staffed, stocked — and the
 * risk in conjuring one is not that it looks wrong. It is that it is a state the
 * economy has never been in: full stores and no history, a staffing table asked
 * to place twelve people at once instead of one a step, households in houses
 * nobody watched them move into. So the assertions below come in two kinds. Most
 * pin what a seeded town <em>is</em>; the last one lets it run and asks whether
 * it survives being one.
 */
class SeededSettlementTest {

    private static final SimPos SITE = new SimPos(0, 72, 0);

    /**
     * The lowland people, whose arrangement is the plain ring.
     *
     * <p>Named rather than defaulted so the plan the buildings are laid on is a
     * decision this test made, not one it inherited.
     */
    private static final String CULTURE = Culture.NORMAN.id();

    private static Settlement seeded(SettlementStage stage) {
        return Founding.seeded(SITE, "Seedholt", stage, BuildCatalogue.DEFAULT, CULTURE);
    }

    @Test
    void everyStageStandsTheWholeProgrammeItClimbedThrough() {
        // Cumulative, and that is the claim: a village did not skip its
        // bunkhouse on the way past, so a seeded one has not either. Asked of
        // StagePlanner rather than of a list written down here, because a
        // second copy of the program would be wrong the first time anybody
        // edited the first.
        for (SettlementStage stage : SettlementStage.values()) {
            Settlement town = seeded(stage);
            for (SettlementStage climbed : SettlementStage.values()) {
                if (!stage.atLeast(climbed)) {
                    break;
                }
                town.setStage(climbed);
                assertTrue(StagePlanner.programComplete(town),
                        "a " + stage.pretty() + " is missing something its "
                                + climbed.pretty() + " program called for");
            }
        }
    }

    @Test
    void noStageStandsWhatItsStageForbids() {
        // The hall is the one the whole staging design exists to hold back, and
        // a seeded village with one in it would be exactly the fault the stages
        // were introduced to fix, arriving by a new door.
        for (SettlementStage stage : SettlementStage.values()) {
            Settlement town = seeded(stage);
            for (Building standing : town.buildings()) {
                assertTrue(StagePlanner.catalogueAllows(stage, standing.blueprintId()),
                        "a " + stage.pretty() + " has no business standing a "
                                + standing.blueprintId());
            }
        }
        assertEquals(0, seeded(SettlementStage.VILLAGE).countBuildings("kingdoms:town_hall"),
                "even a village has not earned the hall");
        assertEquals(1, seeded(SettlementStage.TOWN).countBuildings("kingdoms:town_hall"),
                "and a town has");
    }

    @Test
    void nothingIsBuiltThroughAnythingElse() {
        // The plots come out of the town plan, which already refuses a plot that
        // fouls a neighbor -- so this is really asserting that the seeding
        // walks the plan rather than inventing positions. It is cheap and it is
        // the fault that reads worst from the ground.
        for (SettlementStage stage : SettlementStage.values()) {
            List<Building> holding = new ArrayList<>();
            for (Building standing : seeded(stage).buildings()) {
                if (BuildPlanner.holdsGround(standing.blueprintId())) {
                    holding.add(standing);
                }
            }
            for (int a = 0; a < holding.size(); a++) {
                for (int b = a + 1; b < holding.size(); b++) {
                    Building one = holding.get(a);
                    Building two = holding.get(b);
                    assertFalse(BuildPlanner.plotsOverlap(
                                    one.origin(), span(one), two.origin(), span(two)),
                            stage.pretty() + " put " + one.blueprintId() + " through "
                                    + two.blueprintId());
                }
            }
        }
    }

    private static int span(Building standing) {
        return BuildPlanner.plotSpanOf(standing.blueprintId(), BuildCatalogue.DEFAULT);
    }

    @Test
    void everyStageIsInhabitedByPeopleWithSomethingToDo() {
        for (SettlementStage stage : SettlementStage.values()) {
            Settlement town = seeded(stage);
            // The charter party is the floor: a camp houses nobody, and four
            // settlers in the open is still a camp.
            assertTrue(town.population() >= TownStores.FOUNDING_SETTLERS,
                    stage.pretty() + " is empty, which is a drawing and not a town");

            if (StagePlanner.pioneersLabour(stage)) {
                // Below VILLAGE the generalists are the workforce, and the only
                // fixed trades are the two the fortification names.
                for (Person person : town.residents()) {
                    assertTrue(person.profession() == Profession.PIONEER
                                    || person.profession() == Profession.GUARD
                                    || person.profession() == Profession.LUMBERJACK,
                            stage.pretty() + " has a " + person.profession()
                                    + ", which no stage below the village names");
                }
                continue;
            }

            assertEquals(0, JobPlanner.count(town, Profession.PIONEER),
                    "from VILLAGE the specialists have arrived and the pioneers are gone");
            assertTrue(JobPlanner.count(town, Profession.BUILDER) >= 1,
                    stage.pretty() + " has nobody who can raise its next building");
            assertTrue(JobPlanner.count(town, Profession.FARMER) >= 1,
                    stage.pretty() + " has nobody in its fields");
            assertTrue(JobPlanner.count(town, Profession.GUARD) >= 1,
                    stage.pretty() + " has nobody on its wall");
        }
    }

    @Test
    void theFortificationKeepsItsSentryAndItsWoodcutter() {
        // Crystallization is a stage event, so a settlement that never lived
        // through the stage has to be given what the stage would have named.
        Settlement fort = seeded(SettlementStage.FORTIFIED);

        assertEquals(1, JobPlanner.count(fort, Profession.GUARD),
                "a fortified camp has a sentry, however it came to be fortified");
        assertEquals(1, JobPlanner.count(fort, Profession.LUMBERJACK),
                "and an axe, because the palisade drinks more timber than any kit holds");
    }

    @Test
    void everyStageIsFedAndCanPayForItsNextBuilding() {
        for (SettlementStage stage : SettlementStage.values()) {
            Settlement town = seeded(stage);
            assertFalse(town.isStarving(),
                    stage.pretty() + " arrives starving, which is not a town anybody found");
            assertTrue(FoodPlanner.totalFood(town)
                            >= town.population() * StagePlanner.FED_WINDOW_STEPS,
                    stage.pretty() + " holds less than the larder a homestead has to "
                            + "prove before it may graduate");
            assertTrue(town.woodStock() > 0 && town.stoneStock() > 0,
                    stage.pretty() + " has nothing to build its next building out of");
        }
    }

    @Test
    void theBuildingsAreDataUntilSomebodyComesToLookAtThem() {
        // The whole point of seeding rather than drawing. A seeded town exists
        // in the simulation immediately and is painted in by
        // materializePending when a chunk loads -- the same road a town grown
        // while the player was away comes down. Marking them drawn here would
        // leave a settlement whose blocks nobody ever laid.
        for (SettlementStage stage : SettlementStage.values()) {
            Settlement town = seeded(stage);
            assertEquals(town.buildings().size(), town.pendingBuildings().size(),
                    stage.pretty() + " claims blocks nobody has laid");
            for (Building standing : town.buildings()) {
                assertFalse(standing.isSurveyed(),
                        standing.blueprintId() + " claims a height nobody measured, "
                                + "which stops it ever being moved off bad ground");
            }
        }
    }

    @Test
    void theNextPlotIsNotOneAlreadyBuiltOn() {
        // The cursor has to carry on from where the program left off, or the
        // town spends its first builds discovering itself. Asserted as "past
        // every plot the program took" rather than "the next plot is free",
        // because a plot beside a farm genuinely is not free and the siting
        // loop's answer to that is to walk on, not to stop.
        for (SettlementStage stage : SettlementStage.values()) {
            Settlement town = seeded(stage);
            TownPlan plan = town.arrangement().planFor(town.centre(), PLAN_LOOKUP);
            for (Building standing : town.buildings()) {
                int index = plotIndexOf(plan, standing.origin());
                assertTrue(index >= 0,
                        standing.blueprintId() + " stands somewhere the plan never offered");
                assertTrue(index < town.nextPlotIndex(),
                        stage.pretty() + " would offer plot " + index + " again, and "
                                + standing.blueprintId() + " is already on it");
            }
        }
    }

    @Test
    void everyFamilyIsCalledWhatItsMembersAreCalled() {
        // Residents made first and grouped afterwards produce a household named
        // Miller with three Bakers living in it -- and PopulationPlanner then
        // names their children Miller. A person's name is final, so the only
        // fix is to know the family before the person exists, and this is what
        // pins that order.
        for (SettlementStage stage : SettlementStage.values()) {
            Settlement town = seeded(stage);
            for (Household family : town.households()) {
                for (Person.Id id : family.members()) {
                    Person member = town.resident(id);
                    assertTrue(member != null && member.name().endsWith(" " + family.name()),
                            stage.pretty() + " has a "
                                    + (member == null ? "ghost" : member.name())
                                    + " living with the " + family.name() + "s");
                }
            }
        }
    }

    @Test
    void aTownSeededWithMorePeopleThanBedsStillGathersOrdinaryFamilies() {
        // /civ seed takes a count up to two hundred, and the obvious
        // implementation puts everyone the houses could not take into one
        // household. That is a state the ordinary planner cannot produce and
        // two things break on it: assignHomes houses a family without checking
        // its size, so a hundred and eighty-eight people "move into" a four-bed
        // house and StagePlanner.familyHoused counts them all as housed; and
        // growFamilies then sheds a member every cycle forever.
        Settlement crowded = Founding.seeded(SITE, "Crowdholt", SettlementStage.VILLAGE,
                BuildCatalogue.DEFAULT, CULTURE, 200);

        assertEquals(200, crowded.population(), "the count asked for is the count seeded");
        int biggestHouse = BuildCatalogue.DEFAULT.stream()
                .filter(type -> type.capacity() > 0)
                .mapToInt(type -> type.capacity())
                .max().orElseThrow();
        for (Household family : crowded.households()) {
            assertTrue(family.size() <= biggestHouse,
                    "a family of " + family.size() + " is bigger than any house in the "
                            + "catalog, and nothing in the town can ever house it");
        }
        int counted = crowded.households().stream().mapToInt(Household::size).sum();
        assertEquals(200, counted, "somebody was seeded into no family at all");
    }

    @Test
    void aSeededStageIsAStageAboutToEnd() {
        // Not a fault, and worth pinning precisely because it looks like one.
        // Standing the whole of a stage's program IS most of what
        // StagePlanner.readyToAdvance asks for, so a seeded settlement sits at
        // the exit of its stage rather than in the middle of it -- exactly
        // where an honestly-grown town sits on the step after it finishes
        // building. Seeding only the stages below the one asked for would hand
        // back a "village" with no cottages, market or mill, which is a
        // fortified camp wearing a label.
        TerrainFake ground = new TerrainFake(11);
        assertEquals(SettlementStage.HOMESTEAD,
                afterOneStep(seeded(SettlementStage.CAMP), ground),
                "a camp with its post and cache staked has finished being a camp");
        assertEquals(SettlementStage.VILLAGE,
                afterOneStep(seeded(SettlementStage.FORTIFIED), ground),
                "a fortified camp with its sentry named has finished being one");
        assertEquals(SettlementStage.TOWN,
                afterOneStep(seeded(SettlementStage.VILLAGE), ground),
                "a village with its workshops open and its families housed is a town");

        // The one that holds, and for a reason: the homestead's gate is a fed
        // streak, which is ten steps of feeding itself in front of you and
        // cannot be conjured.
        assertEquals(SettlementStage.HOMESTEAD,
                afterOneStep(seeded(SettlementStage.HOMESTEAD), ground),
                "the homestead has to earn its streak like anybody else");
    }

    private static SettlementStage afterOneStep(Settlement town, TerrainFake ground) {
        town.step(new SimContext(ground, 1, SimSettings.SANDBOX));
        return town.stage();
    }

    /**
     * The soak. Two hundred steps of a village that was conjured rather than
     * grown, on ground with a river in it.
     *
     * <p>This is the assertion the rest of the file exists to make possible, and
     * the one that could genuinely fail: every number above was chosen, and a
     * chosen number is a guess until something runs on it. What it is watching
     * for is the three ways a state the economy has never been in shows up —
     * a throw, a population that falls away, and a town that is quietly in
     * trouble at the end without ever having crashed.
     */
    @Test
    void aSeededVillageLivesTwoHundredStepsWithoutTrouble() {
        TerrainFake ground = new TerrainFake(11);
        Settlement village = seeded(SettlementStage.VILLAGE);
        int arrived = village.population();

        for (int step = 1; step <= SOAK_STEPS; step++) {
            village.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }

        assertTrue(village.population() >= arrived,
                "the village arrived with " + arrived + " people and has "
                        + village.population() + ": a seeded town that shrinks is a "
                        + "town the economy could not carry");
        assertFalse(village.isStarving(),
                "two hundred steps in and the larder has run out");
        // The distress reading the town post and the overview screen show,
        // mirrored here because those live in the platform layer and this does
        // not. A town nobody would want to walk into is a failure even when
        // nothing threw.
        assertEquals(0, howMany(village, Person.HUNGER_SEVERE),
                "somebody is starving to death in a village that was handed a granary");
        assertEquals(0, howMany(village, Person.HUNGER_WEAK),
                "somebody is too weak to work, which is how the spiral starts");
        assertTrue(village.foodStock() > 0, "the granary is empty");
    }

    /** How many residents are at or past a hunger mark. */
    private static int howMany(Settlement town, int mark) {
        return (int) town.residents().stream().filter(p -> p.hunger() >= mark).count();
    }

    private static final int SOAK_STEPS = 200;

    /** Far enough into the plan to find any plot a program could have taken. */
    private static final int PLAN_LOOKUP = 64;

    /** Which plot of the plan a building stands on, or -1 if none does. */
    private static int plotIndexOf(TownPlan plan, SimPos origin) {
        for (int i = 0; i < plan.size(); i++) {
            SimPos at = plan.plot(i).at();
            // The height is the ground's business, not the plan's, so a
            // building's y need not match the plot it was set down on.
            if (at.x() == origin.x() && at.z() == origin.z()) {
                return i;
            }
        }
        return -1;
    }
}
