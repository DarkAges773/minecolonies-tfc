# Changelog — MineColonies × TerraFirmaCraft (mctfc)

All notable changes to this mod are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/). Releases are published as `1.20.1-<version>`
(the Minecraft version prefixes the SemVer).

## [Unreleased]

### Added
- **The Smelter now tends player-built TFC bloomeries for iron.** Take the new **Bloomery Wand** from the Smeltery's
  *Tools* tab and right-click your own TFC bloomeries to mark them (right-click again to unmark); the Smelter then loads
  them with iron ore + charcoal, lights them, and collects the finished **raw iron blooms** into the hut — which you
  finish into wrought iron on a TFC anvil, as usual. This is the iron counterpart to the heat forge's cast metals. The
  wand only marks **properly-built** bloomeries (it tells you if the structure is incomplete), and each Smeltery can tend
  a limited number that grows with its level (none at level 1, up to 3 at level 5 — configurable, and an empty
  `bloomeryCapPerLevel` turns the whole feature off). While you hold the wand, its bound Smeltery is outlined in red
  and every marked bloomery in yellow; the Smelter also warns you if a marked bloomery's structure later gets broken.
  The Smelter batches ore to whole blooms to keep waste low and auto-requests iron ore + charcoal while any bloomery
  is marked.
- **New "heat forge" block — a TFC-flavoured furnace for the worker huts.** Four cosmetic variants (**Brick / Rustic /
  Stone / Tile Furnace**) are craftable and show in the *Functional Blocks* creative tab; each looks like a blast
  furnace with an oven-material shell, glows and crackles with a lit front + light + smoke while burning, and is lit
  with **flint & steel**. Adjacent forges **merge into one multiblock** (up to 5) that shares a single 5-slot
  charcoal-forge fuel column and one temperature gauge, opened as one interactive window. Every position is a little TFC
  firepit×forge — a heat slot (any heatable item) plus output/overflow slots for molds/containers — and the block heats
  and processes **on its own**. It's usable on its own as a heat device, and it's what the furnace workers now run on.
- **The Chef (Kitchen) now cooks raw TFC food in its furnaces** (the *smelting* recipe tab). TFC food isn't
  vanilla-furnace-smeltable, so that tab used to be useless in a TFC world; now you teach it with TFC's own cooking
  (raw food → cooked food) — put a raw food in the teach window and it shows the cooked result to teach — and the Chef
  drives its furnaces with TFC heat, just like the dining-hall Cook. Unlike the Cook (which cooks proactively for the
  restaurant menu), the Chef cooks **on demand** to fulfil requests — e.g. cooked ingredients for its own dishes, or an
  order from another building. Food cooks one piece at a time (throughput via several furnaces at once), the food's
  freshness carries across, and a cook in progress survives a reload. Uses TFC **firepit** fuels (logs / peat / sticks).
- **The Chef (Kitchen) can now make TFC pot foods** (boiled egg, cooked rice, and any similar single-output,
  water-based pot recipe from TFC or its add-ons). These are added to the Chef's recipe list automatically — no
  teaching needed — so the colony can finally produce them; the food carries its ingredients' freshness. (Excluded: pot
  recipes that make a *fluid* like dyes, and recipes needing a non-water liquid — the colony can't stock fluids, so
  those are left out rather than handed over for free.)
- **The Chef (Kitchen) can now make TFC salads and pot soups.** These TFC dishes have no fixed recipe — their type and
  food value depend on the ingredients — so a new **Compose TFC Dish** button on the Chef's crafting-teaching tab opens
  a familiar crafting-style window (with your inventory) where you drop in 1–5 ingredients and a bowl, see the
  resulting dish, and **teach** it. The Chef then makes that exact dish like any other taught recipe. A dish you teach
  keeps the right nutrition and is always made fresh, no matter how long ago it was taught.
