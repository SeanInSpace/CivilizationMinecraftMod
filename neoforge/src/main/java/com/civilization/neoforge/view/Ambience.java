package com.civilization.neoforge.view;

import com.civilization.neoforge.CivilizationConfig;
import com.civilization.neoforge.CivilizationMod;
import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.neoforge.world.Bells;
import com.civilization.neoforge.world.Chimneys;
import com.civilization.sim.culture.Race;
import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Curfew;
import com.civilization.sim.person.Leisure;
import com.civilization.sim.person.NightRest;
import com.civilization.sim.person.Person;
import com.civilization.sim.settlement.Building;
import com.civilization.sim.settlement.BuildingRole;
import com.civilization.sim.settlement.FoodPlanner;
import com.civilization.sim.settlement.Settlement;
import com.civilization.sim.settlement.SmithPlanner;
import com.civilization.sim.view.WorkTheatre;
import com.civilization.sim.world.SimWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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

    /**
     * How near a <em>fire</em> a player has to be for its smoke to be drawn: 64.
     *
     * <p>Per building, not per town. This was once measured to the middle of the
     * settlement, which made it a rule about town size rather than about
     * eyesight: a town wider than a hundred and twenty-eight blocks had roofs
     * that could never smoke no matter where anybody stood, and a player beside
     * one of them was refused while a chimney they could not see was drawn.
     * Sixty-four blocks is about where a campfire plume stops being legible, and
     * that is a fact about the particle rather than about the settlement.
     */
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

    // --- state ----------------------------------------------------------------

    private final ServerLevel level;
    private final SimWorld world;
    private final Chimneys chimneys;
    private final Bells bells;
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
        this.bells = new Bells(level);
    }

    /** The bell finder, so the alarm rings the same bell the morning does. */
    Bells bells() {
        return bells;
    }

    /** One pass over one town: its smoke, its voices, its trades and its morning. */
    void tend(Settlement settlement) {
        long clock = level.getDefaultClockTime();
        ringAtDawn(settlement, clock);
        voices(settlement);
        trades(settlement);
        if (++beat % SMOKE_EVERY == 0) {
            smoke(settlement, clock);
        }
    }

    // --- the trades ---------------------------------------------------------------

    /**
     * The smith strikes, the carpenter cuts, the miller grinds.
     *
     * <p>What the town's indoor work sounds like, and until this it sounded like
     * nothing at all. Outdoor work is audible by accident — felling a tree and
     * cutting a seam go through {@code destroyBlock} and vanilla makes the noise
     * — so a lumberjack read as working and a smith standing in a room with an
     * anvil in it read as switched off.
     *
     * <p><strong>It is worth nothing and must stay worth nothing.</strong> The
     * whole of this method is a sound, an arm and a handful of particles;
     * {@code WorkTheatre} is a gate rather than a step and every fact it reads is
     * somebody else's ledger quoted, never written. A town with a player in it
     * and a town without one make exactly the same tools, the same bread and the
     * same pre-cut timber — which is the same promise the smoke above makes, said
     * about the busy half of the town instead of the sleeping half.
     *
     * <p>Gated on the town's own books before it is gated on anything else: a
     * forge with no iron in it is silent, and that matters more than the range
     * check does. A player who learned to read the hammering as "the smithy is
     * working" would have learned something true, and a hammer over an empty
     * rack would make it false.
     */
    private void trades(Settlement settlement) {
        if (settlement.alarm().isRaised()) {
            return;   // nobody is at a bench during a raid
        }
        boolean forge = SmithPlanner.hasWorkInFront(settlement);
        boolean mill = FoodPlanner.millHasWork(settlement);
        boolean bench = settlement.buildingWithRole(BuildingRole.CARPENTRY) != null
                && !settlement.buildQueue().isEmpty();
        if (!forge && !mill && !bench) {
            return;   // the whole town's indoor work is idle; nothing to draw
        }
        long now = level.getGameTime();
        for (Person person : settlement.residents()) {
            WorkTheatre.Trade trade = WorkTheatre.tradeOf(person.profession());
            if (trade == null) {
                continue;
            }
            boolean busy = switch (trade) {
                case SMITH -> forge;
                case MILLER -> mill;
                case CARPENTER -> bench;
            };
            if (!busy) {
                continue;
            }
            UUID id = person.id().value();
            PersonEntity view = viewOf.apply(id);
            boolean bodied = person.isEmbodied() && view != null && !view.isRemoved()
                    && !view.isSleeping() && !view.isInDanger();
            SimPos where = bodied
                    ? new SimPos(view.getBlockX(), view.getBlockY(), view.getBlockZ())
                    : settlement.center();
            WorkTheatre.Scene work = new WorkTheatre.Scene(
                    bodied && world.bridge().playerWithin(where, WorkTheatre.WATCH_RANGE),
                    bodied && isAtBench(settlement, trade, view),
                    true, bodied);
            Long last = struckAt.get(id);
            if (!WorkTheatre.strikes(work, last == null ? WorkTheatre.NEVER : last,
                    now, WorkTheatre.gapFor(id))) {
                continue;
            }
            struckAt.put(id, now);
            strike(trade, view);
        }
    }

    /**
     * Whether this worker is standing at the building their trade is done in.
     *
     * <p>Measured against the building's origin rather than against the anvil,
     * because the origin is the one position a {@code Building} is guaranteed to
     * have and the furniture inside it is the blueprint's business. {@code AT_WORK}
     * is four blocks, which is inside a workshop and outside the street.
     */
    private boolean isAtBench(Settlement settlement, WorkTheatre.Trade trade,
                              PersonEntity view) {
        Building shop = settlement.buildingWithRole(switch (trade) {
            case SMITH -> BuildingRole.SMITH;
            case MILLER -> BuildingRole.MILL;
            case CARPENTER -> BuildingRole.CARPENTRY;
        });
        if (shop == null) {
            return false;
        }
        double dx = view.getX() - (shop.origin().x() + 0.5);
        double dz = view.getZ() - (shop.origin().z() + 0.5);
        return dx * dx + dz * dz <= WorkTheatre.AT_WORK * WorkTheatre.AT_WORK;
    }

    /**
     * One blow, one cut, one turn of the stone.
     *
     * <p>Vanilla's own sounds rather than a sound pack of ours, for the reason
     * the voices use vanilla's villager murmur: a mod that ships its own noises
     * has to ship a whole resource pack to go with them, and a player already
     * reads an anvil as an anvil.
     *
     * <p>Quiet. The anvil at full volume is one of the loudest sounds in the
     * game and a forge in the middle of a village would be unbearable within a
     * minute; a third of it is somebody working in the next building.
     */
    private void strike(WorkTheatre.Trade trade, PersonEntity view) {
        view.swing(InteractionHand.MAIN_HAND);
        BlockPos at = view.blockPosition();
        switch (trade) {
            case SMITH -> {
                level.playSound(null, at, SoundEvents.ANVIL_USE, SoundSource.BLOCKS,
                        ANVIL_VOLUME, 0.9F + level.getRandom().nextFloat() * 0.2F);
                // Sparks off the work, at chest height where the hammer is.
                level.sendParticles(ParticleTypes.CRIT, view.getX(),
                        view.getY() + SPARK_HEIGHT, view.getZ(), SPARKS,
                        0.2, 0.1, 0.2, 0.02);
                level.sendParticles(ParticleTypes.LAVA, view.getX(),
                        view.getY() + SPARK_HEIGHT, view.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
            case CARPENTER -> {
                level.playSound(null, at, SoundEvents.WOOD_HIT, SoundSource.BLOCKS,
                        BENCH_VOLUME, 0.8F + level.getRandom().nextFloat() * 0.3F);
                level.playSound(null, at, SoundEvents.AXE_STRIP, SoundSource.BLOCKS,
                        BENCH_VOLUME * 0.5F, 1.1F);
            }
            case MILLER -> {
                level.playSound(null, at, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS,
                        STONE_VOLUME, 0.7F + level.getRandom().nextFloat() * 0.2F);
                // Flour off the stone, which is the only thing a mill produces
                // that anybody can see.
                level.sendParticles(ParticleTypes.SMOKE, view.getX(),
                        view.getY() + SPARK_HEIGHT, view.getZ(), 2,
                        0.2, 0.05, 0.2, 0.0);
            }
        }
    }

    /** How loud one blow of a hammer is. Vanilla's anvil is 1.0 and is far too much. */
    private static final float ANVIL_VOLUME = 0.35F;

    /** A saw and a mallet, quieter again: a bench is not a forge. */
    private static final float BENCH_VOLUME = 0.4F;

    /** The stone: a low grind, and the one that runs longest. */
    private static final float STONE_VOLUME = 0.3F;

    /** How high above the feet the work is: chest height. */
    private static final double SPARK_HEIGHT = 1.1;

    /** Sparks off one blow. */
    private static final int SPARKS = 4;

    /**
     * When each worker last struck, by person.
     *
     * <p>Not saved, and there is nothing to save: the whole of it is "do not
     * make this noise again for two seconds". A reload means one extra hammer
     * blow, which is not a thing anybody can notice.
     */
    private final Map<UUID, Long> struckAt = new HashMap<>();

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
        // The cheap refusal first, and it is about the town rather than about
        // any one roof: a town nobody is anywhere near is skipped without its
        // buildings being walked at all. The claim radius plus a chimney's own
        // range, so that it is wide enough to take in the whole of a sprawling
        // town and can never itself be the thing that silences a roof — which
        // is exactly what a bare sixty-four to the middle used to be.
        if (!world.bridge().playerWithin(settlement.center(),
                settlement.claimRadius() + SMOKE_RANGE)) {
            return;
        }
        for (Building building : settlement.buildings()) {
            if (!building.isMaterialized() || !Chimneys.hasAFire(settlement, building)) {
                continue;
            }
            // And then per roof, which is the whole of the fix. Smoke was gated
            // on the player being within sixty-four blocks of the town's
            // *centre*, so in a town a hundred and seventy blocks across a
            // player standing with their nose against an outlying cottage was
            // outside the gate and that cottage could never smoke — while a
            // chimney two hundred blocks away on the far side of the middle
            // could. Distance to the fire is the only thing that decides
            // whether its smoke is worth sending, because distance to the fire
            // is the only thing that decides whether anybody can see it.
            if (!world.bridge().playerWithin(building.origin(), SMOKE_RANGE)) {
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
        // One line a morning, and it exists because the absence of one cost a
        // playtest a bisecting search with `clone ... filtered minecraft:bell`
        // to establish what the log could have said outright: the town has a
        // bell, it is six blocks from where the search was looking, and the
        // morning is silent. Debug-gated like every other diagnostic here.
        if (CivilizationConfig.debugCommandsEnabled()) {
            CivilizationMod.LOGGER.info("DAWNBELL {} day {} bell {}",
                    settlement.name(), day,
                    bell == null ? "NOT FOUND — the morning is silent"
                            : bell.toShortString());
        }
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
     * <p>{@link Bells}, which searches the building's whole footprint rather
     * than the column above its origin. This used to have a search of its own
     * and the alarm had another, and both walked one column — so both missed a
     * belfry that sits six blocks off the middle of a vale hall, which is where
     * the blueprint puts it. One finder now, shared with the alarm, so a bell
     * that can be rung for danger is a bell that can be rung for the morning.
     */
    private BlockPos findBell(Settlement settlement) {
        return bells.of(settlement);
    }

    /** The same mixer the rest of the mod uses; see {@code Leisure}. */
    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
