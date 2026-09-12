#!/usr/bin/env python3
"""Draws the placeholder item art for the town's coin, and its JSON with it.

This is a PLACEHOLDER. The PNG is a 16x16 laid out by hand in the grid below --
a struck gold disc with a rim and an incised diamond, recognizable at a glance in
a hotbar and nothing more. It exists so the currency is finished and playable
without waiting on art, and it is meant to be replaced file for file: drop a real
16x16 over the PNG of the same name and nothing else in the mod has to change.

The mark on the face is a diamond rather than a letter on purpose. The coin's
display name is not settled (see docs/CURRENCY.md), and a "C" stamped into the
art would be a third place the name lives -- one that a translator cannot reach
and that nobody would remember to redraw.

No third-party libraries: PNG is written straight out with zlib and struct, so
this runs anywhere python does and will not rot when Pillow changes its API.

Usage, from the repository root:

    python neoforge/tools/coin_art.py

It rewrites the texture and both JSON files from scratch. Committing the output
is the point -- the build does not run this.

If the coin's REGISTRY ID ever changes, change NAME below to match
Currency.ID and re-run this; it writes the three asset files under the new name
(delete the old three by hand). Changing only the name players READ needs
nothing here -- that is one line in en_us.json. docs/CURRENCY.md has the whole
procedure.
"""

import os
import struct
import zlib

# The registry path, which is also all three asset filenames. Must match
# Currency.ID in :neoforge. See the module docstring.
NAME = "coin"

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "civilization")
TEXTURES = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")

# The palette, by the letter used in the grid below.
#
# One light source, up and to the left, which is where Minecraft's own item art
# puts it: a bright rim along the top, gold in the body, and a brown-gold shadow
# down the lower right so the disc reads as a disc and not a circle.
PALETTE = {
    ".": (0, 0, 0, 0),            # nothing
    "o": (74, 50, 14, 255),       # outline, dark bronze
    "h": (252, 238, 170, 255),    # struck rim, catching the light
    "G": (232, 196, 88, 255),     # gold, lit
    "g": (196, 150, 44, 255),     # gold, body
    "d": (146, 102, 26, 255),     # gold, shadowed
    "m": (120, 80, 18, 255),      # the incised mark, cut into the face
}

# 16 rows of 16 characters, read top row first -- which is the top of the icon,
# the way an item renders. A 14x14 disc inside the 16x16, so the outline is never
# clipped by the edge of the sprite.
GRID = [
    "................",
    ".....oooooo.....",
    "...oohhhhhhoo...",
    "..ohhGGGGGGddo..",
    "..ohGGGGGGGGdo..",
    ".ohhGGGmGGGGGdo.",
    ".ohGGGmmmGGGGdo.",
    ".ohGGmmmmmGGGdo.",
    ".ohGGGmmmGGGgdo.",
    ".ohGGGGmGGGggdo.",
    ".ohGGGGGGGgggdo.",
    "..ohGGGGGgggdo..",
    "..ohdGGGgggddo..",
    "...ooddddddoo...",
    ".....oooooo.....",
    "................",
]

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
        for cell in row:
            if cell not in PALETTE:
                raise SystemExit("%s row %d uses %r, which is not in the palette"
                                 % (name, index, cell))


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


def main():
    for folder in (TEXTURES, MODELS, ITEMS):
        os.makedirs(folder, exist_ok=True)
    check(NAME, GRID)
    png(os.path.join(TEXTURES, NAME + ".png"), GRID, PALETTE)
    write(os.path.join(MODELS, NAME + ".json"), MODEL % NAME)
    write(os.path.join(ITEMS, NAME + ".json"), ITEM_DEFINITION % NAME)
    print("wrote %s: texture, model and item definition" % NAME)


if __name__ == "__main__":
    main()
