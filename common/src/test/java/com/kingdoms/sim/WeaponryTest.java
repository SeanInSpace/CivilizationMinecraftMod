package com.kingdoms.sim;

import com.kingdoms.sim.combat.Weaponry;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.Race;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table an orc is armed from, checked without a world.
 *
 * <p>Everything here is arithmetic on a name, which is the whole reason the
 * table lives in {@code :common}: a real {@code ItemStack} cannot be built in a
 * test JVM, so if the decision were made over items there would be no way to
 * test who carries what at all.
 */
final class WeaponryTest {

    @Test
    void bothTiersBeatTheLowlandWatchsOwn() {
        // A militiaman's wooden sword is +1 and the forge's iron one +3. An orc
        // starts above the first and a forged orc sits above the second, which
        // is the whole of the orc's side of the bargain in this file.
        assertEquals(2.0F, Weaponry.CRUDE_BONUS);
        assertEquals(4.0F, Weaponry.FORGED_BONUS);
        assertTrue(Weaponry.CRUDE_BONUS > 1.0F, "a camp weapon worse than a stick with an edge");
        assertTrue(Weaponry.FORGED_BONUS > 3.0F, "the smithy bought the orcs nothing");
        assertTrue(Weaponry.FORGED_BONUS > Weaponry.CRUDE_BONUS, "the tiers are inside out");
    }

    @Test
    void theTierIsTheOnlyThingThatChangesWhatAnyOfThemHitFor() {
        // Deliberate: a guard must not be quietly handicapped by which of five
        // weapons the deal gave him. See the reasoning on bonusFor.
        for (Weaponry weapon : Weaponry.values()) {
            assertEquals(Weaponry.CRUDE_BONUS, Weaponry.bonusFor(false), weapon.name());
            assertEquals(Weaponry.FORGED_BONUS, Weaponry.bonusFor(true), weapon.name());
        }
    }

    @Test
    void onlyTheOneHandersLeaveAHandForABow() {
        assertFalse(Weaponry.GREATSWORD.carriesBow(), "a greatsword held one-handed");
        assertFalse(Weaponry.MORNINGSTAR.carriesBow(), "a morningstar held one-handed");
        assertTrue(Weaponry.FALCHION.carriesBow());
        assertTrue(Weaponry.CLEAVER.carriesBow());
        assertTrue(Weaponry.AXE.carriesBow());
        for (Weaponry weapon : Weaponry.values()) {
            assertEquals(weapon.grip() == Weaponry.Grip.ONE_HANDED, weapon.carriesBow(),
                    weapon + " disagrees with its own grip");
        }
    }

    @Test
    void halfTheWatchCanStillAnswerACreeper() {
        // The bow is the only answer to something that explodes, so the watch's
        // own kit must never become entirely two-handed. One bowman is the floor
        // and this is the test that would catch a fifth weapon breaking it.
        long withBow = Weaponry.GUARD_KIT.stream().filter(Weaponry::carriesBow).count();
        assertTrue(withBow > 0, "not one guard in the town can shoot a creeper");
        assertTrue(withBow * 2 >= Weaponry.GUARD_KIT.size(),
                "most of the watch would stand off creepers it cannot kill");
    }

    @Test
    void nobodyWhoIsNotOfTheWatchCarriesSomethingTwoHanded() {
        // A farmer with a greatsword is not a farmer, and his other hand is
        // where the load he is carrying goes.
        for (Weaponry weapon : Weaponry.CIVILIAN_KIT) {
            assertTrue(weapon.carriesBow(),
                    weapon + " would take both a settler's hands");
        }
    }

    @Test
    void theDealIsTheSameEveryTimeItIsAsked() {
        UUID who = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Weaponry first = Weaponry.forGuard(who);
        for (int again = 0; again < 100; again++) {
            assertSame(first, Weaponry.forGuard(who), "a guard's weapon changed under him");
        }
        Weaponry own = Weaponry.forCivilian(who);
        for (int again = 0; again < 100; again++) {
            assertSame(own, Weaponry.forCivilian(who));
        }
    }

    @Test
    void theDealReachesEveryWeaponInItsOwnList() {
        // A hash that hands a whole barracks one weapon is the fault this is
        // looking for -- see the avalanche note on spread.
        Set<Weaponry> dealtToGuards = EnumSet.noneOf(Weaponry.class);
        Set<Weaponry> dealtToEverybodyElse = EnumSet.noneOf(Weaponry.class);
        for (int seed = 0; seed < 400; seed++) {
            UUID who = new UUID(seed, seed * 31L);
            dealtToGuards.add(Weaponry.forGuard(who));
            dealtToEverybodyElse.add(Weaponry.forCivilian(who));
        }
        assertEquals(Set.copyOf(Weaponry.GUARD_KIT), dealtToGuards);
        assertEquals(Set.copyOf(Weaponry.CIVILIAN_KIT), dealtToEverybodyElse);
    }

