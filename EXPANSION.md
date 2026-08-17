# How kingdoms sprawl

**Status:** implemented and working. Companion to [POPULATION.md](POPULATION.md) (growth) and [DEFENSE.md](DEFENSE.md) (what sprawl has to survive).

Code: [`ExpansionPlanner`](common/src/main/java/com/kingdoms/sim/kingdom/ExpansionPlanner.java).

---

## The rule, in one sentence

> **A full settlement does not stop growing — it sends people out to found the next one.**

When a town reaches its population ceiling, a founding party of its youngest families departs, walks off the map's ledger, and reappears as a new settlement under the same banner, ~160 blocks away. The parent — relieved of its emigrants — resumes growing. When it fills again, it founds again. **Sprawl is the steady state**, and a kingdom is what accumulates.

## The rules

All deterministic, like everything in the sim:

- **Only a full settlement founds.** Population at the ceiling is the trigger — the cap is not a wall, it is a pump.
- **One frontier town at a time.** No settlement founds while any sibling is still young (below 10 people). A kingdom consolidates before it stretches again.
- **Whole families emigrate, never fragments.** The party is up to 6 people, chosen youngest-families-first — the founding generation leaves, the elders who built the parent stay.
- **Nobody you can see teleports.** A family with any member currently embodied as an entity stays home. Emigration happens in the abstract fidelity only.
- **The site** is picked by hashed direction at 160 blocks, stepping further out when the kingdom's own claims are in the way. Emigrants arrive unhoused, at the new centre, and the ordinary machinery takes over: they build, claim houses, staff jobs, and start drawing raids of their own.

Both towns record the event:

```
[step 412] 6 set out to found Ravenholm
[step 412] Ravenholm founded by settlers from Oakstead
```

## What this makes

The full loop, end to end, with no player input:

```
families grow → town fills → party departs → daughter founded
     ↑                                            ↓
  parent regrows ← ceiling lifted by emigration   builds, staffs, defends itself
```

Hold a Founding Charter and walk the frontier: each green ring is another settlement the kingdom planted itself.

## Not yet

- **Emigration is instant** — a departure step, not a walked journey. Visible caravans belong with the travel system.
- **Sites ignore terrain and other kingdoms** — a daughter can be planted on the ocean (buildings will foundation-pillar out of it) or near a rival's claim. Site *quality* is a later concern.
- **Nothing links the towns afterward** — no trade, no migration back, no shared defense. The kingdom is a family tree, not yet an economy.
