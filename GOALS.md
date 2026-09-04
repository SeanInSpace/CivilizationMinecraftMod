# Goals

Working list for the self-sufficiency milestone. Items move to **Done** with a
one-line summary, and are dropped entirely once they have been played and hold up
— this file is a worklist, not a changelog. The changelog is the git history.

**Milestone:** a town that secures its own materials, feeds itself, equips itself,
and can be traded with — without the player doing any of it for them.

Where an item is not properly specified, the open question is written down rather
than papered over.

---

## Where the mod stands

Roughly 44,900 lines of source across three modules — `common` 21,500 (pure
simulation, never imports Minecraft), `neoforge` 20,700 (the world, the
entities, the blocks), `keystone` 2,600 (blueprints) — with 22,700 more again in
the tests. **950 tests across 98 classes, 0 failures**, and no TODO markers
anywhere in the source.

The milestone is met, and there is a counter to stand at now. A charter-founded
party of four climbs camp → homestead → fortified → village → town on its own,
feeds itself, equips itself from its own forge, walls itself, lays its own
streets, expands, and sells the player what it has spare. What that reads as on
real ground was measured this round on seed 8675309: eighty buildings drawn,
**none of them standing in water**, and a wall that re-staked itself twice as
the town grew out past it.

Two structural weaknesses are worth stating plainly at the top, because they
shape most of what follows.

**What is left untested needs a world, and mostly always will.** `neoforge` has
a test source set, and everything decidable without a running game has been
pulled out behind a seam and pinned: the auditor's geometry, the chest mirror,
the floor a building takes across a sloping plot, the underpinning and its
apron, where a digger may stand and in what order, and now the size every kind
in the catalogue is actually drawn at. What remains is not a seam waiting to be
cut. Choosing a stance ends in the game's own A*; laying a block ends in entity
handling; the site veto's judgement is one comparison wrapped in a sampling
loop. A seam in front of any of those would either omit the thing being tested
or need a fake larger than the code it checks. The JUnit game also never binds
item components, so no test here can hold an `ItemStack`. **The instrument for
the rest is the audit**, which walks the finished world and is the only thing
that can judge what these actually produced — which is why it is now
self-checking.

**The audit is the only instrument.** It is good, and it is now self-checking,
but everything it cannot reach — anything about how the town *reads* — still
needs a person. And **surveys must say how they were grown**: the same seed and
the same script give a town half again as spread out when its ground is
force-loaded as when it is not, so a figure that does not name its conditions is
worth less than one that does. The "Needs eyes" list is not a backlog; it is the
part of the work that cannot be delegated.

---

## Open

- [ ] **A town that grew unwatched draws nothing at all until somebody
      arrives.** The hypothesis this item carried for six runs — that the
      manager is starved of ticks — is now measured and **false**, and what is
      left in its place is sharper.

      **The pacing is instrumented, and it is fine.** `PersonEntityManager.tick`
      marks a `TickRate` as its first act, per manager instance rather than
      statically, over a window of one minute of real time; it reads out as
      `pace=/min pacegap= paceover=` on the `AUDIT` vitals line and at the head
      of `/civ info`, with the intended rate printed beside it. The gap is
      printed next to the mean because a mean hides it. `PerimeterLayer` now
      derives its post budget from real seconds since that settlement's last
      sweep (`DrawBudget`, capped at five seconds of arrears), so a per-pass
      budget is a per-second budget again.

      Measured: unwatched, under `/civ step` load, **pace=57.8/min with a worst
      gap of 4.8 s**; with a player standing in the town, **60.0/min against 60
      intended**. The server is not starving the manager on this build. Every
      timing figure in this file taken on the assumption that it was can be read
      at face value again.

      **What the world run actually showed.** A town grown 511 steps unwatched
      inside a 320×320 force-load box materialised **nothing**: every one of 120
      listed buildings read `[PENDING placement]` and the ring read `looked=0`.
      The same town then drew **80 buildings within 150 seconds** of a player
      standing in it. Nothing was slow. Nothing was drawn.

      So the fault is not pacing and never was: the unwatched materialisation
      path does not treat force-loaded ground as ground it may draw on. That is
      the next thing to chase, and it is one question rather than six — find
      what that path asks about a chunk before it will build on it, and why a
      force-load does not satisfy it.

      **A warning about the instrumentation, which cost more than the bug.**
      Five wrong conclusions in a row, each from a probe rather than from the
      code: a `return` read without measuring; a probe that skipped the
      `isLoaded` guard the real code has and so manufactured bedrock footings; a
      sample interval that aliased exactly with the ring length and made a moving
      cursor look frozen; a `static` counter shared across three dimension
      managers, so the Nether's empty world read as the overworld losing its
      kingdoms; and a probe capped to the first twelve calls, which only ever
      sampled the growth phase. **Measure the thing, in the function that does
      the work, across the window that matters.**

