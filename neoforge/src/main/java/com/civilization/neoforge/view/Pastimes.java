package com.civilization.neoforge.view;

import com.civilization.neoforge.entity.Pace;
import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Curfew;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Leisure;
import com.civilization.sim.person.NightRest;
import com.civilization.sim.person.Person;
import com.civilization.sim.person.Profession;
import com.civilization.sim.settlement.Alarm;
import com.civilization.sim.settlement.Beds;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.KingPlanner;
import com.civilization.sim.settlement.MarketPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SmithPlanner;
import com.civilization.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The town's idle half, made visible.
 *
 * <p>{@code Leisure} decides what somebody does with an empty hour; this finds
 * the well, walks them to it, sits them on the bench when there is one, and
 * turns two people who ended up at the same place toward each other. It is the
 * platform half of one feature and holds no opinions of its own about who is
 * idle — every such question goes to {@code Leisure.mayRest}, which can be
 * tested without a world.
 *
 * <p><strong>It produces nothing and consumes nothing.</strong> A pastime moves
 * a body and turns a head. It does not eat, gather, trade, repair or so much as
 * touch a store, and the reason is the rule the whole mod is built on: the
 * simulation must come out the same whether or not anybody is watching. Leisure
 * only exists where there is a body, so leisure must never be worth anything.
 *
 * <p>Run once a manager pass, after the daily routine and before the work
 * sweeps — which is what makes work outrank leisure without either of them
 * having to know about the other. A farmer given a pastime by this and then
 * steered to his field by {@code workFarmers} in the same pass ends up on the
 * field, and the pastime quietly lapses.
 */
final class Pastimes {

    // --- what it costs -----------------------------------------------------------

    /**
     * How near the destination counts as being at it: 2 blocks.
     *
     * <p>Much tighter than the eight the work steering uses, and for the same
     * reason a bed is: "somewhere about the place" is fine for a field and no use
     * at all for a bench. Somebody who stopped eight blocks short of the well
     * would be standing in the road looking at it.
     */
    private static final double ARRIVE = 2.0;

    /** How long an offer of places is reused before the town is looked at again. */
    private static final long OFFER_TTL_TICKS = 200L;

    /** How far round the square a bench, a fence or a gate is looked for. */
    private static final int FURNITURE_REACH = 10;

    /** How many of each to keep, so no one square offers forty benches. */
    private static final int FURNITURE_KEPT = 8;

    /** Where a seated body sits relative to the block it is sitting on. */
    private static final double SEAT_HEIGHT = 0.4;

    /**
     * Passes between a nod or a gesture during a conversation: 3, so about
     * every three seconds.
     *
     * <p>The arm swing is the same one builders use, which is the only animation
     * this mob has. Once every three seconds reads as somebody making a point;
     * once a second reads as somebody having an argument.
     */
    private static final int NOD_PASSES = 3;

    /** What marks the invisible seat entities as ours, so a stray one can be found. */
    static final String SEAT_TAG = "civilization_seat";

    // --- state --------------------------------------------------------------------

    private final ServerLevel level;
    private final SimWorld world;
    private final Function<UUID, PersonEntity> viewOf;

    /**
     * Whether the construction pass or the public works have this person's hands.
     *
     * <p>Handed in rather than worked out, because "a builder is working" is not
     * a fact about the build queue — a queued job the construction pass bailed
     * out of steers nobody, and that person is exactly the "waiting for hands"
     * case a pastime is for. Only the manager knows which it was.
     */
    private final Predicate<UUID> busyBuilding;

    /** What somebody is doing and until when, by person id. */
    private record Sitting(Leisure.Pastime what, BlockPos where, long endsAt) {
    }

    private final Map<UUID, Sitting> at = new HashMap<>();

    /** The invisible thing a seated person is riding, by person id. */
    private final Map<UUID, Display> seats = new HashMap<>();

    /** Who is in conversation with whom, and until when. */
    private final Map<UUID, UUID> talkingTo = new HashMap<>();
    private final Map<UUID, Long> talkUntil = new HashMap<>();

    /**
     * A town's places to be, and when they were last looked for.
     *
     * @param doors the front doors of the town's <em>housed</em> families, which
     *              is the list a {@code DOORWAY} is drawn from. It is held here
     *              rather than worked out at the moment of resolving because it
     *              also decides whether the doorway is offered at all — see
     *              {@link #offerIn}, and the rain fault it was written against
     */
    private record Offer(long at, List<Leisure.Place> places, List<SimPos> benches,
                         List<SimPos> fences, List<SimPos> gates, List<SimPos> doors) {
    }

    private final Map<UUID, Offer> offers = new HashMap<>();

