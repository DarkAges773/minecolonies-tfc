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

**Layout — one file per feature.** `generate.cs` keeps the `#:property EnableDefaultCompileItems=true` directive,
which makes the file-based run compile every sibling `.cs` in the folder, so the generator is split while the
single `dotnet run generate.cs` command is unchanged. `generate.cs` itself is just the entry point (it runs each
feature, then writes the cross-cutting tags). The shared engine lives in `common.cs` (the static class `Gen`:
config — `MODID`, the `ROCKS` / `ALABASTER_COLORS` / `BAR_STAGES` lists, the tuning constants — the texture
pipeline, file/tag I/O, and the JSON emitters used across features); each feature is a self-contained file
(`chiseledsandstone.cs`, `bookshelves.cs`, `barrels.cs`, `rocktiles.cs`, `alabaster.cs`, `copper.cs`, `prismarinedeposits.cs`,
`soullamps.cs`, `quartz.cs`, `coarsedirt.cs`) holding its own generation logic + JSON emitters. References to
"`generate.cs`" elsewhere in this doc mean the generator as a whole — the specific logic lives in the matching
feature file, and shared helpers/constants (`ROCKS`, `BAR_STAGES`, …) in `common.cs` (`Gen`).
(Editor note: VS Code's C# language server analyses each file-based `.cs` in isolation, so it shows false-positive
"name does not exist" errors across these files; `dotnet run generate.cs` compiles them together and is correct.)

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

## Wood barrels — per wood

TFC's own barrel is a fluid-sealing device, not a vanilla-style item-storage container — so the openable
`minecraft:barrel` has no TFC equivalent. `firmavanilla` adds one per wood — block id `firmavanilla:barrel/<wood>`.

- **`BarrelBlockFV` + `BarrelBlockEntityFV`** ([BarrelBlocks](../firmavanilla/src/main/java/com/firmavanilla/block/BarrelBlocks.java)):
  each wood registers a [BarrelBlockFV](../firmavanilla/src/main/java/com/firmavanilla/block/BarrelBlockFV.java)
  (`extends BarrelBlock`, `Properties.copy(Blocks.BARREL)`) + a `BlockItem`. It keeps the **full** vanilla barrel
  behaviour — the `facing`/`open` blockstates, the open/close animation + sounds (the inherited opener counter) —
  but its block-entity ([BarrelBlockEntityFV](../firmavanilla/src/main/java/com/firmavanilla/block/BarrelBlockEntityFV.java))
  shrinks the container to **TFC's small-chest rules**: **18 slots** (two rows, not vanilla's 27) and the same
  **item-size limit** as a TFC chest. So our barrels follow TFC's storage rules — the behaviour the (now-removed)
  `:compat` `MixinBarrelBlockEntity` used to force onto the *vanilla* barrel, **moved here onto our own blocks**,
  the way TFC ships its own small chests rather than by mixing into vanilla (firmavanilla stays mixin-free; TFC is a
  compile dep). It reuses TFC's own `RestrictedChestContainer` + `CHEST_9x2` menu (`RestrictedSlot#mayPlace` →
  `TFCChestBlockEntity.isValid`) for the GUI restriction, and `canPlaceItem` → the same `isValid` so hoppers / the
  item-handler wrapper honour it too.
- **Why a subclass + a custom BE type** (not vanilla's `BARREL`): `BarrelBlockEntity`'s only constructor hardcodes
  `BlockEntityType.BARREL`, so on load that type's factory would rebuild a plain 27-slot vanilla barrel. So each
  barrel needs its **own** registered block-entity type (`BarrelBlocks.BARREL_BE`) and `BarrelBlockEntityFV`
  overrides `getType()` to it (and re-derives the 18-slot backing list in its ctor + on load via
  `getContainerSize`), so save/load round-trips through our factory. (The block registry fires before the BE
  registry, so `BarrelBlocks.ALL` is the type's valid-blocks list.)
- **Wood scope + conditional registration** mirrors the bookshelves: TFC's 20 woods register unconditionally; AFC's
  10 and Beneath's crimson/warped register only when those mods are loaded (the wood lists are aliased from
  `BookshelfBlocks`). Client assets (blockstate/models/lang) ship for all woods (unused when a block isn't
  registered).
- **Generated faces** ([barrels.cs](../firmavanilla/tools/generate-textures/barrels.cs)): vanilla's four barrel
  faces (`side`/`top`/`bottom`/`top_open`) recoloured through each wood's **planks** palette via CLUT (the same
  luminance-normalized remap as the rest of the mod). The plank palettes are extracted to
  `input/<ns>/planks/<wood>.png`. **Hand-painted masks** per face (tool-root `barrel_<face>_mask.png`, **load-only**,
  never auto-generated/overwritten — white = wood/CLUT, black = metal/hole) hold the metal hoops out: the masked
  region is excluded from the wood region's normalization range (so the near-black hoops don't crush the stave
  contrast — the unmasked `bottom` looked right while side/top read flat) and, on `top_open`, the masked hole is
  painted as that wood's **darkest tone** with only its brightness modulated rim→centre (anchoring to the darkest
  wood + keeping the rim below full brightness guarantees the hole reads darker than the staves on every wood,
  including the dark/normal woods where a fixed multiply of the wood's mid-tones blended in). The generator emits
  the textures + blockstate (vanilla's facing/open variants), closed/open `cube_bottom_top` models, item model, and
  loot (drop self).
- **Recipe — TFC's barrel shape + the matching trapdoor.** TFC crafts its barrel as `X X`/`X X`/`XXX` (6
  `<ns>:wood/lumber/<wood>`); these fill the **top-centre** with that wood's plank trapdoor: `XTX`/`X X`/`XXX` = 7
  `<ns>:wood/lumber/<wood>` + 1 `<ns>:wood/planks/<wood>_trapdoor`. AFC/Beneath recipes carry a `forge:mod_loaded`
  condition (their lumber/trapdoor exist only then), like the bookshelves.
