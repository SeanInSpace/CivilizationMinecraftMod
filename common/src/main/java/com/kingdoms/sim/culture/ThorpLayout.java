package com.kingdoms.sim.culture;

import com.kingdoms.sim.geom.SimPos;

import java.util.List;

/**
 * A farming hamlet: yards hung off a track.
 *
 * <p>A track runs through, and short cul-de-sac lanes leave it at intervals,
 * alternating sides. Each lane runs thirty-odd blocks and ends in a
 * <em>yard</em> — a widening with buildings on three sides of it, so the lane
 * arrives somewhere instead of merely stopping. From the air it reads as a comb:
 * one long line with stubby teeth, each tooth ending in a blob.
 *
 * <p>This is the loose one. {@link Layouts#WARREN} is the other answer to the
 * same instinct and has no roads at all — knots of huts with open ground between
 * them — and this is the streets-first version of that thought: a scatter that
 * nonetheless has frontage. The ragged edge is the point rather than a defect.
 * A {@link PlannedLayout} with hard rectangular edges looks bulldozed when it
 * lands in a forest, and the teeth of a comb never line up.
 *
 * <p><strong>Growth opens lanes, then tracks.</strong> A track does not get
 * indefinitely longer: at seven plots to a lane a hamlet of two hundred and
 * fifty-six wants thirty-seven of them, and thirty-seven lanes strung along one
 * track is twelve hundred and ninety-two blocks of road — a ribbon, not a
 * place. So the lanes are shared out over parallel tracks, counted so the plan
 * stays roughly square. That is the same fault {@code StreetLayout} records at
 * its own lane count and the same shape of answer.
 */
public final class ThorpLayout extends PlannedLayout {

    /**
     * How far apart successive lanes leave the track.
     *
     * <p>Adjacent lanes point opposite ways, so this is the arithmetic floor
     * itself — twice the setback plus the width of the track — and it costs
     * nothing, because their frontage stands on opposite sides of the track and
     * sixty-eight blocks apart across it.
     *
     * <p>What this number is really solved for is the pair of lanes on the
     * <em>same</em> side, sixty-eight apart, whose yards face each other down
     * the track. A yard's flanks stand twenty off its own lane, so those two
     * yards leave twenty-eight blocks of open ground between them against the
     * fourteen inside a yard — two to one, and it is that ratio rather than the
     * separation rule that decides whether a yard reads as a yard. At the
     * twenty-six this was first drawn at the gap fell to twelve: legal, and the
     * blobs had merged into a hedge.
     */
    private static final int LANE_STEP = 34;

    /**
     * How far apart parallel tracks run.
     *
     * <p>Not solved for the separation rule, which is the trap. A lane's
     * furthest plot stands {@code BASE_RUN + RUN_STEP + SETBACK} — fifty-four —
     * from its own track, and two tracks send lanes at each other, so the heads
     * of two facing yards are {@code TRACK_SPACING - 108} apart. A hundred and
     * twenty-two satisfies every rule in the file and was measured: sixteen
     * blocks between yards on facing tracks against fourteen inside a yard, and
     * the two combs had meshed into one field of houses with a road down the
     * middle of it. A hundred and forty leaves thirty-two, which matches the
     * twenty-eight between two yards on the same track.
     *
     * <p>That is only affordable because every track of one hamlet takes the
     * <em>same</em> wander rather than a re-phased one. Two roads bending
     * independently by seven blocks each can close fourteen of the gap between
     * them, which would have cost another fourteen here; parallel tracks that
     * follow the same ground bend together, which is both cheaper and what a
     * pair of lanes over the same valley actually does.
     *
     * <p>Facing yards interleave rather than collide, because the side a lane
     * takes is decided by its place along the track: the lane opposite an
     * eastward one is always a lane-step away, so the two combs mesh like gears.
     *
     * <p>Even, because {@link #trackX} halves it.
     */
    private static final int TRACK_SPACING = 140;

    /** How far a lane runs from the track before its yard opens. */
    private static final int BASE_RUN = 34;

    /**
     * How much longer some lanes run, so the town's edge is not a ruled line.
     *
     * <p>Chosen by a hash of the lane and the town, not by the ground — reading
     * the terrain to plan has been tried twice and measured worse, and the note
     * on that is at the top of {@link PlannedLayout}. Half the lanes are the
     * long kind, which is enough to break the comb's outline without moving the
     * yards far enough to eat {@link #TRACK_SPACING}'s margin.
     */
    private static final int RUN_STEP = 7;

    /**
     * How far back from the lane's end the entrance pair stands.
     *
     * <p>These two are the narrow part, at the ordinary {@link #SETBACK} either
     * side; the yard beyond them stands at {@link #YARD_HALF}. That step from
     * thirteen to twenty is the widening, and it is the whole reason a lane ends
     * in a place rather than at a wall. A plot pitch back, because the pair is
     * only seven blocks off the flanks <em>across</em> the lane and so has to
     * clear them <em>along</em> it instead — which is exactly what a pitch is for.
     *
     * <p>Exactly on the floor, with no rounding slack, and that is safe here in a
     * way it would not be elsewhere: the offset along the lane is this constant
     * itself rather than anything derived from a wander, so the pair is a whole
     * pitch apart on that axis whatever the track does. It is the tightest pair in
     * the arrangement now — the head and the flanks, which used to be, sit at
     * thirteen.
     */
    private static final int SHANK_BACK = PITCH;

