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

    private final Id id;
    private final String name;
    private Profession profession;
    private SimPos position;

    /**
     * Whether a real entity currently represents this person in the world.
     * Owned by the platform layer; the simulation only reads it to decide
     * whether movement needs to be visually plausible or can simply teleport.
     */
    private boolean embodied;

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

    public boolean isEmbodied() {
        return embodied;
    }

    public void setEmbodied(boolean embodied) {
        this.embodied = embodied;
    }

    @Override
    public String toString() {
        return name + " (" + profession + " @ " + position + ")";
    }
}
