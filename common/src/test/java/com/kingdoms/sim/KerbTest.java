package com.kingdoms.sim;

import com.kingdoms.sim.culture.Culture;
import com.kingdoms.sim.culture.TownPlan;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildCatalogue;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.PathPlanner;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.SettlementStage;
import com.kingdoms.sim.world.SimContext;
import com.kingdoms.sim.world.SimSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a house stands on its street or in a field behind it.
 *
 * <p>The plan sets every plot back far enough for the largest building that
 * could stand on it, because it reserves the ground before anybody knows what is
 * going there. What that costs is that the commonest building in any town takes
 * a setback drawn for something twice its size: measured on a grown town, seven
 * blocks of bare grass between a front wall and the curb, on every house, in
 * every arrangement.
 *
 * <p>There was a rule for this and it lived in {@code /civ buildtest}, whose own
 * comment said "the plan cannot fix this, the renderer can". The renderer is not
 * what builds a town. Every settlement that grew, and every settlement world
 * generation raised — which is every settlement a player has ever found — kept
 * the full setback, and the screenshot that started this said so.
 */
class KerbTest {

    private static final SimPos CENTRE = new SimPos(0, 72, 0);

    private static Settlement grow(String layout, int steps) {
        TerrainFake ground = new TerrainFake(11);
        Settlement town = new Settlement(Settlement.Id.random(), "Kerb", CENTRE, 512);
        town.setCatalogue(BuildCatalogue.DEFAULT);
        town.setStage(SettlementStage.CAMP);
        for (Culture culture : Culture.all()) {
            if (culture.layouts().contains(layout)) {
                town.setCultureId(culture.id());
                break;
            }
        }
        town.setLayoutId(layout);
        for (String name : new String[] {"Ada", "Bruno", "Cass", "Dov", "Eda", "Finn"}) {
            town.addResident(new Person(
                    Person.Id.random(), name, Profession.PIONEER, CENTRE));
        }
        for (int step = 1; step <= steps; step++) {
            town.step(new SimContext(ground, step, SimSettings.SANDBOX));
        }
        return town;
    }

    /** How far this building stands from the middle of the nearest planned street. */
    private static double toItsStreet(Settlement town, Building building) {
        TownPlan plan = town.arrangement().fullPlan(town.centre());
        double best = Double.MAX_VALUE;
        for (TownPlan.Street street : plan.streets()) {
            for (SimPos at : street.path()) {
                best = Math.min(best, Math.hypot(at.x() - building.origin().x(),
                        at.z() - building.origin().z()));
            }
        }
        return best;
    }

    private static List<Building> onStreets(Settlement town) {
        List<Building> out = new ArrayList<>();
        for (Building b : town.buildings()) {
            if (BuildPlanner.holdsGround(b.blueprintId()) && toItsStreet(town, b) < 40) {
                out.add(b);
            }
        }
        return out;
    }

    @Test
    void housesComeUpToTheStreetTheyFront() {
        Settlement town = grow(Culture.LAYOUT_HIGH_STREET, 400);
        List<Building> beside = onStreets(town);
        assertTrue(beside.size() >= 8,
                "the town built too little to say anything about its streets");

        int atTheKerb = 0;
        for (Building b : beside) {
            int span = BuildPlanner.plotSpanOf(b.blueprintId(), town.catalogue());
            // Within a couple of blocks of where the router would allow it, as
            // against the plan's thirteen, which is what it used to be for
            // everything however small.
            if (toItsStreet(town, b) <= PathPlanner.keepoutRound(span) + 2) {
                atTheKerb++;
            }
        }
        assertTrue(atTheKerb * 2 >= beside.size(),
                "only " + atTheKerb + " of " + beside.size() + " buildings beside a street"
                        + " actually came up to it; the rest are standing back at a setback"
                        + " drawn for a building twice their size");
    }

    @Test
    void theKerbStillFiresWhenThePlanOffersAtTheSeparation() {
        // The way this rule dies is silently, and it nearly did.
        //
        // Coming up to the curb is asked of the whole rank: pull this plot and
        // its plan neighbors by the same fraction, and refuse the fraction where
        // they foul each other. That was a fair question while the plan offered
        // frontage two blocks wider than the siting code demanded -- a pair that
        // fouled after a pull had been made to foul by it. The plan now offers at
        // the separation itself and reserves a house's span for a plot that may
        // hold a hall, so a rank of plan offers routinely fouls the overlap box
        // BEFORE anybody moves. Asked as a bare "do these clear", the answer is
        // no at every fraction, for every plot, in every arrangement: the curb
        // becomes dead code that still runs, every test above still passes, and
        // every house in the mod quietly steps back to the setback drawn for
        // something twice its size.
        //
        // So: it has to actually move buildings. A plot that has been brought in
        // no longer stands where the plan offered it, which is a thing this test
        // can see and the distance measures above cannot.
        Settlement town = grow(Culture.LAYOUT_HIGH_STREET, 400);
        TownPlan plan = town.arrangement().fullPlan(town.centre());
        List<Building> beside = onStreets(town);
        assertTrue(beside.size() >= 8,
                "the town built too little to say anything about its streets");

        int moved = 0;
        for (Building b : beside) {
            boolean whereThePlanPutIt = false;
            for (TownPlan.Plot offered : plan.plots()) {
                if (offered.at().x() == b.origin().x()
                        && offered.at().z() == b.origin().z()) {
                    whereThePlanPutIt = true;
                    break;
                }
            }
            if (!whereThePlanPutIt) {
                moved++;
            }
        }
        assertTrue(moved * 2 >= beside.size(),
                "only " + moved + " of " + beside.size() + " buildings beside a street"
                        + " were brought in off the plot the plan offered them; the"
                        + " approach is refusing every fraction and doing nothing");
    }

    @Test
    void nothingIsPulledIntoTheCarriageway() {
        // The rule that makes the approach safe, and the one it got wrong first
        // time round. The renderer's version measured to the PAVED strip, which
        // is a third of a street's width rather than half of it -- so a building
        // brought up to the tarmac stood inside the three blocks a street
        // reserves without surfacing. A carpentry and an animal farm were both
        // caught standing on an eight-wide street they had been pulled onto.
        for (String layout : new String[] {
                Culture.LAYOUT_HIGH_STREET, Culture.LAYOUT_GREEN, Culture.LAYOUT_CRESCENTS}) {
            Settlement town = grow(layout, 400);
            for (Building b : onStreets(town)) {
                int span = BuildPlanner.plotSpanOf(b.blueprintId(), town.catalogue());
                double away = toItsStreet(town, b);
                assertTrue(away + 1 >= PathPlanner.keepoutRound(span),
                        layout + ": " + b.blueprintId() + " stands " + Math.round(away)
                                + " from the middle of its street and wants "
                                + PathPlanner.keepoutRound(span) + " -- it is in the road");
            }
        }
    }

    @Test
    void aBuildingTooBigForItsSetbackBacksOffInstead() {
        // The other direction, and it is not symmetry for its own sake. A
        // library claims twenty-five blocks and the plan sets a plot back
        // thirteen, so every offer it is made stands in the carriageway. Without
        // the approach working both ways it is refused by every street-fronting
        // plot in the town and finishes in the outskirts, facing nothing.
        int librarySpan = BuildPlanner.plotSpanOf("kingdoms:library", BuildCatalogue.DEFAULT);
        assertTrue(PathPlanner.keepoutRound(librarySpan) > 13,
                "a library that fits inside the plan's own setback would not be testing"
                        + " anything; this test is about the one that does not");
    }
}
