package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Beds;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A family goes where its house goes.
 *
 * <p>The reported fault, and it is a whole town's worth. A village the world
 * generated stands on ground nobody has ever looked at: plots are chosen blind,
 * families are moved into houses that are still only a line in a ledger, and the
 * first time the hillside is actually read is the moment a player walks up to
 * it. Anything sited badly is quietly moved then — which is the right thing to
 * do with a cottage in a river, and was being done to the cottage alone.
 *
 * <p>The household stayed at the old plot. Nothing anywhere reads a home as a
 * point: {@code buildingAt} finds a house by its plot, so an address the house
 * has left finds nothing standing, and a family that lives nowhere is a family
 * with no beds. Every settler in it walked to an empty field at dusk and stood
 * in it until morning — which is exactly what a playtest found a whole village
 * doing, three cottages and nobody in any of the nine beds.
 *
 * <p>Nor did anything put it right afterwards. The vacancy search reads an
 * address with no building at it as "a house I have no record of" and leaves the
 * family alone rather than orphan them, so the state is permanent: those people
 * never sleep again.
 */
class RelocatedHomeTest {

    /** Capacity three, which is three beds and a small family. */
    private static final BuildingType COTTAGE = catalogEntry("civilization:cottage");

    private static BuildingType catalogEntry(String id) {
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        throw new IllegalStateException("no " + id + " in the catalog");
    }

    /**
     * Flat ground everywhere except one plot, which is a bog.
     *
     * <p>The smallest world that can produce the fault: one site the town has to
     * move off and an entire map of somewhere better, so the choice of where it
     * goes is the settlement's business and this test does not have to guess it.
     */
    private static final class OneBadPlot implements WorldBridge {
        private final SimPos bad;
        /** Where the surface is; flat, so a relocation is a move sideways only. */
        private static final int GROUND = 70;

        OneBadPlot(SimPos bad) {
            this.bad = bad;
        }

        private boolean isBad(SimPos plot) {
            return plot.x() == bad.x() && plot.z() == bad.z();
        }

        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return GROUND; }
        @Override public boolean standsInWater(SimPos pos, int radius) { return isBad(pos); }
        @Override public boolean isSiteSuitable(SimPos plot, int radius) { return !isBad(plot); }

        @Override public int siteFault(SimPos plot, int radius) {
            return isBad(plot) ? SITE_FAULT_OPEN_WATER : SITE_FAULT_NONE;
        }

