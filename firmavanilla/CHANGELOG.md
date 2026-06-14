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
- **Alabaster recipes — colouring matches TFC strictly.** The tile is chiselled from `tfc:alabaster/bricks`
  (in-world smooth chisel + table chisel-craft); the pillar is chiselled from the tile; shapes come off the tile
  via crafting (×8/×6/×6), chisel (stair/slab) and stonecutting (×1/×2/×1). **Dyeing** is TFC's sealed-barrel
  recipe exactly — uncoloured **tile or pillar** + 25 mB `tfc:<colour>_dye` → the coloured one (both are dyeable
  bases). Shapes are **not** dyed and have no uncoloured variant — their colour comes from crafting/chiselling a
  coloured tile, mirroring TFC's "only the base is dyed" rule.
