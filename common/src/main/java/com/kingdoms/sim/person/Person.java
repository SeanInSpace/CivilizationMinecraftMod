package com.kingdoms.sim.person;

import com.kingdoms.sim.geom.SimPos;

import java.util.Objects;
import java.util.UUID;

/**
 * A single inhabitant.
 *
 * <p><strong>This is the source of truth for a person, not an entity.</strong>
 * A {@code Person} exists whether or not any chunk is loaded and whether or not
 * a player is anywhere nearby. The platform layer may spawn a temporary mob to
 * <em>represent</em> this record when a player is close enough to see it, and
 * writes any resulting state back here before despawning it.
 *
 * <p>Never store authoritative state on the entity. The entity is a view.
 */
public final class Person {

    /** Stable identity that survives entity despawn, chunk unload, and save/load. */
    public record Id(UUID value) {
        public Id {
            Objects.requireNonNull(value, "value");
        }

        public static Id random() {
            return new Id(UUID.randomUUID());
        }
    }

    /** Hunger 30+: hungry. 60+: too weak to work, debuffed. 90+: severe. */
    public static final int HUNGER_HUNGRY = 30;
    public static final int HUNGER_WEAK = 60;
    public static final int HUNGER_SEVERE = 90;
    public static final int HUNGER_MAX = 99;

    private final Id id;
    private final String name;
    private Profession profession;
    private SimPos position;

    /** 0 (fed) to {@link #HUNGER_MAX}. Held at the cap while starving. */
    private int hunger;

    /** What they are actually carrying. Real items, eaten from directly. */
    private final Inventory inventory = new Inventory();

    /** Consecutive steps spent at maximum hunger. Death comes when it runs out. */
    private int starvingSteps;

    /** The load they are fetching or delivering, or null when not hauling. */
    private HaulTask haul;

    /**
     * Whether a real entity currently represents this person in the world.
     * Owned by the platform layer; the simulation only reads it to decide
     * whether movement needs to be visually plausible or can simply teleport.
     */
    private boolean embodied;

    /** Issued a tool by the smith; see {@code SmithPlanner}. */
    private boolean hasTool;

    public Person(Id id, String name, Profession profession, SimPos position) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.profession = Objects.requireNonNull(profession, "profession");
        this.position = Objects.requireNonNull(position, "position");
    }

    public Id id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Profession profession() {
        return profession;
    }

    public void setProfession(Profession profession) {
        this.profession = Objects.requireNonNull(profession, "profession");
    }

    public SimPos position() {
        return position;
    }

    public void setPosition(SimPos position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    /**
     * Whether this person has been issued a tool from the town's rack.
     *
     * <p>A worker without one still works — an unequipped town should be poorer,
     * not paralysed — but the forge exists to change that, and the quest board
     * counts the shortfall.
     */
    public boolean hasTool() {
        return hasTool;
    }

    public void setHasTool(boolean hasTool) {
        this.hasTool = hasTool;
    }

    public boolean isEmbodied() {
        return embodied;
    }

    public void setEmbodied(boolean embodied) {
        this.embodied = embodied;
    }

    public int hunger() {
        return hunger;
    }

    public void setHunger(int hunger) {
        this.hunger = Math.max(0, Math.min(HUNGER_MAX, hunger));
    }

    public void addHunger(int amount) {
        setHunger(hunger + amount);
    }

    /** Too hungry to farm, haul, or build. */
    public boolean isTooWeakToWork() {
        return hunger >= HUNGER_WEAK;
    }

    public Inventory inventory() {
        return inventory;
    }

    public HaulTask haul() {
        return haul;
    }

    public void setHaul(HaulTask haul) {
        this.haul = haul;
    }

    public int starvingSteps() {
        return starvingSteps;
    }

    public void setStarvingSteps(int starvingSteps) {
        this.starvingSteps = Math.max(0, starvingSteps);
    }

    @Override
    public String toString() {
        return name + " (" + profession + " @ " + position + ")";
    }
}
