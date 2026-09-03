package com.kingdoms.sim.person;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What somebody is actually carrying.
 *
 * <p>Real items, not a number: a settler holds three bread and an apple, eats the
 * apple, and you can hand them a loaf yourself. Items are named by their game id
 * as a plain string, which keeps this loader-free — the platform layer maps the
 * id to an {@code Item} when it needs to draw or hand over a stack.
 *
 * <p>Deliberately small. A settler is a villager with pockets, not a chest.
 */
public final class Inventory {

    /** Distinct kinds a settler can carry at once. */
    public static final int SLOTS = 6;

    /** Most of any one kind in a slot. */
    public static final int STACK = 16;

    /** One kind of item and how many are held. */
    public record Slot(String itemId, int count) {
        public Slot {
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    private final List<Slot> slots = new ArrayList<>();

    public List<Slot> slots() {
        return Collections.unmodifiableList(slots);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public int count(String itemId) {
        for (Slot slot : slots) {
            if (slot.itemId().equals(itemId)) {
                return slot.count();
            }
        }
        return 0;
    }

    public int total() {
        return slots.stream().mapToInt(Slot::count).sum();
    }

    /**
     * Adds what will fit.
     *
     * @return how many were actually taken in
     */
    public int add(String itemId, int amount) {
        Objects.requireNonNull(itemId, "itemId");
        if (amount <= 0) {
            return 0;
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.itemId().equals(itemId)) {
                int room = STACK - slot.count();
                int taken = Math.min(room, amount);
                if (taken > 0) {
                    slots.set(i, new Slot(itemId, slot.count() + taken));
                }
                return taken;
            }
        }
        if (slots.size() >= SLOTS) {
            return 0;
        }
        int taken = Math.min(STACK, amount);
        slots.add(new Slot(itemId, taken));
        return taken;
    }

    /**
     * Takes what is there.
     *
     * @return how many were actually removed
     */
    public int remove(String itemId, int amount) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (!slot.itemId().equals(itemId)) {
                continue;
            }
            int taken = Math.min(slot.count(), Math.max(0, amount));
            if (slot.count() - taken <= 0) {
                slots.remove(i);
            } else {
                slots.set(i, new Slot(itemId, slot.count() - taken));
            }
            return taken;
        }
        return 0;
    }

    /** Restores a slot when loading from disk. */
    public void restore(String itemId, int count) {
        if (count > 0 && slots.size() < SLOTS) {
            slots.add(new Slot(itemId, Math.min(STACK, count)));
        }
    }

    /**
     * The most nourishing thing carried, or null if none of it is edible.
     * Settlers eat their best food first, so a handed-over loaf is used before
     * the raw grain they were saving.
     */
    public String bestFood() {
        String best = null;
        int bestNutrition = 0;
        for (Slot slot : slots) {
            int nutrition = Foods.nutrition(slot.itemId());
            if (nutrition > bestNutrition) {
                bestNutrition = nutrition;
                best = slot.itemId();
            }
        }
        return best;
    }

    /** Portions of anything edible carried, for the town's larder arithmetic. */
    public int foodCount() {
        return slots.stream()
                .filter(s -> Foods.isFood(s.itemId()))
                .mapToInt(Slot::count)
                .sum();
    }

    /** How much hunger everything carried could undo, for reports. */
    public int totalNutrition() {
        return totalNutrition(slots);
    }

    /**
     * The same sum over slots that came from somewhere else — a saved settler, a
     * snapshot on its way to a screen. Static so a surface holding only the slots
     * does not have to rebuild an {@code Inventory} to ask, and so there is one
     * answer if nutrition ever stops being a plain item-times-count.
     */
    public static int totalNutrition(List<Slot> slots) {
        return slots.stream().mapToInt(s -> Foods.nutrition(s.itemId()) * s.count()).sum();
    }

    @Override
    public String toString() {
        if (slots.isEmpty()) {
            return "empty-handed";
        }
        StringBuilder sb = new StringBuilder();
        for (Slot slot : slots) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(slot.count()).append(" ").append(Foods.displayName(slot.itemId()));
        }
        return sb.toString();
    }
}
