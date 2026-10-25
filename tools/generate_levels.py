#!/usr/bin/env python3
"""Generate Steel Eagle levels 002-050 and their preview sheets.

The level format and the checks mirror core LevelParser/LevelValidator. Level
001 is hand-made and is deliberately left untouched; the script reads it for
the preview and validates it with the same rules.

Usage:
    python3 tools/generate_levels.py
"""

from __future__ import annotations

import random
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEVEL_DIR = ROOT / "app" / "src" / "main" / "assets" / "levels"
PREVIEW_DIR = ROOT / "tools" / "preview"

SIZE = 26
MAIN_LEFT = 2
MAIN_RIGHT = 23
MAIN_TOP = 4
MAIN_BOTTOM = 21

EAGLE_X, EAGLE_Y = 12, 24
PLAYER_SPAWNS = [(8, 24), (16, 24)]
ENEMY_SPAWNS = [(0, 0), (12, 0), (24, 0)]
EAGLE_CELLS = {(12, 24), (13, 24), (12, 25), (13, 25)}
PLAYER_CELLS = {
    (8, 24), (9, 24), (8, 25), (9, 25),
    (16, 24), (17, 24), (16, 25), (17, 25),
}
ENEMY_CELLS = {
    (0, 0), (1, 0), (0, 1), (1, 1),
    (12, 0), (13, 0), (12, 1), (13, 1),
    (24, 0), (25, 0), (24, 1), (25, 1),
}

NAMES = [
    "Front Line", "Brick Yard", "Grandstand", "Hall of Pillars",
    "Crossfire", "Iron Gate", "River Bend", "Green Maze",
    "Ice Road", "Last Outpost", "Steel Garden", "Broken Bridge",
    "Fortress", "Tree Line", "Blue Canal", "Armored Yard",
    "The Tower", "Shallow Ford", "Pine Maze", "Steel Ring",
    "River Fort", "Frozen Pass", "Iron Woods", "Twin Bridges",
    "Citadel", "Ice Field", "White Maze", "Glacier Gate",
    "Cold Steel", "Ice Fortress", "Cracked Road", "Crystal Maze",
    "Frozen River", "Winter Fort", "Ice Needles", "White Halls",
    "Frozen Lake", "Hail Fort", "Crystal Ring", "Last Winter",
    "Iron Web", "Black Maze", "Steel Teeth", "Ruined Fort",
    "The Horde", "Iron Storm", "Final Maze", "Steel Crown",
    "The Trial", "Steel Eagle",
]

TERRAIN = set(".BSWTI")
PASSABLE = set(".BTI")
FONT = "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf"

COLORS = {
    ".": (16, 16, 16),
    "B": (190, 95, 35),
    "S": (175, 175, 182),
    "W": (30, 75, 190),
    "T": (30, 105, 35),
    "I": (170, 220, 240),
}
GRID_COLOR = (8, 8, 8)
MARKER_COLORS = {
    "eagle": (250, 210, 20),
    "player": (0, 210, 210),
    "enemy": (220, 40, 40),
}


def empty_grid() -> list[list[str]]:
    return [["." for _ in range(SIZE)] for _ in range(SIZE)]


def put(grid: list[list[str]], x: int, y: int, char: str) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        grid[y][x] = char


def fill(grid: list[list[str]], x0: int, y0: int, x1: int, y1: int, char: str) -> None:
    for y in range(max(0, y0), min(SIZE - 1, y1) + 1):
        for x in range(max(0, x0), min(SIZE - 1, x1) + 1):
            grid[y][x] = char


def fill_main(grid: list[list[str]], x0: int, y0: int, x1: int, y1: int, char: str) -> None:
    fill(
        grid,
        max(MAIN_LEFT, x0),
        max(MAIN_TOP, y0),
        min(MAIN_RIGHT, x1),
        min(MAIN_BOTTOM, y1),
        char,
    )


def base_grid() -> list[list[str]]:
    grid = empty_grid()
    # Standard eagle wall: row 23 top, sides at 24-25, eagle interior free.
    for x in range(11, 15):
        grid[23][x] = "B"
    for y in (24, 25):
        grid[y][11] = "B"
        grid[y][14] = "B"
    return grid


def tier(level: int) -> int:
    if level <= 10:
        return 1
    if level <= 25:
        return 2
    if level <= 40:
        return 3
    return 4


