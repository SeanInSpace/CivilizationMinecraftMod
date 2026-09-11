package com.keystone.blueprint;

import com.keystone.KeystoneMod;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the vanilla structure NBT format.
 *
 * <p>Deliberately parsed by hand rather than through {@code StructureTemplate}:
 * that class keeps its palettes private, and its only public reader
 * ({@code filterBlocks}) filters by one already-known block — neither of which
 * lets us enumerate a structure's contents. Parsing the tag ourselves also means
 * we choose the ordering, which is what makes course-by-course building possible.
 *
 * <p>Staying on the vanilla format is a deliberate compatibility choice: files
 * authored with structure blocks, shipped in datapacks, or exported by other
 * tools all load here unchanged.
 */
public final class BlueprintNbt {

    private static final String SIZE = "size";
    private static final String PALETTE = "palette";
    private static final String PALETTES = "palettes";
    private static final String BLOCKS = "blocks";
    private static final String ENTITIES = "entities";
    private static final String POS = "pos";
    private static final String STATE = "state";
    private static final String NBT = "nbt";
    private static final String DATA_VERSION = "DataVersion";

    /**
     * Where everything vanilla has no word for lives: one compound, under our
     * own name.
     *
     * <p>Deliberately a side pocket rather than new top-level keys. A file
     * written here still loads in a vanilla structure block, and a file written
     * by a structure block still loads here — it simply has no pocket, and every
     * field in it has a defined answer when absent. That is the whole
     * compatibility story, and it is worth the one extra level of nesting.
     */
    private static final String KEYSTONE = "keystone";
    private static final String ANCHOR = "anchor";
    private static final String FACING = "facing";
    private static final String CROP_BLOCKS = "crop_blocks";

    private BlueprintNbt() {
    }

    // --- reading ---

    /** Reads a structure file, gzipped (as vanilla writes them) or plain. */
    public static Blueprint readFile(Path file, HolderGetter<Block> blocks) throws IOException {
        return read(readTag(file), blocks);
    }

    /**
     * Reads a file in whichever of the two formats it turns out to be in.
     *
     * <p>By what is inside it, not by what it is called. An author who exports a
     * Structurize building and saves it as {@code cottage.nbt} has done nothing
     * wrong, and a loader that read the extension would hand them a
     * zero-by-zero structure and no explanation. The tag says which format it
     * is far more reliably than the filename does — see
     * {@link StructurizeNbt#looksStructurize}.
     */
    public static Blueprint readAnyFile(Path file, HolderGetter<Block> blocks) throws IOException {
        CompoundTag tag = readTag(file);
        return StructurizeNbt.looksStructurize(tag)
                ? StructurizeNbt.read(tag, blocks)
                : read(tag, blocks);
    }