    Pastimes(ServerLevel level, SimWorld world, Function<UUID, PersonEntity> viewOf,
             Predicate<UUID> busyBuilding) {
        this.level = Objects.requireNonNull(level, "level");
        this.world = Objects.requireNonNull(world, "world");
        this.viewOf = Objects.requireNonNull(viewOf, "viewOf");
        this.busyBuilding = Objects.requireNonNull(busyBuilding, "busyBuilding");
    }

    // --- the pass --------------------------------------------------------------------

    /**
     * One town's idle people, given somewhere to be.
     *
     * <p><strong>In the rain this pass has an obligation it does not have on a
     * dry day.</strong> The work sweeps take every outdoor trade off the roster
     * the moment {@code skyOver} answers wet — {@code workFarmers} and its
     * siblings return at their first line — so the people this pass is handed
     * in a downpour are not people with an idle hour, they are people who have
     * just put down a hoe in an open field. Standing where they are is the one
     * outcome that is worse than the one before. A pass that finds them nowhere
     * to go has half-worked: it stopped the work and left them in the weather,
     * which is exactly what the census caught — the farm plot emptied, 3 people
     * to 0, and the count under open sky rose, 9 to 12.
     *
     * <p>Three things follow, and all three are below. The offer must carry no
     * shelter it has not got ({@link #offerIn}); a sheltered kind must resolve
     * to a sheltered position ({@link #placeFor}); and the walk cap must know
     * that turning somebody away from a roof is not the same as turning them
     * away from the well ({@code Leisure.walkCap}). A town with genuinely
     * nowhere dry still sends nobody anywhere — that is a real answer — but it
     * no longer disguises its square as a porch to avoid giving it.
     */
    void tend(Settlement settlement) {
        long now = level.getGameTime();
        long clock = level.getDefaultClockTime();
        Leisure.Hour hour = Leisure.hourOf(clock, Curfew.LEAD_TICKS);
        Leisure.Sky sky = skyOver(settlement);
        Alarm alarm = settlement.alarm();
        // The one thing leisure must never win an argument with. This pass runs
        // after the daily routine and its steering therefore beats it, which is
        // exactly what is wanted for a workplace and a disaster for a bed: a
        // settler walked to his own door by the curfew and then walked back out
        // to the fire by this would never sleep at all. So once it is dark,
        // anybody with a mattress is the routine's business and nobody else's.
        // Whoever has no bed is still free to sit up at a fire, which is the
        // only reason the night offers anything.
        boolean bedtime = level.isDarkOutside() && NightRest.isNight(clock);
        Offer offer = offerIn(settlement, now);
        Leisure.Openings town = openingsIn(settlement, clock);
        int walking = walkersIn(settlement, now);
        List<Leisure.Attendee<UUID>> present = new ArrayList<>();

        for (Person person : settlement.residents()) {
            UUID id = person.id().value();
            PersonEntity view = viewOf.apply(id);
            boolean bodied = person.isEmbodied() && view != null && !view.isRemoved();
            boolean guard = person.profession() == Profession.GUARD;
            // A guard is never "called in" by the bell — the watch goes the other
            // way — so the question of whether the town is up in arms has to be
            // asked of the alarm directly for him, and of the bell for everybody
            // else. Getting this backwards is a watch that leans on its post
            // through a raid.
            boolean called = guard ? alarm.isRaised() : alarm.callsIn(person.profession());
            Leisure.Idleness who = new Leisure.Idleness(bodied, guard, called,
                    bodied && view.isInDanger(),
                    bodied && view.isSleeping(),
                    person.haul() != null || FoodPlanner.isGoingToEat(person),
                    Leisure.hasWork(person.profession(), hour, sky,
                            person.isTooWeakToWork(), openingsFor(town, person)));
            if (!Leisure.mayRest(who)) {
                stop(id, view);
                continue;
            }
            if (guard) {
                leanOnPost(settlement, view);
                continue;
            }
            if (bedtime && Beds.bedFor(settlement, person) != null) {
                stop(id, view);
                continue;   // they have a bed; getting into it is the routine's job
            }

            Sitting held = at.get(id);
            if (held != null && (now >= held.endsAt()
                    || !Leisure.stillFits(held.what(), hour, sky))) {
                stop(id, view);
                held = null;
            }
            if (held == null) {
                // The one man in the town with an offer of his own. A king is
                // not steered anywhere by his profession — his workplace is his
                // own doorstep — so left on the town's offer he would wander to
                // the well like anybody, which is not what a hall is for.
                boolean crowned = person.profession() == Profession.KING
                        || person.profession() == Profession.SHAMAN;
                // His hall's doorstep, and only if he has a hall. KingPlanner's
                // rallyPoint falls back to the middle of the town when there is
                // no seat standing, which is the right answer for a muster and
                // the rain fault's answer for a doorway: it would hand forKing
                // an open square wearing the DOORWAY label, three times over,
                // and stand the king in the weather. A seatless king simply has
                // the town's offer like everybody else.
                Building seat = crowned ? KingPlanner.seat(settlement) : null;
                List<Leisure.Place> offered = crowned
                        ? Leisure.forKing(seat == null ? null : seat.origin(),
                                offer.places())
                        : offer.places();
                // The sieve. In the wet nobody may be sent anywhere that is not
                // under a roof, and the weight table says so already — this says
                // it a second time, in terms of the offer rather than of the
                // table, because the fault was never a weight. See
                // Leisure.shelterIn.
                if (sky.isWet()) {
                    offered = Leisure.shelterIn(offered);
                }
                Leisure.Rest rest = Leisure.restFor(id, now, hour, sky, offered);
                if (rest == null) {
                    continue;   // nothing on offer at this hour; stand as before
                }
                // Asked after the choosing and not before it, which is the whole
                // of the second half of the rain fault. Choosing is a hash and a
                // table; the cost the cap defends is the pathfinding, which has
                // not happened yet. Asked first, the cap could not tell a
                // stroll to the well from a run for a doorway, and when the rain
                // took a dozen people off the rows in one pass it admitted six
                // of them and left the rest standing in it — the 9 -> 12 under
                // open sky. Asked here it knows which walk it is refusing.
                if (walking >= Leisure.walkCap(rest.what(), sky)) {
                    continue;   // the town has enough people crossing it already
                }
                // His hall is already the particular place rather than a
                // representative of a kind, so it goes through untouched.
                // placeFor would swap a DOORWAY for a neighbour's door, which is
                // right for everybody whose doorway was a stand-in and wrong for
                // the one whose was not.
                SimPos where = crowned ? rest.where()
                        : placeFor(settlement, person, rest, offer);
                held = new Sitting(rest.what(),
                        new BlockPos(where.x(), where.y(), where.z()), now + rest.ticks());
                at.put(id, held);
                walking++;
            }
            if (keep(id, view, held)) {
                present.add(new Leisure.Attendee<>(id, held.what(),
                        new SimPos(view.getBlockX(), view.getBlockY(), view.getBlockZ())));
            }
        }
        chat(present, now);
    }

