package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;

/**
 * One straight spine, with crescent lanes looped off it.
 *
 * <p>A lane leaves the spine, bows out in a half-circle and comes back to the
 * spine further along, enclosing a lens of open ground. Lanes alternate sides
 * going up the spine, so from the air the town is a chain of lobes hung on one
 * road — which is the whole of its identity. Straighten the lanes and it is a
 * high street with side turnings, which is a different arrangement that already
 * exists.
 *
 * <p>This is a real form and a deliberate one wherever it appears: Bath's
 * crescents, the Georgian circuses, and the interwar estates that copied them
 * are all a through road with looped frontage hung off it. The loop earns its
 * keep — it carries frontage on both faces and encloses a green nobody drives
 * across.
 *
 * <h2>Why half-circles, centred on the spine</h2>
 *
 * <p>The lane could be any bow, and a sine bow was tried first. It is worse for
 * one reason that matters: two bows of different depth over the same run are not
 * parallel curves, so the gap between an outer lane and the inner one it encloses
 * narrows toward the mouths, and every plot between them there is refused. A
 * half-circle whose centre sits <em>on</em> the spine makes the ranks properly
 * concentric — the gap between them is {@link #RANK_GAP} everywhere — and hands
 * the whole of {@link RadialStreetLayout}'s arithmetic over unchanged, which is
 * arithmetic this codebase has already paid to get right three times.
 *
 * <p>It also means one number describes a lane: its radius is how far it bows out
 * <em>and</em> half how far it runs along the spine. The mouths land exactly on
 * the spine rather than near it.
 *
 * <h2>How it grows</h2>
 *
 * <p>Not by running the spine further, which is the fault {@code StreetLayout}
 * records: a hundred buildings on one street is a street a hundred buildings
 * long. A town wanting more room opens another crescent along the spine, and then
 * nests a second crescent outside the first at the same station. Nesting is the
 * cheaper of the two — an outer crescent is a longer arc, so the second rank at a
 * station carries about twice the frontage of the first without lengthening the
 * town at all.
 *
 * <p>Measured, as laid: a plan of 256 is two ranks at each of eight stations, and
 * puts 227 of its plots on the crescents and 29 on the spine between them. A town
 * of sixty-four reaches 113 blocks, one of a hundred and forty reaches 213, and
 * every plot at both sizes fronts a street.
 */
public final class CrescentLayout extends PlannedLayout {

    /**
     * How far the innermost crescent bows out from the spine.
     *
     * <p>Also half its run along the spine, since the lane is a half-circle
     * centred on the spine: it leaves at {@code zc - 45}, reaches 45 blocks out
     * at {@code zc}, and rejoins at {@code zc + 45}.
     *
     * <p>Bounded below by what has to fit inside the lens. The inner rank of
     * houses stands {@link #SETBACK} inside the lane, so the open ground left in
     * the middle runs from the spine's kerb out to {@code 45 - 13 - 5} — sixteen
     * blocks of green, sixty long. Below about forty the inner rank closes on the
     * spine and the lens stops being ground anybody would call a green; above
     * about sixty the lane runs so far along the spine that its two mouths
     * swallow the frontage between stations.
     */
    private static final int FIRST_BOW = 45;

    /**
     * How far apart nested crescents at one station run.
     *
     * <p>{@link RadialStreetLayout#RING_SPACING} by another name and for the same
     * reason. Twice the setback plus the carriageway is a floor of thirty-four on
     * a straight street; on a curve every clearance is worth {@code sqrt(2)} times
     * what a straight street needs, because two plots separated radially are
     * separated on the <em>wider axis</em> by only {@code gap / sqrt(2)} where the
     * arc runs diagonally. At thirty-four the facing ranks of two nested crescents
     * measure 5.7 apart on the wider axis on the diagonals and every one of them
     * is refused. Forty-six leaves twenty blocks between the two facing rows,
     * which is 14.1 on the diagonal and clears the twelve the siting code demands.
     */
    private static final int RANK_GAP = 46;

