# Generator source textures

The asset generator (`../generate.cs`) reads its source textures from this folder. They're used only to
(re)generate — everything under `input/` is **git-ignored**; the derivatives under
`firmavanilla/src/main/resources` are what ship. All are **third-party textures** (vanilla Minecraft, TFC, AFC,
Beneath). Most are copied straight from the dev jars; `vanilla/bookshelf_overlay.png` is **derived from
Mojang's `bookshelf.png`** — the book-spine pixels lifted out, transparent elsewhere — so it's re-creatable
from the vanilla texture rather than committed.

## Required files

```
# chiseled sandstone (third-party — extract):
input/vanilla/chiseled_sandstone.png        (the "creeper" relief)
input/vanilla/cut_sandstone.png             (its flat counterpart)
input/vanilla/chiseled_red_sandstone.png    (the "wither" relief)
input/vanilla/cut_red_sandstone.png         (its flat counterpart)
input/tfc/cut_sandstone/{black,brown,green,pink,red,white,yellow}.png   (the TFC colour bases)

# decorative bookshelves:
input/vanilla/bookshelf_overlay.png         (book-spine pixels lifted from vanilla bookshelf.png, transparent elsewhere)
input/{tfc,afc,beneath}/bookshelf_empty/<wood>.png   (third-party — each wood's empty bookshelf frame)

# rock tiles (plain + cracked), per TFC rock:
input/vanilla/deepslate_tiles.png           (the plain tile pattern)
input/vanilla/cracked_deepslate_tiles.png   (the cracked tile pattern)
input/tfc/rock_smooth/<rock>.png            (each rock's smooth texture — default CLUT ramp + grain source)
input/tfc/rock_bricks/<rock>.png            (each rock's bricks texture — CLUT ramp for BRICK_LUT_ROCKS + plain-tiles recipe)

# alabaster tile + pillar (purpur recoloured), per TFC dye colour:
input/vanilla/purpur_block.png              (the tile pattern)
input/vanilla/purpur_pillar.png             (the pillar side pattern)
input/vanilla/purpur_pillar_top.png         (the pillar end pattern)
input/tfc/alabaster_bricks/<colour>.png     (each dye colour's alabaster-brick CLUT palette)
input/tfc/alabaster_bricks/uncolored.png    (the uncoloured alabaster-brick palette — for the base alabaster_tile)
input/tfc/alabaster_raw/<colour>.png        (each dye colour's raw alabaster — composite source)
input/tfc/alabaster_raw/uncolored.png       (the uncoloured raw alabaster — base alabaster_tile composite source)

# patina palettes (extracted from vanilla copper weathering stages):
input/vanilla/exposed_copper.png            (the "exposed" oxidation palette source)
input/vanilla/weathered_copper.png          (the "weathered" oxidation palette source)
input/vanilla/oxidized_copper.png           (the "oxidized" oxidation palette source)

# patina'd copper bars (TFC copper bars recoloured through the patina LUTs):
input/tfc/copper_bars/bars.png              (TFC's copper bars grate — tfc:block/metal/bars/copper)
input/tfc/copper_bars/smooth.png            (TFC's smooth copper post edge — tfc:block/metal/smooth/copper)
```

## Extracting them from the dev dependency jars

After at least one Gradle run has populated the caches:

**Vanilla** — `assets/minecraft/textures/block/<name>.png` inside
`~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar`.

**TFC** — note the on-disk path differs from the in-game id: the cut texture lives at
`assets/tfc/textures/block/sandstone/cut/<colour>.png` (in-game id `tfc:cut_sandstone/<colour>`) inside the
TFC jar under `~/.gradle/caches/modules-2/files-2.1/curse.maven/terrafirmacraft-302973/<fileId>/`. Copy each
`sandstone/cut/<colour>.png` to `input/tfc/cut_sandstone/<colour>.png`.

**Bookshelf empty frames** — `assets/<ns>/textures/block/wood/planks/<wood>_bookshelf_empty.png` inside the
TFC / AFC (`arborfirmacraft-877545`) / Beneath (`beneath-1113980`) jars. Copy each to
`input/<ns>/bookshelf_empty/<wood>.png` (drop the `_bookshelf_empty` suffix). Wood lists are in `generate.cs`.

