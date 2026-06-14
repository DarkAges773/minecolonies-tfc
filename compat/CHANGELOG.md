# Changelog — MineColonies × TerraFirmaCraft (mctfc)

All notable changes to this mod are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/). Releases are published as `1.20.1-<version>`
(the Minecraft version prefixes the SemVer).

## [Unreleased]

### Changed
- **Dining hall stocks food to demand, not by the stackful** — the dining hall used to try to keep
  `maxStackSize × building level` of *every* menu dish (e.g. ~160 of each at level 5), so the Chef bulk-cooked far
  more perishable TFC food than the colony eats and it rotted in storage. It now targets a per-dish amount scaled
  to the number of colony citizens, clamped to a sensible floor/cap (config `diningHallStockPerCitizen` 0.5,
  `diningHallStockMin` 4, `diningHallStockMax` 16). Only the dining hall is affected.
- **The Waiter no longer hoards a stack of food** — when fetching food to serve, the Waiter pulled a full stack
  (64) into its own inventory, which (unlike colony storage) isn't covered by food preservation, so the surplus
  decayed in hand. It now carries `diningHallWorkerCarry` (default 16) per trip.

### Added
- **MineColonies foods become TFC foods** — MineColonies' ~60 cooked dishes (cheeses, breads, soups, stews,
  pies, pizzas, etc.) now carry full TFC food data: they **decay** like any TFC food, restore TFC hunger/water,
  and add the five TFC nutrients (grain/fruit/vegetables/protein/dairy) chosen to fit each dish — so colony
  meals count toward a citizen's TFC nutrition balance and integrate with the colony food-preservation, freshness
  stacking and rotten-skipping behaviours. Datapack-only (`data/mctfc/tfc/food_items/`); saturation is calibrated
  so each meal feeds citizens the same as before, and decay speed is a single knob in the generator.
- **Crafted food inherits its ingredients' freshness** — cooking a dish no longer refreshes its ingredients:
  a meal made from a half-spoiled ingredient now comes out correspondingly aged (TFC's `copy_oldest_food`
  rule). Colony **worker** crafting carries this over via a small bridge; **player** crafting picks it up from
  the TFC-style food recipes. So a colony can't launder rot by cooking it into a meal.
- **Colony crafters can make TFC dynamic foods (sandwiches) with real nutrition** — TFC's "dynamic" foods get
  their nutrition from the ingredients they're made with, computed when crafted. A colony crafter previously
  produced them empty (worker crafting skips the step that computes it), so a colony-made sandwich was
  nutritionally worthless. It now comes out with its proper, ingredient-based nutrition — matching a
  hand-crafted one. Works for any crafting-table dynamic food (TFC's or an add-on's), not just sandwiches.
  *(Salads and soups are made in TFC's bowl/pot devices, not a crafting table, so they're not covered yet.)*
- **Colony crafters reuse TFC knives across crafts** — recipes that wear a TFC knife (e.g. sandwiches) no longer
  stall the crafter after a single craft. MineColonies matched the knife by exact NBT, so once a craft damaged it
  the worker no longer recognised its own knife and quietly stopped. TFC knives are now matched
  damage-agnostically (driven by the `tfc:knives` tag, so every metal and add-on knife is covered).
- **Smelter melts & casts TFC metal** — the Smelter worker now runs TFC metallurgy in its furnaces: it melts
  TFC ore into metal and casts it (ingots in molds; iron becomes a raw bloom), driven by the furnace's own
  fuel/heat. The hut's **Ores** and **Fuel** lists are now populated with **TFC ores and TFC fuels** (instead
  of vanilla smeltables), and the worker honours them — uncheck an ore to stop smelting it, and the fuel
  allow-list governs which fuels it burns. It also **auto-restocks**: when the colony runs low on an enabled
  ore / allowed fuel / charcoal it requests more through the colony, with the low-water point exposed as an
  **Ore restock threshold** setting in the hut's Settings tab (default 10). A fresh Smeltery is also seeded with
  a default minimum-stock of 1 stack of ingot molds, so it keeps molds for casting without manual setup.
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
- **Smithing researches + blacksmith anvil work** — five new researches in the University's Technology tab,
  chained after the stock *Hitting Iron* research, each consuming a TFC anvil of its tier (bronze → wrought
  iron → steel → black steel → red/blue steel) and requiring a blacksmith's hut at level 1–5 respectively. Each tier unlocks the colony Blacksmith to produce every TFC
  anvil-smithed item of that tier — tool heads, sheets, double ingots/sheets, rods, shears, tuyeres, plated
  blocks and so on (not finished tools — those are head + stick crafts); stone- and copper-tier anvil work
  needs no research. Recipes cost exactly what the anvil work would (welding also consumes flux) and are
  picked up automatically from the loaded TFC anvil/welding recipes, including ones added by other mods or
  datapacks.

### Changed
- **Builder fill block defaults to TFC loam dirt** — a new builder's hut *Fill Block* setting (used to fill
  solid-placeholder schematic blocks) now defaults to TFC loam dirt instead of vanilla dirt, which is
  unobtainable in a TFC world. Existing huts keep their saved setting; any block can still be picked in the
  hut GUI.

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
