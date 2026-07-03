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

# weathering copper bars / chains / trapdoors (TFC textures recoloured through the patina LUTs):
input/tfc/copper_bars/bars.png              (TFC's copper bars grate — tfc:block/metal/bars/copper)
input/tfc/copper_bars/smooth.png            (TFC's smooth copper post edge — tfc:block/metal/smooth/copper)
input/tfc/copper_chain/block.png            (TFC's copper chain block — tfc:block/metal/chain/copper)
input/tfc/copper_chain/item.png             (TFC's copper chain item — tfc:item/metal/chain/copper)
input/tfc/copper_trapdoor/door.png          (TFC's copper trapdoor — tfc:block/metal/trapdoor/copper)
# (the plated block + stairs + slab reuse VANILLA cut-copper textures — nothing to extract)

# soul lamps (TFC lamp glass recoloured through vanilla SOUL FIRE's palette via CLUT):
input/tfc/lamp.png                          (TFC's lit lamp glass — tfc:block/lamp, animated 16x48)
input/tfc/lamp.png.mcmeta                   (its animation metadata — copied verbatim to soul_lamp.png.mcmeta)
input/tfc/lamp_off.png                      (TFC's unlit lamp glass — tfc:block/lamp_off)
input/vanilla/soul_fire_0.png              (the soul-fire palette source for the CLUT recolour)
input/tfc/lamp_item/<metal>.png             (each metal's lamp ITEM texture — tfc:item/metal/lamp/<metal>, 9 metals)
# (../soul_lantern_item_overlay.png — the hand-extracted soul-glass overlay for the item icon — is a TRACKED tool-root file, not under input/)

# quartz brick (vanilla brick ITEM icon recoloured through the vanilla quartz ITEM icon's palette via CLUT):
input/vanilla/brick.png                     (the vanilla brick item icon — minecraft:item/brick — relief)
input/vanilla/quartz.png                     (the vanilla nether-quartz item icon — minecraft:item/quartz — CLUT palette)

# per-wood barrels (vanilla's four barrel faces CLUT'd through each wood's PLANKS palette):
input/vanilla/barrel_{side,top,bottom,top_open}.png   (the four vanilla barrel faces — the relief)
input/{tfc,afc,beneath}/planks/<wood>.png             (each wood's planks — the CLUT palette; wood lists in barrels.cs)

# coarse dirt: textures are HAND-PAINTED (checked in under textures/block/coarse_dirt/) — no inputs needed;
# coarsedirt.cs generates only the block JSON + tags, not the textures.
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

**Quartz brick** — the vanilla **item** icons `item/brick.png` and `item/quartz.png` come from `client-extra.jar`
(note: `textures/item/`, not `block/`); copy them to `input/vanilla/brick.png` and `input/vanilla/quartz.png`. The
generator CLUTs the brick icon through the quartz icon's palette into `assets/firmavanilla/textures/item/quartz_brick.png`.

**Barrels** — the vanilla `block/barrel_{side,top,bottom,top_open}.png` faces come from `client-extra.jar`; copy them
to `input/vanilla/`. Each wood's planks live at `assets/<ns>/textures/block/wood/planks/<wood>.png` in the TFC / AFC /
Beneath jars (same path for all three); copy each to `input/<ns>/planks/<wood>.png`. The generator CLUTs every barrel
face through each wood's plank palette into `assets/firmavanilla/textures/block/barrel/<wood>_<face>.png`. Wood lists
are in `barrels.cs` (mirroring the bookshelves).

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

## Barrel masks (editable)

The per-wood barrels CLUT vanilla's barrel faces through each wood's planks, but the metal hoops / open hole are
held out via a **hand-painted mask per face** in the tool root (tracked, alongside the grain masks): `../barrel_side_mask.png`,
`../barrel_top_mask.png`, `../barrel_top_open_mask.png`. **White = wood** (recoloured through the planks), **black =
metal/hole** (vanilla's pixel kept as-is). Black pixels are also excluded from the wood region's luminance-normalization
range, so the hoops don't crush the stave contrast (the bottom face is all wood, so it has no mask). Unlike the
luminance-derived grain masks these are **load-only** — the generator never creates or overwrites them, so a hand
edit is safe; paint the pixels and re-run. A missing mask falls back to a plain unmasked CLUT (with a warning).

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

## One-off: honeycomb → beeswax CLUT preview

A prototype (`dotnet run generate.cs -- clut`) recolours Minecraft's honeycomb block through FirmaLife
beeswax's palette via the standard luminance-normalized CLUT, writing `../honeycomb_wax.png` to the tool root
(a beeswax-toned honeycomb texture for the FirmaLife beekeeper — not yet wired to a block). Inputs (extract):

```
input/vanilla/honeycomb_block.png    (minecraft:block/honeycomb_block — from client-extra.jar)
input/firmalife/beeswax.png          (firmalife:item/beeswax — from the FirmaLife jar)
```
