# American spelling audit

Every British spelling in the tree, marked and counted. **Nothing in this audit changes
code.** It exists so that a later agent — working when nobody else is editing — can convert
the codebase to American English in one pass, knowing in advance which hits are free text it
can rewrite blind and which are names and keys that need a real rename or a save migration.

Audited at commit `b688247` on branch `worktree-agent-a816151d692cfe68c`.

> **Status: the conversion has been done.** Categories A and B — prose and free-text
> strings — are converted; category C, the identifiers and keys, follows in the next
> commit. Everything below this line is the audit as it was written, describing the tree
> before any of it moved: read the tables as the worklist that was worked, not as the
> state of the tree. What was deliberately left British, and why, is in **Converted** at
> the end. The counts below are also one commit stale — the tree grew between the audit
> and the conversion, so the sweep found 2041 hits where the audit counted 2055.

## Scope

All 438 tracked files (`git ls-files`) except the three binaries — `gradle/wrapper/gradle-wrapper.jar`,
`neoforge/src/main/resources/icon.png`, `common/src/test/resources/terrain/seed8675309_town.hf`,
and the two generated wrapper scripts `gradlew` / `gradlew.bat`. That leaves Java in `common/`,
`neoforge/` and `keystone/` (main and test), all 12 `*.md`, all 118 `*.json` (lang files,
blockstates, models, and the four `surveys/*.json` fixtures), the five `*.gradle` files,
`gradle.properties`, both `neoforge.mods.toml`, `play.bat`, `server.bat`, `tools/survey.py`
and `tools/townview.html`.

## Totals

| Category | What it is | Hits |
| --- | --- | ---: |
| **A** | Prose — comments, javadoc, markdown, license text | **730** |
| **B** | Free-text string literals — chat, GUI, log and test-assertion messages | **41** |
| **C** | Identifiers and keys — class/method/field/constant/test-method names, string ids, JSON keys, CSS custom properties | **1284** |
| | **Total** | **2055** |

Spread over **206 files** and **47 distinct British forms**.

The shape of the problem is lopsided: three words — `centre`, `catalogue`, `neighbour` — are
**1579 of the 2055 hits (77%)**, and `centre` alone is 1136. Everything else is a long tail of
one- and two-hit prose. Converting the tail is a text edit; converting `centre` is a refactor.

### Where the hits are

| Area | Files | A | B | C | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| (repo root) other | 10 | 85 | 0 | 7 | 92 |
| common src/main | 60 | 352 | 1 | 514 | 867 |
| common src/test | 65 | 128 | 29 | 474 | 631 |
| keystone other | 1 | 1 | 0 | 0 | 1 |
| keystone src/main | 6 | 7 | 0 | 0 | 7 |
| keystone src/test | 2 | 3 | 1 | 1 | 5 |
| neoforge src/main | 43 | 129 | 5 | 227 | 361 |
| neoforge src/test | 12 | 15 | 3 | 33 | 51 |
| surveys other | 4 | 0 | 0 | 8 | 8 |
| tools other | 3 | 10 | 2 | 20 | 32 |

`keystone/` is effectively already American: 13 hits total — 11 prose, one test assertion message,
and one test-method name (`aBlueprintThatSaysNothingIsCentredOnItsOwnFloor`,
`keystone/src/test/java/com/keystone/AnchorTest.java:29`). The lang files carry **zero** British spellings — `neoforge/src/main/resources/assets/kingdoms/lang/en_us.json`
and `keystone/src/main/resources/assets/keystone/lang/en_us.json` are both clean, so no
translation key or player-visible block/item name needs touching.

## Summary table

Counts are occurrences, not lines; a line with two matches counts twice.

| British | American | A prose | B strings | C identifiers/keys | Total |
| --- | --- | ---: | ---: | ---: | ---: |
| centre | center | 192 | 11 | 933 | **1136** |
| catalogue | catalog | 120 | 8 | 208 | **336** |
| neighbour | neighbor | 90 | 6 | 11 | **107** |
| labour | labor | 21 | 1 | 34 | **56** |
| kerb | curb | 36 | 0 | 19 | **55** |
| programme | program | 39 | 3 | 12 | **54** |
| carriageway | roadway | 42 | 1 | 2 | **45** |
| levelled | leveled | 19 | 3 | 15 | **37** |
| armour | armor | 15 | 3 | 16 | **34** |
| behaviour | behavior | 23 | 0 | 5 | **28** |
| colour | color | 15 | 0 | 11 | **26** |
| recognise | recognize | 21 | 0 | 3 | **24** |
| defence | defense | 4 | 0 | 11 | **15** |
| judgement | judgment | 15 | 0 | 0 | **15** |
| honour | honor | 8 | 1 | 1 | **10** |
| burnt | burned | 7 | 0 | 0 | **7** |
| storey | story | 5 | 0 | 2 | **7** |
| cancelled | canceled | 4 | 0 | 1 | **5** |
| initialise | initialize | 4 | 1 | 0 | **5** |
| licence | license | 5 | 0 | 0 | **5** |
| materialise | materialize | 5 | 0 | 0 | **5** |
| maths | math | 4 | 0 | 0 | **4** |
| modelled | modeled | 4 | 0 | 0 | **4** |
| finalise | finalize | 3 | 0 | 0 | **3** |
| grey | gray | 3 | 0 | 0 | **3** |
| metre | meter | 2 | 0 | 0 | **2** |
| normalise | normalize | 2 | 0 | 0 | **2** |
| optimise | optimize | 1 | 1 | 0 | **2** |
| paralyse | paralyze | 2 | 0 | 0 | **2** |
| randomise | randomize | 2 | 0 | 0 | **2** |
| tarmac | asphalt | 2 | 0 | 0 | **2** |
| travelled | traveled | 1 | 1 | 0 | **2** |
| amortise | amortize | 1 | 0 | 0 | **1** |
| capitalise | capitalize | 1 | 0 | 0 | **1** |
| civilise | civilize | 1 | 0 | 0 | **1** |
| economise | economize | 1 | 0 | 0 | **1** |
| favour | favor | 1 | 0 | 0 | **1** |
| generalise | generalize | 1 | 0 | 0 | **1** |
| learnt | learned | 1 | 0 | 0 | **1** |
| metalled | paved | 1 | 0 | 0 | **1** |
| minimise | minimize | 1 | 0 | 0 | **1** |
| serialise | serialize | 0 | 1 | 0 | **1** |
| spiralled | spiraled | 1 | 0 | 0 | **1** |
| stabilise | stabilize | 1 | 0 | 0 | **1** |
| summarise | summarize | 1 | 0 | 0 | **1** |
| synchronise | synchronize | 1 | 0 | 0 | **1** |
| theatre | theater | 1 | 0 | 0 | **1** |
| **TOTAL (47 forms)** | | **730** | **41** | **1284** | **2055** |

### The ten most frequent

1. `centre` → center — 1136
2. `catalogue` → catalog — 336
3. `neighbour` → neighbor — 107
4. `labour` → labor — 56
5. `kerb` → curb — 55
6. `programme` → program — 54
7. `carriageway` → roadway — 45
8. `levelled` → leveled — 37
9. `armour` → armor — 34
10. `behaviour` → behavior — 28

### Vocabulary, not orthography — decide separately

Four of the words above are British **word choice**, not British **spelling**. Converting them
is a content decision, not a mechanical one, and the converting agent should be told which way
the project wants it before touching them:

| Word | Hits | Note |
| --- | ---: | --- |
| `kerb` | 55 | The American spelling of the stone edging a road *is* `curb`, so this one is orthographic-ish — but `KERB` is also a numeric clearance constant in three classes, and `curb` reads oddly as a distance. |
| `carriageway` | 45 | Pure vocabulary. American equivalent is `roadway` or `road surface`. Not a spelling error in any dictionary. |
| `tarmac` | 2 | Vocabulary (`asphalt`/`paving`). Both prose. |
| `metalled` | 1 | Vocabulary — "a metalled surface" (`neoforge/.../world/PathLayer.java:46`). American would be "paved". |
| `pavement` | 0 | Searched for, not present. |

`storey` (7) is genuinely orthographic (`story`, as in floors of a building) and safe to convert
where it means a floor — but note `A_STOREY` in `common/.../settlement/Founding.java:205` is a
height constant, and `story` is ambiguous in a codebase that also has narrative event text.

`burnt` (7), `learnt` (1) and `spelt` are acceptable in American English as adjectives; only
`burned`/`learned` as past-tense verbs are strongly preferred. All 8 hits are prose. Low priority.

## Category C — identifiers and keys

**1284 hits across 73 distinct identifiers.** These do not change with a search-and-replace: each
is a rename, and the ones marked cross-module are a rename that has to land in more than one
Gradle module at once.

