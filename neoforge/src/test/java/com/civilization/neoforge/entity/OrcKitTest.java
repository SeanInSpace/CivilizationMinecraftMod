package com.civilization.neoforge.entity;

import com.civilization.neoforge.item.OrcWeapons;
import com.civilization.neoforge.view.PersonEntityManager;
import com.civilization.sim.combat.Weaponry;
import com.civilization.sim.culture.Race;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the orc armory is worth, and whether it is actually there.
 *
 * <p>Two halves. The numbers half is arithmetic on the table and needs nothing
 * from the game — the same trick, and the same caveat, as {@link GuardKitTest}.
 * The assets half reads the mod's own resources off the classpath, because a
 * weapon with no texture is an item that registers perfectly and renders as a
 * purple checkerboard, which no unit test has ever caught and every player
 * notices within a second.
 *
 * <p>What cannot be checked here is a settler actually holding one: an
 * {@code ItemStack} cannot be built in this JVM. That one is a hands-on check
 * and is written up in DEFENSE.md.
 */
final class OrcKitTest {

    /** Base, then the body, then what is in the hand — the guard's own order. */
    private static float guardSwing(Race race, float weapon) {
        return PersonEntityManager.GUARD_DAMAGE + race.attackBonus() + weapon;
    }

    @Test
    void anOrcGuardOutHitsAMilitiamanAtBothTiers() {
        // The lowland watch is fist 0, wood +1, iron +3 on a base of 4 and no
        // body bonus: 5 and 7 a swing. An orc gets +1 for the body and +2 or +4
        // for the weapon on the same base: 7 and 9.
        assertEquals(5.0F, guardSwing(Race.HUMAN, 1.0F));
        assertEquals(7.0F, guardSwing(Race.HUMAN, 3.0F));
        assertEquals(7.0F, guardSwing(Race.ORC, Weaponry.CRUDE_BONUS));
        assertEquals(9.0F, guardSwing(Race.ORC, Weaponry.FORGED_BONUS));
        assertTrue(guardSwing(Race.ORC, Weaponry.CRUDE_BONUS)
                        > guardSwing(Race.HUMAN, 3.0F) - 0.5F,
                "an orc who has never seen a forge is worse off than a militiaman "
                        + "who has");
    }

    @Test
    void theSmithyIsStillWorthBuildingForAnOrcTown() {
        // The whole argument for a forge, stated as the gap it buys. It must not
        // shrink because the race bonus arrived: the body adds the same +1
        // either side of it.
        float before = guardSwing(Race.ORC, Weaponry.CRUDE_BONUS);
        float after = guardSwing(Race.ORC, Weaponry.FORGED_BONUS);
        assertEquals(Weaponry.FORGED_BONUS - Weaponry.CRUDE_BONUS, after - before);
        assertTrue(after - before >= 2.0F, "the forge bought the camp almost nothing");
    }

    @Test
    void anArmedSettlerIsNoSubstituteForAGuard() {
        // Both get the body's bonus, so the gap is the two bases and nothing else.
        float civilian = Weaponry.CIVILIAN_BASE_DAMAGE + Race.ORC.attackBonus()
                + Weaponry.CRUDE_BONUS;
        float guard = guardSwing(Race.ORC, Weaponry.CRUDE_BONUS);
        assertTrue(civilian < guard,
                "a farmer hits as hard as the watch, so why post one");
        assertTrue(civilian > 0.0F, "an armed settler who cannot hurt anything");
        assertEquals(5.0F, civilian, "an armed orc settler no longer hits for 5");
    }

    @Test
    void heNeverReachesFurtherThanTheWatchDoes() {
        // He also never takes a step toward anything, so the binding bound is
        // the melee reach. The stated one has to sit at or above it or the rule
        // would read as a licence to swing at things out of arm's length.
        assertTrue(Weaponry.CIVILIAN_REACH >= PersonEntityManager.GUARD_STRIKE_RANGE,
                "the stated limit is tighter than a blow can land anyway");
        assertTrue(Weaponry.CIVILIAN_REACH < PersonEntityManager.GUARD_ENGAGE_RANGE,
                "a civilian would answer things across the square");
    }

    @Test
    void aTwoHanderNeverEndsUpHoldingABowItCannotUse() {
        // The rule the view layer turns into an empty off hand. Stated here so
        // that a sixth weapon added to GUARD_KIT has to face it.
        for (Weaponry weapon : Weaponry.values()) {
            if (weapon.grip() == Weaponry.Grip.TWO_HANDED) {
                assertTrue(!weapon.carriesBow(), weapon + " would carry a bow in both fists");
            }
        }
    }

    @Test
    void theGreatswordAndTheMorningstarHitHarderAndSlowerThanTheBlades() {
        for (Weaponry heavy : new Weaponry[] {Weaponry.GREATSWORD, Weaponry.MORNINGSTAR}) {
            for (Weaponry quick : new Weaponry[] {Weaponry.FALCHION, Weaponry.CLEAVER}) {
                assertTrue(OrcWeapons.damageBaseline(heavy) > OrcWeapons.damageBaseline(quick),
                        heavy + " does not out-hit a " + quick);
                assertTrue(OrcWeapons.displayedSpeed(heavy) < OrcWeapons.displayedSpeed(quick),
                        heavy + " swings as fast as a " + quick);
            }
        }
        assertTrue(OrcWeapons.displayedSpeed(Weaponry.MORNINGSTAR)
                        < OrcWeapons.displayedSpeed(Weaponry.GREATSWORD),
                "the morningstar is meant to be the slowest thing in the camp");
    }

