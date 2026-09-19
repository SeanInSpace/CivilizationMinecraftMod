package com.civilization.neoforge.world;

import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Where a town's bell actually hangs.
 *
 * <p>Both of the mod's reasons to ring — the dawn peal in {@code Ambience} and
 * the alarm in {@code PersonEntityManager} — used to run the same search, and
 * the same search was wrong in the same way: it walked the column
 * <em>directly above the building's origin</em> and nothing else. A belfry is
 * not over the middle of a hall. It is over a corner of it, on the gable, up
 * the stair — in the vale hall the playtest measured, six blocks east and five
 * up from the origin, identically in two towns on two seeds, because that is
 * where the blueprint puts it. So a one-column search found nothing, returned
 * null, and both towns were silent every morning of their lives without ever
 * saying why.
 *
 * <p><strong>The footprint, not the column.</strong> A building already knows
 * how big it is — the placer measures the plan and {@code Building.footprint}
 * keeps it — so the search is the box the building actually occupies, from its
 * floor to {@link #SEARCH_HEIGHT} above it. That is a few thousand block reads
 * at worst, which is why it is done at most once per building and never again.
 *
 * <p><strong>Remembered the way {@code Chimneys} remembers a flue</strong>, and
 * keyed the same way: origin, blueprint and facing together, so a hall pulled
 * down and replaced by something else on the same plot cannot inherit the old
 * answer. The one difference is that a miss is <em>not</em> kept. A chimney is
 * derived from a plan and "this plan has no chimney" is a final answer; a bell
 * is read out of the world, and a hall whose belfry has not been laid yet is a
 * hall that will have one in a hundred steps. Keeping the miss would mean a
 * town that was looked at once too early stayed silent for ever — which is the
 * fault this class exists to end, recreated in a cache.
 */
public final class Bells {

    private final ServerLevel level;

    public Bells(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    /**
     * How far above its own floor a bell may hang: twenty-four courses.
     *
     * <p>Comfortably past the top of the tallest tower the placer draws. It was
     * already this number when the search was a single column; the number was
     * never the problem.
     */
    public static final int SEARCH_HEIGHT = 24;

    /**
     * How wide to look when a building has never reported its size.
     *
     * <p>A building the placer has not measured yet has {@link Footprint#UNKNOWN}
     * and no span to search. Eight is wider than any hall the mod draws is half
     * as wide, so the fallback finds the bell rather than missing it, and it
     * costs nothing to be generous about a case that resolves itself the moment
     * the plan is built.
     */
    private static final int FALLBACK_REACH = 8;

    /**
     * The bell this town rings, or null if it has none standing.
     *
     * <p>The hall first and the watchtower second, which is {@code Ambience}'s
     * order and the right one: a settlement has a hall long before it has a
     * tower, so a search that preferred the tower would leave every young town
     * silent. A town with both rings the one on the hall, because that is the
     * bell that belongs to the town rather than to the watch.
     */
    public BlockPos of(Settlement settlement) {
        if (settlement == null) {
            return null;
        }
        BlockPos onATower = null;
        for (Building building : settlement.buildings()) {
            boolean hall = building.role() == BuildingRole.HALL;
            boolean tower = building.blueprintId().contains("watchtower");
            if (!hall && !tower) {
                continue;
            }
            BlockPos found = in(building);
            if (found == null) {
                continue;
            }
            if (hall) {
                return found;
            }
            if (onATower == null) {
                onATower = found;
            }
        }
        return onATower;
    }

    /**
     * The bell inside one building, derived once and then remembered.
     *
     * <p>Null both for a building with no bell in it and for one whose ground is
     * not read, and the two do not need telling apart: nothing can be rung in an
     * unloaded chunk either, and neither answer is kept, so both are asked again
     * when there is something to answer them with.
     */
    public BlockPos in(Building building) {
        if (building == null) {
            return null;
        }
        BlockPos origin = new BlockPos(building.origin().x(), building.origin().y(),
                building.origin().z());
        Belfry key = new Belfry(origin, building.blueprintId(), building.facing());
        BlockPos known = hung.get(key);
        if (known != null) {
            // Checked rather than trusted. A bell a player mined, or one a raid
            // took down with the gable it hung on, is a remembered position that
            // is now a hole in the air — and a town whose alarm rang out of one
            // would be worse than a town with no bell, because it would sound
            // exactly like a town that had one.
            if (level.isLoaded(known) && level.getBlockState(known).is(Blocks.BELL)) {
                return known;
            }
            hung.remove(key);
        }
        if (!level.isLoaded(origin)) {
            return null;
        }
        BlockPos found = search(origin, reachOf(building));
        if (found != null) {
            hung.put(key, found);
        }
        return found;
    }

    /**
     * How far either side of the origin this building reaches.
     *
     * <p>The larger of the two spans in both directions rather than width on x
     * and depth on z, deliberately. Whether a plan's width lies along x or along
     * z depends on the facing it was turned to, and a search that guessed wrong
     * would miss the bell on exactly half the halls in the world — which is the
     * kind of fault that looks like a seed problem for a month. A square box
     * round the larger span is right for every facing, and the few extra columns
     * cost one pass of a loop that runs once per building for the life of the
     * world.
     */
    private static int reachOf(Building building) {
        Footprint print = building.footprint();
        if (print == null || !print.isKnown()) {
            return FALLBACK_REACH;
        }
        return Math.max(print.width(), print.depth()) / 2 + 1;
    }

    /**
     * The lowest bell in the box, with ties broken on the coordinates.
     *
     * <p>Lowest first, and the order is a decision rather than an accident of
     * the loop. A hall may have a hand bell by the door as well as the one in
     * the belfry, and a search that returned whichever it met first would ring a
     * different bell depending on which way the building happened to be turned.
     * The lowest is also the nearest a player standing in the street, which is
     * the one the sound should come from.
     */
    private BlockPos search(BlockPos origin, int reach) {
        for (int dy = 0; dy <= SEARCH_HEIGHT; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos at = origin.offset(dx, dy, dz);
                    if (level.isLoaded(at) && level.getBlockState(at).is(Blocks.BELL)) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    /**
     * What a remembered bell belongs to.
     *
     * <p>{@code Chimneys.Roof}'s key, for {@code Chimneys.Roof}'s reason: a hall
     * razed and replaced by a storehouse on the same plot is a different
     * building, and the old answer would have the new one ringing a bell that is
     * not there.
     */
    private record Belfry(BlockPos origin, String blueprintId, int facing) {
    }

    /** Every bell the search has been paid for. */
    private final Map<Belfry, BlockPos> hung = new HashMap<>();
}
