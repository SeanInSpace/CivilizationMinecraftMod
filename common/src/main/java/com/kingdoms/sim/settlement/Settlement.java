package com.kingdoms.sim.settlement;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.economy.Economy;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.platform.Sighting;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A single settlement: a roster of people, a centre, a claim radius, and a
 * build queue that advances whether or not anyone is watching.
 */
public final class Settlement {

    public record Id(UUID value) {
        public Id {
            Objects.requireNonNull(value, "value");
        }

        public static Id random() {
            return new Id(UUID.randomUUID());
        }
    }

    private final Id id;
    private final String name;
    private final SimPos centre;
    private int claimRadius;

    /**
     * Which plot the next building takes.
     *
     * <p>Tracked rather than derived from the building count, because a site can
     * now be refused — bedrock in the footprint — and the town has to move on to
     * the next plot instead of proposing the same unbuildable one forever.
     */
    private int nextPlotIndex;

    private final Map<Person.Id, Person> residents = new LinkedHashMap<>();
    private final List<BuildTask> buildQueue = new ArrayList<>();

    /** Everything this settlement has finished building, in completion order. */
    private final List<Building> buildings = new ArrayList<>();

    /** Families. The unit that occupies a house and the unit that grows. */
    private final List<Household> households = new ArrayList<>();

    /** Bounded history — raids, losses, sightings. Oldest entries fall off. */
    public static final int MAX_EVENTS = 20;
    private final ArrayDeque<SettlementEvent> events = new ArrayDeque<>();

    /**
     * Steps the job at the head of the queue may sit without moving before a
     * starving town builds around it.
     *
     * <p>Long enough that a load of stone still on somebody's back is not a
     * stall, short enough that a town does not spend a tenth of its remaining
     * food waiting to notice.
     */
    public static final int STALLED_HEAD_STEPS = 8;

    /**
     * The job being timed, how far along it was when it was last looked at, and
     * how many steps it has failed to get any further.
     *
     * <p>Not saved. A reloaded town simply starts its patience over, which costs
     * it a handful of steps and no correctness — and a stall is only interesting
     * while it is still happening.
     */
    private BuildTask stalledTask;
    private int stalledProgress;
    private int stalledSteps;

    /** Content, not save state — not serialized. */
    private List<BuildingType> catalogue = BuildCatalogue.DEFAULT;

    /**
     * Where this settlement stands on the founding road. Defaults to TOWN so
     * every settlement from before stages existed keeps its old behaviour —
     * only a fresh charter starts a CAMP. See {@code FOUNDING.md}.
     */
    private SettlementStage stage = SettlementStage.TOWN;

    /**
     * Consecutive steps the larder has covered the appetite. The homestead
     * graduates on this, not on a snapshot: one lucky harvest tick is not
     * self-sufficiency.
     */
    private int fedStreak;

    /** Set by the perimeter work once a palisade rings the settlement. */
    private boolean perimeterClosed;

    /** The defensive ring, staked at FORTIFIED; null until then. */
    private Perimeter perimeter;

    /** The roads, remembered as segments so they can be joined, drawn and mended. */
    private PathNetwork paths = new PathNetwork();

    /**
     * How threatened this settlement currently is, driving guard behaviour and
     * off-screen combat resolution. Rises when hostiles are detected, decays over time.
     */
    /**
     * How long a town goes on believing what it last saw.
     *
     * <p>Long enough that a hostile using cover does not flicker the alarm on
     * and off every step, short enough that a town does not spend a minute
     * hiding from something that left.
     */
    public static final int SIGHTING_MEMORY_STEPS = 8;

    /**
     * What a settlement is founded holding, and the whole of its money supply
     * until somebody trades with it.
     *
     * <p>Nothing mints more. Production used to create coin out of nothing in
     * proportion to output, which was issuance dressed up as a levy and meant a
     * town's wealth measured how long it had existed. Now it measures what it
     * started with, minus what it has spent — and the only way to get more is
     * for somebody to come and buy something.
     */
    public static final int FOUNDING_TREASURY = 2000;

    /**
     * The town's money. All of it — no settler owns a coin.
     *
     * <p>Spent on public works, and on whatever the town buys from an outsider.
     * Finite on purpose and now genuinely finite: a town that spends its
     * endowment on a wall has spent it.
     */
    private int treasury = FOUNDING_TREASURY;

    private int threatLevel;

    /**
     * Steps the town goes on believing what it last saw.
     *
     * <p>Threat is read fresh from what people can see, and a hostile ducking
     * behind a hill would otherwise clear the alarm the moment it broke line of
     * sight — then raise it again when it stepped out. A town that has seen
     * something does not forget it that fast.
     *
     * <p>Not persisted. A reload has by definition interrupted whatever was
     * happening, and starting a fresh session already convinced of a mob nobody
     * can find would be worse than looking again.
     */
    private int sightingMemory;

    /** Food banked in the granary. The founding party arrives provisioned. */
    private int foodStock = FoodPlanner.STARTING_PROVISIONS;

    /** Timber in the town's stores, felled by lumberjacks. */

    /** Saplings on hand for replanting what has been cut. */

    /** Where the lumber camp may work, or null until one is built. */
    /** Named counters of what the town has seen done. See {@link Tallies}. */
    private String cultureId = Culture.DEFAULT.id();

    private final Tallies tallies = new Tallies();

    /** Everything the town owns, by name. See {@link TownStores}. */
    /**
     * Goods not yet in any building.
     *
     * <p>The founding kit arrives here, because a party that has just stepped
     * off the road has nowhere to put anything. Once a store is raised this is
     * swept into it and stays near empty — see {@link #putAwayLoosePile}.
     */
    private final TownStores loosePile = TownStores.founding(FoodPlanner.STARTING_PROVISIONS);

    /**
     * Everything the town owns, wherever it is.
     *
     * <p>Derived, never stored. Nothing writes a town-wide figure any more:
     * asking adds up the stores standing in the town, which is what makes
     * "where is it" a question with an answer, and what stops two containers
     * showing the same number from each being entitled to hand it all out.
     */
    private final PooledStock pooled = new PooledStock(this::holders);

    private WorkArea lumberArea;

    /** Where the mine may cut, set by the mine post. */
    private WorkArea mineArea;

