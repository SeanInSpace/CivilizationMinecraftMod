package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.world.SimContext;

/**
 * Buildings notice when they have been knocked about, and fix themselves when
 * it gets bad enough to be worth the timber.
 *
 * <p>Nothing in the simulation used to look at a standing building ever again.
 * Once raised it was raised, and a cottage with its front wall blown off was
 * still, as far as the town was concerned, a cottage — counted for housing,
 * assigned to a family, audited for a door that was now a hole. That was
 * tolerable while nothing in the mod destroyed buildings. It stopped being
 * tolerable when creepers arrived.
 *
 * <p><strong>How damage is measured.</strong> Not by watching for explosions,
 * which would mean catching every possible way a block can leave — fire, lava,
 * a player with a pickaxe, another mod entirely. Instead the building is
 * counted when it is first seen whole, and counted again afterwards; the
 * shortfall is the damage. It does not matter what took the blocks.
 *
 * <p>The census is deliberately not the same thing as the blueprint. A
 * blueprint says what should have been laid; a census says what actually stood
 * there once the ground had been leveled, the doorway cut, and the builders
 * had finished improvising. Judging against the blueprint would report every
 * building as damaged from the day it was finished.
 *
 * <p><strong>How little damage is worth mending.</strong> A twentieth of a
 * building. That is not fussiness: a cottage counts a few hundred blocks, so
 * five per cent of one is a dozen blocks gone — a creeper's worth of wall, or a
 * hole a player can walk through. The old figure was a quarter, and a quarter of
 * a house missing is a house with a whole side off; a town that ignored anything
 * short of that left every ordinary bit of war damage standing forever, which is
 * exactly what was reported.
 *
 * <p>Below that share it is noise rather than damage, and the noise is real: the
 * census counts blocks inside a box, and torches burn out, snow melts, crops are
 * cut, and a plot's apron holds grass that comes and goes. {@link #NOISE_FLOOR}
 * is where that stops being an explanation.
 *
 * <p><strong>What a repair costs and who does it.</strong> The blocks it puts
 * back, carried from the stores by a builder — see {@code BuildTask.isRepair}.
 * It is not a re-stamp of the blueprint, and there is no path here that draws a
 * building which is already standing.
 */
public final class RepairPlanner {

    private RepairPlanner() {
    }

    /**
     * Damage at or above this, in percent, and the town says so out loud.
     *
     * <p>No longer the point at which anything is done about it — that is
     * {@link #NOISE_FLOOR} now — only the point at which the shortfall is worth
     * a line in the town's own record. A quarter of a building missing is a wall
     * gone or a roof open to the weather, and a player reading the log wants that
     * distinguished from the fortnight's ordinary wear.
     *
     * <p>It is also one end of the scale {@code TownAuditor.STILL_A_BUILDING}
     * sits on, and the two are deliberately the same number: a quarter missing is
     * when the town admits the building is hurt, three quarters of the walls
     * missing is when it admits there is no building.
     */
    public static final int SEVERE_DAMAGE = 25;

    /**
     * Below this, in percent, the building is called whole again.
     *
     * <p>Not zero. A census taken through a doorway a settler happened to be
     * standing in, or with a torch burned out, comes back a block or two light,
     * and a building that flickered between "damaged" and "sound" every step
     * would fill the event log with nothing.
     *
     * <p>This is now the repair threshold as well, and the two really are one
     * question: damage the town believes in is damage the town mends. There is no
     * band left in between where a building is recorded as hurt and nothing is
     * ever done about it, because that band was the whole complaint.
     */
    public static final int NOISE_FLOOR = 5;

    /**
     * Fewer blocks than this missing is noise whatever share of the building it
     * comes to.
     *
     * <p>The percentage is the rule and this is its floor, for the one place the
     * percentage cannot speak: a small structure. A camp post counts a couple of
     * dozen blocks, so one block off it already reads as five per cent — and one
     * block is a torch, a fence panel, a slab somebody helped themselves to.
     * Two is a hole. On anything the size of a cottage the share binds long
     * before this does and it never comes up.
     */
    public static final int NOISE_BLOCKS = 2;

    /** Work a full rebuild would cost, scaled by how much is actually missing. */
    public static int repairWork(BuildingType type, int damagePercent) {
        return Math.max(1, type.workCost() * damagePercent / 100);
    }