def composition(level: int) -> tuple[int, int, int, int]:
    """Returns basic, fast, power, armored counts (sum 20)."""
    if level == 1:
        return 14, 4, 2, 0
    if level <= 2:
        fast, power, armored = 4, 0, 0
    elif level <= 10:
        fast, power, armored = 6, 2 + (level - 3) // 2, 0
    elif level <= 15:
        fast, power, armored = 6, 6 + (level - 10) // 2, 0
    elif level <= 25:
        fast, power, armored = 5, 8, 1 + (level - 16) // 4
    elif level <= 40:
        fast, power, armored = 4, 8, 4 + (level - 26) // 4
    elif level <= 44:
        fast, power, armored = 2, 8, 8
    elif level <= 46:
        fast, power, armored = 2, 8, 9
    elif level <= 48:
        fast, power, armored = 2, 8, 10
    elif level == 49:
        fast, power, armored = 1, 9, 10
    else:
        fast, power, armored = 0, 10, 10

    basic = 20 - fast - power - armored
    if basic < 0:
        raise ValueError(f"level {level} composition is over 20")
    return basic, fast, power, armored


def enemy_spec(counts: tuple[int, int, int, int]) -> str:
    parts = []
    for char, count in zip("BFP A".replace(" ", ""), counts):
        if count:
            parts.append(f"{char}*{count}")
    return " ".join(parts)


# --------------------------------------------------------------------- layouts

def rooms(grid: list[list[str]], level: int, variant: int) -> None:
    cols = [2, 3, 3, 2, 3][variant]
    rows = [3, 2, 2, 3, 2][variant]
    room_w = (MAIN_RIGHT - MAIN_LEFT + 1) // cols
    room_h = (MAIN_BOTTOM - MAIN_TOP + 1) // rows

    for row in range(rows):
        for column in range(cols):
            x0 = MAIN_LEFT + column * room_w
            y0 = MAIN_TOP + row * room_h
            x1 = min(MAIN_RIGHT, x0 + room_w - 1)
            y1 = min(MAIN_BOTTOM, y0 + room_h - 1)

            for x in range(x0, x1 + 1):
                put(grid, x, y0, "B")
                put(grid, x, y1, "B")
            for y in range(y0, y1 + 1):
                put(grid, x0, y, "B")
                put(grid, x1, y, "B")

            cx = (x0 + x1) // 2
            cy = (y0 + y1) // 2
            for door in (cx, cx + 1):
                put(grid, door, y0, ".")
                put(grid, door, y1, ".")
            for door in (cy, cy + 1):
                put(grid, x0, door, ".")
                put(grid, x1, door, ".")

            if variant in (1, 3) and (row + column) % 2 == 0:
                fill_main(grid, x0 + 2, y0 + 2, min(x1 - 1, x0 + 3), y0 + 2, "B")

    if level >= 6:
        fill_main(grid, 4, 6, 5, 7, "T")
    if level >= 8:
        fill_main(grid, 19, 17, 20, 18, "I")


def crossroads(grid: list[list[str]], level: int, variant: int) -> None:
    cx = [11, 12, 10, 9, 13][variant]
    cy = [10, 11, 9, 12, 8][variant]
    wall = "S" if level >= 16 and variant >= 3 else "B"

    for y in range(MAIN_TOP, MAIN_BOTTOM + 1):
        if y % 6 in (0, 1):
            continue
        for x in (cx, cx + 1):
            put(grid, x, y, wall)

    for x in range(MAIN_LEFT, MAIN_RIGHT + 1):
        if x % 7 in (0, 1):
            continue
        for y in (cy, cy + 1):
            put(grid, x, y, wall)

    for x, y in ((3, 5), (20, 5), (3, 18), (20, 18)):
        fill_main(grid, x, y, x + 1, y + 1, "B")
    if level >= 11:
        fill_main(grid, 14, 15, 15, 16, "T")
        fill_main(grid, 5, 12, 6, 13, "W")


def pillars(grid: list[list[str]], level: int, variant: int) -> None:
    step_x = [5, 6, 4, 5, 6][variant]
    step_y = [4, 5, 5, 4, 5][variant]
    size = [2, 2, 2, 2, 3][variant]
    steel = "S" if level >= 16 else "B"

    for y in range(5, MAIN_BOTTOM, step_y):
        for x in range(3, MAIN_RIGHT, step_x):
            char = steel if (x + y) % 3 == 0 else "B"
            fill_main(grid, x, y, x + size - 1, y + size - 1, char)

    if variant == 3:
        for x in range(3, MAIN_RIGHT, 5):
            fill_main(grid, x, 12, x + 1, 12, "B")
    if level >= 11:
        fill_main(grid, 4, 17, 5, 18, "T")
        fill_main(grid, 18, 6, 19, 7, "W")