- **The dining hall Cook now cooks raw TFC food** in its furnaces. TFC food isn't vanilla-furnace-smeltable, so the
  Cook never used to cook it; now it heats raw TFC food into its cooked form using TFC's own cooking (so the food's
  freshness/decay carries across), for any dish you've put on the **restaurant menu**. The colony now also **orders
  the raw ingredients automatically** for the dishes on the menu (scaled to how much the colony actually eats, so
  perishable food isn't over-ordered) — you just set the menu. Food cooks **one piece at a time** like a TFC firepit,
  with throughput coming from running several furnaces at once. Serving meals to citizens is unchanged. Like the
  Smelter, a cook in progress survives a reload.
- **TFC built-in palette presets** — read-only presets in the *Presets* picker (under a **Built-in** folder),
  each re-palettes a whole build onto one material because picks are shared across buildings (so a colony forced
  onto the local material is re-styled in one click). Groups:
  - **Rock types** — one per TFC rock (Granite, Basalt, Slate, …, 20), swapping every default stone form
    (raw/cobble/bricks/smooth/mossy/cracked/chiseled/slabs/stairs/walls, plus magma for igneous rocks). Also covers
    **deepslate** builds — vanilla deepslate resolves to basalt (and its tile forms to firmavanilla's deepslate
    tiles), so basalt is a source and the firmavanilla tile forms are included.
  - **Planks** and **Logs** — one per wood, kept as separate groups. *Planks* covers the whole plank family —
    planks/stairs/slabs/fences/gates/doors/trapdoors/buttons/pressure-plates/signs **plus** chests, trapped chests,
    lecterns, scribing tables, bookshelves, item barrels and workbenches; *Logs* covers logs/wood/stripped. Both
    cover the base TFC woods, and — when **ArborFirmaCraft** or **Beneath** are installed — their woods too (via the
    same optional datapacks, so they appear only with the mod). Wood sources span all three namespaces, so a build
    is re-woned correctly even where AFC re-routes a vanilla wood (e.g. spruce → cypress).
  - **Dirt** — one per TFC soil type (Sandy Loam, Silt, Silty Loam), swapping dirt/coarse dirt/rooted dirt/grass/
    grass path.

  (Requires the Palette Presets feature in Palette Swap; the presets are read-only but can be cloned and tweaked.)

### Changed
- **The Smeltery, dining hall and Kitchen now run on the heat-forge, not vanilla furnaces.** Every furnace placed by a
  blueprint in a colony is automatically swapped to a heat-forge (it keeps its facing, and adjacent ones merge into one
  multiblock), and the workers now tend those forges — the Smelter melts ore into molds, the Cook and Chef heat TFC
  food. The forge self-heats and self-processes, so the worker just keeps it lit and fuelled, loads it, and hauls out
  the results (it warms up gradually and is kept warm briefly between jobs). Vanilla furnaces are no longer driven by
  the colony. *(Note: this swaps **every** blueprint furnace, including decorative ones in houses — they just become a
  player-usable heat forge with no worker.)*
- **The dining hall menu only offers TFC food now.** The dish picker previously listed every edible item (vanilla and
  modded foods that don't work in TFC's nutrition/spoilage system); it's now limited to **TFC-tracked food** (anything
  carrying TFC's food data — including modded foods a datapack has integrated). Any non-TFC dish already on a menu is
  removed on the next world load.
- **Furnace huts now restrict fuel to the matching TFC device's fuels.** The Smeltery (a charcoal forge) takes only
  **forge fuels** — the coals (coal, charcoal, bituminous coal, lignite) — and the Cook's dining hall (a firepit)
  takes only **firepit fuels** — woods (logs), peat, stick bundles, driftwood, etc. This applies both to the fuel
  list you configure in the hut and to what the worker will actually burn, so you can't, say, run a cook off coal or
  a smeltery off logs. It uses TFC's own `forge_fuel`/`firepit_fuel` tags, so datapacks and addons that add fuels
  flow through automatically.
- **Cobblestone and mossy cobblestone are no longer interchangeable in the Replace picker.** Both still substitute
  to their non-falling cemented twin, but the GUI re-pick pool is now split: plain cemented cobble offers only the
  other rocks' plain cobble, and mossy offers only mossy — you can't swap a wall between plain and mossy.

### Fixed
- **Furnace-worker forges no longer strand food/ore or burn fuel for nothing.** Three heat-forge tending bugs are
  fixed: (1) a forge whose fuel ran out mid-cook with items still inside is now relit on the worker's next visit
  instead of leaving the food/ore stuck cold forever; (2) the worker now restocks the forge's fuel column even when
  every heat slot is busy (previously a fully-loaded forge could starve its own fire and stall); and (3) a forge only
  stays lit while it has something it can actually finish — an item too hot to melt with the hut's fuel (e.g. iron ore
  dropped into a Kitchen furnace) no longer keeps the fire burning pointlessly. The Chef also no longer gets stuck on a
  request when such an unmeltable item is sitting in one of its furnaces.