| Identifier | British form | American form | Hits | Files | Modules | Kind |
| --- | --- | --- | ---: | ---: | --- | --- |
| `centre` | centre | center | 708 | 95 | common,neoforge,surveys,tools **(cross-module)** | java identifier x685, html identifier x12, string id/key literal x7, json key x4 |
| `CENTRE` | centre | center | 189 | 13 | common,neoforge **(cross-module)** | java identifier x189 |
| `BuildCatalogue` | catalogue | catalog | 77 | 29 | common,neoforge,root **(cross-module)** | java identifier x72, markdown code span x5 |
| `catalogue` | catalogue | catalog | 65 | 20 | common,neoforge **(cross-module)** | java identifier x63, string id/key literal x2 |
| `setCatalogue` | catalogue | catalog | 40 | 31 | common,neoforge **(cross-module)** | java identifier x40 |
| `laboursAs` | labour | labor | 22 | 9 | common,neoforge,root **(cross-module)** | java identifier x21, markdown code span x1 |
| `CATALOGUE` | catalogue | catalog | 15 | 4 | common | java identifier x15 |
| `ARMOUR` | armour | armor | 12 | 9 | common,neoforge **(cross-module)** | java identifier x12 |
| `centreX` | centre | center | 11 | 3 | neoforge | java identifier x11 |
| `centreZ` | centre | center | 11 | 3 | neoforge | java identifier x11 |
| `KERB` | kerb | curb | 8 | 4 | common | java identifier x8 |
| `defence` | defence | defense | 8 | 6 | surveys,tools **(cross-module)** | json id/enum value x4, string id/key literal x2, html identifier x2 |
| `catalogueAllows` | catalogue | catalog | 7 | 4 | common | java identifier x7 |
| `pioneersLabour` | labour | labor | 7 | 6 | common | java identifier x7 |
| `colour` | colour | color | 5 | 2 | neoforge,tools **(cross-module)** | html identifier x3, java identifier x2 |
| `neighbour` | neighbour | neighbor | 5 | 2 | neoforge | java identifier x5 |
| `BlockBehaviour` | behaviour | behavior | 4 | 1 | neoforge | java identifier x4 |
| `LABOUR_FACTOR` | labour | labor | 4 | 2 | neoforge,root | java identifier x3, markdown code span x1 |
| `PROGRAMME` | programme | program | 4 | 1 | neoforge | java identifier x4 |
| `againstTheKerb` | kerb | curb | 4 | 2 | common,neoforge **(cross-module)** | java identifier x4 |
| `atTheKerb` | kerb | curb | 4 | 1 | common | java identifier x4 |
| `catalogueRuns` | catalogue | catalog | 3 | 3 | common | java identifier x3 |
| `defenceless` | defence | defense | 3 | 1 | common | java identifier x3 |
| `levelledId` | levelled | leveled | 3 | 2 | common | java identifier x3 |
| `programmed` | programme | program | 3 | 1 | common | java identifier x3 |
| `withCentre` | centre | center | 3 | 3 | common,neoforge **(cross-module)** | java identifier x3 |
| `A_STOREY` | storey | story | 2 | 1 | common | java identifier x2 |
| `MAX_ARMOUR` | armour | armor | 2 | 1 | common | java identifier x2 |
| `NEIGHBOURS` | neighbour | neighbor | 2 | 1 | common | java identifier x2 |
| `NEIGHBOURS_CONSULTED` | neighbour | neighbor | 2 | 1 | common | java identifier x2 |
| `PLOTS_ENOUGH_FOR_ANY_PROGRAMME` | programme | program | 2 | 1 | common | java identifier x2 |
| `colourOf` | colour | color | 2 | 1 | neoforge | java identifier x2 |
| `newCentre` | centre | center | 2 | 1 | common | java identifier x2 |
| `payForLevelling` | levelled | leveled | 2 | 1 | common | java identifier x2 |
| `raiseTheProgrammes` | programme | program | 2 | 1 | common | java identifier x2 |
| `reasonColour` | colour | color | 2 | 1 | neoforge | java identifier x2 |
| `subtitleColour` | colour | color | 2 | 1 | neoforge | java identifier x2 |
| `worthLevelling` | levelled | leveled | 2 | 1 | common | java identifier x2 |
| `Kerb` | kerb | curb | 1 | 1 | common | string id/key literal x1 |
| `KerbTest` | kerb | curb | 1 | 1 | common | java identifier x1 |
| `LevellingTest` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `aBlueprintThatSaysNothingIsCentredOnItsOwnFloor` | centre | center | 1 | 1 | keystone | java identifier x1 |
| `aJobOfOneBlockIsCentredOnThatBlock` | centre | center | 1 | 1 | neoforge | java identifier x1 |
| `aLevelledBuildingIsStillTheSameBuilding` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `aLevelledOrStyledIdStillResolves` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `aNewBuildingJoinsTheNearestRoadRatherThanTheCentre` | centre | center | 1 | 1 | common | java identifier x1 |
| `aRewriteClearsWhatTheStoresRecogniseBeforeLayingOutAgain` | recognise | recognize | 1 | 1 | neoforge | java identifier x1 |
| `abuildingNorthOfTheCentreFacesSouthAsDrawn` | centre | center | 1 | 1 | common | java identifier x1 |
| `abuildingOnTheCentreHasSomethingSaneToDo` | centre | center | 1 | 1 | common | java identifier x1 |
| `abuildingSouthOfTheCentreTurnsRightAround` | centre | center | 1 | 1 | common | java identifier x1 |
| `alevelledBuildingIsSizedAsWhatItGrewFrom` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `alevelledBuildingIsStillFoundByItsRole` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `alevelledBuildingStillCountsAsWhatItIs` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `anAcceptedSiteComesBackWithItsCentre` | centre | center | 1 | 1 | neoforge | java identifier x1 |
| `anOldSaveLoadsAsATownAndKeepsItsBehaviour` | behaviour | behavior | 1 | 1 | common | java identifier x1 |
| `armour` | armour | armor | 1 | 1 | common | string id/key literal x1 |
| `everyLayoutBuildsAroundTheCentreItIsGiven` | centre | center | 1 | 1 | common | java identifier x1 |
| `everyStageStandsTheWholeProgrammeItClimbedThrough` | programme | program | 1 | 1 | common | java identifier x1 |
| `groundDecidesWhetherItCanBeLevelled` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `neighbouringRegionsDoNotShareAKey` | neighbour | neighbor | 1 | 1 | neoforge | java identifier x1 |
| `noLayoutLeavesAFieldBetweenNeighbouringWalls` | neighbour | neighbor | 1 | 1 | common | java identifier x1 |
| `notLevelled` | levelled | leveled | 1 | 1 | common | java identifier x1 |
| `nothingIsBuiltOnACarriageway` | carriageway | roadway | 1 | 1 | common | java identifier x1 |
| `nothingIsPulledIntoTheCarriageway` | carriageway | roadway | 1 | 1 | common | java identifier x1 |
| `pioneersLabourEveryTradeUntilTheVillage` | labour | labor | 1 | 1 | common | java identifier x1 |
| `theCaravanTradesSurplusBreadForIronAndHonoursTheReserve` | honour | honor | 1 | 1 | common | java identifier x1 |
| `theKerbStillFiresWhenThePlanOffersAtTheSeparation` | kerb | curb | 1 | 1 | common | java identifier x1 |
| `theKitIsSweptToTheStoreNearestTheTownCentre` | centre | center | 1 | 1 | common | java identifier x1 |
| `theRepairQueuedForItIsCancelledWithIt` | cancelled | canceled | 1 | 1 | common | java identifier x1 |
| `thingsTheCatalogueNeverAskedForAreLeftAlone` | catalogue | catalog | 1 | 1 | common | java identifier x1 |
| `threeOfAnythingUnrecognisedIsEnoughToEmptyTheStreets` | recognise | recognize | 1 | 1 | common | java identifier x1 |
| `toolsComeBeforeWeaponsWhichComeBeforeArmour` | armour | armor | 1 | 1 | common | java identifier x1 |
| `whatTheContainerSpeaksForItStillRecognises` | recognise | recognize | 1 | 1 | common | java identifier x1 |

### Renames that cross module boundaries

Eleven identifiers are referenced from more than one module. These are the ones that cannot be
converted module-by-module — the rename has to be atomic across `common/`, `neoforge/`,
`tools/` and `surveys/` together, or the build breaks between commits.

| Identifier | Declared at | Reaches | Why it crosses |
| --- | --- | --- | --- |
| `centre` (708 C hits, 95 files) | `Settlement.centre()` `common/src/main/java/com/kingdoms/sim/settlement/Settlement.java:239`; record components `TownPlan(SimPos centre, …)` `common/src/main/java/com/kingdoms/sim/culture/TownPlan.java:32`, `WorkArea(SimPos centre, int radius)` `common/src/main/java/com/kingdoms/sim/settlement/WorkArea.java:15`, `SettlementSites.Site(SimPos centre, …)` `common/src/main/java/com/kingdoms/sim/worldgen/SettlementSites.java:144`; `DigYard.centre()` `common/src/main/java/com/kingdoms/sim/work/DigYard.java:90` | common, neoforge, tools, surveys | Public accessors on `common` types that `neoforge` codecs, commands and view code call by name. Record components generate the accessor, so renaming the component renames the method. |
| `CENTRE` (189, 13 files) | `private static final SimPos CENTRE` in 13 test classes | common, neoforge | Not real API — 13 independent private test constants that happen to share a name. Safe, but 13 files. |
| `BuildCatalogue` (77, 29 files) | `common/src/main/java/com/kingdoms/sim/settlement/BuildCatalogue.java` | common, neoforge, repo root | Public class in `common` used throughout `neoforge`; also named in `BUILD_DECISIONS.md`, `GOALS.md`, `FOUNDING.md`. **The file itself must be renamed.** |
| `catalogue` (65, 20 files) | `Settlement.catalogue()` `common/src/main/java/com/kingdoms/sim/settlement/Settlement.java:1896`; parameters on `BuildPlanner.plotSpanOf/upgradePriority/chooseNext` | common, neoforge | Public accessor plus public static method parameters. |
| `setCatalogue` (40, 31 files) | `Settlement.setCatalogue(List<BuildingType>)` `common/src/main/java/com/kingdoms/sim/settlement/Settlement.java:1900` | common, neoforge | Public mutator called from 31 files — the widest call site spread of any single name here. |
| `laboursAs` (22, 9 files) | `Settlement.laboursAs(Person, Profession)` `common/src/main/java/com/kingdoms/sim/settlement/Settlement.java:1883` | common, neoforge, repo root | Public predicate; also documented by name in `FOUNDING.md:198`. |
| `ARMOUR` (12, 9 files) | `TownStores.ARMOUR` `common/src/main/java/com/kingdoms/sim/settlement/TownStores.java:42` | common, neoforge | Public constant. **The constant name is free to rename; its string value is not — see the do-not-touch list.** |
| `defence` (8, 6 files) | `tools/survey.py:34`, `tools/townview.html:169` + `--defence` CSS custom property at `tools/townview.html:27,38` | tools, surveys | A building-group id written into `surveys/*.json` by the Python surveyor and read back by the HTML viewer. Renaming needs all three sides plus the four committed fixtures. |
| `colour` (5, 2 files) | `tools/townview.html` legend field; `subtitleColour` / `reasonColour` in `neoforge/.../client/MarketScreen.java` | neoforge, tools | Two unrelated uses that share a spelling; can be converted independently. |
| `againstTheKerb` (4, 2 files) | `Settlement.againstTheKerb(SimPos, int)` `common/src/main/java/com/kingdoms/sim/settlement/Settlement.java:2276` (private) | common, neoforge | The method is private, but it is named in a javadoc `{@code}` reference from `common/src/main/java/com/kingdoms/sim/culture/Layout.java:248`. Rename must update the doc reference. |
| `withCentre` (3, 3 files) | `WorkArea.withCentre(SimPos newCentre)` `common/src/main/java/com/kingdoms/sim/settlement/WorkArea.java:25` | common, neoforge | Public wither on a record used by the neoforge codecs. |

### Files that must be renamed

Three tracked files carry a British spelling in their name. Renaming a Java file means renaming
its public type, so these are tied to the identifier renames above:

- `common/src/main/java/com/kingdoms/sim/settlement/BuildCatalogue.java` → `BuildCatalog.java`
- `common/src/test/java/com/kingdoms/sim/KerbTest.java` → `CurbTest.java`
- `common/src/test/java/com/kingdoms/sim/LevellingTest.java` → `LevelingTest.java`

`DEFENSE.md` is already American in its filename but holds 6 British hits in its body.

### Test-method names

Thirty of the 73 C identifiers are single-use JUnit method names written as sentences
(`abuildingSouthOfTheCentreTurnsRightAround`, `groundDecidesWhetherItCanBeLevelled`,
`theKerbStillFiresWhenThePlanOffersAtTheSeparation`, …). Each lives in exactly one file and is
referenced nowhere else, so they are the cheapest C hits to convert — but they are still renames,
and any `@DisplayName`, `--tests` filter or CI selector that names them has to move too.

Note that the existing casing is not uniform — `aLevelledBuildingIsStillTheSameBuilding`
(`common/src/test/java/com/kingdoms/sim/BuildingSizesTest.java:86`) and
`alevelledBuildingIsSizedAsWhatItGrewFrom`
(`common/src/test/java/com/kingdoms/sim/PlotOverlapTest.java:167`) capitalise the word after the
article differently. The identifier table reproduces each name exactly as it appears in the
source; do not normalise the casing while converting the spelling, or the diff stops being
reviewable.

## Category B — free-text strings

