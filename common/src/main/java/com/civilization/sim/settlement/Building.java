package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;

import java.util.List;
import java.util.Objects;

/**
 * A finished building. This is the settlement's memory of what it has built.
 *
 * <p>The simulation is the authority on what exists, not the blocks in the world.
 * A building is recorded here the moment its {@link BuildTask} completes, whether
 * or not any chunk was loaded at the time — and {@link #materialized} tracks
 * separately whether it has since been drawn into the world as actual blocks.
 *
 * <p>That split is what makes "the settlement grew while you were away" work: the
 * building exists in data immediately, and gets painted in later, once someone is
 * around to see it.
 */
public final class Building {

    /** Sentinel for {@link #soundCensus}: nobody has ever counted this building. */
    public static final int UNCOUNTED = -1;

    /**
     * What a hand-authored file put in this building, as found in the placed plan.
     *
     * <p>This exists because of one rule that the drawn buildings let everybody
     * get away with breaking: the simulation never looks at the world. It knows
     * where a settler's bed is because {@link Beds} is a table both halves read,
     * and it knows where the door is because every drawing puts one in the middle
     * of the south wall. Both are true of every building the code draws and
     * neither is true of a building somebody built themselves — their beds are
     * wherever they put them, and their door is wherever they cut it.
     *
     * <p>So for an authored building the answers are <em>found once</em>, when
     * the plan is made, and written down here. Everything downstream goes on
     * asking the simulation rather than the world, which is the rule; it simply
     * gets an answer that came from a file instead of from a table. Positions
     * are offsets from the building's origin, not world coordinates, so a
     * building whose recorded height is corrected at placement takes its
     * furniture with it.
     *
     * @param bedFeet    the foot half of each bed, in the order they are handed
     *                   out, as offsets from the origin
     * @param bedHeads   the head half of the same beds, in the same order. Kept
     *                   rather than derived: a bed's facing is a block state in
     *                   the file, and re-deriving it from the foot would be
     *                   guessing at somebody else's building.
     * @param doorstep   the block outside the door somebody stands on to walk
     *                   in, as an offset from the origin
     * @param cropBlocks crop blocks counted in the plan, or {@link #UNCOUNTED}
     */
    public record Authored(List<SimPos> bedFeet, List<SimPos> bedHeads,
                           SimPos doorstep, int cropBlocks) {

        public Authored {
            bedFeet = List.copyOf(bedFeet);
            bedHeads = List.copyOf(bedHeads);
            if (bedFeet.size() != bedHeads.size()) {
                throw new IllegalArgumentException(
                        "a bed has a foot and a head: " + bedFeet.size()
                                + " feet against " + bedHeads.size() + " heads");
            }
        }

        public int bedCount() {
            return bedFeet.size();
        }

        /** Whether this file said where its door is. */
        public boolean hasDoorstep() {
            return doorstep != null;
        }
    }

    private String blueprintId;
    private SimPos origin;
    private final long completedOnStep;
    private boolean materialized;

    private boolean surveyed;

    /** Standing since before the first step, with no history behind it. */
    private boolean seeded;

    /** How much room it takes up; unknown until its plan has been built. */
    private Footprint footprint = Footprint.UNKNOWN;

    /** What a file put in it, or null for a building the code drew. See {@link Authored}. */
    private Authored authored;

    /** Quarter turns clockwise from the drawn orientation. */
    private int facing;

    /** One is the plain version; higher is an improvement raised on the same spot. */
    private int level = 1;

    /**
     * How many solid blocks stood here when this building was last seen whole.
     *
     * <p>The baseline damage is judged against — see {@link RepairPlanner}. Taken
     * from the world rather than from the blueprint, because a blueprint says
     * what was meant to be laid and a building is what the builders actually
     * managed once the ground had been leveled and the doorway cut.
     *
     * <p>{@link #UNCOUNTED} until somebody has been there to look. A building
     * raised while nobody was watching has never been counted, and must not be
     * mistaken for one that has been reduced to nothing.
     */
    private int soundCensus = UNCOUNTED;

    /** How much of it is missing, in percent. Zero for a building in good repair. */
    private int damage;

