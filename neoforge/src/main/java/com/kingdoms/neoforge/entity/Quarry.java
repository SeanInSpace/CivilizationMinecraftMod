package com.kingdoms.neoforge.entity;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.bridge.Menace;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.util.OptionalInt;

/**
 * Teaching the world's creatures that a citizen is a person.
 *
 * <p>Vanilla's hostiles hunt two things: a player, and an {@code AbstractVillager}.
 * A citizen is neither — the view entity is a plain humanoid on purpose, because
 * the villager brain assumes the body owns its own life and in this mod the
 * record does. So every hostile in the game walked through a town without seeing
 * a soul in it. One line in the join handler had fixed that for zombies and
 * zombies only, which meant the mod's own raids worked and nothing else did: a
 * pillager band, a skeleton on a roof, a creeper in the square and every creature
 * any mod has ever added all ignored the people they were standing among.
 *
 * <p>The rule is {@link Menace.Regard} and it is asked of the creature's class and
 * registration, never of a list of names, so a mod's own monster is covered by
 * being a monster. Three answers and what each one buys:
 *
 * <ul>
 *   <li><b>Hunts.</b> A {@link Hunt} for citizens, one slot behind the creature's
 *       own hunt for players — see {@link #teach} for why one behind rather than
 *       alongside.
 *   <li><b>Retaliates.</b> A neutral creature must not be given a reason to come
 *       for anybody, so it gets a {@link Grudge}: vanilla's own anger goal with a
 *       citizen in the place of the player, which hunts one only while it is
 *       already angry at that one. The first blow needs nothing from us —
 *       {@code HurtByTargetGoal} asks who hit it and does not care what the
 *       answer is, and a guard's swing goes through the ordinary damage path —
 *       but without this the anger dies with line of sight, and a wolf that
 *       forgets a guard the moment he steps behind a house is not a wolf.
 *   <li><b>Ignores.</b> Nothing. Cows, boats, and citizens themselves.
 * </ul>
 *
 * <p><b>A creature that keeps its target in a brain rather than in a goal
 * ignores all of this</b> — a warden, a piglin, a breeze, a zoglin — because it
 * reads {@code getTarget} from a memory the goal never writes. The goal is inert
 * there rather than wrong, which is the right way round for a thing applied to
 * every creature that ever joins a level, including ones written years from now.
 */
public final class Quarry {

    private Quarry() {
    }

    /**
     * Where a citizen goal goes when the creature hunts no player at all.
     *
     * <p>Three, which is what the rule below produces for the commonest case — a
     * player hunt at two — and where this mod's own zombie line has been since it
     * was written.
     */
    private static final int VILLAGER_PRECEDENT = 3;

    /** Vanilla's own interval for an anger goal, in ticks between looks. */
    private static final int ANGER_LOOK_INTERVAL = 10;

