package com.civilization.sim.person;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.Mix;
import com.civilization.sim.settlement.SettlementEvent;
import com.civilization.sim.settlement.SettlementStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a settler says when you speak to them.
 *
 * <p>Six lines used to serve everybody in the mod, and the pick was seeded off
 * the <em>entity's</em> uuid — which is regenerated every time a body is thrown
 * away and rebuilt, so the same settler said something different on every visit.
 * A person who cannot keep their own opinion between two walks across a square
 * is a prop, not a neighbor.
 *
 * <p>Three things are fixed here, in this order of importance:
 *
 * <ul>
 *   <li><strong>Stable.</strong> The pick is seeded from the {@code Person.Id}
 *       and a coarse clock — the in-game day — so a settler says the same thing
 *       all day and something else tomorrow. Nothing about a body is in the
 *       seed.</li>
 *   <li><strong>Situated.</strong> A handful of situations outrank the pool
 *       outright: a hungry settler asks for bread, an alarmed one tells you to
 *       get indoors, a builder with empty arms says what he is waiting for. See
 *       {@link Situation}, which is in priority order.</li>
 *   <li><strong>Of a people.</strong> Every culture has a pool of its own, in
 *       its own register, and mentions its town by name where that reads
 *       naturally.</li>
 * </ul>
 *
 * <p><strong>This is in {@code common} on purpose.</strong> Which line a settler
 * says is arithmetic over a record, a clock and a table; none of it needs a
 * level, a player or an entity, and putting it here is what lets the whole of it
 * be tested. The platform's only job is to fill in a {@link Moment} and print
 * what comes back.
 *
 * <p>And it is worth nothing. A greeting moves no stock, no yield and no ledger
 * — it is a thing that only happens while somebody is watching, and the mod's
 * rule for those is that they must not be worth anything. See the "Leisure has
 * no economy" note in the changelog.
 */
public final class Greetings {

    /**
     * Why a settler is saying what they are saying, highest claim first.
     *
     * <p>Declaration order <em>is</em> the priority: {@link #situationOf} walks
     * the conditions in this order and takes the first that holds, so an alarmed
     * king shouts about the alarm and a hungry guard asks for bread rather than
     * reporting his post. Hunger sits above every trade deliberately — a man who
     * cannot eat has nothing else to say to you.
     */
    public enum Situation {

        /** The town has called everybody in. Nothing else is worth saying. */
        ALARM,

        /** {@link Person#HUNGER_HUNGRY} or worse. */
        HUNGRY,

        /** The crown. */
        KING,

        /** A builder on a job with nothing in his arms. */
        WAITING_ON_MATERIALS,

        /** A farmer with ripe ground in front of him. */
        HARVEST,

        /** A guard, at work, on the wall. */
        ON_POST,

        /** At the inn, of an evening, which is the one place people go to talk. */
        INN,

        /** Walking home ahead of the dark. */
        CURFEW,

        /** Nothing in particular — which is most of the time, and is the point. */
        PASSING
    }

    /**
     * Everything about one settler at one instant that changes what they say.
     *
     * <p>A flat record of plain values rather than the {@code Person} itself,
     * because half of these are questions only the platform can answer — whether
     * this body is sitting in the inn, whether the ground in front of the farmer
     * is ripe — and a chooser that had to go and find out would be a chooser that
     * needed a world.
     *
     * @param who                the person, which is the whole of the seed
     * @param trade              what they do
     * @param hunger             0 to {@link Person#HUNGER_MAX}
     * @param atWork             on the job right now, rather than walking or idle
     * @param atLeisure          taken a pastime; see {@code Leisure}
     * @param atInn              that pastime is the inn's common room
     * @param evening            the light is going
     * @param curfew             on the walk home ahead of dusk
     * @param alarm              the town's alarm is raised
     * @param awaitingMaterials  a builder with a job and empty arms
     * @param harvest            a farmer with ripe crops standing
     */
    public record Moment(Person.Id who, Profession trade, int hunger,
                         boolean atWork, boolean atLeisure, boolean atInn,
                         boolean evening, boolean curfew, boolean alarm,
                         boolean awaitingMaterials, boolean harvest) {

        public Moment {
            Objects.requireNonNull(who, "who");
            Objects.requireNonNull(trade, "trade");
        }

        /** The plainest one there is: a person, and nothing going on. */
        public static Moment of(Person person) {
            return new Moment(person.id(), person.profession(), person.hunger(),
                    false, false, false, false, false, false, false, false);
        }
    }