    public Settlement(Id id, String name, SimPos centre, int claimRadius) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.centre = Objects.requireNonNull(centre, "centre");
        this.claimRadius = claimRadius;
    }

    public Id id() {
        return id;
    }

    public String name() {
        return name;
    }

    public SimPos centre() {
        return centre;
    }

    /**
     * Hands out the next building plot and advances the cursor.
     *
     * <p>For jobs ordered outside the normal planning path, which still need
     * somewhere of their own to stand.
     */
    /**
     * Picks somewhere worth building, skipping ground that plainly is not.
     *
     * <p>Plots come from geometry alone, so some of them land in a lake or on the
     * side of a ravine. Each is put to the world before it is taken; a refused one
     * is burned rather than reconsidered, so the town does not offer itself the
     * same puddle every step.
     *
     * <p>After {@link BuildPlanner#PLOT_ATTEMPTS} it takes whatever comes next. A
     * town on an island must still be able to build.
     */

    /**
     * Ground for a new building: suitable terrain, and nobody else's.
     *
     * <p>The second half is the one that matters. Ring slots are only candidate
     * points; nothing about the geometry knows how broad a farm is, so without an
     * overlap test the town cheerfully proposed a plot inside a building it had
     * already raised and the excavation demolished it to make room. Plots are
     * squares about their origin and are never allowed to touch.
     */
    private SimPos chooseSite(SimContext ctx, BuildingType type) {
        // Behind the wall when there is a wall. The spiral cursor only ever
        // moves outward, so by the time a village orders its market the cursor
        // is past the palisade and every forward candidate is outside it --
        // the first market this town ever built landed beyond its own gates.
        // Civic buildings rescan from the centre instead: slots the spiral
        // skipped on its way out are still empty ground, and the ring was
        // staked around exactly that ground. Producers stay ring-blind;
        // extraction stands where the resource is.
        if (perimeter != null && !BuildPlanner.PRODUCER_OF.containsValue(type.id())) {
            int frontier = nextPlotIndex + BuildPlanner.PLOT_ATTEMPTS;
            for (int index = 0; index < frontier; index++) {
                SimPos candidate = arrangement().plotFor(centre, index);
                if (!insideRing(candidate, type.plotSpan())
                        || !ctx.bridge().isSiteSuitable(candidate, BuildPlanner.PLOT_PROBE_RADIUS)
                        || !isPlotFree(candidate, type.plotSpan(), null)) {
                    continue;
                }
                if (index >= nextPlotIndex) {
                    nextPlotIndex = index + 1;
                }
                return candidate;
            }
            // Nothing fits inside: the town has outgrown its wall and builds
            // beyond it, which is the alpha-wall's cue to re-stake, not ours.
        }
        return chooseSite(ctx, type.plotSpan(), BuildingRole.of(type.id()));
    }

    /** Whether a plot of this span fits wholly inside the staked ring. */
    private boolean insideRing(SimPos candidate, int span) {
        int west = Integer.MAX_VALUE;
        int east = Integer.MIN_VALUE;
        int north = Integer.MAX_VALUE;
        int south = Integer.MIN_VALUE;
        for (SimPos vertex : perimeter.vertices()) {
            west = Math.min(west, vertex.x());
            east = Math.max(east, vertex.x());
            north = Math.min(north, vertex.z());
            south = Math.max(south, vertex.z());
        }
        int half = span / 2 + 1;
        return candidate.x() - half > west && candidate.x() + half < east
                && candidate.z() - half > north && candidate.z() + half < south;
    }

    /**
     * Ground for a new building — scanning without spending.
     *
     * <p>The index advances only when a plot is actually taken. It used to
     * advance on every rejected candidate too, which read as sensible (a bad
     * slot is bad forever) and was a disaster in practice: relocation checks
     * call this every simulation step while a site sits on unfit ground, so a
     * town beside a lake burned up to {@link BuildPlanner#PLOT_ATTEMPTS} ring
     * slots per step without building anything — and marched its own rings six
     * hundred blocks out. Farms were being planned in the next biome over.
     */
    /**
     * Which buildings would rather stand near which.
     *
     * <p>A granary among the fields it fills, a mill beside the corn it grinds,
     * a forge near the ore. Deliberately small, and one direction per entry: it
     * is a preference expressed while siting, not a claim that two buildings
     * belong to each other.
     */
    private static final Map<BuildingRole, BuildingRole> SITS_NEAR = Map.of(
            BuildingRole.GRANARY, BuildingRole.CROP_FARM,
            BuildingRole.CROP_FARM, BuildingRole.GRANARY,
            BuildingRole.MILL, BuildingRole.CROP_FARM,
            BuildingRole.SMITH, BuildingRole.MINE);

    /**
     * How much a partner counts against a street, when both have an opinion.
     *
     * <p>Half. A building off the road is awkward for everybody who ever walks
     * to it; a granary a little further from the fields is awkward only for the
     * carriers, and they are already walking.
     */
    private static final double PARTNER_WEIGHT = 0.5;

    /**
     * What a wooded plot is worth to a lumber camp, per point of woodedness.
     *
     * <p>Half a block per percent, so ground under full canopy is worth a fifty
     * block walk. Generous on purpose: everything else on this list is a
     * convenience and this one is whether the building works at all.
     */
    private static final double WOODS_WEIGHT = 0.5;

    /**
     * What standing here would cost this building, or -1 when nothing minds.
     *
     * <p>Distance to the town's own streets, plus half the distance to whatever
     * this kind of building likes to stand near. A town with neither a road nor
     * a relevant neighbour has no opinion at all and says so, so the caller can
     * fall back to taking the first plot that fits.
     */
    public double siteCost(SimPos candidate, BuildingRole role) {
        return siteCost(candidate, role, null);
    }

    /** As above, and asking the world about the trees when one is to hand. */
    public double siteCost(SimPos candidate, BuildingRole role, SimContext ctx) {
        double toRoad = paths.distanceToRoad(candidate);
        Building partner = SITS_NEAR.containsKey(role)
                ? nearestWithRole(candidate, SITS_NEAR.get(role)) : null;
        boolean wantsTrees = role == BuildingRole.LUMBER_CAMP && ctx != null;
        if (toRoad < 0 && partner == null && !wantsTrees) {
            return -1;
        }
        double cost = toRoad < 0 ? 0 : toRoad;
        if (partner != null) {
            cost += PARTNER_WEIGHT
                    * Math.sqrt(partner.origin().horizontalDistanceSq(candidate));
        }
        if (wantsTrees) {
            // Charged for the trees that are missing rather than credited for
            // the ones that are there, so a cost is never negative. It has to
            // not be: the caller reads any negative as "this town has no
            // opinion" and falls back to the first plot that fits, so crediting
            // would have made every wooded plot look like no plot at all.
            //
            // A camp wants trees more than it wants a short walk. It is the one
            // building whose whole purpose is what happens to be growing around
            // it, and one on open grass has nothing to fell however convenient
            // it is.
            int trees = ctx.bridge().woodedness(candidate, BuildPlanner.PLOT_PROBE_RADIUS);
            cost += WOODS_WEIGHT * (100 - trees);
        }
        return cost;
    }

    /** The nearest standing building of a kind, or null if the town has none. */
    private Building nearestWithRole(SimPos from, BuildingRole role) {
        Building nearest = null;
        long best = Long.MAX_VALUE;
        for (Building building : buildings) {
            if (building.role() != role) {
                continue;
            }
            long away = building.origin().horizontalDistanceSq(from);
            if (away < best) {
                best = away;
                nearest = building;
            }
        }
        return nearest;
    }

    /**
     * How many usable plots are weighed against each other before one is taken.
     *
     * <p>Bounded because relocation checks call this every step while a site
     * sits on unfit ground, and because the point is a choice rather than an
     * exhaustive search: the nearest dozen fits already contain something on a
     * street if a street runs anywhere near.
     */
    private static final int SITE_CHOICES = 12;

    private SimPos chooseSite(SimContext ctx, int span, BuildingRole role) {
        SimPos best = null;
        double bestCost = Double.MAX_VALUE;
        int firstFree = -1;
        int considered = 0;
        for (int attempt = 0; attempt < BuildPlanner.PLOT_ATTEMPTS; attempt++) {
            int index = nextPlotIndex + attempt;
            SimPos candidate = arrangement().plotFor(centre, index);
            if (!ctx.bridge().isSiteSuitable(candidate, BuildPlanner.PLOT_PROBE_RADIUS)
                    || !isPlotFree(candidate, span, null)) {
                continue;
            }
            if (firstFree < 0) {
                firstFree = index;
            }
            double cost = siteCost(candidate, role, ctx);
            if (cost < 0) {
                // Nothing to prefer — no streets and no partner standing yet —
                // so the first fit wins, which is what this did before there was
                // anything to weigh.
                nextPlotIndex = index + 1;
                return candidate;
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
            if (++considered >= SITE_CHOICES) {
                break;
            }
        }
        if (best != null) {
            // Advanced to the first fit rather than past the one taken, so the
            // slots passed over stay available to the next building. The one
            // actually used is offered again later and refused by isPlotFree,
            // which costs an iteration and keeps the ring economy exactly as it
            // was — a town that chooses more carefully must not also creep
            // outward faster.
            nextPlotIndex = firstFree + 1;
            return best;
        }
        // Every candidate examined and none will do. Take the next slot rather
        // than stop building altogether — a town out of room builds on poor
        // ground rather than giving up.
        //
        // Poor ground, though. Not water, and not unexamined. This used to hand
        // back the next slot untested, so the better the terrain rules got, the
        // more often the search exhausted itself and the more buildings were
        // placed with no check at all — a farm, a lumber camp and a watchtower
        // standing in a river at y=54, 55 and 62, none of which the rules had
        // ever been asked about. Every improvement upstream was partly
        // cancelling itself here.
        for (int extra = 0; extra < DESPERATE_ATTEMPTS; extra++) {
            SimPos candidate = arrangement().plotFor(centre, nextPlotIndex + extra);
            // Free ground as well as dry ground. Refusing only water let a
            // desperate build land on a plot somebody was already standing on --
            // an animal farm through the side of a market, caught by the layout
            // fitness test the day after this loop was written. Desperation is a
            // reason to take poor ground, never a reason to take taken ground.
            if (!isPlotFree(candidate, span, null)) {
                continue;
            }
            if (!ctx.bridge().standsInWater(candidate, BuildPlanner.PLOT_PROBE_RADIUS)) {
                nextPlotIndex += extra + 1;
                return candidate;
            }
        }
        return arrangement().plotFor(centre, nextPlotIndex++);
    }

    /**
     * Slots a town will walk past when it is out of good ground.
     *
     * <p>Only water is refused this far down; steepness and distance have
     * already been given up on. Bounded because the answer has to arrive: past
     * this the town takes what is there, which is the old behaviour and is
     * reached only by a settlement hemmed in by sea on every side.
     */
    private static final int DESPERATE_ATTEMPTS = 128;

    /**
     * Whether a plot of this width fouls any building, or any build already ordered.
     *
     * @param ignore a building origin to skip, for an improvement raised in place
     */
    public boolean isPlotFree(SimPos candidate, int span, SimPos ignore) {
        for (Building standing : buildings) {
            if (!BuildPlanner.holdsGround(standing.blueprintId())
                    || (ignore != null && standing.origin().equals(ignore))) {
                continue;
            }
            if (BuildPlanner.plotsOverlap(candidate, span, standing.origin(),
                    plotSpanOf(standing))) {
                return false;
            }
        }
        for (BuildTask queued : buildQueue) {
            if (!BuildPlanner.holdsGround(queued.blueprintId())
                    || (ignore != null && ignore.equals(queued.upgradeOf()))) {
                continue;
            }
            if (BuildPlanner.plotsOverlap(candidate, span, queued.origin(),
                    BuildPlanner.plotSpanOf(queued.blueprintId(), catalogue))) {
                return false;
            }
        }
        return true;
    }

    /**
     * How much ground a standing building takes.
     *
     * <p>Its measured footprint once it has one — that is the real cleared plot,
     * apron and all — and the catalogue's figure before then.
     */
    private int plotSpanOf(Building standing) {
        Footprint measured = standing.footprint();
        if (measured.isKnown()) {
            return Math.max(measured.width(), measured.depth());
        }
        return BuildPlanner.plotSpanOf(standing.blueprintId(), catalogue);
    }

    /**
     * Claims the next ring slot broad enough for a building of this span.
     *
     * <p>For work ordered outside the ordinary planning path — a producer the town
     * urgently needs — which still has to land on ground nothing else holds.
     */
    public SimPos takeNextPlot(int span) {
        return takeNextPlot(span, null);
    }

    /**
     * The next free ring slot, and never one standing in a river.
     *
     * <p>This is the urgent path — a town out of wood ordering a lumber camp
     * does not go through {@code chooseSite} and never has. It asked only
     * whether the plot overlapped another, which meant <strong>producers have
     * always ignored the ground entirely</strong>: a farm at y=54, a lumber camp
     * at 55 and a watchtower at 62, all standing in the sea, on a seed where
     * every civic building had been sited perfectly well around them.
     *
     * <p>Terrain quality is still not judged here, and deliberately: an urgent
     * build is urgent, and a town that will not put a lumber camp on a slope is
     * a town that runs out of wood. Open water is the exception, because it is
     * not poor ground, it is not ground.
     *
     * @param bridge may be null, for callers with no world to ask
     */
    public SimPos takeNextPlot(int span, com.kingdoms.sim.platform.WorldBridge bridge) {
        for (int attempt = 0; attempt < BuildPlanner.PLOT_ATTEMPTS; attempt++) {
            int index = nextPlotIndex + attempt;
            SimPos candidate = arrangement().plotFor(centre, index);
            if (!isPlotFree(candidate, span, null)) {
                continue;
            }
            if (bridge != null
                    && bridge.standsInWater(candidate, BuildPlanner.PLOT_PROBE_RADIUS)) {
                continue;
            }
            nextPlotIndex = index + 1;
            return candidate;
        }
        for (int extra = 0; bridge != null && extra < DESPERATE_ATTEMPTS; extra++) {
            SimPos candidate = arrangement().plotFor(centre, nextPlotIndex + extra);
            if (!isPlotFree(candidate, span, null)) {
                continue;   // taken ground is not poor ground, it is somebody's
            }
            if (!bridge.standsInWater(candidate, BuildPlanner.PLOT_PROBE_RADIUS)) {
                nextPlotIndex += extra + 1;
                return candidate;
            }
        }
        return arrangement().plotFor(centre, nextPlotIndex++);
    }

    public int nextPlotIndex() {
        return nextPlotIndex;
    }

    public void setNextPlotIndex(int nextPlotIndex) {
        this.nextPlotIndex = Math.max(0, nextPlotIndex);
    }

    /**
     * Gives up on the job in hand and moves to the next plot.
     *
     * <p>For a site that cannot be built at all. The plot is burned rather than
     * reconsidered, so the town does not propose the same impossible spot on the
     * very next step.
     */
    public void abandonBuild(long step, String reason) {
        if (buildQueue.isEmpty()) {
            return;
        }
        BuildTask given = buildQueue.removeFirst();
        nextPlotIndex++;
        logEvent(step, "Abandoned " + given.blueprintId() + " at " + given.site() + " — " + reason);
    }

    public int claimRadius() {
        return claimRadius;
    }

    public void setClaimRadius(int claimRadius) {
        this.claimRadius = claimRadius;
    }

    public int threatLevel() {
        return threatLevel;
    }

    /**
     * Which culture this town belongs to.
     *
     * <p>Set from the kingdom when the settlement is founded, and held here so a
     * settlement can be reasoned about without walking back up to its kingdom.
     */
    public String cultureId() {
        return cultureId;
    }

    /**
     * How this town arranges itself on the ground.
     *
     * <p>Read from the culture every time rather than cached: a settlement's
     * culture can be set after it is built (a save restores it, a founding
     * party carries one), and a layout captured at construction would be the
     * default forever.
     */
    public com.kingdoms.sim.culture.Layout arrangement() {
        return Culture.of(cultureId).arrangement();
    }

    public void setCultureId(String cultureId) {
        this.cultureId = cultureId == null ? Culture.DEFAULT.id() : cultureId;
    }

    public Tallies tallies() {
        return tallies;
    }

    /**
     * Everything the town owns, as one view over the places holding it.
     *
     * <p>A {@link Stock} rather than a {@link TownStores}: there is no single
     * ledger to hand back, and the two operations that would need one —
     * {@code set} and {@code restore} — are meaningless spread across
     * buildings. Saving and loading reach for {@link #loosePile()} and the
     * buildings themselves instead.
     */
    public Stock stores() {
        return pooled;
    }

    /** Goods not yet in any building. For the codecs and the founding kit. */
    public TownStores loosePile() {
        return loosePile;
    }

    /**
     * Who holds the town's goods, nearest thing to a store first.
     *
     * <p>Order is the whole of the locality rule: produce lands in the first
     * holder and spending drains them in turn, so stores are filled and emptied
     * before the loose pile is touched. The loose pile is always last and
     * always present — a pool with no holders accepts nothing, and a camp with
     * no storehouse would otherwise drop everything its people produced.
     *
     * <p><strong>Not gated on whether the blocks are stamped.</strong> A
     * building the simulation says was raised is a building, whether or not
     * anybody has been near enough for it to be drawn. Requiring that it be
     * materialized was a way to make a town's entire stock disappear: an
     * upgrade sets the flag back to false while the new blocks go down, and a
     * storehouse that stops counting as a holder takes every log in it out of
     * the town's reckoning — builders idle for want of timber that is right
     * there, and a granary's worth of food gone from a hungry town's books.
     */
    private List<Stock> holders() {
        List<Stock> out = new ArrayList<>();
        for (Building building : buildings) {
            if (building.isStore()) {
                out.add(building.stores());
            }
        }
        out.add(loosePile);
        return out;
    }

    /**
     * The store nearest a point, or null if the town has raised none yet.
     *
     * <p>Horizontal distance, because a store a floor up is the same walk.
     */
    public Building nearestStore(SimPos from) {
        return nearestStore(from, null);
    }

    /**
     * The nearest store that can actually pay for something.
     *
     * <p>This is what makes locality real rather than a convention. A worker
     * walks to a particular building and draws from that building's ledger, so
     * a chest on one side of the village stops being a way to fetch timber
     * kept on the other.
     *
     * <p>It asks for a store that <em>holds</em> the goods rather than simply
     * the closest one, because the closest one being empty must not strand
     * anybody: without couriers there is nothing yet to move goods to the
     * store a builder happens to be standing in, so the builder goes to where
     * the goods are. Once haulers exist this is the rule that tells them which
     * shelves are running dry.
     *
     * @param resource what the store must hold, or null for any store at all
     */
    public Building nearestStore(SimPos from, String resource) {
        Building nearest = null;
        long best = Long.MAX_VALUE;
        for (Building building : buildings) {
            if (!building.isStore()) {
                continue;
            }
            if (resource != null && !building.stores().has(resource, 1)) {
                continue;
            }
            long distance = building.origin().horizontalDistanceSq(from);
            if (distance < best) {
                best = distance;
                nearest = building;
            }
        }
        return nearest;
    }

    /**
     * Where goods made at a place should be put down.
     *
     * <p>The nearest store, or the open ground if the town has not raised one.
     * Everything produced somewhere in particular should come through here:
     * depositing into "the town" put every log in whichever store happened to
     * be first in the list, which left a second storehouse standing empty
     * forever — a building the town had paid for and could never fill.
     */
    public Stock storeNear(SimPos from) {
        Building store = nearestStore(from);
        return store == null ? loosePile : store.stores();
    }

    /**
     * Takes in produce at the store nearest where it was made.
     *
     * <p>The ceiling is the whole town's and the deposit is local, which is the
     * only combination that works. Measuring room against one building would
     * let a town with two stores hold twice what the cap allows; depositing
     * against the whole town is what made the second store decorative. So the
     * room is worked out first, from everything the town owns, and only then is
     * the produce carried to the nearest shelves.
     *
     * @return how much was actually taken in
     */
    public int produceNear(SimPos from, String resource, int amount, int ceiling) {
        int room = Math.max(0, ceiling - pooled.get(resource));
        int fitting = Math.min(Math.max(0, amount), room);
        if (fitting <= 0) {
            return 0;
        }
        storeNear(from).add(resource, fitting);
        // No coin is created here. Production makes goods; goods are not money.
        // This used to mint a coin for every four units produced, which made a
        // town's wealth a measure of how long it had been running rather than
        // of anything it had done with anybody.
        return fitting;
    }

    /** The building standing at exactly this origin, or null. */
    public Building buildingAt(SimPos pos) {
        for (Building building : buildings) {
            if (building.origin().equals(pos)) {
                return building;
            }
        }
        return null;
    }

    /** Every building of this kind, in the order they were raised. */
    public List<Building> buildingsWithRole(BuildingRole role) {
        List<Building> out = new ArrayList<>();
        for (Building building : buildings) {
            if (building.role() == role) {
                out.add(building);
            }
        }
        return out;
    }

    /** The first building of this kind, or null if the town has none. */
    public Building buildingWithRole(BuildingRole role) {
        for (Building building : buildings) {
            if (building.role() == role) {
                return building;
            }
        }
        return null;
    }

    /**
     * Makes the town hold exactly this much of something.
     *
     * <p>Spread across buildings the idea needs a rule, so here it is: empty
     * every holder of the resource, then put the new amount in the first. Only
     * the food accessors and the tests ask for this; everything in the running
     * simulation adds and takes, which need no such rule.
     */
    public void setStock(String resource, int amount) {
        for (Stock holder : holders()) {
            holder.takeUpTo(resource, Integer.MAX_VALUE);
        }
        if (amount > 0) {
            pooled.add(resource, amount);
        }
    }

    /**
     * Moves the loose pile into a store, once there is one to move it into.
     *
     * <p>This is what makes the founding kit real. Four hundred and eighty
     * timber used to be a number on a charter with not one log anywhere in the
     * world; now, as soon as the party raises somewhere to put it, it is in a
     * building that a container can show and a builder can walk to.
     *
     * <p>Run at the top of every {@link #step}, and public so it can be
     * asked for directly rather than only as a side effect of a whole tick.
     */
    public void putAwayLoosePile() {
        if (loosePile.all().isEmpty()) {
            return;
        }
        Building into = nearestStore(centre);
        if (into == null) {
            return;   // nowhere to put it yet; it stays in the open
        }
        for (Map.Entry<String, Integer> held : Map.copyOf(loosePile.all()).entrySet()) {
            into.stores().add(held.getKey(), loosePile.takeUpTo(held.getKey(), held.getValue()));
        }
    }

    public int foodStock() {
        return pooled.get(TownStores.FOOD);
    }

    /**
     * Whether this town is starving, and so may break its own rules to stop.
     *
     * <p>Derived, never stored — see {@link FoodPlanner#isStarving}. The one rule
     * it exists to enforce: a town must never sit idle while its people starve.
     */
    public boolean isStarving() {
        return FoodPlanner.isStarving(this);
    }

    public void setFoodStock(int amount) {
        setStock(TownStores.FOOD, amount);
    }

    public int woodStock() {
        return pooled.get(TownStores.WOOD);
    }

    public void setWoodStock(int amount) {
        setStock(TownStores.WOOD, amount);
    }

    public int stoneStock() {
        return pooled.get(TownStores.STONE);
    }

    public void setStoneStock(int amount) {
        setStock(TownStores.STONE, amount);
    }

    public int saplingStock() {
        return pooled.get(TownStores.SAPLINGS);
    }

    public void setSaplingStock(int amount) {
        setStock(TownStores.SAPLINGS, amount);
    }

    public WorkArea lumberArea() {
        return lumberArea;
    }

    public WorkArea mineArea() {
        return mineArea;
    }

    public void setMineArea(WorkArea mineArea) {
        this.mineArea = mineArea;
    }

    public void setLumberArea(WorkArea lumberArea) {
        this.lumberArea = lumberArea;
    }

    /**
     * What the town is doing about what it has seen.
     *
     * <p>Derived from the threat count rather than stored, so the two can never
     * disagree — and graduated, because one skeleton and a raid are not the same
     * emergency. See {@link Alarm}.
     */
    /** See {@link #treasury}. */
    public int treasury() {
        return treasury;
    }

    /** Takes coin in — a levy on production, or a sale to an outsider. */
    public void bank(int coin) {
        if (coin > 0) {
            treasury += coin;
        }
    }

    /**
     * Pays coin out, and says whether it could.
     *
     * <p>Never goes negative. A town with an empty treasury does not pay wages
     * and does not buy finds; both are visible failures rather than a debt
     * nobody records.
     */
    public boolean spend(int coin) {
        if (coin <= 0 || treasury < coin) {
            return false;
        }
        treasury -= coin;
        return true;
    }

    public void setTreasury(int treasury) {
        this.treasury = Math.max(0, treasury);
    }

    public Alarm alarm() {
        return Alarm.of(threatLevel);
    }

    /**
     * Records what was seen this step: how much danger, and from how many.
     *
     * <p>Raises the alarm to at least what was seen — never lowers it, because
     * the fewer you can see the more likely it is that the rest went round the
     * back — and refreshes the memory so a mob breaking line of sight does not
     * clear the town's mind with it.
     *
     * <p><strong>One creature is never a panic.</strong> A lone hostile, however
     * nasty, is capped one rung below {@link Alarm#ALARMED_AT} no matter what it
     * is worth. This is the difference between a town that fears creepers and a
     * town that is paralysed by them: the danger still registers, the guards
     * still go, the civilians near it still run — but the streets do not empty
     * over one of anything. That is what a watch is for.
     */
    public void sighted(Sighting sighting) {
        if (!sighting.any() || sighting.danger() <= 0) {
            return;
        }
        int worth = sighting.isLone()
                ? Math.min(sighting.danger(), Alarm.ALARMED_AT - 1)
                : sighting.danger();
        if (worth > threatLevel) {
            threatLevel = worth;
        }
        sightingMemory = SIGHTING_MEMORY_STEPS;
    }

    /**
     * Sounds the alarm: somebody has decided this is beyond the watch.
     *
     * <p>Deliberate rather than counted. The tiers below it are the town's own
     * eyes doing arithmetic; this is a guard looking at what is coming and
     * ringing the bell, which panics everybody whether or not three of them
     * happen to be in view at the same moment.
     */
    public void soundAlarm() {
        if (threatLevel < Alarm.ALARMED_AT) {
            threatLevel = Alarm.ALARMED_AT;
        }
        sightingMemory = SIGHTING_MEMORY_STEPS;
    }

    /** Whether the town is still going on what it last saw rather than what it sees. */
    public boolean remembersSighting() {
        return sightingMemory > 0;
    }

    public void setThreatLevel(int threatLevel) {
        this.threatLevel = Math.max(0, threatLevel);
    }

    public void addResident(Person person) {
        residents.put(person.id(), person);
    }

    public Person removeResident(Person.Id personId) {
        return residents.remove(personId);
    }

    /**
     * Removes a person completely: from the roster and from their family. A family
     * emptied by the removal is dissolved, which frees its house for the next one.
     * This is what a death calls.
     */
    public Person removePerson(Person.Id personId) {
        Person removed = residents.remove(personId);
        if (removed == null) {
            return null;
        }
        for (Iterator<Household> it = households.iterator(); it.hasNext(); ) {
            Household household = it.next();
            if (household.removeMember(personId) && household.size() == 0) {
                it.remove();
            }
        }
        return removed;
    }

    public Person resident(Person.Id personId) {
        return residents.get(personId);
    }

    public Collection<Person> residents() {
        return Collections.unmodifiableCollection(residents.values());
    }

    public int population() {
        return residents.size();
    }

    public void enqueueBuild(BuildTask task) {
        buildQueue.add(Objects.requireNonNull(task, "task"));
    }

    /**
     * Puts a job at the head of the queue, pausing whatever was under way.
     * For repairs that are stopping somebody living their life — a door they
     * cannot reach — rather than the ordinary wants of a growing town.
     */
    public void enqueueUrgent(BuildTask task) {
        buildQueue.add(0, Objects.requireNonNull(task, "task"));
    }

    public List<BuildTask> buildQueue() {
        return Collections.unmodifiableList(buildQueue);
    }

    /** Restores a building when loading from disk. New buildings come from {@link #step}. */
    public void addBuilding(Building building) {
        buildings.add(Objects.requireNonNull(building, "building"));
    }

    public List<Building> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    /**
     * How many completed buildings of this blueprint exist. Does not count work in
     * progress — safe only because the settlement queues one project at a time.
     */
    public int countBuildings(String blueprintId) {
        // A levelled building is still one of these. Counting by the plain id is
        // what stops an improved house reading as a house the town no longer has.
        return (int) buildings.stream()
                .filter(b -> BuildPlanner.baseIdOf(b.blueprintId()).equals(blueprintId))
                .count();
    }

    public void addHousehold(Household household) {
        households.add(Objects.requireNonNull(household, "household"));
    }

    /** Record a line of history. What happened here is knowable later, watched or not. */
    public void logEvent(long step, String message) {
        events.addLast(new SettlementEvent(step, Objects.requireNonNull(message, "message")));
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
    }

    public List<SettlementEvent> events() {
        return List.copyOf(events);
    }

    public List<Household> households() {
        return Collections.unmodifiableList(households);
    }

    /** Detach a family without touching its members — for emigration, not death. */
    public boolean removeHousehold(Household household) {
        return households.remove(household);
    }

    /** Families with no house. These are what drive housing demand being felt. */
    public List<Household> unhousedHouseholds() {
        return households.stream().filter(h -> !h.isHoused()).toList();
    }

    /** What this settlement knows how to build. Swap per culture, or from a datapack. */
    public SettlementStage stage() {
        return stage;
    }

    public void setStage(SettlementStage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public int fedStreak() {
        return fedStreak;
    }

    public void setFedStreak(int fedStreak) {
        this.fedStreak = Math.max(0, fedStreak);
    }

    public boolean perimeterClosed() {
        return perimeterClosed;
    }

    public void setPerimeterClosed(boolean perimeterClosed) {
        this.perimeterClosed = perimeterClosed;
    }

    public PathNetwork paths() {
        return paths;
    }

    public void setPaths(PathNetwork paths) {
        this.paths = paths == null ? new PathNetwork() : paths;
    }

    public Perimeter perimeter() {
        return perimeter;
    }

    public void setPerimeter(Perimeter perimeter) {
        this.perimeter = perimeter;
    }

    /**
     * Whether this person does this kind of labour here, today.
     *
     * <p>The seam the pioneer works through: below VILLAGE a pioneer is every
     * labouring trade at once, so a camp of four can build and farm without a
     * staffing table that wants zero farmers below population five ever being
     * consulted. From VILLAGE the specialists exist and the answer is simply
     * the profession.
     */
    public boolean laboursAs(Person person, Profession trade) {
        if (person.profession() == trade) {
            return true;
        }
        // Builder and farmer only. Timber and stone stay with the real trades:
        // FORTIFIED crystallizes a lumberjack the moment the town can fell at
        // all, so pretending pioneers swing axes would only split the work
        // between a planner that counts professions and a seam that lies.
        return person.profession() == Profession.PIONEER
                && StagePlanner.pioneersLabour(stage)
                && (trade == Profession.BUILDER || trade == Profession.FARMER);
    }

    public List<BuildingType> catalogue() {
        return catalogue;
    }

    public void setCatalogue(List<BuildingType> catalogue) {
        this.catalogue = List.copyOf(Objects.requireNonNull(catalogue, "catalogue"));
    }

    /** Buildings that exist in the simulation but have not been drawn into the world yet. */
    public List<Building> pendingBuildings() {
        return buildings.stream().filter(b -> !b.isMaterialized()).toList();
    }

    /**
     * Whether this home is one a family can grow in.
     *
     * <p>The bunkhouse shelters everyone and breeds no one — communal bunks are
     * a stage, not a destination. Births gate on this, which is what makes the
     * cottage transition at VILLAGE a real unlock.
     */
    public boolean isFamilyHome(SimPos home) {
        if (home == null) {
            return false;
        }
        for (Building building : buildings) {
            if (building.origin().equals(home)) {
                return !BuildPlanner.baseIdOf(building.blueprintId())
                        .equals("kingdoms:bunkhouse");
            }
        }
        return true;   // no record of the building; do not orphan the household
    }

    public boolean contains(SimPos pos) {
        return centre.horizontalDistanceSq(pos) <= (long) claimRadius * claimRadius;
    }

    /**
     * Advance this settlement by one simulation step.
     *
     * <p>Called from the slow scheduler, not from the 20 Hz game tick. Everything
     * here must be safe to run with no chunks loaded.
     */
    public void step(SimContext ctx) {
        putAwayLoosePile();
        advanceStage(ctx);
        planNextBuild(ctx);
        // Roads before walls: the perimeter cuts its gates where the roads
        // cross the ring, so the network has to exist before it is staked.
        PathPlanner.advance(this, ctx);
        PerimeterPlanner.advance(this, ctx);
        InnPlanner.advance(this, ctx);
        advanceBuildQueue(ctx);
        materializePending(ctx);
        FoodPlanner.advance(this, ctx);
        // Errands are set before they are walked, so the courier is asked
        // first and its load moves on the same step it was ordered.
        SupplyPlanner.advance(this, ctx);
        HaulPlanner.advance(this, ctx);
        LumberPlanner.advance(this, ctx);
        MinePlanner.advance(this, ctx);
        SmithPlanner.advance(this, ctx);
        equipWorkers();
        JobPlanner.retrainOne(this);
        PopulationPlanner.advance(this, ctx);
        trackFedStreak();
        decayThreat();
        // After decay so a sustained hostile presence holds threat at its level
        // rather than oscillating one below it.
        RaidPlanner.advance(this, ctx);
        // Last, and after the raid pass on purpose: whatever a raid just knocked
        // down is counted on the same step it happens rather than the next one.
        RepairPlanner.advance(this, ctx);
    }

    /**
     * Decides what to build next, if anything.
     *
     * <p>One project at a time: if something is already queued, the settlement is
     * busy and does not reconsider. That keeps behaviour legible — a settlement
     * finishes what it started — and means {@link #countBuildings} can ignore work
     * in progress without double-counting.
     *
     * <p>Starvation is the one thing that interrupts it. See
     * {@link #planSurvivalBuild}, which orders a farm over the top of a stalled
     * head; the counting stays honest because that lane checks the queue for what
     * it is about to order, exactly as the producer bootstrap does.
     */
    /**
     * Hands out tools to whoever is working without one.
     *
     * <p>One a step, so a full rack empties into the workforce gradually rather
     * than all at once, and so a town that is only just keeping up still gets
     * its newest worker equipped eventually.
     */
    private void equipWorkers() {
        for (Person person : residents.values()) {
            if (person.profession() == Profession.IDLER || person.hasTool()) {
                continue;
            }
            if (SmithPlanner.issueTool(this, person)) {
                return;
            }
        }
    }

    /**
     * Puts food in front of everything else while the town is starving.
     *
     * <p>The build queue is head-blocking: nothing new is ever ordered while
     * something is queued, so a head the town cannot pay for freezes all ordering
     * forever. That is how a settlement came to starve beside a half-built hall
     * without ever managing to want a farm — the hall was waiting on stone, and a
     * queue with a hall in it is a queue that considers nothing.
     *
     * <p>So a starving town orders its fields anyway, at the head, once the job
     * in hand has plainly stopped moving. The displaced task keeps its plot and
     * every unit of work already done; it is parked behind the farm, not
     * abandoned, and comes back the moment the farm is finished.
     *
     * <p>One survival job at a time, like every other kind, so a long famine
     * cannot flood the queue or the event log.
     */
    private void planSurvivalBuild(SimContext ctx) {
        if (!isStarving()) {
            return;
        }
        if (!buildQueue.isEmpty() && stalledSteps < STALLED_HEAD_STEPS) {
            return;   // still making progress on something; let it finish
        }
        // One role settles the whole lane, so the rescue and the thing it rescues
        // can never disagree: a town that needs a farm does not get its granary
        // pushed to the front instead, and then wait out the granary's whole
        // build before it is allowed to want the field again.
        String role = survivalRoleWanted();
        if (role == null) {
            return;   // both stand already; this famine is not a building problem
        }
        if (promoteQueuedSurvivalBuild(ctx, role)) {
            return;   // the town already ordered its dinner; it just could not reach it
        }
        BuildingType wanted = survivalWant(role);
        if (wanted == null) {
            return;
        }
        SimPos flat = chooseSite(ctx, wanted);
        SimPos plot = new SimPos(flat.x(), ctx.bridge().surfaceHeight(flat), flat.z());
        if (!contains(plot)) {
            claimRadius = BuildPlanner.claimRadiusFor(centre, plot,
                    arrangement().claimMargin());
        }
        BuildTask ordered = new BuildTask(wanted.id(), plot, wanted.workCost());
        ordered.setFacing(BuildPlanner.facingToward(plot, centre));
        enqueueUrgent(ordered);
        logEvent(ctx.step(), "Starving — work on a " + readableName(wanted.id())
                + " goes ahead of everything else");
    }

    /**
     * Brings a food building the town has already ordered to the front.
     *
     * <p>This exists because {@link #survivalWant(String)} treats a queued farm
     * as a farm on its way, and behind a stalled head that is simply not true.
     * The founding party had it exactly so: the program ordered the farm, the
     * stone ran out, a mine was shoved in front of it, and from then on every
     * step the town asked itself whether it needed a farm and answered "one is
     * coming" about a job that could not start until the mine finished — which it
     * never did. The net was held shut by the very task it was meant to rescue.
     *
     * <p>Promoting rather than ordering a second one is the deliberate choice.
     * The town does not need two farms, it needs the one it has already sited and
     * paid a plot for to be the thing the builders are standing at; ordering
     * another would spend a fresh plot and leave a duplicate in the queue behind
     * it forever. With this running first, {@link #survivalWant(String)}'s
     * "already on the way" test becomes true rather than merely hopeful —
     * anything it finds in the queue is at the head by the time it looks.
     *
     * @param role the one role the town is short of, from {@link #survivalRoleWanted}
     * @return true if a queued building of that role was moved to the head
     */
    private boolean promoteQueuedSurvivalBuild(SimContext ctx, String role) {
        if (!buildQueue.isEmpty()
                && FoodPlanner.namesRole(buildQueue.getFirst().blueprintId(), role)) {
            // It is already first and still going nowhere, so the trouble is not
            // the order of the queue. Shuffling one farm in front of another every
            // eight steps would fill the event log with a town changing its mind
            // and feed nobody.
            return false;
        }
        for (int i = 1; i < buildQueue.size(); i++) {
            BuildTask queued = buildQueue.get(i);
            if (!FoodPlanner.namesRole(queued.blueprintId(), role)) {
                continue;
            }
            buildQueue.remove(i);
            buildQueue.addFirst(queued);
            logEvent(ctx.step(), "Starving — the "
                    + readableName(queued.blueprintId())
                    + " already ordered goes ahead of everything else");
            return true;
        }
        return false;
    }

    /** The half of a blueprint id worth showing a player. */
    private static String readableName(String blueprintId) {
        return blueprintId.substring(blueprintId.indexOf(':') + 1).replace('_', ' ');
    }

    /**
     * The one food building a starving town most needs and does not have.
     *
     * <p>Neither the population thresholds nor the per-resident scaling apply
     * here. A four-person charter wants no farms at all by those rules — nought
     * plus four-sixths — which is a perfectly sensible thing to want right up to
     * the moment the provisions run out.
     *
     * @return the role from {@link FoodPlanner#SURVIVAL_ROLES}, or null if every
     *         one of them already stands
     */
    private String survivalRoleWanted() {
        for (String role : FoodPlanner.SURVIVAL_ROLES) {
            if (buildings.stream().noneMatch(b -> FoodPlanner.namesRole(b.blueprintId(), role))) {
                return role;
            }
        }
        return null;
    }

    /**
     * The catalogue's answer to a role, or null if the town is already raising it.
     *
     * <p>The "already ordered, so want nothing" rule is only honest because
     * {@link #promoteQueuedSurvivalBuild} has run first. A farm parked behind a
     * blocked head is not a farm on its way, and answering this question with one
     * is exactly how a settlement starved with its own farm second in the queue:
     * the program ordered the field, the stone ran out, a mine was shoved in
     * front of it, and every step afterwards the town asked whether it needed a
     * farm and told itself one was coming. With the promotion ahead of it,
     * anything found in the queue here is at the head and genuinely under way.
     *
     * @return the type to raise, or null if it is already ordered or the
     *         catalogue has nothing for the role
     */
    private BuildingType survivalWant(String role) {
        if (buildQueue.stream().anyMatch(t -> FoodPlanner.namesRole(t.blueprintId(), role))) {
            return null;   // already ordered; ordering the next one too would only queue-jump itself
        }
        for (BuildingType type : catalogue) {
            if (FoodPlanner.namesRole(type.id(), role)) {
                return type;
            }
        }
        return null;
    }

    private void planNextBuild(SimContext ctx) {
        planSurvivalBuild(ctx);
        if (!buildQueue.isEmpty()) {
            return;
        }
        // The stage's own program outranks everything the catalogue wants. This
        // is the whole founding fix: a camp raises a bunkhouse and a farm in the
        // program's order, and the catalogue — hall at priority 100 and all —
        // does not get a word in until VILLAGE.
        Optional<BuildingType> programmed = StagePlanner.nextProgramWant(this);
        if (programmed.isPresent()) {
            orderBuild(ctx, programmed.get());
            return;
        }
        if (!StagePlanner.catalogueRuns(stage)) {
            return;   // below VILLAGE the program is the whole of the plan
        }
        // A town no longer improves what already stands. Upgrading raised a
        // bigger building on the footprint of a smaller one, and the plot
        // overlap check deliberately exempts an upgrade from colliding with the
        // thing it is replacing — so a level-two building could grow straight
        // through whatever was next door, which is what "the buildings are
        // stacked" looks like from the ground.
        //
        // Removed rather than gated, because a switch would leave the same
        // geometry waiting to be turned back on. It comes back when a building
        // that grows can be shown not to eat its neighbour. See GOALS.md.
        Optional<BuildingType> wanted = BuildPlanner.chooseNext(this, catalogue)
                .filter(type -> StagePlanner.catalogueAllows(stage, type.id()));
        wanted.ifPresent(type -> orderBuild(ctx, type));
    }

    /** Sites and queues one building — the shared tail of program and catalogue. */
    private void orderBuild(SimContext ctx, BuildingType type) {
        SimPos flat = chooseSite(ctx, type);

        // Snap to the terrain when the chunk is available; otherwise the
        // centre's height stands in and the world snaps again at placement.
        SimPos plot = new SimPos(flat.x(), ctx.bridge().surfaceHeight(flat), flat.z());

        // A settlement claims the ground it builds on, so territory grows outward
        // as the town does rather than being fixed at founding.
        if (!contains(plot)) {
            claimRadius = BuildPlanner.claimRadiusFor(centre, plot,
                    arrangement().claimMargin());
        }

        BuildTask ordered = new BuildTask(type.id(), plot, type.workCost());
        ordered.setFacing(BuildPlanner.facingToward(plot, centre));
        buildQueue.add(ordered);
    }

    /** Graduates the settlement when its stage's conditions are met. */
    private void advanceStage(SimContext ctx) {
        StagePlanner.keepPostsFilled(this);
        if (!StagePlanner.readyToAdvance(this, ctx)) {
            return;
        }
        SettlementStage was = stage;
        stage = stage.next();
        if (stage == was) {
            return;
        }
        StagePlanner.crystallize(this, stage);
        logEvent(ctx.step(), name + " grows: " + was.pretty() + " becomes " + stage.pretty());
    }

    /**
     * The homestead's graduation meter: steps in a row the settlement has
     * genuinely fed itself — the whole larder covering the appetite, or growing.
     */
    private void trackFedStreak() {
        int larder = FoodPlanner.totalFood(this);
        int appetitePerStep = Math.max(1, population());
        if (larder >= appetitePerStep * StagePlanner.FED_WINDOW_STEPS) {
            fedStreak++;
        } else {
            fedStreak = 0;
        }
    }

    private void advanceBuildQueue(SimContext ctx) {
        countHeadStall();
        if (buildQueue.isEmpty()) {
            return;
        }
        int able = ableBuilders();
        // Pre-cut components from a working carpentry count as one more pair of
        // hands on every site -- the discount FOUNDING.md promises, applied to
        // the crew rather than the bill so one lever covers both fidelities.
        if (able > 0 && countBuildings("kingdoms:carpentry") > 0
                && JobPlanner.count(this, Profession.CARPENTER) > 0) {
            able++;
        }
        if (able == 0) {
            return;
        }
        int present = (int) residents.values().stream()
                .filter(p -> laboursAs(p, Profession.BUILDER) && !p.isTooWeakToWork()
                        && p.isEmbodied())
                .count();
        BuildTask current = buildQueue.getFirst();
        if (relocateIfUnsuitable(ctx, current)) {
            return;   // it moved; start on the new ground next step
        }

        if (isBuiltByHand(ctx, current, present)) {
            // Somebody is here to watch, so the masonry is the truth: this step
            // clears the builders to lay their share, and progress is whatever
            // they actually get down. Nothing finishes until the last block does,
            // which is what stops a completed task being stamped over work that
            // is still visibly going up.
            current.grantWork(current.workForStep(present));
            current.syncProgressToWork();
            if (!current.isVisuallyComplete()) {
                // Unless the hands have plainly stopped. Builders can be
                // embodied and standing on a loaded site and still lay nothing
                // for a good while: mob navigation cannot climb everything a
                // town builds on, and /civ step passes no game ticks at all, so
                // the player who typed it is the switch that turned the clock
                // off while nothing turned the hands on. Ten thousand steps of
                // that and everybody is dead.
                if (current.noteWatchedIdleStep() <= WATCHED_BUILD_GRACE_STEPS) {
                    return;
                }
                // Fall through to the clock, exactly as if nobody were here.
            }
        } else {
            // Nobody watching. Nothing to look at, so the clock runs instead and
            // the finished building materializes whole when a chunk next loads.
            List<String> missing = payForProgress(current, able);
            if (!missing.isEmpty()) {
                // Same rule as the watched path: run dry, go build the thing
                // that makes more. A town must not build for free merely
                // because nobody happened to be looking.
                //
                // Every shortage is offered, not just the first, because
                // requestProducer refuses a producer that already stands. A town
                // out of both timber and stone with a lumber camp in the square
                // used to name wood, be told it already has a lumber camp, and go
                // back to waiting — the stone that had actually stopped the work
                // was never once asked about, so no mine was ever ordered.
                for (String resource : missing) {
                    if (BuildPlanner.requestProducer(this, resource, ctx.step(), ctx.bridge())) {
                        break;
                    }
                }
                return;
            }
            current.addProgress(able);
            if (!current.isComplete()) {
                return;
            }
        }

        buildQueue.removeFirst();
        // The building now exists as far as the simulation is concerned. It stands
        // at the surveyed site if construction got far enough to survey one — and
        // counts as already drawn if the builders laid every block by hand, so
        // watched construction is never re-stamped by a placement pass.
        if (current.isUpgrade()) {
            // Raised in place: the same building, one level better. Recording a
            // second one here would leave the town believing it owns two.
            //
            // Matched on the plan position rather than the whole origin. A
            // building's x and z are its plot and never move; its y is wherever
            // the ground turned out to be, and setOriginY writes it again when
            // the structure is finally placed. Comparing all three meant an
            // upgrade whose target had settled at a different height simply
            // found nothing, dropped out of the loop, and threw away every unit
            // of work that had gone into it — leaving a town that believed it
            // had upgraded a building it had not touched.
            for (Building standing : buildings) {
                if (samePlot(standing.origin(), current.upgradeOf())) {
                    standing.setLevel(BuildPlanner.levelOf(current.blueprintId()));
                    standing.setBlueprintId(current.blueprintId());
                    standing.setFootprint(current.footprint());
                    standing.setMaterialized(current.isVisuallyComplete());
                    // Rebuilt in place, so it is whole by definition — and its
                    // old census belongs to a structure that no longer stands.
                    // This is also the repair path: a repair is an upgrade to
                    // the level the building already had.
                    standing.setDamage(0);
                    standing.clearCensus();
                    tallies.record(Tallies.BUILDINGS_RAISED);
                    return;
                }
            }
            return;   // it was pulled down while the work was under way
        }

        Building raised = new Building(
                current.blueprintId(), current.site(), ctx.step(), current.isVisuallyComplete());
        // Surveyed sites keep the height the builders actually worked to; only an
        // unsurveyed one may be snapped to the ground at placement time.
        raised.setSurveyed(current.siteY() != BuildTask.UNSET_SITE_Y);
        // A hand-built structure already knows its size from the survey; one built
        // out of sight learns it when it is finally placed.
        // A building finished out of sight has never been measured, so it has
        // no footprint — and without one it has no doorstep, and without a
        // doorstep no road can be run to it. That is why a town nobody had
        // visited never laid a single street, and why everything that makes
        // siting intelligent was inert there. The plot span is what the
        // catalogue set aside for this building, which is the same figure a
        // measured one comes back with; materialization overwrites it with the
        // real thing the moment the structure is actually drawn.
        raised.setFootprint(current.footprint().isKnown()
                ? current.footprint()
                : expectedFootprint(current));
        raised.setFacing(current.facing());
        buildings.add(raised);
        tallies.record(Tallies.BUILDINGS_RAISED);
    }

    /** Whether two origins name the same plot, whatever height each was read at. */
    private static boolean samePlot(SimPos a, SimPos b) {
        return a != null && b != null && a.x() == b.x() && a.z() == b.z();
    }

    /**
     * The footprint a building is expected to have, before anybody has measured it.
     *
     * <p>Provisional by construction and replaced by the measured one at
     * placement. The height is a storey rather than a guess at the real
     * elevation: nothing reads it before the structure is drawn, and claiming a
     * precise number nobody checked would be worse than claiming a plain one.
     */
    private Footprint expectedFootprint(BuildTask task) {
        int span = BuildPlanner.plotSpanOf(task.blueprintId(), catalogue);
        return new Footprint(task.site().y(), span, span, 4);
    }

    /**
     * Moves a never-drawn building off ground that turns out to be unfit.
     *
     * @return true if it moved, in which case nothing should be drawn this step
     */
    private boolean relocatePending(SimContext ctx, Building building) {
        if (building.isSurveyed() || building.level() > 1
                || !BuildPlanner.holdsGround(building.blueprintId())) {
            // Surveyed means somebody already built or saw it here; levelled
            // means it grew from something that stood here. Both belong where
            // they are, whatever the ground thinks.
            return false;
        }
        if (ctx.bridge().isSiteSuitable(building.origin(), BuildPlanner.PLOT_PROBE_RADIUS)) {
            return false;
        }
        int span = BuildPlanner.plotSpanOf(building.blueprintId(), catalogue);
        SimPos moved = chooseSite(ctx, span, building.role());
        if (moved.equals(building.origin())
                || (ctx.bridge().isLoaded(moved)
                        && !ctx.bridge().isSiteSuitable(moved, BuildPlanner.PLOT_PROBE_RADIUS))) {
            return false;   // nowhere better; draw it here and make the best of it
        }
        SimPos from = building.origin();
        building.setOrigin(new SimPos(moved.x(), ctx.bridge().surfaceHeight(moved), moved.z()));
        building.setFacing(BuildPlanner.facingToward(moved, centre));
        if (!contains(building.origin())) {
            claimRadius = BuildPlanner.claimRadiusFor(centre, building.origin());
        }
        logEvent(ctx.step(), "The ground at " + from + " turned out unfit; the "
                + building.blueprintId().substring(building.blueprintId().indexOf(':') + 1)
                + " moves to " + building.origin());
        return true;
    }

    /**
     * Whether this building has to be built rather than counted.
     *
     * <p>The test is whether builders exist in the world as entities right now. If
     * they do, they are the only thing that can raise a wall — no clock runs
     * alongside them, so a site nobody has walked to does not progress at all.
     *
     * <p>Asking about the builders rather than about the chunk matters, because
     * the two do not coincide. People are embodied within the observed radius
     * (96, released at 128), while chunks stay loaded out to simulation distance
     * (160 by default) and forever in spawn or force-loaded chunks. Keying on the
     * chunk left a band roughly 128–160 blocks out where the site was loaded but
     * every settler had been released — nobody to lay a block and no clock
     * either, so construction simply stopped. Keying on the builders closes it:
     * where there is a hand there is no clock, and where there is no hand the
     * clock is all there is.
     */
    /**
     * Charges the stores for a step of unwatched building.
     *
     * <p>Both shortages are reported, not the first one found. Returning on the
     * wood check meant a town short of both never mentioned stone at all, and the
     * caller has only one thing it can do with the answer — ask for the producer
     * that fixes it — so a shortage the town never names is a shortage it never
     * fixes. See the loop in {@link #advanceBuildQueue}.
     *
     * @return the resources that ran out, in the order they are worth asking
     *         about, or empty if the town could pay
     */
    private List<String> payForProgress(BuildTask task, int progress) {
        if (isFreeToBuild(task)) {
            return List.of();
        }
        int wood = BuildPlanner.WOOD_PER_WORK * progress;
        int stone = BuildPlanner.STONE_PER_WORK * progress;
        boolean shortOfWood = !pooled.has(TownStores.WOOD, wood);
        boolean shortOfStone = !pooled.has(TownStores.STONE, stone);
        if (shortOfWood && shortOfStone) {
            return List.of(TownStores.WOOD, TownStores.STONE);
        }
        if (shortOfWood) {
            return List.of(TownStores.WOOD);
        }
        if (shortOfStone) {
            return List.of(TownStores.STONE);
        }
        pooled.take(TownStores.WOOD, wood);
        pooled.take(TownStores.STONE, stone);
        return List.of();
    }

    /**
     * Whether this job is exempt from the materials it would otherwise cost.
     *
     * <p>Two bootstraps, one rule. A producer is free because a town that cannot
     * pay for the lumber camp can never have timber again — see
     * {@link BuildPlanner#requestProducer} — and a farm raised while the town is
     * starving is free for exactly the same reason: the building that ends the
     * famine must not be blocked by the famine. Ordinary times, ordinary prices;
     * a farm built by a comfortable town pays for itself like anything else.
     */
    private boolean isFreeToBuild(BuildTask task) {
        return BuildPlanner.PRODUCER_OF.containsValue(task.blueprintId())
                || (FoodPlanner.isSurvivalBuilding(task.blueprintId()) && isStarving());
    }

    /**
     * Times how long the queue head has gone nowhere, so a starving town can tell
     * a job that is merely slow from one that has stopped.
     *
     * <p>The question used to be whether the stores could pay for another step,
     * and that let exactly one job kill a town: a producer. Everything in
     * {@link BuildPlanner#PRODUCER_OF} is exempt from materials — see
     * {@link #isFreeToBuild} — so a bootstrapped mine was always "payable" and
     * the counter sat at zero however long it stood untouched. A founding party
     * ran out of stone, shoved a mine to the front, never got a single block laid
     * on it, and starved behind it with the farm it had already ordered parked
     * one place back.
     *
     * <p>So the measure is progress. Nothing is exempt from having to move: a
     * head is stalled when it is no further along than it was last step, whatever
     * it costs and whoever is or is not working it. That covers the unpayable
     * head the old rule caught, the free head it did not, and the head with no
     * able builder at all — {@link #advanceBuildQueue} gives up before it reaches
     * the till when everyone is too weak to lift a stone, and a head that is
     * never priced is a head that never looked stalled either.
     *
     * <p>The cost of measuring it this way is that a crew still walking to a
     * distant plot looks identical to a crew that is never coming: nothing in the
     * model moves until the view layer has surveyed the site and laid a block, so
     * a long walk can burn most of the patience. That is the trade, taken
     * deliberately — a town in a famine cannot spend forty seconds waiting on
     * faith, and a head it builds around keeps its plot and every unit of work it
     * had, so the worst case is a farm going up first.
     */
    private void countHeadStall() {
        BuildTask head = buildQueue.isEmpty() ? null : buildQueue.getFirst();
        if (head != stalledTask) {
            stalledTask = head;
            stalledProgress = head == null ? 0 : head.progress();
            stalledSteps = 0;
        }
        if (head == null) {
            return;
        }
        if (head.progress() > stalledProgress) {
            stalledProgress = head.progress();
            stalledSteps = 0;
            return;
        }
        stalledSteps++;
    }

    private int ableBuilders() {
        return (int) residents.values().stream()
                .filter(p -> laboursAs(p, Profession.BUILDER) && !p.isTooWeakToWork())
                .count();
    }

    /**
     * Moves a build off ground that turns out to be unfit for it.
     *
     * <p>Plots are chosen long before anybody can see them. The terrain test needs
     * loaded chunks and a growing town almost never has any, so the bridge answers
     * "suitable" to everything and the settlement lays out its whole village
     * blind. That was fine while nothing checked afterwards — and it is exactly
     * how farms ended up in lakes and huts ended up buried in hillsides, because
     * the first time the ground was ever actually looked at was when the building
     * was stamped into it.
     *
     * <p>So the question is asked again the moment the chunk is real, and before
     * a single block is moved. A site that fails now is swapped for a fresh plot
     * rather than built on.
     *
     * @return true if the task was moved
     */
    private boolean relocateIfUnsuitable(SimContext ctx, BuildTask task) {
        if (task.isUpgrade() || BuildPlanner.ACCESS_STAIRS.equals(task.blueprintId())) {
            return false;   // both belong exactly where they are
        }
        if (task.siteY() != BuildTask.UNSET_SITE_Y || task.workDone() > 0) {
            return false;   // already surveyed and under way; moving it now loses work
        }
        if (!ctx.bridge().isLoaded(task.origin())
                || ctx.bridge().isSiteSuitable(task.origin(), BuildPlanner.PLOT_PROBE_RADIUS)) {
            return false;
        }

        int span = BuildPlanner.plotSpanOf(task.blueprintId(), catalogue);
        SimPos moved = chooseSite(ctx, span, BuildingRole.of(task.blueprintId()));
        if (moved.equals(task.origin())) {
            return false;   // nowhere better; build it here and make the best of it
        }
        BuildTask replacement = new BuildTask(
                task.blueprintId(), moved, task.requiredWork());
        replacement.setFacing(BuildPlanner.facingToward(moved, centre));
        buildQueue.set(0, replacement);
        logEvent(ctx.step(), "The ground at " + task.origin()
                + " will not do; the site moves to " + moved);
        return true;
    }

    /**
     * Watched steps a build may sit without a block going down before the clock
     * takes over.
     *
     * <p>Same shape and the same number as the harvest and cutting graces. Being
     * watched must never starve a town, and it must not stop it building either.
     */
    public static final int WATCHED_BUILD_GRACE_STEPS = 12;

    private boolean isBuiltByHand(SimContext ctx, BuildTask task, int embodiedBuilders) {
        return embodiedBuilders > 0 && ctx.bridge().isLoaded(task.site());
    }

    /**
     * Draws any building the simulation knows about but the world does not yet show.
     *
     * <p>Deliberately decoupled from completion: a building finished in an unloaded
     * chunk is recorded immediately and painted in on a later step, once the chunk is
     * available. That is why returning to a settlement shows it already grown rather
     * than starting construction on arrival.
     */
    private void materializePending(SimContext ctx) {
        for (Building building : buildings) {
            if (building.isMaterialized()) {
                continue;
            }
            if (ctx.bridge().isLoaded(building.origin())) {
                // The last moment a building can still move. Everything before
                // this ran blind — plots are chosen and finished in unloaded
                // chunks, where the terrain test answers yes to anything — so
                // this is the FIRST time the ground under a grown-while-away
                // building is actually seen. Nothing is drawn yet, so moving the
                // record is free; a step later and it would mean demolition.
                if (relocatePending(ctx, building)) {
                    continue;
                }
                Footprint placed = ctx.bridge().materializeBlueprint(
                        building.blueprintId(), building.origin(), building.isSurveyed(),
                        building.facing());
                if (placed.isKnown()) {
                    // Where it really stands and how big it is, so everyone who
                    // walks here arrives and anything drawing it has its bounds.
                    building.setOriginY(placed.y());
                    building.setFootprint(placed);
                    building.setSurveyed(true);
                }
                building.setMaterialized(true);
            }
        }
    }

    /**
     * Holds the alarm while the memory lasts, then lets it fall.
     *
     * <p>One a step once the memory runs out, which reads as a town standing
     * down gradually: a raid of eight is still alarming for eight steps after
     * the last of them dies.
     */
    private void decayThreat() {
        if (sightingMemory > 0) {
            sightingMemory--;
            return;
        }
        if (threatLevel > 0) {
            threatLevel--;
        }
    }

    @Override
    public String toString() {
        return name + " [pop " + population() + ", threat " + threatLevel + "]";
    }
}
