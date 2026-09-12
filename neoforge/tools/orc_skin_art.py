#!/usr/bin/env python3
"""Draws the player-shaped skins the settler renderer picks by race.

Three 64x64 skins in the vanilla wide (Steve) layout -- orc, war-painted orc,
goblin -- written straight out of the ASCII grids below. Same rules as
``orc_weapon_art.py`` next door: no third-party libraries, PNG written with
``zlib`` and ``struct``, and the committed output is the point (the build does
not run this). Drop a real skin over any PNG of the same name and nothing else
in the mod has to change.

Usage, from the repository root:

    python neoforge/tools/orc_skin_art.py

WHY A GRID PER FACE RATHER THAN ONE 64x64 GRID. A skin is not a picture, it is
twelve unrelated rectangles on one sheet, and sixty-four rows of sixty-four
characters hides which rectangle you are editing. So each cube face is its own
small grid and the atlas below says where it goes -- which doubles as the
documentation of the layout.

THE LAYOUT, read off vanilla's own ``assets/minecraft/textures/entity/player/
wide/steve.png`` (unpacked from the 26.2 client jar and decoded pixel by pixel,
rather than recalled). Every box is (x, y, width, height) in texture pixels:

    head          (0, 0, 32, 16)    hat overlay    (32, 0, 32, 16)
      top     ( 8,  0,  8,  8)        top      (40,  0,  8,  8)
      bottom  (16,  0,  8,  8)        bottom   (48,  0,  8,  8)
      right   ( 0,  8,  8,  8)        right    (32,  8,  8,  8)
      front   ( 8,  8,  8,  8)        front    (40,  8,  8,  8)   <- tusks
      left    (16,  8,  8,  8)        left     (48,  8,  8,  8)
      back    (24,  8,  8,  8)        back     (56,  8,  8,  8)

    body          (16, 16, 24, 16)  jacket overlay (16, 32, 24, 16)
      top     (20, 16,  8,  4)      right arm      (40, 16, 16, 16)
      bottom  (28, 16,  8,  4)        top      (44, 16,  4,  4)
      right   (16, 20,  4, 12)        bottom   (48, 16,  4,  4)
      front   (20, 20,  8, 12)        right    (40, 20,  4, 12)
      left    (28, 20,  4, 12)        front    (44, 20,  4, 12)
      back    (32, 20,  8, 12)        left     (48, 20,  4, 12)
                                      back     (52, 20,  4, 12)
    right leg     (0, 16, 16, 16)    right sleeve   (40, 32, 16, 16)
      top     ( 4, 16,  4,  4)      left arm       (32, 48, 16, 16)
      bottom  ( 8, 16,  4,  4)        top      (36, 48,  4,  4)
      right   ( 0, 20,  4, 12)        bottom   (40, 48,  4,  4)
      front   ( 4, 20,  4, 12)        right    (32, 52,  4, 12)
      left    ( 8, 20,  4, 12)        front    (36, 52,  4, 12)
      back    (12, 20,  4, 12)        left     (40, 52,  4, 12)
                                      back     (44, 52,  4, 12)
    right trousers (0, 32, 16, 16)   left sleeve    (48, 48, 16, 16)
    left leg      (16, 48, 16, 16)   left trousers  ( 0, 48, 16, 16)
      top     (20, 48,  4,  4)
      bottom  (24, 48,  4,  4)
      right   (16, 52,  4, 12)
      front   (20, 52,  4, 12)
      left    (24, 52,  4, 12)
      back    (28, 52,  4, 12)

Only ONE overlay box is painted: the hat front, which carries the tusks. The
head cube's overlay renders half a pixel outside the skull, so two pixels there
stand out in front of the mouth exactly the way a player skin fakes fangs --
and that is the whole reason the tusks live on a layer instead of on the face.
The other overlays (jacket, sleeves, trousers) are left fully transparent: the
jerkin, the arm bands and the boots are painted on the base cubes, which is one
less thing to line up.

Every pixel is either fully opaque or fully transparent, deliberately. The
settler model renders with ``RenderTypes::entityCutout`` (see
``HumanoidModel(ModelPart)``), which discards alpha below the cutoff rather than
blending it, so a half-transparent pixel would simply vanish.

The faces are symmetric left-to-right, so one grid serves both sides of a pair
and nothing has to know which way a side face's u axis runs.
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "civilization")
SKINS = os.path.join(ASSETS, "textures", "entity", "person")

SIZE = 64

# --- palettes -----------------------------------------------------------------
#
# One set of letters, three fills of it. An orc is grey-green and a goblin is
# sallow yellow-green; the leather, the straps and the bone are the same either
# way, because the two peoples buy from the same tanner.

SHARED = {
    ".": (0, 0, 0, 0),            # nothing
    "k": (24, 22, 18, 255),       # eye, mouth, near-black
    "d": (44, 40, 32, 255),       # brow ridge in shadow, and the mohawk
    "l": (74, 54, 38, 255),       # leather, shadow
    "L": (104, 78, 54, 255),      # leather, lit
    "b": (52, 38, 26, 255),       # belt and strapping
    "y": (150, 118, 46, 255),     # brass stud
    "t": (214, 206, 182, 255),    # tusk, bone white
    "r": (150, 38, 34, 255),      # war paint
}

ORC_HIDE = {
    "g": (74, 92, 66, 255),       # grey-green, shadow
    "G": (98, 122, 86, 255),      # grey-green, body
    "H": (122, 148, 106, 255),    # grey-green, highlight
}

GOBLIN_HIDE = {
    "g": (72, 90, 46, 255),       # sallow green, shadow
    "G": (104, 128, 62, 255),     # sallow green, body
    "H": (132, 158, 80, 255),     # sallow green, highlight
}

# --- the head -----------------------------------------------------------------

# Bald, with a dark strip of hair running front to back over the crown.
HEAD_TOP = [
    "gGGddGGg",
    "GGGddGGG",
    "GGGddGGG",
    "GGGddGGG",
    "GGGddGGG",
    "GGGddGGG",
    "GGGddGGG",
    "gGGddGGg",
]

# The underside of the jaw. Never lit, so never shaded.
HEAD_BOTTOM = ["gggggggg"] * 8

# Heavy brow across the whole face, eyes sunk under it, a hard mouth line.
# Row 0 is the hairline, where the mohawk comes over the crown.
HEAD_FRONT = [
    "GGGddGGG",
    "GGGGGGGG",
    "dddddddd",
    "GkkGGkkG",
    "gGGGGGGg",
    "GGGggGGG",
    "GkkkkkkG",
    "ggGGGGgg",
]

# The same face with two stripes of paint down each cheek. One warband's mark,
# and the only difference between the two orc variants.
HEAD_FRONT_PAINTED = [
    "GGGddGGG",
    "GrGGGGrG",
    "dddddddd",
    "rkkGGkkr",
    "grGGGGrg",
    "GrGggGrG",
    "GkkkkkkG",
    "ggGGGGgg",
]

# The temples: the brow band carries round the skull, and an ear below it.
# No mohawk here -- the strip sits on the crown, which a side face never sees.
HEAD_SIDE = [
    "gGGGGGGg",
    "GGGGGGGG",
    "GGGGGGGG",
    "GkkkkkkG",
    "GGGkkGGG",
    "GGGkGGGG",
    "gGGGGGGg",
    "ggGGGGgg",
]

# The mohawk again, coming down the back of the skull to the nape.
HEAD_BACK = [
    "GGGddGGG",
    "GGGddGGG",
    "GGGGGGGG",
    "GkkkkkkG",
    "GGGGGGGG",
    "gGGGGGGg",
    "gGGGGGGg",
    "ggGGGGgg",
]

# The tusks, and the only thing on any overlay layer. Rows 5 and 6 put the
# points just above the mouth line at row 6 of the face, rising from the lower
# jaw the way a boar's do.
HAT_FRONT = [
    "........",
    "........",
    "........",
    "........",
    "........",
    "..t..t..",
    "..t..t..",
    "........",
]

# --- the body -----------------------------------------------------------------

# A laced leather jerkin: the gap down the middle is the lacing. Belt at row 7,
# two studs on it, and a wrap below that.
BODY_FRONT = [
    "llLLLLll",
    "lLLllLLl",
    "lLLllLLl",
    "lLLllLLl",
    "lLLllLLl",
    "lLLllLLl",
    "lLLllLLl",
    "bbbbbbbb",
    "byybbyyb",
    "llllllll",
    "llllllll",
    "lllllllL",
]

BODY_BACK = [
    "llLLLLll",
    "lLLLLLLl",
    "lLLLLLLl",
    "lLLLLLLl",
    "lLLLLLLl",
    "lLLLLLLl",
    "lLLLLLLl",
    "bbbbbbbb",
    "bbbbbbbb",
    "llllllll",
    "llllllll",
    "llllllll",
]

BODY_SIDE = [
    "llll",
    "lLLl",
    "lLLl",
    "lLLl",
    "lLLl",
    "lLLl",
    "lLLl",
    "bbbb",
    "bbbb",
    "llll",
    "llll",
    "llll",
]

BODY_TOP = [
    "llLLLLll",
    "lLLLLLLl",
    "lLLLLLLl",
    "llLLLLll",
]

BODY_BOTTOM = ["llllllll"] * 4

# --- the arms -----------------------------------------------------------------

# Bare to the shoulder, which is the point: a strap high on the arm with a stud
# through it, and a wrist wrap. Rows 10 and 11 are the hand.
ARM_SIDE = [
    "GGGG",
    "GHHG",
    "bbbb",
    "byyb",
    "GHHG",
    "GHHG",
    "gGGg",
    "GHHG",
    "bbbb",
    "llll",
    "GHHG",
    "gGGg",
]

ARM_CAP = [
    "gGGg",
    "GHHG",
    "GHHG",
    "gGGg",
]

# --- the legs -----------------------------------------------------------------

# A leather wrap over the thigh, a bare shin, and a boot from the cuff down.
LEG_SIDE = [
    "llll",
    "lLLl",
    "lLLl",
    "llll",
    "gGGg",
    "GHHG",
    "GHHG",
    "gGGg",
    "bbbb",
    "llll",
    "lLLl",
    "llll",
]

LEG_TOP = ["llll"] * 4
LEG_SOLE = ["bbbb"] * 4

# --- where every grid goes ----------------------------------------------------
#
# (x, y, grid). Read against the box table in the module docstring. A pair of
# limbs shares its grids because the art is symmetric.

def atlas(face):
    """Every (x, y, grid) a skin is made of, given which head front to use."""
    placements = [
        # head
        (8, 0, HEAD_TOP), (16, 0, HEAD_BOTTOM),
        (0, 8, HEAD_SIDE), (8, 8, face), (16, 8, HEAD_SIDE), (24, 8, HEAD_BACK),
        # hat overlay: tusks only
        (40, 8, HAT_FRONT),
        # body
        (20, 16, BODY_TOP), (28, 16, BODY_BOTTOM),
        (16, 20, BODY_SIDE), (20, 20, BODY_FRONT),
        (28, 20, BODY_SIDE), (32, 20, BODY_BACK),
    ]
    # Arms and legs: same four sides and two caps per limb, four limbs.
    for x, y in ((40, 16), (32, 48)):          # right arm, left arm
        placements += [
            (x + 4, y, ARM_CAP), (x + 8, y, ARM_CAP),
            (x, y + 4, ARM_SIDE), (x + 4, y + 4, ARM_SIDE),
            (x + 8, y + 4, ARM_SIDE), (x + 12, y + 4, ARM_SIDE),
        ]
    for x, y in ((0, 16), (16, 48)):           # right leg, left leg
        placements += [
            (x + 4, y, LEG_TOP), (x + 8, y, LEG_SOLE),
            (x, y + 4, LEG_SIDE), (x + 4, y + 4, LEG_SIDE),
            (x + 8, y + 4, LEG_SIDE), (x + 12, y + 4, LEG_SIDE),
        ]
    return placements


# The base (non-overlay) boxes, flooded with shadow hide before anything is
# drawn on them. The corners of these rectangles are not on any cube face and
# so are never seen; filling them anyway means no base pixel is ever
# transparent, which is the one thing a cutout render type punishes.
BASE_BOXES = [
    (0, 0, 32, 16),      # head
    (16, 16, 24, 16),    # body
    (40, 16, 16, 16),    # right arm
    (32, 48, 16, 16),    # left arm
    (0, 16, 16, 16),      # right leg
    (16, 48, 16, 16),    # left leg
]

SKINS_TO_WRITE = (
    ("orc", ORC_HIDE, HEAD_FRONT),
    ("orc_painted", ORC_HIDE, HEAD_FRONT_PAINTED),
    # The goblin is the orc's build in a sicklier green, because the two share
    # one model and a scrawnier goblin is a model change rather than a repaint.
    # It is here because a third palette costs three lines; a goblin body is a
    # later unit.
    ("goblin", GOBLIN_HIDE, HEAD_FRONT),
)


def draw(hide, face):
    """One 64x64 grid of letters: the base boxes flooded, then every face laid in."""
    canvas = [["." for _ in range(SIZE)] for _ in range(SIZE)]
    for x, y, w, h in BASE_BOXES:
        for row in range(y, y + h):
            for col in range(x, x + w):
                canvas[row][col] = "g"
    for x, y, grid in atlas(face):
        for dy, line in enumerate(grid):
            for dx, cell in enumerate(line):
                canvas[y + dy][x + dx] = cell
    palette = dict(SHARED)
    palette.update(hide)
    return ["".join(row) for row in canvas], palette


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


def check():
    """Every grid is the size the box table says, and lands inside the sheet."""
    for x, y, grid in atlas(HEAD_FRONT) + atlas(HEAD_FRONT_PAINTED):
        width = len(grid[0])
        for index, line in enumerate(grid):
            if len(line) != width:
                raise SystemExit("a grid at (%d,%d) is ragged at row %d" % (x, y, index))
        if x + width > SIZE or y + len(grid) > SIZE:
            raise SystemExit("a %dx%d grid at (%d,%d) runs off the sheet"
                             % (width, len(grid), x, y))


def main():
    check()
    os.makedirs(SKINS, exist_ok=True)
    for name, hide, face in SKINS_TO_WRITE:
        rows, palette = draw(hide, face)
        png(os.path.join(SKINS, name + ".png"), rows, palette)
    print("wrote %d skins to %s" % (len(SKINS_TO_WRITE), os.path.normpath(SKINS)))


if __name__ == "__main__":
    main()
