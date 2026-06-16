# Changelog

All notable changes to **TFC Vanilla Building Blocks** (`firmavanilla`) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to a `<mcversion>-<MAJOR.MINOR.patch>` version scheme (the patch auto-rolls from commit count).

## [Unreleased]

### Added
- Initial split-out of decorative TFC building blocks into their own standalone mod (depends only on
  TerraFirmaCraft).
- Non-falling "cemented" cobblestone twins for every cobble block (vanilla + TFC + earlier-loaded mods),
  migrated from the MineColonies × TerraFirmaCraft mod. Craft with 4 mortar around a cobble, or right-click
  cobble while holding mortar to convert in place.
- **Chiseled sandstone** in all 7 TFC sand colours (black/brown/green/pink/red/white/yellow) — the form TFC
  doesn't ship. Each wears one of vanilla's two chiseled reliefs (creeper face or wither motif), recoloured
  onto TFC's matching cut-sandstone texture by the bundled .NET asset generator. Made the TFC way — cut sandstone
  + a chisel → chiseled sandstone, at a crafting table (`tfc:chisels`, tool damaged not consumed) or by
  chiselling the cut block in-world.
- **Decorative bookshelves** per wood — a full-block, enchanting-power bookshelf (the vanilla
  `minecraft:bookshelf` equivalent, which TFC/AFC/Beneath don't add; they only have the chiseled 6-slot one).
  Vanilla-style books on the sides (overlaid on each wood's frame) with planks on top/bottom. Covers TFC's 20
  woods always, plus AFC (10) and Beneath (crimson/warped) when those mods are present. Crafted from 6 lumber +
  3 books; drops 3 books (or itself with Silk Touch); carries the same tags as a vanilla bookshelf
  (`enchantment_power_provider`, `mineable/axe`, `forge:bookshelves`).
- **Rock tiles** (proof of concept) — deepslate-tiles-style blocks for each of TFC's 20 rock types, in **plain**
  (`firmavanilla:tiles/<rock>`) and **cracked** (`firmavanilla:cracked_tiles/<rock>`) variants. The texture is
  CLUT-generated (vanilla `deepslate_tiles` / `cracked_deepslate_tiles` tile pattern recoloured through each
  rock's TFC palette) with each rock's own bright mineral grain (high-pass of its smooth texture) overlaid on the
  faces. No blueprint substitution wiring yet.
- **Rock tile shapes** — stairs, slab and wall for each rock (`firmavanilla:tile_stairs/`, `tile_slab/`,
  `tile_wall/<rock>`), off the plain tiles, matching vanilla's deepslate-tile family; join the vanilla
  `stairs`/`slabs`/`walls` tags. Reuse the plain tile texture (no new images).
- **Recipes match TFC's rock-block paths** (firmavanilla depends on TFC). Plain tile: in-world chisel of TFC's
  `chiseled` rock (smooth mode) **or** a table craft of chiseled + a chisel (`tfc:chisels`, tool damaged not
  consumed). Cracked tile: a plain tile + a hammer (`tfc:hammers`), mirroring TFC cracked bricks. Shapes ship all
  three paths TFC gives its bricks: crafting (stairs ×8, slab ×6, wall ×6) + stonecutting (stairs ×1, slab ×2,
  wall ×1) off the tile, plus chisel stair/slab modes (walls have no TFC chisel mode).
- **Alabaster tile + pillar** in all 16 TFC dye colours, plus **uncoloured bases** (`firmavanilla:alabaster_tile`
  and `alabaster_pillar`) — vanilla's purpur block / pillar recoloured (CLUT) through each colour's
  `tfc:alabaster/bricks/<colour>` palette + composited with raw alabaster, a form TFC doesn't ship.
  `firmavanilla:alabaster_tile/<colour>` (full cube) and `alabaster_pillar/<colour>` (`RotatedPillarBlock`).
- **Alabaster tile shapes** — stairs, slab and wall off the alabaster tile, per colour
  (`firmavanilla:alabaster_tile_stairs/`, `alabaster_tile_slab/`, `alabaster_tile_wall/<colour>`); reuse the tile
  texture, join the vanilla `stairs`/`slabs`/`walls` tags. (Vanilla has no purpur wall, but the alabaster wall is
  provided anyway.)
- **Patina palettes** — reusable luminance→colour LUTs extracted from vanilla copper's three weathering stages
  (`exposed`/`weathered`/`oxidized`), written by the asset generator to
  `tools/generate-textures/patina_{stage}.png` (256×16 ramp strips). Feed a strip to the CLUT to patina-ify a
  block. Sampled straight from each stage (no subtraction, no `copper_block` needed).
- **Cut copper (plated) blocks** — a **cut** version of the plated block + its stairs and slab (vanilla cut-copper
  textures), the cut forms TFC doesn't ship. Cut from the plated block of the same weather stage via a **TFC saw**
  (`tfc:saws`) **and** a **stonecutter** (vanilla `minecraft:stonecutting`); stairs/slabs craft from the cut block
  (×8/×6). They oxidise / scrape / wax / melt exactly like the plated set. Unlike the TFC-bridged forms these are
  firmavanilla-only, so the bright stage has its own item (no TFC bridging).
- **Weathering copper** — six TFC copper forms given vanilla copper's **full lifecycle**: copper **bars**, the
  **plated block** + its **stairs** and **slab**, copper **chains**, and copper **trapdoors**. Each oxidises over time
  (`unaffected`→`exposed`→`weathered`→`oxidized`), scrapes back a stage with an axe, waxes with honeycomb (waxed twin
  for every stage), waxes off with an axe, and de-oxidises when struck by lightning. TFC's own item for each places
  it and it drops that item back, so existing TFC copper just starts aging — no new bright items, no conversions. The
  plated block/stairs/slab reuse vanilla plain copper-block textures; bars/chains/trapdoors are recoloured through the
  extracted patina LUTs. Mine with a pickaxe; each **melts** like its TFC source (bars 25 / block 100 / stairs 75 /
  slab 50 / chain 6 / trapdoor 200 mB `tfc:metal/copper` at 1080 °C). The plated stairs/slab are craftable from the
  plated block of the same weather stage (mirroring TFC's recipe, ×8/×6). (No mixin — by splicing into vanilla's
  `WeatheringCopper`/`HoneycombItem` maps.)
- **Alabaster recipes — colouring matches TFC strictly.** The tile is chiselled from `tfc:alabaster/bricks`
  (in-world smooth chisel + table chisel-craft); the pillar is chiselled from the tile; shapes come off the tile
  via crafting (×8/×6/×6), chisel (stair/slab) and stonecutting (×1/×2/×1). **Dyeing** is TFC's sealed-barrel
  recipe exactly — uncoloured **tile or pillar** + 25 mB `tfc:<colour>_dye` → the coloured one (both are dyeable
  bases). Shapes are **not** dyed and have no uncoloured variant — their colour comes from crafting/chiselling a
  coloured tile, mirroring TFC's "only the base is dyed" rule.
- **Prismarine deposits** — a TFC-style way to obtain the vanilla **prismarine family**, which TFC leaves intact
  but makes unreachable (no guardians/monuments → no shards or crystals). One panable/sluiceable gravel deposit
  block per TFC rock (`firmavanilla:deposit/prismarine/<rock>`) generates on the **deep-ocean** and
  **deep-ocean-trench** floor (a `tfc:soil_disc` swapping that rock's gravel, like TFC's own ore deposits). Mine
  it, then **pan or sluice** it for prismarine **shards** (common) and **crystals** (rare) plus loose-rock filler;
  the untouched vanilla recipes then build prismarine / bricks / dark prismarine / sea lantern. No mixin — the
  worldgen feature, the TFC panning/sluicing definitions and all loot are datapack-driven, and the (animated)
  crystal sheen is a single model overlay layered over each rock's gravel (no per-rock textures). The crystals
  **glow in the dark** (full-bright overlay element via Forge `forge_data`) without emitting any light. The deposit
  blocks carry the same tags as TFC's own deposits (`forge:gravel`, `mineable/shovel`, `tfc:can_landslide` so they
  collapse like gravel, `tfc:ore_deposits`). This is the mod's first worldgen feature.
- **Quartz brick + the vanilla-quartz chisel chain** — a new `firmavanilla:quartz_brick` item (the vanilla brick
  icon recoloured through the vanilla quartz icon's palette) that makes the **whole vanilla quartz block family
  reachable under TFC**. Chisel **nether quartz** → quartz brick; then **TFC's own brick recipe** (the
  `XYX/YXY/XYX` checkerboard — 5 bricks + 4 mortar → 2) makes `minecraft:quartz_bricks` → chisel →
  `minecraft:chiseled_quartz_block`. And the **raw quartz column** chisels
  down the other family: → `minecraft:smooth_quartz` → `minecraft:quartz_block` → `minecraft:quartz_pillar`. Every
  block step has both a TFC in-world chisel (smooth) and a table craft with a chisel (`tfc:chisels`, tool not
  consumed). The **vanilla recipes** for all five quartz blocks (and their stonecutting variants) are **disabled**
  so the TFC chain is the only route; quartz slabs/stairs stay craftable from the now-chain-gated `quartz_block`.
- **Quartz cluster** — a self-shaping connected block (`firmavanilla:quartz_cluster`, six directional sides like a
  wall / a modded pipe) whose shape is the **union of a half-slab per connected side**: one side → slab/vertical
  slab, two adjacent → stair, three adjacent → corner stair, two opposite (or none) → full block — the
  no-connection full block rendered as the original quartz pillar (every other combo falls out the same way). It
  connects to other quartz blocks (the `#firmavanilla:quartz_cluster_connectable` tag) and to any
  solid rock face, so it plugs into cave walls and chains into veins instead of looking like stacked cubes;
  connections (and the matching collision shape) recompute dynamically when placed or when a neighbour changes. It
  is **waterloggable** (keeps water around its open shape when placed in / grown into water). It
  also carries an **axis** like the vanilla quartz pillar (set from the clicked face / the vein direction) that
  orients the quartz grain independently of the shape. Shares the raw quartz column's TFC raw-rock behaviour
  (`tfc:breaks_when_isolated` + a `tfc:is_isolated` loot table): mined with support it drops **1–4 nether
  quartz** (same as the column), but left unsupported it pops off and drops a raw quartz column.
- **Quartz cave clusters** — a worldgen cave-decoration feature (`firmavanilla:quartz_cluster`) that fills the
  caves of **volcanoes** with **quartz cluster** veins (a "quartz cave" look), so quartz is found in the world, not
  just crafted. It places blocks **only into already-carved cave void** (never carves rock): TFC's
  `tfc:carving_mask` (`step: air`) supplies each position, and TFC's **`tfc:volcano`** modifier restricts it to
  volcano footprints (TFC's volcanic biomes) — a rare, regional, lore-fitting spot whose felsic rock
  (rhyolite/dacite) is quartz-bearing. Where a carved spot sits against a quartz-bearing rock surface it shoots a
  vein into the open cave — through air **or water** (flooded sections get waterlogged clusters; lava is left
  alone) — straight, diagonal or vertical, a random length, and the blocks self-shape into one crisscrossing
  thicket. So quartz caves are an occasional concentrated find (at volcanoes) rather than a thin
  layer in every cave. Also gated to **quartz-bearing rock** — a vein grows only from an adjacent
  quartzite/rhyolite/granite/dacite/gneiss/chert block (the datapack-overridable
  `#firmavanilla:quartz_cluster_host` tag) — and to **deeper caves** (a `max_y` depth bound). The mod's second
  worldgen feature; block-only (no entities).
- **Raw quartz column** — a vanilla-quartz-pillar-style directional block with TFC **raw-rock** drops: mined
  directly it drops **1–4 nether quartz**, but if it becomes **isolated** (no connected neighbours) it pops off as
  the block itself, exactly like TFC raw stone. Pure data — it joins TFC's `breaks_when_isolated` tag and uses a
  `tfc:is_isolated` loot table, so nothing re-implements the isolation logic. (Now **creative-only**: the cave
  feature places the self-shaping quartz cluster instead, and the column has no recipe — it stays registered and
  in the cluster's connect tag.)
- **Soul lamps** — a soul-lantern-style **teal** variant of every TFC metal lamp (all 9 metals). They reuse TFC's
  lamp block-entity, so they fuel / light / fill / break / **melt** exactly like a normal lamp (fuel kept on
  break; melts to the same metal at the same temperature), but glow teal and emit a **dimmer light (10**, like a
  vanilla soul lantern). **Convert a finished lamp**: right-click a
  normal TFC lamp holding any item in `#firmavanilla:soul_lamp_catalyst` (seeded with TFC **sulfur** + **native
  copper** powder, datapack-overridable) — preserving its lit state and fuel — or craft a lamp with a catalyst
  item. A soul lamp **burns back to a normal lamp when its fuel runs out** (a lava-fuelled one never empties, so
  it stays soul). The teal glass is a hue-shift of TFC's own lamp texture (animation preserved); no mixin (reuses
  TFC's lamp block-entity + a Forge right-click event).
