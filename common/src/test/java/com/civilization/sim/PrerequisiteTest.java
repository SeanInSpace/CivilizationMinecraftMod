package com.civilization.sim;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.BuildCatalog;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingType;
import com.civilization.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The second kind of "not yet" a catalog row can say.
 *
 * <p>Every gate in the catalog used to be a population: a town does not want a
 * smithy at nine people because there is nobody to keep one. That is the honest
 * measure for almost everything, and it cannot say the one thing the grand
 * library needs said — that it is the library a town builds <em>after</em> its
 * library. No headcount means that. A town that lost its library to a raid would
 * go on wanting the grand one; a town that somehow never built the small one
 * would raise the great one first and never build the ordinary one at all.
 *
 * <p>So {@link BuildingType} grew a {@code requires}, and this is the whole of
 * what it promises: a row with one is invisible to the catalog scan until the
 * thing it names is standing, a row without one behaves exactly as it always
 * did, and the gate is read off buildings rather than off a flag so it lapses
 * again if the prerequisite comes down.
 */
class PrerequisiteTest {

    private static final SimPos CENTER = new SimPos(0, 64, 0);

    private static final String LIBRARY = "civilization:library";
    private static final String GRAND = "civilization:grand_library";

    /** A town big enough for anything in the catalog, and holding nothing yet. */
    private static Settlement townOf(int residents) {
        Settlement town = new Settlement(Settlement.Id.random(), "Prereq", CENTER, 512);
        town.setCatalog(BuildCatalog.DEFAULT);
        for (int i = 0; i < residents; i++) {
            town.addResident(new Person(
                    Person.Id.random(), "P" + i, Profession.IDLER, CENTER));
        }
        return town;
    }

    private static BuildingType row(String id) {
        return BuildCatalog.DEFAULT.stream()
                .filter(type -> type.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no " + id + " in the catalog"));
    }

    /**
     * Whether the scan chooses this once the town has everything else it wants.
     *
     * <p>Asked with the rest of the catalog satisfied rather than by walking the
     * planner down from nothing, because a town of a hundred wants thirty-three
     * houses and the honest answer to "what next" is "housing" for thirty-three
     * steps running. What is being measured is whether this row is <em>in the
     * running at all</em>, and the only way to see that is to take everything
     * that outranks it off the table.
     */
    private static boolean everWanted(Settlement town, String id) {
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (type.id().equals(id)) {
                continue;
            }
            int wanted = type.desiredCount(town.population());
            for (int copy = town.countBuildings(type.id()); copy < wanted; copy++) {
                town.addBuilding(new Building(type.id(), CENTER, 1, true));
            }
        }
        Optional<BuildingType> next = BuildPlanner.chooseNext(town, BuildCatalog.DEFAULT);
        return next.isPresent() && next.get().id().equals(id);
    }