    /**
     * One pass: re-count what is standing, record the shortfall, and queue a
     * repair for anything badly hurt.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        for (Building building : settlement.buildings()) {
            if (!building.isMaterialized() || !building.footprint().isKnown()) {
                continue;
            }
            int standing = ctx.bridge().solidBlocksIn(building.origin(), building.footprint());
            if (standing < 0) {
                continue;   // nobody is there to look; an unwatched building is not decaying
            }
            if (!building.hasCensus()) {
                // First sight of it whole. This is the baseline everything after
                // is judged against.
                building.setSoundCensus(standing);
                continue;
            }
            if (standing >= building.soundCensus()) {
                // Whole again, or better than it was — somebody has repaired or
                // improved it. Take the higher figure as the new truth.
                building.setSoundCensus(standing);
                markSound(settlement, building, ctx);
                continue;
            }
            int missing = building.soundCensus() - standing;
            if (missing < NOISE_BLOCKS) {
                markSound(settlement, building, ctx);
                continue;
            }
            int damage = Math.min(100, missing * 100 / building.soundCensus());
            record(settlement, building, damage, ctx);
        }
    }

    private static void record(Settlement settlement, Building building, int damage,
                               SimContext ctx) {
        if (damage < NOISE_FLOOR) {
            markSound(settlement, building, ctx);
            return;
        }
        boolean wasSevere = building.damage() >= SEVERE_DAMAGE;
        building.setDamage(damage);
        if (damage >= SEVERE_DAMAGE && !wasSevere) {
            settlement.logEvent(ctx.step(), niceName(building) + " at " + building.origin()
                    + " is badly damaged — " + damage + "% of it is gone");
        }
        queueRepair(settlement, building, damage, ctx);
    }

    private static void markSound(Settlement settlement, Building building, SimContext ctx) {
        if (building.damage() >= SEVERE_DAMAGE) {
            settlement.logEvent(ctx.step(),
                    niceName(building) + " at " + building.origin() + " is whole again");
        }
        building.setDamage(0);
    }

    /**
     * Books the work, unless it is already booked or the town cannot start it.
     *
     * <p>Enqueued against the building's own plot at its own level, which is what
     * a repair is: the completion path already knows how to finish work on a
     * building in place without the town believing it now owns two of them. It is
     * marked a repair as well as an improvement, because the two want opposite
     * things from the ground — see {@code BuildTask.isRepair}.
     */
    private static void queueRepair(Settlement settlement, Building building, int damage,
                                    SimContext ctx) {
        for (BuildTask queued : settlement.buildQueue()) {
            if (isWorkOn(queued, building)) {
                return;   // already being seen to
            }
        }
        BuildingType type = settlement.catalogue().stream()
                .filter(candidate -> candidate.id()
                        .equals(BuildPlanner.baseIdOf(building.blueprintId())))
                .findFirst()
                .orElse(null);
        if (type == null) {
            return;   // a building the catalog no longer knows; nothing to rebuild it from
        }
        int work = repairWork(type, damage);
        if (!canBeMended(settlement, work)) {
            return;
        }
        BuildTask ordered = new BuildTask(building.blueprintId(), building.origin(), work);
        ordered.setFacing(building.facing());
        ordered.setUpgradeOf(building.origin());
        ordered.setRepair(true);
        settlement.enqueueUrgent(ordered);
        settlement.logEvent(ctx.step(), "Repairs begin on the " + niceName(building)
                + " at " + building.origin());
    }

    /** Whether work standing on the queue is this building's repair. */
    public static boolean isWorkOn(BuildTask queued, Building building) {
        SimPos target = queued.upgradeOf();
        // The plot, not the whole origin. A building's x and z are its ground and
        // never move; its y is whatever the survey settled on, and the record and
        // the task are written at different moments — which is why every other
        // comparison in this machinery is made this way.
        return target != null
                && target.x() == building.origin().x()
                && target.z() == building.origin().z();
    }

    /**
     * Whether anybody in this town is fit to be sent out with a trowel.
     *
     * <p>Half of the demolition interlock, and it has to be askable from outside.
     * {@code TownAuditor.demolishRuins} spares a building with a repair on the
     * books; a booked repair with nobody left to work it would spare a ruin
     * forever, and a raid that kills the last builder is exactly that case. The
     * queue is head-blocking too, so the pinned job would stop the town ordering
     * anything else for the rest of the world's life. Asked afresh on every
     * sweep, the shield lifts with the crew, the shell is written off, and
     * writing it off is what takes the job off the queue.
     */
    public static boolean hasAbleBuilder(Settlement settlement) {
        return settlement.residents().stream()
                .anyMatch(p -> settlement.laboursAs(p, Profession.BUILDER)
                        && !p.isTooWeakToWork());
    }

    /**
     * Whether the town has the hands and the stock to see a repair through.
     *
     * <p>Asked before the job is booked rather than after, and that is the other
     * half of the interlock. A repair is slow now — a crew lays the missing
     * blocks one at a time out of loads they fetch — and three sweeps of a shell
     * reading as gone is a minute or two, so a building being mended has to be
     * spared. If a town booked repairs it could not begin, that mercy would apply
     * to every ruin it ever saw.
     *
     * <p>So the shape is deliberate. A wreck the town can mend is protected while
     * it mends it. A wreck it cannot — no builder alive and fit, or not enough
     * timber and stone in the whole settlement to pay for the blocks — is never
     * booked, is not protected, and is written off, which is the honest answer:
     * the town is not repairing it and is not going to.
     *
     * <p>The whole bill rather than a step of it, because a repair booked on the
     * strength of one affordable step parks itself at the head of the queue and
     * stops the town ordering anything else while it waits for stone.
     */
    private static boolean canBeMended(Settlement settlement, int work) {
        if (!hasAbleBuilder(settlement)) {
            return false;
        }
        return settlement.stores().has(TownStores.WOOD, BuildPlanner.WOOD_PER_WORK * work)
                && settlement.stores().has(TownStores.STONE, BuildPlanner.STONE_PER_WORK * work);
    }

    private static String niceName(Building building) {
        String id = BuildPlanner.baseIdOf(building.blueprintId());
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    /** Convenience for callers that only want the plot. */
    public static SimPos plotOf(Building building) {
        return building.origin();
    }
}
