#!/usr/bin/env python3
"""Draws the placeholder item art for the orc armory, and the model JSON with it.

These are PLACEHOLDERS. Every PNG this writes is a 16x16 silhouette laid out by
hand in the grids below -- recognizable at a glance in a hotbar and nothing more.
They exist so the items are finished and playable without waiting on art, and
they are meant to be replaced file for file: drop a real 16x16 over the PNG of
the same name and nothing else in the mod has to change. A Blockbench export
lands in exactly the same place.

Two tiers per weapon. The crude one is dark pitted iron with a bone edge and a
red rag round the grip; the forged one is the same silhouette in bright steel
with a paler edge -- close enough to read as the same weapon, different enough
that a player can tell across a courtyard which of his guards the smithy has
got to.

No third-party libraries: PNG is written straight out with zlib and struct, so
this runs anywhere python does and will not rot when Pillow changes its API.

Usage, from the repository root:

    python neoforge/tools/orc_weapon_art.py

It rewrites every texture and every model JSON from scratch. Committing the
output is the point -- the build does not run this.
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kingdoms")
TEXTURES = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")

# The palette, by the letter used in the grids below.
#
# One palette for both tiers, differing only in the three metal ramps: a crude
# weapon is pitted grey-brown, a forged one is blued steel. Everything a hand
# touches -- haft, wrap, pommel -- is the same either way, because the smith
# reforged the head and not the handle.
SHARED = {
    ".": (0, 0, 0, 0),            # nothing
    "o": (26, 20, 16, 255),       # outline, near-black
    "w": (60, 46, 38, 255),       # wood haft, dark
    "W": (92, 72, 56, 255),       # wood haft, lit
    "r": (124, 30, 28, 255),      # red rag, shadow
    "R": (176, 46, 40, 255),      # red rag, lit
    "b": (214, 206, 182, 255),    # bone, white edge
    "y": (150, 118, 46, 255),     # brass pin
}

CRUDE_METAL = {
    "m": (62, 58, 54, 255),       # pitted iron, shadow
    "M": (104, 100, 94, 255),     # pitted iron, body
    "h": (146, 142, 134, 255),    # pitted iron, highlight
}

FORGED_METAL = {
    "m": (74, 82, 96, 255),       # blued steel, shadow
    "M": (136, 148, 166, 255),    # blued steel, body
    "h": (206, 216, 230, 255),    # blued steel, highlight
}

# Each grid is 16 rows of 16 characters, read top row first -- which is the top
# of the icon, the way an item renders. Minecraft hangs an item from the top
# left and points it up and to the right, so every blade below runs from a grip
# at the bottom left to a point at the top right.

GRIDS = {
    # A slab of a blade, two hands of haft, and a crossguard wider than the
    # blade is. Nothing subtle about it.
    "orc_greatsword": [
        "..............b.",
        ".............bh.",
        "............bhM.",
        "...........bhMm.",
        "..........bhMm..",
        ".........bhMm...",
        "........bhMm....",
        ".......bhMm.....",
        "......bhMm......",
        ".....bhMm.......",
        "....bhMm........",
        "...oMMMMo.......",
        "..oyRrRyo.......",
        "...oWwo.........",
        "...oWwo.........",
        "....oo..........",
    ],
    # Short, curved, wide at the tip. A hacking blade for a crowded line.
    "orc_falchion": [
        "..........bbb...",
        ".........bhMMb..",
        "........bhMMMh..",
        ".......bhMMMh...",
        "......bhMMMh....",
        ".....bhMMMh.....",
        "....bhMMMh......",
        "...bhMMMh.......",
        "...bMMMh........",
        "..oMMMo.........",
        "..oRrRo.........",
        "..oWwo..........",
        "..oWwo..........",
        "..oWwo..........",
        "...oyo..........",
        "...oo...........",
    ],
    # A butcher's tool: rectangular, heavy at the far corner, a hole in the
    # back edge to hang it up by.
    "orc_cleaver": [
        "................",
        "......oooooo....",
        ".....obbbbbho...",
        ".....ohMMMMho...",
        ".....ohMoMMho...",
        ".....ohMoMMho...",
        ".....ohMMMMho...",
        ".....ohMMMMho...",
        ".....obhMMMho...",
        "....oMMMMMMo....",
        "....oRrRRro.....",
        "...oWwWo........",
        "...oWwWo........",
        "...oWwWo........",
        "....oyo.........",
        "....oo..........",
    ],
    # A felling axe swung at people: one bearded blade, a long haft, an iron
    # butt cap so it can be used the other way round.
    "orc_axe": [
        "........oooo....",
        ".......obbbho...",
        "......obhMMho...",
        ".....obhMMMho...",
        "....oMhMMMMho...",
        "....oMMMMMMo....",
        "....obhMMMo.....",
        ".....obhMo......",
        "....oRrRo.......",
        "....oWwo........",
        "...oWwo.........",
        "...oWwo.........",
        "..oWwo..........",
        "..oWwo..........",
        "..oyMo..........",
        "..oo............",
    ],
    # A ball of spikes on a haft. Slowest thing in the camp and the one that
    # goes through a helmet.
    "orc_morningstar": [
        ".......o.o.o....",
        "......ohMhMho...",
        ".....oMMMMMMo...",
        "....o.MhMMhM.o..",
        "....oMMMMMMMMo..",
        "....oMhMMMMhMo..",
        "....oMMMMMMMMo..",
        "....o.MMhMMM.o..",
        ".....oMMMMMo....",
        "......o.o.o.....",
        ".....oRrRo......",
        "....oWwWo.......",
        "....oWwWo.......",
        "...oWwWo........",
        "...oWwWo........",
        "...oyo..........",
    ],
}

MODEL = """{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "kingdoms:item/%s"
  }
}
"""

ITEM_DEFINITION = """{
  "model": {
    "type": "minecraft:model",
    "model": "kingdoms:item/%s"
  }
}
"""


def png(path, rows, palette):
    """Writes one 16x16 RGBA PNG, no libraries."""
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


def check(name, rows):
    if len(rows) != 16:
        raise SystemExit("%s is %d rows, not 16" % (name, len(rows)))
    for index, row in enumerate(rows):
        if len(row) != 16:
            raise SystemExit("%s row %d is %d wide, not 16" % (name, index, len(row)))


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


def main():
    for folder in (TEXTURES, MODELS, ITEMS):
        os.makedirs(folder, exist_ok=True)
    written = 0
    for crude_name, rows in sorted(GRIDS.items()):
        check(crude_name, rows)
        for name, metal in ((crude_name, CRUDE_METAL),
                            (crude_name + "_forged", FORGED_METAL)):
            palette = dict(SHARED)
            palette.update(metal)
            png(os.path.join(TEXTURES, name + ".png"), rows, palette)
            write(os.path.join(MODELS, name + ".json"), MODEL % name)
            write(os.path.join(ITEMS, name + ".json"), ITEM_DEFINITION % name)
            written += 1
    print("wrote %d weapons: texture, model and item definition each" % written)


if __name__ == "__main__":
    main()
