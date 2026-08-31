package com.kingdoms.neoforge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

/**
 * What the ground is, at any column, without loading it.
 *
 * <p>The simulation kept asking the wrong question. It wanted to know
 * <em>what the ground is like</em> at a plot, and the only way it had to find
 * out was to have the chunk <em>loaded</em> — so siting either answered "yes" to
 * everything it could not see, or the harness force-loaded thousands of chunks
 * to make it see, and the server then spent its whole tick budget generating
 * terrain instead of running the town. Both of those were measured today, and
 * both are the same mistake: a loaded chunk is a <em>ticking</em> chunk, with
 * mobs and redstone and entity tracking, and none of that is wanted by something
 * that only needs a height.
 *
 * <p>So this answers the question directly. Two sources, in order of authority:
 *
 * <ol>
 *   <li><strong>A loaded chunk</strong>, when there is one. Authoritative: it
 *       includes everything a player has dug, built or flooded.</li>
 *   <li><strong>The generator's own noise</strong> otherwise, through
 *       {@code getBaseHeight}, which is what vanilla itself asks when deciding
 *       where a village may go. No chunk is created and nothing is allocated.</li>
 * </ol>
 *
 * <p>What the noise answer does <em>not</em> know: caves and ravines, trees,
 * feature-placed lakes, and anything anybody has changed. It is the shape of the
 * land before the world was decorated. That is plenty for choosing between one
 * hillside and another, and not enough to stand a building on without looking —
 * which is why a reading taken from noise is remembered as such, and replaced
 * the moment the chunk it describes is genuinely loaded.
 */
public final class TerrainOracle {

    /**
     * Columns remembered before the whole table is dropped.
     *
     * <p>A quarter of a million is about a 500-block square at one sample a
     * block, which is more than any settlement asks about. Dropping the lot
     * rather than evicting cleverly is deliberate: the cost of a miss is one
     * noise sample, so an occasional cold start is cheaper than the bookkeeping
     * a smarter policy would want.
     */
    private static final int REMEMBERED = 262_144;

    /** Height offset, so the world's negative depths survive being packed. */
    private static final int BIAS = 2048;

    private static final long WET = 1L << 20;
    private static final long FROM_CHUNK = 1L << 21;

    /**
     * The reading came from a chunk generated far enough to have real ground.
     *
     * <p>Between the two sources this class had. Noise is a guess: it asks the
     * generator's density function for a column height without generating
     * anything, and it is <strong>wrong by about eight courses on average</strong>
     * — enough that a road judged walkable on it climbs sixteen blocks in a
     * step when somebody finally stands there. A loaded chunk is the truth but
     * costs a ticking chunk, with mobs and redstone, for a height.
     *
     * <p>A chunk generated to {@link #GROUND_TRUTH} and no further is neither.
     * It has the real terrain — carved, so ravines are in it, which is exactly
     * what makes a hillside unwalkable — and it does not tick. One call yields
     * every column in the chunk, where noise charges per column.
     */
    private static final long FROM_SURFACE = 1L << 23;

    /**
     * How far a chunk must be generated before its ground is worth reading.
     *
     * <p>{@code CARVERS} rather than {@code SURFACE}, deliberately. Surface
     * rules place the grass and the sand, but the carvers cut the caves and the
     * <em>ravines</em> — and a ravine is precisely the thing that turns a
     * planned street into a cliff. Reading before them would give a confident
     * answer with the interesting parts missing.
     *
     * <p>And not {@code FEATURES}, which is where the trees go in: the heightmap
     * this reads counts a log as ground, so a forest would come back as a plateau
     * eleven blocks up. That mistake has already been made once in this class's
     * history, from the other direction — comparing WORLD_SURFACE against
     * OCEAN_FLOOR reported 742 meadows as lakes because it counted the grass.
     */
    private static final ChunkStatus GROUND_TRUTH = ChunkStatus.CARVERS;

    /**
     * Chunks generated to that status in one tick.
     *
     * <p>Small on purpose and separate from {@link #SAMPLES_PER_TICK}, because
     * the two cost nothing alike: a noise sample is one column of Perlin, and
     * generating a cold chunk pulls every status beneath it — structures,
     * biomes, noise, surface, carvers. The first version of this class asked for
     * a hundred noise samples per candidate plot and the watchdog killed the
     * server twice. This buys sixty-four times more ground per unit of work, so
     * it can afford to be timid.
     */
    private static final int GROUND_CHUNKS_PER_TICK = 2;