    /**
     * What it is doing over this town, asked at the town's own middle.
     *
     * <p>Per town and not per level, because weather in Minecraft is per biome:
     * a village on a desert edge stands in a dry square while its far field is
     * in a downpour, and {@code level.isRaining()} would have it sheltering from
     * somebody else's rain. {@code isRainingAt} is the column question and
     * answers false in a desert, under a roof, and below the surface — all of
     * which are the right answer for the block it was asked about.
     *
     * <p><strong>Asked at the top of the column, not at the plan's y.</strong>
     * {@code Level.precipitationAt} refuses any position the motion-blocking
     * heightmap stands above — which is every block a settlement's recorded
     * centre ever is, because that centre is the ground somebody walks on rather
     * than the air over it. Asked there it answers "not raining" in a
     * thunderstorm, for ever, and the whole feature would have been dead code
     * that ran. So the surface is taken first and the question is asked of that.
     *
     * <p>Thunder is the dimension's rather than the column's — there is no
     * per-block thunder — so it is only taken to mean weather here where the
     * middle of the town is under open sky. A town in a biome it does not rain
     * in is dry through an ordinary storm, which {@code isRainingAt} already
     * says by itself; a blizzard over it is still a reason to be inside.
     *
     * <p><strong>It is worth nothing.</strong> Nothing downstream of this answer
     * touches a store, a field, a ledger or a yield — it decides which of the
     * places the town already has an idle body walks to, and which bodies the
     * work sweeps stand aside for, and that is all.
     *
     * <p>Shared with {@code PersonEntityManager} rather than read twice. The
     * whole of the rain fault was two halves that disagreed — leisure freed a
     * farmer and the farm sweep fetched him straight back — and two separate
     * readings of the same sky is how that kind of disagreement gets rebuilt.
     */
    Leisure.Sky skyOver(Settlement settlement) {
        // Asked once a second by this class and four times a tick by the work
        // sweeps, so the dry answer has to be free. A dimension that is not
        // raining cannot be raining on any column in it -- precipitationAt says
        // so itself, first line -- and that is two field reads against a
        // heightmap walk and a biome lookup.
        if (!level.isRaining() && !level.isThundering()) {
            return Leisure.Sky.FAIR;
        }
        long now = level.getGameTime();
        Wet cached = wet.get(settlement.id().value());
        if (cached != null && cached.at() == now) {
            return cached.sky();
        }
        SimPos center = settlement.center();
        BlockPos sky = new BlockPos(center.x(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.x(), center.z()),
                center.z());
        Leisure.Sky answer = Leisure.skyOf(level.isRainingAt(sky),
                level.isThundering() && level.canSeeSky(sky));
        wet.put(settlement.id().value(), new Wet(now, answer));
        return answer;
    }

