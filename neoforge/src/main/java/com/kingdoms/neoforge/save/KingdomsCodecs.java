package com.kingdoms.neoforge.save;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.kingdom.Standing;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.SettlementEvent;
import com.kingdoms.sim.settlement.WorkArea;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
 */
public final class KingdomsCodecs {

    private KingdomsCodecs() {
    }

    /** UUIDs as strings — verbose but human-readable in the save file, which is worth it while debugging. */
    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    /**
     * Enum codec that degrades gracefully. An unknown profession in an old save
     * becomes IDLER rather than throwing and taking the whole world with it.
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
            // Optional and defaulted to food, because every errand saved before
            // couriers existed was one, and a load in transit must survive the
            // reload that taught the game about timber.
            Codec.STRING.optionalFieldOf("resource", TownStores.FOOD)
                    .forGetter(HaulTask::resource)
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
            Codec.INT.optionalFieldOf("carry_load", 0).forGetter(Person::carriedLoad)
    ).apply(i, (id, name, profession, position, hunger, carried, starving, haul, hasTool,
                carryMaterial, carryLoad) -> {
        Person person = new Person(id, name, profession, position);
        person.setHunger(hunger);
        carried.forEach(slot -> person.inventory().restore(slot.itemId(), slot.count()));
        person.setStarvingSteps(starving);
        haul.ifPresent(person::setHaul);
        person.setHasTool(hasTool);
        person.setCarry(carryMaterial.isEmpty() ? null : carryMaterial, carryLoad);
        return person;
    }));

    public static final Codec<BuildTask> BUILD_TASK = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("blueprint").forGetter(BuildTask::blueprintId),
            SIM_POS.fieldOf("origin").forGetter(BuildTask::origin),
            Codec.INT.fieldOf("required_work").forGetter(BuildTask::requiredWork),
            Codec.INT.fieldOf("progress").forGetter(BuildTask::progress),
            Codec.INT.optionalFieldOf("site_y", BuildTask.UNSET_SITE_Y).forGetter(BuildTask::siteY),
            Codec.BOOL.optionalFieldOf("site_prepared", false).forGetter(BuildTask::isSitePrepared),
            Codec.INT.optionalFieldOf("steps_done", 0).forGetter(BuildTask::stepsDone),
            Codec.INT.optionalFieldOf("step_progress", 0).forGetter(BuildTask::stepProgress),
            Codec.INT.optionalFieldOf("work_done", 0).forGetter(BuildTask::workDone),
            Codec.INT.optionalFieldOf("plan_work", 0).forGetter(BuildTask::planWork),
            Codec.INT.optionalFieldOf("plan_place_work", 0).forGetter(BuildTask::planPlaceWork),
            Codec.INT.optionalFieldOf("pending_work", 0).forGetter(BuildTask::pendingWork),
            Codec.INT.optionalFieldOf("facing", 0).forGetter(BuildTask::facing),
            // Work booked against a building that already stands, and which kind of
            // it this is. Both were left out while upgrading was the only thing that
            // set them and nothing produced one any more; repairs revived the field
            // and made the omission dangerous. A reloaded repair that had forgotten
            // it was a repair is an ordinary build of the same blueprint on the same
            // spot -- so the crew's first act is to excavate the footprint, which is
            // to say pull down the house they were sent to mend, and on finishing it
            // the town records a second building on the plot.
            SIM_POS.optionalFieldOf("upgrade_of").forGetter(
                    task -> Optional.ofNullable(task.upgradeOf())),
            Codec.BOOL.optionalFieldOf("repair", false).forGetter(BuildTask::isRepair),
            // Absent means a save from before excavation was split out of the step
            // list. See the rewind below.
            Codec.INT.optionalFieldOf("dig_done", -1).forGetter(BuildTask::digDone)
    ).apply(i, (blueprint, origin, requiredWork, progress, siteY, prepared,
                stepsDone, stepProgress, workDone, planWork, planPlaceWork, pending, facing,
                upgradeOf, repair, digDone) -> {
        BuildTask task = new BuildTask(blueprint, origin, requiredWork);
        task.addProgress(progress);
        task.setSiteY(siteY);
        task.setSitePrepared(prepared);
        task.setPlan(planWork, planPlaceWork);
        task.setFacing(facing);
        upgradeOf.ifPresent(task::setUpgradeOf);
        task.setRepair(repair);
        if (digDone < 0) {
            // An older save. Its step cursor indexed a combined dig-and-lay list;
            // the same number now indexes masonry alone, so resuming from it would
            // skip straight past however many courses the digging used to account
            // for. Rewind the visible half instead: laying over blocks that already
            // stand is harmless, and a building with a wall missing is not.
            task.setStepsDone(0);
            task.setStepProgress(0);
            task.setWorkDone(0);
            task.setPendingWork(0);
        } else {
            task.setStepsDone(stepsDone);
            task.setStepProgress(stepProgress);
            task.setWorkDone(workDone);
            task.setPendingWork(pending);
            task.setDigDone(digDone);
        }
        return task;
    }));

    /**
     * Everything that would not fit shares one slot of the settlement codec:
     * culture, stage, the fed streak, the perimeter and the roads.
     *
     * <p>The settlement group already sits at DFU's sixteen-field cap, and a
     * {@code MapCodec} used directly in a group flattens its fields into the
     * parent map without spending another slot or changing the wire format --
     * "culture" reads and writes exactly as it always did.
     */
    private record Flavor(String culture, String stage, int fedStreak,
                          boolean perimeterClosed, Optional<Perimeter> perimeter,
                          Optional<PathNetwork> paths, boolean drawnOnly,
                          Optional<String> layout) {
        static Flavor of(Settlement s) {
            return new Flavor(s.cultureId(), s.stage().pretty(), s.fedStreak(),
                    s.perimeterClosed(), Optional.ofNullable(s.perimeter()),
                    s.paths().isEmpty() && s.paths().joined().isEmpty()
                            ? Optional.empty() : Optional.of(s.paths()),
                    s.isDrawnOnly(),
                    // Always written, whether the town has been told its
                    // arrangement or is still reading it off its people: this is
                    // where the derived answer stops being derived. Only saves
                    // from before the field existed come back without one, which
                    // is the whole of the compatibility rule below.
                    Optional.of(s.layoutId()));
        }
    }

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
            // the one thing this must not leave behind. Absent from every world
            // saved before a town could outgrow its ring, and an empty list is
            // exactly right for those: they have one wall and always had.
            RETIRED_LINE.listOf().optionalFieldOf("retired", List.of())
                    .forGetter(Perimeter::retired),
            // The step the standing line was staked on, which is what says
            // whether the town may move it yet. Saved because a restart is not
            // a generation. Note that the counter it will be compared against
            // is NOT saved -- SimWorld starts every session at step zero -- so
            // this comes back looking like a step in the future; Perimeter.ageAt
            // is where that is read for what it is. Absent means a world saved
            // before walls had an age, and zero is the honest answer for those.
            Codec.LONG.optionalFieldOf("staked_on", 0L).forGetter(Perimeter::stakedOn)
    ).apply(i, Perimeter::new));

    private static final Codec<PathNetwork.Segment> PATH_SEGMENT =
            RecordCodecBuilder.create(i -> i.group(
                    SIM_POS.fieldOf("from").forGetter(PathNetwork.Segment::from),
                    SIM_POS.fieldOf("to").forGetter(PathNetwork.Segment::to),
                    // Absent in every world saved before streets were planned,
                    // and those are all footpaths, so the old width is the
                    // default rather than a migration.
                    Codec.INT.optionalFieldOf("width", PathNetwork.TRACK_WIDTH)
                            .forGetter(PathNetwork.Segment::width)
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
            // level down. Optional, so a save from before roads were work loads
            // with none opened and re-opens them as its builders get to them.
            Codec.INT.listOf().optionalFieldOf("opened", List.of())
                    .forGetter(PathNetwork::openedSegments),
            // How many buildings the planned streets were last laid for. Absent
            // on a world saved before streets existed, and minus one is right for
            // those: it matches no count, so they lay theirs on the next step
            // rather than never.
            Codec.INT.optionalFieldOf("streetsLaidFor", -1)
                    .forGetter(PathNetwork::streetsLaidFor),
            // Which planned streets were routed onto the ground and which the
            // ground refused. Persisted because routing is not cheap and, more
            // importantly, because its answer is a road: re-deriving it after a
            // reload could pick a different line through the same hill and lay
            // the street twice. The network is the authority once a road exists.
            Codec.INT.listOf().optionalFieldOf("streetsRouted", List.of())
                    .forGetter(PathNetwork::routedStreets),
            Codec.INT.listOf().optionalFieldOf("streetsRefused", List.of())
                    .forGetter(PathNetwork::refusedStreets)
    ).apply(i, (segments, joined, opened, streetsLaidFor, routed, refused) -> {
        PathNetwork network = new PathNetwork(segments, joined);
        network.restoreOpened(opened);
        network.setStreetsLaidFor(streetsLaidFor);
        network.restoreStreets(routed, refused);
        return network;
    }));

    private static final MapCodec<Flavor> FLAVOR = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("culture", Culture.DEFAULT.id()).forGetter(Flavor::culture),
            Codec.STRING.optionalFieldOf("stage", "").forGetter(Flavor::stage),
            Codec.INT.optionalFieldOf("fed_streak", 0).forGetter(Flavor::fedStreak),
            Codec.BOOL.optionalFieldOf("perimeter_closed", false).forGetter(Flavor::perimeterClosed),
            PERIMETER.optionalFieldOf("perimeter").forGetter(Flavor::perimeter),
            PATH_NETWORK.optionalFieldOf("paths").forGetter(Flavor::paths),
            // A town drawn by /civ buildtest, which must stay a drawing across a
            // save. Without this it reloaded as an ordinary settlement and began
            // planning on top of the render -- quietly destroying the one thing
            // the instrument exists to hold still.
            Codec.BOOL.optionalFieldOf("drawn_only", false).forGetter(Flavor::drawnOnly),
            // Which of its people's arrangements this town was laid out in. A
            // culture carries several now and picks between them by hashing the
            // centre, so the answer has to be written down rather than worked
            // out again: a town that grew half its streets under one derivation
            // and half under another would be neither shape.
            //
            // Written as the id the settlement holds rather than the arrangement
            // it resolves to, the same way "culture" is. Layouts.of answers an
            // id it does not know with rings, so resolving on the way out would
            // quietly rewrite a datapack's arrangement -- or one from a newer
            // build of the mod -- into a village, permanently and in the file.
            Codec.STRING.optionalFieldOf("layout").forGetter(Flavor::layout)
    ).apply(i, Flavor::new));

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
            SIM_POS.fieldOf("centre").forGetter(WorkArea::centre),
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

    public static final Codec<Building> BUILDING = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("blueprint").forGetter(Building::blueprintId),
            SIM_POS.fieldOf("origin").forGetter(Building::origin),
            Codec.LONG.fieldOf("completed_on_step").forGetter(Building::completedOnStep),
            Codec.BOOL.fieldOf("materialized").forGetter(Building::isMaterialized),
            Codec.INT.optionalFieldOf("food", 0).forGetter(Building::foodStored),
            Codec.BOOL.optionalFieldOf("surveyed", false).forGetter(Building::isSurveyed),
            FOOTPRINT.optionalFieldOf("footprint", Footprint.UNKNOWN).forGetter(Building::footprint),
            Codec.INT.optionalFieldOf("facing", 0).forGetter(Building::facing),
            // Where the town's goods actually are. Optional and omitted when
            // empty, because most buildings hold nothing and a map apiece
            // would be written for every hut in every town.
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stores", Map.of())
                    .forGetter(b -> b.hasStores() ? b.stores().all() : Map.<String, Integer>of()),
            // The building's condition. Both optional so every save written
            // before buildings could be damaged still loads: an old record comes
            // back uncounted and undamaged, and is counted afresh the first time
            // somebody is there to look at it.
            Codec.INT.optionalFieldOf("sound_census", Building.UNCOUNTED)
                    .forGetter(Building::soundCensus),
            Codec.INT.optionalFieldOf("damage", 0).forGetter(Building::damage)
    ).apply(i, (blueprint, origin, step, materialized, food, surveyed, footprint, facing, held,
                census, damage) -> {
        Building building = new Building(blueprint, origin, step, materialized);
        building.setFoodStored(food);
        building.setSurveyed(surveyed);
        building.setFootprint(footprint);
        building.setFacing(facing);
        building.setSoundCensus(census);
        building.setDamage(damage);
        if (!held.isEmpty()) {
            building.stores().restore(held);
        }
        return building;
    }));

    /**
     * The four running totals, gathered so the settlement codec stays inside
     * {@code group()}'s sixteen-field ceiling.
     *
     * <p>A {@link MapCodec} rather than a nested object on purpose: its fields are
     * written flat into the settlement, so this costs one slot instead of four and
     * every save written before it existed still reads.
     */
    /**
     * The town ledger, plus the four flat fields it grew out of.
     *
     * <p>Written as a single {@code stores} map so a new resource never needs a
     * codec change. The legacy keys are still read — a save written before the
     * ledger existed carries its food, timber and stone across — and written back
     * out as zero-defaulted duplicates only when the map is absent, which it never
     * is once a world has been saved again.
     */
    private record Stores(Map<String, Integer> amounts, int food, int wood, int saplings,
                          int stone, int treasury) {

        TownStores toTownStores() {
            TownStores out = new TownStores();
            out.restore(amounts);
            // Legacy fields fill in only what the map did not carry.
            if (!amounts.containsKey(TownStores.FOOD)) {
                out.set(TownStores.FOOD, food);
            }
            if (!amounts.containsKey(TownStores.WOOD)) {
                out.set(TownStores.WOOD, wood);
            }
            if (!amounts.containsKey(TownStores.SAPLINGS)) {
                out.set(TownStores.SAPLINGS, saplings);
            }
            if (!amounts.containsKey(TownStores.STONE)) {
                out.set(TownStores.STONE, stone);
            }
            return out;
        }

        static Stores of(Settlement settlement) {
            // The loose pile alone. The rest of the town's goods are saved by
            // the buildings holding them, so writing the total here as well
            // would restore every log twice.
            return new Stores(settlement.loosePile().all(), 0, 0, 0, 0, settlement.treasury());
        }
    }

    private static final MapCodec<Stores> STORES = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("stores", Map.of()).forGetter(Stores::amounts),
            Codec.INT.optionalFieldOf("food", FoodPlanner.STARTING_PROVISIONS).forGetter(Stores::food),
            Codec.INT.optionalFieldOf("wood", 0).forGetter(Stores::wood),
            Codec.INT.optionalFieldOf("saplings", 0).forGetter(Stores::saplings),
            Codec.INT.optionalFieldOf("stone", 0).forGetter(Stores::stone),
            // Rides here rather than in the settlement group, which is already
            // at the sixteen fields group() allows. Optional, so a save written
            // before the town had money loads with an empty treasury and earns
            // its first coin from the next thing it produces.
            Codec.INT.optionalFieldOf("treasury", Settlement.FOUNDING_TREASURY)
                    .forGetter(Stores::treasury)
    ).apply(i, Stores::new));

    // "buildings" is optional so saves written before it existed still load.
    public static final Codec<Settlement> SETTLEMENT = RecordCodecBuilder.create(i -> i.group(
            SETTLEMENT_ID.fieldOf("id").forGetter(Settlement::id),
            Codec.STRING.fieldOf("name").forGetter(Settlement::name),
            SIM_POS.fieldOf("centre").forGetter(Settlement::centre),
            Codec.INT.fieldOf("claim_radius").forGetter(Settlement::claimRadius),
            Codec.INT.fieldOf("threat_level").forGetter(Settlement::threatLevel),
            PERSON.listOf().fieldOf("residents").forGetter(s -> List.copyOf(s.residents())),
            BUILD_TASK.listOf().fieldOf("build_queue").forGetter(Settlement::buildQueue),
            BUILDING.listOf().optionalFieldOf("buildings", List.of()).forGetter(Settlement::buildings),
            HOUSEHOLD.listOf().optionalFieldOf("households", List.of()).forGetter(Settlement::households),
            SETTLEMENT_EVENT.listOf().optionalFieldOf("events", List.of()).forGetter(Settlement::events),
            STORES.forGetter(Stores::of),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("tallies", Map.of()).forGetter(s -> s.tallies().all()),
            FLAVOR.forGetter(Flavor::of),
            WORK_AREA.optionalFieldOf("lumber_area").forGetter(s -> Optional.ofNullable(s.lumberArea())),
            WORK_AREA.optionalFieldOf("mine_area").forGetter(s -> Optional.ofNullable(s.mineArea())),
            Codec.INT.optionalFieldOf("next_plot", -1).forGetter(Settlement::nextPlotIndex)
    ).apply(i, (id, name, centre, claimRadius, threatLevel, residents, buildQueue, buildings, households, events, stores, tallies, flavor, lumberArea, mineArea, nextPlot) -> {
        Settlement settlement = new Settlement(id, name, centre, claimRadius);
        settlement.setThreatLevel(threatLevel);
        settlement.loosePile().restore(stores.toTownStores().all());
        settlement.setTreasury(stores.treasury());
        settlement.tallies().restore(tallies);
        settlement.setCultureId(flavor.culture());
        // A save written before the layout was recorded takes the head of its
        // people's list, which is the arrangement that people has always built
        // in. Deriving one from the centre instead would rearrange every town
        // already standing in somebody's world, which is exactly what a new
        // arrangement is not allowed to do.
        settlement.setLayoutId(flavor.layout()
                .orElseGet(() -> Culture.of(flavor.culture()).layouts().get(0)));
        // Saves from before stages existed carry no stage; they load as TOWN,
        // which is the behaviour they were built under. Only fresh charters camp.
        settlement.setStage(SettlementStage.parse(flavor.stage(), SettlementStage.TOWN));
        settlement.setDrawnOnly(flavor.drawnOnly());
        settlement.setFedStreak(flavor.fedStreak());
        settlement.setPerimeterClosed(flavor.perimeterClosed());
        flavor.perimeter().ifPresent(settlement::setPerimeter);
        flavor.paths().ifPresent(settlement::setPaths);
        lumberArea.ifPresent(settlement::setLumberArea);
        mineArea.ifPresent(settlement::setMineArea);
        residents.forEach(settlement::addResident);
        buildQueue.forEach(settlement::enqueueBuild);
        buildings.forEach(settlement::addBuilding);
        households.forEach(settlement::addHousehold);
        events.forEach(e -> settlement.logEvent(e.step(), e.message()));
        // Saves written before the cursor existed carry on from their building count.
        settlement.setNextPlotIndex(nextPlot >= 0 ? nextPlot : buildings.size());
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