**41 hits.** Only six are actually visible to a player or a tool user; the other 35 are JUnit
assertion messages and `LOGGER` lines. Split out so the converting agent knows which ones are
worth reading carefully.

### Genuinely player-facing (4)

| File | Line | Text | Word |
| --- | ---: | --- | --- |
| `neoforge/src/main/java/com/kingdoms/neoforge/KingdomsBlocks.java` | 62 | Inn description: "beds for **travellers** and a yard for the caravans…" | travelled |
| `neoforge/src/main/java/com/kingdoms/neoforge/KingdomsBlocks.java` | 151 | Smithy description: "tools, weapons and **armour** are made here…" | armour |
| `neoforge/src/main/java/com/kingdoms/neoforge/command/KingdomsCommand.java` | 603 | `/civ` chat output: `.append(", centre ")` | centre |
| `common/src/main/java/com/kingdoms/sim/settlement/Founding.java` | 348 | Town event shown by `/civ info`: "Seeded short: the … **programme** wanted a …" | programme |

### Tool UI (2)

`tools/townview.html:557` "same centre — comparable" and `:558` "different centres — NOT the
same ground". Seen by whoever runs the survey viewer, not by players.

### Developer-facing (35)

Test assertion messages in `common/src/test/`, `neoforge/src/test/`, `keystone/src/test/`, plus
two `LOGGER` lines: `neoforge/src/main/java/com/kingdoms/neoforge/KingdomsMod.java:97`
("Initialised {} dimension simulation(s)") and
`neoforge/src/main/java/com/kingdoms/neoforge/world/BuildTest.java:213`
(`"BUILDTEST start layout={} centre={} …"`). All safe to rewrite; none is a key.

## Do not touch

These hits look convertible and are not. Changing any of them either loses player data or
breaks a name the project does not own.

### 1. Save-file field names — a rename silently destroys existing worlds

The mod's DFU codecs write these strings into level data. Renaming the string changes the NBT
key, and every existing save loses that field on load.

| Where | Literal | Consequence of renaming |
| --- | --- | --- |
| `neoforge/src/main/java/com/kingdoms/neoforge/save/KingdomsCodecs.java:469` | `SIM_POS.fieldOf("centre")` on `Settlement` | Every saved town loses its centre. |
| `neoforge/src/main/java/com/kingdoms/neoforge/save/KingdomsCodecs.java:351` | `SIM_POS.fieldOf("centre")` on `WorkArea` | Lumber and mine areas lose their centre. |
| `neoforge/src/main/java/com/kingdoms/neoforge/save/SiteLedger.java:70` | `optionalFieldOf("centre")` | Recorded sites lose their centre. |
| `common/src/main/java/com/kingdoms/sim/settlement/TownStores.java:42` | the **value** `"armour"` in `public static final String ARMOUR = "armour"` | Store maps are persisted with `Codec.unboundedMap(Codec.STRING, Codec.INT)` at `KingdomsCodecs.java:379` and `:452`, so `"armour"` is a live save key. Every town's armour stock silently drops to zero on load. |

The Java-side names (`Settlement.centre()`, `WorkArea::centre`, `TownStores.ARMOUR`) can all be
renamed freely — it is only the quoted strings that are frozen. If the project wants the strings
converted too, that is a codec migration, not a spelling pass, and it belongs in its own change.

### 2. Vanilla and NeoForge names

`BlockBehaviour` — `net.minecraft.world.level.block.state.BlockBehaviour`, 4 hits in
`neoforge/src/main/java/com/kingdoms/neoforge/KingdomsBlocks.java`. Mojang spells it with the
`u`; it is not ours to change. Anything matching `net.minecraft`, `net.neoforged`,
`com.mojang` or a `minecraft:` resource location is off-limits by the same rule.

### 3. The survey/viewer data contract

`"centre"` (JSON key) and `"defence"` (group id) in the four `surveys/*_town.json` fixtures are
written by `tools/survey.py` and read by `tools/townview.html`. All three sides plus the four
committed fixtures move together or none of them do. `tools/townview.html` also declares CSS
custom properties `--defence` at lines 27 and 38 that must match the group id.

### 4. License text

`LICENSE:664` contains "programmer" inside the GPL boilerplate. The license text is verbatim by
definition — leave it.

### 5. Not found, but check again before converting

No British spellings appear in either `en_us.json` lang file, in either `neoforge.mods.toml`, in
`gradle.properties`, in any blockstate/model/item JSON, or in any URL in the tree. Config and
command literals (`/civ` subcommand names, `debug.*` keys) are all already American. If the
conversion pass finds one of these, something changed after this audit.

## False positives excluded

Six matches were caught by the patterns and verified by eye to be coincidental substrings, not
British spellings. They are excluded from every count above:

| Pattern | Matched token | Why it is not a hit |
| --- | --- | --- |
| `tyre` | `EntityRenderersEvent` ×2, `EntityRendererProvider` ×2, `registerEntityRenderer` ×1 | "Enti**tyRe**ndere…" — vanilla NeoForge names, in `neoforge/.../client/KingdomsClient.java` and `PersonRenderer.java` |
| `cosy` | `ecosystem` | "e**cosy**stem" — `keystone/src/main/java/com/keystone/source/StructurizeSource.java:23` |

Two further near-misses were checked and are correct as written: `"Kerb"` at
`common/src/test/java/com/kingdoms/sim/KerbTest.java:45` is a **test town name**, a free string
rather than an id — it is counted in C by the single-token heuristic but is safe to change or
leave. And `analysis`, `emphasis`, `hypothesis`, `synthesis`, `paralysis`, `cancellation`,
`fulfilled`, `advertising`, `supervise`, `merchandise`, `otherwise`, `clockwise`, `promise`,
`exercise` and `compromise` are spelled identically in both dialects; the patterns are written to
exclude them (see the appendix) and none appears in the counts.

## Per-file counts

All 206 files with at least one hit, ordered by total.

