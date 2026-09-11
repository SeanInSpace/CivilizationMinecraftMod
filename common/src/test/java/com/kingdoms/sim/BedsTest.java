package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Beds;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bed table, held to the three things that make it worth having.
 *
 * <p>One: every home has a bed for every head the catalog says it holds. Housing
 * capacity is what decides whether a family may grow, so a house whose capacity
 * and whose bed count disagree is a house where somebody is entitled to live and
 * has nowhere to lie down.
 *
 * <p>Two: the cells are all indoors, and no two beds share one. This is the
 * check that cannot be made in the world without drawing every home in every
 * culture — {@link BuildingSizes} already says where the walls are, so it can be
 * made here instead, for free, against the same table the placer draws from.
 *
 * <p>Three: the assignment is deterministic and one bed per person. Nothing
 * about beds is written to the save; a settler's bed is recomputed from who
 * lives under their roof, so "the same answer twice" is load-bearing rather
 * than tidy.
 *
 * <p>That the world actually holds a bed where this says it does is
 * {@code BlueprintPlacerBedTest}'s job, because only that one can draw blocks.
 */
class BedsTest {

    /** Every home in the catalog: the kinds with room for heads in them. */
    private static List<BuildingType> homes() {
        List<BuildingType> homes = new ArrayList<>();
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (type.capacity() > 0) {
                homes.add(type);
            }
        }
        return homes;
    }

    private static String pathOf(BuildingType type) {
        String id = type.id();
        return id.substring(id.indexOf(':') + 1);
    }

    // --- one bed per head -----------------------------------------------------

    @Test
    void everyHomeHasABedForEveryHeadItHolds() {
        List<BuildingType> homes = homes();
        assertFalse(homes.isEmpty(), "the catalog has stopped housing anybody");

        for (BuildingType home : homes) {
            assertEquals(home.capacity(), Beds.countIn(home.id()),
                    pathOf(home) + " is declared to hold " + home.capacity()
                            + " and lays " + Beds.countIn(home.id()) + " beds."
                            + " Capacity is what lets a family grow, so the"
                            + " difference is somebody with a right to live here"
                            + " and nowhere to lie down");
        }
    }

    @Test
    void nothingThatIsNotAHomeLaysABed() {
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (type.capacity() > 0) {
                continue;
            }
            assertEquals(0, Beds.countIn(type.id()),
                    pathOf(type) + " houses nobody and has beds in it");
            assertFalse(Beds.isHome(type.id()));
        }
    }

    @Test
    void aBuildingNobodyHasHeardOfHasNoBeds() {
        // The lookup a datapack will reach, and the one an old save reaches for a
        // building that has since been renamed. Empty, never null.
        assertEquals(List.of(), Beds.layoutOf("kingdoms:nothing_like_this"));
        assertEquals(List.of(), Beds.layoutOf(null));
        assertEquals(0, Beds.countIn("kingdoms:granary"));
    }

    // --- indoors, and one bed to a cell --------------------------------------

    @Test
    void everyBedIsIndoorsAndNoTwoShareACell() {
        for (BuildingType home : homes()) {
            String path = pathOf(home);
            BuildingSizes.Size size = BuildingSizes.of(home.id());
            assertNotNull(size, path + " houses people and has no declared size");
            int rx = size.width() / 2;
            int rz = size.depth() / 2;
            Set<String> taken = new HashSet<>();

            for (Beds.Slot slot : Beds.layoutOf(home.id())) {
                for (int[] cell : new int[][]{
                        {slot.dx(), slot.dz()}, {slot.headX(), slot.headZ()}}) {
                    int dx = cell[0];
                    int dz = cell[1];
                    // Strictly inside the wall ring: a cell at |dx| == rx is the
                    // wall itself, and a bed half laid in a wall is a hole in it.
                    assertTrue(Math.abs(dx) < rx && Math.abs(dz) < rz,
                            path + " lays half a bed at " + dx + "," + dz
                                    + ", which is in its own wall (" + size.width()
                                    + "x" + size.depth() + ")");
                    assertTrue(size.covers(dx, dz),
                            path + " lays half a bed at " + dx + "," + dz
                                    + ", in the corner its own notch cuts away");
                    assertTrue(taken.add(dx + "," + dz),
                            path + " lays two bed halves in the same cell "
                                    + dx + "," + dz);
                }
            }
        }
    }

    @Test
    void noBedStandsWhereTheShapePutsSomethingElse() {
        // The lantern every cabin and hall hangs at the middle of its own floor,
        // and the post that names the building. Both are drawn at the furniture
        // course, and a bed half sharing a cell with either is one of the two
        // quietly missing from the finished building.
        //
        // Cottage, bunkhouse and longhouse stand their post at (0,-1); the house
        // stands it at (-1,-1) and the croft at (0,3).
        assertNoBedAt("cottage", 0, 0);
        assertNoBedAt("cottage", 0, -1);
        assertNoBedAt("house", 0, 0);
        assertNoBedAt("house", -1, -1);
        assertNoBedAt("longhouse", 0, 0);
        assertNoBedAt("longhouse", 0, -1);
        assertNoBedAt("croft", 0, 0);
        assertNoBedAt("croft", 0, 3);
        assertNoBedAt("bunkhouse", 0, 0);
        assertNoBedAt("bunkhouse", 0, -1);
    }

    private static void assertNoBedAt(String path, int dx, int dz) {
        for (Beds.Slot slot : Beds.layoutOf("kingdoms:" + path)) {
            assertFalse(slot.dx() == dx && slot.dz() == dz,
                    path + " lays a bed's foot at " + dx + "," + dz);
            assertFalse(slot.headX() == dx && slot.headZ() == dz,
                    path + " lays a bed's head at " + dx + "," + dz);
        }
    }

    // --- the quarter turns ---------------------------------------------------

    @Test
    void aBedTurnsWithTheBuildingAndItsHeadTurnsWithIt() {
        // The whole of why turning is a function. A bed swung round without its
        // head swinging too is two blocks of furniture pointing at each other,
        // and the pair pops.
        Beds.Slot laid = new Beds.Slot(2, -3, 0, -1);

        assertEquals(new Beds.Slot(2, -3, 0, -1), Beds.turned(laid, 0));
        assertEquals(new Beds.Slot(3, 2, 1, 0), Beds.turned(laid, 1));
        assertEquals(new Beds.Slot(-2, 3, 0, 1), Beds.turned(laid, 2));
        assertEquals(new Beds.Slot(-3, -2, -1, 0), Beds.turned(laid, 3));
    }

    @Test
    void fourQuarterTurnsComeBackToWhereItStarted() {
        for (BuildingType home : homes()) {
            for (Beds.Slot slot : Beds.layoutOf(home.id())) {
                Beds.Slot round = slot;
                for (int turn = 0; turn < 4; turn++) {
                    round = Beds.turned(round, 1);
                }
                assertEquals(slot, round, pathOf(home) + " does not come back round");
            }
        }
    }

    @Test
    void aTurnedBedKeepsItsHeadOneBlockFromItsFoot() {
        for (BuildingType home : homes()) {
            for (int facing = 0; facing < 4; facing++) {
                for (Beds.Slot slot : Beds.bedsOf(home.id(), facing)) {
                    int span = Math.abs(slot.headX() - slot.dx())
                            + Math.abs(slot.headZ() - slot.dz());
                    assertEquals(1, span, pathOf(home) + " at facing " + facing
                            + " has a bed whose halves are not neighbors");
                }
            }
        }
    }

    @Test
    void everyBedOfEveryHomeHasAPlaceAtEveryFacing() {
        SimPos origin = new SimPos(100, 64, -40);
        for (BuildingType home : homes()) {
            for (int facing = 0; facing < 4; facing++) {
                Set<SimPos> cells = new HashSet<>();
                for (int index = 0; index < Beds.countIn(home.id()); index++) {
                    SimPos foot = Beds.footOf(home.id(), origin, facing, index);
                    SimPos head = Beds.headOf(home.id(), origin, facing, index);
                    assertNotNull(foot);
                    assertNotNull(head);
                    assertEquals(origin.y() + Beds.FLOOR_COURSE, foot.y(),
                            "a bed stands on the floor course, one above the base");
                    assertEquals(origin.y() + Beds.FLOOR_COURSE, head.y());
                    assertTrue(cells.add(foot), pathOf(home) + " doubles a foot up");
                    assertTrue(cells.add(head), pathOf(home) + " doubles a head up");
                }
                assertNull(Beds.footOf(home.id(), origin, facing,
                                Beds.countIn(home.id())),
                        "there is no bed past the last one");
                assertNull(Beds.footOf(home.id(), origin, facing, -1));
            }
        }
    }

    // --- who sleeps where ----------------------------------------------------

    /** Capacity three and the bed table's cottage: a home for a small family. */
    private static final BuildingType COTTAGE = catalogEntry("kingdoms:cottage");

    private static BuildingType catalogEntry(String id) {
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        throw new IllegalStateException("no " + id + " in the catalog");
    }

    /**
     * A town with one cottage and {@code heads} people living in it.
     *
     * <p>Ids are fixed rather than random so the expected ranking can be written
     * down: the order is by identity, which is the only order that survives a
     * save and a reload.
     */
    private static Settlement townOf(int heads) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Bedford", new SimPos(0, 64, 0), 64);
        town.setCatalog(List.of(COTTAGE));
        town.addBuilding(new Building(COTTAGE.id(), new SimPos(8, 64, 8), 0, true));

        Household family = new Household(Household.Id.random(), "Sleeper");
        family.setHome(new SimPos(8, 64, 8));
        for (int head = 0; head < heads; head++) {
            Person person = new Person(
                    new Person.Id(UUID.nameUUIDFromBytes(("head" + head).getBytes())),
                    "Head " + head, Profession.FARMER, town.center());
            town.addResident(person);
            family.addMember(person.id());
        }
        town.addHousehold(family);
        return town;
    }

    @Test
    void everybodyUnderTheRoofGetsOneBedAndNobodyShares() {
        Settlement town = townOf(3);
        Set<Integer> taken = new HashSet<>();
        Set<SimPos> beds = new HashSet<>();

        for (Person person : town.residents()) {
            int index = Beds.indexOf(town, person);
            assertTrue(index >= 0, person.name() + " lives in a cottage and has no bed");
            assertTrue(taken.add(index), "two people were given bed " + index);
            assertTrue(beds.add(Beds.bedFor(town, person)), "two people, one mattress");
        }
        assertEquals(3, taken.size());
    }

    @Test
    void theSameQuestionGetsTheSameAnswerEveryTime() {
        // The claim that lets this stay out of the save. Asked again on a freshly
        // built town of the same people, the ranking must be identical -- so an
        // old world's settlers wake up in the beds they went to sleep in.
        Settlement first = townOf(3);
        Settlement again = townOf(3);

        for (Person person : first.residents()) {
            Person same = again.resident(person.id());
            assertNotNull(same);
            assertEquals(Beds.indexOf(first, person), Beds.indexOf(again, same),
                    person.name() + " changed beds between two identical towns");
            assertEquals(Beds.bedFor(first, person), Beds.bedFor(again, same));
        }
    }

    @Test
    void aHouseMoreCrowdedThanItsBedsPutsTheExtraHeadsOnTheFloor() {
        // Five in a cottage that has three beds. Three get one; the other two get
        // nothing rather than somebody else's -- a bed hunt across the village is
        // a night spent walking.
        Settlement town = townOf(5);
        int withABed = 0;
        for (Person person : town.residents()) {
            if (Beds.indexOf(town, person) >= 0) {
                withABed++;
            } else {
                assertNull(Beds.bedFor(town, person));
            }
        }
        assertEquals(3, withABed, "a cottage sleeps three however many live in it");
    }

    @Test
    void somebodyWithNoHomeHasNoBed() {
        Settlement town = townOf(1);
        Person drifter = new Person(Person.Id.random(), "Drifter",
                Profession.IDLER, town.center());
        town.addResident(drifter);

        assertEquals(-1, Beds.indexOf(town, drifter));
        assertNull(Beds.bedFor(town, drifter),
                "an idler sleeps at the center, as they always did");
    }

    @Test
    void aFamilyHousedWhereNothingStandsHasNoBed() {
        // A household whose home has no building record: the case that crashed
        // the tick once already. Asking for a bed must not be the next thing that
        // does.
        Settlement town = townOf(1);
        Household ghosts = new Household(Household.Id.random(), "Nobody");
        ghosts.setHome(new SimPos(500, 64, 500));
        Person orphan = new Person(Person.Id.random(), "Orphan",
                Profession.IDLER, town.center());
        town.addResident(orphan);
        ghosts.addMember(orphan.id());
        town.addHousehold(ghosts);

        assertNull(Beds.bedFor(town, orphan));
    }

    @Test
    void aBedIsInsideTheBuildingItBelongsTo() {
        // The arithmetic joined up: the address a body is walked to is the home's
        // own origin plus an offset the walls contain.
        Settlement town = townOf(1);
        Building cottage = town.buildingAt(new SimPos(8, 64, 8));
        BuildingSizes.Size size = BuildingSizes.of(COTTAGE.id());
        Person only = town.residents().iterator().next();
        SimPos bed = Beds.bedFor(town, only);

        assertNotNull(bed);
        assertTrue(Math.abs(bed.x() - cottage.origin().x()) < size.width() / 2);
        assertTrue(Math.abs(bed.z() - cottage.origin().z()) < size.depth() / 2);
        assertEquals(cottage.origin().y() + Beds.FLOOR_COURSE, bed.y());
    }
}
