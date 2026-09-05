"""Turns a server log containing `/civ plan` output into a survey file.

    python tools/survey.py neoforge/run/logs/latest.log surveys/

One JSON per settlement found in the log. The town viewer
(``tools/townview.html``) reads these; nothing else does, so the shape is
allowed to change as long as both ends move together.

Why a file and not a picture: a picture answers one question and a survey
answers whatever you ask it later. Two runs of the same seed under different
layouts are only comparable if both were written down the same way.
"""
import json
import pathlib
import re
import sys

TOWN = re.compile(r"PLAN TOWN (\S+) (-?\d+) (-?\d+) (-?\d+) (\S+) (\d+) (\S+) (\S+)")
BUILD = re.compile(r"PLAN B (\S+) (-?\d+) (-?\d+) (-?\d+) (\d+) (\d+) (\S+)")
ROAD = re.compile(r"PLAN R (-?\d+) (-?\d+) (-?\d+) (-?\d+)")
WALL = re.compile(r"PLAN W (\d+) (\d+) (.*)")
GATE = re.compile(r"PLAN G (.*)")
GROUND = re.compile(r"PLAN H (-?\d+) ([\d,\-]+) ([.~?:]+)")

# What each blueprint is FOR, which is the only grouping worth coloring by.
# A town where the dwellings cluster and the industry rings the edge is a
# different town from one where they are shuffled, and no count shows that.
GROUPS = {
    "dwelling": ["house", "cottage", "bunkhouse", "inn", "longhouse", "hut"],
    "food": ["farm", "animal_farm", "granary", "mill", "hearth", "fishery"],
    "industry": ["workshop", "smith", "carpentry", "lumber_camp", "mine", "kiln"],
    "trade": ["storehouse", "warehouse", "cache", "market", "stall"],
    "civic": ["town_hall", "camp_post", "well", "shrine"],
    "defense": ["watchtower", "barracks", "guardhouse"],
}
KIND_GROUP = {k: g for g, ks in GROUPS.items() for k in ks}


def parse(text):
    """Every settlement the log holds a plan for, in the order they appear."""
    towns = []
    current = None
    for line in text.splitlines():
        m = TOWN.search(line)
        if m:
            name, cx, cy, cz, stage, pop, culture, layout = m.groups()
            current = {
                "name": name.replace("_", " "),
                "center": [int(cx), int(cy), int(cz)],
                "stage": stage, "pop": int(pop),
                "culture": culture, "layout": layout,
                "builds": [], "roads": [], "verts": [], "gates": [],
                "laid": 0, "ringlen": 0, "zs": [], "grid": [], "wet": [],
                "step": 4, "groups": {},
            }
            towns.append(current)
            continue
        if current is None:
            continue

        m = BUILD.search(line)
        if m:
            kind = m.group(1).split(":")[-1]
            current["builds"].append([kind, int(m.group(2)), int(m.group(3)),
                                      int(m.group(4)), int(m.group(5)),
                                      int(m.group(6)), m.group(7)])
            current["groups"][kind] = KIND_GROUP.get(kind, "civic")
            continue

        m = ROAD.search(line)
        if m:
            current["roads"].append([int(v) for v in m.groups()])
            continue

        m = WALL.search(line)
        if m:
            current["laid"] = int(m.group(1))
            current["ringlen"] = int(m.group(2))
            current["verts"] = [[int(a) for a in v.split(",")]
                                for v in m.group(3).split() if "," in v]
            continue

        m = GATE.search(line)
        if m and m.group(1).strip():
            current["gates"] = [[int(a) for a in v.split(",")]
                                for v in m.group(1).split() if "," in v]
            continue

        m = GROUND.search(line)
        if m:
            current["zs"].append(int(m.group(1)))
            current["grid"].append([None if h == "-" else int(h)
                                    for h in m.group(2).strip(",").split(",")])
            current["wet"].append(m.group(3))
    return towns


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    log = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "surveys")
    tag = sys.argv[3] if len(sys.argv) > 3 else None

    towns = parse(log.read_text(encoding="utf-8", errors="replace"))
    if not towns:
        print("no /civ plan output in", log)
        return 1
    out.mkdir(parents=True, exist_ok=True)
    for town in towns:
        # A survey is only worth keeping if it can be told apart from the next
        # one. Name it for what varies between runs, not for the town.
        label = tag or town["layout"]
        town["label"] = label
        name = f"{label}-{town['name'].replace(' ', '_').lower()}.json"
        path = out / name
        path.write_text(json.dumps(town, separators=(",", ":")), encoding="utf-8")
        wet = sum(row.count("~") for row in town["wet"])
        cells = sum(len(row) for row in town["wet"]) or 1
        print(f"{path}  {town['pop']} people, {len(town['builds'])} buildings, "
              f"{len(town['roads'])} road runs, ring {town['laid']}/{town['ringlen']}, "
              f"{100 * wet // cells}% water")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
