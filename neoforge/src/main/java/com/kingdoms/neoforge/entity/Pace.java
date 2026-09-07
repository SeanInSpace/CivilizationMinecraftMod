package com.kingdoms.neoforge.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How fast a citizen ever moves. One number, and there is no second one.
 *
 * <p>There used to be five. A farmer walked a row at 0.6, a lumberjack crossed
 * to a tree at 0.7, a foreman at 0.65; a town under alarm ran for its doors at
 * 0.9, a guard charged at 0.9, and a settler who saw a creeper hit 1.3 — more
 * than twice a working pace. Players read that correctly and disliked it: the
 * townsfolk were plainly capable of moving at that speed the whole time and were
 * choosing not to until something scary showed up. Danger was handing out
 * statistics.
 *
 * <p>It is not supposed to. Danger is supposed to change what a person
 * <em>decides</em> — that they down tools now rather than at the end of the row,
 * that they head for their own door rather than the field, that they notice the
 * creeper at eighteen blocks instead of ten — and every one of those is a
 * decision a person makes at their ordinary walking speed. A settler survives a
 * creeper by having started sooner, not by being faster than they were an
 * instant ago.
 *
 * <p>So: {@link #WALK}, and nothing above it, ever, for anybody, in any state.
 * Guards included — a guard closing on a zombie walks like a man with a job, and
 * a guard who could sprint would be a guard who had been strolling. Everything
 * that used to name its own speed now names this, and {@link #all} is the list
 * a test walks to keep the next one honest.
 */
public final class Pace {

    private Pace() {
    }

    /**
     * A citizen's walking pace, as a navigation modifier on their 0.5
     * movement-speed attribute — so 0.35 blocks per tick, seven blocks a second.
     *
     * <p>0.7 rather than 0.6: it is the fastest anybody already walked at during
     * ordinary work (a lumberjack to a tree, a miner to a face, a digger to a
     * stand), so collapsing the range upward is the choice where nobody in the
     * town gets visibly slower than players have already seen them. The change
     * players do see is entirely downward, and entirely in the panic speeds,
     * which is the complaint.
     *
     * <p>It also happens to be the number the escape arithmetic needs. At 0.35
     * blocks per tick a fleeing settler out-walks a creeper's 0.25 on open
     * ground; at 0.6 they would still out-walk it, but with a third less margin
     * to spend on a path that bends. See {@code FleeCreepersGoal}.
     */
    public static final double WALK = 0.7;

    /**
     * Pottering about with nothing to do — the idle stroll goal, and the only
     * pace below walking, because ambling is a mood and not an emergency.
     */
    public static final double STROLL = 0.35;

    /**
     * Every pace a citizen can be handed, by name.
     *
     * <p>Exists for the test that asserts none of them exceeds {@link #WALK}.
     * The list being here rather than in the test is deliberate: a new pace is
     * added next to the others or it is not a pace.
     */
    public static Map<String, Double> all() {
        Map<String, Double> paces = new LinkedHashMap<>();
        paces.put("walk", WALK);
        paces.put("stroll", STROLL);
        return paces;
    }

    /** Whether a speed is one a citizen may be given. */
    public static boolean allows(double speed) {
        return speed <= WALK;
    }
}
