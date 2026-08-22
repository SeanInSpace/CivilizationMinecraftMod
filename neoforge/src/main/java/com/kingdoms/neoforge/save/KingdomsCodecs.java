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
            Codec.INT.optionalFieldOf("carried", 0).forGetter(HaulTask::carried)
    ).apply(i, (fromStore, fromPos, toStore, toPos, requested, carried) -> {
        HaulTask task = new HaulTask(fromStore, fromPos, toStore, toPos, requested);
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
            // Absent means a save from before excavation was split out of the step
            // list. See the rewind below.
            Codec.INT.optionalFieldOf("dig_done", -1).forGetter(BuildTask::digDone)
    ).apply(i, (blueprint, origin, requiredWork, progress, siteY, prepared,
                stepsDone, stepProgress, workDone, planWork, planPlaceWork, pending, facing,
                digDone) -> {
        BuildTask task = new BuildTask(blueprint, origin, requiredWork);
        task.addProgress(progress);
        task.setSiteY(siteY);
        task.setSitePrepared(prepared);
        task.setPlan(planWork, planPlaceWork);
        task.setFacing(facing);
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
     * Culture, stage and the fed streak share one slot of the settlement codec.
     *
     * <p>The settlement group already sits at DFU's sixteen-field cap, and a
     * {@code MapCodec} used directly in a group flattens its fields into the
     * parent map without spending another slot or changing the wire format --
     * "culture" reads and writes exactly as it always did.
     */
    private record Flavor(String culture, String stage, int fedStreak,
                          boolean perimeterClosed, Optional<Perimeter> perimeter) {
        static Flavor of(Settlement s) {
            return new Flavor(s.cultureId(), s.stage().pretty(), s.fedStreak(),
                    s.perimeterClosed(), Optional.ofNullable(s.perimeter()));
        }
    }

    private static final Codec<Perimeter> PERIMETER = RecordCodecBuilder.create(i -> i.group(
            SIM_POS.listOf().fieldOf("vertices").forGetter(Perimeter::vertices),
            SIM_POS.listOf().fieldOf("gates").forGetter(Perimeter::gates),
            Codec.INT.optionalFieldOf("laid", 0).forGetter(Perimeter::laid)
    ).apply(i, Perimeter::new));

    private static final MapCodec<Flavor> FLAVOR = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("culture", Culture.DEFAULT.id()).forGetter(Flavor::culture),
            Codec.STRING.optionalFieldOf("stage", "").forGetter(Flavor::stage),
            Codec.INT.optionalFieldOf("fed_streak", 0).forGetter(Flavor::fedStreak),
            Codec.BOOL.optionalFieldOf("perimeter_closed", false).forGetter(Flavor::perimeterClosed),
            PERIMETER.optionalFieldOf("perimeter").forGetter(Flavor::perimeter)
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
            Codec.INT.optionalFieldOf("facing", 0).forGetter(Building::facing)
    ).apply(i, (blueprint, origin, step, materialized, food, surveyed, footprint, facing) -> {
        Building building = new Building(blueprint, origin, step, materialized);
        building.setFoodStored(food);
        building.setSurveyed(surveyed);
        building.setFootprint(footprint);
        building.setFacing(facing);
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
    private record Stores(Map<String, Integer> amounts, int food, int wood, int saplings, int stone) {

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
            return new Stores(settlement.stores().all(), 0, 0, 0, 0);
        }
    }

    private static final MapCodec<Stores> STORES = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("stores", Map.of()).forGetter(Stores::amounts),
            Codec.INT.optionalFieldOf("food", FoodPlanner.STARTING_PROVISIONS).forGetter(Stores::food),
            Codec.INT.optionalFieldOf("wood", 0).forGetter(Stores::wood),
            Codec.INT.optionalFieldOf("saplings", 0).forGetter(Stores::saplings),
            Codec.INT.optionalFieldOf("stone", 0).forGetter(Stores::stone)
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
        settlement.stores().restore(stores.toTownStores().all());
        settlement.tallies().restore(tallies);
        settlement.setCultureId(flavor.culture());
        // Saves from before stages existed carry no stage; they load as TOWN,
        // which is the behaviour they were built under. Only fresh charters camp.
        settlement.setStage(SettlementStage.parse(flavor.stage(), SettlementStage.TOWN));
        settlement.setFedStreak(flavor.fedStreak());
        settlement.setPerimeterClosed(flavor.perimeterClosed());
        flavor.perimeter().ifPresent(settlement::setPerimeter);
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
        settlements.forEach(kingdom::addSettlement);
        diplomacy.forEach(kingdom::setStandingWith);
        return kingdom;
    }));

    public static final Codec<List<Kingdom>> KINGDOM_LIST = KINGDOM.listOf();

    /** Convenience for the saved-data root. */
    public static Codec<Map<Kingdom.Id, Standing>> diplomacyCodec() {
        return Codec.unboundedMap(KINGDOM_ID, STANDING);
    }
}
