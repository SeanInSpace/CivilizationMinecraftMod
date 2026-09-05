package com.kingdoms.sim.work;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.BuildLoad;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;

import java.util.ArrayList;
import java.util.List;

/**
 * Every public work a settlement currently has, in the order it cares about them.
 *
 * <p>Three so far — the line the wall replaced, the wall, and the roads — and
 * the point of the list is that a fourth is an entry here rather than a new
 * worker, a new tick pass and a new set of rules about who is free to build it.
 *
 * <p>The order is the priority, and it is the only priority there is. All of it
 * comes after the build queue, which the foreman checks before it asks this at
 * all: shelter and stores before any of them, and a repair is work in that queue
 * too, so a hole in a house outranks a hole in the wall.
 */
public final class PublicWorks {

    private PublicWorks() {
    }

    /**
     * What this settlement has outstanding, most important first.
     *
     * <p>The wall before the roads, which is the other way round from how this
     * list started. The argument for roads first was that a road is what lets
     * everybody else get to work faster, and that is a good argument about a town
     * still filling in — but the wall is not staked until the charter, by which
     * time the streets of the quarter it encloses are mostly walked out already.
     * What the old order actually produced was a town that answered every new
     * outlying shed with a fresh stretch of track and never got back to the ring,
     * because a growing town always has one more street planned. A half-built
     * wall is a town that cannot shut its gate; a half-built lane is a walk
     * across grass.
     *
     * <p>The old line comes down before either. It is barely any work — a post
     * pulled up is a swing and half a plank back on the shelf — and while it
     * stands there are two walls round one town, which is the fence line through
     * the middle of a settlement that the whole concave-hull argument was about.
     */
    public static List<Worksite> of(Settlement settlement) {
        List<Worksite> works = new ArrayList<>(3);
        works.add(new DismantleWork());
        works.add(new WallWork());
        works.add(new RoadWork());
        return works;
    }

    /**
     * Opening a stretch of road.
     *
     * <p>Only opening it. Keeping an existing road clear of the grass that grows
     * back over it is upkeep, happens wherever the town is loaded, and is not
     * worth walking somebody across the village for — the difference between
     * building a road and sweeping one.
     */
    public static final class RoadWork implements Worksite {

        @Override
        public String name() {
            return "road";
        }

        @Override
        public SimPos nextStation(Settlement settlement) {
            PathNetwork paths = settlement.paths();
            if (paths == null) {
                return null;
            }
            List<PathNetwork.Segment> segments = paths.segments();
            for (int i = 0; i < segments.size(); i++) {
                if (!paths.isOpened(i) && !paths.isUnwalkable(i)) {
                    // The middle of the run, so one walk covers a whole stretch
                    // rather than a settler pacing it column by column.
                    return midpointOf(segments.get(i));
                }
            }
            return null;
        }

        @Override
        public boolean pay(Settlement settlement) {
            return true;   // a track is trodden, not bought; the labour is the cost
        }

        @Override
        public void completeOne(Settlement settlement, boolean worked) {
            PathNetwork paths = settlement.paths();
            if (paths == null) {
                return;
            }
            List<PathNetwork.Segment> segments = paths.segments();
            for (int i = 0; i < segments.size(); i++) {
                if (!paths.isOpened(i) && !paths.isUnwalkable(i)) {
                    paths.markOpened(i);
                    return;
                }
            }
        }

        private static SimPos midpointOf(PathNetwork.Segment segment) {
            List<SimPos> run = segment.positions();
            return run.get(run.size() / 2);
        }
    }

    /**
     * Raising the palisade, one post at a time and in the order it was staked.
     *
     * <p>Costs timber and coin, which is what puts it late in a settlement's
     * life — see {@link PerimeterPlanner#COIN_PER_POST}. The timber is a plank
     * the builder carries out of the storehouse and the coin is charged at the
     * post, which is the same total the clock pays by a different route; see
     * {@link PerimeterPlanner#payCoinForPost}.
     */
    public static final class WallWork implements Worksite {

        @Override
        public String name() {
            return "wall";
        }

        @Override
        public SimPos nextStation(Settlement settlement) {
            Perimeter perimeter = settlement.perimeter();
            if (perimeter == null || perimeter.laid() >= perimeter.length()) {
                return null;
            }
            return perimeter.ringPositions().get(perimeter.laid());
        }

        /**
         * A plank, carried from the storehouse.
         *
         * <p>What a post is made of, and the reason a wall now empties a
         * warehouse rather than a number. Sixteen to a load, so a builder plants
         * sixteen posts — a good side of a small ring — between trips.
         */
        @Override
        public String material() {
            return TownStores.WOOD;
        }

