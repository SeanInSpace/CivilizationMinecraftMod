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

    private BlueprintNbt() {
    }

    // --- reading ---

    /** Reads a structure file, gzipped (as vanilla writes them) or plain. */
    public static Blueprint readFile(Path file, HolderGetter<Block> blocks) throws IOException {
        return read(readTag(file), blocks);
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
        return new Blueprint(new Vec3i(size[0], size[1], size[2]), out);
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
        tag.putInt(DATA_VERSION, SharedConstants.getCurrentVersion().dataVersion().version());
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