- [ ] **The warren layout cannot support a town, and separation was not why.**
      A prediction made in advance and falsified, which is the useful kind. The
      geometry fix took accepted plots from 31/48 to 48/48 — and the town got
      *smaller*, not larger.

      | layout | people | buildings | spread |
      | --- | --- | --- | --- |
      | ring | 96 | 113 | 268 |
      | stronghold | 96 | 113 | 318 |
      | warren, original (broken separation) | 26 | 40 | 476 |
      | warren, separation fixed | **16** | **30** | 328 |

      Sprawl is not the cause either: the original sprawled *further* (476) and
      supported *more* people. The cause is visible in where the early buildings
      land — distance from the centre, in order:

      - ring: 12, 28, 44, 44, 60, 60 — fills continuously outward, 16 farms
      - warren: 16, 16, 45, **93, 126, 166** — jumps, 2 farms

      A warren puts six huts in a knot and then crosses open ground to the next
      one. `Economy.WORTH_THE_WALK` is 20 and the void is 36. So the seventh
      building onward is out of reach, food never gets back to the granary, the
      population cannot grow, and the town never builds the farms that would
      feed it. Sixteen people and two farms is that loop closing.

      **It is not tunable.** Six huts at radius 16 make a knot 32 blocks across,
      so the next knot must clear it: searching all three constants under the
      separation and knot-legibility rules, the smallest void available is 35
      against the 36 now shipping. The ring layout's seventh building sits at 28.

      So it needs a design decision, not a constant:
      - pack more knots close in before spiralling out, accepting a denser
        centre than a warren "should" have; or
      - let a warren be what it looks like — a scatter of hamlets — and teach
        the simulation to run one: per-knot stores, hauling between knots, and a
        food chain that does not assume one granary within a short walk. That is
        the more interesting game and much the larger job.

      Skipped again this round, on purpose: it wants that decision and not
      another pass at the constants. The two-block spacing work is the evidence
      from the other side — widening a knot to the new separation makes a warren
      *bigger* rather than denser, which is written on `LayoutFitnessTest`'s
      warren ceiling and in the spacing commit.

- [ ] **Finish the wall.** The ring now follows the town, re-stakes when the
      town outgrows it and takes the old line down behind it. Good is not done,
      and three things are left standing.

      - **One post in 986 still will not go up.** Stable across every report, at
        a footing reading air — where `put` would succeed, so it is not a
        refusal. It was 3, then 8, then 6 on earlier rings: always under half a
        per cent, never zero. It no longer halts anything, because the sweep
        walks past a position it cannot place, and that is precisely why it will
        sit there forever unless somebody looks. Instrument the one position
        rather than reasoning about it — every guess at this class of fault so
        far has been wrong.
      - **A town cannot afford the wall that follows it.** Withdrawn once, and
        back in a new form now the ring grows with the town. In the world run
        Batchmere stalled at `wall=666/1292` with `coin=2`; on a 1400-step
        fixture the wall unit measured the same stall, 666 laid of 2612. Nothing
        is charged twice — the posts already raised travel with the town — so
        this is not the old finding returning. A town that keeps growing simply
        outruns its own income, and coin only ever enters through player trade.
        It is a siting and economy question rather than a wall one, and there is
        a market to trade at now, which changes the sum.
      - **The gates are unproven.** Nine on one ring, seven on another, and
        `tendGates` opens one for anybody facing it — but a closed fence gate is
        impassable to vanilla pathfinding, so a settler may not be able to path
        to the gate that would let them through. Never tested. `shut out of bed`
        refusing to settle is evidence that people cross, not proof of how.