    /**
     * Chunks a deliberate warm may generate.
     *
     * <p>Large, and it should be. A warm is not a tick-time question — it is
     * somebody asking for a survey of a square of the world and willing to wait
     * for it. Bounding it like a query is how surveys came back mostly empty:
     * of the ten thousand columns a {@code /civ plan} reports, <strong>seven
     * thousand eight hundred were the unread sentinel</strong>, which reads as a
     * height of minus two thousand and forty-eight and was being taken for
     * ground by everything downstream, this project's own fault surveys
     * included.
     *
     * <p>A thousand chunks is a five-hundred-block square read properly. The
     * per-tick budget still guards the simulation's own queries, which is where
     * the watchdog risk actually lives.
     */
    private static final int WARM_GROUND_CHUNKS = 1024;

    /**
     * Columns are remembered on a grid this coarse.
     *
     * <p>Sampling the generator is <strong>expensive</strong> — it evaluates a
     * density column of Perlin noise, and the first version of this class asked
     * for about a hundred per candidate plot and ninety-six candidates per
     * building. A single tick took sixty seconds and the watchdog killed the
     * server. The stack was all {@code PerlinNoise.getValue}.
     *
     * <p>Rounding to four blocks cuts the distinct columns by sixteen and, far
     * more importantly, turns the scattered queries siting makes into repeats
     * that actually hit the cache. Four blocks is finer than the survey samples
     * and much finer than a plot, so nothing that reads this can tell.
     */
    public static final int GRAIN = 4;

    /**
     * Generator samples allowed in one game tick.
     *
     * <p>The hard promise this class makes: asking about terrain can never cost
     * more than a bounded slice of a tick, whatever it is asked. Past the
     * budget it answers from memory alone and reports what it does not know,
     * and the caller decides what to do about that. A planner stalling a server
     * to be certain about a hillside is a worse outcome than a planner being
     * unsure of one.
     *
     * <p>A thousand rather than the first draft's three hundred, because the
     * grain changed what a sample costs. Callers now ask on the same four-block
     * grid this remembers on, so the first candidate plot in a neighbourhood
     * pays for its readings and every candidate after it is answered from
     * memory. The budget bounds a cold start, not the steady state.
     */
    private static final int SAMPLES_PER_TICK = 1024;

    private final ServerLevel level;
    private final Map<Long, Long> known = new HashMap<>();

    private long asked;
    private long sampled;
    private long burstTick = Long.MIN_VALUE;
    private int sampledThisTick;
    private int groundChunksThisTick;
    private long generatedChunks;

    public TerrainOracle(ServerLevel level) {
        this.level = level;
    }

    /** The surface height of this column: the first solid ground, water aside. */
    public int height(int x, int z) {
        return (int) (read(x, z) & 0xFFFFF) - BIAS;
    }

    /** Whether this column stands under water. */
    public boolean isWet(int x, int z) {
        return (read(x, z) & WET) != 0;
    }

    /**
     * Whether the answer came from a loaded chunk, and so knows what players did.
     *
     * <p>Deliberately still only the loaded case. A generated-but-not-loaded
     * chunk has the real <em>terrain</em> and cannot know about the quarry
     * somebody dug in it, so anything that cares about the world as it stands
     * rather than as it was made must keep asking this.
     */
    public boolean isCertain(int x, int z) {
        return (read(x, z) & FROM_CHUNK) != 0;
    }

    /**
     * Whether the answer is real ground rather than a guess at it.
     *
     * <p>True for a loaded chunk and for one generated to {@link #GROUND_TRUTH}.
     * This is the question worth asking about <em>terrain</em> — is this the
     * shape of the land, or the generator's estimate of it — and the answer the
     * road rules need, which is why they were being misled: the estimate is
     * smooth by nature and real ground is not.
     */
    public boolean isSurveyed(int x, int z) {
        long value = read(x, z);
        return (value & (FROM_CHUNK | FROM_SURFACE)) != 0;
    }

    /** Whether anything at all is known about this column right now. */
    public boolean isRead(int x, int z) {
        return (read(x, z) & UNREAD) == 0;
    }