- **Tags — matching vanilla `minecraft:barrel`** so anything keying off them treats our barrels identically:
  `minecraft:mineable/axe` (block; shared accumulator with the bookshelves — a mod ships one file per tag path, so
  it's written once by the generator entry point), `minecraft:guarded_by_piglins` (block; piglins guard/anger when
  it's opened or broken near them), and `forge:barrels/wooden` (**block + item**; what other mods key off to
  recognise a wooden barrel — it rolls up into `forge:barrels` via that tag's own `#forge:barrels/wooden` include).
  All `replace:false` appends; AFC/Beneath ids are `required:false`. Plus the creative tab (also what makes them
  discoverable by MineColonies). The wood lists are duplicated in `BarrelBlocks.java` (via `BookshelfBlocks`) and
  `barrels.cs`; keep them in sync.

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

For ad-hoc use outside the generator there's a **self-contained CLI** —
[`tools/patina/`](../firmavanilla/tools/patina/) (`dotnet run patina.cs -- <texture> [outDir]`) — that bundles the
LUT strips and writes the `exposed`/`weathered`/`oxidized` variants of any input texture, plus an `extract`
subcommand to rebuild the LUTs from vanilla copper. Same CLUT technique; no dependency on `generate-textures`.

## Weathering copper — TFC copper forms with vanilla's full copper lifecycle

TFC's copper blocks are bright and **non-aging**. This gives **six TFC copper forms** the complete vanilla copper
experience — **oxidation over time, axe-scraping, honeycomb-waxing, axe wax-off, lightning de-oxidation,
neighbour-influence** — and is the consumer of the extracted patina LUTs.

**Forms** (each a "set" of 4 weather stages `copper_<form>/<stage>` + 4 waxed twins `waxed_copper_<form>/<stage>`,
for `unaffected`/`exposed`/`weathered`/`oxidized`), all registered by
[`CopperWeathering`](../firmavanilla/src/main/java/com/firmavanilla/block/CopperWeathering.java), each
`Properties.copy(<its TFC source block>)`:

| Form id | TFC source / bridge | Block type | Melt (mB) |
|---|---|---|---|
| `bars` | `metal/bars/copper` | `IronBarsBlock` | 25 |
| `block` | `metal/block/copper` | `Block` (cube) | 100 |
| `block_stairs` | `metal/block/copper_stairs` | `StairBlock` | 75 |
| `block_slab` | `metal/block/copper_slab` | `SlabBlock` | 50 |
| `chain` | `metal/chain/copper` | `TFCChainBlock` | 6 |
| `trapdoor` | `metal/trapdoor/copper` | `TrapDoorBlock` (`BlockSetType.IRON`) | 200 |
| `cut` | — (props from plated block) | `Block` (cube) | 100 |
| `cut_stairs` | — | `StairBlock` | 75 |
| `cut_slab` | — | `SlabBlock` | 50 |

The four weather stages are `WeatheringCopper*Block` classes (each extends the vanilla block type + `implements
WeatheringCopper`, random-tick wiring copied from `WeatheringCopperFullBlock`); the waxed twins are plain vanilla
blocks (waxed copper never ages). That's **72 blocks** total (9 forms × 8).

The **`cut`** forms are firmavanilla-only — TFC has no cut copper. `registerForm` takes a `propsSource` (always, for
`Properties.copy`) and a nullable `tfcBridge`: bridged forms (the first six) give the bright stage no item and
placement-swap TFC's block to it; the cut forms pass `tfcBridge = null`, so **every** stage is a normal firmavanilla
item with self-loot and no swap. A cut block is **cut from the plated block of the same stage** via a TFC saw
(`tfc:damage_inputs_shapeless_crafting` with the `tfc:saws` tag) **and** a vanilla `minecraft:stonecutting` recipe;
the bright cut block cuts from TFC's `metal/block/copper`, the aged ones from our `copper_block/<stage>`. Cut
stairs/slabs are crafted from the cut block (same as the plated set). Cut textures are the vanilla **cut-copper**
series (`minecraft:block/<stage>_cut_copper`) — no generation.

**Textures.**
- `bars`, `chain`, `trapdoor` — TFC's own textures recoloured through the patina LUTs (`ClutSide(tex, strip, w, h)`,
  alpha preserved); `unaffected` = the TFC texture verbatim. (Bars also recolour a smooth `edge`; chain also recolours
  its separate item texture.) Transparent forms set Forge `render_type` on their models: bars/chain
  `cutout_mipped`, trapdoor `cutout` (else the grate/links/gaps render opaque).
- `block`, `block_stairs`, `block_slab` — **no generation**: they reuse vanilla's **plain copper-block** textures
  (`minecraft:block/copper_block`, `exposed_copper`, `weathered_copper`, `oxidized_copper`) — the smooth full block,
  matching TFC's plated block (not the lined `cut_copper`). The stairs/slabs put that same texture on every face. The
  big `StairsBlockstate`/`SlabBlockstate` emitters are reused as-is (they only reference model paths); only
  texture-bearing models are supplied per stage.

Waxed twins reuse the unwaxed textures (vanilla waxed copper is visually identical). All 48 blocks join
`mineable/pickaxe`.

