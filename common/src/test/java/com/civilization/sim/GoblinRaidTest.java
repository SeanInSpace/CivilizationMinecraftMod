package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.Sighting;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.GoblinRaids;
import com.civilization.sim.settlement.RaidPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Goblin camps going out, and what it costs both ends.
 *
 * <p>The raid arithmetic in reverse. Everything that decides <em>what a raid does
 * to a town</em> is {@code RaidPlanner}'s and is not re-tested here; what is
 * tested is that a camp sends a party at all, that the party's strength is a
 * number the town's own rules then apply, that stores move on a breakthrough and
 * do not move on a repel, and that a camp pays for the walk.
 *
 * <p>Two settlements in one test, which nothing else in this suite needs — see
 * {@link GoblinRaids}, which is why the pass runs from the world rather than from
 * a town.
 */
class GoblinRaidTest {

    /** A world nobody is watching, so every raid resolves as arithmetic. */
    private static final class Quiet implements WorldBridge {
        final List<String> spawnedFor = new ArrayList<>();
        boolean watched;

        @Override public boolean playerWithin(SimPos pos, double radius) {
            return watched;
        }

        @Override public boolean isLoaded(SimPos pos) {
            return true;
        }

        @Override public int surfaceHeight(SimPos pos) {
            return pos.y();
        }

        @Override public int groundHeight(SimPos pos) {
            return 72;
        }

        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }

        @Override public Sighting hostilesSeen(SimPos center, double radius) {
            return Sighting.NONE;
        }

        @Override public void spawnRaiders(int count, SimPos around, String cultureId) {
            spawnedFor.add(cultureId + " x" + count);
        }

        @Override public int forageableNear(SimPos center, int radius) {
            return 64;
        }

