package com.kingdoms.neoforge.block;

import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.net.TownOverviewPayload.Distress;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.settlement.BuildPlanner;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The post that marks what a building is for.
 *
 * <p>Every building the town raises gets one, standing on its floor — the same
 * idea colony mods use, where a building is a thing you walk up to and read
 * rather than a name in a menu. Right-click and it tells you which town it
 * belongs to, what it is, and whatever that building actually keeps track of.
 *
 * <p>Posts with orders to give, like the lumber camp, subclass this and add them;
 * see {@link LumberCampBlock}. A plain post is an informational sign that happens
 * to be structural.
 */
public class BuildingPostBlock extends Block {

    private final String role;
    private final String explains;

    /**
     * @param role     what shows up in the report, e.g. {@code "Granary"}
     * @param explains one line on what the building does for the town
     */
    public BuildingPostBlock(String role, String explains, Properties properties) {
        super(properties);
        this.role = role;
        this.explains = explains;
    }

    public String role() {
        return role;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        SimWorld world = KingdomsMod.simulationFor(serverLevel);
        if (world == null) {
            return InteractionResult.FAIL;
        }

        Settlement settlement = owningSettlement(world, NeoForgeWorldBridge.toSimPos(pos));
        if (settlement == null) {
            player.sendSystemMessage(Component.literal(
                    role + " — belongs to no settlement. Found one nearby first."));
            return InteractionResult.SUCCESS;
        }

        // Crisis leads, whatever this post is for, and it has to lead from here.
        // The under-construction branch below returns outright, and a blank report
        // suppresses its own header — so anything said further down would be
        // missing from an unfinished site and from every post that opens a screen
        // instead of talking, which between them is most of a young town.
        announceDistress(player, settlement);

        // A post standing on an unfinished site answers for the site, not for
        // the town: what is going up here, and how far along it is. This is why
        // the post is the first block laid — from the very start of the job
        // there is something to walk up to and ask.
        BuildTask underWay = taskAt(settlement, pos);
        if (underWay != null) {
            int percent = (int) Math.round(underWay.completionFraction() * 100);
            player.sendSystemMessage(Component.literal(
                    role + " of " + settlement.name() + " — under construction, "
                            + percent + "% (" + underWay.workDone() + "/"
                            + underWay.planWork() + " blocks)"));
            return InteractionResult.SUCCESS;
        }

        // A post whose report is blank speaks for itself some other way — the
        // town hall opens a screen — so it gets no chat line at all rather than
        // an empty one underneath its own window.
        String summary = report(settlement);
        if (!summary.isBlank()) {
            player.sendSystemMessage(Component.literal(
                    role + " of " + settlement.name() + " — " + explains));
            player.sendSystemMessage(Component.literal(summary));
        }
        extraReport(player, settlement);
        return InteractionResult.SUCCESS;
    }

    /**
     * The town's condition, said before this post says anything of its own.
     *
     * <p>The spiral that motivated this was silent: a town starved to death while
     * every post it owned went on reporting population and stock, and the player
     * only worked out what had happened afterwards. A town in trouble now says so
     * on whatever the player happens to walk up to.
     *
     * <p>Reaches every post that inherits {@link #useWithoutItem}. It does not
     * reach {@link MineBlock} or {@link LumberCampBlock}, which replace that
     * method wholesale because they are controls rather than reports — a knob
     * that shouts before turning reads as a malfunction. This is
     * {@code protected static} so both can add the one line if that judgement
     * turns out to be wrong in play.
     *
     * <p>The hall does hear it twice, in chat here and again on the band of the
     * screen it opens. That is deliberate: the screen is momentary and the chat
     * line is not, so closing the window does not close the warning with it.
     */
    protected static void announceDistress(Player player, Settlement settlement) {
        Distress distress = Distress.of(settlement);
        if (distress.silent()) {
            return;
        }
        player.sendSystemMessage(Component.literal(settlement.name() + " is "
                        + distress.word() + " — " + distress.fact() + ".")
                .withStyle(alarmStyle(distress.alarm())));
        if (distress.showsRemedy()) {
            player.sendSystemMessage(Component.literal("  " + distress.remedy())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Loud in proportion to the trouble; red and bold is reserved for deaths coming. */
    private static ChatFormatting[] alarmStyle(int alarm) {
        return switch (alarm) {
            case Distress.ALARM_DYING ->
                    new ChatFormatting[] {ChatFormatting.RED, ChatFormatting.BOLD};
            case Distress.ALARM_FAILING -> new ChatFormatting[] {ChatFormatting.GOLD};
            default -> new ChatFormatting[] {ChatFormatting.YELLOW};
        };
    }

    /** The build this post stands on, if its site is still under construction. */
    private static BuildTask taskAt(Settlement settlement, BlockPos pos) {
        for (BuildTask task : settlement.buildQueue()) {
            SimPos site = task.site();
            int reach = BuildPlanner.plotSpanOf(
                    task.blueprintId(), settlement.catalogue()) / 2 + 1;
            if (Math.abs(pos.getX() - site.x()) <= reach
                    && Math.abs(pos.getZ() - site.z()) <= reach
                    && Math.abs(pos.getY() - site.y()) <= 8) {
                return task;
            }
        }
        return null;
    }

    /** What this particular post is worth saying beyond its name. Overridable. */
    protected String report(Settlement settlement) {
        return "Population " + settlement.population()
                + ", " + settlement.buildings().size() + " buildings"
                + ", food " + settlement.foodStock()
                + ", timber " + settlement.woodStock()
                + ", stone " + settlement.stoneStock();
    }

    /** Anything this post wants to say beyond the one-line summary. */
    protected void extraReport(Player player, Settlement settlement) {
    }

    /**
     * The town this post stands in, or null if it stands outside every claim.
     *
     * <p>Nearest centre wins, but only when the post is actually inside that
     * town's borders plus a little slack — otherwise a post dropped in the wild
     * would report on a settlement half a world away.
     */
    protected static Settlement owningSettlement(SimWorld world, SimPos pos) {
        Settlement nearest = null;
        long best = Long.MAX_VALUE;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                long distance = settlement.centre().horizontalDistanceSq(pos);
                if (distance < best) {
                    best = distance;
                    nearest = settlement;
                }
            }
        }
        if (nearest != null) {
            long limit = (long) nearest.claimRadius() + 32L;
            if (best <= limit * limit) {
                return nearest;
            }
        }
        return null;
    }
}
