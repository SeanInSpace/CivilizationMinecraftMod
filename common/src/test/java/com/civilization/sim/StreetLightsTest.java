package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.RoadUpkeep;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.work.LightPlanner;
import com.civilization.sim.work.LightStyle;
import com.civilization.sim.work.PublicWorks;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The street lighting: where a lamp goes, what it costs, and whether the plan it
 * comes to actually leaves anywhere dark.
 *
 * <p>The coverage test is the one that matters. Everything else here is
 * bookkeeping; that one is the promise the whole work was added to keep, and it is
 * measured over every column of every carriageway rather than argued from the
 * spacing constant — because the spacing is eight along the run and the lamps
 * stand off the centerline on alternating verges, so what a column is actually
 * worth is a diagonal nobody should be doing in their head.
 */
class StreetLightsTest {

    private static class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX =
            new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    /** A crossroads of four eight-wide streets, all opened, and nothing else. */
    private static Settlement crossroads() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Lampton", new SimPos(0, 64, 0), 128);
        town.setCultureId(Culture.NORMAN.id());
        PathNetwork paths = new PathNetwork();
        paths.add(new PathNetwork.Segment(
                new SimPos(-40, 64, 0), new SimPos(40, 64, 0), 8));
        paths.add(new PathNetwork.Segment(
                new SimPos(0, 64, -40), new SimPos(0, 64, 40), 8));
        for (int i = 0; i < paths.segments().size(); i++) {
            paths.markOpened(i);
        }
        town.setPaths(paths);
        town.addResident(new Person(
                Person.Id.random(), "Lamplighter", Profession.BUILDER, town.center()));
        return town;
    }

    // --- the promise ---

    @Test
    void everyColumnOfEveryOpenedStreetIsWithinEightBlocksOfALamp() {
        Settlement town = crossroads();
        List<LightPlanner.Lamp> lamps = LightPlanner.lamps(town);
        assertFalse(lamps.isEmpty(), "an opened street has to be lit");

        PathNetwork paths = town.paths();
        double worst = 0;
        SimPos worstAt = null;
        for (int i = 0; i < paths.segments().size(); i++) {
            PathNetwork.Segment run = paths.segments().get(i);
            int half = run.paveHalf();
            for (SimPos along : run.positions()) {
                for (int ox = -half; ox <= half; ox++) {
                    for (int oz = -half; oz <= half; oz++) {
                        SimPos column = along.offset(ox, 0, oz);
                        double distance = LightPlanner.toNearestLamp(lamps, column);
                        if (distance > worst) {
                            worst = distance;
                            worstAt = column;
                        }
                    }
                }
            }
        }
        assertTrue(worst <= LightPlanner.REACH,
                "the darkest column of the carriageway is " + worst
                        + " blocks from a lamp at " + worstAt
                        + ", and the promise is " + LightPlanner.REACH);
    }

    @Test
    void everyDoorGetsALampBesideIt() {
        Settlement town = crossroads();
        Building house = new Building("civilization:house", new SimPos(20, 64, 20), 1, true);
        house.setFootprint(new Footprint(64, 7, 7, 4));
        house.setFacing(0);
        town.addBuilding(house);

        List<LightPlanner.Lamp> lamps = LightPlanner.lamps(town);
        assertTrue(LightPlanner.toNearestLamp(lamps, house.doorstep())
                        <= LightPlanner.REACH,
                "a door with no lamp beside it is the last five steps home in the dark");
    }

    @Test
    void aFieldGetsALampOnEveryCorner() {
        Settlement town = crossroads();
        Building farm = new Building("civilization:farm", new SimPos(80, 64, 80), 1, true);
        farm.setFootprint(new Footprint(64, 11, 11, 1));
        town.addBuilding(farm);

        List<LightPlanner.Lamp> lamps = LightPlanner.lamps(town);
        long corners = lamps.stream()
                .filter(lamp -> lamp.why() == LightPlanner.Post.CORNER)
                .count();
        assertEquals(4, corners,
                "an outlying field is where the measurement found the bodies");
    }

    @Test
    void noLampStandsInsideSomebodysHouse() {
        Settlement town = crossroads();
        Building house = new Building("civilization:house", new SimPos(6, 64, 6), 1, true);
        house.setFootprint(new Footprint(64, 9, 9, 4));
        town.addBuilding(house);

        for (LightPlanner.Lamp lamp : LightPlanner.lamps(town)) {
            assertFalse(house.footprint().covers(house.origin().x(), house.origin().z(),
                            lamp.at().x(), lamp.at().z()),
                    "a lamp at " + lamp.at() + " is inside a house, where the "
                            + "demolition census would read it as a standing wall");
        }
    }

    @Test
    void noLampStandsInTheCarriageway() {
        Settlement town = crossroads();
        for (LightPlanner.Lamp lamp : LightPlanner.lamps(town)) {
            assertFalse(LightPlanner.standsInTheRoad(town, lamp.at()),
                    "a lamp at " + lamp.at() + " is on the stones, which the "
                            + "paving sweep would fight over forever");
        }
    }

    @Test
    void nothingIsLitBeforeItIsOpened() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Planned", new SimPos(0, 64, 0), 128);
        PathNetwork paths = new PathNetwork();
        paths.add(new PathNetwork.Segment(
                new SimPos(-40, 64, 0), new SimPos(40, 64, 0), 8));
        town.setPaths(paths);

        assertEquals(0, LightPlanner.wanted(town),
                "a street nobody has walked out is a line on paper, not a street");
    }

    // --- the order ---

    @Test
    void lampsAreRaisedFromTheMiddleOutward() {
        Settlement town = crossroads();
        List<LightPlanner.Lamp> lamps = LightPlanner.lamps(town);
        long previous = -1;
        for (LightPlanner.Lamp lamp : lamps) {
            long distance = lamp.at().horizontalDistanceSq(town.center());
            assertTrue(distance >= previous,
                    "the raising order has to grow at its end, or the saved count "
                            + "means a different lamp after the town grows");
            previous = distance;
        }
    }

    @Test
    void aNewStreetAppendsRatherThanRenumbering() {
        Settlement town = crossroads();
        List<LightPlanner.Lamp> before = LightPlanner.lamps(town);

        PathNetwork paths = town.paths();
        paths.add(new PathNetwork.Segment(
                new SimPos(60, 64, -40), new SimPos(60, 64, 40), 8));
        paths.markOpened(paths.segments().size() - 1);

        List<LightPlanner.Lamp> after = LightPlanner.lamps(town);
        assertTrue(after.size() > before.size(), "a new street wants new lamps");
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).at(), after.get(i).at(),
                    "lamp " + i + " moved when an outlying street opened, which "
                            + "would send a builder to a lamp that already stands");
        }
    }

    // --- the price ---

    @Test
    void aLampCostsAPostAndALight() {
        Settlement town = crossroads();
        town.setCultureId(Culture.NORMAN.id());   // a lantern on an oak post
        town.setStock(TownStores.WOOD, 10);
        town.setStock(TownStores.IRON, 10);

        assertTrue(LightPlanner.payForLamp(town));
        assertEquals(9, town.stores().get(TownStores.WOOD), "the post is timber");
        assertEquals(9, town.stores().get(TownStores.IRON), "the lantern is iron");
    }

    @Test
    void aTornLampIsNotPaidForHalfway() {
        Settlement town = crossroads();
        town.setCultureId(Culture.NORMAN.id());
        town.setStock(TownStores.WOOD, 10);
        town.setStock(TownStores.IRON, 0);

        assertFalse(LightPlanner.payForLamp(town), "no iron, no lantern");
        assertEquals(10, town.stores().get(TownStores.WOOD),
                "and the timber stays on the shelf rather than buying half a lamp");
    }

    @Test
    void aTorchTownSpendsTimberTwice() {
        Settlement town = crossroads();
        town.setCultureId(Culture.VALE.id());   // a torch on an oak post
        town.setStock(TownStores.WOOD, 10);

        assertTrue(LightPlanner.payForLamp(town));
        assertEquals(8, town.stores().get(TownStores.WOOD),
                "the post and the torch are both timber");
    }

    @Test
    void theLightingWillNotTakeTheTimberAHouseIsOwed() {
        Settlement town = crossroads();
        town.setStock(TownStores.WOOD, LightPlanner.TIMBER_KEPT_FOR_BUILDING);
        town.setStock(TownStores.IRON, 100);

        assertFalse(LightPlanner.worthStarting(town),
                "the reserve is the build queue's, and a lamp is not a roof");
    }

    // --- the two fidelities ---

    @Test
    void theClockRaisesLampsForATownNobodyIsWatching() {
        Settlement town = crossroads();
        town.setStock(TownStores.WOOD, 500);
        town.setStock(TownStores.IRON, 500);

        LightPlanner.advance(town, CTX);

        assertEquals(LightPlanner.LIGHTS_PER_STEP, town.lightsRaised(),
                "an unwatched town lights itself at the clock's pace");
        assertFalse(LightPlanner.isLit(town), "and has a good way to go yet");

        town.setLightsRaised(LightPlanner.wanted(town));
        assertTrue(LightPlanner.isLit(town), "until it does not");
    }

    @Test
    void nobodyRaisesALampPostAtNight() {
        Settlement town = crossroads();
        town.setStock(TownStores.WOOD, 500);
        town.setStock(TownStores.IRON, 500);
        SimContext afterDusk = new SimContext(new QuietBridge() {
            @Override public long dayTime() {
                return com.civilization.sim.person.NightRest.DUSK + 1000;
            }
        }, 0, SimSettings.SANDBOX);

        LightPlanner.advance(town, afterDusk);

        assertEquals(0, town.lightsRaised(),
                "a watched town's builders are walked home by the curfew, so a clock "
                        + "that lit through the dark would outpace them");
    }

    @Test
    void aDeadTownLightsNothing() {
        Settlement town = crossroads();
        town.setStock(TownStores.WOOD, 500);
        town.setStock(TownStores.IRON, 500);
        for (Person person : List.copyOf(town.residents())) {
            town.removeResident(person.id());
        }

        LightPlanner.advance(town, CTX);

        assertEquals(0, town.lightsRaised(), "nobody is left to hold the ladder");
        assertFalse(RoadUpkeep.mayMendLight(town, 0),
                "and a broken lamp stays broken, which is what the village looks like");
    }

    @Test
    void theClockStandsAsideForAPlayerWhoCanSeeTheVerge() {
        Settlement town = crossroads();
        town.setStock(TownStores.WOOD, 500);
        town.setStock(TownStores.IRON, 500);
        SimContext watched = new SimContext(new QuietBridge() {
            @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        }, 0, SimSettings.SANDBOX);

        LightPlanner.advance(town, watched);

        assertEquals(0, town.lightsRaised(),
                "a lantern that lights itself in front of somebody is magic");
    }

    @Test
    void theLightingIsAPublicWorkBehindTheRoadsItStandsBeside() {
        Settlement town = crossroads();
        List<String> order = PublicWorks.of(town).stream().map(w -> w.name()).toList();
        assertEquals(List.of("dismantle", "wall", "road", "lights", "clearing"), order);
    }

    @Test
    void aBuildingTownStillLightsItsStreets() {
        Settlement town = crossroads();
        town.enqueueBuild(new com.civilization.sim.settlement.BuildTask(
                "civilization:house", new SimPos(16, 64, 16), 40));

        List<String> order = PublicWorks.availableTo(town).stream().map(w -> w.name()).toList();
        assertEquals(List.of("road", "lights"), order,
                "a town is dark on every night it is growing, which is every night — "
                        + "but a lamp needs a street to stand beside, so the paving "
                        + "leads and the lighting follows it");
    }

    @Test
    void theWorkKnowsWhereTheNextLampGoes() {
        Settlement town = crossroads();
        town.setStock(TownStores.WOOD, 500);
        town.setStock(TownStores.IRON, 500);
        PublicWorks.LightWork work = new PublicWorks.LightWork(town);

        assertNotNull(work.nextStation(town));
        assertEquals(LightPlanner.next(town).at(), work.nextStation(town));
        assertEquals(TownStores.WOOD, work.material(), "a norman post is a plank");

        town.setLightsRaised(LightPlanner.wanted(town));
        assertNull(work.nextStation(town), "a lit town has nothing left to raise");
    }

    @Test
    void aBurgherCarriesStoneAndAGoblinCarriesBamboo() {
        Settlement burgh = crossroads();
        burgh.setCultureId(Culture.BURGHER.id());
        assertEquals(LightStyle.STONE_LANTERN, LightPlanner.styleOf(burgh));
        assertEquals(TownStores.STONE, new PublicWorks.LightWork(burgh).material());

        Settlement mire = crossroads();
        mire.setCultureId(Culture.GOBLIN.id());
        assertEquals(LightStyle.STICK_TORCH, LightPlanner.styleOf(mire),
                "the goblins exist after the merge, so they get their own lamp");
        assertEquals(TownStores.WOOD, new PublicWorks.LightWork(mire).material());
    }

    @Test
    void everyCultureHasALampAndNoneOfThemIsNothing() {
        for (Culture culture : Culture.all()) {
            LightStyle style = LightStyle.of(culture);
            assertNotNull(style, culture.id() + " has no lamp");
            assertNotNull(style.postResource());
            assertNotNull(style.lightResource());
            assertTrue(style.height() >= 1, "a lamp at ground level lights a boot");
        }
        assertEquals(LightStyle.FENCE_TORCH, LightStyle.of("something_a_datapack_made_up"),
                "an unlit town is the one outcome this work exists to prevent");
    }
}