    /**
     * The worst step between neighbours across a square of this half-width.
     *
     * <p>The worst step rather than the average, because a plot does not care
     * what the mean gradient is — it cares about the deepest course it has to
     * cut, and one cliff edge across an otherwise flat shelf is what makes a
     * site unbuildable.
     */
    public int roughness(int x, int z, int radius, int stride) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += stride) {
            for (int dz = -radius; dz <= radius; dz += stride) {
                int at = height(x + dx, z + dz);
                lowest = Math.min(lowest, at);
                highest = Math.max(highest, at);
            }
        }
        return highest - lowest;
    }

    /**
     * How far the bulk of a square falls, ignoring its few worst columns.
     *
     * <p>{@link #roughness} answers with the worst step anywhere, which is the
     * right question for a cliff and the wrong one for a hole: a flat shelf with
     * a cave mouth clipping one corner reads as unbuildable when a builder would
     * pack two courses of fill into it and think nothing of it. This reads the
     * middle three fifths and lets the foundation deal with the rest.
     */
    public int bulkFall(int x, int z, int radius, int stride) {
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += stride) {
            for (int dz = -radius; dz <= radius; dz += stride) {
                heights.add(height(x + dx, z + dz));
            }
        }
        if (heights.isEmpty()) {
            return 0;
        }
        java.util.Collections.sort(heights);
        return heights.get((heights.size() * 4) / 5) - heights.get(heights.size() / 5);
    }

    /** Whether any column in this square is under water. */
    public boolean anyWet(int x, int z, int radius, int stride) {
        for (int dx = -radius; dx <= radius; dx += stride) {
            for (int dz = -radius; dz <= radius; dz += stride) {
                if (isWet(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The generator's answer for a column, ignoring any loaded chunk.
     *
     * <p>Only for checking this class against the truth. Everything else should
     * use {@link #height}, which prefers a real chunk when there is one.
     */
    public int noiseHeight(int x, int z) {
        return (int) (measureFromNoise(x, z) & 0xFFFFF) - BIAS;
    }

    /** The generator's answer for whether a column is wet, ignoring any chunk. */
    public boolean noiseWet(int x, int z) {
        return (measureFromNoise(x, z) & WET) != 0;
    }

    /** Readings taken, and how many of those had to go to the generator. */
    public String tally() {
        return asked + " asked, " + sampled + " sampled, " + known.size() + " remembered";
    }

    /**
     * Reads a whole square now, budget or no budget.
     *
     * <p>The per-tick budget exists to stop a <em>planner</em> stalling the
     * server while it weighs a hillside. A survey is not a planner: somebody has
     * typed a command and is waiting for an answer about ten thousand columns,
     * and metering that out at three hundred a tick returns a map nine tenths
     * unread. So this says plainly that the caller has asked for the work and
     * will wait for it.
     *
     * <p>Bounded even so, because "the caller will wait" is not the same as
     * "the server will wait". A generator sample measures about <strong>six
     * milliseconds</strong> — {@code getBaseHeight} builds a noise chunk for
     * every column, which is far dearer than it looks — so ten thousand of them
     * is a sixty-second tick and the watchdog kills the server. It did, twice.
     * Past {@link #WARM_CEILING} this stops and the rest of the square is
     * reported unread, which the survey draws as unread.
     *
     * <p>The real answer is to stop asking column by column: one chunk generated
     * to a partial status yields two hundred and fifty-six columns for about the
     * price of a handful of these. That is the next version of this class.
     *
     * <p>Only for explicit, operator-driven work. Nothing on the simulation's
     * own clock may call it.
     */
    public void warm(int centreX, int centreZ, int radius, int stride) {
        // Chunk by chunk, not column by column. Somebody asking for a survey
        // wants the ground, and generating a chunk to CARVERS answers two
        // hundred and fifty-six columns for about what a handful of noise
        // samples used to cost -- so the whole square can be had properly
        // rather than estimated.
        int chunks = 0;
        int firstChunkX = (centreX - radius) >> 4;
        int lastChunkX = (centreX + radius) >> 4;
        int firstChunkZ = (centreZ - radius) >> 4;
        int lastChunkZ = (centreZ + radius) >> 4;
        for (int cz = firstChunkZ; cz <= lastChunkZ; cz++) {
            for (int cx = firstChunkX; cx <= lastChunkX; cx++) {
                if (chunkIsKnown(cx, cz)) {
                    continue;
                }
                if (chunks++ >= WARM_GROUND_CHUNKS) {
                    return;   // enough generation for one request
                }
                readGroundChunk(cx, cz);
            }
        }
        // Anything still unread -- past the chunk ceiling, or refused -- falls
        // back to the old column sweep, so a warm never comes back emptier than
        // it used to.
        int spent = 0;
        for (int dz = -radius; dz <= radius; dz += stride) {
            for (int dx = -radius; dx <= radius; dx += stride) {
                int x = centreX + dx;
                int z = centreZ + dz;
                long key = key(x, z);
                Long had = known.get(key);
                if (had != null && (had & (FROM_CHUNK | FROM_SURFACE)) != 0) {
                    continue;
                }
                boolean cheap = level.hasChunkAt(new BlockPos(x, 0, z));
                if (!cheap && spent++ >= WARM_CEILING) {
                    return;   // out of noise; the rest is honestly unread
                }
                if (known.size() >= REMEMBERED) {
                    known.clear();
                }
                known.put(key, measure(x - Math.floorMod(x, GRAIN),
                                       z - Math.floorMod(z, GRAIN)));
            }
        }
    }

    /** Whether every grain cell of this chunk already has real ground behind it. */
    private boolean chunkIsKnown(int chunkX, int chunkZ) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int dx = 0; dx < 16; dx += GRAIN) {
            for (int dz = 0; dz < 16; dz += GRAIN) {
                Long had = known.get(key(baseX + dx, baseZ + dz));
                if (had == null || (had & (FROM_CHUNK | FROM_SURFACE)) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Samples one {@code warm} call may spend.
     *
     * <p>Twelve hundred is about seven seconds of noise at the measured cost:
     * slow enough that an operator notices, comfortably inside the watchdog's
     * sixty, and enough to cover a town at the survey's four-block grain out to
     * about seventy blocks. Beyond that the survey reports unread ground, which
     * is true and drawn as such.
     */
    private static final int WARM_CEILING = 1200;

    private static long key(int x, int z) {
        int gx = x - Math.floorMod(x, GRAIN);
        int gz = z - Math.floorMod(z, GRAIN);
        return (((long) gx) << 32) ^ (gz & 0xFFFFFFFFL);
    }

    /** Chunks generated to read their ground, for reports. */
    public long generatedChunks() {
        return generatedChunks;
    }

    /** Forgets everything, for when a world is closing or has been reshaped wholesale. */
    public void forget() {
        known.clear();
    }

    // --- the small print ---

    private long read(int x, int z) {
        asked++;
        x -= Math.floorMod(x, GRAIN);
        z -= Math.floorMod(z, GRAIN);
        long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
        Long remembered = known.get(key);
        // A noise reading is provisional. The moment the chunk it describes is
        // genuinely loaded, the real ground is there to be read and is better --
        // it knows the quarry somebody dug and the noise does not.
        if (remembered != null
                && ((remembered & FROM_CHUNK) != 0 || !level.hasChunkAt(new BlockPos(x, 0, z)))) {
            return remembered;
        }
        long now = level.getGameTime();
        if (now != burstTick) {
            burstTick = now;
            sampledThisTick = 0;
            groundChunksThisTick = 0;
        }
        // Real ground first, if the budget allows and nothing better is known.
        // One chunk answers sixty-four of these grain cells, so a miss here
        // usually pays for its neighbours too.
        if ((remembered == null || (remembered & FROM_SURFACE) == 0)
                && groundChunksThisTick++ < GROUND_CHUNKS_PER_TICK
                && readGroundChunk(x >> 4, z >> 4)) {
            Long generated = known.get(key);
            if (generated != null) {
                return generated;
            }
        }
        if (sampledThisTick >= SAMPLES_PER_TICK) {
            // Out of budget this tick. Answer from what is remembered, and say
            // plainly that nothing is known rather than spend the tick finding
            // out. UNREAD is the caller's problem, and a small one.
            return remembered != null ? remembered : UNREAD;
        }
        sampledThisTick++;
        long value = measure(x, z);
        if (known.size() >= REMEMBERED) {
            known.clear();
        }
        known.put(key, value);
        return value;
    }

    /** The answer when the tick's sampling budget is spent and nothing is remembered. */
    private static final long UNREAD = 1L << 22;

    private long measure(int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (level.hasChunkAt(column)) {
            // Ask the fluid directly. Comparing WORLD_SURFACE against OCEAN_FLOOR
            // looks like a water test and is not: WORLD_SURFACE counts grass and
            // flowers and OCEAN_FLOOR does not, so every meadow came back as a
            // lake -- 742 of them on the first check of this class.
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            boolean wet = !level.getFluidState(new BlockPos(x, floor, z)).isEmpty()
                    || !level.getFluidState(new BlockPos(x, floor - 1, z)).isEmpty();
            return pack(floor, wet, true);
        }
        return measureFromNoise(x, z);
    }

    /**
     * Generates one chunk to {@link #GROUND_TRUTH} and remembers all of it.
     *
     * <p>The whole point of the class, finally done properly. Every grain cell
     * in the chunk is filled from one call, so the cost is per chunk rather than
     * per column — which is the difference between this being affordable and
     * being the thing that killed the server twice.
     *
     * <p>Reads the <em>worldgen</em> heightmaps. The plain {@code OCEAN_FLOOR}
     * is only primed once a chunk is finished; {@code OCEAN_FLOOR_WG} is the one
     * that exists during generation, and the gap between it and
     * {@code WORLD_SURFACE_WG} is standing water — the same test the noise path
     * makes, so the two sources answer in the same terms and only their accuracy
     * differs.
     *
     * @return whether the chunk was read
     */
    private boolean readGroundChunk(int chunkX, int chunkZ) {
        // Generation joins chunk futures on the main thread. The simulation
        // steps there, so this holds -- but a stray caller must degrade to noise
        // rather than deadlock the server.
        if (!level.getServer().isSameThread()) {
            return false;
        }
        ChunkAccess chunk;
        try {
            chunk = level.getChunkSource().getChunk(chunkX, chunkZ, GROUND_TRUTH, true);
        } catch (RuntimeException beyondUs) {
            return false;   // a generator that will not answer is not our business
        }
        if (chunk == null) {
            return false;
        }
        generatedChunks++;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        if (known.size() + 16 >= REMEMBERED) {
            known.clear();
        }
        for (int dx = 0; dx < 16; dx += GRAIN) {
            for (int dz = 0; dz < 16; dz += GRAIN) {
                int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, dx, dz);
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz);
                floor = settleOnGround(chunk, baseX + dx, floor, baseZ + dz);
                long packed = pack(floor, surface > floor, false) | FROM_SURFACE;
                long cell = (((long) (baseX + dx)) << 32) ^ ((baseZ + dz) & 0xFFFFFFFFL);
                Long had = known.get(cell);
                // Never overwrite a loaded-chunk reading with a generated one.
                if (had == null || (had & FROM_CHUNK) == 0) {
                    known.put(cell, packed);
                }
            }
        }
        return true;
    }

    /**
     * Corrects a worldgen heightmap reading against the blocks actually there.
     *
     * <p>The heightmap is a good guess and not the answer. Measured against the
     * ground the rest of the simulation means — {@code BlueprintPlacer.groundLevel},
     * which is what the live bridge answers with — the raw worldgen reading sat
     * <strong>one course low in forty-five per cent of columns</strong> and
     * fifteen or sixteen low in another eight, and a road judged a block out is
     * a road with a step in it.
     *
     * <p>A constant correction would have fitted the first of those and hidden
     * the second. So this looks: from the heightmap's guess it walks to the
     * first air above solid ground, up or down, within a short reach. Sixteen
     * block reads per grain cell at worst, against a chunk that is already in
     * hand — cheap next to having generated it.
     *
     * <p>Out of reach it keeps the guess. Better a known approximation than a
     * scan that wanders into a cave.
     */
    private static int settleOnGround(ChunkAccess chunk, int x, int from, int z) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int lowest = chunk.getMinY();
        // Already standing on air with something solid beneath: that is ground.
        for (int step = 0; step <= GROUND_SEARCH; step++) {
            int y = from + step;
            if (isGroundTop(chunk, at, x, y, z)) {
                return y;
            }
            int down = from - step;
            if (down > lowest && isGroundTop(chunk, at, x, down, z)) {
                return down;
            }
        }
        return from;
    }

    /** Whether this is the first empty block above something solid. */
    private static boolean isGroundTop(ChunkAccess chunk, BlockPos.MutableBlockPos at,
                                       int x, int y, int z) {
        if (y <= chunk.getMinY()) {
            return false;
        }
        boolean hereClear = chunk.getBlockState(at.set(x, y, z)).isAir();
        if (!hereClear) {
            return false;
        }
        var below = chunk.getBlockState(at.set(x, y - 1, z));
        return !below.isAir() && below.getFluidState().isEmpty();
    }

    /** How far from the heightmap's guess the real surface is looked for. */
    private static final int GROUND_SEARCH = 8;

    private long measureFromNoise(int x, int z) {
        sampled++;
        // Two heightmaps and the gap between them is the water. WORLD_SURFACE_WG
        // stops at the top of a lake and OCEAN_FLOOR_WG at the bed beneath it, so
        // one being above the other is a column with fluid standing in it -- an
        // exact answer rather than a guess against sea level, which gets every
        // mountain tarn and every dry basin below sea level wrong.
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int floor = generator.getBaseHeight(
                x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        int surface = generator.getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
        return pack(floor, surface > floor, false);
    }

    private static long pack(int height, boolean wet, boolean fromChunk) {
        long value = Math.max(0, Math.min(0xFFFFF, height + BIAS));
        if (wet) {
            value |= WET;
        }
        if (fromChunk) {
            value |= FROM_CHUNK;
        }
        return value;
    }
}
