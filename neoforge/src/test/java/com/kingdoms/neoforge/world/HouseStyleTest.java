package com.kingdoms.neoforge.world;

import com.kingdoms.sim.culture.Culture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every people has a way of building, and that two houses of theirs are not
 * the same house.
 *
 * <p>Two failures, opposite to each other, and a style table can walk into
 * either:
 *
 * <ul>
 *   <li><strong>A people with no entry.</strong> A culture added to
 *       {@link Culture} and forgotten here falls back to the lowland style,
 *       which is the right behaviour and a silent one — a whole people quietly
 *       building somebody else's houses. So the table is asked for every id
 *       there is, and the fallback is proved to be a fallback rather than the
 *       answer everybody gets.</li>
 *   <li><strong>A street of one house repeated.</strong> The variation exists so
 *       that a row of cottages is a row rather than a mirror hall, and a hash
 *       that came out the same everywhere would be invisible from a unit test
 *       that only ever drew one building.</li>
 * </ul>
 *
 * <p>And the property both of those rest on: a house drawn twice at the same
 * place is the same house. A repair is the difference between the drawing and
 * the world ({@code BlueprintPlacer.owedOf}), so a drawing that wandered would
 * have a crew rebuilding a sound roof forever.
 */
class HouseStyleTest {

    private static BlueprintPlacer.Site flatFor(Culture culture) {
        return new BlueprintPlacer.Site() {
            @Override public boolean loaded(BlockPos pos) { return true; }
            @Override public boolean unsupported(BlockPos pos) { return pos.getY() >= 64; }
            @Override public Culture culture() { return culture; }
            @Override public int groundLevel(int x, int z) { return 64; }
        };
    }

    private static List<BlueprintPlacer.Placement> drawn(Culture culture, String path,
                                                         BlockPos base) {
        List<BlueprintPlacer.Placement> blocks = new ArrayList<>();
        BlueprintPlacer.draw(flatFor(culture), blocks, path, base);
        return blocks;
    }

    // --- every people builds in some way -------------------------------------

    @Test
    void everyPeopleHasAWholeStyleAndNotHalfOfOne() {
        for (Culture culture : Culture.all()) {
            HouseStyle style = HouseStyle.forCulture(culture.id());
            assertNotNull(style, culture.id() + " builds houses out of nothing");

            for (Object piece : List.of(style.wall(), style.frame(), style.roofStairs(),
                    style.roofRidge(), style.plinth(), style.gable(), style.roof(),
                    style.timber(), style.post(), style.trapdoor())) {
                assertNotNull(piece, culture.id() + " has a hole in its palette");
            }
        }
    }

    @Test
    void apeopleNobodyHasDrawnBuildsOrdinaryHousesRatherThanNone() {
        // The fallback, said out loud. A culture id from a save this build has
        // never heard of must get a house, and a null id -- which is what a world
        // written before cultures had names carries -- must not throw.
        assertEquals(HouseStyle.forCulture("kingdoms:human/norman"),
                HouseStyle.forCulture("kingdoms:nobody_has_drawn_these"));
        assertEquals(HouseStyle.forCulture("kingdoms:human/norman"),
                HouseStyle.forCulture(null));
    }

    @Test
    void thePeoplesHousesAreActuallyMadeOfDifferentThings() {
        // The whole point of the table. If two cultures draw the same set of
        // blocks then the style is a table with one row in it wearing seven hats.
        Set<Set<Block>> palettes = new HashSet<>();
        for (Culture culture : Culture.all()) {
            palettes.add(blocksIn(drawn(culture, "cottage", new BlockPos(0, 63, 0))));
        }
        assertTrue(palettes.size() >= 5,
                "seven peoples build cottages out of " + palettes.size()
                        + " distinguishable sets of blocks");
    }