    /** One town's sky, and the tick it was read on. */
    private record Wet(long at, Leisure.Sky sky) {
    }

    /**
     * The wet answer, held for the tick it was worked out on.
     *
     * <p>Every caller inside one tick wants the same answer and the weather does
     * not turn over between two lines of the same pass. Keyed by settlement
     * because the question is per town: rain is per biome, so two towns in one
     * world genuinely differ.
     */
    private final Map<UUID, Wet> wet = new HashMap<>();

    /**
     * What this town has on offer for each trade, gathered once a pass.
     *
     * <p>The judging moved to {@code Leisure.hasWork}, which can be tested
     * without a world; what is left here is the gathering, which cannot. Every
     * line quotes somebody else's ledger rather than forming an opinion —
     * {@code SmithPlanner} on the forge, {@code FoodPlanner} on the mill,
     * {@code MarketPlanner} on the stall — so the answer to "is the smith busy"
     * is the same answer the haul planner gets when it asks whether he can be
     * spared to carry something.
     *
     * <p>Gathered per settlement rather than per person because none of it
     * varies by person: a town has one forge and one mill, and asking them once
     * for the whole pass is the difference between three lookups and sixty.
     */
    private Leisure.Openings openingsIn(Settlement settlement, long clock) {
        return new Leisure.Openings(false,
                settlement.buildingWithRole(BuildingRole.CROP_FARM) != null,
                settlement.lumberArea() != null,
                settlement.mineArea() != null,
                settlement.buildingWithRole(BuildingRole.ANIMAL_FARM) != null,
                MarketPlanner.isOpen(clock),
                SmithPlanner.hasWorkInFront(settlement),
                FoodPlanner.millHasWork(settlement),
                settlement.buildingWithRole(BuildingRole.CARPENTRY) != null
                        && !settlement.buildQueue().isEmpty());
    }

    /**
     * The same, for one person: the one opening that is theirs alone.
     *
     * <p>Whether the construction pass or the public works already have this
     * person's hands, which outranks every trade and is the only thing in the
     * record that has to be asked per settler.
     */
    private Leisure.Openings openingsFor(Leisure.Openings town, Person person) {
        return new Leisure.Openings(busyBuilding.test(person.id().value()),
                town.field(), town.wood(), town.stone(), town.pens(), town.stall(),
                town.forge(), town.mill(), town.bench());
    }

    /** How many of this town's people are already walking to a pastime. */
    private int walkersIn(Settlement settlement, long now) {
        int walking = 0;
        for (Person person : settlement.residents()) {
            Sitting held = at.get(person.id().value());
            if (held == null || now >= held.endsAt()) {
                continue;
            }
            PersonEntity view = viewOf.apply(person.id().value());
            if (view != null && !view.isRemoved() && !hasArrived(view, held)) {
                walking++;
            }
        }
        return walking;
    }

    // --- being there ------------------------------------------------------------------

    /**
     * Walks somebody to their pastime, or settles them once they are at it.
     *
     * @return whether they have arrived, and so are somebody who might be talked to
     */
    private boolean keep(UUID id, PersonEntity view, Sitting held) {
        if (view.isPassenger()) {
            return true;   // already sitting; nothing to steer
        }
        if (!hasArrived(view, held)) {
            view.getNavigation().moveTo(held.where().getX() + 0.5, held.where().getY(),
                    held.where().getZ() + 0.5, Pace.WALK);
            return false;
        }
        view.getNavigation().stop();
        if (held.what().isSeated()) {
            sit(id, view, held.where());
        }
        return true;
    }

    private static boolean hasArrived(PersonEntity view, Sitting held) {
        double dx = view.getX() - (held.where().getX() + 0.5);
        double dz = view.getZ() - (held.where().getZ() + 0.5);
        return dx * dx + dz * dz <= ARRIVE * ARRIVE;
    }

    /**
     * Sits a body down on the block it is standing at.
     *
     * <p>Vanilla has no sitting pose for a mob and no way to ask for one. What it
     * has is a rider: {@code HumanoidModel} bends the legs of anything it draws
     * as a passenger, and {@code PersonRenderer} draws these people with exactly
     * that model. So a seat is a vehicle nobody can see.
     *
     * <p>A {@code BlockDisplay} showing no block, specifically, and the choice
     * took three tries. An armor stand cannot be made a marker from code any more
     * — {@code setMarker} is private in 26.2 — so it would leave a real hitbox in
     * the middle of the square. A {@code Marker} is worse: its own add-entity
     * packet throws, because markers are deliberately never sent to a client, so
     * nobody would see the rider ride. A block display is sized zero by zero,
     * defaults to air, and has no physics, which leaves exactly the seat and
     * nothing else.
     *
     * <p>Tagged so that a seat orphaned by a crash can be found and swept up. In
     * the ordinary course they are discarded by {@link #stop}, which runs the
     * moment anything at all reclaims the person — work, the bell, a hostile,
     * bedtime, or simply the end of the pastime.
     */
    private void sit(UUID id, PersonEntity view, BlockPos on) {
        if (seats.containsKey(id)) {
            return;
        }
        Display seat = seatUnder(view, on);
        if (seat != null) {
            seats.put(id, seat);
        }
    }

