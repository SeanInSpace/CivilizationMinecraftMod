package com.civilization.sim.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a town thinks of each person who has done something for it.
 *
 * <p>Per settlement rather than per kingdom, and deliberately: the point of a
 * standing is that it is <em>this</em> town's, earned at this board, and that
 * helping a village on one coast does not buy you anything on the other. A
 * kingdom-wide number would make every town the same town.
 *
 * <p>It only ever goes up. Nothing in the mod can yet wrong a town badly enough
 * to lose its regard — killing a settler is not attributed to anybody — so a
 * falling standing would be a rule with no way to fire it. The room is left:
 * {@link #add} takes a negative just as happily.
 *
 * <p>Named for what it is rather than "standing", because
 * {@code com.civilization.sim.kingdom.Standing} is already the posture between two
 * kingdoms and two classes of that name in one save format is a trap. The word
 * a player reads is still "standing".
 */
public final class Reputation {

    /** Past this, the town treats you as one of its own suppliers. */
    public static final int TRUSTED_AT = 20;

    /** Past this, the town names you when it talks about its luck. */
    public static final int HONORED_AT = 60;

    /** Past this, you are family. */
    public static final int SWORN_AT = 120;

    private final Map<UUID, Integer> byPlayer = new LinkedHashMap<>();

    public int of(UUID player) {
        return player == null ? 0 : byPlayer.getOrDefault(player, 0);
    }

    /** Moves somebody's standing and reports where it ends up. Never below zero. */
    public int add(UUID player, int change) {
        if (player == null || change == 0) {
            return of(player);
        }
        int now = Math.max(0, of(player) + change);
        if (now == 0) {
            byPlayer.remove(player);
        } else {
            byPlayer.put(player, now);
        }
        return now;
    }

    /**
     * The highest standing this town holds with anybody.
     *
     * <p>What the board's difficulty is set from. A board is one board — it is
     * pinned to a post, not handed to a player — so the town cannot offer each
     * reader a different tier of work without the post lying to somebody. It
     * asks for what its best friend could manage, and everyone else may try.
     */
    public int best() {
        int best = 0;
        for (int standing : byPlayer.values()) {
            best = Math.max(best, standing);
        }
        return best;
    }

    public boolean isEmpty() {
        return byPlayer.isEmpty();
    }

    public Map<UUID, Integer> all() {
        return Collections.unmodifiableMap(byPlayer);
    }

    public void restore(Map<UUID, Integer> saved) {
        byPlayer.clear();
        saved.forEach((player, standing) -> {
            if (player != null && standing != null && standing > 0) {
                byPlayer.put(player, standing);
            }
        });
    }

    /** Which rung of the ladder this much standing is on: 0 through 3. */
    public static int tierOf(int standing) {
        if (standing >= SWORN_AT) {
            return 3;
        }
        if (standing >= HONORED_AT) {
            return 2;
        }
        return standing >= TRUSTED_AT ? 1 : 0;
    }

    /** What the town would call you, in one word. */
    public static String wordFor(int standing) {
        return switch (tierOf(standing)) {
            case 3 -> "Sworn";
            case 2 -> "Honored";
            case 1 -> "Trusted";
            default -> "Stranger";
        };
    }
}
