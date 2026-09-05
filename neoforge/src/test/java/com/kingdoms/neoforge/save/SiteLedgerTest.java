package com.kingdoms.neoforge.save;

import com.kingdoms.sim.geom.SimPos;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger through its codec, which is the only part of it a save file sees.
 *
 * <p>A ledger that does not survive a round trip is worse than no ledger: the
 * regions it forgets are the regions that raise a second town on top of the
 * first, and the failure shows up hours later in somebody's world rather than
 * here.
 *
 * <p>JSON rather than NBT deliberately — the codec is the same either way, and
 * a failure prints something readable.
 */
class SiteLedgerTest {

    private static SiteLedger roundTrip(SiteLedger original) {
        DataResult<JsonElement> written = SiteLedger.CODEC.encodeStart(JsonOps.INSTANCE, original);
        JsonElement json = written.getOrThrow(message ->
                new AssertionError("the ledger would not serialize: " + message));
        return SiteLedger.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(message ->
                new AssertionError("the ledger would not read back: " + message));
    }

    @Test
    void anAcceptedSiteComesBackWithItsCenter() {
        SiteLedger ledger = new SiteLedger();
        ledger.accept(3, -7, new SimPos(1898, 71, -3315));

        SiteLedger read = roundTrip(ledger);

        assertEquals(1, read.size());
        SiteLedger.Entry entry = read.entry(3, -7).orElseThrow();
        assertTrue(entry.accepted());
        assertEquals(new SimPos(1898, 71, -3315), entry.center().orElseThrow());
    }

    @Test
    void aRefusedSiteComesBackRefusedRatherThanMissing() {
        // The distinction the whole class exists for. A refusal that reads back
        // as "never decided" costs the same terrain check again on every
        // approach, forever.
        SiteLedger ledger = new SiteLedger();
        ledger.reject(-2, 5);

        SiteLedger read = roundTrip(ledger);

        assertTrue(read.isResolved(-2, 5));
        assertFalse(read.entry(-2, 5).orElseThrow().accepted());
        assertTrue(read.entry(-2, 5).orElseThrow().center().isEmpty());
    }

    @Test
    void aMixedLedgerSurvivesWhole() {
        SiteLedger ledger = new SiteLedger();
        ledger.accept(0, 0, new SimPos(200, 64, 200));
        ledger.reject(0, 1);
        ledger.accept(1, 0, new SimPos(712, 88, 190));
        ledger.reject(-30000, 29999);   // far out, to catch a packing that loses sign

        SiteLedger read = roundTrip(ledger);

        assertEquals(4, read.size());
        assertEquals(ledger.entries(), read.entries());
        assertTrue(read.isResolved(-30000, 29999));
        assertFalse(read.isResolved(-30000, -29999));
    }

    @Test
    void anEmptyLedgerIsStillAValidOne() {
        // The first thing every world writes, and the shape most likely to be
        // missing from the file altogether.
        SiteLedger read = roundTrip(new SiteLedger());
        assertTrue(read.isEmpty());
        assertEquals(List.of(), read.entries());
    }

    @Test
    void neighboringRegionsDoNotShareAKey() {
        // Packing two signed ints into a long is the obvious place for a region
        // to be mistaken for its neighbor, and the symptom would be a town
        // silently never founded.
        SiteLedger ledger = new SiteLedger();
        for (int rx = -2; rx <= 2; rx++) {
            for (int rz = -2; rz <= 2; rz++) {
                ledger.accept(rx, rz, new SimPos(rx, 0, rz));
            }
        }
        assertEquals(25, ledger.size());
        SiteLedger read = roundTrip(ledger);
        for (int rx = -2; rx <= 2; rx++) {
            for (int rz = -2; rz <= 2; rz++) {
                assertEquals(new SimPos(rx, 0, rz),
                        read.entry(rx, rz).orElseThrow().center().orElseThrow(),
                        "region (" + rx + ", " + rz + ") came back as somebody else");
            }
        }
    }

    @Test
    void aRegionIsDecidedOnceAndOnlyOnce() {
        SiteLedger ledger = new SiteLedger();
        assertTrue(ledger.accept(4, 4, new SimPos(100, 70, 100)));
        assertFalse(ledger.reject(4, 4), "a later refusal overwrote a standing town");
        assertFalse(ledger.accept(4, 4, new SimPos(900, 70, 900)),
                "a second acceptance would raise a second town on the same region");
        assertEquals(new SimPos(100, 70, 100),
                ledger.entry(4, 4).orElseThrow().center().orElseThrow());
        assertEquals(1, ledger.size());
    }
}