    /**
     * How much of a crescent one plot takes, measured along its own arc.
     *
     * <p>Wider than the {@link #PITCH} a straight street uses, and it has to be:
     * separation is measured on the wider axis, so two plots a chord apart on a
     * curve are only {@code chord / sqrt(2)} apart where the arc runs diagonally.
     * At the ordinary pitch of fourteen that is 9.9, inside the twelve the siting
     * code demands, and every plot on the diagonal quarters of every crescent is
     * refused. Eighteen is {@code 12 * sqrt(2)} rounded up.
     *
     * <p>Applied to each face on <em>its own</em> radius, never the lane's. The
     * inner face of the first crescent is a 100-block arc where the lane is a
     * 141-block one; spacing both by the lane packs the inner face at 12.8 along
     * its arc, which is 9.1 on the wider axis and fails.
     */
    private static final int ARC_PITCH = 18;

    /**
     * Spine left clear beyond the outermost crescent's mouth at each station.
     *
     * <p>Stations alternate sides, so the two crescents that have to clear each
     * other are two stations apart — a whole {@code 2 * stride} — and what has to
     * clear is the last plot each of them puts near the spine. On the outermost
     * face of a two-rank station, a hundred and four blocks out, that plot stands
     * a hundred blocks along the spine from its own station; so the two of them
     * sit {@code 2 * MOUTH_ROOM - 19} apart, at the same distance off the spine,
     * which is the wider axis and the one that counts. Twenty gives twenty-one
     * against the twelve the siting code demands. Twelve gives five and one of
     * every such pair is thrown away.
     */
    private static final int MOUTH_ROOM = 20;

    /**
     * How many crescents may be nested at one station.
     *
     * <p>A bound rather than a target, and the bound is what the town looks like
     * rather than what fits. Three ranks was built and measured: it packs the same
     * plots into a rounder town — 168 blocks of reach at a hundred and forty plots
     * against 213 — and loses the arrangement doing it. The stride has to grow
     * with the outermost lane, but not as fast as the lane does, so at three ranks
     * the outer lanes of neighbouring stations overlap along more than a hundred
     * blocks of spine: the chain of lobes closes up into one blob and the town is
     * a worse ring town. The silhouette is the whole of this arrangement's
     * identity and it is not worth fifty blocks of reach.
     *
     * <p>Kept at three rather than two so an ask far past any real town has
     * somewhere to go besides running the spine to the horizon.
     */
    private static final int MOST_RANKS = 3;

    /** A bound on stations, so a plan that cannot fill runs out rather than on. */
    private static final int MOST_STATIONS = 32;

    /** The spine is always the plan's first street. */
    private static final int SPINE = 0;

    /**
     * How much of a station's offered frontage is expected to survive.
     *
     * <p>Used only to decide how many stations to draw. Offers die at the mouths,
     * where a lane crosses the spine and everything within a carriageway of the
     * crossing is refused: a two-rank station offers 46 pieces of frontage and
     * about 31 of them survive, measured on the seven-station plan that sixty-eight
     * draws.
     *
     * <p>Sixty rather than the sixty-eight that measures, and deliberately so.
     * At the measured rate a plan of 256 lands on seven stations having filled
     * exactly, with nothing at all in hand — and a plan that comes up one plot
     * short is not one plot short: {@code lay} asks its design again at twice the
     * size, which nests a third rank at every station and changes the shape of the
     * town. Guessing low draws an eighth station instead, which costs nothing but
     * the spine that serves it: offers are taken nearest-centre-first, so a plan
     * of 256 takes the same 213 blocks of reach at a hundred and forty either way,
     * and lands on 227 plots off the crescents and 29 off the spine.
     */
    private static final double FRONTAGE_THAT_SURVIVES = 0.60;

    private final String id;

    public CrescentLayout() {
        this("crescents");
    }

