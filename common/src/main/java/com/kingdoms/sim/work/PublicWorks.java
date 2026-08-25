package com.kingdoms.sim.work;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.PerimeterPlanner;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Settlement;

import java.util.ArrayList;
import java.util.List;

/**
 * Every public work a settlement currently has, in the order it cares about them.
 *
 * <p>Two so far — the wall and the roads — and the point of the list is that a
 * third is an entry here rather than a new worker, a new tick pass and a new set
 * of rules about who is free to build it.
 *
 * <p>The order is the priority, and it is the only priority there is. Roads
 * before the wall, because a road is what lets everybody else get to work
 * faster and a wall is a thing a finished town puts round itself. Both after
 * the build queue, which the foreman checks before it asks this at all: shelter
 * and stores before either.
 */
public final class PublicWorks {

    private PublicWorks() {
    }

    /** What this settlement has outstanding, most important first. */
    public static List<Worksite> of(Settlement settlement) {
        List<Worksite> works = new ArrayList<>(2);
        works.add(new RoadWork());
        works.add(new WallWork());
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
                if (!paths.isOpened(i)) {
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
        public void completeOne(Settlement settlement) {
            PathNetwork paths = settlement.paths();
            if (paths == null) {
                return;
            }
            List<PathNetwork.Segment> segments = paths.segments();
            for (int i = 0; i < segments.size(); i++) {
                if (!paths.isOpened(i)) {
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
     * life — see {@link PerimeterPlanner#COIN_PER_POST}.
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

        @Override
        public boolean pay(Settlement settlement) {
            return PerimeterPlanner.payForPost(settlement);
        }

        @Override
        public void completeOne(Settlement settlement) {
            Perimeter perimeter = settlement.perimeter();
            if (perimeter != null) {
                perimeter.setLaid(perimeter.laid() + 1);
            }
        }

        @Override
        public boolean isWorthStarting(Settlement settlement) {
            // Do not take the timber a building is waiting on. The wall is the
            // one work with no queue behind it and no deadline, so it is the one
            // that gives way.
            return nextStation(settlement) != null
                    && settlement.woodStock()
                        >= PerimeterPlanner.TIMBER_KEPT_FOR_BUILDING
                            + PerimeterPlanner.WOOD_PER_POST;
        }
    }
}