    /** Food held at this building — harvest waiting at a farm, stock at a market. */
    private int foodStored;

    /**
     * The step a farmer last harvested this farm's actual crops.
     *
     * <p>What lets the two fidelities share one field without double-crediting:
     * while real harvests are fresh, the clock stands aside; when they go stale
     * — the farm is unwatched, or its farmers cannot reach the field — the clock
     * takes over, because being watched must never starve a town. Deliberately
     * not persisted: after a reload the clock simply runs until a farmer proves
     * the field is workable again.
     */
    private long lastRealHarvestStep = Long.MIN_VALUE;

    /**
     * How much of this farm's field is standing ripe, in hundredths of a block.
     *
     * <p>See {@link Field}. Persisted, unlike the harvest stamp above, and that
     * is the whole reason it is a saved number rather than something recomputed:
     * a worldgen town can go whole sessions with nobody near it, and a field that
     * forgot what it had grown every time the game closed would be a field that
     * never fed anybody.
     */
    private int ripeHundredths;

    /**
     * A lumber camp's standing timber, in thousandths of a log. See {@link Stand}.
     *
     * <p>{@link Stand#UNCOUNTED} until the ground under the camp has been loaded
     * with somebody asking, because "nobody has counted these trees" and "these
     * trees have all been felled" are opposite facts, and only one of them means
     * the camp should stop working.
     */
    private int standThousandths = Stand.UNCOUNTED;

    /** A lumber camp's saplings, as the timber they are going to be. See {@link Stand}. */
    private int growingThousandths;

    /**
     * A mine's remaining stone, in blocks. See {@link Seam}.
     *
     * <p>{@link Seam#UNCOUNTED} until the ground has been counted, for the same
     * reason the stand is: a mine nobody has ever looked at is not a mine that
     * has been cut out.
     */
    private int stoneSeam = Seam.UNCOUNTED;

    /**
     * Whether a player was near this farm the last time the clock looked.
     *
     * <p>Only ever compared against the current answer, to catch the moment a
     * field goes from nobody-there to somebody-standing-in-it — which is when
     * the world's crops have to be made to agree with the ledger. Not persisted:
     * a reloaded world starts everybody unwatched, so the first watched step
     * reconciles, which is exactly what wanted to happen anyway.
     */
    private boolean watched;

    public Building(String blueprintId, SimPos origin, long completedOnStep) {
        this(blueprintId, origin, completedOnStep, false);
    }

    public Building(String blueprintId, SimPos origin, long completedOnStep, boolean materialized) {
        this.blueprintId = Objects.requireNonNull(blueprintId, "blueprintId");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.completedOnStep = completedOnStep;
        this.materialized = materialized;
    }

    /**
     * What this building physically holds.
     *
     * <p>Null until something is put here, because most buildings never hold
     * anything and a ledger apiece would be written to disk for every hut in
     * every town. Reach for it through {@link #stores()}.
     */
    private TownStores stores;

    /** Worked out from the blueprint id on first use. See {@link #role()}. */
    private BuildingRole role;

    /**
     * This building's own goods.
     *
     * <p>The town's holdings are the sum of these — see {@link PooledStock}.
     * Created on first use, so asking is enough to make a building a holder.
     */
    public TownStores stores() {
        if (stores == null) {
            stores = new TownStores();
        }
        return stores;
    }

    /** Whether anything is held here, without building a ledger to find out. */
    public boolean hasStores() {
        return stores != null && !stores.all().isEmpty();
    }

    /**
     * Whether this is somewhere the town keeps its bulk goods.
     *
     * <p>Asked by the settlement to decide which buildings are holders, and by
     * the platform to decide which ones get a container. It reads the blueprint
     * name because that is the only thing a placed building carries that says
     * what it is for.
     */
    public boolean isStore() {
        return role() == BuildingRole.STORE;
    }

    /**
     * What this building is for.
     *
     * <p>Cached because the holder list asks every settlement's every building
     * on every read of the town's stock, and thrown away when an upgrade
     * renames the blueprint underneath it.
     */
    public BuildingRole role() {
        if (role == null) {
            role = BuildingRole.of(blueprintId);
        }
        return role;
    }

