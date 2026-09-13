package com.civilization.sim;

import com.civilization.sim.settlement.BlueprintCheck;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.Field;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules a hand-authored building is held to, and why each one exists.
 *
 * <p>Every case here is a real way to build a perfectly nice-looking house that
 * quietly breaks a town. That is the whole difficulty with authored content and
 * the reason this check exists at all: none of these faults is visible in the
 * building. A cottage two blocks too wide looks like a cottage. A cottage with
 * two beds in it looks like a cottage. The town is what goes wrong, days later,
 * somewhere else.
 */
class BlueprintCheckTest {

    private static final String COTTAGE = "civilization:cottage";

    /**
     * A file that is everything the declared cottage is: seven by seven, three
     * beds, a post, a door, nothing outside its own walls.
     */
    private static BlueprintCheck.Survey goodCottage() {
        return new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                3, true, true, 0, 0, 0, 0);
    }

    private static boolean refuses(BlueprintCheck.Survey survey) {
        return !BlueprintCheck.passes(BlueprintCheck.of(survey));
    }

    private static boolean says(BlueprintCheck.Survey survey, String fragment) {
        for (BlueprintCheck.Finding finding : BlueprintCheck.of(survey)) {
            if (finding.message().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    // --- the file that is fine ------------------------------------------------

    @Test
    void aFileThatMatchesTheDrawnBuildingPassesWithNothingToSay() {
        List<BlueprintCheck.Finding> findings = BlueprintCheck.of(goodCottage());

        assertTrue(findings.isEmpty(),
                "a file that agrees with every table has nothing wrong with it: " + findings);
    }

    @Test
    void aFileSmallerThanTheDeclaredSizeIsFine() {
        // Smaller is allowed and larger is not, which is not symmetry for its own
        // sake: the plan reserved ground for the declared size, so anything that
        // fits inside it fits. A five-wide cottage on seven blocks of ground is
        // a cottage with elbow room.
        BlueprintCheck.Survey small = new BlueprintCheck.Survey(COTTAGE, 5, 5, 5,
                3, true, true, 0, 0, 0, 0);

        assertFalse(refuses(small));
    }

    // --- the size -------------------------------------------------------------

    @Test
    void aFileBiggerThanTheDeclaredSizeIsRefusedAsASizeMismatch() {
        // Nine by nine where the catalog reserved ground for seven. Placed, its
        // walls go through whatever the plan put on the next plot, and the town
        // goes on believing the neighbour is standing.
        BlueprintCheck.Survey big = new BlueprintCheck.Survey(COTTAGE, 9, 9, 6,
                3, true, true, 0, 0, 0, 0);

        assertTrue(refuses(big));
        assertTrue(says(big, "SIZE MISMATCH"));
    }

    @Test
    void anEvenSpanIsRefused() {
        // A building is placed about a middle cell and turned about it. An
        // eight-wide building has no middle cell, so every quarter turn moves it
        // half a block -- and it moves in a different direction each time.
        BlueprintCheck.Survey even = new BlueprintCheck.Survey(COTTAGE, 6, 7, 6,
                3, true, true, 0, 0, 0, 0);

        assertTrue(refuses(even));
        assertTrue(says(even, "both spans must be odd"));
    }

    @Test
    void aFileWithNoVolumeIsRefusedAndSaysSoOnce() {
        BlueprintCheck.Survey empty = new BlueprintCheck.Survey(COTTAGE, 0, 0, 0,
                0, false, false, 0, 0, 0, 0);

        assertTrue(refuses(empty));
        assertTrue(says(empty, "no volume"));
    }

    // --- the height, which is the third span and was not checked at all -------

    @Test
    void aFileTallerThanTheDeclaredHeightIsRefusedAsASizeMismatch() {
        // The declared height is how far up the site is cleared and how tall the
        // surveyor's lamp draws the plot's box. A file that outgrows it is a
        // building with the hillside still standing through its roof -- and unlike
        // the width, that is not about the neighbours, so it is stated as its own
        // line rather than folded into theirs.
        int declared = BuildingSizes.of(COTTAGE).height();
        BlueprintCheck.Survey tall = new BlueprintCheck.Survey(COTTAGE, 7, 7, declared + 4,
                3, true, true, 0, 0, 0, 0);

        assertTrue(refuses(tall));
        assertTrue(says(tall, "courses tall"));
        assertTrue(says(tall, "SIZE MISMATCH"),
                "the line somebody greps the log for, which is what the placer"
                        + " matches on to refuse the file");
    }

    @Test
    void aFileExactlyAsTallAsDeclaredIsFine() {
        // The ceiling is inclusive: the tallest roof any culture puts on a cottage
        // is the declared figure, so a file that reaches it is the drawn building
        // and not an overgrown one.
        int declared = BuildingSizes.of(COTTAGE).height();
        BlueprintCheck.Survey exact = new BlueprintCheck.Survey(COTTAGE, 7, 7, declared,
                3, true, true, 0, 0, 0, 0);

        assertFalse(refuses(exact), "a file at the declared height is refused");
    }

    // --- the anchor -----------------------------------------------------------

    @Test
    void anAnchorOffTheMiddleIsWarnedAboutRatherThanObeyed() {
        // The fault this rule exists for is not in the file, it is in what the
        // mod would do with it. A plot is a point, a Footprint is measured about
        // that point, and every overlap check compares two Footprints -- so a
        // structure laid with its corner on the point is displaced four blocks
        // while the town records it centered, and the plan reads the ground it is
        // actually standing in as free.
        //
        // A warning and not a refusal: the file is placed centered, so nothing is
        // displaced and nothing is lost but whatever the author meant.
        BlueprintCheck.Survey cornered = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                3, true, true, 0, 0, 0, 0, 3);

        assertFalse(refuses(cornered), "a file that will be placed correctly is not refused");
        assertTrue(says(cornered, "off the middle of its own box"));
        assertTrue(says(cornered, "the anchor is ignored"),
                "and it says what was done about it, or the author will believe"
                        + " the cell they named did something");
    }

    @Test
    void anAnchorAtTheMiddleSaysNothingAtAll() {
        assertFalse(says(goodCottage(), "off the middle"),
                "the ordinary file, which is centered, is not warned about being centered");
    }

    // --- the beds -------------------------------------------------------------

    @Test
    void aHomeWithTooFewBedsIsRefused() {
        // The town houses people by the catalog's number, not by counting beds.
        // One short means one settler walked into their house at dusk and stood
        // there all night, and nothing anywhere reports it.
        BlueprintCheck.Survey short_ = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                2, true, true, 0, 0, 0, 0);

        assertTrue(refuses(short_));
        assertTrue(says(short_, "sleeps 3"));
    }

    @Test
    void aHomeWithTooManyBedsIsRefusedToo() {
        // Exact rather than "at least": a fourth bed in a cottage is furniture
        // nobody is ever sent to, and the author almost certainly meant the
        // cottage to hold four people.
        BlueprintCheck.Survey many = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                4, true, true, 0, 0, 0, 0);

        assertTrue(refuses(many));
    }

    @Test
    void bedsInSomethingThatIsNotAHomeAreAWarningRatherThanAFault() {
        BlueprintCheck.Survey granary = new BlueprintCheck.Survey("civilization:granary",
                7, 7, 6, 2, true, true, 0, 0, 0, 0);

        assertFalse(refuses(granary), "it will work; it is simply not what the author thought");
        assertTrue(says(granary, "is not a home"));
    }

    // --- the door -------------------------------------------------------------

    @Test
    void noWayInIsRefused() {
        BlueprintCheck.Survey sealed = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                3, true, false, 0, 0, 0, 0);

        assertTrue(refuses(sealed));
        assertTrue(says(sealed, "no way through the front wall"));
    }

    @Test
    void noPostIsANoteBecauseOneWillBeAddedForYou() {
        BlueprintCheck.Survey postless = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                3, false, true, 0, 0, 0, 0);

        assertFalse(refuses(postless));
        assertTrue(says(postless, "no building post"));
    }

    // --- the footprint --------------------------------------------------------

    @Test
    void anEaveIsAllowedAndSaidOutLoud() {
        BlueprintCheck.Survey eaved = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                3, true, true, 0, 0, 0, 12);

        assertFalse(refuses(eaved));
        assertTrue(says(eaved, "eave"));
    }

    @Test
    void aWallStandingOutsideTheFootprintAtGroundLevelIsRefused() {
        // The same one block out, three courses lower. Up high it is a roof; down
        // here it is masonry on the ground the plan gave to the building next
        // door, and the plan has no idea it is there.
        BlueprintCheck.Survey low = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                3, true, true, 0, 0, 4, 0);

        assertTrue(refuses(low));
    }

    @Test
    void anythingTwoBlocksOutsideTheFootprintIsRefusedAtAnyHeight() {
        BlueprintCheck.Survey sprawling = new BlueprintCheck.Survey(COTTAGE, 7, 7, 6,
                3, true, true, 0, 9, 0, 0);

        assertTrue(refuses(sprawling));
        assertTrue(says(sprawling, "outside the footprint"));
    }

    // --- the field ------------------------------------------------------------

    @Test
    void aFarmWithNoCropsInItIsRefused() {
        // A town's whole food supply is a count of crop blocks. A field with none
        // is a farm that looks perfectly well built and starves the town.
        BlueprintCheck.Survey barren = new BlueprintCheck.Survey("civilization:farm",
                11, 11, 3, 0, true, true, 0, 0, 0, 0);

        assertTrue(refuses(barren));
        assertTrue(says(barren, "no crops"));
    }

    @Test
    void aSmallerFieldIsAWarningWithTheArithmeticInIt() {
        BlueprintCheck.Survey lean = new BlueprintCheck.Survey("civilization:farm",
                11, 11, 3, 0, true, true, Field.CROP_BLOCKS / 2, 0, 0, 0);

        assertFalse(refuses(lean), "a smaller field is a smaller field, not a broken one");
        assertTrue(says(lean, "against the drawn farm's " + Field.CROP_BLOCKS));
    }

    @Test
    void afullFieldSaysNothingAboutItsCrops() {
        BlueprintCheck.Survey full = new BlueprintCheck.Survey("civilization:farm",
                11, 11, 3, 0, true, true, Field.CROP_BLOCKS, 0, 0, 0);

        assertFalse(says(full, "crops"));
    }

    // --- the kinds nothing draws ---------------------------------------------

    @Test
    void aKindWithNoDeclaredSizeIsNotedRatherThanRefused() {
        // Somebody authoring a building the mod has never heard of is doing
        // something reasonable. There is no table to hold it to, and saying so
        // is more use than refusing it.
        BlueprintCheck.Survey unknown = new BlueprintCheck.Survey("civilization:bathhouse",
                9, 9, 7, 0, true, true, 0, 0, 0, 0);

        assertFalse(refuses(unknown));
        assertTrue(says(unknown, "no declared size"));
    }
}
