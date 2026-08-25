package com.kingdoms.neoforge.bridge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;

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
 * <p>Scale, so the numbers are arguable rather than arbitrary: <b>1</b> is one
 * guard's routine afternoon. <b>3</b> is a guard's full attention. <b>6</b> is
 * a thing a lone guard probably loses to. Tune these; they are the whole of the
 * town's opinion about danger and nothing else needs to change with them.
 */
public final class Menace {

    private Menace() {
    }

    /** A creature nobody has a specific opinion about. */
    public static final int ORDINARY = 1;

    /**
     * What one of these is worth.
     *
     * <p>Ordered most specific first — {@link CaveSpider} before {@link Spider},
     * and the illager subclasses before the family — because the first match
     * wins.
     */
    public static int of(Entity hostile) {
        if (hostile instanceof Creeper) {
            // The reason this class exists. Kills the guard who kills it.
            return 4;
        }
        if (hostile instanceof Warden) {
            // Nothing a settlement can field is going to stop this.
            return 10;
        }
        if (hostile instanceof Ravager) {
            return 5;
        }
        if (hostile instanceof Evoker) {
            return 4;
        }
        if (hostile instanceof Witch || hostile instanceof EnderMan
                || hostile instanceof AbstractIllager) {
            // Ranged, or hits far harder than a zombie.
            return 3;
        }
        if (hostile instanceof AbstractSkeleton) {
            // Shoots back, and does it from outside a guard's reach.
            return 2;
        }
        if (hostile instanceof CaveSpider) {
            return 2;
        }
        if (hostile instanceof Phantom) {
            return 2;
        }
        if (hostile instanceof Zombie || hostile instanceof Spider) {
            return ORDINARY;
        }
        return ORDINARY;
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
