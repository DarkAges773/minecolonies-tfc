# firmavanilla — TFC Vanilla Building Blocks

A **standalone** TerraFirmaCraft companion (`firmavanilla`, package `com.firmavanilla`) that ships
TFC-palette decorative building blocks — the variants bare TFC doesn't include but MineColonies (and any
TFC) builds want. Depends **only on TerraFirmaCraft** (no MineColonies, no `:replacements`). The
MineColonies × TerraFirmaCraft bridge (`:compat`/`mctfc`) hard-depends on this mod and points its
Structurize substitution rules at these blocks.

Two kinds of content live here, registered very differently:

| | Cemented cobble | Chiseled sandstone |
|---|---|---|
| Source | mirrors an **existing** TFC/vanilla cobble | **net-new** form TFC lacks |
| Set | unknown at build time (registry scan) | fixed (7 TFC sand colours) |
| Registration | runtime `RegisterEvent` scan | static `DeferredRegister` |
| Assets | none (model delegated to source) | machine-generated, checked in |

---

## Non-falling ("mortared"/"cemented") cobble — DONE & verified

> Migrated here from `:compat` unchanged (package `com.mctfc` → `com.firmavanilla`); the twin ids changed
> `mctfc:mortared/*` → `firmavanilla:mortared/*` and the tag `mctfc:mortared_cobblestone` →
> `firmavanilla:mortared_cobblestone`. `:compat`'s substitution rules ([tfc_stone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json))
> were repointed to match.

TFC makes cobble collapse (gravity), which wrecks cobble builds. `firmavanilla` registers a **non-falling
twin** of every cobble block and `:compat` substitutes builds onto it.

- **Why a twin block, not a property/mixin:** TFC's falling is **tag-gated** — `tfc:can_landslide` lists
  `minecraft:cobblestone`/`mossy_cobblestone` and every `tfc:rock/cobble|mossy_cobble/<rock>`, checked per
  *block* (not per state). You can't add a blockstate property to an existing block (its `StateDefinition`
  is frozen at construction), and even if you could, TFC reads the tag, not a property. So the surgical fix
  is a separate block that simply isn't in `can_landslide`. (Same technique as MehVahdJukaar's
  StoneZone/Moonlight: registry scan + naming detection + runtime-generated assets.)
- **Scan + register** ([MortaredCobbleRegistry](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleRegistry.java)):
  on `RegisterEvent`, iterate `ForgeRegistries.BLOCKS` and register a
  [MortaredCobbleBlock](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleBlock.java)
  (`extends Block`, `Properties.copy(source)`, drops self, name "Cemented &lt;source&gt;") + a
  [MortaredCobbleBlockItem](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleBlockItem.java)
  per cobble, id `firmavanilla:mortared/<source-ns>/<source-path>`. **Detection is a name heuristic**
  (`isCobble`: path ends `cobblestone` or contains a `cobble/` segment, minus
  `_stairs/_slab/_wall/_button/_pressure_plate` and `infested`) — tags are unavailable at registration; the
  heuristic is anchored to reproduce `forge:cobblestone/normal`. **Only sees blocks registered before
  `firmavanilla`** (mods.toml orders it AFTER tfc) — a cobble mod loading after us isn't covered.
- **Client model delegation** ([MortaredCobbleClient](../firmavanilla/src/main/java/com/firmavanilla/client/MortaredCobbleClient.java)):
  twins ship no blockstate/model JSON, so `ModelEvent.ModifyBakingResult` repoints each twin's baked block +
  item model at its source's. The bakery logs a benign "missing model" per twin during load — expected,
  overwritten here. (`getModels()` is keyed by `ResourceLocation`, not `ModelResourceLocation`.)
