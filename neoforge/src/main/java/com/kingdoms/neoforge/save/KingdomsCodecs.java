package com.kingdoms.neoforge.save;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.kingdom.Standing;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.quest.Quest;
import com.kingdoms.sim.quest.QuestKind;
import com.kingdoms.sim.quest.QuestState;
import com.kingdoms.sim.quest.Reward;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.SettlementEvent;
import com.kingdoms.sim.settlement.WorkArea;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Serialization for the simulation types.
 *
 * <p>These live in the platform module rather than in {@code :common} on purpose:
 * {@code Codec} comes from Mojang's DataFixerUpper, and keeping {@code :common}
 * free of <em>all</em> external dependencies is what makes its tests run instantly
 * with no downloads.
 *
 * <p>If you later add a Fabric module, move this class into a shared module that
 * depends on {@code com.mojang:datafixerupper} — both loaders bundle it, so the
 * codecs themselves port unchanged.
 *
 * <p><strong>The save format is not frozen, and nothing here migrates.</strong>
 * A record that grows a field grows a field; a record that has outgrown one flat
 * list of fields is split into sub-records that say what they are. Worlds written
 * by an older build of the mod do not load, by design — the alternative was a
 * codec that kept a British save key for ever and carried four dead legacy
 * fields so that a save nobody still has would open.
 *
 * <p>The practical rule for a new field: give it {@code fieldOf} unless a town
 * that has never had the thing the field describes is meaningfully different from
 * a town whose value for it is the default. An {@code optionalFieldOf} with a
 * default here means "a fresh town honestly starts here", never "an old save
 * omits this".
 */
public final class KingdomsCodecs {

    private KingdomsCodecs() {
    }

    /** UUIDs as strings — verbose but human-readable in the save file, which is worth it while debugging. */
    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    /**
     * Enum codec that degrades gracefully. An unknown profession — from a
     * datapack that has since been removed, or a build of the mod that had one
     * more than this one — becomes IDLER rather than throwing and taking the
     * whole world with it.
     */
    private static final Codec<Profession> PROFESSION = Codec.STRING.xmap(
            name -> {
                try {
                    return Profession.valueOf(name);
                } catch (IllegalArgumentException e) {
                    return Profession.IDLER;
                }
            },
            Profession::name);

    private static final Codec<Standing> STANDING = Codec.STRING.xmap(
            name -> {
                try {
                    return Standing.valueOf(name);
                } catch (IllegalArgumentException e) {
                    return Standing.NEUTRAL;
                }
            },
            Standing::name);

