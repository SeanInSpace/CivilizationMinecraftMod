package com.keystone.preview;

import com.keystone.KeystoneComponents;
import com.keystone.KeystoneItems;
import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Shows where a blueprint would land, as an outline of sparks.
 *
 * <p>Drawn from the server with {@code sendParticles}, the same way the charter
 * draws settlement borders — a technique already proven in this codebase. That
 * choice matters: 26.2 moved world rendering to an extract-and-submit pipeline
 * ({@code SubmitCustomGeometryEvent}), and a translucent block-by-block ghost
 * written against an unfamiliar renderer is exactly the kind of code that cannot
 * be verified without a person watching a screen. An outline answers the
 * questions that actually matter before committing — where does it go, which way
 * is it facing, how big is it — with no client code at all.
 *
 * <p>The server raycasts on the player's behalf, so nothing has to be sent up
 * from the client to keep the preview following the crosshair.
 */
public final class PlacementPreview {

    /** How far ahead the wand will place. Beyond this the outline simply stops. */
    public static final double REACH = 24.0;

    /** Ticks between redraws — particles linger, so this need not be every tick. */
    private static final int INTERVAL = 4;

    /** Roughly one spark per this many blocks along an edge, and never fewer than 2. */
    private static final int SPACING = 2;

    private PlacementPreview() {
    }

    /** The block a wand would anchor to, or empty if the player is looking at nothing. */
    public static Optional<BlockPos> targetOf(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        // Anchor against the face that was hit, so a blueprint sits on top of the
        // ground rather than inside it.
        return Optional.of(block.getBlockPos().relative(block.getDirection()));
    }

    public static Optional<LoadedBlueprint> selectionOf(ServerPlayer player, ItemStack wand,
                                                        BlockPos at) {
        Identifier id = wand.get(KeystoneComponents.SELECTED.get());
        if (id == null || !(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return Blueprints.load(level, at, id,
                KeystoneComponents.rotationOf(wand.get(KeystoneComponents.ROTATION.get())),
                Mirror.NONE);
    }

    /** Called every player tick; cheap and silent unless a wand is lined up. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % INTERVAL != 0) {
            return;
        }
        ItemStack wand = heldWand(player);
        if (wand == null || wand.get(KeystoneComponents.SELECTED.get()) == null) {
            return;
        }
        Optional<BlockPos> target = targetOf(player);
        if (target.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<LoadedBlueprint> blueprint = selectionOf(player, wand, target.get());
        if (blueprint.isEmpty()) {
            return;
        }
        drawOutline(level, target.get(), blueprint.get().size());
    }

    public static ItemStack heldWand(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(KeystoneItems.BLUEPRINT_WAND.get())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.is(KeystoneItems.BLUEPRINT_WAND.get()) ? off : null;
    }

    /**
     * Traces the twelve edges of the bounding box.
     *
     * <p>Edges rather than a filled volume: a solid box of particles hides the
     * terrain underneath, which is the very thing you are trying to judge.
     */
    private static void drawOutline(ServerLevel level, BlockPos base, Vec3i size) {
        double x0 = base.getX();
        double y0 = base.getY();
        double z0 = base.getZ();
        double x1 = x0 + size.getX();
        double y1 = y0 + size.getY();
        double z1 = z0 + size.getZ();

        for (double x : new double[]{x0, x1}) {
            for (double z : new double[]{z0, z1}) {
                edge(level, x, y0, z, x, y1, z);
            }
        }
        for (double y : new double[]{y0, y1}) {
            edge(level, x0, y, z0, x1, y, z0);
            edge(level, x0, y, z1, x1, y, z1);
            edge(level, x0, y, z0, x0, y, z1);
            edge(level, x1, y, z0, x1, y, z1);
        }
    }

    private static void edge(ServerLevel level, double ax, double ay, double az,
                             double bx, double by, double bz) {
        double length = Math.max(Math.abs(bx - ax), Math.max(Math.abs(by - ay), Math.abs(bz - az)));
        int steps = Math.max(2, (int) Math.ceil(length / SPACING));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            level.sendParticles(ParticleTypes.END_ROD,
                    ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Turns the selection a quarter turn and reports the new facing. */
    public static Rotation rotate(ItemStack wand) {
        int quarters = Math.floorMod(
                (wand.getOrDefault(KeystoneComponents.ROTATION.get(), 0)) + 1, 4);
        wand.set(KeystoneComponents.ROTATION.get(), quarters);
        return KeystoneComponents.rotationOf(quarters);
    }
}
