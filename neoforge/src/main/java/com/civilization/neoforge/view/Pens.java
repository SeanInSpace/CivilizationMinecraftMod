package com.civilization.neoforge.view;

import com.civilization.sim.culture.Culture;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.BuildingSizes;
import com.civilization.sim.settlement.Herd;
import com.civilization.sim.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The beasts, made real — and the rest of the life a town ought to have in it.
 *
 * <p>{@link Herd} is the ledger and this is the world it is a ledger <em>of</em>.
 * The rule between them is the one the whole mod runs on: the ledger is the
 * authority and the world is reconciled to it. A compound whose books say four
 * cows gets four cows the moment somebody is near enough to see them, and the
 * clock never conjures one where a player is standing.
 *
 * <p><strong>Spawn the missing, never delete the extra.</strong> An unloaded
 * chunk throws its animals away on its own schedule and a loaded one may have
 * anything in it a player drove there — so a pen short of its ledger is a pen
 * that lost beasts to the game and gets them back, and a pen over its ledger is
 * somebody's business and not ours. Nothing here has ever a reason to kill an
 * animal; the shepherd does that by hand, in {@link ShepherdWorker}, and pays
 * for it on the books.
 *
 * <p>The second half of the file is not livestock at all. A town with four cows
 * behind a fence and absolutely nothing else alive in it reads as a diorama, so
 * the compounds get loose fowl scratching in the yard, the storehouse gets a
 * cat, the inn's stable gets a horse in it and a guard or a shepherd gets a dog.
 * They are dressing: nothing counts them, nothing eats them, nothing breeds
 * them, and every one of them is capped and pinned against despawning so that
 * a town left running for a week does not become a menagerie.
 */
public final class Pens {

    /**
     * Beasts put down in one pass, across the whole town.
     *
     * <p>A pen forty head short — a town coming back after a long absence with
     * every chunk freshly loaded — would otherwise land forty entities in one
     * tick, on the frame the player walks over the hill. Two a pass fills any
     * real pen inside half a minute and is not felt.
     */
    private static final int SPAWN_PER_PASS = 2;

    /** Passes between reconciliations: the view layer's pass is twenty ticks. */
    private static final int PEN_BEAT = 4;

    /** Passes between sweeps of the loose life, which changes far more slowly. */
    private static final int DRESS_BEAT = 40;

    /** Loose fowl scratching about one compound's yard. */
    private static final int YARD_FOWL = 3;

    /** How far from the compound's edge a loose bird will be put down. */
    private static final int YARD_REACH = 4;

    /** Horses under the inn's stable roof. */
    private static final int STABLE_HORSES = 2;

    /** One cat to a storehouse, and it is the whole point of a storehouse. */
    private static final int STORE_CATS = 1;

    /** One dog to a town, at the heels of whoever is out in the fields. */
    private static final int TOWN_DOGS = 1;

    private static final String FOWL = "minecraft:chicken";
    private static final String CAT = "minecraft:cat";
    private static final String DOG = "minecraft:wolf";
    private static final String HORSE = "minecraft:horse";

    private static int beat;

    private Pens() {
    }

    /**
     * One pass of keeping the town's animals honest.
     *
     * <p>The single entry point, because the view layer should have one line
     * about beasts in it and not four.
     */
    public static void tend(ServerLevel level, Settlement settlement) {
        beat++;
        if (beat % PEN_BEAT == 0) {
            reconcile(level, settlement);
        }
        if (beat % DRESS_BEAT == 0) {
            dress(level, settlement);
        }
    }

    // --- the pens --------------------------------------------------------------

    /**
     * Put the ledger's beasts into the ledger's pens.
     *
     * @return whether anything was actually put down
     */
    public static boolean reconcile(ServerLevel level, Settlement settlement) {
        Culture culture = Culture.of(settlement.cultureId());
        List<String> kinds = Herd.kindsOf(culture);
        if (kinds.isEmpty()) {
            return false;
        }
        int budget = SPAWN_PER_PASS;
        for (Building farm : settlement.buildingsWithRole(BuildingRole.ANIMAL_FARM)) {
            if (budget <= 0) {
                break;
            }
            BlockPos base = toBlockPos(farm.origin());
            if (!farm.isMaterialized() || !level.isLoaded(base)) {
                continue;   // not drawn yet, or its chunks are away
            }
            for (String species : Herd.speciesOf(culture)) {
                int wanted = Herd.head(farm, species);
                if (wanted <= 0 || budget <= 0) {
                    continue;
                }
                EntityType<?> type = typeOf(species);
                if (type == null) {
                    continue;   // a culture naming a beast this game does not have
                }
                List<Integer> mine = pensOf(kinds, species);
                int alive = 0;
                for (int pen : mine) {
                    alive += countIn(level, penBox(base, kinds.size(), pen), type);
                }
                for (int pen : mine) {
                    while (alive < wanted && budget > 0) {
                        BlockPos spot = freeSpotIn(level, base, kinds.size(), pen);
                        if (spot == null) {
                            break;   // this pen is full of byre, post or beast
                        }
                        if (!put(level, type, spot)) {
                            break;
                        }
                        alive++;
                        budget--;
                    }
                }
            }
        }
        return budget < SPAWN_PER_PASS;
    }

