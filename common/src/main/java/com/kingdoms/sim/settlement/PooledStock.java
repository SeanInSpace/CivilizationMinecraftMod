package com.kingdoms.sim.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A town's holdings, read as the sum of the places that actually hold them.
 *
 * <p>Until this existed a settlement had one number per resource and no notion
 * of where the goods were, which is why a builder could draw timber from a
 * warehouse on the far side of the village and why two chests mirroring the
 * same number could be made to mint it. The town total is now a derived
 * quantity: nothing stores it, and asking for it adds up the buildings.
 *
 * <p><strong>Deposits land in one place, draws walk the list.</strong> Produce
 * goes to the first holder — the town's own pile today, the nearest store once
 * buildings are ordered by distance — and spending takes from holders in turn
 * until it has enough. That ordering is the seam where locality goes: the
 * arithmetic below does not care what the order means, only that it is stable.
 *
 * <p><strong>Holders are read fresh on every call.</strong> Buildings are
 * raised and lost while the town runs, so this keeps a supplier rather than a
 * list. A pool with no holders at all holds nothing and, more importantly,
 * <em>accepts</em> nothing — so a settlement must always contribute its own
 * pile as the first holder, or a camp with no storehouse would quietly drop
 * everything its people produced.
 */
public final class PooledStock implements Stock {

    private final Supplier<List<Stock>> holders;

    public PooledStock(Supplier<List<Stock>> holders) {
        this.holders = holders;
    }

    @Override
    public int get(String resource) {
        int total = 0;
        for (Stock holder : holders.get()) {
            total += holder.get(resource);
        }
        return total;
    }

    @Override
    public boolean has(String resource, int amount) {
        return get(resource) >= amount;
    }

    @Override
    public int add(String resource, int amount) {
        if (amount < 0) {
            // Kept because TownStores.add takes negatives and clamps at zero;
            // across a pool the only sense that makes is a draw on the whole.
            takeUpTo(resource, -amount);
            return get(resource);
        }
        List<Stock> into = holders.get();
        if (!into.isEmpty()) {
            into.getFirst().add(resource, amount);
        }
        return get(resource);
    }

    @Override
    public int addCapped(String resource, int amount, int ceiling) {
        int room = Math.max(0, ceiling - get(resource));
        int fitting = Math.min(Math.max(0, amount), room);
        if (fitting <= 0) {
            return 0;
        }
        // Measured rather than assumed: a pool with nowhere to put goods
        // reports that nothing fit, instead of billing a ceiling it never used.
        int before = get(resource);
        add(resource, fitting);
        return get(resource) - before;
    }

    @Override
    public boolean take(String resource, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (!has(resource, amount)) {
            // Asked before a single holder is touched, so a town that cannot
            // pay is not left half-drained across three buildings.
            return false;
        }
        return takeUpTo(resource, amount) == amount;
    }

    @Override
    public int takeUpTo(String resource, int amount) {
        int wanted = Math.max(0, amount);
        int drawn = 0;
        for (Stock holder : holders.get()) {
            if (drawn >= wanted) {
                break;
            }
            drawn += holder.takeUpTo(resource, wanted - drawn);
        }
        return drawn;
    }

    @Override
    public Map<String, Integer> all() {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Stock holder : holders.get()) {
            holder.all().forEach((resource, amount) -> merged.merge(resource, amount, Integer::sum));
        }
        return Collections.unmodifiableMap(merged);
    }

    @Override
    public String toString() {
        return all().toString();
    }
}