**Recipes.** Every itemised block gets a `tfc:heating` recipe mirroring its TFC source's amount (see table) at
1080 °C, so they melt like any TFC metal. The plated **stairs/slab** are also craftable from the plated **block** of
the *same weather stage* (mirroring TFC's `copper_block`→stairs/slab `crafting_shaped`, ×8 / ×6) — per aged stage and
per waxed stage. The bright (UNAFFECTED) stage has no item of its own (it's TFC's), so TFC's existing heating + craft
recipes cover it. Note: TFC's heating schema puts `temperature` at the recipe top level, a sibling of `result_fluid`.

**How the mechanics work — no mixin.** Every vanilla copper mechanic keys off static `Supplier<BiMap>` tables
(`WeatheringCopper.NEXT_BY_BLOCK` + inverse drive oxidation/scrape/`getFirst`/lightning; `HoneycombItem.WAXABLES` +
inverse drive wax-on/wax-off). [`WeatheringMaps`](../firmavanilla/src/main/java/com/firmavanilla/weathering/WeatheringMaps.java)
**splices our block→block links into those maps** at `FMLCommonSetupEvent` — it finds each field by *probing its
contents* (which map sends `COPPER_BLOCK`→`EXPOSED_COPPER`, obfuscation-proof, no SRG/AT), rebuilds it with vanilla +
our entries, and overwrites via `Unsafe` (defeats `final`). Then Forge's own `AxeItem`/`HoneycombItem`/`LightningBolt`
do everything for free; our weathering blocks just `implements WeatheringCopper` (waxed don't, so lightning skips
them). **This is the reusable workflow core** — a new weathering form is one `CopperWeathering.registerForm(...)` call
(it auto-wires the oxidation chain, wax pairs, and placement swap).

**Stage 0 is bridged to TFC, not duplicated.** Each form's bright block has **no item of its own**: TFC's matching
item places it (one shared `BlockEvent.EntityPlaceEvent` listener swaps any registered TFC source block for our
bright block, carrying over every shared property — facing/half/open/type/axis/waterlogged), the block's loot +
pick-block (`getCloneItemStack`) yield the TFC item, so existing TFC copper simply starts aging with **no duplicate
items, no conversion recipes**. The aged + waxed stages have their own items. The stage list is duplicated in
`CopperWeathering.java` (`STAGE_NAMES`) and `generate.cs` (`BAR_STAGES`); keep them in sync. **TFC is a compile-time
dep** of `:firmavanilla` for this (see its `build.gradle`); the mod stays mixin-free.

## Prismarine deposits — a TFC-style source for the vanilla prismarine family

TFC leaves vanilla's prismarine blocks **and their recipes** intact, but never generates guardians or ocean
monuments — so prismarine **shards** and **crystals** (the recipe inputs) are simply unreachable. This adds a
TFC-native way to get them, mirroring TFC's own `native_copper` ore deposits **exactly** — and is the mod's first
worldgen feature. Registered by
[`PrismarineDeposits`](../firmavanilla/src/main/java/com/firmavanilla/block/PrismarineDeposits.java); all the
JSON (blockstates/models/loot/panning/sluicing/worldgen/tags) is machine-generated in `generate.cs`.

**The acquisition loop (no mixin — entirely datapack-driven):**
1. **One deposit block per TFC rock** — `firmavanilla:deposit/prismarine/<rock>` (20), a plain non-falling
   `Block` with gravel properties, exactly like TFC's deposit block.
2. **Worldgen** — a `tfc:soil_disc` configured feature (`firmavanilla:prismarine_deposit`) swaps each
   `tfc:rock/gravel/<rock>` for the matching deposit; a placed feature (`rarity_filter` chance 6 +
   `OCEAN_FLOOR_WG` + `tfc:biome`) is appended to TFC's **empty** `#tfc:in_biome/soil_discs/deep_ocean` and
   `deep_ocean_trench` tags (the biome `features` lists already reference those tags, so TFC's chunk generator
   runs them). Because all TFC gravel is per-rock, the per-rock deposit blends into the floor automatically; like
   TFC's own deposits it appears **only where the floor exposes gravel**, so density tracks the deep-ocean
   gravel cover.
3. **Pan / sluice** — TFC's data-driven `panning`/`sluicing` managers, populated under our namespace at
   `data/firmavanilla/tfc/{panning,sluicing}/deposits/prismarine_<rock>.json` (TFC scans all namespaces). Both
   reference one fishing-type loot table per rock: **prismarine shard** (common ~0.45), **prismarine crystals**
   (rare ~0.12), loose-rock filler — one item per wash. The block-break loot drops the deposit itself (carry it
   to a pan/sluice). The vanilla recipes then build block / bricks / dark prismarine / sea lantern.

**Textures — one animated overlay, no per-rock art.** Each deposit's block model is `parent: tfc:block/ore` with
`all` = that rock's `tfc:block/rock/gravel/<rock>` and `overlay` = a **single** hand-made
`firmavanilla:block/deposit/prismarine` crystal sheen, layered over the gravel by the model engine (TFC's own
trick). The pan-stage item models likewise reference TFC's gravel + vanilla `prismarine` via the `tfc:item/pan/*`
parents. So the only bespoke art is that one overlay — a **tracked, animated** input, `prismarine_overlay.png` in
the generator's tool root (a 16×64 four-frame strip). The generator copies it verbatim to the deposit texture and
writes its `.mcmeta` (frame timing/sequence mirroring vanilla `prismarine`); the input stays in the tool root, so
the copy never clobbers it. To restyle the deposits, edit `prismarine_overlay.png` and re-run the generator.

The block model is **inlined** (not `parent: tfc:block/ore`) — a gravel cube plus a coplanar overlay cube — so the
**overlay element alone** can carry Forge `forge_data` `{block_light:15, sky_light:15}`, making the crystals
**glow in the dark** (full-bright) with **no actual light emission** and no on/off state. Both share
`render_type: cutout_mipped` — required because TFC sets its own deposits' render layer in code (which we can't
reuse) and the default solid layer would render the overlay's transparent pixels as opaque black. The pan-stage
item models (`item/pan/prismarine/<rock>_{full,half}` + a shared `result`) aren't blockstate/registered-item
models, so they're registered as additional models in
[`PrismarineClient`](../firmavanilla/src/main/java/com/firmavanilla/client/PrismarineClient.java) (mirroring TFC's
`ClientEventHandler`) — otherwise TFC's pan renderer shows the missing-model placeholder while panning.

## Soul lamps — soul-lantern variant of every TFC metal lamp

A teal, dimmer "soul" twin of each TFC metal lamp (all 9 metals), the vanilla lantern→soul-lantern idea applied
to TFC's fuel-burning lamps. Registered by
[`SoulLamps`](../firmavanilla/src/main/java/com/firmavanilla/block/SoulLamps.java); **mixin-free**.

- **Behaviour is TFC's, inherited wholesale.** [`SoulLampBlock`](../firmavanilla/src/main/java/com/firmavanilla/block/SoulLampBlock.java)
  `extends` TFC's `LampBlock`, and `SoulLamps#props` **copies the real `tfc:metal/lamp/<metal>` block's properties**
  at registration (`BlockBehaviour.Properties.copy(...)`, the same pattern the cemented-cobble twins use — TFC
  loads first, so its lamp is already in the registry) then wires `ExtendedProperties.blockEntity(TFCBlockEntities.LAMP)`.
  So it *shares* TFC's `LampBlockEntity` and inherits **every** lamp property per metal (strength / sound /
  map-color / push-reaction / random-ticks / occlusion / no-tool-requirement) — nothing lamp-related is
  hand-authored, and it tracks any future TFC change. The **only** override is `lightLevel(lit ? 10 : 0)`
  (soul-lantern parity), plus the burn-out revert. The item is TFC's `LampBlockItem` (carries fuel NBT). **No
  `validBlocks` mixin needed** — verified that 1.20.1
  Forge's BE place/load/tick paths (`setBlockEntity`/`promotePendingBlockEntity`/`BlockEntityType.create`) don't
  gate on `BlockEntityType.isValid`, so reusing TFC's `LAMP` type for our blocks just works.