- **The Chef now actually fulfils requests for salads and pot soups** (e.g. from a dining hall's menu). TFC's
  salad/soup items stash their bowl in the item's NBT, and MineColonies matches food requests by exact NBT, so a
  freshly-made salad never counted as "the salad that was requested" and the Chef ignored the order. Salads and soups
  are now matched by item, ignoring that per-bowl NBT (the same way worn TFC knives are matched damage-agnostically).
- **TFC food shown in the restaurant menu, the dish picker and food requests no longer appears rotten.** Those are
  *templates* (a reference to a dish), but were stored as real food stacks whose TFC freshness aged over time, so they
  looked spoiled. They're now stamped TFC's persistent "never decay" date, so they stay fresh everywhere — in the menu,
  the picker, and the raw-ingredient requests. Cosmetic only — it never affected what the cook serves or which food
  fulfills a request. (Existing menus clean up on next world load.)

### Added
- **Lumberjack teaches TFC log recipes, not vanilla ones.** Removed MineColonies' built-in vanilla-log lumberjack
  recipes (the `strip_logs`/`strip_stems` templates — dead clutter in a TFC world) and added the TFC equivalents:
  each TFC log (`tfc:wood/log/<wood>`) → its stripped log, wood, or stripped wood, mirroring the vanilla
  one-log-to-three-forms behavior. The bamboo stripping recipe is kept. The same TFC-style log recipes are added
  for **ArborFirmaCraft** and **Beneath** woods when those mods are present (via their optional datapacks).
- **Lumberjack fells whole TFC trees (TFC axe behavior).** TFC gives every axe a one-hit tree-felling behavior,
  but it only triggers for real players — so the colony forester used to climb each trunk log-by-log (slow, and apt
  to get stuck on tall trees). The forester now fells the whole connected trunk in one chop via TFC's own logging,
  dropping the logs (its gathering phase collects them) and wearing the axe per log, exactly like a player. Falls
  back to the normal single-log chop where TFC itself wouldn't fell (non-trunk logs, 2×2 trunks). The forester cuts
  the tree from the **base** block (not the top down), and the fell takes time proportional to the tree's size —
  the log count × the normal per-log chop time (which scales with axe tier / worker skill / research) — so big
  trees and worse axes take longer, rather than every tree dropping instantly. The worker swings its axe at the base
  throughout that time instead of standing idle.
- **Lumberjack replants TFC trees.** The Lumberjack already found and chopped TFC trees, but never replanted them:
  vanilla's replant check only sustains saplings on `BlockTags.DIRT`, which (under TFC) excludes TFC grass — where
  wild TFC trees actually grow — so the worker pulled the stump and planted nothing, depleting the forest. It now
  also replants wherever the sapling can genuinely survive (TFC grass/dirt/farmland).
- **Lumberjack-planted TFC saplings now grow on a timer, not instantly.** The worker plants via `setBlockAndUpdate`
  (no `setPlacedBy`), so a TFC sapling's growth counter was never started and it sprouted on its first random tick.
  We now reset the counter on placement, so a planted sapling waits its normal `daysToGrow` like a hand-placed one.
- **Coarse dirt / podzol / mycelium substitutions** — `minecraft:coarse_dirt` → firmavanilla's coarse dirt
  (default coarse loam, with a pick-any-soil pool: loam/sandy_loam/silt/silty_loam); `minecraft:podzol` and
  `minecraft:mycelium` → TFC rooted dirt (default loam, same soil-pick pool).
- **Campfires → signal campfires** — `minecraft:campfire`/`soul_campfire` in blueprints now substitute to
  firmavanilla's `signal_campfire`/`soul_signal_campfire` (campfire look, no cooking, torch-style burn-out), placed
  **lit** so colony builds light up as designed.
- **Magma block → TFC magma** — `minecraft:magma_block` now substitutes to `tfc:rock/magma/dacite` (closest look),
  with an any-magma re-pick pool covering TFC's 7 igneous magmas (andesite/basalt/dacite/diorite/gabbro/granite/rhyolite).
- **Honeycomb block → block of beeswax** (requires FirmaLife) — `minecraft:honeycomb_block` in blueprints now
  substitutes to `firmavanilla:beeswax_block` (the honeycomb motif in FirmaLife beeswax's tones, craftable from
  beeswax). FirmaLife-gated, since the block is only craftable when FirmaLife is present.
- **Beekeeper works a FirmaLife apiary** (requires FirmaLife) — base TFC has no beekeeping, so the Beekeeper was
  inert. The worker now registers FirmaLife hives with the scepter and **services** them: harvests **honey** into TFC
  empty jars (→ jars of honey), scrapes **beeswax** from frames you designate, and tops up empty frame slots — while
  FirmaLife's bees breed on their own. A new **Beeswax frames** hut setting (one row, a toggle per frame) picks which
  frames are scraped for wax (default none — scraping a frame kills its queen, so leave some for honey/breeding). Set
  up the apiary (frames, queens, flowers) and register the hives as you would by hand. Vanilla `beehive`/`bee_nest`
  blueprint blocks substitute to `firmalife:beehive`. The hut's now-irrelevant vanilla bits are hidden when FirmaLife
  is present: the honeycomb/honey/both harvest toggle, the flower-list tab, and the glass-bottle/shears/flower stock
  requests (the worker requests TFC empty jars / a knife / beehive frames instead).
- **Chicken Herder collects eggs from nest boxes** — TFC chickens lay into `tfc:nest_box` blocks instead of dropping
  eggs on the ground, so the vanilla Chicken Herder never gathered them. The worker now visits nest boxes in its hut
  and harvests the **food (non-fertilized) eggs**, leaving **fertilized** eggs in the box to hatch into chicks (that's
  how the flock grows). Place nest boxes in the hut; they're protected from the builder's air-clear.
- **Mixed-species pen halts the worker** — a herding worker now raises a blocking problem and **stops tending** when
  its pen holds more than one suitable TFC species (TFC breeds, familiarizes and reserves per species, so a mixed pen
  can't be tended properly). It resumes — and the warning clears itself — once the pen is down to a single species.
- **Shepherd shears TFC wooly animals** — the Shepherd now shears TFC sheep/alpaca/musk ox (not just vanilla sheep)
  when an animal is ready (familiarity + product cooldown), driving TFC's own shear path (`IForgeShearable`) so it
  yields **TFC wool** and fires TFC's product event — **FirmaLife/add-on wool variants** come out correctly. No
  vanilla colored-wool or sheep-dyeing for TFC animals. Vanilla sheep still shear the vanilla way.
- **Cowhand milks TFC dairy animals** — the Cowhand now milks TFC cows/goats/yaks (not just vanilla cows) by driving
  TFC's own milking: when an animal is ready (familiarity + product cooldown), the worker fills a TFC fluid container
  with the animal's milk, respecting TFC's rules and firing TFC's product event — so **FirmaLife's per-animal milk
  variants (goat/yak milk)** come out correctly. A vanilla bucket isn't used (it can't hold the variant fluids).
  Vanilla cows still milk the vanilla way.
- **Cowhand "Milk Item" setting offers TFC containers** — the hut's Milk Item setting (which used to pick between a
  vanilla milk bucket and a large milk bottle, neither able to hold TFC milk) now lists the **TFC fluid containers
  that can actually hold milk** — ceramic jug (default), wooden bucket, red/blue steel bucket. The Cowhand milks
  **only** into the chosen container: it requests that container (**as many as the Milking Amount setting**, since
  each milking uses up an empty one) and **pulls it from the hut's racks** into its own inventory before milking,
  rather than milking with whatever fluid item it happened to carry. If no empty selected container is available
  (worker or hut), it waits for delivery instead of milking the animal for nothing. Existing huts pick up the new
  options automatically.
