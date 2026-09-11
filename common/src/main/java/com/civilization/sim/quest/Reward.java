package com.civilization.sim.quest;

/**
 * What a town is offering for the job.
 *
 * <p>Three currencies, and they are three because they cost the town three
 * different things. Coin comes out of the treasury, which is finite and was
 * never minted — see {@code Settlement#FOUNDING_TREASURY} — so a poor town
 * offers little and says so. Goods come off its own shelves, which is why a
 * starving town does not pay in bread. Standing costs it nothing at all and is
 * the only part that cannot run out, which is exactly why it is the part that
 * accumulates into something.
 *
 * @param goods       the ledger word for what is handed over, or empty for none
 * @param goodsAmount how many of it
 */
public record Reward(int coin, String goods, int goodsAmount, int standing) {

    public static final Reward NOTHING = new Reward(0, "", 0, 0);

    public Reward {
        coin = Math.max(0, coin);
        goods = goods == null ? "" : goods;
        goodsAmount = goods.isEmpty() ? 0 : Math.max(0, goodsAmount);
        standing = Math.max(0, standing);
    }

    /** Coin only, which is what most of the board offers. */
    public static Reward of(int coin, int standing) {
        return new Reward(coin, "", 0, standing);
    }

    public boolean hasGoods() {
        return !goods.isEmpty() && goodsAmount > 0;
    }

    /** Whether this is worth anything at all — a town that can pay nothing pays nothing. */
    public boolean isEmpty() {
        return coin <= 0 && !hasGoods() && standing <= 0;
    }
}