    /**
     * As much of a settlement as a greeting can use.
     *
     * <p>Three facts and no object graph: what the place is called, how far along
     * it is, and the last thing that happened in it. Taking the {@code Settlement}
     * itself would mean a test of this file had to found one.
     *
     * @param name      what the place is called
     * @param stage     how far along it is; a camp and a town are talked about
     *                  differently
     * @param lastEvent the most recent line of the settlement's history, or null
     */
    public record Town(String name, SettlementStage stage, String lastEvent) {

        public Town {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(stage, "stage");
        }

        /** The last thing that happened here, out of a settlement's own log. */
        public static Town of(String name, SettlementStage stage,
                              List<SettlementEvent> events) {
            return new Town(name, stage,
                    events == null || events.isEmpty()
                            ? null : events.get(events.size() - 1).message());
        }
    }

    /** The token a line puts the settlement's name in. */
    private static final String TOWN = "{town}";

    /** The token a line puts "town" or "camp" in, whichever this place is. */
    private static final String PLACE = "{place}";

    // --- the pools ---------------------------------------------------------------

    /**
     * The lowlanders: farmers, weather, and the road.
     *
     * <p>Plain and rural, which is the register the changelog keeps and the one
     * these people would actually use. Nobody here says "hail, traveller".
     */
    private static final List<String> NORMAN = List.of(
            "Fine day for it.",
            "Fields want walking before dark.",
            "{town} was three huts when I came here.",
            "Mind the ruts on the road out.",
            "There's work enough for another pair of hands.",
            "Rain by evening, I'd say. Always is.",
            "We keep to ourselves and do well enough.",
            "Ask at the hall if you're after a bed.",
            "The ox is lame again. It's always the ox.",
            "Granary's full. That's all {town} asks.",
            "Nothing to report, and that suits me.");

    /** The hill folk: steep ground, hard weather, long memory. */
    private static final List<String> HIGHLAND = List.of(
            "Steep ground. Hard people.",
            "Goats don't mind the wind. Nor do we.",
            "You'll not find flat ground in {town}.",
            "Watch your footing on the wet stone.",
            "We built where the ground let us, not where we liked.",
            "Down below they'd call this a hill. We call it a yard.",
            "Weather comes over that ridge with no warning at all.",
            "There's a spring past the fold, if you're dry.",
            "My grandfather carried every stone of that wall.",
            "{town} sits where it sits. Nobody chose it twice.",
            "Cold coming. You can smell it.");

    /** The townsfolk: streets, carts, accounts. */
    private static final List<String> BURGHER = List.of(
            "Good day. Trading, or just passing?",
            "Keep to the street and you'll not get lost.",
            "Market's that way. Mind the carts.",
            "{town} was laid out proper. Not like some.",
            "Prices are fair here. Fairer than most.",
            "There's a lane behind the row, if you're in a hurry.",
            "Somebody has to keep the accounts.",
            "We paved that ourselves. Took a season.",
            "Every street in {town} goes somewhere. That's the point of them.",
            "Business first. Talk after.",
            "New face. Good. New faces spend.");

    /** The vale folk: everything happens on the green. */
    private static final List<String> VALE = List.of(
            "Everything happens on the green, sooner or later.",
            "You'll find us round the middle. We always are.",
            "{town} faces inward. Kinder that way.",
            "Sit on the green a while. Nobody minds.",
            "There's a gathering most evenings, if it's dry.",
            "Backs to the weather, faces to each other.",
            "Grass wants cutting again.",
            "We've no high street here and we want none.",
            "Stay the evening. {town} is better after dark.",
            "The lanes all come back here. Try it.",
            "Quiet year. Long may it hold.");

    /** The warhost: short sentences, and every one of them a fact. */
    private static final List<String> ORC = List.of(
            "You're standing in {town}. Remember it.",
            "Speak, or move on.",
            "The chief eats first. Everyone knows it.",
            "We took this ground. We keep it.",
            "Sharpen something. Idle hands rot.",
            "Rows straight, posts deep. That's how it's done.",
            "You're small.",
            "No trouble from you, none from me.",
            "{town} has never been taken.",
            "Work, eat, sleep, fight. In that order.");

