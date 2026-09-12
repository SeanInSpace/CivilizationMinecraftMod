package com.civilization.neoforge.block;

import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.bridge.NeoForgeWorldBridge;
import com.civilization.neoforge.net.QuestActionPayload;
import com.civilization.neoforge.net.QuestBoardPayload;
import com.civilization.neoforge.trade.Currency;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.quest.QuestPlanner;
import com.civilization.sim.settlement.Resources;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The town's noticeboard: what it is asking for, and what it remembers being
 * done for it.
 *
 * <p>It used to read out the tally of deeds and a wish list of stock levels into
 * chat, which was the right shape and the wrong thing — a list of numbers a
 * player could do nothing about. The board now carries actual asks, generated
 * off the same readings the planners act on: a town short of timber is a town
 * whose builders have stopped, and the notice for it says so.
 *
 * <p>Right-click opens a screen rather than talking, so the board has room for
 * a reason, a reward and a button. See {@link QuestBoardPayload}.
 *
 * <p><strong>The board is a claim and the settlement is the record.</strong>
 * Nothing a client sends is believed: the quest is looked up again here, its
 * state and its owner re-checked, and the player's distance from the post
 * measured. The board is then sent back whether or not anything happened,
 * because the interesting case is that it did.
 */
public class QuestBoardBlock extends BuildingPostBlock {

    /**
     * How far a player may stand from the board and still press its buttons,
     * squared.
     *
     * <p>Eight blocks, the same as the market stall and for the same reason: a
     * screen is a claim by a client and can be kept open across half a kingdom.
     */
    private static final double REACH_SQ = 64.0;

    public QuestBoardBlock(String role, String explains, Properties properties) {
        super(role, explains, properties);
    }

    /**
     * Blank on purpose.
     *
     * <p>A post whose report is empty gets no chat line at all — see
     * {@code BuildingPostBlock.useWithoutItem} — which is what a post that opens
     * a screen wants. The town's distress is still announced above it, because
     * that line outlives the window.
     */
    @Override
    protected String report(Settlement settlement) {
        return "";
    }

    @Override
    protected void extraReport(Player player, Settlement settlement, BlockPos pos) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        long step = stepOf(server);
        // An empty board still opens. "They are asking for nothing" is a real
        // thing for a town to be saying, and the screen says it.
        PacketDistributor.sendToPlayer(server, QuestBoardPayload.of(
                settlement, pos, server.getUUID(), step, true));
    }

    /**
     * One button, pressed at the board a player says they are standing at.
     *
     * <p>Every refusal says something. A board that does nothing when a button
     * is pressed is indistinguishable from a broken one, and the screen holds no
     * checks of its own — a player four steps too far back would otherwise press
     * Accept and get silence.
     */
    public static void takeAction(ServerPlayer player, BlockPos post, String questId,
                                  String action) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (post.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > REACH_SQ) {
            said(player, "You have stepped away from the board.");
            return;
        }
        if (!(level.getBlockState(post).getBlock() instanceof QuestBoardBlock)) {
            said(player, "There is no board there any more.");
            return;
        }
        SimWorld world = CivilizationMod.simulationFor(level);
        if (world == null) {
            return;
        }
        SimPos at = NeoForgeWorldBridge.toSimPos(post);
        Settlement settlement = owningSettlement(world, at);
        if (settlement == null) {
            said(player, "That board belongs to nobody.");
            return;
        }
        if (taskAt(settlement, post) != null) {
            said(player, "They are rebuilding the board. Come back when it is up.");
            return;
        }
        apply(player, settlement, questId, action, world.stepsElapsed());
        PacketDistributor.sendToPlayer(player, QuestBoardPayload.of(
                settlement, post, player.getUUID(), world.stepsElapsed(), false));
    }

    /** The three things a button can mean, and nothing else. */
    private static void apply(ServerPlayer player, Settlement settlement, String questId,
                              String action, long step) {
        switch (action) {
            case QuestActionPayload.ACCEPT -> {
                if (QuestPlanner.accept(settlement, player.getUUID(), questId, step)) {
                    said(player, "Taken. They are counting on you now.");
                } else {
                    said(player, "Somebody else has that one.");
                }
            }
            case QuestActionPayload.ABANDON -> {
                if (QuestPlanner.abandon(settlement, player.getUUID(), questId, step)) {
                    said(player, "Given back. Whatever you had done toward it is lost.");
                } else {
                    said(player, "That is not yours to give back.");
                }
            }
            case QuestActionPayload.CLAIM ->
                    payOut(player, settlement,
                            QuestPlanner.claim(settlement, player.getUUID(), questId, step));
            // An action word this build has never heard of. Refused rather than
            // guessed at: the one thing worse than a button that does nothing is
            // a button that does something else.
            default -> said(player, "Nobody here knows what you are asking.");
        }
    }

    /**
     * Hands over what the town owes, in things a player can hold.
     *
     * <p><strong>One money, and deliberately so.</strong> The mod has a single
     * currency — the treasury — and {@link Currency} is the shape it takes in a
     * hand, on the rule that every coin a town pays out came out of its own
     * books. A separate quest token would be a second currency that no counter
     * in the world accepts, which is worse than no reward at all. So the board
     * pays out of the same purse the stall does, and a coin earned here buys
     * grain there.
     */
    private static void payOut(ServerPlayer player, Settlement settlement,
                               QuestPlanner.Payout paid) {
        if (!paid.any()) {
            said(player, "There is nothing here to collect.");
            return;
        }
        giveMany(player, Currency.stack(), paid.coin());
        if (paid.goodsAmount() > 0) {
            String itemId = Resources.itemFor(paid.goods());
            if (itemId != null) {
                giveMany(player, stackOf(itemId, 1), paid.goodsAmount());
            }
        }
        player.sendSystemMessage(Component.literal("  " + settlement.name()
                        + " is in your debt — standing " + paid.standingNow() + ".")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * A ledger word's canonical item, as a stack.
     *
     * <p>An id the registry has never heard of hands over nothing rather than
     * throwing: the dictionary in {@code :common} is free to name something a
     * datapack has since removed, and a reward that crashes the server is not a
     * reward.
     */
    private static ItemStack stackOf(String itemId, int count) {
        net.minecraft.resources.Identifier id =
                net.minecraft.resources.Identifier.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> new ItemStack(item, count))
                .orElse(ItemStack.EMPTY);
    }

    /** Which step the simulation is on, for the deadline a board shows. */
    private static long stepOf(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        SimWorld world = CivilizationMod.simulationFor(level);
        return world == null ? 0 : world.stepsElapsed();
    }

    /**
     * Hands over a count of something, a stack at a time.
     *
     * <p>A reward can run past sixty-four — a sworn friend's delivery pays a
     * hundred and twenty coin — and an {@code ItemStack} built with a count
     * over its own limit is not a big stack, it is a broken one. So the count is
     * the number and the stack is the unit.
     *
     * @param unit an example of the item, whose count is ignored
     */
    private static void giveMany(Player player, ItemStack unit, int count) {
        if (unit.isEmpty() || count <= 0) {
            return;
        }
        int per = Math.max(1, unit.getMaxStackSize());
        for (int left = count; left > 0; left -= per) {
            ItemStack lot = unit.copy();
            lot.setCount(Math.min(per, left));
            if (!player.getInventory().add(lot)) {
                player.drop(lot, false);
            }
        }
    }

    private static void said(ServerPlayer player, String what) {
        player.sendSystemMessage(Component.literal("  " + what)
                .withStyle(ChatFormatting.GRAY));
    }
}
