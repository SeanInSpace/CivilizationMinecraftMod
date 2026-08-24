package com.kingdoms.neoforge.world;

import com.kingdoms.sim.settlement.Building;

/**
 * Where the mirror finds a town's shelves, and how it says the books moved.
 *
 * <p>Two methods, because two things happen outside the arithmetic: locating a
 * building's container in a world that may not be loaded, and marking the saved
 * data dirty when a ledger changes. Both were places bugs lived — a lookup that
 * force-loaded chunks, and a ledger that moved in memory while only the chest
 * saved — and neither belongs in the reconciliation itself.
 */
public interface StoreWorld {

    /**
     * This building's shelves, or null if it has none standing and loaded.
     *
     * <p>Null is ordinary, not exceptional: a store whose chunk is away is the
     * normal state of most towns most of the time, which is the whole reason
     * the ledger is the authority and the chest is only its likeness.
     */
    Shelves shelvesOf(Building building);

    /**
     * A building's ledger moved, so the saved data must be written.
     *
     * <p>Chests save with their chunk whatever happens; the ledgers live in the
     * saved data, which nothing else here marks. Without this a crash between
     * now and the next simulation step restores a building that never saw a
     * withdrawal its chest still remembers.
     */
    void ledgerChanged();
}
