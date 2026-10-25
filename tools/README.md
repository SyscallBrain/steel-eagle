# Level tools

`generate_levels.py` writes `app/src/main/assets/levels/002.txt` through
`050.txt` and rebuilds the preview sheets in `tools/preview/`.

Level `001.txt` is hand-made and is never overwritten.

## Run

```sh
python3 tools/generate_levels.py
```

The script:

- applies the tier curve (brick rooms first, then steel,
  water and trees, then ice/mazes, then dense asymmetric maps);
- keeps the top spawn rows, side corridors and bottom eagle approach empty,
  and writes the standard brick eagle wall;
- checks spawn areas, the eagle area, enemy/player paths, terrain characters
  and the enemy roster using the same gameplay rules as
  `core/.../LevelValidator`, plus generator-only frame, tier and density checks;
- writes `002.txt`-`050.txt`; **manual edits to those generated files are
  overwritten on the next run** (edit this script instead). `001.txt` is
  hand-made, preserved and outside the 002-050 density curve;
- writes the preview files:
  - `tools/preview/levels.png` - 10x5 contact sheet with markers;
  - `tools/preview/levels.txt` - full ASCII maps with names and rosters;
  - `tools/preview/levels.md` - table of names and enemy compositions.

The Kotlin `AllLevelsTest` loads and validates every asset after generation, so
a Python-only pass is not enough.

ImageMagick (`magick` and `montage`) is required for the PNG.