    public static final Codec<SimPos> SIM_POS = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(SimPos::x),
            Codec.INT.fieldOf("y").forGetter(SimPos::y),
            Codec.INT.fieldOf("z").forGetter(SimPos::z)
    ).apply(i, SimPos::new));

    public static final Codec<Person.Id> PERSON_ID =
            UUID_CODEC.xmap(Person.Id::new, Person.Id::value);

    public static final Codec<Settlement.Id> SETTLEMENT_ID =
            UUID_CODEC.xmap(Settlement.Id::new, Settlement.Id::value);

    public static final Codec<Kingdom.Id> KINGDOM_ID =
            UUID_CODEC.xmap(Kingdom.Id::new, Kingdom.Id::value);

    private static final Codec<HaulTask.Store> HAUL_STORE = Codec.STRING.xmap(
            name -> {
                try {
                    return HaulTask.Store.valueOf(name);
                } catch (IllegalArgumentException e) {
                    return HaulTask.Store.GRANARY;
                }
            },
            HaulTask.Store::name);

    public static final Codec<HaulTask> HAUL_TASK = RecordCodecBuilder.create(i -> i.group(
            HAUL_STORE.fieldOf("from_store").forGetter(HaulTask::fromStore),
            SIM_POS.fieldOf("from_pos").forGetter(HaulTask::fromPos),
            HAUL_STORE.fieldOf("to_store").forGetter(HaulTask::toStore),
            SIM_POS.fieldOf("to_pos").forGetter(HaulTask::toPos),
            Codec.INT.fieldOf("requested").forGetter(HaulTask::requested),
            Codec.INT.optionalFieldOf("carried", 0).forGetter(HaulTask::carried),
            // Which goods the errand is for. Required: an errand is meaningless
            // without one, and "assume food" was only ever the right answer for
            // a save written before couriers carried anything else.
            Codec.STRING.fieldOf("resource").forGetter(HaulTask::resource)
    ).apply(i, (fromStore, fromPos, toStore, toPos, requested, carried, resource) -> {
        HaulTask task = new HaulTask(resource, fromStore, fromPos, toStore, toPos, requested);
        task.setCarried(carried);
        return task;
    }));

    public static final Codec<Inventory.Slot> INVENTORY_SLOT = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(Inventory.Slot::itemId),
            Codec.INT.fieldOf("count").forGetter(Inventory.Slot::count)
    ).apply(i, Inventory.Slot::new));

    public static final Codec<Person> PERSON = RecordCodecBuilder.create(i -> i.group(
            PERSON_ID.fieldOf("id").forGetter(Person::id),
            Codec.STRING.fieldOf("name").forGetter(Person::name),
            PROFESSION.fieldOf("profession").forGetter(Person::profession),
            SIM_POS.fieldOf("position").forGetter(Person::position),
            Codec.INT.optionalFieldOf("hunger", 0).forGetter(Person::hunger),
            INVENTORY_SLOT.listOf().optionalFieldOf("inventory", List.of())
                    .forGetter(p -> p.inventory().slots()),
            Codec.INT.optionalFieldOf("starving_steps", 0).forGetter(Person::starvingSteps),
            HAUL_TASK.optionalFieldOf("haul").forGetter(p -> Optional.ofNullable(p.haul())),
            Codec.BOOL.optionalFieldOf("has_tool", false).forGetter(Person::hasTool),
            Codec.STRING.optionalFieldOf("carry_material", "").forGetter(
                    person -> person.carriedMaterial() == null ? "" : person.carriedMaterial()),
            Codec.INT.optionalFieldOf("carry_load", 0).forGetter(Person::carriedLoad),
            // What they have dug up and not yet walked anywhere. Saved because an
            // armful is real stock that left the ground: a reload that emptied
            // everybody's pockets would quietly destroy a crew's afternoon of
            // clearing, and "goods only ever exist in one place" would stop being
            // true across a restart.
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("pockets", Map.of())
                    .forGetter(person -> person.pockets().all())
    ).apply(i, (id, name, profession, position, hunger, carried, starving, haul, hasTool,
                carryMaterial, carryLoad, pockets) -> {
        Person person = new Person(id, name, profession, position);
        person.setHunger(hunger);
        carried.forEach(slot -> person.inventory().restore(slot.itemId(), slot.count()));
        person.setStarvingSteps(starving);
        haul.ifPresent(person::setHaul);
        person.setHasTool(hasTool);
        person.setCarry(carryMaterial.isEmpty() ? null : carryMaterial, carryLoad);
        person.pockets().restore(pockets);
        return person;
    }));

    /**
     * The ground a job is being done on: the height the crew settled on for the
     * floor, and whether they have finished making it flat.
     */
    private record Site(int y, boolean prepared) {
        static Site of(BuildTask task) {
            return new Site(task.siteY(), task.isSitePrepared());
        }
    }

    private static final Codec<Site> SITE = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("y", BuildTask.UNSET_SITE_Y).forGetter(Site::y),
            Codec.BOOL.optionalFieldOf("prepared", false).forGetter(Site::prepared)
    ).apply(i, Site::new));

    /**
     * How far a build has actually got, in every unit the job is measured in.
     *
     * <p>Seven numbers that only mean anything together, and which used to sit
     * loose among the task's own fields. {@code digDone} is the excavation half
     * and {@code workDone} the masonry half of the same total; {@code stepsDone}
     * and {@code stepProgress} are the cursor into the block list; the two plan
     * figures are what the job was estimated at before anyone lifted a spade.
     */
    private record Work(int stepsDone, int stepProgress, int digDone, int workDone,
                        int pendingWork, int planWork, int planPlaceWork) {
        static Work of(BuildTask task) {
            return new Work(task.stepsDone(), task.stepProgress(), task.digDone(),
                    task.workDone(), task.pendingWork(), task.planWork(), task.planPlaceWork());
        }
    }

    private static final Codec<Work> WORK = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("steps_done", 0).forGetter(Work::stepsDone),
            Codec.INT.optionalFieldOf("step_progress", 0).forGetter(Work::stepProgress),
            // Required. This was optional and defaulted to a sentinel that meant
            // "a save from before excavation was split out of the step list", and
            // reading it triggered a rewind of the whole cursor. Nothing writes a
            // task without it now, so the sentinel and the rewind are both gone.
            Codec.INT.fieldOf("dig_done").forGetter(Work::digDone),
            Codec.INT.optionalFieldOf("work_done", 0).forGetter(Work::workDone),
            Codec.INT.optionalFieldOf("pending_work", 0).forGetter(Work::pendingWork),
            Codec.INT.optionalFieldOf("plan_work", 0).forGetter(Work::planWork),
            Codec.INT.optionalFieldOf("plan_place_work", 0).forGetter(Work::planPlaceWork)
    ).apply(i, Work::new));

    public static final Codec<BuildTask> BUILD_TASK = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("blueprint").forGetter(BuildTask::blueprintId),
            SIM_POS.fieldOf("origin").forGetter(BuildTask::origin),
            Codec.INT.optionalFieldOf("facing", 0).forGetter(BuildTask::facing),
            Codec.INT.fieldOf("required_work").forGetter(BuildTask::requiredWork),
            Codec.INT.fieldOf("progress").forGetter(BuildTask::progress),
            SITE.fieldOf("site").forGetter(Site::of),
            WORK.fieldOf("work").forGetter(Work::of),
            // Work booked against a building that already stands, and which kind
            // of it this is. A reloaded repair that had forgotten it was a repair
            // is an ordinary build of the same blueprint on the same spot -- so
            // the crew's first act is to excavate the footprint, which is to say
            // pull down the house they were sent to mend, and on finishing it the
            // town records a second building on the plot.
            SIM_POS.optionalFieldOf("upgrade_of").forGetter(
                    task -> Optional.ofNullable(task.upgradeOf())),
            Codec.BOOL.optionalFieldOf("repair", false).forGetter(BuildTask::isRepair)
    ).apply(i, (blueprint, origin, facing, requiredWork, progress, site, work, upgradeOf, repair) -> {
        BuildTask task = new BuildTask(blueprint, origin, requiredWork);
        task.addProgress(progress);
        task.setSiteY(site.y());
        task.setSitePrepared(site.prepared());
        task.setPlan(work.planWork(), work.planPlaceWork());
        task.setFacing(facing);
        upgradeOf.ifPresent(task::setUpgradeOf);
        task.setRepair(repair);
        task.setStepsDone(work.stepsDone());
        task.setStepProgress(work.stepProgress());
        task.setWorkDone(work.workDone());
        task.setPendingWork(work.pendingWork());
        task.setDigDone(work.digDone());
        return task;
    }));

    /**
     * A wall a town has replaced. The raised count travels with the line
     * because it is what says where there is anything to pull down: the tail a
     * ring never reached is ordinary ground, and whatever stands on it now
     * belongs to somebody else.
     */
    private static final Codec<Perimeter.Retired> RETIRED_LINE =
            RecordCodecBuilder.create(i -> i.group(
                    SIM_POS.listOf().fieldOf("vertices").forGetter(Perimeter.Retired::vertices),
                    Codec.INT.optionalFieldOf("laid", 0).forGetter(Perimeter.Retired::laid)
            ).apply(i, Perimeter.Retired::new));

    private static final Codec<Perimeter> PERIMETER = RecordCodecBuilder.create(i -> i.group(
            SIM_POS.listOf().fieldOf("vertices").forGetter(Perimeter::vertices),
            SIM_POS.listOf().fieldOf("gates").forGetter(Perimeter::gates),
            Codec.INT.optionalFieldOf("laid", 0).forGetter(Perimeter::laid),
            // The lines a re-staked wall has superseded, which are still posts
            // in the ground until the layer has pulled them down. It has to
            // survive a save or a town reloaded mid-demolition keeps its old
            // wall for ever, with the new one outside it -- two walls, which is
            // the one thing this must not leave behind. Required and usually an
            // empty list: a town that has never moved its wall says so.
            RETIRED_LINE.listOf().fieldOf("retired").forGetter(Perimeter::retired),
            // The step the standing line was staked on, which is what says
            // whether the town may move it yet. Saved because a restart is not
            // a generation. Note that the counter it will be compared against
            // is NOT saved -- SimWorld starts every session at step zero -- so
            // this comes back looking like a step in the future; Perimeter.ageAt
            // is where that is read for what it is.
            Codec.LONG.fieldOf("staked_on").forGetter(Perimeter::stakedOn),
            // How far along the retired line the crew has got pulling it up. A
            // prefix like "laid", and saved for the same reason: a reload that
            // forgot it would send builders back to the head of a line they have
            // already taken half of, to walk the whole of it again finding
            // nothing.
            Codec.INT.fieldOf("pulled").forGetter(Perimeter::pulled)
    ).apply(i, KingdomsCodecs::perimeterOf));

    /**
     * A perimeter read back with its demolition where it left off.
     *
     * <p>{@code pulled} is set rather than constructed because it is bounded by
     * {@link Perimeter#retiredPositions()}, which cannot be worked out until the
     * lines it is derived from are in hand.
     */
    private static Perimeter perimeterOf(
            List<com.kingdoms.sim.geom.SimPos> vertices,
            List<com.kingdoms.sim.geom.SimPos> gates, int laid,
            List<Perimeter.Retired> retired, long stakedOn, int pulled) {
        Perimeter ring = new Perimeter(vertices, gates, laid, retired, stakedOn);
        ring.setPulled(pulled);
        return ring;
    }

    private static final Codec<PathNetwork.Segment> PATH_SEGMENT =
            RecordCodecBuilder.create(i -> i.group(
                    SIM_POS.fieldOf("from").forGetter(PathNetwork.Segment::from),
                    SIM_POS.fieldOf("to").forGetter(PathNetwork.Segment::to),
                    // How wide the stretch is. Required: a footpath and a street
                    // are the same two endpoints and a different road, and
                    // guessing the narrower of them was only ever a way of
                    // reading a save from before streets were planned.
                    Codec.INT.fieldOf("width").forGetter(PathNetwork.Segment::width)
            ).apply(i, PathNetwork.Segment::new));

    /**
     * The road network, stored as its segments and the buildings already joined
     * to it. Both halves matter on reload: without the joined set a restart
     * re-plans every road the town ever laid, which is how the old in-memory
     * version quietly did the whole job again on every server start.
     */
    private static final Codec<PathNetwork> PATH_NETWORK = RecordCodecBuilder.create(i -> i.group(
            PATH_SEGMENT.listOf().optionalFieldOf("segments", List.of())
                    .forGetter(PathNetwork::segments),
            SIM_POS.listOf().optionalFieldOf("joined", List.of())
                    .forGetter(PathNetwork::joined),
            // Which stretches somebody has actually walked out and opened. A
            // road is a job now, not a line on a plan, so without this a reload
            // would send the builders out to open every street the town already
            // has -- the same mistake the joined set exists to prevent, one
            // level down.
            Codec.INT.listOf().fieldOf("opened").forGetter(PathNetwork::openedSegments),
            // How many buildings the planned streets were last laid for. Minus
            // one on a network that has never laid any, which matches no count
            // and so lays them on the next step rather than never.
            Codec.INT.fieldOf("streets_laid_for").forGetter(PathNetwork::streetsLaidFor),
            // Which planned streets were routed onto the ground and which the
            // ground refused. Persisted because routing is not cheap and, more
            // importantly, because its answer is a road: re-deriving it after a
            // reload could pick a different line through the same hill and lay
            // the street twice. The network is the authority once a road exists.
            Codec.INT.listOf().optionalFieldOf("streets_routed", List.of())
                    .forGetter(PathNetwork::routedStreets),
            Codec.INT.listOf().optionalFieldOf("streets_refused", List.of())
                    .forGetter(PathNetwork::refusedStreets),
            // How far the stones have actually gone down, as opposed to how far
            // the town has walked its streets out. This lived in the drawing
            // sweep's memory, so every server start forgot that the roads had
            // ever been drawn and re-laid the lot -- which for a town with
            // nobody left in it is a road crew nobody could have hired.
            Codec.INT.fieldOf("laid_through").forGetter(PathNetwork::laidThrough)
    ).apply(i, (segments, joined, opened, streetsLaidFor, routed, refused, laidThrough) -> {
        PathNetwork network = new PathNetwork(segments, joined);
        network.restoreOpened(opened);
        network.setStreetsLaidFor(streetsLaidFor);
        network.restoreStreets(routed, refused);
        network.setLaidThrough(laidThrough);
        return network;
    }));

    public static final Codec<Household.Id> HOUSEHOLD_ID =
            UUID_CODEC.xmap(Household.Id::new, Household.Id::value);

    public static final Codec<Household> HOUSEHOLD = RecordCodecBuilder.create(i -> i.group(
            HOUSEHOLD_ID.fieldOf("id").forGetter(Household::id),
            Codec.STRING.fieldOf("name").forGetter(Household::name),
            PERSON_ID.listOf().fieldOf("members").forGetter(Household::members),
            SIM_POS.optionalFieldOf("home").forGetter(h -> Optional.ofNullable(h.home())),
            Codec.INT.fieldOf("growth").forGetter(Household::growthProgress),
            Codec.INT.optionalFieldOf("pantry", 0).forGetter(Household::pantry)
    ).apply(i, (id, name, members, home, growth, pantry) -> {
        Household household = new Household(id, name);
        members.forEach(household::addMember);
        home.ifPresent(household::setHome);
        household.addGrowthProgress(growth);
        household.setPantry(pantry);
        return household;
    }));

    public static final Codec<WorkArea> WORK_AREA = RecordCodecBuilder.create(i -> i.group(
            SIM_POS.fieldOf("center").forGetter(WorkArea::center),
            Codec.INT.fieldOf("radius").forGetter(WorkArea::radius)
    ).apply(i, WorkArea::new));

    public static final Codec<SettlementEvent> SETTLEMENT_EVENT = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("step").forGetter(SettlementEvent::step),
            Codec.STRING.fieldOf("message").forGetter(SettlementEvent::message)
    ).apply(i, SettlementEvent::new));

    public static final Codec<Footprint> FOOTPRINT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("y").forGetter(Footprint::y),
            Codec.INT.fieldOf("w").forGetter(Footprint::width),
            Codec.INT.fieldOf("d").forGetter(Footprint::depth),
            Codec.INT.fieldOf("h").forGetter(Footprint::height)
    ).apply(i, Footprint::new));

    /**
     * Where a building stands and how much room it takes: its corner, the box a
     * survey found around it, and the quarter it turns to face.
     *
     * <p>The three answer one question — which ground is this building's — and a
     * reader wanting the plot no longer has to know that two of them live next
     * to the food count.
     */
    private record Plot(SimPos origin, Footprint footprint, int facing) {
        static Plot of(Building building) {
            return new Plot(building.origin(), building.footprint(), building.facing());
        }
    }

    private static final Codec<Plot> PLOT = RecordCodecBuilder.create(i -> i.group(
            SIM_POS.fieldOf("origin").forGetter(Plot::origin),
            FOOTPRINT.optionalFieldOf("footprint", Footprint.UNKNOWN).forGetter(Plot::footprint),
            Codec.INT.optionalFieldOf("facing", 0).forGetter(Plot::facing)
    ).apply(i, Plot::new));

    /**
     * How sound a building is: what the last survey counted whole, and what has
     * happened to it since.
     */
    private record Condition(int soundCensus, int damage) {
        static Condition of(Building building) {
            return new Condition(building.soundCensus(), building.damage());
        }
    }

    private static final Codec<Condition> CONDITION = RecordCodecBuilder.create(i -> i.group(
            // Required, and usually the uncounted sentinel: a building nobody
            // has stood in front of has no census, which is a different fact
            // from a building counted and found whole.
            Codec.INT.fieldOf("sound_census").forGetter(Condition::soundCensus),
            Codec.INT.fieldOf("damage").forGetter(Condition::damage)
    ).apply(i, Condition::new));

    /**
     * What the ground a building works has been quietly doing, in the units the
     * building counts it in.
     *
     * <p>All four are the only record of an unwatched town's trade: a farm's
     * ripeness, a lumber camp's standing and regrowing timber, a mine's seam. A
     * town can go whole sessions with nobody within sight of it, so a ledger
     * rebuilt from the blocks at every load would be rebuilt from blocks nobody
     * has generated — which is to say from nothing.
     *
     * <p>{@code stand} and {@code seam} are required and carry their UNCOUNTED
     * sentinels rather than zero, because "nobody has counted this" and "felled
     * bare" are opposite facts and zero is the second one.
     */
    private record Ledgers(int ripeHundredths, int stand, int growing, int seam) {
        static Ledgers of(Building building) {
            return new Ledgers(building.ripeHundredths(), building.standThousandths(),
                    building.growingThousandths(), building.stoneSeam());
        }
    }

    private static final Codec<Ledgers> LEDGERS = RecordCodecBuilder.create(i -> i.group(
            // Hundredths of a crop block. See Field.
            Codec.INT.fieldOf("ripe").forGetter(Ledgers::ripeHundredths),
            // Thousandths of a tree, standing and coming up. See Stand.
            Codec.INT.fieldOf("stand").forGetter(Ledgers::stand),
            Codec.INT.fieldOf("growing").forGetter(Ledgers::growing),
            // Blocks of stone left in the seam. See Seam.
            Codec.INT.fieldOf("seam").forGetter(Ledgers::seam)
    ).apply(i, Ledgers::new));

    public static final Codec<Building> BUILDING = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("blueprint").forGetter(Building::blueprintId),
            PLOT.fieldOf("plot").forGetter(Plot::of),
            Codec.LONG.fieldOf("completed_on_step").forGetter(Building::completedOnStep),
            Codec.BOOL.fieldOf("materialized").forGetter(Building::isMaterialized),
            Codec.BOOL.optionalFieldOf("surveyed", false).forGetter(Building::isSurveyed),
            // A debt the first drawing pays: a seeded camp has to have its wood
            // planted around it. A world can be saved between the seeding and the
            // day a player walks close enough to see it, so the debt has to
            // survive the save or the town wakes up on bare ground. Required,
            // because "written into existence" and "built by somebody" is a
            // distinction a save has to carry rather than assume.
            Codec.BOOL.fieldOf("seeded").forGetter(Building::isSeeded),
            Codec.INT.optionalFieldOf("food", 0).forGetter(Building::foodStored),
            // Where the town's goods actually are. Optional and omitted when
            // empty, because most buildings hold nothing and a map apiece
            // would be written for every hut in every town.
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stores", Map.of())
                    .forGetter(b -> b.hasStores() ? b.stores().all() : Map.<String, Integer>of()),
            CONDITION.fieldOf("condition").forGetter(Condition::of),
            LEDGERS.fieldOf("ledgers").forGetter(Ledgers::of)
    ).apply(i, (blueprint, plot, step, materialized, surveyed, seeded, food, held,
                condition, ledgers) -> {
        Building building = new Building(blueprint, plot.origin(), step, materialized);
        building.setSeeded(seeded);
        building.setFoodStored(food);
        building.setSurveyed(surveyed);
        building.setFootprint(plot.footprint());
        building.setFacing(plot.facing());
        building.setSoundCensus(condition.soundCensus());
        building.setDamage(condition.damage());
        building.setRipeHundredths(ledgers.ripeHundredths());
        building.setStandThousandths(ledgers.stand());
        building.setGrowingThousandths(ledgers.growing());
        building.setStoneSeam(ledgers.seam());
        if (!held.isEmpty()) {
            building.stores().restore(held);
        }
        return building;
    }));

    private static final Codec<QuestKind> QUEST_KIND =
            Codec.STRING.xmap(QuestKind::parse, QuestKind::name);

    private static final Codec<QuestState> QUEST_STATE = Codec.STRING.xmap(
            name -> QuestState.parse(name, QuestState.OFFERED),
            QuestState::name);

    private static final Codec<Reward> REWARD = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("coin", 0).forGetter(Reward::coin),
            // The ledger word for a rider off the town's own shelves, and empty
            // for the ordinary case. Optional because most rewards are coin.
            Codec.STRING.optionalFieldOf("goods", "").forGetter(Reward::goods),
            Codec.INT.optionalFieldOf("goods_amount", 0).forGetter(Reward::goodsAmount),
            Codec.INT.optionalFieldOf("standing", 0).forGetter(Reward::standing)
    ).apply(i, Reward::new));

    /**
     * The notice itself: what is being asked, in what words, and where.
     *
     * <p>The words are stored rather than re-derived, and that is the whole
     * reason this is a record of its own. A town asks for grain because it is
     * starving; by the time anybody reads the board it may have eaten. What the
     * town said when it asked is what it asked, so it travels with the quest and
     * into the file.
     */
    private record Notice(QuestKind kind, String title, String detail, String target,
                          Optional<SimPos> place) {
        static Notice of(Quest quest) {
            return new Notice(quest.kind(), quest.title(), quest.detail(), quest.target(),
                    Optional.ofNullable(quest.place()));
        }
    }

    private static final Codec<Notice> NOTICE = RecordCodecBuilder.create(i -> i.group(
            QUEST_KIND.fieldOf("kind").forGetter(Notice::kind),
            Codec.STRING.fieldOf("title").forGetter(Notice::title),
            Codec.STRING.fieldOf("detail").forGetter(Notice::detail),
            Codec.STRING.optionalFieldOf("target", "").forGetter(Notice::target),
            // Absent for the kinds that are not anywhere in particular — a
            // delivery happens at the storehouse, wherever that is today.
            SIM_POS.optionalFieldOf("place").forGetter(Notice::place)
    ).apply(i, Notice::new));

    public static final Codec<Quest> QUEST = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Quest::id),
            NOTICE.fieldOf("notice").forGetter(Notice::of),
            Codec.INT.fieldOf("amount").forGetter(Quest::amount),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(Quest::progress),
            REWARD.fieldOf("reward").forGetter(Quest::reward),
            Codec.LONG.fieldOf("offered_on").forGetter(Quest::offeredOn),
            // When it goes stale. Saved rather than re-derived from the offer
            // step, because accepting moves it -- a job somebody is doing gets a
            // longer leash than the ask it came off.
            Codec.LONG.fieldOf("expires_on").forGetter(Quest::expiresOn),
            QUEST_STATE.fieldOf("state").forGetter(Quest::state),
            // Whose job it is. Absent for anything still on the board, which is
            // a real state and not a missing field.
            UUID_CODEC.optionalFieldOf("accepted_by").forGetter(
                    quest -> Optional.ofNullable(quest.acceptedBy()))
    ).apply(i, (id, notice, amount, progress, reward, offeredOn, expiresOn, state,
                acceptedBy) -> new Quest(id, notice.kind(), notice.title(),
            notice.detail(), notice.target(), notice.place().orElse(null), amount,
            progress, reward, offeredOn, expiresOn, state, acceptedBy.orElse(null))));

    /**
     * The terms a town exists under: whose people raised it, how far along it
     * is, which of that people's arrangements it was laid out in, and the two
     * flags that say it was written rather than built.
     */
    private record Charter(String culture, String stage, String layout,
                           boolean drawnOnly, boolean seededRoadsOwed) {
        static Charter of(Settlement s) {
            return new Charter(s.cultureId(), s.stage().pretty(),
                    // The id the settlement holds rather than the arrangement it
                    // resolves to, the same way "culture" is. Layouts.of answers
                    // an id it does not know with rings, so resolving on the way
                    // out would quietly rewrite a datapack's arrangement -- or
                    // one from a newer build of the mod -- into a village,
                    // permanently and in the file.
                    s.layoutId(),
                    s.isDrawnOnly(), s.seededRoadsOwed());
        }
    }

    private static final Codec<Charter> CHARTER = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("culture", Culture.DEFAULT.id()).forGetter(Charter::culture),
            Codec.STRING.fieldOf("stage").forGetter(Charter::stage),
            // Which of its people's arrangements this town was laid out in. A
            // culture carries several now and picks between them by hashing the
            // center, so the answer has to be written down rather than worked
            // out again: a town that grew half its streets under one derivation
            // and half under another would be neither shape. Required — saving
            // is an asking, and a town always has an answer by the time it is
            // written.
            Codec.STRING.fieldOf("layout").forGetter(Charter::layout),
            // A town drawn by /civ buildtest, which must stay a drawing across a
            // save. Without this it reloaded as an ordinary settlement and began
            // planning on top of the render -- quietly destroying the one thing
            // the instrument exists to hold still.
            Codec.BOOL.optionalFieldOf("drawn_only", false).forGetter(Charter::drawnOnly),
            // A town world generation wrote down whose streets have not been
            // walked out yet. It has to survive a save for the same reason the
            // lumber camp's wood does: a town generated in one session and found
            // in another is exactly the town that still owes them, and a flag
            // that reset to false on load would strand its roads for good.
            Codec.BOOL.fieldOf("seeded_roads_owed").forGetter(Charter::seededRoadsOwed)
    ).apply(i, Charter::new));

    /**
     * What the town owns and how well it has been eating.
     *
     * <p>{@code stores} is the loose pile alone — the rest of the town's goods
     * are saved by the buildings holding them, so writing the total here as well
     * would restore every log twice. One map rather than a field per resource, so
     * that a new resource is a datapack change rather than a codec change.
     */
    private record Holdings(Map<String, Integer> goods, int treasury,
                            Map<String, Integer> tallies, int fedStreak) {
        static Holdings of(Settlement s) {
            return new Holdings(s.loosePile().all(), s.treasury(), s.tallies().all(),
                    s.fedStreak());
        }
    }

    private static final Codec<Holdings> HOLDINGS = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("stores", Map.of()).forGetter(Holdings::goods),
            Codec.INT.fieldOf("treasury").forGetter(Holdings::treasury),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("tallies", Map.of()).forGetter(Holdings::tallies),
            Codec.INT.optionalFieldOf("fed_streak", 0).forGetter(Holdings::fedStreak)
    ).apply(i, Holdings::new));

    /**
     * What stands between the town and whatever is outside it: how badly it is
     * being pressed, whether its ring is closed, and the ring itself.
     */
    private record Defense(int threatLevel, boolean perimeterClosed,
                           Optional<Perimeter> perimeter) {
        static Defense of(Settlement s) {
            return new Defense(s.threatLevel(), s.perimeterClosed(),
                    Optional.ofNullable(s.perimeter()));
        }
    }

    private static final Codec<Defense> DEFENSE = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("threat_level").forGetter(Defense::threatLevel),
            Codec.BOOL.optionalFieldOf("perimeter_closed", false).forGetter(Defense::perimeterClosed),
            // Absent means a town that has never staked a line, which is a real
            // state and not a missing field.
            PERIMETER.optionalFieldOf("perimeter").forGetter(Defense::perimeter)
    ).apply(i, Defense::new));

    /**
     * Everything the town has built, is building, or works: the queue, the
     * standing buildings, the plot cursor that says where the next one goes, the
     * roads, and the two areas its outdoor trades are worked in.
     */
    private record Works(List<BuildTask> buildQueue, List<Building> buildings, int nextPlot,
                         Optional<PathNetwork> paths, Optional<WorkArea> lumberArea,
                         Optional<WorkArea> mineArea) {
        static Works of(Settlement s) {
            return new Works(s.buildQueue(), s.buildings(), s.nextPlotIndex(),
                    s.paths().isEmpty() && s.paths().joined().isEmpty()
                            ? Optional.empty() : Optional.of(s.paths()),
                    Optional.ofNullable(s.lumberArea()),
                    Optional.ofNullable(s.mineArea()));
        }
    }

    private static final Codec<Works> WORKS = RecordCodecBuilder.create(i -> i.group(
            BUILD_TASK.listOf().fieldOf("build_queue").forGetter(Works::buildQueue),
            BUILDING.listOf().fieldOf("buildings").forGetter(Works::buildings),
            // Where the next plot is taken from. Required: a cursor derived from
            // the building count instead is a cursor that forgets every plot the
            // town offered and refused.
            Codec.INT.fieldOf("next_plot").forGetter(Works::nextPlot),
            PATH_NETWORK.optionalFieldOf("paths").forGetter(Works::paths),
            WORK_AREA.optionalFieldOf("lumber_area").forGetter(Works::lumberArea),
            WORK_AREA.optionalFieldOf("mine_area").forGetter(Works::mineArea)
    ).apply(i, Works::new));

    /**
     * The noticeboard: what the town is asking for, what it remembers being
     * done, and what it thinks of whoever did it.
     *
     * <p>A group of its own rather than three more fields under {@code holdings},
     * because standing is not a holding — it is not spent, cannot run out, and
     * belongs to a person rather than to the town. The three together are one
     * subject, and it is the subject a player interacts with.
     */
    private record Quests(List<Quest> offered, List<Quest> done,
                          Map<UUID, Integer> standing) {
        static Quests of(Settlement s) {
            return new Quests(s.quests().offered(), s.quests().completed(),
                    s.standing().all());
        }
    }

    private static final Codec<Quests> QUESTS = RecordCodecBuilder.create(i -> i.group(
            QUEST.listOf().optionalFieldOf("offered", List.of()).forGetter(Quests::offered),
            QUEST.listOf().optionalFieldOf("done", List.of()).forGetter(Quests::done),
            // Who has earned what here. Per settlement rather than per kingdom:
            // helping a village on one coast buys nothing on the other, which is
            // the entire point of a standing being this town's.
            Codec.unboundedMap(UUID_CODEC, Codec.INT)
                    .optionalFieldOf("standing", Map.of()).forGetter(Quests::standing)
    ).apply(i, Quests::new));

    public static final Codec<Settlement> SETTLEMENT = RecordCodecBuilder.create(i -> i.group(
            SETTLEMENT_ID.fieldOf("id").forGetter(Settlement::id),
            Codec.STRING.fieldOf("name").forGetter(Settlement::name),
            SIM_POS.fieldOf("center").forGetter(Settlement::center),
            Codec.INT.fieldOf("claim_radius").forGetter(Settlement::claimRadius),
            CHARTER.fieldOf("charter").forGetter(Charter::of),
            PERSON.listOf().fieldOf("residents").forGetter(s -> List.copyOf(s.residents())),
            HOUSEHOLD.listOf().optionalFieldOf("households", List.of()).forGetter(Settlement::households),
            SETTLEMENT_EVENT.listOf().optionalFieldOf("events", List.of()).forGetter(Settlement::events),
            HOLDINGS.fieldOf("holdings").forGetter(Holdings::of),
            DEFENSE.fieldOf("defense").forGetter(Defense::of),
            WORKS.fieldOf("works").forGetter(Works::of),
            QUESTS.fieldOf("quests").forGetter(Quests::of)
    ).apply(i, (id, name, center, claimRadius, charter, residents, households, events,
                holdings, defense, works, quests) -> {
        Settlement settlement = new Settlement(id, name, center, claimRadius);
        settlement.setThreatLevel(defense.threatLevel());
        settlement.loosePile().restore(holdings.goods());
        settlement.setTreasury(holdings.treasury());
        settlement.tallies().restore(holdings.tallies());
        settlement.setCultureId(charter.culture());
        settlement.setLayoutId(charter.layout());
        // An unknown stage name -- from a datapack, or a build of the mod that
        // had one more -- reads as TOWN rather than refusing the whole world.
        settlement.setStage(SettlementStage.parse(charter.stage(), SettlementStage.TOWN));
        settlement.setDrawnOnly(charter.drawnOnly());
        settlement.setSeededRoadsOwed(charter.seededRoadsOwed());
        settlement.setFedStreak(holdings.fedStreak());
        settlement.setPerimeterClosed(defense.perimeterClosed());
        defense.perimeter().ifPresent(settlement::setPerimeter);
        works.paths().ifPresent(settlement::setPaths);
        works.lumberArea().ifPresent(settlement::setLumberArea);
        works.mineArea().ifPresent(settlement::setMineArea);
        residents.forEach(settlement::addResident);
        works.buildQueue().forEach(settlement::enqueueBuild);
        works.buildings().forEach(settlement::addBuilding);
        households.forEach(settlement::addHousehold);
        events.forEach(e -> settlement.logEvent(e.step(), e.message()));
        settlement.quests().restore(quests.offered(), quests.done());
        settlement.standing().restore(quests.standing());
        // After the buildings, because adding one can push the cursor past it.
        settlement.setNextPlotIndex(works.nextPlot());
        return settlement;
    }));

    public static final Codec<Kingdom> KINGDOM = RecordCodecBuilder.create(i -> i.group(
            KINGDOM_ID.fieldOf("id").forGetter(Kingdom::id),
            Codec.STRING.fieldOf("name").forGetter(Kingdom::name),
            Codec.STRING.fieldOf("culture").forGetter(Kingdom::cultureId),
            SETTLEMENT.listOf().fieldOf("settlements").forGetter(k -> List.copyOf(k.settlements())),
            Codec.unboundedMap(KINGDOM_ID, STANDING).fieldOf("diplomacy").forGetter(Kingdom::diplomacy)
    ).apply(i, (id, name, culture, settlements, diplomacy) -> {
        Kingdom kingdom = new Kingdom(id, name, culture);
        // restore, not add: adding is founding, and founding stamps the
        // kingdom's culture over the one this settlement was just decoded with.
        settlements.forEach(kingdom::restoreSettlement);
        diplomacy.forEach(kingdom::setStandingWith);
        return kingdom;
    }));

    public static final Codec<List<Kingdom>> KINGDOM_LIST = KINGDOM.listOf();

    /** Convenience for the saved-data root. */
    public static Codec<Map<Kingdom.Id, Standing>> diplomacyCodec() {
        return Codec.unboundedMap(KINGDOM_ID, STANDING);
    }
}
