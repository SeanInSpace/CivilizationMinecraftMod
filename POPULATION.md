# How population grows

**Status:** implemented and working. Companion to [BUILD_DECISIONS.md](BUILD_DECISIONS.md) — that document covers what a settlement builds, this one covers who lives there.

Code: [`PopulationPlanner`](common/src/main/java/com/kingdoms/sim/settlement/PopulationPlanner.java), [`Household`](common/src/main/java/com/kingdoms/sim/person/Household.java).

---

## The rule, in one sentence

> **A family only grows if it has a home with room left in it.**

No house, no children. Full house, no children — until somebody moves out into a new one.

This makes **house construction the pacing mechanism for the entire simulation.** Population cannot outrun what the builders have put up, and a town that stops building stops growing.

---

## People do not reproduce — families do

The unit of growth is the **household**, not the individual. A household is a family: a name, a list of members, the house they live in, and how close they are to their next child.

A person on their own is not a growth engine. The family they belong to is. This is what makes housing a meaningful constraint rather than a decoration — you are not housing 40 individuals, you are housing 10 families of 4, and a family that fills its house is done growing.

---

## The three stages

Every simulation step, each settlement runs these in order.

### 1. Newcomers are gathered into families

Anyone living in the settlement who does not belong to a family yet gets put in one. This covers people added by the debug command, and whatever migration system exists later.

They are **grouped**, not given a family each. New arrivals fill the most recent family up to the size of the largest house type (4, currently), and only start a new family when that one is full.

This matters. If six settlers each became their own household, the town would need six houses to accommodate six people. Grouped, they need two.

### 2. Families claim empty houses

Any family without a home takes the first unoccupied house, in order. **One family per house** — houses are not shared between families.

When the houses run out, the remaining families stay unhoused. They persist, they are counted in the population, and they do nothing. They are waiting for the builders.

### 3. Housed families grow

For each family that has a home:

- Add 1 to its growth progress, up to a threshold of **8 steps** (about 40 seconds of real time).
- On reaching the threshold:
  - **If the house has room** — a child is born. Population goes up by one. Progress resets.
  - **If the house is full** — one member moves out to found a new family in an empty house, if one exists. Population does **not** change; a person moved, nobody was born. Progress resets.
  - **If the house is full and there is no empty house** — nothing happens. Progress is *held* at the threshold, so the family splits the instant a house becomes available.

Unhoused families skip all of this. They do not accumulate progress at all.

---

## How jobs are assigned

*(Changed in Phase 2 — children previously inherited the family trade blindly, which meant a town founded by farmers could never build anything. Jobs are now assigned by need.)*

Each settlement wants a staffing mix, using the same arithmetic as the build catalogue — `base + population ÷ per-residents`, highest priority wins, deterministic ties:

| Profession | Always want | Plus one per | Priority |
|---|---|---|---|
| Builder | 1 | 5 residents | 90 |
| Guard | 0 | 8 residents | 80 |
| Farmer | 0 | 5 residents | 70 |
| Trader | 0 | 15 residents | 50 |

Builders lead because construction gates housing and housing gates growth — a town short of builders is short of everything.

Two mechanisms move people into jobs:

- **One person per step retrains** into the most-needed trade. Idlers volunteer first; failing that, someone from the profession with the **largest surplus** over its own desired count steps up. A profession at or below its desired staffing is never drained — retraining fills gaps from slack, never by opening new ones. *(The surplus rule came from live playtesting: a town of ninety-seven farmers had no idlers and stayed defenseless forever under the old idler-only rule.)*
- **Newborns take the most-needed trade** if anything is still short at the moment of birth, falling back to the family's eldest member's trade. In practice retraining (one per step) outpaces births (one per eight steps), so children usually follow the family.

---

## The growth loop

This is the cycle the whole simulation now runs on:

```
families grow  →  houses fill up  →  population rises
                                          ↓
                            build planner wants more houses
                                          ↓
                              builders construct them
                                          ↓
              full families split into the new houses
                                          ↓
                        more families with room to grow
```

Every stage gates the next. Remove the builders and it stops at "construct them". Fill every house and it stops at "split into". That interlock is the point.

### Why it does not deadlock

There is a real risk here worth being explicit about: if population is needed to justify houses, and houses are needed to grow population, a settlement could lock at its starting size forever.

It does not, because **housing supply is deliberately set to run ahead of demand.** Houses are wanted at `1 + (population ÷ 3)`, and each holds 4. So a town of 6 wants 3 houses — 12 beds for 6 people. There is always slack for the next child.

If you retune those numbers, keep `capacity × (base + pop/perResidents) > pop` at every population, or towns will freeze.

---

## How the village feeds itself