def corridors(grid: list[list[str]], level: int, variant: int) -> None:
    wall = "S" if level >= 18 else "B"

    for index, y in enumerate(range(6, MAIN_BOTTOM, 4)):
        gap = 4 + ((variant + index * 3) % 16)
        for x in range(MAIN_LEFT, MAIN_RIGHT + 1):
            if abs(x - gap) <= 1:
                continue
            put(grid, x, y, "B" if index % 2 == 0 else wall)
        put(grid, gap, y, ".")
        put(grid, gap + 1, y, ".")

    for index, x in enumerate((5 + variant, 17 - variant)):
        for y in range(MAIN_TOP + 1, MAIN_BOTTOM):
            if y % 5 in (0, 1):
                continue
            put(grid, x, y, "B")

    if variant == 4:
        for x in range(6, 20):
            put(grid, x, 12, "W")
        for x in (8, 9, 16, 17):
            put(grid, x, 12, ".")
    if level >= 11:
        fill_main(grid, 20, 16, 21, 17, "T")


def fortress(grid: list[list[str]], level: int, variant: int) -> None:
    x0 = [8, 7, 9, 6, 6][variant]
    x1 = [15, 16, 14, 17, 17][variant]
    y0 = [7, 8, 6, 6, 5][variant]
    y1 = [15, 14, 16, 16, 17][variant]
    wall = "S" if level >= 16 else "B"
    moat = "W" if level >= 11 else "T"

    for x in range(x0, x1 + 1):
        put(grid, x, y0, wall)
        put(grid, x, y1, wall)
    for y in range(y0, y1 + 1):
        put(grid, x0, y, wall)
        put(grid, x1, y, wall)

    cx = (x0 + x1) // 2
    cy = (y0 + y1) // 2
    for door in (cx, cx + 1):
        put(grid, door, y0, ".")
        put(grid, door, y1, ".")
    for door in (cy, cy + 1):
        put(grid, x0, door, ".")
        put(grid, x1, door, ".")

    for x in range(max(MAIN_LEFT, x0 - 1), min(MAIN_RIGHT, x1 + 1) + 1):
        put(grid, x, y0 - 1, moat)
        put(grid, x, y1 + 1, moat)
    for y in range(y0 - 1, y1 + 2):
        put(grid, x0 - 1, y, moat)
        put(grid, x1 + 1, y, moat)
    for door in (cx, cx + 1):
        put(grid, door, y0 - 1, ".")
        put(grid, door, y1 + 1, ".")
    for door in (cy, cy + 1):
        put(grid, x0 - 1, door, ".")
        put(grid, x1 + 1, door, ".")

    for x, y in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
        fill_main(grid, x, y, x + 1, y + 1, wall)
    fill_main(grid, cx - 1, cy - 1, cx + 1, cy + 1, "B")
    if level >= 22:
        fill_main(grid, 3, 6, 4, 7, "T")
        fill_main(grid, 19, 16, 20, 17, "I")


def river(grid: list[list[str]], level: int, variant: int) -> None:
    if variant < 3:
        y0 = [8, 9, 10, 11, 9][variant]
        width = 3 if variant in (0, 4) else 2
        for x in range(MAIN_LEFT, MAIN_RIGHT + 1):
            for y in range(y0, y0 + width):
                put(grid, x, y, "W")
        for x in range(MAIN_LEFT, MAIN_RIGHT + 1):
            put(grid, x, y0 - 1, "B")
            put(grid, x, y0 + width, "B")
        bridges = [4, 11, 18] if variant % 2 == 0 else [6, 15]
        for bridge in bridges:
            for x in (bridge, bridge + 1):
                for y in range(y0 - 1, y0 + width + 1):
                    put(grid, x, y, ".")
            for y in (y0 - 2, y0 + width + 1):
                put(grid, bridge - 2, y, "T")
                put(grid, bridge + 3, y, "T")
    else:
        x0 = [10, 12, 8, 13, 11][variant]
        width = 2 if variant % 2 else 3
        for y in range(MAIN_TOP, MAIN_BOTTOM + 1):
            for x in range(x0, x0 + width):
                put(grid, x, y, "W")
        for y in range(MAIN_TOP, MAIN_BOTTOM + 1):
            put(grid, x0 - 1, y, "B")
            put(grid, x0 + width, y, "B")
        bridges = [7, 12, 17] if variant % 2 == 0 else [9, 15]
        for bridge in bridges:
            for y in (bridge, bridge + 1):
                for x in range(x0 - 1, x0 + width + 1):
                    put(grid, x, y, ".")
    if level >= 26:
        fill_main(grid, 4, 18, 5, 19, "I")