- [ ] **The market's counters disagree.** The stall is a real screen now and the
      simulation behind it holds, but two things about it are somebody else's
      call rather than the market's own.

      - **The storehouse and the market price timber sixteen times apart.** The
        storehouse sells eight logs the emerald; the market buys at two emeralds
        the log. A town holding both can be pumped for about fifteen coin a
        click — an endowment in roughly a hundred and thirty of them. It
        predates this work and was deliberately not closed by it, because
        closing it means either making the founding arc's helping hand
        unaffordable or making the market a town's only counter, which is what
        [TRADE.md](TRADE.md) asks for. The constant carries the arithmetic so
        the next person cannot miss it; the decision is still open.
      - **The screen has been opened in a world by the manager**, and what it
        did there is recorded here:

        Opened in a world by the manager on seed 8675309: right-clicking the market post of a town with two coin in its treasury showed four goods, each with its reason beside the price — "They can spare it" on food, wood and stone, "More than they can store" on iron — and the footer "Prices move with what the town is short of. Paid in emeralds." The post stands a block off centre because the stall is turned to face its street, which is worth knowing before clicking at it from a script.

- [ ] **Settle the danger table.** Three of the four questions this item asked
      are decided: `Danger` in `common` names the rungs and both thresholds read
      from it, an unrecognised creature is no longer read as a zombie — the
      default is derived from what the game itself knows (nothing hostile 0, a
      boss 10, a raider or anything ranged 3, any other hostile 2), with drowned
      2, ghast 4, blaze, breeze and piglin brute 3, and the wither and the
      dragon 10 named outright — and the sweep now reaches everything the table
      grades rather than only what walks. One is left.

      - **Whether a creeper at 4 is right.** It is the number the whole feature
        turns on. Too low and a lone creeper barely registers; too high and a
        pair of them panics a town that could have handled it. This one is only
        answerable by watching a town meet one.

- [ ] **The watch is still narrower than the town's eyes, and it cuts both
      ways.** `PersonEntityManager.guardCombat` picks its target with
      `nearestHostile`, which collects `Monster` — the narrowing the sighting
      sweep has just come out of.

      - **A guard will not go for what the town is afraid of.** A phantom, a
        ghast, a slime and a provoked wolf all raise the alarm now and none of
        them is a `Monster`, so the town shuts its doors and the watch stands
        under the thing with its hands in its pockets.
      - **A guard will go for what the town is not afraid of.** An enderman and
        a zombified piglin are `Monster implements NeutralMob`: the sweep reads
        them at nothing until they are angry, and `nearestHostile` walks a guard
        into one anyway. The alarm catches up on the next step — hitting it makes
        the guard its target, which is exactly what `provoked` looks for — but
        the fight is started by the town, on a creature that was leaving it
        alone, without anybody being warned first.
      - **The wolf half of the retaliation is unreachable until this moves.** A
        wolf, a bee and a polar bear all carry the grudge goal now and no settler
        can ever provoke one, because the only settler who strikes anything is a
        guard and a guard only strikes `Monster`.

      The sweep's answer was to ask `Menace` instead of naming a class, and the
      same answer fits here. It is not a tidy-up: a guard picking fights with
      everything the table grades changes who dies in a night, and it wants
      measuring rather than assuming — a guard who walks out under a ghast is a
      dead guard, and `Menace.blowsUp` already exists because one creature needed
      fighting differently.

