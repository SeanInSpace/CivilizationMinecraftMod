package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;
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
 */
public final class MinePlanner {

    public static final int DEFAULT_RADIUS = 16;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 48;
    public static final int RADIUS_STEP = 8;

    /** Stone one miner cuts per step when nobody is watching them do it. */
    /**
     * Stone one miner cuts per step when nobody is watching them do it.
     *
     * <p>A little above break-even against what building spends. At exactly
     * break-even a town hovers near zero stone and stalls at random, which reads
     * as a bug even though the arithmetic is working.
     */
    public static final int STONE_PER_STEP = 6;

    /** Iron turned up per miner per step: ore is rarer than rock. */
    public static final int IRON_PER_STEP = 1;

    /**
     * Iron worth keeping on hand.
     *
     * <p>The forge stops once tools, weapons and armour are all stocked, so
     * without a ceiling the ore just piles up — a 700-step run ended holding
     * eleven hundred of it, which is noise in the ledger rather than wealth.
     */
    public static final int MAX_IRON = 256;

    /** Stone the town can stockpile before further cutting is pointless. */
    public static final int BASE_STONE_STORAGE = 512;
    public static final int STONE_PER_STOREHOUSE = 400;

    /**
     * Steps a watched mine may go without real work before the clock takes
     * over again.
     *
     * <p>Deliberately the same shape and the same number as
     * {@code FoodPlanner.WATCHED_HARVEST_GRACE_STEPS}. What stood here was an
     * outright return the moment a player came within the observed radius —
     * which is ninety-six blocks, against a mine on a ring plot a dozen from
     * the town square. Standing in your own town suppressed the abstract yield
     * entirely, and that is only correct while the real hands are actually
     * working: on ordinary flat grassland there is no exposed stone to swing at until
     * a shaft has been sunk. So the town received nothing at all for exactly as long
     * as somebody was there to watch it fail.
     *
     * <p>Being watched must never starve a town, and it must not bankrupt one
     * either.
     */
    public static final int WATCHED_WORK_GRACE_STEPS = 12;

    private MinePlanner() {
    }

    public static void advance(Settlement settlement, SimContext ctx) {
        SimPos mine = minePos(settlement);
        if (settlement.mineArea() == null && mine != null) {
            settlement.setMineArea(new WorkArea(mine, DEFAULT_RADIUS));
            settlement.logEvent(ctx.step(), "The mine claims the stone around " + mine);
        }
        cutUnwatched(settlement, ctx, mine);
    }

    /**
     * Stone and iron won while nobody is looking. See
     * {@code LumberPlanner.fellUnwatched} — same reasoning, one layer down.
     */
    private static void cutUnwatched(Settlement settlement, SimContext ctx, SimPos mine) {
        if (mine == null || !wantsMoreStone(settlement)) {
            return;
        }
        if (ctx.bridge().playerWithin(mine, ctx.settings().observedRadius())
                && cutRecently(settlement, ctx.step())) {
            return;   // somebody is watching and the real picks are cutting
        }
        int miners = (int) settlement.residents().stream()
                .filter(p -> p.profession() == Profession.MINER && !p.isTooWeakToWork())
                .count();
        if (miners <= 0) {
            return;
        }
        // At the mine head that cut it, split between the mines for the same
        // reason the timber is split between the camps.
        List<Building> mines = settlement.buildingsWithRole(BuildingRole.MINE);
        int places = Math.max(1, mines.size());
        for (int i = 0; i < places; i++) {
            int share = Workforce.shareOf(miners, i, places);
            if (share <= 0) {
                continue;
            }
            SimPos at = mines.isEmpty() ? settlement.centre() : mines.get(i).origin();
            settlement.produceNear(at, TownStores.STONE,
                    share * STONE_PER_STEP, stoneCapacity(settlement));
            settlement.produceNear(at, TownStores.IRON, share * IRON_PER_STEP, MAX_IRON);
        }
    }

    /** Whether any mine has seen a real block cut lately. */
    private static boolean cutRecently(Settlement settlement, long step) {
        for (Building mine : settlement.buildingsWithRole(BuildingRole.MINE)) {
            if (mine.harvestedWithin(step, WATCHED_WORK_GRACE_STEPS)) {
                return true;
            }
        }
        return false;
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
