package com.civilization.neoforge.world;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Footprint;
import com.civilization.sim.settlement.Settlement;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A house with no way in gets one dug.
 *
 * <p><strong>The report.</strong> {@code /civ audit} on the second playtest's run
 * A: {@code civilization:house @ 164, 79, 565: no way in — 1 gap(s) in 32 wall
 * columns}. One of thirty houses. The audit caught it — which is what the audit is
 * for, and {@code TownAuditorGeometryTest} is full of proof that it catches the
 * right things — and then nothing whatever happened. The house stood shut for the
 * life of the world.
 *
 * <p>So the finding feeds a repair now. The cheap half of one: whatever is
 * <em>standing</em> in the doorway and on the doorstep comes out, which is the
 * afternoon's work a crew with shovels would do. A doorway that opens onto a drop
 * is left alone and goes on being reported, because the cure for that is to move
 * the house and clearing cannot conjure ground.
 */
class DoorwayRepairTest {

    private static final int FLOOR = 64;
    private static final int SPAN = 7;
    private static final int HALF = 2;

    private static Building house() {
        Building house = new Building("civilization:house", new SimPos(0, FLOOR, 0), 1, true);
        house.setFootprint(new Footprint(FLOOR, SPAN, SPAN, 4));
        return house;
    }

    private static Settlement townWith(Building building) {
        Settlement town = new Settlement(
                Settlement.Id.random(), "Testburg", new SimPos(0, FLOOR, 0), 64);
        town.addBuilding(building);
        return town;
    }

    /** A walled house on a flat plain, with the ground level with the floor. */
    private static FakeWorld sealedHouse() {
        return new FakeWorld(FLOOR + 1)
                .plain(FLOOR, 12)
                .walls(HALF, FLOOR);
    }

    private static boolean reportsNoWayIn(FakeWorld world) {
        return TownAuditor.audit(world, townWith(house())).stream()
                .map(TownAuditor.Fault::describe)
                .anyMatch(f -> f.contains("no way in"));
    }

    /** Runs the repair against a world, taking the blocks out of it as it goes. */
    private static List<BlockPos> dig(FakeWorld world, Settlement town) {
        List<BlockPos> taken = new ArrayList<>();
        TownAuditor.openDoorways(world, town, at -> {
            taken.add(at);
            world.empty(at.getX(), at.getY(), at.getZ());
        });
        return taken;
    }

    @Test
    void aDoorwayWithATreeInItIsDugOutAndTheHouseOpens() {
        // The live shape of the fault: a gap in the wall, and a trunk filling the
        // column outside it. Three courses of it, which is what the audit's own
        // test uses so the step tolerance cannot quietly pass this.
        FakeWorld blocked = sealedHouse().doorway(HALF, 0, FLOOR);
        blocked.solid(HALF + 1, FLOOR + 1, 0);
        blocked.solid(HALF + 1, FLOOR + 2, 0);
        blocked.solid(HALF + 1, FLOOR + 3, 0);
        assertTrue(reportsNoWayIn(blocked), "the fixture is not the fault");

        Settlement town = townWith(house());
        List<BlockPos> taken = dig(blocked, town);

        assertFalse(taken.isEmpty(), "nothing was dug out of a doorway with a tree in it");
        assertFalse(reportsNoWayIn(blocked),
                "the doorway was dug out and the audit still cannot get in");
    }

    @Test
    void aWallRingWithNoGapAtAllIsOpened() {
        // The other half of what "no way in" means: not a blocked doorway but no
        // doorway. The hillside the apron was cut into slumped back over the
        // whole ring, or the cut never happened.
        FakeWorld sealed = sealedHouse();
        assertTrue(reportsNoWayIn(sealed), "a house with no gap in its walls is shut");

        Settlement town = townWith(house());
        dig(sealed, town);

        assertFalse(reportsNoWayIn(sealed),
                "a sealed house was not opened, so nothing in the mod ever will");
    }

    @Test
    void ahouseThatCanAlreadyBeWalkedIntoIsNotTouched() {
        // The idle case, and the one that has to be cheap: a town at rest costs
        // this the wall ring of each of its buildings and not one block changes.
        FakeWorld open = sealedHouse().doorway(HALF, 0, FLOOR);
        assertFalse(reportsNoWayIn(open), "the fixture is not a working doorway");

        assertEquals(List.of(), dig(open, townWith(house())),
                "a house somebody can already walk into had its doorway dug out"
                        + " anyway, which is a crew re-cutting the same door every"
                        + " minute for the life of the world");
    }

    @Test
    void agateIsAWayInAndIsNotDugOut() {
        // A fence gate counts whether open or shut — it is an intended access
        // point even where it is kept closed to pen animals, which is the
        // auditor's own rule and has to be the repair's too.
        FakeWorld penned = sealedHouse();
        penned.gate(HALF, FLOOR + 1, 0);

        assertEquals(List.of(), dig(penned, townWith(house())),
                "the crew pulled down a gate somebody had shut on purpose");
    }

    @Test
    void adoorwayOverADropIsLeftAloneForSomebodyElseToFix() {
        // The half this deliberately does not do. Outside every gap is air all
        // the way down — the building is perched — and no amount of clearing
        // makes ground to stand on. It has to go on being reported, because the
        // cure is to move the house.
        FakeWorld perched = new FakeWorld(FLOOR + 1).walls(HALF, FLOOR);
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                perched.solid(dx, FLOOR, dz);   // its own floor, and nothing outside
            }
        }
        perched.doorway(HALF, 0, FLOOR);
        assertTrue(reportsNoWayIn(perched), "the fixture is not a perched house");

        assertEquals(List.of(), dig(perched, townWith(house())),
                "blocks were taken out over a drop, which cannot open anything");
        assertTrue(reportsNoWayIn(perched),
                "the audit stopped reporting a house nobody can get into, which is"
                        + " worse than not fixing it");
    }

    @Test
    void anunloadedPlotIsNotDugAt() {
        // Nobody is there to dig and nothing is known about the ground. The same
        // rule every other sweep in the auditor follows.
        FakeWorld away = sealedHouse();
        away.unloaded(0, FLOOR, 0);

        assertEquals(List.of(), dig(away, townWith(house())),
                "a plot in an unloaded chunk was dug at");
    }
}