- **Convert a finished lamp** (`#firmavanilla:soul_lamp_catalyst`, datapack-overridable, seeded `tfc:powder/sulfur`
  + `tfc:powder/native_copper`):
  - **Right-click** a normal TFC lamp holding a catalyst item →
    [`SoulLampInteraction`](../firmavanilla/src/main/java/com/firmavanilla/block/SoulLampInteraction.java)
    (Forge `RightClickBlock`) swaps it to the soul lamp, copying the block-entity NBT (fuel) and `LIT`/`HANGING`,
    consuming one item (free in creative). Holding a powder does nothing in TFC's own lamp `use()`, so this is
    safe to intercept.
  - **Crafting**: shapeless lamp + catalyst → soul lamp (empty; the right-click path is the fuel-preserving one).
- **Fuel validity + the 2× burn.** A TFC `LampFuel` matches by `(fluid, valid_lamps block)`, and
  `LampBlockEntity.getFuel()` returns null otherwise — so a lamp no fuel covers has fuel in its tank but reads as
  empty and **can't be lit/filled**. Its `burn_rate` is **ticks-per-mB** (`checkHasRanOut` drains
  `ticksSinceUpdate / burn_rate`), so a **higher** rate burns **slower / longer**. Soul lamps deliberately **burn
  fuel 2× longer** than TFC lamps. Rather than join TFC's `#tfc:lamps` block tag (whose only functional use is
  TFC's normal-rate `olive_oil`/`tallow` fuels — adding the soul lamps there would make those *also* match, and
  which fuel `getFuel()` returns first is load-order-dependent), the soul lamps get their **own**
  `firmavanilla:soul_lamps` block tag + **dedicated fuels at double rate**: `soul_olive_oil` (burn_rate 16000, TFC
  8000) and `soul_tallow` (3600, TFC 1800), both `valid_lamps` = `#firmavanilla:soul_lamps`, plus `soul_lava`
  (`-1` infinite, restricted to the blue_steel soul lamp like TFC). For a soul lamp only these match (TFC's fuels
  need `#tfc:lamps`, which the soul lamps aren't in), so it's deterministic and they still fill/light. (Safe to
  skip `#tfc:lamps`: TFC has no LAMPS block-tag *code* constant, and the lamp item-size keys off a separate ITEM
  tag.) Tune the multiplier via `SOUL_BURN_MULT` in `soullamps.cs`.
- **Burns back to normal.** TFC's `LampBlockEntity#checkHasRanOut` flips `LIT` false on empty — and it runs from
  **both** `randomTick` *and* `use()` (a right-click re-checks fuel), so `SoulLampBlock` wraps **both** and reverts
  to the matching normal lamp on the lit→unlit transition. Key guard: only revert when the **tank is empty** (a
  real burn-out) — a manual shift-toggle-*off* leaves fuel in the lamp and must stay soul. A never-lit lamp, or a
  lava lamp that never empties, is left alone. (A burn-out that happens while the chunk is unloaded — via
  `fluidTankChanged` on reload — isn't caught by these two hooks; that residual edge would need a
  `checkHasRanOut` mixin, deliberately not added.) The normal↔soul block maps are resolved at `FMLCommonSetup`.
  Light **10** while lit.
- **Melting** mirrors TFC's lamp heating recipes exactly — `recipes/heating/soul_lamp/<metal>.json` melts to the
  same fluid/temperature, amount 100 (note wrought-iron → `cast_iron`, like TFC). **Crucially it also needs an
  `item_heat`** (`data/firmavanilla/tfc/item_heats/soul_lamp/<metal>.json`, `heat_capacity` 2.857 + per-metal
  forging/welding temps, mirroring TFC) — without it the item can't gain temperature, the melt never fires, and
  TFC's `SelfTests` errors ("ingredient to a heating recipe without a heat definition").
- **Item name/icon**: TFC's `LampBlockItem` appends `.filled` to the description id when fuelled, so soul lamps
  ship `block.firmavanilla.soul_lamp.<metal>.filled` lang keys too (else a filled lamp shows the raw key). The
  item is flat `item/generated` (a 3D block-model item renders blank in the GUI, which TFC sidesteps the same
  way); the icon is TFC's lamp item texture with a hand-extracted soul-glass overlay (`soul_lantern_item_overlay.png`,
  a tracked 16×16 in the tool root) composited over it — one overlay serves all metals, the metal body keeps
  each metal's colour.