    @Test
    void aTownWithNoLibraryNeverWantsAGrandOneHoweverManyPeopleItHas() {
        // The claim in one line. A hundred residents is well past the grand
        // library's own gate of eighty, and it is still not wanted.
        Settlement town = townOf(100);
        assertFalse(BuildPlanner.prerequisiteStands(town, row(GRAND)),
                "nothing stands, so the prerequisite cannot be met");

        // Every library the scan would have built is taken back out, so the
        // walk cannot satisfy the prerequisite on its own and then claim the
        // gate never worked.
        Settlement never = townOf(100);
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (!type.id().equals(LIBRARY) && !type.id().equals(GRAND)) {
                for (int copy = 0; copy < type.desiredCount(100); copy++) {
                    never.addBuilding(new Building(type.id(), CENTER, 1, true));
                }
            }
        }
        assertEquals(Optional.of(row(LIBRARY)),
                BuildPlanner.chooseNext(never, BuildCatalog.DEFAULT),
                "with everything else standing the library is what a town of a"
                        + " hundred wants, and the grand library is not even in the"
                        + " running");
    }

    @Test
    void aTownWithALibraryStandingDoesWantTheGrandOne() {
        Settlement town = townOf(100);
        town.addBuilding(new Building(LIBRARY, CENTER, 1, true));
        assertTrue(BuildPlanner.prerequisiteStands(town, row(GRAND)));
        assertTrue(everWanted(town, GRAND),
                "a town of a hundred with a library and everything else it wants"
                        + " should reach the grand library");
    }

    @Test
    void aLeveledLibraryStillSatisfiesIt() {
        // Buildings are improved in place and the id grows a level suffix. A
        // prerequisite that stopped counting a library the moment somebody
        // improved it would be a gate that closed behind the town.
        Settlement town = townOf(100);
        town.addBuilding(new Building(LIBRARY + "_l3", CENTER, 1, true));
        assertTrue(BuildPlanner.prerequisiteStands(town, row(GRAND)),
                "a library raised to level three is still a library");
    }

    @Test
    void aGrandLibraryIsStillCappedAtOnePerTown() {
        Settlement town = townOf(100);
        town.addBuilding(new Building(LIBRARY, CENTER, 1, true));
        town.addBuilding(new Building(GRAND, CENTER, 1, true));
        assertEquals(0, BuildPlanner.shortfall(town, row(GRAND), 100),
                "base one and nothing per resident is the cap, the same way the"
                        + " library's and the great hut's are");
    }

    @Test
    void aTownBelowTheHeadcountDoesNotGetOneEvenWithALibrary() {
        // Both gates, not either. The prerequisite is the new one and it does not
        // replace the old: a village of fifty with a library is a village.
        Settlement small = townOf(50);
        small.addBuilding(new Building(LIBRARY, CENTER, 1, true));
        assertTrue(BuildPlanner.prerequisiteStands(small, row(GRAND)));
        assertFalse(everWanted(small, GRAND),
                "fifty residents is under the eighty the row asks for");
    }

    // --- and the rest of the catalog is untouched by any of it ----------------

    @Test
    void everyOtherRowInTheCatalogWantsNothingStandingFirst() {
        List<String> gated = BuildCatalog.DEFAULT.stream()
                .filter(BuildingType::hasPrerequisite)
                .map(BuildingType::id)
                .toList();
        assertEquals(List.of(GRAND), gated,
                "exactly one row has a prerequisite. A second one is fine, but it"
                        + " should be a deliberate decision rather than a default"
                        + " somebody picked up by copying a row");
    }

    @Test
    void anythingARowRequiresIsSomethingTheCatalogKnowsHowToBuild() {
        // A prerequisite naming a building nobody can build is a row that is
        // simply switched off, silently and forever.
        for (BuildingType type : BuildCatalog.DEFAULT) {
            if (!type.hasPrerequisite()) {
                continue;
            }
            assertTrue(BuildCatalog.DEFAULT.stream()
                            .anyMatch(other -> other.id().equals(type.requires())),
                    type.id() + " requires " + type.requires()
                            + ", which is not in the catalog at all");
        }
    }

    @Test
    void aRowWantingSomethingOfItsOwnKindFirstIsRefusedWhereItIsWritten() {
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingType("civilization:thing", 10, 1, 1, 0, 50, 0, 0, 11,
                        "civilization:thing"),
                "a building that requires itself can never have a first one, and"
                        + " that should throw at the row rather than read as a"
                        + " building the town never wants");
    }

    @Test
    void aRowThatSaysNothingAboutItIsTreatedAsWantingNothing() {
        BuildingType plain = new BuildingType("civilization:thing", 10, 1, 1, 0, 50, 0);
        assertFalse(plain.hasPrerequisite());
        assertEquals(BuildingType.NOTHING, plain.requires());
        // And null reads as the same thing, because a datapack entry that omits
        // the field has to land where a hardcoded row does.
        assertEquals(BuildingType.NOTHING,
                new BuildingType("civilization:thing", 10, 1, 1, 0, 50, 0, 0, 11, null)
                        .requires());
    }
}
