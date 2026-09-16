package com.civilization.neoforge.view;

import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.neoforge.world.Chimneys;
import com.civilization.sim.culture.Race;
import com.civilization.sim.person.Curfew;
import com.civilization.sim.person.Leisure;
import com.civilization.sim.person.NightRest;
import com.civilization.sim.person.Person;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * What a town sounds like and smells of.
 *
 * <p>Everything else about a settlement is a decision with a consequence. This
 * is the one part that is purely for the person standing in it: smoke over the
 * roofs at dusk, a voice from somebody across the square, and the bell at dawn.
 * None of it feeds anybody, nothing reads it back, and a town with a player in
 * it and a town without one produce exactly the same grain — which is the only
 * thing that had to be true for this to be allowed to exist at all.
 *
 * <p>Held apart from {@code PersonEntityManager} for that reason rather than for
 * tidiness. A file whose every line is cosmetic is a file that can be read
 * quickly when somebody is looking for the thing that broke the economy, and
 * ruled out.
 */
final class Ambience {

    // --- what it costs --------------------------------------------------------

    /**
     * Manager passes between puffs of smoke: 2, so about one every two seconds.
     *
     * <p>Campfire smoke lasts far longer than that on screen, so the column over
     * a roof is continuous at this rate and would be no more continuous at twice
     * it. The whole sweep is also skipped in daylight, which is two thirds of the
     * clock.
     */
    private static final int SMOKE_EVERY = 2;

    /** How far a player has to be for a town's chimneys to be worth drawing. */
    private static final double SMOKE_RANGE = 64.0;

    /** How high above the pot the smoke starts, so it clears the masonry. */
    private static final double SMOKE_RISE = 1.1;

    /**
     * One in this many passes, a given settler says something: 1 in 140.
     *
     * <p>Per person per second, so a village of twenty makes a noise about every
     * seven seconds and a town of sixty about every two — which is roughly what
     * a vanilla village sounds like, and is the figure this was tuned against.
     * Rarer would be a ghost town; more often is a farmyard.
     */
    private static final int VOICE_ODDS = 140;

    /** How loud one settler is. Vanilla villagers are 1.0; a town is many of them. */
    private static final float VOICE_VOLUME = 0.6F;

    /**
     * How long after dawn the bell may still be rung: 300 ticks.
     *
     * <p>Wide enough that a server which stuttered, or a manager pass that
     * arrived late, still catches the morning; short enough that the ring is
     * unmistakably dawn. Nothing rings twice — see {@link #rungOn}.
     */
    private static final long DAWN_WINDOW_TICKS = 300L;

    /** How far up a hall or a tower the bell might be. */
    private static final int BELL_SEARCH_HEIGHT = 24;

    // --- state ----------------------------------------------------------------

    private final ServerLevel level;
    private final SimWorld world;
    private final Chimneys chimneys;
    private final Function<UUID, PersonEntity> viewOf;

    /**
     * Which day each town last rang dawn on, by settlement.
     *
     * <p>Not saved. A reload on the morning of the fourth day means the bell has
     * not been rung that day as far as anything here knows, so it rings — which
     * is a bell one morning too many at worst, and never a town that stops
     * ringing because it was restarted.
     */
    private final Map<UUID, Long> rungOn = new HashMap<>();

    private int beat;

    Ambience(ServerLevel level, SimWorld world, Function<UUID, PersonEntity> viewOf) {
        this.level = Objects.requireNonNull(level, "level");
        this.world = Objects.requireNonNull(world, "world");
        this.viewOf = Objects.requireNonNull(viewOf, "viewOf");
        this.chimneys = new Chimneys(level);
    }

    /** One pass over one town: its smoke, its voices, and its morning. */
    void tend(Settlement settlement) {
        long clock = level.getDefaultClockTime();
        ringAtDawn(settlement, clock);
        voices(settlement);
        if (++beat % SMOKE_EVERY == 0) {
            smoke(settlement, clock);
        }
    }

    // --- smoke -----------------------------------------------------------------

