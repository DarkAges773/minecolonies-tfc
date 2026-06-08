# Changelog — MineColonies × TerraFirmaCraft (mctfc)

All notable changes to this mod are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/). Releases are published as `1.20.1-<version>`
(the Minecraft version prefixes the SemVer).

## [Unreleased]

### Added
- **Cemented cobble creative tab** — the mortared ("Cemented") cobble twins now live in a *MineColonies ×
  TerraFirmaCraft* creative tab. This also makes them selectable as the miner's *Fill Block* (and visible in
  MineColonies' other item pickers), which previously only listed blocks that appear in some creative tab.
- **Colonists avoid TFC heat sources** — TFC's contact-damaging heat blocks (firepit, grill, pot, charcoal
  forge, molten) are added to MineColonies' `dangerousblocks` tag, so citizen pathfinding routes around them
  the same way it avoids vanilla campfires and fire.
- **Miner lucky-ore drops are TFC ores** — the miner's "lucky block" finds (rolled while tunnelling through
  stone) now yield TFC rich ore nuggets instead of vanilla ore blocks, scaling with hut level by metal age:
  L1 copper → L2 bronze (tin/bismuth/zinc) → L3 silver/gold → L4 iron → L5 nickel. Gives the colony miner a
  real reason to exist in a TFC world (TFC ore is rare, so miners otherwise just dig stone).

## [0.1.0] - 2026-06-08
First release. Requires Palette Swap for MineColonies (`structurizereplacements`) `1.20.1-0.1.0`+.

### Added
- **TFC material builds** — datapack substitution rules re-skin colony blueprints into TFC stone, wood,
  sandstone, flower, metal and mud families, plus per-form candidate pools. Vanilla cobble maps to a
  registered non-falling ("mortared") twin so cobble builds survive TFC gravity.
- **Farming** — the farmer tills TFC soil, plants/harvests TFC crops, and fertilizes them with TFC nutrients,
  with a per-field Fruiting/Seeding harvest mode chosen in the field GUI.
- **Food** — decay-aware item stacking, colony-storage preservation (a configurable freshness trait),
  freshness-aware eating (skip rotten, FIFO tiebreaker), and a TFC-food nutrition bridge so citizens get
  proper saturation.
- **World survival** — active build areas are collapse-proof while building; the miner shaft uses the hut
  fill-block; citizens rest for TFC's *localized* rain; colony light sources don't burn out.
- **Decorative vanilla furnaces** (player GUI blocked) with TFC-flavored recipes so the blocks stay
  obtainable; the vanilla barrel matches `tfc:chest` size/restrictions.
- **Optional per-mod datapacks** — extra substitution rules auto-enabled only when Beneath, ArborFirmaCraft
  or FirmaLife are installed.

[Unreleased]: https://github.com/DarkAges773/minecolonies-tfc/compare/mctfc/v0.1.0...HEAD
[0.1.0]: https://github.com/DarkAges773/minecolonies-tfc/releases/tag/mctfc/v0.1.0
