package com.civilization.sim.person;

import com.civilization.sim.geom.SimPos;

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

    /**
     * The rungs of the hunger ladder, which climbs one a step.
     *
     * <p>30+: hungry — eats what is carried, refills from the family pantry, and
     * walks to the nearest food when there is none at home. 60+: too weak to
     * work, so the tools go down and dinner comes first. 90+: starving.
     * {@link #HUNGER_MAX} is death, reached on the step hunger gets there with
     * nothing eaten — the 90–98 band is the whole death clock, and there is no
     * separate one. Debuffs are the town's business, not a person's: they are
     * applied only while the settlement itself is starving.
     */
    public static final int HUNGER_HUNGRY = 30;
    public static final int HUNGER_WEAK = 60;
    public static final int HUNGER_SEVERE = 90;
    public static final int HUNGER_MAX = 99;

    private final Id id;
    private final String name;
    private Profession profession;
    private SimPos position;

    /** 0 (fed) to {@link #HUNGER_MAX}, which is not survived. */
    private int hunger;

    /** What they are actually carrying. Real items, eaten from directly. */
    private final Inventory inventory = new Inventory();

    /**
     * What they have dug up and not yet put anywhere. See {@link Pockets}.
     *
     * <p>Deliberately not the same pocket as {@link #carriedMaterial}: one is a
     * load fetched from a store to be spent into a wall, the other is what came
     * out of the ground under them, and a settler routinely has both.
     */
    private final Pockets pockets = new Pockets();

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

    private String carriedMaterial;
    private int carriedLoad;

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
     * not paralyzed — but the forge exists to change that, and the quest board
     * counts the shortfall.
     */
    /**
     * What this person is carrying to a build site, and how much of it.
     *
     * <p>Building materials do not appear in a builder's hands out of nothing any
     * more: a load is drawn from the town's stores at the warehouse and spent
     * block by block. The stock leaves the ledger when it is picked up, so a load
     * in transit is genuinely out of the stores and a carrier killed on the road
     * takes it with them.
     */
    public String carriedMaterial() {
        return carriedMaterial;
    }

    public int carriedLoad() {
        return carriedLoad;
    }

    public void setCarry(String material, int load) {
        this.carriedMaterial = load > 0 ? material : null;
        this.carriedLoad = Math.max(0, load);
    }

    /** Whether this load can pay for one of the given material. */
    public boolean carries(String material) {
        return carriedLoad > 0 && material != null && material.equals(carriedMaterial);
    }

    /** Spends one from the load. */
    public void spendCarry() {
        carriedLoad = Math.max(0, carriedLoad - 1);
        if (carriedLoad == 0) {
            carriedMaterial = null;
        }
    }

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

    /**
     * What this person has broken out of the ground and is still holding.
     *
     * <p>Every block a citizen breaks yields its material into here, and what is
     * in here is walked to the town's stores. See {@code Spoil}.
     */
    public Pockets pockets() {
        return pockets;
    }

    public HaulTask haul() {
        return haul;
    }

    public void setHaul(HaulTask haul) {
        this.haul = haul;
    }

    @Override
    public String toString() {
        return name + " (" + profession + " @ " + position + ")";
    }
}
