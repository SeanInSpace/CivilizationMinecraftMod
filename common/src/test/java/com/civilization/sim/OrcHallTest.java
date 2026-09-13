package com.civilization.sim;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Layouts;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Founding;
import com.civilization.sim.settlement.Homes;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SettlementStage;
import com.civilization.sim.settlement.StagePlanner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A war camp has one hall, and it is the great hut.
 *
 * <p>The report: "A war camp still raises a town hall beside its great hut." The
 * TOWN program wants a hall; nothing told it the orcs already have one. The great
 * hut is where the chief sits, it is what {@code KingPlanner.SEAT} names, and it
 * stands on the muster yard in the middle of the camp — every rule written about
 * "the hall" has always meant it and none of them could see it.
 *
 * <p>Two halves, one table. {@link Homes} substitutes the great hut for a hall
 * where this people is asked for one, which satisfies the program's want without
 * the program knowing anybody's architecture; and {@link BuildingRole} now names
 * the great hut a {@code HALL}, which is what makes it answer to the want at all.
 */
class OrcHallTest {

    private static final String WARHOST = "civilization:orc/warhost";

    private static final String BURGHER = "civilization:human/burgher";

    private static final SimPos SITE = new SimPos(0, 64, 0);

    @Test
    void theGreatHutIsAHall() {
        assertEquals(BuildingRole.HALL, BuildingRole.of("civilization:great_hut"),
                "the chief's seat is the camp's hall");
        assertEquals(BuildingRole.HALL, BuildingRole.of("civilization:orc/great_hut_l2"),
                "at every address it is written at");
    }

    @Test
    void awarhostAskedForAHallIsAskedForItsGreatHut() {
        assertEquals("civilization:great_hut",
                Homes.instead(WARHOST, "civilization:town_hall"),
                "the orcs build a great hut where a program says hall");
        assertEquals("civilization:town_hall",
                Homes.instead(BURGHER, "civilization:town_hall"),
                "and everybody else still builds a town hall");
    }

    @Test
    void awarhostMayNotBuildATownHallAtAll() {
        assertFalse(Homes.buildableBy(WARHOST, "civilization:town_hall"),
                "a warhost has a replacement for the hall, so it never builds one");
        assertFalse(Homes.buildableBy(WARHOST, "civilization:town_hall_l2"),
                "at any level");
        assertTrue(Homes.buildableBy(BURGHER, "civilization:town_hall"),
                "and the rule reaches nobody else");
    }

    @Test
    void theTownProgramOfAWarCampAsksForAGreatHutAndNeverAHall() {
        // The fault where it actually bit. The TOWN program wants a hall; a camp
        // with no hall yet has to be asked for its OWN hall, which is the great
        // hut, and never for a town hall.
        Settlement camp = Founding.seeded(SITE, "Karrgurd", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, WARHOST, Founding.AS_THE_STAGE_HOUSES,
                Layouts.ORC_RING.id());
        camp.setStage(SettlementStage.TOWN);

        assertEquals("civilization:great_hut",
                StagePlanner.nextProgramWant(camp).map(BuildingType::id).orElse("nothing"),
                "a war camp that becomes a town raises its chief a great hut");
    }

    @Test
    void andOnceTheGreatHutStandsTheProgramWantsNothingMore() {
        // The other half: the want has to be SATISFIED by the hut, not merely
        // renamed to it, or a camp orders one great hut after another.
        Settlement camp = Founding.seeded(SITE, "Karrgurd", SettlementStage.TOWN,
                BuildCatalog.DEFAULT, WARHOST, Founding.AS_THE_STAGE_HOUSES,
                Layouts.ORC_RING.id());
        assertTrue(camp.buildings().stream()
                        .anyMatch(b -> b.blueprintId().endsWith("great_hut")),
                "a camp seeded as a town has its great hut: " + idsOf(camp));

        Optional<BuildingType> wanted = StagePlanner.nextProgramWant(camp);

        assertTrue(wanted.isEmpty(),
                "a war camp with a great hut wants no hall, and asked for a "
                        + wanted.map(BuildingType::id).orElse("nothing"));
    }

    @Test
    void andAVillageStillWantsItsTownHall() {
        // The control. The substitution must not have turned the hall off for
        // everybody who is not an orc.
        Settlement village = Founding.seeded(SITE, "Millbrook", SettlementStage.VILLAGE,
                BuildCatalog.DEFAULT, BURGHER, Founding.AS_THE_STAGE_HOUSES,
                Layouts.RING.id());
        village.setStage(SettlementStage.TOWN);

        assertEquals("civilization:town_hall",
                StagePlanner.nextProgramWant(village).map(BuildingType::id).orElse("nothing"),
                "a village that becomes a town builds a town hall");
    }

    @Test
    void noWarCampEverStandsATownHall() {
        // The measurement, at the level the report is written at: raise a camp
        // through every stage and count the town halls. Zero, and the great hut
        // standing.
        for (String layoutId : Culture.ORC.layouts()) {
            Settlement camp = Founding.seeded(SITE, "Karrgurd", SettlementStage.TOWN,
                    BuildCatalog.DEFAULT, WARHOST, Founding.AS_THE_STAGE_HOUSES, layoutId);
            List<String> halls = new ArrayList<>();
            for (Building standing : camp.buildings()) {
                if (BuildPlanner.baseIdOf(standing.blueprintId())
                        .endsWith("town_hall")) {
                    halls.add(standing.blueprintId() + " at " + standing.origin());
                }
            }
            assertTrue(halls.isEmpty(),
                    layoutId + " raised a town hall in a warhost: " + halls);
            assertTrue(camp.buildings().stream()
                            .anyMatch(b -> b.role() == BuildingRole.HALL),
                    layoutId + " left the camp with no hall at all: " + idsOf(camp));
            for (BuildTask queued : camp.buildQueue()) {
                assertFalse(BuildPlanner.baseIdOf(queued.blueprintId()).endsWith("town_hall"),
                        layoutId + " queued a town hall in a warhost");
            }
        }
    }

    private static List<String> idsOf(Settlement town) {
        List<String> ids = new ArrayList<>();
        for (Building standing : town.buildings()) {
            ids.add(standing.blueprintId());
        }
        return ids;
    }
}