    /** Which pens of the compound hold this species; the mire goblins keep two of fowl. */
    private static List<Integer> pensOf(List<String> kinds, String species) {
        List<Integer> out = new ArrayList<>();
        for (int pen = 0; pen < kinds.size(); pen++) {
            if (kinds.get(pen).equals(species)) {
                out.add(pen);
            }
        }
        return out;
    }

    /**
     * The box one pen occupies in the world.
     *
     * <p>Read off {@link Herd}, which is where the compound's geometry lives now,
     * so the box a beast is counted in is the box the fence was drawn around.
     * Feet at the course above the grass; three blocks of headroom, which is a
     * fence and its gap.
     */
    public static AABB penBox(BlockPos base, int pens, int index) {
        int rx = Herd.penHalfWidth();
        int firstRow = Herd.penFirstRow(pens, index);
        return new AABB(
                base.getX() - rx, base.getY() + 1.0, base.getZ() + firstRow,
                base.getX() + rx + 1.0, base.getY() + 4.0,
                base.getZ() + firstRow + Herd.PEN_DEPTH);
    }

    /** Every cell inside one pen a beast could stand on, nearest the middle first. */
    private static List<BlockPos> penFloor(BlockPos base, int pens, int index) {
        int rx = Herd.penHalfWidth();
        int firstRow = Herd.penFirstRow(pens, index);
        List<BlockPos> cells = new ArrayList<>();
        for (int row = 0; row < Herd.PEN_DEPTH; row++) {
            for (int dx = -rx; dx <= rx; dx++) {
                cells.add(base.offset(dx, 1, firstRow + row));
            }
        }
        cells.sort((a, b) -> Integer.compare(
                Math.abs(a.getX() - base.getX()), Math.abs(b.getX() - base.getX())));
        return cells;
    }

    /**
     * Somewhere inside a pen with nothing already in it.
     *
     * <p>The compound's own post stands in the middle of the first pen and the
     * byre stands at the head of it — see {@code TradeParts.byre} — so "the
     * center of the box" is not a free cell and never was. A beast put down
     * inside a hay bale falls through the world's floor or suffocates, which is
     * the whole reason this looks rather than assuming.
     */
    private static BlockPos freeSpotIn(ServerLevel level, BlockPos base, int pens, int index) {
        for (BlockPos cell : penFloor(base, pens, index)) {
            if (level.getBlockState(cell).isAir()
                    && level.getBlockState(cell.above()).isAir()
                    && !level.getBlockState(cell.below()).isAir()) {
                return cell;
            }
        }
        return null;
    }

    private static int countIn(ServerLevel level, AABB box, EntityType<?> type) {
        return level.getEntities((Entity) null, box,
                e -> e.getType() == type && e.isAlive()).size();
    }

