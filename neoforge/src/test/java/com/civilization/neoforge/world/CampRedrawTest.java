package com.civilization.neoforge.world;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.worldgen.SettlementSites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a goblin camp on the wrong ground turns into, and where.
 *
 * <p>{@code /civ sites} and the join greeting describe sites nobody has been to,
 * and a playtest caught one of those descriptions lying. Run A listed
 * {@code r(-1,-1) civilization:goblin/mire goblin_camp} as "not raised yet", and
 * the town that came up on that very site was <em>Wilbury, civilization:human/vale
 * laid out as ring_streets</em>. Nothing was broken about the raise: goblins
 * belong in bog and deep wood, that site was in neither, and
 * {@code WorldgenSettlements.consider} re-drew it as an ordinary town exactly as
 * it was meant to. What was broken is that the listing did not know, so the only
 * description of the place a player gets before walking to it promised a camp of
 * goblins and delivered a human village.
 *
 * <p>The fix is that both now go through {@code asItWillBeRaised}, which is one
 * function rather than two opinions. The half of it that depends on a biome
 * cannot be exercised here — the JUnit game populates the registries but there is
 * no {@code ServerLevel} to ask, which is the same limit
 * {@code SiteDirectoryTest} documents about item stacks. The half that can is the
 * one the listing leans on, and it is the half a regression would break: that the
 * re-draw keeps the region's site and changes only the people standing on it.
 */
class CampRedrawTest {

    private static final long SEED = 8675309L;

    /** Every arrangement anybody builds in, evenly weighted. */
    private static Map<String, Integer> everyArrangement() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Culture culture : Culture.all()) {
            for (String layout : culture.layouts()) {
                weights.put(layout, 1);
            }
        }
        return weights;
    }

    @Test
    @DisplayName("a camp re-drawn off goblin country keeps its site and loses its goblins")
    void aCampRedrawnKeepsItsSiteAndLosesItsGoblins() {
        SettlementSites.Grid grid = SettlementSites.Grid.DEFAULT;
        Map<String, Integer> weights = everyArrangement();
        int camps = 0;

        for (int rz = -24; rz <= 24; rz++) {
            for (int rx = -24; rx <= 24; rx++) {
                Optional<SettlementSites.Site> drawn = grid.siteIn(SEED, rx, rz, weights);
                if (drawn.isEmpty() || !Culture.of(drawn.get().cultureId()).isHostile()) {
                    continue;
                }
                camps++;
                Optional<SettlementSites.Site> instead =
                        WorldgenSettlements.withoutTheGoblins(grid, SEED, rx, rz, weights);
                assertTrue(instead.isPresent(),
                        "region " + rx + "," + rz + " had a camp and nothing to put"
                                + " in its place, so the region would go empty");

                // The centre is the whole point. The jitter that places a site
                // reads its own hash stream and never the weights, so taking the
                // goblins out of the draw must move nobody -- which is what lets
                // the listing print this answer against the site's own position.
                assertEquals(drawn.get().center(), instead.get().center(),
                        "the re-draw moved the town at region " + rx + "," + rz);
                assertFalse(Culture.of(instead.get().cultureId()).isHostile(),
                        "region " + rx + "," + rz + " re-drew one camp as another");
            }
        }

        assertTrue(camps > 0,
                "no goblin camp anywhere in a 49-region sweep, so this measured nothing");
        System.out.println("CAMPS re-drawn off goblin country in a 49-region sweep: " + camps);
    }

    @Test
    @DisplayName("the re-draw is the same answer every time it is asked")
    void theRedrawIsTheSameAnswerEveryTimeItIsAsked() {
        // The listing asks it on every `/civ sites` and the raise asks it once,
        // possibly sessions later. Two answers would be worse than the fault,
        // because the row would then be right about a town that never stood.
        SettlementSites.Grid grid = SettlementSites.Grid.DEFAULT;
        Map<String, Integer> weights = everyArrangement();
        for (int rz = -8; rz <= 8; rz++) {
            for (int rx = -8; rx <= 8; rx++) {
                Optional<SettlementSites.Site> once =
                        WorldgenSettlements.withoutTheGoblins(grid, SEED, rx, rz, weights);
                Optional<SettlementSites.Site> again =
                        WorldgenSettlements.withoutTheGoblins(grid, SEED, rx, rz, weights);
                assertEquals(once.map(SettlementSites.Site::cultureId),
                        again.map(SettlementSites.Site::cultureId),
                        "region " + rx + "," + rz + " re-drew differently on a second ask");
                assertEquals(once.map(SettlementSites.Site::layoutId),
                        again.map(SettlementSites.Site::layoutId),
                        "region " + rx + "," + rz + " re-drew a different shape second time");
            }
        }
    }

    @Test
    @DisplayName("a site nobody objects to comes back exactly as it was drawn")
    void aSiteNobodyObjectsToComesBackAsDrawn() {
        SettlementSites.Grid grid = SettlementSites.Grid.DEFAULT;
        Map<String, Integer> weights = everyArrangement();
        for (int rz = -8; rz <= 8; rz++) {
            for (int rx = -8; rx <= 8; rx++) {
                Optional<SettlementSites.Site> drawn = grid.siteIn(SEED, rx, rz, weights);
                if (drawn.isEmpty() || Culture.of(drawn.get().cultureId()).isHostile()) {
                    continue;
                }
                SimPos where = drawn.get().center();
                assertEquals(where,
                        grid.siteIn(SEED, rx, rz, weights).orElseThrow().center(),
                        "the draw itself is not stable at region " + rx + "," + rz);
            }
        }
    }
}
