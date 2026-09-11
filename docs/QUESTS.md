# Quests

How a town asks for help, how it notices you gave it, and what it pays.

The whole design rests on one rule: **an ask is a reading, not an invention.**
Nothing in the generator makes up a task. Every notice on a board comes off a
number one of the planners is already acting on — `FoodPlanner.isStarving`, the
threat level that empties the streets, the build queue that has stopped for want
of logs, a seam `Seam.isExhausted` says is finished. If the board could ask for
something that was not true of the place, the board would be a menu and the town
would be scenery.

---

## Where the pieces live

Everything that decides anything is in `:common`, in `com.civilization.sim.quest`,
and none of it has ever heard of Minecraft.

| Class | What it is |
|---|---|
| `Quest` | One notice. An immutable record; every change makes a new one. |
| `QuestKind` | `DELIVER`, `SLAY`, `CLEAR`, `VISIT`, and `UNKNOWN` for a name this build has not got. |
| `QuestState` | `OFFERED`, `ACCEPTED`, `DONE`, `EXPIRED`. |
| `Reward` | Coin, goods, standing. |
| `QuestBoard` | Five offers and the last ten finished jobs. Holds no opinions. |
| `Reputation` | What a town thinks of each person who has helped it. |
| `QuestPlanner` | Generation, lapsing, counting, payment. |
| `QuestVoice` | What the notice actually says, in the town's voice. |

The platform half is three files: `QuestBoardBlock` (the post you right-click),
`QuestBoardPayload` (the board, read for one player) and `QuestActionPayload`
(the button, travelling the other way), plus `QuestBoardScreen` on the client.

`QuestPlanner.advance` runs last in `Settlement.step`, after every other planner,
so each quest is a reading of what *this* step settled — a building a raid
flattened is on the board on the step it is flattened.

---

## The kinds, and what generates them

One notice at a time, every `OFFER_EVERY` (40) steps, chosen by weight from
everything the town currently has to complain about. A town never posts two
notices for the same kind and target at once.

| Kind | Fires when | Counted by |
|---|---|---|
| `DELIVER` food | `isStarving` (weight 40); food under `STARTING_PROVISIONS` (14); fed streak under `FED_COMFORT` (6) | donations at the storehouse |
| `DELIVER` timber | `wantsMoreTimber` **and** under `Market.LOT * 8` — 22 with a build queued, 10 without | as above |
| `DELIVER` stone | `wantsMoreStone` and the same threshold — 18 queued, 8 idle | as above |
| `DELIVER` iron | a smithy stands and iron is under 32 (6) | as above |
| `DELIVER` saplings | a lumber camp whose `Stand.isBare` (10) | as above |
| `SLAY` | threat at `Alarm.WARY_AT` or above; weight `6 + threat`, capped at 30 | hostiles a player kills inside the claim |
| `CLEAR` | a building past `RepairPlanner.SEVERE_DAMAGE` (12) | kills within `CLEAR_RADIUS` (24) of the wreck |
| `VISIT` | a cut-out mine (6), a stripped cutting ground (5), a building no road reaches (4), the newest building (3) | the player standing within `VISIT_RADIUS` (6) |

`VISIT` is the fallback and the reason a settlement with nothing wrong still has
a board worth reading. It is the only kind that is not a chore: the town is not
asking for anything, it is showing you the mine it has finished, the wood it has
stripped, or the building it forgot to build a road to. Every one of those is a
real fact about the place that a player would otherwise have to go looking for.

**Short is not the same as "not full."** `LumberPlanner.wantsMoreTimber` answers
whether felling is still worth doing, which is true of a town holding eight
hundred logs of a possible eight hundred and ninety-six. Reading it alone had
every settlement in the world permanently asking for wood. The reading that means
short is the market's — it wants more *and* it is nearly out — and the two share
one constant on purpose: a town buying timber at a premium on one counter while
advertising that it has plenty on the other is a town lying to somebody.

### Determinism

Generation is deterministic in `(settlement, step)`. There is no `Random`
anywhere in the planner; the roll is SplitMix64's finalizer over the settlement's
UUID and the step number. The same town stepped to the same number posts the same
notice, which is what makes `/civ step` an instrument rather than a slot machine —
a fault seen once can be seen again.

---

## The reward table

`tier` is `Reputation.tierOf` of the **highest** standing the town holds with
anybody — 0 for a town that has never met a soul, 3 for one with a sworn friend.
A board is pinned to a post rather than handed to a reader, so it cannot offer
each person a different tier without lying to somebody; it asks for what its best
friend could manage, and everybody else may try.

| Kind | Amount | Coin | Standing |
|---|---|---|---|
| `DELIVER` food | `16 × (2 + tier) / 2` | `Market.basePrice × amount × 3/2` | `2 + tier` |
| `DELIVER` timber, stone | `32 × (2 + tier) / 2` | as above | `2 + tier` |
| `DELIVER` iron, saplings | `8 × (2 + tier) / 2` | as above (saplings at 3/unit, having no market price) | `2 + tier` |
| `SLAY` | `4 + 2 × tier` | 6 a head | `3 + tier` |
| `CLEAR` | `5 + 2 × tier` | 8 a head | `4 + tier` |
| `VISIT` | 1 | `8 + 4 × tier` | `1 + tier` |