- [ ] **Two holes left in the demolition sweep.** A building can be pulled down
      now and the town notices, but the noticing has a window and a bug.

      - **A building drawn and destroyed inside one sweep is never written off.**
        The `WAS_A_BUILDING` mark — you cannot say a building has been
        demolished unless you saw it standing — is only ever taken by a sweep,
        and a sweep runs once a minute. Closing it means taking the mark where
        the structure is *drawn*, at both fidelities, which is a seam worth
        cutting. Every failure this way leaves a ruin on the books, which is the
        state the mod was in already; the failure the other way evicts a family
        from a house that is standing.
      - **A kingdom of two towns can never write off an undrawn building.**
        `TownAuditor.LAST_UNDRAWN` is cleared per `audit()` call and `audit()` is
        per settlement, so the second town's sweep wipes the first town's
        record and the two-sweep rule never fires for either. Pre-existing, and
        it means the "the simulation records it and the world never drew it"
        report has been silently inert in every kingdom that expanded.

- [ ] **The curve constants are a cliff, not a slope.** `ARC_PITCH` at 18 and
      the rank gap at 46 are honest sums now rather than literals, but the
      numbers themselves have not moved, and every attempt to tighten them made
      the town worse. One block of rounding slack stranded **six doors of
      forty-six** on the rough-ground fixture against four; a rank gap of 44 ran
      the crescents' chain out to **433 blocks against 358**. That second one is
      the cliff: a station that loses one plot leaves the plan short of its
      count, and a short plan is re-laid at twice the size with a third rank at
      every station. Recorded on `RANK_GAP`.

      The medians that are left — 7 to 9 blocks on `ring_streets`,
      `radial_concentric` and `crescents`, where every other arrangement now
      sits at 4 — want arcs spaced by the wider axis rather than evenly along
      themselves. That is a change to how offers are generated, not a constant
      to nudge, which is why it was not attempted here.

- [ ] **The lumber camp's post is not a post.** `LumberCampBlock` does not
      extend `BuildingPostBlock`, so `isPost` does not recognise the camp's own
      marker: it is neither laid first at the site nor withheld from the
      excavation that follows, and a digger will happily level it. A fault in
      the block hierarchy rather than in the geometry, found while building the
      drawn-size check and left alone so that the check could be pointed at
      `postFor` instead.

- [ ] **Two things the siting work measured and could not explain.** Both are
      recorded rather than resolved, because a hunch dressed as a fix is worse
      than an open question.

      - **The plan cache answers differently depending on how far it has been
        grown.** The same three figures off the same run read 39/41/2 from a
        fresh JVM and 32/32/1 from a warm one, before the plot-cursor fix. The
        cursor fix stabilised it and nothing is known to be wrong now, but a
        cache that depended on history was really there and nobody found out
        why.
      - **`relocatePending` still spends a ring slot when it decides not to
        move.** A relocation check that declines to move has not used a plot,
        and leaving the cursor past it costs the town a slot every step it sits
        on unfit ground. Handing it back in `relocateIfUnsuitable` measured
        better on seed 8675309 — 47 buildings against 46, cursor 166 against
        195, three stranded doors against four. The identical edit in
        `relocatePending` measured worse, three stranded doors to five. Left
        alone, and the disagreement written down.

- [ ] **A wall raised before the posts were walked along the line cannot be
      found again.** `Perimeter.laid` is an index into `ringPositions()`, and
      that walk changed: a leg used to be walked as an L — its whole x run at
      the starting z, then its whole z run at the finishing x — and is now
      walked along the straight line the staking checked. Same count of posts,
      different columns. So a save carrying a half-raised ring points its built
      stretch at ground nobody planted, leaves a gap where the old L ran, and
      orphans the real posts with nothing naming them; `Retired` carries the
      same wound, and a demolition circuit that finds nothing outstanding then
      calls `forgetRetired` and loses the line for good. Nothing in `common` can
      recover those columns — the walk that made them is gone, and keeping it
      for old lines is only right until the first line raised under the new one.
      A migration wants a save version and the old walk kept beside the new one
      to read it with, which is a decision about the save format. Until then a
      world carried across this change may have stray fence where its wall used
      to bend.