        @Override public void log(String message) {
        }
    }

    /**
     * A camp and a village, close enough to be neighbours.
     *
     * <p>Both are handed fixed ids so the going-out clock and the raid clock hash
     * the same way on every run — the property this whole suite leans on, and the
     * reason {@link GoblinRaids#dueOut} hashes an id rather than drawing a number.
     */
    private static Settlement camp(int goblins) {
        Settlement camp = Founding.seeded(new SimPos(0, 72, 0), "Gritmaw",
                SettlementStage.TOWN, BuildCatalog.DEFAULT, Culture.GOBLIN.id(),
                goblins, Culture.LAYOUT_GOBLIN_CAMP);
        return camp;
    }

    private static Settlement village(int people, int guards) {
        Settlement town = Founding.seeded(new SimPos(200, 72, 0), "Bellbrook",
                SettlementStage.VILLAGE, BuildCatalog.DEFAULT, Culture.NORMAN.id(), people);
        // The watch, named outright: what is being measured is what a raid does to
        // a given defense, and letting the staffing table decide it would make the
        // fixture's own numbers a moving target.
        int made = 0;
        for (Person person : town.residents()) {
            if (made >= guards) {
                break;
            }
            person.setProfession(Profession.GUARD);
            made++;
        }
        town.setStock(TownStores.FOOD, 400);
        town.setStock(TownStores.IRON, 80);
        town.setTreasury(400);
        return town;
    }

    /**
     * The sandbox, with raids switched back on.
     *
     * <p>{@code SimSettings.SANDBOX} turns them off, and for a good reason — a raid
     * schedule hashes a random settlement id, so a growth test that left them on
     * would vary run to run. This file is <em>about</em> raids and cannot, so it
     * keeps the sandbox's still yield policy and fixes the ids instead.
     */
    private static final SimSettings RAIDING = new SimSettings(
            com.civilization.sim.world.SimWorld.SIM_INTERVAL_TICKS,
            com.civilization.sim.settlement.PopulationPlanner.STEPS_PER_BIRTH,
            96.0, 64, SimSettings.DEFAULT_RAID_INTERVAL_STEPS, true,
            SimSettings.NO_POPULATION_CAP, false,
            com.civilization.sim.world.YieldPolicy.FULL);

    private static SimContext step(WorldBridge bridge, long at) {
        return new SimContext(bridge, at, RAIDING);
    }

    /** Ages a settlement past its raid grace without running its whole life. */
    private static void pastItsGrace(Settlement town, WorldBridge bridge) {
        town.setFirstStep(0);
        assertFalse(RaidPlanner.withinGrace(town, RaidPlanner.RAID_GRACE_STEPS
                + RaidPlanner.EARLY_CAP_STEPS + 1));
    }

    // --- the party ----------------------------------------------------------

    @Test
    void aCampSendsHalfOfWhatItHasAndKeepsSomebodyHome() {
        Settlement camp = camp(8);
        camp.step(step(new Quiet(), 1));
        List<Person> party = GoblinRaids.partyFrom(camp);
        assertTrue(party.size() >= GoblinRaids.MIN_PARTY,
                "a camp of eight sent " + party.size());
        assertTrue(party.size() < camp.population(),
                "the camp emptied itself into the raid");
        // The shaman keeps the camp and never walks out of it.
        assertTrue(party.stream().noneMatch(p -> p.profession() == Profession.SHAMAN),
                "the shaman went raiding and left the camp with no keeper");
    }

    @Test
    void aCampTooSmallToSpareAnybodySendsNobody() {
        Settlement tiny = Founding.seeded(new SimPos(0, 72, 0), "Grubfen",
                SettlementStage.HOMESTEAD, BuildCatalog.DEFAULT, Culture.GOBLIN.id(),
                3, Culture.LAYOUT_GOBLIN_CAMP);
        assertEquals(List.of(), GoblinRaids.partyFrom(tiny),
                "three goblins walked up to a village on their own");
        assertEquals(0, GoblinRaids.strengthOf(tiny, GoblinRaids.partyFrom(tiny)));
    }

    @Test
    void aPartysStrengthIsCountedInTheSameCurrencyATownDefendsIn() {
        Settlement camp = camp(8);
        camp.step(step(new Quiet(), 1));
        List<Person> party = GoblinRaids.partyFrom(camp);
        int strength = GoblinRaids.strengthOf(camp, party);
        // One point a body, and the chieftain's morale on top when he leads --
        // which is the same bonus the garrison counts him for, spent on attack.
        assertTrue(strength >= party.size(), "a party of " + party.size()
                + " was worth only " + strength);
        assertTrue(strength <= RaidPlanner.MAX_RAID_STRENGTH,
                "a camp spawned a horde: " + strength);
    }

    @Test
    void aCampRaidsTheNearestSettlementThatIsNotACamp() {
        Settlement camp = camp(8);
        Settlement near = village(12, 1);
        Settlement far = Founding.seeded(new SimPos(400, 72, 0), "Millbrook",
                SettlementStage.VILLAGE, BuildCatalog.DEFAULT, Culture.NORMAN.id(), 12);
        Settlement otherCamp = Founding.seeded(new SimPos(40, 72, 0), "Rotcrag",
                SettlementStage.TOWN, BuildCatalog.DEFAULT, Culture.GOBLIN.id(),
                8, Culture.LAYOUT_GOBLIN_CAMP);

        assertSame(near, GoblinRaids.nearestVictim(camp, List.of(camp, otherCamp, near, far)),
                "the camp walked past its neighbour, or raided another camp");
        // And nothing at all when everybody is out of reach.
        Settlement distant = Founding.seeded(new SimPos(GoblinRaids.REACH * 4, 72, 0),
                "Whitecliff", SettlementStage.VILLAGE, BuildCatalog.DEFAULT,
                Culture.NORMAN.id(), 12);
        assertNull(GoblinRaids.nearestVictim(camp, List.of(camp, distant)));
    }

    // --- the loot -----------------------------------------------------------

    @Test
    void aBreakthroughMovesAShareOfTheStoresToTheLootPile() {
        Quiet bridge = new Quiet();
        Settlement camp = camp(8);
        Settlement town = village(12, 0);   // no watch at all: the line will give
        pastItsGrace(town, bridge);
        camp.setFirstStep(0);

        int foodBefore = town.stores().get(TownStores.FOOD);
        int ironBefore = town.stores().get(TownStores.IRON);
        int coinBefore = town.treasury();
        int campFood = camp.stores().get(TownStores.FOOD);

        RaidPlanner.Outcome outcome = RaidPlanner.raidBy(town,
                step(bridge, RaidPlanner.RAID_GRACE_STEPS + RaidPlanner.EARLY_CAP_STEPS + 1),
                8, camp);
        assertTrue(outcome.happened());
        assertTrue(outcome.brokeIn(), "a raid of eight did not get past a town with no guards");

        // The raid resolved; now the carry-home, which is what GoblinRaids does
        // with a breakthrough. Driven through the planner's own pass so the share
        // and the order come from one place.
        GoblinRaids.advance(List.of(camp, town),
                step(bridge, dueStep(camp, bridge)));

        assertTrue(town.stores().get(TownStores.FOOD) < foodBefore
                        || town.stores().get(TownStores.IRON) < ironBefore
                        || town.treasury() < coinBefore,
                "the raiders got in and the town lost nothing");
        assertTrue(camp.stores().get(TownStores.FOOD) > campFood
                        || camp.stores().get(TownStores.IRON) > 0
                        || camp.treasury() > Settlement.FOUNDING_TREASURY,
                "the town lost its stores and the loot pile is empty");
    }

    @Test
    void aRepelMovesNothingAtAll() {
        Quiet bridge = new Quiet();
        Settlement camp = camp(8);
        // A garrison that outmatches anything a camp of eight can field: eight
        // guards is a defense of sixteen against a party worth at most six.
        Settlement town = village(20, 8);
        pastItsGrace(town, bridge);
        camp.setFirstStep(0);

        int foodBefore = town.stores().get(TownStores.FOOD);
        int ironBefore = town.stores().get(TownStores.IRON);
        int coinBefore = town.treasury();
        int pileFood = camp.stores().get(TownStores.FOOD);
        int pileIron = camp.stores().get(TownStores.IRON);

        for (long at = 0; at <= GoblinRaids.RAID_PERIOD_STEPS * 2; at++) {
            GoblinRaids.advance(List.of(camp, town), step(bridge, at));
        }

        assertEquals(foodBefore, town.stores().get(TownStores.FOOD),
                "a repelled raid still took the town's food");
        assertEquals(ironBefore, town.stores().get(TownStores.IRON));
        assertEquals(coinBefore, town.treasury());
        assertEquals(pileFood, camp.stores().get(TownStores.FOOD),
                "the loot pile grew on a raid that never got in");
        assertEquals(pileIron, camp.stores().get(TownStores.IRON));
    }

    @Test
    void aRaidOnANewTownDoesNothingAtAll() {
        // The grace is a rule about the town, and a party that walks up to one
        // turns round again. Asserted here because the camp is the first raider
        // that could have been written to ignore it.
        Quiet bridge = new Quiet();
        Settlement camp = camp(8);
        Settlement town = village(12, 0);
        town.setFirstStep(0);
        int foodBefore = town.stores().get(TownStores.FOOD);

        RaidPlanner.Outcome outcome = RaidPlanner.raidBy(town, step(bridge, 5), 8, camp);
        assertFalse(outcome.happened(), "a town five steps old was raided");
        assertEquals(foodBefore, town.stores().get(TownStores.FOOD));
    }

    @Test
    void aWatchedRaidSpawnsGoblinsRatherThanZombies() {
        Quiet bridge = new Quiet();
        bridge.watched = true;
        Settlement camp = camp(8);
        Settlement town = village(12, 1);
        pastItsGrace(town, bridge);

        RaidPlanner.Outcome outcome = RaidPlanner.raidBy(town,
                step(bridge, RaidPlanner.RAID_GRACE_STEPS + RaidPlanner.EARLY_CAP_STEPS + 1),
                6, camp);
        assertTrue(outcome.fought(),
                "somebody was watching and the raid resolved as arithmetic anyway");
        assertEquals(1, bridge.spawnedFor.size());
        assertTrue(bridge.spawnedFor.get(0).startsWith(Culture.GOBLIN.id()),
                "the party spawned as somebody else: " + bridge.spawnedFor);
    }

    @Test
    void aCampPaysForTheWalkWhenTheTownFieldsALine() {
        Quiet bridge = new Quiet();
        Settlement camp = camp(8);
        Settlement town = village(20, 6);
        pastItsGrace(town, bridge);
        camp.setFirstStep(0);
        int before = camp.population();

        for (long at = 0; at <= GoblinRaids.RAID_PERIOD_STEPS * 2; at++) {
            GoblinRaids.advance(List.of(camp, town), step(bridge, at));
        }
        assertTrue(camp.population() < before,
                "a camp walked at a garrison of twelve and lost nobody");
        // And never more than half the party, or one bad night ends the camp.
        assertTrue(camp.population() >= before / 2,
                "a single raid cost the camp " + (before - camp.population()) + " of " + before);
    }

    @Test
    void aCampThatIsComingApartDoesNotGoOut() {
        Quiet bridge = new Quiet();
        Settlement camp = camp(8);
        Settlement town = village(12, 0);
        pastItsGrace(town, bridge);
        camp.setFirstStep(0);
        camp.step(step(bridge, 1));
        camp.removePerson(com.civilization.sim.settlement.KingPlanner.king(camp).id());
        camp.removePerson(
                com.civilization.sim.settlement.GoblinCamp.shaman(camp).id());
        camp.step(step(bridge, 2));

        int foodBefore = town.stores().get(TownStores.FOOD);
        for (long at = 0; at <= GoblinRaids.RAID_PERIOD_STEPS * 2; at++) {
            GoblinRaids.advance(List.of(camp, town), step(bridge, at));
        }
        assertEquals(foodBefore, town.stores().get(TownStores.FOOD),
                "a camp walking away from itself still went out raiding");
    }

    @Test
    void everyCampGoesOutOnItsOwnClock() {
        // Two camps near one village must not arrive together on every multiple of
        // the period, which is the whole reason the offset is hashed from the id.
        Quiet bridge = new Quiet();
        List<Long> firstOut = new ArrayList<>();
        // Distinct msb AND lsb, which is not fussiness: UUID.hashCode() xors the
        // two halves, so new UUID(n, n) hashes to zero for every n and four camps
        // built that way share one clock by construction. The first cut of this
        // fixture did exactly that and the test passed for the wrong reason.
        for (long seed : new long[] {11L, 22L, 33L, 44L}) {
            Settlement camp = new Settlement(new Settlement.Id(new UUID(seed, seed * 7 + 1)),
                    "Camp " + seed, new SimPos(0, 72, 0), 64);
            camp.setCultureId(Culture.GOBLIN.id());
            for (long at = 0; at < GoblinRaids.RAID_PERIOD_STEPS; at++) {
                if (GoblinRaids.dueOut(camp, step(bridge, at))) {
                    firstOut.add(at);
                    break;
                }
            }
        }
        assertEquals(4, firstOut.size(), "some camp never came out at all");
        assertTrue(firstOut.stream().distinct().count() > 1,
                "every camp goes out on the same step: " + firstOut);
    }

    /** The next step this camp's party walks out on. */
    private static long dueStep(Settlement camp, WorldBridge bridge) {
        long from = RaidPlanner.RAID_GRACE_STEPS + RaidPlanner.EARLY_CAP_STEPS + 1;
        for (long at = from; at < from + GoblinRaids.RAID_PERIOD_STEPS * 2; at++) {
            if (GoblinRaids.dueOut(camp, step(bridge, at))) {
                return at;
            }
        }
        throw new AssertionError("the camp never comes out");
    }
}