    /**
     * Put one beast down and pin it there.
     *
     * <p>Persistence is not a nicety. A pen of sheep that vanishes overnight
     * because the mob cap decided they were surplus is a town whose ledger and
     * whose world disagree every morning, and reconciling that back would put
     * this class in the business of spawning four sheep a day forever.
     */
    private static boolean put(ServerLevel level, EntityType<?> type, BlockPos at) {
        Entity beast = type.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
        if (beast == null) {
            return false;
        }
        if (beast instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        // The town's animals are the town's, and for the dog it is not
        // decoration: a wild wolf hunts sheep and rabbits, so a loose one put
        // down beside the compound would spend its life eating the herd — and
        // since the pens are reconciled back to the ledger every few seconds, it
        // would spend the town's life eating the same sheep over and over. A
        // tame one only fights what its owner fights, and it has no owner.
        if (beast instanceof TamableAnimal pet) {
            pet.setTame(true, true);
        }
        return true;
    }

    // --- the loose life --------------------------------------------------------

    /** One sweep of the dressing: fowl, cats, a dog and the inn's horses. */
    public static void dress(ServerLevel level, Settlement settlement) {
        for (Building farm : settlement.buildingsWithRole(BuildingRole.ANIMAL_FARM)) {
            topUp(level, farm, FOWL, YARD_FOWL, yardReach());
        }
        for (Building store : settlement.buildings()) {
            if (store.role() == BuildingRole.STORE) {
                topUp(level, store, CAT, STORE_CATS, YARD_REACH);
                break;   // one cat to a town, whatever a town's mouse problem
            }
        }
        for (Building inn : settlement.buildings()) {
            if (inn.role() == BuildingRole.INN) {
                stable(level, inn);
                break;
            }
        }
        Building anchor = settlement.buildingWithRole(BuildingRole.ANIMAL_FARM);
        if (anchor == null) {
            anchor = settlement.buildingWithRole(BuildingRole.HALL);
        }
        if (anchor != null) {
            topUp(level, anchor, DOG, TOWN_DOGS, YARD_REACH);
        }
    }

    /** How far out of a compound a loose bird may scratch: clear of its own fence. */
    private static int yardReach() {
        return Herd.compoundSize().width() / 2 + YARD_REACH;
    }

    /**
     * Keep a handful of something alive near a building, and no more than a handful.
     *
     * <p>Counted before anything is put down, which is what makes this safe to
     * run forever: the cap is on what is <em>there</em> and not on what this has
     * spawned, so a bird that wandered off is replaced and a bird that did not
     * is left alone.
     */
    private static void topUp(ServerLevel level, Building near,
                              String entityId, int wanted, int reach) {
        BlockPos base = toBlockPos(near.origin());
        if (!near.isMaterialized() || !level.isLoaded(base)) {
            return;
        }
        EntityType<?> type = typeOf(entityId);
        if (type == null) {
            return;
        }
        AABB around = new AABB(base).inflate(reach, 4.0, reach);
        int alive = countIn(level, around, type);
        while (alive < wanted) {
            BlockPos spot = looseSpot(level, base, reach);
            if (spot == null || !put(level, type, spot)) {
                return;
            }
            alive++;
        }
    }

    /**
     * Grass near a building with room to stand on it.
     *
     * <p>Grass, dirt or podzol and nothing else, which is a deliberately narrow
     * rule and does the whole job: every road this mod lays is gravel, path or
     * masonry, so a spot that passes this is by construction not a street. A
     * chicken standing in the carriageway is the single most obvious way loose
     * dressing goes wrong.
     */
    private static BlockPos looseSpot(ServerLevel level, BlockPos base, int reach) {
        for (int ring = 2; ring <= reach; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;   // the ring itself, not the disc inside it
                    }
                    BlockPos foot = base.offset(dx, 1, dz);
                    if (!level.isLoaded(foot)) {
                        continue;
                    }
                    BlockState under = level.getBlockState(foot.below());
                    if ((under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.DIRT)
                            || under.is(Blocks.PODZOL) || under.is(Blocks.COARSE_DIRT))
                            && level.getBlockState(foot).isAir()
                            && level.getBlockState(foot.above()).isAir()) {
                        return foot;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A horse or two under the inn's lean-to.
     *
     * <p>Purely decorative and deliberately so: an inn with an empty stable is
     * an inn nobody has ever ridden to. The stalls are where
     * {@code CivicParts.stable} puts them — the flank at {@code side = -1}, with
     * the fodder in the middle stall — so the horses stand either side of the
     * hay.
     */
    private static void stable(ServerLevel level, Building inn) {
        BlockPos base = toBlockPos(inn.origin());
        if (!inn.isMaterialized() || !level.isLoaded(base)) {
            return;
        }
        EntityType<?> type = typeOf(HORSE);
        if (type == null) {
            return;
        }
        BuildingSizes.Size size = BuildingSizes.of("inn");
        int out = -(size.width() / 2 + 1);
        AABB stalls = new AABB(
                base.getX() + out - 1, base.getY() + 1.0, base.getZ() - 3,
                base.getX() + out + 1.0, base.getY() + 4.0, base.getZ() + 3.0);
        int alive = countIn(level, stalls, type);
        for (int dz = -1; dz <= 1 && alive < STABLE_HORSES; dz += 2) {
            BlockPos stall = base.offset(out, 1, dz);
            if (level.getBlockState(stall).isAir() && put(level, type, stall)) {
                alive++;
            }
        }
    }

    private static EntityType<?> typeOf(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);
    }

    static BlockPos toBlockPos(SimPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }
}