    /**
     * Puts an invisible seat under a body and gets the body onto it.
     *
     * <p>The whole of the trick, with nothing in it about who is sitting: the
     * residents' seats and a visitor's are the same three entities' worth of
     * work, and the only difference between them is which map remembers it.
     *
     * @return the seat, or null if either half of it failed
     */
    private Display seatUnder(Entity rider, BlockPos on) {
        Display seat = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        seat.setPos(on.getX() + 0.5, on.getY() + SEAT_HEIGHT, on.getZ() + 0.5);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.addTag(SEAT_TAG);
        if (!level.addFreshEntity(seat)) {
            return null;
        }
        // Forced, because a mob will not otherwise volunteer to ride anything,
        // and with the event suppressed: nothing about sitting on a bench is a
        // mount, and the advancement machinery should not hear about it.
        if (!rider.startRiding(seat, true, false)) {
            seat.discard();
            return null;
        }
        return seat;
    }

    // --- somebody who does not live here ------------------------------------------

    /**
     * How far from the door a visitor's seat may be: 3 blocks.
     *
     * <p>Much tighter than the ten the square's furniture is looked for over,
     * and for a reason that is about the body rather than about the bench. A
     * seat is taken by teleporting somebody onto it, so a bench found across the
     * yard would be a stranger who walked to the door and then jumped to the
     * far wall. Three blocks is a bench <em>at</em> the door, which is the only
     * kind worth having here.
     */
    private static final int VISITOR_BENCH_REACH = 3;

    /**
     * Seats under bodies that are on nobody's roster.
     *
     * <p>Its own map, and that is the whole design. {@link #seats} is keyed by
     * person id and is emptied by {@link #stop}, which is called from
     * {@code release} and {@code reapOrphans} — both of which walk the town's
     * <em>residents</em> and neither of which has ever heard of a stranger. Put
     * a visitor in that map and his seat is a {@code civilization_seat} nothing
     * will ever look up again: invisible, solid to nobody, standing in the inn
     * yard for the rest of the save.
     *
     * <p>So a visitor is keyed by his <em>entity</em> id and let go of by
     * whoever put him down. See {@code Caravans}, which is the only caller and
     * releases on every path out of a visit — the trader leaving, the trader
     * dying, the chunk going away, the world closing.
     */
    private final Map<UUID, Display> visitorSeats = new HashMap<>();

    /**
     * Where a stranger at this door would sit: a bench beside it, or the
     * doorway itself.
     *
     * <p>The doorway is a real answer and not a fallback that gave up. Sitting
     * on the step outside an inn is what somebody waiting outside an inn does,
     * and most towns in this mod have no bench at the inn at all — the square's
     * furniture is found near the market, which is somewhere else.
     */
    BlockPos visitorSeatNear(BlockPos doorway) {
        List<SimPos> benches = world.bridge().leisureSpots(
                new SimPos(doorway.getX(), doorway.getY(), doorway.getZ()),
                VISITOR_BENCH_REACH, Leisure.Pastime.BENCH, FURNITURE_KEPT);
        BlockPos best = doorway;
        double nearest = Double.MAX_VALUE;
        for (SimPos bench : benches) {
            BlockPos at = new BlockPos(bench.x(), bench.y(), bench.z());
            double away = at.distSqr(doorway);
            if (away < nearest) {
                nearest = away;
                best = at;
            }
        }
        return best;
    }

    /**
     * Sits a body that does not live here down, keyed by its entity id.
     *
     * <p>Idempotent: asking again for somebody already seated is a map lookup
     * and nothing else, so a caller may simply ask on every pass.
     *
     * @return whether it is now sitting
     */
    boolean seatVisitor(UUID entityId, Entity body, BlockPos on) {
        if (visitorSeats.containsKey(entityId)) {
            return true;
        }
        if (body == null || body.isRemoved()) {
            return false;
        }
        Display seat = seatUnder(body, on);
        if (seat == null) {
            return false;
        }
        visitorSeats.put(entityId, seat);
        return true;
    }

    /** Whether this body is on one of our seats. */
    boolean isVisitorSeated(UUID entityId) {
        return visitorSeats.containsKey(entityId);
    }