    /**
     * Smoke from every chimney in the town, once the light starts to go.
     *
     * <p>Evening and night, on the same reckoning the curfew uses, because that
     * is when a fire is lit: a hearth going at noon in summer reads as a house on
     * fire. The hour comes from {@code Leisure.hourOf} rather than from a figure
     * of its own so that the evening the town walks home in and the evening its
     * fires are lit in are the same evening.
     */
    private void smoke(Settlement settlement, long clock) {
        if (Leisure.hourOf(clock, Curfew.LEAD_TICKS) == Leisure.Hour.DAY) {
            return;
        }
        if (!world.bridge().playerWithin(settlement.center(), SMOKE_RANGE)) {
            return;
        }
        for (Building building : settlement.buildings()) {
            if (!building.isMaterialized() || !Chimneys.hasAFire(settlement, building)) {
                continue;
            }
            for (BlockPos pot : chimneys.potsOf(building)) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pot.getX() + 0.5, pot.getY() + SMOKE_RISE, pot.getZ() + 0.5,
                        1, 0.02, 0.0, 0.02, 0.005);
            }
        }
    }

    // --- voices ----------------------------------------------------------------

    /**
     * Somebody says something.
     *
     * <p>Vanilla's villager chatter, reused rather than recorded, because it is
     * the sound a player already reads as "a village is here" and because a mod
     * that ships its own murmur has to ship a whole sound pack to go with it.
     *
     * <p>The race is in the pitch and nowhere else, which is the same decision
     * {@code PersonRenderer} makes about the model: an orc is a big man rather
     * than a different creature, so he has the same voice pitched down. A goblin
     * is the other end of the same line.
     *
     * <p>Sleepers are silent, and so is anybody a hostile is standing next to —
     * a town murmuring pleasantly through a raid is worse than a silent one.
     */
    private void voices(Settlement settlement) {
        if (settlement.alarm().isRaised()) {
            return;
        }
        long now = level.getGameTime();
        for (Person person : settlement.residents()) {
            if (!person.isEmbodied()) {
                continue;
            }
            PersonEntity view = viewOf.apply(person.id().value());
            if (view == null || view.isRemoved() || view.isSleeping() || view.isInDanger()) {
                continue;
            }
            if (Math.floorMod(mix(person.id().value().hashCode(), now), VOICE_ODDS) != 0) {
                continue;
            }
            level.playSound(null, view.blockPosition(), SoundEvents.VILLAGER_AMBIENT,
                    SoundSource.NEUTRAL, VOICE_VOLUME, pitchOf(view.race()));
        }
    }

    /**
     * What a race sounds like: a human at vanilla's own pitch, an orc below it,
     * a goblin above.
     *
     * <p>Derived from {@code Race.paceFactor} deliberately rather than tabled
     * separately — the same one number already says "bigger and slower" and
     * "smaller and quicker", and a second table would be a second thing to keep
     * in step. Squared, because a tenth off the pace is a tenth, and a voice a
     * tenth lower is not a different voice.
     */
    private static float pitchOf(Race race) {
        double factor = race.paceFactor();
        return (float) Math.max(0.5, Math.min(2.0, factor * factor));
    }

    // --- the bell ----------------------------------------------------------------

    /**
     * The town rings its bell at dawn, once.
     *
     * <p>The bell already has one reason to ring — {@code PersonEntityManager}
     * swings it when the alarm goes up — and this is the second. A bell that only
     * ever meant danger is a bell nobody wants to hear; a bell that also opens the
     * day is a bell that belongs to the town.
     *
     * <p>Keyed on the day number rather than on a flag, so the question "has this
     * town rung this morning" has an answer that survives the clock being set
     * forward and cannot be left true by a pass that was skipped.
     */
    private void ringAtDawn(Settlement settlement, long clock) {
        long intoDay = Math.floorMod(clock, NightRest.DAY);
        if (intoDay >= DAWN_WINDOW_TICKS) {
            return;
        }
        long day = Math.floorDiv(clock, NightRest.DAY);
        Long last = rungOn.get(settlement.id().value());
        if (last != null && last == day) {
            return;
        }
        rungOn.put(settlement.id().value(), day);
        BlockPos bell = findBell(settlement);
        if (bell == null) {
            return;   // no tower, no hall, no bell: the morning is silent
        }
        if (level.getBlockEntity(bell) instanceof BellBlockEntity ringing) {
            ringing.onHit(Direction.NORTH);
        }
        level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    /**
     * The bell on this town's hall or its watchtower.
     *
     * <p>Its own search rather than the alarm's, and it looks at the hall first:
     * a settlement has a hall long before it has a tower, so a dawn that only
     * rang from a watchtower would be a dawn no young town ever heard. The alarm
     * keeps its own narrower search — what rings for danger is the watch's bell,
     * and that is a decision rather than an oversight.
     */
    private BlockPos findBell(Settlement settlement) {
        BlockPos onHall = null;
        for (Building building : settlement.buildings()) {
            boolean hall = building.role() == com.civilization.sim.settlement.BuildingRole.HALL;
            boolean tower = building.blueprintId().contains("watchtower");
            if (!hall && !tower) {
                continue;
            }
            BlockPos origin = new BlockPos(building.origin().x(), building.origin().y(),
                    building.origin().z());
            if (!level.isLoaded(origin)) {
                continue;
            }
            for (int dy = 0; dy <= BELL_SEARCH_HEIGHT; dy++) {
                BlockPos at = origin.above(dy);
                if (!level.getBlockState(at).is(Blocks.BELL)) {
                    continue;
                }
                if (hall) {
                    return at;
                }
                if (onHall == null) {
                    onHall = at;
                }
                break;
            }
        }
        return onHall;
    }

    /** The same mixer the rest of the mod uses; see {@code Leisure}. */
    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
