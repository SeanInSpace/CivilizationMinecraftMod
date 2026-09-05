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
 * <p>{@link Profession#IDLER} goes first, and a trade at its desired count is
 * never drained — settlements correct their mix through newborns, idlers and
 * genuine surplus, not by upending existing lives. A starving town is allowed to
 * upend one: see {@link #retrainOne}.
 */
public final class JobPlanner {

    /**
     * How many of a profession a settlement wants. Same arithmetic as
     * {@link BuildingType}: {@code base + population / perResidents}.
     */
    public record ProfessionNeed(Profession profession, int base, int perResidents, int priority,
                                 BuildingRole requiresBuilding) {

        public ProfessionNeed {
            Objects.requireNonNull(profession, "profession");
            if (perResidents < 0) {
                throw new IllegalArgumentException("perResidents must not be negative");
            }
        }

        /** A need that applies to every settlement, whatever it has built. */
        public ProfessionNeed(Profession profession, int base, int perResidents, int priority) {
            this(profession, base, perResidents, priority, null);
        }

        public int desiredCount(int population) {
            int scaled = perResidents > 0 ? population / perResidents : 0;
            return base + scaled;
        }

        /**
         * Whether this trade is wanted here at all.
         *
         * <p>A lumberjack with no camp has nowhere to work, so a small town must
         * not spend one of its four people on the job. Once the camp stands the
         * need switches on — which is what makes a town staff its own production
         * the moment it can, without starving its building crew before then.
         */
        public boolean appliesTo(Settlement settlement) {
            if (requiresBuilding == null) {
                return true;
            }
            return settlement.buildings().stream()
                    .anyMatch(b -> b.role() == requiresBuilding);
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
            new ProfessionNeed(Profession.LUMBERJACK,  1,           10,       60, BuildingRole.LUMBER_CAMP),
            new ProfessionNeed(Profession.MINER,       1,           12,       55, BuildingRole.MINE),
            new ProfessionNeed(Profession.SMITH,       1,           14,       52, BuildingRole.SMITH),
            new ProfessionNeed(Profession.SHEPHERD,    1,           16,       48, BuildingRole.ANIMAL_FARM),
            new ProfessionNeed(Profession.MILLER,      1,           20,       46, BuildingRole.MILL),
            new ProfessionNeed(Profession.CARPENTER,   1,           20,       44, BuildingRole.CARPENTRY),
            new ProfessionNeed(Profession.TRADER,      0,           15,       50)
    );

    /**
     * Farmers a starving town insists on, whatever the staffing table says.
     *
     * <p>One, because one is the whole difference. A single field hand brings in
     * a loaf a step and a loaf feeds somebody for fifteen, so one farmer carries
     * a founding party comfortably — and the party that died in the playtest had
     * none, because the table wants no farmers at all below five residents and
     * the charter lands four. Everything past the first is still the table's
     * business.
     */
    public static final int FARMERS_WHILE_STARVING = 1;

    private JobPlanner() {
    }

    public static int count(Settlement settlement, Profession profession) {
        return (int) settlement.residents().stream()
                .filter(p -> p.profession() == profession)
                .count();
    }

    public static int shortfall(Settlement settlement, ProfessionNeed need) {
        if (!need.appliesTo(settlement)) {
            return 0;
        }
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
     * <p>A starving town is the exception to all of it. The table's shortfall is
     * the wrong question when the answer takes five residents to become yes, so
     * the crisis lane below asks a different one — is anybody farming? — and
     * makes somebody a farmer if the answer is no.
     *
     * @return true if somebody changed jobs
     */
    public static boolean retrainOne(Settlement settlement) {
        if (settlement.isStarving()
                && count(settlement, Profession.FARMER) < FARMERS_WHILE_STARVING) {
            Person hand = spareHandsForTheFields(settlement);
            if (hand != null) {
                hand.setProfession(Profession.FARMER);
                return true;
            }
        }
        // Below VILLAGE the table does not staff at all: pioneers are every
        // laboring trade at once, and pulling them into fixed jobs early is
        // exactly the churn the stages exist to prevent. The crisis lane above
        // still runs -- a starving camp crystallizes a farmer, and should.
        if (StagePlanner.pioneersLabor(settlement.stage())) {
            return false;
        }
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

    /**
     * Whoever can be spared to farm right now.
     *
     * <p>Idle hands first, then the trade with the most people in it. Nobody is
     * asked whether they are too weak, because the hungry are exactly who has to
     * feed themselves here — and the last builder is left where they are, since
     * they are the only person who can raise the farm this is all for.
     */
    private static Person spareHandsForTheFields(Settlement settlement) {
        Person idler = settlement.residents().stream()
                .filter(p -> p.profession() == Profession.IDLER)
                .findFirst()
                .orElse(null);
        if (idler != null) {
            return idler;
        }
        Profession fullest = null;
        int most = 0;
        for (Profession trade : Profession.values()) {
            if (trade == Profession.FARMER || trade == Profession.IDLER) {
                continue;
            }
            int heads = count(settlement, trade);
            if (trade == Profession.BUILDER && heads <= 1) {
                continue;
            }
            if (heads > most) {
                most = heads;
                fullest = trade;
            }
        }
        if (fullest == null) {
            return null;
        }
        Profession chosen = fullest;
        return settlement.residents().stream()
                .filter(p -> p.profession() == chosen)
                .findFirst()
                .orElse(null);
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