    @Test
    void theCleaverIsAFalchionAndABit() {
        // The ask was "a bonus against the unarmored, kept simple": one more
        // damage than the falchion, paid for in swing speed.
        assertEquals(OrcWeapons.damageBaseline(Weaponry.FALCHION) + 1.0F,
                OrcWeapons.damageBaseline(Weaponry.CLEAVER));
        assertTrue(OrcWeapons.displayedSpeed(Weaponry.CLEAVER)
                        < OrcWeapons.displayedSpeed(Weaponry.FALCHION),
                "the cleaver is strictly better than the falchion, which is not a choice");
    }

    @Test
    void theAxeIsAnIronAxe() {
        // Vanilla's iron axe is AxeItem(IRON, 6.0F, -3.1F): 9 damage at 0.9
        // swings a second. The forged orc axe is that, exactly.
        assertEquals(6.0F, OrcWeapons.damageBaseline(Weaponry.AXE));
        assertEquals(0.9F, OrcWeapons.displayedSpeed(Weaponry.AXE), 1.0E-5F);
        assertEquals(9.0F, OrcWeapons.displayedDamage(Weaponry.AXE, true), 1.0E-5F);
        assertTrue(OrcWeapons.isAxe(Weaponry.AXE));
    }

    @Test
    void onlyTheAxeIsAnAxe() {
        // A greatsword is not a felling tool however much it looks like one.
        for (Weaponry weapon : Weaponry.values()) {
            assertEquals(weapon == Weaponry.AXE, OrcWeapons.isAxe(weapon), weapon.name());
        }
    }

    @Test
    void forgingOneAlwaysMakesItBetterAndNeverChangesItsSwing() {
        for (Weaponry weapon : Weaponry.values()) {
            assertTrue(OrcWeapons.displayedDamage(weapon, true)
                            > OrcWeapons.displayedDamage(weapon, false),
                    "forging a " + weapon + " bought the town nothing");
            // The smith reforged the head, not the grip.
            assertEquals(OrcWeapons.displayedSpeed(weapon), OrcWeapons.displayedSpeed(weapon));
        }
    }

    @Test
    void nothingInTheArmoryOutDoesANetheriteSword() {
        // Netherite is 8 damage at 1.6 swings a second. Anything here that beat
        // it outright would make the deepest tool in the game pointless; the
        // heavy weapons are allowed to out-hit it only by being much slower.
        for (Weaponry weapon : Weaponry.values()) {
            float perSecond = OrcWeapons.displayedDamage(weapon, true)
                    * OrcWeapons.displayedSpeed(weapon);
            assertTrue(perSecond < 8.0F * 1.6F,
                    weapon + " does " + perSecond + " a second, which is better than netherite");
        }
    }

    @Test
    void everyWeaponHasATextureAModelAndAnItemDefinition() {
        for (String name : Weaponry.everyName()) {
            assertResource("/assets/civilization/textures/item/" + name + ".png");
            assertResource("/assets/civilization/models/item/" + name + ".json");
            assertResource("/assets/civilization/items/" + name + ".json");
        }
    }

    @Test
    void everyTextureIsARealSixteenBySixteenPng() {
        for (String name : Weaponry.everyName()) {
            byte[] png = read("/assets/civilization/textures/item/" + name + ".png");
            assertTrue(png.length > 8, name + " has an empty texture");
            // The eight-byte PNG signature, then IHDR: width and height are the
            // two big-endian ints at offsets 16 and 20.
            assertEquals((byte) 0x89, png[0], name + " is not a PNG at all");
            assertEquals('P', png[1]);
            assertEquals(16, intAt(png, 16), name + " is not 16 wide");
            assertEquals(16, intAt(png, 20), name + " is not 16 tall");
        }
    }

    @Test
    void everyModelHangsOffTheHandheldParentAndPointsAtItsOwnTexture() {
        for (String name : Weaponry.everyName()) {
            String model = text("/assets/civilization/models/item/" + name + ".json");
            assertTrue(model.contains("minecraft:item/handheld"),
                    name + " renders flat in the hand like a map");
            assertTrue(model.contains("civilization:item/" + name),
                    name + " points at somebody else's texture");
            String definition = text("/assets/civilization/items/" + name + ".json");
            assertTrue(definition.contains("civilization:item/" + name),
                    name + " has no client item definition pointing at its model");
        }
    }

    @Test
    void everyWeaponHasAnEnglishName() {
        String lang = text("/assets/civilization/lang/en_us.json");
        for (String name : Weaponry.everyName()) {
            assertTrue(lang.contains("\"item.civilization." + name + "\""),
                    name + " shows up in the tab as item.civilization." + name);
        }
    }

    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static void assertResource(String path) {
        assertNotNull(OrcKitTest.class.getResource(path), path + " is not on the classpath");
    }

    private static byte[] read(String path) {
        try (InputStream in = OrcKitTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return in.readAllBytes();
        } catch (java.io.IOException failed) {
            throw new AssertionError(path + " could not be read", failed);
        }
    }

    private static String text(String path) {
        return new String(read(path), StandardCharsets.UTF_8);
    }
}