    @Test
    void theOrcsKeepTheirFlatRoofBecauseThatIsTheirLook() {
        // A style is allowed to opt out of the whole vocabulary, and this is the
        // one that does. Worth pinning: a later pass that "fixed" the roofless
        // people would be undoing a decision rather than filling a gap.
        assertEquals(HouseStyle.Roof.FLAT, HouseStyle.forCulture("kingdoms:orc/warhost").roof());
        assertEquals(HouseStyle.Roof.HIP, HouseStyle.forCulture("kingdoms:human/highland").roof());
        assertEquals(HouseStyle.Roof.GABLE, HouseStyle.forCulture("kingdoms:human/norman").roof());
    }

    // --- and no two of their houses are the same one -------------------------

    @Test
    void twoHousesOfTheSamePeopleInDifferentPlacesAreNotTheSameHouse() {
        Set<HouseStyle.Variation> seen = new LinkedHashSet<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                seen.add(HouseStyle.Variation.forOrigin(new BlockPos(x * 16, 64, z * 16)));
            }
        }
        assertTrue(seen.size() >= 4,
                "a hundred and forty-four plots produced " + seen.size()
                        + " kinds of house, so a street of them is a mirror hall");
    }

    @Test
    void twoCottagesOnOneStreetReallyComeOutDifferent() {
        // The variation reaching the blocks, rather than only the record. Two
        // plots on the same pitch, which is exactly the case a hash over raw
        // coordinates would hand the same answer to.
        BlockPos first = new BlockPos(0, 63, 0);
        List<BlockPos> street = List.of(
                new BlockPos(16, 63, 0), new BlockPos(32, 63, 0),
                new BlockPos(48, 63, 0), new BlockPos(64, 63, 0));

        Set<String> ours = shape(drawn(Culture.NORMAN, "cottage", first), first);
        boolean differed = false;
        for (BlockPos neighbor : street) {
            if (!ours.equals(shape(drawn(Culture.NORMAN, "cottage", neighbor), neighbor))) {
                differed = true;
            }
        }
        assertTrue(differed, "every cottage down the street is the same cottage");
    }

    @Test
    void aHouseDrawnTwiceInOnePlaceIsTheSameHouseBothTimes() {
        // The property a repair rests on. Asserted across every people and every
        // home, because it costs nothing and the one that wandered would be the
        // one nobody thought to check.
        for (Culture culture : Culture.all()) {
            for (String home : List.of("cottage", "house", "longhouse", "croft",
                    "bunkhouse")) {
                BlockPos base = new BlockPos(112, 63, -48);
                List<BlueprintPlacer.Placement> once = drawn(culture, home, base);
                List<BlueprintPlacer.Placement> again = drawn(culture, home, base);

                assertEquals(once, again,
                        culture.id() + "'s " + home + " is drawn differently the"
                                + " second time, so a repair crew would rebuild a"
                                + " sound one forever");
            }
        }
    }

    @Test
    void aVariationIsReadOffThePlaceAndNotOffTheHeight() {
        // A building resited a course up the hill is the same building. If the
        // height were in the hash, settling a plot would redraw the house on it.
        assertEquals(HouseStyle.Variation.forOrigin(new BlockPos(80, 64, 32)),
                HouseStyle.Variation.forOrigin(new BlockPos(80, 71, 32)));
    }

    // --- helpers -------------------------------------------------------------

    private static Set<Block> blocksIn(List<BlueprintPlacer.Placement> plan) {
        Set<Block> blocks = new HashSet<>();
        for (BlueprintPlacer.Placement placement : plan) {
            blocks.add(placement.state().getBlock());
        }
        return blocks;
    }

    /** Where a building puts what, relative to its own base. */
    private static Set<String> shape(List<BlueprintPlacer.Placement> plan, BlockPos base) {
        Set<String> cells = new HashSet<>();
        for (BlueprintPlacer.Placement placement : plan) {
            cells.add(placement.pos().subtract(base).toShortString()
                    + "=" + placement.state());
        }
        return cells;
    }
}