- **Runtime data pack** ([GeneratedDataPack](../firmavanilla/src/main/java/com/firmavanilla/data/GeneratedDataPack.java) +
  [MortaredCobbleData](../firmavanilla/src/main/java/com/firmavanilla/data/MortaredCobbleData.java)): the
  twins are dynamic so the tag/recipes can't be static JSON. At `AddPackFindersEvent` (twins already
  registered) we serve an in-memory **forced built-in** `SERVER_DATA` pack with
  `firmavanilla:mortared_cobblestone` (all twins) + a **shaped** recipe per twin (the cobble surrounded by 4
  `#tfc:mortar`, cross pattern). The pack also makes twins behave/identify like normal cobble by adding
  `#firmavanilla:mortared_cobblestone` (tag-of-tags) to the block tags real cobble sits in —
  `minecraft:mineable/pickaxe`, `forge:cobblestone/normal`, `tfc:can_carve`, `tfc:toughness_2` (+ Domum
  Ornamentum material tags) — but deliberately **not** `tfc:can_landslide` (the gravity we're escaping). The
  DO tag joins are harmless when DO is absent (the tag files just go unread).
- **In-world conversion** ([MortaredCobbleInteraction](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleInteraction.java),
  Forge bus): right-click a cobble holding `#tfc:mortar` → swap to its twin, consume 4 mortar (free in
  creative). Cancels the interaction; server-authoritative.
- **Creative tab** ([FirmaVanillaCreativeTab](../firmavanilla/src/main/java/com/firmavanilla/FirmaVanillaCreativeTab.java)):
  holds the static chiseled blocks + every dynamic twin. Beyond grabbing, being in a creative tab is what
  makes blocks **discoverable by MineColonies** (its `CompatibilityManager` item list — fill-block setting,
  pickers — is the union of all creative tabs' contents).

---

## Chiseled sandstone — 7 TFC colours

TFC ships `raw`/`cut`/`smooth` sandstone in 7 colours (black/brown/green/pink/red/white/yellow) but **no
chiseled** form, so `:compat` used to degrade `minecraft:chiseled_sandstone` → `tfc:cut_sandstone/yellow`
(losing the relief). `firmavanilla` fills the gap.

- **Static registration** ([SandstoneBlocks](../firmavanilla/src/main/java/com/firmavanilla/block/SandstoneBlocks.java)):
  a `DeferredRegister<Block>`/`<Item>` registers `firmavanilla:chiseled_sandstone/<colour>` for each of the
  7 colours (plain `Block`, `Properties.copy(Blocks.CHISELED_SANDSTONE)`). Static lang keys
  (`block.firmavanilla.chiseled_sandstone.<colour>`; `/` → `.` in description ids).
- **Motif split:** vanilla ships two chiseled reliefs — the *creeper* face (normal sandstone) and the
  *wither* motif (red sandstone). Each TFC colour wears one: **creeper** = yellow/white/pink/green,
  **wither** = red/black/brown. The split lives only in the texture generator, not the block code.
- **`:compat` substitution** ([tfc_sandstone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_sandstone.json)):
  `chiseled_sandstone` → `firmavanilla:chiseled_sandstone/yellow`, `chiseled_red_sandstone` →
  `firmavanilla:chiseled_sandstone/red`, plus a `mctfc:subst/sandstone/chiseled` re-pick pool (all 7) so the
  player can pick any colour in the Replace GUI.
- **Recipes (TFC chisel pattern):** `tfc:cut_sandstone/<colour>` + a chisel → the chiseled block — both at a
  crafting table (`tfc:damage_inputs_shapeless_crafting` with the `tfc:chisels` tag, tool damaged not consumed)
  and **in-world** (`tfc:chisel` smooth mode; TFC ships no sandstone chisel, so cut sandstone is free to claim).

### Asset generator (`tools/generate-textures`)

A **.NET 10 file-based app** ([generate.cs](../firmavanilla/tools/generate-textures/generate.cs), ImageSharp)
generates the side textures + all per-block JSON (blockstate / cube_column model / item model / loot table /
recipe / `minecraft:mineable/pickaxe` tag), checked into `src/main/resources`. Run:

```
cd firmavanilla/tools/generate-textures
dotnet run generate.cs            # needs the .NET 10 SDK
```

**Technique — CLUT (palette remap), the default.** Build a 256-entry luminance→colour ramp by sampling every
pixel of TFC's real `cut_sandstone/<colour>` (average the colours at each brightness; interpolate the gaps),
then repaint vanilla's chiseled art through it. **Normalization is the key step:** each vanilla pixel's
luminance is mapped from vanilla's range onto that colour's *actual* TFC tonal range before the lookup —
without it, palettes that don't overlap vanilla's luminance (dark **black**, low-contrast **green/pink**) clamp
to a single endpoint and the emblem flattens to a plain colour. With it, the full emblem contrast spans
whatever range the colour has. The emblem stays **crisp** (it's vanilla's exact pixels) and every colour is
**authentically TFC** (sampled from the real texture). This clearly beat the earlier multiply approach on the strongly-recoloured
colours — e.g. red, where vanilla's bright-orange wither motif becomes TFC's muted red-brown and stays fully
legible. (Same family as TFC's own `manual_palette_swap`, but the source→target table is built automatically
from the two textures instead of hand-authored palette strips.)

A second mode, **MULTIPLY (relief-transfer)**, is kept behind the `MODE` switch: `out = tfc_base ×
(chiseled ÷ cut)`, clamped `[0.45, 1.55]`. It preserves TFC's spatial grain but the emblem reads fainter and
frame differences between the two vanilla designs leak in as edge artifacts — so CLUT is the default.

Only the **side** texture is generated; the top/bottom reference TFC's smooth sandstone
(`tfc:block/sandstone/top/<colour>`) directly — matching vanilla, whose chiseled sandstone caps with the
smooth top, not the cut face (no point shipping a verbatim copy of TFC's asset).

> **Sources are not committed.** The generator reads vanilla + TFC textures from `tools/generate-textures/input/`
> (git-ignored — third-party assets we don't redistribute). See
> [input/README.md](../firmavanilla/tools/generate-textures/input/README.md) for the exact files and how to
> extract them from the dev dependency jars. The generated **derivatives** under `src/main/resources` are
> what ship.

---

## Decorative bookshelves — per wood

TFC/AFC/Beneath add the **chiseled** (6-slot `ChiseledBookShelfBlock`) bookshelf per wood, but **no** plain
decorative bookshelf with enchanting power (the `minecraft:bookshelf` equivalent). `firmavanilla` adds one per
wood — block id `firmavanilla:bookshelf/<wood>`.

- **Block** ([DecorativeBookshelfBlock](../firmavanilla/src/main/java/com/firmavanilla/block/DecorativeBookshelfBlock.java)):
  a full cube, `Properties.copy(Blocks.BOOKSHELF)`, overriding `getEnchantPowerBonus → 1.0F` (powers enchanting
  tables like vanilla) and `getDrops` to mirror vanilla bookshelf loot — **3 books**, or the block itself with
  **Silk Touch**. Drops are done in code (not a loot table) because the AFC/Beneath variants register
  conditionally, so a shipped loot table referencing them would fail to validate when those mods are absent.
- **Generated side texture.** Each wood's side is a **books overlay**
  (`tools/generate-textures/input/vanilla/bookshelf_overlay.png` — the book-spine pixels lifted from vanilla's
  `bookshelf.png`, transparent elsewhere; git-ignored like the other extracted inputs, since it's re-derivable
  from the vanilla texture) composited source-over onto that wood's **empty** bookshelf frame
  (`<ns>:..._bookshelf_empty`), so the books read like a vanilla bookshelf while the frame keeps each wood's
  colour. (TFC's own `_bookshelf_occupied` face — the chiseled 6-slot look — read wrong as a full block.) The
  block model is `cube_column`: sides = our `firmavanilla:block/bookshelf/<wood>`, top/bottom = the source mod's
  planks `<ns>:block/wood/planks/<wood>` (referenced directly).
- **Wood scope + conditional registration** ([BookshelfBlocks](../firmavanilla/src/main/java/com/firmavanilla/block/BookshelfBlocks.java)):
  TFC's 20 woods register unconditionally; AFC's 10 and Beneath's crimson/warped register **only when those mods
  are loaded** (`ModList.isLoaded`), since their textures/planks exist only then. The client assets
  (blockstate/model/lang) ship for **all** woods — unused (never baked) when a block isn't registered; the
  recipes carry a `forge:mod_loaded` condition and the `minecraft:mineable/axe` tag entries are `required:false`,
  so nothing errors when AFC/Beneath are absent.
- **Recipe:** 6 **lumber** + 3 books (vanilla bookshelf shape, but `<ns>:wood/lumber/<wood>` instead of plank
  blocks — lumber exists for every wood with no exceptions). AFC/Beneath recipes carry a `forge:mod_loaded`
  condition.
- **Tags** matching vanilla `minecraft:bookshelf`, so anything keying off them treats the decorative block
  identically: `minecraft:enchantment_power_provider`, `minecraft:mineable/axe`, and `forge:bookshelves` (block
  **and** item). All `replace:false` appends; AFC/Beneath entries are `required:false`.
- **Substitution** (`:compat`): `minecraft:bookshelf → firmavanilla:bookshelf/oak` + the
  `mctfc:subst/wood/decorative_bookshelf` re-pick pool (TFC woods; AFC/Beneath woods join via their conditional
  datapacks). This re-adds the bookshelf swap that `:compat` had previously *dropped* for lack of a target — and
  the old `:compat` recipe that restored a craftable vanilla bookshelf was **removed**, since the decorative
  ones replace it. `:compat` also tags them into MineColonies (`tier2blocks`, `reduceable_product_excluded`) and
  Domum Ornamentum (`slab_materials`) — the same tags vanilla bookshelf is in — so the colony builder and DO
  treat them identically (these are MineColonies/DO-namespaced, hence in `:compat`, generated by
  `gen_tfc_substitutions.sh`, with AFC/Beneath entries in their conditional datapacks).
- **Assets are generated** by the same [generate.cs](../firmavanilla/tools/generate-textures/generate.cs): the
  side texture (overlay composited over each empty frame via ImageSharp) plus blockstate / model / item-model /
  recipe / tag JSON (the three vanilla-matching tags) for every wood. The wood lists are duplicated in
  `BookshelfBlocks.java` and `generate.cs`; keep them in sync.

---

## Rock tiles — per TFC rock (proof of concept)

A deepslate-tiles-style "tiles" block for each of TFC's 20 rock types, in **two variants** per rock:
`firmavanilla:tiles/<rock>` (plain) and `firmavanilla:cracked_tiles/<rock>` (cracked). Both are plain full cubes
(`Properties.copy(Blocks.STONE_BRICKS)`, drop self). See
[TileBlocks](../firmavanilla/src/main/java/com/firmavanilla/block/TileBlocks.java).

The plain tile extends TFC's own `bricks → chiseled` smooth-chisel chain one step — both **in-world**
(chisel the `tfc:rock/chiseled/<rock>` block, smooth mode) and at a **crafting table** (chiseled + a chisel,
`tfc:damage_inputs_shapeless_crafting` with the `tfc:chisels` tag — the tool is damaged, not consumed; mirrors
TFC's own `bricks + chisel → chiseled`). The **cracked** tile mirrors TFC cracked bricks: a plain tile + a hammer
(`tfc:hammers` tag, same damaged-tool craft). firmavanilla hard-depends on TFC, so these recipes always load.

The texture combines **two** generated layers:

1. **CLUT base** — the vanilla tile *pattern* (`deepslate_tiles` for plain, `cracked_deepslate_tiles` for cracked)
   recoloured (luminance-normalized palette remap — see the chiseled-sandstone section) through each rock's TFC
   *palette*. The ramp source is per-rock: most rocks ramp from the flatter `tfc:rock/smooth/<rock>` (cleaner
   colour, stronger grain, softer seams), but the few in `BRICK_LUT_ROCKS` (basalt, claystone, conglomerate,
   granite) ramp from `tfc:rock/bricks/<rock>` for its built-in dark-mortar seam contrast. Move a rock between
   the two looks by adding/removing it from that one set. Normalization makes it hold across the full range —
   light **marble** stays white, dark **basalt** blue-grey — all carrying the same tile layout.
2. **Grain overlay** (`GrainOverlay`) — each rock's own bright mineral flecks, lifted from its
   `tfc:rock/smooth/<rock>` texture as a **bright high-pass** (`max(0, smooth − blur(smooth))`, σ=0.8),
   amplified (`GRAIN_STRENGTH` ×1.5, granite ×1.2 via `GRAIN_STRENGTH_GRANITE`) and *added* onto the tile
   **faces** only. Faces vs mortar is a **hand-editable mask** — one per tile pattern:
   `tools/generate-textures/grain_mask.png` (plain) and `grain_mask_cracked.png` (cracked, whose black pixels also
   follow the crack lines so grain/seam-darken skip the cracks). White = grain, black = skip; auto-built from
   granite's tile structure (shared by every rock, since the CLUT preserves the pattern's luminance ordering) and
   reused across all 20; rebuild with `dotnet run generate.cs -- regen-mask`. The grain self-scales: speckled rocks
   (granite) get visible grain, near-uniform rocks (marble) almost none — both correct, since it's the rock's
   *real* grain.

`cube_all` model, our generated texture on every face.

### Shapes (stairs / slab / wall)

Off the **plain** tiles, each rock also gets the full vanilla deepslate-tile shape family —
`firmavanilla:tile_stairs/<rock>`, `tile_slab/<rock>`, `tile_wall/<rock>` (`StairBlock`/`SlabBlock`/`WallBlock`,
same `STONE_BRICKS` properties). These add **no textures**: their blockstates/models reference the plain
`block/tiles/<rock>` texture on every face, so the generator emits only JSON (blockstate, the vanilla
`stairs`/`slab`/`wall` model templates, item model, loot — slabs with the double-count drop). They join the vanilla
`minecraft:blocks/{stairs,slabs,walls}` tags (walls need it for connection logic) and `mineable/pickaxe`. Cracked
tiles get **no** shapes (plain only).

**Recipes — all three paths TFC ships for its brick shapes** (crafting + stonecutting + chisel), off the plain
tile, with TFC's counts:

| Shape | Crafting | Stonecutting | Chisel |
|---|---|---|---|
| `tile_stairs` | `X␣␣/XX␣/XXX` → 8 | ×1 | tile, **stair** mode |
| `tile_slab` | `XXX` → 6 | ×2 | tile, **slab** mode (spare-slab `extra_drop`) |
| `tile_wall` | `XXX/XXX` → 6 | ×1 | — (TFC has no chisel-wall mode) |

The plain tile and the cracked tile are made the TFC tool-craft way (see above — chiseled + chisel; tile + hammer),
not stonecut. Chisel JSON lives in `data/firmavanilla/recipes/chisel/{smooth,stair,slab}/` as `tfc:chisel` recipes;
the shape craft/stonecut under `recipes/tile_{stairs,slab,wall}/`; the tile/cracked tool-crafts under
`recipes/{tiles,cracked_tiles}/`.

**Status:** plain + cracked full blocks, plus stairs/slab/wall off the plain tiles. **No** `:compat` substitution
wiring yet (deliberate proof of concept). The rock list is duplicated in `TileBlocks.java` and `generate.cs`; keep
them in sync.

## Alabaster tile + pillar — per TFC dye colour

Vanilla's **purpur** block and pillar recoloured (CLUT — same luminance-normalized palette remap as the rock
tiles) through each of TFC's **16 alabaster dye colours**' `tfc:alabaster/bricks/<colour>` palette — a form TFC
doesn't ship. Per colour: `firmavanilla:alabaster_tile/<colour>` (full cube `cube_all`, from `purpur_block`) and
`firmavanilla:alabaster_pillar/<colour>` (a `RotatedPillarBlock` `cube_column`, from `purpur_pillar` + `_top`).
Both `Properties.copy(Blocks.STONE_BRICKS)`. See
[AlabasterBlocks](../firmavanilla/src/main/java/com/firmavanilla/block/AlabasterBlocks.java).

After the CLUT, a **detail-stamp** pass (the same `GrainOverlay` high-pass used by the rock tiles) adds each
colour's natural stone speckle — lifted from its **raw alabaster** (`tfc:alabaster/raw/<colour>`) — over the tile
and both pillar faces. Unlike the rocks this pass is **mask-less** (`GrainOverlay(..., null)`): the speckle lands
everywhere, since alabaster has no mortar grooves to protect. Strength is the single `ALABASTER_GRAIN_STRENGTH`
(start 1.0). The pillar's side and `_top` faces both ramp through the same alabaster-brick palette. Join
`mineable/pickaxe`. The colour list (vanilla DyeColor order) is duplicated in `AlabasterBlocks.java` and
`generate.cs`; keep them in sync.

There are also **uncoloured bases** `firmavanilla:alabaster_tile` and `alabaster_pillar` (no colour segment, like
TFC's `tfc:alabaster/bricks`), generated from the uncoloured alabaster bricks/raw. Both are dyeable bases (see
Recipes).

**Shapes.** Off the alabaster tile, each colour also gets `alabaster_tile_stairs/`, `alabaster_tile_slab/` and
`alabaster_tile_wall/<colour>` (`StairBlock`/`SlabBlock`/`WallBlock`), reusing the tile texture on every face via
the same generic shape emitters as the rock tiles (`StairsBlockstate`/`SlabBlockstate`/`WallBlockstate`, now
parameterised by `kind`/`baseKind`). They join the vanilla `stairs`/`slabs`/`walls` tags. Vanilla ships no purpur
wall, but the alabaster wall is provided anyway (it just uses the tile texture).

**Recipes — colouring matches TFC strictly.** Colour enters at the **tile** and flows outward, exactly as TFC dyes
its alabaster bases and crafts coloured shapes from them:
- **Tile** ← chisel `tfc:alabaster/bricks(/<colour>)` (in-world `tfc:chisel` smooth + table
  `tfc:damage_inputs_shapeless_crafting` with `tfc:chisels`), uncoloured and per-colour.
- **Dyeing** (like TFC's `barrel/dye/*_alabaster`): sealed barrel, the uncoloured **tile or pillar** + 25 mB
  `tfc:<colour>_dye` → the coloured one, duration 1000. Both the tile and pillar are dyeable full-block bases;
  shapes are **never** dyed (TFC dyes only base forms).
- **Pillar** ← chisel the tile (smooth, in-world + table) **or** dye the uncoloured pillar. **Shapes** ← off the
  coloured tile: crafting (stairs ×8, slab ×6, wall ×6) + chisel (stair/slab; no wall) + stonecutting (×1/×2/×1).
- No uncoloured shapes and no dye on shapes — matching TFC, where the uncoloured bases have no shapes and a shape's
  colour is carried by crafting/chiselling, not re-dyeing.

## Patina palettes — extracted from vanilla copper (reusable LUTs)

A small generator step that **extracts the copper oxidation palette** as a reusable luminance→colour LUT, one per
vanilla weathering stage (`exposed`/`weathered`/`oxidized`). The LUTs are tooling assets usable to patina-ify any
block; the first consumer is the patina'd copper bars below.

**How (and why not subtraction).** The obvious idea is "weathered − copper_block" to isolate the green. Measured
on the real 16×16 textures, copper's relief is ~80 % shared across stages (luminance correlation copper↔stage:
exposed 0.78, weathered 0.83, oxidized 0.77) and the patina is a near-uniform colour shift (avg copper
(192,107,79) → weathered (108,153,110) → oxidized (82,162,132); green-dominant pixel share 0 % → 92 % → 100 %). So
a literal subtraction yields a near-flat teal field that's *coupled to copper's own brightness* — wrong the moment
you add it onto a non-copper base. Instead we reuse the same **CLUT** machinery as the rest of the mod: sample each
stage's own pixels with `BuildPaletteRamp` (256-entry luminance→avg-colour) — `copper_block` and subtraction not
needed at all — and emit a normalized 256×16 ramp strip per stage to the tool root (tracked, like the grain masks):
`tools/generate-textures/patina_{exposed,weathered,oxidized}.png`.

**Reuse.** Feed any strip as a `lutBase` (identical to how the alabaster tiles consume
`tfc:alabaster/bricks/<colour>`); the target block's luminance is repainted through the copper patina,
brightness-correct on any base. Note: for a **16×16** target call `ClutSide(pattern, strip, w, h)` directly (not
`ClutThrough`, which resizes the output to the strip's 256×16). The strips and the extraction step are documented
in [input/README.md](../firmavanilla/tools/generate-textures/input/README.md) ("Patina palettes").

## Patina'd copper bars — first LUT consumer

TFC ships only **bright** copper bars (`tfc:metal/bars/copper`); these add the aged look. Per oxidation stage
(`exposed`/`weathered`/`oxidized`) a block `firmavanilla:copper_bars/<stage>` — a vanilla
[`IronBarsBlock`](../firmavanilla/src/main/java/com/firmavanilla/block/CopperBarsBlocks.java) (`Properties.copy(Blocks.IRON_BARS)`).
The generator recolours **both** of TFC's bar textures through the stage's patina LUT: the grate
(`metal/bars/copper`, transparency preserved — the CLUT copies alpha) and the smooth post `edge`
(`metal/smooth/copper`), at native 16×16 via `ClutSide(tex, strip, 16, 16)`. The blockstate/models are a 1:1
retexture of TFC's iron-bars **multipart** (six parts — `post`/`post_ends`/`cap`/`cap_alt`/`side`/`side_alt`,
parenting `minecraft:block/iron_bars_*`), item is `item/generated` of the grate, loot drops self, and the blocks
join `mineable/pickaxe`. Purely decorative — **no oxidation progression / scraping / waxing** (the LUTs only colour
the texture; vanilla copper's `WeatheringCopper` mechanics aren't wired). The `exposed`/`weathered`/`oxidized` stage
list is duplicated in `CopperBarsBlocks.java` (`STAGES`) and `generate.cs` (`COPPER_STAGES`); keep them in sync.