    /**
     * Gets a visitor up and takes the seat out of the world.
     *
     * <p>Safe for an id that was never seated, and safe for a body that has
     * already gone: the seat is ours and outlives the rider, which is exactly
     * the state this exists to prevent becoming permanent.
     */
    void releaseVisitor(UUID entityId) {
        Display seat = visitorSeats.remove(entityId);
        if (seat == null) {
            return;
        }
        for (Entity rider : List.copyOf(seat.getPassengers())) {
            rider.stopRiding();
        }
        if (!seat.isRemoved()) {
            seat.discard();
        }
    }

    /**
     * A sentry with nothing happening leans on his post and looks outward.
     *
     * <p>Not a place and not a walk — a guard is already exactly where the town
     * wants him, and moving him would be undoing the watch. All this does is turn
     * him to face away from the middle of the settlement, which is the direction
     * anything is going to come from, and only once he has stopped walking, so it
     * never fights his round.
     */
    private void leanOnPost(Settlement settlement, PersonEntity view) {
        if (!view.getNavigation().isDone()) {
            return;
        }
        SimPos center = settlement.center();
        double dx = view.getX() - (center.x() + 0.5);
        double dz = view.getZ() - (center.z() + 0.5);
        if (dx * dx + dz * dz < 1.0) {
            return;   // standing on the middle of town; there is no "outward"
        }
        view.getLookControl().setLookAt(view.getX() + dx, view.getEyeY(), view.getZ() + dz);
    }

    // --- talking -----------------------------------------------------------------------