| File | A | B | C | Total |
| --- | ---: | ---: | ---: | ---: |
| `common/src/test/java/com/kingdoms/sim/LayoutTest.java` | 24 | 1 | 120 | 145 |
| `common/src/main/java/com/kingdoms/sim/settlement/Settlement.java` | 57 | 0 | 84 | 141 |
| `common/src/main/java/com/kingdoms/sim/settlement/Founding.java` | 38 | 1 | 23 | 62 |
| `common/src/main/java/com/kingdoms/sim/culture/PlannedLayout.java` | 9 | 0 | 52 | 61 |
| `common/src/main/java/com/kingdoms/sim/culture/Layouts.java` | 16 | 0 | 34 | 50 |
| `common/src/main/java/com/kingdoms/sim/settlement/PerimeterPlanner.java` | 4 | 0 | 39 | 43 |
| `neoforge/src/main/java/com/kingdoms/neoforge/command/KingdomsCommand.java` | 10 | 1 | 32 | 43 |
| `neoforge/src/main/java/com/kingdoms/neoforge/view/PersonEntityManager.java` | 9 | 0 | 29 | 38 |
| `common/src/main/java/com/kingdoms/sim/culture/RadialStreetLayout.java` | 11 | 0 | 26 | 37 |
| `common/src/test/java/com/kingdoms/sim/SettlementFaultsTest.java` | 7 | 0 | 27 | 34 |
| `common/src/main/java/com/kingdoms/sim/settlement/PathPlanner.java` | 20 | 0 | 13 | 33 |
| `common/src/main/java/com/kingdoms/sim/settlement/BuildPlanner.java` | 10 | 0 | 22 | 32 |
| `common/src/main/java/com/kingdoms/sim/culture/CrescentLayout.java` | 11 | 0 | 18 | 29 |
| `common/src/main/java/com/kingdoms/sim/culture/CrossroadsLayout.java` | 8 | 0 | 21 | 29 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/BuildTest.java` | 13 | 1 | 15 | 29 |
| `common/src/main/java/com/kingdoms/sim/culture/Layout.java` | 16 | 0 | 12 | 28 |
| `common/src/test/java/com/kingdoms/sim/CrescentLayoutTest.java` | 1 | 0 | 27 | 28 |
| `common/src/main/java/com/kingdoms/sim/culture/ThorpLayout.java` | 5 | 0 | 22 | 27 |
| `common/src/test/java/com/kingdoms/sim/SettlementSitesTest.java` | 4 | 0 | 22 | 26 |
| `common/src/test/java/com/kingdoms/sim/BuildPlannerTest.java` | 2 | 1 | 22 | 25 |
| `common/src/test/java/com/kingdoms/sim/KerbTest.java` | 6 | 0 | 19 | 25 |
| `common/src/main/java/com/kingdoms/sim/culture/GreenLayout.java` | 10 | 0 | 14 | 24 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/BlueprintPlacer.java` | 21 | 0 | 2 | 23 |
| `common/src/main/java/com/kingdoms/sim/culture/BastideLayout.java` | 1 | 0 | 21 | 22 |
| `common/src/main/java/com/kingdoms/sim/culture/StreetLayout.java` | 3 | 0 | 19 | 22 |
| `common/src/main/java/com/kingdoms/sim/worldgen/SettlementSites.java` | 14 | 0 | 7 | 21 |
| `common/src/test/java/com/kingdoms/sim/StageProgressionTest.java` | 5 | 2 | 14 | 21 |
| `tools/townview.html` | 1 | 2 | 18 | 21 |
| `CHANGELOG.md` | 19 | 0 | 0 | 19 |
| `common/src/main/java/com/kingdoms/sim/settlement/PopulationPlanner.java` | 16 | 0 | 3 | 19 |
| `common/src/test/java/com/kingdoms/sim/LayoutFitnessTest.java` | 8 | 1 | 10 | 19 |
| `neoforge/src/main/java/com/kingdoms/neoforge/bridge/NeoForgeWorldBridge.java` | 5 | 0 | 14 | 19 |
| `BUILD_DECISIONS.md` | 15 | 0 | 3 | 18 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/Excavation.java` | 7 | 0 | 11 | 18 |
| `common/src/test/java/com/kingdoms/sim/SeededSettlementTest.java` | 6 | 2 | 9 | 17 |
| `common/src/test/java/com/kingdoms/sim/ThorpLayoutTest.java` | 1 | 1 | 15 | 17 |
| `common/src/test/java/com/kingdoms/sim/UnknownCapacityTest.java` | 9 | 2 | 6 | 17 |
| `GOALS.md` | 15 | 0 | 1 | 16 |
| `common/src/main/java/com/kingdoms/sim/culture/Culture.java` | 9 | 0 | 7 | 16 |
| `common/src/main/java/com/kingdoms/sim/kingdom/ExpansionPlanner.java` | 0 | 0 | 16 | 16 |
| `common/src/main/java/com/kingdoms/sim/settlement/StagePlanner.java` | 11 | 0 | 5 | 16 |
| `neoforge/src/main/java/com/kingdoms/neoforge/view/LumberjackWorker.java` | 0 | 0 | 16 | 16 |
| `common/src/test/java/com/kingdoms/sim/LeastBadSiteTest.java` | 0 | 0 | 15 | 15 |
| `common/src/test/java/com/kingdoms/sim/PlotOverlapTest.java` | 2 | 1 | 12 | 15 |
| `neoforge/src/main/java/com/kingdoms/neoforge/save/SiteLedger.java` | 9 | 0 | 6 | 15 |
| `common/src/test/java/com/kingdoms/sim/RealTerrainRoadsTest.java` | 3 | 1 | 10 | 14 |
| `neoforge/src/main/java/com/kingdoms/neoforge/view/ShepherdWorker.java` | 0 | 0 | 14 | 14 |
| `common/src/main/java/com/kingdoms/sim/culture/GridStreetLayout.java` | 1 | 0 | 12 | 13 |
| `common/src/main/java/com/kingdoms/sim/settlement/FoodPlanner.java` | 7 | 0 | 5 | 12 |
| `common/src/test/java/com/kingdoms/sim/FoundingEconomicsTest.java` | 7 | 1 | 4 | 12 |
| `common/src/test/java/com/kingdoms/sim/WallRestakeTest.java` | 1 | 0 | 11 | 12 |
| `neoforge/src/main/java/com/kingdoms/neoforge/client/TownMapScreen.java` | 2 | 0 | 10 | 12 |
| `neoforge/src/test/java/com/kingdoms/neoforge/save/PerimeterRetiredCodecTest.java` | 0 | 0 | 12 | 12 |
| `FOUNDING.md` | 10 | 0 | 1 | 11 |
| `common/src/test/java/com/kingdoms/sim/BuildingSizesTest.java` | 4 | 1 | 6 | 11 |
| `common/src/test/java/com/kingdoms/sim/DemolitionTest.java` | 5 | 1 | 5 | 11 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/TerrainOracle.java` | 3 | 0 | 8 | 11 |
| `common/src/test/java/com/kingdoms/sim/PavedStreetsTest.java` | 3 | 1 | 6 | 10 |
| `common/src/test/java/com/kingdoms/sim/UpgradeTest.java` | 0 | 0 | 10 | 10 |
| `neoforge/src/main/java/com/kingdoms/neoforge/client/PersonInventoryScreen.java` | 3 | 0 | 7 | 10 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/WorldgenSettlements.java` | 2 | 0 | 8 | 10 |
| `README.md` | 9 | 0 | 0 | 9 |
| `common/src/main/java/com/kingdoms/sim/platform/WorldBridge.java` | 7 | 0 | 2 | 9 |
| `common/src/test/java/com/kingdoms/sim/CultureTest.java` | 4 | 1 | 4 | 9 |
| `common/src/test/java/com/kingdoms/sim/FacingTest.java` | 1 | 3 | 5 | 9 |
| `common/src/test/java/com/kingdoms/sim/LevellingTest.java` | 3 | 3 | 3 | 9 |
| `neoforge/src/main/java/com/kingdoms/neoforge/save/KingdomsCodecs.java` | 3 | 0 | 6 | 9 |
| `neoforge/src/test/java/com/kingdoms/neoforge/world/BlueprintPlacerSizeTest.java` | 3 | 2 | 4 | 9 |
| `common/src/main/java/com/kingdoms/sim/culture/Wander.java` | 8 | 0 | 0 | 8 |
| `common/src/main/java/com/kingdoms/sim/settlement/TownStores.java` | 6 | 0 | 2 | 8 |
| `common/src/main/java/com/kingdoms/sim/settlement/WorkArea.java` | 0 | 0 | 8 | 8 |
| `common/src/test/java/com/kingdoms/sim/MarketTest.java` | 3 | 2 | 3 | 8 |
| `common/src/test/java/com/kingdoms/sim/RecordedTerrain.java` | 3 | 0 | 5 | 8 |
| `common/src/test/java/com/kingdoms/sim/TerrainFake.java` | 4 | 0 | 4 | 8 |
| `neoforge/src/main/java/com/kingdoms/neoforge/view/MinerWorker.java` | 0 | 0 | 8 | 8 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/LevelStoreWorld.java` | 0 | 0 | 8 | 8 |
| `neoforge/src/test/java/com/kingdoms/neoforge/save/SettlementLayoutCodecTest.java` | 2 | 0 | 6 | 8 |
| `tools/README.md` | 8 | 0 | 0 | 8 |
| `common/src/main/java/com/kingdoms/sim/settlement/SmithPlanner.java` | 3 | 0 | 4 | 7 |
| `common/src/test/java/com/kingdoms/sim/PathNetworkTest.java` | 1 | 2 | 4 | 7 |
| `neoforge/src/main/java/com/kingdoms/neoforge/KingdomsBlocks.java` | 1 | 2 | 4 | 7 |
| `neoforge/src/main/java/com/kingdoms/neoforge/client/KingdomsPanel.java` | 2 | 0 | 5 | 7 |
| `neoforge/src/main/java/com/kingdoms/neoforge/net/TownMapPayload.java` | 0 | 0 | 7 | 7 |
| `neoforge/src/test/java/com/kingdoms/neoforge/save/SiteLedgerTest.java` | 1 | 0 | 6 | 7 |
| `DEFENSE.md` | 5 | 0 | 1 | 6 |
| `POPULATION.md` | 5 | 0 | 1 | 6 |
| `common/src/main/java/com/kingdoms/sim/culture/TownPlan.java` | 3 | 0 | 3 | 6 |
| `common/src/test/java/com/kingdoms/sim/AlarmTest.java` | 1 | 0 | 5 | 6 |
| `neoforge/src/main/java/com/kingdoms/neoforge/client/MarketScreen.java` | 4 | 0 | 2 | 6 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/PerimeterLayer.java` | 6 | 0 | 0 | 6 |
| `KEYSTONE.md` | 5 | 0 | 0 | 5 |
| `common/src/main/java/com/kingdoms/sim/settlement/BuildingSizes.java` | 5 | 0 | 0 | 5 |
| `common/src/main/java/com/kingdoms/sim/settlement/RaidPlanner.java` | 1 | 0 | 4 | 5 |
| `common/src/main/java/com/kingdoms/sim/settlement/RoadRouter.java` | 3 | 0 | 2 | 5 |
| `common/src/test/java/com/kingdoms/sim/ExpansionPlannerTest.java` | 0 | 0 | 5 | 5 |
| `common/src/test/java/com/kingdoms/sim/PopulationTest.java` | 2 | 0 | 3 | 5 |
| `common/src/test/java/com/kingdoms/sim/StarvationTest.java` | 0 | 0 | 5 | 5 |
| `common/src/test/java/com/kingdoms/sim/TownEconomyTest.java` | 0 | 0 | 5 | 5 |
| `common/src/test/java/com/kingdoms/sim/VillageLifeTest.java` | 0 | 0 | 5 | 5 |
| `neoforge/src/main/java/com/kingdoms/neoforge/item/FoundingCharterItem.java` | 0 | 0 | 5 | 5 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/TownAuditor.java` | 5 | 0 | 0 | 5 |
| `common/src/main/java/com/kingdoms/sim/economy/Market.java` | 4 | 0 | 0 | 4 |
| `common/src/main/java/com/kingdoms/sim/settlement/BuildCatalogue.java` | 2 | 0 | 2 | 4 |
| `common/src/main/java/com/kingdoms/sim/settlement/PathNetwork.java` | 4 | 0 | 0 | 4 |
| `common/src/main/java/com/kingdoms/sim/settlement/RepairPlanner.java` | 3 | 0 | 1 | 4 |
| `common/src/test/java/com/kingdoms/sim/RaidPlannerTest.java` | 0 | 0 | 4 | 4 |
| `common/src/test/java/com/kingdoms/sim/SiteChoiceTest.java` | 0 | 0 | 4 | 4 |
| `common/src/test/java/com/kingdoms/sim/UnwatchedFeedingTest.java` | 0 | 0 | 4 | 4 |
| `keystone/src/test/java/com/keystone/AnchorTest.java` | 2 | 1 | 1 | 4 |
| `neoforge/src/main/java/com/kingdoms/neoforge/block/BuildingPostBlock.java` | 2 | 0 | 2 | 4 |
| `neoforge/src/main/java/com/kingdoms/neoforge/block/LumberCampBlock.java` | 1 | 0 | 3 | 4 |
| `neoforge/src/test/java/com/kingdoms/neoforge/world/FoundationTest.java` | 4 | 0 | 0 | 4 |
| `common/src/main/java/com/kingdoms/sim/settlement/BuildingRole.java` | 3 | 0 | 0 | 3 |
| `common/src/main/java/com/kingdoms/sim/work/DigYard.java` | 1 | 0 | 2 | 3 |
| `common/src/test/java/com/kingdoms/sim/EconomyTest.java` | 0 | 0 | 3 | 3 |
| `common/src/test/java/com/kingdoms/sim/EmptyHouseholdTest.java` | 1 | 0 | 2 | 3 |
| `common/src/test/java/com/kingdoms/sim/StorehouseTradeTest.java` | 0 | 0 | 3 | 3 |
| `common/src/test/java/com/kingdoms/sim/StressTest.java` | 0 | 0 | 3 | 3 |
| `common/src/test/java/com/kingdoms/sim/SupplyPlannerTest.java` | 0 | 0 | 3 | 3 |
| `neoforge/src/main/java/com/kingdoms/neoforge/KingdomsMod.java` | 2 | 1 | 0 | 3 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/PathLayer.java` | 3 | 0 | 0 | 3 |
| `neoforge/src/test/java/com/kingdoms/neoforge/world/BuildTestTrimTest.java` | 3 | 0 | 0 | 3 |
| `tools/survey.py` | 1 | 0 | 2 | 3 |
| `common/src/main/java/com/kingdoms/sim/economy/Valuation.java` | 2 | 0 | 0 | 2 |
| `common/src/main/java/com/kingdoms/sim/person/Appetite.java` | 2 | 0 | 0 | 2 |
| `common/src/main/java/com/kingdoms/sim/settlement/Building.java` | 2 | 0 | 0 | 2 |
| `common/src/main/java/com/kingdoms/sim/settlement/FieldRoster.java` | 0 | 0 | 2 | 2 |
| `common/src/main/java/com/kingdoms/sim/settlement/JobPlanner.java` | 1 | 0 | 1 | 2 |
| `common/src/main/java/com/kingdoms/sim/settlement/MinePlanner.java` | 1 | 0 | 1 | 2 |
| `common/src/main/java/com/kingdoms/sim/settlement/SettlementStage.java` | 2 | 0 | 0 | 2 |
| `common/src/main/java/com/kingdoms/sim/settlement/SupplyPlanner.java` | 0 | 0 | 2 | 2 |
| `common/src/test/java/com/kingdoms/sim/FieldRosterTest.java` | 0 | 0 | 2 | 2 |
| `common/src/test/java/com/kingdoms/sim/FullPocketsTest.java` | 0 | 0 | 2 | 2 |
| `common/src/test/java/com/kingdoms/sim/HaulPlannerTest.java` | 1 | 0 | 1 | 2 |
| `common/src/test/java/com/kingdoms/sim/HullSimplicityTest.java` | 2 | 0 | 0 | 2 |
| `common/src/test/java/com/kingdoms/sim/SitingTest.java` | 0 | 0 | 2 | 2 |
| `common/src/test/java/com/kingdoms/sim/StoreMirrorTest.java` | 0 | 1 | 1 | 2 |
| `common/src/test/java/com/kingdoms/sim/VisibleConstructionTest.java` | 0 | 0 | 2 | 2 |
| `keystone/src/main/java/com/keystone/blueprint/StructurizeNbt.java` | 2 | 0 | 0 | 2 |
| `neoforge/src/main/java/com/kingdoms/neoforge/block/MineBlock.java` | 0 | 0 | 2 | 2 |
| `neoforge/src/main/java/com/kingdoms/neoforge/bridge/Menace.java` | 2 | 0 | 0 | 2 |
| `neoforge/src/main/java/com/kingdoms/neoforge/client/TownOverviewScreen.java` | 1 | 0 | 1 | 2 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/Bridge.java` | 2 | 0 | 0 | 2 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/DrawBudget.java` | 2 | 0 | 0 | 2 |
| `neoforge/src/test/java/com/kingdoms/neoforge/net/MarketPayloadTest.java` | 0 | 0 | 2 | 2 |
| `neoforge/src/test/java/com/kingdoms/neoforge/world/StoreSyncTest.java` | 1 | 0 | 1 | 2 |
| `surveys/noswimming-estimate_town.json` | 0 | 0 | 2 | 2 |
| `surveys/oracle-estimate_town.json` | 0 | 0 | 2 | 2 |
| `surveys/organic-comparison_town.json` | 0 | 0 | 2 | 2 |
| `surveys/ring-comparison_town.json` | 0 | 0 | 2 | 2 |
| `LICENSE` | 1 | 0 | 0 | 1 |
| `PLAYING.md` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/geom/Hull.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/geom/Ways.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/kingdom/Kingdom.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/person/Foods.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/person/Person.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/BuildTask.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/BuildingType.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/Footprint.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/InnPlanner.java` | 0 | 0 | 1 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/LumberPlanner.java` | 0 | 0 | 1 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/Perimeter.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/Resources.java` | 0 | 0 | 1 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/StorehousePlanner.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/settlement/Tallies.java` | 1 | 0 | 0 | 1 |
| `common/src/main/java/com/kingdoms/sim/work/PublicWorks.java` | 1 | 0 | 0 | 1 |
| `common/src/test/java/com/kingdoms/sim/AccessRepairTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/AppetiteTest.java` | 1 | 0 | 0 | 1 |
| `common/src/test/java/com/kingdoms/sim/BuildOrderTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/DangerTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/DigYardTest.java` | 0 | 1 | 0 | 1 |
| `common/src/test/java/com/kingdoms/sim/FoodPlannerTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/FootprintTest.java` | 1 | 0 | 0 | 1 |
| `common/src/test/java/com/kingdoms/sim/FoundingTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/HeightField.java` | 1 | 0 | 0 | 1 |
| `common/src/test/java/com/kingdoms/sim/PublicWorksTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/RepairPlannerTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/ResourcesTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/SettlementStoresTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/SupplyTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/WallShapeTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/WatchedProductionTest.java` | 0 | 0 | 1 | 1 |
| `common/src/test/java/com/kingdoms/sim/WorkforceTest.java` | 1 | 0 | 0 | 1 |
| `keystone/build.gradle` | 1 | 0 | 0 | 1 |
| `keystone/src/main/java/com/keystone/api/Blueprints.java` | 1 | 0 | 0 | 1 |
| `keystone/src/main/java/com/keystone/api/Placer.java` | 1 | 0 | 0 | 1 |
| `keystone/src/main/java/com/keystone/blueprint/BlockSubstitutions.java` | 1 | 0 | 0 | 1 |
| `keystone/src/main/java/com/keystone/blueprint/Transforms.java` | 1 | 0 | 0 | 1 |
| `keystone/src/main/java/com/keystone/source/FolderSource.java` | 1 | 0 | 0 | 1 |
| `keystone/src/test/java/com/keystone/TransformsTest.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/KingdomsAttachments.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/KingdomsItems.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/block/StoreChestBlockEntity.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/client/SupplyScreen.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/item/ExcavationStakeItem.java` | 0 | 0 | 1 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/item/TownMapItem.java` | 0 | 0 | 1 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/net/TownOverviewPayload.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/view/FarmWorker.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/view/Foreman.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/Shelves.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/main/java/com/kingdoms/neoforge/world/StoreSync.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/test/java/com/kingdoms/neoforge/block/StoreChestBlockEntityTest.java` | 0 | 0 | 1 | 1 |
| `neoforge/src/test/java/com/kingdoms/neoforge/bridge/MenaceTest.java` | 1 | 0 | 0 | 1 |
| `neoforge/src/test/java/com/kingdoms/neoforge/view/FarmWorkerTest.java` | 0 | 1 | 0 | 1 |
| `neoforge/src/test/java/com/kingdoms/neoforge/world/ExcavationReachTest.java` | 0 | 0 | 1 | 1 |

## Appendix — reproducing this audit

Every count above comes from one `git grep` per word, run from the repository root. The general
form is:

```sh
git grep -n -I -i -o -P "[A-Za-z0-9_]*<CORE>[A-Za-z0-9_]*" \
    -- ':!*.png' ':!*.jar' ':!*.hf' ':!gradlew' ':!gradlew.bat'
