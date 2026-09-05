package com.kingdoms.neoforge.world;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingSizes;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Telling a building that has been knocked down from one that has been knocked
 * about, and from one that was never drawn at all.
 *
 * <p>Three states look similar in a fault list and mean entirely different
 * things. A creeper hole is damage and the town repairs it. A record with
 * nothing on the ground is a building waiting to be painted in. Only the third —
 * a shell that has gone — is a building the town should stop believing in, and
 * getting the line between them wrong in either direction is expensive: leave it
 * too high and a cottage that is a crater goes on housing a family; set it too
 * low and the town evicts people from houses that are standing.
 */
class TownDemolitionTest {

    private static final int FLOOR = 64;

    /** Span 7 gives wall half-extents of 2, so the ring sits at x,z = +/-2. */
    private static final int SPAN = 7;
    private static final int HALF = 2;

    @BeforeEach
    void startWithNoMemory() {
        TownAuditor.forget();
    }

    private static Settlement town() {
        return new Settlement(Settlement.Id.random(), "Testburg", new SimPos(0, FLOOR, 0), 64);
    }

    private static Building house() {
        Building house = new Building("kingdoms:house", new SimPos(0, FLOOR, 0), 1, true);
        house.setFootprint(new Footprint(FLOOR, SPAN, SPAN, 4));
        return house;
    }

    /** A walled house on a flat plain, with the ground reading level with the floor. */
    private static FakeWorld standing() {
        return new FakeWorld(FLOOR + 1).plain(FLOOR, 24).walls(HALF, FLOOR);
    }

    /** The same plain, with every course of the house taken off it. */
    private static FakeWorld flattened() {
        return new FakeWorld(FLOOR + 1).plain(FLOOR, 24);
    }

    private static List<String> faultsOf(FakeWorld world, Settlement settlement) {
        return TownAuditor.audit(world, settlement).stream()
                .map(TownAuditor.Fault::describe)
                .toList();
    }

    private static boolean reportsMostlyGone(FakeWorld world, Settlement settlement) {
        return faultsOf(world, settlement).stream().anyMatch(f -> f.contains("mostly gone"));
    }

    // --- what counts as gone ---

    @Test
    void aHouseWhoseWallsAreGoneIsReported() {
        // The whole point, and note the order: the auditor has to see the house
        // standing before it is entitled to an opinion about it having gone.
        Settlement town = town();
        Building house = house();
        town.addBuilding(house);
        TownAuditor.audit(standing(), town);

        assertTrue(reportsMostlyGone(flattened(), town),
                "no walls at all is not damage, it is a crater");
    }

