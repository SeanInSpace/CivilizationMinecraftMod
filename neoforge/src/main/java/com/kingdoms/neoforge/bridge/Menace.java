package com.kingdoms.neoforge.bridge;

import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.sim.settlement.Danger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.neoforge.common.Tags;

/**
 * What each kind of creature is worth to a frightened town.
 *
 * <p>The threat count used to be a head count, which made a creeper and a
 * zombie the same news. They are not. A zombie is a thing a guard walks up to
 * and hits; a creeper is a thing that takes the guard, the wall and half a house
 * with it. The numbers below are how much of the watch's attention one of these
 * is reckoned to be worth, and they feed both the alarm tiers in {@code Alarm}
 * and the bell rule in {@code RaidPlanner}.
 *
 * <p>The scale is {@link Danger} and it lives in {@code :common}, because the
 * tiers that read these numbers are there and cannot see Minecraft. Every entry
 * below is a rung on it rather than a bare integer, so an argument about what
 * the scale means moves the table and the thresholds together.
 *
 * <p><b>There is no blanket default any more.</b> A creature the table has no
 * opinion about used to read as a zombie, wrong in the one direction that costs a
 * town everything: an unrecognized horror — a modded boss, a vanilla mob nobody
 * got round to naming — was a shambling corpse until it was inside the walls.
 * What the table does not name is now read from what the game itself says about
 * the creature; see {@code unnamed} below, which carries the reasoning for each
 * rung it can hand out.
 *
 * <p><b>The sweep now reaches everything the table has an opinion about.</b> It
 * used to collect {@code Monster}, which is a {@code PathfinderMob} — so a
 * ghast, a phantom, a slime, the ender dragon and, the part that mattered, any
 * modded boss written in vanilla's own boss shape ({@code Mob implements Enemy})
 * never reached the table however carefully it graded them. The sweep collects
 * every {@code Mob} now and keeps whatever {@link #inSight} scores above
 * nothing, so who reaches the reckoning is this table rather than a class name.
 * Nothing here was re-tuned to do it; a town is simply wary of things it could
 * always see and was never told about.
 *
 * <p><b>Neutrality is a state, not a species.</b> A {@link NeutralMob} — a wolf,
 * an enderman, a bee, an iron golem, a mod's own herd beast — is on nobody's
 * side until somebody makes it pick one, so it reaches the table only while its
 * quarrel is with a person. That cuts as well as widens: an enderman standing in
 * a field used to be three points of permanent alarm, and is now worth nothing
 * until it is angry. See {@link #inSight}.
 */
public final class Menace {

    private Menace() {
    }