```

`-P` (PCRE) is required — the patterns use `(?!…)` lookaheads. `-i` catches
`CENTRE`/`Centre`/`centre`; the `[A-Za-z0-9_]*` on both sides is what makes camelCase and
SCREAMING_SNAKE match (`getNeighbour`, `MAX_ARMOUR`) where `\b` would not, and it returns the
whole surrounding token so identifiers can be inventoried. `-I` skips binaries; the pathspec
exclusions drop the three binary files and the two generated Gradle wrapper scripts.

Three pattern shapes appear, for three reasons:

- **`-our` / `-re` / `-ence` / `-ogue`** words take a plain stem plus `[a-z]*`
  (`colour[a-z]*` catches `colours`, `coloured`, `colouring`).
- **`-ise` / `-yse` words take an explicit suffix alternation**, never a bare `-is` stem —
  `organis(e|es|ed|ing|er|ers|ation|…)` matches `organisation` but not `organism`, and
  `analys(e|ed|ing|er|ers)` matches `analysed` but not the dialect-neutral `analysis`.
- **Single-`l` words take a negative lookahead** so the American double-`l` form is not swept up:
  `fulfil(?!l)(s|ment|ments)?` matches `fulfilment` and rejects `fulfilled`.

The complete list — British form, American form, and the finished pattern handed to `-P` with
`<CORE>` already substituted in — in the order the sweep ran them. Paste any third column
straight into the `git grep` above in place of the whole quoted pattern:

```
colour	color	[A-Za-z0-9_]*colour[a-z]*[A-Za-z0-9_]*
behaviour	behavior	[A-Za-z0-9_]*behaviour[a-z]*[A-Za-z0-9_]*
favour	favor	[A-Za-z0-9_]*favour[a-z]*[A-Za-z0-9_]*
honour	honor	[A-Za-z0-9_]*honour[a-z]*[A-Za-z0-9_]*
labour	labor	[A-Za-z0-9_]*labour[a-z]*[A-Za-z0-9_]*
neighbour	neighbor	[A-Za-z0-9_]*neighbour[a-z]*[A-Za-z0-9_]*
harbour	harbor	[A-Za-z0-9_]*harbour[a-z]*[A-Za-z0-9_]*
armour	armor	[A-Za-z0-9_]*armour[a-z]*[A-Za-z0-9_]*
humour	humor	[A-Za-z0-9_]*humour[a-z]*[A-Za-z0-9_]*
savour	savor	[A-Za-z0-9_]*savour[a-z]*[A-Za-z0-9_]*
vapour	vapor	[A-Za-z0-9_]*vapour[a-z]*[A-Za-z0-9_]*
rumour	rumor	[A-Za-z0-9_]*rumour[a-z]*[A-Za-z0-9_]*
flavour	flavor	[A-Za-z0-9_]*flavour[a-z]*[A-Za-z0-9_]*
odour	odor	[A-Za-z0-9_]*odour[a-z]*[A-Za-z0-9_]*
valour	valor	[A-Za-z0-9_]*valour[a-z]*[A-Za-z0-9_]*
splendour	splendor	[A-Za-z0-9_]*splendour[a-z]*[A-Za-z0-9_]*
endeavour	endeavor	[A-Za-z0-9_]*endeavour[a-z]*[A-Za-z0-9_]*
demeanour	demeanor	[A-Za-z0-9_]*demeanour[a-z]*[A-Za-z0-9_]*
parlour	parlor	[A-Za-z0-9_]*parlour[a-z]*[A-Za-z0-9_]*
rigour	rigor	[A-Za-z0-9_]*rigour[a-z]*[A-Za-z0-9_]*
vigour	vigor	[A-Za-z0-9_]*vigour[a-z]*[A-Za-z0-9_]*
candour	candor	[A-Za-z0-9_]*candour[a-z]*[A-Za-z0-9_]*
clamour	clamor	[A-Za-z0-9_]*clamour[a-z]*[A-Za-z0-9_]*
fervour	fervor	[A-Za-z0-9_]*fervour[a-z]*[A-Za-z0-9_]*
saviour	savior	[A-Za-z0-9_]*saviour[a-z]*[A-Za-z0-9_]*
arbour	arbor	[A-Za-z0-9_]*arbour[a-z]*[A-Za-z0-9_]*
tumour	tumor	[A-Za-z0-9_]*tumour[a-z]*[A-Za-z0-9_]*
ardour	ardor	[A-Za-z0-9_]*ardour[a-z]*[A-Za-z0-9_]*
succour	succor	[A-Za-z0-9_]*succour[a-z]*[A-Za-z0-9_]*
centre	center	[A-Za-z0-9_]*centre[a-z]*[A-Za-z0-9_]*
metre	meter	[A-Za-z0-9_]*metre[a-z]*[A-Za-z0-9_]*
litre	liter	[A-Za-z0-9_]*litre[a-z]*[A-Za-z0-9_]*
theatre	theater	[A-Za-z0-9_]*theatre[a-z]*[A-Za-z0-9_]*
fibre	fiber	[A-Za-z0-9_]*fibre[a-z]*[A-Za-z0-9_]*
calibre	caliber	[A-Za-z0-9_]*calibre[a-z]*[A-Za-z0-9_]*
sombre	somber	[A-Za-z0-9_]*sombre[a-z]*[A-Za-z0-9_]*
lustre	luster	[A-Za-z0-9_]*lustre[a-z]*[A-Za-z0-9_]*
spectre	specter	[A-Za-z0-9_]*spectre[a-z]*[A-Za-z0-9_]*
sceptre	scepter	[A-Za-z0-9_]*sceptre[a-z]*[A-Za-z0-9_]*
meagre	meager	[A-Za-z0-9_]*meagre[a-z]*[A-Za-z0-9_]*
manoeuvre	maneuver	[A-Za-z0-9_]*manoeuvr[a-z]*[A-Za-z0-9_]*
reconnoitre	reconnoiter	[A-Za-z0-9_]*reconnoitr[a-z]*[A-Za-z0-9_]*
organise	organize	[A-Za-z0-9_]*organis(e|es|ed|ing|er|ers|ation|ations|ational)[A-Za-z0-9_]*
realise	realize	[A-Za-z0-9_]*realis(e|es|ed|ing|er|ers|ation|able)[A-Za-z0-9_]*
recognise	recognize	[A-Za-z0-9_]*recognis(e|es|ed|ing|er|ers|ation|able)[A-Za-z0-9_]*
materialise	materialize	[A-Za-z0-9_]*materialis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
initialise	initialize	[A-Za-z0-9_]*initialis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
prioritise	prioritize	[A-Za-z0-9_]*prioritis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
optimise	optimize	[A-Za-z0-9_]*optimis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
minimise	minimize	[A-Za-z0-9_]*minimis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
maximise	maximize	[A-Za-z0-9_]*maximis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
analyse	analyze	[A-Za-z0-9_]*analys(e|ed|ing|er|ers)[A-Za-z0-9_]*
catalyse	catalyze	[A-Za-z0-9_]*catalys(e|ed|ing)[A-Za-z0-9_]*
paralyse	paralyze	[A-Za-z0-9_]*paralys(e|ed|ing)[A-Za-z0-9_]*
dialyse	dialyze	[A-Za-z0-9_]*dialys(e|ed|ing)[A-Za-z0-9_]*
specialise	specialize	[A-Za-z0-9_]*specialis(e|es|ed|ing|er|ers|ation|ations)[A-Za-z0-9_]*
normalise	normalize	[A-Za-z0-9_]*normalis(e|es|ed|ing|er|ers|ation|ations)[A-Za-z0-9_]*
serialise	serialize	[A-Za-z0-9_]*serialis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
deserialise	deserialize	[A-Za-z0-9_]*deserialis(e|es|ed|ing|er|ers|ation|ations|able)[A-Za-z0-9_]*
synchronise	synchronize	[A-Za-z0-9_]*synchronis(e|es|ed|ing|er|ers|ation|ations)[A-Za-z0-9_]*
apologise	apologize	[A-Za-z0-9_]*apologis(e|es|ed|ing)[A-Za-z0-9_]*
summarise	summarize	[A-Za-z0-9_]*summaris(e|es|ed|ing|er|ers|ation)[A-Za-z0-9_]*
utilise	utilize	[A-Za-z0-9_]*utilis(e|es|ed|ing|er|ers|ation|ations)[A-Za-z0-9_]*
emphasise	emphasize	[A-Za-z0-9_]*emphasis(e|ed|ing)[A-Za-z0-9_]*
criticise	criticize	[A-Za-z0-9_]*criticis(e|es|ed|ing|er|ers)[A-Za-z0-9_]*
authorise	authorize	[A-Za-z0-9_]*authoris(e|es|ed|ing|er|ers|ation|ations)[A-Za-z0-9_]*
visualise	visualize	[A-Za-z0-9_]*visualis(e|es|ed|ing|er|ers|ation|ations)[A-Za-z0-9_]*
finalise	finalize	[A-Za-z0-9_]*finalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
generalise	generalize	[A-Za-z0-9_]*generalis(e|es|ed|ing|ation|ations)[A-Za-z0-9_]*
centralise	centralize	[A-Za-z0-9_]*centralis(e|es|ed|ing|ation|ations)[A-Za-z0-9_]*
decentralise	decentralize	[A-Za-z0-9_]*decentralis(e|es|ed|ing|ation)[A-Za-z0-9_]*
stabilise	stabilize	[A-Za-z0-9_]*stabilis(e|es|ed|ing|er|ers|ation)[A-Za-z0-9_]*
standardise	standardize	[A-Za-z0-9_]*standardis(e|es|ed|ing|ation|ations)[A-Za-z0-9_]*
customise	customize	[A-Za-z0-9_]*customis(e|es|ed|ing|er|ers|ation|able)[A-Za-z0-9_]*
categorise	categorize	[A-Za-z0-9_]*categoris(e|es|ed|ing|ation|ations)[A-Za-z0-9_]*
characterise	characterize	[A-Za-z0-9_]*characteris(e|es|ed|ing|ation|ations)[A-Za-z0-9_]*
colonise	colonize	[A-Za-z0-9_]*colonis(e|es|ed|ing|er|ers|ation|ations)[A-Za-z0-9_]*
civilise	civilize	[A-Za-z0-9_]*civilis(e|es|ed|ing|ation|ations)[A-Za-z0-9_]*
memorise	memorize	[A-Za-z0-9_]*memoris(e|es|ed|ing|ation)[A-Za-z0-9_]*
penalise	penalize	[A-Za-z0-9_]*penalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
randomise	randomize	[A-Za-z0-9_]*randomis(e|es|ed|ing|er|ers|ation)[A-Za-z0-9_]*
rationalise	rationalize	[A-Za-z0-9_]*rationalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
socialise	socialize	[A-Za-z0-9_]*socialis(e|es|ed|ing|ation)[A-Za-z0-9_]*
symbolise	symbolize	[A-Za-z0-9_]*symbolis(e|es|ed|ing)[A-Za-z0-9_]*
sympathise	sympathize	[A-Za-z0-9_]*sympathis(e|es|ed|ing)[A-Za-z0-9_]*
theorise	theorize	[A-Za-z0-9_]*theoris(e|es|ed|ing)[A-Za-z0-9_]*
urbanise	urbanize	[A-Za-z0-9_]*urbanis(e|es|ed|ing|ation)[A-Za-z0-9_]*
vandalise	vandalize	[A-Za-z0-9_]*vandalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
capitalise	capitalize	[A-Za-z0-9_]*capitalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
equalise	equalize	[A-Za-z0-9_]*equalis(e|es|ed|ing|er|ers|ation)[A-Za-z0-9_]*
familiarise	familiarize	[A-Za-z0-9_]*familiaris(e|es|ed|ing|ation)[A-Za-z0-9_]*
formalise	formalize	[A-Za-z0-9_]*formalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
fossilise	fossilize	[A-Za-z0-9_]*fossilis(e|es|ed|ing|ation)[A-Za-z0-9_]*
harmonise	harmonize	[A-Za-z0-9_]*harmonis(e|es|ed|ing|ation)[A-Za-z0-9_]*
hospitalise	hospitalize	[A-Za-z0-9_]*hospitalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
idealise	idealize	[A-Za-z0-9_]*idealis(e|es|ed|ing|ation)[A-Za-z0-9_]*
immunise	immunize	[A-Za-z0-9_]*immunis(e|es|ed|ing|ation)[A-Za-z0-9_]*
industrialise	industrialize	[A-Za-z0-9_]*industrialis(e|es|ed|ing|ation)[A-Za-z0-9_]*
legalise	legalize	[A-Za-z0-9_]*legalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
localise	localize	[A-Za-z0-9_]*localis(e|es|ed|ing|ation|ations)[A-Za-z0-9_]*
modernise	modernize	[A-Za-z0-9_]*modernis(e|es|ed|ing|ation)[A-Za-z0-9_]*
nationalise	nationalize	[A-Za-z0-9_]*nationalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
naturalise	naturalize	[A-Za-z0-9_]*naturalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
neutralise	neutralize	[A-Za-z0-9_]*neutralis(e|es|ed|ing|ation)[A-Za-z0-9_]*
popularise	popularize	[A-Za-z0-9_]*popularis(e|es|ed|ing|ation)[A-Za-z0-9_]*
publicise	publicize	[A-Za-z0-9_]*publicis(e|es|ed|ing)[A-Za-z0-9_]*
sanitise	sanitize	[A-Za-z0-9_]*sanitis(e|es|ed|ing|er|ers|ation)[A-Za-z0-9_]*
scrutinise	scrutinize	[A-Za-z0-9_]*scrutinis(e|es|ed|ing)[A-Za-z0-9_]*
subsidise	subsidize	[A-Za-z0-9_]*subsidis(e|es|ed|ing)[A-Za-z0-9_]*
mobilise	mobilize	[A-Za-z0-9_]*mobilis(e|es|ed|ing|ation)[A-Za-z0-9_]*
monopolise	monopolize	[A-Za-z0-9_]*monopolis(e|es|ed|ing|ation)[A-Za-z0-9_]*
moralise	moralize	[A-Za-z0-9_]*moralis(e|es|ed|ing)[A-Za-z0-9_]*
hypothesise	hypothesize	[A-Za-z0-9_]*hypothesis(e|ed|ing)[A-Za-z0-9_]*
itemise	itemize	[A-Za-z0-9_]*itemis(e|es|ed|ing|ation)[A-Za-z0-9_]*
jeopardise	jeopardize	[A-Za-z0-9_]*jeopardis(e|es|ed|ing)[A-Za-z0-9_]*
marginalise	marginalize	[A-Za-z0-9_]*marginalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
mesmerise	mesmerize	[A-Za-z0-9_]*mesmeris(e|es|ed|ing)[A-Za-z0-9_]*
ostracise	ostracize	[A-Za-z0-9_]*ostracis(e|es|ed|ing)[A-Za-z0-9_]*
oxidise	oxidize	[A-Za-z0-9_]*oxidis(e|es|ed|ing|ation)[A-Za-z0-9_]*
patronise	patronize	[A-Za-z0-9_]*patronis(e|es|ed|ing)[A-Za-z0-9_]*
personalise	personalize	[A-Za-z0-9_]*personalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
pluralise	pluralize	[A-Za-z0-9_]*pluralis(e|es|ed|ing|ation)[A-Za-z0-9_]*
polarise	polarize	[A-Za-z0-9_]*polaris(e|es|ed|ing|ation)[A-Za-z0-9_]*
pressurise	pressurize	[A-Za-z0-9_]*pressuris(e|es|ed|ing|ation)[A-Za-z0-9_]*
privatise	privatize	[A-Za-z0-9_]*privatis(e|es|ed|ing|ation)[A-Za-z0-9_]*
professionalise	professionalize	[A-Za-z0-9_]*professionalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
pulverise	pulverize	[A-Za-z0-9_]*pulveris(e|es|ed|ing)[A-Za-z0-9_]*
quantise	quantize	[A-Za-z0-9_]*quantis(e|es|ed|ing|ation|er|ers)[A-Za-z0-9_]*
revitalise	revitalize	[A-Za-z0-9_]*revitalis(e|es|ed|ing|ation)[A-Za-z0-9_]*
revolutionise	revolutionize	[A-Za-z0-9_]*revolutionis(e|es|ed|ing)[A-Za-z0-9_]*
satirise	satirize	[A-Za-z0-9_]*satiris(e|es|ed|ing)[A-Za-z0-9_]*
scandalise	scandalize	[A-Za-z0-9_]*scandalis(e|es|ed|ing)[A-Za-z0-9_]*
sensitise	sensitize	[A-Za-z0-9_]*sensitis(e|es|ed|ing|ation)[A-Za-z0-9_]*
sterilise	sterilize	[A-Za-z0-9_]*sterilis(e|es|ed|ing|ation)[A-Za-z0-9_]*
stigmatise	stigmatize	[A-Za-z0-9_]*stigmatis(e|es|ed|ing)[A-Za-z0-9_]*
synthesise	synthesize	[A-Za-z0-9_]*synthesis(e|ed|ing)[A-Za-z0-9_]*
systematise	systematize	[A-Za-z0-9_]*systematis(e|es|ed|ing)[A-Za-z0-9_]*
tantalise	tantalize	[A-Za-z0-9_]*tantalis(e|es|ed|ing)[A-Za-z0-9_]*
tenderise	tenderize	[A-Za-z0-9_]*tenderis(e|es|ed|ing)[A-Za-z0-9_]*
terrorise	terrorize	[A-Za-z0-9_]*terroris(e|es|ed|ing)[A-Za-z0-9_]*
tokenise	tokenize	[A-Za-z0-9_]*tokenis(e|es|ed|ing|er|ers|ation)[A-Za-z0-9_]*
trivialise	trivialize	[A-Za-z0-9_]*trivialis(e|es|ed|ing)[A-Za-z0-9_]*
unionise	unionize	[A-Za-z0-9_]*unionis(e|es|ed|ing)[A-Za-z0-9_]*
vaporise	vaporize	[A-Za-z0-9_]*vaporis(e|es|ed|ing)[A-Za-z0-9_]*
verbalise	verbalize	[A-Za-z0-9_]*verbalis(e|es|ed|ing)[A-Za-z0-9_]*
victimise	victimize	[A-Za-z0-9_]*victimis(e|es|ed|ing)[A-Za-z0-9_]*
vocalise	vocalize	[A-Za-z0-9_]*vocalis(e|es|ed|ing)[A-Za-z0-9_]*
weaponise	weaponize	[A-Za-z0-9_]*weaponis(e|es|ed|ing)[A-Za-z0-9_]*
westernise	westernize	[A-Za-z0-9_]*westernis(e|es|ed|ing)[A-Za-z0-9_]*
amortise	amortize	[A-Za-z0-9_]*amortis(e|es|ed|ing|ation)[A-Za-z0-9_]*
dramatise	dramatize	[A-Za-z0-9_]*dramatis(e|es|ed|ing|ation)[A-Za-z0-9_]*
acclimatise	acclimatize	[A-Za-z0-9_]*acclimatis(e|es|ed|ing|ation)[A-Za-z0-9_]*
catalogue	catalog	[A-Za-z0-9_]*catalogu(e|es|ed|ing|er|ers)[A-Za-z0-9_]*
dialogue	dialog	[A-Za-z0-9_]*dialogu(e|es|ed|ing)[A-Za-z0-9_]*
analogue	analog	[A-Za-z0-9_]*analogu(e|es)[A-Za-z0-9_]*
monologue	monolog	[A-Za-z0-9_]*monologu(e|es)[A-Za-z0-9_]*
epilogue	epilog	[A-Za-z0-9_]*epilogu(e|es)[A-Za-z0-9_]*
prologue	prolog	[A-Za-z0-9_]*prologu(e|es)[A-Za-z0-9_]*
travelogue	travelog	[A-Za-z0-9_]*travelogu(e|es)[A-Za-z0-9_]*
homologue	homolog	[A-Za-z0-9_]*homologu(e|es)[A-Za-z0-9_]*
defence	defense	[A-Za-z0-9_]*defence[a-z]*[A-Za-z0-9_]*
offence	offense	[A-Za-z0-9_]*offence[a-z]*[A-Za-z0-9_]*
licence	license	[A-Za-z0-9_]*licence[a-z]*[A-Za-z0-9_]*
pretence	pretense	[A-Za-z0-9_]*pretence[a-z]*[A-Za-z0-9_]*
travelled	traveled	[A-Za-z0-9_]*travell(ed|ing|er|ers)[A-Za-z0-9_]*
modelled	modeled	[A-Za-z0-9_]*modell(ed|ing|er|ers)[A-Za-z0-9_]*
cancelled	canceled	[A-Za-z0-9_]*cancell(ed|ing|er|ers)[A-Za-z0-9_]*
labelled	labeled	[A-Za-z0-9_]*labell(ed|ing|er|ers)[A-Za-z0-9_]*
levelled	leveled	[A-Za-z0-9_]*levell(ed|ing|er|ers)[A-Za-z0-9_]*
signalled	signaled	[A-Za-z0-9_]*signall(ed|ing|er|ers)[A-Za-z0-9_]*
fuelled	fueled	[A-Za-z0-9_]*fuell(ed|ing|er|ers)[A-Za-z0-9_]*
totalled	totaled	[A-Za-z0-9_]*totall(ed|ing)[A-Za-z0-9_]*
equalled	equaled	[A-Za-z0-9_]*equall(ed|ing)[A-Za-z0-9_]*
channelled	channeled	[A-Za-z0-9_]*channell(ed|ing)[A-Za-z0-9_]*
funnelled	funneled	[A-Za-z0-9_]*funnell(ed|ing)[A-Za-z0-9_]*
panelled	paneled	[A-Za-z0-9_]*panell(ed|ing)[A-Za-z0-9_]*
parcelled	parceled	[A-Za-z0-9_]*parcell(ed|ing)[A-Za-z0-9_]*
pencilled	penciled	[A-Za-z0-9_]*pencill(ed|ing)[A-Za-z0-9_]*
rivalled	rivaled	[A-Za-z0-9_]*rivall(ed|ing)[A-Za-z0-9_]*
shovelled	shoveled	[A-Za-z0-9_]*shovell(ed|ing)[A-Za-z0-9_]*
spiralled	spiraled	[A-Za-z0-9_]*spirall(ed|ing)[A-Za-z0-9_]*
tunnelled	tunneled	[A-Za-z0-9_]*tunnell(ed|ing)[A-Za-z0-9_]*
unravelled	unraveled	[A-Za-z0-9_]*unravell(ed|ing)[A-Za-z0-9_]*
dialled	dialed	[A-Za-z0-9_]*diall(ed|ing)[A-Za-z0-9_]*
marshalled	marshaled	[A-Za-z0-9_]*marshall(ed|ing)[A-Za-z0-9_]*
quarrelled	quarreled	[A-Za-z0-9_]*quarrell(ed|ing)[A-Za-z0-9_]*
swivelled	swiveled	[A-Za-z0-9_]*swivell(ed|ing)[A-Za-z0-9_]*
refuelled	refueled	[A-Za-z0-9_]*refuell(ed|ing)[A-Za-z0-9_]*
marvellous	marvelous	[A-Za-z0-9_]*marvellous[A-Za-z0-9_]*
jewellery	jewelry	[A-Za-z0-9_]*jewellery[A-Za-z0-9_]*
counsellor	counselor	[A-Za-z0-9_]*counsell(or|ors|ed|ing)[A-Za-z0-9_]*
councillor	councilor	[A-Za-z0-9_]*councill(or|ors)[A-Za-z0-9_]*
focussed	focused	[A-Za-z0-9_]*focuss(ed|ing|es)[A-Za-z0-9_]*
worshipped	worshiped	[A-Za-z0-9_]*worshipp(ed|ing|er|ers)[A-Za-z0-9_]*
grey	gray	[A-Za-z0-9_]*grey[a-z]*[A-Za-z0-9_]*
kerb	curb	[A-Za-z0-9_]*kerb[a-z]*[A-Za-z0-9_]*
mould	mold	[A-Za-z0-9_]*mould[a-z]*[A-Za-z0-9_]*
smoulder	smolder	[A-Za-z0-9_]*smoulder[a-z]*[A-Za-z0-9_]*
moult	molt	[A-Za-z0-9_]*moult[a-z]*[A-Za-z0-9_]*
plough	plow	[A-Za-z0-9_]*plough[a-z]*[A-Za-z0-9_]*
sceptic	skeptic	[A-Za-z0-9_]*sceptic[a-z]*[A-Za-z0-9_]*
whilst	while	[A-Za-z0-9_]*whilst[A-Za-z0-9_]*
amongst	among	[A-Za-z0-9_]*amongst[A-Za-z0-9_]*
artefact	artifact	[A-Za-z0-9_]*artefact[a-z]*[A-Za-z0-9_]*
aluminium	aluminum	[A-Za-z0-9_]*aluminium[A-Za-z0-9_]*
judgement	judgment	[A-Za-z0-9_]*judgement[a-z]*[A-Za-z0-9_]*
acknowledgement	acknowledgment	[A-Za-z0-9_]*acknowledgement[a-z]*[A-Za-z0-9_]*
cheque	check	[A-Za-z0-9_]*cheque[a-z]*[A-Za-z0-9_]*
draught	draft	[A-Za-z0-9_]*draught[a-z]*[A-Za-z0-9_]*
gaol	jail	[A-Za-z0-9_]*gaol(s|er|ers|ed|ing)?[A-Za-z0-9_]*
pyjamas	pajamas	[A-Za-z0-9_]*pyjama[a-z]*[A-Za-z0-9_]*
sulphur	sulfur	[A-Za-z0-9_]*sulph[a-z]*[A-Za-z0-9_]*
tonne	ton	[A-Za-z0-9_]*tonne(s)?[A-Za-z0-9_]*
storey	story	[A-Za-z0-9_]*store(y|ys|yed)[A-Za-z0-9_]*
tyre	tire	[A-Za-z0-9_]*tyre(s)?[A-Za-z0-9_]*
fulfil	fulfill	[A-Za-z0-9_]*fulfil(?!l)(s|ment|ments)?[A-Za-z0-9_]*
enrol	enroll	[A-Za-z0-9_]*enrol(?!l)(s|ment|ments)?[A-Za-z0-9_]*
instil	instill	[A-Za-z0-9_]*instil(?!l)(s|ment|ments)?[A-Za-z0-9_]*
distil	distill	[A-Za-z0-9_]*distil(?!l)(s|ment|lery)?[A-Za-z0-9_]*
appal	appall	[A-Za-z0-9_]*appal(?!l)(s)?[A-Za-z0-9_]*
enthral	enthrall	[A-Za-z0-9_]*enthral(?!l)(s|ment)?[A-Za-z0-9_]*
skilful	skillful	[A-Za-z0-9_]*skilful[a-z]*[A-Za-z0-9_]*
wilful	willful	[A-Za-z0-9_]*wilful[a-z]*[A-Za-z0-9_]*
instalment	installment	[A-Za-z0-9_]*instalment(s)?[A-Za-z0-9_]*
practise	practice	[A-Za-z0-9_]*practis(e|es|ed|ing)[A-Za-z0-9_]*
programme	program	[A-Za-z0-9_]*programme(s|d|r|rs)?[A-Za-z0-9_]*
aeroplane	airplane	[A-Za-z0-9_]*aeroplane(s)?[A-Za-z0-9_]*
cosy	cozy	[A-Za-z0-9_]*cos(y|ily|iness)[A-Za-z0-9_]*
doughnut	donut	[A-Za-z0-9_]*doughnut(s)?[A-Za-z0-9_]*
pavement	sidewalk	[A-Za-z0-9_]*pavement(s)?[A-Za-z0-9_]*
carriageway	roadway	[A-Za-z0-9_]*carriageway(s)?[A-Za-z0-9_]*
tarmac	asphalt	[A-Za-z0-9_]*tarmac[a-z]*[A-Za-z0-9_]*
speciality	specialty	[A-Za-z0-9_]*specialit(y|ies)[A-Za-z0-9_]*
orientated	oriented	[A-Za-z0-9_]*orientat(ed|ing)[A-Za-z0-9_]*
ageing	aging	[A-Za-z0-9_]*ageing[A-Za-z0-9_]*
encyclopaedia	encyclopedia	[A-Za-z0-9_]*encyclopaedi[a-z]*[A-Za-z0-9_]*
mediaeval	medieval	[A-Za-z0-9_]*mediaeval[A-Za-z0-9_]*
foetus	fetus	[A-Za-z0-9_]*foetus[a-z]*[A-Za-z0-9_]*
haemo	hemo	[A-Za-z0-9_]*haemo[a-z]*[A-Za-z0-9_]*
paediatric	pediatric	[A-Za-z0-9_]*paediatric[a-z]*[A-Za-z0-9_]*
oesophagus	esophagus	[A-Za-z0-9_]*oesophag[a-z]*[A-Za-z0-9_]*
aeon	eon	[A-Za-z0-9_]*aeon(s)?[A-Za-z0-9_]*
annexe	annex	[A-Za-z0-9_]*annexe(s)?[A-Za-z0-9_]*
dependant	dependent	[A-Za-z0-9_]*dependant(s)?[A-Za-z0-9_]*
despatch	dispatch	[A-Za-z0-9_]*despatch[a-z]*[A-Za-z0-9_]*
connexion	connection	[A-Za-z0-9_]*connexion(s)?[A-Za-z0-9_]*
cypher	cipher	[A-Za-z0-9_]*cypher[a-z]*[A-Za-z0-9_]*
moustache	mustache	[A-Za-z0-9_]*moustache(s)?[A-Za-z0-9_]*
yoghurt	yogurt	[A-Za-z0-9_]*yoghurt(s)?[A-Za-z0-9_]*
whisky	whiskey	[A-Za-z0-9_]*whisky[A-Za-z0-9_]*
chilli	chili	[A-Za-z0-9_]*chilli(es)?[A-Za-z0-9_]*
racoon	raccoon	[A-Za-z0-9_]*racoon(s)?[A-Za-z0-9_]*
verandah	veranda	[A-Za-z0-9_]*verandah(s)?[A-Za-z0-9_]*
titbit	tidbit	[A-Za-z0-9_]*titbit(s)?[A-Za-z0-9_]*
learnt	learned	[A-Za-z0-9_]*learnt[A-Za-z0-9_]*
spelt	spelled	[A-Za-z0-9_]*spelt[A-Za-z0-9_]*
dreamt	dreamed	[A-Za-z0-9_]*dreamt[A-Za-z0-9_]*
leapt	leaped	[A-Za-z0-9_]*leapt[A-Za-z0-9_]*
spilt	spilled	[A-Za-z0-9_]*spilt[A-Za-z0-9_]*
burnt	burned	[A-Za-z0-9_]*burnt[A-Za-z0-9_]*
anticlockwise	counterclockwise	[A-Za-z0-9_]*anticlockwise[A-Za-z0-9_]*
maths	math	[A-Za-z0-9_]*maths(?![a-z])[A-Za-z0-9_]*
mum	mom	\bmum(?![a-z])
economise	economize	[A-Za-z0-9_]*economis(e|es|ed|ing|ation)[A-Za-z0-9_]*
metalled	paved	[A-Za-z0-9_]*metall(ed|ing)[A-Za-z0-9_]*
```

Classification into A/B/C was done with a byte-offset pass over each file that marks comment
spans, string-literal spans and code spans, so a match can be attributed to exactly one of them:

- **A** — the offset falls inside a `//`, `/* */`, `#` or `<!-- -->` comment, or the file is
  Markdown or plain text and the match is not inside a backtick span. Comment hits whose line
  also carries `{@link}`, `{@code}`, `{@value}`, a backtick span or a `#method(` reference are
  flagged separately (43 of them) because the prose there may be naming an identifier and has to
  move with it. That flag is per-line, not per-token, so it over-reports slightly — read each one.
