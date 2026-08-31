package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;

/**
 * What a charter actually creates.
 *
 * <p>Lives here rather than in the item because a founding party is a fact
 * about the simulation, not about right-clicking. It was written out longhand
 * inside {@code FoundingCharterItem}, which meant the one path a player can
 * actually take was also the one path no test could reach — and meant
 * {@code /civ found}, which every playtest and every scripted run goes through,
 * quietly created something else entirely: a settlement with a kit and nobody
 * to spend it. Towns founded that way sat at population zero forever, and the
 * scripts had to follow with {@code /civ populate} to paper over it.
 *
 * <p>Both now come through here, so a headless run founds the same party a
 * player's charter does.
 */
public final class Founding {

    /**
     * The claim a brand-new settlement lays around its centre.
     *
     * <p>The same for a charter, a console command and a daughter colony —
     * three places that had each written 64 down separately.
     */
    public static final int INITIAL_CLAIM = 64;

    /**
     * How far a town founded by the world itself may be moved to find ground.
     *
     * <p>A daughter colony or a generated town has no opinion about where it
     * lands, so it may look properly.
     */
    public static final int SITING_REACH = 64;

    /**
     * How far a town founded by a person may be moved.
     *
     * <p>Much less, and the difference is the whole point. Somebody who plants
     * a charter has chosen that spot — for the view, for the river, for reasons
     * the simulation cannot see — and a settlement that appears sixty blocks
     * away has overruled them. A dozen blocks is the difference between
     * "shifted off the cliff edge" and "went somewhere else".
     */
    public static final int CHARTER_REACH = 12;

    /** How wide a patch is judged when weighing a site. */
    private static final int SITE_RADIUS = 24;

    /** How far apart the candidate centres are tried. */
    private static final int SITE_STEP = 8;

    private Founding() {
    }

    /**
     * The best ground for a town within reach of where one was wanted.
     *
     * <p>Founding never looked at the ground at all. A town planted across a
     * ravine fights it forever: every street it plans runs into the cut, every
     * plot on the far side is refused, and no amount of cleverness downstream
     * recovers what choosing eight blocks to the left would have given for
     * nothing. The roads work that prompted this could route around a hillside
     * and could not undo having been founded on one.
     *
     * <p>Judged on the two things that actually stop a town building: standing
     * water, and how far the ground falls across the patch a town first fills.
     * Measured on the bulk rather than the extremes — the twentieth and
     * eightieth percentiles, the same rule the siting code uses — so a single
     * boulder does not condemn a shelf and a genuine slope is not excused by
     * flat ground either side of it.
     *
     * <p>Ties go to staying put: the score a candidate must beat includes how
     * far it has strayed, so a town only moves when moving is clearly better.
     * With no bridge to ask, it does not move at all.
     */
    public static SimPos bestSiteNear(SimPos wanted, int reach,
                                      com.kingdoms.sim.platform.WorldBridge ground) {
        if (ground == null || reach <= 0) {
            return wanted;
        }
        SimPos best = wanted;
        double bestScore = scoreSite(wanted, ground);
        for (int dz = -reach; dz <= reach; dz += SITE_STEP) {
            for (int dx = -reach; dx <= reach; dx += SITE_STEP) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double away = Math.hypot(dx, dz);
                if (away > reach) {
                    continue;   // a disc, not a box: a corner is not "near"
                }
                SimPos candidate = new SimPos(
                        wanted.x() + dx, wanted.y(), wanted.z() + dz);
                // Strayed ground has to be better by more than it has strayed.
                double score = scoreSite(candidate, ground) + away * DRIFT_PENALTY;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best.equals(wanted) ? wanted
                : new SimPos(best.x(), ground.surfaceHeight(best), best.z());
    }

    /** What a stretch of ground costs a town, lower being better. */
    private static double scoreSite(SimPos centre,
                                    com.kingdoms.sim.platform.WorldBridge ground) {
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        int wet = 0;
        int samples = 0;
        for (int dz = -SITE_RADIUS; dz <= SITE_RADIUS; dz += SITE_STEP) {
            for (int dx = -SITE_RADIUS; dx <= SITE_RADIUS; dx += SITE_STEP) {
                SimPos at = new SimPos(centre.x() + dx, centre.y(), centre.z() + dz);
                heights.add(ground.groundHeight(at));
                samples++;
                if (ground.standsInWater(at, 0)) {
                    wet++;
                }
            }
        }
        java.util.Collections.sort(heights);
        int low = heights.get(heights.size() / 5);
        int high = heights.get((heights.size() * 4) / 5);
        double fall = high - low;
        double drowned = samples == 0 ? 0 : (double) wet / samples;
        // Water is worse than slope: a town can terrace a hillside and cannot
        // drain a lake.
        return fall + drowned * DROWNED_PENALTY;
    }

    /** What a block of straying costs, against a course of fall. */
    private static final double DRIFT_PENALTY = 0.06;

    /** What being wholly under water costs, against courses of fall. */
    private static final double DROWNED_PENALTY = 60;

    /**
     * A settlement as a charter makes one: a camp, a party, and rations.
     *
     * <p>Pioneers, all of them — generalists who build, farm and haul as the
     * camp needs, with professions crystallizing as the stages demand them. A
     * party split into half builders and half idlers is how one ended up with
     * idlers it could not turn into farmers.
     *
     * <p>The food is carried by the settlers rather than banked, because until
     * the first house stands there is no larder to fetch from: what they have
     * on them is what they live on. The timber and stone come from
     * {@link TownStores#founding}, laid on open ground until somebody raises a
     * store to put it in.
     */
    public static Settlement party(SimPos site, String name) {
        Settlement settlement = new Settlement(Settlement.Id.random(), name, site, INITIAL_CLAIM);
        // Fresh foundings live the ladder. Only loaded saves default to TOWN.
        settlement.setStage(SettlementStage.CAMP);
        for (int i = 0; i < TownStores.FOUNDING_SETTLERS; i++) {
            Person settler = new Person(Person.Id.random(), "Settler " + (i + 1),
                    Profession.PIONEER, site);
            settler.inventory().add(Foods.PROVISION, TownStores.FOUNDING_PROVISIONS_EACH);
            settlement.addResident(settler);
        }
        return settlement;
    }
}