**Rock tiles** — the vanilla patterns `block/deepslate_tiles.png` and `block/cracked_deepslate_tiles.png` come
from the vanilla `client-extra.jar` above. The TFC rock textures live at `assets/tfc/textures/block/rock/smooth/<rock>.png`
and `.../rock/bricks/<rock>.png` in the TFC jar; copy each to `input/tfc/rock_smooth/<rock>.png` and
`input/tfc/rock_bricks/<rock>.png`. Rock list is in `generate.cs`.

**Alabaster** — the vanilla `block/purpur_block.png`, `block/purpur_pillar.png`, `block/purpur_pillar_top.png`
come from `client-extra.jar`. The TFC alabaster palettes live at `assets/tfc/textures/block/alabaster/bricks/<colour>.png`
(CLUT palette) and `.../alabaster/raw/<colour>.png` (detail-stamp source) in the TFC jar; copy each to
`input/tfc/alabaster_bricks/<colour>.png` and `input/tfc/alabaster_raw/<colour>.png`. Colour list is in `generate.cs`.
Also copy the **uncoloured** `assets/tfc/textures/block/alabaster/bricks.png` and `.../raw.png` to
`input/tfc/alabaster_bricks/uncolored.png` and `input/tfc/alabaster_raw/uncolored.png` (the base `alabaster_tile`).

**Patina palettes** — the vanilla `block/{exposed,weathered,oxidized}_copper.png` come from `client-extra.jar`
above (the clean `copper_block.png` is **not** needed — the CLUT samples each stage's own pixels, no
subtraction). The generator reads them and writes the reusable LUT strips `../patina_{stage}.png` (see below).

**Copper bars** — TFC's `block/metal/bars/copper.png` (grate) and `block/metal/smooth/copper.png` (post edge) live
in the TFC jar; copy them to `input/tfc/copper_bars/bars.png` and `input/tfc/copper_bars/smooth.png`. The generator
recolours both through each patina LUT into `assets/firmavanilla/.../copper_bars/<stage>.png` (+ `_edge`).

## Running

```
cd firmavanilla/tools/generate-textures
dotnet run generate.cs          # needs the .NET 10 SDK
```

## Grain masks (editable)

The rock-tiles grain (mineral flecks added onto the tile faces) is gated by a 16×16 control map: **white = grain
applied** (tile faces), **black = skipped** (mortar grooves / shadows). There are **two**, one per tile pattern:

- `../grain_mask.png` — for the **plain** tiles (`deepslate_tiles` layout).
- `../grain_mask_cracked.png` — for the **cracked** tiles (`cracked_deepslate_tiles` layout); its black pixels
  also follow the crack lines, so grain (and chalk's seam-darken) skip the cracks instead of painting over them.

Both are tracked, hand-editable files (NOT under `input/`): paint pixels black/white in any editor to control
exactly where flecks appear, then re-run the generator and it uses your edited masks as-is.

Each is auto-created the first time (and rebuilt only with `dotnet run generate.cs -- regen-mask`, or if the file
is missing) from granite's tile structure — which every rock shares, since the CLUT preserves the pattern's
luminance ordering, so one mask per pattern serves all 20 rocks.

## Patina palettes (generated, reusable)

The generator extracts a reusable **luminance → patina-colour LUT** from each of vanilla copper's three weathering
stages and writes them to the tool root (tracked, alongside the grain masks):

- `../patina_exposed.png` — copper barely turned (a faint green fleck amid the copper-brown ramp).
- `../patina_weathered.png` — the classic teal/green patina ramp.
- `../patina_oxidized.png` — fully oxidized teal→bright-green ramp.

Each is a 256×16 strip: column *x* is the patina colour for source luminance *x* (the stage texture's own
`luma min..max` remapped across the full width). It's self-contained — no `copper_block` and no subtraction; the
ramp is sampled straight from the stage via `BuildPaletteRamp`. To **patina-ify any block** later, feed a strip
to `ClutThrough(pattern, patinaStrip)` as the `lutBase` (exactly like the alabaster-brick palettes), and the
target's luminance is repainted through the copper patina. Re-run the generator to refresh them.
