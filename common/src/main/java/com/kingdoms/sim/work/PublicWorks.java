package com.kingdoms.sim.work;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.BuildLoad;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
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
     * The work a town's spare hands would be put on right now, or null when it
     * has none free or nothing of its own within reach.
     *
     * <p>The question a clock has to ask before it does anything: where there is
     * a hand there is no clock, and the hand has to be on <em>this</em> work.
     * "Somebody in this town is a builder" is not the same question and answering
     * it instead is how a work below the wall in this list came to be done by
     * nobody at all — the clock stood aside for hands that were never coming,
     * because the foreman had them on the ring.
     *
     * <p>It is the foreman's own choice, made without a world: the build queue
     * first, because shelter and stores come before all of this and a repair is
     * work in that queue too; then the list in order, taking the first work with
     * a job in a loaded chunk that the town can start and has the materials for.
     * The platform adds two refusals this cannot see — a route nothing can path
     * and growth in the way — and both of those only ever mean the crew is one
     * pass later than this says.
     *
     * <p>{@code PerimeterPlanner} asks a blunter question of its own and is left
     * to: a wall must never go up beside a builder who is standing right there,
     * so it stands aside for any hands at all rather than for the hands the
     * foreman would actually send. The cost is a wall that waits while the town
     * builds, which is the priority this list states anyway.
     */
    public static Worksite handsAreOn(Settlement settlement, WorldBridge bridge) {
        if (!settlement.buildQueue().isEmpty()) {
            return null;   // shelter and stores before roads and walls
        }
        boolean spare = false;
        for (Person person : settlement.residents()) {
            if (settlement.laboursAs(person, Profession.BUILDER)
                    && person.isEmbodied() && !person.isTooWeakToWork()) {
                spare = true;
                break;
            }
        }
        if (!spare) {
            return null;
        }
        for (Worksite work : of(settlement)) {
            if (!work.isWorthStarting(settlement)) {
                continue;
            }
            SimPos station = work.nextStation(settlement);
            if (station == null || !bridge.isLoaded(station)) {
                continue;
            }
            if (work.material() != null
                    && settlement.nearestStore(station, work.material()) == null) {
                continue;   // no shelf holds it, so nobody is going to be sent
            }
            return work;
        }
        return null;
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

        /**
         * The near end of the next run, which is where a crew starts walking.
         *
         * <p>It used to be the middle of the run, on the reasoning that one walk
         * should cover a whole stretch. That was true of the work it described:
         * a settler walked out, swung once, and the entire street was stamped in
         * behind them. A road is paved as it is walked now, cross-section by
         * cross-section, so a crew starts at one end and works to the other, and
         * the middle is where they will be halfway through.
         */
        @Override
        public SimPos nextStation(Settlement settlement) {
            int run = nextRun(settlement);
            return run < 0 ? null : settlement.paths().segments().get(run).positions().getFirst();
        }

        /**
         * Which run the crew is opening, or -1 when the network is all walked out.
         *
         * <p>Named rather than derived from {@link #nextStation}, because the
         * platform needs the run itself — its width and the columns along it —
         * and not only a place to stand.
         */
        public int nextRun(Settlement settlement) {
            PathNetwork paths = settlement.paths();
            if (paths == null) {
                return -1;
            }
            List<PathNetwork.Segment> segments = paths.segments();
            for (int i = 0; i < segments.size(); i++) {
                if (!paths.isOpened(i) && !paths.isUnwalkable(i)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Nothing. A dirt path is shovel work.
         *
         * <p>Worth stating rather than leaving to the default, because it is the
         * one thing that separates a road from the wall above it in this list. A
         * fence post is a plank somebody carries out of the storehouse; a track
         * is the ground that was already underfoot, trodden down. So a paving
         * crew never walks to the stores, is never held up by an empty shelf, and
         * can never be the reason a town runs out of timber — and a builder the
         * wall has stranded at a bare warehouse is exactly the builder who should
         * be out here instead.
         *
         * <p>The bridge over a stream is the exception that proves it, and it is
         * free for the same reason {@code Bridge} has always given: a village
         * that cannot cross its own brook because it is short of stone is a
         * village with a bug in it, not one with a supply problem.
         */
        @Override
        public String material() {
            return null;
        }

        @Override
        public boolean pay(Settlement settlement) {
            return true;   // a track is trodden, not bought; the labour is the cost
        }

        @Override
        public void completeOne(Settlement settlement, boolean worked) {
            // Nothing per column. A street is opened or it is not; see
            // finishStretch, which is where a run is written down.
        }

        @Override
        public void finishStretch(Settlement settlement) {
            int run = nextRun(settlement);
            if (run >= 0) {
                settlement.paths().markOpened(run);
            }
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
