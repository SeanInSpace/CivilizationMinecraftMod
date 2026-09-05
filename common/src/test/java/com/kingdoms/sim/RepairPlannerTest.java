package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.RepairPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A building notices when it has been knocked about, and gets fixed when it
 * matters.
 *
 * <p>Damage is measured by counting, not by watching for explosions: the
 * building is counted when first seen whole and counted again afterwards, and
 * the shortfall is the damage. That is why none of these tests mention a
 * creeper — it does not matter what took the blocks.
 */
class RepairPlannerTest {

    private static final BuildingType COTTAGE =
            new BuildingType("test:cottage", 100, 0, 0, 0, 80, 4);

    /** Reports whatever census it is told to. */
    private static final class QuarryBridge implements WorldBridge {
        static final int WHOLE = 200;

        int standing = WHOLE;

        /**
         * Whether the ground can be written to as well as counted.
         *
         * <p>An odd pairing until you see where it comes from. The clock runs on
         * a settlement whether or not anybody is near it, so the last payment for
         * a repair can land on a step after the player has walked away: the
         * census was taken while the chunk was there, and the blocks have nowhere
         * to go by the time they are paid for.
         */
        boolean writable = true;

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin, boolean s, int f) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
        @Override public int solidBlocksIn(SimPos origin, Footprint plot) { return standing; }

