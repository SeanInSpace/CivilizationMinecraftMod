package com.civilization.neoforge.world;

import com.civilization.sim.settlement.Founding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What reading a town's ground before siting it actually costs.
 *
 * <p>Raising a seeded town on chunks nobody has generated is what put the
 * playtest's village on ground it had never looked at, and the cure —
 * generating the claim to real terrain first — is the one genuinely expensive
 * thing {@link WorldgenSettlements} does. An expensive thing whose cost is not
 * counted is how a tick comes to take sixty seconds and the watchdog kills the
 * server, which has happened twice in this project's history and is written up
 * in {@link TerrainOracle}.
 *
 * <p>So the cost is arithmetic, and arithmetic can be tested without a world.
 */
class ClaimGroundTest {

    /**
     * A town's claim is about seventy chunks, and nine of them about six hundred.
     *
     * <p>The number the whole decision rests on. Sixty-four blocks of claim is a
     * hundred and twenty-eight across, which is eight chunks plus whatever the
     * center's offset inside its own chunk drags in — nine or ten squared — and
     * the disc takes about four fifths of that.
     */
    @Test
    void aClaimIsAboutSeventyChunks() {
        int chunks = ClaimGround.chunkCount(106, 180, Founding.INITIAL_CLAIM);
        assertTrue(chunks >= 60 && chunks <= 85,
                "a sixty-four block claim should be about seventy chunks, not " + chunks);
        assertTrue(chunks * 9 <= 800,
                "and the nine spawn towns under eight hundred: " + chunks * 9);
    }

    /**
     * The disc is cheaper than the square that bounds it, which is why it is a disc.
     */
    @Test
    void andTheDiscIsCheaperThanTheSquare() {
        int radius = Founding.INITIAL_CLAIM;
        int across = ((106 + radius) >> 4) - ((106 - radius) >> 4) + 1;
        int square = across * (((180 + radius) >> 4) - ((180 - radius) >> 4) + 1);
        int disc = ClaimGround.chunkCount(106, 180, radius);
        assertTrue(disc < square,
                "the claim is round: " + disc + " chunks against " + square);
    }

    /**
     * Every chunk once, no chunk twice.
     *
     * <p>A ring walk that revisits is a budget spent twice on the same ground and
     * a count that lies about what a town costs.
     */
    @Test
    void everyChunkOnceAndNoChunkTwice() {
        Set<Long> seen = new HashSet<>();
        List<long[]> order = new ArrayList<>();
        ClaimGround.forEachChunk(-77, 903, 64, (x, z) -> {
            long id = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
            assertTrue(seen.add(id), "chunk " + x + "," + z + " was walked twice");
            order.add(new long[] {x, z});
            return true;
        });
        assertEquals(seen.size(), ClaimGround.chunkCount(-77, 903, 64),
                "the count and the walk must agree");
    }

    /**
     * The middle of the claim is read first.
     *
     * <p>A read can be cut short by its budget, and the ground the town's own
     * square stands on is worth more than the ground at the edge of its claim.
     */
    @Test
    void andTheMiddleComesFirst() {
        List<long[]> order = new ArrayList<>();
        ClaimGround.forEachChunk(106, 180, 64, (x, z) -> {
            order.add(new long[] {x, z});
            return true;
        });
        assertEquals(106 >> 4, order.get(0)[0], "the center's own chunk is first");
        assertEquals(180 >> 4, order.get(0)[1], "the center's own chunk is first");
        long lastRing = Math.max(Math.abs(order.get(order.size() - 1)[0] - (106 >> 4)),
                Math.abs(order.get(order.size() - 1)[1] - (180 >> 4)));
        assertTrue(lastRing >= 4, "and the last chunk is out at the rim: " + lastRing);
    }

    /** A sink that says stop is obeyed, which is what a budget is made of. */
    @Test
    void andAWalkCanBeCutShort() {
        int[] seen = {0};
        ClaimGround.forEachChunk(0, 0, 64, (x, z) -> {
            seen[0]++;
            return seen[0] < 5;
        });
        assertEquals(5, seen[0], "the walk stops when it is told to");
    }

    /**
     * Nine towns at eight chunks a tick is under four seconds of world start.
     *
     * <p>The promise the budget is chosen against. A tick is fifty milliseconds,
     * so eighty ticks is four seconds — and nobody is in the world yet to feel
     * any of it, because the anchor runs from the moment the level loads.
     */
    @Test
    void andNineTownsFitInsideAWorldStart() {
        int perTown = ClaimGround.ticksToRead(106, 180, Founding.INITIAL_CLAIM, 8);
        assertTrue(perTown <= 11, "a town's ground in eleven ticks or fewer: " + perTown);
        int nine = perTown * 9;
        assertTrue(nine <= 100,
                "and the nine spawn towns inside a hundred ticks: " + nine);
    }

    /** A budget of nothing never finishes, and says so rather than looping. */
    @Test
    void andABudgetOfNothingIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> ClaimGround.ticksToRead(0, 0, 64, 0));
    }

    /**
     * A claim smaller than a chunk still has a chunk to read.
     *
     * <p>Zero would mean a town raised with nothing read at all, which is the
     * fault rather than a saving.
     */
    @Test
    void andATinyClaimStillReadsSomething() {
        assertFalse(ClaimGround.chunkCount(8, 8, 1) == 0,
                "a claim inside one chunk still has that chunk");
        assertEquals(1, ClaimGround.ticksToRead(8, 8, 1, 8),
                "and it is read in a single tick");
    }
}
