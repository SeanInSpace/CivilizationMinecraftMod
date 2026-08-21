package com.kingdoms.sim.work;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A hole several people are digging at once.
 *
 * <p>The problem this solves is that a crowd handed one ordered list of blocks
 * all converge on the same block, shove each other off it, and regularly cut the
 * ground out from under themselves. This decomposes the job instead:
 *
 * <ol>
 *   <li><strong>Sliced top down.</strong> A block may only be taken when nothing
 *       above it in the same column is still wanted. The excavation therefore
 *       peels the terrain surface downward one layer at a time, and no digger can
 *       ever be standing on something another digger is about to remove — which
 *       is what makes this work without anybody needing to fly.</li>
 *   <li><strong>Partitioned into micro-chunks.</strong> Each layer is cut into
 *       {@value #MICRO}x{@value #MICRO} cells. A cell is the unit of work, and
 *       exactly one digger may hold one at a time, so two diggers are never
 *       inside the same three-block square. Collision avoidance falls out of the
 *       locking rather than being bolted on afterwards.</li>
 *   <li><strong>Claimed from a shared pool.</strong> There is no static
 *       assignment: a digger takes the next cell that is ready and free, so the
 *       work balances itself across broken ground and across any number of
 *       diggers, including one who wanders off mid-job.</li>
 * </ol>
 *
 * <p>Deliberately free of Minecraft types. Everything here is arithmetic over
 * integer positions, which is what lets the layering and claim rules be tested
 * without a world. The platform layer wraps this with the parts that genuinely
 * need a level: break times, pathing and particles.
 *
 * <p><strong>Threading:</strong> single-threaded by contract. The server thread
 * is the only caller, so {@link #claim} is a plain check-and-set and is atomic
 * for the same reason a server-thread block update is.
 */
public final class DigYard {

    /** Edge of one claimable cell. One layer thick: cells never span heights. */
    public static final int MICRO = 3;

    /**
     * How many layers below the highest remaining one stay open for work.
     *
     * <p>Purely about keeping the crowd together and the excavation reading as a
     * descending surface. It is a preference and not a cage; see {@link #openCells}.
     */
    public static final int LAYER_WINDOW = 4;

    /** A claim whose holder has not touched it in this long is considered dropped. */
    public static final long CLAIM_TIMEOUT_TICKS = 100L;

    /**
     * How many times a block may be set aside before the job gives up on it.
     *
     * <p>Most blocks that look unreachable are only unreachable for the moment —
     * somebody is standing on the one square you could have worked from, or the
     * ledge you need has not been cut yet. So a block goes back in the pile and is
     * tried again. Only a block that fails this many separate times is genuinely
     * beyond anybody, and only then is it abandoned.
     */
    public static final int MAX_DEFERRALS = 3;

    /** One claimable unit: a {@value #MICRO}x{@value #MICRO} square on a single layer. */
    public record Cell(int cx, int y, int cz) {

        public int minX() {
            return cx * MICRO;
        }

        public int minZ() {
            return cz * MICRO;
        }

        /** The middle of the cell, for distance comparisons. */
        public SimPos centre() {
            return new SimPos(minX() + MICRO / 2, y, minZ() + MICRO / 2);
        }
    }

    private record Claim(UUID digger, long touched) {
    }

    /** Remaining heights per (x, z) column. The last entry is what is exposed. */
    private final Map<Long, NavigableSet<Integer>> columns = new HashMap<>();

    /** Remaining blocks per cell, so claiming does not rescan the whole job. */
    private final Map<Cell, Set<SimPos>> cells = new HashMap<>();

    private final Map<Cell, Claim> claims = new HashMap<>();
    private final Map<UUID, Cell> owned = new HashMap<>();

    /**
     * Blocks nobody can get at, and when to look at them again.
     *
     * <p>Set aside rather than destroyed. A block with nowhere legal to stand — an
     * overhang, a ledge out over a drop — is not the excavation's to delete just
     * because it is inconvenient; it is simply not diggable from anywhere a person
     * can stand. Setting it aside also lifts it off the column beneath, which is
     * what lets the dig carry on at the highest layer somebody <em>can</em> reach
     * instead of stopping dead under an unreachable top.
     */
    private final Map<SimPos, Long> deferred = new HashMap<>();

    /** How many times each block has been set aside, so a hopeless one can be dropped. */
    private final Map<SimPos, Integer> deferrals = new HashMap<>();

    /** Blocks given up on entirely: wanted, never reachable, never dug. */
    private int abandoned;

    private final int total;
    private final int floorY;
    private int cleared;

    private int cachedTop;
    private boolean topDirty = true;

    public DigYard(Collection<SimPos> targets) {
        int lowest = Integer.MAX_VALUE;
        for (SimPos target : targets) {
            if (add(target)) {
                lowest = Math.min(lowest, target.y());
            }
        }
        this.total = size();
        this.floorY = lowest == Integer.MAX_VALUE ? 0 : lowest;
    }

    private boolean add(SimPos pos) {
        boolean fresh = columns
                .computeIfAbsent(columnKey(pos.x(), pos.z()), key -> new TreeSet<>())
                .add(pos.y());
        if (fresh) {
            cells.computeIfAbsent(cellOf(pos), key -> new HashSet<>()).add(pos);
            topDirty = true;
        }
        return fresh;
    }

    // --- the job ---

    public int total() {
        return total;
    }

    public int cleared() {
        return cleared;
    }

    /** Blocks still wanted and still worth trying. */
    public int remaining() {
        return total - cleared - abandoned;
    }

    /** Blocks the job gave up on because nobody could ever stand to work them. */
    public int abandonedCount() {
        return abandoned;
    }

    /**
     * Whether there is any work left that anybody could do.
     *
     * <p>Blocks set aside still count as work: they are retried, and only once
     * one has failed {@value #MAX_DEFERRALS} separate times is it abandoned and
     * the job allowed to finish without it. Treating a single deferral as done
     * ended digs with a third of the ground still standing.
     */
    public boolean isComplete() {
        return cells.isEmpty() && deferred.isEmpty();
    }

    /** The lowest layer this job reaches: the floor being cut to. */
    public int floorY() {
        return floorY;
    }

    public boolean contains(SimPos pos) {
        NavigableSet<Integer> column = columns.get(columnKey(pos.x(), pos.z()));
        return column != null && column.contains(pos.y());
    }

    /** Every block still wanted, for drawing or for a bulk finish. */
    public List<SimPos> remainingBlocks() {
        List<SimPos> all = new ArrayList<>(remaining());
        cells.values().forEach(all::addAll);
        return all;
    }

    /**
     * Whether this block can be taken right now.
     *
     * <p>True only when it is the highest remaining block in its column. That one
     * rule is the entire top-down guarantee: whatever a digger is standing on is
     * either outside the job or still buried under something, and either way it is
     * not about to disappear.
     */
    public boolean isExposed(SimPos pos) {
        NavigableSet<Integer> column = columns.get(columnKey(pos.x(), pos.z()));
        return column != null && !column.isEmpty() && column.last() == pos.y();
    }

    /** The highest layer with anything left in it. */
    public int activeTop() {
        if (topDirty) {
            int top = Integer.MIN_VALUE;
            for (NavigableSet<Integer> column : columns.values()) {
                if (!column.isEmpty()) {
                    top = Math.max(top, column.last());
                }
            }
            cachedTop = top;
            topDirty = false;
        }
        return cachedTop;
    }

    public static Cell cellOf(SimPos pos) {
        return new Cell(Math.floorDiv(pos.x(), MICRO), pos.y(), Math.floorDiv(pos.z(), MICRO));
    }

    /** What is left in a cell. Empty for a cell that is finished or was never in the job. */
    public Set<SimPos> blocksIn(Cell cell) {
        Set<SimPos> blocks = cells.get(cell);
        return blocks == null ? Set.of() : Collections.unmodifiableSet(blocks);
    }

    /**
     * A cell is ready when every block left in it is exposed.
     *
     * <p>Strict on purpose. On broken ground the columns of one cell top out at
     * different heights, and the cell has to wait for the highest of them —
     * otherwise a digger could be sent to a block with a neighbour still towering
     * over it, which is how you end up cornered at the bottom of a pit.
     */
    public boolean isReady(Cell cell) {
        Set<SimPos> blocks = cells.get(cell);
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        for (SimPos block : blocks) {
            if (!isExposed(block)) {
                return false;
            }
        }
        return true;
    }

    // --- claims ---

    public Cell claimOf(UUID digger) {
        return owned.get(digger);
    }

    /** Whether this cell is free to take: unclaimed, dropped, or already ours. */
    public boolean isFree(Cell cell, UUID digger, long tick) {
        Claim claim = claims.get(cell);
        if (claim == null) {
            return true;
        }
        return claim.digger().equals(digger) || tick - claim.touched() >= CLAIM_TIMEOUT_TICKS;
    }

    /**
     * Cells worth offering to a digger, best first.
     *
     * <p>Ordered by layer from the top down, then by how close the cell is to
     * where the digger is standing, so a crowd spreads along the working face
     * instead of racing each other to one corner of it.
     *
     * <p>The height window sorts rather than filters. Within it the nearest cell
     * wins, so the crew spreads sideways along the working face; below it cells
     * are still offered, just last. Filtering outright looked right on a gentle
     * slope and was hopeless on a cliff, where the exposed surface spans far more
     * layers than the window is wide and a hard clamp left one cell open for the
     * whole crew. Because every out-of-window cell sits below every in-window one,
     * sorting this way is still strictly top-down.
     */
    public List<Cell> openCells(UUID digger, SimPos from, long tick) {
        List<Cell> open = new ArrayList<>();
        for (Cell cell : cells.keySet()) {
            if (isReady(cell) && isFree(cell, digger, tick)) {
                open.add(cell);
            }
        }
        int window = activeTop() - LAYER_WINDOW + 1;
        open.sort(Comparator
                .comparingInt((Cell cell) -> cell.y() >= window ? 0 : 1)
                .thenComparing(Comparator.comparingInt(Cell::y).reversed())
                .thenComparingLong(cell -> cell.centre().horizontalDistanceSq(from))
                .thenComparingInt(Cell::cx)
                .thenComparingInt(Cell::cz));
        return open;
    }

    /**
     * Takes a cell for this digger, if it is still going.
     *
     * @return true if the cell is now theirs
     */
    public boolean claim(Cell cell, UUID digger, long tick) {
        if (!cells.containsKey(cell) || !isFree(cell, digger, tick)) {
            return false;
        }
        // Taking over a dropped claim has to evict its holder, not merely overwrite
        // the entry. Leaving them listed as owning it means they never ask for work
        // again — they believe they already have some — and one abandoned cell
        // quietly retires a digger for the rest of the job.
        Claim previous = claims.get(cell);
        if (previous != null && !previous.digger().equals(digger)) {
            owned.remove(previous.digger());
        }
        release(digger);
        claims.put(cell, new Claim(digger, tick));
        owned.put(digger, cell);
        return true;
    }

    /** Keeps a claim alive. A digger who stops calling this loses the cell. */
    public void touch(UUID digger, long tick) {
        Cell cell = owned.get(digger);
        if (cell != null) {
            claims.put(cell, new Claim(digger, tick));
        }
    }

    /** Gives up whatever this digger holds, on finishing it, wandering off, or dying. */
    public void release(UUID digger) {
        Cell held = owned.remove(digger);
        if (held == null) {
            return;
        }
        Claim claim = claims.get(held);
        if (claim != null && claim.digger().equals(digger)) {
            claims.remove(held);
        }
    }

    /**
     * Takes a block out of the job.
     *
     * @return true if it was actually wanted, so callers can tell a dig from a
     *         block that had already gone
     */
    public boolean remove(SimPos pos) {
        if (deferred.remove(pos) != null) {
            deferrals.remove(pos);
            cleared++;
            return true;   // set aside earlier, dug anyway
        }
        if (!detach(pos)) {
            return false;
        }
        cleared++;
        return true;
    }

    /**
     * Sets a block aside until {@code untilTick}, without counting it as dug.
     *
     * @return true if it was actually in the job
     */
    public boolean defer(SimPos pos, long untilTick) {
        if (!detach(pos)) {
            return false;
        }
        deferred.put(pos, untilTick);
        deferrals.merge(pos, 1, Integer::sum);
        return true;
    }

    /** Puts deferred blocks back in the job once their wait is up. */
    public void reconsider(long tick) {
        if (deferred.isEmpty()) {
            return;
        }
        List<SimPos> due = new ArrayList<>();
        for (Map.Entry<SimPos, Long> waiting : deferred.entrySet()) {
            if (tick >= waiting.getValue()) {
                due.add(waiting.getKey());
            }
        }
        for (SimPos pos : due) {
            deferred.remove(pos);
            if (deferrals.getOrDefault(pos, 0) >= MAX_DEFERRALS) {
                deferrals.remove(pos);
                abandoned++;   // nobody was ever going to reach this one
                continue;
            }
            add(pos);
        }
    }

    public int deferredCount() {
        return deferred.size();
    }

    /** Takes a block out of the working set. Says nothing about why. */
    private boolean detach(SimPos pos) {
        long key = columnKey(pos.x(), pos.z());
        NavigableSet<Integer> column = columns.get(key);
        if (column == null || !column.remove(pos.y())) {
            return false;
        }
        if (column.isEmpty()) {
            columns.remove(key);
        }
        Cell cell = cellOf(pos);
        Set<SimPos> blocks = cells.get(cell);
        if (blocks != null) {
            blocks.remove(pos);
            if (blocks.isEmpty()) {
                cells.remove(cell);
                Claim claim = claims.remove(cell);
                if (claim != null) {
                    owned.remove(claim.digger());
                }
            }
        }
        topDirty = true;
        return true;
    }

    private int size() {
        int count = 0;
        for (NavigableSet<Integer> column : columns.values()) {
            count += column.size();
        }
        return count;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
