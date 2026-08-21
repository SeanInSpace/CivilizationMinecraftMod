package com.kingdoms.neoforge.item;

import com.kingdoms.neoforge.KingdomsComponents;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.view.PersonEntityManager;
import com.kingdoms.neoforge.world.Excavation;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Marks out a box and sets a town's builders to clearing it.
 *
 * <p>Built to exercise the excavation properly. A build site is whatever shape
 * the terrain happens to be, which makes it a poor way to answer "does a crew of
 * six actually share out a hillside" — this lets you pick the hillside.
 *
 * <ul>
 *   <li>Click a block for the first corner.</li>
 *   <li>Sneak-click a block for the second.</li>
 *   <li>Click the air to set the nearest town digging.</li>
 *   <li>Sneak-click the air to call them off.</li>
 * </ul>
 *
 * <p>Only what genuinely stands in the way is taken: air and anything a block
 * can be placed into are skipped, and bedrock is left where it is. The order is
 * held in memory, so it does not survive a restart — deliberate, since a
 * clearance is something you stand and watch.
 */
public final class ExcavationStakeItem extends Item {

    /**
     * Largest box a stake will accept.
     *
     * <p>Not a technical limit — the yard would handle far more — but a slip of
     * the hand between two corners a thousand blocks apart should not silently
     * queue a job no crew will finish this year.
     */
    public static final long MAX_VOLUME = 32_768L;

    public ExcavationStakeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stake = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        boolean second = context.isSecondaryUseActive();
        stake.set(second ? KingdomsComponents.CORNER_B.get()
                : KingdomsComponents.CORNER_A.get(), clicked);

        player.sendSystemMessage(Component.literal(
                (second ? "Second corner" : "First corner") + " at " + clicked.toShortString()
                        + describe(stake)));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        SimWorld world = KingdomsMod.simulationFor(server);
        PersonEntityManager manager = KingdomsMod.managerFor(server);
        if (world == null || manager == null) {
            return InteractionResult.FAIL;
        }
        Settlement town = nearest(world, player.blockPosition());
        if (town == null) {
            player.sendSystemMessage(Component.literal("No settlement nearby to give orders to."));
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            boolean called = manager.cancelClear(town);
            player.sendSystemMessage(Component.literal(called
                    ? town.name() + " calls its builders off the clearance."
                    : town.name() + " was not clearing anything."));
            return InteractionResult.SUCCESS;
        }

        ItemStack stake = player.getItemInHand(hand);
        BlockPos a = stake.get(KingdomsComponents.CORNER_A.get());
        BlockPos b = stake.get(KingdomsComponents.CORNER_B.get());
        if (a == null || b == null) {
            player.sendSystemMessage(Component.literal(
                    "Mark two corners first: click a block, then sneak-click another."));
            return InteractionResult.FAIL;
        }
        long volume = volumeOf(a, b);
        if (volume > MAX_VOLUME) {
            player.sendSystemMessage(Component.literal(
                    "That is " + volume + " blocks. " + MAX_VOLUME + " is the limit."));
            return InteractionResult.FAIL;
        }

        int ordered = manager.orderClear(town, a, b);
        if (ordered == 0) {
            player.sendSystemMessage(Component.literal("Nothing in that box needs digging."));
            return InteractionResult.FAIL;
        }
        Excavation yard = manager.clearOrder(town);
        player.sendSystemMessage(Component.literal(
                town.name() + " sets " + builderCount(town) + " builder(s) to clear "
                        + ordered + " blocks."
                        + (yard == null ? "" : " Sneak-click the air to call them off.")));
        return InteractionResult.SUCCESS;
    }

    private static String describe(ItemStack stake) {
        BlockPos a = stake.get(KingdomsComponents.CORNER_A.get());
        BlockPos b = stake.get(KingdomsComponents.CORNER_B.get());
        if (a == null || b == null) {
            return "";
        }
        return " — box " + (Math.abs(a.getX() - b.getX()) + 1)
                + "x" + (Math.abs(a.getY() - b.getY()) + 1)
                + "x" + (Math.abs(a.getZ() - b.getZ()) + 1)
                + ". Right-click the air to set the town digging.";
    }

    private static long volumeOf(BlockPos a, BlockPos b) {
        return (long) (Math.abs(a.getX() - b.getX()) + 1)
                * (Math.abs(a.getY() - b.getY()) + 1)
                * (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    private static int builderCount(Settlement town) {
        return (int) town.residents().stream()
                .filter(person -> person.profession() == Profession.BUILDER)
                .count();
    }

    private static Settlement nearest(SimWorld world, BlockPos from) {
        SimPos here = new SimPos(from.getX(), from.getY(), from.getZ());
        Settlement best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                long distance = settlement.centre().horizontalDistanceSq(here);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = settlement;
                }
            }
        }
        return best;
    }
}