*(See [`FoodPlanner`](common/src/main/java/com/kingdoms/sim/settlement/FoodPlanner.java).)*

Food travels a real chain, and every link is held state:

```
fields grow → FARMERS haul → granary → MARKET HANDS stock → market
                                                              ↓
   mouths ← personal inventory ← family pantry ← a family member fetches
```

**Goods never teleport.** Every transfer is an errand somebody physically runs: they walk to the source empty-handed, pick the load up, carry it to the destination and set it down. While it is on their back it is *nowhere else* — so a carrier caught in a raid genuinely costs the town that food, and you can watch grain cross the village in someone's hands.

Both fidelities share one arrival test, because a person's position is the same field either way: watched, it is synced from the walking entity; unwatched, the simulation advances it ~12 blocks per step. A delivery takes about as long whether or not anyone is looking.

- **Fields grow harvest into their own stores** (up to 2 farmers worked per farm); **farmers carry it to the granary**, one load per step.
- The **granary** is the town's bulk store — 200 base, +400 per granary or storehouse building. Towns build a granary early (population 4) and a market at 6.
- **Market hands (traders) stock the market** from the granary; **one member per family fetches from the market** to keep the pantry at 3 food per member. Young towns without a market shop straight from the granary.
- **Each person carries real food** — actual items in actual pockets, six kinds at most — and eats the most nourishing thing they hold when hunger bites, refilling from the family pantry. Different foods go different distances: bread undoes 30 hunger, cooked beef 45, raw wheat only 10. Anything inedible is carried and never eaten, so a settler will starve holding a pickaxe. Players can hand food over directly, and sneak-right-click to read someone's pockets.
- **Births require banked food** in the granary — the fields pace the village as much as the housing does.

Break any link — no farmers, full granary, no market hands, an unhoused family — and hunger arrives downstream.

### Hunger and starvation

Everyone's hunger rises 2 per step and is scored 0–99:

| Hunger | State | Effect |
|---|---|---|
| 0–29 | Fed | none — eats at 30 when food is available |
| 30–59 | Hungry | none yet, but eating whenever food can be had |
| 60–89 | Weak | **stops farming, hauling and building**; visibly slowed in the world |
| 90–99 | Starving | heavy debuffs; after 10 steps held at the cap, **death** |

Starvation deaths are permanent — roster, family, and a line in the town's history ("Ada Baker starved"). The weak-worker rule makes famine compound: hungry farmers bring in less, which is exactly how real famines spiral — and why the granary buffer matters.

New settlements begin with **100 provisions** — the founding party packs supplies to survive until the first field is tilled.

## The population ceiling

Births stop at **48 people per settlement** (`population.max_per_settlement` in config). This is not a soft limit — it is load-bearing. Housing demand scales with population (`1 + pop ÷ 3` houses), so supply always keeps up with growth, and an uncapped town grows *exponentially forever*: live playtesting produced a thousand-person settlement drawing 131-zombie raids within sixteen minutes. Millénaire and MineColonies cap settlement size for exactly this reason.

Growth progress holds at the threshold while a town is full, so growth resumes the moment deaths make room. The eventual replacement for the hard cap is a food/resource economy; until then the ceiling is the economy.

## What this deliberately does not do

- **Nobody dies of natural causes.** No age, no mortality. A person whose view entity is killed dies for real — removed from roster and family — and raid arithmetic kills people while unwatched. Nothing else does.
- **Nobody arrives.** No migration between settlements or from outside.
- **No pairing.** There are no couples, no parents, no genealogy. A family of one can have a child.
- **No age.** A newborn is immediately a full worker contributing to construction.
- **Families never merge, and nobody moves house** except through the one-member split.
- **Unhoused families are inert**, not unhappy. There is no consequence to homelessness beyond not growing.
- **Houses are never lost.** Breaking the blocks does not evict anyone.

---

## Tuning

| Want | Change |
|---|---|
| Faster or slower growth | `PopulationPlanner.STEPS_PER_BIRTH` (currently 8) |
| Bigger families per house | `capacity` on the house type in `BuildCatalogue` |
| Housing to be tighter | Raise `perResidents` on the house type — but mind the deadlock rule above |
| More house types | Add rows with `capacity > 0`; mansions and hovels work with no code change |

---

## Where this goes next

1. **Mortality and age.** The obvious counterweight. Without it, every settlement grows forever, and "population" becomes the only number that matters.
2. **Job assignment.** Trade inheritance is a stopgap. A settlement that notices it has no farmers and reassigns an idler would be far more robust than one whose fate is fixed at founding.
3. **Migration.** Families moving between settlements is what turns several towns into a kingdom rather than several unrelated simulations.
4. **Consequences for homelessness.** Unhoused families currently just wait. Unrest, or leaving for another settlement, would make housing shortfalls matter.
