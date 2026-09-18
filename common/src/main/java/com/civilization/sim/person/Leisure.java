package com.civilization.sim.person;

import com.civilization.sim.geom.SimPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What somebody does when there is nothing to do.
 *
 * <p>The town had two states and no third one. A person worked their trade, or
 * they stood on the exact block the steering last left them on, forever, and the
 * only thing that noticed was a report. That is what "doesn't feel alive" means
 * on the ground: not that the people are idle — a village is idle most of the
 * day — but that being idle looked like being switched off.
 *
 * <p>So an idle person is given a <em>pastime</em>: a place to be and a while to
 * be there. Not a job. Nothing here produces, consumes, moves goods or touches a
 * ledger, and that is deliberate and permanent — see {@link #mayRest}, whose
 * first question is whether anybody is looking. A pastime that fed anybody would
 * be an economy that only runs while a player is standing in it, which is the
 * one asymmetry between the two fidelities this mod does not allow.
 *
 * <p><strong>Here in {@code common} because the choosing is arithmetic.</strong>
 * Which pastime somebody takes, how long they stay, and whether two people
 * standing near each other fall into conversation are all decisions about a
 * hash, a clock and a distance. The platform's half is finding the bench and
 * putting a body on it.
 */
public final class Leisure {

    private Leisure() {
    }

    // --- the constants --------------------------------------------------------

    /**
     * The shortest a pastime lasts: 200 ticks, ten seconds.
     *
     * <p>Long enough that somebody who walked to the well is still at the well
     * when a player comes round the corner. Much shorter and the town reads as
     * agitated rather than alive — people crossing the square over and over is
     * worse than people standing in it.
     */
    public static final int MIN_TICKS = 200;

    /** The longest: 600 ticks, half a minute. Then they think of something else. */
    public static final int MAX_TICKS = 600;

    /**
     * How near two people have to be to fall into talking: 3 blocks.
     *
     * <p>Conversational distance, and the point of it being small is that it is
     * the <em>pastime</em> that gathers people, not the pairing. Two settlers
     * talk because they both went to the well; they do not cross the square to
     * find somebody to talk to.
     */
    public static final double TALK_REACH = 3.0;

    /** The shortest exchange: 100 ticks, five seconds. */
    public static final int TALK_MIN_TICKS = 100;

    /** The longest: 300 ticks. Past that it is a meeting. */
    public static final int TALK_MAX_TICKS = 300;

    /**
     * How many people may be walking to a pastime at once, per town: 6.
     *
     * <p>The cost of this whole feature is pathfinding, and a town of forty that
     * decided on the same second that everybody should go somewhere would ask
     * vanilla for forty routes in one tick. Six is roughly what a town already
     * has in motion for work, so leisure at most doubles the traffic and never
     * more. Anybody over the cap simply keeps standing where they are, and is
     * offered a pastime again on the next pass.
     */
    public static final int WALKS_AT_ONCE = 6;

    /**
     * Ticks between assignment passes: 20, which is one a second.
     *
     * <p>Stated here rather than assumed at the call site so the cost is a fact
     * about the feature rather than a property of whichever loop happened to
     * host it. A pastime is the slowest-moving decision in the mod — the
     * shortest one lasts ten of these — so a second is already far finer than
     * the thing being decided.
     */
    public static final int PASS_TICKS = 20;

    // --- what there is to do --------------------------------------------------

    /**
     * The things a settler does with an afternoon.
     *
     * <p>Every one of them is a real place in the town rather than an animation:
     * the well the market square was drawn round, the inn's common room, the
     * family hearth, a neighbor's door, a stair somebody put down as a bench, a
     * fence, the gate of the field. A town where the idle people are at the
     * places the town is made of is a town that reads as being for its people.
     */
    public enum Pastime {

        /** The well in the middle of the market square, which is where a town meets. */
        WELL,

        /** The open square itself, or the heart of the settlement before one stands. */
        SQUARE,

        /** The inn's common room. Evenings. */
        INN,

        /** The fire at home, which is where a family is when it is not out. */
        HEARTH,

        /** A neighbor's doorway, leaned on. */
        DOORWAY,

        /** A bench: any stair block the town's dressing or its civic drawings left. */
        BENCH,

        /** A fence, leaned on. */
        FENCE,

        /** The field gate, looked over. */
        FARM_GATE,

        /** A guard's own post, leaned on, facing out. Never chosen; see {@link #forGuard}. */
        POST;

        /** Whether this is a thing done under a roof. */
        public boolean isIndoors() {
            return this == INN || this == HEARTH;
        }

        /** Whether somebody at this one is sitting rather than standing. */
        public boolean isSeated() {
            return this == BENCH;
        }
    }

    /**
     * A pastime and the place in the world it is taken at.
     *
     * <p>The platform builds the offer — it is the only half that knows where
     * the inn's door is or which stair is a bench — and the choosing here never
     * invents a place. A town with no inn simply offers none, and nobody goes to
     * one.
     */
    public record Place(Pastime what, SimPos where) {

        public Place {
            Objects.requireNonNull(what, "what");
            Objects.requireNonNull(where, "where");
        }
    }

    /** A chosen pastime, with how long the person stays at it. */
    public record Rest(Pastime what, SimPos where, int ticks) {

        public Rest {
            Objects.requireNonNull(what, "what");
            Objects.requireNonNull(where, "where");
        }

        /** Whether the body should be sat down when it gets there. */
        public boolean isSeated() {
            return what.isSeated();
        }
    }

    // --- when ------------------------------------------------------------------

    /** The three parts of a day a pastime is chosen differently in. */
    public enum Hour {

        /** Working light. Anybody idle in it is idle out of doors. */
        DAY,

        /**
         * After the curfew and before the beds.
         *
         * <p>The hour this whole planner exists for. The town has stopped
         * working and is not yet asleep, everybody is walking in, and what they
         * do with the gap is the difference between a village and a dormitory.
         */
        EVENING,

        /** Dark. Whoever is still up is at a fire. */
        NIGHT
    }

    /**
     * Which part of the day this is, read off the curfew rather than a clock
     * hour of its own.
     *
     * <p>Evening is exactly the window {@code Curfew} already opens, so the hour
     * the town stops working and the hour it goes to the inn cannot come to
     * disagree by a tick.
     */
    public static Hour hourOf(long dayTime, long lead) {
        if (NightRest.isNight(dayTime)) {
            return Hour.NIGHT;
        }
        return Curfew.isCurfew(dayTime, lead) ? Hour.EVENING : Hour.DAY;
    }

    // --- who --------------------------------------------------------------------

    /**
     * Everything about somebody that decides whether they are at leisure.
     *
     * <p>Plain booleans, and the platform answers all of them. The point of
     * gathering them into one record is that the rule below can then be read in
     * one line and tested without a world — "at leisure" is a great many
     * negatives and a list of negatives spread over a steering loop is how a
     * sleeping farmer ends up strolling to the well.
     *
     * @param embodied   somebody is near enough that this person has a body
     * @param guard      one of the watch, whose leisure is their own post
     * @param called     the bell is ringing for them, or the town is up in arms
     * @param inDanger   fleeing, or something hostile is inside notice
     * @param asleep     in bed
     * @param onErrand   hauling something, or walking to a meal
     * @param working    the town has this person's hands on something right now
     */
    public record Idleness(boolean embodied, boolean guard, boolean called,
                           boolean inDanger, boolean asleep, boolean onErrand,
                           boolean working) {
    }

    /**
     * Whether this person may be given a pastime.
     *
     * <p>Work outranks it, danger outranks it, the bell outranks it and sleep
     * outranks it — a pastime is what is left when everything else has declined
     * to claim somebody. The first clause is the load-bearing one: an unembodied
     * person is a record in a ledger, and a record has no afternoon.
     */
    public static boolean mayRest(Idleness who) {
        return who.embodied()
                && !who.called()
                && !who.inDanger()
                && !who.asleep()
                && !who.onErrand()
                && !who.working();
    }

    /**
     * What the town's own ledgers say there is to do, one boolean per trade.
     *
     * <p>{@link Idleness}'s sibling and written for the same reason. "Has this
     * person's hands on something" used to be a switch over every profession in
     * the platform's steering loop, where it could not be tested and where the
     * one branch that mattered most was a {@code default} arm reading
     * <em>true</em> — so a smith, a miller, a carpenter and the king were
     * permanently at work whatever their building had in it, and were the
     * stiffest figures in any town. Gathering the answers the planners already
     * hold into a record is what lets the rule below be read and be checked.
     *
     * <p>Every field is somebody else's judgment, quoted rather than re-derived:
     * {@code SmithPlanner.hasWorkInFront}, {@code FoodPlanner.millHasWork},
     * {@code MarketPlanner.isOpen}. A second opinion about whether the forge is
     * cold is a second thing to get wrong.
     *
     * @param handsOnAWork the construction pass or the public works have this
     *                     person already, which outranks every trade below
     * @param field        a field stands for a farmer to work
     * @param wood         a wood is claimed for a lumberjack
     * @param stone        a seam is claimed for a miner
     * @param pens         an animal farm stands for a shepherd
     * @param stall        the market is open
     * @param forge        the smithy has iron, fuel and something worth making
     * @param mill         the mill has grain and the larder has room for the loaf
     * @param bench        the carpentry stands and a build is queued for it
     */
    public record Openings(boolean handsOnAWork, boolean field, boolean wood,
                           boolean stone, boolean pens, boolean stall,
                           boolean forge, boolean mill, boolean bench) {
    }

    /**
     * Whether the town has this person's hands on something right now.
     *
     * <p>Everything outside the working day answers no, which is the whole of
     * "off-shift" — and so does anybody too weak to work, who is the
     * "weak-but-fed" case: a settler the hunger rules have taken off the job but
     * who is not walking to a meal is somebody sitting down, not somebody frozen
     * at their workplace.
     *
     * <p>The four that used to fall through to <em>true</em> now answer off a
     * ledger like everybody else. A smith at a forge with no iron in it, a
     * miller at a mill with an empty hopper and a carpenter with nothing queued
     * are all people waiting, and the town they are waiting in has a square and
     * a doorway and a bench in it. The king answers no outright, because that is
     * what a king is: {@code KingPlanner} puts him in the staffing table
     * nowhere, and {@code HaulPlanner} already says he has no work in front of
     * him by definition — this is the same fact, said where his afternoon is
     * decided. The shaman is the same case in a camp.
     *
     * <p>What has <em>not</em> changed is that a forge with iron in it keeps its
     * smith. A pastime never outbids work; it is only ever what is left when
     * work has declined to claim somebody.
     */
    public static boolean hasWork(Profession what, Hour hour, boolean tooWeak,
                                  Openings open) {
        if (hour != Hour.DAY || tooWeak) {
            return false;
        }
        if (open.handsOnAWork()) {
            return true;
        }
        return switch (what) {
            // Nothing to be waiting for, by definition.
            case IDLER -> false;
            // A builder with a site is busy on it and was caught above; a builder
            // without one is precisely the person waiting for hands.
            case BUILDER, PIONEER -> false;
            // The watch's leisure is its post; see Pastimes.leanOnPost.
            case GUARD -> false;
            // Idle by right, both of them. See above.
            case KING, SHAMAN -> false;
            case FARMER -> open.field();
            case LUMBERJACK -> open.wood();
            case MINER -> open.stone();
            case SHEPHERD -> open.pens();
            // A trader is at the stall while the stall is open and is somebody
            // with an afternoon when it is not. See MarketPlanner.
            case TRADER -> open.stall();
            case SMITH -> open.forge();
            case MILLER -> open.mill();
            case CARPENTER -> open.bench();
            // A forager's field is the wood and there is no building to be short
            // of: while a camp lives hand to mouth its hands are in the bushes,
            // and a camp with a full larder has already been sent home by the
            // hour. Left as it was, deliberately — this unit is about the four
            // above it.
            case FORAGER -> true;
        };
    }

    /**
     * Where the king spends a quiet hour: his own hall, mostly.
     *
     * <p>{@link #forGuard}'s idea for the other man the town has exactly one of,
     * and the opposite answer. A guard's leisure is staying where the town put
     * him, because the whole point of a sentry is where he is standing. A king
     * has no post to hold, and the reason he had no pastime at all was simply
     * that the rule above used to say he was working — so what he needs is not a
     * special case but an offer, and this is it: his own hall, the square, and
     * the inn.
     *
     * <p>Weighted by repetition rather than by a second weight table, because
     * {@link #choose} already weighs one entry at a time and a table that had to
     * know whose offer it was weighing would be a table with a person in it. The
     * hall appears {@link #KING_HALL_SHARE} times, so at the day's weights it
     * outdraws the square roughly three to two and the town's king is usually to
     * be found at the town's hall — which is what anybody looking for him would
     * expect, and is why the doorway is on the list at all.
     *
     * <p>Evening and night take care of themselves: the square weighs nothing
     * after the curfew and the inn weighs seven, so the king walks in with
     * everybody else and drinks where they drink.
     */
    public static List<Place> forKing(SimPos hallDoorstep, List<Place> townOffer) {
        List<Place> mine = new ArrayList<>();
        if (hallDoorstep != null) {
            for (int i = 0; i < KING_HALL_SHARE; i++) {
                mine.add(new Place(Pastime.DOORWAY, hallDoorstep));
            }
        }
        if (townOffer != null) {
            for (Place place : townOffer) {
                if (place.what() == Pastime.SQUARE || place.what() == Pastime.INN) {
                    mine.add(place);
                }
            }
        }
        return List.copyOf(mine);
    }

    /** How many entries of a king's offer are his own hall: three. */
    public static final int KING_HALL_SHARE = 3;

    /**
     * What one of the watch does with a quiet hour: leans on their post.
     *
     * <p>Not chosen from the offer and never one of the others. A guard who
     * strolled to the inn is not off duty, he is off the wall — and the whole of
     * the third cure was about where the watch is standing when something
     * appears. So the only leisure a guard has is the leisure of staying exactly
     * where the town put him and looking outward, which costs the watch nothing
     * and is also what a sentry actually looks like.
     */
    public static Pastime forGuard() {
        return Pastime.POST;
    }

    // --- choosing ----------------------------------------------------------------

    /**
     * How much each pastime is worth at each hour of the day.
     *
     * <p>Zero is a refusal rather than a low preference: the evening and the
     * night offer nothing out of doors at all, because the entire reason the
     * town walks home before dusk is that being outside after it kills people.
     * A settler who has just been sent in by the curfew and then strolls back
     * out to the well has undone the curfew.
     */
    private static int weightOf(Pastime what, Hour hour) {
        return switch (hour) {
            case DAY -> switch (what) {
                case WELL -> 4;
                case SQUARE -> 4;
                case BENCH -> 3;
                case DOORWAY -> 2;
                case FENCE -> 2;
                case FARM_GATE -> 2;
                case HEARTH -> 1;
                case INN -> 1;
                case POST -> 0;
            };
            // The inn, or your own fire, and nothing else. See above.
            case EVENING -> switch (what) {
                case INN -> 7;
                case HEARTH -> 3;
                default -> 0;
            };
            // The same two, the other way round: whoever is still up at this
            // hour is more likely to be at home than at the bar.
            case NIGHT -> switch (what) {
                case HEARTH -> 6;
                case INN -> 4;
                default -> 0;
            };
        };
    }

    /**
     * Whether this pastime is one anybody would still be at, at this hour.
     *
     * <p>Asked of a pastime already under way rather than of a fresh offer: the
     * light goes while people are out at it, and somebody sitting on a bench in
     * the square as dusk falls has to get up and walk in rather than see their
     * half-minute out.
     */
    public static boolean stillFits(Pastime what, Hour hour) {
        return what == Pastime.POST || weightOf(what, hour) > 0;
    }

    /**
     * Which of the offered pastimes this person takes, now.
     *
     * <p>Deterministic in the person and the tick and nothing else, so the same
     * town replayed from the same save makes the same afternoon — and so that
     * nothing here has to be written down. A pastime is thirty seconds long; a
     * save format is forever.
     *
     * <p>Weighted rather than uniform because the weights are the character of
     * the hour: an evening whose inn was one option in eight would be an evening
     * nobody went to the inn.
     *
     * @return the chosen place, or null when the hour offers nothing — a town
     *         with no inn and no hearth has no evening, and its people walk home
     *         exactly as they did before this existed
     */
    public static Place choose(UUID who, long tick, Hour hour, List<Place> offered) {
        if (offered == null || offered.isEmpty()) {
            return null;
        }
        long total = 0;
        for (Place place : offered) {
            total += weightOf(place.what(), hour);
        }
        if (total <= 0) {
            return null;
        }
        long roll = Math.floorMod(mix(who, tick), total);
        for (Place place : offered) {
            roll -= weightOf(place.what(), hour);
            if (roll < 0) {
                return place;
            }
        }
        return offered.get(offered.size() - 1);
    }

    /** How long this person stays at the pastime they have just been given. */
    public static int ticksFor(UUID who, long tick) {
        int span = MAX_TICKS - MIN_TICKS + 1;
        return MIN_TICKS + (int) Math.floorMod(mix(who, tick * 31L + 7L), span);
    }

    /** The whole decision in one call: what, where, and for how long. */
    public static Rest restFor(UUID who, long tick, Hour hour, List<Place> offered) {
        Place place = choose(who, tick, hour, offered);
        if (place == null) {
            return null;
        }
        return new Rest(place.what(), place.where(), ticksFor(who, tick));
    }

    // --- pairs -------------------------------------------------------------------

    /**
     * Somebody already at a pastime, for working out who is talking to whom.
     *
     * @param who   whatever the caller identifies people by
     * @param what  the pastime they are at
     * @param where the block they are standing on, not the place they were sent to
     */
    public record Attendee<T>(T who, Pastime what, SimPos where) {
    }

    /** Two people who have fallen into conversation, and for how long. */
    public record Chat<T>(T a, T b, int ticks) {
    }

    /**
     * Who is talking to whom.
     *
     * <p>Two conditions, both of them narrow on purpose: the same pastime, and
     * inside {@link #TALK_REACH}. Same pastime, because two people who happen to
     * pass within three blocks on their separate errands are not in conversation
     * — a town whose people struck up a chat with anybody who walked past would
     * be a town that never got anywhere. Inside three blocks, because that is
     * what talking to somebody looks like.
     *
     * <p>Greedy in the order given, and each person is in at most one
     * conversation: three settlers at the same well are a pair and an onlooker,
     * which is both cheaper and truer than a three-way. The caller's ordering
     * decides who pairs with whom, so callers pass a stable one — otherwise the
     * pairs reshuffle every pass and nobody finishes a sentence.
     */
    public static <T> List<Chat<T>> talks(List<Attendee<T>> present, long tick) {
        List<Chat<T>> chats = new ArrayList<>();
        if (present == null || present.size() < 2) {
            return List.of();
        }
        boolean[] taken = new boolean[present.size()];
        for (int i = 0; i < present.size(); i++) {
            if (taken[i]) {
                continue;
            }
            Attendee<T> one = present.get(i);
            for (int j = i + 1; j < present.size(); j++) {
                if (taken[j]) {
                    continue;
                }
                Attendee<T> other = present.get(j);
                if (one.what() != other.what()) {
                    continue;
                }
                if (one.where().horizontalDistance(other.where()) > TALK_REACH) {
                    continue;
                }
                taken[i] = true;
                taken[j] = true;
                chats.add(new Chat<>(one.who(), other.who(), talkTicks(tick, i, j)));
                break;
            }
        }
        return List.copyOf(chats);
    }

    /** How long a particular exchange runs for. */
    public static int talkTicks(long tick, int one, int other) {
        int span = TALK_MAX_TICKS - TALK_MIN_TICKS + 1;
        return TALK_MIN_TICKS
                + (int) Math.floorMod(mix(tick, one * 1000003L + other), span);
    }

    // --- the hash ----------------------------------------------------------------

    /**
     * SplitMix64's finalizer, over a person and a moment.
     *
     * <p>The same mixer {@code Wander} and {@code HouseStyle.Variation} use, for
     * the same reason: the inputs move in lockstep — consecutive ticks, ids that
     * share a version nibble — and reading the low bits of either raw would give
     * a whole town one answer on one second and a different single answer on the
     * next, which is a town that moves as a flock.
     */
    private static long mix(UUID who, long tick) {
        return mix(who.getMostSignificantBits() ^ (who.getLeastSignificantBits() * 31L),
                tick);
    }

    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
