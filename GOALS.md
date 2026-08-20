# Goals

Working list for the self-sufficiency milestone. Items are struck through and
summarised when done, then dropped entirely once they have been played and hold
up — this file is a worklist, not a changelog. The changelog is the git history.

**Milestone:** a town that secures its own materials, feeds itself, equips itself,
and can be traded with — without the player doing any of it for them.

---

## In progress

### 1. Supply limitedness → self-sufficiency
- [ ] Generic town stores (resource id → amount) replacing the four hardcoded ints
- [ ] Construction consumes materials; a build stalls when the town cannot pay
- [ ] Production chains close the loop: fell → stock → build, cut → stock → build
- [ ] `/civ info` reads the ledger

### 2. Warehouse
- [ ] Building + post; raises the town's storage ceiling
- [ ] Resources live in the warehouse rather than in an abstract global pool

### 3. Smith
- [ ] Consumes iron + fuel, produces tools / weapons / armour
- [ ] Tools wear out and are reissued: builders, miners, lumberjacks, farmers
- [ ] Guards draw weapons and armour; equipment shows on the entity
- [ ] Surplus goes to the market

### 4. Larger farms
- [ ] Bigger footprint, more yield, scaled work cost

### 5. Animal farm
- [ ] Separated pens, one species per pen
- [ ] Species list per culture (one default culture for now)
- [ ] Animals are real entities, penned and kept

### 6. Market hours
- [ ] A trading window in the day; stalls staffed only then
- [ ] Residents buy food and goods from the market
- [ ] The player can trade with the market during hours

### 7. Paths and layout
- [ ] Culture-driven settlement layout (one default to start)
- [ ] Path blocks laid from building entrance to building entrance

### 8. Quest board
- [ ] Block + screen listing the town's asks
- [ ] Tracks arbitrary named stats, so counters can be added without code
- [ ] Seeded with: mobs slain, bosses slain, hideouts cleared (hideouts not yet a thing)

---

## Then

- [ ] Push to `main`
- [ ] Branch, playtest through quickplay, fix what the play surfaces

---

## Done

*(nothing yet)*
