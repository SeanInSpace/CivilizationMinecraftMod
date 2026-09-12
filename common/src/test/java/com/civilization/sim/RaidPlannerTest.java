package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.Sighting;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.RaidPlanner;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
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
    private static final List<BuildingType> CATALOG = List.of(TOWER);

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
        @Override public Sighting hostilesSeen(SimPos center, double radius) { return new Sighting(hostiles, hostiles); }
        @Override public void spawnHostiles(int count, SimPos around) { spawnedRaids.add(count); }
    }

    /** Fixed id so raid hashes are identical on every run. */
    private static Settlement settlement(int guards, int civilians, int towers) {
        Settlement s = new Settlement(
                new Settlement.Id(new UUID(1234L, 5678L)), "Testburg", new SimPos(0, 64, 0), 64);
        s.setCatalog(CATALOG);
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

    // --- a town too new to have been noticed ---

    /**
     * The whole of F1 in one claim: the town the world stood before you looked
     * at it keeps its people.
     *
     * <p>Nine villages of twelve are raised around the world spawn before the
     * first tick, and unwatched they were losing an average of 3.75 people to
     * raids by step 200 and 8.42 by step 400 — a founding population every five
     * hundred steps, to arithmetic, before anybody had met them.
     * {@code "Raid of 4 overran the defenses (2) — 2 lost: Ada Baker, Bren
     * Baker"} at step eight is the line this test exists to make impossible.
     */
    @Test
    void aNewTownIsNotRaidedAtAll() {
        Settlement s = settlement(1, 11, 0);
        WarBridge bridge = new WarBridge();
        // One real step to stamp the birthday, exactly as a world does, then the
        // raid pass alone — so what is measured is the raid clock rather than
        // whether a test town with one tower in its catalog can feed itself.
        s.step(new SimContext(bridge, 0, SimSettings.DEFAULTS));
        for (long step = 1; step < RaidPlanner.RAID_GRACE_STEPS; step++) {
            RaidPlanner.advance(s, ctx(bridge, step));
        }

        assertEquals(0, s.firstStep(), "born on the step it first lived");
        assertTrue(s.events().stream().noneMatch(e -> e.message().contains("Raid")),
                "no raid of any strength within the grace");
        assertTrue(bridge.spawnedRaids.isEmpty(), "and none made real either");
    }

    @Test
    void theGraceIsCountedFromTheTownsOwnFirstStep() {
        // Founded late, as a daughter colony or a charter is: the grace runs
        // from when this town started living, not from when the world did.
        Settlement s = settlement(1, 11, 0);
        WarBridge bridge = new WarBridge();
        long born = 9000;
        s.step(new SimContext(bridge, born, SimSettings.DEFAULTS));
        for (long step = born + 1; step < born + RaidPlanner.RAID_GRACE_STEPS; step++) {
            RaidPlanner.advance(s, ctx(bridge, step));
        }

        assertEquals(born, s.firstStep(), "the birthday is the first step it took");
        assertTrue(s.events().stream().noneMatch(e -> e.message().contains("Raid")),
                "a town founded at step nine thousand gets the same start");
    }

    @Test
    void andTheGraceEnds() {
        Settlement s = settlement(1, 11, 0);
        WarBridge bridge = new WarBridge();
        s.setFirstStep(0);

        // Two intervals past the grace, so the town's own hashed offset has
        // certainly come round whatever it is.
        for (long step = 0;
                step < RaidPlanner.RAID_GRACE_STEPS
                        + 2L * SimSettings.DEFAULT_RAID_INTERVAL_STEPS;
                step++) {
            RaidPlanner.advance(s, ctx(bridge, step));
        }

        assertTrue(s.events().stream().anyMatch(e -> e.message().contains("Raid")),
                "a grace is a delay, not an exemption");
    }

    // --- the early cap ---

    @Test
    void anEarlyRaidIsHeldToWhatTheWatchCanTurnBack() {
        Settlement s = settlement(2, 10, 1);   // 2 guards, one tower worth 3
        s.setFirstStep(0);

        assertEquals(2 + 3 + 1, RaidPlanner.earlyStrengthCap(s, 10),
                "guards plus structures plus one");
        assertTrue(RaidPlanner.earlyStrengthCap(s, 10) <= RaidPlanner.defensePower(s),
                "and a capped raid against a town with a guard in it is repelled");
    }

    @Test
    void aTownWithNoGuardsAtAllIsOnlyScared() {
        Settlement s = settlement(0, 12, 0);   // defense 0
        s.setFirstStep(0);
        WarBridge bridge = new WarBridge();
        int cap = RaidPlanner.earlyStrengthCap(s, 10);

        assertEquals(1, cap, "nothing standing, nothing to add: one");
        RaidPlanner.execute(s, ctx(bridge, 10), cap);

        assertEquals(12, s.population(),
                "over the line by one, which is under the casualty margin");
        assertTrue(s.events().getFirst().message().contains("driven off"));
    }

    @Test
    void theCapLiftsWhenTheTownIsNoLongerNew() {
        Settlement s = settlement(1, 11, 0);
        s.setFirstStep(0);

        assertEquals(RaidPlanner.MAX_RAID_STRENGTH,
                RaidPlanner.earlyStrengthCap(s, RaidPlanner.EARLY_CAP_STEPS),
                "past the cap the raid is whatever the town has grown to deserve");
    }

    /**
     * The playtest's own case, on real ground, with nobody watching.
     *
     * <p>A village world generation stood before anybody looked at it, run four
     * hundred steps. Measured before any of this, over twelve trials, it lost an
     * average of 3.75 people to raids by step 200 and 8.42 by step 400 — worst
     * trial twelve, out of a founding population of twelve. The grace covers the
     * first two hundred outright; the early cap covers the two hundred after it,
     * because this town holds one guard and a defense of two until step 778 and
     * a capped raid against one guard is repelled by definition.
     */
    @Test
    void aSeededVillageLosesNobodyToRaidsInItsFirstFourHundredSteps() {
        Settlement town = Founding.seeded(new SimPos(0, 72, 0), "Millbrook",
                SettlementStage.VILLAGE, BuildCatalog.DEFAULT,
                "civilization:human/norman");
        TerrainFake ground = new TerrainFake(11);
        int lostByTwoHundred = 0;

        for (long step = 0; step < 400; step++) {
            town.step(new SimContext(ground, step, SimSettings.DEFAULTS));
            if (step == RaidPlanner.RAID_GRACE_STEPS - 1) {
                lostByTwoHundred = raidDeaths(town);
            }
        }

        assertEquals(0, lostByTwoHundred, "nobody lost to a raid in the first 200");
        assertEquals(0, raidDeaths(town),
                "nor in the 200 after it, which the early cap covers");
    }

    /** How many people the settlement's own history says raids have killed. */
    private static int raidDeaths(Settlement s) {
        int lost = 0;
        for (var event : s.events()) {
            int at = event.message().indexOf(" lost: ");
            if (event.message().contains("overran") && at >= 0) {
                lost += event.message().substring(at + 7).split(", ").length;
            }
        }
        return lost;
    }

    // --- the unwatched casualty margin ---

    @Test
    void aMarginOfOneCostsNobodyTheirLife() {
        Settlement s = settlement(1, 7, 0);   // defense 2
        WarBridge bridge = new WarBridge();

        RaidPlanner.execute(s, ctx(bridge, 10), 3);

        assertEquals(8, s.population(),
                "nobody dies to a die roll in a town nobody has ever visited");
        assertEquals(3, s.threatLevel(), "but the town knows what came for it");
        assertTrue(s.events().getFirst().message().contains("driven off"));
    }

    @Test
    void aMarginOfTwoStillKills() {
        Settlement s = settlement(1, 7, 0);   // defense 2
        WarBridge bridge = new WarBridge();

        RaidPlanner.execute(s, ctx(bridge, 10), 4);

        assertEquals(6, s.population(), "the deficit is still the toll");
        assertTrue(s.events().getFirst().message().contains("overran"));
    }

    @Test
    void theMarginIsOnlyForRaidsNobodyWatches() {
        // Watched, the raid becomes entities and the margin has no opinion:
        // whether anybody dies is decided by the fight, not by the subtraction.
        Settlement s = settlement(1, 7, 0);
        WarBridge bridge = new WarBridge();
        bridge.playerNearby = true;

        RaidPlanner.execute(s, ctx(bridge, 10), 3);

        assertEquals(List.of(3), bridge.spawnedRaids,
                "the raid is real however narrow the margin would have been");
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