- **Block glass (the teal)**: blockstates/models reuse TFC's `block/lamp` + `lamp_hanging` geometry, binding
  `#metal` to each metal's `tfc:block/metal/smooth/<metal>` and `#lamp` to a shared **soul** glass pair —
  `generate.cs` recolours TFC's own `lamp`/`lamp_off` glass via **CLUT through vanilla `soul_fire_0`'s palette**
  (so the teal carries soul-fire's real tones, not a flat hue shift; the lit glass keeps its 3-frame `.mcmeta`,
  the off glass is darkened since CLUT normalises each input's range). One glass pair serves all 9 metals. Loot
  drops self with `tfc:copy_fluid` (keeps fuel on break, like TFC); `minecraft:mineable/pickaxe`.

## Soul torches — vanilla soul torch + TFC burn-out

A vanilla-**soul-torch** look (teal soul-fire flame, vanilla models/textures) given **TFC's burn-out mechanic** —
a standing `firmavanilla:soul_torch` + wall `firmavanilla:soul_wall_torch` that burn down over time into TFC's
**dead torch**, but last **twice as long** as a normal TFC torch. Registered by
[`SoulTorches`](../firmavanilla/src/main/java/com/firmavanilla/block/SoulTorches.java); **mixin-free**.

- **Behaviour is TFC's, inherited.** [`SoulTorchBlock`](../firmavanilla/src/main/java/com/firmavanilla/block/SoulTorchBlock.java)
  `extends` TFC's `TFCTorchBlock` and [`SoulWallTorchBlock`](../firmavanilla/src/main/java/com/firmavanilla/block/SoulWallTorchBlock.java)
  its `TFCWallTorchBlock` — so they reuse the **whole** burn-out machinery: a `TFCBlockEntities.TICK_COUNTER`
  block-entity times the burn (reused like the soul lamps reuse TFC's `LAMP` BE), `setPlacedBy` resets it on
  placement, and the block converts itself to a dead torch. `SoulTorches#props` copies the real `tfc:torch` /
  `tfc:wall_torch` block's properties (instabreak / no-collision / **random-ticks** / sound / push-reaction — so the
  burn-out tick fires and it breaks like a torch), wires back `TICK_COUNTER`, and sets light **10** (vanilla
  soul-torch parity). `TFCTorchBlock`'s constructor takes a `ParticleOptions`, so the soul flame is just
  `ParticleTypes.SOUL_FIRE_FLAME` handed up — **no client code, no generated textures** (it reuses the vanilla
  soul-torch sprite). The generated **block models** are our own (vanilla torch geometry + that sprite) carrying
  Forge's `"render_type": "minecraft:cutout"`: vanilla registers the torch cutout layer in *code* for its own
  blocks only, so reusing vanilla's `block/soul_torch` model directly would render our torch's transparent pixels
  as **opaque black** — the same render-layer gotcha as the prismarine deposits.
- **The 2× burn — the only override.** TFC's torch burns out when `TickCounterBlockEntity.getTicksSinceUpdate()`
  exceeds the `torchTicks` **server config**; that's not data-driven, so the duration needs code. Each soul torch
  overrides `randomTick` (not calling super, which would burn at 1×) to run the shared `SoulTorches#tryBurnOut`,
  which fires at `BURN_MULT × torchTicks` (×2) and swaps to TFC's `dead_torch` (standing, default state) /
  `dead_wall_torch` (wall, **facing copied** via `withPropertiesOf`). `torchTicks ≤ 0` disables burn-out (matching
  TFC). The dead-torch blocks are resolved from the registry at common setup.
- **Made by converting a lit TFC torch** (reuses the soul lamps' `firmavanilla:soul_lamp_catalyst` tag — *not* a
  new tag):
  - **Right-click** a placed TFC torch (standing or wall) holding a catalyst →
    [`SoulTorchInteraction`](../firmavanilla/src/main/java/com/firmavanilla/block/SoulTorchInteraction.java)
    (Forge `RightClickBlock`) swaps it to the soul torch (`withPropertiesOf` carries the wall facing) and
    `TickCounterBlockEntity.reset`s the timer for a fresh doubled life, consuming one catalyst (free in creative).
    Safe to intercept: TFC's torch `use()` only reacts to `#tfc:CAN_BE_LIT_ON_TORCH` items (lighting another torch),
    not the powder catalyst, and the event is cancelled.
  - **Crafting**: shapeless `tfc:torch` + a catalyst → soul torch (empty/fresh; the right-click path is the
    in-place one).
- **Standing + wall share one item** (`StandingAndWallBlockItem`, like vanilla soul torch — floor places standing,
  walls place the wall form); both blocks drop the standing item. In the creative tab.
- **Known cosmetic limitation (Jade).** TFC registers its torch burn-out tooltip against `TFCTorchBlock` /
  `TFCWallTorchBlock` (our superclasses) with the **1×** `torchTicks` config, so Jade shows soul torches a burn-out
  estimate at 1× (~half the real time) — the actual burn-out is correctly 2× (it's our `randomTick`, not TFC's, that
  fires). Left as-is by choice: correcting it would mean either decoupling from `TFCTorchBlock` (losing the clean
  reuse) or a Jade plugin that deletes TFC's line by id (coupling to TFC+Jade internals). The HUD number is the only
  thing wrong; behaviour is right.

## Raw quartz column — quartz pillar with TFC raw-rock drops

A vanilla-`quartz_pillar`-style directional block (`firmavanilla:raw_quartz_column`,
[`QuartzBlocks`](../firmavanilla/src/main/java/com/firmavanilla/block/QuartzBlocks.java)) that drops like TFC raw
stone. **Nothing re-implements the isolation logic** — it's a plain `RotatedPillarBlock`; the behaviour is two
pieces of data:

- **Loot table**: a `minecraft:alternatives` — drop the block itself when `tfc:is_isolated`, else **1–4 nether
  quartz** (`minecraft:quartz`, `set_count` 1–4) — mirroring TFC's raw-rock loot exactly.
- **Tag**: joins `tfc:breaks_when_isolated` (merged into TFC's). TFC's `WorldTracker`/`ForgeEventHandler` watch
  neighbour updates for tagged blocks and, when one becomes isolated, pop it off supplying the `ISOLATED` loot
  param — which is what `tfc:is_isolated` tests (`LootContext.hasParam(TFCLoot.ISOLATED)`). So mine it connected →
  quartz; leave it isolated → it drops as a block.

Visuals reuse vanilla's `cube_column` / `cube_column_horizontal` with the two hand-made textures (`quartz.png`
side, `quartz_top.png` end, tracked tool-root inputs copied verbatim by the generator); blockstate is the standard
3-axis pillar. `requiresCorrectToolForDrops` + `minecraft:mineable/pickaxe` (props copied from `quartz_pillar`).

> **Note:** the raw quartz column is **creative-only** — it is no longer generated (the cave feature places the
> self-shaping `quartz_cluster` block below instead) and has no recipe. It stays registered (and in the
> `#firmavanilla:quartz_cluster_connectable` tag, so clusters plug into player-placed columns).

## Quartz cluster — self-shaping cave crystal

The block the cave feature actually places: `firmavanilla:quartz_cluster`
([`QuartzClusterBlock`](../firmavanilla/src/main/java/com/firmavanilla/block/QuartzClusterBlock.java)) — a
**connected, self-shaping block** with the six directional boolean properties (the vanilla `PipeBlock` property
set, like a wall / a modded pipe, but driving a richer shape). Full cubes read as blocky lumps in caves.

- **Shape = the union of a half-slab per connected side.** Each side it connects to fills the half of the cube
  adjacent to that side, and that one rule yields the whole stair/slab family:
  - **1 side → slab / vertical slab** (one half-slab),
  - **2 _adjacent_ sides → stair** (two perpendicular halves, an L cross-section = 3/4 cube),
  - **3 adjacent sides → corner stair** (7/8, one corner missing),
  - **2 _opposite_ sides → full block** (the two halves fill the cube; grain runs along that axis),
  - **0 sides → also a full block**, rendered as the original quartz pillar (`raw_quartz_column`'s `cube_column`
    model, y-axis grain) via an all-sides-`false` multipart case,
  - and every remaining combination (4/5/6 sides, opposite pairs, …) falls out automatically.

  The blockstate is multipart (one half-slab model per `true` side); the collision/outline `VoxelShape` is the
  matching union, precomputed for all 64 combinations in the block class.
- **Axis grain** — it carries an `AXIS` property like the vanilla quartz pillar (set from the clicked face on
  placement, from the vein's primary direction in worldgen), orienting the quartz grain (which faces show the
  `#end` quartz-top cap) **independently of the shape**. So each connected side picks a per-axis model variant
  (6 sides × 3 axes = 18 half-slab models), and the full-block cases render as the `raw_quartz_column` pillar
  oriented for that axis. Axis does **not** affect the collision shape (only the connections do).
- **Connection rule** (`connects`): connects toward a neighbour that is either (a) another quartz block — anything
  in `#firmavanilla:quartz_cluster_connectable` (this block + `raw_quartz_column`) — or (b) a **solid sturdy face**
  (the cave wall/floor/ceiling it plugs into). Recomputed dynamically via `getStateForPlacement`/`updateShape`, so
  it also looks right when player-placed or when a neighbour changes.
- **Waterloggable** (`SimpleWaterloggedBlock`) — placed in water (or grown into a flooded cave by the worldgen
  feature) it keeps the water around its open shape.
- **TFC raw-rock behaviour, matching the column** — it joins `tfc:breaks_when_isolated` and uses a
  `tfc:is_isolated` loot table: mined **with support** → **1–4 nether quartz** (same as the column;
  `requiresCorrectToolForDrops` gates the quartz on the tool); left **unsupported (isolated)** → it pops off and
  **drops a raw quartz column** (the full pillar an isolated cluster visually becomes).
- **Model:** 18 half-slab element-models (six sides × three axes — the `#end` quartz-top cap follows `AXIS`,
  reusing the `raw_quartz_column` side/top textures) + an inventory model (a representative stair) for the creative
  icon; `noOcclusion` + amethyst-cluster sound. All generated by the tool.

### Quartz cave clusters (worldgen)

So quartz is **found in the world**, not just crafted, a custom cave-decoration feature
(`firmavanilla:quartz_cluster`,
[`QuartzClusterFeature`](../firmavanilla/src/main/java/com/firmavanilla/worldgen/QuartzClusterFeature.java))
fills the caves of **volcanoes** with `quartz_cluster` veins — the mod's **second** worldgen feature (after the
prismarine deposits). It is modelled on how TFC's own `cave_column`/`cave_spike` decorate caves, and is
**block-only** — the earlier worldgen experiment that broke chunk generation did so by spawning *entities*, which
this never does.