- **B** — the offset falls inside a string or char literal whose contents contain whitespace and
  do not look like an id (`KEYISH` = `^[a-z0-9]+([._:/-][a-z0-9]+)+$`, `SCREAMY` = `^[A-Z0-9_]+$`,
  or a single token with no spaces).
- **C** — everything else: bare code identifiers, string literals that pass the id test, JSON
  string values immediately followed by `:` (keys), other JSON string values that pass the id
  test, backtick spans in Markdown, and CSS custom properties.

Nine discovery sweeps were also run, to catch British forms the hand-written list missed. They
returned `economising` and `metalled`, both since added to the table above; the last two returned
nothing at all:

```sh
git grep -h -I -i -o -P "[a-z]{3,}isation" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}is(e|ed|es|ing)(?![a-z])" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}our(?![a-z])" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}(tre|bre|gre|cre)(?![a-z])" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}ogue(?![a-z])" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}ence(?![a-z])" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}(lled|lling|llor)(?![a-z])" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}yse(?![a-z])" -- ':!*.png' ':!*.jar' ':!*.hf'
git grep -h -I -i -o -P "[a-z]{3,}aeo|[a-z]{3,}oeu" -- ':!*.png' ':!*.jar' ':!*.hf'
```

Pipe each through `tr 'A-Z' 'a-z' | sort | uniq -c | sort -rn` and read the list; the
dialect-neutral words (`difference`, `sequence`, `otherwise`, `dwelling`, `stalled`) dominate and
the British ones stand out.

