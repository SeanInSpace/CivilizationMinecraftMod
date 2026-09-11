package com.civilization.sim.settlement;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.world.SimContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Who the warband follows, and what follows from that.
 *
 * <p>Every other trade in this mod is a <em>job</em>: the staffing table counts
 * how many the town is short of and {@link JobPlanner} fills the gap. A king is
 * not that. There is exactly one, he is never short and never surplus, he does
 * no work at all, and he is chosen by a rule about the town rather than by a
 * shortfall — so the staffing table has no opinion about him and this is where
 * the opinion lives.
 *
 * <h2>The rules, in order</h2>
 *
 * <ol>
 *   <li><strong>Only orcs crown one.</strong> A human village has a town hall
 *       and a population; a warband has a chief. This is the one place in the
 *       mod that reads {@link Race} to decide behavior rather than to set a
 *       number on a body, and it is deliberate — a king is a <em>political</em>
 *       fact about a people, not a stat.</li>
 *   <li><strong>No seat, no king.</strong> Nobody is crowned until the great hut
 *       stands. A king with nowhere to sit is a label on a settler, and the
 *       whole of what makes this readable in the world is that there is a
 *       building in the middle of the camp with him in it.</li>
 *   <li><strong>The oldest guard, else the oldest resident.</strong> A warband
 *       follows whoever has been carrying a weapon longest, and if nobody is
 *       carrying one it follows whoever has been there longest. See
 *       {@link #oldest} for what "oldest" can honestly mean here.</li>
 *   <li><strong>Exactly one.</strong> Enforced every step rather than assumed,
 *       because the ways a second one could appear are all quiet: a save, a
 *       migration, a command.</li>
 *   <li><strong>He never works.</strong> Not by a special case in every planner
 *       — by not being in {@link JobPlanner#DEFAULT_NEEDS} at all, which means
 *       nothing ever wants a king and nothing ever retrains one away. The one
 *       lane that could have taken him is the starving town's, which reaches
 *       past the table for the fullest trade; it skips him by name.</li>
 *   <li><strong>While he lives the warband fights harder.</strong>
 *       {@link #KING_GUARD_BONUS} on the town's guard strength, which is morale
 *       and not a bodyguard: it is worth two guards to the garrison and to the
 *       raid alike, and it is gone the step he falls.</li>
 *   <li><strong>When he falls the camp mourns.</strong> No king for
 *       {@link #MOURNING_STEPS}, and then the same rule picks the next one.</li>
 * </ol>
 */
public final class KingPlanner {

    private KingPlanner() {
    }

    /** The building a king sits in, and the one a warband is drawn round. */
    public static final String SEAT = "civilization:great_hut";

    /**
     * What a living king is worth to the watch.
     *
     * <p>Two, which is one guard's worth on the scale
     * {@link RaidPlanner#GUARD_POWER} sets — so a king is exactly one more
     * fighter in the line. Small on purpose: it is the difference between a
     * warband that has somebody to follow and one that does not, and a number
     * that decided raids on its own would make killing the king the whole game.
     *
     * <p>It counts in both places, which is the point. {@link Garrison} is what
     * the town <em>recruits</em> by and {@link RaidPlanner#defensePower} is what
     * it <em>fights</em> by, and a bonus in only one of them would be a town
     * that felt safe and died anyway, or one that raised guards it did not need.
     * Watchtowers are deliberately in the second and not the first — a tower can
     * be knocked down before the raid it was built for — and a king is not: he
     * is in the line, and if he is not, there is no bonus to count.
     */
    public static final int KING_GUARD_BONUS = 2;

    /**
     * How much more a king's body takes than his race's ordinary one.
     *
     * <p>Half again on top of {@link Race#maxHealth}, so an orc king stands at
     * forty-five against his warband's thirty and a human's twenty. Applied to
     * the race number rather than to a constant, which is what keeps this one
     * decision — "a king is tougher than his own people" — true whatever a race
     * is worth later.
     */
    public static final double KING_HEALTH_FACTOR = 1.5;

    /**
     * Steps a warband goes without a chief after the last one falls.
     *
     * <p>A hundred, which at the shipped simulation interval is a little over
     * eight minutes. Long enough that losing the king is a thing that happened
     * to the town rather than a title that moves sideways within a step, and
     * short enough that a camp is not leaderless for a session.
     */
    public static final int MOURNING_STEPS = 100;

    /** Whether this people crown anybody at all. */
    public static boolean crownsAKing(Settlement settlement) {
        return Culture.of(settlement.cultureId()).race() == Race.ORC;
    }

    /** The great hut, if one stands, or null. */
    public static Building seat(Settlement settlement) {
        for (Building standing : settlement.buildings()) {
            if (BuildPlanner.baseIdOf(standing.blueprintId()).equals(SEAT)) {
                return standing;
            }
        }
        return null;
    }

    /**
     * Where the town musters when the bell goes.
     *
     * <p>The great hut for a warband, and the middle of the town for everybody
     * else — which is what the view layer already did for anybody with no home
     * to run to. A camp is drawn round its chief's roof
     * ({@code OrcRingLayout} reserves the middle for it), so for an orc town
     * these are usually the same place and the difference only shows once the
     * camp has grown past its first ring.
     *
     * <p>Never null: a town always has a middle.
     */
    public static SimPos rallyPoint(Settlement settlement) {
        Building seat = seat(settlement);
        return seat != null ? seat.origin() : settlement.center();
    }

    /** The king, or null if this town has none. */
    public static Person king(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (person.profession() == Profession.KING) {
                return person;
            }
        }
        return null;
    }

    /** Whether a king is alive in this town right now. */
    public static boolean hasKing(Settlement settlement) {
        return king(settlement) != null;
    }

    /**
     * One step of the succession.
     *
     * <p>Run from {@link Settlement#step}, after the population pass so that a
     * king who died this step is mourned on the step he died rather than the
     * next one.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (!crownsAKing(settlement)) {
            // A town re-badged away from the orcs puts its crown down. /civ
            // culture can do this on a living town, and a settler wearing a
            // title his people do not have is a settler who does no work for a
            // reason nobody can see.
            for (Person person : new ArrayList<>(settlement.residents())) {
                if (person.profession() == Profession.KING) {
                    person.setProfession(Profession.IDLER);
                }
            }
            return;
        }

        List<Person> crowned = new ArrayList<>();
        for (Person person : settlement.residents()) {
            if (person.profession() == Profession.KING) {
                crowned.add(person);
            }
        }
        // Exactly one, whatever arrived here. The extras become idlers and the
        // staffing table puts them to work on its own next step.
        for (int i = 1; i < crowned.size(); i++) {
            crowned.get(i).setProfession(Profession.IDLER);
        }

        if (!crowned.isEmpty()) {
            settlement.setReigningKing(crowned.getFirst().id());
            return;
        }

        // Nobody is wearing it. Either the last one has just fallen, or this
        // camp has never had one.
        if (settlement.reigningKing() != null) {
            settlement.setReigningKing(null);
            settlement.setMourningUntil(ctx.step() + MOURNING_STEPS);
            settlement.logEvent(ctx.step(),
                    "The king is dead — the warband mourns and follows nobody");
            return;
        }
        if (ctx.step() < settlement.mourningUntil()) {
            return;   // still mourning
        }
        if (seat(settlement) == null) {
            return;   // no great hut, so nowhere to put him
        }
        Person heir = oldest(settlement, Profession.GUARD);
        if (heir == null) {
            heir = oldest(settlement, null);
        }
        if (heir == null) {
            return;   // an empty camp crowns nobody
        }
        heir.setProfession(Profession.KING);
        settlement.setReigningKing(heir.id());
        settlement.logEvent(ctx.step(),
                heir.name() + " takes the great hut and the warband follows him");
    }

    /**
     * The longest-standing resident of that trade, or of any trade.
     *
     * <p><strong>"Oldest" means longest here, and it is worth saying so out
     * loud.</strong> Nobody in this simulation has an age: a {@link Person}
     * carries a name, a trade, a position and a stomach, and nothing that
     * counts years. What a settlement does carry is the order people joined it —
     * {@code residents} is a {@code LinkedHashMap} — so the first guard in the
     * list is the one who has been carrying a weapon here longest, which is the
     * thing the rule was actually reaching for.
     *
     * <p>It is deterministic, which matters more than it sounds: the succession
     * has to pick the same heir on a reload, and a rule that read a real age
     * would need that age written into the save. When people do get ages this
     * becomes a comparison and nothing else about the rule changes.
     */
    private static Person oldest(Settlement settlement, Profession trade) {
        for (Person person : settlement.residents()) {
            if (person.profession() == Profession.KING) {
                continue;
            }
            if (trade == null || person.profession() == trade) {
                return person;
            }
        }
        return null;
    }
}
