package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.Sighting;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers hostile pressure: scheduling, resolution, casualties, and the evidence trail. */
class RaidPlannerTest {

    private static final BuildingType TOWER = new BuildingType("test:tower", 10, 1, 0, 0, 60, 0, 3);
    private static final List<BuildingType> CATALOGUE = List.of(TOWER);

    /** Bridge that records raid spawns and reports a configurable world state. */
    private static final class WarBridge implements WorldBridge {
        boolean playerNearby = false;
        int hostiles = 0;
        final List<Integer> spawnedRaids = new ArrayList<>();

        @Override public boolean playerWithin(SimPos pos, double radius) { return playerNearby; }
        @Override public boolean isLoaded(SimPos pos) { return true; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin, boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
        // Plain zombies: one danger apiece, so the count and the weight agree.
        @Override public Sighting hostilesSeen(SimPos centre, double radius) { return new Sighting(hostiles, hostiles); }
        @Override public void spawnHostiles(int count, SimPos around) { spawnedRaids.add(count); }
    }

    /** Fixed id so raid hashes are identical on every run. */
    private static Settlement settlement(int guards, int civilians, int towers) {
        Settlement s = new Settlement(
                new Settlement.Id(new UUID(1234L, 5678L)), "Testburg", new SimPos(0, 64, 0), 64);
        s.setCatalogue(CATALOGUE);
        for (int i = 0; i < guards; i++) {
            s.addResident(new Person(Person.Id.random(), "Guard " + i, Profession.GUARD, new SimPos(0, 64, 0)));
        }
        for (int i = 0; i < civilians; i++) {
            s.addResident(new Person(Person.Id.random(), "Farmer " + i, Profession.FARMER, new SimPos(0, 64, 0)));
        }
        for (int i = 0; i < towers; i++) {
            s.addBuilding(new Building(TOWER.id(), new SimPos(10 + i, 64, 0), 0, true));
        }
        return s;
    }

    private static SimContext ctx(WarBridge bridge, long step) {
        return new SimContext(bridge, step, SimSettings.DEFAULTS);
    }

    // --- defense arithmetic ---

    @Test
    void defenseCountsGuardsAndStructures() {
        Settlement s = settlement(3, 0, 2);

        assertEquals(3 * RaidPlanner.GUARD_POWER + 2 * 3, RaidPlanner.defensePower(s),
                "three guards at 2 each plus two towers at 3 each");
    }

    // --- scheduling ---

    @Test
    void raidsFireExactlyOncePerInterval() {
        Settlement s = settlement(2, 6, 0);
        WarBridge bridge = new WarBridge();

        int fires = 0;
        for (long step = 0; step < SimSettings.DEFAULT_RAID_INTERVAL_STEPS; step++) {
            if (RaidPlanner.raidDue(s, ctx(bridge, step))) {
                fires++;
            }
        }
        assertEquals(1, fires, "one raid per settlement per interval, on its own clock");
    }

    @Test
    void strengthIsDeterministicPerStep() {
        Settlement s = settlement(2, 6, 0);

        assertEquals(RaidPlanner.raidStrength(s, 42), RaidPlanner.raidStrength(s, 42),
                "same settlement and step must always produce the same raid");
    }

    @Test
    void smallSettlementsAreBeneathNotice() {
        Settlement s = settlement(0, RaidPlanner.MIN_POPULATION_FOR_RAIDS - 1, 0);
        WarBridge bridge = new WarBridge();

        for (long step = 0; step < SimSettings.DEFAULT_RAID_INTERVAL_STEPS * 2; step++) {
            RaidPlanner.advance(s, ctx(bridge, step));
        }
        assertEquals(RaidPlanner.MIN_POPULATION_FOR_RAIDS - 1, s.population(), "nobody lost");
        assertTrue(s.events().stream().noneMatch(e ->
                        e.message().contains("Raid") || e.message().contains("sighted")),
                "no raid should ever have fired");
    }

    // --- unobserved resolution ---

    @Test
    void garrisonRepelsWeakRaid() {
        Settlement s = settlement(3, 5, 1);   // defense 9
        WarBridge bridge = new WarBridge();

        RaidPlanner.execute(s, ctx(bridge, 10), 7);

        assertEquals(8, s.population(), "a repelled raid costs nothing");
        assertEquals(7, s.threatLevel(), "but the town is on alert");
        assertEquals(1, s.events().size());
        assertTrue(s.events().getFirst().message().contains("repelled"));
    }

    @Test
    void undefendedTownBleeds() {
        Settlement s = settlement(0, 8, 0);   // defense 0
        WarBridge bridge = new WarBridge();

        RaidPlanner.execute(s, ctx(bridge, 10), 3);

        assertEquals(5, s.population(), "deficit of 3 means 3 lost");
        assertTrue(s.events().getFirst().message().contains("overran"));
    }