    @Test
    void aHouseMissingOnlyItsDoorIsNotReported() {
        // One column of the sixteen this fixture's ring has -- a real cottage
        // is nine across once its apron is counted and so has twenty-four. If
        // this reads as demolition then every house in every town is
        // demolished, because every house has a door.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        assertFalse(reportsMostlyGone(standing().doorway(HALF, 0, FLOOR), town),
                "a doorway is a hole somebody put there on purpose");
    }

    @Test
    void aCreeperHoleInOneWallIsDamageRatherThanDemolition() {
        // The case the threshold exists to get right. A blast takes a few
        // columns out of one side; the town repairs that, and a town that tore
        // the house down instead would be unplayable near a creeper.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        FakeWorld holed = standing();
        for (int dz = -1; dz <= 1; dz++) {
            holed.doorway(HALF, dz, FLOOR);
        }

        assertFalse(reportsMostlyGone(holed, town),
                "three columns of sixteen is a hole in a wall, not a missing house");
    }

    @Test
    void aWholeWallBlownOutIsStillABuilding() {
        // Five of sixteen -- seven of twenty-four on a real cottage -- which is
        // the loudest thing that must not trip this: a house open to the
        // weather down one whole side is exactly what RepairPlanner exists for.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        FakeWorld openSided = standing();
        for (int dx = -HALF; dx <= HALF; dx++) {
            openSided.doorway(dx, HALF, FLOOR);
        }

        assertFalse(reportsMostlyGone(openSided, town), "there is still a house here to mend");
    }

    // --- things that never had walls ---

    @Test
    void aFieldIsNotAHouseWithItsWallsMissing() {
        // A field is a one-block fence round tilled soil, so its wall ring reads
        // as empty on the day it is finished and every day after. Without the
        // gate on having been seen standing, every farm in the mod would be
        // written off three sweeps after it was built.
        Settlement town = town();
        Building farm = new Building("kingdoms:farm", new SimPos(0, FLOOR, 0), 1, true);
        farm.setFootprint(new Footprint(FLOOR, SPAN, SPAN, 4));
        town.addBuilding(farm);

        assertFalse(reportsMostlyGone(flattened(), town),
                "nothing here ever stood at head height; there is nothing to have lost");
        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF + 1; sweep++) {
            TownAuditor.demolishRuins(flattened(), town);
        }
        assertEquals(1, town.buildings().size(), "and no number of sweeps changes that");
    }

    @Test
    void aBuildingTheWorldNeverDrewIsUnbuiltRatherThanDemolished() {
        // Both faults are "the simulation says there is a building here and
        // there is not", and they call for opposite things: one wants drawing,
        // the other wants forgetting. Keeping them apart is the difference
        // between a town that finishes building and a town that deletes its own
        // half-finished work.
        Settlement town = town();
        Building pending = new Building("kingdoms:house", new SimPos(0, FLOOR, 0), 1, false);
        pending.setFootprint(new Footprint(FLOOR, SPAN, SPAN, 4));
        town.addBuilding(pending);

        TownAuditor.audit(flattened(), town);
        List<String> second = faultsOf(flattened(), town);

        assertTrue(second.stream().anyMatch(f -> f.contains("nothing stands on the ground")),
                "it is reported, and reported as never drawn");
        assertTrue(second.stream().noneMatch(f -> f.contains("mostly gone")),
                "a building that was never raised has not been demolished");

        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF + 1; sweep++) {
            TownAuditor.demolishRuins(flattened(), town);
        }
        assertEquals(1, town.buildings().size(),
                "and the town keeps the record it is still waiting to draw");
    }

    // --- the yard in the crook of an L ---

    /**
     * A croft as the placer reports one: thirteen by eleven with a corner cut
     * away, the notch already narrowed by the apron.
     */
    private static Building croft() {
        Building croft = new Building("kingdoms:croft", new SimPos(0, FLOOR, 0), 1, true);
        croft.setFootprint(new Footprint(FLOOR, 13, 11, 4,
                new BuildingSizes.Notch(5, 3, 1, -1)));
        return croft;
    }

    @Test
    void aCroftsYardIsNotCountedAsMissingWall() {
        // The ring of a thirteen by eleven footprint is thirty-six columns, five
        // of which fall in the yard and have never had a wall in them. Counting
        // those as wall the croft has lost puts every reading about three points
        // low — so the number in the report is wrong, and near the line so is
        // the verdict.
        //
        // Four columns of the west wall left standing: 4 of the 31 real ones is
        // 12%, and 4 of all 36 would be 11%. The croft is a ruin either way; the
        // point is that the auditor says what it actually measured.
        Settlement town = town();
        town.addBuilding(croft());
        // Oblong rather than square, so the ring the auditor reads is the one
        // this fixture actually walls: half-extents of five and four.
        FakeWorld whole = new FakeWorld(FLOOR + 1).plain(FLOOR, 24);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) == 5 || Math.abs(dz) == 4) {
                    whole.solid(dx, FLOOR + 1, dz).solid(dx, FLOOR + 2, dz);
                }
            }
        }
        TownAuditor.audit(whole, town);

        FakeWorld stumps = new FakeWorld(FLOOR + 1).plain(FLOOR, 24);
        for (int dz : new int[]{-4, -2, 0, 2}) {
            stumps.solid(-5, FLOOR + 1, dz).solid(-5, FLOOR + 2, dz);
        }

        assertTrue(faultsOf(stumps, town).stream()
                        .anyMatch(f -> f.contains("mostly gone — 12% of its walls still standing")),
                "twelve per cent of the walls a croft has, not eleven of a rectangle "
                        + "it never was: " + faultsOf(stumps, town));
    }

    // --- the clock ---

    @Test
    void nothingIsWrittenOffBeforeTheCountIsIn() {
        // A player rebuilding a house by hand, or a town part-way through its own
        // repair, looks exactly like a ruin for as long as the walls are down.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF - 1; sweep++) {
            assertTrue(TownAuditor.demolishRuins(flattened(), town).isEmpty(),
                    "sweep " + sweep + " is not enough evidence on its own");
        }
        assertEquals(1, town.buildings().size(), "still on the books");
    }

    @Test
    void theSweepAfterThatTakesItOffTheBooks() {
        Settlement town = town();
        Building house = house();
        town.addBuilding(house);
        TownAuditor.audit(standing(), town);

        List<Building> razed = List.of();
        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF; sweep++) {
            razed = TownAuditor.demolishRuins(flattened(), town);
        }

        assertEquals(List.of(house), razed, "the sweep says what it took down");
        assertTrue(town.buildings().isEmpty(), "and the town no longer has a house");
        assertTrue(town.events().stream().anyMatch(e -> e.message().contains("is gone")),
                "with a line in its own history saying so: " + town.events());
    }

    @Test
    void theCountStartsAgainWhenTheWallsGoBackUp() {
        // Somebody rebuilt it. The town must not go on counting toward a
        // demolition that has been undone.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        TownAuditor.demolishRuins(flattened(), town);
        TownAuditor.demolishRuins(flattened(), town);
        TownAuditor.demolishRuins(standing(), town);
        TownAuditor.demolishRuins(flattened(), town);

        assertEquals(1, town.buildings().size(),
                "one ruined sweep since the rebuild is not three");
    }

    @Test
    void groundNobodyIsLookingAtIsNoEvidenceEitherWay() {
        // The same rule the undrawn check follows: a building nobody has walked
        // past for an hour has not been a ruin for an hour, because there was no
        // sweep to see it.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        TownAuditor.demolishRuins(flattened(), town);
        TownAuditor.demolishRuins(flattened(), town);
        FakeWorld away = flattened();
        away.unloaded(0, FLOOR, 0);
        TownAuditor.demolishRuins(away, town);
        TownAuditor.demolishRuins(flattened(), town);

        assertEquals(1, town.buildings().size(),
                "the count starts again from the first sweep that could see it");
    }

    @Test
    void halfARingIsNoBetterThanNoRing() {
        // A building straddling the edge of the loaded area, with its standing
        // half away and its blown-out half in view, would read as a ruin on
        // every sweep and be written off with most of it still up. The whole
        // ring or none of it.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        // Nothing standing anywhere the sweep can see, and the west side of the
        // ring in an unloaded chunk. Read over the columns it could reach this
        // is nought per cent; read honestly it is not a reading at all. The ring
        // is walked at the floor line, which is the y these have to be unloaded
        // at for the fixture to mean what it says.
        FakeWorld halfSeen = flattened();
        for (int dz = -HALF; dz <= HALF; dz++) {
            halfSeen.unloaded(-HALF, FLOOR, dz);
        }

        assertFalse(reportsMostlyGone(halfSeen, town), "half a count is not a verdict");
        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF + 1; sweep++) {
            TownAuditor.demolishRuins(halfSeen, town);
        }
        assertEquals(1, town.buildings().size(), "however many times it is asked");
    }

    // --- the crew standing in the ruin ---

    /**
     * A town with somebody who can hold a trowel.
     *
     * <p>The shield a booked repair gives a ruin is only good while the town could
     * still work it, so a settlement with nobody in it would test the wrong half
     * of the rule.
     */
    private static Settlement withBuilder() {
        Settlement town = town();
        town.addResident(new Person(
                Person.Id.random(), "Alder", Profession.BUILDER, town.center()));
        return town;
    }

    /** The repair {@code RepairPlanner} books: work on the plot, marked a repair. */
    private static BuildTask repairOf(Building building) {
        BuildTask mending = new BuildTask(building.blueprintId(), building.origin(), 40);
        mending.setUpgradeOf(building.origin());
        mending.setRepair(true);
        return mending;
    }

    @Test
    void aBuildingUnderRepairIsNotWrittenOffUnderItsOwnBuilders() {
        // The interlock a slow repair needs. A repair used to be a re-stamp of the
        // whole blueprint and was over in a second, so no sweep could catch one
        // half done; a crew fetching loads and laying blocks one at a time takes
        // minutes, and a shell reads as gone for every one of them. Without this
        // the town writes off the building its own builders are standing in,
        // evicts the family, and cancels the repair on the way past.
        Settlement town = withBuilder();
        Building house = house();
        town.addBuilding(house);
        town.enqueueUrgent(repairOf(house));
        TownAuditor.audit(standing(), town);

        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF + 3; sweep++) {
            assertTrue(TownAuditor.demolishRuins(flattened(), town).isEmpty(),
                    "sweep " + sweep + " found work booked here");
        }

        assertEquals(1, town.buildings().size(), "the house is still on the books");
        assertEquals(1, town.buildQueue().size(), "and so is the job to put it right");
    }

    @Test
    void aRepairWithNobodyLeftToWorkItStopsSparingTheRuin() {
        // Booking is a decision taken once and a town changes. A raid damages a
        // house, the repair is booked while a builder is alive, the next raid
        // kills him — and the job sits at the head of a queue nothing can advance,
        // shielding a ruin nobody will ever touch and stopping the town ordering
        // anything else for the rest of the world's life. So the crew is asked for
        // again on every sweep, the shell goes, and taking it off the books is
        // what clears the job.
        Settlement town = withBuilder();
        Building house = house();
        town.addBuilding(house);
        town.enqueueUrgent(repairOf(house));
        TownAuditor.audit(standing(), town);

        assertTrue(TownAuditor.demolishRuins(flattened(), town).isEmpty(),
                "while somebody can work it, it is spared");
        town.removePerson(town.residents().iterator().next().id());

        List<Building> razed = List.of();
        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF; sweep++) {
            razed = TownAuditor.demolishRuins(flattened(), town);
        }

        assertEquals(List.of(house), razed, "with nobody to send, it is a ruin");
        assertTrue(town.buildQueue().isEmpty(),
                "and the queue is freed with it, rather than pinned forever");
    }

    @Test
    void aRuinNobodyIsMendingIsStillWrittenOff() {
        // The other half, and it is what stops the mercy above swallowing the
        // whole check. RepairPlanner refuses to book a repair the town cannot
        // begin — nobody fit to send, or nothing on the shelves to send them with
        // — so a ruin with no task against it is a ruin the town is not repairing
        // and is not going to.
        Settlement town = town();
        Building house = house();
        town.addBuilding(house);
        TownAuditor.audit(standing(), town);

        List<Building> razed = List.of();
        for (int sweep = 0; sweep < TownAuditor.SWEEPS_BEFORE_WRITTEN_OFF; sweep++) {
            razed = TownAuditor.demolishRuins(flattened(), town);
        }

        assertEquals(List.of(house), razed, "nothing was booked, so nothing was spared");
    }

    @Test
    void theSweepCountStartsAgainAfterTheRepairIsDone() {
        // Sweeps spent under a repair are not evidence of anything: the shell was
        // meant to be down. A building whose repair finished and which is still a
        // ruin gets its full three sweeps from a standing start.
        Settlement town = withBuilder();
        Building house = house();
        town.addBuilding(house);
        town.enqueueUrgent(repairOf(house));
        TownAuditor.audit(standing(), town);

        for (int sweep = 0; sweep < 5; sweep++) {
            TownAuditor.demolishRuins(flattened(), town);
        }
        // Off the queue, however it left it: finished, or given up on. All the
        // sweep count knows is that nobody is booked here any more.
        town.abandonBuild(0, "the crew is done");

        assertTrue(TownAuditor.demolishRuins(flattened(), town).isEmpty(),
                "the first sweep after the crew leaves is the first sweep");
        assertTrue(TownAuditor.demolishRuins(flattened(), town).isEmpty());
        assertEquals(1, TownAuditor.demolishRuins(flattened(), town).size(),
                "and the third takes it off the books");
    }

    @Test
    void askingForAReportNeverKnocksAnythingDown() {
        // /civ audit is a report, and a player who types it twenty times must
        // not thereby demolish the town they were asking about.
        Settlement town = town();
        town.addBuilding(house());
        TownAuditor.audit(standing(), town);

        for (int asked = 0; asked < 20; asked++) {
            TownAuditor.audit(flattened(), town);
        }

        assertEquals(1, town.buildings().size(), "the audit reads, it does not raze");
    }
}