    /** The mire goblins, who are not to be reasoned with and know it. */
    private static final List<String> GOBLIN = List.of(
            "Whatcha want? Got nothing.",
            "Don't touch that. That's mine.",
            "{town} stinks. I like it.",
            "You're big. Too big.",
            "Found a boot yesterday. Good boot.",
            "Nobody said you could stand there.",
            "Shiny? You got shiny?",
            "Mud's warm this time of year.",
            "Go on, then. Go on.",
            "We was here first. Probably.");

    /**
     * Which pool a people draws from.
     *
     * <p>Keyed on the culture id rather than the race, because that is the whole
     * claim the four human cultures make: they are the same body and a different
     * people, and a people who all say the same thing are not a people. A culture
     * with no pool of its own — the sentinel, and anything a datapack adds later
     * — falls to the lowlanders, who are what every unnamed town has always been.
     */
    private static final Map<String, List<String>> POOLS = Map.of(
            Culture.NORMAN.id(), NORMAN,
            Culture.HIGHLAND.id(), HIGHLAND,
            Culture.BURGHER.id(), BURGHER,
            Culture.VALE.id(), VALE,
            Culture.ORC.id(), ORC,
            Culture.GOBLIN.id(), GOBLIN);

    /** Everything this people might say in passing. Never empty. */
    public static List<String> poolFor(Culture culture) {
        if (culture == null) {
            return NORMAN;
        }
        return POOLS.getOrDefault(culture.id(), NORMAN);
    }

    // --- the overrides -----------------------------------------------------------

    /**
     * What outranks the pool, and what is said instead.
     *
     * <p>Shared across every people rather than written out six times. A hungry
     * orc and a hungry burgher want the same thing and the answer should be
     * recognizable as the same answer — the culture pools are where a people
     * sounds like itself, and a situation is where they all sound like anybody.
     * Each still holds more than one line, seeded the same way as the pool, so
     * two hungry settlers in the same square are not an echo.
     */
    private static final Map<Situation, List<String>> OVERRIDES = Map.of(
            Situation.ALARM, List.of(
                    "Get indoors!",
                    "Not now. Get inside.",
                    "Something's out there. Move."),
            Situation.HUNGRY, List.of(
                    "Have you any bread?",
                    "I've not eaten today.",
                    "Anything to spare? Anything at all."),
            Situation.KING, List.of(
                    "{town} is mine to answer for.",
                    "You'll find everything in order here.",
                    "Speak plainly. I've a {place} to run."),
            Situation.WAITING_ON_MATERIALS, List.of(
                    "Can't lay what I haven't got.",
                    "Waiting on stone. Always waiting on something.",
                    "Send word to the store, would you."),
            Situation.HARVEST, List.of(
                    "It's all coming in at once. It always does.",
                    "Cut today or lose it. That's the whole of farming.",
                    "Good year. Don't say it out loud."),
            Situation.ON_POST, List.of(
                    "Move along. Nothing to see.",
                    "I've the wall until dark.",
                    "Quiet so far. Long may it hold."),
            Situation.INN, List.of(
                    "Sit down. You're letting the cold in.",
                    "One more, then home.",
                    "Best room in {town}, and the only one."),
            Situation.CURFEW, List.of(
                    "Getting dark. I'm for home.",
                    "Walk with me if you're going that way.",
                    "Nothing good happens out here after dusk."));

    /**
     * Which situation has the strongest claim on this settler right now.
     *
     * <p>In {@link Situation}'s own declaration order, first match wins. Never
     * null: {@link Situation#PASSING} is the answer when nothing else holds, and
     * it is the answer most of the time.
     */
    public static Situation situationOf(Moment moment) {
        Objects.requireNonNull(moment, "moment");
        if (moment.alarm()) {
            return Situation.ALARM;
        }
        if (moment.hunger() >= Person.HUNGER_HUNGRY) {
            return Situation.HUNGRY;
        }
        if (moment.trade() == Profession.KING) {
            return Situation.KING;
        }
        if (moment.trade() == Profession.BUILDER && moment.awaitingMaterials()) {
            return Situation.WAITING_ON_MATERIALS;
        }
        if (moment.trade() == Profession.FARMER && moment.harvest() && moment.atWork()) {
            return Situation.HARVEST;
        }
        if (moment.trade() == Profession.GUARD && moment.atWork()) {
            return Situation.ON_POST;
        }
        if (moment.atInn() && moment.atLeisure() && moment.evening()) {
            return Situation.INN;
        }
        if (moment.curfew()) {
            return Situation.CURFEW;
        }
        return Situation.PASSING;
    }

