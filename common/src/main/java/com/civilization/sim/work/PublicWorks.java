package com.civilization.sim.work;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.BuildLoad;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.platform.WorldBridge;
import com.civilization.sim.settlement.BuildPlanner;
import com.civilization.sim.settlement.BuildTask;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Perimeter;
import com.civilization.sim.settlement.PerimeterPlanner;
import com.civilization.sim.settlement.PathNetwork;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.TownStores;

import java.util.ArrayList;
import java.util.List;

/**
 * Every public work a settlement currently has, in the order it cares about them.
 *
 * <p>Six so far — the line the wall replaced, the street lighting, the wall, the
 * roads, the wood inside them, and the ground between the houses — and the point
 * of the list is that a seventh is an entry here rather than a new worker, a new
 * tick pass and a new set of rules about who is free to build it. Three of the six
 * were added in as many afternoons, two on the strength of one night's casualty
 * list and one on the strength of a screenshot of an empty field, and none of them
 * needed a line of new machinery to be built by hand or by the clock.
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
     *
     * <p><strong>The lighting comes after the roads, and it has to.</strong> A lamp
     * stands on the verge of an <em>opened</em> street, so the street is not merely
     * more important, it is the thing the lamp is a function of — and the first
     * arrangement of this list had the lighting first, on the strength of the
     * measurement that produced it, which is a powerful argument for the wrong
     * order. What it produced was a town that paved twenty-eight streets of
     * fifty-eight in three hundred steps instead of all of them: the lighting of a
     * growing town is never finished, because every street it opens wants ten more
     * lamps, so a work above the roads holds the one spared hand for ever and the
     * roads it feeds on stop arriving. The lamps starved themselves.
     *
     * <p>Where the lighting is genuinely ahead of the wall is in
     * {@link #availableTo}, which is the list that runs nearly all the time — a
     * town's build queue is empty for about one step in ten. So in practice the
     * lamps go up alongside the roads and only a settlement with nothing queued and
     * an unfinished ring defers them, which is the same bargain the roads have
     * always had with the wall.
     *
     * <p><strong>The clearing comes after the lighting</strong> because it is the
     * slowest, it is free, and it is the one a town can be behind on for a season
     * without anybody dying of it once the lamps are up. <strong>And the dressing
     * comes after the clearing</strong>, last of all, because it is the only work
     * on the list nobody needs at all.
     */
    public static List<Worksite> of(Settlement settlement) {
        List<Worksite> works = new ArrayList<>(6);
        works.add(new DismantleWork());
        works.add(new WallWork());
        works.add(new RoadWork());
        works.add(new LightWork(settlement));
        works.add(new ClearingWork());
        // And last of all, the dressing. It is last because it is the only work
        // on this list that nobody needs: a town with no wall is raided, a town
        // with no lamps is a spawner, a town with no roads never gets anywhere,
        // and a town with no kitchen gardens is merely a town nobody believes
        // anybody lives in. See Furnishings, and the screenshot that produced it.
        works.add(new DressingWork());
        return works;
    }

    /**
     * Hands a town keeps on its buildings before it spares any for the roads.
     *
     * <p>One, and the whole argument for the number is that a road is the only
     * public work a busy town can afford to send anybody to. Shelter and stores
     * still come first and the last pair of hands in a settlement stays on them;
     * the second pair is what a founding party has four of, and putting all four
     * on the same doorframe is how a town you were standing in came to have no
     * roads at all.
     *
     * <p>The wall does not get this exemption and must not. A post costs a plank
     * the build queue is owed and the ring is hundreds of them, so a wall that
     * interleaved with building would be the town spending its timber on a fence
     * while its bunkhouse waited. A track is trodden — see
     * {@link RoadWork#material} — so the only thing it ever takes from the queue
     * is one person's afternoon.
     */
    public static final int HANDS_KEPT_ON_BUILDINGS = 1;

    /**
     * Items a leading work may be behind and still count as keeping up: two.
     *
     * <p>Applies as an absolute for a small work — a build queue is a handful of
     * plots, and two outstanding is a town keeping pace with its own families.
     * For a work counted in hundreds it is the floor under {@link #keepingUp},
     * which also accepts a proportion.
     */
    public static final int KEEPING_UP = 2;

    /**
     * How much of a work has to be done before the work behind it may start:
     * nine tenths.
     *
     * <p>A town plans a lamp beside every new door and a street every time a
     * house goes up, so a settlement of two hundred and sixty people had
     * fourteen hundred and ninety lamps planned. Asking for an absolute backlog
     * of two out of fourteen hundred and ninety is asking for a coincidence; the
     * question that was actually meant is whether the streets are lit, and they
     * are when nine of every ten lamps are standing.
     */
    private static final int KEEPING_UP_TENTHS = 9;

    /**
     * Whether a work is keeping up well enough for the next one to proceed.
     *
     * <p><strong>What this replaces, and why.</strong> The mod's public works
     * are a priority chain — the houses, then the paving, then the lamps, then
     * the dressing — and every link in it was written as a test for
     * <em>completion</em>: nothing queued, no owed run unopened, every planned
     * lamp standing. Each of those reads like the right sentence and is the
     * wrong test, because a settlement plans the next street and the next lamp
     * out of the same growth that fills the queue. No living town is ever once
     * in any of those states, so the chain did not prioritise — it starved,
     * silently, from the far end inward.
     *
     * <p>The playtest measured the whole length of it. A vale town of a hundred
     * and forty-five people had five hundred and fifty-three dressing pieces
     * planned and <strong>zero</strong> raised at step fourteen hundred: no
     * square, no signpost, no inn board, no garden, no orchard, no woodpile, in
     * a town that had been standing for its entire life. Behind that, the
     * lighting was two hundred and sixty-one lamps short for the same reason.
     * The one sign anybody found in the town was the trade board the inn's own
     * blueprint hangs, blank, because the board that was <em>meant</em> to carry
     * the town's name had never been raised for anything to write on.
     *
     * <p><strong>The test that was meant.</strong> A work is keeping up when it
     * is within {@link #KEEPING_UP} items of done, or when it is
     * {@link #KEEPING_UP_TENTHS} tenths of the way there. The first is for the
     * small works, the second for the ones counted in hundreds, and either is
     * enough. A work further behind than both is genuinely losing ground, and
     * then the chain does what it was always for and makes the next one wait.
     *
     * @param done   how many of this work's items are standing
     * @param wanted how many its shape currently calls for
     */
    public static boolean keepingUp(int done, int wanted) {
        int behind = wanted - done;
        if (behind <= KEEPING_UP) {
            return true;
        }
        return (long) done * 10L >= (long) wanted * KEEPING_UP_TENTHS;
    }

    /**
     * Timber a second job has to cost before it is worth holding for as well: 64.
     *
     * <p>Sixteen work units at {@link BuildPlanner#WOOD_PER_WORK}, which is about
     * a hearth and comfortably under a cottage. The head of the queue is always
     * held for; the one behind it only when holding for it costs the lamps
     * nothing much, because a town that reserved two houses' worth of timber
     * would be a town that never lit a street again — and the whole reason the
     * chain was loosened is that it had starved itself from the far end inward.
     */
    public static final int CHEAP_ENOUGH_TO_HOLD_FOR = 64;

    /**
     * Timber the build queue is owed, which nothing below construction may spend.
     *
     * <p><strong>Construction outranks dressing for timber, and it did not.</strong>
     * The chain in {@link #of} is a priority order over <em>hands</em>, and once
     * {@link #keepingUp} let the lower works run in a living town it turned out
     * that nothing anywhere was a priority order over <em>timber</em>. A lamp is
     * a plank and a garden fence is four, and a town that raises them out of the
     * same pile the bunkhouse is waiting on has spent the bunkhouse. Both works
     * held a flat reserve of {@link LightPlanner#TIMBER_KEPT_FOR_BUILDING} against
     * exactly this, and thirty-two planks is nothing beside a house: two founding
     * parties were measured stalled — one at four people and nine buildings after
     * a thousand steps, one at one field and seven people after fifteen hundred —
     * with timber in single figures and fourteen hundred logs felled.
     *
     * <p>So the reserve is the job itself: what the head of the queue still owes,
     * read off its remaining work at {@link BuildPlanner#WOOD_PER_WORK}, plus the
     * job behind it when that one is {@link #CHEAP_ENOUGH_TO_HOLD_FOR}. A lamp or
     * a hedge that would dip into it is refused, and {@code /civ info}'s lamps and
     * dressing lines say so by name.
     *
     * <p>Nought for a job that is free to build — a producer, or a survival
     * building in a famine — because the timber those never spend is timber
     * nothing is waiting on. That is {@code Settlement.isFreeToBuild}'s rule,
     * written out here rather than shared, because it is the only thing in the
     * simulation outside the queue that has to ask.
     */
    public static int timberOwedToTheQueue(Settlement settlement) {
        List<BuildTask> queue = settlement.buildQueue();
        if (queue.isEmpty()) {
            return 0;
        }
        int owed = timberOwedFor(settlement, queue.getFirst());
        if (queue.size() > 1) {
            int next = timberOwedFor(settlement, queue.get(1));
            if (next <= CHEAP_ENOUGH_TO_HOLD_FOR) {
                owed += next;
            }
        }
        return owed;
    }

    /** What one queued job still owes the stores in timber. */
    public static int timberOwedFor(Settlement settlement, BuildTask task) {
        if (BuildPlanner.PRODUCER_OF.containsValue(task.blueprintId())
                || (FoodPlanner.isSurvivalBuilding(task.blueprintId())
                    && settlement.isStarving())) {
            return 0;   // this one is free; see Settlement.isFreeToBuild
        }
        int left = Math.max(0, task.requiredWork() - task.progress());
        return BuildPlanner.WOOD_PER_WORK * left;
    }

    /**
     * What the timber is being held for, for the line in {@code /civ info}.
     *
     * <p>The head of the queue by name, because "timber 5 under 168" on its own
     * is a number a player can do nothing with and "held for the bunkhouse" is a
     * sentence they can go and look at.
     */
    public static String jobTheTimberIsHeldFor(Settlement settlement) {
        List<BuildTask> queue = settlement.buildQueue();
        return queue.isEmpty() ? null : queue.getFirst().blueprintId();
    }

    /**
     * The works this town may put a spare hand on right now.
     *
     * <p>Everything it has outstanding when there is nothing to build, and the
     * roads alone while there is. That second case is the interleave, and it
     * exists because "shelter and stores before roads and walls" was written for
     * a queue that empties. A settlement's stage program orders the next building
     * on the same step the last one is struck off, so from the moment a camp is
     * founded until the day it stops growing its queue is empty for at most one
     * step in ten — and a watched town, which is any town you are standing in,
     * has no clock to fall back on. The roads were not merely late; they arrived
     * at one stretch for every two the town planned, which is a gap that widens
     * for ever.
     */
    public static List<Worksite> availableTo(Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            return of(settlement);
        }
        // The lighting interleaves with building for the same reason the roads do,
        // and with a better one: a lamp is a plank and an afternoon, and a town
        // that would not spare a hand for one until its queue emptied is a town
        // that is dark on every night it is growing -- which is every night.
        //
        // Behind the roads, though, and not in front of them. A lamp is planned
        // onto the verge of an opened street, so a lighting crew that outranked
        // the paving crew would be waiting on streets that were no longer being
        // opened -- measured, and it cost a village thirty of its fifty-eight
        // stretches. Roads first is also self-limiting in a way the other order is
        // not: a network is a finite thing a town finishes, and from the day it
        // does, every spare hand this list has goes to the lamps.
        return List.of(new RoadWork(), new LightWork(settlement));
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
     * work in that queue too; then {@link #availableTo} in order, taking the
     * first work with a job in a loaded chunk that the town can start and has the
     * materials for. The platform adds two refusals this cannot see — a route
     * nothing can path and growth in the way — and both of those only ever mean
     * the crew is one pass later than this says.
     *
     * <p>"The build queue first" is a rule about the <em>last</em> pair of hands
     * rather than about all of them. A town raising something keeps
     * {@link #HANDS_KEPT_ON_BUILDINGS} on it and may send whatever is left to the
     * roads — and only to the roads. A settlement with one builder therefore
     * behaves exactly as it always did: the house, and nothing else.
     *
     * <p>{@code PerimeterPlanner} asks a blunter question of its own and is left
     * to: a wall must never go up beside a builder who is standing right there,
     * so it stands aside for any hands at all rather than for the hands the
     * foreman would actually send. The cost is a wall that waits while the town
     * builds, which is the priority this list states anyway.
     */
    public static Worksite handsAreOn(Settlement settlement, WorldBridge bridge) {
        if (!canSpareAHand(settlement)) {
            return null;   // shelter and stores before roads and walls
        }
        for (Worksite work : availableTo(settlement)) {
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

    /** Whether anybody in this town is both able to build and standing in the world. */
    private static boolean hasSpareHands(Settlement settlement) {
        return workingHands(settlement) > 0;
    }

    /**
     * Whether the town has a pair of hands it could send to a public work.
     *
     * <p>Any of them when there is nothing queued; one more than the queue keeps
     * while there is. See {@link #HANDS_KEPT_ON_BUILDINGS}.
     */
    public static boolean canSpareAHand(Settlement settlement) {
        int hands = workingHands(settlement);
        if (hands == 0) {
            return false;
        }
        return settlement.buildQueue().isEmpty() || hands > HANDS_KEPT_ON_BUILDINGS;
    }

    /**
     * How many people here can build and are standing in the world.
     *
     * <p>Embodied, because a public work is walked to: somebody who exists only
     * on the roster cannot carry a plank or tread a road, and counting them is
     * how the clock came to stand aside for a crew that was never coming.
     */
    public static int workingHands(Settlement settlement) {
        int hands = 0;
        for (Person person : settlement.residents()) {
            if (settlement.laborsAs(person, Profession.BUILDER)
                    && person.isEmbodied() && !person.isTooWeakToWork()) {
                hands++;
            }
        }
        return hands;
    }

    /**
     * Whether the clock should leave this work to the town's own people.
     *
     * <p>Two ways it should. The crew is on it now, which is
     * {@link #handsAreOn}; or the crew is raising a house <em>and every pair of
     * hands is on it</em>, in which case they are coming back — a build queue is
     * a finite thing a town works through, the foreman defers to it by design,
     * and a clock that opened a street while the builders were busy would be
     * opening it in front of the very people who were on their way to open it.
     *
     * <p>The second clause once said only "the crew is raising a house", and that
     * is the sentence a founded camp's roads died of. A town that can spare a
     * hand for the street already has one walking out there, so if the foreman
     * has nonetheless chosen something else — an unread chunk at the near end of
     * the run, most often — then nobody is coming to that stretch and the clock
     * is all it has.
     *
     * <p>What it does <em>not</em> wait for is a crew on a public work above this
     * one. That is the distinction the reordering forced, and it is a real one: a
     * town with a ring still going up has builders who will not reach the streets
     * for as long as the wall takes, and waiting for them leaves a street opened
     * by nobody at all. The old rule made no such distinction because it did not
     * have to — the roads were the first work a crew was offered, so "is anybody
     * here a builder" and "is anybody coming to this street" were the same
     * question.
     *
     * <p>And it does not wait for a crew that is not being <em>offered</em> this
     * work at all. The second clause below — the build queue holding the last
     * pair of hands — reads as "they are busy and will be back", and that is only
     * true of a work {@link #availableTo} will hand them when they are. For a
     * work it will not, there is no coming back: the queue of a growing town is
     * never empty for long enough, so "they are busy" means "for ever". That is
     * the whole of how a town came to stand undressed for its entire life with
     * every gate open — the same mistake this method's own third paragraph
     * describes, made one work further down the list.
     */
    public static boolean leaveItToTheCrew(Settlement settlement, WorldBridge bridge,
                                           Worksite work) {
        Worksite chosen = handsAreOn(settlement, bridge);
        if (chosen != null && chosen.getClass() == work.getClass()) {
            return true;
        }
        if (settlement.buildQueue().isEmpty() || !hasSpareHands(settlement)
                || canSpareAHand(settlement)) {
            return false;
        }
        // They are busy -- but "and will be back" is only true of a work they
        // will be offered when they are. Asked last because it is the dear
        // question and the three cheap ones above settle it nearly every time.
        return isOffered(settlement, work);
    }

    /**
     * Whether a spare hand would currently be offered this work at all.
     *
     * <p>{@link #availableTo}'s own list, asked rather than restated. Restating
     * it is how the roads and the wall came to disagree about which of them a
     * town was building, and a membership test spelled out here would be the
     * same rule written down twice again.
     */
    private static boolean isOffered(Settlement settlement, Worksite work) {
        for (Worksite offered : availableTo(settlement)) {
            if (offered.getClass() == work.getClass()) {
                return true;
            }
        }
        return false;
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
            // Only the stretches the town owes. A planned street that fronts
            // nothing, carries nobody to the square, and closes no circuit that
            // has filled up is a line on a drawing -- sending a crew to pave it
            // is how a ring road came to be finished before the fifteen houses
            // inside it. Asked once for the whole network rather than per run:
            // this loop runs every step of every settlement.
            java.util.Set<Integer> owed = com.civilization.sim.settlement.PathPlanner
                    .owedStretches(settlement);
            for (int i = 0; i < segments.size(); i++) {
                if (paths.isOpened(i) || paths.isUnwalkable(i) || !owed.contains(i)) {
                    continue;
                }
                return i;
            }
            return -1;
        }

        /**
         * How many runs the town still owes the paving.
         *
         * <p>{@link #nextRun}'s loop counted instead of stopped at the first
         * hit. Asked by anything that has to know whether the paving is
         * <em>behind</em> rather than whether it is finished — which turns out
         * to be a different question, and one nobody was asking.
         *
         * <p>A growing town plans new streets as fast as it opens old ones, so
         * "is there an unopened owed run" is true for the whole life of every
         * healthy settlement. Anything that waited on that answer waited for
         * ever; see {@code Furnishings.whyNotStarting}, whose dressing did.
         */
        public int owedRuns(Settlement settlement) {
            PathNetwork paths = settlement.paths();
            if (paths == null) {
                return 0;
            }
            List<PathNetwork.Segment> segments = paths.segments();
            java.util.Set<Integer> owed = com.civilization.sim.settlement.PathPlanner
                    .owedStretches(settlement);
            int behind = 0;
            for (int i = 0; i < segments.size(); i++) {
                if (!paths.isOpened(i) && !paths.isUnwalkable(i) && owed.contains(i)) {
                    behind++;
                }
            }
            return behind;
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
            return true;   // a track is trodden, not bought; the labor is the cost
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
     * however honorable its history. So the old posts come up the same way they
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

    /**
     * Raising the street lighting, one lamp at a time and in the order it was
     * planned.
     *
     * <p>The wall's shape exactly, and deliberately: a lamp is a post somebody
     * carries out of the storehouse and plants on a verge, and the only differences
     * are where the positions come from ({@code LightPlanner} rather than
     * {@code Perimeter}) and what is hung on top of it. Same seam, same loader,
     * same charge-at-the-station rule.
     *
     * <p>Costs the standard and the light — see {@code LightStyle}, which is where
     * a people's idiom and its price live together. The standard is the carried
     * material and leaves the books at the shelves; the light is charged at the
     * verge, which is the wall's arrangement with its coin.
     */
    public static final class LightWork implements Worksite {

        /**
         * What this people's lamp standard is made of, resolved when the work is
         * handed out.
         *
         * <p>{@link Worksite#material} takes no settlement, because for every other
         * work what a station is made of is a constant — a plank, or nothing. A
         * lamp's standard is not: it is whatever this people build posts out of, and
         * the burgher's is stone where everybody else's is timber. Rather than widen
         * the interface for one case and make every other work, the loader and the
         * foreman carry an argument none of them wants, the style travels with the
         * work. {@link PublicWorks#of} and {@link PublicWorks#availableTo} both have
         * the settlement in hand already.
         */
        private final LightStyle style;

        /** The lighting of a particular town, in that town's own idiom. */
        public LightWork(Settlement settlement) {
            this(LightPlanner.styleOf(settlement));
        }

        public LightWork(LightStyle style) {
            this.style = style;
        }

        /** The plainest lamp there is, for a caller with no town to ask. */
        public LightWork() {
            this(LightStyle.FENCE_TORCH);
        }

        @Override
        public String name() {
            return "lights";
        }

        @Override
        public SimPos nextStation(Settlement settlement) {
            LightPlanner.Lamp lamp = LightPlanner.next(settlement);
            return lamp == null ? null : lamp.at();
        }

        /** The standard: a plank, or a block of stone for a people who build in it. */
        @Override
        public String material() {
            return style.postResource();
        }

        /**
         * The light itself. The post is already in the builder's hands.
         *
         * <p>{@code LightPlanner.payForLamp} is the whole price and is what the
         * clock pays; this is what is left of it once the standard has come off the
         * books at the storehouse.
         */
        @Override
        public boolean pay(Settlement settlement) {
            return LightPlanner.payForTheLightOnly(settlement);
        }

        @Override
        public void completeOne(Settlement settlement, boolean worked) {
            // Whether or not a block went in, exactly as the wall does. A verge
            // that refuses a post -- a lamp planned onto somebody's new doorstep,
            // a column that turned out to be water -- is a lamp the town is not
            // going to have, and a crew that would not count it would stand there
            // for ever.
            settlement.setLightsRaised(settlement.lightsRaised() + 1);
        }

        @Override
        public boolean isWorthStarting(Settlement settlement) {
            return LightPlanner.worthStarting(settlement);
        }
    }

    /**
     * Taking the wood down inside the town's own streets.
     *
     * <p>Free, and that is not a shortcut. A tree is felled with an axe and the
     * logs go on the shelves, so the work pays for itself: the only thing it takes
     * from the town is somebody's afternoon, exactly as a road does. See
     * {@code InteriorClearing} for which ground, and {@code Woodcut} for what a
     * swing at a cell actually brings down.
     *
     * <p>A station is a cell of ground rather than a tree, because the simulation
     * does not know where trees are and must not guess. The platform finds what is
     * standing in the cell; a cell with nothing left in it is a station done.
     */
    public static final class ClearingWork implements Worksite {

        @Override
        public String name() {
            return "clearing";
        }

        @Override
        public SimPos nextStation(Settlement settlement) {
            return InteriorClearing.next(settlement);
        }

        @Override
        public boolean pay(Settlement settlement) {
            return true;   // an axe and an afternoon; the logs come back
        }

        @Override
        public void completeOne(Settlement settlement, boolean worked) {
            settlement.setInteriorCleared(settlement.interiorCleared() + 1);
        }
    }

    /**
     * Standing the town's own dressing on its free ground.
     *
     * <p>The yards, the hedges, the woodpiles, the square: everything in
     * {@code Furnishings}, worked through one piece at a time by exactly the
     * machinery the lamps go up by. {@code FurnishingLayer} is its platform half.
     *
     * <p><strong>It carries nothing.</strong> {@link Worksite#material} is null,
     * which for every other work means "this costs only labor" and here means
     * something slightly different: a piece of dressing costs three resources at
     * once — fence timber, paving stone and saplings, in proportions that differ
     * from one kind of piece to the next — and there is no single load a builder
     * could fetch that would cover a square and
     * an orchard both. So the whole price is charged at the piece, with somebody
     * standing there ready to raise it, which is where the wall charges its coin.
     * The cost of that choice is that a dressing crew walks back to the storehouse
     * for nothing; they do not, because they were never sent for anything.
     */
    public static final class DressingWork implements Worksite {

        @Override
        public String name() {
            return "dressing";
        }

        @Override
        public SimPos nextStation(Settlement settlement) {
            Furnishings.Furnishing piece = Furnishings.next(settlement);
            return piece == null ? null : piece.at();
        }

        @Override
        public boolean pay(Settlement settlement) {
            Furnishings.Furnishing piece = Furnishings.next(settlement);
            return piece != null && Furnishings.payFor(settlement, piece.piece());
        }

        @Override
        public void completeOne(Settlement settlement, boolean worked) {
            // Whether or not a block went in, exactly as the wall and the lamps
            // do. A patch of ground that refuses a fence -- a yard planned onto
            // what turned out to be a pond -- is a yard the town is not going to
            // have, and a crew that would not count it would stand there for ever.
            settlement.setPiecesRaised(settlement.piecesRaised() + 1);
        }

        @Override
        public boolean isWorthStarting(Settlement settlement) {
            return Furnishings.worthStarting(settlement);
        }
    }
}