    /**
     * Gives one creature whatever regard for citizens its own nature earns it.
     *
     * <p><b>One slot behind its hunt for players, not alongside it.</b> Goals
     * holding the same flag only give way to a <em>strictly</em> lower number, so
     * a citizen goal at the player's own priority does not tie with it — it locks
     * it out, and a zombie that started after a settler would ignore a player
     * standing beside it until the settler was dead. One behind is also, and this
     * is the part worth knowing, exactly where vanilla puts its own hunt for
     * villagers in every mob that has one: a zombie, a drowned, a pillager and an
     * evoker all hunt players at two and villagers at three, and a ravager hunts
     * players at three and villagers at four. The rule is read off the creature
     * rather than copied from a zombie so that a modded mob which deliberately
     * puts people behind six other errands keeps that order.
     *
     * <p>Called for every mob that joins a level, so it must be cheap and it must
     * never throw. It walks the creature's target goals once, which is a handful
     * of entries.
     */
    public static void teach(Mob creature) {
        Menace.Regard regard = Menace.regards(creature.getClass(), creature.getType());
        if (regard == Menace.Regard.IGNORES) {
            return;
        }
        OptionalInt playerSlot = OptionalInt.empty();
        for (WrappedGoal wrapped : creature.targetSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof Hunt || goal instanceof Grudge) {
                // Taught already. An entity normally joins a level once, but
                // nothing promises it, and two goals hunting the same thing is a
                // creature that looks for it twice as hard.
                return;
            }
            Class<?> hunted = hunts(goal);
            if (hunted != null && isPlayerHunt(hunted)
                    && (playerSlot.isEmpty() || wrapped.getPriority() < playerSlot.getAsInt())) {
                playerSlot = OptionalInt.of(wrapped.getPriority());
            }
        }
        int slot = citizenSlot(playerSlot);
        if (regard == Menace.Regard.HUNTS) {
            creature.targetSelector.addGoal(slot, new Hunt(creature));
        } else if (creature instanceof NeutralMob neutral) {
            // Always true here: RETALIATES is the answer Menace gives to a
            // NeutralMob and to nothing else. Written as a test rather than a
            // cast because this runs on every entity that joins a world, and a
            // creature that quietly gets no goal is better than one that takes
            // the server down.
            creature.targetSelector.addGoal(slot, new Grudge(creature, neutral));
        }
    }

    /**
     * The slot a citizen goal takes, given where the creature hunts players.
     *
     * <p>One behind, for the reasons in {@link #teach}, and the fallback is what
     * that produces for a player hunt at two — where all but one vanilla mob puts
     * theirs.
     *
     * <p>It will often land on a slot the creature is already using, and that is
     * left alone: the goals it lands on are the creature's other hunts for
     * everything that is not a player, and vanilla ties those with each other
     * already — a zombie's villager and iron golem hunts share three, a ravager's
     * share four. Two goals at one number cannot interrupt each other, which is
     * the right relationship between peers and the wrong one with a player, and
     * the player is the one this avoids.
     */
    static int citizenSlot(OptionalInt playerSlot) {
        return playerSlot.isPresent() ? playerSlot.getAsInt() + 1 : VILLAGER_PRECEDENT;
    }

    /**
     * A creature hunting citizens the way it hunts anyone else.
     *
     * <p>This one cannot be short-circuited the way {@link Grudge} is — a hostile
     * has to look to find anybody — so every hostile in every loaded chunk now
     * sweeps for settlers a few times a second, where before only zombies did.
     * It is one more of a thing vanilla already does several of: a skeleton
     * already sweeps for an iron golem and for a turtle, and a zombie for a
     * villager as well, all through the same machinery and all in chunks that
     * have never seen a town. Worth knowing rather than worth avoiding.
     *
     * <p><b>What it does not copy is the condition on the creature's player
     * hunt</b>, which is private to that goal and not worth two more reflective
     * reads plus a wrapper to survive a modded condition that assumes its target
     * is a player. It costs one creature something real: a ghast and a slime both
     * hunt players only within four blocks of their own height, and a ghast can
     * see a hundred blocks, so a ghast will now come for a settler from an
     * altitude it would never come for a player from. In the Nether, where the
     * only ghasts are. The danger table already reads a ghast as a thing that
     * burns roofs from beyond anything the watch can reach, so this is that
     * sentence being more true than it was rather than a new kind of wrong —
     * but it is a change and somebody should look at it.
     */
    private static final class Hunt extends NearestAttackableTargetGoal<PersonEntity> {

        private Hunt(Mob creature) {
            super(creature, PersonEntity.class, true);
        }
    }

    /**
     * A neutral creature coming back for the citizen who started it.
     *
     * <p>Vanilla's anger goal with a citizen where the player goes, and one
     * addition: it does not look at all while the creature is calm. That matters
     * because looking is not free — a targeting goal with anything but a player
     * in it walks every entity within the creature's follow range, several times
     * a second — and this goal is on every bee in a meadow and every wolf in a
     * taiga, none of which has ever had a grudge against anybody. Being angry is
     * a clock comparison, and a creature that is not angry cannot be angry at a
     * citizen, so nothing is given up for it.
     */
    private static final class Grudge extends NearestAttackableTargetGoal<PersonEntity> {

        private final NeutralMob anger;

        private Grudge(Mob creature, NeutralMob anger) {
            super(creature, PersonEntity.class, ANGER_LOOK_INTERVAL, true, false, anger::isAngryAt);
            this.anger = anger;
        }

        @Override
        public boolean canUse() {
            return anger.isAngry() && super.canUse();
        }
    }

    /**
     * Whether a goal that hunts this class is the creature's hunt for people.
     *
     * <p>Asked the wide way round on purpose. A goal aimed at {@code LivingEntity}
     * — a guardian's — hunts players among everything else, and a creature that
     * will drop what it is doing for anything alive is telling us where its
     * interest in a person sits.
     */
    static boolean isPlayerHunt(Class<?> hunted) {
        return hunted.isAssignableFrom(Player.class);
    }

    /**
     * What a goal is looking for, or null if it is not the kind that looks.
     *
     * <p>Reflection, and only because there is no other way to ask: the field is
     * protected, NeoForge's own access transformer does not widen it, and the
     * class offers no accessor. An access transformer of our own would remove
     * this — and would turn a soft failure into an {@code IllegalAccessError} at
     * class load if the transformer ever failed to reach the running game, which
     * is a trade nobody should make for a goal priority. Read once into
     * {@link #WHAT_A_GOAL_HUNTS} rather than per creature.
     */
    private static Class<?> hunts(Goal goal) {
        if (WHAT_A_GOAL_HUNTS == null || !(goal instanceof NearestAttackableTargetGoal<?>)) {
            return null;
        }
        try {
            return (Class<?>) WHAT_A_GOAL_HUNTS.get(goal);
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * The field a targeting goal keeps its quarry's class in.
     *
     * <p>Null if it cannot be reached, and then every creature falls back to the
     * villager precedent instead of the handler throwing on every mob in the
     * world. Losing it costs the reading of a modded mob's own ordering and
     * nothing else — a zombie's citizen goal lands on three either way — which is
     * why the fallback is allowed to be silent.
     */
    private static final Field WHAT_A_GOAL_HUNTS = whatAGoalHunts();

    private static Field whatAGoalHunts() {
        try {
            Field field = NearestAttackableTargetGoal.class.getDeclaredField("targetType");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException unreachable) {
            // Said out loud, because it cannot be found any other way: the unit
            // test runs off a plain classpath where this always succeeds, and
            // the degraded mode is quiet by design. Somebody wondering why a
            // modded mob hunts settlers sooner than it hunts players needs this
            // line to be in the log.
            KingdomsMod.LOGGER.warn(
                    "Cannot read what a targeting goal hunts ({}); every creature will "
                            + "hunt settlers in the villager slot rather than its own",
                    unreachable.toString());
            return null;
        }
    }

    /** Whether a goal's quarry can be read at all. For {@code QuarryTest}. */
    static boolean readsWhatGoalsHunt() {
        return WHAT_A_GOAL_HUNTS != null;
    }
}