    public CrescentLayout(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    protected void design(SimPos centre, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        int ranks = ranksFor(wanted);
        int stride = strideFor(ranks);
        int stations = stationsFor(wanted, ranks, stride);

        int furthest = 0;
        for (int station = 0; station < stations; station++) {
            furthest = Math.max(furthest, Math.abs(stationZ(station, stride)));
        }
        int reach = furthest + laneRadius(ranks - 1) + PITCH;

        // The spine first, and always index nought. A plot records the street it
        // fronts by index, so a plan that renumbered its roads as it grew would
        // repoint every door in the town.
        //
        // Drawn as a run of points every SEGMENT blocks rather than as the two
        // ends it geometrically is, though it is dead straight and the two ends
        // describe it exactly. The points are not for the shape; they are the
        // unit the rest of the town works in. PathPlanner routes a street a
        // stretch at a time on purpose -- "a spine runs a thousand blocks, so one
        // ravine anywhere along it condemns the lot", and routing whole streets
        // once cost a measured town twenty-three of its sixty-two buildings. A
        // two-point spine hands that fault straight back: one refusal, and the
        // whole main road of the town is given up permanently. The same points
        // are what lets the dev renderer and the paving layer keep the near end
        // of the spine while a young town has not grown out to the far one.
        streets.add(northSouth(centre, Wander.STRAIGHT, 0, -reach, reach,
                ROAD_HALF * 2, TownPlan.Kind.SPINE));

        // Then the crescents, station by station and innermost rank first, so a
        // town that opens another station appends indices rather than shifting
        // the ones its houses already front.
        for (int station = 0; station < stations; station++) {
            int zc = stationZ(station, stride);
            int side = sideOf(station);
            for (int rank = 0; rank < ranks; rank++) {
                streets.add(crescentLane(centre, zc, side, laneRadius(rank)));
            }
        }

        // Frontage on both faces of every crescent. The rank outside the lane
        // looks in across it and over the green; the rank inside stands between
        // the lane and the green with its back to the grass, which is what a
        // terrace on the inside of a crescent is. Both front the same lane and
        // both say so.
        for (int station = 0; station < stations; station++) {
            int zc = stationZ(station, stride);
            int side = sideOf(station);
            for (int rank = 0; rank < ranks; rank++) {
                int lane = laneRadius(rank);
                int street = SPINE + 1 + station * ranks + rank;
                for (int face : new int[] {-1, 1}) {
                    // Each face on its own arc length, not the lane's.
                    int at = lane + face * SETBACK;
                    int along = Math.max(3, (int) (Math.PI * at / ARC_PITCH));
                    for (int i = 0; i < along; i++) {
                        double turn = -Math.PI / 2 + (i + 0.5) * Math.PI / along;
                        SimPos where = onArc(centre, zc, side, at, turn);
                        SimPos onLane = onArc(centre, zc, side, lane, turn);
                        offers.add(new Offer(where, street,
                                Layout.facingToward(where, onLane)));
                    }
                }
            }
        }

        // And along the spine itself, which is what stops the stations reading as
        // a string of hamlets. Both sides, except where a crescent has already
        // taken the ground: a lane's lens belongs to the lane, and spine frontage
        // standing in it would fill the one piece of open ground this arrangement
        // exists to enclose.
        int rows = reach / PITCH;
        for (int k = -rows; k <= rows; k++) {
            int z = k * PITCH;
            for (int face : new int[] {-1, 1}) {
                SimPos where = new SimPos(centre.x() + face * SETBACK, centre.y(),
                        centre.z() + z);
                if (insideALens(centre, where, stations, stride, face)) {
                    continue;
                }
                offers.add(new Offer(where, SPINE, face > 0 ? 3 : 1));
            }
        }
    }

    /**
     * How many crescents are nested at one station, for a town of this size.
     *
     * <p>Growth is shared between opening stations and nesting ranks, and the
     * divisor is what decides the share. Ninety puts a plan of 256 — the size
     * every plan is laid at — on two ranks, which is the arrangement the eye reads
     * as a chain of lobes with a lobe inside each. Fifty-five gives three ranks
     * and four stations, and {@link #MOST_RANKS} records what that measured.
     */
    private static int ranksFor(int wanted) {
        return Math.max(1, Math.min(MOST_RANKS,
                (int) Math.ceil(Math.sqrt(Math.max(1, wanted) / 90.0))));
    }

    /** How far apart successive stations sit along the spine. */
    private static int strideFor(int ranks) {
        return laneRadius(ranks - 1) + MOUTH_ROOM;
    }

    /** How far out the nth nested crescent at a station bows. */
    private static int laneRadius(int rank) {
        return FIRST_BOW + rank * RANK_GAP;
    }

    /**
     * How many stations a town of this size opens.
     *
     * <p>Counted rather than guessed at, the way {@link RadialStreetLayout}
     * counts its rings: a station's capacity depends on how many ranks it has and
     * how long their arcs are, so a formula in the plot count alone either runs
     * out of frontage or draws lanes nothing fronts.
     */
    private static int stationsFor(int wanted, int ranks, int stride) {
        int room = 0;
        int stations = 0;
        while (room < wanted && stations < MOST_STATIONS) {
            room += (int) (roomAtAStation(ranks) * FRONTAGE_THAT_SURVIVES)
                    + stride / PITCH;
            stations++;
        }
        return Math.max(2, stations);
    }

    /** Frontage offered at one station, over all its ranks and both faces. */
    private static int roomAtAStation(int ranks) {
        int room = 0;
        for (int rank = 0; rank < ranks; rank++) {
            int lane = laneRadius(rank);
            room += (int) (Math.PI * (lane - SETBACK) / ARC_PITCH)
                    + (int) (Math.PI * (lane + SETBACK) / ARC_PITCH);
        }
        return room;
    }

    /**
     * Where the nth station sits along the spine, walking outward from the middle.
     *
     * <p>Half a stride off centre, so the town's middle is a length of open spine
     * between two crescents rather than the inside of one. Offers are taken
     * nearest-centre-first, so that length is where the first houses of a young
     * town stand — a street, before it has grown any lobes.
     */
    private static int stationZ(int station, int stride) {
        int step = (station % 2 == 0) ? station / 2 : -(station + 1) / 2;
        return (int) Math.round((step + 0.5) * stride);
    }

    /**
     * Which side of the spine the nth station's crescents hang off.
     *
     * <p>Read off the station's position rather than its index, so the sides
     * alternate in the order somebody walking the spine meets them and not in the
     * order the plan happened to draw them.
     */
    private static int sideOf(int station) {
        int step = (station % 2 == 0) ? station / 2 : -(station + 1) / 2;
        return step % 2 == 0 ? 1 : -1;
    }

    /**
     * Whether this spine frontage would stand in a crescent's enclosed ground.
     *
     * <p>Judged against the innermost lane's own circle, so the whole lens is left
     * to the crescent. Being stricter than the siting code would be costs a little
     * frontage at the flanks of each mouth and buys the one thing this arrangement
     * is for: a green the plan can promise is empty rather than one that happens
     * to be empty today.
     */
    private static boolean insideALens(SimPos centre, SimPos where, int stations,
                                       int stride, int face) {
        for (int station = 0; station < stations; station++) {
            if (sideOf(station) != face) {
                continue;   // the lens is on the crescent's own side of the spine
            }
            double dx = where.x() - centre.x();
            double dz = where.z() - (centre.z() + stationZ(station, stride));
            if (Math.hypot(dx, dz) < FIRST_BOW) {
                return true;
            }
        }
        return false;
    }

    /** One crescent, as the run of points a lane looping off the spine passes through. */
    private static TownPlan.Street crescentLane(SimPos centre, int zc, int side, int radius) {
        List<SimPos> path = new ArrayList<>();
        int steps = Math.max(6, (int) (Math.PI * radius / SEGMENT));
        for (int i = 0; i <= steps; i++) {
            double turn = -Math.PI / 2 + i * Math.PI / steps;
            path.add(onArc(centre, zc, side, radius, turn));
        }
        return new TownPlan.Street(path, ROAD_HALF * 2, TownPlan.Kind.LANE);
    }

    /**
     * A point on a crescent of this radius, at this turn round it.
     *
     * <p>The turn runs from {@code -pi/2} at the lane's first mouth through nought
     * at its furthest point to {@code +pi/2} at the second mouth, and at both ends
     * the cosine is nought — so a lane's mouths land exactly on the spine rather
     * than near it, whatever radius it has.
     */
    private static SimPos onArc(SimPos centre, int zc, int side, double radius, double turn) {
        return new SimPos(
                centre.x() + (int) Math.round(side * radius * Math.cos(turn)),
                centre.y(),
                centre.z() + zc + (int) Math.round(radius * Math.sin(turn)));
    }
}
