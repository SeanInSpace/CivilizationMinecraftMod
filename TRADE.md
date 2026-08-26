# Trading with a settlement

**Status:** designed, not built. Companion to [POPULATION.md](POPULATION.md) (who lives there) and [DEFENSE.md](DEFENSE.md) (who attacks). This one covers the only thing a player can currently *do* with a town, which today is nothing.

Code it would touch: `MarketBlock`, `Economy`, `Valuation`, `Settlement` (treasury), `Profession.TRADER`.

---

## The problem this solves

A player's entire vocabulary today is: place a charter, watch, right-click things to read reports, type debug commands. The town is an excellent simulation with no game attached.

Three separate defects all turn out to be the same missing thing — **there is nothing outside the town**:

- **Coin is minted from nothing.** `produceNear` calls `bank(levyOn(...))`; no money is taken from anybody. It is issuance, not a levy, and it was documented as a circulating loop that does not exist.
- **Income stops when the warehouses fill.** Measured: wood and stone pin at 53,312, coin peaks at 112,066 and then declines forever. A town is punished for being well stocked.
- **Population stalls at 401** because food cannot keep up. A hard ceiling with no way through it.

An outside buyer and seller fixes all three at once, and is one verb.

---

## The rule, in one sentence

> **A town with a trader will buy what it is short of and sell what it has spare, at prices its own shortages set — and that trade is the only way coin enters or leaves a settlement.**

---

## Who you trade with

**The market building, and only if the town has a living TRADER.** The profession exists and currently only restocks food stalls. This gives it the job it is named for.

No trader, no trade. That makes a profession the player can actually feel the absence of, and gives a reason to care that the smith died.

The market block already reports; right-clicking it opens the trade screen instead.

---

## Coin and emeralds

Internally, coin stays what it is: an integer on `Settlement`. Nothing about the ledger changes.

**Emeralds are the physical form of that coin, and exist only at the boundary.** When the town pays you it converts treasury → emeralds; when you pay the town, emeralds → treasury. Villagers never hold emeralds; their purses stay abstract.

This is worth stating plainly because it is the one place the two representations meet, and getting it wrong means either duplicating money or destroying it:

| | Treasury | Emeralds in the world |
|---|---|---|
| Town buys from you | **falls** | **created** into your inventory |
| Town sells to you | **rises** | **consumed** from your inventory |

The invariant: **every emerald that enters the world came out of a treasury, and every emerald that leaves it went into one.** A test can assert exactly that.

Using vanilla's `MerchantMenu` gets the familiar villager-trading screen for free, and players already understand its stock limits and restocking.

---

## Prices move with need

This is where the fun is, and it is the reason not to use a flat price list.

```
price = base × need
```

`base` comes from `Valuation`, extended to cover the ordinary goods a town uses — timber, stone, grain, iron — which it currently prices at nothing because nobody could buy them.

`need` is what makes a shortage legible:

| Town's position | Buying from you | Selling to you |
|---|---|---|
| **Starving / distress** | pays **double** for food | will not sell food at all |
| Below what a queued build wants | pays **1.5×** | will not sell it |
| Ordinary stock | pays base | sells at base + margin |
| At or near its ceiling | **refuses to buy** | sells cheap, and is glad to |

The town already knows all of this: `FoodPlanner.isStarving`, `BuildPlanner.requestProducer`, `MinePlanner.wantsMoreStone`, `LumberPlanner.wantsMoreTimber`, and the store ceilings. None of it needs inventing — it needs exposing.

The consequence is a live signal. Ride past a town paying double for wheat and you know it is in trouble, without opening a screen.

---

## What it will and will not do

**Buying from you** is bounded three ways, all of which already exist:

1. It buys only up to its storage ceiling — see the warehouse arithmetic in `MinePlanner.stoneCapacity`.
2. It buys only what it wants. A town with full granaries does not want grain at any price.
3. It cannot pay more than its treasury holds. A poor town says no, which is the point of the treasury being finite.

**Selling to you** keeps a reserve, so a town can never be stripped bare by a player with deep pockets:

- Never below what its build queue is waiting on.
- Never food below the starvation buffer.
- Never the last of anything.

A young settlement therefore has almost nothing to sell and desperately wants basics. A mature one sells you the surplus it cannot store. That is a natural difficulty curve nobody had to design.

---

## Where coin comes from, after this

The minting goes. This is the substantive change to the existing economy.

**Now:** production creates coin from nothing, forever, and the only way it leaves is wall posts.

**After:**

- **In:** the town sells goods to outsiders. Coin enters *because somebody paid it*.
- **Out:** the town buys from outsiders, and pays for public works.
- **Around:** wages, and eventually villagers buying at their own market.

A small production levy should probably survive — a town with no player nearby still needs to pay wages, and a settlement that can never afford its wall because nobody visited is a worse outcome than slightly loose money. But it becomes a trickle rather than the whole supply, and the headline number stops being "how long has this town existed" and starts being "how much has it traded".

**The open question I cannot answer alone:** should an untraded town be able to get rich on its own at all? Keeping a levy says yes, slowly. Removing it says a town's prosperity is the player's doing. The second is a stronger game and a lonelier simulation.

---

## How it should feel

**Early.** The town is poor and wants everything. It pays well for wood, stone and grain — things you can gather in ten minutes. This is a genuine early-game income for the player, and the town visibly grows because of it.

**Middle.** It has surplus and starts selling. You buy stone cheaply from a town whose warehouses are full. Its wall goes up on money you brought.

**Late.** It is rich, buys the luxuries in `Valuation` that no villager can produce, and sells you the iron and rare finds its miners turned up while you were away.

Across all three the loop is the same: **the town's problems are your opportunities.** That is the whole design.

---

## What this deliberately does not include

- **Villagers spending their own purses.** They accumulate wages and cannot spend them, which is a real hole — but it does not pay off until a player can tell one villager from another. See the note at the end of this file.
- **Wandering traders between settlements.** Interesting, and much later.
- **Haggling, reputation, contracts.** The price signal carries the drama already.

---

## Testing

Almost all of it lands in `common` and is testable without a game:

- Price responds to shortage: a starving town pays double for grain; a full one refuses it.
- The town never pays more than it holds.
- The reserve holds: selling can never take a town below its build queue's needs or its starvation buffer.
- Emerald conservation: every emerald created is matched by treasury spent, and vice versa.

The screen itself and the emerald handover need a running game, which is what the playtest harness is for.

---

## The thing to build after this

**Legible individuals.** Click a settler and see who they are, their trade, how they are faring, and what they are doing *and why*. No individual system pays off before that exists — including the purse. Once it does, villagers spending wages at a market the player stocked becomes a real loop, and the individual economy stops being bookkeeping and starts being stories.
