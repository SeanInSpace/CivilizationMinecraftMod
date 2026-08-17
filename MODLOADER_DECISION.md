# Mod Loader Decision — Minecraft 26.2

**Date:** 2026-08-16
**Project:** Millénaire-class civilization simulation — NPC settlements with individually-simulated inhabitants, self-directed building and expansion, autonomous defense against mobs, scaling to sprawling multi-village kingdoms

---

## Recommendation: **NeoForge**

At town scale this was a close call. At **Millénaire-class kingdom scale it stops being close** — NeoForge wins more clearly, and the margin widens as ambition grows.

The reasons shift, though. See [Revised reasoning at kingdom scale](#revised-reasoning-at-kingdom-scale) — the per-entity data argument gets *weaker* at this scale, while the content-breadth arguments get considerably stronger.

---

## The empirical answer

Two reference projects settle this better than any feature comparison, because both are the exact thing being proposed here:

**Millénaire itself chose NeoForge — from scratch, in 2026.**
Version 9.0.0-beta.2 (released 2026-07-25) is described by its own maintainers as *"a complete rewrite for Minecraft 1.21.1 and NeoForge."* Every prior release, back to 1.6.1, was Forge. This was a clean-slate architectural decision with no legacy constraint forcing the loader choice — a team that had already built this exact mod once, rebuilding it with full knowledge of where the bodies are buried, picked NeoForge.

**MineColonies — the most ambitious colony sim in modded Minecraft — is officially NeoForge/Forge.**
Individual NPC workers, progressive construction, research trees, raids, colony management at scale. Fabric support exists only as an unofficial community port not affiliated with or supported by the official team.

That is both of the closest existing analogs, independently, landing on the Forge lineage. It is not proof Fabric *cannot* do this. It is strong evidence about where the path of least resistance runs for this specific genre of mod.

---

## The thing most loader comparisons get wrong

Nearly all of your actual AI work is **loader-agnostic vanilla class work**.

Individual entity AI in modern Minecraft means the `Brain` system — `Behavior`, `Sensor`, `MemoryModuleType`, `Activity`, `Schedule` — which is what villagers and piglins already use, plus the older `Goal`/`GoalSelector` system that most mobs still use. Both loaders give you identical, unrestricted access to all of it. Neither has a "better AI API," because neither has an AI API at all. You are extending Mojang's classes either way.

So the loader choice is **not** about the AI. It is about everything wrapped around the AI:

- Where does per-villager state (job, relationships, grudges, schedule, morale) get **persisted**?
- How do you **hook** mob spawns, targeting decisions, damage, and death without owning those classes?
- How do you store **town-scoped** state (threat level, claimed territory, alert status)?
- How much of that is first-party and maintained for you vs. a mixin you own forever?

That reframing is what drives the recommendation.

---

## Revised reasoning at kingdom scale

Doubling the ambition does not just scale the original arguments — it **reorders** them. One gets weaker, four get considerably stronger.

### What gets weaker: the per-entity data argument

At town scale I ranked Data Attachments first. At kingdom scale it drops down the list, and it is worth being clear about why.

Kingdom state **cannot live on entities at all.** A kingdom's roster, treasury, diplomatic standing, building queues, and territory claims must persist while every villager is unloaded, while chunks are unloaded, and across villager deaths. That state belongs in `SavedData` / `DimensionDataStorage` — which is **pure vanilla and identical on both loaders.**

So the bulk of your most important persistence layer is loader-neutral. Attachments remain genuinely useful — chunk attachments are an excellent fit for territory claims, and entity attachments still carry the per-villager slice — but they are no longer the deciding factor. Fabric is not meaningfully disadvantaged here.

### What gets stronger: everything that scales with content breadth

**1. Registries.** Kingdoms × cultures × building types × professions × trade goods × diplomatic states. NeoForge's `DeferredRegister` and custom registry support is ergonomic at 20 content types and load-bearing at 500. Hand-rolling registration is fine for a town mod and genuinely painful at Millénaire scale.

**2. Datapack-driven content — the single most important one.** Millénaire's defining architectural trait was that its cultures, buildings, and villager definitions lived in data files, not code. That is *why* it could support Norman, Japanese, Mayan, Byzantine, Inuit and Seljuk cultures without the codebase collapsing. At twice that ambition this is not optional — you need to add a culture by writing data, not by writing Java. Codec-based datapack registries are vanilla, but NeoForge has materially more built-in convention and reload-listener support around custom datapack registries. This is where the loader gap is widest for your project.

**3. Config surface.** A civilization sim needs an enormous amount of tuning — growth rates, spawn caps, aggression, economy balance, simulation distances. NeoForge ships a real config system. On Fabric you bring your own.

**4. Networking and UI.** Diplomacy screens, trade interfaces, kingdom management, build queues. Many custom packets and screens. NeoForge's payload/channel system is more structured, and the volume of this work scales directly with feature count.

**5. Mixin maintenance compounds — this is the one that actually hurts.** Twice the ambition on Fabric means roughly twice the mixins into vanilla, and mixin breakage scales with *both* mod size and version churn. At Millénaire scale this is the difference between a weekend port and a month-long port, every single Minecraft version, forever. It is the cost that quietly kills long-lived ambitious mods.

---

## Why NeoForge for this project

**1. Data Attachments are built in, and you will lean on them hard.**
NeoForge patches entities to extend `AttachmentHolder`. You register an `AttachmentType` with a default-value supplier and an optional serializer, and you get persistent custom data on *any* entity — including vanilla villagers and mobs you do not own. That is precisely the "individual AI per entity" storage problem. Attachments also work on chunks and levels, which is exactly where town-scoped state (threat level, claimed area, defense posture) belongs.

On Fabric the equivalent is Cardinal Components — genuinely good and actively maintained (7.3.2, July 2026, already on 26.2) — but it is a third-party dependency in your critical path. When 26.3 lands, your persistence layer waits on someone else's schedule. NeoForge's attachments update when the loader does.

**2. The event bus covers the hooks a defense system needs — first-party.**
A town that defends itself means intercepting mob spawns, retargeting, damage, death, and raid logic. NeoForge exposes these as maintained events. On Fabric, most become mixins you write and then re-verify against every vanilla update. Each one is small; twenty of them is a part-time job. This is the single biggest long-run cost difference for a mod of this shape.

**3. Mojang removed obfuscation in 26.1 — and it helps NeoForge's style more.**
You now get official parameter names from Minecraft's own source. For a project that lives inside `Brain`, `Behavior`, and the pathfinder, this is a large quality-of-life win regardless of loader. It also lowers the historic barrier to reading vanilla deeply, which was one of Fabric's cultural advantages.

**4. Content-mod gravity.**
Large simulation/content mods and most new major modpack development sit on NeoForge for modern versions. If you ever want your town to interoperate with other content mods — or to be included in a pack — that is where the surface area is.

**5. Mixins still work.**
NeoForge supports mixins fully. You are not giving up the deep-surgery tool; you are adding a maintained layer above it so you use it less.

---

## Where Fabric would genuinely be the better call

Be honest about these — if two or more describe you, reconsider.

- **Faster iteration loop.** Lighter startup, quicker dev cycles. Over thousands of test runs that compounds.
- **You plan to write everything from scratch.** If your townsfolk are entirely custom entity classes with your own brain implementation, and you barely touch vanilla mobs, the attachments advantage mostly evaporates.
- **You prefer mixin-first, minimal-abstraction work.** Fabric's culture matches deep AI surgery well, and you will be mixin-ing regardless.
- **Server-side-only distribution.** If the mod never needs client installs, Fabric's leanness is attractive.

Note what is *no longer* a Fabric advantage: the performance-mod gap has closed. Lithium ships for both loaders as of August 2026, and Canary is the NeoForge port covering server-side mob AI, physics, and block ticks (typically 20–40% MSPT improvement). You are not sacrificing optimization headroom by choosing NeoForge.

---

## Options ruled out

| Option | Verdict |
|---|---|
| **Forge (legacy)** | No. NeoForge is the maintained successor for modern versions. No reason to start here in 2026. |
| **Quilt** | No. Momentum stalled; the ecosystem consolidated around Fabric and NeoForge. Avoid for a long-lived project. |
| **Architectury (multi-loader)** | Not yet — see below. It is a build strategy, not a loader. |
| **Paper / Spigot plugin** | No. Zero client install is appealing, and the AI/pathfinding hooks exist, but you lose custom entity types, custom rendering, and models. Too constraining for an ambitious town sim. |

### On Architectury specifically

Architectury API 21.0.7 supports both NeoForge 26.2 and Fabric 26.2 (released 2 August 2026), so shipping to both loaders is achievable. But do **not** start there. Multi-loader taxes early velocity exactly when you need to move fast and change your mind often, and the abstraction gets in the way while your architecture is still fluid.

Cheap insurance instead: **adopt the `common/` + `neoforge/` source layout from day one** and keep your simulation logic — brains, behaviors, town state, scheduling — free of loader imports. If you later want Fabric, you add a `fabric/` module and port the thin platform layer rather than untangling the whole codebase. This costs almost nothing now and preserves the option.

---

## Starting stack for 26.2

- **Loader:** NeoForge for Minecraft 26.2 — check [neoforged.net](https://neoforged.net/) for the current build; versions move fast and the numbering scheme changed with the year-based versions.
- **Java 25** — required as of 26.1+. Not Java 21; update your toolchain.
- **Gradle 9.1.0+** with **ModDevGradle 2.0.141+**.
- **Layout:** `common/` (pure simulation, no loader imports) + `neoforge/` (platform glue).
- **Persistence:** Data Attachments for per-entity and per-chunk state from the start. Retrofitting persistence is painful.
- **Testing:** Install Canary early and profile with it on. Build against the optimized case, not the vanilla one.

---

## The decision that matters more than the loader

**Your inhabitants must not be entities.**

This is the architectural choice that determines whether a Millénaire-class mod ships or dies, and it is entirely loader-agnostic. Get it right and either loader works. Get it wrong and no loader saves you.

### Why the naive approach cannot work

Vanilla villager AI has been the number-one source of server lag since 1.14 — and that is *plain* villagers, doing nothing but looking for beds and workstations. Every entity costs position updates, collision checks, AI evaluation and state updates 20 times per second, with pathfinding the most expensive part, made worse by exactly the confined, structure-dense geometry that settlements are built from. Fifty villagers in a trading hall can wreck a server unaided.

Now count your target. Sprawling kingdoms means multiple settlements, each with dozens of inhabitants, plus travellers, plus caravans, plus defenders, plus hostiles. That is plausibly thousands of individually-simulated lives across a world. **You cannot have thousands of entities running `Brain` ticks.** Not on any loader, not on any hardware, not with any optimization mod.

This is not a hypothetical limit. It is why MineColonies enforces colony size caps and why Millénaire has always constrained village counts and simulation radius. Those caps are not arbitrary conservatism — they are the load-bearing walls holding the design up.

### The architecture that does work

Separate the **simulation** from its **physical representation** completely:

- **Villagers are records, not entities.** A villager is a row in a settlement roster — name, family, profession, inventory, relationships, current task, position. That record is the source of truth and it always exists.
- **Entities are ephemeral views.** Spawn a real entity only when a player is close enough to see it, hydrated from the record. When the player leaves, write state back to the record and despawn. The entity is a rendering and interaction detail, not the villager.
- **Simulate the world on a slow global scheduler.** Kingdoms, settlements, economies and build queues tick a few times per second at most — or once every several seconds — not 20 times. Nobody perceives the difference, and it buys you orders of magnitude.
- **Travel is a timer, not a walk.** A trader moving between settlements is a departure time, a destination and an ETA. Only materialize the walking entity if a player is nearby to watch it. Pathfinding across a kingdom is otherwise ruinous.
- **Construction is a queue that advances abstractly.** Buildings progress in the data model whether or not the chunk is loaded, and only materialize into blocks when someone is there to see them.
- **Defense resolves at two fidelities.** Off-screen, a mob attack on a settlement is a statistical combat resolution against the garrison — losses, damage, outcome. On-screen, it is real entities fighting. The same event, two levels of detail.

This is the standard approach for large-scale simulation games generally, and it is the only thing that makes a kingdom-scale Minecraft mod tractable.

### What this means for your build order

Build the **simulation layer first, with no entities at all.** Settlements that grow, populations that live and work, kingdoms that interact — all as pure data on a slow tick, verifiable in tests without launching a client. Add the entity view layer afterward, as presentation.

Doing it in this order is the difference between a mod that scales and a mod that has to be rewritten. Both reference projects arrived here eventually; starting here saves you the detour.

The corollary for the loader decision: because the simulation core is pure data and vanilla `SavedData`, **keep it entirely free of loader imports** in `common/`. That is what makes the multi-loader option cheap later, and it is also just good architecture.

---

## Sources

- [NeoForge for Minecraft 26.1 — The NeoForged project](https://neoforged.net/news/26.1release/)
- [Data Attachments — NeoForged docs](https://docs.neoforged.net/docs/datastorage/attachments/)
- [Entity Data and Networking — NeoForged docs](https://docs.neoforged.net/docs/entities/data/)
- [Porting to 26.2 — Fabric Documentation](https://docs.fabricmc.net/develop/porting/)
- [Cardinal Components API](https://modrinth.com/mod/cardinal-components-api)
- [Architectury API](https://modrinth.com/project/lhGA9TYQ)
- [Lithium (Fabric/NeoForge)](https://www.curseforge.com/minecraft/mc-mods/lithium)
- [Minecraft Server Entity Limits: Lag Prevention](https://gameteam.io/blog/minecraft-server-entity-limits-lag-prevention/)
- [Millénaire downloads — 9.0.0-beta.2, "a complete rewrite for Minecraft 1.21.1 and NeoForge"](https://www.millenaire.org/downloads)
- [MineColonies on CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecolonies)
- [MineColonies Unofficial Fabric Port](https://www.curseforge.com/minecraft/mc-mods/minecolonies-unofficial-fabric-port)