**Placement — gated to volcanoes, in existing caves, never carves rock.** The placed feature chains three
modifiers:
- `tfc:carving_mask` (`step: air`, `min_y {above_bottom: 8}`, **`max_y {absolute: 48}`**) — origins are carved-cave
  air in deeper caves; the feature only ever `setBlock`s air → quartz.
- `minecraft:rarity_filter` `chance: 10` — density *within* a volcano (how many carved positions seed a vein).
- **`tfc:volcano`** — the concentrator. This TFC modifier passes only inside a volcano footprint (TFC's volcanic
  biomes — `volcanic_mountains`/`volcanic_oceanic_mountains`/`canyons`/…, and only the sparse volcano cells within
  them). It's **(x,z)-only**, so it works underground (no `heightmap` modifier — that would force it to the
  surface). Volcanoes are rare and regional, and their rock is the igneous-extrusive set whose felsic members
  (**rhyolite/dacite**) are in the host tag — so this makes quartz caves a rare, concentrated, lore-fitting find
  **without** any big-pocket / cross-chunk / deterministic mechanism (the proven small-reach per-position veins
  never clip).

It's added (merge, `replace:false`) to TFC's universal `in_biome/underground_decoration` placed-feature tag; the
`tfc:volcano` modifier is what restricts it from "every cave" to "volcano caves."

**Shape — veins growing out of cave surfaces.** Per carved origin (that passes the volcano + rarity gates), the
feature proceeds only if the spot is **against a surface** with an adjacent **host-rock** face (the "near a block"
anchor + strata gate); the vein direction is built **purely from the open faces** — one face per axis, 1–3 axes,
sign uniform (unbiased) — so every component points into open cave, never into the wall, and the vein can be
**straight, diagonal or vertical**, running a random length up to `max_reach` (10) or until it hits a wall. Each
block is then **finalised** (`withConnections`) so it grows its core+arms toward its neighbours and the host wall.
Because `place()` runs per carved position, a volcano's caves fill with crisscrossing quartz veins.

"Open" (passable for a vein) is **air or water** — so veins grow through **flooded** cave sections too, placing a
**waterlogged** cluster at each water-filled spot (`waterlog`). **Lava is not open**, so veins stop at lava and it
is never replaced.

**Why diagonals are walked as staircases.** A diagonal vein is laid one axis-step at a time (a *face-connected*
staircase) so consecutive cluster blocks always share a face. That matters because the connected block only
connects toward a **face** neighbour — a naïve corner-to-corner diagonal line wouldn't connect, leaving a row of
disjoint blocks. The staircase guarantees the vein reads as one continuous strand. It also reads well with the
half-slab shape rule: a bend (each block connecting to a prev/next in *different* axes) becomes a **stair**, a
straight run (opposite connections) becomes **full blocks**, and the vein's ends (one connection) become **slabs**
butting against the wall they hit.

**Strata gate (the "fitting rock" requirement).** There is **no data-only placement modifier that gates on rock
type** for cave features (TFC gates rock only via per-rock state lists in *surface* soil-discs), so the feature
itself checks it: a spike grows only where an **adjacent** solid block (the surface it anchors to) is in the
`#firmavanilla:quartz_cluster_host` block tag — **quartz-bearing rock**: raw + hardened
`quartzite`/`rhyolite`/`granite`/`dacite`/`gneiss`/`chert`. The tag is datapack-overridable to widen/narrow the set.

