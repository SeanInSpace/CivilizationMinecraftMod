package com.kingdoms.neoforge.world;

/**
 * One store's container, described in the words the ledger uses.
 *
 * <p>Deliberately not expressed in {@code ItemStack}s, and that is the whole
 * design. A JUnit game can populate the registries but never binds item
 * components, so {@code new ItemStack(...)} cannot be constructed in a test at
 * all — a seam that spoke in stacks would have been untestable for exactly the
 * same reason the code above it was. In resources and counts, a fake is a pair
 * of arrays.
 *
 * <p>It also says something truer. The reconciler does not care that timber is
 * an oak log; it cares that this slot holds sixty-four of the thing the town
 * calls wood. Translating at the edge is {@link ChestShelves}' job.
 */
public interface Shelves {

    /** How many slots there are to lay goods in. */
    int slots();

    /** Nothing at all in this slot. */
    boolean isEmpty(int slot);

    /**
     * The resource in this slot, or null.
     *
     * <p>Null covers both an empty slot and one holding something the stores do
     * not speak for — a diamond somebody dropped in. The two are told apart by
     * {@link #isEmpty}, and the difference matters: the reconciler clears what
     * it recognizes and steps politely around what it does not.
     */
    String resourceAt(int slot);

    /** How much is in this slot; zero when empty. */
    int amountAt(int slot);

    /** Lay this much of a resource in a slot, replacing whatever was there. */
    void lay(int slot, String resource, int amount);

    /** Take everything out of a slot. */
    void empty(int slot);

    /**
     * What the reconciler last wrote here for a resource.
     *
     * <p>The only way to tell a player's hand from the town's own bookkeeping,
     * and persisted for that reason: recomputed on load, it would read the whole
     * chest as a donation and credit the town twice.
     */
    int lastSynced(String resource);

    void setLastSynced(String resource, int amount);

    /**
     * How much of a resource one slot can hold here, or zero if it cannot be
     * shown at all — the town has no item to pay it out in.
     */
    int perSlot(String resource);

    /** Finished writing; save it. Called once per pass, not per slot. */
    void done();
}
