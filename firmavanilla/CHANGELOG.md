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
  onto TFC's matching cut-sandstone texture by the bundled .NET asset generator. Craftable from 2 TFC
  cut-sandstone slabs.
- **Decorative bookshelves** per wood — a full-block, enchanting-power bookshelf (the vanilla
  `minecraft:bookshelf` equivalent, which TFC/AFC/Beneath don't add; they only have the chiseled 6-slot one).
  Vanilla-style books on the sides (overlaid on each wood's frame) with planks on top/bottom. Covers TFC's 20
  woods always, plus AFC (10) and Beneath (crimson/warped) when those mods are present. Crafted from 6 lumber +
  3 books; drops 3 books (or itself with Silk Touch); carries the same tags as a vanilla bookshelf
  (`enchantment_power_provider`, `mineable/axe`, `forge:bookshelves`).
