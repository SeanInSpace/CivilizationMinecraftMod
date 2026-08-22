package com.kingdoms.neoforge.block;

import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildingType;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.JobPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.StagePlanner;

import java.util.Optional;

/**
 * The staked claim a founding party answers from.
 *
 * <p>Before there is a hall there is this: the first block of the first camp,
 * and the place a player walks up to to learn where the founding stands — the
 * stage, what the program is raising next, and what graduation is waiting on.
 * The hall takes over the town's reporting at TOWN; the post keeps answering
 * anyway, because what it says never stops being true.
 */
public class CampPostBlock extends BuildingPostBlock {

    public CampPostBlock(Properties properties) {
        super("Camp Post",
                "the claim is staked here; the founding answers for itself.", properties);
    }

    @Override
    protected String report(Settlement settlement) {
        return "Stage: " + settlement.stage().pretty() + " — " + footing(settlement)
                + ". Party of " + settlement.population()
                + ", food " + FoodPlanner.totalFood(settlement)
                + ", timber " + settlement.woodStock()
                + ", stone " + settlement.stoneStock();
    }

    /** What the founding is doing right now, or what its graduation waits on. */
    private static String footing(Settlement settlement) {
        Optional<BuildingType> next = StagePlanner.nextProgramWant(settlement);
        if (next.isPresent()) {
            return "raising the " + plainName(next.get().id());
        }
        return switch (settlement.stage()) {
            case HOMESTEAD -> "fed " + settlement.fedStreak() + "/"
                    + StagePlanner.FED_WINDOW_STEPS + " toward fortifying";
            case FORTIFIED -> !settlement.perimeterClosed()
                    ? "the perimeter is still open"
                    : JobPlanner.count(settlement, Profession.GUARD) < 1
                            ? "the perimeter wants a sentry"
                            : "standing ready";
            case VILLAGE -> "housing families and opening workshops";
            default -> "the program is built";
        };
    }

    private static String plainName(String blueprintId) {
        int colon = blueprintId.indexOf(':');
        return (colon < 0 ? blueprintId : blueprintId.substring(colon + 1))
                .replace('_', ' ');
    }
}
