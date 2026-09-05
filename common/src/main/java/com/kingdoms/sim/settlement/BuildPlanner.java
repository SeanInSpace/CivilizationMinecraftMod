package com.kingdoms.sim.settlement;

import com.kingdoms.sim.culture.Layouts;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Decides what a settlement builds next, and where.
 *
 * <p>Two independent questions, kept separate on purpose:
 * <ul>
 *   <li><strong>What</strong> — the settlement builds the most important thing it
 *       is currently short of. See {@link #chooseNext}.</li>
 *   <li><strong>Where</strong> — plots are handed out along expanding rings around
 *       the center, in a fixed order. See {@link #plotFor}.</li>
 * </ul>
 *
 * <p>Everything here is deterministic. Same settlement state in, same decision out,
 * every time — which is what lets the tests assert on specific choices, and what
 * will let two players on a server see the same town.
 *
 * <p>Plain-English write-up of these rules: {@code BUILD_DECISIONS.md}.
 */
public final class BuildPlanner {

    /** Plot width assumed for anything that does not declare one. */
    public static final int DEFAULT_PLOT_SPAN = 11;

    /**
     * Bare ground left between two plot claims, over and above the claims.
     *
     * <p>Nought, and that is the whole of it. A plot is already a building's
     * walls plus the doorstep ring {@link BuildingSizes#APRON} clears round
     * them, so two claims that touch are two doorsteps that touch and neither
     * building is short of ground. A block on top of that was a third apron
     * belonging to nobody.
     *
     * <p>What it bought was three blocks of nothing between every pair of walls
     * in every town — the arithmetic is in {@link #plotsOverlap} — and the plan
     * offered frontage so much coarser than that the floor was never reached
     * anyway. Measured on a grown high street, walls stood a median five and a
     * tightest three apart. At nought the floor is two, and the plan is pitched
     * to reach it.
     *
     * <p>The margin this gives up is against a building drawn larger than the
     * table says it is, and that fault is now watched for where it happens:
     * {@code BlueprintPlacer} logs SIZE MISMATCH when a builder draws something
     * other than what was declared. A block of slack on every plot in the mod is
     * an expensive way to not have that check.
     */
    public static final int PLOT_GAP = 0;

    /** Keeps the claim boundary a little beyond the outermost building. */
    static final int CLAIM_MARGIN = 8;

    /**
     * Materials an unwatched build spends per builder-step.
     *
     * <p>The observed path charges per block laid, which it can do because it has
     * the block plan. Away from a watcher there is no plan — the chunk is not even
     * loaded — so the cost is estimated from the work instead. The numbers are set
     * to land near what a cabin actually costs, so a town cannot out-build its
     * stores simply by being unobserved.
     */
    public static final int WOOD_PER_WORK = 4;
    public static final int STONE_PER_WORK = 2;

    /**
     * How many plots a settlement will look at before it settles for one.
     *
     * <p>Bounded on purpose: a town ringed by water or cliffs must still build
     * something rather than searching forever. Past this it takes what it can get,
     * and the excavation deals with whatever that turns out to be.
     */
    public static final int PLOT_ATTEMPTS = 96;

    /** Half-span probed when judging a plot, before the real building is chosen. */
    public static final int PLOT_PROBE_RADIUS = 6;

    /**
     * How much ground a building of this id takes, walls and cleared shelf both.
     *
     * <p>Falls back through the level suffix, so {@code house_l2} is sized as a
     * house — the catalog span already allows for the levels a building may be
     * raised to, precisely so that improving one never has to find new ground.
     */
    /**
     * How far a plot may fall and still be worth leveling rather than refusing.
     *
     * <p>Beyond this a site is a hillside, and a town that flattened it would be
     * quarrying. Within it, the fall is a dip or a hummock and a few barrows of
     * earth make it a building plot — which is what a settlement actually does
     * with awkward ground, and what refusing everything uneven has been costing:
     * plots pushed to the outskirts, doors stranded from roads, a town that got
     * smaller the better its judgment became.
     */
    public static final int LEVELABLE_FALL = 8;

    /**
     * The earth a town must hold before it will level a plot.
     *
     * <p>Counted from the fill actually wanted, so a shallow dip is cheap and a
     * deep one is not. A town with nothing dug yet levels nothing, which is
     * correct: its first buildings go on ground that was already flat, and the
     * spoil from those foundations pays for the next.
     */
    public static int earthToLevel(SimPos plot, int span,
                                   com.kingdoms.sim.platform.WorldBridge ground) {
        int[] heights = heightsAcross(plot, span, ground);
        if (heights.length == 0) {
            return 0;
        }
        // The median is the bench, because leveling to it moves the least earth
        // of any height you could choose.
        int bench = heights[heights.length / 2];
        // And the price is everything that has to MOVE, cut as well as fill.
        // Counting only the fill read zero for a plot that is mostly hollow --
        // the median sits on the floor of the dip, nothing is below it, and a
        // bowl came back free. Spoil from a cut still has to be barrowed away
        // and a fill still has to be barrowed in; a town pays for both.
        int moved = 0;
        for (int height : heights) {
            moved += Math.abs(height - bench);
        }
        // One sample stands for the ground between samples, so the barrows the
        // town pays for are the barrows the site actually wants.
        return moved * SAMPLE_SPACING * SAMPLE_SPACING;
    }

    /**
     * How far the ground falls across a plot, measured on its bulk.
     *
     * <p>Twentieth to eightieth percentile, the same measure siting uses, so a
     * single boulder does not condemn a shelf.
     */
    public static int fallAcross(SimPos plot, int span,
                                 com.kingdoms.sim.platform.WorldBridge ground) {
        int[] heights = heightsAcross(plot, span, ground);
        if (heights.length == 0) {
            return 0;
        }
        return heights[(heights.length * 4) / 5] - heights[heights.length / 5];
    }

    private static int[] heightsAcross(SimPos plot, int span,
                                       com.kingdoms.sim.platform.WorldBridge ground) {
        int half = Math.max(1, span / 2);
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        for (int dx = -half; dx <= half; dx += SAMPLE_SPACING) {
            for (int dz = -half; dz <= half; dz += SAMPLE_SPACING) {
                heights.add(ground.groundHeight(
                        new SimPos(plot.x() + dx, plot.y(), plot.z() + dz)));
            }
        }
        java.util.Collections.sort(heights);
        int[] sorted = new int[heights.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = heights.get(i);
        }
        return sorted;
    }

    /** How far apart the ground is sampled when weighing a plot. */
    private static final int SAMPLE_SPACING = 3;

    public static int plotSpanOf(String blueprintId, List<BuildingType> catalogue) {
        String base = baseIdOf(blueprintId);
        if (base.equals(ACCESS_STAIRS)) {
            // Steps are a path to a door, not a plot. They are built hard against
            // the building they serve, on purpose, and must neither be pushed away
            // from it nor push anything else away from themselves.
            return 1;
        }
        for (BuildingType type : catalogue) {
            if (type.id().equals(base)) {
                return type.plotSpan();
            }
        }
        return DEFAULT_PLOT_SPAN;
    }

    /** Whether this is a plot that must be kept clear of other plots at all. */
    public static boolean holdsGround(String blueprintId) {
        return !baseIdOf(blueprintId).equals(ACCESS_STAIRS);
    }

    /**
     * Whether two plots, each a square about its own origin, foul one another.
     *
     * <p>Squares rather than the true rectangles because buildings are turned to
     * face the center, and a turned building swaps its width and depth. A plot
     * that only fitted at one rotation is not a plot.
     *
     * <p><strong>Two blocks between walls, whatever the two buildings are.</strong>
     * That falls out of the arithmetic rather than being aimed at. A span is the
     * building's longer side plus two aprons and every side is odd, so
     * {@code span / 2} is half that side plus one; the closest a pair may stand is
     * {@code reach + 1}, and taking the two half-sides and the origin block off
     * that leaves {@code 2 * APRON} however big the two are. A cottage beside a
     * cottage and a library beside a hall both leave their doorsteps touching,
     * which is what a street of buildings looks like.
     *
     * <p>The longer side, because a plot is a square and a building is turned to
     * face its street. So a building that is not square stands further off across
     * its narrow way — a compound is nine wide and seventeen deep and keeps a
     * seventeen's worth of ground on both — and a kind that declares
     * {@code ROOM_TO_SPARE} keeps that on top again, which is the point of
     * declaring it. Two is the floor everything shares, not a promise about every
     * pair of walls.
     */
    public static boolean plotsOverlap(SimPos a, int spanA, SimPos b, int spanB) {
        int reach = spanA / 2 + spanB / 2 + PLOT_GAP;
        return Math.abs(a.x() - b.x()) <= reach && Math.abs(a.z() - b.z()) <= reach;
    }

    /**
     * Which way a building on this plot should face, in quarter turns clockwise.
     *
     * <p>Structures are drawn with their door to the south, so zero means "as
     * drawn". Everything turns to put its door toward the town center, which is
     * the difference between a village and a field of identical sheds all facing
     * the same way.
     *
     * <p>The dominant axis wins. A building on a diagonal faces whichever of the
     * two directions it is more squarely off, which is what a builder standing
     * there would choose.
     */
    public static int facingToward(SimPos plot, SimPos centre) {
        int dx = centre.x() - plot.x();
        int dz = centre.z() - plot.z();
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz >= 0 ? 0 : 2;   // center to the south: as drawn; north: half turn
        }
        return dx < 0 ? 1 : 3;        // center west: a quarter clockwise; east: three
    }

    /**
     * How far an improvement is outranked by building something new.
     *
     * <p>Improvements compete for the same builders rather than waiting for a town
     * to run out of things it wants — which never happens, because housing and
     * farms scale with a population that keeps growing. Waiting for idleness meant
     * no building was ever improved at all.
     *
     * <p>Set so a house improvement sits about level with a workshop: new housing
     * still beats it while anyone is homeless, but comforts do not.
     */
    public static final int UPGRADE_PENALTY = 30;

    /** How far a building can be improved. Level one is the plain version. */
    public static final int MAX_LEVEL = 3;

    /**
     * The blueprint id for a given level of a building.
     *
     * <p>Level one is the plain id; above that the level is a suffix. Putting it in
     * the id rather than in a parameter means a datapack can supply
     * {@code house_l2.nbt} and have it used, with no code involved.
     */
    public static String levelledId(String baseId, int level) {
        return level <= 1 ? baseId : baseId + "_l" + level;
    }

    /** The level an id names, and the id without it. */
    public static int levelOf(String blueprintId) {
        int mark = blueprintId.lastIndexOf("_l");
        if (mark < 0) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(blueprintId.substring(mark + 2)));
        } catch (NumberFormatException notLevelled) {
            return 1;
        }
    }

    public static String baseIdOf(String blueprintId) {
        int mark = blueprintId.lastIndexOf("_l");
        if (mark < 0 || levelOf(blueprintId) == 1) {
            return blueprintId;
        }
        return blueprintId.substring(0, mark);
    }

    /**
     * How much storehouse a town has, counting a bigger one as more.
     *
     * <p>Levels, not buildings. Capacity used to be a count of stores, which
     * meant a storehouse raised to level two held exactly what it did before —
     * so improving one gained the town nothing at all, and "improve the lowest
     * first" was an even distribution of no effect. A town with three plain
     * stores measures three either way, so nothing moves until somebody
     * actually improves something.
     */
    public static int storeStrength(Settlement settlement) {
        int strength = 0;
        for (Building standing : settlement.buildings()) {
            if (standing.isStore()) {
                strength += Math.max(1, standing.level());
            }
        }
        return strength;
    }

    /**
     * The building most worth improving, or empty when nothing is.
     *
     * <p>Only ever consulted once a town has everything it wants — a second house
     * beats a grander town hall while anybody is still homeless. Among the rest the
     * lowest level goes first, so a town improves evenly rather than raising one
     * showpiece and leaving the rest as huts.
     */
    public static Optional<Building> chooseUpgrade(Settlement settlement,
                                                   List<BuildingType> catalogue) {
        Building best = null;
        for (Building standing : settlement.buildings()) {
            // Not gated on being materialized: a finished building is real to the
            // simulation whether or not its blocks exist yet, and an unwatched town
            // never materializes anything — so that test meant no town out of sight
            // ever improved a single building.
            if (standing.level() >= MAX_LEVEL) {
                continue;
            }
            boolean known = catalogue.stream()
                    .anyMatch(type -> type.id().equals(baseIdOf(standing.blueprintId())));
            if (!known) {
                continue;   // repair flights and the like are not improved
            }
            if (best == null || standing.level() < best.level()) {
                best = standing;
            }
        }
        return Optional.ofNullable(best);
    }

    /** How urgent improving this building is, against the priority of new work. */
    public static int upgradePriority(Settlement settlement, List<BuildingType> catalogue,
                                      Building standing) {
        String baseId = baseIdOf(standing.blueprintId());
        return catalogue.stream()
                .filter(type -> type.id().equals(baseId))
                .mapToInt(BuildingType::priority)
                .findFirst()
                .orElse(0) - UPGRADE_PENALTY;
    }

    /** What improving to this level costs, over the plain build. */
    public static int upgradeWork(BuildingType type, int toLevel) {
        return type.workCost() * toLevel;
    }

    /**
     * Which building lets a town make a thing it has run out of.
     *
     * <p>Food joined wood and stone after a founding party starved with full
     * timber stores: there was no building it could reach for when the larder
     * emptied, so running out of bread was simply the end.
     *
     * <p>Putting the farm here is not free. Everything in this table is exempt
     * from material cost while it is built — see {@code payForProgress} and
     * {@code BlueprintPlacer.canPayFor} — which now makes every farm a town
     * raises cost nothing, not only the desperate one. That is the trade, taken
     * deliberately: a town with an empty larder has nothing to sell, nothing to
     * spend and no way to earn, so a farm it must pay for is a farm it can never
     * have. Charging for farms again means first giving a starving town some
     * other way to get one.
     */
    public static final Map<String, String> PRODUCER_OF = Map.of(
            TownStores.WOOD, "kingdoms:lumber_camp",
            TownStores.STONE, "kingdoms:mine",
            TownStores.FOOD, "kingdoms:farm");

    /**
     * Who works each producer. Every entry in {@link #PRODUCER_OF} needs one.
     *
     * <p>The staffing table cannot be asked: it does not want a miner until a
     * mine already stands ({@code ProfessionNeed.appliesTo}), which is the whole
     * fault — the town builds the tool of its own rescue and has nobody to pick
     * it up. So the producer names its own trade and {@link #requestProducer}
     * hires for it directly.
     */
    public static final Map<String, Profession> TRADE_OF = Map.of(
            "kingdoms:lumber_camp", Profession.LUMBERJACK,
            "kingdoms:mine", Profession.MINER,
            "kingdoms:farm", Profession.FARMER);

    /**
     * Who gives somebody up first when two trades are equally staffed.
     *
     * <p>Only a tiebreak — the largest cohort is asked first whatever it is,
     * because that is where the slack actually is. Idlers are not here because
     * they always go first and give up nothing.
     */
    private static final List<Profession> DONOR_ORDER = List.of(
            Profession.TRADER, Profession.SHEPHERD, Profession.SMITH, Profession.GUARD,
            Profession.MINER, Profession.LUMBERJACK, Profession.FARMER, Profession.BUILDER);

    /** Builder-steps for a producer ordered out of turn; the catalog cost is used when known. */
    public static final int PRODUCER_WORK = 30;

    /** Blueprint for a run of steps up to a doorway nobody can reach. */
    public static final String ACCESS_STAIRS = "kingdoms:stairs";

    /** Builder-steps per block of climb, so a taller flight is a longer job. */
    public static final int STAIR_WORK_PER_BLOCK = 3;

    /**
     * Orders the building that would fix a shortage, ahead of everything else,
     * and puts somebody in it in the same breath.
     *
     * <p>This is what stops limited supply becoming a deadlock. A town short of
     * timber wants a house it cannot pay for; the house is the highest-priority
     * thing it lacks, so it would sit on that job forever. Noticing the shortage
     * and going to build a lumber camp instead is both the way out and the
     * obviously sensible thing for a town to do.
     *
     * <p>Ordering it was only ever half the job. A town watched live built its
     * hall, ran dry, bootstrapped a mine, staffed it with nobody and starved
     * beside it: the staffing table will not want a miner until a mine actually
     * stands, and by the time one did the idlers it would have retrained had
     * been spent on higher-priority trades. So the worker is chosen here, when
     * the building is ordered rather than when it is finished — which is also
     * how they come to be trained and walking to the site while the walls are
     * still going up.
     *
     * <p>And if nobody can be spared, nothing is ordered at all. A producer no
     * resident will ever work is not a rescue, it is a hole in the ground the
     * builders are diverted into. The refusal is logged, and callers ask again
     * every step the shortage lasts, so the order goes in the moment a resident
     * comes free.
     *
     * @return true if a producer was ordered by this call
     */
    public static boolean requestProducer(Settlement settlement, String resource, long step) {
        return requestProducer(settlement, resource, step, null);
    }

    /**
     * @param bridge the world to ask about ground, or null when there is none.
     *               Only used to keep an urgent build out of open water.
     */
    public static boolean requestProducer(Settlement settlement, String resource, long step,
                                          com.kingdoms.sim.platform.WorldBridge bridge) {
        String producer = PRODUCER_OF.get(resource);
        if (producer == null) {
            return false;
        }
        for (Building standing : settlement.buildings()) {
            if (standing.blueprintId().equals(producer)) {
                return false;   // already have one; the shortage is a real shortage
            }
        }
        for (BuildTask queued : settlement.buildQueue()) {
            if (queued.blueprintId().equals(producer)) {
                return false;
            }
        }
        // Hands are settled before ground is: takeNextPlot spends a ring slot for
        // good, and an order refused for want of a worker must not also cost the
        // town a plot it never built on.
        Profession trade = TRADE_OF.get(producer);
        if (trade == null) {
            sayOnce(settlement, step, "Out of " + resource + " but nobody knows how to run a "
                    + readableName(producer));
            return false;
        }
        boolean alreadyStaffed = settlement.residents().stream()
                .anyMatch(resident -> resident.profession() == trade);
        Person hands = alreadyStaffed ? null : sparestResident(settlement);
        if (!alreadyStaffed && hands == null) {
            sayOnce(settlement, step, "Out of " + resource + " and nobody to spare for a "
                    + readableName(producer) + " — none is ordered until there is");
            return false;
        }
        int work = settlement.catalogue().stream()
                .filter(type -> type.id().equals(producer))
                .mapToInt(BuildingType::workCost)
                .findFirst()
                .orElse(PRODUCER_WORK);
        // A real plot, and one nobody else is standing on. This used to take the
        // next ring slot unchecked, which is how a lumber camp came to be ordered
        // through the side of the town hall — an urgent build is still a build,
        // and gets the same ground rules as any other.
        int span = plotSpanOf(producer, settlement.catalogue());
        SimPos plot = settlement.takeNextPlot(span, bridge);
        BuildTask ordered = new BuildTask(producer, plot, work);
        ordered.setFacing(settlement.arrangement().facingFor(settlement.centre(), plot));
        settlement.enqueueUrgent(ordered);
        String announcement = "Out of " + resource + " — work starts on a " + readableName(producer);
        if (hands != null) {
            hands.setProfession(trade);
            announcement += ", and " + hands.name() + " takes up "
                    + trade.name().toLowerCase(Locale.ROOT);
        }
        settlement.logEvent(step, announcement);
        return true;
    }

    /**
     * The resident a town can most afford to lose to a new trade, or null if it
     * cannot afford to lose one at all.
     *
     * <p>An idler first, because retraining one gives up nothing. After that the
     * largest cohort hands somebody over, since that is where the slack is — a
     * town of ninety-seven farmers can send one down the mine and never feel it.
     *
     * <p>Some last workers are never taken; see {@link #irreplaceable}. Where
     * those are all a town has, this returns null and the caller says so out
     * loud rather than ordering a building nobody will ever work in.
     *
     * <p>Deliberately not {@code JobPlanner.retrainOne}: that fills whichever
     * trade the staffing table most wants, which is precisely not this one, and
     * it refuses to draw from a profession sitting exactly at quota — the
     * politeness that left the bootstrapped mine empty. The two rules want
     * consolidating once both have settled.
     */
    private static Person sparestResident(Settlement settlement) {
        Map<Profession, Integer> heads = new EnumMap<>(Profession.class);
        for (Person resident : settlement.residents()) {
            if (resident.profession() == Profession.IDLER) {
                return resident;
            }
            heads.merge(resident.profession(), 1, Integer::sum);
        }
        Profession give = null;
        int largest = 0;
        for (Profession candidate : DONOR_ORDER) {
            int cohort = heads.getOrDefault(candidate, 0);
            if (cohort > largest && !(cohort == 1 && irreplaceable(settlement, candidate))) {
                largest = cohort;
                give = candidate;
            }
        }
        if (give == null) {
            // Below VILLAGE there are no donor trades to raid: the party is
            // pioneers. One of them takes the post — a producer shortage is a
            // shortage event, and reassignment on shortage is exactly what the
            // founding design permits. Without this a camp refuses to order the
            // lumber camp forever and stalls at HOMESTEAD with a granary it
            // cannot pay for.
            return settlement.residents().stream()
                    .filter(resident -> resident.profession() == Profession.PIONEER)
                    .findFirst()
                    .orElse(null);
        }
        Profession chosen = give;
        return settlement.residents().stream()
                .filter(resident -> resident.profession() == chosen)
                .findFirst()
                .orElse(null);
    }

    /**
     * Whether a town with exactly one of these must keep them.
     *
     * <p>The last builder, because retraining them means the rescue just ordered
     * is never raised at all. The last farmer, because the town then starves
     * while it waits for the stone. And the last worker of a producer already
     * standing, because taking them stops the production a previous rescue was
     * ordered to start — and {@link #requestProducer} would never replace them:
     * it refuses outright once the building stands, so an emptied camp stays
     * empty and its resource can never be rescued again.
     */
    private static boolean irreplaceable(Settlement settlement, Profession trade) {
        if (trade == Profession.BUILDER || trade == Profession.FARMER) {
            return true;
        }
        for (Building standing : settlement.buildings()) {
            if (TRADE_OF.get(baseIdOf(standing.blueprintId())) == trade) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records an event unless the town is already saying it.
     *
     * <p>Refusals repeat: callers ask again every step a shortage lasts, several
     * times a second while somebody is watching. A settlement remembers twenty
     * lines, so one repeating refusal would scrub out the founding, the raids
     * and everything else within seconds — a distress signal that erases the
     * evidence is worse than silence.
     */
    private static void sayOnce(Settlement settlement, long step, String message) {
        for (SettlementEvent said : settlement.events()) {
            if (said.message().equals(message)) {
                return;
            }
        }
        settlement.logEvent(step, message);
    }

    /** "kingdoms:lumber_camp" reads as "lumber camp" in an event. */
    private static String readableName(String blueprintId) {
        return blueprintId.substring(blueprintId.indexOf(':') + 1).replace('_', ' ');
    }

    private BuildPlanner() {
    }

    /**
     * Orders steps up to a doorway a resident cannot reach.
     *
     * <p>Houses are planted by geometry, not by surveyors: on a slope, or where
     * the foundation had to pillar up out of a dip, a door can end up above
     * anything a person can climb. Rather than leaving somebody stranded outside
     * their own home forever, the settlement notices and builds them a way up.
     *
     * <p>The job jumps the queue — a family locked out is more pressing than the
     * next workshop — and is refused if a flight is already ordered or standing,
     * so a stuck resident cannot flood the queue with duplicates.
     *
     * @return true if a new order was placed
     */
    public static boolean requestAccessStairs(Settlement settlement, SimPos doorway,
                                              int climb, long step) {
        for (BuildTask queued : settlement.buildQueue()) {
            if (queued.blueprintId().equals(ACCESS_STAIRS) && queued.origin().equals(doorway)) {
                return false;
            }
        }
        for (Building standing : settlement.buildings()) {
            if (standing.blueprintId().equals(ACCESS_STAIRS) && standing.origin().equals(doorway)) {
                return false;
            }
        }
        int work = Math.max(4, climb * STAIR_WORK_PER_BLOCK);
        settlement.enqueueUrgent(new BuildTask(ACCESS_STAIRS, doorway, work));
        settlement.logEvent(step, "Steps ordered up to a door nobody could reach, at " + doorway);
        return true;
    }

    /**
     * Picks the next building, or empty if the settlement wants nothing right now.
     *
     * <p>A type is a candidate when the settlement is big enough for it and has
     * fewer than it wants. Among candidates, highest priority wins; ties go to
     * the larger shortfall <em>as a share of what is wanted</em>, then to
     * whichever id sorts first so the result is stable.
     *
     * <p>The share is what makes the first of a kind matter more than the
     * fourth. Ranked on the raw shortfall, a town wanting five houses with three
     * standing is two short and beats its very first storehouse, which is only
     * one short — so it builds a fourth house while it has nowhere to put
     * anything. Measured proportionally the storehouse is missing all of what
     * it wants and the houses two fifths of theirs, and the storehouse goes
     * first. Nothing else needed changing: this is a tiebreak, and priority
     * still decides everything it has an opinion about.
     */
    public static Optional<BuildingType> chooseNext(Settlement settlement, List<BuildingType> catalogue) {
        int population = settlement.population();

        return catalogue.stream()
                .filter(type -> population >= type.minPopulation())
                .filter(type -> shortfall(settlement, type, population) > 0)
                .max(Comparator
                        .comparingInt(BuildingType::priority)
                        .thenComparingInt((BuildingType type) -> makesSomethingScarce(settlement, type))
                        .thenComparingInt((BuildingType type) -> shareShort(settlement, type, population))
                        .thenComparing(BuildingType::id, Comparator.reverseOrder()));
    }

    /**
     * Below this, a town counts as scarce in something a producer makes.
     *
     * <p>A stack. Enough that a build wanting a few dozen blocks is not already
     * failing, and little enough that a town sitting on nine hundred timber is
     * not told it needs another camp.
     */
    public static final int SCARCE = 64;

    /**
     * Whether this building makes something the town is running out of.
     *
     * <p>The table of wants has always been a fixed list — so many houses per
     * head, one granary, and so on — with no notion that a mine is worth more
     * the morning the stone runs out than it was the day before. Reacting to
     * that was left to {@link #requestProducer}, which shoves a producer to the
     * front of the queue when a build actually fails for want of materials:
     * correct, but a special case bolted beside the table rather than part of
     * how the table is read.
     *
     * <p>This is the milder version of the same idea, and it sits where ties
     * are broken rather than jumping the queue. Priority still outranks it
     * entirely: a town short of stone does not stop building the hall to raise a
     * second mine.
     *
     * @return 1 when it makes something scarce, 0 otherwise — an int so it can
     *         be used as a comparator key alongside the others
     */
    public static int makesSomethingScarce(Settlement settlement, BuildingType type) {
        String base = baseIdOf(type.id());
        for (Map.Entry<String, String> made : PRODUCER_OF.entrySet()) {
            if (made.getValue().equals(base)
                    && settlement.stores().get(made.getKey()) < SCARCE) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * How short of a type the settlement is, per hundred of what it wants.
     *
     * <p>In hundredths rather than a fraction so the comparison stays integer
     * arithmetic and two types that are equally short compare equal rather than
     * on floating-point dust.
     */
    public static int shareShort(Settlement settlement, BuildingType type, int population) {
        int wanted = type.desiredCount(population);
        if (wanted <= 0) {
            return 0;
        }
        return shortfall(settlement, type, population) * 100 / wanted;
    }

    /** How many more of this type the settlement wants than it has. */
    public static int shortfall(Settlement settlement, BuildingType type, int population) {
        return type.desiredCount(population) - settlement.countBuildings(type.id());
    }

    /**
     * Where the nth building goes, for a town that has not said how it builds.
     *
     * <p>The ring arrangement, which is what a settlement with no layout named
     * gets. It used to be spelled out here as well as in {@link Layouts#RING} —
     * the same rings, the same stagger, the same four constants — from before a
     * layout was a thing a town could have. Two copies of one lattice is a rule
     * with two definitions, and the day the ring's spacing was tightened they
     * would have quietly stopped agreeing: a test that names a plot by index
     * through this one and hands it to a settlement that goes through the other
     * would have been refusing ground the town was never offered.
     */
    public static SimPos plotFor(SimPos centre, int index) {
        return Layouts.RING.plotFor(centre, index);
    }

    /** Claim radius needed to keep the given plot inside the settlement's territory. */
    public static int claimRadiusFor(SimPos centre, SimPos plot) {
        return claimRadiusFor(centre, plot, CLAIM_MARGIN);
    }

    /**
     * The same, with the margin a particular arrangement wants.
     *
     * <p>A layout that flings clumps of buildings about needs more room outside
     * its outermost plot than one that grows in tidy rings, or its own outliers
     * end up outside the claim that is supposed to contain them.
     */
    public static int claimRadiusFor(SimPos centre, SimPos plot, int margin) {
        return (int) Math.ceil(centre.horizontalDistance(plot)) + Math.max(0, margin);
    }
}