    /**
     * How far the yard's flanking buildings stand from the lane's centreline.
     *
     * <p>Seven blocks wider than the frontage that leads to it. Any less and the
     * yard is not a widening but more lane; much more and the buildings stop
     * reading as enclosing anything.
     */
    private static final int YARD_HALF = 20;

    /**
     * How far apart the three buildings across the head of the yard sit.
     *
     * <p>A plot pitch, and written as one now rather than as the number that
     * pitch happened to be. It also has to clear the flanks, which stand six
     * further out in z and thirteen nearer in x: thirteen on the wider axis, which
     * was the tightest pair in the whole arrangement until the pitch came down and
     * {@link #SHANK_BACK} took the title.
     */
    private static final int HEAD_SPREAD = PITCH;

    /**
     * How much frontage one lane is reckoned to carry.
     *
     * <p>A lane offers eight — two at the entrance, two flanking the yard, three
     * across its head, and one cottage across the track from the mouth — and
     * this is deliberately one less. An optimistic estimate costs a doubling of
     * the whole design in {@code lay()} and a town half again as big; a
     * pessimistic one costs a few lanes nobody fills, and the nearest-first sort
     * never reaches them.
     */
    private static final int PLOTS_PER_LANE = 7;

    /**
     * How many lanes one track carries before another track is worth opening.
     *
     * <p>Solved for a square hamlet rather than picked. A track's lanes stretch
     * it {@code LANE_STEP / 2} in z each; a track widens the town by
     * {@code TRACK_SPACING / 2} either side. Setting those equal puts the
     * crossover at about five lanes to the track — one track to five lanes, two
     * to twenty, three to forty-five — so the count is {@code sqrt(lanes / 5)}.
     * A division would open a track per five lanes however many there were, and
     * hand back a ribbon of tracks instead of a hamlet.
     */
    private static final double LANES_PER_TRACK = 5.0;

    private final String id;
    private final Wander wander;

    /** The default: a track that was a cart track first, and still looks it. */
    public ThorpLayout() {
        this(Culture.LAYOUT_THORP, Wander.gentle(7, 0x7401D5L));
    }

