package com.civilization.sim.person;

import com.civilization.sim.geom.SimPos;

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

    /**
     * Never more progress than the threshold it is progress toward.
     *
     * <p>Progress is banked while a family cannot act on it — a full house with
     * nowhere to move to, a town at its cap, a famine — and the threshold it is
     * banked against is not a constant: {@code PopulationPlanner.stepsPerBirthIn}
     * stretches it by how crowded the town is, so it <em>falls</em> when the
     * population does. Seventeen people died in one night on seed 8675309 and
     * every surviving family's banked progress was suddenly above a threshold that
     * had dropped under it, which is how {@code /civ info} came to read
     * "growth 45/24" — a numerator past its own denominator, for a counter whose
     * whole documented behavior is to hold at the line.
     *
     * <p>Held rather than reset, because a family that has waited is owed its
     * wait: the child arrives on the next step, which is what the threshold
     * falling ought to mean.
     */
    public void holdGrowthProgressAt(int threshold) {
        growthProgress = Math.min(growthProgress, Math.max(0, threshold));
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