        /**
         * The coin only. The plank is already in the builder's hands.
         *
         * <p>{@link PerimeterPlanner#payForPost} is the whole price and is what
         * the clock pays; this is what is left of it once the timber has come
         * off the books at the storehouse. Charging both here would take two
         * logs out of a town for one post.
         */
        @Override
        public boolean pay(Settlement settlement) {
            return PerimeterPlanner.payCoinForPost(settlement);
        }

        @Override
        public void completeOne(Settlement settlement, boolean worked) {
            Perimeter perimeter = settlement.perimeter();
            if (perimeter != null) {
                // Whether or not a block went in. A position where the line is
                // already shut -- by a building it grazes, or by a fence that was
                // there first -- is a position of wall the town has, and a crew
                // that would not count it would stand there for ever.
                perimeter.setLaid(perimeter.laid() + 1);
            }
        }

        /**
         * Whether the wall may take timber at all.
         *
         * <p>Do not take the timber a building is waiting on. The wall is the one
         * work with no queue behind it and no deadline, so it is the one that
         * gives way.
         *
         * <p>The reserve is measured against a whole <em>load</em> rather than a
         * post, and that is what a builder actually takes: {@code BuildLoad}
         * draws {@code LOAD_SIZE} at the shelves in one grab. Asking for one
         * post's worth let a town at exactly the reserve pass the check and drop
         * fifteen planks below it — and those fifteen were then in somebody's
         * arms, where the build queue this reserve is held for cannot see them.
         */
        @Override
        public boolean isWorthStarting(Settlement settlement) {
            return nextStation(settlement) != null
                    && settlement.woodStock()
                        >= PerimeterPlanner.TIMBER_KEPT_FOR_BUILDING + BuildLoad.LOAD_SIZE;
        }
    }

    /**
     * Taking down the line the town has replaced.
     *
     * <p>A settlement that outgrows its palisade stakes a wider one, and the two
     * must never both stand: an old ring left up inside a new one is a fence
     * through the middle of a town, and it shuts settlers out of their beds
     * however honourable its history. So the old posts come up the same way they
     * went in — somebody walks to them and pulls them out, in the order the line
     * was walked, which is what {@link Perimeter#pulled()} counts.
     *
     * <p>Half the timber comes back. A palisade post that has stood in the
     * ground through a generation of weather is firewood rather than lumber, and
     * a town that got its whole wall back every time it moved one would be
     * re-staking at a profit. Half is the trade a salvage yard would make, and it
     * keeps moving a wall from being free.
     */
    public static final class DismantleWork implements Worksite {

        @Override
        public String name() {
            return "dismantle";
        }

        @Override
        public SimPos nextStation(Settlement settlement) {
            Perimeter perimeter = settlement.perimeter();
            if (perimeter == null || perimeter.dismantled()) {
                return null;
            }
            return perimeter.retiredPositions().get(perimeter.pulled());
        }

        @Override
        public boolean pay(Settlement settlement) {
            return true;   // pulling a post up costs a swing and nothing else
        }

        /**
         * One post up, and half a plank back on the shelf.
         *
         * <p>The half is read off the position rather than banked, so no
         * fraction has to be carried anywhere: every second post along the line
         * returns a whole one. Sixteen posts come back as eight planks however
         * often the work is interrupted, because which of them pay is decided by
         * where they stand and not by the order they were reached in.
         *
         * <p>Only where a post actually came out of the ground. The away sweep
         * clears whatever the crew never reached, and a second re-staking starts
         * the count again at the head of a line that may be half down — so a
         * crew walks over cleared ground often, and a town paid salvage for it
         * would be making timber out of nothing and moving its wall at a profit.
         */
        @Override
        public void completeOne(Settlement settlement, boolean worked) {
            Perimeter perimeter = settlement.perimeter();
            if (perimeter == null || perimeter.dismantled()) {
                return;
            }
            if (worked && perimeter.pulled() % 2 == 0) {
                settlement.stores().add(TownStores.WOOD, PerimeterPlanner.WOOD_PER_POST);
            }
            perimeter.setPulled(perimeter.pulled() + 1);
            if (perimeter.dismantled()) {
                // Nothing of the old line is left to walk to, so the town stops
                // carrying it. The sweep that pulls down whatever the crew never
                // reached has its own way of arriving at the same conclusion.
                perimeter.forgetRetired();
            }
        }
    }
}
