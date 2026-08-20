# Goals

Working list for the self-sufficiency milestone. Items move to **Done** with a
one-line summary, and are dropped entirely once they have been played and hold up
— this file is a worklist, not a changelog. The changelog is the git history.

**Milestone:** a town that secures its own materials, feeds itself, equips itself,
and can be traded with — without the player doing any of it for them.

---

## In progress

### Needs eyes, not tests
Everything below runs without throwing and produces the right numbers. What no
automated check can confirm is whether it *looks* right:

- [ ] Are the animal pens actually separated, and do the beasts stay in them?
- [ ] Do paths land on the ground and reach the doors, rather than stopping short?
- [ ] Do guards visibly carry swords once a smithy stands?
- [ ] Is a hillside build's material cost bearable in real time, or a grind?
- [ ] Does the quest board read well, or is it a wall of numbers?

### Next
- [ ] Hideouts, so `hideouts_cleared` counts something
- [ ] A second culture, to prove the hook earns its keep
- [ ] `.blueprint` reader, for the MineColonies/Structurize content ecosystem

---

## Done

- **Endurance run.** 700 steps from five settlers: three settlements, 55 / 32 / 5
  buildings, both mature towns fully equipped (48/48 and 35/35), twelve raids
  repelled, zero exceptions, zero starvation. Iron capped afterwards — the forge
  stops at its ceilings and the ore was piling up unspent.
- **Client playtest.** Quickplay into a played-out save: world loads, all 44
  buildings materialize — smiths, animal farm, watchtowers, markets — and nothing
  in `kingdoms` or `keystone` throws across a 5,959-line log.

- **Headless playtest, and five bugs it found.** Buildings were free when
  unwatched; production ran only in the view layer so an unwatched town could
  never make anything; staffing wanted no lumberjack below ten residents; two
  producers ordered out of turn shared one plot; and settlers starved beside a
  full granary because food could only reach them through the family pantry.
  All fixed — a town now goes from five settlers to 48 people, 44 buildings,
  every trade staffed and two thirds equipped, with nobody starving.
- **Opt-in quickplay** (`-Pquickplay=<world>`), so a missing world can no longer
  break every launch.

- **Generic town ledger.** `TownStores`: resource id → amount, all-or-nothing
  spending. Replaced four hardcoded ints; codec writes one map and still reads the
  old flat keys, so existing worlds migrate.
- **Supply is limited.** Laying a block spends wood or stone, keyed off the same
  tags that pick the digging tool. Digging costs only sweat.
- **Self-sufficiency loop.** A founding party arrives stocked; when a build cannot
  be paid for, `requestProducer` orders the lumber camp or mine that fixes it,
  ahead of the thing nobody can afford. Producers are exempt from material cost so
  the bootstrap can always run.
- **Warehouse.** Building, post, and it raises the storage ceiling like a storehouse.
- **Smith.** Iron (from ore the miners cut through) plus timber as fuel becomes
  tools, then weapons, then armour. Tools are issued to workers one a step; guards
  draw a sword and chestplate from the rack and hit harder for it.
- **Larger farms.** 7×7 → 11×11, work 30 → 45, one per 6 residents rather than 5.
- **Animal farm.** Fenced compound split into strip pens, one species per pen, list
  taken from the culture. Shepherds stock them a beast at a time and leave vanilla
  to do the breeding.
- **Market hours.** Stalls open 1000–11000. The post reports hours and stock, and
  sells bread for emeralds during them — keeping a food reserve back so a town can
  never be bought into starvation.
- **Paths.** Tracks laid from each building's door to the hall, following the
  terrain. Only grass and dirt are paved and only foliage is cleared, so a path
  can never eat a wall.
- **Quest board.** Reads whatever counters a settlement happens to keep rather than
  a fixed list — so a stat can be tallied by a datapack or an addon and appear
  without the board being told. Seeded with mobs slain, raids repelled, buildings
  raised, trees felled, stone cut, goods traded. "Hideouts cleared" is a name the
  board will show the moment anything raises it.
- **Culture hook.** `Culture` carries the penned-animal list and a layout id, with
  one default — so a second culture is a table entry, not new code.
- **A post on every building**, and `/civ info` reads the ledger, the deeds, the
  culture and how much of the workforce is equipped.