- [ ] **The concave hull never checks its own starting legs against a plot.**
      `Hull.concave` begins from the convex hull and tests keepouts only on the
      two legs of a dig-in split; `pushOut` and `relax` refuse a move that would
      cross a plot but neither repairs a crossing that is already there. So a
      building — or a plot the town has ordered — lying under a convex-hull leg
      shorter than `MAX_STRAIGHT_RUN` is crossed with nothing to correct it.
      Measured over 117 grown towns after the walk and the ordered-ground
      keepout landed: **68 buildings with a post inside their walls, 63 of them
      ordered when the ring was staked and 5 standing**, against 738 before.
      Left open rather than fixed because the obvious repair — dig any crossing
      leg regardless of its length — puts a keepout scan on every edge visit of
      the loop `RESTAKE_REVIEW` already measures at a second and a half on a
      town of two hundred, and that is a cost to weigh in a world rather than
      guess at. Queued plots are also deliberately not hull *points*: obliging
      the ring to enclose every order the moment it is made would drive a
      re-staking off one shed and undo `RESTAKE_GROWTH`.

- [ ] **A retired line's posts are not consulted when a plot is chosen.**
      `Settlement.standsOnTheWall` asks the standing ring only, so a town that
      has just moved its wall will happily site a building on the old line's
      raised posts. In a world it mostly heals — the excavation clears the
      building's own footprint and the demolition sweeps the rest — but nothing
      guarantees the order, and in the simulation nothing sweeps at all, so the
      fixtures count 695 of these across 117 towns and cannot tell which would
      survive contact with a player. Worth watching in a world before refusing
      the ground, which would sterilise a band right through the middle of a
      town for as long as the demolition takes.

- [ ] **A worldgen town is laid out in the wrong arrangement.**
      `WorldgenSettlements.resolve` calls `Founding.seeded`, which places every
      building through `town.arrangement()` — the culture's default for that
      centre — and only afterwards calls `settlement.setLayoutId(site.layoutId())`.
      So the buildings stand in one arrangement, the plot cursor was counted in
      that one, and every building the town raises from its first step is sited
      in another. The config weights and the `WORLDGEN raised … laid out as …`
      log both name the arrangement the town does not have. No overlap comes of
      it — `isPlotFree` still guards every placement — but the town is two plans
      interleaved. `Founding.seeded` wants the layout id alongside the culture
      id rather than after it.

- [ ] **An authored blueprint is never measured against the ground reserved for
      it.** `BlueprintPlacer.fromBlueprint` takes the file's own size and its
      own anchor cell, and neither is compared with `BuildingSizes` the way
      `procedural` is — no `SIZE MISMATCH`, no bound. Worse, an anchor that is
      not the middle of the structure puts the building off centre on its plot
      while `Footprint` records it as centred, so every overlap check in the mod
      is wrong by the anchor offset. Nothing ships a blueprint today, so this is
      a datapack's way of making two structures overlap rather than a fault a
      player can hit now.

---

## Needs eyes, not tests

Deliberately unordered. Everything here runs without throwing and produces the
right numbers; what no automated check can confirm is whether it *looks* right,
and no agent can answer any of it. This list is worked through by playing, not by
scheduling. Several of these want asking again now that the spacing and frontage
work has landed, which changes what a street looks like from the middle of it.

- [ ] Is a town wary of the sky now, and is that right? Phantoms, ghasts,
      slimes and the dragon reach the alarm for the first time. A phantom is
      worth 2, so three of them overhead empties the streets, and phantoms come
      three at a time. Watch a town on the third night without sleeping in it:
      `/civ info` shows the alarm, and the `AUDIT` lines say who went indoors.
      If a town spends every third night shut, the phantom is the entry to argue
      about, not the sweep.