def forest(grid: list[list[str]], level: int, variant: int) -> None:
    for y in (6, 12, 18):
        for x in range(MAIN_LEFT, MAIN_RIGHT + 1):
            if (x + variant) % 6 not in (0, 1):
                put(grid, x, y, "B")

    positions = [
        (3, 5), (8, 6), (14, 5), (19, 7),
        (5, 11), (11, 12), (17, 11), (21, 13),
        (4, 17), (9, 18), (15, 17), (20, 19),
    ]
    for index, (x, y) in enumerate(positions):
        xx = x + (variant * 2 if index % 2 == 0 else -variant)
        yy = y + (variant if index % 3 == 0 else 0)
        fill_main(grid, xx, yy, xx + 2, yy + 1, "T")
        if variant >= 3 and index % 4 == 0:
            put(grid, xx + 1, yy + 2, "S")

    for x, y in ((8, 9), (15, 9), (11, 15)):
        fill_main(grid, x, y, x + 2, y + 2, ".")
    if level >= 32:
        fill_main(grid, 19, 5, 20, 6, "I")


def ice_field(grid: list[list[str]], level: int, variant: int) -> None:
    offset_x = [0, 2, 4, 1, 3][variant]
    offset_y = [0, 2, 1, 3, 1][variant]
    for y in range(5 + offset_y, MAIN_BOTTOM, 4):
        for x in range(4 + offset_x, MAIN_RIGHT, 5):
            if (x // 3 + y // 3 + variant) % 2 == 0:
                fill_main(grid, x, y, x + 2, y + 1, "I")
            else:
                fill_main(grid, x, y, x + 1, y + 1, "B")

    lake_x = 8 + (variant % 2)
    lake_y = 8 + (variant // 2)
    fill_main(grid, lake_x, lake_y, lake_x + 5, lake_y + 3, "I")
    for x, y in (
        (lake_x - 1, lake_y - 1),
        (lake_x + 6, lake_y - 1),
        (lake_x - 1, lake_y + 4),
        (lake_x + 6, lake_y + 4),
    ):
        fill_main(grid, x, y, x + 1, y + 1, "S")

    for x in range(2 + offset_x, MAIN_RIGHT, 6):
        for y in (5, 17):
            fill_main(grid, x, y, x + 1, y, "B")
    if level >= 37:
        fill_main(grid, 3 + offset_x, 18, 5 + offset_x, 19, "T")


def labyrinth(grid: list[list[str]], level: int, variant: int) -> None:
    for index, x in enumerate((5, 10, 15, 20)):
        for y in range(MAIN_TOP, MAIN_BOTTOM + 1):
            if (y + variant * 2 + index * 3) % 6 < 2:
                continue
            put(grid, x, y, "S")

    for index, y in enumerate((8, 13, 18)):
        for x in range(MAIN_LEFT, MAIN_RIGHT + 1):
            if (x + variant * 3 + index * 4) % 7 < 2:
                continue
            put(grid, x, y, "S")

    for y in range(5, MAIN_BOTTOM, 3):
        for x in range(3, MAIN_RIGHT, 4):
            if grid[y][x] == ".":
                fill_main(grid, x, y, x + 1, y + 1, "B")
    if level >= 42:
        fill_main(grid, 6, 19, 7, 20, "I")
        fill_main(grid, 18, 7, 19, 8, "W")


def asymmetric(grid: list[list[str]], level: int, variant: int) -> None:
    blocks = [
        (3, 5, 6, 3, "B"),
        (14, 4, 7, 4, "S"),
        (4, 13, 5, 4, "W"),
        (12, 12, 7, 5, "B"),
        (18, 9, 4, 8, "T"),
        (7, 7, 4, 3, "I"),
    ]
    for index, (x, y, w, h, char) in enumerate(blocks):
        xx = x + (variant if index % 2 == 0 else -variant)
        yy = y + (variant // 2 if index % 3 == 0 else 0)
        fill_main(grid, xx, yy, xx + w - 1, yy + h - 1, char)

    rng = random.Random(1000 + level)
    for index in range(10):
        x = rng.randrange(MAIN_LEFT, MAIN_RIGHT - 1)
        y = rng.randrange(MAIN_TOP, MAIN_BOTTOM - 1)
        char = "BSWTI"[(index + variant) % 5]
        fill_main(grid, x, y, x + 1, y + 1, char)

    for index in range(6):
        x = 3 + index * 3
        y = 5 + index * 2
        fill_main(grid, x, y, x + 1, y + 1, "B")


FAMILIES = [
    rooms,
    crossroads,
    pillars,
    corridors,
    fortress,
    river,
    forest,
    ice_field,
    labyrinth,
    asymmetric,
]


def place_if_missing(grid: list[list[str]], char: str, x: int, y: int, w: int = 2, h: int = 2) -> None:
    if any(char in row[MAIN_LEFT:MAIN_RIGHT + 1] for row in grid[MAIN_TOP:MAIN_BOTTOM + 1]):
        return
    fill_main(grid, x, y, x + w - 1, y + h - 1, char)


def decorate(grid: list[list[str]], level: int) -> None:
    if 4 <= level <= 10:
        fill_main(grid, 5, 6, 6, 7, "T")
        if level >= 8:
            fill_main(grid, 18, 16, 19, 17, "I")
    if 11 <= level <= 25:
        place_if_missing(grid, "S", 3, 5)
        place_if_missing(grid, "W", 18, 16, 2, 2)
        place_if_missing(grid, "T", 5, 18)
    if 26 <= level <= 40:
        place_if_missing(grid, "I", 9, 10, 3, 2)
        place_if_missing(grid, "S", 20, 6)
        place_if_missing(grid, "W", 3, 14, 3, 2)
        place_if_missing(grid, "T", 16, 18)
    if 41 <= level <= 50:
        place_if_missing(grid, "I", 9, 20, 3, 2)
        place_if_missing(grid, "S", 3, 7)
        place_if_missing(grid, "W", 20, 12, 3, 2)
        place_if_missing(grid, "T", 6, 17)


MAIN_WIDTH = MAIN_RIGHT - MAIN_LEFT + 1
MAIN_HEIGHT = MAIN_BOTTOM - MAIN_TOP + 1
MAIN_CELLS = MAIN_WIDTH * MAIN_HEIGHT

FALLBACKS = {
    "S": [(3, 6), (18, 7), (3, 15), (18, 16), (10, 5), (14, 17)],
    "W": [(6, 9), (14, 9), (6, 15), (14, 15), (3, 12), (18, 12)],
    "T": [(4, 5), (18, 5), (4, 17), (18, 17), (9, 13), (15, 7)],
    "I": [(8, 11), (15, 11), (8, 18), (15, 18), (3, 8), (19, 14)],
}


def patch_size(char: str) -> tuple[int, int]:
    return (3, 2) if char in "WI" else (2, 2)


def target_density(level: int) -> float:
    if level <= 10:
        return 0.33
    if level <= 25:
        return 0.33 + (level - 10) * 0.006
    if level <= 40:
        return 0.42 + (level - 25) * 0.0055
    return 0.50 + (level - 40) * 0.007


def minimum_terrain(level: int) -> dict[str, int]:
    if level <= 10:
        return {}
    if level <= 25:
        return {"S": 12, "W": 12, "T": 12}
    if level <= 40:
        return {"I": 12, "S": 8, "W": 8, "T": 8}
    return {"S": 12, "W": 12, "T": 12, "I": 12}


def count_main(grid: list[list[str]]) -> int:
    return sum(
        1
        for y in range(MAIN_TOP, MAIN_BOTTOM + 1)
        for x in range(MAIN_LEFT, MAIN_RIGHT + 1)
        if grid[y][x] != "."
    )


def count_char(grid: list[list[str]], char: str) -> int:
    return sum(row.count(char) for row in grid)


def patch_empty(grid: list[list[str]], x: int, y: int, w: int, h: int) -> bool:
    if x < MAIN_LEFT or y < MAIN_TOP or x + w - 1 > MAIN_RIGHT or y + h - 1 > MAIN_BOTTOM:
        return False
    return all(grid[yy][xx] == "." for yy in range(y, y + h) for xx in range(x, x + w))


def ensure_minimum(grid: list[list[str]], char: str, minimum: int) -> None:
    w, h = patch_size(char)
    positions = FALLBACKS[char]
    index = 0
    guard = 0
    while count_char(grid, char) < minimum and guard < 80:
        guard += 1
        placed = False
        for _ in range(len(positions)):
            x, y = positions[index % len(positions)]
            index += 1
            if patch_empty(grid, x, y, w, h):
                fill_main(grid, x, y, x + w - 1, y + h - 1, char)
                placed = True
                break
        if not placed:
            x, y = positions[index % len(positions)]
            index += 1
            fill_main(grid, x, y, x + w - 1, y + h - 1, char)


def fill_density(grid: list[list[str]], level: int) -> None:
    target = round(MAIN_CELLS * target_density(level))
    if level <= 7:
        materials = ["B", "B", "T"]
    elif level <= 10:
        materials = ["B", "T", "I"]
    elif level <= 25:
        materials = ["B", "S", "W", "T"]
    elif level <= 40:
        materials = ["B", "I", "S", "W", "T"]
    else:
        materials = ["S", "W", "B", "I", "T"]

    candidates = [
        (x, y)
        for y in range(MAIN_TOP + 1, MAIN_BOTTOM - 1, 2)
        for x in range(MAIN_LEFT + 1, MAIN_RIGHT - 1, 2)
    ]
    random.Random(5000 + level).shuffle(candidates)

    material_index = 0
    cursor = 0
    while count_main(grid) < target:
        placed = False
        for _ in range(len(candidates)):
            x, y = candidates[cursor % len(candidates)]
            cursor += 1
            for offset in range(len(materials)):
                char = materials[(material_index + offset) % len(materials)]
                shapes = (
                    [(3, 2), (2, 2), (2, 1), (1, 2)]
                    if char in "WI"
                    else [(2, 2), (2, 1), (1, 2)]
                )
                for w, h in shapes:
                    if patch_empty(grid, x, y, w, h):
                        fill_main(grid, x, y, x + w - 1, y + h - 1, char)
                        material_index += 1
                        placed = True
                        break
                if placed:
                    break
            if placed:
                break
        if not placed:
            break


def build_level(level: int) -> list[list[str]]:
    grid = base_grid()
    family_index = (level - 2) // 5 % len(FAMILIES)
    variant = (level - 2) % 5
    FAMILIES[family_index](grid, level, variant)
    decorate(grid, level)
    for char, minimum in minimum_terrain(level).items():
        ensure_minimum(grid, char, minimum)
    fill_density(grid, level)
    return grid


# ---------------------------------------------------------------- validation

def area_cells(x: int, y: int) -> list[tuple[int, int]]:
    return [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)]


def reachable(
    grid: list[list[str]],
    sources: set[tuple[int, int]],
    targets: set[tuple[int, int]],
) -> bool:
    visited = set()
    queue = []
    for x, y in sources:
        if 0 <= x < SIZE and 0 <= y < SIZE and grid[y][x] in PASSABLE:
            visited.add((x, y))
            queue.append((x, y))
    head = 0
    while head < len(queue):
        x, y = queue[head]
        head += 1
        if (x, y) in targets:
            return True
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if not (0 <= nx < SIZE and 0 <= ny < SIZE):
                continue
            if (nx, ny) in visited or grid[ny][nx] not in PASSABLE:
                continue
            visited.add((nx, ny))
            queue.append((nx, ny))
    return False


def validate_enemy_spec(spec: str) -> list[str]:
    errors = []
    spec = spec.strip()
    if not spec:
        return ["enemies: empty roster"]

    total = 0
    if "*" in spec:
        for token in spec.split():
            if token.count("*") != 1:
                errors.append(f"enemies: bad token '{token}'")
                continue
            type_char, count_text = token.split("*")
            if len(type_char) != 1 or type_char.upper() not in "BFPA":
                errors.append(f"enemies: bad type in '{token}'")
                continue
            if not count_text.isdigit() or int(count_text) <= 0:
                errors.append(f"enemies: bad count in '{token}'")
                continue
            total += int(count_text)
    else:
        types = "".join(spec.split()).upper()
        if any(char not in "BFPA" for char in types):
            errors.append("enemies: bad type list")
        total = len(types)

    if total != 20:
        errors.append(f"enemies: roster has {total}, expected 20")
    return errors


def density_band(level: int) -> tuple[float, float]:
    if level <= 10:
        return 0.28, 0.38
    if level <= 25:
        return 0.32, 0.46
    if level <= 40:
        return 0.40, 0.54
    return 0.47, 0.60


def validate(grid: list[list[str]], level: int, enemies: str) -> list[str]:
    errors = []
    if len(grid) != SIZE:
        return [f"map has {len(grid)} rows"]
    for y, row in enumerate(grid):
        if len(row) != SIZE:
            errors.append(f"row {y} has {len(row)} cells")
        for x, char in enumerate(row):
            if char not in TERRAIN:
                errors.append(f"row {y} column {x} has '{char}'")
    if errors:
        return errors

    errors.extend(validate_enemy_spec(enemies))

    for label, (x, y) in [
        ("enemy 1", ENEMY_SPAWNS[0]),
        ("enemy 2", ENEMY_SPAWNS[1]),
        ("enemy 3", ENEMY_SPAWNS[2]),
        ("player 1", PLAYER_SPAWNS[0]),
        ("player 2", PLAYER_SPAWNS[1]),
        ("eagle", (EAGLE_X, EAGLE_Y)),
    ]:
        if any(grid[cy][cx] != "." for cx, cy in area_cells(x, y)):
            errors.append(f"{label} spawn at ({x},{y}) is blocked")

    if not reachable(
        grid,
        {cell for spawn in ENEMY_SPAWNS for cell in area_cells(*spawn)},
        EAGLE_CELLS,
    ):
        errors.append("no path from an enemy spawn to the eagle")

    exterior = {
        (x, y)
        for x in range(10, 16)
        for y in range(22, 26)
        if x in (10, 15) or y == 22
    }
    for label, spawn in zip(("player 1", "player 2"), PLAYER_SPAWNS):
        if not reachable(grid, set(area_cells(*spawn)), exterior):
            errors.append(f"no path from {label} spawn to the eagle exterior")

    for char, minimum in minimum_terrain(level).items():
        actual = count_char(grid, char)
        if actual < minimum:
            errors.append(f"tier needs {minimum} of '{char}', got {actual}")

    if level >= 2:
        density = count_main(grid) / MAIN_CELLS
        low, high = density_band(level)
        if not (low <= density <= high):
            errors.append(
                f"density {density:.2f} outside {low:.2f}..{high:.2f}",
            )

    return errors


def check_layout(grid: list[list[str]], level: int) -> list[str]:
    errors = []
    for y in range(0, 4):
        if any(char != "." for char in grid[y]):
            errors.append(f"level {level}: reserved top row {y} is not empty")
    if any(char != "." for char in grid[22]):
        errors.append(f"level {level}: row 22 is not empty")
    for y in range(0, 23):
        for x in (0, 1, 24, 25):
            if grid[y][x] != ".":
                errors.append(f"level {level}: side corridor ({x},{y}) is not empty")
    for x in range(11, 15):
        if grid[23][x] != "B":
            errors.append(f"level {level}: eagle wall top is not brick")
    for y in (24, 25):
        for x in (11, 14):
            if grid[y][x] != "B":
                errors.append(f"level {level}: eagle wall side is not brick")
    for (x, y), label in [((12, 24), "eagle"), ((8, 24), "player 1"), ((16, 24), "player 2")]:
        if any(grid[cy][cx] != "." for cx, cy in area_cells(x, y)):
            errors.append(f"level {level}: {label} area is not empty")
    return errors


# -------------------------------------------------------------------- preview

def parse_level_file(path: Path) -> tuple[str, str, list[list[str]]]:
    name = ""
    enemies = ""
    grid = []
    in_map = False
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if not in_map:
            if line.startswith("name:"):
                name = line.removeprefix("name:").strip()
            elif line.startswith("enemies:"):
                enemies = line.removeprefix("enemies:").strip()
            elif line == "map:":
                in_map = True
        else:
            grid.append(list(line))
    return name, enemies, grid


def write_level_file(path: Path, number: int, name: str, counts: tuple[int, int, int, int], grid: list[list[str]]) -> None:
    lines = [
        f"# Steel Eagle level {number}",
        f"name: {name}",
        f"enemies: {enemy_spec(counts)}",
        "map:",
    ]
    lines.extend("".join(row) for row in grid)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def marker_for(x: int, y: int) -> tuple[int, int, int] | None:
    if (x, y) in EAGLE_CELLS:
        return MARKER_COLORS["eagle"]
    if (x, y) in PLAYER_CELLS:
        return MARKER_COLORS["player"]
    if (x, y) in ENEMY_CELLS:
        return MARKER_COLORS["enemy"]
    return None


def write_ppm(path: Path, grid: list[list[str]]) -> None:
    scale = 6
    size = SIZE * scale
    pixels = bytearray()
    for y in range(SIZE):
        for sy in range(scale):
            for x in range(SIZE):
                base = COLORS[grid[y][x]]
                marker = marker_for(x, y)
                for sx in range(scale):
                    if sx == scale - 1 or sy == scale - 1:
                        color = GRID_COLOR
                    elif marker is not None and 1 <= sx <= 2 and 1 <= sy <= 2:
                        color = marker
                    else:
                        color = base
                    pixels.extend(color)
    path.write_bytes(f"P6\n{size} {size}\n255\n".encode("ascii") + pixels)


def render_preview(levels: list[tuple[int, str, str, list[list[str]]]]) -> None:
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    tile_dir = PREVIEW_DIR / "tiles"
    if tile_dir.exists():
        for old in tile_dir.glob("*.png"):
            old.unlink()
    tile_dir.mkdir(exist_ok=True)

    ascii_path = PREVIEW_DIR / "levels.txt"
    with ascii_path.open("w", encoding="utf-8") as out:
        out.write("STEEL EAGLE - 50 LEVELS\n")
        out.write(". empty  B brick  S steel  W water  T trees  I ice\n")
        out.write("E eagle  P player spawns (both)  X enemy spawns\n\n")
        for number, name, enemies, grid in levels:
            preview = [row[:] for row in grid]
            for x, y in EAGLE_CELLS:
                preview[y][x] = "E"
            for x, y in PLAYER_CELLS:
                preview[y][x] = "P"
            for x, y in ENEMY_CELLS:
                preview[y][x] = "X"
            out.write(f"### {number:03d} - {name}\n")
            out.write(f"enemies: {enemies}\n")
            for row in preview:
                out.write("".join(row) + "\n")
            out.write("\n")

    labelled = []
    for number, name, _, grid in levels:
        ppm = tile_dir / f"{number:03d}.ppm"
        png = tile_dir / f"{number:03d}.png"
        write_ppm(ppm, grid)
        command = [
            "magick", str(ppm),
            "-gravity", "South",
            "-background", "#181818",
            "-splice", "0x34",
            "-fill", "white",
            "-font", FONT,
            "-pointsize", "11",
            "-annotate", "+0+26",
            f"{number:03d}",
            "-pointsize", "7",
            "-annotate", "+0+6",
            name,
            str(png),
        ]
        subprocess.run(command, check=True)
        labelled.append(str(png))

    contact = PREVIEW_DIR / "levels-contact.png"
    subprocess.run(
        [
            "montage", *labelled,
            "-tile", "10x5",
            "-geometry", "+6+6",
            "-background", "#101010",
            str(contact),
        ],
        check=True,
    )

    preview = PREVIEW_DIR / "levels.png"
    subprocess.run(
        [
            "magick", str(contact),
            "-gravity", "North",
            "-background", "#101010",
            "-splice", "0x34",
            "-fill", "white",
            "-font", FONT,
            "-pointsize", "18",
            "-annotate", "+0+8",
            "STEEL EAGLE - 50 LEVELS",
            str(preview),
        ],
        check=True,
    )
    markdown = PREVIEW_DIR / "levels.md"
    with markdown.open("w", encoding="utf-8") as out:
        out.write("# Steel Eagle - 50 levels\n\n")
        out.write("![Level preview](levels.png)\n\n")
        out.write("Markers: gold = eagle, cyan = player spawns, red = enemy spawns.\n\n")
        out.write("| # | Name | Enemies |\n")
        out.write("|---:|---|---|\n")
        for number, name, enemies, _ in levels:
            out.write(f"| {number:03d} | {name} | {enemies} |\n")

    print(f"preview: {preview}")
    print(f"ascii:   {ascii_path}")
    print(f"table:   {markdown}")


# ----------------------------------------------------------------------- main

def main() -> int:
    LEVEL_DIR.mkdir(parents=True, exist_ok=True)

    levels = []
    generated: dict[int, tuple[str, tuple[int, int, int, int], list[list[str]]]] = {}
    for number in range(1, 51):
        path = LEVEL_DIR / f"{number:03d}.txt"
        if number == 1:
            name, enemies, grid = parse_level_file(path)
        else:
            name = NAMES[number - 1]
            grid = build_level(number)
            counts = composition(number)
            enemies = enemy_spec(counts)
            generated[number] = (name, counts, grid)
        levels.append((number, name, enemies, grid))

    for number, name, enemies, grid in levels:
        errors = validate(grid, number, enemies)
        if number > 1:
            errors += check_layout(grid, number)
        if errors:
            print(f"level {number:03d} ({name}) invalid:", file=sys.stderr)
            for error in errors:
                print(f"  - {error}", file=sys.stderr)
            return 1

    seen: dict[str, int] = {}
    for number, _, _, grid in levels:
        key = "\n".join("".join(row) for row in grid)
        if key in seen:
            print(
                f"level {number:03d} duplicates level {seen[key]:03d}",
                file=sys.stderr,
            )
            return 1
        seen[key] = number

    for number, (name, counts, grid) in generated.items():
        write_level_file(LEVEL_DIR / f"{number:03d}.txt", number, name, counts, grid)

    counts = [
        count_main(grid)
        for _, _, _, grid in levels
    ]
    print(
        f"validated 001-050; main-area tiles min={min(counts)} max={max(counts)} "
        f"avg={sum(counts) / len(counts):.1f}",
    )
    render_preview(levels)
    return 0


if __name__ == "__main__":
    sys.exit(main())
