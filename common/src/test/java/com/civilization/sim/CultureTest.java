package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.SimPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A second people, which is what the culture type was a promise about.
 *
 * <p>The claim was that adding one is filling in a table rather than threading
 * a new idea through the simulation. These are the parts of that claim which
 * can be checked without a world: that a second entry exists, that it is
 * genuinely different, and that everything asking for a culture by name gets
 * the one it asked for rather than quietly getting the default.
 */
class CultureTest {

    @Test
    void thePeopleEveryTownHasBeenAllAlongNowHaveAName() {
        // Settlements were stamped civilization:norman and the blueprint loader was
        // looking for civilization:norman/house long before anything defined a
        // culture by that name, so every lookup fell through to the default and
        // nobody noticed — because the default was the only thing to fall
        // through to.
        assertSame(Culture.NORMAN, Culture.of("civilization:human/norman"),
                "asking for the normans should not quietly hand back the default");
        assertEquals("civilization:human/norman", Culture.NORMAN.id());
    }

    @Test
    void aSecondPeopleKeepDifferentBeasts() {
        assertNotEquals(Culture.NORMAN.pennedAnimals(), Culture.HIGHLAND.pennedAnimals(),
                "a culture that keeps the same animals in the same pens is a rename");
        assertTrue(Culture.HIGHLAND.pennedAnimals().contains("minecraft:goat"));
        assertTrue(Culture.NORMAN.pennedAnimals().contains("minecraft:cow"));
    }

    @Test
    void everyCultureFitsThePlotReservedForItsPens() {
        // The animal farm's ground is reserved in the catalog at a fixed size,
        // so a culture cannot quietly outgrow the plot set aside for it. A fifth
        // pen is a catalog change, not a table entry — which is worth failing
        // loudly here rather than discovering as a compound built through its
        // neighbor's wall.
        for (Culture culture : Culture.all()) {
            assertTrue(culture.penCount() <= 4,
                    culture.id() + " keeps " + culture.penCount()
                            + " kinds of beast, and the reserved plot holds four");
            assertTrue(culture.penCount() >= 1, culture.id() + " must keep something");
        }
    }

    @Test
    void anUnknownCultureFallsBackRatherThanFailing() {
        // A datapack naming a culture nobody shipped must give a plain town, not
        // a crash — the whole point of the id being a string.
        assertSame(Culture.DEFAULT, Culture.of("someone:nonexistent"));
        assertSame(Culture.DEFAULT, Culture.of(null));
    }

    @Test
    void everyCultureIsFindableByItsOwnName() {
        for (Culture culture : Culture.all()) {
            assertSame(culture, Culture.of(culture.id()),
                    culture.id() + " does not answer to its own name");
        }
    }

    @Test
    void everyCultureLaysItselfOutSomeWayItKnowsAbout() {
        // This used to insist on rings, which was the only honest thing to say
        // while rings were the only arrangement that existed — the field was a
        // string nothing read. Now the question is the one that was always
        // meant: does the name a culture asks for resolve to something real.
        //
        // Every name, not just the first. Layouts.of falls back to rings rather
        // than throwing, so a typo in the second entry of a list would be a
        // people who quietly build villages half the time.
        for (Culture culture : Culture.all()) {
            for (String layout : culture.layouts()) {
                assertEquals(layout, Layouts.of(layout).id(),
                        culture.id() + " asks for a layout nothing implements: " + layout);
            }
        }
    }

    @Test
    void thePeoplesDoNotAllBuildTheSameTown() {
        // The claim the culture type has been making since it was written, and
        // could not back up while every settlement was laid out in rings
        // whatever it called itself.
        assertNotEquals(Culture.NORMAN.layouts(), Culture.GOBLIN.layouts());
        assertNotEquals(Culture.GOBLIN.layouts(), Culture.ORC.layouts());
    }

    @Test
    void aPeopleAlwaysBuildsTheSameTownInTheSamePlace() {
        // The whole reason the choice is a hash of the center rather than a die
        // roll: nothing is written down until a settlement exists, so the answer
        // has to be reconstructible from the ground the town stands on.
        SimPos center = new SimPos(1_337, 72, -404);
        String first = Culture.BURGHER.layoutFor(center);
        for (int again = 0; again < 8; again++) {
            assertEquals(first, Culture.BURGHER.layoutFor(center),
                    "the same people at the same center changed their minds");
        }
        assertSame(Layouts.of(first), Culture.BURGHER.arrangementFor(center));
    }