    public String blueprintId() {
        return blueprintId;
    }

    public SimPos origin() {
        return origin;
    }

    /** Which simulation step this was finished on. Useful for debugging and for "built N steps ago". */
    public long completedOnStep() {
        return completedOnStep;
    }

    /** Whether this has been drawn into the world as blocks yet. */
    /**
     * Whether this building's height was measured against real terrain.
     *
     * <p>False for one planned and finished while its chunk was never loaded —
     * its origin carries an estimate, and placement has to snap to the ground.
     */
    /**
     * Corrects the recorded height to where the building actually stands.
     *
     * <p>A building planned while its chunk was unloaded carries an estimated
     * height. Placement finds the real ground; without writing that back, every
     * worker who walks to this building aims at the estimate instead.
     */
    /**
     * Moves the whole record. Only legitimate while nothing has been drawn: a
     * building that exists as blocks cannot follow its own bookkeeping.
     */
    public void setOrigin(SimPos origin) {
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public void setOriginY(int y) {
        this.origin = new SimPos(origin.x(), y, origin.z());
    }

    /** Changed only when a building is improved in place; see {@code planUpgrade}. */
    public void setBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
        this.role = null;   // an upgrade can change what a building is
    }

    /** See {@link #soundCensus}. */
    public int soundCensus() {
        return soundCensus;
    }

    public void setSoundCensus(int soundCensus) {
        this.soundCensus = soundCensus;
    }

    /** Whether this building has ever been counted whole. */
    public boolean hasCensus() {
        return soundCensus > 0;
    }

    /** Forgets the baseline, so the next look re-establishes it. */
    public void clearCensus() {
        this.soundCensus = UNCOUNTED;
    }