        @Override public boolean isSiteLevelable(SimPos plot, int radius) { return !isBad(plot); }

        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                                        boolean surveyed, int facing) {
            // The real bridge snaps an unsurveyed origin to the ground it finds
            // and reports the height it actually drew at; so does this.
            return new Footprint(surveyed ? origin.y() : GROUND, 9, 9, 5);
        }

        @Override public void log(String message) { }
    }

    /** One cottage, one family of three in it, and the cottage never drawn. */
    private static Settlement townOf(SimPos plot) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Millbrook", new SimPos(0, 70, 0), 128);
        town.setCatalog(List.of(COTTAGE));
        town.addBuilding(new Building(COTTAGE.id(), plot, 0, false));

        Household family = new Household(Household.Id.random(), "Smith");
        family.setHome(plot);
        for (int head = 0; head < 3; head++) {
            Person person = new Person(
                    new Person.Id(UUID.nameUUIDFromBytes(("head" + head).getBytes())),
                    "Head " + head, Profession.FARMER, plot);
            town.addResident(person);
            family.addMember(person.id());
        }
        town.addHousehold(family);
        return town;
    }

    // --- the fault itself ----------------------------------------------------

    @Test
    void aFamilyWhoseHouseIsMovedMovesWithIt() {
        SimPos bog = new SimPos(40, 70, 40);
        Settlement town = townOf(bog);
        town.step(new SimContext(new OneBadPlot(bog), 1, SimSettings.SANDBOX));

        Building cottage = town.buildings().getFirst();
        assertNotEquals(bog.x() + "," + bog.z(),
                cottage.origin().x() + "," + cottage.origin().z(),
                "the cottage was standing in a bog and should have been moved;"
                        + " this test proves nothing until it is");

        Household family = town.households().getFirst();
        assertEquals(cottage.origin(), family.home(),
                "the house moved and the family did not: they are recorded as"
                        + " living on a plot with nothing on it");
        assertNotNull(town.buildingAt(family.home()),
                "a home address with no building at it is a family the town"
                        + " believes is housed and can find no roof for");
    }

    @Test
    void andEverybodyInItStillHasABedToGoTo() {
        // The fault as it was actually seen: not an address in a report, a
        // village standing about in the dark.
        SimPos bog = new SimPos(40, 70, 40);
        Settlement town = townOf(bog);
        town.step(new SimContext(new OneBadPlot(bog), 1, SimSettings.SANDBOX));

        Building cottage = town.buildings().getFirst();
        for (Person person : town.residents()) {
            SimPos bed = Beds.bedFor(town, person);
            assertNotNull(bed, person.name() + " lives in a cottage with three beds"
                    + " in it and has nowhere to sleep");
            assertTrue(Math.abs(bed.x() - cottage.origin().x()) <= 4
                            && Math.abs(bed.z() - cottage.origin().z()) <= 4,
                    person.name() + "'s bed is at " + bed + ", which is not inside"
                            + " the cottage at " + cottage.origin());
        }
    }

    @Test
    void andTheirBodiesAreWhereTheTownSaysTheyLive() {
        // Half of the same fix. A family recorded across town while everyone in
        // it is still standing on the old plot is a family the view layer puts
        // back on the old plot the moment it spawns them.
        SimPos bog = new SimPos(40, 70, 40);
        Settlement town = townOf(bog);
        town.step(new SimContext(new OneBadPlot(bog), 1, SimSettings.SANDBOX));

        // Where they end the step is the day's business -- somebody may have
        // been sent to a field or to the middle of town in the same pass. What
        // none of them may be is standing on the plot the house left.
        for (Person person : town.residents()) {
            assertNotEquals(bog.x() + "," + bog.z(),
                    person.position().x() + "," + person.position().z(),
                    person.name() + " is still standing where the house used to be");
        }
    }

    // --- the predicted bed is in the building that is standing ---------------

    @Test
    void theBedIsPredictedFromWhereTheBuildingEndedUpNotWhereItWasPlanned() {
        // The other half of a move: the height. A plot is chosen in an unloaded
        // chunk and carries an estimate; drawing it writes the real ground into
        // the building. A bed worked out from the plan's height is a bed one or
        // more courses out of the floor, which is a body standing on a mattress
        // it cannot see.
        SimPos bog = new SimPos(40, 99, 40);   // an estimate nine courses too high
        Settlement town = townOf(bog);
        town.step(new SimContext(new OneBadPlot(bog), 1, SimSettings.SANDBOX));

        Building cottage = town.buildings().getFirst();
        assertEquals(OneBadPlot.GROUND, cottage.origin().y(),
                "the building should be recorded at the ground it was drawn on");
        for (Person person : town.residents()) {
            assertEquals(cottage.origin().y() + Beds.FLOOR_COURSE,
                    Beds.bedFor(town, person).y(),
                    "a bed stands on the floor course of the building as placed");
        }
    }

    // --- and the whole village, on the ground every siting fault reproduces on -

    /** The recorded hillside, loaded everywhere, with somebody standing in it. */
    private static final class Hillside implements WorldBridge {
        private final RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);

        @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return ground.surfaceHeight(pos); }
        @Override public boolean standsInWater(SimPos p, int r) { return ground.standsInWater(p, r); }
        @Override public boolean isSiteSuitable(SimPos p, int r) { return ground.isSiteSuitable(p, r); }
        @Override public int siteFault(SimPos p, int r) { return ground.siteFault(p, r); }
        @Override public boolean isSiteLevelable(SimPos p, int r) { return ground.isSiteLevelable(p, r); }
        @Override public int woodedness(SimPos c, int r) { return ground.woodedness(c, r); }

        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return ground.materializeBlueprint(id, origin, surveyed, facing);
        }

        @Override public void log(String message) { }
    }

    @Test
    void andNotOneVillageOnTheRecordedGroundLosesAnybodysBed() {
        // The sweep that found it. Seeded villages up and down the recorded
        // hillside, each one stepped until its buildings are drawn, and the one
        // question asked of every settler in every one of them: the town says
        // you are housed, so where do you sleep? Before the fix, towns whose
        // bunkhouse or cottage was moved on arrival answered "nowhere" for
        // everyone under that roof -- six housed people out of twelve with a
        // bed, and three out of twelve where two homes moved.
        List<String> homeless = new ArrayList<>();
        for (int x = -200; x <= 200; x += 40) {
            for (int z = -200; z <= 200; z += 40) {
                Hillside bridge = new Hillside();
                SimPos at = new SimPos(x, bridge.surfaceHeight(new SimPos(x, 64, z)), z);
                Settlement town = Founding.seeded(at, "Millbrook", SettlementStage.VILLAGE,
                        BuildCatalog.DEFAULT, Culture.NORMAN.id());
                town.setLayoutId(Layouts.RING.id());
                for (Person resident : town.residents()) {
                    resident.setEmbodied(true);
                }
                for (int step = 1; step <= 3; step++) {
                    town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
                }
                int housed = 0;
                int withABed = 0;
                for (Person person : town.residents()) {
                    if (!isHoused(town, person)) {
                        continue;   // a real state, and not this test's business
                    }
                    housed++;
                    if (Beds.bedFor(town, person) != null) {
                        withABed++;
                    }
                }
                if (withABed < housed) {
                    homeless.add("the village at " + x + "," + z + " houses " + housed
                            + " and has beds for " + withABed);
                }
            }
        }
        assertTrue(homeless.isEmpty(), "villages where somebody the town calls housed"
                + " has no bed to go to: " + homeless);
    }

    private static boolean isHoused(Settlement town, Person person) {
        for (Household household : town.households()) {
            if (household.isHoused() && household.members().contains(person.id())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void andNoTwoOfThemAreSentToTheSameMattress() {
        // The way a careless re-link would break it: move a family onto a plot
        // another family already holds and both are ranked in the same three
        // beds, so two bodies climb into one.
        Map<SimPos, Integer> perBed = new HashMap<>();
        Hillside bridge = new Hillside();
        SimPos at = new SimPos(-200, bridge.surfaceHeight(new SimPos(-200, 64, 0)), 0);
        Settlement town = Founding.seeded(at, "Millbrook", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, Culture.NORMAN.id());
        town.setLayoutId(Layouts.RING.id());
        for (int step = 1; step <= 3; step++) {
            town.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }
        for (Person person : town.residents()) {
            SimPos bed = Beds.bedFor(town, person);
            if (bed != null) {
                perBed.merge(bed, 1, Integer::sum);
            }
        }
        for (Map.Entry<SimPos, Integer> mattress : perBed.entrySet()) {
            assertEquals(1, mattress.getValue().intValue(),
                    "two people were sent to the bed at " + mattress.getKey());
        }
    }
}
