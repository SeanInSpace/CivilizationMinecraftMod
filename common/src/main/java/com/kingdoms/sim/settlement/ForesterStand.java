package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The wood a settled town has always had.
 *
 * <p>A town that grew raised its lumber camp in a forest and has been cutting it
 * back ever since. A town that was <em>seeded</em> — world generation, or
 * {@code /civ seed} — was written into existence complete, and the ground around
 * its camp is whatever the terrain generator happened to leave there. On a plain
 * that is grass. So a lumberjack walked out of a finished camp, found nothing
 * standing inside the claim, and the town's only timber was the abstract clock's
 * — which is exactly the yield a world can now turn off. A forester with no trees
 * is not a forester.
 *
 * <p>This lays the stand that camp is supposed to have been working: grown trees,
 * of the wood the biome grows, on the free ground of the camp's claim, at the
 * spacing {@code LumberjackWorker} fells and replants at. It runs once, at the
 * moment the camp is first drawn — see {@link Building#isSeeded()} — because that
 * is the first time anybody knows what the ground under the camp actually is.
 *
 * <p>The species and the placing of an actual tree are world work, done by the
 * platform layer. What lives here is the part the simulation is authoritative
 * over: how wide the camp's woodland claim has to be, and which squares of it a
 * tree may stand on.
 */
public final class ForesterStand {

    /**
     * Blocks between one trunk and the next.
     *
     * <p>Wide enough that two vanilla canopies do not grow through each other,
     * and narrow enough that a lumberjack who has felled one is already within
     * {@code LumberjackWorker.WORK_REACH} of walking to the next. Tighter than
     * this and the stand reads as a hedge; wider and a dozen trees no longer fit
     * in a claim.
     */
    public static final int SPACING = 5;

    /**
     * Trees a seeded camp is given, at most.
     *
     * <p>A dozen is a stand somebody has been working, not a forest: enough
     * timber to keep a camp in work for a long while, few enough that the town
     * still stands in open country. The ground decides the real number — a claim
     * that is half lake plants half a stand, and nothing here levels anything to
     * fix that.
     */
    public static final int TREES_WANTED = 12;

    /**
     * Saplings in the camp's own store on the day the town is seeded.
     *
     * <p>Enough to put back what the stand loses before the woodland renews
     * itself, and it is a possession rather than a yield: a working camp has a
     * box of seed in it, the same way it has axes. Deliberately not scaled to
     * {@link #TREES_WANTED} — a camp keeps what it keeps whether or not the
     * ground took every tree.
     */
    public static final int SAPLINGS_ON_HAND = 8;

    /**
     * How far past the edge of the village the woodland claim has to reach.
     *
     * <p>Not decoration. {@code LumberjackWorker} refuses to replant anywhere
     * inside the village — trees between the houses block every path the town
     * lays — so a claim that lies wholly within {@code Settlement.claimRadius}
     * is a claim nothing can ever be planted in, and a stand laid there would be
     * felled once and never come back. A seeded camp therefore claims out past
     * the houses, far enough for three ranks of trees.
     */
    public static final int BELT = 3 * SPACING;

    private ForesterStand() {
    }

    /**
     * The woodland a seeded camp works.
     *
     * <p>Centered on the camp, like any other, and no smaller than the default —
     * but widened until it reaches {@link #BELT} blocks beyond the village edge,
     * because that annulus is the only ground a lumberjack will replant on. Still
     * clamped to what a player may set through the camp block, so nothing here
     * produces a claim they could not have made themselves.
     */
    public static WorkArea woodlandFor(SimPos camp, SimPos center, int claimRadius) {
        int outFromTheHouses = claimRadius
                - (int) Math.floor(camp.horizontalDistance(center)) + BELT;
        int radius = LumberPlanner.clampRadius(
                Math.max(LumberPlanner.DEFAULT_RADIUS, outFromTheHouses));
        return new WorkArea(camp, radius);
    }

    /**
     * Where the trees of the stand stand.
     *
     * <p>A grid on {@link #SPACING}, anchored on the camp so the ranks line up
     * with the camp rather than with the world origin, minus everything a tree
     * has no business being on: the camp's own square, anything outside the
     * claim, anything inside the village, and any plot, road or wall the town's
     * own siting rules already refuse — {@code Settlement.isPlotFree} answers all
     * three of those in one question, and answering it the same way is the point.
     *
     * <p>Nearest the camp first, so a claim that only yields a handful of usable
     * squares yields the handful closest to the people who work them, and so the
     * platform layer can simply walk the list until it has planted enough.
     *
     * <p>More positions are returned than {@link #TREES_WANTED}: these are
     * candidates, not a stand. Only the world knows which of them are lake,
     * cliff or bare stone, so it takes the list in order and stops when it has
     * its dozen.
     */
    public static List<SimPos> candidates(Settlement town, WorkArea area) {
        SimPos camp = area.center();
        int radius = area.radius();
        int claim = town.claimRadius();
        List<SimPos> spots = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += SPACING) {
            for (int dz = -radius; dz <= radius; dz += SPACING) {
                if (dx == 0 && dz == 0) {
                    continue;   // the camp is standing here
                }
                SimPos spot = camp.offset(dx, 0, dz);
                if (!area.contains(spot)) {
                    continue;
                }
                if (spot.horizontalDistanceSq(town.center()) <= (long) claim * claim) {
                    continue;   // the village; a tree here is in somebody's way
                }
                if (!town.isPlotFree(spot, SPACING, null)) {
                    continue;   // a plot, a road, or the wall
                }
                spots.add(spot);
            }
        }
        spots.sort(Comparator.comparingLong(spot -> spot.horizontalDistanceSq(camp)));
        return spots;
    }

    /**
     * Plants the stand a seeded camp should always have had, once.
     *
     * <p>Called from {@code Settlement.materializePending} the moment the camp is
     * first drawn — the earliest step at which the ground under it is loaded and
     * therefore knowable. The camp's woodland claim is widened first, so the
     * squares chosen are squares a lumberjack will later replant on.
     *
     * <p>Nothing happens for a founded camp: the four pioneers of a charter get
     * the country they walked into, and their lumberjacks plant saplings and wait
     * for them, which is the whole of the early game they are for.
     */
    public static void raise(Settlement town, Building camp, SimContext ctx) {
        if (camp.role() != BuildingRole.LUMBER_CAMP) {
            return;
        }
        WorkArea woodland = woodlandFor(camp.origin(), town.center(), town.claimRadius());
        WorkArea standing = town.lumberArea();
        // Never overrule a claim somebody has moved. A camp block the player has
        // already pointed somewhere else is their decision, and widening it back
        // would undo it silently.
        if (standing == null || standing.center().equals(woodland.center())) {
            town.setLumberArea(woodland);
        } else {
            woodland = standing;
        }
        int planted = ctx.bridge().plantGrownTrees(candidates(town, woodland), TREES_WANTED);
        // The camp's ledger is sized from what actually went in, rather than
        // being made to go and count trees the platform has just this moment
        // planted and knows the number of. Everywhere else a stand is counted
        // off the ground — see {@code LumberPlanner.reckonStand} — and this is
        // the one moment the world can simply say.
        //
        // Only when something went in. A platform that planted nothing has not
        // told us the ground is bare — it may be thick with trees the generator
        // put there — so the camp is left uncounted and counts for itself.
        if (planted > 0) {
            Stand.recount(camp, planted);
            town.logEvent(ctx.step(), "The wood around the lumber camp stands "
                    + planted + " trees deep");
        }
    }
}
