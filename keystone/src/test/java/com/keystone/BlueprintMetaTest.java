package com.keystone;

import com.keystone.blueprint.Blueprint;
import com.keystone.blueprint.BlueprintNbt;
import com.keystone.blueprint.StructurizeNbt;
import com.keystone.blueprint.Transforms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three things a structure file says that its blocks cannot.
 *
 * <p>The anchor, the front, and the crop count. None of them is visible in a
 * building — a file with the wrong anchor looks exactly like a file with the
 * right one, right up until the town builds it a block and a half off its own
 * plot — so the round trip is the only place the mistake can be caught.
 *
 * <p>No registries here, which is why every structure below is empty. That is
 * not a shortcut: the palette is the only part of the format that needs a block
 * registry, and the fields under test are stored beside it rather than in it.
 * Blocks going round intact is {@code StructurizeNbtTest}'s and the in-world
 * tests' business.
 */
class BlueprintMetaTest {

    private static Vec3i size(int x, int y, int z) {
        return new Vec3i(x, y, z);
    }

    private static Blueprint roundTrip(Blueprint blueprint) {
        // Through the tag rather than through a file: the compression and the
        // stream are java.io's problem, and everything this file is about
        // happens in the tag.
        return BlueprintNbt.read(BlueprintNbt.write(blueprint), null);
    }

    // --- the round trip ---

    @Test
    void anAnchorSurvivesBeingWrittenAndReadBack() {
        Blueprint stated = new Blueprint(size(9, 5, 7), List.of(),
                new BlockPos(2, 0, 6), Blueprint.Meta.NONE);

        assertEquals(new BlockPos(2, 0, 6), roundTrip(stated).anchor(),
                "the cell the building lines up by has to come back, or every"
                        + " re-saved blueprint quietly moves onto a different plot");
    }

    @Test
    void aFrontAndACropCountSurviveToo() {
        Blueprint stated = new Blueprint(size(11, 6, 11), List.of(),
                new BlockPos(5, 0, 5), new Blueprint.Meta(3, 71));

        Blueprint back = roundTrip(stated);

        assertEquals(3, back.facing(), "which way the door faces");
        assertEquals(71, back.meta().cropBlocks(), "how many crops the author counted");
    }

    @Test
    void aStructureThatSaysNothingComesBackSayingNothing() {
        Blueprint plain = new Blueprint(size(7, 4, 7), List.of());

        Blueprint back = roundTrip(plain);

        assertEquals(Blueprint.defaultAnchor(size(7, 4, 7)), back.anchor(),
                "the middle of its own floor");
        assertEquals(0, back.facing(), "southward, which is what nothing said means");
        assertFalse(back.meta().hasCropCount(), "nobody counted, which is not the same as none");
    }

    @Test
    void aVanillaFileWithNoPocketOfOursReadsAsSayingNothing() {
        // What a structure block writes: a size, a palette, blocks, and nothing
        // else. Every file authored before this feature existed looks like this,
        // and every one of them must still load.
        CompoundTag vanilla = new CompoundTag();
        ListTag dimensions = new ListTag();
        dimensions.add(IntTag.valueOf(5));
        dimensions.add(IntTag.valueOf(3));
        dimensions.add(IntTag.valueOf(5));
        vanilla.put("size", dimensions);
        vanilla.put("palette", new ListTag());
        vanilla.put("blocks", new ListTag());

        Blueprint read = BlueprintNbt.read(vanilla, null);

        assertEquals(new BlockPos(2, 0, 2), read.anchor());
        assertEquals(Blueprint.Meta.NONE, read.meta());
    }

    @Test
    void anAnchorOutsideTheStructureIsDroppedRatherThanHonored() {
        // A file naming a cell it does not contain would line the building up by
        // nothing at all, and move it by however far outside the cell lay.
        CompoundTag tag = BlueprintNbt.write(new Blueprint(size(5, 3, 5), List.of()));
        CompoundTag ours = tag.getCompoundOrEmpty("keystone");
        ListTag outside = new ListTag();
        outside.add(IntTag.valueOf(40));
        outside.add(IntTag.valueOf(0));
        outside.add(IntTag.valueOf(40));
        ours.put("anchor", outside);
        tag.put("keystone", ours);

        assertEquals(new BlockPos(2, 0, 2), BlueprintNbt.read(tag, null).anchor(),
                "centered, which is the honest fallback");
    }

    // --- turning ---

    @Test
    void theFrontTurnsWithTheWalls() {
        Blueprint facingSouth = new Blueprint(size(7, 4, 7), List.of(),
                new BlockPos(3, 0, 3), new Blueprint.Meta(0, Blueprint.UNCOUNTED));

        assertEquals(1, Transforms.apply(facingSouth, Rotation.CLOCKWISE_90, Mirror.NONE).facing());
        assertEquals(2, Transforms.apply(facingSouth, Rotation.CLOCKWISE_180, Mirror.NONE).facing());
        assertEquals(3, Transforms.apply(facingSouth,
                Rotation.COUNTERCLOCKWISE_90, Mirror.NONE).facing());
    }

    @Test
    void aFrontAlreadyTurnedKeepsGoingRoundRatherThanResetting() {
        Blueprint facingWest = new Blueprint(size(7, 4, 7), List.of(),
                new BlockPos(3, 0, 3), new Blueprint.Meta(1, Blueprint.UNCOUNTED));

        assertEquals(2, Transforms.apply(facingWest, Rotation.CLOCKWISE_90, Mirror.NONE).facing());
        assertEquals(0, Transforms.apply(facingWest,
                Rotation.COUNTERCLOCKWISE_90, Mirror.NONE).facing(),
                "three quarters round from west is south again");
    }

    @Test
    void theTurnFromOneFrontToAnotherIsTheDifferenceBetweenThem() {
        // What lets a file authored facing any way at all land with its door on
        // the street: the consumer says where the front should end up, and this
        // works out the rest.
        assertEquals(Rotation.NONE, Transforms.between(2, 2));
        assertEquals(Rotation.CLOCKWISE_90, Transforms.between(0, 1));
        assertEquals(Rotation.CLOCKWISE_180, Transforms.between(1, 3));
        assertEquals(Rotation.COUNTERCLOCKWISE_90, Transforms.between(1, 0));
        assertEquals(Rotation.CLOCKWISE_90, Transforms.between(3, 0),
                "and it wraps, rather than turning three quarters the long way");
    }

    // --- telling the two formats apart ---

    @Test
    void aStructurizeTagIsRecognizedByItsThreeSeparateSizes() {
        CompoundTag structurize = new CompoundTag();
        structurize.putShort("size_x", (short) 11);
        structurize.putShort("size_y", (short) 24);
        structurize.putShort("size_z", (short) 11);

        assertTrue(StructurizeNbt.looksStructurize(structurize));
    }

    @Test
    void aVanillaTagIsNot() {
        // Both formats have a "palette" and a "blocks", with entirely different
        // contents in each. The size is the one field that cannot be mistaken.
        CompoundTag vanilla = new CompoundTag();
        ListTag dimensions = new ListTag();
        dimensions.add(IntTag.valueOf(11));
        dimensions.add(IntTag.valueOf(24));
        dimensions.add(IntTag.valueOf(11));
        vanilla.put("size", dimensions);
        vanilla.put("palette", new ListTag());
        vanilla.put("blocks", new ListTag());

        assertFalse(StructurizeNbt.looksStructurize(vanilla));
        assertFalse(StructurizeNbt.looksStructurize(new CompoundTag()));
        assertFalse(StructurizeNbt.looksStructurize(null));
    }
}
