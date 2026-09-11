package com.civilization.sim.quest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The noticeboard: what the town is asking for, and what it remembers being
 * done for it.
 *
 * <p>Bounded at both ends, and both bounds are about reading rather than about
 * memory. Five asks is a board a player can take in from a standing position;
 * ten finished ones is a town with a short and recent gratitude rather than a
 * ledger. A town that wanted eleven things at once would be a town nobody could
 * help.
 *
 * <p>Holds no opinion about what belongs on it. {@link QuestPlanner} decides
 * what a town wants, what it will pay and when an ask goes stale; this is where
 * the answers are kept.
 */
public final class QuestBoard {

    /** How many things a town will ask for at one time. */
    public static final int BOARD_SIZE = 5;

    /** How far back the town's thanks reaches. */
    public static final int REMEMBERED = 10;

    private final List<Quest> offered = new ArrayList<>();
    private final Deque<Quest> completed = new ArrayDeque<>();

    /** Everything on the board right now, in the order it was posted. */
    public List<Quest> offered() {
        return Collections.unmodifiableList(offered);
    }

    /** The last {@link #REMEMBERED} jobs somebody finished, newest first. */
    public List<Quest> completed() {
        return List.copyOf(completed);
    }

    public boolean hasRoom() {
        return offered.size() < BOARD_SIZE;
    }

    public int size() {
        return offered.size();
    }

    public boolean isEmpty() {
        return offered.isEmpty();
    }

    /**
     * Pins one up, if there is room and nothing of that id is already there.
     *
     * @return true if it went on the board
     */
    public boolean post(Quest quest) {
        Objects.requireNonNull(quest, "quest");
        if (!hasRoom() || find(quest.id()) != null) {
            return false;
        }
        offered.add(quest);
        return true;
    }

    public Quest find(String id) {
        if (id == null) {
            return null;
        }
        for (Quest quest : offered) {
            if (quest.id().equals(id)) {
                return quest;
            }
        }
        return null;
    }

    /**
     * Swaps one quest for its successor, in place.
     *
     * <p>In place, so the board does not reshuffle itself under a player who is
     * reading it: accepting the third row must leave the third row where it is.
     *
     * @return true if a quest of that id was there to replace
     */
    public boolean replace(Quest quest) {
        for (int i = 0; i < offered.size(); i++) {
            if (offered.get(i).id().equals(quest.id())) {
                offered.set(i, quest);
                return true;
            }
        }
        return false;
    }

    /** Takes one down. Returns what was there, or null. */
    public Quest remove(String id) {
        for (int i = 0; i < offered.size(); i++) {
            if (offered.get(i).id().equals(id)) {
                return offered.remove(i);
            }
        }
        return null;
    }

    /**
     * Takes a finished quest down and remembers it.
     *
     * <p>Newest first, and the oldest falls off the back — the same shape as the
     * settlement's own event log, for the same reason.
     */
    public void recordDone(Quest quest) {
        remove(quest.id());
        completed.addFirst(quest);
        while (completed.size() > REMEMBERED) {
            completed.removeLast();
        }
    }

    /** For the codecs, which rebuild a board rather than acting on one. */
    public void restore(List<Quest> open, List<Quest> done) {
        offered.clear();
        completed.clear();
        for (Quest quest : open) {
            if (offered.size() < BOARD_SIZE) {
                offered.add(quest);
            }
        }
        for (Quest quest : done) {
            if (completed.size() < REMEMBERED) {
                completed.addLast(quest);
            }
        }
    }
}