- [ ] Does a ghast come for a Nether town from too far up? The citizen goal
      copies the slot a creature gives its hunt for players but not the condition
      on it, which is private to that goal. Vanilla lets a ghast take a player
      only within four blocks of its own height, and a ghast can see a hundred:
      so a settler is now fair game from an altitude a player would not be. The
      danger table already calls a ghast a thing that burns roofs from beyond
      the watch's reach, so this may simply be that sentence coming true — but
      it is the one place the "same terms as a player" rule is not kept, and it
      wants somebody standing in a Nether town to say whether it reads as
      menace or as harassment.
- [ ] Does a slime read as three creatures or as thirty-two? The arithmetic was
      not done when the sweep was widened, and it should be looked at before the
      number is: a slime is worth 2 whatever its size, a large one splits into
      four mediums and each of those into four smalls, so one spawn resolves to
      32 points of danger from sixteen creatures that at the smallest size do no
      damage at all. `Alarm.ALARMED_AT` is 6. A swamp town may simply live
      indoors. If it does, the answer is a size-aware entry in `Menace` rather
      than a narrower sweep — the table can see the entity, it just is not asked
      to look.
- [ ] Do creatures hunt citizens as though citizens were people? Every hostile
      that joins a level is now handed a target goal for our settlers, at the
      slot it already uses for players — not just zombies. Stand a town in front
      of a pillager patrol and watch whether the band comes for the people or
      walks past them; the same for a skeleton on a roof at night. This is the
      one place a reflective read is relied on (`Quarry`, the goal's
      `targetType`), and if the module layer refuses it the only symptom is
      every creature landing in the villager slot instead — which still works,
      just less deliberately.
- [ ] Does a provoked wolf read as a threat or as noise? A neutral creature
      counts only while its quarrel is with a person, and then only at the
      smallest rung on the scale. One wolf is a `WARY`; a pack of six empties
      the streets. Hit a wolf beside a town and watch what the town does — and
      note that the watch will not come, for the reason in the open item above.
- [ ] Does a repair read as repair? A cottage with its walls taken off came
      back whole within seconds, twenty times in four minutes — the whole
      blueprint re-materialised rather than builders laying courses. It is
      what kept the demolition write-off from ever firing, and whether it
      looks like a town mending a house or a house un-happening is a
      question for somebody watching it.
- [ ] The larder's ceiling counts granaries and storehouses but not warehouses,
      while the timber and stone ceilings count every store. Preserved rather
      than changed while making capacity scale with level, because it may well
      be deliberate — a warehouse full of logs is not a pantry. Worth somebody
      deciding on purpose.
- [ ] Three buildings a town still report `no way in`, down from nine. The
      doorway check itself is tested and correct, and the floor rule that was
      sinking buildings into hillsides is fixed — so what is left is most likely
      the plots the new rule perches rather than sinks, which the auditor also
      reports and which wants somebody to go and look at one.
- [ ] Does the palisade read as a wall around a town now, rather than a box
      around a field? It follows the buildings, drifts along contours, and moves
      outward when the town outgrows it; all three are claims about how it looks
      from inside the gate.
- [ ] Does digging read as labour now, at `Excavation.LABOUR_FACTOR` of two —
      and does a watched town still get its farm up in time? The factor is one
      named constant, so this is a question about a number, not a rewrite. Three
      was the tempting value and was deliberately not taken: watched digging is
      not on the clock, and the stall guard measures whether the queue head
      moved rather than how fast, so slowing the dig slows a watched town's
      building outright.
- [ ] Does a town read as sensibly frightened now? These are danger totals
      rather than head counts — see the danger table above — so one zombie in
      sight should be the woodcutters walking in while the guards deal with it,
      and six should be everybody indoors. `Alarm.WARY_AT`, `Alarm.ALARMED_AT`
      and a citizen's sight range are single constants, so this is a question
      about numbers rather than a rewrite.
