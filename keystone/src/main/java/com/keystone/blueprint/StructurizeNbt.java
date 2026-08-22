package com.keystone.blueprint;

import com.keystone.KeystoneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads Structurize's {@code .blueprint} format — the one MineColonies and its
 * schematic packs are authored in.
 *
 * <p>Worth having because of what is on the other side of it: hundreds of
 * hand-built, professionally styled buildings that a consuming mod can raise
 * without anybody drawing them again.
 *
 * <p>It is <em>not</em> vanilla structure NBT, and the differences are the whole
 * of this class:
 *
 * <ul>
 *   <li><strong>Dense, not sparse.</strong> Vanilla lists the blocks it has,
 *       each with a position. Structurize stores every cell of the bounding box
 *       in one array, in {@code y → z → x} order, position implied by index.</li>
 *   <li><strong>Two palette indices to an int.</strong> The cells are shorts,
 *       packed two at a time into an {@code int[]} — high half first. An odd
 *       cell count leaves one short of padding at the end, which must not be
 *       read as a block.</li>
 *   <li><strong>Dimensions are shorts</strong> under {@code size_x/y/z}, not a
 *       list under {@code size}.</li>
 *   <li><strong>Block entities carry their own coordinates</strong> as short
 *       {@code x}/{@code y}/{@code z} inside each compound, rather than being
 *       attached to a block entry.</li>
 *   <li><strong>The palette is full of other people's mods</strong>, including
 *       Structurize's own instruction blocks. See {@link BlockSubstitutions}.</li>
 * </ul>
 *
 * <p>Never throws on damaged content, matching {@link BlueprintNbt}: a bad cell
 * is dropped and a bad palette entry becomes air, so one broken file in a
 * community pack cannot take the loader down with it.
 */
public final class StructurizeNbt {

    private static final String VERSION = "version";
    private static final String SIZE_X = "size_x";
    private static final String SIZE_Y = "size_y";
    private static final String SIZE_Z = "size_z";
    private static final String PALETTE = "palette";
    private static final String BLOCKS = "blocks";
    private static final String TILE_ENTITIES = "tile_entities";
    private static final String NAME = "Name";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";

    /** The only format version there has ever been; anything else is read hopefully. */
    private static final byte KNOWN_VERSION = 1;

    private StructurizeNbt() {
    }

    // --- the pure arithmetic, which is where the bugs would be ---

    /**
     * Unpacks the cell array: two shorts to an int, high half first.
     *
     * @param packed the stored {@code blocks} array
     * @param cells  {@code sizeX * sizeY * sizeZ} — the count that matters, since
     *               an odd one leaves a trailing padding short that is not a cell
     * @return one palette index per cell, unsigned
     */
    public static int[] unpackIndices(int[] packed, int cells) {
        int[] out = new int[Math.max(0, cells)];
        for (int i = 0; i < out.length; i++) {
            int word = packed[i / 2];
            // Unsigned: the format writes shorts, but a palette may hold more
            // than 32767 entries and sign-extending one would index backwards.
            out[i] = (i % 2 == 0) ? (word >>> 16) & 0xFFFF : word & 0xFFFF;
        }
        return out;
    }

    /** Where a cell lives in the flat array. The format walks y, then z, then x. */
    public static int cellIndex(int x, int y, int z, int sizeX, int sizeZ) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /** Whether the array is long enough to hold every cell. */
    public static boolean holdsEveryCell(int[] packed, int cells) {
        return packed.length * 2 >= cells;
    }

    // --- reading ---

    /** Reads a {@code .blueprint} file, gzipped (as Structurize writes them) or plain. */
    public static Blueprint readFile(Path file, HolderGetter<Block> blocks) throws IOException {
        return read(readTag(file), blocks);
    }

