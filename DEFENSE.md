# How settlements defend themselves

**Status:** implemented and working. Companion to [BUILD_DECISIONS.md](BUILD_DECISIONS.md) (what gets built) and [POPULATION.md](POPULATION.md) (who lives there). This one covers who attacks, who fights back, and what it costs.

Code: [`RaidPlanner`](common/src/main/java/com/civilization/sim/settlement/RaidPlanner.java) (all rules), [`Garrison`](common/src/main/java/com/civilization/sim/settlement/Garrison.java) (threat versus watch), [`GuardStance`](common/src/main/java/com/civilization/sim/combat/GuardStance.java) (sword or bow, and how close to stand), [`FiringPoint`](common/src/main/java/com/civilization/sim/combat/FiringPoint.java) (where to stand when the shot is blocked), [`PersonEntityManager`](neoforge/src/main/java/com/civilization/neoforge/view/PersonEntityManager.java) (guard combat and the kit), [`NeoForgeWorldBridge`](neoforge/src/main/java/com/civilization/neoforge/bridge/NeoForgeWorldBridge.java) (raid spawning).

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

## A new town is not raided

A settlement's first five hundred steps are protected, in two stages, counted from **its own first step** rather than from the world's — so a daughter colony budded off at step nine thousand gets the same start the world's own towns got at step zero. The settlement records that birthday itself (`Settlement.firstStep`, stamped at the top of every step and saved), because there is no other single place every town passes through: a charter, `/civ found`, an expansion, and world generation all create one differently.

| Age of the town | What a raid is |
|---|---|
| 0–199 steps (`RAID_GRACE_STEPS`) | **Nothing.** No raid fires at all — four whole raid intervals, about seventeen minutes of play. |
| 200–499 steps (`EARLY_CAP_STEPS`) | **A probe.** Strength is capped at `guards + structure bonuses + 1`, which a town with even one guard repels outright, because defense is `guards × 2 + structures` and `2g + s ≥ g + s + 1` for every `g ≥ 1`. A town with *no* guards is over its defense by exactly one, which is under the casualty margin below — so it takes the scare and keeps its people. |
| 500 steps and after | The ordinary clock, at the ordinary strength. |

**Why this exists.** World generation stands nine villages around the world spawn before the player has finished loading in, each twelve people with one guard and a defense of two — and then raided them. Measured unwatched over twelve trials, such a village lost an average of **3.75 people by step 200, 8.42 by step 400 and 20.67 by step 1000**. It is losing a founding population roughly every five hundred steps and only standing at all because it can outbreed the arithmetic. Every worldgen town was starting by being eaten in its cradle, and the player's first sight of one was its history saying so.

**Why the grace is not "until the wall is up."** That was the obvious anchor and it does not survive measurement. Nothing is staked before TOWN and nothing is staked before the stage's own program stands, so a seeded village **stakes its ring on step 532 and does not pay for the last post until step 1256**. A grace running to 1257 is twenty-five raid intervals — an hour and a half in which a raid cannot happen — which is not a grace period, it is the feature switched off. So the grace covers the first four intervals outright and the cap carries the town from there with raids it can actually turn back.

**A known thinness.** The same measurement shows a seeded village holding **one guard, defense two, until step 778** — the staffing table does not call for a second until then. So the cap lifts at step 500 on a town that is still thin, and from there it does bleed: an average of 4.92 deaths by step 1000, against 20.67 before. That is a staffing question rather than a raid question, and it is left where it is on purpose.

## When nobody was ever watching, a narrow loss costs nobody

`UNWATCHED_CASUALTY_MARGIN = 2`. A raid resolved as arithmetic must beat the town's defense **by two or more** before anybody dies; a margin of one is logged as broken through and driven off, counted as repelled, and costs no lives.

The reason is who is dying. An unwatched raid is settled by subtraction, so a person lost to a margin of one is a person lost to a hash of the settlement's id and the step number — no fight, no body, nobody watching, and a name in the history that nothing anywhere could have changed. A margin of two is a line the defense was genuinely short of holding. Above the margin the arithmetic is exactly what it always was: the deficit is the toll.

## Defense power

```
defense = guards × 2  +  sum of structure bonuses
```

Guards contribute 2 each. Buildings contribute their `defenseBonus` from the
catalog — currently only the **watchtower, at +3**. (The bonus is a column in the
same table as everything else, so datapack cultures can have their own defensive
structures later.)

**Race is not in this number, deliberately.** An orc guard is tougher and hits
harder in a real fight, but the statistical raid still counts heads, because this
formula and the one the town recruits by (`guards needed = threat ÷ 2`) are
exactly each other inverted — that is the whole point of there being one
comparison in one place. Weighting an orc guard here without weighting him there
would make a town recruit by a number it does not fight by, which is the fault
`Garrison` exists to prevent. When it goes in it goes in both ends at once,
alongside the weapon weighting the same paragraph has been waiting for.

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
- **strength − defense = 1** → broken through and driven off, no losses, counted as a repel. See the casualty margin above.
- **strength − defense ≥ 2** → the deficit is paid in lives. **Guards fall first** — they are the line — then civilians in roster order. The fallen are removed from the roster *and their families* (an emptied house frees up for the next family). Logged with names.

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
him. Armor works as it always has: one iron chestplate off the `ARMOR` rack when
one is available.