All worldgen JSON (configured/placed feature), the host tag, and the `underground_decoration` merge are
machine-generated in the raw-quartz-column section of `generate.cs`. The feature's tunables (`tfc:volcano`
`distance` for how much of each volcano fills, `rarity_filter chance` for in-volcano density, `max_reach` for vein
length) and the rock set live in data, so they can be retuned without touching Java.

## Quartz brick + the vanilla-quartz chisel chain

A single new item — `firmavanilla:quartz_brick` (registered in
[`QuartzBlocks`](../firmavanilla/src/main/java/com/firmavanilla/block/QuartzBlocks.java)) — makes the **whole
vanilla quartz block family reachable under TFC**, since the nether quartz it starts from is now obtainable (mine
the quartz clusters / raw column). The item's texture is the vanilla **brick item icon recoloured through the
vanilla quartz item icon's palette** (CLUT — `ClutSide(brick, quartz)`); inputs are `input/vanilla/brick.png` +
`input/vanilla/quartz.png`.

The progression is two chisel chains (every block→block step ships **both** a TFC in-world chisel — `tfc:chisel`,
`mode: smooth` — and a table craft with a chisel — `tfc:chisels`, tool damaged not consumed — like the rest of the
mod). All recipes are machine-generated:

- **Bricks:** `minecraft:quartz` —chisel→ **`firmavanilla:quartz_brick`** —(TFC's brick recipe: the
  `XYX/YXY/XYX` checkerboard, 5 bricks + 4 `tfc:mortar` → **2**)→ `minecraft:quartz_bricks` —chisel→
  `minecraft:chiseled_quartz_block`.
- **Column:** `firmavanilla:raw_quartz_column` —chisel→ `minecraft:smooth_quartz` —chisel→ `minecraft:quartz_block`
  —chisel→ `minecraft:quartz_pillar`.

**Vanilla recipes disabled.** So the TFC chain is the *only* route, every vanilla recipe that produces one of the
five chain blocks is removed — its `data/minecraft/recipes/*.json` is overridden with a `forge:false`-conditioned
copy (Forge skips it before parsing). That's 8 files: `quartz_block`, `smooth_quartz`, `quartz_pillar` (+ its
stonecutting), `chiseled_quartz_block` (+ stonecutting), `quartz_bricks` (+ stonecutting). Quartz **slabs/stairs**
are left enabled — they still derive from the now-chain-gated `quartz_block`, so nothing is orphaned.

No new blocks — `quartz_bricks`/`chiseled_quartz_block`/`smooth_quartz`/`quartz_block`/`quartz_pillar` are all
vanilla (models/blockstates already exist); only the `quartz_brick` item + the recipes are added. The
`quartz_bricks` craft mirrors TFC's own brick recipe exactly (the `XYX/YXY/XYX` checkerboard, 5 bricks + 4 mortar →
2 blocks); the other quantities (1 quartz → 1 brick) live in `generate.cs` and are easy to retune.

## Coarse dirt — per TFC soil, non-transforming

Vanilla coarse dirt's gravelly look in TFC's four soil palettes: `firmavanilla:coarse_dirt/<soil>` for
`loam`/`sandy_loam`/`silt`/`silty_loam`
([`CoarseDirtBlocks`](../firmavanilla/src/main/java/com/firmavanilla/block/CoarseDirtBlocks.java)).

**Texture** (machine-generated): an **overlay** is derived as `coarse_dirt − dirt` (the scattered pebble detail
where vanilla coarse_dirt differs from plain dirt — rgb = coarse colour, alpha = the difference ×
`COARSE_OVERLAY_GAIN`), saved to the tool root (`coarse_dirt_overlay.png`, tracked next to the grain masks/LUTs).
That overlay is CLUT'd through a TFC **gravel** palette (so the pebbles read as gravel) and source-over'd onto each
soil's TFC dirt, so every soil keeps its own colour and just gains gravel flecks.

**Behaviour — dirt-like but no transforms.** TFC gates soil transformations on the **block type**, not a tag:
grass spreads only onto an `IDirtBlock`, and shovel→path / hoe→farmland live in TFC's `DirtBlock`. So these are a
plain `Block` (not `DirtBlock`, not `IDirtBlock`) with TFC dirt's **properties** (`MapColor.DIRT`, strength 1.4,
gravel sound) and **tags** — added to `tfc:dirt` (which cascades into `minecraft:dirt`/sniffer/`tfc:can_carve` via
their `#tfc:dirt` reference) plus `minecraft:mineable/shovel` and `tfc:can_landslide`. The result behaves like dirt
(mined with a shovel, collapses like TFC soil) yet **never** turns into grass, path or farmland — exactly like
vanilla coarse dirt.

> **Landslide needs a recipe, not just the tag.** `tfc:can_landslide` only *enqueues* a collapse check; TFC's
> `tryLandslide` does nothing without a matching **`tfc:landslide` recipe**. So each soil ships
> `data/firmavanilla/recipes/landslide/coarse_dirt_<soil>.json` collapsing the block **into itself** (it stays
> coarse dirt, like TFC plain dirt landslides to itself).

**Recipe:** vanilla coarse-dirt's 2×2 checkerboard, but with the concrete matching `tfc:dirt/<soil>` and the
`#forge:gravel` tag for the gravel slot (so any TFC rock gravel — or our prismarine deposits — works) → 4.

> The shared `minecraft:mineable/shovel` and `tfc:can_landslide` tags are written once in `generate.cs` from lists
> the prismarine-deposit and coarse-dirt sections both append to (a mod can ship only one file per tag path).

## Block of beeswax — honeycomb motif in FirmaLife wax tones

`firmavanilla:beeswax_block` — vanilla's **honeycomb-block** comb motif recoloured to **FirmaLife beeswax**'s
warm-tan palette, the beeswax analogue of vanilla's 4-honeycomb → honeycomb-block. A plain decorative full cube
(`Properties.copy(Blocks.HONEYCOMB_BLOCK)` — honeycomb sound/hardness, no tool needed, `cube_all`, drops self).
See [`WaxBlocks`](../firmavanilla/src/main/java/com/firmavanilla/block/WaxBlocks.java).

