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
import net.minecraft.world.entity.EntityTypes;

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

    /** A town's places to be, and when they were last looked for. */
    private record Offer(long at, List<Leisure.Place> places, List<SimPos> benches,
                         List<SimPos> fences, List<SimPos> gates) {
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

    /** One town's idle people, given somewhere to be. */
    void tend(Settlement settlement) {
        long now = level.getGameTime();
        long clock = level.getDefaultClockTime();
        Leisure.Hour hour = Leisure.hourOf(clock, Curfew.LEAD_TICKS);
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
                    Leisure.hasWork(person.profession(), hour,
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
                    || !Leisure.stillFits(held.what(), hour))) {
                stop(id, view);
                held = null;
            }
            if (held == null) {
                if (walking >= Leisure.WALKS_AT_ONCE) {
                    continue;   // the town has enough people crossing it already
                }
                // The one man in the town with an offer of his own. A king is
                // not steered anywhere by his profession — his workplace is his
                // own doorstep — so left on the town's offer he would wander to
                // the well like anybody, which is not what a hall is for.
                boolean crowned = person.profession() == Profession.KING
                        || person.profession() == Profession.SHAMAN;
                List<Leisure.Place> offered = crowned
                        ? Leisure.forKing(KingPlanner.rallyPoint(settlement), offer.places())
                        : offer.places();
                Leisure.Rest rest = Leisure.restFor(id, now, hour, offered);
                if (rest == null) {
                    continue;   // nothing on offer at this hour; stand as before
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
        Display seat = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        seat.setPos(on.getX() + 0.5, on.getY() + SEAT_HEIGHT, on.getZ() + 0.5);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.addTag(SEAT_TAG);
        if (!level.addFreshEntity(seat)) {
            return;
        }
        // Forced, because a mob will not otherwise volunteer to ride anything,
        // and with the event suppressed: nothing about sitting on a bench is a
        // mount, and the advancement machinery should not hear about it.
        if (!view.startRiding(seat, true, false)) {
            seat.discard();
            return;
        }
        seats.put(id, seat);
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
        if (!settlement.households().isEmpty()) {
            places.add(new Leisure.Place(Leisure.Pastime.DOORWAY, square));
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
        Offer offer = new Offer(now, List.copyOf(places), benches, fences, gates);
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
     */
    private SimPos placeFor(Settlement settlement, Person person, Leisure.Rest rest,
                            Offer offer) {
        UUID id = person.id().value();
        return switch (rest.what()) {
            case HEARTH -> {
                SimPos home = homeOf(settlement, person);
                yield home != null ? home : rest.where();
            }
            case DOORWAY -> {
                SimPos neighbour = neighbourDoor(settlement, person);
                yield neighbour != null ? neighbour : rest.where();
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
