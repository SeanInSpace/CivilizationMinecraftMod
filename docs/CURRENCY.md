# Currency

What the money is, and exactly what to change when it gets its real name.

The name is not settled. This file exists so that settling it is a small,
obvious edit instead of an archaeology expedition through every message the game
prints.

---

## What the money is

Two things, and the distinction is the whole design.

**Inside a town, money is an integer.** `Settlement.treasury` — no item, no
owner, no stack. Every planner in `:common` reads and moves it and none of them
has ever heard of Minecraft. The ledger word for a unit of it is "coin", and has
been since long before there was an item; that is what `Reward.coin()`,
`"Treasury 40 coin"` on the town map, and the `"coin"` field in the reward codec
all mean.

**In a hand, money is one item:** `civilization:coin`, registered in
`CivilizationItems` and reached through
`neoforge/src/main/java/com/civilization/neoforge/trade/Currency.java`.

The two meet only at a counter, and the invariant is that they meet one for one:
**every coin in the world came out of a treasury, and every coin that leaves the
world goes into one.** `MarketCounter` is where that is enforced — the payment is
counted before the ledger is touched and removed after, so a player is never
charged for goods a town would not sell and a town is never debited for goods
that were never handed over.

Nothing else issues money. The coin has no recipe on purpose: a crafting grid
that could make one would print money no ledger ever debited.

---

## Where the money changes hands

| Where | Which way |
|---|---|
| Market stall (`MarketCounter`, `MarketScreen`) | Both. The town buys your finds and sells its surplus, at prices its own shortages set. |
| Storehouse (`StorehouseBlock`) | Out of your purse. `WOOD_PER_COIN` logs to the coin, above a reserve. |
| Quest board (`QuestBoardBlock`) | Into your purse, out of the treasury. |

`MarketPlanner.FOOD_PER_COIN` and `StorehousePlanner.WOOD_PER_COIN` are the two
fixed rates; everything else is priced by `Market`.

---

## Renaming it

### The name players read — one line

This is almost certainly the change you want. **Edit one value:**

```
neoforge/src/main/resources/assets/civilization/lang/en_us.json
  "item.civilization.coin": "Coin"        <-- this string
```

That is all. Not one line of Java, not one asset, not a recompile. Every place
the game says the money's name out loud — the stall's footer, what the storehouse
tells you it takes, every "12 Coin" in a trade message — builds from
`Currency.name()`, which is that key. Rename it to "Mark", "Sovereign",
"Denarius" or anything else and the whole game says the new word at once, in
whatever language the reader is in.

The registry id can stay `coin` forever regardless. Players never see it.

### The registry id as well — four edits and a command

Only worth doing if `civilization:coin` itself is wrong (a `/give` command a
player types, a datapack that references it). Save compatibility is not required
here, so nothing needs migrating.

1. `Currency.ID` in `neoforge/.../trade/Currency.java` — **rename point 1.**
2. The lang **key** `item.civilization.coin` in `en_us.json` — **rename point 2.**
   (Vanilla derives the description id from the registry key, so the two must
   agree; `CurrencyTest` asserts they do.)
3. `NAME` at the top of `neoforge/tools/coin_art.py`.
4. Run `python neoforge/tools/coin_art.py`, which writes the texture, the model
   and the item definition under the new name. Delete the three old files.

Nothing else in Java spells the path — `CurrencyTest` walks the whole
`:neoforge` source tree and fails if anything does.

The one deliberate exception it allows is the `"coin"` **codec field** in
`CivilizationCodecs`, which is a save key for the treasury integer in a stored
reward. It is not the item's id and must not move with it, or every old save
would lose its unclaimed rewards.

Two more places say "coin" on purpose and would not follow a rename:

- **`/civ list`, `/civ stores` and the log lines beside them.** `coin %-6d` is a
  column label in a fixed-width operator table, sitting next to `pop`, `wall` and
  `food`. It names the ledger field, not the item, and a translated component
  cannot go through `String.format` without breaking the alignment that makes
  those tables readable.
- **Identifiers and javadoc throughout `:common`.** `Reward.coin()`,
  `Settlement.treasury`, "two coin for one log" — all about the integer.

Everything a player reads in a screen or a chat message does follow the rename:
the stall's header and footer, the town map's treasury line, the quest board's
reward column, and every trade message.

### What not to do

Do not write the money's name into a message. `Component.literal("2 coins")` is
a sentence that will still say "coins" the day somebody renames the money, and
it is a sentence only English speakers can read. Use `Currency.name()` for the
word and `Currency.amount(n)` for a sum.

`Currency.amount` renders "7 Coin", not "7 Coins". A plural is a second word and
a third place the name lives — one whose grammar the mod would be guessing at
the moment anybody translates it. Money is commonly uncountable in English
anyway ("7 gold", "7 silver"), so the reading costs nothing.

---

## Emeralds

The money used to be the vanilla emerald. It is not any more, and no counter
accepts one: `CurrencyTest` fails if `Items.EMERALD` appears anywhere in
`:neoforge`'s sources.

Emeralds are otherwise untouched, and deliberately so:

- **They are still a find a town will buy.** `Valuation` prices one at 40,
  beside diamond and gold, which is what it always was. Emeralds in an old save
  are now simply a valuable gem, and the stall is where you turn one into money.
- **They are still what a Founding Charter is crafted from**, and the Wayfinder
  with it. Those recipes must not want coin: the only source of coin is a town
  paying you, and the charter is how a player founds their first town. A charter
  priced in coin is a game nobody can start.
