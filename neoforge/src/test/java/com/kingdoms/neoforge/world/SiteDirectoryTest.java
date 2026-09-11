package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The line a player reads, and the component the needle reads.
 *
 * <p>Both halves of "locatable at once" come down to two things that can be
 * checked without a world. The message is a pure function of a name and a
 * displacement, and the wayfinder's target is a vanilla data component whose
 * only interesting property — that it survives being ticked — is decided by one
 * boolean.
 *
 * <p><strong>What this environment cannot do.</strong> Item stacks. See the note
 * on {@code StoreChestBlockEntityTest}: the JUnit game populates the registries
 * but never binds item components, so {@code new ItemStack(...)} throws and
 * {@code WayfinderItem.aimAt} cannot be called here. What it writes is checked
 * one level down instead, on the component itself. Whether the needle actually
 * turns is a playtest check and is written up as one.
 */
class SiteDirectoryTest {

    @Test
    @DisplayName("the eight points read the way a player standing there would")
    void theEightPointsReadTheWayAPlayerStandingThereWould() {
        // Minecraft's north is negative z. Getting this backwards is the classic
        // way a direction indicator ends up naming the reflection of the truth,
        // and it is invisible in every test that only checks "some letter came
        // back", so each cardinal is pinned by hand.
        assertEquals("N", SiteDirectory.bearing(0, -100));
        assertEquals("S", SiteDirectory.bearing(0, 100));
        assertEquals("E", SiteDirectory.bearing(100, 0));
        assertEquals("W", SiteDirectory.bearing(-100, 0));
        assertEquals("NE", SiteDirectory.bearing(100, -100));
        assertEquals("SE", SiteDirectory.bearing(100, 100));
        assertEquals("SW", SiteDirectory.bearing(-100, 100));
        assertEquals("NW", SiteDirectory.bearing(-100, -100));
    }

    @Test
    @DisplayName("every displacement gets one of the eight, including nowhere")
    void everyDisplacementGetsOneOfTheEightIncludingNowhere() {
        assertNotNull(SiteDirectory.bearing(0, 0));
        for (int dx = -400; dx <= 400; dx += 7) {
            for (int dz = -400; dz <= 400; dz += 7) {
                String point = SiteDirectory.bearing(dx, dz);
                assertTrue(point.length() <= 2 && !point.isEmpty(),
                        "bearing(" + dx + ", " + dz + ") = " + point);
            }
        }
    }

    @Test
    @DisplayName("the eighths fall on the boundaries a compass rose has")
    void theEighthsFallOnTheBoundariesACompassRoseHas() {
        // A twenty-two-and-a-half degree wedge each way from due north, and the
        // wedge next door beyond it. Rounding at the seam is what decides
        // whether a player told "NE" walks into the town or past its shoulder.
        assertEquals("N", SiteDirectory.bearing(40, -100));    // about 22 degrees
        assertEquals("NE", SiteDirectory.bearing(45, -100));   // about 24
        assertEquals("N", SiteDirectory.bearing(-40, -100));
        assertEquals("NW", SiteDirectory.bearing(-45, -100));
    }

    @Test
    @DisplayName("a raised town is named and an unraised one is described")
    void aRaisedTownIsNamedAndAnUnraisedOneIsDescribed() {
        assertEquals("  Haldstead — 200 blocks E",
                SiteDirectory.line("Haldstead", true, 200, 0));
        assertEquals("  a Norman crossroads — 200 blocks E (not raised yet)",
                SiteDirectory.line("a Norman crossroads", false, 200, 0));
    }

    @Test
    @DisplayName("the distance is the walk, not either leg of it")
    void theDistanceIsTheWalkNotEitherLegOfIt() {
        assertTrue(SiteDirectory.line("Town", true, 300, 400).contains("500 blocks"),
                SiteDirectory.line("Town", true, 300, 400));
        assertTrue(SiteDirectory.line("Town", true, 0, 0).contains("0 blocks"));
    }

    @Test
    @DisplayName("a people and a shape read as words, not as identifiers")
    void aPeopleAndAShapeReadAsWordsNotAsIdentifiers() {
        assertEquals("a Norman crossroads",
                SiteDirectory.describe("kingdoms:norman", "kingdoms:crossroads"));
        assertEquals("a Burgher high street",
                SiteDirectory.describe("kingdoms:burgher", "kingdoms:high_street"));
        assertEquals("an Orc warren",
                SiteDirectory.describe("kingdoms:orc", "kingdoms:warren"));
        assertEquals("high street", SiteDirectory.readable("kingdoms:high_street"));
        assertEquals("plain", SiteDirectory.readable("plain"));
    }

    @Test
    @DisplayName("the heading says how far it looked either way")
    void theHeadingSaysHowFarItLookedEitherWay() {
        assertTrue(SiteDirectory.heading(0).contains(String.valueOf(SiteDirectory.EARSHOT)));
        assertTrue(SiteDirectory.heading(3).contains(String.valueOf(SiteDirectory.EARSHOT)));
        assertTrue(SiteDirectory.heading(0).startsWith("No settlement"));
    }

    @Test
    @DisplayName("an untracked lodestone target survives being ticked")
    void anUntrackedLodestoneTargetSurvivesBeingTicked() {
        // The single fact the wayfinder rests on. LodestoneTracker.tick clears
        // the target of a TRACKED compass the moment the block it names is not
        // a lodestone POI, and a town square is not a lodestone — so a tracked
        // wayfinder would blank itself on its first tick in an inventory.
        // Untracked, tick returns before it ever looks at the level, which is
        // why a null level is safe to hand it here and why the flag is written
        // false in WayfinderItem.aimAt.
        LodestoneTracker aimed = new LodestoneTracker(
                Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(1184, 71, -2720))),
                false);
        assertSame(aimed, aimed.tick(null));
        assertEquals(new BlockPos(1184, 71, -2720), aimed.target().orElseThrow().pos());
    }

    @Test
    @DisplayName("the wayfinder is a registered item with a needle to draw")
    void theWayfinderIsARegisteredItemWithANeedleToDraw() {
        assertTrue(BuiltInRegistries.ITEM
                        .getOptional(Identifier.parse("kingdoms:wayfinder")).isPresent(),
                "kingdoms:wayfinder is not in the item registry");
        String definition = resource("assets/kingdoms/items/wayfinder.json");
        // The needle is vanilla's, driven by the vanilla component. If either of
        // these two strings goes, the item renders as a still picture of a
        // compass and nothing says so.
        assertTrue(definition.contains("\"minecraft:compass\""), definition);
        assertTrue(definition.contains("\"lodestone\""), definition);
        assertTrue(definition.contains("minecraft:item/compass_31"), "not all 32 needles");
        assertTrue(resource("data/kingdoms/recipe/wayfinder.json")
                .contains("kingdoms:wayfinder"));
    }

    private static String resource(String path) {
        try (InputStream in = SiteDirectoryTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException broken) {
            throw new AssertionError("could not read " + path, broken);
        }
    }
}
