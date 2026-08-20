package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides who does what for a living.
 *
 * <p>Same shape as {@link BuildPlanner} on purpose: a table of needs, a shortfall
 * calculation, highest priority wins, deterministic ties. The settlement staffs
 * the job it is most short of.
 *
 * <p>Two entry points, both driven from the settlement step:
 * <ul>
 *   <li>{@link #mostNeeded} — what a newborn should become (used by
 *       {@link PopulationPlanner} instead of blind inheritance)</li>
 *   <li>{@link #retrainOneIdler} — one idler per step takes up the most needed
 *       trade. One per step keeps changes legible, like one build at a time.</li>
 * </ul>
 *
 * <p>Only {@link Profession#IDLER} is ever retrained. A farmer is never yanked
 * into guard duty — settlements correct their mix through newborns and idlers,
 * not by upending existing lives.
 */
public final class JobPlanner {

    /**
     * How many of a profession a settlement wants. Same arithmetic as
     * {@link BuildingType}: {@code base + population / perResidents}.
     */
    public record ProfessionNeed(Profession profession, int base, int perResidents, int priority) {

        public ProfessionNeed {
            Objects.requireNonNull(profession, "profession");
            if (perResidents < 0) {
                throw new IllegalArgumentException("perResidents must not be negative");
            }
        }

        public int desiredCount(int population) {
            int scaled = perResidents > 0 ? population / perResidents : 0;
            return base + scaled;
        }
    }

    /**
     * The default staffing table. Builders lead — construction gates housing and
     * housing gates growth, so a town short of builders is short of everything.
     */
    public static final List<ProfessionNeed> DEFAULT_NEEDS = List.of(
            //                 profession           base  perResidents  priority
            new ProfessionNeed(Profession.BUILDER,     1,            5,       90),
            new ProfessionNeed(Profession.GUARD,       0,            8,       80),
            new ProfessionNeed(Profession.FARMER,      0,            5,       70),
            new ProfessionNeed(Profession.LUMBERJACK,  0,           10,       60),
            new ProfessionNeed(Profession.MINER,       0,           12,       55),
            new ProfessionNeed(Profession.SMITH,       0,           14,       52),
            new ProfessionNeed(Profession.SHEPHERD,    0,           16,       48),
            new ProfessionNeed(Profession.TRADER,      0,           15,       50)
    );

    private JobPlanner() {
    }

    public static int count(Settlement settlement, Profession profession) {
        return (int) settlement.residents().stream()
                .filter(p -> p.profession() == profession)
                .count();
    }

    public static int shortfall(Settlement settlement, ProfessionNeed need) {
        return need.desiredCount(settlement.population()) - count(settlement, need.profession());
    }

    /**
     * The profession the settlement is most short of, or empty when fully staffed.
     * Highest priority wins; ties go to the larger shortfall, then to name order
     * so the answer is deterministic.
     */
    public static Optional<Profession> mostNeeded(Settlement settlement) {
        return DEFAULT_NEEDS.stream()
                .filter(need -> shortfall(settlement, need) > 0)
                .max(Comparator
                        .comparingInt(ProfessionNeed::priority)
                        .thenComparingInt((ProfessionNeed need) -> shortfall(settlement, need))
                        .thenComparing(need -> need.profession().name(), Comparator.reverseOrder()))
                .map(ProfessionNeed::profession);
    }

    /**
     * Retrains at most one person into the most needed trade.
     *
     * <p>Donors, in order: an idler if one exists, otherwise someone from the
     * profession with the <em>largest surplus</em> over its own desired count. A
     * profession at or below its desired staffing is never drained — retraining
     * fills gaps from slack, it does not open new ones.
     *
     * <p>The surplus rule matters in practice: a town of ninety-seven farmers has
     * no idlers, and idler-only retraining left it permanently defenseless (found
     * in the first live playtest). Now its surplus farmers take up the sword.
     *
     * @return true if somebody changed jobs
     */
    public static boolean retrainOne(Settlement settlement) {
        Optional<Profession> needed = mostNeeded(settlement);
        if (needed.isEmpty()) {
            return false;
        }
        Person donor = settlement.residents().stream()
                .filter(p -> p.profession() == Profession.IDLER)
                .findFirst()
                .orElseGet(() -> biggestSurplusDonor(settlement));
        if (donor == null) {
            return false;
        }
        donor.setProfession(needed.get());
        return true;
    }

    private static Person biggestSurplusDonor(Settlement settlement) {
        int population = settlement.population();
        Profession donorProfession = null;
        int bestSurplus = 0;
        for (ProfessionNeed need : DEFAULT_NEEDS) {
            int surplus = count(settlement, need.profession()) - need.desiredCount(population);
            if (surplus > bestSurplus) {
                bestSurplus = surplus;
                donorProfession = need.profession();
            }
        }
        if (donorProfession == null) {
            return null;
        }
        Profession chosen = donorProfession;
        return settlement.residents().stream()
                .filter(p -> p.profession() == chosen)
                .findFirst()
                .orElse(null);
    }
}
