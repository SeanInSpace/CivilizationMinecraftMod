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

    /**
     * How many candidate squares the stand is actually chosen from.
     *
     * <p>The nearest three dozen, and it is a bound on <em>reading the world</em>
     * rather than on planting. Every square a tree might go on has to be ground
     * somebody can see before any of them is planted — that is what makes the
     * stand the whole dozen nearest the camp rather than whichever half happened
     * to be inside the horizon — and a village that has grown out past its camp
     * has a belt hundreds of squares long. Demanding the whole of one is
     * demanding more loaded ground than a server keeps: the debt would never
     * settle, and a camp owed its wood forever is the same bare field as a camp
     * that was never owed any.
     *
     * <p>Three times what is wanted, for the reason there are more candidates
     * than trees at all: the ground gets a say, and a camp on a lakeshore has to
     * be able to lose two squares in three and still come out with a stand.
     */
    public static final int STAND_SEARCH = 3 * TREES_WANTED;

    /**
     * How many further belts a camp will look out before settling for what it
     * has: four.
     *
     * <p>A bound rather than a budget. Measured across every people and every
     * arrangement they build, on flat ground, at the size a town settles at: the
     * first belt offers between forty-eight and ninety-seven squares, so the
     * loop below almost never runs at all, and one further belt has always been
     * enough where it does. Four is that with room to spare, and it is here so
     * that a camp walled in by its own town stops rather than claiming the
     * county.
     */
    public static final int BELTS_OUT = 4;

    private ForesterStand() {
    }

    /**
     * The woodland a seeded camp works.
     *
     * <p>Centered on the camp, like any other, and no smaller than the default —
     * but widened until it reaches {@link #BELT} blocks beyond the village edge,
     * because that annulus is the only ground a lumberjack will replant on.
     *
     * <p><strong>Widened as far as it actually takes, and that is the whole of
     * the second bug this method has had.</strong> It used to stop at
     * {@code LumberPlanner.MAX_RADIUS} — what a player can dial up on the camp
     * block — on the grounds that nothing here should produce a claim they could
     * not have made themselves. That reasoning holds only while a village stays
     * inside sixty-four blocks of its own lumber camp, which the concentric
     * rings did and no other arrangement does: a crossroads town flings its
     * frontage out along four arms, a warren buds knots off knots, and the
     * claim circle that contains the outermost of them is a hundred blocks and
     * more. Clamped, the camp's whole claim then lay <em>inside</em> the village,
     * every square of it was refused as somebody's doorstep, and the camp
     * settled its debt having planted nothing at all. A claim that cannot reach
     * the countryside is not a claim; the reach is the requirement and the dial
     * is the convenience, so the dial gives way.
     */
    public static WorkArea woodlandFor(SimPos camp, SimPos center, int claimRadius) {
        int outFromTheHouses = claimRadius
                - (int) Math.floor(camp.horizontalDistance(center)) + BELT;
        int radius = Math.max(LumberPlanner.DEFAULT_RADIUS, outFromTheHouses);
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
        return freeOf(town, grid(town, area), Integer.MAX_VALUE);
    }

    /**
     * Every square of the camp's grid a tree could stand on, nearest first, on
     * the geometry alone.
     *
     * <p>Split out from {@link #candidates} so the two questions can be asked at
     * two prices. This half is arithmetic and costs nothing; the other half asks
     * the settlement whether each square is somebody's plot, road or wall, which
     * walks every building and every stretch of way in the town. A claim widened
     * round a long-armed city holds thousands of squares and only the nearest
     * dozen are ever planted, so the dear question is worth asking in order and
     * stopping — see {@link #stand}.
     */
    private static List<SimPos> grid(Settlement town, WorkArea area) {
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
                spots.add(spot);
            }
        }
        spots.sort(Comparator.comparingLong(spot -> spot.horizontalDistanceSq(camp)));
        return spots;
    }

    /**
     * The squares of that grid the town has not already spoken for, at most
     * {@code wanted} of them.
     *
     * <p>Sorting before the filter rather than after it, which is the same list:
     * the sort is stable and keyed on distance alone, so dropping squares from
     * an ordered list and ordering what is left of an unfiltered one come to the
     * same order. What it buys is the stopping — a caller that wants a dozen
     * asks the expensive question a dozen-odd times instead of four thousand.
     */
    private static List<SimPos> freeOf(Settlement town, List<SimPos> grid, int wanted) {
        List<SimPos> free = new ArrayList<>();
        for (SimPos spot : grid) {
            if (free.size() >= wanted) {
                break;
            }
            if (town.isPlotFree(spot, SPACING, null)) {
                free.add(spot);   // not a plot, not a road, not the wall
            }
        }
        return free;
    }

    /**
     * Where this town's stand actually stands, worked out rather than stored.
     *
     * <p>The camp keeps a count of its trees and never kept their squares, and it
     * does not have to: the stand is the nearest {@link #TREES_WANTED} squares of
     * the camp's own grid that the town has not spoken for, which is a function
     * of the camp, the claim and what is standing — all three of which the
     * settlement already knows. A list on the building would be one more thing to
     * write, to load, and to get out of step with the ground.
     *
     * <p>What it is for is the roads. A town goes on building after its stand is
     * planted, and a lane run out to the last farm on the edge of the claim used
     * to be laid straight through the belt, felling the very trunks the camp
     * exists to cut — see {@code PathPlanner}, which holds a road off these
     * squares exactly as it holds one off a plot.
     *
     * <p>Empty for a town with no lumber camp, and empty before the camp has a
     * woodland claim to work. It stays answerable afterwards whether the stand was
     * seeded or grew: the squares are where a stand goes either way, and keeping a
     * road off a dozen squares of a forester's belt costs the network nothing.
     */
    public static List<SimPos> stand(Settlement town) {
        WorkArea wood = town.lumberArea();
        if (wood == null || town.buildingWithRole(BuildingRole.LUMBER_CAMP) == null) {
            return List.of();
        }
        return freeOf(town, grid(town, wood), TREES_WANTED);
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
     *
     * <p><strong>The ground has to be readable first, and that is the whole of
     * the bug this guard exists for.</strong> A camp is drawn the instant its own
     * chunk arrives — which is, by definition, the far edge of what the player
     * can see — while the stand it is owed lies a further {@link #BELT} beyond
     * the edge of the village, out in chunks nobody has loaded. The platform
     * plants nothing on ground it cannot read and says so by returning nought,
     * and the debt used to have been cancelled a line earlier: every town raised
     * by world generation lost its wood at exactly the moment it was supposed to
     * get one. So the camp keeps its mark until the stand can be seen, and is
     * asked again on the next step until it can. It terminates because standing
     * at the camp is what loads the belt, and a camp with no candidate squares at
     * all settles on the first ask.
     *
     * <p>The stand, and not the belt. What has to be readable is the
     * {@link #STAND_SEARCH} squares nearest the camp — the ones a tree is
     * actually going to go on — rather than every square of the claim. The
     * difference does not show on a tidy ring town, whose belt is a few dozen
     * squares either way; it is the difference between a stand and a bare field
     * on a town whose arms have carried the village edge a hundred blocks out,
     * because no server keeps that much ground loaded at once and a camp waiting
     * for it would wait forever.
     *
     * @return whether the debt is settled — false means the ground is still
     *         unread and the camp is owed its wood yet
     */
    public static boolean raise(Settlement town, Building camp, SimContext ctx) {
        if (camp.role() != BuildingRole.LUMBER_CAMP) {
            return true;   // nothing is owed, so nothing is outstanding
        }
        WorkArea woodland = woodlandFor(camp.origin(), town.center(), town.claimRadius());
        WorkArea standing = town.lumberArea();
        // Never overrule a claim somebody has moved. A camp block the player has
        // already pointed somewhere else is their decision, and widening it back
        // would undo it silently.
        boolean ours = standing == null || standing.center().equals(woodland.center());
        if (!ours) {
            woodland = standing;
        }
        if (!ctx.bridge().isLoaded(camp.origin())) {
            if (ours) {
                town.setLumberArea(woodland);
            }
            return false;   // the camp's own ground is dark; ask again another step
        }
        List<SimPos> spots = candidates(town, woodland);
        // And further out again while the belt is too thin to hold a stand. The
        // geometry usually settles this on the first ask — a village edge with
        // three ranks of trees outside it offers several dozen squares — but a
        // long-armed arrangement can leave the annulus a sliver: a ring road
        // laid along it, an arm of frontage across it, and the dozen squares
        // wanted are four. A camp does not stop at four. It looks another belt
        // out, and another, which is what anybody working that wood would do.
        for (int wider = 0; ours && wider < BELTS_OUT && spots.size() < STAND_SEARCH;
                wider++) {
            woodland = woodland.withRadius(woodland.radius() + BELT);
            spots = candidates(town, woodland);
        }
        if (ours) {
            town.setLumberArea(woodland);
        }
        if (spots.size() > STAND_SEARCH) {
            spots = spots.subList(0, STAND_SEARCH);
        }
        for (SimPos spot : spots) {
            if (!ctx.bridge().isLoaded(spot)) {
                return false;   // some of the wood is still unread
            }
        }
        int planted = ctx.bridge().plantGrownTrees(spots, TREES_WANTED);
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
        return true;
    }
}
