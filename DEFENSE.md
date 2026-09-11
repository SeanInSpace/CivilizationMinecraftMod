# How settlements defend themselves

**Status:** implemented and working. Companion to [BUILD_DECISIONS.md](BUILD_DECISIONS.md) (what gets built) and [POPULATION.md](POPULATION.md) (who lives there). This one covers who attacks, who fights back, and what it costs.

Code: [`RaidPlanner`](common/src/main/java/com/kingdoms/sim/settlement/RaidPlanner.java) (all rules), [`Garrison`](common/src/main/java/com/kingdoms/sim/settlement/Garrison.java) (threat versus watch), [`GuardStance`](common/src/main/java/com/kingdoms/sim/combat/GuardStance.java) (sword or bow, and how close to stand), [`FiringPoint`](common/src/main/java/com/kingdoms/sim/combat/FiringPoint.java) (where to stand when the shot is blocked), [`PersonEntityManager`](neoforge/src/main/java/com/kingdoms/neoforge/view/PersonEntityManager.java) (guard combat and the kit), [`NeoForgeWorldBridge`](neoforge/src/main/java/com/kingdoms/neoforge/bridge/NeoForgeWorldBridge.java) (raid spawning).

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

Guards contribute 2 each. Buildings contribute their `defenseBonus` from the
catalog — currently only the **watchtower, at +3**. (The bonus is a column in the
same table as everything else, so datapack cultures can have their own defensive
structures later.)

**The wall is not in this number yet.** A town rings itself with a real wall and
real gates, and a sentry really walks it — but the arithmetic above still counts
only guards and towers, so the wall currently buys physical obstruction and
atmosphere rather than defense points. Giving the perimeter a bonus is the
obvious next move and is deliberately not done blind: it wants a playtest to say
what a wall is worth. It matters less than it sounds, besides: a wall is a
TOWN-stage build, so nothing below town size has one to count.

With the default staffing and build tables, a town's life arc looks like:

| Population | Garrison | Defense | Typical raid | Outcome |
|---|---|---|---|---|
| 6 | none until the town fortifies | 0 | 1–3 | **bleeds** — the dangerous years |
| 8 | 1 guard | 2 | 2–4 | losses about half the time |
| 16 | 2 guards + watchtower | 7 | 3–5 | safe |
| 32 | 4 guards + 2 towers | 14 | 5–7 | untouchable |

The early game is deliberately dangerous: a town must grow through its vulnerable
band, and growth is what saves it. Towers arrive at population 12.

Guards arrive by **three** routes, and the first one matters most. The staffing table
wants one guard per 8 residents (see [POPULATION.md](POPULATION.md)), but a
settlement that reaches the **fortified** stage names its first sentry immediately,
whatever its population — fortified means a watch and not a wall, which is exactly
the stage's own program standing and somebody standing guard over it. That sentry
is a graduation condition, so the post is also *kept* filled: a raid that kills the
only guard is replaced from the pioneers next step. It had to be. The playtest that
forced the rule lost its sentry to a raid twelve steps after the stage named them,
and the founding stalled for three hundred and fifty steps one post short of a
stage it had already reached.

## Mustering

A third route, and the only one that looks outside. The staffing table wants one
guard per eight residents and has no idea what is standing in the treeline — so a
town of nine wanted exactly one guard, got exactly one guard, and met a raid of
six with him.

Now a town compares the two directly. **Threat** is the number on the town's own
alarm — what its people can see, or the strength of the last raid, which are the
same scale. **The watch** is its guards. Since a raid of strength *S* is turned
back by `guards × 2`, the watch a given threat calls for is simply:

```
guards needed = threat ÷ 2, rounded up
```

| What is out there | Threat | Guards needed |
|---|---|---|
| nothing | 0 | 0 |
| a zombie | 1 | 1 |
| a skeleton | 2 | 1 |
| a witch | 3 | 2 |
| a creeper | 4 | 2 |
| a ravager | 5 | 3 |
| more than one guard can hold | 6 | 3 |
| a warden | 10 | 5 |
| the largest raid there is | 16 | 8 |

Deliberately the pessimistic of the two numbers a guard is worth. He *holds* three
danger's worth of wandering hostiles on an ordinary afternoon, but he only *counts*
for two when the raid arrives all at once — and two is the one the town dies by, so
two is the one it recruits by. Standing towers are not counted either: a town with a
watchtower musters as though it had none, because a tower can be knocked down and
the number a town steers by should be one it can carry on its own two feet.

**While the threat outweighs the watch**, two things change and nothing else does:

- **Somebody takes up the sword, one person per step.** An idler first; failing
  that, whichever trade is standing furthest above the minimum its own row of the
  staffing table states. Never a guard, never the last farmer, and never the last
  builder — a town that answers a raid by conscripting its only field hand wins the
  raid and starves in the fortnight after it, and one that takes its only builder
  cannot raise the tower this same fright just ordered. Hunger still comes first of
  the two crises: a town can be wrong about the raid and live.
