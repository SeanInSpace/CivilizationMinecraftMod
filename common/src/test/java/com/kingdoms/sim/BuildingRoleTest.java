package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a building is for, at every address it might answer to.
 *
 * <p>Eleven places used to work this out by looking for a substring in a
 * blueprint id, and they did not all agree. These are the cases that made that
 * dangerous: a name that contains another name, a building raised a level, and
 * a culture's own style folder.
 */
class BuildingRoleTest {

    private static Building building(String blueprintId) {
        return new Building(blueprintId, new SimPos(0, 64, 0), 1, true);
    }

    @Test
    void aStoreIsAStoreAtEveryAddressItAnswersTo() {
        assertEquals(BuildingRole.STORE, BuildingRole.of("kingdoms:storehouse"));
        assertEquals(BuildingRole.STORE, BuildingRole.of("kingdoms:warehouse"));
        assertEquals(BuildingRole.STORE, BuildingRole.of("kingdoms:storehouse_l2"),
                "raising it a level does not stop it being a store");
        assertEquals(BuildingRole.STORE, BuildingRole.of("kingdoms:norman/storehouse"),
                "nor does building it in a culture's own style");
        assertEquals(BuildingRole.STORE, BuildingRole.of("kingdoms:norman/warehouse_l3"),
                "nor both at once");
    }

    @Test
    void theTwoFarmsAreNotTheSameBuilding() {
        // The trap a substring match walks straight into: "animal_farm" contains
        // "farm", so the pens were a crop field to anything asking that way —
        // and the auditor would have judged them for bare rows.
        assertEquals(BuildingRole.CROP_FARM, BuildingRole.of("kingdoms:farm"));
        assertEquals(BuildingRole.ANIMAL_FARM, BuildingRole.of("kingdoms:animal_farm"));
        assertEquals(BuildingRole.ANIMAL_FARM, BuildingRole.of("kingdoms:norman/animal_farm_l2"));
    }

    @Test
    void eachSpecialBuildingIsItself() {
        assertEquals(BuildingRole.LUMBER_CAMP, BuildingRole.of("kingdoms:lumber_camp"));
        assertEquals(BuildingRole.MINE, BuildingRole.of("kingdoms:mine"));
        assertEquals(BuildingRole.GRANARY, BuildingRole.of("kingdoms:granary"));
        assertEquals(BuildingRole.MARKET, BuildingRole.of("kingdoms:market"));
        assertEquals(BuildingRole.SMITH, BuildingRole.of("kingdoms:smith"));
        assertEquals(BuildingRole.HALL, BuildingRole.of("kingdoms:town_hall"));
        assertEquals(BuildingRole.INN, BuildingRole.of("kingdoms:inn"));
        assertEquals(BuildingRole.MILL, BuildingRole.of("kingdoms:mill"));
        assertEquals(BuildingRole.CARPENTRY, BuildingRole.of("kingdoms:carpentry"));
    }

    @Test
    void anOrdinaryBuildingIsNothingInParticular() {
        assertEquals(BuildingRole.OTHER, BuildingRole.of("kingdoms:house"));
        assertEquals(BuildingRole.OTHER, BuildingRole.of("kingdoms:cottage"));
        assertEquals(BuildingRole.OTHER, BuildingRole.of("kingdoms:watchtower"));
        assertEquals(BuildingRole.OTHER, BuildingRole.of(null),
                "and a building with no id at all is not a crash");
    }

    @Test
    void aNameThatMerelyContainsAnotherNameIsNotThatBuilding() {
        // Exact names, not substrings. Nothing today is called "stone_mine" or
        // "farmhouse", and this is what stops the day somebody adds one from
        // quietly turning it into a mine or a field.
        assertEquals(BuildingRole.OTHER, BuildingRole.of("kingdoms:stone_mine"));
        assertEquals(BuildingRole.OTHER, BuildingRole.of("kingdoms:farmhouse"));
        assertEquals(BuildingRole.OTHER, BuildingRole.of("kingdoms:smithy_ruins"));
    }

    @Test
    void aBuildingUpgradedIntoAStoreBecomesOne() {
        // The role is cached, so the cache has to be dropped when an upgrade
        // renames the blueprint underneath it. Otherwise a building would keep
        // answering for what it used to be.
        Building building = building("kingdoms:house");
        assertFalse(building.isStore());

        building.setBlueprintId("kingdoms:storehouse");

        assertTrue(building.isStore(), "it is what it is now, not what it was");
        assertEquals(BuildingRole.STORE, building.role());
    }

    @Test
    void aStoreUpgradedInPlaceIsStillAStore() {
        // The case that happens in play: storehouse -> storehouse_l2. Verified
        // in a real town, but worth pinning here where it is cheap to check.
        Building building = building("kingdoms:storehouse");
        building.setBlueprintId("kingdoms:storehouse_l2");

        assertTrue(building.isStore(), "and its goods stay findable through the upgrade");
    }
}