        /**
         * The blocks go back, which is the whole point of the call.
         *
         * <p>A fake that swallowed this and left the census short would let the
         * town book the same repair again on the very next look, pay for it
         * again, and go round forever — so a fake that does nothing would hide
         * the one property the unwatched path has to have.
         */
        @Override public int repairBlueprint(String id, SimPos origin, int facing) {
            if (!writable) {
                return -1;
            }
            int missing = Math.max(0, WHOLE - standing);
            standing = WHOLE;
            return missing;
        }
    }

    /**
     * A town that can actually mend something: a builder on his feet and enough
     * timber and stone to pay for the blocks.
     *
     * <p>Both are part of the fixture rather than incidental to it. The planner
     * refuses to book a repair the settlement could not begin — see the
     * demolition interlock in {@code TownAuditor} for why that matters — so a
     * town with nobody in it and nothing on its shelves would test only the
     * refusal.
     */
    private static Settlement town() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, 64, 0), 64);
        town.setCatalogue(List.of(COTTAGE));
        Building cottage = new Building(COTTAGE.id(), new SimPos(10, 64, 10), 1, true);
        cottage.setFootprint(new Footprint(64, 5, 5, 4));
        town.addBuilding(cottage);
        town.addResident(new Person(
                Person.Id.random(), "Alder", Profession.BUILDER, town.centre()));
        town.setStock(TownStores.WOOD, 4000);
        town.setStock(TownStores.STONE, 4000);
        return town;
    }

    private static Building only(Settlement town) {
        return town.buildings().getFirst();
    }

    private static void look(Settlement town, QuarryBridge bridge, int step) {
        RepairPlanner.advance(town, new SimContext(bridge, step, SimSettings.SANDBOX));
    }

    // --- the baseline ---

    @Test
    void theFirstLookEstablishesWhatWholeLooksLike() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();

        look(town, bridge, 0);

        assertEquals(200, only(town).soundCensus(), "counted as it stands, not as drawn");
        assertEquals(0, only(town).damage(), "and nothing is wrong with it yet");
    }

    @Test
    void aBuildingNobodyCanSeeIsNotDecaying() {
        // The distinction the whole feature turns on: "I cannot count it" must
        // never read as "there is nothing left of it".
        Settlement town = town();
        QuarryBridge blind = new QuarryBridge();
        blind.standing = -1;

        look(town, blind, 0);

        assertFalse(only(town).hasCensus(), "no baseline was taken");
        assertEquals(0, only(town).damage(), "and certainly no damage was invented");
    }

    @Test
    void anUnmeasuredBuildingIsLeftAlone() {
        Settlement town = town();
        only(town).setFootprint(Footprint.UNKNOWN);
        QuarryBridge bridge = new QuarryBridge();

        look(town, bridge, 0);

        assertFalse(only(town).hasCensus(), "there is no volume to count inside");
    }

    // --- damage ---

    @Test
    void blocksGoneAreDamageRecorded() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 180;   // ten percent of it gone
        look(town, bridge, 1);

        assertEquals(10, only(town).damage());
        assertTrue(only(town).isDamaged(), "ten percent is worth recording");
        assertTrue(only(town).needsRepair(),
                "and worth mending: twenty blocks out of a cottage is a hole you"
                        + " can walk through, not weathering");
    }

    @Test
    void aBlockOrTwoIsNotDamage() {
        // A census taken through a doorway somebody is standing in comes back
        // light. A building that flickered between damaged and sound every step
        // would fill the log with nothing.
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 197;
        look(town, bridge, 1);

        assertFalse(only(town).isDamaged(), "under the noise floor");
        assertEquals(0, only(town).damage());
    }

    @Test
    void severeDamageBooksTheWork() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 120;   // forty percent gone
        look(town, bridge, 1);

        assertTrue(only(town).needsRepair());
        assertEquals(1, town.buildQueue().size(), "a repair is booked");
        assertEquals(new SimPos(10, 64, 10), town.buildQueue().getFirst().upgradeOf(),
                "as work on the building that stands there, not a second cottage");
        assertEquals(COTTAGE.id(), town.buildQueue().getFirst().blueprintId(),
                "and to the level it already had");
        assertTrue(town.buildQueue().getFirst().isRepair(),
                "and marked a repair, which is what keeps the crew from excavating"
                        + " the cottage they were sent to mend");
    }

    // --- where the town starts caring ---

    @Test
    void aTwentiethMissingIsWorthMending() {
        // The reported fault, stated as a number. Five per cent of a cottage is a
        // dozen blocks — a creeper's worth of wall — and the town used to need a
        // quarter of the building gone before it would lift a finger.
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 190;   // ten of two hundred: exactly five percent
        look(town, bridge, 1);

        assertEquals(5, only(town).damage());
        assertTrue(only(town).needsRepair());
        assertEquals(1, town.buildQueue().size(), "a repair is booked at the threshold");
    }

    @Test
    void aFortiethMissingIsNot() {
        // The other side of the same line, so the threshold is a threshold rather
        // than a direction of travel.
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 192;   // eight of two hundred: four percent
        look(town, bridge, 1);

        assertEquals(0, only(town).damage(), "under the noise floor, so no damage at all");
        assertTrue(town.buildQueue().isEmpty(), "and nobody is sent out to it");
    }

    @Test
    void oneBlockOffSomethingTinyIsStillNothing() {
        // Where the percentage cannot speak. A structure of twenty blocks reads a
        // single missing one as five per cent, and one block is a torch or a slab
        // somebody helped themselves to.
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        bridge.standing = 20;
        look(town, bridge, 0);

        bridge.standing = 19;
        look(town, bridge, 1);

        assertEquals(0, only(town).damage(), "one block is not a hole");
        assertTrue(town.buildQueue().isEmpty());

        bridge.standing = 18;
        look(town, bridge, 2);

        assertEquals(10, only(town).damage(), "two is");
        assertEquals(1, town.buildQueue().size());
    }

    // --- what the clock pays for it ---

    /**
     * The settings these runs are measured under.
     *
     * <p>The growth ceiling is held at one so the crew is one pair of hands from
     * the first step to the last. The clock clears a step's work for every able
     * builder, so a town that gained a settler halfway through would be paying at
     * a rate that changed under the measurement.
     */
    private static SimSettings pinnedToOneSettler() {
        return new SimSettings(
                SimSettings.SANDBOX.simIntervalTicks(),
                SimSettings.SANDBOX.stepsPerBirth(),
                SimSettings.SANDBOX.observedRadius(),
                SimSettings.SANDBOX.embodyCapPerSettlement(),
                SimSettings.SANDBOX.raidIntervalSteps(),
                false,
                1);
    }

    /** Wood then stone drawn out of a town's shelves over a run of steps. */
    private static int[] spentOver(Settlement town, QuarryBridge bridge,
                                   SimSettings settings, int from, int to) {
        int wood = town.stores().get(TownStores.WOOD);
        int stone = town.stores().get(TownStores.STONE);
        for (int step = from; step < to; step++) {
            town.step(new SimContext(bridge, step, settings));
        }
        return new int[]{
                wood - town.stores().get(TownStores.WOOD),
                stone - town.stores().get(TownStores.STONE)};
    }

    @Test
    void theClockChargesForTheMissingBlocksAndNoMore() {
        // The unwatched half, driven rather than asserted about. Nobody is
        // embodied, so no hand can lay anything, the settlement's own clock runs
        // the job, and it pays per unit of work at the rate any build pays — over
        // a job priced at the share of the building that is gone. Two fifths of a
        // cottage missing costs two fifths of a cottage.
        //
        // Measured against a control rather than against nothing, because a town
        // is never only doing one thing: the same forty steps see it staking a
        // perimeter and equipping its worker, and that timber is not the repair's
        // timber. Two identical settlements, the same number of steps, and only
        // one of them with a hole in its cottage — the difference is the repair.
        SimSettings settings = pinnedToOneSettler();
        int steps = 60;   // comfortably past the forty units this repair is worth

        Settlement hurt = town();
        hurt.setStock(TownStores.FOOD, 4000);
        QuarryBridge knocked = new QuarryBridge();
        hurt.step(new SimContext(knocked, 0, settings));   // seen whole once
        knocked.standing = 120;                            // then two fifths gone
        int[] withRepair = spentOver(hurt, knocked, settings, 1, steps);

        Settlement whole = town();
        whole.setStock(TownStores.FOOD, 4000);
        QuarryBridge sound = new QuarryBridge();
        whole.step(new SimContext(sound, 0, settings));
        int[] without = spentOver(whole, sound, settings, 1, steps);

        assertTrue(whole.buildQueue().isEmpty(),
                "the control has nothing to build, so its spend is the town's own"
                        + " housekeeping and nothing else");
        int work = RepairPlanner.repairWork(COTTAGE, 40);
        assertEquals(40, work, "two fifths of a hundred-unit cottage");
        assertEquals(BuildPlanner.WOOD_PER_WORK * work, withRepair[0] - without[0],
                "the timber for the missing blocks and not a stick more");
        assertEquals(BuildPlanner.STONE_PER_WORK * work, withRepair[1] - without[1],
                "and the same for the stone");
    }

    @Test
    void anUnwatchedRepairMendsTheBuildingRatherThanReDrawingIt() {
        // The fault the whole unit is about, from the simulation's side. A repair
        // that finished used to clear the materialized flag, which handed the
        // building to materializePending and stamped the entire blueprint back
        // down in one tick. Now it stays drawn from beginning to end and only the
        // missing blocks are asked for — and because the census is kept rather
        // than re-baselined, a building that really was mended reads sound and
        // the town does not book the same job again forever.
        SimSettings settings = pinnedToOneSettler();
        Settlement town = town();
        town.setStock(TownStores.FOOD, 4000);
        QuarryBridge bridge = new QuarryBridge();
        town.step(new SimContext(bridge, 0, settings));
        bridge.standing = 120;

        for (int step = 1; step < 120; step++) {
            town.step(new SimContext(bridge, step, settings));
            assertTrue(only(town).isMaterialized(),
                    "the cottage is standing throughout; nothing may ever ask for it"
                            + " to be drawn again");
        }

        assertEquals(QuarryBridge.WHOLE, bridge.standing, "the blocks went back");
        assertEquals(200, only(town).soundCensus(),
                "against the count it was whole at, not against the hole");
        assertEquals(0, only(town).damage());
        assertTrue(town.buildQueue().isEmpty(), "and the job is not booked again");
    }

    @Test
    void aRepairPaidForOnGroundNobodyCanWriteToIsNotWrittenOff() {
        // The clock does not stop when the player walks away, so the last payment
        // for a repair can land on a step where there is no chunk to lay anything
        // in. Squaring the books on that step is the worst of both: the town has
        // paid, no block has moved, the damage is cleared, and the census is
        // retaken against the shell — which makes the hole the building's proper
        // size for good, and no repair is ever booked for it again.
        SimSettings settings = pinnedToOneSettler();
        Settlement town = town();
        town.setStock(TownStores.FOOD, 4000);
        QuarryBridge bridge = new QuarryBridge();
        town.step(new SimContext(bridge, 0, settings));
        bridge.standing = 120;
        bridge.writable = false;

        for (int step = 1; step < 120; step++) {
            town.step(new SimContext(bridge, step, settings));
        }

        assertEquals(200, only(town).soundCensus(),
                "still measured against the cottage, not against what is left of it");
        assertEquals(40, only(town).damage(), "the hole is still a hole");
        assertEquals(1, town.buildQueue().size(),
                "so the town books it again, for somebody who can actually reach it");
    }

    @Test
    void aTownThatCannotStartTheRepairDoesNotBookIt() {
        // The demolition interlock, from this end. TownAuditor spares any building
        // with a repair on the books, so a town that booked repairs it could never
        // begin would spare every ruin it ever saw and write nothing off again.
        Settlement broke = town();
        broke.setStock(TownStores.WOOD, 0);
        broke.setStock(TownStores.STONE, 0);
        QuarryBridge bridge = new QuarryBridge();
        look(broke, bridge, 0);

        bridge.standing = 120;
        look(broke, bridge, 1);

        assertTrue(broke.buildQueue().isEmpty(),
                "there is nothing on the shelves to mend it with");
        assertEquals(40, only(broke).damage(), "which is not the same as not noticing");
    }

    @Test
    void aTownWithNobodyToSendDoesNotBookItEither() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Emptyburg", new SimPos(0, 64, 0), 64);
        town.setCatalogue(List.of(COTTAGE));
        Building cottage = new Building(COTTAGE.id(), new SimPos(10, 64, 10), 1, true);
        cottage.setFootprint(new Footprint(64, 5, 5, 4));
        town.addBuilding(cottage);
        town.setStock(TownStores.WOOD, 4000);
        town.setStock(TownStores.STONE, 4000);
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 120;
        look(town, bridge, 1);

        assertTrue(town.buildQueue().isEmpty(), "a repair wants a pair of hands");
    }

    @Test
    void theWorkIsPricedByWhatIsMissing() {
        assertEquals(40, RepairPlanner.repairWork(COTTAGE, 40),
                "two fifths of a hundred-unit building");
        assertTrue(RepairPlanner.repairWork(COTTAGE, 40)
                        < RepairPlanner.repairWork(COTTAGE, 90),
                "a wall gone costs less than a building flattened");
        assertTrue(RepairPlanner.repairWork(COTTAGE, 0) >= 1,
                "no repair is free");
    }

    @Test
    void oneRepairIsBookedNotOnePerStep() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);
        bridge.standing = 120;

        for (int step = 1; step <= 10; step++) {
            look(town, bridge, step);
        }

        assertEquals(1, town.buildQueue().size(),
                "the town books the job once, not every time it looks at the hole");
    }

    // --- getting better ---

    @Test
    void aBuildingPutRightIsCalledWholeAgain() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);
        bridge.standing = 120;
        look(town, bridge, 1);
        assertTrue(only(town).needsRepair());

        bridge.standing = 200;
        look(town, bridge, 2);

        assertEquals(0, only(town).damage());
        assertFalse(only(town).isDamaged());
    }

    @Test
    void aBuildingMadeBiggerIsNotPermanentlySound() {
        // An upgrade adds blocks. Keeping the old baseline would mean the new,
        // larger building could lose everything it gained before anybody noticed.
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 300;
        look(town, bridge, 1);

        assertEquals(300, only(town).soundCensus(), "the higher figure is the new truth");
    }

    @Test
    void aBuildingThatIsAllButGoneIsNotOverAHundredPercent() {
        Settlement town = town();
        QuarryBridge bridge = new QuarryBridge();
        look(town, bridge, 0);

        bridge.standing = 0;
        look(town, bridge, 1);

        assertEquals(100, only(town).damage(), "flattened is flattened");
    }
}