    /** Reads a gzipped structure from an arbitrary stream, e.g. a datapack resource. */
    public static Blueprint readStream(InputStream in, HolderGetter<Block> blocks) throws IOException {
        return read(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()), blocks);
    }

    private static CompoundTag readTag(Path file) throws IOException {
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException compressed) {
            // Hand-made or tool-exported files are sometimes left uncompressed.
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(file)))) {
                return NbtIo.read(in);
            } catch (IOException plain) {
                plain.addSuppressed(compressed);
                throw plain;
            }
        }
    }

    /**
     * Decodes a structure tag. Never throws on damaged content: an unreadable
     * palette entry degrades to air and a malformed block is skipped, so one bad
     * file in a community pack cannot take the loader down with it.
     */
    public static Blueprint read(CompoundTag tag, HolderGetter<Block> blocks) {
        int[] size = triple(tag, SIZE);
        List<BlockState> palette = readPalette(tag, blocks);

        ListTag blockList = tag.getListOrEmpty(BLOCKS);
        List<Blueprint.BlueprintBlock> out = new ArrayList<>(blockList.size());
        int skipped = 0;
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag entry = blockList.getCompoundOrEmpty(i);
            int index = entry.getIntOr(STATE, -1);
            if (index < 0 || index >= palette.size()) {
                skipped++;
                continue;
            }
            int[] pos = triple(entry, POS);
            out.add(new Blueprint.BlueprintBlock(
                    new BlockPos(pos[0], pos[1], pos[2]),
                    palette.get(index),
                    entry.getCompound(NBT).orElse(null)));
        }
        if (skipped > 0) {
            KeystoneMod.LOG.warn("Skipped {} block(s) with an out-of-range palette index", skipped);
        }
        Vec3i bounds = new Vec3i(size[0], size[1], size[2]);
        return new Blueprint(bounds, out, readAnchor(tag, bounds), readMeta(tag));
    }

    /**
     * The anchor the file names, or the default when it names none.
     *
     * <p>Refused rather than honored when it falls outside the structure: a file
     * naming a cell it does not contain would line the building up by nothing,
     * and the middle of the floor is a better answer than a guess. Same rule
     * {@link StructurizeNbt} applies to {@code primary_offset}, and for the same
     * reason.
     */
    private static BlockPos readAnchor(CompoundTag tag, Vec3i size) {
        Optional<CompoundTag> ours = tag.getCompound(KEYSTONE);
        if (ours.isEmpty() || ours.get().getListOrEmpty(ANCHOR).isEmpty()) {
            return Blueprint.defaultAnchor(size);
        }
        int[] cell = triple(ours.get(), ANCHOR);
        BlockPos anchor = new BlockPos(cell[0], cell[1], cell[2]);
        if (Blueprint.anchorFits(anchor, size)) {
            return anchor;
        }
        KeystoneMod.LOG.warn("Ignoring an anchor at {} that is outside a {} structure",
                anchor, size);
        return Blueprint.defaultAnchor(size);
    }

    private static Blueprint.Meta readMeta(CompoundTag tag) {
        Optional<CompoundTag> ours = tag.getCompound(KEYSTONE);
        if (ours.isEmpty()) {
            return Blueprint.Meta.NONE;
        }
        return new Blueprint.Meta(
                ours.get().getIntOr(FACING, 0),
                ours.get().getIntOr(CROP_BLOCKS, Blueprint.UNCOUNTED));
    }

    private static List<BlockState> readPalette(CompoundTag tag, HolderGetter<Block> blocks) {
        // "palettes" is the multi-variant form; any one of them is a valid
        // rendering of the structure, so take the first.
        ListTag entries = tag.getList(PALETTE)
                .orElseGet(() -> tag.getListOrEmpty(PALETTES).getListOrEmpty(0));

        List<BlockState> palette = new ArrayList<>(entries.size());
        int unreadable = 0;
        for (int i = 0; i < entries.size(); i++) {
            BlockState state;
            try {
                state = NbtUtils.readBlockState(blocks, entries.getCompoundOrEmpty(i));
            } catch (RuntimeException damaged) {
                state = Blocks.AIR.defaultBlockState();
                unreadable++;
            }
            palette.add(state);
        }
        if (unreadable > 0) {
            KeystoneMod.LOG.warn("{} palette entries unreadable, treated as air", unreadable);
        }
        return palette;
    }

    // --- writing ---

    public static void writeFile(Blueprint blueprint, Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        NbtIo.writeCompressed(write(blueprint), file);
    }

    /** Encodes to the vanilla format, so structure blocks can load what we write. */
    public static CompoundTag write(Blueprint blueprint) {
        CompoundTag tag = new CompoundTag();
        Vec3i size = blueprint.size();
        tag.put(SIZE, intList(size.getX(), size.getY(), size.getZ()));

        Map<BlockState, Integer> ids = new LinkedHashMap<>();
        ListTag blockList = new ListTag();
        for (Blueprint.BlueprintBlock block : blueprint.blocks()) {
            Integer id = ids.get(block.state());
            if (id == null) {
                id = ids.size();
                ids.put(block.state(), id);
            }
            CompoundTag entry = new CompoundTag();
            BlockPos pos = block.pos();
            entry.put(POS, intList(pos.getX(), pos.getY(), pos.getZ()));
            entry.putInt(STATE, id);
            if (block.nbt() != null) {
                entry.put(NBT, block.nbt().copy());
            }
            blockList.add(entry);
        }

        ListTag palette = new ListTag();
        for (BlockState state : ids.keySet()) {
            palette.add(NbtUtils.writeBlockState(state));
        }

        tag.put(PALETTE, palette);
        tag.put(BLOCKS, blockList);
        // Vanilla always writes the key; an absent one trips stricter readers.
        tag.put(ENTITIES, new ListTag());
        // The compiled-in number rather than the detected one. They are the same
        // value in a running game, and only one of them can be asked for
        // without a game behind it: getCurrentVersion() throws when nobody has
        // detected a version, which is every context that is not a launched
        // client or server. A structure writer that cannot be exercised outside
        // a launched game is a structure writer nobody tests.
        tag.putInt(DATA_VERSION, SharedConstants.WORLD_VERSION);

        // Always written, even when it is all defaults. A pocket that appeared
        // only sometimes would mean "no anchor stated" and "anchor happens to be
        // the middle" were the same file, and a re-save of a scanned building
        // would quietly lose the cell the author lined it up by.
        CompoundTag ours = new CompoundTag();
        BlockPos anchor = blueprint.anchor();
        ours.put(ANCHOR, intList(anchor.getX(), anchor.getY(), anchor.getZ()));
        ours.putInt(FACING, blueprint.meta().facing());
        ours.putInt(CROP_BLOCKS, blueprint.meta().cropBlocks());
        tag.put(KEYSTONE, ours);
        return tag;
    }

    // --- helpers ---

    /**
     * Reads a 3-integer field. The format uses a list of ints, but int-arrays
     * appear in files from other tools, so accept either.
     */
    private static int[] triple(CompoundTag tag, String key) {
        Optional<int[]> array = tag.getIntArray(key);
        if (array.isPresent() && array.get().length >= 3) {
            return array.get();
        }
        ListTag list = tag.getListOrEmpty(key);
        if (list.size() >= 3) {
            return new int[]{list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0)};
        }
        return new int[]{0, 0, 0};
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }
}
