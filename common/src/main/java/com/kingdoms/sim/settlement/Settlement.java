package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
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

    private final Map<Person.Id, Person> residents = new LinkedHashMap<>();
    private final List<BuildTask> buildQueue = new ArrayList<>();

    /** Everything this settlement has finished building, in completion order. */
    private final List<Building> buildings = new ArrayList<>();

    /** Families. The unit that occupies a house and the unit that grows. */
    private final List<Household> households = new ArrayList<>();

    /** Bounded history — raids, losses, sightings. Oldest entries fall off. */
    public static final int MAX_EVENTS = 20;
    private final ArrayDeque<SettlementEvent> events = new ArrayDeque<>();

    /** Content, not save state — not serialized. */
    private List<BuildingType> catalogue = BuildCatalogue.DEFAULT;

    /**
     * How threatened this settlement currently is, driving guard behaviour and
     * off-screen combat resolution. Rises when hostiles are detected, decays over time.
     */
    private int threatLevel;

    /** Food banked in the granary. The founding party arrives provisioned. */
    private int foodStock = FoodPlanner.STARTING_PROVISIONS;

    /** Timber in the town's stores, felled by lumberjacks. */
    private int woodStock;

    /** Saplings on hand for replanting what has been cut. */
    private int saplingStock;

    /** Where the lumber camp may work, or null until one is built. */
    private WorkArea lumberArea;

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

    public int claimRadius() {
        return claimRadius;
    }

    public void setClaimRadius(int claimRadius) {
        this.claimRadius = claimRadius;
    }

    public int threatLevel() {
        return threatLevel;
    }

    public int foodStock() {
        return foodStock;
    }

    public void setFoodStock(int foodStock) {
        this.foodStock = Math.max(0, foodStock);
    }

    public int woodStock() {
        return woodStock;
    }

    public void setWoodStock(int woodStock) {
        this.woodStock = Math.max(0, woodStock);
    }

    public int saplingStock() {
        return saplingStock;
    }

    public void setSaplingStock(int saplingStock) {
        this.saplingStock = Math.max(0, saplingStock);
    }

    public WorkArea lumberArea() {
        return lumberArea;
    }

    public void setLumberArea(WorkArea lumberArea) {
        this.lumberArea = lumberArea;
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
        return (int) buildings.stream().filter(b -> b.blueprintId().equals(blueprintId)).count();
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
        planNextBuild(ctx);
        advanceBuildQueue(ctx);
        materializePending(ctx);
        FoodPlanner.advance(this, ctx);
        HaulPlanner.advance(this, ctx);
        LumberPlanner.advance(this, ctx);
        JobPlanner.retrainOne(this);
        PopulationPlanner.advance(this, ctx);
        decayThreat();
        // After decay so a sustained hostile presence holds threat at its level
        // rather than oscillating one below it.
        RaidPlanner.advance(this, ctx);
    }

    /**
     * Decides what to build next, if anything.
     *
     * <p>One project at a time: if something is already queued, the settlement is
     * busy and does not reconsider. That keeps behaviour legible — a settlement
     * finishes what it started — and means {@link #countBuildings} can ignore work
     * in progress without double-counting.
     */
    private void planNextBuild(SimContext ctx) {
        if (!buildQueue.isEmpty()) {
            return;
        }
        BuildPlanner.chooseNext(this, catalogue).ifPresent(type -> {
            SimPos flat = BuildPlanner.plotFor(centre, buildings.size());

            // Snap to the terrain when the chunk is available; otherwise the
            // centre's height stands in and the world snaps again at placement.
            SimPos plot = new SimPos(flat.x(), ctx.bridge().surfaceHeight(flat), flat.z());

            // A settlement claims the ground it builds on, so territory grows outward
            // as the town does rather than being fixed at founding.
            if (!contains(plot)) {
                claimRadius = BuildPlanner.claimRadiusFor(centre, plot);
            }

            buildQueue.add(new BuildTask(type.id(), plot, type.workCost()));
        });
    }

    private void advanceBuildQueue(SimContext ctx) {
        if (buildQueue.isEmpty()) {
            return;
        }
        int able = (int) residents.values().stream()
                .filter(p -> p.profession() == Profession.BUILDER && !p.isTooWeakToWork())
                .count();
        if (able == 0) {
            return;
        }
        int present = (int) residents.values().stream()
                .filter(p -> p.profession() == Profession.BUILDER && !p.isTooWeakToWork()
                        && p.isEmbodied())
                .count();
        BuildTask current = buildQueue.getFirst();

        if (isBuiltByHand(ctx, current, present)) {
            // Somebody is here to watch, so the masonry is the truth: this step
            // clears the builders to lay their share, and progress is whatever
            // they actually get down. Nothing finishes until the last block does,
            // which is what stops a completed task being stamped over work that
            // is still visibly going up.
            current.grantWork(current.workForStep(present));
            current.syncProgressToWork();
            if (!current.isVisuallyComplete()) {
                return;
            }
        } else {
            // Nobody watching. Nothing to look at, so the clock runs instead and
            // the finished building materializes whole when a chunk next loads.
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
        buildings.add(new Building(
                current.blueprintId(), current.site(), ctx.step(), current.isVisuallyComplete()));
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
                ctx.bridge().materializeBlueprint(building.blueprintId(), building.origin());
                building.setMaterialized(true);
            }
        }
    }

    private void decayThreat() {
        if (threatLevel > 0) {
            threatLevel--;
        }
    }

    @Override
    public String toString() {
        return name + " [pop " + population() + ", threat " + threatLevel + "]";
    }
}
