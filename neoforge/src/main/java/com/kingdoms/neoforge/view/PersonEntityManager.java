package com.kingdoms.neoforge.view;

import com.kingdoms.neoforge.KingdomsAttachments;
import com.kingdoms.neoforge.KingdomsEntities;
import com.kingdoms.neoforge.KingdomsItems;
import com.kingdoms.neoforge.KingdomsMod;
import com.kingdoms.neoforge.entity.PersonEntity;
import com.kingdoms.neoforge.bridge.NeoForgeWorldBridge;
import com.kingdoms.neoforge.save.KingdomsSavedData;
import com.kingdoms.neoforge.world.BlueprintPlacer;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.view.EmbodimentPlanner;
import com.kingdoms.sim.world.SimWorld;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Executes the embodiment plan: spawns villagers for people players can see,
 * writes their state back and despawns them when nobody can.
 *
 * <p>All decisions live in {@link EmbodimentPlanner} in the loader-free core â€”
 * this class only carries them out. The invariants it maintains:
 * <ul>
 *   <li>at most one entity per person, ever;</li>
 *   <li>the {@code Person} record is the authority â€” entities are disposable
 *       views, culled on sight if they leak to disk;</li>
 *   <li>positions flow entity â†’ record every cycle, so a crash loses at most a
 *       second of movement and never a person.</li>
 * </ul>
 */
public final class PersonEntityManager {

    /** Game ticks between manager passes (20 = once a second). */
    public static final int TICK_INTERVAL = 20;

    /** Close enough to a destination counts as arrived; the wander goal takes over. */
    private static final double ARRIVE_RADIUS = 8.0;

    private static final double WALK_SPEED = 0.6;

    /** Civilians run (not stroll) for home while the settlement is threatened. */
    private static final double SHELTER_SPEED = 0.9;

    /** Hunger debuffs are reapplied every manager pass; this outlasts the gap. */
    private static final int EFFECT_REFRESH_TICKS = 40;

    /** Most construction blocks laid per second — the visible pace of building. */
    private static final int CONSTRUCTION_BLOCKS_PER_PASS = 6;

    /** Guards engage hostiles within this range, strike within melee reach. */
    private static final double GUARD_ENGAGE_RANGE = 20.0;
    private static final double GUARD_STRIKE_RANGE = 2.5;
    private static final double GUARD_CHARGE_SPEED = 0.9;
    private static final float GUARD_DAMAGE = 4.0F;

    private final ServerLevel level;
    private final SimWorld world;

    /** Person id â†’ the live view entity for that person. */
    private final Map<UUID, PersonEntity> tracked = new HashMap<>();

    public PersonEntityManager(ServerLevel level, SimWorld world) {
        this.level = Objects.requireNonNull(level, "level");
        this.world = Objects.requireNonNull(world, "world");
    }

