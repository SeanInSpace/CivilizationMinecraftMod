package com.kingdoms.neoforge.bridge;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.sim.settlement.Alarm;
import com.kingdoms.sim.settlement.Danger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The town's opinion about how frightening each kind of creature is.
 *
 * <p>Two halves, and they need different evidence. The named entries are a
 * judgement — a creeper is worth four because somebody argued it was — so the
 * assertions below are written with the literal numbers rather than with
 * {@link Danger} rungs: a test that reads the same constants the table does
 * cannot notice a number moving. The unnamed default is a rule, and is tested as
 * one, against every entity type the game has registered.
 *
 * <p><strong>What this environment cannot do.</strong> Two things, both
 * measured rather than assumed. Entities cannot be built: {@code create} wants a
 * {@code Level} and there is not one, so nothing here can hold a live creeper —
 * which is why {@code Menace} answers about a class and a registration rather
 * than only about a body. And tags are not bound: {@code c:bosses} and
 * {@code #raiders} answer false for everything outside a running server, so the
 * boss rung cannot be exercised here at all. That is the reason the wither and
 * the ender dragon are named in the table rather than left to the tag, and the
 * reason the raider rung is asked of a class rather than of the tag.
 */
class MenaceTest {

    // --- the table ---

    @Test
    void theNamedEntriesReadWhatTheyAlwaysDid() {
        // Deliberately literal. This is the record of what was decided, and it
        // fails if a refactor of the scale quietly moves one of them.
        assertEquals(4, Menace.of(Creeper.class, EntityTypes.CREEPER), "creeper");
        assertEquals(10, Menace.of(Warden.class, EntityTypes.WARDEN), "warden");
        assertEquals(5, Menace.of(Ravager.class, EntityTypes.RAVAGER), "ravager");
        assertEquals(4, Menace.of(Evoker.class, EntityTypes.EVOKER), "evoker");
        assertEquals(3, Menace.of(Witch.class, EntityTypes.WITCH), "witch");
        assertEquals(3, Menace.of(EnderMan.class, EntityTypes.ENDERMAN), "enderman");
        assertEquals(3, Menace.of(Pillager.class, EntityTypes.PILLAGER), "pillager");
        assertEquals(3, Menace.of(Vindicator.class, EntityTypes.VINDICATOR), "vindicator");
        assertEquals(2, Menace.of(Skeleton.class, EntityTypes.SKELETON), "skeleton");
        assertEquals(2, Menace.of(CaveSpider.class, EntityTypes.CAVE_SPIDER), "cave spider");
        assertEquals(2, Menace.of(Phantom.class, EntityTypes.PHANTOM), "phantom");
        assertEquals(1, Menace.of(Zombie.class, EntityTypes.ZOMBIE), "zombie");
        assertEquals(1, Menace.of(Spider.class, EntityTypes.SPIDER), "spider");
    }

    @Test
    void aFamilyIsReadAsItsFamily() {
        // The reason the table matches on the class rather than on the exact
        // type: a husk is a zombie and a bogged is a skeleton, and so is a mod's
        // own zombie that nobody here has ever seen.
        assertEquals(1, Menace.of(Husk.class, EntityTypes.HUSK));
        assertEquals(1, Menace.of(ZombieVillager.class, EntityTypes.ZOMBIE_VILLAGER));
        assertEquals(2, Menace.of(Bogged.class, EntityTypes.BOGGED));
    }

    @Test
    void aDrownedIsNoLongerReadAsAShamblingCorpse() {
        // The entry GOALS names. A drowned is a zombie by descent and used to be
        // read as one, trident and all.
        assertEquals(2, Menace.of(Drowned.class, EntityTypes.DROWNED));
        assertTrue(Menace.of(Drowned.class, EntityTypes.DROWNED)
                        > Menace.of(Zombie.class, EntityTypes.ZOMBIE),
                "the thing that throws a trident from the river is not the thing that shambles");
    }

    @Test
    void theTwoThingsWithABossBarAreNamedOutright() {
        // Not left to the c:bosses tag: it is the only handle a modded boss
        // offers, and it is unbound everywhere except a running server.
        assertEquals(Danger.HOPELESS, Menace.of(WitherBoss.class, EntityTypes.WITHER));
        assertEquals(Danger.HOPELESS, Menace.of(EnderDragon.class, EntityTypes.ENDER_DRAGON));
    }

    // --- the gaps ---

    @Test
    void aHostileTheTableNeverNamedReadsAsAtLeastASkeleton() {
        // The old answer for all four of these was ORDINARY, which is a zombie.
        assertEquals(Danger.AWKWARD, Menace.of(Silverfish.class, EntityTypes.SILVERFISH));
        assertEquals(Danger.AWKWARD, Menace.of(Endermite.class, EntityTypes.ENDERMITE));
        assertEquals(Danger.AWKWARD, Menace.of(Guardian.class, EntityTypes.GUARDIAN));
        assertEquals(Danger.AWKWARD, Menace.of(Slime.class, EntityTypes.SLIME));
    }

    @Test
    void aCreatureNothingIsKnownAboutStillReadsAsAHostileIfTheGameFilesItAsOne() {
        // What a mod's own monster looks like from here: a class this code has
        // never heard of, and a registration saying "monster". The registration
        // is enough.
        assertEquals(Danger.AWKWARD, Menace.of(Entity.class, EntityTypes.CREAKING),
                "the registration says monster even when the class says nothing");
    }

    @Test
    void aRaiderNobodyNamedIsStillARaider() {
        // Raider itself stands in for one: every vanilla raider is named, so
        // nothing shipped with the game can reach this rung.
        assertEquals(Danger.FULL_ATTENTION, Menace.of(Raider.class, EntityTypes.PILLAGER),
                "a thing built to arrive in a band, aimed at exactly this kind of town");
    }

    @Test
    void aHostileThatShootsIsNotReadAsOneThatHasToWalkUp() {
        assertEquals(Danger.FULL_ATTENTION, Menace.of(ArcherNobodyNamed.class, EntityTypes.CREAKING),
                "an archer nobody has named has to be closed with under fire");
    }

    @Test
    void aPassiveCreatureIsNotWorthLookingAtTwice() {
        assertEquals(Danger.NONE, Menace.of(Cow.class, EntityTypes.COW));
        assertEquals(Danger.NONE, Menace.of(Bat.class, EntityTypes.BAT));
        assertEquals(Danger.NONE, Menace.of(Villager.class, EntityTypes.VILLAGER));
        assertEquals(Danger.NONE, Menace.of(IronGolem.class, EntityTypes.IRON_GOLEM),
                "the one standing in the square is on the town's side");
    }

    @Test
    void everyMonsterTheGameKnowsOfOutweighsEverythingItDoesNot() {
        // Asked of the whole registry, with the class held at Entity so that
        // only the derived rule answers: this is the question "if the table said
        // nothing at all, would a town still tell a monster from a sheep?".
        int passive = Menace.of(Entity.class, EntityTypes.COW);
        assertEquals(Danger.NONE, passive);

        int monsters = 0;
        for (EntityType<?> kind : BuiltInRegistries.ENTITY_TYPE) {
            String name = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(kind));
            int reading = Menace.of(Entity.class, kind);
            if (kind.getCategory() == MobCategory.MONSTER) {
                monsters++;
                assertTrue(reading > passive,
                        name + " is registered as a monster and reads " + reading);
                assertTrue(reading >= Danger.AWKWARD,
                        name + " reads below a skeleton at " + reading);
            } else {
                assertEquals(Danger.NONE, reading,
                        name + " is not filed as a monster and should worry nobody");
            }
        }
        assertTrue(monsters > 30,
                "forty-five types are monsters in 26.2; " + monsters
                        + " means the sweep found nothing and this test proved nothing");
    }

    // --- who reaches the reckoning at all ---

    /**
     * The sweep's own admission test, written out rather than called.
     *
     * <p>{@code hostilesSeen} keeps what scores above nothing and drops the rest,
     * which is one rule rather than a number and a predicate that could drift
     * apart. Spelling it here keeps the assertions below readable without adding
     * a second public way to ask the same question.
     */
    private static boolean reaches(Class<? extends Entity> creature, EntityType<?> kind, boolean provoked) {
        return Menace.inSight(creature, kind, provoked) > Danger.NONE;
    }

    @Test
    void aHostileIsOnTheReckoningWhetherOrNotItHasPickedAFight() {
        // The four the sweep could never reach. None of them is a Monster, and
        // Monster is what it used to collect.
        assertTrue(reaches(Ghast.class, EntityTypes.GHAST, false));
        assertTrue(reaches(Phantom.class, EntityTypes.PHANTOM, false));
        assertTrue(reaches(Slime.class, EntityTypes.SLIME, false));
        assertTrue(reaches(EnderDragon.class, EntityTypes.ENDER_DRAGON, false));
        assertTrue(reaches(HorrorNobodyNamed.class, EntityTypes.COW, false),
                "the shape a modded boss takes, which is why the gap mattered");
    }

    @Test
    void aPassiveCreatureNeverReachesIt() {
        assertFalse(reaches(Cow.class, EntityTypes.COW, false));
        assertFalse(reaches(Cow.class, EntityTypes.COW, true),
                "there is no such thing as a cow that has picked a fight");
        assertFalse(reaches(Villager.class, EntityTypes.VILLAGER, true));
    }

    @Test
    void aNeutralCreatureCountsOnlyWhileItsQuarrelIsWithTheTown() {
        assertFalse(reaches(Wolf.class, EntityTypes.WOLF, false),
                "a wolf asleep in the grass is scenery");
        assertTrue(reaches(Wolf.class, EntityTypes.WOLF, true),
                "the same wolf with a guard's sword in it is a fight");
        assertFalse(reaches(Bee.class, EntityTypes.BEE, false));
        assertTrue(reaches(Bee.class, EntityTypes.BEE, true));
        assertFalse(reaches(PolarBear.class, EntityTypes.POLAR_BEAR, false));
        assertFalse(reaches(IronGolem.class, EntityTypes.IRON_GOLEM, false),
                "the one standing in the square is still on the town's side");
        assertFalse(reaches(HerdBeastNobodyNamed.class, EntityTypes.COW, false));
        assertTrue(reaches(HerdBeastNobodyNamed.class, EntityTypes.COW, true));
    }

    @Test
    void aMonsterThatIsNeutralIsReadAsNeutral() {
        // Filed as a monster, graded as one, and still not a threat until
        // somebody touches it: the interface outranks the filing. This is the
        // half of the change that makes a town *less* frightened — an enderman
        // in a field used to be three points of permanent alarm.
        assertFalse(reaches(EnderMan.class, EntityTypes.ENDERMAN, false));
        assertTrue(reaches(EnderMan.class, EntityTypes.ENDERMAN, true));
        assertFalse(reaches(ZombifiedPiglin.class, EntityTypes.ZOMBIFIED_PIGLIN, false));
        assertTrue(reaches(ZombifiedPiglin.class, EntityTypes.ZOMBIFIED_PIGLIN, true));
    }

    @Test
    void aProvokedNeutralIsWorthAtLeastOneGuardsAfternoon() {
        assertEquals(Danger.NONE, Menace.inSight(Wolf.class, EntityTypes.WOLF, false));
        assertEquals(Danger.ROUTINE, Menace.inSight(Wolf.class, EntityTypes.WOLF, true),
                "a guard walks up to it and hits it, which is the definition of the rung");
        assertEquals(Danger.FULL_ATTENTION, Menace.inSight(EnderMan.class, EntityTypes.ENDERMAN, true),
                "the floor cannot lift something the table already grades");
    }

    @Test
    void theFloorCannotLiftANeutralTheTableAlreadyGrades() {
        // The floor is a maximum against the table, so it can only ever fire for
        // a creature the table scores at nothing. Both of these are neutral and
        // both are graded, and a zombified piglin sits exactly on the floor —
        // which is where a raised one would be caught first.
        assertEquals(Menace.of(EnderMan.class, EntityTypes.ENDERMAN),
                Menace.inSight(EnderMan.class, EntityTypes.ENDERMAN, true));
        assertEquals(Menace.of(ZombifiedPiglin.class, EntityTypes.ZOMBIFIED_PIGLIN),
                Menace.inSight(ZombifiedPiglin.class, EntityTypes.ZOMBIFIED_PIGLIN, true));
        assertEquals(Danger.ROUTINE, Menace.of(ZombifiedPiglin.class, EntityTypes.ZOMBIFIED_PIGLIN),
                "if this stops being the floor's own rung the test above stops being sharp");
    }

    @Test
    void aCreatureThatIsNotNeutralReadsTheTableWhateverItIsDoing() {
        // The whole registry, class held at Entity, which is not a NeutralMob:
        // being provoked must make no difference to any of them. This is what
        // catches a floor applied to everything rather than only to neutrals —
        // a cow that reads one because somebody kicked it.
        for (EntityType<?> kind : BuiltInRegistries.ENTITY_TYPE) {
            String name = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(kind));
            assertEquals(Menace.of(Entity.class, kind), Menace.inSight(Entity.class, kind, true), name);
            assertEquals(Menace.of(Entity.class, kind), Menace.inSight(Entity.class, kind, false), name);
        }
    }

    @Test
    void whatTheWidenedSweepDoesToATownsAlarm() {
        // The downstream effect, in the tiers it lands in. Everything named here
        // is a creature the town's own eyes could not report until now.
        // Through inSight rather than through the table, because inSight is what
        // the sweep adds up: a later rule about phantoms at dawn or about the
        // size of a slime would go there, and this should notice when it does.
        assertEquals(Alarm.WARY, Alarm.of(Menace.inSight(Phantom.class, EntityTypes.PHANTOM, false)),
                "one phantom overhead: the outdoor trades come in");
        assertEquals(Alarm.ALARMED, Alarm.of(3 * Menace.inSight(Phantom.class, EntityTypes.PHANTOM, false)),
                "three of them: everybody indoors");
        assertEquals(Alarm.WARY, Alarm.of(Menace.inSight(Slime.class, EntityTypes.SLIME, false)),
                "a slime in a swamp");
        assertEquals(Alarm.CALM, Alarm.of(Menace.inSight(Wolf.class, EntityTypes.WOLF, false)));
        assertEquals(Alarm.WARY, Alarm.of(Menace.inSight(Wolf.class, EntityTypes.WOLF, true)),
                "one provoked wolf");
        assertEquals(Alarm.ALARMED, Alarm.of(6 * Menace.inSight(Wolf.class, EntityTypes.WOLF, true)),
                "a pack of six");
    }

    // --- what a creature makes of a citizen ---

    @Test
    void aHostileHuntsACitizen() {
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Zombie.class, EntityTypes.ZOMBIE));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Skeleton.class, EntityTypes.SKELETON));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Creeper.class, EntityTypes.CREEPER));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Phantom.class, EntityTypes.PHANTOM));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Warden.class, EntityTypes.WARDEN));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(HorrorNobodyNamed.class, EntityTypes.COW),
                "a mod's own boss, covered by being what it is");
        assertEquals(Menace.Regard.HUNTS, Menace.regards(ArcherNobodyNamed.class, EntityTypes.CREAKING));
    }

    @Test
    void aRaiderHuntsACitizenWithoutBeingNamedARaider() {
        // The raid answer, and it matters: vanilla's raiders hunt
        // AbstractVillager and a citizen is not one, so a band used to walk
        // through a town seeing nobody in it. They arrive here by being Enemy
        // and not neutral, like anything else would.
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Pillager.class, EntityTypes.PILLAGER));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Vindicator.class, EntityTypes.VINDICATOR));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Evoker.class, EntityTypes.EVOKER));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Ravager.class, EntityTypes.RAVAGER));
        assertEquals(Menace.Regard.HUNTS, Menace.regards(Raider.class, EntityTypes.PILLAGER));
    }

    @Test
    void aNeutralCreatureOnlyHitsBack() {
        assertEquals(Menace.Regard.RETALIATES, Menace.regards(Wolf.class, EntityTypes.WOLF));
        assertEquals(Menace.Regard.RETALIATES, Menace.regards(Bee.class, EntityTypes.BEE));
        assertEquals(Menace.Regard.RETALIATES, Menace.regards(PolarBear.class, EntityTypes.POLAR_BEAR));
        assertEquals(Menace.Regard.RETALIATES, Menace.regards(IronGolem.class, EntityTypes.IRON_GOLEM));
        assertEquals(Menace.Regard.RETALIATES, Menace.regards(EnderMan.class, EntityTypes.ENDERMAN));
        assertEquals(Menace.Regard.RETALIATES,
                Menace.regards(ZombifiedPiglin.class, EntityTypes.ZOMBIFIED_PIGLIN),
                "the one creature that loses a hunt it used to have: the old line armed "
                        + "every Zombie by class, and a zombified piglin is one by descent");
        assertEquals(Menace.Regard.RETALIATES, Menace.regards(HerdBeastNobodyNamed.class, EntityTypes.COW));
    }

    @Test
    void nothingHuntsACitizenThatHasNoReasonTo() {
        assertEquals(Menace.Regard.IGNORES, Menace.regards(Cow.class, EntityTypes.COW));
        assertEquals(Menace.Regard.IGNORES, Menace.regards(Villager.class, EntityTypes.VILLAGER));
        assertEquals(Menace.Regard.IGNORES, Menace.regards(PersonEntity.class, EntityTypes.VILLAGER),
                "a citizen is not quarry to another citizen");
    }

    /**
     * What a mod adds: a hostile that shoots, which nothing in the table has
     * heard of.
     *
     * <p>Abstract and never built. Only its class is ever asked about, which is
     * as well — a live entity cannot be constructed in this environment.
     */
    private abstract static class ArcherNobodyNamed extends Monster implements RangedAttackMob {
        private ArcherNobodyNamed() {
            super(null, null);
        }
    }

    /**
     * The shape a modded boss is most likely to take: a {@link Mob} that is an
     * {@link Enemy} without being a {@code Monster}, because {@code Monster} is a
     * {@code PathfinderMob} and a thing that flies is not one.
     *
     * <p>Asked about with a passive registration on purpose, so that only the
     * interface can answer.
     */
    private abstract static class HorrorNobodyNamed extends Mob implements Enemy {
        private HorrorNobodyNamed() {
            super(null, null);
        }
    }

    /** What a mod adds at the other end: something that minds its own business. */
    private abstract static class HerdBeastNobodyNamed extends PathfinderMob implements NeutralMob {
        private HerdBeastNobodyNamed() {
            super(null, null);
        }
    }
}
