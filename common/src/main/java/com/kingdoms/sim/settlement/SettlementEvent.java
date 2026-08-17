package com.kingdoms.sim.settlement;

import java.util.Objects;

/**
 * One line of settlement history — a raid repelled, people lost, raiders sighted.
 *
 * <p>This is how "you can tell it happened while you were away" works: events are
 * recorded whether or not anyone was watching, persist with the settlement, and
 * are readable later. The evidence trail the roadmap asked for.
 */
public record SettlementEvent(long step, String message) {

    public SettlementEvent {
        Objects.requireNonNull(message, "message");
    }

    @Override
    public String toString() {
        return "[step " + step + "] " + message;
    }
}
