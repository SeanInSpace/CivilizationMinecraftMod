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
                                 BuildingRole requiresBuilding,
                                 BuildingRole staffs, int staffPerBuilding) {

        public ProfessionNeed {
            Objects.requireNonNull(profession, "profession");
            if (perResidents < 0) {
                throw new IllegalArgumentException("perResidents must not be negative");
            }
            if (staffPerBuilding < 0) {
                throw new IllegalArgumentException("staffPerBuilding must not be negative");
            }
        }

        /** A need that applies to every settlement, whatever it has built. */
        public ProfessionNeed(Profession profession, int base, int perResidents, int priority) {
            this(profession, base, perResidents, priority, null, null, 0);
        }

        /** A need that switches on once a building it can work at stands. */
        public ProfessionNeed(Profession profession, int base, int perResidents, int priority,
                              BuildingRole requiresBuilding) {
            this(profession, base, perResidents, priority, requiresBuilding, null, 0);
        }

        public int desiredCount(int population) {
            int scaled = perResidents > 0 ? population / perResidents : 0;
            return base + scaled;
        }

        /**
         * What this settlement actually wants, buildings included.
         *
         * <p>The table's own {@code base + population / perResidents}, or the
         * staffing the buildings on the ground demand, whichever is larger.
         *
         * <p>The second half exists because the first half was quietly wrong
         * about farms. A town of four with a field standing in it wanted
         * {@code 4 / 5} farmers — none — so the field it had just spent a
         * fortnight building had nobody in it, produced nothing, and the town
         * ate its founding provisions and died with a farm in the middle of it.
         * A building nobody works is not a building, it is a decoration, and
         * "N hands per M residents" cannot express that however the numbers are
         * tuned: the hands belong to the field, not to the census.
         *
         * <p>A floor, never a ceiling. Where the population row asks for more
         * than the fields do — a grown town, which wants a field hand per five
         * residents — the population row still wins.
         */
        public int desiredCount(Settlement settlement) {
            int byPopulation = desiredCount(settlement.population());
            if (staffs == null || staffPerBuilding == 0) {
                return byPopulation;
            }
            int buildings = (int) settlement.buildings().stream()
                    .filter(b -> b.role() == staffs)
                    .count();
            return Math.max(byPopulation, buildings * staffPerBuilding);
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
            // The farmer row is the one that answers to the ground rather than
            // to the census: FARMERS_PER_FARM hands for every field standing,
            // or one per five residents, whichever is more. See
            // ProfessionNeed.desiredCount(Settlement).
            new ProfessionNeed(Profession.FARMER,      0,            5,       70, null,
                    BuildingRole.CROP_FARM, FoodPlanner.FARMERS_PER_FARM),
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
        return need.desiredCount(settlement) - count(settlement, need.profession());
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
     * <p><strong>A frightened town is the second exception.</strong> The table
     * wants one guard per eight residents and has no idea what is outside; a
     * town of nine with a raid bearing down on it wanted exactly one guard and
     * got exactly one guard, which is how a garrison of one met a raid of six.
     * So when {@link Garrison#outnumbered} holds, GUARD jumps the table
     * entirely — see {@link #spareHandsForTheWatch} for who pays for it. Still
     * one person per step, and still nothing when the threat has decayed back
     * under the watch: the table simply resumes, and the surplus guards it then
     * sees drain away no faster than they ever did.
     *
     * <p>Hunger comes first of the two. A town can be wrong about the raid and
     * live; it cannot be wrong about dinner.
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
        if (Garrison.outnumbered(settlement)) {
            Person recruit = spareHandsForTheWatch(settlement);
            if (recruit != null) {
                recruit.setProfession(Profession.GUARD);
                return true;
            }
            // Nobody can be spared. Fall through rather than stopping: the town
            // is in trouble either way, and a step spent staffing the farm it
            // is short of is better than a step spent doing nothing at all.
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
            // The king is the one person a starving town may not put in the
            // fields, and this is the only lane that could have taken him: it
            // reaches past DEFAULT_NEEDS -- which has no king row and never will
            // -- for whatever trade has the most heads in it, and in a small
            // camp the trade with the most heads can be the one with one head
            // and a crown on it.
            if (trade.isIdleByRight()) {
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

    /**
     * Whoever can be spared to take up the sword right now.
     *
     * <p>Idle hands first, exactly as everywhere else — an idler costs the town
     * nothing. After that, the trade standing furthest above the minimum its own
     * row of {@link #DEFAULT_NEEDS} states, so the militia is raised out of
     * whatever the town has most of rather than out of whoever happens to be
     * listed first.
     *
     * <p>This digs deeper than {@link #biggestSurplusDonor} on purpose. That one
     * will not take a trade below its <em>desired</em> count — the right rule
     * for shuffling jobs on an ordinary afternoon, and far too polite when
     * something is coming: a town whose every trade is exactly staffed has no
     * surplus at all and would raise not one guard. Under threat the floor drops
     * to the row's minimum, which is the number the table itself says the town
     * cannot do without.
     *
     * <p>Three things are never taken, whatever the arithmetic says:
     * <ul>
     *   <li><strong>Guards.</strong> Robbing the watch to pay the watch.</li>
     *   <li><strong>The last farmer.</strong> A defended town that has stopped
     *       growing food is a town that starves a fortnight after the raid it
     *       won.</li>
     *   <li><strong>The last builder.</strong> The watchtower this same threat
     *       just moved to the front of the queue needs somebody to raise it.</li>
     * </ul>
     *
     * @return the person to retrain, or null if nobody at all can be spared
     */
    private static Person spareHandsForTheWatch(Settlement settlement) {
        Person idler = settlement.residents().stream()
                .filter(p -> p.profession() == Profession.IDLER)
                .findFirst()
                .orElse(null);
        if (idler != null) {
            return idler;
        }
        Profession donorProfession = null;
        int bestSpare = 0;
        for (ProfessionNeed need : DEFAULT_NEEDS) {
            Profession trade = need.profession();
            if (trade == Profession.GUARD) {
                continue;
            }
            int heads = count(settlement, trade);
            if ((trade == Profession.FARMER || trade == Profession.BUILDER) && heads <= 1) {
                continue;   // the last one of these is not spare, ever
            }
            int spare = heads - need.base();
            if (spare > bestSpare) {
                bestSpare = spare;
                donorProfession = trade;
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

    private static Person biggestSurplusDonor(Settlement settlement) {
        Profession donorProfession = null;
        int bestSurplus = 0;
        for (ProfessionNeed need : DEFAULT_NEEDS) {
            int surplus = count(settlement, need.profession()) - need.desiredCount(settlement);
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
