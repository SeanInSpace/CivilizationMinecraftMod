package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The founding progression: what each stage builds, who works it, and when a
 * settlement graduates to the next one.
 *
 * <p>This is the priority structure that replaced hall-first founding. Below
 * {@link SettlementStage#VILLAGE} the catalogue's priorities do not run at all
 * — the stage's own ordered program is the only thing that gets built, so a
 * camp raises a bunkhouse and a farm and nothing else, however loudly the
 * catalogue would like a hall. From VILLAGE the catalogue scan resumes for
 * growth (houses per residents, and so on), and the hall stays off the table
 * until TOWN, where it is the stage's own headline build.
 *
 * <p>Program entries name blueprints that may not exist yet in a given
 * catalogue; unknown entries are skipped rather than built as markers, which
 * keeps the machine honest while the content catches up and keeps test
 * catalogues inert.
 */
public final class StagePlanner {

    /** One thing a stage wants standing, and how many of it. */
    public record Want(String blueprintId, int count) {
    }

    /**
     * The ordered build program per stage. Order matters: a program is worked
     * top to bottom, which is what makes shelter come before the farm and the
     * farm before the granary.
     */
    private static final Map<SettlementStage, List<Want>> PROGRAMS = Map.of(
            SettlementStage.CAMP, List.of(
                    new Want("kingdoms:camp_post", 1),
                    new Want("kingdoms:cache", 1)),
            SettlementStage.HOMESTEAD, List.of(
                    new Want("kingdoms:bunkhouse", 1),
                    new Want("kingdoms:hearth", 1),
                    new Want("kingdoms:farm", 1),
                    new Want("kingdoms:granary", 1)),
            SettlementStage.FORTIFIED, List.of(
                    // The lumber camp comes first: the palisade this stage
                    // exists for drinks more timber than any founding kit
                    // carries, and until one stands the town fells nothing.
                    new Want("kingdoms:lumber_camp", 1),
                    new Want("kingdoms:storehouse", 1)),
            SettlementStage.VILLAGE, List.of(
                    new Want("kingdoms:cottage", 2),
                    new Want("kingdoms:mill", 1),
                    new Want("kingdoms:carpentry", 1),
                    new Want("kingdoms:market", 1),
                    new Want("kingdoms:inn", 1)),
            SettlementStage.TOWN, List.of(
                    new Want("kingdoms:town_hall", 1)));

    /**
     * Steps the larder must have covered the appetite before a homestead is
     * called fed. A rolling condition, not a snapshot: one good harvest tick
     * does not make a settlement self-sufficient.
     */
    public static final int FED_WINDOW_STEPS = 10;

    /** Buildings that count as workshops for the village graduation. */
    private static final List<String> WORKSHOPS =
            List.of("kingdoms:smith", "kingdoms:mill", "kingdoms:carpentry");

    private StagePlanner() {
    }

    /**
     * The next thing this stage's program wants built, or empty when the
     * program is satisfied (or names nothing this catalogue knows).
     */
    public static Optional<BuildingType> nextProgramWant(Settlement settlement) {
        List<Want> program = PROGRAMS.getOrDefault(settlement.stage(), List.of());
        for (Want want : program) {
            Optional<BuildingType> type = typeOf(settlement, want.blueprintId());
            if (type.isEmpty()) {
                continue;   // content not in this catalogue yet; the machine moves on
            }
            int standing = settlement.countBuildings(want.blueprintId());
            boolean queued = settlement.buildQueue().stream()
                    .anyMatch(t -> BuildPlanner.baseIdOf(t.blueprintId())
                            .equals(want.blueprintId()));
            if (standing < want.count() && !queued) {
                return type;
            }
        }
        return Optional.empty();
    }

    /** Whether every known entry of the stage's program stands. */
    public static boolean programComplete(Settlement settlement) {
        for (Want want : PROGRAMS.getOrDefault(settlement.stage(), List.of())) {
            if (typeOf(settlement, want.blueprintId()).isEmpty()) {
                continue;
            }
            if (settlement.countBuildings(want.blueprintId()) < want.count()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the catalogue's own priority scan may run at this stage.
     *
     * <p>Below VILLAGE the answer is no: the program is the whole of the plan,
     * which is precisely what stops a camp ordering civic architecture.
     */
    public static boolean catalogueRuns(SettlementStage stage) {
        return stage.atLeast(SettlementStage.VILLAGE);
    }

    /**
     * Whether this building may be ordered from the catalogue at this stage.
     * The hall is the one the whole design exists to hold back.
     */
    public static boolean catalogueAllows(SettlementStage stage, String blueprintId) {
        if (BuildPlanner.baseIdOf(blueprintId).equals("kingdoms:town_hall")) {
            return stage.atLeast(SettlementStage.TOWN);
        }
        return true;
    }

    /**
     * Whether generalists still labour here.
     *
     * <p>Early settlers are pioneers — no fixed trade, taking whatever work the
     * camp has. Professions crystallize as the stages demand them; from VILLAGE
     * the specialists have arrived and a pioneer is a person the staffing table
     * has not caught up with yet.
     */
    public static boolean pioneersLabour(SettlementStage stage) {
        return stage.before(SettlementStage.VILLAGE);
    }

    /**
     * Whether the settlement has met this stage's graduation conditions.
     * Conditions, not day counts: a timer would advance over a party that is
     * failing.
     */
    public static boolean readyToAdvance(Settlement settlement, SimContext ctx) {
        return switch (settlement.stage()) {
            case CAMP -> programComplete(settlement);
            case HOMESTEAD -> programComplete(settlement)
                    && settlement.fedStreak() >= FED_WINDOW_STEPS;
            case FORTIFIED -> programComplete(settlement)
                    && settlement.perimeterClosed()
                    && JobPlanner.count(settlement, Profession.GUARD) >= 1;
            case VILLAGE -> programComplete(settlement)
                    && familyHoused(settlement) * 2 >= settlement.population()
                    && workshopCount(settlement) >= 2;
            case TOWN -> false;   // the road ends here
        };
    }

    /**
     * The people-side of a stage transition.
     *
     * <p>FORTIFIED crystallizes the first fixed profession — the sentry.
     * VILLAGE dissolves the pioneers into idlers, and the ordinary staffing
     * table (which wakes at VILLAGE) distributes them one per step from there,
     * exactly as it staffs any other town. Reassignment is a stage event and a
     * shortage event, never a reflex fired by a structure completing.
     */
    public static void crystallize(Settlement settlement, SettlementStage reached) {
        if (reached == SettlementStage.FORTIFIED) {
            // The sentry first, then the woodcutter: the palisade needs an axe
            // as much as a spear, and every timber path in the mod — watched
            // and unwatched alike — counts real lumberjacks, not stand-ins.
            crystallizeOne(settlement, Profession.GUARD);
            crystallizeOne(settlement, Profession.LUMBERJACK);
        }
        if (reached == SettlementStage.VILLAGE) {
            for (Person person : settlement.residents()) {
                if (person.profession() == Profession.PIONEER) {
                    person.setProfession(Profession.IDLER);
                }
            }
        }
    }

    private static void crystallizeOne(Settlement settlement, Profession trade) {
        // A shortage may have crystallized this trade years early — a camp that
        // ran out of timber already named its woodcutter. The stage fills the
        // gap, never doubles the post.
        if (JobPlanner.count(settlement, trade) > 0) {
            return;
        }
        settlement.residents().stream()
                .filter(p -> p.profession() == Profession.PIONEER)
                .findFirst()
                .or(() -> settlement.residents().stream()
                        // Not every camp is charter-born: a command-founded
                        // party may hold no pioneers at all, and a stage that
                        // cannot name its sentry is a stage nobody graduates.
                        // Idlers are the next-sparest hands.
                        .filter(p -> p.profession() == Profession.IDLER)
                        .findFirst())
                .ifPresent(p -> p.setProfession(trade));
    }

    private static int familyHoused(Settlement settlement) {
        int housed = 0;
        for (Household household : settlement.households()) {
            if (household.isHoused() && settlement.isFamilyHome(household.home())) {
                housed += household.members().size();
            }
        }
        return housed;
    }

    private static int workshopCount(Settlement settlement) {
        int count = 0;
        for (String workshop : WORKSHOPS) {
            if (settlement.countBuildings(workshop) > 0) {
                count++;
            }
        }
        return count;
    }

    private static Optional<BuildingType> typeOf(Settlement settlement, String blueprintId) {
        return settlement.catalogue().stream()
                .filter(type -> type.id().equals(blueprintId))
                .findFirst();
    }
}