    /** How much of this building is missing, in percent. */
    public int damage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = Math.max(0, Math.min(100, damage));
    }

    /** Whether anything is visibly wrong with it. */
    public boolean isDamaged() {
        return damage >= RepairPlanner.NOISE_FLOOR;
    }

    /**
     * Whether the town will spend timber on it.
     *
     * <p>The same question as {@link #isDamaged()} since the threshold came down
     * to the noise floor, and that is the point rather than an oversight: a town
     * that recorded damage it had no intention of mending is what left ordinary
     * war damage standing forever. Both names are kept because both are asked —
     * one by anything reporting on the building, one by
     * {@link RepairPlanner} deciding whether to book a crew — and a reader
     * following either into the other should find them agreeing.
     */
    public boolean needsRepair() {
        return isDamaged();
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int facing() {
        return facing;
    }

    public void setFacing(int facing) {
        this.facing = Math.floorMod(facing, 4);
    }

    /**
     * The block you stand on to walk in: one step outside the door, on the side
     * the building actually faces.
     *
     * <p>Every blueprint draws its doorway in the middle of the south wall and
     * {@link BuildPlanner#facingToward} then turns the whole structure to face
     * the town center, so the door ends up on whichever side that turn put it.
     * Code that assumed south — the path layer did, for as long as paths have
     * existed — aimed three buildings in four at a blank wall.
     */
    public SimPos doorstep() {
        // An authored building's door is wherever its author cut it, and that
        // was found in the plan and written down. The arithmetic below is the
        // right answer for a drawing, where the door is always the middle of the
        // front wall, and a confident guess about anybody else's building.
        if (authored != null && authored.hasDoorstep()) {
            SimPos step = authored.doorstep();
            return new SimPos(origin.x() + step.x(), origin.y() + step.y(),
                    origin.z() + step.z());
        }
        boolean acrossX = facing == 1 || facing == 3;
        int span = footprint.isKnown()
                ? (acrossX ? footprint.width() : footprint.depth())
                : 4;   // unplaced: a cabin's own depth, close enough to stand off
        int reach = span / 2 + 1;
        return switch (facing) {
            case 1 -> new SimPos(origin.x() - reach, origin.y(), origin.z());
            case 2 -> new SimPos(origin.x(), origin.y(), origin.z() - reach);
            case 3 -> new SimPos(origin.x() + reach, origin.y(), origin.z());
            default -> new SimPos(origin.x(), origin.y(), origin.z() + reach);
        };
    }

    /**
     * What an authored file put here, or null when the code drew this building.
     *
     * <p>Null rather than an empty record, so "drawn" and "authored, and it has
     * no beds" stay different facts. A granary from a file has an {@code Authored}
     * with no beds in it; a drawn granary has none at all, and the beds it does
     * not have come from the table like everything else's.
     */
    public Authored authored() {
        return authored;
    }

    /** Whether this building came out of a file somebody wrote. */
    public boolean isAuthored() {
        return authored != null;
    }

    public void setAuthored(Authored authored) {
        this.authored = authored;
    }

    public Footprint footprint() {
        return footprint;
    }

    public void setFootprint(Footprint footprint) {
        this.footprint = footprint == null ? Footprint.UNKNOWN : footprint;
    }

    public boolean isSurveyed() {
        return surveyed;
    }

    public void setSurveyed(boolean surveyed) {
        this.surveyed = surveyed;
    }

    public boolean isMaterialized() {
        return materialized;
    }

    public void setMaterialized(boolean materialized) {
        this.materialized = materialized;
    }

    /**
     * Whether this building was seeded rather than built.
     *
     * <p>The difference nobody could see from the record: a building the town
     * raised course by course has a history in the world around it — the ground
     * it cleared, the wood it felled — and a seeded one has none, because
     * {@code Founding.seeded} writes the building and stops. Anything that has
     * to make the surroundings agree with the story needs to know which it is,
     * and the only honest moment to act on it is the first drawing, when the
     * chunk is finally loaded.
     *
     * <p>Cleared once acted on, so it is a debt rather than a label: a camp that
     * has had its wood planted is thereafter an ordinary camp.
     */
    public boolean isSeeded() {
        return seeded;
    }

    public void setSeeded(boolean seeded) {
        this.seeded = seeded;
    }

    public int foodStored() {
        return foodStored;
    }

    public void touchRealHarvest(long step) {
        lastRealHarvestStep = step;
    }

    /** Whether real hands have worked this farm recently enough to trust them. */
    public boolean harvestedWithin(long step, int grace) {
        return lastRealHarvestStep != Long.MIN_VALUE && step - lastRealHarvestStep <= grace;
    }

    public void setFoodStored(int foodStored) {
        this.foodStored = Math.max(0, foodStored);
    }

    /** This farm's ripeness ledger, in hundredths of a crop block. See {@link Field}. */
    public int ripeHundredths() {
        return ripeHundredths;
    }

    public void setRipeHundredths(int hundredths) {
        this.ripeHundredths = Math.max(0, hundredths);
    }

    /** This camp's standing timber, in thousandths of a log. See {@link Stand}. */
    public int standThousandths() {
        return standThousandths;
    }

    /**
     * Deliberately not clamped at zero: {@link Stand#UNCOUNTED} lives in this
     * field and means something no amount of felling can.
     */
    public void setStandThousandths(int thousandths) {
        this.standThousandths = Math.max(Stand.UNCOUNTED, thousandths);
    }

    /** This camp's saplings, as the timber they are going to be. See {@link Stand}. */
    public int growingThousandths() {
        return growingThousandths;
    }

    public void setGrowingThousandths(int thousandths) {
        this.growingThousandths = Math.max(0, thousandths);
    }

    /** This mine's remaining stone, in blocks. See {@link Seam}. */
    public int stoneSeam() {
        return stoneSeam;
    }

    /** Not clamped at zero either, and for the same reason: see {@link Seam#UNCOUNTED}. */
    public void setStoneSeam(int blocks) {
        this.stoneSeam = Math.max(Seam.UNCOUNTED, blocks);
    }

    /** Whether a player was near this building the last time the clock looked. */
    public boolean wasWatched() {
        return watched;
    }

    public void setWatched(boolean watched) {
        this.watched = watched;
    }

    @Override
    public String toString() {
        return blueprintId + " @ " + origin + (materialized ? "" : " (pending)");
    }
}
