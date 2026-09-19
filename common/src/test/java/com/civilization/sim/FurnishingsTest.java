package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Homes;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.TownStores;
import com.civilization.sim.settlement.WorkArea;
import com.civilization.sim.work.FurnishingStyle;
import com.civilization.sim.work.Furnishings;
import com.civilization.sim.work.InteriorClearing;
import com.civilization.sim.work.LightPlanner;
import com.civilization.sim.work.PublicWorks;
import com.civilization.sim.world.SimContext;
import com.civilization.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground between a town's buildings.
 *
 * <p>The fault this work exists for is a picture rather than a casualty list: a
 * finished ring town of fifteen buildings and fifteen people is a wide loop of
 * dirt road around a field of empty grass. So what has to be pinned is that the
 * dressing <em>arrives</em> — every home has a garden, the square lands where the
 * town's heart is — and that it arrives on ground nothing else has a claim to,
 * which is the half that can do damage. A fence post through somebody's wall is
 * worse than an undressed town.
 *
 * <p>Measured on the recorded ground of seed 8675309 across every arrangement any
 * people build in, because a rule tested on flat ground is a rule that has not
 * been tested: see {@code RecordedTerrain}, which is here for exactly this reason.
 */
class FurnishingsTest {

    private static final SimPos CENTER = new SimPos(0, 72, 0);

    /**
     * How far a town is grown for the siting tests: enough that the streets are
     * out and the plan has had to make hard choices, and not so far that the
     * suite pays four minutes for it.
     */
    private static final int STEPS = 600;

    // --- fixtures ------------------------------------------------------------

