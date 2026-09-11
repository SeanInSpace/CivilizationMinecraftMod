package com.civilization.sim.quest;

import com.civilization.sim.geom.SimPos;

import java.util.Objects;
import java.util.UUID;

/**
 * One thing a town is asking somebody to do.
 *
 * <p>Immutable, and every change makes a new one. That is not ceremony: a quest
 * is read by a screen, a payload, a codec and three counters, and the one bug
 * this shape rules out is the interesting one — a row drawn from a quest that
 * changed underneath it halfway down the panel.
 *
 * <p><strong>The words travel with it.</strong> The title and the detail are
 * written once, when the quest is posted, and then carried rather than derived.
 * A town asks for grain <em>because</em> it is starving, and by the time the
 * player reads the board it may not be — a description re-derived on every draw
 * would quietly stop matching the thing it is describing. What the town said
 * when it asked is what it asked.
 *
 * @param target the ledger word for a delivery, or a plain word naming what is
 *               wanted for the kinds that are not about goods
 * @param place  where this is, or null for a quest that is not anywhere
 */
public record Quest(String id, QuestKind kind, String title, String detail,
                    String target, SimPos place, int amount, int progress,
                    Reward reward, long offeredOn, long expiresOn,
                    QuestState state, UUID acceptedBy) {

    public Quest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        title = title == null ? "" : title;
        detail = detail == null ? "" : detail;
        target = target == null ? "" : target;
        reward = reward == null ? Reward.NOTHING : reward;
        amount = Math.max(1, amount);
        progress = Math.min(Math.max(0, progress), amount);
    }

    /** A fresh offer, nobody's yet. */
    public static Quest offered(String id, QuestKind kind, String title, String detail,
                                String target, SimPos place, int amount, Reward reward,
                                long offeredOn, long expiresOn) {
        return new Quest(id, kind, title, detail, target, place, amount, 0, reward,
                offeredOn, expiresOn, QuestState.OFFERED, null);
    }

    public boolean isOffered() {
        return state == QuestState.OFFERED;
    }

    public boolean isAccepted() {
        return state == QuestState.ACCEPTED;
    }

    public boolean isDone() {
        return state == QuestState.DONE;
    }

    /** Whether this is the quest that player took. */
    public boolean isHeldBy(UUID player) {
        return acceptedBy != null && acceptedBy.equals(player);
    }

    public int remaining() {
        return Math.max(0, amount - progress);
    }

    /** How far along, in whole percent, for a bar or a line of text. */
    public int percent() {
        return (int) Math.round(100.0 * progress / amount);
    }

    /**
     * Taken by somebody, with a longer leash than the offer had.
     *
     * <p>An offer goes stale in {@link QuestPlanner#OFFER_LIFETIME} because a
     * board of month-old asks is not a board. A job somebody is actually doing
     * is a different thing, so accepting resets the clock — otherwise a quest
     * taken on its last step expires under the player on the walk out of town.
     */
    public Quest takenBy(UUID player, long until) {
        return new Quest(id, kind, title, detail, target, place, amount, 0, reward,
                offeredOn, until, QuestState.ACCEPTED, player);
    }

    /** Put back on the board, and whatever was done toward it is lost. */
    public Quest putBack(long until) {
        return new Quest(id, kind, title, detail, target, place, amount, 0, reward,
                offeredOn, until, QuestState.OFFERED, null);
    }

    /**
     * Further along — and finished the moment that is the whole of it.
     *
     * <p>DONE is decided here rather than by whoever is counting, because there
     * are three counters (deliveries, kills, arrivals) and three places to
     * forget the same {@code if}.
     */
    public Quest withProgress(int done) {
        int capped = Math.min(Math.max(0, done), amount);
        QuestState next = capped >= amount && state == QuestState.ACCEPTED
                ? QuestState.DONE : state;
        return new Quest(id, kind, title, detail, target, place, amount, capped, reward,
                offeredOn, expiresOn, next, acceptedBy);
    }

    /** Nobody got to it. */
    public Quest lapsed() {
        return new Quest(id, kind, title, detail, target, place, amount, progress, reward,
                offeredOn, expiresOn, QuestState.EXPIRED, acceptedBy);
    }

    /** Whether this quest's time is up as of this step. DONE work never lapses. */
    public boolean isStale(long step) {
        return state != QuestState.DONE && step >= expiresOn;
    }

    /** Steps left before it lapses, floored at zero. */
    public long stepsLeft(long step) {
        return Math.max(0, expiresOn - step);
    }
}