- **Herding huts tend TFC livestock (recognition + breeding)** — the Cowhand, Shepherd, Swineherd, Chicken Herder and
  Rabbit Hutch now recognize TFC animals (cow/goat/yak, sheep/alpaca/musk ox, pig, chicken/duck/quail, rabbit) instead
  of only vanilla ones, driven by per-hut `#mctfc:herding/<job>` entity tags. Their worker raises them the TFC way,
  in two phases: it first **familiarizes** animals (feeding TFC grain to any hungry animal still below its familiarity
  cap), then **breeds** the familiar ones by feeding a fitting **pair** at once (a male + female, mate-ready,
  non-pregnant, hungry that day) so TFC's own husbandry mates them — rather than vanilla love-mode. No food is wasted:
  at-cap animals aren't re-fed, a breeding animal with no opposite-gender partner is skipped, and **rotten** grain
  only feeds animals that eat rotten food (e.g. pigs). They butcher animals for TFC meat. Culling is herd-aware: the worker **always butchers old** animals (they no longer breed
  or produce), and otherwise culls surplus down to a breeding reserve kept **per species and per gender**, scaling
  with hut level and **female-weighted** — females +1 per level, males +1 per two levels rounded up, each floored at
  1 (L1 1♂/1♀, L2 1♂/2♀, L3 2♂/3♀, L4 2♂/4♀, L5 3♂/5♀). Because the reserve is per species, a multi-species hut
  (cow + goat + yak, etc.) keeps a breeding pair of **each** species instead of culling a minority species out — it
  won't butcher the last female goat just because there are also cows. It trims the species+gender most over its
  reserve, picking old first then the least-familiar, harvesting the rest for meat (this replaces the vanilla
  `level × 2` herd cap). Animal **products**
  (milk, wool, eggs) are not yet wired — that's a follow-up. The Swineherd and Rabbit Hutch are fully functional;
  the other three breed and butcher.