    // --- the pick ----------------------------------------------------------------

    /**
     * One line, for this person, in this place, on this day.
     *
     * <p>The whole contract in one sentence: hand the same arguments in twice and
     * the same words come back, and hand them in tomorrow and different words come
     * back.
     *
     * @param day the in-game day, which is the only part of the clock the seed
     *            can see. Coarse on purpose — a settler who changed their mind
     *            between two right-clicks would be no better than one seeded off
     *            a body.
     */
    public static String lineFor(Moment moment, Town town, Culture culture, long day) {
        Objects.requireNonNull(moment, "moment");
        Objects.requireNonNull(town, "town");
        Situation situation = situationOf(moment);
        List<String> lines = situation == Situation.PASSING
                ? passingLines(town, culture)
                : OVERRIDES.get(situation);
        long seed = seedOf(moment.who(), day, situation);
        return fill(lines.get((int) Math.floorMod(seed, lines.size())), town, culture);
    }

    /**
     * The culture's pool, plus whatever this particular settlement has added to
     * it.
     *
     * <p>Two things can join the pool, and both are facts about the place rather
     * than about the people: a settlement that is not a village yet gets a line
     * about being new, and a settlement with anything in its history gets a line
     * about the last thing that happened. They are appended rather than
     * substituted so that the pool stays the pool — a town with news does not
     * stop talking about its weather, it simply has one more thing to say.
     */
    private static List<String> passingLines(Town town, Culture culture) {
        List<String> pool = poolFor(culture);
        if (town.stage().atLeast(SettlementStage.VILLAGE) && town.lastEvent() == null) {
            return pool;
        }
        List<String> lines = new ArrayList<>(pool);
        if (town.stage().before(SettlementStage.VILLAGE)) {
            lines.add("{town} is barely a {place} yet. Give it a year.");
        }
        if (town.lastEvent() != null) {
            lines.add("You'll have heard: " + town.lastEvent());
        }
        return lines;
    }

    /** The settlement's name and what it is, put into whatever the line asked for. */
    private static String fill(String line, Town town, Culture culture) {
        return line.replace(TOWN, town.name()).replace(PLACE, placeWord(town, culture));
    }

    /**
     * "town" or "camp", whichever this settlement honestly is today.
     *
     * <p>Two ways to be a camp and they are different questions: a goblin
     * settlement is a camp forever, which is {@code Culture.settlementWord}'s
     * answer and the one four other places in the mod already use; and anybody's
     * settlement is a camp until it is a village, which is the stage. A settler
     * standing in eight huts and calling it a town would be the same kind of
     * false note as a nameplate on a figure in a diorama.
     */
    private static String placeWord(Town town, Culture culture) {
        String settled = culture == null ? "town" : culture.settlementWord();
        return town.stage().before(SettlementStage.VILLAGE) ? "camp" : settled;
    }

    /**
     * The seed: a person, a day, and what is going on.
     *
     * <p>The situation is stirred in so that a settler's hungry line and their
     * idle line are not forced to be the same index — without it, everybody whose
     * seed landed on zero would say the first line of whichever list they were
     * handed, and the overrides would visibly march in step with the pool.
     *
     * <p>Finished with {@link Mix}, for the reason {@code Culture.layoutFor}
     * wants it: a day is a small number and a UUID hash is not random, so the
     * low bits of either one raw would hand a whole town the same line.
     */
    private static long seedOf(Person.Id who, long day, Situation situation) {
        return Mix.finish(who.value().getMostSignificantBits() * 0x9E3779B97F4A7C15L
                ^ Long.rotateLeft(who.value().getLeastSignificantBits(), 32)
                ^ day * 0xC2B2AE3D27D4EB4FL
                ^ (situation.ordinal() + 1L) * 0x2545F4914F6CDD1DL);
    }

    private Greetings() {
    }
}
