package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;
import java.util.List;

/**
 * The stone trade — the timber trade's twin, one layer down.
 *
 * <p>Miners cut stone inside the mine's {@link WorkArea} and carry it to the
 * town's stores. As with {@link LumberPlanner}, the cutting itself is world work
 * done by the platform layer; this class owns what the simulation is
 * authoritative over — where the mine may work, how much stone the town can
 * hold, and the running total.
 *
 * <p>Stone matters because building now costs digging as well as laying. A town
 * that cannot cut its own is a town that cannot found the next one.
 *
 * <p>Deliberately mirrors LumberPlanner rather than sharing code with it. The two
 * trades are the same shape today and will not stay that way — ore, depth and
 * tool tiers all belong here and nowhere near the woodland.
 *
 * <p><strong>What a mine produces is its {@link Seam}</strong>, and the seam does
 * not grow back. A camp felled bare puts its own saplings in; a mine cut out is
 * cut out, and the town has to find more ground. No yield table has any say in
 * either.
 */
public final class MinePlanner {

    public static final int DEFAULT_RADIUS = 16;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 48;
    public static final int RADIUS_STEP = 8;

    /**
     * Iron worth keeping on hand.
     *
     * <p>The forge stops once tools, weapons and armor are all stocked, so
     * without a ceiling the ore just piles up — a 700-step run ended holding
     * eleven hundred of it, which is noise in the ledger rather than wealth.
     */
    public static final int MAX_IRON = 256;

    /** Stone the town can stockpile before further cutting is pointless. */
    public static final int BASE_STONE_STORAGE = 512;
    public static final int STONE_PER_STOREHOUSE = 400;

    private MinePlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        SimPos mine = minePos(settlement);
        if (settlement.mineArea() == null && mine != null) {
            settlement.setMineArea(new WorkArea(mine, DEFAULT_RADIUS));
            settlement.logEvent(ctx.step(), "The mine claims the stone around " + mine);
        }
        workTheStone(settlement, ctx);
    }

    /**
     * A step of the stone trade, mine by mine. See {@code LumberPlanner} — same
     * reasoning, one layer down, minus the regrowth.
     */
    private static void workTheStone(Settlement settlement, SimContext ctx) {
        List<Building> mines = settlement.buildingsWithRole(BuildingRole.MINE);
        if (mines.isEmpty()) {
            return;
        }
        int miners = (int) settlement.residents().stream()
                .filter(p -> p.profession() == Profession.MINER && !p.isTooWeakToWork())
                .count();
        boolean wantsStone = wantsMoreStone(settlement);
        int places = mines.size();
        for (int i = 0; i < places; i++) {
            Building mine = mines.get(i);
            if (reckonSeam(settlement, mine, ctx)) {
                continue;   // where there is a hand there is no clock
            }
            int share = Workforce.shareOf(miners, i, places);
            if (share <= 0 || !wantsStone) {
                continue;
            }
            dig(settlement, mine, share, ctx);
        }
    }

    /** One mine's digging for one step, and the ore turned up while cutting it. */
    private static void dig(Settlement settlement, Building mine, int miners, SimContext ctx) {
        int room = Math.max(0, stoneCapacity(settlement) - settlement.stoneStock());
        int cut = Seam.cut(mine,
                Math.min(miners * Seam.STONE_PER_MINER_PER_STEP, room));
        if (cut <= 0) {
            return;
        }
        // At the mine head that cut it, for the same reason the timber is put
        // down at the camp: the load lands on the nearest shelves.
        SimPos at = mine.origin();
        settlement.produceNear(at, TownStores.STONE, cut, stoneCapacity(settlement));
        settlement.produceNear(at, TownStores.IRON, cut / Seam.STONE_PER_IRON, MAX_IRON);
        if (Seam.isExhausted(mine)) {
            settlement.logEvent(ctx.step(),
                    "The mine at " + at + " is cut out — there is no stone left in it");
        }
    }

    /**
     * Counts the stone under the mine when the ground can answer, and says
     * whether anybody is watching.
     *
     * <p>The rock is its own truth, exactly as the trees are: a mine somebody
     * has just walked up to counts what is actually down there rather than being
     * told what the ledger believed. That is also what keeps a watched mine
     * honest — real picks debit the seam block by block, and the next arrival
     * squares the books against the hole they left.
     */
    private static boolean reckonSeam(Settlement settlement, Building mine, SimContext ctx) {
        // The town's answer, not the mine's: a shaft inside a watched claim is
        // worked by picks. A mine sunk out past the ring answers for its own
        // ground.
        boolean watched = settlement.isWatched(ctx, mine.origin());
        boolean arriving = watched && !mine.wasWatched();
        if (arriving || !Seam.isCounted(mine)) {
            if (ctx.bridge().isLoaded(mine.origin())) {
                WorkArea area = settlement.mineArea();
                int radius = area == null ? DEFAULT_RADIUS : area.radius();
                Seam.recount(mine, ctx.bridge().countStoneBelow(
                        mine.origin(), radius, Seam.WORKINGS_DEPTH));
            } else if (!Seam.isCounted(mine)) {
                // Ground nobody can read is a mine of unknown worth rather than
                // an empty one — see Seam.UNSURVEYED, and LumberPlanner for the
                // same reasoning at the surface.
                Seam.recount(mine, Seam.UNSURVEYED);
            }
        }
        mine.setWatched(watched);
        return watched;
    }

    /** Where the mine stands, or null if the town has not built one. */
    public static SimPos minePos(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (building.role() == BuildingRole.MINE) {
                return building.origin();
            }
        }
        return null;
    }

    public static int stoneCapacity(Settlement settlement) {
        return BASE_STONE_STORAGE
                + BuildPlanner.storeStrength(settlement) * STONE_PER_STOREHOUSE;
    }

    /** Whether cutting is still worth doing, so miners idle instead of hollowing the hill. */
    public static boolean wantsMoreStone(Settlement settlement) {
        return settlement.stoneStock() < stoneCapacity(settlement);
    }

    public static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }
}