    /**
     * What one of these is worth.
     *
     * <p>Split into what the creature <em>is</em> (its class, which is the only
     * thing that can tell a drowned from any other zombie) and what the game has
     * <em>registered</em> it as (its type, which is all there is to go on for a
     * mob nobody has named).
     *
     * <p>This is the table, and nothing in the game asks it directly any more:
     * what a town is frightened of this moment is {@link #inSight}, which is this
     * plus the one thing a class cannot say — whether a creature that starts on
     * nobody's side has picked one. An overload taking a live entity used to sit
     * here and was deleted rather than left, because a caller reaching for the
     * obvious name would have got the number without the neutrality rule.
     *
     * <p>The two arguments are the class and the registration of one creature,
     * and asking about a pair that never met is a nonsense question — but it is
     * a nonsense a unit test can hold, and a live entity cannot be built outside
     * a running game. See {@code MenaceTest}.
     *
     * <p>Ordered most specific first — {@link CaveSpider} before {@link Spider},
     * {@link Drowned} before {@link Zombie}, and the illager subclasses before
     * the family — because the first match wins. Matching on the class rather
     * than the exact type is what makes a modded creeper a creeper.
     */
    public static int of(Class<? extends Entity> creature, EntityType<?> kind) {
        if (is(creature, Creeper.class)) {
            // The reason this class exists. Kills the guard who kills it.
            return Danger.DANGEROUS;
        }
        if (is(creature, Warden.class)) {
            // Nothing a settlement can field is going to stop this.
            return Danger.HOPELESS;
        }
        if (is(creature, WitherBoss.class) || is(creature, EnderDragon.class)) {
            // The two things vanilla puts a boss bar on. A town has no answer to
            // either and no business trying to work out which is worse.
            return Danger.HOPELESS;
        }
        if (is(creature, Ravager.class)) {
            return Danger.DIRE;
        }
        if (is(creature, Evoker.class)) {
            return Danger.DANGEROUS;
        }
        if (is(creature, Ghast.class)) {
            // Sets the roofs alight from beyond anything the watch can reach.
            // What it costs a town is buildings rather than guards, which is the
            // same bill a creeper leaves.
            return Danger.DANGEROUS;
        }
        if (is(creature, Witch.class) || is(creature, EnderMan.class)
                || is(creature, AbstractIllager.class)) {
            // Ranged, or hits far harder than a zombie.
            return Danger.FULL_ATTENTION;
        }
        if (is(creature, PiglinBrute.class)) {
            // An axe, no fear and nothing that will buy it off.
            return Danger.FULL_ATTENTION;
        }
        if (is(creature, Blaze.class)) {
            // Shoots fire, out of reach, and cannot be burned back.
            return Danger.FULL_ATTENTION;
        }
        if (is(creature, Breeze.class)) {
            // It will not stand still, it knocks a guard off the wall he is
            // standing on, and arrows bounce off it.
            return Danger.FULL_ATTENTION;
        }
        if (is(creature, AbstractSkeleton.class)) {
            // Shoots back, and does it from outside a guard's reach.
            return Danger.AWKWARD;
        }
        if (is(creature, Drowned.class)) {
            // A skeleton's worth rather than a zombie's, which is the entry this
            // whole table was re-argued for. Only some of them carry a trident,
            // and neither the citizen on the bank nor this method can tell which
            // — so the kind is read at what an armed one is worth, because the
            // cost of being wrong the other way is a thrown trident nobody
            // expected.
            return Danger.AWKWARD;
        }
        if (is(creature, CaveSpider.class)) {
            return Danger.AWKWARD;
        }
        if (is(creature, Phantom.class)) {
            return Danger.AWKWARD;
        }
        if (is(creature, Zombie.class) || is(creature, Spider.class)) {
            return Danger.ROUTINE;
        }
        return unnamed(creature, kind);
    }

    /**
     * What a creature nobody has named is worth.
     *
     * <p>Read off what the game knows about it, in the order that a person
     * watching from a wall would notice things. Each rung, and why:
     *
     * <ul>
     *   <li><b>{@link Danger#NONE}</b> — it is not hostile. A creature the game
     *       files under a friendly {@link MobCategory} and whose class is not an
     *       {@link Enemy} is a cow, a bat or a boat, and a town that jumped at
     *       those would never finish a building.
     *   <li><b>{@link Danger#HOPELESS}</b> — it has a boss bar. The
     *       {@code c:bosses} tag is the one thing a modded boss can be expected
     *       to declare about itself, and a boss is by construction not a fight
     *       for a village watch. Being wrong high here costs a town an afternoon
     *       indoors; being wrong low costs it the town.
     *   <li><b>{@link Danger#FULL_ATTENTION}</b> — it is a {@link Raider}, or it
     *       attacks at range. A raider is a thing built to arrive in a band and
     *       aimed at exactly this kind of settlement, and an archer nobody has
     *       named has to be closed with under fire. Both are read a rung above a
     *       skeleton on purpose: naming a creature is what earns it the lower
     *       number, because a name means somebody has decided it is only that.
     *   <li><b>{@link Danger#AWKWARD}</b> — anything else hostile. A skeleton's
     *       worth, as the floor rather than the ceiling.
     * </ul>
     *
     * <p>That floor is not free and the price should be stated: at two apiece,
     * <em>three</em> unnamed hostiles in sight reach {@code Alarm.ALARMED_AT}
     * and put the whole town indoors, where six of them were needed before. The
     * trade is deliberate — being wrong high about a silverfish costs a town an
     * afternoon's work, and being wrong low about whatever a mod has just
     * spawned in the woods costs it the town — but it is a real change to how
     * often a town stops working. The same arithmetic is the cost of the drowned
     * entry in the table above: three of them on a river bank are a lockdown
     * where they used to be a shrug.
     *
     * <p>{@link MobCategory#MONSTER} alone is enough to be read as hostile, even
     * for the handful of vanilla types that are harmless with it — a zombie
     * horse, a camel husk. The game's own filing is the best evidence there is
     * about a creature nobody has looked at, and the sighting sweep never offers
     * those two anyway.
     */
    private static int unnamed(Class<? extends Entity> creature, EntityType<?> kind) {
        boolean hostile = kind.getCategory() == MobCategory.MONSTER
                || is(creature, Enemy.class);   // Monster implements Enemy; this covers both
        if (!hostile) {
            return Danger.NONE;
        }
        if (isBoss(kind)) {
            return Danger.HOPELESS;
        }
        if (is(creature, Raider.class) || is(creature, RangedAttackMob.class)) {
            return Danger.FULL_ATTENTION;
        }
        return Danger.AWKWARD;
    }

