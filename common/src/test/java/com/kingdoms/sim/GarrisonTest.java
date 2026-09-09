package com.kingdoms.sim;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Danger;
import com.kingdoms.sim.settlement.Garrison;
import com.kingdoms.sim.settlement.RaidPlanner;
import com.kingdoms.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one comparison between what a town fears and what it can field.
 *
 * <p>The whole point of {@link Garrison} is that the number it hands out is the
 * same number {@link RaidPlanner} settles raids with. If those two ever drift,
 * a town would recruit exactly the watch it was told to and still lose — so the
 * agreement is asserted rung by rung rather than described in a comment.
 */
class GarrisonTest {

    private static Settlement settlement() {
        return new Settlement(Settlement.Id.random(), "Watchburg", new SimPos(0, 64, 0), 64);
    }

    private static void add(Settlement s, Profession profession, int count) {
        for (int i = 0; i < count; i++) {
            s.addResident(new Person(
                    Person.Id.random(), profession + " " + i, profession, new SimPos(0, 64, 0)));
        }
    }

    /** Every rung of the ladder, spelled out. Literal on purpose, like DangerTest. */
    @Test
    void theMappingIsWrittenDownRungByRung() {
        assertEquals(0, Garrison.neededGuards(Danger.NONE));
        assertEquals(1, Garrison.neededGuards(Danger.ROUTINE));
        assertEquals(1, Garrison.neededGuards(Danger.AWKWARD));
        assertEquals(2, Garrison.neededGuards(Danger.FULL_ATTENTION));
        assertEquals(2, Garrison.neededGuards(Danger.DANGEROUS));
        assertEquals(3, Garrison.neededGuards(Danger.DIRE));
        assertEquals(3, Garrison.neededGuards(Danger.OVERMATCH));
        assertEquals(5, Garrison.neededGuards(Danger.HOPELESS));
        assertEquals(8, Garrison.neededGuards(RaidPlanner.MAX_RAID_STRENGTH));
    }

    /** Negative or absent threat wants nobody; the staffing table has the floor. */
    @Test
    void nothingToFearWantsNoGuards() {
        assertEquals(0, Garrison.neededGuards(0));
        assertEquals(0, Garrison.neededGuards(-4));

        Settlement s = settlement();
        add(s, Profession.FARMER, 8);
        assertFalse(Garrison.outnumbered(s), "no threat, no emergency");
        assertEquals(0, Garrison.guardsWanted(s));
    }

    /**
     * The agreement that matters: a town that meets the number survives the raid
     * the number was derived from, at every strength a raid can actually be.
     */
    @Test
    void theNeededWatchActuallyRepelsTheRaidItPredicts() {
        for (int strength = 1; strength <= RaidPlanner.MAX_RAID_STRENGTH; strength++) {
            int needed = Garrison.neededGuards(strength);

            Settlement s = settlement();
            add(s, Profession.GUARD, needed);
            assertTrue(RaidPlanner.defensePower(s) >= strength,
                    needed + " guards should repel a raid of " + strength
                            + " but muster only " + RaidPlanner.defensePower(s));

            // And not one guard more than it has to be: the town is not being
            // told to raise a standing army it does not need.
            Settlement lean = settlement();
            add(lean, Profession.GUARD, needed - 1);
            assertTrue(RaidPlanner.defensePower(lean) < strength,
                    "a watch of " + (needed - 1) + " should not already be enough for " + strength);
        }
    }

    /** Guards are counted the same way the raid counts them, hungry ones included. */
    @Test
    void guardStrengthIsTheHeadCountTheRaidSees() {
        Settlement s = settlement();
        add(s, Profession.GUARD, 3);
        add(s, Profession.FARMER, 5);

        assertEquals(3, Garrison.guardStrength(s));
        assertEquals(3 * RaidPlanner.GUARD_POWER, RaidPlanner.defensePower(s),
                "no structures here, so defense is the garrison and nothing else");
    }

    /** More threat than watch, and then the threat decays and the town relaxes. */
    @Test
    void outnumberedTurnsOffAgainWhenTheThreatDecays() {
        Settlement s = settlement();
        add(s, Profession.GUARD, 2);
        add(s, Profession.FARMER, 6);

        s.setThreatLevel(Danger.OVERMATCH);   // 6 -> needs 3, has 2
        assertTrue(Garrison.outnumbered(s));
        assertEquals(1, Garrison.guardsWanted(s));

        s.setThreatLevel(Danger.DANGEROUS);   // 4 -> needs 2, has 2
        assertFalse(Garrison.outnumbered(s), "the watch is enough again");
        assertEquals(0, Garrison.guardsWanted(s));
    }
}