## Suggested order for the conversion pass

1. **Prose only (730 hits, 150 files, almost no risk).** Comments, javadoc, markdown. Nothing
   compiles differently. Do this first and separately so the identifier diff is readable. The
   one caveat: **43 of those 730 sit on a line that also carries a `{@link}`, `{@code}`,
   `#method` or backtick reference to a real identifier**, and some of them are that reference —
   e.g. `{@code Settlement.againstTheKerb}` at
   `common/src/main/java/com/kingdoms/sim/culture/Layout.java:248`, or `` `BuildCatalogue` `` in
   `BUILD_DECISIONS.md`. Those must stay spelled the way the identifier is spelled, so leave them
   until step 3 or 4 renames the thing they point at.
2. **Free-text strings (41).** Six need a human read (the player-facing four and the two tool UI
   strings); the other 35 are test and log messages.
3. **Single-module identifiers (the 62 non-cross-module entries in the C table).** Test-method
   names first — 30 of them, one file each.
4. **The eleven cross-module renames, one commit each,** starting with the small ones
   (`withCentre`, `againstTheKerb`, `ARMOUR`, `laboursAs`) and ending with `centre`.
   `BuildCatalogue` carries a file rename; `catalogue`/`setCatalogue` are the same refactor and
   should land together.
5. **Never:** the four save keys, `BlockBehaviour`, the survey data contract, and `LICENSE`.

