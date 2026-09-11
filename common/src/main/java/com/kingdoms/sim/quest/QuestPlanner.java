package com.kingdoms.sim.quest;

import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Alarm;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.BuildingRole;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.LumberPlanner;
import com.kingdoms.sim.settlement.MinePlanner;
import com.kingdoms.sim.settlement.RepairPlanner;
import com.kingdoms.sim.settlement.Seam;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.Stand;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.world.SimContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What a town asks strangers for, and what it pays them.
 *
 * <p><strong>Every quest comes off a real reading.</strong> Nothing here
 * invents a task: the food asks fire on {@link FoodPlanner#isStarving} and the
 * fed streak, the timber asks on {@link LumberPlanner#wantsMoreTimber} and a
 * build queue with nothing to build with, the slaying on the same threat number
 * that empties the streets, the clearing on a building the raid left standing in
 * pieces. That is the whole design and the reason the board is worth reading: a
 * town asking for saplings is a town that has felled its wood bare, and you can
 * walk out and see that it has.
 *
 * <p><strong>Generation is deterministic in (settlement, step).</strong> The
 * same town stepped to the same number offers the same board, which is what
 * makes {@code /civ step} an instrument rather than a slot machine — a fault
 * seen once can be seen again. There is no {@code Random} anywhere in this
 * class; the roll is a hash of the settlement's id and the step.
 *
 * <p><strong>Nothing here touches the world.</strong> Deliveries are counted by
 * the storehouse calling {@link #creditDelivery}, kills by the platform's death
 * hook calling {@link #creditKill}, and arrivals by asking the bridge which
 * players are standing where — the one thing a step genuinely has to look
 * outward for.
 */
public final class QuestPlanner {

    private QuestPlanner() {
    }

    /**
     * Steps between the town thinking of something else it wants.
     *
     * <p>Slow on purpose. A board that refills the moment a row comes off it is
     * a vending machine; one that takes a while is a town whose troubles arrive
     * at the pace troubles arrive at. Forty steps is a few minutes of the slow
     * scheduler with nobody watching, and rather less with somebody there.
     */
    public static final int OFFER_EVERY = 40;

    /** How long an ask nobody has taken stays pinned up. */
    public static final int OFFER_LIFETIME = 600;

    /**
     * How long somebody has once they have taken the job.
     *
     * <p>Longer than the offer, and it has to be: an ask on its last step would
     * otherwise expire under the player on the walk out of the gate. Accepting
     * resets the clock to this.
     */
    public static final int ACCEPTED_LIFETIME = 1_200;

    /** How near you have to stand for a VISIT to count. */
    public static final int VISIT_RADIUS = 6;

    /** How near a wreck a kill has to be to count toward clearing it. */
    public static final int CLEAR_RADIUS = 24;

    /**
     * Consecutive fed steps below which a town starts worrying about the
     * larder rather than waiting to starve.
     *
     * <p>The same instinct {@code Settlement.planFarmAhead} acts on, expressed
     * as an ask instead of a field: a town whose eating has been interrupted
     * recently would like some bread in hand, and does not want to be arguing
     * with a clock when it finally runs out.
     */
    public static final int FED_COMFORT = 30;

    // --- what a town wants, per kind, before tier scaling ---

    private static final int SLAY_BASE = 4;
    private static final int CLEAR_BASE = 5;

    /** Coin a town will pay per hostile put down inside its bounds. */
    private static final int COIN_PER_KILL = 6;

    /** And per hostile turned out of one of its own wrecked buildings. */
    private static final int COIN_PER_CLEARED = 8;

    /** What a look is worth, before the tier. */
    private static final int COIN_PER_LOOK = 8;

    /** What the town throws in off a shelf it cannot close. See {@link #glutRider}. */
    private static final int GLUT_RIDER = 16;

    /**
     * What a resource is worth to the town over the counter price.
     *
     * <p>Half again on the market's own base, so carrying a load to the board
     * beats selling the same load at the stall. It has to: the stall is eight
     * units and a button press, and the board is a walk, a deadline and
     * somebody else deciding when they want it.
     */
    private static final int REWARD_NUMERATOR = 3;
    private static final int REWARD_DENOMINATOR = 2;

    /** A ledger word with no market price still has to be worth carrying. */
    private static final int UNPRICED = 3;

    /**
     * One tick of the whole board: lapse, count, and think of something new.
     *
     * <p>The empty-board shortcut is not premature: this runs for every
     * settlement in the world on every step, and most boards are empty most of
     * the time — a town only thinks of something once in forty steps, and only
     * then if it has a trouble worth a stranger's while.
     */
    public static void advance(Settlement settlement, SimContext ctx) {
        if (!settlement.quests().isEmpty()) {
            lapseStale(settlement, ctx.step());
            creditArrivals(settlement, ctx);
        }
        if (ctx.step() % OFFER_EVERY == 0) {
            offerSomething(settlement, ctx.step());
        }
    }

    // ------------------------------------------------------------------
    // lapsing
    // ------------------------------------------------------------------

    /**
     * Takes down whatever nobody got to.
     *
     * <p>Finished work never lapses, however long the reward sits uncollected —
     * a town that took back a reward for a job it had already had done would be
     * the single worst thing on this board.
     */
    private static void lapseStale(Settlement settlement, long step) {
        for (Quest quest : List.copyOf(settlement.quests().offered())) {
            if (!quest.isStale(step)) {
                continue;
            }
            settlement.quests().remove(quest.id());
            if (quest.isAccepted()) {
                settlement.logEvent(step, "Nobody came back about the "
                        + quest.title().toLowerCase(Locale.ROOT));
            }
        }
    }

    // ------------------------------------------------------------------
    // progress
    // ------------------------------------------------------------------

    /**
     * Marks off anybody standing where they were asked to stand.
     *
     * <p>The one outward look a step takes. Asking the bridge for the players
     * near a point, rather than having the platform push arrivals in, is what
     * keeps a VISIT completable while nobody is clicking anything: you walk to
     * the mark, and the next step notices.
     */
    private static void creditArrivals(Settlement settlement, SimContext ctx) {
        for (Quest quest : List.copyOf(settlement.quests().offered())) {
            if (quest.kind() != QuestKind.VISIT || !quest.isAccepted()
                    || quest.place() == null) {
                continue;
            }
            List<UUID> there = ctx.bridge().playersWithin(quest.place(), VISIT_RADIUS);
            if (there.contains(quest.acceptedBy())) {
                settlement.quests().replace(quest.withProgress(quest.amount()));
            }
        }
    }

    /**
     * Counts goods handed over at the storehouse against whatever was asked for.
     *
     * <p>Called from the donation path rather than being a second way to give
     * the town things: a delivery is the ordinary gesture, and the quest is the
     * town noticing. That is why a player who has taken no quest loses nothing
     * by donating, and why donating more than was asked is not wasted — the
     * surplus is simply a donation, which is what it always was.
     *
     * @return how much of the handover the board could count
     */
    public static int creditDelivery(Settlement settlement, UUID player,
                                     String resource, int amount) {
        if (player == null || resource == null || amount <= 0) {
            return 0;
        }
        int counted = 0;
        for (Quest quest : List.copyOf(settlement.quests().offered())) {
            if (quest.kind() != QuestKind.DELIVER || !quest.isAccepted()
                    || !quest.isHeldBy(player) || !quest.target().equals(resource)) {
                continue;
            }
            int fits = Math.min(amount - counted, quest.remaining());
            if (fits <= 0) {
                continue;
            }
            settlement.quests().replace(quest.withProgress(quest.progress() + fits));
            counted += fits;
            if (counted >= amount) {
                break;
            }
        }
        return counted;
    }

    /**
     * Counts one hostile a player put down.
     *
     * <p>Attributed, and only to the hand that did it: a zombie the town's own
     * guards killed is the guards doing their job, and crediting it to whoever
     * happened to be standing about would let a player take a slaying quest and
     * watch the watch finish it.
     *
     * <p>Where it died decides which asks it can count toward. Inside the claim
     * for a slaying; within {@link #CLEAR_RADIUS} of the wreck for a clearing.
     * One death can satisfy both, and should — the wreck is inside the town.
     */
    public static void creditKill(Settlement settlement, UUID player, SimPos where) {
        if (player == null || where == null) {
            return;
        }
        for (Quest quest : List.copyOf(settlement.quests().offered())) {
            if (!quest.kind().countsKills() || !quest.isAccepted()
                    || !quest.isHeldBy(player)) {
                continue;
            }
            if (!countsHere(settlement, quest, where)) {
                continue;
            }
            settlement.quests().replace(quest.withProgress(quest.progress() + 1));
        }
    }

    private static boolean countsHere(Settlement settlement, Quest quest, SimPos where) {
        if (quest.kind() == QuestKind.SLAY) {
            return settlement.contains(where);
        }
        if (quest.place() == null) {
            return false;
        }
        return quest.place().horizontalDistanceSq(where)
                <= (long) CLEAR_RADIUS * CLEAR_RADIUS;
    }

    // ------------------------------------------------------------------
    // the three things a player can press
    // ------------------------------------------------------------------

    /** Takes an offer off the board and into somebody's hands. */
    public static boolean accept(Settlement settlement, UUID player, String questId,
                                 long step) {
        Quest quest = settlement.quests().find(questId);
        if (quest == null || !quest.isOffered() || player == null) {
            return false;
        }
        return settlement.quests().replace(
                quest.takenBy(player, step + ACCEPTED_LIFETIME));
    }

    /**
     * Gives a job back, and loses whatever was done toward it.
     *
     * <p>Losing the progress is the point of there being a button: an abandoned
     * quest goes back on the board for anybody, and carrying one player's half-
     * finished count over to the next one would be the town paying twice.
     */
    public static boolean abandon(Settlement settlement, UUID player, String questId,
                                  long step) {
        Quest quest = settlement.quests().find(questId);
        if (quest == null || !quest.isHeldBy(player) || quest.isDone()) {
            return false;
        }
        return settlement.quests().replace(quest.putBack(step + OFFER_LIFETIME));
    }

    /** What the town actually handed over. All zeroes means it could not pay. */
    public record Payout(int coin, String goods, int goodsAmount, int standing,
                         int standingNow) {

        public static final Payout NOTHING = new Payout(0, "", 0, 0, 0);

        public boolean any() {
            return coin > 0 || goodsAmount > 0 || standing > 0;
        }
    }

    /**
     * Pays for finished work and takes the notice down.
     *
     * <p>Coin comes out of the treasury and goods off the shelves, so a reward
     * is a transfer and never an issuance — the same rule the market lives
     * under, and the reason a town's wealth still measures what it started with
     * minus what it has spent. A town that has gone broke between offering and
     * paying pays what it has, and the standing is honored in full: regard is
     * the one thing a settlement can always afford.
     *
     * @return what to put in the player's hands, or {@link Payout#NOTHING}
     */
    public static Payout claim(Settlement settlement, UUID player, String questId,
                               long step) {
        Quest quest = settlement.quests().find(questId);
        if (quest == null || !quest.isDone() || !quest.isHeldBy(player)) {
            return Payout.NOTHING;
        }
        Reward reward = quest.reward();
        int coin = Math.min(reward.coin(), settlement.treasury());
        if (coin > 0) {
            settlement.spend(coin);
        }
        int goods = reward.hasGoods()
                ? settlement.stores().takeUpTo(reward.goods(), reward.goodsAmount()) : 0;
        int now = settlement.standing().add(player, reward.standing());
        settlement.quests().recordDone(quest);
        settlement.logEvent(step, "A stranger saw to it: "
                + quest.title().toLowerCase(Locale.ROOT));
        return new Payout(coin, goods > 0 ? reward.goods() : "", goods,
                reward.standing(), now);
    }

    // ------------------------------------------------------------------
    // generation
    // ------------------------------------------------------------------

    /** One thing the town would like, and how badly. */
    private record Want(int weight, QuestKind kind, String target, SimPos place) {
    }

    /**
     * Thinks of one thing, weighted by how much trouble the town is actually in.
     *
     * <p>One at a time, never a batch. A board that filled itself in a single
     * step would show five asks all dated the same moment and all expiring
     * together, which is a mailing rather than a noticeboard.
     */
    private static void offerSomething(Settlement settlement, long step) {
        if (!settlement.quests().hasRoom()) {
            return;
        }
        List<Want> wants = wants(settlement);
        if (wants.isEmpty()) {
            return;   // nothing wrong and nowhere worth looking at
        }
        Want chosen = pick(wants, roll(settlement, step));
        if (alreadyAsking(settlement, chosen)) {
            return;   // the town is not going to ask twice for the same thing
        }
        Quest quest = shape(settlement, chosen, step);
        if (quest.reward().isEmpty()) {
            return;   // a town with nothing to offer does not post a job
        }
        settlement.quests().post(quest);
    }

    /** Whether an ask of this kind and target is already pinned up. */
    private static boolean alreadyAsking(Settlement settlement, Want want) {
        for (Quest quest : settlement.quests().offered()) {
            if (quest.kind() == want.kind() && quest.target().equals(want.target())) {
                return true;
            }
        }
        return false;
    }

    /** Everything this town would like a hand with, right now. */
    private static List<Want> wants(Settlement settlement) {
        List<Want> wants = new ArrayList<>();
        addFood(settlement, wants);
        addMaterials(settlement, wants);
        addDefense(settlement, wants);
        addLook(settlement, wants);
        return wants;
    }

    private static void addFood(Settlement settlement, List<Want> wants) {
        int held = settlement.stores().get(TownStores.FOOD);
        int weight;
        if (settlement.isStarving()) {
            weight = 40;
        } else if (held < FoodPlanner.STARTING_PROVISIONS) {
            weight = 14;
        } else if (settlement.fedStreak() < FED_COMFORT) {
            weight = 6;
        } else {
            return;
        }
        wants.add(new Want(weight, QuestKind.DELIVER, TownStores.FOOD, null));
    }

    /**
     * Timber, stone, iron and saplings — the four the town builds and mends with.
     *
     * <p><strong>Short is not the same as "not full".</strong>
     * {@link LumberPlanner#wantsMoreTimber} answers whether felling is still
     * worth doing, which is true of a town holding eight hundred logs of a
     * possible eight hundred and ninety-six — so reading it alone had every
     * settlement in the world permanently asking for wood. The reading that
     * means short is the market's: it wants more <em>and</em> it is nearly out.
     * The two must agree, or a town would be buying timber at a premium on one
     * counter while advertising that it had plenty on the other.
     *
     * <p>A queue on top of that is a second trouble rather than the same one: a
     * camp that would like to keep felling is a want, and a build that has
     * stopped for lack of logs is a stoppage, and a town in both should outrank
     * a town in either.
     */
    private static void addMaterials(Settlement settlement, List<Want> wants) {
        boolean building = !settlement.buildQueue().isEmpty();

        if (LumberPlanner.wantsMoreTimber(settlement)
                && settlement.woodStock() < WAITING_ON) {
            wants.add(new Want(building ? 22 : 10, QuestKind.DELIVER,
                    TownStores.WOOD, null));
        }

        if (MinePlanner.wantsMoreStone(settlement)
                && settlement.stoneStock() < WAITING_ON) {
            wants.add(new Want(building ? 18 : 8, QuestKind.DELIVER,
                    TownStores.STONE, null));
        }

        if (settlement.buildingWithRole(BuildingRole.SMITH) != null
                && settlement.stores().get(TownStores.IRON) < IRON_COMFORT) {
            wants.add(new Want(6, QuestKind.DELIVER, TownStores.IRON, null));
        }

        for (Building camp : settlement.buildingsWithRole(BuildingRole.LUMBER_CAMP)) {
            if (Stand.isBare(camp)) {
                wants.add(new Want(10, QuestKind.DELIVER, TownStores.SAPLINGS, null));
                break;
            }
        }
    }

    /**
     * A stock this low is short, whatever the shelves would hold.
     *
     * <p>{@code Market.LOT * 8} — the market's own reading of "short of it", and
     * the same number on purpose. The stall and the board are two counters of
     * one town, and a settlement paying double for stone at one while telling
     * the other it has plenty is a settlement lying to somebody.
     */
    private static final int WAITING_ON = Market.LOT * 8;

    /** Iron below this and the smith is idle. Matches the market's own reading. */
    private static final int IRON_COMFORT = 32;

    private static void addDefense(Settlement settlement, List<Want> wants) {
        if (settlement.threatLevel() >= Alarm.WARY_AT) {
            wants.add(new Want(Math.min(30, 6 + settlement.threatLevel()),
                    QuestKind.SLAY, "hostiles", null));
        }
        for (Building hurt : settlement.buildings()) {
            if (hurt.damage() >= RepairPlanner.SEVERE_DAMAGE) {
                wants.add(new Want(12, QuestKind.CLEAR, "wreck", hurt.origin()));
                break;   // one wreck at a time; the board is five rows, not a survey
            }
        }
    }

    /**
     * Somewhere the town would like a stranger to go and look at.
     *
     * <p>The fallback, and the reason a settlement with nothing wrong still has
     * a board worth reading. It is also the only kind that is not a chore: the
     * town is not asking for anything, it is showing you the cut-out mine, or
     * the wood it has stripped, or the building it forgot to build a road to.
     * Every one of those is a real fact about the place that a player would
     * otherwise have to go looking for.
     */
    private static void addLook(Settlement settlement, List<Want> wants) {
        Building mine = spentMine(settlement);
        if (mine != null) {
            wants.add(new Want(6, QuestKind.VISIT, "mine", mine.origin()));
        }
        Building stripped = bareStand(settlement);
        if (stripped != null) {
            wants.add(new Want(5, QuestKind.VISIT, "stand", stripped.origin()));
        }
        Building stranded = unjoined(settlement);
        if (stranded != null) {
            wants.add(new Want(4, QuestKind.VISIT, "door", stranded.doorstep()));
        }
        Building newest = newestBuilding(settlement);
        if (newest != null) {
            wants.add(new Want(3, QuestKind.VISIT, "mark", newest.origin()));
        }
    }

    private static Building spentMine(Settlement settlement) {
        for (Building mine : settlement.buildingsWithRole(BuildingRole.MINE)) {
            if (Seam.isExhausted(mine)) {
                return mine;
            }
        }
        return null;
    }

    private static Building bareStand(Settlement settlement) {
        for (Building camp : settlement.buildingsWithRole(BuildingRole.LUMBER_CAMP)) {
            if (Stand.isBare(camp)) {
                return camp;
            }
        }
        return null;
    }

    /** A building the road crew never reached. See {@code PathNetwork.hasJoined}. */
    private static Building unjoined(Settlement settlement) {
        for (Building standing : settlement.buildings()) {
            if (!settlement.paths().hasJoined(standing.origin())) {
                return standing;
            }
        }
        return null;
    }

    private static Building newestBuilding(Settlement settlement) {
        List<Building> standing = settlement.buildings();
        return standing.isEmpty() ? null : standing.get(standing.size() - 1);
    }

    /** Weighted choice, from a roll already made. */
    private static Want pick(List<Want> wants, long roll) {
        int total = 0;
        for (Want want : wants) {
            total += want.weight();
        }
        long at = Math.floorMod(roll, Math.max(1, total));
        for (Want want : wants) {
            at -= want.weight();
            if (at < 0) {
                return want;
            }
        }
        return wants.get(wants.size() - 1);
    }

    /**
     * The roll, which is a hash and not a random number.
     *
     * <p>SplitMix64's finalizer over the settlement's own id and the step. Two
     * towns stepped together get different boards; one town stepped to the same
     * number twice gets the same board, which is what {@code /civ step} is for.
     */
    private static long roll(Settlement settlement, long step) {
        long seed = settlement.id().value().getMostSignificantBits()
                ^ (settlement.id().value().getLeastSignificantBits() * 0x9E3779B97F4A7C15L)
                ^ (step * 0xBF58476D1CE4E5B9L);
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }

    // ------------------------------------------------------------------
    // pricing
    // ------------------------------------------------------------------

    /** Turns a want into the notice that goes on the board. */
    private static Quest shape(Settlement settlement, Want want, long step) {
        int tier = Reputation.tierOf(settlement.standing().best());
        int amount = amountFor(want, tier);
        Reward reward = rewardFor(settlement, want, amount, tier);
        String id = "q" + step + want.kind().name().charAt(0);
        return Quest.offered(id, want.kind(),
                QuestVoice.title(want.kind(), want.target(), settlement),
                QuestVoice.detail(want.kind(), want.target(), settlement),
                want.target(), want.place(), amount, reward,
                step, step + OFFER_LIFETIME);
    }

    /**
     * How much is asked for, scaled by what the town has learned to expect.
     *
     * <p>The tier is read off the best standing the town holds with anybody,
     * because a board is pinned to a post rather than handed to a reader — see
     * {@link Reputation#best()}. A town that has never met anybody asks for what
     * a stranger could manage.
     */
    private static int amountFor(Want want, int tier) {
        int base = switch (want.kind()) {
            case DELIVER -> deliveryBase(want.target());
            case SLAY -> SLAY_BASE;
            case CLEAR -> CLEAR_BASE;
            case VISIT, UNKNOWN -> 1;
        };
        if (want.kind() == QuestKind.VISIT) {
            return 1;   // you are there or you are not
        }
        // Half again per rung: a stranger carries sixteen loaves, a sworn friend
        // forty. Integer arithmetic on purpose, and rounded down, so the numbers
        // on a board are numbers a person would say.
        return Math.max(1, base * (2 + tier) / 2);
    }

    private static int deliveryBase(String resource) {
        return switch (resource) {
            case TownStores.FOOD -> 16;
            case TownStores.WOOD, TownStores.STONE -> 32;
            case TownStores.IRON, TownStores.SAPLINGS -> 8;
            default -> 16;
        };
    }

    /**
     * What the town will pay, bounded by what it actually has.
     *
     * <p>Offered coin is capped at the treasury as it stands, so a board never
     * advertises money the town does not have. It can still go broke between the
     * offer and the claim — {@link #claim} pays what is left — but that is a
     * town's fortunes changing rather than a town lying.
     */
    private static Reward rewardFor(Settlement settlement, Want want, int amount,
                                    int tier) {
        int coin = switch (want.kind()) {
            case DELIVER -> unitWorth(want.target()) * amount
                    * REWARD_NUMERATOR / REWARD_DENOMINATOR;
            case SLAY -> COIN_PER_KILL * amount;
            case CLEAR -> COIN_PER_CLEARED * amount;
            case VISIT -> COIN_PER_LOOK + 4 * tier;
            case UNKNOWN -> 0;
        };
        coin = Math.min(coin, settlement.treasury());
        int standing = switch (want.kind()) {
            case DELIVER -> 2 + tier;
            case SLAY -> 3 + tier;
            case CLEAR -> 4 + tier;
            case VISIT -> 1 + tier;
            case UNKNOWN -> 0;
        };
        String rider = want.kind().countsKills() ? glutRider(settlement) : null;
        return rider == null
                ? Reward.of(coin, standing)
                : new Reward(coin, rider, GLUT_RIDER, standing);
    }

    /** The market's own price where there is one; something, where there is not. */
    private static int unitWorth(String resource) {
        int base = Market.basePrice(resource);
        return base > 0 ? base : UNPRICED;
    }

    /**
     * Something off a shelf the town cannot close, thrown in on top.
     *
     * <p>Only on the fighting quests, and the reason is the same reason the
     * market says "more than they can store": a town that has run out of room
     * for stone would genuinely rather give you sixteen of it than have it. It
     * is not thrown in on a delivery, because paying for goods in goods is how
     * a player learns to carry the same load round in a circle.
     *
     * @return the ledger word, or null if the town is comfortable everywhere
     */
    private static String glutRider(Settlement settlement) {
        for (String resource : Market.TRADED) {
            if (TownStores.FOOD.equals(resource)) {
                continue;   // a town's dinner is never spare, whatever the shelf says
            }
            if (Market.isGlutted(settlement, resource)
                    && settlement.stores().has(resource, GLUT_RIDER)) {
                return resource;
            }
        }
        return null;
    }
}
