package com.civilization.sim.combat;

import com.civilization.sim.culture.Race;

import java.util.List;
import java.util.UUID;

/**
 * What an orc carries, and what it is worth in a fight.
 *
 * <p>A table rather than a pile of {@code if}s in the view layer, and it lives
 * in {@code :common} for the usual reason: everything here is arithmetic on a
 * name, none of it needs a world, and the one place these numbers must not be
 * allowed to drift is a test that can run without one. The view layer maps each
 * entry onto a real registered item; nothing below knows that items exist.
 *
 * <p><strong>Two tiers, one shape.</strong> Every weapon is forged twice — a
 * {@code crude} one the camp beats out for itself and a {@code forged} one off
 * the smithy's rack. They are separate registered items, because a tier a
 * player cannot see is a smithy whose whole argument is invisible, and the
 * human watch has always been able to tell wood from iron at a glance. The rack
 * upgrade swaps the crude item for the forged one and nothing else changes: the
 * weapon a given orc carries is decided once, by who he is, and he keeps it.
 *
 * <p><strong>Two hands or one.</strong> A greatsword and a morningstar take
 * both hands, so their bearers carry no bow; a falchion or an axe leaves a hand
 * for one. That is the only thing the grip decides, and it is why the watch's
 * answer to a creeper still exists in an orc town — see {@code GuardStance}.
 */
public enum Weaponry {

    /** Both hands, slowest but one, and the heaviest thing in the camp bar the flail. */
    GREATSWORD("orc_greatsword", Grip.TWO_HANDED),

    /** One hand, quick, the workaday blade of the line. */
    FALCHION("orc_falchion", Grip.ONE_HANDED),

    /** One hand, a hair slower than the falchion and a hair heavier. Butcher's tool. */
    CLEAVER("orc_cleaver", Grip.ONE_HANDED),

    /** One hand, a woodsman's axe swung at people. */
    AXE("orc_axe", Grip.ONE_HANDED),

    /** Both hands, slowest in the table, and the hardest single blow. */
    MORNINGSTAR("orc_morningstar", Grip.TWO_HANDED);

    /** Whether a weapon leaves a hand free. */
    public enum Grip {
        ONE_HANDED, TWO_HANDED
    }

    private final String crudeName;
    private final Grip grip;

    Weaponry(String crudeName, Grip grip) {
        this.crudeName = crudeName;
        this.grip = grip;
    }

    /** The registered name of the camp-beaten version. */
    public String crudeName() {
        return crudeName;
    }

    /**
     * The registered name of the smithy's version.
     *
     * <p>Derived rather than stored beside the other, so the two can never
     * disagree — the same reason {@code Culture.style()} derives a folder from
     * an id instead of carrying one.
     */
    public String forgedName() {
        return crudeName + "_forged";
    }

    /** The name of this weapon at the given tier. */
    public String nameAt(boolean forged) {
        return forged ? forgedName() : crudeName();
    }

    public Grip grip() {
        return grip;
    }

    /** Whether its bearer has a hand left for a bow. */
    public boolean carriesBow() {
        return grip == Grip.ONE_HANDED;
    }

    /**
     * What the weapon is worth on top of a bare fist, by tier.
     *
     * <p>The same two rungs for every weapon in the table, deliberately. A
     * guard's swing is {@code GUARD_DAMAGE} plus this, and making it depend on
     * which of five weapons he happened to be dealt would turn a cosmetic roll
     * into a silent handicap: two guards of the same town, same smithy, doing
     * different damage for no reason either of them chose.
     *
     * <p>Both rungs sit above the human watch's, whose wooden sword is
     * <strong>+1</strong> and whose iron one is <strong>+3</strong>. That is the
     * orc's side of the bargain — more damage a swing, and see {@code Race} for
     * the rest of it.
     */
    public static final float CRUDE_BONUS = 2.0F;
    public static final float FORGED_BONUS = 4.0F;

    /** What a weapon of this tier adds to a swing. */
    public static float bonusFor(boolean forged) {
        return forged ? FORGED_BONUS : CRUDE_BONUS;
    }

    /**
     * What a settler who is not of the watch hits for, before his weapon.
     *
     * <p>Half the watch's {@code GUARD_DAMAGE}, and it must stay below it. A
     * farmer who swings as hard as a trained guard is a town with no reason to
     * post one.
     */
    public static final float CIVILIAN_BASE_DAMAGE = 2.0F;

    /**
     * How far a civilian will answer a blow, in blocks.
     *
     * <p>He does not charge and he does not follow. Something that hit him and
     * is still within arm's reach gets hit back; something that hit him and
     * walked off is somebody else's problem, which is the whole difference
     * between a civilian defending himself and a second, worse guard.
     */
    public static final double CIVILIAN_REACH = 4.0;

    /**
     * What the watch is issued from, in a fixed order.
     *
     * <p>The cleaver is not in it: it is a kitchen tool that happens to cut, and
     * the one weapon here that reads as something a settler already owned.
     */
    public static final List<Weaponry> GUARD_KIT =
            List.of(GREATSWORD, FALCHION, AXE, MORNINGSTAR);

    /**
     * What everybody else carries, in a fixed order.
     *
     * <p>Both one-handed, because a farmer with a greatsword is not a farmer,
     * and both plausible as things somebody owns for another reason.
     */
    public static final List<Weaponry> CIVILIAN_KIT = List.of(CLEAVER, AXE);

    /** Which weapon the watch deals this person. */
    public static Weaponry forGuard(UUID personId) {
        return GUARD_KIT.get(Math.floorMod(spread(personId), GUARD_KIT.size()));
    }

    /** Which weapon this person owns when he is not of the watch. */
    public static Weaponry forCivilian(UUID personId) {
        return CIVILIAN_KIT.get(Math.floorMod(spread(personId), CIVILIAN_KIT.size()));
    }

    /** The weapon of that registered name at either tier, or null for anything else. */
    public static Weaponry byName(String name) {
        for (Weaponry weapon : values()) {
            if (weapon.crudeName().equals(name) || weapon.forgedName().equals(name)) {
                return weapon;
            }
        }
        return null;
    }

    /** Whether that registered name is the smithy's version of something. */
    public static boolean isForged(String name) {
        Weaponry weapon = byName(name);
        return weapon != null && weapon.forgedName().equals(name);
    }

    /** Every registered name this table stands for, crude and forged alike. */
    public static List<String> everyName() {
        return java.util.Arrays.stream(values())
                .flatMap(weapon -> java.util.stream.Stream.of(weapon.crudeName(), weapon.forgedName()))
                .toList();
    }

    /**
     * Whether a people arms everybody rather than only the watch.
     *
     * <p>Asked of the <em>race</em> and not the culture, deliberately. Arming
     * the miller is a fact about the kind of body a people is born into, the
     * same way {@link com.civilization.sim.culture.Race#attackBonus} is; the culture
     * is how they lay their streets out. Orcs have one culture today and will
     * have four, and every one of them will arm everybody without this line
     * being touched.
     */
    public static boolean armsEveryone(Race race) {
        return race == Race.ORC;
    }

    /**
     * SplitMix64's finalizer over a person's id.
     *
     * <p>The same trick, and the same reasoning, as {@code Culture.spread}: the
     * choice has to be stable across reloads and across the test suite without
     * anything being written down, and it has to avalanche, or a run of ids
     * allocated together hands a whole barracks one weapon.
     */
    private static long spread(UUID personId) {
        long h = personId.getMostSignificantBits() * 0x9E3779B97F4A7C15L
                ^ personId.getLeastSignificantBits() * 0xC2B2AE3D27D4EB4FL
                ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
