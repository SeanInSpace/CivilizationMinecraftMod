package com.kingdoms.neoforge.trade;

import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * The counter itself: where a town's ledger and a player's pockets meet.
 *
 * <p>What the deals <em>are</em> is not decided here — {@link Market} decides
 * that, from what the town is short of, and can be tested without a game. All
 * that is left for this side is moving real things, which cannot be.
 *
 * <p><strong>Emeralds exist only here.</strong> Inside the town money is an
 * integer on the settlement and nobody owns any of it. At this counter the two
 * meet: emeralds are created out of the treasury when the town pays, and
 * consumed into it when the town is paid, one for one. The invariant is that
 * every emerald entering the world came out of a treasury and every one leaving
 * it went into one, which is why nothing below moves an item without the ledger
 * having moved first.
 *
 * <p>That ordering is the whole of the care taken here. The payment is
 * <em>counted</em> before the ledger is touched and <em>removed</em> after, so
 * the two failures that would matter are both impossible: a player charged for
 * goods a town would not sell, and a town debited for goods that were never
 * handed over.
 */
public final class MarketCounter {

    private MarketCounter() {
    }

    /**
     * Takes one lot, in whichever direction the player asked for.
     *
     * <p>Every reason it can fail is worth saying out loud. A stall that does
     * nothing when a button is pressed is indistinguishable from a broken one,
     * and most of these are the town telling you something: it has run out of
     * money, or it will not go below its reserve.
     *
     * @return whether anything actually changed hands
     */
    public static boolean take(ServerPlayer player, Settlement settlement, SimPos at,
                               String resource, boolean townBuys) {
        return townBuys
                ? townBuys(player, settlement, at, resource)
                : townSells(player, settlement, at, resource);
    }

    /** The town pays: goods in off the player, emeralds out of the treasury. */
    private static boolean townBuys(ServerPlayer player, Settlement settlement,
                                    SimPos at, String resource) {
        int units = Market.LOT;
        Item wanted = itemFor(resource);
        if (wanted == null) {
            refuse(player, "They do not deal in that.");
            return false;
        }
        Predicate<ItemStack> goods = stack -> stack.is(wanted);
        if (count(player, goods) < units) {
            refuse(player, "You have not got " + units + " " + resource + " to sell.");
            return false;
        }
        int paid = Market.townBuys(settlement, at, resource, units);
        if (paid <= 0) {
            refuse(player, settlement.name() + " will not take that today.");
            return false;
        }
        // Counted first, so this cannot come up short and leave the town paying
        // for goods it never received.
        remove(player, goods, units);
        give(player, Items.EMERALD, paid);
        told(player, settlement.name() + " takes " + units + " " + resource
                + " for " + paid + " emerald" + (paid == 1 ? "" : "s") + ".");
        return true;
    }

    /** The town is paid: emeralds in, goods out of its surplus. */
    private static boolean townSells(ServerPlayer player, Settlement settlement,
                                     SimPos at, String resource) {
        Market.Deal deal = Market.sellOffer(settlement, resource);
        if (deal == null) {
            refuse(player, settlement.name() + " has none of that to spare.");
            return false;
        }
        Item item = itemFor(resource);
        if (item == null) {
            refuse(player, "There is nothing they could hand you for that.");
            return false;
        }
        int price = deal.lotPrice();
        if (count(player, stack -> stack.is(Items.EMERALD)) < price) {
            refuse(player, "That costs " + price + " emeralds.");
            return false;
        }
        int taken = Market.townSells(settlement, at, resource, Market.LOT);
        if (taken <= 0) {
            refuse(player, settlement.name() + " changed its mind — the reserve is not for sale.");
            return false;
        }
        // The ledger settled on `taken`, not on the price read off the offer a
        // moment ago, so the two cannot part company if the deal moved underfoot.
        remove(player, stack -> stack.is(Items.EMERALD), taken);
        give(player, item, Market.LOT);
        told(player, Market.LOT + " " + resource + " from " + settlement.name()
                + " for " + taken + " emeralds.");
        return true;
    }

    /**
     * The one item a resource is bought and sold as, or null if nothing stands
     * for it.
     *
     * <p><strong>The same item in both directions, and only that item.</strong>
     * The obvious kindness is to take anything the town would count as timber —
     * {@link Resources#resourceOf} accepts every log, plank and stem — and it is
     * a money pump, because the ledger counts units and vanilla crafting does
     * not: eight logs bought at three become thirty-two planks sold at two, and
     * the treasury pays forty coin a pass for wood it already owned. The spread
     * is 3:2 and crafting is 4:1, so the spread loses.
     *
     * <p>The warehouse's tag classifier is wider still and wrong here for a
     * second reason: it answers stone for any pickaxe-mineable block item, so a
     * shulker box in an early slot would be bought off a player for two coin.
     * That one is right for a gift and wrong for a purchase, which is why there
     * are two of them.
     */
    private static Item itemFor(String resource) {
        String id = Resources.itemFor(resource);
        if (id == null) {
            return null;
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.AIR);
        return item == Items.AIR ? null : item;
    }

    /**
     * How many matching items a player is carrying.
     *
     * <p>The whole inventory rather than the hand: a screen with a button on it
     * is not a gesture made with one stack, and a player whose logs are in the
     * second row would otherwise be told they had none.
     */
    private static int count(ServerPlayer player,
                             Predicate<ItemStack> wanted) {
        int found = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && wanted.test(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** Takes exactly this many, having already established there are that many. */
    private static void remove(ServerPlayer player,
                               Predicate<ItemStack> wanted, int count) {
        int left = count;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !wanted.test(stack)) {
                continue;
            }
            left -= inventory.removeItem(slot, Math.min(left, stack.getCount())).getCount();
        }
    }

    /**
     * Hands goods over, a stack at a time.
     *
     * <p>Batched to the item's own stack size rather than handed over as one
     * oversized stack: a hundred and twenty-eight emeralds is two stacks, and
     * {@code Inventory.add} reports success when it has placed only part of
     * what it was given. Anything that will not fit lands at the player's feet,
     * which is the one outcome where nothing is destroyed.
     */
    private static void give(ServerPlayer player, Item item, int count) {
        int left = count;
        while (left > 0) {
            int batch = Math.min(left, item.getDefaultMaxStackSize());
            left -= batch;
            ItemStack stack = new ItemStack(item, batch);
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private static void refuse(ServerPlayer player, String why) {
        player.sendSystemMessage(Component.literal("  " + why)
                .withStyle(ChatFormatting.GRAY));
    }

    private static void told(ServerPlayer player, String what) {
        player.sendSystemMessage(Component.literal("  " + what)
                .withStyle(ChatFormatting.GREEN));
    }
}
