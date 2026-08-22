package com.kingdoms.neoforge.net;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.client.KingdomsScreens;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Inventory;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.FoodPlanner;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * "Here is the state of your town" — server to client, to open the overview.
 *
 * <p>A snapshot rather than a live view. The screen shows what was true when it
 * was opened and does not update behind the player's back, which is honest: the
 * alternative is a number that changes while you are reading it, for a
 * simulation that steps once every five seconds anyway.
 *
 * <p>The ledger travels as a list of name/amount pairs rather than a typed set of
 * fields, because {@code TownStores} lets anything be stored under any name. A
 * resource this build has never heard of still shows up.
 *
 * <p>The alarm travels alongside it. A town's ledger read on its own is a set of
 * numbers with no verdict attached, and a player reading "food 0" has to know
 * what that costs before it means anything.
 */
public record TownOverviewPayload(String town, int population, Distress distress, List<Line> lines)
        implements CustomPacketPayload {

    /** One row of the ledger. */
    public record Line(String resource, int amount) {
    }

    /**
     * How badly the town is doing, and the two sentences that say why.
     *
     * <p>A fresh town once built its hall, ran out of materials, bootstrapped a
     * mine, staffed it with nobody and starved to death — and every surface the
     * player could reach reported population and stock as if nothing were wrong.
     * This is the verdict those surfaces were missing.
     *
     * <p>Prose is composed on the server rather than the client because it counts
     * residents and reads planner stock the client has never been sent. Both
     * surfaces — the post's chat lines and the hall screen's band — draw from
     * this one reading so they cannot drift apart.
     *
     * <p>Deliberately computed here from the public {@link Settlement} API rather
     * than from the simulation, because the simulation does not yet have a crisis
     * predicate of its own. When it grows one, this whole record should become a
     * thin translation of it and {@link #of(Settlement)} should go away.
     */
    public record Distress(int alarm, String fact, String remedy) {

        /** Rungs, not a flag: a hungry town should not sound like a dying one. */
        public static final int ALARM_STEADY = 0;
        public static final int ALARM_HUNGRY = 1;
        public static final int ALARM_FAILING = 2;
        public static final int ALARM_DYING = 3;

        public static final Distress STEADY = new Distress(ALARM_STEADY, "", "");

        public boolean silent() {
            return alarm <= ALARM_STEADY;
        }

        /**
         * Only the serious rungs earn a second line. A town that is merely thin on
         * food should report the fact and stop talking; one that is losing people
         * should also say what to do about it.
         */
        public boolean showsRemedy() {
            return alarm >= ALARM_FAILING && !remedy.isBlank();
        }

        /** The state word both surfaces build their headline from. */
        public String word() {
            return switch (alarm) {
                case ALARM_DYING -> "dying";
                case ALARM_FAILING -> "failing";
                default -> "going hungry";
            };
        }

        /**
         * Reads a settlement's condition in one pass over its people and larder.
         *
         * <p>The rungs run worst-first: somebody actually starving outranks
         * somebody merely too weak to work, which outranks an empty larder, which
         * outranks a thin one. Whichever bites first is what the player is told,
         * because a list of four complaints is a list nobody reads.
         */
        public static Distress of(Settlement settlement) {
            int population = settlement.population();
            if (population == 0) {
                return new Distress(ALARM_DYING, "Nobody lives here any more",
                        "The town is dead.");
            }

            int starving = 0;
            int weak = 0;
            int hungry = 0;
            int farmers = 0;
            int carried = 0;
            for (Person person : settlement.residents()) {
                int hunger = person.hunger();
                if (hunger >= Person.HUNGER_SEVERE) {
                    starving++;
                }
                if (hunger >= Person.HUNGER_WEAK) {
                    weak++;
                }
                if (hunger >= Person.HUNGER_HUNGRY) {
                    hungry++;
                }
                if (person.profession() == Profession.FARMER) {
                    farmers++;
                }
                carried += carriedFood(person);
            }

            // Everything edible the town owns, wherever it happens to be sitting.
            // Nothing teleports between stores here — a load is debited at pickup
            // and credited at delivery, so at any given moment a share of the
            // town's food is on somebody's back, and settlers eat out of their own
            // packs besides. Counting only the stores would tell a town mid-errand
            // that its larder is empty, which is a haulage frame, not a famine.
            int larder = settlement.foodStock()
                    + FoodPlanner.pantryTotal(settlement)
                    + FoodPlanner.farmStock(settlement)
                    + FoodPlanner.marketStock(settlement)
                    + carried;

            String remedy = remedyFor(larder, farmers);

            if (starving > 0) {
                return new Distress(ALARM_DYING,
                        starving + " of " + population + " are starving", remedy);
            }
            if (weak > 0) {
                return new Distress(ALARM_FAILING,
                        weak + " of " + population + " are too weak to work", remedy);
            }
            if (larder == 0) {
                return new Distress(ALARM_FAILING, "The larder is empty", remedy);
            }
            if (hungry > 0) {
                return new Distress(ALARM_HUNGRY,
                        hungry + " of " + population + " are going hungry", remedy);
            }
            // Borrows the simulation's own per-head buffer as a yardstick — it is
            // the number the sim uses to decide a town can feed one more mouth,
            // so a town that cannot bank it is living hand to mouth. Not the same
            // predicate as canFeedAnotherMouth, which weighs only the granary and
            // is asking a narrower question.
            if (larder < population * FoodPlanner.BIRTH_FOOD_BUFFER_STEPS) {
                return new Distress(ALARM_HUNGRY,
                        larder + " food for " + population + " residents", remedy);
            }
            return STEADY;
        }

        /** Food on one person's back: the load being walked, plus their own pack. */
        private static int carriedFood(Person person) {
            HaulTask haul = person.haul();
            int total = haul == null ? 0 : haul.carried();
            for (Inventory.Slot slot : person.inventory().slots()) {
                if (Foods.isFood(slot.itemId())) {
                    total += slot.count();
                }
            }
            return total;
        }

        /**
         * The shortest true thing a player could act on.
         *
         * <p>Kept under the hall panel's width on purpose — the screen centres it
         * in 240 pixels and has nowhere to wrap to.
         */
        private static String remedyFor(int larder, int farmers) {
            if (larder == 0 && farmers == 0) {
                return "No food, and nobody is farming.";
            }
            if (farmers == 0) {
                return "Nobody is farming.";
            }
            if (larder == 0) {
                return "No food anywhere in the town.";
            }
            return "Bring food to the warehouse.";
        }
    }

    public static final Type<TownOverviewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "town_overview"));

    /** Generous for a town name, short enough not to be a payload attack. */
    private static final int MAX_NAME = 96;
    private static final int MAX_RESOURCE = 64;
    private static final int MAX_NOTE = 96;

    private static final StreamCodec<RegistryFriendlyByteBuf, Line> LINE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_RESOURCE), Line::resource,
                    ByteBufCodecs.VAR_INT, Line::amount,
                    Line::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Distress> DISTRESS_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Distress::alarm,
                    ByteBufCodecs.stringUtf8(MAX_NOTE), Distress::fact,
                    ByteBufCodecs.stringUtf8(MAX_NOTE), Distress::remedy,
                    Distress::new);

    // Order here is load-bearing: the terminal ::new is the canonical record
    // constructor, so each getter must sit where its component does.
    public static final StreamCodec<RegistryFriendlyByteBuf, TownOverviewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), TownOverviewPayload::town,
                    ByteBufCodecs.VAR_INT, TownOverviewPayload::population,
                    DISTRESS_CODEC, TownOverviewPayload::distress,
                    LINE_CODEC.apply(ByteBufCodecs.list()), TownOverviewPayload::lines,
                    TownOverviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Reads a settlement into a payload, in the ledger's own order. */
    public static TownOverviewPayload of(Settlement settlement) {
        List<Line> lines = new ArrayList<>();
        settlement.stores().all().forEach((resource, amount) -> lines.add(new Line(resource, amount)));
        return new TownOverviewPayload(settlement.name(), settlement.population(),
                Distress.of(settlement), lines);
    }

    public static void handle(TownOverviewPayload payload, IPayloadContext context) {
        // Already on the client's main thread by the time a handler runs.
        KingdomsScreens.openTownOverview(payload);
    }
}