    @Test
    void theDealIsNeverFromTheWrongList() {
        for (int seed = 0; seed < 400; seed++) {
            UUID who = new UUID(seed * 7L, seed);
            assertTrue(Weaponry.GUARD_KIT.contains(Weaponry.forGuard(who)));
            assertTrue(Weaponry.CIVILIAN_KIT.contains(Weaponry.forCivilian(who)));
        }
    }

    @Test
    void neighboringIdsDoNotAllGetTheSameWeapon() {
        // Ids allocated in a run are the realistic case: a town embodied in one
        // pass. If the low bits leaked through, this barracks would be uniform.
        Set<Weaponry> spread = EnumSet.noneOf(Weaponry.class);
        for (int step = 0; step < 40; step++) {
            spread.add(Weaponry.forGuard(new UUID(0L, step)));
        }
        assertTrue(spread.size() > 1, "forty consecutive ids were dealt one weapon");
    }

    @Test
    void everyWeaponHasTwoNamesAndTheyAreDerivedFromEachOther() {
        for (Weaponry weapon : Weaponry.values()) {
            assertEquals(weapon.crudeName() + "_forged", weapon.forgedName());
            assertEquals(weapon.crudeName(), weapon.nameAt(false));
            assertEquals(weapon.forgedName(), weapon.nameAt(true));
            assertTrue(weapon.crudeName().startsWith("orc_"),
                    weapon.crudeName() + " is not obviously an orc's");
        }
        assertEquals(Weaponry.values().length * 2, Weaponry.everyName().size());
        assertEquals(Weaponry.everyName().size(), Set.copyOf(Weaponry.everyName()).size(),
                "two weapons registered under one name");
    }

    @Test
    void aNameGoesBackToTheWeaponItCameFrom() {
        for (Weaponry weapon : Weaponry.values()) {
            assertSame(weapon, Weaponry.byName(weapon.crudeName()));
            assertSame(weapon, Weaponry.byName(weapon.forgedName()));
            assertFalse(Weaponry.isForged(weapon.crudeName()));
            assertTrue(Weaponry.isForged(weapon.forgedName()));
        }
        assertNull(Weaponry.byName("minecraft:iron_sword"));
        assertNull(Weaponry.byName("orc_spear"));
        assertFalse(Weaponry.isForged("bread"));
    }

    @Test
    void everyNameTheTableKnowsResolves() {
        for (String name : Weaponry.everyName()) {
            assertNotNull(Weaponry.byName(name), name + " is registered and unreadable");
        }
    }

    @Test
    void orcsAreTheOnlyRaceThatArmsEverybody() {
        assertTrue(Weaponry.armsEveryone(Race.ORC));
        assertFalse(Weaponry.armsEveryone(Race.HUMAN));
        assertFalse(Weaponry.armsEveryone(Race.GOBLIN));
    }

    @Test
    void everyOrcCultureArmsEverybodyAndNoOtherCultureDoes() {
        // Asked of the race rather than the culture id on purpose, so the orcs'
        // second and third cultures arm their millers without anybody editing
        // this. The ids are here to catch a culture landing on the wrong body.
        for (Culture culture : Culture.all()) {
            assertEquals(culture.race() == Race.ORC,
                    Weaponry.armsEveryone(culture.race()),
                    culture.id() + " disagrees with its own race about arming its people");
        }
        assertTrue(Weaponry.armsEveryone(Culture.of("kingdoms:orc/warhost").race()),
                "the warhost does not arm its own people");
        assertFalse(Weaponry.armsEveryone(Culture.of("kingdoms:human/norman").race()));
        assertFalse(Weaponry.armsEveryone(Culture.of("kingdoms:goblin/mire").race()));
    }

    @Test
    void aTownWithNoPeopleNamedAtAllArmsNobody() {
        // A save written before cultures had names resolves to the default,
        // which is human. Arming a whole world's worth of old towns off the back
        // of a failed lookup is the fault this is watching for.
        assertFalse(Weaponry.armsEveryone(Culture.of(null).race()));
        assertFalse(Weaponry.armsEveryone(Culture.of("kingdoms:nothing_of_the_sort").race()));
    }

    @Test
    void aSettlerHitsSofterThanTheWatchAndNeverReachesFurther() {
        assertTrue(Weaponry.CIVILIAN_BASE_DAMAGE > 0.0F,
                "an armed settler who cannot hurt anything");
        // The watch's own base is 4. A farmer swinging as hard as a trained
        // guard is a town with no reason to post one.
        assertTrue(Weaponry.CIVILIAN_BASE_DAMAGE < 4.0F);
        assertTrue(Weaponry.CIVILIAN_REACH > 0.0);
        assertTrue(Weaponry.CIVILIAN_REACH < GuardStanceReach.NOTICE,
                "a civilian would answer things he cannot even see");
    }

    /** The one figure from the view layer this file needs, kept where it is visible. */
    private static final class GuardStanceReach {
        /** {@code PersonEntityManager.GUARD_ENGAGE_RANGE}, which :common cannot import. */
        static final double NOTICE = 20.0;
    }
}
