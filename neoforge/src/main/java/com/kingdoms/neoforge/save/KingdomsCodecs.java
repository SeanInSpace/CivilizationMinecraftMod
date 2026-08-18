package com.kingdoms.neoforge.save;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.kingdom.Standing;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Settlement;
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

    public static final Codec<Person> PERSON = RecordCodecBuilder.create(i -> i.group(
            PERSON_ID.fieldOf("id").forGetter(Person::id),
            Codec.STRING.fieldOf("name").forGetter(Person::name),
            PROFESSION.fieldOf("profession").forGetter(Person::profession),
            SIM_POS.fieldOf("position").forGetter(Person::position),
            Codec.INT.optionalFieldOf("hunger", 0).forGetter(Person::hunger),
            Codec.INT.optionalFieldOf("food_carried", 0).forGetter(Person::foodCarried),
            Codec.INT.optionalFieldOf("starving_steps", 0).forGetter(Person::starvingSteps),
            HAUL_TASK.optionalFieldOf("haul").forGetter(p -> Optional.ofNullable(p.haul()))
    ).apply(i, (id, name, profession, position, hunger, carried, starving, haul) -> {
        Person person = new Person(id, name, profession, position);
        person.setHunger(hunger);
        person.setFoodCarried(carried);
        person.setStarvingSteps(starving);
        haul.ifPresent(person::setHaul);
        return person;
    }));

    public static final Codec<BuildTask> BUILD_TASK = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("blueprint").forGetter(BuildTask::blueprintId),
            SIM_POS.fieldOf("origin").forGetter(BuildTask::origin),
            Codec.INT.fieldOf("required_work").forGetter(BuildTask::requiredWork),
            Codec.INT.fieldOf("progress").forGetter(BuildTask::progress),
            Codec.INT.optionalFieldOf("site_y", BuildTask.UNSET_SITE_Y).forGetter(BuildTask::siteY),
            Codec.BOOL.optionalFieldOf("site_prepared", false).forGetter(BuildTask::isSitePrepared),
            Codec.INT.optionalFieldOf("blocks_placed", 0).forGetter(BuildTask::blocksPlaced),
            Codec.INT.optionalFieldOf("plan_size", 0).forGetter(BuildTask::planSize)
    ).apply(i, (blueprint, origin, requiredWork, progress, siteY, prepared, placed, planSize) -> {
        BuildTask task = new BuildTask(blueprint, origin, requiredWork);
        task.addProgress(progress);
        task.setSiteY(siteY);
        task.setSitePrepared(prepared);
        task.setBlocksPlaced(placed);
        task.setPlanSize(planSize);
        return task;
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
            SIM_POS.fieldOf("centre").forGetter(WorkArea::centre),
            Codec.INT.fieldOf("radius").forGetter(WorkArea::radius)
    ).apply(i, WorkArea::new));

    public static final Codec<SettlementEvent> SETTLEMENT_EVENT = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("step").forGetter(SettlementEvent::step),
            Codec.STRING.fieldOf("message").forGetter(SettlementEvent::message)
    ).apply(i, SettlementEvent::new));

    public static final Codec<Building> BUILDING = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("blueprint").forGetter(Building::blueprintId),
            SIM_POS.fieldOf("origin").forGetter(Building::origin),
            Codec.LONG.fieldOf("completed_on_step").forGetter(Building::completedOnStep),
            Codec.BOOL.fieldOf("materialized").forGetter(Building::isMaterialized),
            Codec.INT.optionalFieldOf("food", 0).forGetter(Building::foodStored)
    ).apply(i, (blueprint, origin, step, materialized, food) -> {
        Building building = new Building(blueprint, origin, step, materialized);
        building.setFoodStored(food);
        return building;
    }));

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
            Codec.INT.optionalFieldOf("food", FoodPlanner.STARTING_PROVISIONS).forGetter(Settlement::foodStock),
            Codec.INT.optionalFieldOf("wood", 0).forGetter(Settlement::woodStock),
            Codec.INT.optionalFieldOf("saplings", 0).forGetter(Settlement::saplingStock),
            WORK_AREA.optionalFieldOf("lumber_area").forGetter(s -> Optional.ofNullable(s.lumberArea()))
    ).apply(i, (id, name, centre, claimRadius, threatLevel, residents, buildQueue, buildings, households, events, food, wood, saplings, lumberArea) -> {
        Settlement settlement = new Settlement(id, name, centre, claimRadius);
        settlement.setThreatLevel(threatLevel);
        settlement.setFoodStock(food);
        settlement.setWoodStock(wood);
        settlement.setSaplingStock(saplings);
        lumberArea.ifPresent(settlement::setLumberArea);
        residents.forEach(settlement::addResident);
        buildQueue.forEach(settlement::enqueueBuild);
        buildings.forEach(settlement::addBuilding);
        households.forEach(settlement::addHousehold);
        events.forEach(e -> settlement.logEvent(e.step(), e.message()));
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
