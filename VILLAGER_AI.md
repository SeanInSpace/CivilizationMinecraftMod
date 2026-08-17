# How vanilla villager AI actually works

A plain-language tour of Minecraft's villager brain, written against the real decompiled 26.2 source on this machine — not folklore. Read this first; dip into the code after, if at all.

## Where to read more

- **The exact source, on your disk** (this is the ground truth; open it in any IDE or unzip it):
  `neoforge/build/moddev/artifacts/minecraft-patched-26.2.0.59-sources.jar`
  - `net/minecraft/world/entity/npc/villager/Villager.java` — `registerBrainGoals` is the master list
  - `net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java` — every behaviour, grouped by activity (surprisingly readable once you have this document)
  - `net/minecraft/world/entity/ai/memory/MemoryModuleType.java` — everything a villager can "know"
  - `net/minecraft/world/entity/ai/sensing/` — how knowledge gets in
- **[minecraft.wiki/w/Villager](https://minecraft.wiki/w/Villager)** — the best prose reference for observable behaviour: schedules, job sites, breeding rules, gossip, zombie sieges. Player-perspective rather than code-perspective, and kept current.
- **[docs.neoforged.net/docs/entities/](https://docs.neoforged.net/docs/entities/)** — the modder's view: entity anatomy, and where AI hooks live.

---

## Two AI systems, not one

Minecraft has two entirely separate mob-AI systems living side by side:

**The Goal system** (the old one — zombies, cows, most mobs). A mob holds a flat list of *goals* with priorities: "float in water", "attack my target", "wander randomly". Each tick, the highest-priority goal whose conditions hold gets to run. Simple, stateless, easy to extend — but it can't represent *knowledge* ("my bed is over there") or *routine* ("it's morning, so work").

**The Brain system** (villagers, piglins, axolotls, and other newer mobs). Built around stored knowledge and a daily schedule. Far more capable, far more tangled. This is the one people mean by "villager AI".

## The Brain, in four ideas

### 1. Memories — what the villager knows

A brain is, at its core, a typed key-value store. Each key is a `MemoryModuleType`; the value is a fact, optionally with an expiry time. Real examples:

- `HOME` — the position of my claimed bed
- `JOB_SITE` — my claimed workstation
- `MEETING_POINT` — the village bell
- `NEAREST_HOSTILE`, `HURT_BY` — danger, recently seen or felt
- `INTERACTION_TARGET` — who I'm socialising with
- `WALK_TARGET`, `LOOK_TARGET` — what I'm currently doing with my legs and eyes

Everything downstream is just code reading and writing these slots. "Villager AI" is mostly *bookkeeping about memories*.

### 2. Sensors — how knowledge gets in

Sensors run every few ticks and write memories. One scans for nearby living entities and fills `NEAREST_LIVING_ENTITIES` and `NEAREST_HOSTILE`; another watches for beds and job blocks; the villager-specific ones track the last slept time, golems seen, and so on. Behaviours never scan the world directly — they read what sensors wrote. That separation is the system's best design idea.

### 3. Activities and the schedule — what mode I'm in

Behaviours are grouped into **activities**: `CORE`, `WORK`, `REST`, `MEET`, `PLAY`, `IDLE`, `PANIC`, `RAID`, plus a couple more. A **schedule** maps time-of-day to activity — adult villagers roughly: wake → **WORK** at the job site → **MEET** at the bell in the afternoon → **IDLE** socialising → **REST** in bed at night. Babies get a schedule that's mostly **PLAY**. (In 26.x the schedule constants moved into `EnvironmentAttributes`; the idea is unchanged.)

`CORE` runs *always*, regardless of schedule — swimming, opening doors, panicking, waking up, answering the bell — and `UpdateActivityFromSchedule` sits at the bottom of every package, flipping the villager to whatever the clock says next.

### 4. Behaviours — what actually runs

Each activity is a list of `(priority, behaviour)` pairs; a behaviour declares which memories must be present or absent for it to start, then reads and writes memories while it runs. From the actual 26.2 `VillagerGoalPackages`:

- **CORE**: swim; open doors; look at things; panic trigger; wake up; react to the bell; set raid status; validate that my claimed bed/job site still exist; *acquire* a job site or bed if I lack one; assign my profession from my job site.
- **WORK**: work at my job block (farmers get a composter variant); stroll around near it; harvest and bonemeal farmland (farmers); **show trades to players**; give gifts to the hero of the village.
- **MEET/IDLE**: gather at the bell; socialise with the nearest villager (which is when *gossip* transfers); wander the village; trade.
- **REST**: walk home; sleep in the claimed bed; mill about indoors if homeless.
- **PLAY** (babies): play tag; jump on beds.

Notice what this means: a villager's famous behaviours — claiming beds, commuting to work, gathering at the bell, fleeing zombies — are all just entries in these lists, gated on memory slots.

## The supporting cast

- **POI (points of interest).** Beds, job blocks and bells register themselves in a per-chunk POI index when placed. Villagers *claim* a POI (one owner each) and remember it. This is why breaking a bed matters: the POI vanishes, `ValidateNearbyPoi` notices, the memory clears, and the villager hunts for a new one.
- **Gossip.** Each villager stores reputation entries about players (major/minor positive/negative, trading credit). Socialising at the bell spreads them; gossip decays over time; cured zombie-villagers generate large positive gossip. Prices follow reputation.
- **Panic and raids.** `VillagerPanicTrigger` in CORE overrides the schedule when hurt or when a hostile is remembered nearby; raids swap in raid-specific behaviour packages (hide, celebrate).
- **Breeding** is not in the brain proper: it needs food in the villager's inventory *and* spare claimed beds — which is why village growth is really *bed* growth.

## Why Kingdoms replaced it

Two reasons, one philosophical and one practical.

Philosophical: in this mod, **the simulation is the authority and the entity is a disposable view**. The brain assumes the opposite — the entity *is* the villager, and all state (home, job, gossip) lives inside it, evaporating on despawn. Everything Kingdoms cares about (family, home, job, history) lives in `Person`/`Settlement` records that exist whether or not any entity does; a brain would be a second, competing source of truth.

Practical: the brain owns the navigator. External control — "guard, go fight that zombie" — fights the brain's own `WALK_TARGET` writes, and the result is a mob that stutters between two masters. That is why guards were puppeteered from the manager even when views were vanilla villagers.

**What ours does instead:** the view entity (`PersonEntity`, a plain `PathfinderMob` with the *Goal* system) carries only ambience — float, wander, look at players. Everything meaningful is driven from outside, by the records: the manager walks strays back to their family's house, marches guards at hostiles, and writes positions back each second. The vanilla brain's jobs are done by the sim: `JobPlanner` is our "assign profession", `PopulationPlanner` our beds-and-breeding, the settlement event log our gossip.