Half again on the market's own base, because the stall is eight units and a
button press and the board is a walk, a deadline and somebody else deciding when
they want it.

**The glut rider.** A `SLAY` or `CLEAR` also throws in 16 of anything the town is
glutted on — `Market.isGlutted`, the same reading behind "more than they can
store" at the stall. Not on a delivery: paying for goods in goods is how a player
learns to carry the same load round in a circle. Never food, whatever the shelf
says.

**Coin is emeralds, and there is no `civilization:coin` item.** The mod has one
money — the treasury — and the market already makes emeralds its physical form,
on the rule that every emerald a town pays out came out of its own books and
every one it takes in goes into them. A separate quest coin would be a second
currency no counter in the world accepts, which is worse than no reward at all.
So the board pays out of the same purse the stall does, and a coin earned here
buys grain there.

Offered coin is capped at the treasury as it stands, so a board never advertises
money the town has not got. It can still go broke between the offer and the claim,
and then it pays what is left — and honors the standing in full, because regard
is the one thing a settlement can always afford.

---

## Standing

Per settlement, not per kingdom. Helping a village on one coast buys nothing on
the other; that is the entire point.

| Rung | At | The town calls you |
|---|---|---|
| 0 | 0 | Stranger |
| 1 | 20 | Trusted |
| 2 | 60 | Honored |
| 3 | 120 | Sworn |

It is used for two things today. It scales what the board asks for and what it
pays (the table above), and it is shown in the board's header.

**It is deliberately not wired into market prices.** The seam is obvious enough —
`Market.sellOffer` computes one integer — and the arithmetic underneath it will
not take a discount. Stone's base price is 1 and its sell price is `max(base + 1,
base × 3/2)`, which is 2; there is no room below that which does not collapse the
spread the market's own comments exist to protect, and a sell price at or under
the buy price turns the treasury into a fountain a player can stand at. Widening
the spread first is a market change with its own reasoning to do, and it is not
the quest system's to make on the market's behalf. The room is left: everything a
price hook would need is a public reading on `Reputation`.

It only ever goes up. Nothing in the mod can yet wrong a town badly enough to
lose its regard — killing a settler is attributed to nobody — so a falling
standing would be a rule with no way to fire it. `Reputation.add` takes a
negative just as happily.

---

## How progress is actually counted

Three counters, three places, and none of them is a button on the board.

**Deliveries** are credited from the storehouse's donation path
(`StorehouseBlock.donate` → `QuestPlanner.creditDelivery`). A donation is the
ordinary gesture and the quest is the town having wanted it, which is why a
player who has taken no quest loses nothing by donating and why carrying more
than was asked is not wasted — the surplus is a donation, which is what all of it
always was.

**Kills** come from `LivingDeathEvent` in `CivilizationMod`, attributed to the hand
that did it: `getEntity` on the damage source, which is the archer rather than
the arrow. A hostile the town's own guards killed counts for nobody, or a player
could take a slaying quest and watch the watch finish it. What counts as a
hostile is `Menace`'s question rather than a class name, so a modded horror counts
and a wolf minding its own business does not — the same table that decides whether
the town was frightened of it in the first place.

**Arrivals** are the one outward look a simulation step takes:
`WorldBridge.playersWithin` names who is standing near a point. Asking the bridge,
rather than having the platform push arrivals in, is what keeps a `VISIT`
completable while nobody is clicking anything — you walk to the mark and the next
step notices.

Only the player who accepted a quest can make progress on it. Abandoning gives it
back to the board and loses whatever was done toward it: carrying one player's
half-finished count over to the next one is the town paying twice.

---

## Lifetimes

An unaccepted offer lapses after `OFFER_LIFETIME` (600 steps). Accepting resets
the clock to `ACCEPTED_LIFETIME` (1200) — an ask taken on its last step would
otherwise expire under the player on the walk out of the gate. **Finished work
never lapses**, however long the reward sits uncollected; a town that took back a
reward for a job it had already had done would be the single worst thing on this
board.

---

## The save format

A fifth sub-record beside `charter`, `holdings`, `defense` and `works`:

```
quests: { offered: [...], done: [...], standing: { <uuid>: <int> } }
```

Standing is filed here rather than under `holdings` because it is not a holding:
it is not spent, it cannot run out, and it belongs to a person rather than to the
town.

**The words travel with the notice.** A quest's title and detail are written once,
when it is posted, and then carried — into the payload, into the file — rather
than re-derived. A town asks for grain *because* it is starving, and by the time
anybody reads the board it may have eaten; a description worked out again on every
draw would quietly stop describing the thing it is describing.

---

## Adding a kind

1. A member on `QuestKind`, and a completion rule. A kind with no rule is a quest
   nobody can finish.
2. A branch in `QuestVoice.title` and `QuestVoice.detail`. The town's voice, short,
   and never a number the row beside it is already showing.
3. A `Want` in whichever `QuestPlanner.add*` method matches the trouble, with a
   weight you can argue for against the ones already there.
4. Amount, coin and standing in `amountFor` and `rewardFor`.
5. An icon in `QuestBoardScreen.iconFor`.

Old clients and old saves meet the new name as `UNKNOWN` and draw a row they
cannot act on rather than taking the screen — or the world — down.