    public ThorpLayout(String id, Wander wander) {
        this.id = id;
        this.wander = wander;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isSameShapeEverywhere() {
        // The lane lengths and the track's bends are both drawn from the town's
        // own centre, so no two thorps are the same thorp twice.
        return false;
    }

    /** How much this arrangement's track strays, for tests and for the viewer. */
    public Wander wander() {
        return wander;
    }

    @Override
    public int claimMargin() {
        return 12;   // the yards stand well off the track; the claim has to cover them
    }

    @Override
    protected void design(SimPos centre, int wanted,
                          List<TownPlan.Street> streets, List<Offer> offers) {
        int lanes = lanesFor(wanted);
        int tracks = tracksFor(lanes);
        int perTrack = (lanes + tracks - 1) / tracks;
        // A track runs one lane-step past its outermost lane, so it reads as a
        // route through the hamlet rather than as something that begins at the
        // first farm and gives up after the last.
        int reach = (perTrack / 2 + 1) * LANE_STEP;

        // Every track first, then every lane. A plot records the street it
        // fronts by index, so the order streets are emitted in is the order they
        // must stay in -- StreetLayout has the note about what a renumbering
        // costs. Tracks before lanes means opening a lane cannot renumber a
        // track, which is the pairing a growing hamlet actually does.
        //
        // One bend, shared by every track. See TRACK_SPACING: independently
        // phased tracks would close fourteen blocks of the gap between two
        // facing yards in the worst case, and buying that back would cost
        // fourteen blocks of hamlet.
        Wander bend = wanderFor(wander, centre, 0);
        for (int track = 0; track < tracks; track++) {
            streets.add(northSouth(centre, bend, trackX(track, tracks), -reach, reach,
                    ROAD_HALF * 2, TownPlan.Kind.SPINE));
        }

        for (int track = 0; track < tracks; track++) {
            int base = trackX(track, tracks);
            for (int nth = 0; nth < perTrack; nth++) {
                int step = alongTrack(nth);
                int laneZ = step * LANE_STEP;
                // Alternating by the lane's place along the track rather than by
                // the order it is emitted in: the lanes are laid out from the
                // middle outward, so emission order runs 0, +1, -1, +2, -2 and
                // sides taken from that would put two east lanes side by side.
                int side = (step % 2 == 0) ? 1 : -1;

                // Where the lane leaves the track is read off the track's own
                // wander, not off the straight line it is nominally on. Curving
                // the drawn road and leaving the frontage behind is the fault
                // Wander's own javadoc records, and a lane that starts where the
                // track is not would leave a gap in the carriageway.
                int mouth = base + bend.blocksAt(laneZ);
                int run = runOf(centre, track, nth);
                int lane = streets.size();
                streets.add(eastWest(centre, Wander.STRAIGHT, laneZ,
                        mouth, mouth + side * run, ROAD_HALF, TownPlan.Kind.LANE));

                yard(centre, offers, lane, laneZ, mouth, side, run);

                // One cottage across the track from the lane's mouth, and that
                // is all the track itself carries. A bare street costs twice --
                // it refuses every plot it crosses and gives nothing back, which
                // held a ring town to 62% frontage with six lanes earning their
                // keep as fences -- but a thorp is yards, not a high street, and
                // frontage along the whole track would make it one.
                //
                // The far side, so the cottage does not stand in its own lane's
                // mouth. It lands a plot pitch from the neighbouring lane's
                // entrance pair on the wider axis, which is the tightest this
                // arrangement gets and clears.
                SimPos across = at(centre, mouth - side * SETBACK, laneZ);
                offers.add(new Offer(across, track,
                        Layout.facingToward(across, at(centre, mouth, laneZ))));
            }
        }
    }

    /**
     * The buildings round one yard, and the pair that lead into it.
     *
     * <p>Three sides face in and the fourth is the lane, which is what makes a
     * yard a yard. Everything is measured from the lane's end rather than from
     * the track, so a longer lane carries its whole yard outward instead of
     * stretching it — a yard that stretched with its lane would pull the head
     * away from the flanks and stop enclosing anything.
     */
    private void yard(SimPos centre, List<Offer> offers, int lane, int laneZ,
                      int mouth, int side, int run) {
        // The entrance pair, at the ordinary setback: the narrow part.
        int shankX = mouth + side * (run - SHANK_BACK);
        for (int off : new int[] {-SETBACK, SETBACK}) {
            SimPos where = at(centre, shankX, laneZ + off);
            offers.add(new Offer(where, lane,
                    Layout.facingToward(where, at(centre, shankX, laneZ))));
        }

        // The yard's two flanks, standing wider, looking in across it.
        int flankX = mouth + side * run;
        for (int off : new int[] {-YARD_HALF, YARD_HALF}) {
            SimPos where = at(centre, flankX, laneZ + off);
            offers.add(new Offer(where, lane,
                    Layout.facingToward(where, at(centre, flankX, laneZ))));
        }

        // The head: three across the far end, all looking back down the lane.
        // Faced at the mouth rather than at the nearest point of road, or the
        // outer two would turn to face each other across the yard and present
        // their gable ends to everybody arriving.
        int headX = mouth + side * (run + SETBACK);
        for (int off : new int[] {-HEAD_SPREAD, 0, HEAD_SPREAD}) {
            SimPos where = at(centre, headX, laneZ + off);
            offers.add(new Offer(where, lane,
                    Layout.facingToward(where, at(centre, mouth, laneZ))));
        }
    }

    /** How many lanes a hamlet of this size opens. */
    private static int lanesFor(int wanted) {
        return Math.max(1, (int) Math.ceil(Math.max(1, wanted) / (double) PLOTS_PER_LANE));
    }

    /** How many parallel tracks those lanes are shared over. */
    private static int tracksFor(int lanes) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(lanes / LANES_PER_TRACK)));
    }

    /** Where the nth track runs, as an offset from the hamlet's middle. */
    private static int trackX(int track, int tracks) {
        return (2 * track - (tracks - 1)) * TRACK_SPACING / 2;
    }

    /**
     * Which place along the track the nth lane takes: 0, +1, -1, +2, -2, ...
     *
     * <p>Outward from the middle, so opening another lane appends a street
     * rather than shifting the ones already numbered, and so a hamlet that has
     * only filled its first few plots has them round its middle.
     */
    private static int alongTrack(int nth) {
        return (nth + 1) / 2 * (nth % 2 == 1 ? 1 : -1);
    }

    /**
     * How far this particular lane runs, which is one of two lengths.
     *
     * <p>Seeded from the town's own centre as well as the lane, so the ragged
     * edge is a different raggedness in every hamlet on the map. A repeated
     * outline reads worse than a straight one because it reads as a repeated
     * asset.
     */
    private static int runOf(SimPos centre, int track, int nth) {
        long mixed = (long) centre.x() * 0x9E3779B97F4A7C15L
                ^ (long) centre.z() * 0xC2B2AE3D27D4EB4FL
                ^ track * 0x100000001B3L ^ nth * 0x9E3779B1L;
        mixed ^= mixed >>> 31;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        return BASE_RUN + (int) ((mixed >>> 17) & 1L) * RUN_STEP;
    }

    private static SimPos at(SimPos centre, int dx, int dz) {
        return new SimPos(centre.x() + dx, centre.y(), centre.z() + dz);
    }
}
