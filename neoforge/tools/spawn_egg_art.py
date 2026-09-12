#!/usr/bin/env python3
"""Draws the two settler spawn eggs, and the model JSON with them.

WHY THIS EXISTS AT ALL. A spawn egg used to need no art: the item model was
``minecraft:item/template_spawn_egg``, two grey layers that the game tinted from
a pair of colors carried on the item. That is gone. In 26.2 every vanilla spawn
egg is a hand-painted 16x16 of its own -- ``assets/minecraft/items/
creeper_spawn_egg.json`` points at a plain ``item/generated`` model over
``item/creeper_spawn_egg.png``, with no tint anywhere and no template left to
parent to (checked against the 26.2 client jar; there is no
``template_spawn_egg`` in it). So a modded egg has to be drawn, and these are
drawn: the vanilla egg silhouette, read pixel for pixel out of
``creeper_spawn_egg.png``, filled in two colors per race.

Same rules as the other scripts in this folder: no third-party libraries, PNG
written straight out with ``zlib`` and ``struct``, and the committed output is
the point -- the build does not run this. The PNG writer is copied rather than
shared so that every tool here runs on its own with nothing but a python.

Usage, from the repository root:

    python neoforge/tools/spawn_egg_art.py
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "civilization")
TEXTURES = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")

# The egg, exactly the silhouette vanilla's eggs use: fourteen rows, widest in
# the middle, a flat top and a flat bottom four pixels across.
EGG = [
    "................",
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "...##########...",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "...##########...",
    "....########....",
    ".....######.....",
    "................",
]

# Where the light falls, and where it does not. Both are clipped to the inside
# of the egg, so they can be drawn generously and still not spill over the rim.
SHEEN = [
    "................",
    "................",
    "......##........",
    ".....##.........",
    "....##..........",
    "....#...........",
    "...#............",
    "...#............",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

SHADE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "............#...",
    "...........##...",
    "...........##...",
    "..........##....",
    ".........##.....",
    ".......###......",
    "................",
    "................",
]

# Five blobs, the same five on both eggs. A spawn egg is read as "base color
# plus spots" and always has been; only the two colors change.
SPOTS = [
    "................",
    "................",
    "................",
    ".....##.........",
    ".....##.........",
    "................",
    ".........##.....",
    ".........##.....",
    "....##..........",
    "....##..........",
    "..........##....",
    "..........##....",
    "......##........",
    "......##........",
    "................",
    "................",
]

# Two colors per egg, as the old tinted template had: the shell and the spots.
# A brown egg with a teal flash reads as the mod's ordinary settler (Steve's
# skin and Steve's shirt); a green one with bone spots reads as an orc.
EGGS = {
    "human_spawn_egg": {
        ".": (0, 0, 0, 0),
        "o": (99, 61, 44, 255),       # rim
        "B": (170, 114, 89, 255),     # shell
        "H": (198, 148, 120, 255),    # sheen
        "d": (134, 84, 63, 255),      # shade
        "s": (0, 164, 164, 255),      # spots
    },
    "orc_spawn_egg": {
        ".": (0, 0, 0, 0),
        "o": (44, 58, 40, 255),       # rim
        "B": (98, 122, 86, 255),      # shell
        "H": (132, 158, 114, 255),    # sheen
        "d": (74, 92, 66, 255),       # shade
        "s": (214, 206, 182, 255),    # spots
    },
}

MODEL = """{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "civilization:item/%s"
  }
}
"""

ITEM_DEFINITION = """{
  "model": {
    "type": "minecraft:model",
    "model": "civilization:item/%s"
  }
}
"""


def shell():
    """The egg as letters: rim where it meets nothing, then sheen, shade, spots."""
    size = len(EGG)
    inside = [[EGG[y][x] == "#" for x in range(size)] for y in range(size)]

    def solid(x, y):
        return 0 <= x < size and 0 <= y < size and inside[y][x]

    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            if not inside[y][x]:
                row.append(".")
            elif not (solid(x - 1, y) and solid(x + 1, y)
                      and solid(x, y - 1) and solid(x, y + 1)):
                row.append("o")            # the rim is whatever touches the outside
            elif SPOTS[y][x] == "#":
                row.append("s")
            elif SHEEN[y][x] == "#":
                row.append("H")
            elif SHADE[y][x] == "#":
                row.append("d")
            else:
                row.append("B")
        rows.append("".join(row))
    return rows


def png(path, rows, palette):
    """Writes one RGBA PNG of whatever size the grid is, no libraries."""
    raw = bytearray()
    for row in rows:
        raw.append(0)                       # filter: none
        for cell in row:
            raw.extend(palette[cell])
    header = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
    parts = [b"\x89PNG\r\n\x1a\n"]
    for tag, body in ((b"IHDR", header),
                      (b"IDAT", zlib.compress(bytes(raw), 9)),
                      (b"IEND", b"")):
        parts.append(struct.pack(">I", len(body)))
        parts.append(tag + body)
        parts.append(struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))
    with open(path, "wb") as out:
        out.write(b"".join(parts))


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


def main():
    for grid, name in ((EGG, "EGG"), (SHEEN, "SHEEN"),
                       (SHADE, "SHADE"), (SPOTS, "SPOTS")):
        if len(grid) != 16 or any(len(row) != 16 for row in grid):
            raise SystemExit("%s is not 16x16" % name)
    for folder in (TEXTURES, MODELS, ITEMS):
        os.makedirs(folder, exist_ok=True)
    rows = shell()
    for name, palette in sorted(EGGS.items()):
        png(os.path.join(TEXTURES, name + ".png"), rows, palette)
        write(os.path.join(MODELS, name + ".json"), MODEL % name)
        write(os.path.join(ITEMS, name + ".json"), ITEM_DEFINITION % name)
    print("wrote %d eggs: texture, model and item definition each" % len(EGGS))


if __name__ == "__main__":
    main()