- **Builder won't strip tagged blocks** — a new `#mctfc:builder_dont_clear` block tag protects listed world blocks from the
  MineColonies builder's CLEAR phase: when a blueprint has air where one of these blocks already sits, the builder leaves it in
  place instead of tearing it out. This is the air-strip path only — the builder still places real blueprint blocks normally, and
  deliberate building demolition is unaffected. The tag is empty by default (opt-in); add block ids in a datapack and `/reload`.
- **Vanilla soul torch → firmavanilla soul torch substitution** — `minecraft:soul_torch`/`soul_wall_torch` in a
  blueprint now substitute to `firmavanilla:soul_torch`/`soul_wall_torch` (vanilla-soul look + TFC burn-out), beside
  the existing `torch`→`tfc:torch` rules; the wall variant keeps its facing.
- **Vanilla barrel → firmavanilla wood barrel substitution** — placing a blueprint's `minecraft:barrel` now
  substitutes to `firmavanilla:barrel/oak` by default, with an any-wood re-pick pool (`mctfc:subst/wood/barrel`,
  all TFC woods; AFC/Beneath woods join via their conditional datapacks) — mirroring the decorative-bookshelf wiring.
  When **AFC** is present its datapack overrides the default to `firmavanilla:barrel/cypress` (priority 1, matching
  AFC's spruce→cypress mapping).
- **Candle substitutions** — every vanilla candle now substitutes to the **matching-colour TFC candle** (plain
  `minecraft:candle` → `tfc:candle`, each dyed → `tfc:candle/<colour>`), with a pick-any-colour GUI pool. Candle
  count and lit state carry over.
- **Sea pickle substitution** — `minecraft:sea_pickle` → `tfc:sea_pickle` (count + waterlogged carry over).
- **Enchanting table → scribing table** — `minecraft:enchanting_table` now substitutes to TFC's wooden **scribing
  table** (`tfc:wood/scribing_table/oak` by default), with an any-wood re-pick pool — TFC's nearest wooden
  workstation twin.
- **FirmaLife beehive substitution** — when **FirmaLife** is present, `minecraft:beehive` → `firmalife:beehive` (via
  the FirmaLife-only datapack).

### Changed
- **End-stone substitution fixed & extended** — the end stone / end-stone-brick rules pointed at uncolored
  `tfc:alabaster/raw` and `tfc:alabaster/bricks`, which aren't real blocks (TFC alabaster is colour-only), so they
  silently did nothing. They now resolve to **`light_gray`** TFC alabaster, and the previously-missing
  **end-stone-brick stairs, slab and wall** are covered too (`tfc:alabaster/bricks/light_gray_{stairs,slab,wall}`).
  Each still offers the pick-any-colour GUI pool.
- **Herder huts drop their vanilla-only chores in TFC** — the Cowhand no longer wastes time attempting **mooshroom
  stew** (TFC spawns no mooshrooms), and the Shepherd's auto-**dyeing** no longer applies (TFC sheep aren't vanilla
  `Sheep`, and the TFC shear path doesn't dye). Their now-inert settings (**Stewing Amount** on the Cowhand, **Dyeing**
  on the Shepherd) are hidden from the hut GUI. The Shepherd also strictly honours its **Shearing** setting — with it
  off, the worker won't shear TFC wooly animals even mid-state.

