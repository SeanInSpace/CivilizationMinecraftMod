# Looking at a town

Two things live here, and between them they answer one question: *what does a
settlement actually look like, and is this change an improvement?*

Every picture of this simulation before these existed was a **port** of it —
the layout arithmetic rewritten somewhere else and plotted on blank paper.
Faithful to the formulas and to nothing else: no ground, no water, no roads, no
wall, and every plot drawn the same size because the port had never seen the
catalogue. A port also drifts from what it copied, silently, the first time
either side moves.

## The loop

```
/civ plan                →  the town writes itself into the server log
tools/survey.py          →  log becomes one JSON per settlement
tools/townview.html      →  drop the JSONs in and look
```

**1. Survey a town.** In game or on a console, stand near it (or force-load it)
and run:

```
/civ plan
```

It writes buildings at their real catalogue spans, the roads the path planner
actually laid, the ring as staked with its gates, and the ground underneath
sampled every four blocks across ±200. Thousands of lines, so it goes to the
log rather than to chat.

**2. Turn the log into surveys.**

```bash
python tools/survey.py neoforge/run/logs/latest.log surveys/ ring
```

The third argument is a label — how this run differs from the one you are
comparing it to. It is what the viewer shows in its picker, so `ring` and
`stronghold` are useful and `run3` is not.

**3. Open `tools/townview.html`.** Just open it; there is no build step and no
server. Drag the survey files onto the page, or use **Load surveys**.

## Comparing

The two panels share one camera. Pan or zoom either and both move, so the same
ground is under both pictures — which is the entire reason this exists rather
than two screenshots side by side. The bar along the bottom reports what
changed between them and, importantly, **says so when the two towns are not on
the same centre** and therefore are not comparable at all.

Layer buttons toggle ground, water, roads, wall and each class of building, so
"where do the dwellings sit relative to the industry" is a question you can
actually ask. Hovering a building names it and gives its plot span and facing.

To make a fair pair, hold everything still but the one thing:

```
civ found Comparison
civ culture "kingdoms:goblin"     ← before a single plot is taken
civ step 100  (x9)
civ plan
```

Same seed, same centre, same step count. The layout is the only functional
difference a culture carries — it holds an id, its penned animals, its layout
and its name lists, and nothing else the planners read — so a difference
between two such runs is a difference the layout caused.

## What a survey cannot tell you

Worth knowing before trusting one, because a convincing picture is exactly the
kind that gets believed past its evidence.

- **Ground is sampled every 4 blocks.** A one-block ditch or a narrow stream is
  invisible, and the slope judgements that actually refuse a plot are finer
  than this grid.
- **Height is a colour, not a contour.** A cliff and a slow rise of the same
  total climb look alike; the wall's real trouble is only ever with the former.
- **Outlines are plot claims, not footprints.** The claim is the ground the
  siting code reserves; the building inside it is smaller and turned to face
  the centre.
- **There is no time in it.** A town that grew east because the west flooded
  looks identical to one that simply started east.
- **It is one town on one seed.** Any claim about a layout in general needs
  several.