- [ ] Does the rule that one creature is never a panic hold up in play? However
      nasty a lone hostile is, it is capped one rung below the tier that empties
      the streets, on the grounds that a watch is exactly what a town keeps so it
      does not have to hide from one of anything. Verified as *working* — a
      creeper parked beside a town for two hundred seconds never rang the bell —
      but whether it reads as composure or as complacency is a question for
      somebody watching it happen.
- [ ] Do civilians running from a creeper read as sensible, or as a stampede?
      They flee at ten blocks and only from creepers, which is the one hostile
      that kills by arriving. Everything else they ignore and let the watch
      handle.
- [ ] Does a guard fighting a creeper read as skill or as cowardice? One hit,
      then out of the blast for `FUSE_RESET_TICKS` (45), then back in. It is
      slower than standing and swinging, and the guard lives through it.
- [ ] Is the town's memory of a sighting the right length? Eight steps, and it
      exists so a hostile using cover cannot flicker the alarm on and off. Too
      short and the town dithers; too long and it hides from something that left.
- [ ] Does the bell read as an alarm rather than a noise? It rings once, on the
      rise to alarmed, from the watchtower if one stands. The watch rings when
      the danger it can see outweighs what it can hold — so the same two creepers
      are a Tuesday for a town with three guards and an emergency for a town with
      one. It will not ring for a single creature at all, whatever it is.
- [ ] Does the market screen read as a town talking, or as a shop? The reason
      beside each price is the whole design — "they are starving", "more than
      they can store" — and whether a shortage is legible from the road is
      exactly the thing no test can answer.
- [ ] Does opening a town's stores read as the town's stores, or as a loot chest?
- [ ] With two stores standing, does the split between them read as sensible —
      timber by the woods, stone by the mine — or as goods scattered at random?
- [ ] Does a builder fetching from the *nearest* store still read as too much
      walking, now that they no longer cross the village for every load?
- [ ] Are the animal pens actually separated, and do the beasts stay in them?
- [ ] Do the wider paths reach the doors, and stay clear of trees now?
- [ ] Do guards visibly carry swords once a smithy stands?
- [ ] Is a hillside build's material cost bearable in real time, or a grind?
- [ ] Does the quest board read well, or is it a wall of numbers?
- [ ] Does the town overview screen look the part, and are the icons legible?
- [ ] Do the lamp's building outlines read clearly, or is a dense town a blur?
- [ ] Does the town map read as a plan, and is the fixed claim scale right?
- [ ] Do turned buildings actually face their street, doors and stairs included?
- [ ] Does a crew of six digging a hillside read as a crew, or as a scrum?
- [ ] Does the excavation stake feel like a usable tool for marking out ground?
- [ ] Does the post-then-hole-then-walls sequence read as construction, or as clutter?
- [ ] Do the hollow planned plots on the map read as plans, or as bugs?
- [ ] Does a farmer working the rows read as farming — harvest, tend, replant?
      Worth a specific look now that the long-standing "crops are being lost"
      report has turned out to be a measurement artifact rather than a defect.
      Nothing is eating the crops; whether the rows *fill* at a sensible rate is
      a separate question and still an open one.
- [ ] Does the distress banner on the posts and the hall read clearly, and only
      when it should?
- [ ] Does a ruin read as a ruin? A building written off is gone from the books
      the moment the third sweep agrees, and nobody has watched that happen from
      inside the town it happened to.

---

## Done

*Short on purpose. Everything older than this batch has been dropped — it was
proven by the endurance and client playtests and it lives in the git history.
What is left is the nine units just landed, kept only until a run has been
watched over them.*

- [x] **Two blocks between any two walls.** The bare block that belonged to
      neither building is gone, and every literal spacing is the sum it was
      standing for: across thirteen arrangements the median gap went 6 → 4 and
      the tightest 3 → 2 everywhere, with no sprawl ceiling raised — and three
      layout faults fell out of the tests on the way, the bastide's odd block,
      the ring's spoke start and the spoke frontage on the diagonals.