### Removed
- **Vanilla barrel handling moved to firmavanilla** — both the `mctfc:barrel` crafting recipe (which restored a
  craftable vanilla barrel in a TFC world) and `MixinBarrelBlockEntity` (which shrank the *vanilla* barrel to
  `tfc:chest` rules) were removed, now that **firmavanilla** ships per-wood barrels. The vanilla barrel is left
  untouched — plain 27-slot, uncraftable in TFC; the TFC small-chest behaviour (18 slots + item-size limit) now
  lives on firmavanilla's own barrel blocks instead.

### Changed
- **Dining hall stocks food to demand, not by the stackful** — the dining hall used to try to keep
  `maxStackSize × building level` of *every* menu dish (e.g. ~160 of each at level 5), so the Chef bulk-cooked far
  more perishable TFC food than the colony eats and it rotted in storage. It now targets a per-dish amount scaled
  to the number of colony citizens, clamped to a sensible floor/cap (config `diningHallStockPerCitizen` 0.5,
  `diningHallStockMin` 4, `diningHallStockMax` 16). Only the dining hall is affected.
- **The Waiter no longer hoards a stack of food** — when fetching food to serve, the Waiter pulled a full stack
  (64) into its own inventory, which (unlike colony storage) isn't covered by food preservation, so the surplus
  decayed in hand. It now carries `diningHallWorkerCarry` (default 16) per trip.
- **Cemented cobblestone moved to a dedicated mod** — the non-falling "cemented" cobblestone blocks are now
  provided by the new **TFC Vanilla Building Blocks** (`firmavanilla`) companion, a **required dependency** of
  this mod. Behaviour is unchanged (craft with mortar / right-click to convert); only the block ids moved
  namespace (`mctfc:mortared/…` → `firmavanilla:mortared/…`).

### Added
- **Deepslate → basalt (incl. firmavanilla rock tiles)** — the whole vanilla deepslate family now substitutes to
  TFC basalt: the non-tile forms to TFC's basalt rock forms (raw / non-falling mortared cobble twin / smooth /
  bricks / cracked bricks / chiseled), and the deepslate **tile** forms (tiles/cracked/stairs/slab/wall) to
  firmavanilla's new basalt rock tiles. Each carries an any-rock *Replace* pool.
- **Vanilla copper → firmavanilla weathering copper** — all vanilla copper now places the matching TFC-integrated
  firmavanilla copper (plated block ↔ `copper_block`, cut copper/stairs/slab ↔ `copper_cut*`, waxed → waxed),
  stage- and form-matched 1:1 across the four weather stages.
- **Purpur → alabaster; end stone → alabaster** — purpur block/pillar/stairs/slab now place firmavanilla alabaster
  tiles/pillars (default **purple**, with a pick-any-colour *Replace* pool); end stone → uncolored TFC raw
  alabaster and end stone bricks → uncolored TFC alabaster bricks (each + a pick-any-colour pool).
- **Soul lantern → soul lamp** — `minecraft:soul_lantern` now places a firmavanilla soul lamp (default wrought
  iron, lit; any-metal *Replace* pool), mirroring the regular lantern → TFC lamp swap.
- **Chiseled sandstone in TFC colours** — blueprints using chiseled sandstone now place a real chiseled block
  in one of TFC's 7 sand colours (with a *Replace* pool to re-pick the colour), instead of degrading to flat
  cut sandstone. Provided by the new `firmavanilla` dependency.
- **Decorative bookshelves substitution** — `minecraft:bookshelf` now substitutes to a per-wood decorative
  bookshelf with enchanting power (`firmavanilla:bookshelf/<wood>`, oak default + an any-wood *Replace* pool),
  re-adding the bookshelf swap that was previously dropped for lack of a TFC target. AFC/Beneath woods join the
  pool when those mods are present. The decorative bookshelves are also registered into MineColonies'
  `tier2blocks` + `reduceable_product_excluded` tags and Domum Ornamentum's `slab_materials` tag (matching the
  vanilla bookshelf), so the colony builder and DO handle them correctly.

### Removed
- The recipe that restored a craftable vanilla `minecraft:bookshelf` in a TFC world — the decorative
  TFC-wood bookshelves (above) replace it.
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
