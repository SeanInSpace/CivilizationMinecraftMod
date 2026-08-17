# How settlements defend themselves

**Status:** implemented and working. Companion to [BUILD_DECISIONS.md](BUILD_DECISIONS.md) (what gets built) and [POPULATION.md](POPULATION.md) (who lives there). This one covers who attacks, who fights back, and what it costs.

Code: [`RaidPlanner`](common/src/main/java/com/kingdoms/sim/settlement/RaidPlanner.java) (all rules), [`PersonEntityManager`](neoforge/src/main/java/com/kingdoms/neoforge/view/PersonEntityManager.java) (guard combat), [`NeoForgeWorldBridge`](neoforge/src/main/java/com/kingdoms/neoforge/bridge/NeoForgeWorldBridge.java) (raid spawning).

---

## The rule, in one sentence

> **Raids come on a clock; the same raid is real monsters if you are watching, and arithmetic if you are not — and either way, the settlement's history records what happened.**

That last clause is the point of the whole design. You can leave a town overnight, come back, and read exactly how it fared.

---

## The raid clock

- Every settlement is checked for a raid once per **raid interval** (50 simulation steps ≈ every 4 minutes at defaults; configurable).
- Each settlement raids on **its own clock** — the step within the interval is hashed from the settlement's id, so towns are not all attacked simultaneously.
- **Settlements below 6 people are beneath raiders' notice.** A hamlet gets to establish itself before the pressure starts.
- Raid strength = `1 + population ÷ 8`, plus 0–2 hashed jitter, **capped at 16**. Bigger towns attract bigger raids, but never an apocalypse — an uncapped formula once sent 131 zombies at an oversized town.

There is no randomness anywhere — schedules and strengths hash the settlement id with the step number. The same world replays identically, which is what keeps the whole simulation testable.

## Defense power

```
defense = guards × 2  +  sum of structure bonuses
```

Guards contribute 2 each. Buildings contribute their `defenseBonus` from the catalogue — currently only the **watchtower, at +3**. (The bonus is a column in the same table as everything else, so datapack cultures can have their own defensive structures later.)

With the default staffing and build tables, a town's life arc looks like:

| Population | Garrison | Defense | Typical raid | Outcome |
|---|---|---|---|---|
| 6 | none (guards staff at 8) | 0 | 1–3 | **bleeds** — the dangerous years |
| 8 | 1 guard | 2 | 2–4 | losses about half the time |
| 16 | 2 guards + watchtower | 7 | 3–5 | safe |
| 32 | 4 guards + 2 towers | 14 | 5–7 | untouchable |

The early game is deliberately dangerous: a town must grow through its vulnerable band, and growth is what saves it. Guards appear automatically at population 8 (see [POPULATION.md](POPULATION.md)), towers at 12.

---

## Fidelity 1: nobody is watching

The raid resolves as arithmetic, immediately:

- **defense ≥ strength** → repelled, no losses. Logged.
- **defense < strength** → the deficit is paid in lives. **Guards fall first** — they are the line — then civilians in roster order. The fallen are removed from the roster *and their families* (an emptied house frees up for the next family). Logged with names.

Threat rises to the raid's strength either way, then decays 1 per step — a returning player can read "something happened recently" straight off the threat number.

One protection: **someone a player can currently see is never killed by arithmetic.** Statistical casualties skip embodied people. (Resolution only runs when the town centre is unobserved, so this is a rare edge — a player standing at the fringe of a large claim.)

## Fidelity 2: someone is watching

No arithmetic at all. The raid becomes **real zombies**, spawned in a ring 32 blocks from the town centre and pointed inward. From there, vanilla and the guard system decide:

- Zombies hunt villagers on their own — that is vanilla behaviour.
- **Guards fight back.** Once a second, every visible guard picks the nearest hostile within 20 blocks: in melee reach they strike (4 damage, with a swing); otherwise they charge. Hostiles retaliate through normal aggression, so **guards genuinely can lose**.
- Any villager death — guard or civilian — kills the person it represents, permanently, through the same death path that already existed. The loss is logged.

Guard combat is deliberately puppeteered from the manager rather than grafted into the villager brain: vanilla villagers cannot fight, and bolting goals onto a brain-driven mob makes two AIs wrestle over navigation. One decision per second per guard is cheap and looks right.

---

## The evidence trail

Every settlement keeps a bounded history (last 20 events, persisted with the save):

```
[step 214] Raid of 4 repelled by the garrison (defense 7), no losses
[step 264] Raiders sighted — 5 attackers approach Normandy Town
[step 265] Esa Cooper was killed
```

`/kingdoms info` shows the last five. This is the roadmap's "done when": leave a town overnight, return, and tell from the state what happened.

---

## Testing levers

```
/kingdoms raid            force a raid at natural strength
/kingdoms raid 12         force a raid of strength 12
/kingdoms threat 5        set the alarm level directly
```

Force a raid while standing in town and zombies come over the hill. Force one via the server console with nobody logged in and it resolves statistically — same command, both fidelities.

Config: `defense.raids_enabled` (master switch — disable for peaceful building) and `defense.raid_interval_steps`.

---

## What this deliberately does not do

- **Raids are zombies only.** No skeletons, no variety, no siege equipment. The spawn ring is one line to extend.
- **No building damage.** Raids cost lives, never structures. Razed buildings would be more dramatic; they are also block-cleanup complexity deferred until buildings are real structures rather than placeholders.
- **Day-blind.** Raids can fire at noon, and observed noon-raid zombies burn. Harmless, mildly silly, unaddressed.
- **Guards do not patrol.** They stand near home like everyone else until a hostile enters engagement range. Patrol routes belong with real schedules (Phase 6+ territory).
- **Threat does nothing yet.** It is a visible alarm metric that rises and decays, but nothing reads it — construction does not pause, guards do not muster. First candidate for deepening.
- **An observed raid interrupted mid-fight** (player logs out) leaves zombies standing in the unloading chunks; they resume when someone returns rather than resolving statistically. Rare, self-correcting at dawn, accepted.

---

## Tuning

| Want | Change |
|---|---|
| Gentler early game | Raise `MIN_POPULATION_FOR_RAIDS`, or lower strength scaling from `population ÷ 8` |
| Tougher guards | `GUARD_POWER` (statistical) and `GUARD_DAMAGE` / ranges in `PersonEntityManager` (observed) |
| Stronger towers | `defenseBonus` column in `BuildCatalogue` |
| More/less frequent raids | `defense.raid_interval_steps` in config |
| Raids off entirely | `defense.raids_enabled = false` |