    /**
     * Whether the game puts a boss bar over this.
     *
     * <p>A tag rather than a class check because a modded boss shares no
     * ancestor with vanilla's two, and {@code c:bosses} is the convention
     * NeoForge publishes for saying so. It is only bound on a running server:
     * outside one — in a unit test, which is where this was measured — the
     * lookup answers false for everything, which is why the wither and the ender
     * dragon are named in the table above rather than left to this.
     */
    private static boolean isBoss(EntityType<?> kind) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(kind).is(Tags.EntityTypes.BOSSES);
    }

    /** Reads as "this creature is a creeper", including a mod's own creeper. */
    private static boolean is(Class<? extends Entity> creature, Class<?> family) {
        return family.isAssignableFrom(creature);
    }

    // --- who reaches the table at all ---

    /**
     * What one of these in sight is worth to the town <em>right now</em>.
     *
     * <p>The table above answers what a kind of creature is worth. This answers
     * what one particular creature is worth this moment, which is a different
     * question for exactly one family: a {@link NeutralMob} is on nobody's side
     * until somebody makes it pick one. A wolf asleep in the grass is scenery; the
     * same wolf with a guard's sword in it is a fight. So a neutral is worth
     * nothing at all until it is {@code provoked}, and everything else is worth
     * what it always was, awake or asleep, because a zombie does not need
     * provoking.
     *
     * <p>The floor of {@link Danger#ROUTINE} on a provoked neutral is the one
     * number this adds, and it is the smallest one there is: a creature that has
     * decided to attack somebody is at minimum one guard's routine afternoon —
     * he walks up to it and hits it — which is the definition of that rung. It
     * can only ever fire for a creature the table scores at zero, which is a
     * creature the table has never been asked about, because until now no
     * peaceful-category animal could reach the sweep. Nothing that already had a
     * number gets a different one: an angry enderman is still worth a witch.
     *
     * <p>This is also the sweep's rule about <em>who</em> reaches the reckoning,
     * and deliberately so rather than a second predicate beside it: a creature
     * counts when it is worth something, which is one rule instead of two that
     * could disagree. {@link Danger#NONE} is the refusal, and it is what every
     * cow in the box answers, so widening the sweep from {@code Monster} to every
     * {@code Mob} costs a town no cows.
     */
    public static int inSight(Class<? extends Entity> creature, EntityType<?> kind, boolean provoked) {
        if (!is(creature, NeutralMob.class)) {
            return of(creature, kind);
        }
        return provoked ? Math.max(of(creature, kind), Danger.ROUTINE) : Danger.NONE;
    }

    /** {@link #inSight} about a creature that is standing there. */
    public static int inSight(Entity creature) {
        return inSight(creature.getClass(), creature.getType(), provoked(creature));
    }

    /**
     * Whether the town has anything to fear from this creature right now.
     *
     * <p>The admission test, and it has two readers who must never disagree: the
     * sighting sweep, which is what a town's own eyes report and what its alarm
     * is reckoned from, and the guard, who is the answer to that alarm. They did
     * disagree, and the disagreement was the whole complaint — the sweep was
     * widened to everything this table has an opinion about, and the guard went
     * on collecting {@code Monster}. So a town could be hiding indoors from a
     * phantom no guard would look up at, and could send one out at a calm
     * enderman that had frightened nobody. A watch that fights a different list
     * of creatures from the one the town is afraid of is not a watch.
     *
     * <p>Written as a rung comparison rather than as a second predicate beside
     * {@link #inSight}: a creature counts when it is worth something, which is
     * one rule instead of two that could drift.
     */
    public static boolean threatens(Class<? extends Entity> creature, EntityType<?> kind,
                                    boolean provoked) {
        return inSight(creature, kind, provoked) > Danger.NONE;
    }

    /** {@link #threatens} about a creature that is standing there. */
    public static boolean threatens(Entity creature) {
        return inSight(creature) > Danger.NONE;
    }

    /**
     * Whether this creature's current quarrel is with the town.
     *
     * <p>Read from what it is doing rather than from a flag, because "angry" on
     * its own is not the question a town is asking. An iron golem beating a
     * zombie in the square is angry and is on the town's side; a wolf tearing
     * into a sheep is angry and is a wolf being a wolf. Both of those are the
     * town's afternoon going normally.
     *
     * <p>So it is one of the town's own people or it is nothing — not a player
     * either, deliberately. A wolf chasing somebody through the woods is that
     * somebody's problem, and a golem chasing the man who punched it is the
     * town's defender doing its job. Counting either would make a town wary of
     * fights it is not in, and the golem case would have it counting its own
     * guard as danger.
     *
     * <p>A creature that has lost sight of who it is angry with reads as calm for
     * as long as that lasts. It is not a hole: the town remembers a sighting for
     * several steps after it stops seeing it, which is precisely the case this
     * would otherwise have to handle itself.
     */
    private static boolean provoked(Entity creature) {
        return creature instanceof Mob mob && mob.getTarget() instanceof PersonEntity;
    }

    // --- how a creature treats a citizen ---

    /**
     * What a creature will do about a citizen.
     *
     * <p>By class and interface only — never by a list of mods — so a creature a
     * mod added is covered by being what it is. What the game says about it is
     * all there is to go on and all that is needed.
     */
    public enum Regard {

        /** It hunts one, unasked, the way it hunts a player. */
        HUNTS,

        /** It leaves one alone until struck, and then remembers who struck it. */
        RETALIATES,

        /** A citizen is nothing to it either way. */
        IGNORES
    }

    /**
     * How this creature should treat a citizen.
     *
     * <p>{@link NeutralMob} is asked first and it wins, because the interface is
     * the game saying "this one starts on nobody's side" and that outranks the
     * category it was filed under: a zombified piglin is registered as a monster
     * and graded like one, and still must not hunt a citizen who has not touched
     * it. Everything else follows the table — anything worth anything at all
     * hunts, and anything worth nothing does not care.
     *
     * <p>Raiders fall out of this rather than being named: a raider is an
     * {@link Enemy} and is not neutral, so it hunts. That matters more than it
     * looks, because vanilla's raiders hunt {@code AbstractVillager} and a
     * citizen is not one — a pillager band would walk through a town without
     * seeing anybody in it.
     */
    public static Regard regards(Class<? extends Entity> creature, EntityType<?> kind) {
        if (is(creature, NeutralMob.class)) {
            return Regard.RETALIATES;
        }
        return of(creature, kind) > Danger.NONE ? Regard.HUNTS : Regard.IGNORES;
    }

    /**
     * Whether a citizen should keep well away from this rather than share a
     * street with it.
     *
     * <p>Only creepers, and deliberately so. Every other hostile is a thing you
     * can be near and survive; a creeper is a thing whose whole purpose is to be
     * near you. Civilians run from these on sight, and guards fight them only by
     * hitting and stepping back out of the blast.
     */
    public static boolean blowsUp(Entity hostile) {
        return hostile instanceof Creeper;
    }
}
