package com.kingdoms.neoforge.bridge;

import com.kingdoms.sim.settlement.Danger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
 * town everything: an unrecognised horror — a modded boss, a vanilla mob nobody
 * got round to naming — was a shambling corpse until it was inside the walls.
 * What the table does not name is now read from what the game itself says about
 * the creature; see {@code unnamed} below, which carries the reasoning for each
 * rung it can hand out.
 *
 * <p><b>The sweep is narrower than this table.</b>
 * {@code NeoForgeWorldBridge.hostilesSeen} collects {@code Monster}, and three
 * of the entries below are not monsters: a ghast, a phantom and the ender dragon
 * are all {@code Mob implements Enemy}. So is a slime, and so — this is the part
 * that matters — is the shape a modded boss is most likely to take, since
 * {@code Monster} is a {@code PathfinderMob} and a thing that flies is not. The
 * boss rung in {@code unnamed} can therefore only fire today for a boss that
 * happens to extend {@code Monster}.
 *
 * <p>The entries stay, and the sweep is left alone on purpose: this table is the
 * town's opinion about a creature rather than a list of what the sweep happens
 * to catch, and widening the sweep would make a town wary of phantoms, slimes
 * and swamp weather overnight. That is a change somebody has to watch happen,
 * not one to make while renaming constants.
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
     */
    public static int of(Entity hostile) {
        return of(hostile.getClass(), hostile.getType());
    }

    /**
     * The same question with the body taken away.
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
            // Shoots fire, out of reach, and cannot be burnt back.
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