    public static Blueprint readStream(InputStream in, HolderGetter<Block> blocks)
            throws IOException {
        return read(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()), blocks);
    }

    private static CompoundTag readTag(Path file) throws IOException {
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException compressed) {
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(file)))) {
                return NbtIo.read(in);
            } catch (IOException plain) {
                plain.addSuppressed(compressed);
                throw plain;
            }
        }
    }

    /** Decodes a {@code .blueprint} tag into the common blueprint model. */
    public static Blueprint read(CompoundTag tag, HolderGetter<Block> blocks) {
        byte version = tag.getByteOr(VERSION, KNOWN_VERSION);
        if (version != KNOWN_VERSION) {
            KeystoneMod.LOG.warn("Blueprint format version {} is not {}; reading anyway",
                    version, KNOWN_VERSION);
        }

        int sizeX = tag.getShortOr(SIZE_X, (short) 0);
        int sizeY = tag.getShortOr(SIZE_Y, (short) 0);
        int sizeZ = tag.getShortOr(SIZE_Z, (short) 0);
        int cells = sizeX * sizeY * sizeZ;
        if (cells <= 0) {
            KeystoneMod.LOG.warn("Blueprint has no volume ({}x{}x{})", sizeX, sizeY, sizeZ);
            return new Blueprint(new Vec3i(Math.max(sizeX, 0), Math.max(sizeY, 0),
                    Math.max(sizeZ, 0)), List.of());
        }

        int[] packed = tag.getIntArray(BLOCKS).orElseGet(() -> new int[0]);
        if (!holdsEveryCell(packed, cells)) {
            KeystoneMod.LOG.error("Blueprint block array holds {} cells, needs {} — refusing it",
                    packed.length * 2, cells);
            return new Blueprint(new Vec3i(sizeX, sizeY, sizeZ), List.of());
        }
        int[] indices = unpackIndices(packed, cells);

        Palette palette = readPalette(tag, blocks);
        Map<BlockPos, CompoundTag> blockEntities = readBlockEntities(tag);

        List<Blueprint.BlueprintBlock> out = new ArrayList<>();
        int dropped = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = indices[cellIndex(x, y, z, sizeX, sizeZ)];
                    if (index >= palette.states.length) {
                        dropped++;
                        continue;
                    }
                    BlockState state = palette.states[index];
                    if (state == null || state.isAir()) {
                        continue;   // empty, or a "leave this alone" marker
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    // Block-entity data belongs to the block that was authored
                    // here. Once a block has been substituted it is a different
                    // block, and another mod's payload on it means nothing.
                    CompoundTag data = palette.native_[index] ? blockEntities.get(pos) : null;
                    out.add(new Blueprint.BlueprintBlock(pos, state, data));
                }
            }
        }
        if (dropped > 0) {
            KeystoneMod.LOG.warn("Dropped {} cell(s) with an out-of-range palette index", dropped);
        }
        palette.report();
        return new Blueprint(new Vec3i(sizeX, sizeY, sizeZ), out);
    }

    /** The decoded palette: a state per entry, and whether it was ours to begin with. */
    private record Palette(BlockState[] states, boolean[] native_, Map<String, String> swapped) {

        void report() {
            if (swapped.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            swapped.forEach((from, to) -> sb.append("\n    ").append(from)
                    .append(" -> ").append(to == null ? "(left empty)" : to));
            KeystoneMod.LOG.info("Blueprint used {} block(s) this game does not have:{}",
                    swapped.size(), sb);
        }
    }

    private static Palette readPalette(CompoundTag tag, HolderGetter<Block> blocks) {
        ListTag entries = tag.getListOrEmpty(PALETTE);
        BlockState[] states = new BlockState[entries.size()];
        boolean[] native_ = new boolean[entries.size()];
        // Sorted so the log reads the same way twice for the same file.
        Map<String, String> swapped = new TreeMap<>();

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            String name = entry.getStringOr(NAME, "");

            if (isRegistered(blocks, name)) {
                states[i] = readState(blocks, entry);
                native_[i] = true;
                continue;
            }

            String replacement = BlockSubstitutions.substituteFor(name);
            swapped.put(name, replacement);
            if (replacement == BlockSubstitutions.SKIP) {
                states[i] = null;
                continue;
            }
            // Keep the properties. A substituted stair that forgot its facing
            // would turn every roof in an imported pack into a staircase to
            // nowhere; vanilla's reader ignores properties the new block has
            // no place for, so this is safe as well as worthwhile.
            CompoundTag rewritten = entry.copy();
            rewritten.putString(NAME, replacement);
            states[i] = readState(blocks, rewritten);
        }
        return new Palette(states, native_, swapped);
    }

    private static BlockState readState(HolderGetter<Block> blocks, CompoundTag entry) {
        try {
            return NbtUtils.readBlockState(blocks, entry);
        } catch (RuntimeException damaged) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    /** Whether this game actually has the block, as opposed to quietly reading air for it. */
    private static boolean isRegistered(HolderGetter<Block> blocks, String name) {
        if (name.isEmpty()) {
            return false;
        }
        try {
            return blocks.get(ResourceKey.create(Registries.BLOCK, Identifier.parse(name)))
                    .isPresent();
        } catch (RuntimeException unparseable) {
            return false;
        }
    }

    /**
     * Block entities, keyed by the position each one carries.
     *
     * <p>Unlike vanilla structures, where the data hangs off the block entry,
     * a {@code .blueprint} keeps one flat list and each compound holds its own
     * short {@code x}/{@code y}/{@code z} relative to the blueprint origin.
     */
    private static Map<BlockPos, CompoundTag> readBlockEntities(CompoundTag tag) {
        ListTag list = tag.getListOrEmpty(TILE_ENTITIES);
        Map<BlockPos, CompoundTag> out = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            if (entry.isEmpty()) {
                continue;
            }
            BlockPos pos = new BlockPos(
                    entry.getShortOr(X, (short) 0),
                    entry.getShortOr(Y, (short) 0),
                    entry.getShortOr(Z, (short) 0));
            // The coordinates were addressing; the placer supplies the real
            // ones, and leaving these in would tell the block entity it lives
            // a few blocks from wherever it was actually put.
            CompoundTag data = entry.copy();
            data.remove(X);
            data.remove(Y);
            data.remove(Z);
            out.put(pos, data);
        }
        return out;
    }
}
