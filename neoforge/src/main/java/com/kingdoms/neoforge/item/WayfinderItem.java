package com.kingdoms.neoforge.item;

import com.kingdoms.neoforge.world.SiteDirectory;
import com.kingdoms.sim.geom.SimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * A compass that points at the nearest town instead of at spawn.
 *
 * <p>Millénaire shipped an amulet that did this, and it is the other half of
 * why finding a village there was never a chore: the join message tells you a
 * town is eight hundred blocks northeast, and the needle keeps telling you so
 * while you walk, through the forest where you lost the heading.
 *
 * <p><strong>No custom rendering.</strong> The needle is vanilla's, driven by
 * the vanilla {@code minecraft:lodestone_tracker} data component and the
 * {@code minecraft:compass} model property with {@code target: lodestone} — the
 * item definition in {@code assets/kingdoms/items/wayfinder.json} dispatches to
 * the same thirty-two compass models the vanilla compass uses. Verified against
 * the 26.2 sources rather than remembered: {@code CompassAngleState.CompassTarget
 * .LODESTONE} reads {@code itemStack.get(DataComponents.LODESTONE_TRACKER)} and
 * takes {@code tracker.target()}, with no interest in whether there is a
 * lodestone block there or in the tracker's {@code tracked} flag.
 *
 * <p>Which is why the tracker is written with {@code tracked = false}, and that
 * is load-bearing rather than tidy. {@link LodestoneTracker#tick} — run from
 * {@code CompassItem.inventoryTick} — clears the target of a <em>tracked</em>
 * compass the moment the block it names is not a lodestone POI. A town square is
 * not a lodestone. A tracked wayfinder would blank itself on the first tick in
 * a player's inventory; an untracked one is left alone, which is exactly the
 * behavior wanted. This item also does not extend {@code CompassItem} and so
 * never runs that tick at all, but the flag is set correctly regardless: a stack
 * transmuted or copied into a real compass must not evaporate.
 */
public final class WayfinderItem extends Item {

    public WayfinderItem(Properties properties) {
        super(properties);
    }

    /** A targeted wayfinder glows, the way a lodestone compass does. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER) || super.isFoil(stack);
    }

    /**
     * Points the needle at the next town out.
     *
     * <p>Untargeted, it takes the nearest. Targeted, it advances — so a player
     * holding one and clicking walks down the same short list the join message
     * printed, rather than being stuck on a town they have already visited.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide() || !(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        List<SiteDirectory.Near> near = SiteDirectory.near(server,
                new SimPos(player.getBlockX(), player.getBlockY(), player.getBlockZ()));
        if (near.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "The needle turns and turns — no settlement within "
                            + SiteDirectory.EARSHOT + " blocks."));
            return InteractionResult.FAIL;
        }
        SiteDirectory.Near chosen = near.get(nextAfter(held, near));
        aimAt(held, server, chosen.at());
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal(SiteDirectory.line(
                chosen.what(), chosen.standing(),
                chosen.at().x() - player.getBlockX(),
                chosen.at().z() - player.getBlockZ()).trim()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Writes the needle's target onto the stack.
     *
     * <p>Also used when a player is handed one on first join, which is why it is
     * not private: the wayfinder that arrives in the hotbar already points
     * somewhere, and one that has to be clicked before it does anything is a
     * compass that looks broken.
     */
    public static void aimAt(ItemStack stack, ServerLevel level, SimPos at) {
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(GlobalPos.of(level.dimension(),
                        new BlockPos(at.x(), at.y(), at.z()))),
                false));
    }

    /**
     * Which entry of the list to point at next.
     *
     * <p>The current target is matched by position, because that is all the
     * component stores. A target that has fallen off the list — the player
     * walked out of {@link SiteDirectory#EARSHOT} of it, or it was never on the
     * list — starts the walk over at the nearest, which is the useful answer
     * rather than the pedantic one.
     */
    private static int nextAfter(ItemStack stack, List<SiteDirectory.Near> near) {
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker == null || tracker.target().isEmpty()) {
            return 0;
        }
        BlockPos aimed = tracker.target().get().pos();
        for (int at = 0; at < near.size(); at++) {
            SimPos center = near.get(at).at();
            if (center.x() == aimed.getX() && center.z() == aimed.getZ()) {
                return (at + 1) % near.size();
            }
        }
        return 0;
    }
}