    /**
     * Turns the people who ended up at the same place toward each other.
     *
     * <p>Who pairs with whom is {@code Leisure.talks}'s answer and is recomputed
     * every pass; what is remembered here is only how long a particular
     * conversation has left to run, so that two people at the same well are not
     * locked face to face for the whole half-minute they are there.
     */
    private void chat(List<Leisure.Attendee<UUID>> present, long now) {
        for (Leisure.Chat<UUID> chat : Leisure.talks(present, now)) {
            UUID one = chat.a();
            UUID other = chat.b();
            boolean together = other.equals(talkingTo.get(one))
                    && one.equals(talkingTo.get(other));
            long until = talkUntil.getOrDefault(one, 0L);
            if (!together) {
                if (until > now || talkUntil.getOrDefault(other, 0L) > now) {
                    continue;   // one of them is already mid-sentence with somebody
                }
                until = now + chat.ticks();
                talkUntil.put(one, until);
                talkUntil.put(other, until);
                talkingTo.put(one, other);
                talkingTo.put(other, one);
            } else if (until <= now) {
                continue;   // they have said what they had to say
            }
            PersonEntity a = viewOf.apply(one);
            PersonEntity b = viewOf.apply(other);
            if (a == null || b == null || a.isRemoved() || b.isRemoved()) {
                continue;
            }
            a.getLookControl().setLookAt(b.getX(), b.getEyeY(), b.getZ());
            b.getLookControl().setLookAt(a.getX(), a.getEyeY(), a.getZ());
            if (Math.floorMod(now / Leisure.PASS_TICKS, NOD_PASSES) == 0) {
                // The one animation this mob has. Whoever is listed first makes
                // the gesture, so a pair does not both gesture at once.
                a.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    // --- where there is to go ------------------------------------------------------------

    /**
     * Everywhere in this town somebody could be, looked for once and reused.
     *
     * <p>Rebuilt on a timer rather than on a change, because most of what makes
     * it up is not a change anything reports: a player puts a stair down beside
     * the square and it is a bench, and nothing in the mod was told. Ten seconds
     * is far finer than the rate at which a town gains furniture and far coarser
     * than the cost of the sweep that finds it.
     */
    private Offer offerIn(Settlement settlement, long now) {
        Offer cached = offers.get(settlement.id().value());
        if (cached != null && now - cached.at() < OFFER_TTL_TICKS) {
            return cached;
        }
        List<Leisure.Place> places = new ArrayList<>();
        Building market = settlement.buildingWithRole(BuildingRole.MARKET);
        SimPos square = market != null ? market.origin() : settlement.center();
        // The heart of the town is always somewhere to be, market or no market:
        // a settlement of four huts has a middle, and standing about in it is
        // what a settlement of four huts does.
        places.add(new Leisure.Place(Leisure.Pastime.SQUARE, settlement.center()));
        if (market != null) {
            // The well the square was drawn round. CivicParts puts it a block
            // off the market post, which is inside the radius anybody counts as
            // having arrived at, so the post is a good enough address for it.
            places.add(new Leisure.Place(Leisure.Pastime.WELL, market.origin()));
        }
        Building inn = settlement.buildingWithRole(BuildingRole.INN);
        if (inn != null) {
            places.add(new Leisure.Place(Leisure.Pastime.INN, inn.origin()));
        }
        SimPos fire = hearthOf(settlement);
        if (fire != null) {
            places.add(new Leisure.Place(Leisure.Pastime.HEARTH, fire));
        }
        // Every front door the town actually has. Housed only, and the list
        // itself is the condition: a doorway is offered when there is a door,
        // and not when there is merely a family.
        //
        // This line was the rain fault. It used to read "households is not
        // empty" and offer the DOORWAY at `square` -- the open middle of the
        // town -- as a representative of the kind, on the understanding that
        // placeFor would swap in a real door before anybody walked anywhere. It
        // does, when it can: neighbourDoor wants another *housed* family, and
        // returns null when the town has none. Unhoused families count towards
        // households() and contribute no door, so a young town, a founding camp
        // and any town whose one housed family is the person standing there all
        // took the null and fell back to the representative. In the dry that is
        // a settler standing in the square, which is what SQUARE is for and
        // costs nothing. In the wet it is the whole fault: DOORWAY weighs more
        // in the rain than the inn and the hearth put together, so it is what
        // most people draw, and every one of them was walked out of the rows
        // and into the middle of the open square to stand in it. The census:
        // cover flat at 14, people under open sky 9 -> 12, farm plot 3 -> 0.
        // They downed tools and went outside.
        //
        // So the representative is now a door. If there are no doors there is
        // no doorway on offer, the wet chooser is left with the inn and the
        // hearth, and a town with neither of those either legitimately has
        // nowhere dry -- see Leisure.shelterIn, and restFor answering null.
        List<SimPos> doors = new ArrayList<>();
        for (Household household : settlement.households()) {
            if (household.isHoused()) {
                doors.add(household.home());
            }
        }
        if (!doors.isEmpty()) {
            places.add(new Leisure.Place(Leisure.Pastime.DOORWAY, doors.getFirst()));
        }
        List<SimPos> benches = world.bridge().leisureSpots(square, FURNITURE_REACH,
                Leisure.Pastime.BENCH, FURNITURE_KEPT);
        if (!benches.isEmpty()) {
            places.add(new Leisure.Place(Leisure.Pastime.BENCH, benches.getFirst()));
        }
        List<SimPos> fences = world.bridge().leisureSpots(square, FURNITURE_REACH,
                Leisure.Pastime.FENCE, FURNITURE_KEPT);
        if (!fences.isEmpty()) {
            places.add(new Leisure.Place(Leisure.Pastime.FENCE, fences.getFirst()));
        }
        Building farm = settlement.buildingWithRole(BuildingRole.CROP_FARM);
        List<SimPos> gates = farm == null ? List.of()
                : world.bridge().leisureSpots(farm.origin(), FURNITURE_REACH,
                        Leisure.Pastime.FARM_GATE, FURNITURE_KEPT);
        if (!gates.isEmpty()) {
            places.add(new Leisure.Place(Leisure.Pastime.FARM_GATE, gates.getFirst()));
        }
        Offer offer = new Offer(now, List.copyOf(places), benches, fences, gates,
                List.copyOf(doors));
        offers.put(settlement.id().value(), offer);
        return offer;
    }

    /**
     * The one place in the offer that is different for everybody.
     *
     * <p>A hearth is <em>your</em> hearth, a doorway is somebody else's, and a
     * bench is whichever of the square's benches is free — none of which the
     * town-wide offer can say, and all of which would be wrong if it tried. The
     * offer carries a representative of each so that {@code Leisure.choose} can
     * weigh the kind; this is where the representative becomes a particular
     * place.
     *
     * <p><strong>Every fallback here has to be as sheltered as the kind it
     * stands for.</strong> That is the rule the rain fault was a breach of, and
     * it is a rule about this method rather than about the weather table: the
     * table refuses to send anybody anywhere open while it is raining, and it
     * does so by kind, so the moment a kind resolves to a position of a
     * different sort the refusal has been walked round. The two that matter are
     * below. A {@code HEARTH} falls back to the town's own fire — a hearth
     * building's or the hall's interior, never bare ground — and a
     * {@code DOORWAY} falls back to another of the town's real front doors,
     * because {@link #offerIn} now only puts one on the table when the town has
     * some. Neither may fall back to the square, whatever else changes here.
     */
    private SimPos placeFor(Settlement settlement, Person person, Leisure.Rest rest,
                            Offer offer) {
        UUID id = person.id().value();
        return switch (rest.what()) {
            // Their own fire first; failing that the town's, which is what the
            // offer's representative already is (hearthOf: the hearth building,
            // or the hall). Both are rooms, so the unhoused settler this arm is
            // for ends up at the town's fire rather than beside it.
            case HEARTH -> {
                SimPos home = homeOf(settlement, person);
                SimPos fire = home != null ? home : hearthOf(settlement);
                // rest.where() is that same town fire, because that is what the
                // offer's HEARTH entry is built from; the arm is unreachable
                // with no fire at all. Kept as the last link so no path through
                // here can hand back null.
                yield fire != null ? fire : rest.where();
            }
            // Somebody else's door by preference -- leaning on your own is not
            // what the pastime means. But the fallback is the town's own list of
            // doors and not the square: a settler in a one-house town leans on
            // the one house, under its eave, which is a slightly odd thing to be
            // doing and a much better thing than standing in a downpour in the
            // middle of the market. The list is non-empty whenever this arm is
            // reachable at all, because the offer carries no DOORWAY otherwise.
            case DOORWAY -> {
                SimPos neighbour = neighbourDoor(settlement, person);
                yield neighbour != null ? neighbour : pick(offer.doors(), id, rest.where());
            }
            case BENCH -> pick(offer.benches(), id, rest.where());
            case FENCE -> pick(offer.fences(), id, rest.where());
            case FARM_GATE -> pick(offer.gates(), id, rest.where());
            default -> rest.where();
        };
    }

    /** One of these, chosen by the person so two settlers rarely want the same seat. */
    private static SimPos pick(List<SimPos> from, UUID who, SimPos fallback) {
        if (from == null || from.isEmpty()) {
            return fallback;
        }
        return from.get(Math.floorMod(who.hashCode(), from.size()));
    }

    /** This settler's own home, or null if the town never housed them. */
    private static SimPos homeOf(Settlement settlement, Person person) {
        for (Household household : settlement.households()) {
            if (household.isHoused() && household.members().contains(person.id())) {
                return household.home();
            }
        }
        return null;
    }

    /** Somebody else's front door, chosen by whoever is going to lean on it. */
    private static SimPos neighbourDoor(Settlement settlement, Person person) {
        List<SimPos> doors = new ArrayList<>();
        SimPos own = homeOf(settlement, person);
        for (Household household : settlement.households()) {
            if (household.isHoused() && !household.home().equals(own)) {
                doors.add(household.home());
            }
        }
        return doors.isEmpty() ? null
                : doors.get(Math.floorMod(person.id().value().hashCode(), doors.size()));
    }

    /** The town's fire: its hearth if it has one, otherwise its inn, otherwise nothing. */
    private static SimPos hearthOf(Settlement settlement) {
        Building hearth = settlement.buildingWithRole(BuildingRole.HEARTH);
        if (hearth != null) {
            return hearth.origin();
        }
        Building hall = settlement.buildingWithRole(BuildingRole.HALL);
        return hall == null ? null : hall.origin();
    }

    // --- putting it down -----------------------------------------------------------------

    /**
     * Ends whatever this person was doing and takes the seat out of the world.
     *
     * <p>Called on every path out of leisure, including the ones that are not
     * about leisure at all: work reclaiming somebody, the bell, a hostile, dusk,
     * a body despawning. Safe to call for somebody who was never at leisure, and
     * safe with a null body — a person whose entity has already gone still has a
     * seat under them that has not.
     */
    void stop(UUID id, PersonEntity view) {
        at.remove(id);
        talkUntil.remove(id);
        UUID partner = talkingTo.remove(id);
        if (partner != null) {
            talkingTo.remove(partner);
            talkUntil.remove(partner);
        }
        Display seat = seats.remove(id);
        if (view != null && !view.isRemoved() && view.isPassenger()) {
            view.stopRiding();
        }
        if (seat != null && !seat.isRemoved()) {
            seat.discard();
        }
    }

    /** Everybody up, every seat gone. For a server shutting down. */
    void stopAll() {
        for (UUID id : List.copyOf(seats.keySet())) {
            stop(id, viewOf.apply(id));
        }
        // And the strangers', which no roster walk would have reached. See
        // visitorSeats: this is the backstop for a world closing on somebody
        // sitting at the inn, and Caravans.forget is the one that names him.
        for (UUID id : List.copyOf(visitorSeats.keySet())) {
            releaseVisitor(id);
        }
        at.clear();
        talkingTo.clear();
        talkUntil.clear();
        offers.clear();
    }

    /** What somebody is doing with their afternoon, for {@code /civ info}, or null. */
    String reportFor(UUID id) {
        Leisure.Pastime what = pastimeOf(id);
        return what == null ? null : what.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The same fact, unworded, for anything that wants to branch on it.
     *
     * <p>{@link #reportFor} is a line of a report and returns a word; a greeting
     * has to know whether this is the inn in particular, and parsing a lowercased
     * enum name back into an enum would be a round trip nobody should make.
     */
    Leisure.Pastime pastimeOf(UUID id) {
        Sitting held = at.get(id);
        return held == null ? null : held.what();
    }
}