- **The watchtower goes to the front of the build table** — and the smithy too, if
  the town has not got one, since a forge is what puts a weapon in the hands the
  first rule is busy recruiting. This is the only thing in the whole catalog allowed
  to outrank priority: the hall sits at 100 and the tower at 60, which is right nine
  days in ten and wrong on the tenth. It does **not** shove aside whatever is
  half-built, which is the difference between this and a famine. A famine is a clock
  the town cannot argue with; threat decays every step and the raid may never come,
  so the preference only speaks when the builders are between jobs.

Both lapse on their own. Threat decays a point a step, and the moment it falls back
under the watch the ordinary table resumes — nothing has to switch anything off, and
the guards already recruited drain away no faster than surplus guards ever did.

`/civ info` shows the comparison as `garrison: N guards vs threat T (needs M)`, and
only while the town is short.

## The wall

A **town** stakes a ring around everything it has built and raises it post by post
as its stores allow — a timber and three coin the post — pausing whenever a real
building needs the crew. Gates are cut where the streets reach, one to a side, and
are re-sited as the roads appear until the wall closes over them. The perimeter's
vertices double as the sentry's patrol route.

**The stage is the whole of the rule.** A camp, a homestead, a fortified settlement
and a village build no wall at all: several hundred posts is not what a party that
has only just learned to feed itself should spend its timber on, and the stage that
used to be named for a palisade is named for its watch instead. A settlement that
*has* a wall keeps it whatever stage it stands at — nothing pulls a standing ring
down because the settlement behind it slipped.

**A town that grows past its wall has suburbs, not a problem.** The ring is
re-staked only once the suburbs have become the town — more buildings standing
outside the line than inside it — and then only if the standing wall is paid for to
its last post, and never more often than once in 500 steps. Growth in between is
simply outside the wall. When the line does move, the old one comes down as the new
one goes up: a settlement has one wall, and an abandoned circuit through the middle
of a town is a fence between a settler and their bed. Towns really did wall once at
their charter and live inside that line for generations — Paris rebuilt its circuit
three times in four hundred and fifty years, and London never did.

---

## Fidelity 1: nobody is watching

The raid resolves as arithmetic, immediately:

- **defense ≥ strength** → repelled, no losses. Logged.
- **defense < strength** → the deficit is paid in lives. **Guards fall first** — they are the line — then civilians in roster order. The fallen are removed from the roster *and their families* (an emptied house frees up for the next family). Logged with names.

Threat rises to the raid's strength either way, then decays 1 per step — a returning player can read "something happened recently" straight off the threat number.

One protection: **someone a player can currently see is never killed by arithmetic.** Statistical casualties skip embodied people. (Resolution only runs when the town center is unobserved, so this is a rare edge — a player standing at the fringe of a large claim.)

## Fidelity 2: someone is watching

No arithmetic at all. The raid becomes **real zombies**, spawned in a ring 32 blocks from the town center and pointed inward. From there, vanilla and the guard system decide:

- Zombies hunt villagers on their own — that is vanilla behavior.
- **Guards fight back.** Once a second, every visible guard picks the nearest hostile within 20 blocks and takes a stance against it — see below. Hostiles retaliate through normal aggression, so **guards genuinely can lose**.
- Any villager death — guard or civilian — kills the person it represents, permanently, through the same death path that already existed. The loss is logged.

Guard combat is deliberately puppeteered from the manager rather than grafted into the villager brain: vanilla villagers cannot fight, and bolting goals onto a brain-driven mob makes two AIs wrestle over navigation. One decision per second per guard is cheap and looks right.

## The watch's kit

**Every guard carries a sword and a bow, on duty or off, from the moment he takes
the profession.** Not only when something hostile appears, and not only if the
town has a forge — the wooden sword and the bow are the watch's own equipment and
cost the stores nothing. The leading weapon is in the main hand and the spare in
the off hand, where it can be seen.

What the **smithy** buys is the upgrade. The first iron sword on the weapons rack
replaces a guard's wooden one — one debit from `WEAPONS`, once — and stays with
him. Armor works as it always has: one iron chestplate off the `ARMOUR` rack when
one is available.

