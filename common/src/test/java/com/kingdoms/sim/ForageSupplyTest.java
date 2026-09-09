package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.platform.WorldBridge;
import com.kingdoms.sim.settlement.BuildCatalog;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import com.kingdoms.sim.world.YieldPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Foraging gathers what is growing there, and nothing else.
 *
 * <p>A camp used to turn a headcount into loaves: hands over three, every step,
 * anywhere. A party pitched on bare superflat ate exactly as well as one in a
 * berry-thick taiga, and a party in the middle of a desert lived on sand
 * forever. Those loaves were the largest single source of food in the mod that
 * nobody anywhere had grown, picked or carried.
 *
 * <p>What is pinned here: bare ground feeds nobody at all, ever; good ground
 * feeds a camp up to what is actually standing on it and no further; and a
 * picked patch comes back slowly rather than instantly, so wild food is a
 * reprieve and never an economy.
 */
class ForageSupplyTest {

    /** Ground with a fixed number of meals growing on it, and nobody watching. */
    private static final class Ground implements WorldBridge {
        private final int meals;
        private int asked;

        Ground(int meals) {
            this.meals = meals;
        }

        @Override public boolean playerWithin(SimPos pos, double radius) { return false; }
        @Override public boolean isLoaded(SimPos pos) { return false; }
        @Override public int surfaceHeight(SimPos pos) { return pos.y(); }
        @Override public Footprint materializeBlueprint(String id, SimPos origin,
                                                        boolean surveyed, int facing) {
            return new Footprint(origin.y(), 3, 3, 3);
        }
        @Override public void log(String message) { }

        @Override
        public int forageableNear(SimPos center, int radius) {
            asked++;
            return meals;
        }
    }

    /** Bare superflat: grass, dirt, stone, sky. Nothing edible for a hundred blocks. */
    private static Ground superflat() {
        return new Ground(0);
    }

    /** A wood with berries in it: forty meals standing, and no more than forty. */
    private static Ground wood() {
        return new Ground(40);
    }

    /**
     * A camp of four pioneers, no fields, no stores, and one job: eat.
     *
     * <p>Held at CAMP and given no catalog wants it can afford, so that the only
     * food that can appear in it is food somebody foraged.
     */
    private static Settlement camp() {
        Settlement camp = new Settlement(
                Settlement.Id.random(), "Berrywick", new SimPos(0, 64, 0), 128);
        camp.setCatalog(BuildCatalog.DEFAULT);
        camp.setStage(SettlementStage.CAMP);
        camp.setFoodStock(0);
        camp.setWoodStock(0);
        camp.setStoneStock(0);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov"}) {
            camp.addResident(new Person(Person.Id.random(), name, Profession.PIONEER,
                    new SimPos(0, 64, 0)));
        }
        return camp;
    }

    /** Everything the camp gathered over these steps, counting what it ate. */
    private static int gatheredOver(Settlement camp, Ground ground, int steps) {
        SimSettings settings = SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);
        int gathered = 0;
        int before = FoodPlanner.totalFood(camp);
        for (int step = 1; step <= steps; step++) {
            int start = FoodPlanner.totalFood(camp);
            FoodPlanner.advance(camp, new SimContext(ground, step, settings));
            int now = FoodPlanner.totalFood(camp);
            if (now > start) {
                gathered += now - start;
            }
        }
        // The eaten loaves are gone from the total; the gains are what was picked.
        assertTrue(FoodPlanner.totalFood(camp) <= before + gathered);
        return gathered;
    }

    @Test
    void aCampOnBareSuperflatForagesNothingAtAll() {
        Ground bare = superflat();
        Settlement camp = camp();

        assertEquals(0, gatheredOver(camp, bare, 200),
                "there is nothing growing on superflat, so there is nothing to pick");
        assertEquals(0, FoodPlanner.totalFood(camp),
                "and the camp is therefore exactly as empty as it started");
    }

    @Test
    void aCampInADesertForagesNothingEither() {
        // A desert answers zero for the same reason superflat does: cactus is
        // not food and sand is not food. Same bridge answer, same outcome --
        // stated separately because it is the case the change was asked for.
        assertEquals(0, gatheredOver(camp(), new Ground(0), 200));
    }

    @Test
    void aCampInAWoodForagesWhatIsThereAndThenOnlyWhatGrowsBack() {
        Ground wood = wood();
        int gathered = gatheredOver(camp(), wood, 200);

        // Forty standing, plus one back every four steps for two hundred steps.
        int regrown = 200 / FoodPlanner.FORAGE_REGROWTH_STEPS;
        assertTrue(gathered <= 40 + regrown,
                "a wood with forty meals in it cannot yield more than forty plus "
                        + regrown + " regrown, and yielded " + gathered);
        assertTrue(gathered > 40,
                "but it does keep yielding as it recovers, and yielded " + gathered);
    }

    @Test
    void theSupplyIsTheCeilingAndTheHandsAreNotEnoughToPassIt() {
        // Four meals standing and two hundred steps of hands: the hands could
        // pick two a step forever, and the ground is what stops them.
        Ground thin = new Ground(4);
        int gathered = gatheredOver(camp(), thin, 40);

        assertTrue(gathered <= 4 + 40 / FoodPlanner.FORAGE_REGROWTH_STEPS,
                "four meals of ground cannot feed a camp indefinitely, and gave "
                        + gathered);
    }

    @Test
    void aPickedPatchGrowsBackAndTheLedgerSaysSo() {
        Settlement camp = camp();
        Ground wood = wood();
        SimSettings settings = SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);

        // Step once so the survey happens and the ledger starts.
        FoodPlanner.advance(camp, new SimContext(wood, 1, settings));
        int taken = camp.foragedRecently();
        assertTrue(taken > 0, "a wood yields something on the first step");

        // Long enough afterwards for every picked meal to have come back, the
        // patch is whole again and the ledger is clear.
        int later = 1 + taken * FoodPlanner.FORAGE_REGROWTH_STEPS + 1;
        assertEquals(40, camp.forageAllowance(new SimContext(wood, later, settings)),
                "the wood is whole again once everything picked has grown back");
        assertEquals(0, camp.foragedRecently(),
                "and the ledger of what was taken is clear");
    }

    @Test
    void theGroundIsNotAskedEveryStep() {
        Ground wood = wood();
        Settlement camp = camp();
        SimSettings settings = SimSettings.SANDBOX.withYields(YieldPolicy.DEFAULTS);
        for (int step = 1; step <= FoodPlanner.FORAGE_SURVEY_STEPS; step++) {
            FoodPlanner.advance(camp, new SimContext(wood, step, settings));
        }

        assertEquals(1, wood.asked,
                "a chunk read per camp per step is a chunk read too many");
    }
}