**Texture** (machine-generated, [beeswax.cs](../firmavanilla/tools/generate-textures/beeswax.cs)): the standard
luminance-normalized **CLUT** — vanilla `honeycomb_block`'s exact pixels repainted through a palette ramp built
from FirmaLife's `beeswax` item (`ClutSide(honeycomb, beeswax, 16, 16)`). So the comb relief stays crisp and the
colour is authentically beeswax. Inputs `input/vanilla/honeycomb_block.png` + `input/firmalife/beeswax.png` are
git-ignored like the rest; the committed PNG is what ships.

**No FirmaLife dependency.** firmavanilla stays TFC-only: the block is registered unconditionally and the texture
ships as a committed derivative. The **only** FirmaLife touch is the recipe — `BB`/`BB` of **4
`firmalife:beeswax`** → 1 block (mirroring 4-honeycomb → honeycomb-block) — wrapped in a `forge:mod_loaded`
condition (`firmalife`), so it simply doesn't load when FirmaLife is absent (the block is then craft-less, like
any other content gated on an optional mod).

**`:compat` substitution:** `minecraft:honeycomb_block → firmavanilla:beeswax_block`, in the **FirmaLife-gated**
datapack (`firmalife.json`, via `gen_tfc_substitutions.sh`) — placed there rather than the base pack because the
block is only craftable with FirmaLife present, so there's no point mapping to an uncraftable block in a
FirmaLife-less world.

## Signal campfires — campfires that don't cook and burn out

`firmavanilla:signal_campfire` and `firmavanilla:soul_signal_campfire` — they look like vanilla (soul) campfires
but **can't cook** and **burn out like a TFC torch** (4× a torch's duration), extinguishing to their unlit state.
One block class, [`SignalCampfireBlock`](../firmavanilla/src/main/java/com/firmavanilla/block/SignalCampfireBlock.java),
serves both ([`SignalCampfires`](../firmavanilla/src/main/java/com/firmavanilla/block/SignalCampfires.java) registers
the pair); standalone (TFC-only) and **mixin-free**.

It **extends vanilla `CampfireBlock`** (TFC ships no campfire — only a firepit — so vanilla's is free to subclass),
keeping the look, light, fire damage, facing/lit/signal-fire/waterlogged states, douse-with-water and flint-&-steel
relight, with three surgical changes:

- **No cooking.** Vanilla `use()` only places food to cook, so it's overridden to `PASS`; and `newBlockEntity`
  returns our own [`SignalCampfireBlockEntity`](../firmavanilla/src/main/java/com/firmavanilla/block/SignalCampfireBlockEntity.java)
  instead of the cooking `CampfireBlockEntity` — so there's no cook logic at all.
- **Burns out + smokes via its BE.** `SignalCampfireBlockEntity` **extends** TFC's `TickCounterBlockEntity` (keeping
  the **calendar-based** timer — ages even while the chunk is unloaded, like a torch) but is registered as our **own**
  BE type with both campfires as valid blocks. `getTicker` wires its `serverTick` and `clientTick` (the rising cozy /
  tall "signal" smoke column). Once the counter passes the block's burn multiplier × TFC's `torchTicks` — **normal 1×
  (like a TFC torch), soul 2× (like a soul torch)**; `torchTicks ≤ 0` disables — `serverTick` **burns it out into the
  normal (unlit) signal campfire** (a soul campfire *degrades to a plain one*, like a TFC torch → dead torch, carrying
  over facing/waterlogged/signal-fire), plays the fizzle, and relighting then gives the normal flame. The smoke is a **client BE tick** — so it shows at full render distance like vanilla
  campfire smoke; the block's inherited `animateTick` still adds the crackle + lava sparks.
  - *Why our own BE type, not TFC's `TICK_COUNTER` directly:* the smoke needs the **client** to tick the BE, and the
    client only creates/ticks a BE for a block in that BE type's valid-blocks. Reusing `TICK_COUNTER` (whose
    valid-blocks don't include our campfires) ticks fine on the server but the client drops it — no smoke. (The soul
    lamps/torches reuse `TICK_COUNTER` happily because their visuals don't need a client BE tick.)
- **Placed unlit; (re)lightable.** `getStateForPlacement` forces `LIT=false`, so signal campfires are placed **unlit**
  (vanilla campfires place lit) — you light them with flint & steel, which works because the blocks join
  **`#minecraft:campfires`** (vanilla's `CampfireBlock.canLight` gates on that tag). The burn timer (re)starts whenever
  it's lit — reset on placement-while-lit (`setPlacedBy`) and on relight (`onPlace`, `LIT` false→true) — so each light
  gets a fresh life.

**Assets** ([signalcampfire.cs](../firmavanilla/tools/generate-textures/signalcampfire.cs)) are **JSON-only — no
texture generation**: the models reuse vanilla campfire art — the LIT model parents `minecraft:block/campfire`
(soul: `…/soul_campfire`) with `render_type: cutout` (vanilla registers the campfire cutout layer in *code* for its
own blocks only, so the fire's transparent pixels would render opaque black otherwise — the same render-layer
gotcha as the soul torches / prismarine deposits); the unlit state reuses vanilla's opaque `campfire_off` directly.
Blockstate mirrors vanilla campfire (facing rotations × lit); drop-self loot; the **item icon reuses vanilla's flat
campfire sprite** (`item/generated`, `layer0: minecraft:item/campfire` — vanilla's campfire item is flat, not the 3D
block). It also writes the `#minecraft:campfires` block tag (for relight).

**Recipes.** The normal signal campfire is crafted in the vanilla campfire shape — **`tfc:straw` (fuel) over three
`#minecraft:logs`** (` S ` / `LLL`). The **soul** variant matches the soul torch: a **shapeless** craft of a normal
signal campfire + a `#firmavanilla:soul_lamp_catalyst` (the same catalyst tag the soul lamps/torches use), **and** an
in-world **right-click conversion** ([`SignalCampfireInteraction`](../firmavanilla/src/main/java/com/firmavanilla/block/SignalCampfireInteraction.java),
Forge bus): hold a catalyst, right-click a placed normal signal campfire → it becomes the soul one (carrying
facing/lit/etc., timer reset), consuming one catalyst.