| In the main hand | Damage a swing |
|---|---|
| bare hands | 4 |
| wooden sword (the watch's own) | 5 |
| iron sword (off the rack) | 7 |

**A guard who stops being a guard hands the kit back.** The wooden sword and bow
vanish with the job; the iron sword returns to the rack, because the town paid a
smith to make it. The same happens when a body is released — the player walked out
of sight — so the weapon count does not leak every time a town is left alone.

**Arrows are infinite.** There is no arrow store, no fletcher, and nothing to
haul. A quiver would mean a fourth item on the ledger for the smith to make, the
haulers to carry and the player to run out of mid-raid, to answer a question
nobody was asking.

## The two stances

The whole decision is a distance and one question — *does this thing explode?* —
and it lives as a pure function in `GuardStance` (`:common`), with a test on it.

**Sword, for everything that does not explode.** Out of reach, walk at it; within
reach (2.5 blocks), swing. A guard never gives ground to a zombie: what is behind
him is a farmer.

**Bow, for creepers.** A creeper cannot be fought with a sword — its fuse is
shorter than its health, so a guard who stands in reach and swings dies with it.
So the bow comes up and the sword goes to the off hand, and he holds a band:

- **inside 8 blocks** — in the blast. Back off, at a walk, still shooting.
- **8 to 14 blocks** — where he wants to be. Stand still and shoot.
- **beyond 14** — too far for a useful shot. Close in.

Eight is one block clear of the seven a creeper's blast hurts at, which is the
same seven the flight goal runs on. Fourteen is inside the 20 a guard notices
anything at, and vanilla's own skeleton holds fifteen.

He looses **one arrow every 20 ticks** — vanilla's skeleton rate on hard
difficulty; a skeleton on anything easier fires half as often. The arrow is a
real `AbstractArrow`, built and fired exactly the way `AbstractSkeleton.performRangedAttack`
builds and fires one (`ProjectileUtil.getMobArrow`, then
`Projectile.spawnProjectileUsingShoot` with vanilla's lead, arc and
difficulty-scaled spread), so it does what any other arrow in the game does:
about 4 damage a hit, four or five hits for a creeper's 20 health. Arrows are
marked unpickupable, so a well-defended town does not silt up with free ammunition.

**He only shoots down a line he actually has.** Before an arrow is loosed the
guard needs an unobstructed view of the creeper — vanilla's `hasLineOfSight`, a
single block clip from his eye to its eye — held for a full second, and no
townsperson standing on the arrow's path; a guard holding the band with a barn
or a farmer in the way walks to a stand on a ring around the creeper that he can
shoot from, or, failing that, to the near edge of the band, and never inside the
blast. Which stand is `FiringPoint` (`:common`), with a test on it.

Once the creeper is dead or out of sight, the sword comes back to the main hand.
A guard with the bow up who is charged by a zombie is looking at the zombie by
then — it is the nearer threat — so he draws the sword as it comes into reach.

**No speed above a walk, in either stance**, retreat included. See `Pace`.

---

## The evidence trail

Every settlement keeps a bounded history (last 20 events, persisted with the save):

```
[step 214] Raid of 4 repelled by the garrison (defense 7), no losses
[step 264] Raiders sighted — 5 attackers approach Normandy Town
[step 265] Esa Cooper was killed
```

`/civ info` shows the last five. This is the roadmap's "done when": leave a town overnight, return, and tell from the state what happened.

---

## Testing levers

```
/civ raid            force a raid at natural strength
/civ raid 12         force a raid of strength 12
/civ threat 5        set the alarm level directly
```

Force a raid while standing in town and zombies come over the hill. Force one via the server console with nobody logged in and it resolves statistically — same command, both fidelities.

Config: `defense.raids_enabled` (master switch — disable for peaceful building) and `defense.raid_interval_steps`.

---

## What this deliberately does not do

- **Raids are zombies only.** No skeletons, no variety, no siege equipment. The spawn ring is one line to extend.
- **No building damage.** Raids cost lives, never structures. Razed buildings would be more dramatic; they are also block-cleanup complexity deferred until buildings are real structures rather than placeholders.
- **Day-blind.** Raids can fire at noon, and observed noon-raid zombies burn. Harmless, mildly silly, unaddressed.
- **Guards do not patrol.** They stand near home like everyone else until a hostile enters engagement range. Patrol routes belong with real schedules (Phase 6+ territory).
- **Construction does not pause for a raid.** A frightened town changes what it builds *next* (see "Mustering"), but it does not drop a half-built storehouse and run. Only starvation does that.
- **An observed raid interrupted mid-fight** (player logs out) leaves zombies standing in the unloading chunks; they resume when someone returns rather than resolving statistically. Rare, self-correcting at dawn, accepted.

---

## Tuning

| Want | Change |
|---|---|
| Gentler early game | Raise `MIN_POPULATION_FOR_RAIDS`, or lower strength scaling from `population ÷ 8` |
| Tougher guards | `GUARD_POWER` (statistical) and `GUARD_DAMAGE` / ranges in `PersonEntityManager` (observed) |
| Stronger towers | `defenseBonus` column in `BuildCatalog` |
| A jumpier or calmer militia | `GUARD_POWER` again — it is the divisor in `Garrison.neededGuards`, so halving a guard's worth doubles the watch a fright calls for |
| More/less frequent raids | `defense.raid_interval_steps` in config |
| Raids off entirely | `defense.raids_enabled = false` |