| In the main hand | Damage a swing |
|---|---|
| bare hands | 4 |
| wooden sword (the watch's own) | 5 |
| iron sword (off the rack) | 7 |

**And then the body he was born into.** A guard's race adds to every swing on top
of the table above: humans nothing, orcs one, goblins nothing — so an orc guard
with an iron sword hits for 8 where a human hits for 7. An orc also carries 30
health against a human's 20 and a goblin's 14, so an orc watch is harder to break
as well as harder to survive. He is a tenth slower closing the distance, which is
the price. See the race table in [PLAYING.md](PLAYING.md).

**A guard who stops being a guard hands the kit back.** The wooden sword and bow
vanish with the job; the iron sword returns to the rack, because the town paid a
smith to make it. The same happens when a body is released — the player walked out
of sight — so the weapon count does not leak every time a town is left alone.
(An orc keeps something: he goes back to the cleaver or hand axe he owns. See
[The orc kit](#the-orc-kit).)

**Arrows are infinite.** There is no arrow store, no fletcher, and nothing to
haul. A quiver would mean a fourth item on the ledger for the smith to make, the
haulers to carry and the player to run out of mid-raid, to answer a question
nobody was asking.

## The orc kit

**An orc town arms everybody.** Not only the watch — a farmer, a hauler, the
miller: everyone walks around with something in his fist, all day, for as long as
he lives there. It is his own, the town never bought it, and the rack never sees
it back. Which weapon is decided once by who he is and never re-rolled, so the
big one at the gate is the big one at the gate every time you come back.

Five weapons, each forged twice — a **crude** one the camp beats out cold, and a
**forged** one off the smithy's rack. The smithy upgrade swaps the crude one for
the forged one of the same shape; nobody is ever handed somebody else's weapon.

| Weapon | Who carries it | Damage (player, crude / forged) | Swings a second | Hands |
|---|---|---|---|---|
| greatsword | watch | 9 / 10 | 0.8 | **two** |
| falchion | watch | 5 / 6 | 1.8 | one |
| axe | watch and everybody else | 8 / 9 | 0.9 | one |
| morningstar | watch | 10 / 11 | 0.6 | **two** |
| cleaver | everybody else | 6 / 7 | 1.6 | one |

Those are the figures a **player** sees on the tooltip, because these are real
items built the way vanilla builds its own: same cooldown sweep, same enchanting,
same repair, same durability bar. The forged axe is an iron axe, exactly.

What a **settler** does with one is a much flatter table. A guard's swing is base,
then the body, then what is in his hand — an orc's body is worth +1, so on the
same base of 4:

| Guard, in the main hand | Damage a swing |
|---|---|
| human, bare hands | 4 |
| human, wooden sword (the watch's own) | 5 |
| human, iron sword (off the rack) | 7 |
| **orc, any crude weapon** | **7** |
| **orc, any forged weapon** | **9** |

Every orc weapon is worth the same as every other at the same tier, deliberately.
A guard must not be quietly handicapped by which of five weapons the deal gave
him — two guards of the same town with the same smithy behind them doing
different damage for no reason either of them chose is a cosmetic roll with a
hidden cost.

**The two-handers carry no bow.** A greatsword or a morningstar takes both fists,
so its bearer has an empty off hand and no answer to a creeper: he keeps the
band, gives ground when it closes, and waits for somebody who can shoot. That is
a stand-off rather than a kill, and it is the price of the big weapon. The watch
is dealt from four weapons, two of which leave a hand, so half the wall can still
shoot — and the arithmetic that guarantees at least one of them can has a test on
it.

**Everybody else fights back.** An armed settler who is not of the watch answers
the thing that just hit him, and the rule is three refusals: he does not go
looking (his target is whatever last struck him, never the nearest hostile), he
does not chase (nothing here ever touches his feet — he swings on his way to the
door, and past 4 blocks the quarrel is over), and he does not stand up to a
creeper (anything that explodes is left to the flight goal exactly as before). He
hits for **2, plus the body, plus his weapon** — 5 with a crude cleaver, the same
three terms in the same order as a guard's swing — which is below a guard's on
purpose, and below it by the two bases and nothing else. A farmer who swings as
hard as the watch is a town with no reason to post one. A player who hits a
settler is not answered at all; only mobs are.

**A load rides in the off hand.** An orc carrying grain to the storehouse keeps
the weapon in his fist and the sack in the other hand. The alternative — hiding
the weapon while carrying — was rejected because a town of orcs spends most of
the day hauling something, so the weapon would be the thing nobody ever saw.
A block is different: somebody with a wall block in his fist is working, and the
weapon waits until he puts the job down.

**Nothing drops.** Same as the lowland kit: a killed orc does not carpet the
square with weapons the player can pick up and the town cannot. They are not
craftable either — the five and their forged twins are in the creative tab and
that is the only way a player gets one.

**The art is placeholder.** Every icon is a 16×16 silhouette laid out by hand in
`neoforge/tools/orc_weapon_art.py` and written straight out as a PNG. They are
meant to be replaced file for file — drop a real 16×16 (a Blockbench export, say)
over the PNG of the same name and nothing else has to change.

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
| Gentler early game | `RAID_GRACE_STEPS` (nothing at all) and `EARLY_CAP_STEPS` (probes only) — or raise `MIN_POPULATION_FOR_RAIDS`, or lower strength scaling from `population ÷ 8` |
| Fewer deaths to raids nobody saw | `UNWATCHED_CASUALTY_MARGIN` |
| Tougher guards | `GUARD_POWER` (statistical) and `GUARD_DAMAGE` / ranges in `PersonEntityManager` (observed) |
| Stronger towers | `defenseBonus` column in `BuildCatalog` |
| A jumpier or calmer militia | `GUARD_POWER` again — it is the divisor in `Garrison.neededGuards`, so halving a guard's worth doubles the watch a fright calls for |
| More/less frequent raids | `defense.raid_interval_steps` in config |
| Raids off entirely | `defense.raids_enabled = false` |