    /** One pass: sync positions, release the unwatched, embody the watched, herd stragglers. */
    public void tick() {
        renderClaimBorders();
        boolean changed = false;
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                changed |= syncPositions(settlement);

                EmbodimentPlanner.Plan plan =
                        EmbodimentPlanner.plan(settlement, world.bridge(), world.settings());
                for (Person person : plan.toRelease()) {
                    release(person);
                    changed = true;
                }
                for (Person person : plan.toEmbody()) {
                    changed |= embody(person);
                }

                dailyRoutine(settlement);
                applyHungerEffects(settlement);
                guardCombat(settlement);
                changed |= advanceConstructionSites(settlement);
            }
        }
        reapOrphans();
        if (changed) {
            KingdomsSavedData.get(level).setDirty();
        }
    }

    /**
     * The visible half of construction: the active site rises toward the build
     * task's progress a few blocks a second, laid in mason's order, while the
     * builders stand over the work. The simulation still owns progress; this
     * only draws it.
     */
    private boolean advanceConstructionSites(Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            return false;
        }
        return BlueprintPlacer.advanceConstruction(
                level, settlement.buildQueue().getFirst(), CONSTRUCTION_BLOCKS_PER_PASS);
    }

    /**
     * Hunger made visible. Weak (60+) people move slowly; the severely starved
     * (90+) barely crawl and hit like children. Effects refresh each second so
     * they lift on their own once the person eats.
     */
    private void applyHungerEffects(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (!person.isEmbodied() || person.hunger() < Person.HUNGER_WEAK) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                continue;
            }
            if (person.hunger() >= Person.HUNGER_SEVERE) {
                view.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_REFRESH_TICKS, 1), null);
                view.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_REFRESH_TICKS, 0), null);
            } else {
                view.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_REFRESH_TICKS, 0), null);
            }
        }
    }

    /**
     * A person can die inside the simulation — starvation, off-screen raid math —
     * while their view entity still stands. The record is the authority: the
     * orphaned body collapses here, on screen, rather than living on as a ghost.
     */
    private void reapOrphans() {
        for (Map.Entry<UUID, PersonEntity> entry : List.copyOf(tracked.entrySet())) {
            if (world.settlementOf(new Person.Id(entry.getKey())).isPresent()) {
                continue;
            }
            PersonEntity view = entry.getValue();
            tracked.remove(entry.getKey());
            if (view != null && !view.isRemoved()) {
                view.hurtServer(level, level.damageSources().starve(), Float.MAX_VALUE);
                if (!view.isRemoved() && !view.isDeadOrDying()) {
                    view.discard();
                }
            }
        }
    }

    /**
     * Guards fight. Once a second each embodied guard picks the nearest hostile
     * in range: close enough, they strike; otherwise they close the distance.
     *
     * <p>Deliberately puppeteered from here rather than grafted onto the view
     * brain â€” vanilla villagers cannot fight, and mixing custom goals into a
     * brain-driven mob makes two AIs wrestle over the navigator. The hostiles
     * retaliate through normal vanilla anger, so guards genuinely can lose.
     */
    private void guardCombat(Settlement settlement) {
        for (Person person : settlement.residents()) {
            if (person.profession() != Profession.GUARD || !person.isEmbodied()) {
                continue;
            }
            PersonEntity guard = tracked.get(person.id().value());
            if (guard == null || guard.isRemoved()) {
                continue;
            }
            Monster target = nearestHostile(guard);
            if (target == null) {
                continue;
            }
            if (guard.distanceTo(target) <= GUARD_STRIKE_RANGE) {
                guard.swing(InteractionHand.MAIN_HAND);
                target.hurtServer(level, level.damageSources().mobAttack(guard), GUARD_DAMAGE);
            } else {
                guard.getNavigation().moveTo(target, GUARD_CHARGE_SPEED);
            }
        }
    }

    private Monster nearestHostile(PersonEntity guard) {
        AABB box = guard.getBoundingBox().inflate(GUARD_ENGAGE_RANGE);
        return level.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive).stream()
                .min(Comparator.comparingDouble(guard::distanceToSqr))
                .orElse(null);
    }

    /** Entity positions are truth while embodied â€” copy them into the records. */
    private boolean syncPositions(Settlement settlement) {
        boolean changed = false;
        for (Person person : settlement.residents()) {
            if (!person.isEmbodied()) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                // The entity vanished outside our control (chunk unloaded under it,
                // another mod removed it). The person is unharmed; drop the view
                // and let the next plan respawn it if anyone is still watching.
                tracked.remove(person.id().value());
                person.setEmbodied(false);
                changed = true;
                continue;
            }
            person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            changed = true;
        }
        return changed;
    }

    private boolean embody(Person person) {
        PersonEntity view = new PersonEntity(KingdomsEntities.PERSON.get(), level);
        SimPos pos = person.position();
        int y = world.bridge().surfaceHeight(pos);
        view.setPos(pos.x() + 0.5, y, pos.z() + 0.5);
        view.setCustomName(Component.literal(person.name() + " — " + pretty(person)));
        view.setCustomNameVisible(true);
        view.setPersistenceRequired();
        view.setData(KingdomsAttachments.PERSON_ID.get(), person.id().value());

        // Registered before addFreshEntity so the join hook recognises our own spawn.
        tracked.put(person.id().value(), view);
        if (!level.addFreshEntity(view)) {
            tracked.remove(person.id().value());
            return false;
        }
        person.setEmbodied(true);
        return true;
    }

    private void release(Person person) {
        PersonEntity view = tracked.remove(person.id().value());
        if (view != null && !view.isRemoved()) {
            person.setPosition(NeoForgeWorldBridge.toSimPos(view.blockPosition()));
            view.discard();
        }
        person.setEmbodied(false);
    }

    /**
     * The village day. Threatened civilians run home; at night everyone but the
     * watch turns in; by day people head to their work — farmers to the fields,
     * builders to the site, traders to the storehouse, guards to the tower,
     * idlers about their homes. The wander goal mills them around whatever spot
     * this chooses, so the town reads as lived-in rather than marched.
     */
    private void dailyRoutine(Settlement settlement) {
        boolean night = level.isDarkOutside();
        boolean underThreat = settlement.threatLevel() > 0;

        Map<UUID, SimPos> homes = new HashMap<>();
        for (Household household : settlement.households()) {
            if (household.isHoused()) {
                for (Person.Id member : household.members()) {
                    homes.put(member.value(), household.home());
                }
            }
        }

        for (Person person : settlement.residents()) {
            if (!person.isEmbodied()) {
                continue;
            }
            PersonEntity view = tracked.get(person.id().value());
            if (view == null || view.isRemoved()) {
                continue;
            }

            boolean guard = person.profession() == Profession.GUARD;
            SimPos home = homes.get(person.id().value());

            SimPos target;
            double speed;
            if (underThreat && !guard) {
                target = home != null ? home : settlement.centre();
                speed = SHELTER_SPEED;
            } else if (night && !guard) {
                target = home != null ? home : settlement.centre();
                speed = WALK_SPEED;
            } else {
                target = workplaceFor(settlement, person, home);
                speed = WALK_SPEED;
            }

            double dx = view.getX() - (target.x() + 0.5);
            double dz = view.getZ() - (target.z() + 0.5);
            double arrive = underThreat && !guard ? 2.0 : ARRIVE_RADIUS;
            if (dx * dx + dz * dz > arrive * arrive) {
                int y = world.bridge().surfaceHeight(target);
                view.getNavigation().moveTo(target.x() + 0.5, y, target.z() + 0.5, speed);
            }
        }
    }

    private SimPos workplaceFor(Settlement settlement, Person person, SimPos home) {
        return switch (person.profession()) {
            case FARMER -> nearestBuilding(settlement, "farm", person.position());
            case BUILDER -> settlement.buildQueue().isEmpty()
                    ? nearestBuilding(settlement, "hall", person.position())
                    : settlement.buildQueue().getFirst().origin();
            case TRADER -> nearestBuilding(settlement, "market", person.position());
            case GUARD -> nearestBuilding(settlement, "watchtower", person.position());
            case IDLER -> home != null ? home : settlement.centre();
        };
    }

    /** Nearest completed building whose blueprint path ends with the suffix, else the centre. */
    private static SimPos nearestBuilding(Settlement settlement, String pathSuffix, SimPos from) {
        SimPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Building building : settlement.buildings()) {
            if (!building.blueprintId().endsWith(pathSuffix)) {
                continue;
            }
            long d = building.origin().horizontalDistanceSq(from);
            if (d < bestDistance) {
                bestDistance = d;
                best = building.origin();
            }
        }
        return best != null ? best : settlement.centre();
    }

    /** Whether this exact entity is the live view we spawned for this person. */
    public boolean owns(UUID personId, Entity entity) {
        return tracked.get(personId) == entity;
    }

    /**
     * A view view died, so the person it represented dies with it. This is
     * the one place the view writes anything other than a position back into
     * the simulation â€” deliberate, and the seed of the Phase 3 defense loop.
     */
    public void onViewEntityDeath(LivingEntity view) {
        UUID personId = view.getData(KingdomsAttachments.PERSON_ID.get());
        tracked.remove(personId);

        Person.Id id = new Person.Id(personId);
        world.settlementOf(id).ifPresent(settlement -> {
            Person fallen = settlement.removePerson(id);
            if (fallen != null) {
                settlement.logEvent(world.stepsElapsed(), fallen.name() + " was killed");
                KingdomsMod.LOGGER.info("{} of {} was killed", fallen.name(), settlement.name());
                KingdomsSavedData.get(level).setDirty();
            }
        });
    }

    /** Write everything back and drop every view. Called as the server stops. */
    public void releaseAll() {
        for (Kingdom kingdom : world.kingdoms()) {
            for (Settlement settlement : kingdom.settlements()) {
                for (Person person : settlement.residents()) {
                    if (person.isEmbodied()) {
                        release(person);
                    }
                }
            }
        }
        tracked.clear();
        KingdomsSavedData.get(level).setDirty();
    }

    private static String pretty(Person person) {
        String name = person.profession().name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // --- claim borders ---

    /** How far from the border line a player still sees it. */
    private static final double BORDER_VIEW_RANGE = 64.0;

    /** Blocks between sparkles along the border. */
    private static final double BORDER_POINT_SPACING = 3.0;

    /**
     * Players holding a Founding Charter see every nearby settlement's claim as a
     * ring of green sparkles laid over the terrain. Server-side particles only â€”
     * no client rendering code, so it works for vanilla-client observers too.
     */
    private void renderClaimBorders() {
        for (ServerPlayer player : level.players()) {
            if (!player.getMainHandItem().is(KingdomsItems.FOUNDING_CHARTER.get())
                    && !player.getOffhandItem().is(KingdomsItems.FOUNDING_CHARTER.get())) {
                continue;
            }
            for (Kingdom kingdom : world.kingdoms()) {
                for (Settlement settlement : kingdom.settlements()) {
                    drawBorderNear(player, settlement);
                }
            }
        }
    }

    private void drawBorderNear(ServerPlayer player, Settlement settlement) {
        double radius = settlement.claimRadius();
        SimPos centre = settlement.centre();
        double dx = player.getX() - centre.x();
        double dz = player.getZ() - centre.z();
        if (Math.sqrt(dx * dx + dz * dz) > radius + BORDER_VIEW_RANGE) {
            return;
        }
        int points = Math.max(16, (int) (2 * Math.PI * radius / BORDER_POINT_SPACING));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = centre.x() + radius * Math.cos(angle) + 0.5;
            double z = centre.z() + radius * Math.sin(angle) + 0.5;
            double px = player.getX() - x;
            double pz = player.getZ() - z;
            if (px * px + pz * pz > BORDER_VIEW_RANGE * BORDER_VIEW_RANGE) {
                continue;   // draw only the arc the player can actually see
            }
            int y = world.bridge().surfaceHeight(new SimPos((int) Math.floor(x), centre.y(), (int) Math.floor(z)));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.6, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

}