- [x] **A building is drawn the size its plot was reserved for, and a test says
      so.** `BuildingSizes` is the one table, `BuildingSizesTest` pins the
      catalogue to it, and `BlueprintPlacerSizeTest` draws every one of the
      twenty-four kinds through a level-free `Site` seam and pins the placer to
      it too; the `SIZE MISMATCH` log stays for the one path a test cannot see,
      and never fired in the world run.

- [x] **A town out of good ground takes the least bad plot it looked at.**
      `WorldBridge.siteFault` scores a site instead of passing or failing it,
      both give-up paths take the best candidate the search already examined
      rather than the next unexamined slot, and a relocation refuses to move to
      ground that scores no better — 9 buildings to 45 on ground built to refuse
      in families, and 46 → 47 buildings with the cursor 195 → 166 on seed
      8675309.

- [x] **A town that outgrows its wall moves it, and the old line comes down.**
      `PerimeterPlanner.restakeIfOutgrown` reviews every hundredth step and
      adopts a new ring only when it is an eighth longer; the superseded line is
      retired and its posts pulled up, checking for two courses of fence or a
      gate so pens and bridge railings survive — 58 of 85 buildings outside
      their own wall at 700 steps became 0 at every staking.

- [x] **The danger scale has rungs, and a stranger is no longer a zombie.**
      `Danger` names the tiers in `common` and both thresholds are arithmetic on
      them; an unnamed creature is graded from what the game knows about it
      rather than read as a shambling corpse, and seven vanilla hostiles the
      table had never named are named.

- [x] **A family in a house the town cannot name is left alone.** `capacityOf`
      returns an `OptionalInt`, and unknown now means no shedding, no birth and
      no vacancy instead of meaning full — three members held at 200 steps where
      they used to be gone by 72 — while a household with no home or a phantom
      address still reads a plain zero, because reading those as unknown freezes
      a family at that address forever.

- [x] **A building can be pulled down, and the town notices.**
      `Settlement.removeBuilding` moves the goods to the loose pile, forgets the
      road, evicts or retires the household, cancels queued repairs and clears
      the work area; `TownAuditor.wallsStanding` writes a building off after
      three sweeps below a quarter of its wall ring, but only one it once saw at
      three quarters, so a field's one-block fence is not condemned the day it
      is drawn. Verified in a world by the manager:

      Tried in a world by the manager and NOT seen firing, for a reason worth more than a tick: a cottage with its walls and roof taken off was reported by the auditor as "mostly gone — 0% of its walls still standing" on three sweeps, but the town rebuilt it after every flattening — twenty fills over four minutes, twenty `Materialized` lines — so no three sweeps in a row ever agreed and the write-off count kept resetting. That is the repair planner doing what it is for, and the design defers to it on purpose; the write-off itself is held by thirty-four tests and has not been watched in a world. To see it, flatten a house in a town that has no timber to mend it with.

- [x] **The manager's pace is measured and the wall is paced by the clock.**
      `PersonEntityManager.tick` marks a `TickRate` per instance over a
      one-minute window, printed as `pace= pacegap= paceover=` on the `AUDIT`
      vitals line and at the head of `/civ info`; `PerimeterLayer` earns its
      posts from real seconds since its last sweep, capped at five seconds of
      arrears, so ten minutes away lays 120 posts and not fourteen thousand.

- [x] **The market says why, and the price is the town talking.** The economy
      had been built and never ticked; it has a screen of its own now
      (`MarketScreen` over `MarketPayload`/`MarketDealPayload`) that puts the
      reason beside the price, and four holes were closed on the way — an
      armoury sold at a coin an ingot to anybody who knew the ledger word, a
      log-to-plank money pump, a store that refused to sell goods it demonstrably
      owned, and a storehouse counter that destroyed the emeralds it took.