## Converted

Done in two commits on `worktree-agent-a913b94b406e6d632`, on top of `d22db69`:
prose and free-text strings first, then identifiers, keys and file renames. The
suite was 1072 tests, 0 failures, before each and after each.

**2042 hits converted, 45 forms, 212 files.** The audit counted 2055 at `b688247`;
the tree grew before the conversion ran, so the sweep found its own 2041, and the
`TownStores.ARMOUR` declaration was renamed by hand because its line is protected
whole. By category: **A prose 683**, **B free-text strings 43**, **C identifiers and
keys 1316**. The ten that carry it: `centre` 1148, `catalogue` 342, `neighbour` 114,
`labour` 62, `kerb` 57, `programme` 50, `levelled` 48, `armour` 32, `colour` 26,
`recognise` 24.

Three of those counts are higher than the audit's, because three of its patterns are
narrower than the words are. `centre[a-z]*` does not match `centred` (14) or `centring`
(3); `levell(ed|ing|er|ers)` does not match `levellable` (11 across `isSiteLevellable`
and `anyLevellable`). All were found by reading the token inventory rather than the
pattern list, and all are converted.

Three files renamed with `git mv`, each carrying its public type:

- `BuildCatalogue.java` → `BuildCatalog.java`
- `KerbTest.java` → `CurbTest.java`
- `LevellingTest.java` → `LevelingTest.java`

The survey/viewer data contract moved as one piece, because every side of it is a
file in this repository: `tools/survey.py` writes `center` and `defense`,
`tools/townview.html` reads them and declares `--defense`, and the four
`surveys/*_town.json` fixtures were rewritten to match. `survey.py`'s own docstring
licenses this — "the shape is allowed to change as long as both ends move together" —
and nothing but the viewer reads those files. A survey JSON captured before this will
not draw; re-run `survey.py` against the log.

### What is left, and why

Re-running every pattern in the appendix over the converted tree — excluding this
document, as the audit has always excluded itself — returns **83 hits across 14
forms**. Thirteen of those are the CHANGELOG entry for this change, which has to
name `centre`, `BuildCatalogue`, `KERB`, `laboursAs`, `carriageway`, `tarmac`,
`metalled` and `"armour"` in order to say what moved and what did not. Subtract
them and the residue proper is **70 hits across 10 forms**, every one deliberate:

(Counting this document too returns 796, because its appendix names all 265
British forms on purpose. That is why the sweep skips it, and why its body was
left in the old spelling: an audit that spells its own worklist American stops
being able to find anything.)

| Form | Left | Why |
| --- | ---: | --- |
| `carriageway` | 46 | Vocabulary, not orthography. The project means the word. |
| `tyre` | 5 | False positive: `EntityRenderersEvent`, `EntityRendererProvider`, `registerEntityRenderer` — vanilla names, "Enti**tyRe**ndere…". |
| `behaviour` | 4 | `BlockBehaviour` — `net.minecraft.world.level.block.state.BlockBehaviour`. Mojang's name, not ours. |
| `programme` | 4 | `programmer` in `LICENSE:664` (verbatim GPL text) and `programmed` ×3 in `Settlement.java:2200-2202`, which is already American — the audit's `programme(s\|d\|r\|rs)?` pattern over-caught it. |
| `armour` | 3 | The frozen save value in `TownStores.java`, the comment in `Market.java:189` that quotes it, and the `{@code armour}` in `MarketTest.java:420` that names it. All three describe the key, so all three keep its spelling. |
| `centre` | 3 | The three frozen save keys: `fieldOf("centre")` ×2 in `KingdomsCodecs.java` and `optionalFieldOf("centre")` in `SiteLedger.java`. |
| `tarmac` | 2 | Vocabulary. |
| `offence` | 1 | False positive: `aPostIsTwoCoursesO**fFence**OnItsFooting`. |
| `cosy` | 1 | False positive: `e**cosy**stem`. |
| `metalled` | 1 | Vocabulary. |

`timber` was checked and is not a British spelling at all — it is the same word in
both dialects and appears nowhere in the pattern list.

The four save keys stayed exactly as written, and each now carries a one-line comment
beside it saying it is a save key spelled as first written and that changing it is a
codec migration rather than a spelling. **The Java names around them all moved**:
`Settlement.centre()` is `center()`, `WorkArea::centre` is `WorkArea::center`,
`SiteLedger.Entry::centre` is `Entry::center`, and `TownStores.ARMOUR` is
`TownStores.ARMOR` with its value still `"armour"`.

One player-visible string still reads British and is meant to: the stores panel shows
**Armour**, because `Tallies.pretty` capitalizes the store key rather than holding a
word of its own. It will read `Armor` the day the key migrates, and not before.