    private static class QuietBridge implements WorldBridge {
        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String blueprintId, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 5, 5, 4);
        }
        @Override public void log(String message) { }
    }

    private static final SimContext CTX =
            new SimContext(new QuietBridge(), 0, SimSettings.SANDBOX);

    /** Every arrangement any people in the mod builds in. */
    private static List<String> layouts() {
        Set<String> named = new LinkedHashSet<>();
        for (Culture culture : Culture.all()) {
            named.addAll(culture.layouts());
        }
        return List.copyOf(named);
    }

    private static Culture cultureFor(String layout) {
        for (Culture culture : Culture.all()) {
            if (culture.layouts().contains(layout)) {
                return culture;
            }
        }
        return Culture.DEFAULT;
    }

    /**
     * A town grown on real ground until it has at least this many buildings.
     *
     * <p>{@code WallProbe.grow} in miniature: stocked every step so that the
     * building is never the thing being measured, and stepped until the town has
     * the size asked for or the steps run out.
     */
    private static Settlement grown(String layout, WorldBridge ground, int buildings) {
        Settlement town = new Settlement(Settlement.Id.random(), "Dressing", CENTER, 256);
        town.setCatalog(BuildCatalog.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        Culture culture = cultureFor(layout);
        town.setCultureId(culture.id());
        town.setLayoutId(layout);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(Person.Id.random(), name, Profession.PIONEER, CENTER));
        }
        for (int step = 1; step <= STEPS; step++) {
            town.stores().add(TownStores.WOOD, 24);
            town.stores().add(TownStores.STONE, 18);
            town.stores().add(TownStores.SAPLINGS, 2);
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
            if (town.buildings().size() >= buildings) {
                break;
            }
        }
        return town;
    }

    /** A ring road of four opened sides, which is what the screenshot showed. */
    private static Settlement ringTown() {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Millbrook", new SimPos(0, 64, 0), 128);
        town.setCultureId("civilization:human/norman");
        PathNetwork paths = new PathNetwork();
        List<SimPos> corners = List.of(
                new SimPos(-48, 64, -48), new SimPos(48, 64, -48),
                new SimPos(48, 64, 48), new SimPos(-48, 64, 48));
        for (int i = 0; i < corners.size(); i++) {
            paths.add(new PathNetwork.Segment(
                    corners.get(i), corners.get((i + 1) % corners.size()), 8));
        }
        for (int i = 0; i < paths.segments().size(); i++) {
            paths.markOpened(i);
        }
        town.setPaths(paths);
        town.addResident(new Person(
                Person.Id.random(), "Mason", Profession.BUILDER, town.center()));
        return town;
    }

    private static Building house(Settlement town, String id, int x, int z, int facing) {
        Building building = new Building(id, new SimPos(x, 64, z), 1, true);
        building.setFootprint(new Footprint(64, 7, 7, 4));
        building.setFacing(facing);
        town.addBuilding(building);
        return building;
    }

    // --- the invariant that can do damage ------------------------------------

    @Test
    void everyPieceStandsOnGroundNothingElseHasAClaimTo() {
        RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        for (String layout : layouts()) {
            Settlement town = grown(layout, ground, 30);
            // A full churchyard before anything is asked, so the graves are in
            // the plan this whole assertion is about. A grave is the one piece
            // that is not a function of the buildings and the streets — there is
            // one per entry in the town's dead — and a row of twelve stones
            // planned out past the last house has every chance of landing on a
            // plot, a carriageway or the wall line that the rest of the dressing
            // has. It was worth extending the existing sweep rather than writing
            // a second one, because what is being stated is the same sentence.
            fillTheChurchyard(town);
            assertTrue(Furnishings.standOnFreeGround(town),
                    layout + " plans a piece on ground somebody else has: a fence"
                            + " through a wall is worse than an undressed town");

            for (Furnishings.Furnishing piece : Furnishings.pieces(town)) {
                for (Building building : town.buildings()) {
                    if (!building.footprint().isKnown()) {
                        continue;
                    }
                    for (int dx = -piece.reach(); dx <= piece.reach(); dx++) {
                        for (int dz = -piece.reach(); dz <= piece.reach(); dz++) {
                            assertFalse(building.footprint().covers(
                                            building.origin().x(), building.origin().z(),
                                            piece.at().x() + dx, piece.at().z() + dz),
                                    layout + ": a " + piece.piece() + " at " + piece.at()
                                            + " stands inside " + building.blueprintId()
                                            + " at " + building.origin());
                        }
                    }
                }
                if (piece.piece() != Furnishings.Piece.SQUARE) {
                    // Every piece but the square, for the reason the lamp sweep
                    // below gives at length: the square stands on ground the plan
                    // holds for it and every lane in the town AIMS at that point,
                    // so "is there a carriageway here" is true of the square's own
                    // ground by construction. Refusing it for that is refusing it
                    // for being the hub, which is what sent it thirty blocks out
                    // into a field in six arrangements of eight.
                    assertFalse(LightPlanner.standsInTheRoad(town, piece.at()),
                            layout + ": a " + piece.piece() + " at " + piece.at()
                                    + " stands in the carriageway");
                }
            }

            // The lamps and the forester's belt, on the same grown town rather
            // than on a second one: growing every arrangement on recorded ground
            // is most of what this file costs to run, and the two questions are
            // asked of the same plan.
            List<LightPlanner.Lamp> lamps = LightPlanner.lamps(town);
            WorkArea belt = town.lumberArea();
            for (Furnishings.Furnishing piece : Furnishings.pieces(town)) {
                for (LightPlanner.Lamp lamp : lamps) {
                    if (piece.piece() == Furnishings.Piece.SQUARE) {
                        // Every piece but one. What this rule is about is a piece
                        // of dressing CLOSING OVER a lamp — the wording is its own
                        // argument, "a hedge round a lamp is a dark street" — and
                        // the square is the one piece that encloses nothing. It is
                        // paving, written a course below the surface, on ground the
                        // plan holds empty and every lane in the town aims at. A
                        // lamp post standing in a town square is a lamp post in a
                        // town square, and refusing the square for having one is
                        // how {@code crescents} came to have no square at all.
                        continue;
                    }
                    assertFalse(Math.abs(lamp.at().x() - piece.at().x()) <= piece.reach()
                                    && Math.abs(lamp.at().z() - piece.at().z())
                                            <= piece.reach(),
                            layout + ": a " + piece.piece() + " swallows the lamp at "
                                    + lamp.at() + ", and a hedge round a lamp is a"
                                    + " dark street");
                }
                if (belt != null) {
                    assertTrue(piece.at().horizontalDistance(new SimPos(
                                    belt.center().x(), piece.at().y(), belt.center().z()))
                                    > belt.radius(),
                            layout + ": a " + piece.piece() + " stands in the"
                                    + " forester's belt, which is the town's timber");
                }
            }
        }
    }

    // --- the thing the screenshot was missing --------------------------------

    @Test
    void everyFamilyHomeGetsAGardenWhenThereIsRoomForOne() {
        Settlement town = ringTown();
        // Four houses well apart on the open ground inside the ring, each facing
        // the middle, which is what BuildPlanner.facingToward produces.
        house(town, "civilization:house", -24, -24, 0);
        house(town, "civilization:house", 24, -24, 0);
        house(town, "civilization:house", -24, 24, 2);
        house(town, "civilization:house", 24, 24, 2);

        List<Furnishings.Furnishing> pieces = Furnishings.pieces(town);
        for (Building building : town.buildings()) {
            if (!Homes.isFamilyHome(building.blueprintId())) {
                continue;
            }
            boolean hasOne = false;
            for (Furnishings.Furnishing piece : pieces) {
                hasOne |= piece.piece() == Furnishings.Piece.YARD
                        && piece.at().horizontalDistance(building.origin()) <= 12;
            }
            assertTrue(hasOne, "the house at " + building.origin() + " has open grass"
                    + " on all four sides and no kitchen garden, which is the"
                    + " screenshot this whole work is about");
        }
    }

    @Test
    void theSquareLandsAtTheHeart() {
        Settlement town = ringTown();
        house(town, "civilization:house", -24, -24, 0);
        house(town, "civilization:house", 24, 24, 2);

        Furnishings.Furnishing square = null;
        for (Furnishings.Furnishing piece : Furnishings.pieces(town)) {
            if (piece.piece() == Furnishings.Piece.SQUARE) {
                square = piece;
            }
        }
        assertNotNull(square, "a town with open ground at its middle has a square");
        assertTrue(square.at().horizontalDistance(town.center()) <= 24,
                "the square is at " + square.at() + " and the heart of the town is "
                        + town.center() + ": a square on the edge is a yard");
        assertTrue(square.reach() >= Furnishings.SMALLEST_SQUARE,
                "nine blocks across is the smallest thing worth calling a square");
    }

    @Test
    void aWellStandsAtTheHeartUntilAMarketDrawsItsOwn() {
        Settlement town = ringTown();
        assertTrue(countOf(town, Furnishings.Piece.WELL) > 0,
                "a town with no market has nowhere to draw water at all");

        Building market = new Building("civilization:market", new SimPos(8, 64, 8), 1, true);
        market.setFootprint(new Footprint(64, 9, 9, 4));
        town.addBuilding(market);
        assertEquals(0, countOf(town, Furnishings.Piece.WELL),
                "the market square draws its own well, and a second one twenty"
                        + " blocks away is a town that dug twice");
    }

    // --- the index has to keep meaning the same piece ------------------------

    @Test
    void theCountIsAppendOnlyWhenATownGrowsOutward() {
        // Open ground and no streets, deliberately. A house raised beside a street
        // takes the verge a hedge was standing on, which is a real thing that
        // happens and is not what is being stated here: what is stated is that the
        // ORDER appends, so a piece already raised keeps its index. The other case
        // is survivable for the reason LightPlanner gives — a shifted index sends
        // a builder to a piece that is already there, and the layer reads the
        // ground before it charges for anything.
        Settlement town = new Settlement(
                Settlement.Id.random(), "Outward", new SimPos(0, 64, 0), 160);
        town.setCultureId("civilization:human/norman");
        town.addResident(new Person(
                Person.Id.random(), "Mason", Profession.BUILDER, town.center()));
        house(town, "civilization:house", -24, -24, 0);
        house(town, "civilization:house", 24, -24, 0);
        List<Furnishings.Furnishing> before = Furnishings.pieces(town);
        assertFalse(before.isEmpty());

        // A new house further out than anything standing, which is how a town
        // grows: every piece it brings with it sorts after everything already
        // planned, so nothing already raised changes its index.
        house(town, "civilization:house", 60, 60, 2);
        List<Furnishings.Furnishing> after = Furnishings.pieces(town);
        assertTrue(after.size() > before.size(),
                "the new house brought no dressing with it at all");
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i), after.get(i),
                    "piece " + i + " moved when the town grew outward, so every"
                            + " builder already sent to it is at the wrong place");
        }
    }

    @Test
    void theListIsOrderedCenterOutward() {
        RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        Settlement town = grown(Culture.LAYOUT_RADIAL_CONCENTRIC, ground, 30);
        long previous = -1;
        for (Furnishings.Furnishing piece : Furnishings.pieces(town)) {
            long out = piece.at().horizontalDistanceSq(town.center());
            assertTrue(out >= previous,
                    "the dressing is not ordered center-outward, so the count the"
                            + " town saves stops naming the same piece as it grows");
            previous = out;
        }
    }

    // --- who dresses, and when ----------------------------------------------

    @Test
    void goblinsGetNoKitchenGarden() {
        assertFalse(FurnishingStyle.MIRE.raises(Furnishings.Piece.YARD));
        assertFalse(FurnishingStyle.MIRE.raises(Furnishings.Piece.SQUARE));
        assertFalse(FurnishingStyle.MIRE.raises(Furnishings.Piece.HEDGE));
        assertTrue(FurnishingStyle.MIRE.raises(Furnishings.Piece.WOODPILE));
        assertTrue(FurnishingStyle.MIRE.raises(Furnishings.Piece.CAGE));

        assertTrue(FurnishingStyle.WARHOST.raises(Furnishings.Piece.STAKES));
        assertFalse(FurnishingStyle.WARHOST.raises(Furnishings.Piece.ORCHARD),
                "a war camp does not plant a thing it will not be here to pick");

        assertFalse(FurnishingStyle.HIGHLAND.raises(Furnishings.Piece.HEDGE),
                "a hedge is a thing you plant where a thing you plant will grow");
        assertEquals(FurnishingStyle.NORMAN, FurnishingStyle.of("nobody/has/drawn/this"),
                "an undressed town is the outcome this whole work exists to prevent");
    }

    /**
     * A backlog of building does not stop the dressing; being buried does.
     *
     * <p>This test used to say "nothing is dressed while anything at all is
     * queued", which was the rule and was the bug. A settlement queues a house
     * every time a family outgrows one, so a growing town's queue is never
     * empty, and a rule that waited for an empty one waited for ever: a
     * playtested town of a hundred and forty-five people had five hundred and
     * fifty-three pieces planned and not one raised, for its entire life. The
     * sentence the old test was defending is still defended — at three — and
     * the other half of the rule is stated here beside it, because "it starts
     * when the town is keeping up" is the half that was missing and is the half
     * that broke.
     */
    @Test
    void aBacklogOfBuildingDoesNotStopTheDressingButBeingBuriedDoes() {
        Settlement town = dressableTown();
        assertTrue(Furnishings.worthStarting(town));

        for (int i = 0; i < PublicWorks.KEEPING_UP; i++) {
            town.enqueueBuild(new com.civilization.sim.settlement.BuildTask(
                    "civilization:house", new SimPos(40 + i, 64, 0), 100));
        }
        assertTrue(Furnishings.worthStarting(town),
                "a town one or two plots behind is a town keeping up with itself,"
                        + " and a dressing that waited for an empty queue waited"
                        + " for the whole life of every town that was growing");

        town.enqueueBuild(new com.civilization.sim.settlement.BuildTask(
                "civilization:house", new SimPos(50, 64, 0), 100));
        assertFalse(Furnishings.worthStarting(town),
                "a village that fenced a kitchen garden while its bunkhouse waited"
                        + " would be a village with a very pretty famine");
    }

    /**
     * And the same about the lamps, which is where the starvation actually
     * began: the lighting waited on the paving finishing, the dressing waited on
     * the lighting finishing, and neither of those ever finishes.
     */
    @Test
    void theDressingStartsOnceTheStreetsAreMostlyLit() {
        Settlement town = dressableTown();
        int lamps = LightPlanner.wanted(town);
        assertTrue(lamps > 0, "a town with no lamps planned cannot test this");

        town.setLightsRaised(0);
        assertFalse(Furnishings.worthStarting(town),
                "the lamps keep people alive and the flowers do not");

        // Nine in ten standing is a lit town, whatever the tenth is doing.
        town.setLightsRaised(lamps - Math.max(1, lamps / 20));
        assertTrue(Furnishings.worthStarting(town),
                "a town that plans a lamp beside every new door is never once"
                        + " completely lit, so a dressing that waited for that"
                        + " never ran at all");
    }

    @Test
    void nothingIsDressedBeforeTheLampsAreUp() {
        Settlement town = dressableTown();
        assertTrue(Furnishings.worthStarting(town));
        town.setLightsRaised(0);
        assertFalse(Furnishings.worthStarting(town),
                "the lamps keep people alive and the flowers do not");
    }

    /**
     * And the town that is <em>nearly</em> lit dresses itself, which is the fault
     * the nine-tenths rule left behind.
     *
     * <p>{@code lamps 581 of 683} is eighty-five hundredths, and a town that plans
     * a lamp beside every new door lives at eighty-five hundredths for its whole
     * life. {@link PublicWorks#keepingUp} calls that behind, and behind used to
     * mean nothing at all: no board, no headstone, no square, in a town one answer
     * short of the rule. It hands down half the passes now — see
     * {@link PublicWorks#shareOfPasses} — so the dressing goes on while the lamps
     * keep the priority and the larger half of the work.
     */
    @Test
    void aTownThatIsNearlyLitGoesOnDressingItself() {
        Settlement town = dressableTown();
        int lamps = LightPlanner.wanted(town);
        assertTrue(lamps >= 20, "the fixture needs enough lamps to be 85% of");

        town.setLightsRaised(lamps * 85 / 100);
        assertFalse(PublicWorks.keepingUp(town.lightsRaised(), lamps),
                "at eighty-five hundredths the old rule says behind, which is the"
                        + " whole of the fault");
        assertEquals(PublicWorks.MOST_HANDED_DOWN,
                PublicWorks.shareOfPasses(town.lightsRaised(), lamps));
        assertTrue(Furnishings.worthStarting(town),
                "a town measured at lamps 581 of 683 raised none of its five"
                        + " hundred and fifty-three planned pieces, for its whole"
                        + " life, because eighty-five hundredths was a no");
    }

    @Test
    void theClockRaisesAPieceAndPaysForIt() {
        Settlement town = dressableTown();
        int wood = town.stores().get(TownStores.WOOD);
        Furnishings.advance(town, CTX);
        assertEquals(1, town.piecesRaised());
        assertTrue(town.stores().get(TownStores.WOOD) <= wood,
                "a piece raised for nothing is a town minting fence posts");
    }

    @Test
    void theClockStandsAsideForSomebodyWatching() {
        Settlement town = dressableTown();
        Furnishings.advance(town, watchedCtx());
        assertEquals(0, town.piecesRaised(),
                "a flower bed that plants itself in front of a player is the one"
                        + " thing this must never look like");
    }

    /**
     * The fault the second playtest measured on both its seeds: a town somebody
     * is standing in is watched at every piece in its plan, so a veto on a
     * watcher is a veto on the whole of its dressing, for the whole of its life.
     */
    @Test
    void butItDoesNotStandAsideForEver() {
        Settlement town = dressableTown();
        SimContext watched = watchedCtx();
        for (int pass = 0; pass < Furnishings.WATCHED_PASSES_BEFORE_DRAWING; pass++) {
            Furnishings.advance(town, watched);
        }
        assertEquals(1, town.piecesRaised(),
                "nobody is coming to this piece, so a clock that waits for hands"
                        + " waits for nobody and the town is never dressed");
        // And it goes on going up, at the ordinary rate, rather than once.
        Furnishings.advance(town, watched);
        Furnishings.advance(town, watched);
        Furnishings.advance(town, watched);
        assertEquals(2, town.piecesRaised(),
                "each piece waits its own three passes, and then goes up");
    }

    @Test
    void andTheTownSaysWhatIsHoldingItUpWhileItWaits() {
        Settlement town = dressableTown();
        Furnishings.advance(town, watchedCtx());
        assertEquals(0, town.piecesRaised());
        String held = Furnishings.whyNotRaising(town);
        assertNotNull(held, "a dressing count that is not moving must say why");
        assertTrue(held.contains("standing over it"),
                "and the why has to be the real one: " + held);
    }

    /**
     * The half of the fault that no gate could have reported. Every gate was
     * open for a hundred and sixty seconds of a playtest and nothing was raised,
     * so the report reads the clock's own note rather than re-asking the gates.
     */
    @Test
    void aTownThatIsRaisingSaysSoRatherThanNothing() {
        Settlement town = dressableTown();
        Furnishings.advance(town, CTX);
        assertEquals(1, town.piecesRaised());
        assertNull(Furnishings.whyNotRaising(town),
                "nothing is holding this town up and the line must not invent one");
    }

    /**
     * The other half of the same playtest's zero, and the one no gate could have
     * named: a town with every gate open, wood in three figures and nothing
     * raised, because the first piece in its plan cost a sapling it did not have
     * and the plan is a prefix.
     */
    @Test
    void aPieceTheTownCannotAffordDoesNotBlockTheOnesBehindIt() {
        Settlement town = dressableTown();
        town.stores().take(TownStores.SAPLINGS,
                town.stores().get(TownStores.SAPLINGS));
        town.stores().take(TownStores.STONE, town.stores().get(TownStores.STONE));
        int before = town.piecesRaised();
        for (int pass = 0; pass < Furnishings.PASSES_BEFORE_PASSING_OVER - 1; pass++) {
            Furnishings.advance(town, CTX);
        }
        assertEquals(before, town.piecesRaised(),
                "the town is given a minute to come back with an armful before"
                        + " anything gives up on the piece");
        assertTrue(Furnishings.whyNotRaising(town).contains("cannot pay"),
                "and it says what it is short of while it waits: "
                        + Furnishings.whyNotRaising(town));
        Furnishings.advance(town, CTX);
        assertEquals(before + 1, town.piecesRaised(),
                "one piece nothing in the stores can buy must not be a wall across"
                        + " the whole of a town's dressing for the rest of its life");
        assertTrue(Furnishings.whyNotRaising(town).startsWith("passed over"),
                "and passing one over is said out loud, not done quietly: "
                        + Furnishings.whyNotRaising(town));
        // And the minute of patience is spent once per dry spell rather than
        // once per piece: the stores are still bare, so the next piece goes the
        // same way on the very next pass rather than after another twelve.
        Furnishings.advance(town, CTX);
        assertEquals(before + 2, town.piecesRaised(),
                "a town with bare stores is about to refuse the next piece for the"
                        + " same reason, and charging it a fresh minute each time"
                        + " leaves a plan of three hundred taking five hours");
    }

    private static SimContext watchedCtx() {
        return new SimContext(new QuietBridge() {
            @Override public boolean playerWithin(SimPos pos, double radius) { return true; }
        }, 0, SimSettings.SANDBOX);
    }

    @Test
    void aDeadTownDressesNothing() {
        Settlement town = dressableTown();
        for (Person person : List.copyOf(town.residents())) {
            town.removeResident(person.id());
        }
        Furnishings.advance(town, CTX);
        assertEquals(0, town.piecesRaised(),
                "raising a hedge is work, and there is nobody left to do it");
    }

    @Test
    void aPieceIsNotMendedInATownWithNobodyLeft() {
        Settlement town = dressableTown();
        Furnishings.advance(town, CTX);
        assertTrue(Furnishings.mayDraw(town, 0));
        assertTrue(Furnishings.mayMend(town, 0));
        for (Person person : List.copyOf(town.residents())) {
            town.removeResident(person.id());
        }
        assertTrue(Furnishings.mayDraw(town, 0),
                "the gardens a town kept while it lived stay standing");
        assertFalse(Furnishings.mayMend(town, 0),
                "and putting one back is a morning with a spade, which needs hands");
    }

    /** A town that has finished everything and is entitled to dress itself. */
    private static Settlement dressableTown() {
        Settlement town = ringTown();
        house(town, "civilization:house", -24, -24, 0);
        house(town, "civilization:house", 24, 24, 2);
        // The roads are open and the lamps are all up: the two works that come
        // before this one.
        town.setLightsRaised(LightPlanner.wanted(town));
        town.stores().add(TownStores.WOOD, 512);
        town.stores().add(TownStores.STONE, 512);
        town.stores().add(TownStores.SAPLINGS, 64);
        return town;
    }

    // --- the churchyard ------------------------------------------------------

    /**
     * Buries a full row without changing how many people the town has.
     *
     * <p>Each of the dead is added and then buried, so the roster comes out
     * exactly as it went in. That matters: an orchard is counted off the
     * residents, so killing the town's people to give it a graveyard would
     * quietly be testing a different plan.
     */
    private static void fillTheChurchyard(Settlement town) {
        for (int i = 0; i < Settlement.DEAD_REMEMBERED; i++) {
            Person lost = new Person(
                    Person.Id.random(), "Lost " + i, Profession.FARMER, town.center());
            town.addResident(lost);
            town.bury(lost.id(), i);
        }
    }

    @Test
    void aTownThatHasLostNobodyHasNoGraveyard() {
        Settlement town = dressableTown();
        assertEquals(0, countOf(town, Furnishings.Piece.GRAVE),
                "a town with no dead standing a row of headstones would be a"
                        + " memorial to nobody");
    }

    @Test
    void oneStoneForEachOfTheDead() {
        Settlement town = dressableTown();
        for (int buried = 1; buried <= 4; buried++) {
            Person lost = new Person(
                    Person.Id.random(), "Lost " + buried, Profession.MINER, town.center());
            town.addResident(lost);
            town.bury(lost.id(), buried);
            assertEquals(buried, countOf(town, Furnishings.Piece.GRAVE),
                    "the town has buried " + buried + " and the plan says"
                            + " otherwise — the dressing memo has not noticed a"
                            + " death, which is the one input to it that is not"
                            + " the town's shape");
        }
    }

    /**
     * The row lies out past the last roof, which is what "on the outer verge"
     * means and is the whole of why a graveyard reads as one.
     */
    @Test
    void theGravesLieBeyondTheLastHouse() {
        Settlement town = dressableTown();
        fillTheChurchyard(town);
        double lastRoof = 0;
        for (Building building : town.buildings()) {
            lastRoof = Math.max(lastRoof,
                    building.origin().horizontalDistance(town.center()));
        }
        int stones = 0;
        for (Furnishings.Furnishing piece : Furnishings.pieces(town)) {
            if (piece.piece() != Furnishings.Piece.GRAVE) {
                continue;
            }
            stones++;
            assertTrue(piece.at().horizontalDistance(town.center()) > lastRoof,
                    "a headstone at " + piece.at() + " stands nearer the middle"
                            + " than the outermost house, which is a grave in"
                            + " somebody's front garden");
        }
        assertTrue(stones > 0, "a town with twelve dead planned no stones at all");
    }

    /**
     * A burial appends a stone rather than moving the ones already standing.
     *
     * <p>Which is what makes the count the town saves keep meaning the same
     * piece: a churchyard that reshuffled itself every time somebody died would
     * send a builder to a stone that is already up and leave the newest grave
     * unmarked for ever.
     */
    @Test
    void aBurialAppendsAStoneRatherThanMovingTheOnesAlreadyStanding() {
        Settlement town = dressableTown();
        List<Furnishings.Furnishing> before = List.of();
        for (int buried = 1; buried <= 6; buried++) {
            Person lost = new Person(
                    Person.Id.random(), "Lost " + buried, Profession.IDLER, town.center());
            town.addResident(lost);
            town.bury(lost.id(), buried);
            List<Furnishings.Furnishing> now = Furnishings.graves(town);
            for (int i = 0; i < before.size(); i++) {
                assertEquals(before.get(i), now.get(i),
                        "stone " + i + " moved when the town buried somebody"
                                + " else, so it is no longer the stone the count"
                                + " already standing named");
            }
            before = now;
        }
    }

    /** A row: laid on one line, evenly, rather than scattered over a field. */
    @Test
    void theStonesStandInARow() {
        Settlement town = dressableTown();
        fillTheChurchyard(town);
        List<Furnishings.Furnishing> row = Furnishings.graves(town);
        assertTrue(row.size() >= 2, "too few stones to be a row");
        boolean sameX = true;
        boolean sameZ = true;
        for (Furnishings.Furnishing stone : row) {
            sameX &= stone.at().x() == row.getFirst().at().x();
            sameZ &= stone.at().z() == row.getFirst().at().z();
        }
        assertTrue(sameX || sameZ,
                "the stones are scattered rather than laid in a line, so what the"
                        + " town has out past its last house is not a churchyard");
        for (int i = 1; i < row.size(); i++) {
            int apart = Math.abs(row.get(i).at().x() - row.get(i - 1).at().x())
                    + Math.abs(row.get(i).at().z() - row.get(i - 1).at().z());
            // On the row's own spacing, and a multiple of it rather than exactly
            // it: a slot the siting refuses is skipped and the row goes on past
            // it, so a plot nobody has used yet is allowed. What is not allowed
            // is a stone off the line, or two in the same place.
            assertTrue(apart > 0 && apart % Furnishings.GRAVES_APART == 0,
                    "stone " + i + " is " + apart + " from the one before it,"
                            + " which is not a slot in the row at all");
        }
    }

    /** Everybody buries their dead, including the two peoples who dress nothing else. */
    @Test
    void evenAWarhostAndAGoblinCampRaiseAStone() {
        for (FurnishingStyle style : FurnishingStyle.values()) {
            assertTrue(style.raises(Furnishings.Piece.GRAVE),
                    style + " leaves its dead where they fell, which is a claim"
                            + " about them this mod has not earned");
        }
    }

    // --- the one thing the dressing writes down ------------------------------

    // --- how full a heap stands ----------------------------------------------

    @Test
    void aHeapClimbsAStepForEachStackTheStoreHolds() {
        assertEquals(1, Furnishings.coursesFor(0, 64));
        assertEquals(1, Furnishings.coursesFor(63, 64));
        assertEquals(2, Furnishings.coursesFor(64, 64));
        assertEquals(3, Furnishings.coursesFor(128, 64));
        assertEquals(Furnishings.HEAP_STEPS, Furnishings.coursesFor(100000, 64),
                "the top step is open-ended: past a point a woodpile is a woodpile");
        assertEquals(1, Furnishings.coursesFor(-40, 64),
                "a negative stock is not a negative pile");
    }

    /**
     * A woodpile reads the camp's timber and a rick reads the granary's grain,
     * and everything else stands at its full height whatever the town holds.
     */
    @Test
    void onlyTheTwoHeapsReadTheStoresAtAll() {
        Settlement town = dressableTown();
        Furnishings.Furnishing pile = new Furnishings.Furnishing(
                town.center(), Furnishings.Piece.WOODPILE, 0);
        Furnishings.Furnishing rick = new Furnishings.Furnishing(
                town.center(), Furnishings.Piece.HAYSTACK, 0);
        Furnishings.Furnishing fence = new Furnishings.Furnishing(
                town.center(), Furnishings.Piece.HEDGE, 0);
        assertEquals(Furnishings.HEAP_STEPS, Furnishings.heapOf(town, fence),
                "a hedge got shorter because the granary was empty");

        // dressableTown has no camp and no granary, so both fall back on what the
        // town holds altogether -- and it was given 512 wood and no grain.
        assertEquals(Furnishings.HEAP_STEPS, Furnishings.heapOf(town, pile),
                "five hundred logs and a pile one course high");
        assertEquals(1, Furnishings.heapOf(town, rick),
                "no grain anywhere and a rick standing at its tallest");
    }

    @Test
    void aCampWithATimberYardOfItsOwnIsWhatTheWoodpileReads() {
        Settlement town = dressableTown();
        Building camp = house(town, "civilization:lumber_camp", 24, -24, 0);
        Furnishings.Furnishing pile = new Furnishings.Furnishing(
                town.center(), Furnishings.Piece.WOODPILE, 0);
        assertEquals(1, Furnishings.heapOf(town, pile),
                "the camp's own yard is empty and the pile beside it is full,"
                        + " which is the pile telling a lie about the camp");
        camp.stores().add(TownStores.WOOD, Furnishings.TIMBER_PER_COURSE);
        assertEquals(2, Furnishings.heapOf(town, pile));
        camp.stores().add(TownStores.WOOD, Furnishings.TIMBER_PER_COURSE);
        assertEquals(Furnishings.HEAP_STEPS, Furnishings.heapOf(town, pile));
    }

    /**
     * And the one thing that must not follow from any of it: the plan itself
     * does not move when a log does.
     *
     * <p>{@code Furnishings.pieces} is memoized on the town's shape, and a stock
     * changes every step. Folding how full a heap is into that memo would throw
     * the most expensive plan in the mod away several times a second — so the
     * height is asked separately, at the moment a piece is drawn, and the list
     * of pieces has to come out identical either way.
     */
    @Test
    void movingTheStoreDoesNotMoveThePlan() {
        Settlement town = dressableTown();
        Building camp = house(town, "civilization:lumber_camp", 24, -24, 0);
        List<Furnishings.Furnishing> before = Furnishings.pieces(town);
        camp.stores().add(TownStores.WOOD, 4096);
        assertEquals(before, Furnishings.pieces(town),
                "a load of timber arriving re-sited the town's dressing");
    }

    // --- the thirteenth death ------------------------------------------------

    /** Buries {@code many} people without changing how many the town has. */
    private static void buryAll(Settlement town, int many) {
        for (int i = 0; i < many; i++) {
            Person lost = new Person(
                    Person.Id.random(), "Lost " + i, Profession.FARMER, town.center());
            town.addResident(lost);
            town.bury(lost.id(), i);
        }
    }

    /**
     * The row goes on growing after the roster of names has stopped.
     *
     * <p>The whole of the fault. The stones used to be planned one per entry in
     * {@code Settlement.dead}, which is bounded at a dozen — so a town's
     * thirteenth burial planned no thirteenth stone, and worse, the twelve it
     * did plan were paired to the roster by index, so every board in the
     * churchyard was recut with the name of the person buried after the one it
     * was raised for.
     */
    @Test
    void theRowGrowsPastTheDozenTheTownRemembersNamesFor() {
        Settlement town = dressableTown();
        buryAll(town, Settlement.DEAD_REMEMBERED + 5);
        assertEquals(Settlement.DEAD_REMEMBERED, town.dead().size(),
                "the names are still bounded, which is the point of the count");
        assertEquals(Settlement.DEAD_REMEMBERED + 5, town.buried());
        assertEquals(Settlement.DEAD_REMEMBERED + 5,
                Furnishings.graves(town).size(),
                "the churchyard stopped at a dozen, so five people the town"
                        + " buried have no stone and never will");
    }

    /**
     * A stone's slot is its burial number and never moves.
     *
     * <p>Asked across the boundary the bug lived on: the row is snapshotted at
     * twelve burials, six more people are buried, and every stone already
     * standing has to be exactly where it was. If a slot moved, the board on it
     * is now somebody else's board — which is the whole reason the count is
     * written down at all.
     */
    @Test
    void aStoneKeepsItsSlotWhenTheOldestNameFallsOff() {
        Settlement town = dressableTown();
        buryAll(town, Settlement.DEAD_REMEMBERED);
        List<Furnishings.Furnishing> before = Furnishings.graves(town);
        buryAll(town, 6);
        List<Furnishings.Furnishing> after = Furnishings.graves(town);
        assertTrue(after.size() > before.size(), "no new stone was planned at all");
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i), after.get(i),
                    "stone " + i + " moved when six more people were buried, so"
                            + " the board on it now names the wrong person");
        }
    }

    /**
     * The offset between the row and the roster, which is what says whose name
     * a stone still has.
     */
    @Test
    void whatHasAgedOffIsTheGapBetweenTheRowAndTheRoster() {
        Settlement town = dressableTown();
        buryAll(town, 5);
        assertEquals(0, town.agedOff(), "nothing has fallen off a roster of five");

        Settlement older = dressableTown();
        buryAll(older, Settlement.DEAD_REMEMBERED + 5);
        assertEquals(5, older.agedOff(),
                "five names fell off, so the roster now starts at burial five");
        // Which is the assertion the boards depend on: the first name the town
        // still has is the one buried agedOff() places into the row, so stone
        // five is the stone it belongs on and stones nought to four have none.
        assertEquals("Lost " + older.agedOff(), older.dead().getFirst().name(),
                "the offset is the only arithmetic in this unit and it is off by"
                        + " one, which means every board is off by one");
    }

    /**
     * A century of deaths does not ring the town.
     *
     * <p>The row is capped at {@link Furnishings#GRAVES_PLANNED} stones and the
     * cap is a stop rather than a window: once it is reached the churchyard is
     * simply full and stays exactly as it stands, which is both what a
     * churchyard does and the only reading whose slots stay inside the claim.
     */
    @Test
    void theChurchyardFillsUpRatherThanRingingTheVillage() {
        Settlement town = dressableTown();
        buryAll(town, Furnishings.GRAVES_PLANNED);
        List<Furnishings.Furnishing> full = Furnishings.graves(town);
        assertTrue(full.size() <= Furnishings.GRAVES_PLANNED,
                "the cap is not a cap: " + full.size() + " stones planned");
        buryAll(town, 80);
        assertEquals(full, Furnishings.graves(town),
                "eighty more deaths moved the churchyard, which is eighty stones"
                        + " of cobble laid round the outside of a village");
    }

    @Test
    void aRestoredCountIsNeverSmallerThanTheNamesItCameBackWith() {
        Settlement town = ringTown();
        town.restoreDead(List.of(
                new Settlement.Grave("Ada", Profession.FARMER, 1),
                new Settlement.Grave("Bran", Profession.MINER, 2)));
        town.restoreBuried(0);
        assertEquals(2, town.buried(),
                "a town with two names in its save has buried at least two, and a"
                        + " total under that would plan a churchyard shorter than"
                        + " the roster it is read against");
        town.restoreBuried(40);
        assertEquals(40, town.buried());
    }

    @Test
    void theDeadAreBoundedAndTheOldestFallOff() {
        Settlement town = ringTown();
        for (int i = 0; i < Settlement.DEAD_REMEMBERED * 3; i++) {
            Person lost = new Person(
                    Person.Id.random(), "Lost " + i, Profession.IDLER, town.center());
            town.addResident(lost);
            town.bury(lost.id(), i);
        }
        assertEquals(Settlement.DEAD_REMEMBERED, town.dead().size(),
                "a town that buried four hundred over a century would carry four"
                        + " hundred names in its save");
        assertEquals("Lost " + (Settlement.DEAD_REMEMBERED * 3 - 1),
                town.dead().getLast().name(),
                "the newest death is missing, so the row shows the wrong dozen");
        assertEquals("Lost " + (Settlement.DEAD_REMEMBERED * 2),
                town.dead().getFirst().name(),
                "the oldest is what falls off, not the newest");
    }

    @Test
    void restoringMoreDeadThanATownKeepsStillKeepsOnlyADozen() {
        Settlement town = ringTown();
        List<Settlement.Grave> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < Settlement.DEAD_REMEMBERED * 2; i++) {
            tooMany.add(new Settlement.Grave("Lost " + i, Profession.IDLER, i));
        }
        town.restoreDead(tooMany);
        assertEquals(Settlement.DEAD_REMEMBERED, town.dead().size(),
                "a save written by a build with a larger cap, or edited by hand,"
                        + " must not stand a hundred stones round a village");
    }

    @Test
    void aStoneSaysWhoItIsForAndWhatTheyDid() {
        assertEquals("Ada Baker, Farmer",
                new Settlement.Grave("Ada Baker", Profession.FARMER, 12).epitaph());
        assertEquals("Ada Baker",
                new Settlement.Grave("Ada Baker", Profession.IDLER, 12).epitaph(),
                "\"Ada Baker, Idler\" is not an epitaph, it is an insult");
    }

    @Test
    void aDeathIsWhatRaisesAStoneAndADepartureIsNot() {
        Settlement town = ringTown();
        Person gone = new Person(
                Person.Id.random(), "Emigrant", Profession.PIONEER, town.center());
        town.addResident(gone);
        town.removeResident(gone.id());
        assertTrue(town.dead().isEmpty(),
                "a settler who walked away to found a village is not dead, and a"
                        + " stone with their name on it would be a lie the town"
                        + " tells for ever");
    }

    private static int countOf(Settlement town, Furnishings.Piece kind) {
        int found = 0;
        for (Furnishings.Furnishing piece : Furnishings.pieces(town)) {
            found += piece.piece() == kind ? 1 : 0;
        }
        return found;
    }

    // --- the numbers the brief asked for -------------------------------------

    /**
     * How much dressing a town of each size calls for, and how much of its own
     * ground it covers.
     *
     * <p>Printed rather than only asserted, because the question the brief asks —
     * "is fifteen buildings' worth of town dressed enough to look lived in, and
     * sixty not so dressed it looks like a garden center" — is a judgement
     * somebody has to make off a number, and a number nobody can find again is
     * not evidence. The assertion under it is the only part that can fail: a town
     * whose dressing covered most of its own interior would have paved the place.
     */
    @Test
    void reportsWhatATownOfEachSizeCallsFor() {
        RecordedTerrain ground = RecordedTerrain.of(RecordedTerrain.SEED_8675309);
        int[] marks = {15, 30, 60};
        for (String layout : List.of(Culture.LAYOUT_RADIAL_CONCENTRIC,
                Culture.LAYOUT_RING_STREETS, Culture.LAYOUT_GREEN)) {
            // Grown once and read at three sizes rather than grown three times:
            // a town of sixty passes through fifteen and thirty on its way, and
            // growing it again to stop earlier is the same two thousand steps
            // paid for twice.
            Settlement town = new Settlement(Settlement.Id.random(), "Report", CENTER, 256);
            town.setCatalog(BuildCatalog.DEFAULT);
            town.setStage(SettlementStage.CAMP);
            town.setCultureId(cultureFor(layout).id());
            town.setLayoutId(layout);
            for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
                town.addResident(new Person(
                        Person.Id.random(), name, Profession.PIONEER, CENTER));
            }
            int next = 0;
            for (int step = 1; step <= REPORT_STEPS && next < marks.length; step++) {
                town.stores().add(TownStores.WOOD, 24);
                town.stores().add(TownStores.STONE, 18);
                town.stores().add(TownStores.SAPLINGS, 2);
                town.step(new SimContext(ground, step, SimSettings.SANDBOX));
                if (town.buildings().size() >= marks[next]) {
                    report(layout, town);
                    next++;
                }
            }
            // Whatever it reached, if it stopped growing before sixty. A town that
            // stops is itself a number worth having, and a silently missing row is
            // not.
            if (next < marks.length) {
                report(layout, town);
            }
        }
    }

    /** How far the report grows a town: far enough for sixty buildings. */
    private static final int REPORT_STEPS = 2000;

    private static void report(String layout, Settlement town) {
        List<Furnishings.Furnishing> pieces = Furnishings.pieces(town);
        // And what the plan costs, which matters as much as what is in it: this is
        // derived rather than stored, so it is wanted by the clock, by the foreman
        // and by the drawing sweep, several times a tick on a town somebody is
        // standing in. Three numbers rather than one, best of five each.
        //
        // COLD is working it out from nothing, which happens once per change to the
        // town's shape. WARM is what every other caller pays: the walk of the
        // buildings, streets and wall that decides the plan is still the right one.
        // LAMPS is how much of COLD is LightPlanner's rather than this work's — the
        // siting has to ask where the lamps are, because a hedge may not swallow
        // one, and on a grown town that question is two thirds of the whole bill.
        long cold = Long.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            town.cacheDressing(0L, null);   // forget the plan, so the next one is real
            long began = System.nanoTime();
            Furnishings.pieces(town);
            cold = Math.min(cold, System.nanoTime() - began);
        }
        long warm = Long.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            long began = System.nanoTime();
            Furnishings.pieces(town);
            warm = Math.min(warm, System.nanoTime() - began);
        }
        long lamps = Long.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            long began = System.nanoTime();
            LightPlanner.lamps(town);
            lamps = Math.min(lamps, System.nanoTime() - began);
        }
        long covered = 0;
        TreeMap<String, Integer> byKind = new TreeMap<>();
        for (Furnishings.Furnishing piece : pieces) {
            long side = 2L * piece.reach() + 1;
            covered += side * side;
            byKind.merge(piece.piece().name(), 1, Integer::sum);
        }
        long interior = interiorArea(town);
        double share = interior <= 0 ? 0 : 100.0 * covered / interior;
        System.out.printf(
                "FURNISHINGS %s buildings=%d people=%d pieces=%d covered=%d"
                        + " interior=%d share=%.1f%% coldMicros=%d warmMicros=%d"
                        + " lampsMicros=%d %s%n",
                layout, town.buildings().size(), town.residents().size(),
                pieces.size(), covered, interior, share,
                cold / 1000, warm / 1000, lamps / 1000, byKind);
        assertTrue(share < 50.0,
                layout + " at " + town.buildings().size() + " buildings dresses "
                        + String.format("%.1f", share) + "% of its own interior,"
                        + " which is not a town with gardens in it, it is a"
                        + " garden with a town in it");
    }

    /** The ground the town's opened streets enclose, by the shoelace formula. */
    private static long interiorArea(Settlement town) {
        List<SimPos> hull = InteriorClearing.outline(town);
        if (hull.size() < 3) {
            return 0;
        }
        long twice = 0;
        for (int i = 0; i < hull.size(); i++) {
            SimPos a = hull.get(i);
            SimPos b = hull.get((i + 1) % hull.size());
            twice += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        return Math.abs(twice) / 2;
    }
}
