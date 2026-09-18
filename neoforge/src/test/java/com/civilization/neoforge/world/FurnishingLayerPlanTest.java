package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.work.FurnishingStyle;
import com.civilization.sim.work.Furnishings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a town's dressing is made of, for each people who raise any.
 *
 * <p>Runs without a world for the reason {@link BlueprintPlacerSizeTest} does: a
 * piece's <em>shape</em> is a property of the kind of thing it is and the people
 * raising it, and the only thing it asks the ground is where the surface of each
 * column is. So {@link FurnishingLayer#plan} takes a {@link BlueprintPlacer.Site}
 * and a flat fake satisfies it, and every culture is checked rather than the one
 * that happened to be convenient.
 *
 * <p>The tests that earn their keep are the two about survival. A hedge of leaves
 * laid without {@link LeavesBlock#PERSISTENT} decays inside a minute, and a
 * drawing sweep that re-plants it every second spends the town's whole budget on
 * twenty blocks while nothing else is ever drawn — the torch-on-a-fence fault
 * again, and just as invisible, because a bare verge reads as a verge nobody has
 * got round to. And a sapling that takes stops being a sapling, so a plan that
 * demanded it back would have a town fell its own orchard to replant it.
 */
class FurnishingLayerPlanTest {

    /** Level ground whose first free block is 64, which is where a piece stands. */
    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    /**
     * A hillside that falls a block every two paces along x.
     *
     * <p>Here because the one thing a piece does read off the ground is the
     * surface of each of its columns, and a fence that read the middle of itself
     * and stood every post at that height would float over half a garden.
     */
    private static BlueprintPlacer.Site slopeFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return true; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64 + Math.floorDiv(x, 2); }
        };
    }

    private static final SimPos AT = new SimPos(0, 64, 0);

    private static List<FurnishingLayer.Course> drawn(Culture culture,
                                                      Furnishings.Piece kind) {
        return FurnishingLayer.plan(flatFor(culture),
                new Furnishings.Furnishing(AT, kind, 0));
    }

    /** Every people the mod names, one town of each. */
    private static java.util.Collection<Culture> peoples() {
        return Culture.all();
    }

    // --- the check the whole file exists for --------------------------------

    @Test
    void everyPieceEveryPeopleRaisesIsMadeOfSomething() {
        for (Culture culture : peoples()) {
            FurnishingStyle style = FurnishingStyle.of(culture);
            for (Furnishings.Piece kind : Furnishings.Piece.values()) {
                if (!style.raises(kind)) {
                    continue;
                }
                List<FurnishingLayer.Course> plan = drawn(culture, kind);
                assertFalse(plan.isEmpty(),
                        culture.id() + " raises a " + kind + " made of no blocks at"
                                + " all, so a builder walks out there, counts the"
                                + " station done and nothing appears");
                for (FurnishingLayer.Course course : plan) {
                    assertFalse(course.state().isAir(),
                            culture.id() + "'s " + kind + " lays air, which is a"
                                    + " builder digging a hole and filling it in");
                }
            }
        }
    }

    @Test
    void noPieceEverWritesTheSameCellTwiceWithDifferentBlocks() {
        for (Culture culture : peoples()) {
            FurnishingStyle style = FurnishingStyle.of(culture);
            for (Furnishings.Piece kind : Furnishings.Piece.values()) {
                if (!style.raises(kind)) {
                    continue;
                }
                Set<BlockPos> seen = new HashSet<>();
                for (FurnishingLayer.Course course : drawn(culture, kind)) {
                    assertTrue(seen.add(course.pos()),
                            culture.id() + "'s " + kind + " writes " + course.pos()
                                    + " twice. A crew reads the course that is"
                                    + " missing off the ground, so a cell with two"
                                    + " answers is a crew that never finishes it");
                }
            }
        }
    }

    @Test
    void everyPieceStaysInsideTheGroundItWasSitedOn() {
        for (Culture culture : peoples()) {
            FurnishingStyle style = FurnishingStyle.of(culture);
            for (Furnishings.Piece kind : Furnishings.Piece.values()) {
                if (!style.raises(kind)) {
                    continue;
                }
                Furnishings.Furnishing piece = new Furnishings.Furnishing(AT, kind, 0);
                for (FurnishingLayer.Course course : FurnishingLayer.plan(
                        flatFor(culture), piece)) {
                    assertTrue(Math.abs(course.pos().getX()) <= piece.reach()
                                    && Math.abs(course.pos().getZ()) <= piece.reach(),
                            culture.id() + "'s " + kind + " draws at " + course.pos()
                                    + ", outside the " + piece.reach()
                                    + "-block box the planner cleared for it — so"
                                    + " everything the siting rules refused is back");
                }
            }
        }
    }

    // --- the two that are bugs rather than style -----------------------------

    @Test
    void noHedgeIsEverLaidWithLeavesThatWillDecay() {
        for (Culture culture : peoples()) {
            if (!FurnishingStyle.of(culture).raises(Furnishings.Piece.HEDGE)) {
                continue;
            }
            for (FurnishingLayer.Course course : drawn(culture, Furnishings.Piece.HEDGE)) {
                assertTrue(course.state().getBlock() instanceof LeavesBlock,
                        "a hedge is leaves");
                assertTrue(course.state().getValue(LeavesBlock.PERSISTENT),
                        culture.id() + " plants a hedge that vanishes inside a"
                                + " minute, and the sweep re-plants it for ever");
            }
        }
    }

    @Test
    void aGrownTreeCountsAsTheSaplingThatWasPlanted() {
        List<FurnishingLayer.Course> plan =
                drawn(Culture.of("civilization:human/norman"), Furnishings.Piece.ORCHARD);
        FurnishingLayer.Course sapling = plan.get(0);
        assertTrue(sapling.state().getBlock() instanceof SaplingBlock);
        assertTrue(FurnishingLayer.stands(Blocks.OAK_LOG.defaultBlockState(), sapling),
                "an orchard that took reads as a trunk, and a plan that wanted the"
                        + " sapling back would fell the orchard to replant it");
        assertTrue(FurnishingLayer.stands(Blocks.OAK_LEAVES.defaultBlockState(), sapling));
        assertFalse(FurnishingLayer.stands(Blocks.AIR.defaultBlockState(), sapling),
                "and bare ground is still an orchard that was eaten");
        FurnishingLayer.Course fence = new FurnishingLayer.Course(
                BlockPos.ZERO, Blocks.OAK_FENCE.defaultBlockState());
        assertFalse(FurnishingLayer.stands(Blocks.OAK_LOG.defaultBlockState(), fence),
                "a wild tree where a fence was planned is still a fence missing");
    }

    // --- what tells one people's ground from another's -----------------------

    @Test
    void aNormanYardIsOakFenceRoundCarrots() {
        List<FurnishingLayer.Course> plan =
                drawn(Culture.of("civilization:human/norman"), Furnishings.Piece.YARD);
        assertTrue(holds(plan, Blocks.OAK_FENCE), "an oak rail round it");
        assertTrue(holds(plan, Blocks.OAK_FENCE_GATE), "and a gate to get in by");
        assertTrue(holds(plan, Blocks.CARROTS), "and a crop in it");
        assertTrue(holds(plan, Blocks.FARMLAND), "over tilled ground, in that order");
        assertTrue(holds(plan, Blocks.POPPY), "and a bed of flowers along the back");
    }

    @Test
    void aBurgherMasonsWhatEverybodyElseNailsTogether() {
        List<FurnishingLayer.Course> square = FurnishingLayer.plan(
                flatFor(Culture.of("civilization:human/burgher")),
                new Furnishings.Furnishing(AT, Furnishings.Piece.SQUARE, 0));
        assertTrue(holds(square, Blocks.STONE_BRICKS),
                "the people who lay a plan before they build on it pave in brick");
        assertTrue(holds(square, Blocks.STONE_BRICK_STAIRS), "and sit on it");
    }

    @Test
    void aHighlandYardGrowsPotatoes() {
        List<FurnishingLayer.Course> plan =
                drawn(Culture.of("civilization:human/highland"), Furnishings.Piece.YARD);
        assertTrue(holds(plan, Blocks.SPRUCE_FENCE));
        assertTrue(holds(plan, Blocks.POTATOES), "which is what grows up there");
    }

    @Test
    void aGoblinCampIsDressedWithWhatWasDraggedBackToIt() {
        Culture mire = Culture.of("civilization:goblin/mire");
        assertTrue(holds(drawn(mire, Furnishings.Piece.WOODPILE), Blocks.DARK_OAK_LOG));
        assertTrue(holds(drawn(mire, Furnishings.Piece.CAGE), Blocks.DARK_OAK_FENCE));
        assertTrue(holds(drawn(mire, Furnishings.Piece.FIRE_PIT), Blocks.CAMPFIRE));
        assertFalse(FurnishingStyle.of(mire).raises(Furnishings.Piece.YARD),
                "a goblin camp with a kitchen garden is not a goblin camp");
    }

    @Test
    void aWarhostGetsLogPilesAndStakes() {
        Culture warhost = Culture.of("civilization:orc/warhost");
        assertTrue(holds(drawn(warhost, Furnishings.Piece.STAKES), Blocks.SPRUCE_FENCE));
        assertTrue(holds(drawn(warhost, Furnishings.Piece.WOODPILE), Blocks.SPRUCE_LOG));
    }

    // --- the ground ----------------------------------------------------------

    @Test
    void aYardOnAHillsideStandsEveryPostOnItsOwnColumn() {
        Culture norman = Culture.of("civilization:human/norman");
        Furnishings.Furnishing piece = new Furnishings.Furnishing(
                AT, Furnishings.Piece.YARD, 0);
        for (FurnishingLayer.Course course : FurnishingLayer.plan(
                slopeFor(norman), piece)) {
            int surface = 64 + Math.floorDiv(course.pos().getX(), 2);
            assertTrue(Math.abs(course.pos().getY() - surface) <= 1,
                    "a post at " + course.pos() + " floats over a hillside whose"
                            + " surface there is " + surface);
        }
    }

    @Test
    void thePavingReplacesTheTurfRatherThanStandingOnIt() {
        List<FurnishingLayer.Course> plan = FurnishingLayer.plan(
                flatFor(Culture.of("civilization:human/norman")),
                new Furnishings.Furnishing(AT, Furnishings.Piece.SQUARE, 0));
        boolean anySoil = false;
        for (FurnishingLayer.Course course : plan) {
            if (course.soil()) {
                anySoil = true;
                assertEquals(63, course.pos().getY(),
                        "a square you step up onto is a plinth");
            }
        }
        assertTrue(anySoil, "a square with no paving in it is a field");
    }

    @Test
    void aPieceTurnsToFaceWhateverItBelongsTo() {
        assertEquals(0, FurnishingLayer.turn(0, 1, 0)[0], "facing south: unturned");
        assertEquals(1, FurnishingLayer.turn(0, 1, 0)[1], "facing south: unturned");
        assertEquals(-1, FurnishingLayer.turn(0, 1, 1)[0], "facing west: +z becomes -x");
        assertEquals(-1, FurnishingLayer.turn(0, 1, 2)[1], "facing north: +z becomes -z");
        assertEquals(1, FurnishingLayer.turn(0, 1, 3)[0], "facing east: +z becomes +x");
    }

    // --- the boards ----------------------------------------------------------

    /**
     * A signpost's board hangs on the face toward the middle of the town and an
     * inn's hangs on the face away from the inn.
     *
     * <p>The two pieces are the same three blocks and the only thing that
     * distinguishes them on the ground is which way round the board is, so it is
     * the one thing worth pinning. {@code Furnishing.facing} says where the thing
     * a piece belongs to lies: for a signpost that is the middle of the town, and
     * a board facing the middle is a board you read walking in; for an inn board
     * that is the inn itself, and a board facing the inn is a board only the
     * innkeeper can read.
     */
    @Test
    void asignpostFacesTheMiddleAndAnInnBoardFacesTheStreet() {
        Culture norman = Culture.of("civilization:human/norman");
        // facing 0 means the thing this piece belongs to lies toward +z, which
        // FurnishingLayer.towardTheGate reads as SOUTH.
        assertEquals(net.minecraft.core.Direction.SOUTH,
                boardFace(drawn(norman, Furnishings.Piece.SIGNPOST)));
        assertEquals(net.minecraft.core.Direction.NORTH,
                boardFace(drawn(norman, Furnishings.Piece.INN_SIGN)));
    }

    /**
     * And the words go on with the board rather than beside it.
     *
     * <p>A course carries its own text, which is the whole of the widening this
     * unit made to the seam — so a plan drawn for a named town has exactly one
     * course with writing on it and every other course is bare.
     */
    @Test
    void exactlyTheBoardCarriesTheWriting() {
        Culture norman = Culture.of("civilization:human/norman");
        com.civilization.sim.work.Signage.Plaque plaque =
                new com.civilization.sim.work.Signage.Plaque("Millbrook", "town", 3,
                        "Red Lion");
        for (Furnishings.Piece kind : List.of(Furnishings.Piece.SIGNPOST,
                Furnishings.Piece.INN_SIGN, Furnishings.Piece.SQUARE)) {
            List<FurnishingLayer.Course> plan = FurnishingLayer.plan(flatFor(norman),
                    new Furnishings.Furnishing(AT, kind, 0), plaque);
            long written = plan.stream().filter(c -> !c.lines().isEmpty()).count();
            assertEquals(1, written, kind + " wrote on " + written + " courses");
            for (FurnishingLayer.Course course : plan) {
                if (course.lines().isEmpty()) {
                    continue;
                }
                assertTrue(course.state().getBlock()
                                instanceof net.minecraft.world.level.block.SignBlock,
                        kind + " wrote on something that is not a sign");
                assertEquals(4, course.lines().size());
            }
        }
    }

    /** A plan with no town behind it writes nothing, which is what every size test draws. */
    @Test
    void apieceWithNoTownBehindItCarriesNoWriting() {
        for (Culture culture : peoples()) {
            for (Furnishings.Piece kind : Furnishings.Piece.values()) {
                for (FurnishingLayer.Course course : drawn(culture, kind)) {
                    assertTrue(course.lines().isEmpty(),
                            culture.id() + " " + kind + " wrote on a blank plaque");
                }
            }
        }
    }

    /**
     * A town that has buried three people stands three stones, and each one says
     * who it is for.
     *
     * <p>The pairing is the thing being stated, not the lettering. Nothing writes
     * a name into the plan: {@code Furnishings.graves} lists the stones in the
     * order {@code Settlement.dead} lists the people, and
     * {@link FurnishingLayer#graveAt} is nothing but an index into that. So the
     * way this can break is silent and total — every stone in the churchyard
     * carrying the wrong name, or all three carrying the same one — and neither
     * would look like anything until somebody read a board.
     */
    @Test
    void everyStoneSaysWhoLiesUnderIt() {
        com.civilization.sim.settlement.Settlement town = buriedThree();
        List<Furnishings.Furnishing> stones = Furnishings.graves(town);
        assertEquals(3, stones.size(),
                "a town with three dead planned " + stones.size() + " stones");

        Culture norman = Culture.of(town.cultureId());
        List<String> wanted = List.of("Ada Baker", "Bren Smith", "Cass Fletcher");
        for (int stone = 0; stone < stones.size(); stone++) {
            List<FurnishingLayer.Course> plan = FurnishingLayer.plan(flatFor(norman),
                    stones.get(stone), com.civilization.sim.work.Signage.Plaque.NONE,
                    FurnishingLayer.graveAt(town, stones.get(stone)));
            List<String> written = boardOf(plan);
            assertFalse(written.isEmpty(),
                    "stone " + stone + " carries no writing at all");
            assertEquals(wanted.get(stone), written.get(0),
                    "stone " + stone + " is for " + written.get(0) + " and the"
                            + " town buried " + wanted.get(stone) + " in that"
                            + " place: the row and the roster have come apart");
            assertEquals("Miller", written.get(1),
                    "and the trade under the name");
        }
    }

    /**
     * A headstone is legible in a town with no name, which is the one board that
     * has to be.
     *
     * <p>Every other board is composed from a plaque and says nothing without
     * one. A grave says a person's name, and a person is dead whether or not the
     * settlement they died in has a name recorded — so a stone planned against
     * {@code Plaque.NONE} still reads.
     */
    @Test
    void aHeadstoneNeedsNoPlaque() {
        com.civilization.sim.settlement.Settlement town = buriedThree();
        Furnishings.Furnishing stone = Furnishings.graves(town).getFirst();
        assertEquals("Ada Baker",
                boardOf(FurnishingLayer.plan(flatFor(Culture.of(town.cultureId())),
                        stone, com.civilization.sim.work.Signage.Plaque.NONE,
                        FurnishingLayer.graveAt(town, stone))).getFirst());
    }

    /** The lines on the one sign block in a plan, or none if there is none. */
    private static List<String> boardOf(List<FurnishingLayer.Course> plan) {
        for (FurnishingLayer.Course course : plan) {
            if (!course.lines().isEmpty()) {
                return course.lines();
            }
        }
        return List.of();
    }

    /** A ring town with an opened street round it and three people buried. */
    private static com.civilization.sim.settlement.Settlement buriedThree() {
        SimPos center = new SimPos(0, 64, 0);
        com.civilization.sim.settlement.Settlement town =
                new com.civilization.sim.settlement.Settlement(
                        com.civilization.sim.settlement.Settlement.Id.random(),
                        "Millbrook", center, 128);
        town.setCultureId("civilization:human/norman");
        com.civilization.sim.settlement.PathNetwork paths =
                new com.civilization.sim.settlement.PathNetwork();
        List<SimPos> corners = List.of(
                new SimPos(-48, 64, -48), new SimPos(48, 64, -48),
                new SimPos(48, 64, 48), new SimPos(-48, 64, 48));
        for (int i = 0; i < corners.size(); i++) {
            paths.add(new com.civilization.sim.settlement.PathNetwork.Segment(
                    corners.get(i), corners.get((i + 1) % corners.size()), 8));
        }
        for (int i = 0; i < paths.segments().size(); i++) {
            paths.markOpened(i);
        }
        town.setPaths(paths);
        // Everybody a miller, so the trade line is the same on all three and the
        // assertion above is about the name rather than about the trade.
        for (String name : List.of("Ada Baker", "Bren Smith", "Cass Fletcher")) {
            com.civilization.sim.person.Person lost =
                    new com.civilization.sim.person.Person(
                            com.civilization.sim.person.Person.Id.random(), name,
                            com.civilization.sim.person.Profession.MILLER, center);
            town.addResident(lost);
            town.bury(lost.id(), 12);
        }
        return town;
    }

    /** Which way the one sign block in this plan is looking. */
    private static net.minecraft.core.Direction boardFace(
            List<FurnishingLayer.Course> plan) {
        for (FurnishingLayer.Course course : plan) {
            if (course.state().getBlock()
                    instanceof net.minecraft.world.level.block.SignBlock) {
                return course.state().getValue(net.minecraft.world.level.block
                        .HorizontalDirectionalBlock.FACING);
            }
        }
        return null;
    }

    private static boolean holds(List<FurnishingLayer.Course> plan,
                                 net.minecraft.world.level.block.Block block) {
        for (FurnishingLayer.Course course : plan) {
            if (course.state().is(block)) {
                return true;
            }
        }
        return false;
    }
}
