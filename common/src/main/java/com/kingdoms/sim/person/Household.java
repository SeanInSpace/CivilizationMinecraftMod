package com.kingdoms.sim.person;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A family. The unit that occupies a house and the unit that grows.
 *
 * <p>People do not reproduce individually — households do, and only when they have
 * a home with room left in it. That is the whole housing constraint: a family
 * living in a four-person house stops growing at four, and cannot grow again until
 * somebody moves into a new house.
 *
 * <p>{@link #home} is the origin of the building this family lives in, or null if
 * they are unhoused. Building origins are unique per settlement, so they serve as
 * the key.
 */
public final class Household {

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
    private final List<Person.Id> members = new ArrayList<>();
    private SimPos home;
    private int growthProgress;

    /** The family larder. A close member restocks it from the market. */
    private int pantry;

    public Household(Id id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
    }

    public Id id() {
        return id;
    }

    /** The family name. Children take it. */
    public String name() {
        return name;
    }

    public List<Person.Id> members() {
        return Collections.unmodifiableList(members);
    }

    public int size() {
        return members.size();
    }

    public void addMember(Person.Id personId) {
        members.add(Objects.requireNonNull(personId, "personId"));
    }

    public boolean removeMember(Person.Id personId) {
        return members.remove(personId);
    }

    public boolean contains(Person.Id personId) {
        return members.contains(personId);
    }

    /** Origin of this family's house, or null if unhoused. */
    public SimPos home() {
        return home;
    }

    public void setHome(SimPos home) {
        this.home = home;
    }

    public boolean isHoused() {
        return home != null;
    }

    public int growthProgress() {
        return growthProgress;
    }

    public void addGrowthProgress(int amount) {
        growthProgress += amount;
    }

    public void resetGrowthProgress() {
        growthProgress = 0;
    }

    public int pantry() {
        return pantry;
    }

    public void setPantry(int pantry) {
        this.pantry = Math.max(0, pantry);
    }

    @Override
    public String toString() {
        return "the " + name + "s (" + size() + (isHoused() ? " @ " + home : ", unhoused") + ")";
    }
}
