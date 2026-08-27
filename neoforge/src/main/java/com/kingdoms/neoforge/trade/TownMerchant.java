package com.kingdoms.neoforge.trade;

import com.kingdoms.sim.economy.Market;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.settlement.Resources;
import com.kingdoms.sim.settlement.Settlement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.HashMap;
import java.util.Map;

/**
 * A settlement, standing behind its own market stall.
 *
 * <p>Implementing {@link Merchant} buys the whole villager trading screen for
 * nothing — the layout, the arrows, the greyed-out offers a player cannot
 * afford, the stock limits — all of which players already understand. A custom
 * screen would look better and would have to earn that by being better; see
 * {@code GUI_GUIDE.md} for how to build one when it does.
 *
 * <p><strong>Emeralds exist only here.</strong> Inside the town money is an
 * integer on the settlement and nobody owns any of it. At this counter the two
 * meet: emeralds are created out of the treasury when the town pays, and
 * consumed into it when the town is paid. The invariant is that every emerald
 * entering the world came out of a treasury and every one leaving went into
 * one, which is exactly what {@link #notifyTrade} maintains.
 *
 * <p>What the deals <em>are</em> is not decided here — {@link Market} decides
 * that from what the town is short of. This only dresses them as offers and
 * keeps the ledger straight afterwards.
 */
public final class TownMerchant implements Merchant {

    private final Settlement settlement;
    private final SimPos where;
    private final MerchantOffers offers = new MerchantOffers();

    /** Which of our deals each offer stands for, so a completed trade can be applied. */
    private final Map<MerchantOffer, Market.Deal> deals = new HashMap<>();

    private Player trader;

    public TownMerchant(Settlement settlement, SimPos where) {
        this.settlement = settlement;
        this.where = where;
        build();
    }

    /** Whether there is anything at all to show a player. */
    public boolean hasAnything() {
        return !offers.isEmpty();
    }

    private void build() {
        for (Market.Deal deal : Market.offers(settlement)) {
            Item item = itemFor(deal.resource());
            if (item == null) {
                continue;   // a resource with no item to stand for it cannot be traded
            }
            int perLot = Market.LOT;
            int coin = deal.unitPrice() * perLot;
            if (coin <= 0 || coin > 64 || perLot > item.getDefaultMaxStackSize()) {
                continue;   // will not fit in a vanilla trade slot; skip rather than lie
            }
            MerchantOffer offer = deal.townBuys()
                    // The town pays: goods in, emeralds out.
                    ? new MerchantOffer(new ItemCost(item, perLot),
                            new ItemStack(Items.EMERALD, coin), deal.lots(), 0, 0.0F)
                    // The town is paid: emeralds in, goods out.
                    : new MerchantOffer(new ItemCost(Items.EMERALD, coin),
                            new ItemStack(item, perLot), deal.lots(), 0, 0.0F);
            offers.add(offer);
            deals.put(offer, deal);
        }
    }

    private static Item itemFor(String resource) {
        String id = Resources.itemFor(resource);
        if (id == null || id.isEmpty()) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        return item == Items.AIR ? null : item;
    }

    /**
     * A trade has just happened in the screen; make the ledger agree with it.
     *
     * <p>Vanilla has already moved the items and the emeralds by the time this
     * is called, so this cannot refuse — which is why the offers were built from
     * what the town could actually honour, and why each carries a use limit.
     * The one thing that must not happen here is the ledger and the world
     * disagreeing about what changed hands.
     */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        Market.Deal deal = deals.get(offer);
        if (deal == null) {
            return;
        }
        if (deal.townBuys()) {
            Market.townBuys(settlement, where, deal.resource(), Market.LOT);
        } else {
            Market.townSells(settlement, where, deal.resource(), Market.LOT);
        }
    }

    @Override
    public void setTradingPlayer(Player player) {
        this.trader = player;
    }

    @Override
    public Player getTradingPlayer() {
        return trader;
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers replacement) {
        // Nothing to do: a town's offers come from its own stores and shortages,
        // and letting anything else write them would be letting it lie about
        // what it has.
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;   // a town does not level up by being traded with
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return trader == player;
    }
}