    @Test
    void guardsFallFirst() {
        Settlement s = settlement(1, 5, 0);   // defense 2
        WarBridge bridge = new WarBridge();

        RaidPlanner.execute(s, ctx(bridge, 10), 4);   // deficit 2: the guard and one farmer

        assertEquals(4, s.population());
        assertEquals(0, s.residents().stream().filter(p -> p.profession() == Profession.GUARD).count(),
                "the guard is the line, and the line broke");
    }

    @Test
    void casualtiesUpdateFamilies() {
        Settlement s = settlement(0, 8, 0);
        // Group everyone into families first, as a real step would have.
        s.step(new SimContext(new WarBridge(), 0, SimSettings.SANDBOX));

        RaidPlanner.execute(s, ctx(new WarBridge(), 10), 2);

        int inFamilies = s.households().stream().mapToInt(h -> h.size()).sum();
        assertEquals(s.population(), inFamilies, "the fallen must leave their households too");
    }

    @Test
    void embodiedPeopleAreNeverKilledInvisibly() {
        Settlement s = settlement(0, 6, 0);
        s.residents().forEach(p -> p.setEmbodied(true));
        WarBridge bridge = new WarBridge();

        RaidPlanner.execute(s, ctx(bridge, 10), 4);

        assertEquals(6, s.population(),
                "someone a player can currently see must never vanish to arithmetic");
    }

    // --- observed resolution ---

    @Test
    void observedRaidSpawnsRealHostilesInstead() {
        Settlement s = settlement(2, 6, 0);
        WarBridge bridge = new WarBridge();
        bridge.playerNearby = true;

        RaidPlanner.execute(s, ctx(bridge, 10), 5);

        assertEquals(List.of(5), bridge.spawnedRaids, "the raid becomes real entities");
        assertEquals(8, s.population(), "no statistical deaths while someone is watching");
        assertTrue(s.events().getFirst().message().contains("sighted"));
    }

    // --- threat tracking ---

    @Test
    void threatTracksWhatTheTownCanSee() {
        // Three zombies on purpose: that is below the bell floor, so no watch
        // however thin can ring about it, and what is measured here is the
        // tracking rather than the panic on top of it.
        Settlement s = settlement(1, 2, 0);
        WarBridge bridge = new WarBridge();
        bridge.hostiles = 3;

        s.step(new SimContext(bridge, 0, SimSettings.SANDBOX));
        assertEquals(3, s.threatLevel(), "threat mirrors what the town can see");
    }

    @Test
    void aTownDoesNotForgetTheMomentAMobStepsBehindAHill() {
        // Read fresh every step, a hostile using cover would clear the alarm and
        // raise it again on alternate steps. A town that has seen something does
        // not forget it that fast.
        // Three zombies on purpose: that is below the bell floor, so no watch
        // however thin can ring about it, and what is measured here is the
        // tracking rather than the panic on top of it.
        Settlement s = settlement(1, 2, 0);
        WarBridge bridge = new WarBridge();
        bridge.hostiles = 3;
        s.step(new SimContext(bridge, 0, SimSettings.SANDBOX));

        bridge.hostiles = 0;
        s.step(new SimContext(bridge, 1, SimSettings.SANDBOX));

        assertEquals(3, s.threatLevel(), "still believed, and still true for all the town knows");
        assertTrue(s.remembersSighting());
    }

    @Test
    void andThenItStandsDown() {
        // Three zombies on purpose: that is below the bell floor, so no watch
        // however thin can ring about it, and what is measured here is the
        // tracking rather than the panic on top of it.
        Settlement s = settlement(1, 2, 0);
        WarBridge bridge = new WarBridge();
        bridge.hostiles = 3;
        s.step(new SimContext(bridge, 0, SimSettings.SANDBOX));

        bridge.hostiles = 0;
        // Out of sight for longer than the town's memory, then a step to fall.
        for (int step = 1; step <= Settlement.SIGHTING_MEMORY_STEPS + 1; step++) {
            s.step(new SimContext(bridge, step, SimSettings.SANDBOX));
        }

        assertFalse(s.remembersSighting(), "the memory has run out");
        assertEquals(2, s.threatLevel(), "and the alarm has started to fall");
    }

    // --- the evidence trail ---

    @Test
    void historyIsBounded() {
        Settlement s = settlement(0, 0, 0);
        for (int i = 0; i < Settlement.MAX_EVENTS + 15; i++) {
            s.logEvent(i, "event " + i);
        }

        assertEquals(Settlement.MAX_EVENTS, s.events().size());
        assertEquals("event 15", s.events().getFirst().message(), "oldest entries fall off");
    }
}
