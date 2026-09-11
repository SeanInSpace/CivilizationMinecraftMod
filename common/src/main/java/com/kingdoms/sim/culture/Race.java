package com.kingdoms.sim.culture;

import java.util.Locale;

/**
 * What kind of body a people is born into, and the three numbers that follow.
 *
 * <p>A race is not a culture and the two are deliberately separate. A culture is
 * how a people <em>build</em> — the arrangement, the beasts, the names, the
 * architecture — and several cultures can share one body: Normans, highlanders,
 * burghers and vale folk are four peoples and one race, which is exactly the
 * distinction this type exists to make. Orcs are one race with one culture
 * today, and that is a gap in the table rather than a property of orcs.
 *
 * <p>The numbers are small on purpose. The user asked for orcs who are tougher,
 * <em>minimally</em> harder-hitting and <em>minimally</em> slower, and the way
 * to keep a promise like that is to write it as one table nobody can drift from:
 *
 * <table border="1">
 *   <caption>The three numbers</caption>
 *   <tr><th>race</th><th>max health</th><th>attack bonus</th><th>pace factor</th></tr>
 *   <tr><td>{@link #HUMAN}</td><td>20</td><td>0</td><td>1.0</td></tr>
 *   <tr><td>{@link #ORC}</td><td>30</td><td>+1</td><td>0.9</td></tr>
 *   <tr><td>{@link #GOBLIN}</td><td>14</td><td>0</td><td>1.1</td></tr>
 * </table>
 *
 * <p><strong>{@link #paceFactor} is a base-attribute multiplier and not a second
 * gear.</strong> Everything that walks anybody anywhere still hands the
 * navigation {@code Pace.WALK}, so the rule that nobody in this mod ever moves
 * faster than their own walking pace holds per race automatically: an orc's walk
 * is 0.9 of a human's walk and his panic is the same 0.9 of it, because the race
 * lives in the attribute the modifier is applied to rather than in the modifier.
 * Danger still buys no speed at all, for anybody.
 *
 * <p><strong>{@link #attackBonus} is a hook as much as a number.</strong> It sits
 * on top of the guard's base damage, and weapon tiers sit on top of it — so an
 * orc with an iron sword is base plus race plus weapon, and nothing about the
 * smithy has to know what an orc is.
 *
 * <p>Pure simulation, like everything else in this package: three numbers and a
 * word, and not one Minecraft type in sight.
 */
public enum Race {

    /**
     * The measure everything else is stated against.
     *
     * <p>Twenty health, no bonus, and a pace factor of exactly one — which is to
     * say a human is what the mod has always shipped, written down. Four
     * cultures wear this body: see {@link Culture#humans()}.
     */
    HUMAN(20.0, 0, 1.0),

    /**
     * Bigger, a shade harder to put down, a shade slower on his feet.
     *
     * <p>Half again a human's health is the one number here that is not
     * "minimal", and it is the one the user named first. The other two are: one
     * point of damage, which is a quarter of a guard's bare-handed swing and less
     * than the difference between his wooden sword and his iron one; and a tenth
     * off the pace, which over the twenty blocks a guard closes is about half a
     * second. Big enough to feel in a fight that lasts, small enough that an orc
     * is not a different game.
     */
    ORC(30.0, 1, 0.9),

    /**
     * Small, quick, and easy to kill.
     *
     * <p>The other end of the same argument, and the reason the table has three
     * rows rather than two: if the only non-human race were stronger in every
     * column the type would be a difficulty setting rather than a body.
     */
    GOBLIN(14.0, 0, 1.1);

    private final double maxHealth;
    private final int attackBonus;
    private final double paceFactor;

    Race(double maxHealth, int attackBonus, double paceFactor) {
        this.maxHealth = maxHealth;
        this.attackBonus = attackBonus;
        this.paceFactor = paceFactor;
    }

    /** How much punishment one of these takes before it falls. */
    public double maxHealth() {
        return maxHealth;
    }

    /**
     * What this body adds to a blow, before anything it is holding.
     *
     * <p>Read by the guard's strike and by nothing else today. Weapons add on
     * top of it rather than replacing it.
     */
    public int attackBonus() {
        return attackBonus;
    }

    /**
     * What this body multiplies the base movement-speed attribute by.
     *
     * <p>One for a human by construction, so the humans the mod has always had
     * move at exactly the speed they always did.
     */
    public double paceFactor() {
        return paceFactor;
    }

    /** The word for one of these in a chat line: {@code "orc"}. */
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The race that word names, or {@link #HUMAN} when nobody has said.
     *
     * <p>Falls back rather than throwing, for the same reason
     * {@link Culture#of} does: an id a datapack made up, or a save written
     * before any of this existed, should produce ordinary people and not an
     * exception halfway through loading a world.
     */
    public static Race of(String word) {
        if (word == null) {
            return HUMAN;
        }
        for (Race race : values()) {
            if (race.word().equals(word.toLowerCase(Locale.ROOT))) {
                return race;
            }
        }
        return HUMAN;
    }
}
