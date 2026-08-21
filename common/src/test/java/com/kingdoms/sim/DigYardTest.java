package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.work.DigYard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that stop a crowd of diggers burying themselves or each other.
 *
 * <p>All of this is arithmetic over integer positions, which is the whole reason
 * the decomposition lives in {@code :common}: the guarantees can be proved here
 * in milliseconds instead of being eyeballed in a running game.
 */
class DigYardTest {

    private static final UUID ANNE = UUID.nameUUIDFromBytes("anne".getBytes());
    private static final UUID BOEL = UUID.nameUUIDFromBytes("boel".getBytes());
    private static final UUID CERI = UUID.nameUUIDFromBytes("ceri".getBytes());

    /** A solid box, the crude case: flat top, every column the same height. */
    private static DigYard box(int width, int height) {
        List<SimPos> targets = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                for (int y = 64; y < 64 + height; y++) {
                    targets.add(new SimPos(x, y, z));
                }
            }
        }
        return new DigYard(targets);
    }

    @Test
    void onlyTheTopOfAColumnIsEverExposed() {
        DigYard yard = box(3, 4);

        assertTrue(yard.isExposed(new SimPos(1, 67, 1)), "the top block is diggable");
        assertFalse(yard.isExposed(new SimPos(1, 66, 1)), "the one under it is not");
        assertFalse(yard.isExposed(new SimPos(1, 64, 1)), "and the floor certainly is not");

        yard.remove(new SimPos(1, 67, 1));
        assertTrue(yard.isExposed(new SimPos(1, 66, 1)), "taking the cap exposes the next");
    }

    @Test
    void acellIsNotReadyWhileAnyOfItsColumnsIsStillBuried() {
        // One tall column in the middle of an otherwise flat 3x3: the whole cell
        // waits for it, because a digger sent to the low blocks would be working
        // in the shadow of a wall that is still standing over them.
        List<SimPos> targets = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                targets.add(new SimPos(x, 64, z));
            }
        }
        targets.add(new SimPos(1, 65, 1));
        DigYard yard = new DigYard(targets);

        DigYard.Cell low = DigYard.cellOf(new SimPos(0, 64, 0));
        DigYard.Cell high = DigYard.cellOf(new SimPos(1, 65, 1));

        assertFalse(yard.isReady(low), "the flat layer is held up by the spur above it");
        assertTrue(yard.isReady(high), "the spur itself is exposed and can go");

        yard.remove(new SimPos(1, 65, 1));
        assertTrue(yard.isReady(low), "once the spur is gone the layer opens");
    }

    @Test
    void twoDiggersAreNeverGivenTheSameCell() {
        DigYard yard = box(9, 1);

        DigYard.Cell wanted = yard.openCells(ANNE, SimPos.ORIGIN, 0L).getFirst();
        assertTrue(yard.claim(wanted, ANNE, 0L));
        assertFalse(yard.claim(wanted, BOEL, 0L), "a held cell is not on offer");

        DigYard.Cell other = yard.openCells(BOEL, SimPos.ORIGIN, 0L).getFirst();
        assertNotEquals(wanted, other, "the offer skips what somebody already holds");
        assertTrue(yard.claim(other, BOEL, 0L));
    }

    @Test
    void adroppedClaimIsPickedUpBySomebodyElse() {
        DigYard yard = box(3, 1);
        DigYard.Cell only = yard.openCells(ANNE, SimPos.ORIGIN, 0L).getFirst();
        assertTrue(yard.claim(only, ANNE, 0L));

        long stillWarm = DigYard.CLAIM_TIMEOUT_TICKS - 1;
        assertFalse(yard.claim(only, BOEL, stillWarm), "Anne has not been gone long enough");

        long coldNow = DigYard.CLAIM_TIMEOUT_TICKS;
        assertTrue(yard.claim(only, BOEL, coldNow), "a claim nobody is working is not a lock");
        assertEquals(only, yard.claimOf(BOEL));
        assertNull(yard.claimOf(ANNE), "and the original holder loses it");
    }

    @Test
    void aliveClaimIsKeptAliveByWorkingIt() {
        DigYard yard = box(3, 1);
        DigYard.Cell only = yard.openCells(ANNE, SimPos.ORIGIN, 0L).getFirst();
        yard.claim(only, ANNE, 0L);

        for (long tick = 0; tick < DigYard.CLAIM_TIMEOUT_TICKS * 3; tick += 10) {
            yard.touch(ANNE, tick);
        }
        long later = DigYard.CLAIM_TIMEOUT_TICKS * 3;
        assertFalse(yard.claim(only, BOEL, later - 5), "she is plainly still digging it");
    }

    @Test
    void theTopLayerIsOfferedBeforeAnythingBeneathIt() {
        // A staircase: each column one block taller than the last, so the exposed
        // surface spans several layers at once.
        List<SimPos> targets = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 64; y <= 64 + x; y++) {
                    targets.add(new SimPos(x, y, z));
                }
            }
        }
        DigYard yard = new DigYard(targets);

        List<DigYard.Cell> offered = yard.openCells(ANNE, SimPos.ORIGIN, 0L);
        assertEquals(yard.activeTop(), offered.getFirst().y(),
                "work starts at the highest ground, never underneath it");
        for (int i = 1; i < offered.size(); i++) {
            assertTrue(offered.get(i).y() <= offered.get(i - 1).y(), "and descends from there");
        }
    }

    @Test
    void asteepFaceStillGivesEverybodySomethingToDo() {
        // A face taller than the height window. Clamping hard would leave one
        // cell open and the rest of the crew standing around watching.
        List<SimPos> targets = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 64; y <= 64 + x * 3; y++) {
                    targets.add(new SimPos(x, y, z));
                }
            }
        }
        DigYard yard = new DigYard(targets);

        List<DigYard.Cell> offered = yard.openCells(ANNE, SimPos.ORIGIN, 0L);
        assertTrue(offered.size() >= 3,
                "a cliff must not serialise the whole crew onto one cell; got " + offered.size());
    }

    @Test
    void afinishedCellReleasesItsHolder() {
        DigYard yard = box(3, 1);
        DigYard.Cell only = yard.openCells(ANNE, SimPos.ORIGIN, 0L).getFirst();
        yard.claim(only, ANNE, 0L);

        for (SimPos block : List.copyOf(yard.blocksIn(only))) {
            yard.remove(block);
        }
        assertNull(yard.claimOf(ANNE), "she is free to take the next cell");
    }

    @Test
    void anUnreachableTopLetsTheLayerBelowItBeWorked() {
        // A pillar with nothing to stand on beside it. Until it is set aside, the
        // block under it is buried and the whole column is stuck behind something
        // no one can get to.
        DigYard yard = box(3, 3);
        SimPos overhang = new SimPos(1, 66, 1);

        assertFalse(yard.isExposed(new SimPos(1, 65, 1)), "buried while the top stands");

        assertTrue(yard.defer(overhang, 500L));
        assertTrue(yard.isExposed(new SimPos(1, 65, 1)),
                "setting the top aside is what lets the dig start lower");
        assertEquals(0, yard.cleared(), "deferring is not digging");
        assertEquals(1, yard.deferredCount());
    }

    @Test
    void adeferredBlockComesBackWhenItsWaitIsUp() {
        DigYard yard = box(3, 1);
        SimPos block = new SimPos(1, 64, 1);
        yard.defer(block, 500L);
        assertFalse(yard.contains(block));

        yard.reconsider(499L);
        assertFalse(yard.contains(block), "not yet");

        yard.reconsider(500L);
        assertTrue(yard.contains(block), "back in the job to be tried again");
        assertEquals(0, yard.deferredCount());
    }

    @Test
    void ajobOfNothingButUnreachableBlocksEndsOnlyAfterRetrying() {
        // A block that looks unreachable usually is not: somebody is standing on
        // the one square you could work from. So it goes back in the pile. Only
        // after it has failed on its own several times is it given up on -- and it
        // must be given up on, or the building this hole was dug for never gets
        // built.
        DigYard yard = box(3, 1);
        long tick = 0;
        for (int round = 0; round < DigYard.MAX_DEFERRALS; round++) {
            List<SimPos> left = List.copyOf(yard.remainingBlocks());
            assertFalse(left.isEmpty(), "round " + round + " should still have work");
            for (SimPos block : left) {
                yard.defer(block, tick + 10);
            }
            assertFalse(yard.isComplete(), "not done while blocks are waiting to be retried");
            tick += 10;
            yard.reconsider(tick);
        }

        assertTrue(yard.isComplete(), "a hole nobody can dig anywhere is eventually done");
        assertEquals(0, yard.cleared(), "and none of it counts as dug");
        assertEquals(9, yard.abandonedCount(), "it is counted as beyond reach instead");
    }

    @Test
    void everyBlockComesOutAndTheJobEnds() {
        // The whole point, driven end to end: three diggers, no coordination
        // beyond the claim rules, and a hill that is not flat anywhere.
        List<SimPos> targets = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                int height = 64 + (x * 7 + z * 3) % 5;
                for (int y = 64; y <= height; y++) {
                    targets.add(new SimPos(x, y, z));
                }
            }
        }
        DigYard yard = new DigYard(targets);
        int expected = yard.total();

        Set<SimPos> everCut = new HashSet<>();
        List<UUID> crew = List.of(ANNE, BOEL, CERI);
        long tick = 0;
        int guard = expected * 20;

        while (!yard.isComplete() && guard-- > 0) {
            for (UUID digger : crew) {
                DigYard.Cell held = yard.claimOf(digger);
                if (held == null) {
                    List<DigYard.Cell> open = yard.openCells(digger, SimPos.ORIGIN, tick);
                    if (open.isEmpty()) {
                        continue;
                    }
                    yard.claim(open.getFirst(), digger, tick);
                    held = yard.claimOf(digger);
                }
                if (held == null) {
                    continue;
                }
                for (SimPos block : List.copyOf(yard.blocksIn(held))) {
                    assertTrue(yard.isExposed(block),
                            "a digger was handed " + block + " with ground still over it");
                    assertTrue(everCut.add(block), block + " was dug twice");
                    yard.remove(block);
                    break;   // one block per digger per tick, like the real thing
                }
                yard.touch(digger, tick);
            }
            tick++;
        }

        assertTrue(yard.isComplete(), "the crew left " + yard.remaining() + " blocks standing");
        assertEquals(expected, everCut.size(), "every block accounted for exactly once");
        assertEquals(expected, yard.cleared());
    }
}
