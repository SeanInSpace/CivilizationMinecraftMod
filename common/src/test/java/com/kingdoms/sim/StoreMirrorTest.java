package com.kingdoms.sim;

import com.kingdoms.sim.settlement.StoreMirror;
import com.kingdoms.sim.settlement.TownStores;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one subtraction that keeps a town's chest and its ledger honest.
 *
 * <p>Written as sequences rather than single calls, because every way this can
 * go wrong is a sequence: a reload between two passes, a withdrawal the town
 * never notices, stock counted twice because a snapshot was lost.
 */
class StoreMirrorTest {

    /** A stand-in for the chest: how much of the resource is sitting in it. */
    private static final String WOOD = TownStores.WOOD;

    private static TownStores storesWith(int wood) {
        TownStores stores = new TownStores();
        stores.set(WOOD, wood);
        return stores;
    }

    @Test
    void aChestThatNobodyTouchedChangesNothing() {
        TownStores stores = storesWith(480);
        int snapshot = 480;

        int next = StoreMirror.reconcile(stores, WOOD, 480, snapshot);

        assertEquals(480, stores.get(WOOD),
                "an untouched chest is not news; the ledger must not move");
        assertEquals(480, next, "and the snapshot still describes what is there");
    }

    @Test
    void aStackTakenOutIsAStackTheTownNoLongerHas() {
        TownStores stores = storesWith(480);

        int next = StoreMirror.reconcile(stores, WOOD, 416, 480);

        assertEquals(416, stores.get(WOOD),
                "sixty-four logs left the chest, so the town owns sixty-four fewer");
        assertEquals(416, next);
    }

    @Test
    void aStackPutInIsADonation() {
        TownStores stores = storesWith(480);

        int next = StoreMirror.reconcile(stores, WOOD, 544, 480);

        assertEquals(544, stores.get(WOOD), "the town keeps what it was given");
        assertEquals(544, next);
    }

    @Test
    void stockEarnedWhileNobodyWasLookingSurvivesTheNextSync() {
        // The case the whole design exists for. The town felled timber with the
        // chunk unloaded, so the ledger grew while the chest stood still.
        // Reading the chest back must not undo that.
        TownStores stores = storesWith(480);
        int snapshot = 480;
        stores.add(WOOD, 120);   // the clock, while away

        StoreMirror.reconcile(stores, WOOD, 480, snapshot);

        assertEquals(600, stores.get(WOOD),
                "an unchanged chest says nothing about what the town earned elsewhere");
    }

    @Test
    void aWithdrawalRightBeforeAnUnloadIsStillSeenOnReturn() {
        // Chest contents and snapshot both persist, so the difference outlives
        // the unload. This is why the snapshot is saved rather than kept in
        // memory: recomputing it on load would read the whole chest as a gift.
        TownStores stores = storesWith(480);
        int snapshotOnDisk = 480;
        int chestOnDisk = 416;   // somebody took a stack, then the chunk unloaded

        StoreMirror.reconcile(stores, WOOD, chestOnDisk, snapshotOnDisk);

        assertEquals(416, stores.get(WOOD),
                "the stack was taken before the save, and is still gone after it");
    }

    @Test
    void aLostSnapshotWouldHaveCreditedTheTownTwice() {
        // Not a bug being asserted — the reason the snapshot is persisted. If
        // it came back as zero, every log already in the chest reads as a
        // donation and the town's timber doubles on every reload.
        TownStores stores = storesWith(480);

        StoreMirror.reconcile(stores, WOOD, 480, 0);

        assertEquals(960, stores.get(WOOD),
                "this is what losing the snapshot costs, and why it is saved");
    }

    @Test
    void manyPassesWithNoInterferenceLeaveTheLedgerWhereItStarted() {
        TownStores stores = storesWith(300);
        int snapshot = 300;
        for (int pass = 0; pass < 50; pass++) {
            snapshot = StoreMirror.reconcile(stores, WOOD, 300, snapshot);
        }
        assertEquals(300, stores.get(WOOD),
                "fifty quiet seconds must not drift the ledger by a single log");
    }

    @Test
    void aStoreTooSmallToShowEverythingStillMeasuresWithdrawalsHonestly() {
        // Eight free slots hold 512 logs; the town owns 900. The mirror records
        // what fitted, so the 388 it could not show are not read as missing.
        int fitted = StoreMirror.showable(WOOD, 900, 8);
        assertEquals(512, fitted, "eight slots of sixty-four is what can be shown");

        TownStores stores = storesWith(900);
        StoreMirror.reconcile(stores, WOOD, fitted, fitted);
        assertEquals(900, stores.get(WOOD),
                "the overflow is out of sight, not out of the ledger");

        // Now a player takes one stack from the visible part.
        StoreMirror.reconcile(stores, WOOD, fitted - 64, fitted);
        assertEquals(836, stores.get(WOOD),
                "a withdrawal from a full-to-the-brim store is still just a withdrawal");
    }

    @Test
    void aTownCannotBeDrivenBelowNothing() {
        TownStores stores = storesWith(10);

        StoreMirror.reconcile(stores, WOOD, 0, 500);

        assertEquals(0, stores.get(WOOD),
                "a snapshot larger than the town's holdings empties it, never past empty");
    }

    @Test
    void anItemTheTownHasNoUseForDoesNotBringDownTheSimulation() {
        // The crash this exists to prevent: resourceOf answers null for junk,
        // and the mirrored list is a List.of, whose contains throws on null
        // rather than answering false. One diamond dropped into the store took
        // out the whole per-second pass for the dimension, once a second, for
        // as long as it sat there.
        List<String> mirrored = List.of(WOOD, TownStores.STONE);

        assertFalse(StoreMirror.mirrors("minecraft:diamond", mirrored),
                "a diamond is not timber, and saying so must not throw");
        assertFalse(StoreMirror.mirrors("minecraft:jukebox", mirrored));
        assertFalse(StoreMirror.mirrors(null, mirrored),
                "an unreadable id is refused, not fatal");
        assertFalse(StoreMirror.mirrors("", mirrored));
    }

    @Test
    void whatTheContainerSpeaksForItStillRecognises() {
        List<String> mirrored = List.of(WOOD, TownStores.STONE);

        assertTrue(StoreMirror.mirrors("minecraft:oak_log", mirrored),
                "timber is exactly what this store is for");
        assertTrue(StoreMirror.mirrors("minecraft:cobblestone", mirrored));
        assertFalse(StoreMirror.mirrors("minecraft:bread", mirrored),
                "food is a resource, but not one this container speaks for");
    }

    @Test
    void gearIsCountedBySlotBecauseItDoesNotStack() {
        assertEquals(32, StoreMirror.showable(TownStores.WEAPONS, 32, 32),
                "thirty-two swords need thirty-two slots");
        assertEquals(8, StoreMirror.showable(TownStores.WEAPONS, 32, 8),
                "eight slots show eight swords, whatever the armoury holds");
    }
}