    @Test
    void aPeopleWithSeveralArrangementsUsesAllOfThem() {
        // A picker that technically varies but lands on one entry ninety-nine
        // times in a hundred is the bug this whole unit exists to avoid: the
        // second arrangement would ship and nobody would ever see it.
        for (Culture culture : Culture.all()) {
            if (culture.layouts().size() < 2) {
                continue;
            }
            // A grid rather than a line. The first draft walked one diagonal, so
            // a picker that happened to alternate along it would have passed and
            // a retuned hash could have failed for having sampled the wrong
            // thousand blocks rather than for being wrong.
            Set<String> seen = new HashSet<>();
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    String picked = culture.layoutFor(new SimPos(x * 617, 72, z * 421));
                    assertTrue(culture.layouts().contains(picked),
                            culture.id() + " lays a town out as " + picked
                                    + ", which is not one of its own");
                    seen.add(picked);
                }
            }
            assertEquals(Set.copyOf(culture.layouts()), seen,
                    culture.id() + " never builds some of the arrangements it names");
        }
    }

    @Test
    void everyPeopleKeepsTheArrangementItAlreadyBuiltInFirst() {
        // A save written before the layout was recorded takes the head of the
        // list, so reordering one of these rearranges every town of that people
        // already standing in somebody's world.
        assertEquals(Culture.LAYOUT_RING, Culture.NORMAN.layouts().get(0));
        assertEquals(Culture.LAYOUT_RING, Culture.DEFAULT.layouts().get(0));
        assertEquals(Culture.LAYOUT_ORGANIC, Culture.HIGHLAND.layouts().get(0));
        assertEquals(Culture.LAYOUT_HIGH_STREET, Culture.BURGHER.layouts().get(0));
        assertEquals(Culture.LAYOUT_RING_STREETS, Culture.VALE.layouts().get(0));
        // The goblins are the second deliberate exception, and for the same
        // reason as the orcs below: what save compatibility was protecting here
        // was the very thing being replaced. A warren is a settled shape — its
        // first knot sits fifty-two blocks out, which is further across than a
        // whole camp — and these goblins are not settled any more. The warren is
        // still in the list.
        assertEquals(Culture.LAYOUT_GOBLIN_CAMP, Culture.GOBLIN.layouts().get(0));
        assertTrue(Culture.GOBLIN.layouts().contains(Culture.LAYOUT_WARREN));

        // The orcs are the exception, and a deliberate one. Their war camp took
        // the head of the list from the stronghold, which rearranges every orc
        // town already standing -- they had two arrangements and both were
        // rectangles, so an orc settlement read as a garrison and nothing else.
        // Save compatibility bought nothing here: what it was protecting was the
        // very thing being replaced. The two rectangles are still in the list.
        assertEquals(Culture.LAYOUT_ORC_RING, Culture.ORC.layouts().get(0));
        assertTrue(Culture.ORC.layouts().contains(Culture.LAYOUT_STRONGHOLD));
        assertTrue(Culture.ORC.layouts().contains(Culture.LAYOUT_STRONGHOLD_STREETS));
    }

    @Test
    void everyArrangementBelongsToSomebody() {
        // Two of them were registered and named by no culture at all, which made
        // them unreachable outside /civ buildtest — shipped code that no town
        // could ever be.
        Set<String> claimed = new HashSet<>();
        for (Culture culture : Culture.all()) {
            claimed.addAll(culture.layouts());
        }
        for (var layout : Layouts.all()) {
            assertTrue(claimed.contains(layout.id()),
                    layout.id() + " is an arrangement no people builds");
        }
    }

    @Test
    void aCultureDrawsFromItsOwnFolder() {
        // The style is the id's own path, derived rather than stored beside it,
        // so the two can never drift apart.
        assertEquals("norman", Culture.NORMAN.style());
        assertEquals("highland", Culture.HIGHLAND.style());
        assertEquals("default", Culture.DEFAULT.style());
    }

    @Test
    void everyCultureHasAFolderToDrawFrom() {
        // An empty style would make the placer ask for "/house", which resolves
        // to nothing and would silently drop every town of that culture back to
        // the built-in shapes.
        for (Culture culture : Culture.all()) {
            assertTrue(!culture.style().isEmpty(), culture.id() + " has no style folder");
            assertTrue(!culture.style().contains(":"),
                    culture.id() + " kept its namespace in the folder name");
            // One segment, not the race's folder and then the people's. The
            // placer composes "<style>/house"; a style of "human/norman" would
            // ask for "human/norman/house", which nothing draws.
            assertTrue(!culture.style().contains("/"),
                    culture.id() + " kept its race in the folder name: " + culture.style());
        }
    }

    @Test
    void everybodyIsBornIntoSomeBody() {
        // A culture with no race would be a people whose settlers had no health,
        // no pace and no reach -- and Race.of falls back rather than throwing, so
        // the failure would be silent normal humans rather than a crash.
        for (Culture culture : Culture.all()) {
            assertNotNull(culture.race(), culture.id() + " is born into nothing");
        }
        assertEquals(Race.HUMAN, Culture.NORMAN.race());
        assertEquals(Race.HUMAN, Culture.HIGHLAND.race());
        assertEquals(Race.HUMAN, Culture.BURGHER.race());
        assertEquals(Race.HUMAN, Culture.VALE.race());
        assertEquals(Race.ORC, Culture.ORC.race());
        assertEquals(Race.GOBLIN, Culture.GOBLIN.race());
        // The sentinel has no race segment at all and reads as human, which is
        // what every unnamed town in the mod has always been.
        assertEquals(Race.HUMAN, Culture.DEFAULT.race());
    }

    @Test
    void theHumansAreTheFourPeoplesAndTheSentinel() {
        // The grouping the user asked for by name. Four cultures, one body --
        // which is the whole distinction between the two types.
        assertEquals(
                List.of(Culture.DEFAULT, Culture.BURGHER, Culture.HIGHLAND,
                        Culture.NORMAN, Culture.VALE),
                Culture.humans(),
                "the human cultures are Norman, highland, burgher and vale, "
                        + "plus the no-culture sentinel");
        assertEquals(List.of(Culture.GOBLIN), Culture.ofRace(Race.GOBLIN));
        assertEquals(List.of(Culture.ORC), Culture.ofRace(Race.ORC),
                "orcs have one culture for now, and that is a gap in the table");
    }

    @Test
    void theRaceTableSaysWhatTheUserAskedFor() {
        // Orcs: higher HP, minimally higher attack, minimally slower. Written
        // here as the three comparisons rather than as three numbers, so that
        // retuning the table cannot quietly invert the promise.
        assertTrue(Race.ORC.maxHealth() > Race.HUMAN.maxHealth(),
                "orcs were asked for with higher health");
        assertTrue(Race.ORC.attackBonus() > Race.HUMAN.attackBonus(),
                "orcs were asked for with a higher base attack");
        assertTrue(Race.ORC.paceFactor() < Race.HUMAN.paceFactor(),
                "orcs were asked for slower on their feet");
        // "Minimally" is a promise too, and it is the easy half to lose.
        assertTrue(Race.ORC.attackBonus() <= 1, "minimally is one point, not three");
        assertTrue(Race.ORC.paceFactor() >= 0.85, "minimally slower, not a limp");

        // Goblins are the other end, or the table is a difficulty setting.
        assertTrue(Race.GOBLIN.maxHealth() < Race.HUMAN.maxHealth());
        assertTrue(Race.GOBLIN.paceFactor() > Race.HUMAN.paceFactor());

        // The human is the measure, and must stay exactly what shipped.
        assertEquals(20.0, Race.HUMAN.maxHealth());
        assertEquals(0, Race.HUMAN.attackBonus());
        assertEquals(1.0, Race.HUMAN.paceFactor());

        assertEquals(30.0, Race.ORC.maxHealth());
        assertEquals(1, Race.ORC.attackBonus());
        assertEquals(0.9, Race.ORC.paceFactor());
        assertEquals(14.0, Race.GOBLIN.maxHealth());
        assertEquals(0, Race.GOBLIN.attackBonus());
        assertEquals(1.1, Race.GOBLIN.paceFactor());
    }

    @Test
    void aRaceNobodyHasHeardOfIsPeople() {
        // Same reasoning as Culture.of: a datapack id that means nothing should
        // produce ordinary people, not an exception halfway through a world load.
        assertEquals(Race.HUMAN, Race.of("dwarf"));
        assertEquals(Race.HUMAN, Race.of(null));
        assertEquals(Race.ORC, Race.of("ORC"), "the id's case is not a promise");
    }

    @Test
    void noArrangementIsBuiltByTwoRaces() {
        // The worldgen draw picks the arrangement first and the people second, so
        // an arrangement two races both build would hand an orc town to a human
        // shape or the reverse. None is shared today, and this is the assertion
        // that says so -- the day one is, the draw needs to be told about races.
        Map<String, Race> builtBy = new HashMap<>();
        for (Culture culture : Culture.all()) {
            if (culture.id().equals(Culture.DEFAULT.id())) {
                continue;   // the sentinel is never drawn; see SettlementSites
            }
            for (String layout : culture.layouts()) {
                Race already = builtBy.putIfAbsent(layout, culture.race());
                assertTrue(already == null || already == culture.race(),
                        layout + " is built by both " + already + " and "
                                + culture.race() + "; the layout-first draw would "
                                + "hand a town to the wrong kind of people");
            }
        }
    }
}
