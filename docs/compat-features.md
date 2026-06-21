# mctfc (`:compat`) — feature notes

Detailed implementation notes for each MineColonies × TerraFirmaCraft bridge feature, moved out of
CLAUDE.md to keep it lean. Read the relevant section before changing a feature so you don't re-derive
or undo a hard-won fix. See [CLAUDE.md](../CLAUDE.md) for the project overview, constraints and conventions.

## TFC default substitutions (stone + wood families) — DONE

Vanilla MineColonies blueprints are built from vanilla blocks; these rules retexture colony builds into TFC
materials. The design uses the engine's two-stage resolution (fixed rule converts vanilla→TFC first, then a
candidate pool keyed on the **converted** block lets the player re-pick — see
[BlockSubstitutions](../replacements/src/main/java/com/structurizereplacements/substitution/BlockSubstitutions.java)`#applyState`/`resolveBlock`).
So every covered source gets **both** a fixed default **and** a pick pool, with unique sources (fixed on the
vanilla block, pool on the TFC-result tag) — never a fixed `to` + `to_tag` on the same source (that shadows).

- **Wood** ([tfc_wood.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_wood.json)): vanilla →
  the **look-alike** TFC wood — oak/acacia/mangrove keep their name; spruce→chestnut, birch→douglas_fir,
  jungle→spruce, dark_oak→hickory, cherry→kapok, bamboo→palm — across all forms (planks, log/wood + stripped,
  stairs, slab, fence, fence_gate, door, trapdoor, button, pressure_plate, **sign + wall_sign**). **Hanging signs**
  (`*_hanging_sign`, `*_wall_hanging_sign`) map by the same wood rule but TFC keys them as
  `tfc:wood/planks/{hanging_sign,wall_hanging_sign}/<metal>/<wood>` (a metal × wood matrix) — we default the metal to
  **copper** (`hanging_sign/copper/<wood>`); the candidate pool offers the wood re-pick (copper kept). Bamboo is special
  (`*_block` → log, `*_mosaic*` → TFC palm mosaic). Singletons default to oak: `minecraft:chest`, `trapped_chest`,
  `crafting_table` (→ `oak_workbench`), `lectern` (→ `tfc:wood/lectern/oak`; TFC lecterns are per-wood
  `tfc:wood/lectern/<wood>`), and the two firmavanilla per-wood forms TFC lacks — `bookshelf` →
  `firmavanilla:bookshelf/oak` and `barrel` → `firmavanilla:barrel/oak` (each with its own any-wood re-pick pool).
  Plus a per-form candidate pool so the player can pick any TFC wood. The
  **nether woods (crimson/warped) are NOT mapped to TFC here** — they're handled 1:1 by the optional Beneath
  datapack (Beneath ships real crimson/warped wood); see "Optional per-mod datapacks".
- **Stone** ([tfc_stone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json)): vanilla
  stone family → **dacite** forms (closest look) — `stone→raw`, `stone_bricks→bricks`, `smooth_stone→smooth`,
  mossy/cracked/chiseled likewise, all with stairs/slabs/walls. Cobble and mossy-cobble map to the **non-falling
  mortared dacite twin** (`firmavanilla:mortared/tfc/rock/.../dacite`, from the firmavanilla mod) so builds
  survive TFC gravity; their stairs/slabs/walls (which don't landslide) use plain TFC.
  `minecraft:stone_button` → `tfc:rock/button/dacite`. Vanilla
  **granite/diorite/andesite** (which are real TFC rock types) map to the **same** rock — plain → `tfc:rock/raw/<rock>`,
  polished → `tfc:rock/smooth/<rock>` (+ stairs/slabs/walls). Per-form candidate pools let the player pick any
  TFC rock — the cobble/mossy-cobble full-block pick reuses the runtime `firmavanilla:mortared_cobblestone` pool, and
  granite/diorite/andesite reuse the existing `raw`/`smooth` pools.
- **Sandstone** ([tfc_sandstone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_sandstone.json)):
  **pool-only, no implicit swap** — vanilla sandstone is accessible in TFC so it stays the default, but every
  variant (normal + red, raw/cut/smooth + stairs/slabs/walls) offers a *Replace* pool of TFC colored sandstones
  (`tfc:{raw,smooth,cut}_sandstone/<color>`, all 7 colors) of the matching form. This is the `from` → `to_tag`
  pattern (pool keyed directly on the vanilla block, since nothing converts it first). **Chiseled** sandstone
  (which TFC lacks) instead maps to the firmavanilla mod's `firmavanilla:chiseled_sandstone/<color>` blocks
  (vanilla creeper/wither relief recoloured onto TFC's cut sandstone) + a `mctfc:subst/sandstone/chiseled`
  re-pick pool — see [docs/firmavanilla.md](firmavanilla.md).
- **Deepslate** ([firmavanilla_deepslate.json](../compat/src/main/resources/data/mctfc/block_substitutions/firmavanilla_deepslate.json)):
  the whole deepslate family → **basalt** (default) with any-rock pools. The non-tile forms map to TFC basalt
  rock forms exactly like the stone family above (raw / mortared cobble twin / `polished→smooth` / bricks /
  cracked_bricks / chiseled), **reusing tfc_stone.json's existing `tfc:rock/*` pools** (the GUI keys off the
  resolved basalt block). The **tile** forms (`deepslate_tiles`/`cracked`/`tile_stairs`/`slab`/`wall`) map to
  firmavanilla's new rock tiles (`firmavanilla:{tiles,cracked_tiles,tile_stairs,tile_slab,tile_wall}/basalt`) with
  new `mctfc:subst/firmavanilla/*` pools.
- **Copper** ([firmavanilla_copper.json](../compat/src/main/resources/data/mctfc/block_substitutions/firmavanilla_copper.json)):
  all vanilla copper → firmavanilla's TFC-integrated weathering copper, **form- and stage-matched 1:1** (no pool):
  plated full block ↔ `copper_block`, cut copper/stairs/slab ↔ `copper_cut*`, `waxed_*` → `waxed_*`, and the four
  weather stages map straight across (unaffected/exposed/weathered/oxidized).
- **Alabaster** ([firmavanilla_alabaster.json](../compat/src/main/resources/data/mctfc/block_substitutions/firmavanilla_alabaster.json)):
  **purpur** block/pillar/stairs/slab → firmavanilla alabaster tiles/pillars, default **purple** + a pick-any-colour
  pool (`mctfc:subst/firmavanilla/alabaster_*`). **end_stone** → uncolored `tfc:alabaster/raw`; **end_stone_bricks** →
  uncolored `tfc:alabaster/bricks` — each with a colour pool (`mctfc:subst/alabaster/{raw,bricks}`) that *includes
  the uncolored base* so the GUI offers the pool on the default. **Soul lantern** → firmavanilla soul lamp
  (default wrought_iron, `lit=true`; any-metal pool) is hand-written in [defaults.json](../compat/src/main/resources/data/mctfc/block_substitutions/defaults.json)
  beside the regular lantern rule (it needs `apply_properties`). **Soul torch** / **soul wall torch** →
  `firmavanilla:soul_torch` / `soul_wall_torch` (single block, no pool — wall facing carries via shared-property
  copy), also in `defaults.json` beside the regular `torch`→`tfc:torch` rules.
- **Pool tags** live under `data/mctfc/tags/blocks/subst/{wood,rock,firmavanilla,alabaster,…}/*.json` (one per form, listing every TFC
  variant). The rule files and tags are emitted by [gen_tfc_substitutions.sh](../compat/gen_tfc_substitutions.sh)
  (re-run if TFC's rock/wood set changes); they're plain static JSON, so `/reload`-able and editable. Validated:
  every fixed-rule target and all pool-tag members (880) resolve to real TFC blocks.
- **Flowers** ([tfc_flowers.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_flowers.json)):
  vanilla small flowers → TFC plants (`tfc:plant/<x>`) with name/closest-match **fixed defaults** (dandelion/poppy/
  blue_orchid/allium/oxeye_daisy/lily_of_the_valley keep their name; azure_bluet→houstonia, the four tulips→
  `tulip_<color>`, cornflower→grape_hyacinth, wither_rose→black_orchid, torchflower→calendula) **plus a candidate
  pool** (`mctfc:subst/plant/flower`, keyed on the converted result) of all 46 TFC ornamental flowers. **Potted**
  flowers (`minecraft:potted_<x>`) map the same way to TFC's own potted plant blocks (`tfc:plant/potted/<x>`) with
  pool `mctfc:subst/plant/potted_flower`. The small-flower pool is the intersection of TFC's potted (decorative) set and its
  standalone plants, minus non-flowers (saplings/ferns/krummholz/cactus/grass/…) and **all double-tall plants** (rose,
  sapphire_tower, foxglove — they live in the separate `tall_flower` pool; mixing them into the single-flower pool would let
  a 2-tall plant be picked for a 1-block slot). Flowers are plain
  block-state substitution (no engine change — they go through the same `handleBlockPlacement`/preview path as any
  block). The pool/rule files are hand-curated static JSON (not from gen_tfc_substitutions.sh).
- **Double-tall flowers** (same [tfc_flowers.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_flowers.json)):
  vanilla `DoublePlantBlock`s map to TFC two-tall plants — lilac→lilac, rose_bush→rose, sunflower→sapphire_tower,
  peony→foxglove — plus a `mctfc:subst/plant/tall_flower` pool (foxglove/hibiscus/lilac/rose/sapphire_tower +
  the water ones marigold/sea_lavender/pickerelweed/arrowhead). **Gotcha that needed an engine feature:** TFC
  two-tall plants (`TALL_GRASS`/`TALL_WATER`/`TALL_WATER_FRESH` types) aren't vanilla `DoublePlantBlock`s — they
  key their halves on a **`part`** property where vanilla uses **`half`** (both serialize `lower`/`upper`). A plain
  swap would leave both cells at the target's default `part` (desynced halves). So each double rule carries
  `"copy_properties": { "half": "part" }` — the engine's new source→target property-copy (see "The substitution
  feature"), applied per cell, so the lower/upper halves stay aligned. (Both blueprint halves are separate
  `BlockInfo`s; the rewrite fires on each, and since the TFC target isn't a `DoublePlantBlock`, Structurize's
  generic per-cell placement places each independently — works as long as the build goes bottom-up onto valid
  ground, which TFC soil substitution provides.)

## Farmer farms TFC crops (till → plant → fertilize → harvest) — DONE & verified

The MineColonies farmer (`com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer`)
now tills TFC soil, plants TFC crops on the resulting TFC farmland, keeps the soil's nutrients up with TFC
fertilizers, and harvests them (with a per-field Fruiting/Seeding mode). All hooks live in
[MixinEntityAIWorkFarmer](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java) +
[TfcFarmlandHelper](../compat/src/main/java/com/mctfc/farming/TfcFarmlandHelper.java) +
[FertilizerHelper](../compat/src/main/java/com/mctfc/farming/FertilizerHelper.java) (`@Mixin(remap = false)` —
MineColonies' own class/methods; only the inner MC calls are remapped per their `@At`).

Why each piece works (recon, MC 1.20.1-1.1.1231 / TFC):
- TFC seeds (`tfc:seeds/*`) are `ItemNameBlockItem` (a `BlockItem`) pointing at the crop block, and TFC's
  `CropBlock extends` **vanilla** `net.minecraft.world.level.block.CropBlock`; its `canSurvive` only needs the
  block below to be in `tfc:farmland`. So the AI's `plantCrop` (BlockItem + `instanceof CropBlock` + canSurvive)
  and the ripe-harvest path (`instanceof CropBlock` + `isMaxAge`) already work for live TFC crops once the
  farmland is recognized. The seed assigns to the scarecrow with no extra work (no plantable filter).
- TFC crop lifecycle: a live crop grows to `growth==1.0` (**fruiting**, age==max → harvest gives produce + 1
  seed); if left unharvested it `die(...)`s into a `DeadCropBlock` (the **seeding** stage; `MATURE=true` →
  drops extra seeds, no produce). `DeadCropBlock extends TFCBushBlock` (not a `CropBlock`), so the base AI is
  blind to it.

**The TILL hooks** (see the old recon below): both `@Redirect`, no `@Shadow` of the inherited `world` field.
- **Recognition** — redirect `BlockState.is(BlockTags.DIRT)` in `findHoeableSurface` to also accept
  `#mctfc:farmer_tillable`
  ([farmer_tillable.json](../compat/src/main/resources/data/mctfc/tags/blocks/farmer_tillable.json): the 8 TFC
  grass variants with a farmland twin; `peat_grass`/`kaolin_clay_grass` excluded). TFC bare dirt already passes
  via `minecraft:dirt` (TFC ships `#tfc:dirt` into it).
- **Farmland type** — redirect the `Level.setBlockAndUpdate` call in `createCorrectFarmlandForSeed`: place what
  a hoe would make of the soil (`getToolModifiedState(HOE_TILL)` → `tfc:farmland/<soil>`) when that's a
  non-vanilla farmland, else place exactly what MineColonies intended (vanilla soil + MC crop-preferred farmland
  untouched). The block above is already cleared by the AI before this call, so TFC's air-above check passes.

**The PLANT hook** — `@Inject(HEAD, cancellable)` on `isRightFarmLandForCrop`: the AI only treats a vanilla
`FarmBlock` as valid for non-MC seeds, so it never planted on `tfc:farmland`. Return `true` when the block is in
`tfc:farmland` (`TFC_FARMLAND` tag) and the field's seed plants a `CropBlock`. The AI's own `plantCrop` still
runs `canSurvive`, so an incompatible crop simply isn't placed. (This also stops the AI re-hoeing land that's
already TFC farmland.)

**The HARVEST hook** — `@Inject(HEAD, cancellable)` on `findHarvestableSurface`: also return the position when
the block above is a **mature** `DeadCropBlock`, so the farmer collects the seeding stage (seeds) and frees the
cell. Non-dead-crop cases fall through to the base AI (it already harvests ripe TFC crops via `isMaxAge`). This
hook needs the level + crop position; rather than shadow the inherited `world`, it shadows two methods declared
on `EntityAIWorkFarmer` itself — `getCitizen()` (→ `getCitizen().level()`) and the private `getSurfacePos(...)`
— which resolve reliably. **Verified in-game: farmer collects both ripe and dead crops.**

**Per-field harvest mode (Fruiting / Seeding), chosen in the field GUI — DONE & verified.**
[HarvestMode](../compat/src/main/java/com/mctfc/farming/HarvestMode.java): *Fruiting* (default — harvest ripe
crops for produce + any dead crop for seeds); *Seeding* (leave ripe crops to die, then harvest only the
mature dead stage for max seeds). Pieces:
- **State on `FarmField`** — [MixinFarmField](../compat/src/main/java/com/mctfc/mixin/MixinFarmField.java) adds a
  `HarvestMode` field + the [FarmFieldHarvestMode](../compat/src/main/java/com/mctfc/farming/FarmFieldHarvestMode.java)
  duck-type interface, and carries it through both of `FarmField`'s existing serialization paths: NBT
  (`serializeNBT`@RETURN / `deserializeNBT`@TAIL, colony save) and the buffer (`serialize`/`deserialize`@TAIL,
  client sync). The buffer hooks append/consume the enum **last** on both sides so the stream stays aligned
  with the base seed/radii/stage payload. Same class both sides, so the client GUI reads the synced mode.
- **GUI toggle** — [MixinWindowField](../compat/src/main/java/com/mctfc/mixin/MixinWindowField.java) (client) adds
  a `ButtonImage` below the seed icon (shadows the window's own private `farmField`/`getCurrentColony()` — both
  declared on `WindowField`, so they resolve). Mirrors the seed selector: optimistic client update + a server
  message; the label (current mode) refreshes each `onUpdate` (the client `farmField` resolves a tick after
  ctor). **`ButtonImage` gotcha:** a programmatic `ButtonImage()` won't draw its label — its `setSize` rescales
  the text-render box *proportionally from the previous value*, and the no-arg ctor leaves that at 0, so the box
  stays 0 and the text is clipped to nothing; also the default text colour is white (invisible on the light
  button). Fix: `setTextRenderBox(w, h)` after `setSize`, and `setColors(0x000000)`.
- **Network** — own channel [McFarmingNetwork](../compat/src/main/java/com/mctfc/network/McFarmingNetwork.java) +
  [SetHarvestModeMessage](../compat/src/main/java/com/mctfc/network/SetHarvestModeMessage.java) (client→server;
  resolves the colony via `getColonyByDimension(id, dim)`, finds the field via
  `getServerBuildingManager().getMatchingBuildingExtension(pos)`, sets the mode, `markBuildingExtensionsDirty()`
  to persist + re-sync). Registered from the mod ctor.
- **Harvest reads the mode** — [MixinEntityAIWorkFarmer](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java)
  captures the worked field's mode into a `@Unique` field via two `@Redirect`s on the extension module's
  `getExtensionToWorkOn()` (in `prepareForFarming`) / `getCurrentExtension()` (in `workAtField`) — fetched right
  before the harvest dispatch on each side. The `findHarvestableSurface` hook then: harvests mature dead crops
  (both modes); and in *Seeding*, returns `null` for live `CropBlock`s so ripe crops are left to go to seed.

**Fertilizing (TFC soil nutrients), best-match auto-request — DONE & verified.** Vanilla MineColonies uses
"fertilizer" (its compost / bone meal) as a **growth accelerator** (`findHarvestableSurface` → `crop.growCrops`).
TFC is different: fertilizers don't speed growth, they top up the farmland's N/P/K nutrients (`IFarmland`); each
crop drains its own `primaryNutrient` (`ICropBlock.getPrimaryNutrient`) and low nutrient → low yield/death.
[FertilizerHelper](../compat/src/main/java/com/mctfc/farming/FertilizerHelper.java) bridges this; the model is
data-driven (`Fertilizer.MANAGER` maps items → N/P/K; `Fertilizer.get(stack)`):
- **No more growth-cheat for TFC crops** — the `findHarvestableSurface` hook now fully *owns* the decision for a
  live TFC `CropBlock` (harvest only when ripe + Fruiting; else `null`), so the base AI's `growCrops` compost
  path never runs on them.
- **Apply at plant + opportunistically on visit** — `fertilizeForSeed` (HEAD inject on `plantCrop`) and a
  `fertilize(...)` call in the `findHarvestableSurface` hook (which the harvest scan runs for every cell of a
  planted field) top up the crop's primary nutrient. `fertilize` re-picks the **best-matching** fertilizer the
  farmer carries (most of the needed nutrient — so guano/compost beat a pure powder) and applies until the
  nutrient reaches `Config.fertilizeTarget`, only kicking in below `Config.fertilizeBelow` (hysteresis). Config
  in [Config](../compat/src/main/java/com/mctfc/Config.java) (`config/mctfc-common.toml`: `fertilizeBelow` 0.4,
  `fertilizeTarget` 0.9).
- **Auto-request the *right* fertilizer, nutrient-specific** — the request must match the crop's nutrient, but
  the base AI checks "do I have ANY fertilizer" *before* it fetches the field. Two hooks fix this:
  - `mctfc$fertilizerCountsAsCompost` (`isCompost` HEAD inject) makes the count/gather/request-gate count **only
    TFC fertilizer supplying the current crop's nutrient** — so a phosphorus field with only nitrogen in stock
    (or a stray bone meal, which is a *phosphorus* fertilizer) doesn't read as "stocked" and block the right
    request. Falls through to the base check when there's no TFC crop (`mctfc$neededNutrient == null`).
  - The needed nutrient is captured **before** that count block by redirecting the *first* module call in
    `prepareForFarming` (`getOwnedExtensions`, the advancement check) and reading `getExtensionToWorkOn()` there
    (it's sticky, so reading it early doesn't change selection). The old getExtensionToWorkOn capture moved here.
  - `mctfc$requestTfcFertilizer` redirects the base `createRequestAsync` to a `StackList` of the fertilizers that
    supply the nutrient (`fertilizersFor`), so the farmer requests usable fertilizer, not MC compost. It sits
    inside the AI's existing `building.requestFertilizer()` gate, so the **hut's "request fertilizer" toggle**
    still governs it.

**Planting correctness (avoid pointless/destructive planting) — DONE & verified.**
- **Gate `FARMER_PLANT` on real work** (`mctfc$skipEmptyPlanting`, HEAD inject on `canGoPlanting`): unlike hoe/
  harvest (gated by `checkIfShouldExecute`), the base AI enters planting whenever a seed exists — so a fully
  planted field of still-growing crops makes the farmer pointlessly walk every cell each stage cycle (very
  visible under TFC's slow growth). If no cell is plantable, advance the stage instead of entering the state.
  Uses shadowed `checkIfShouldExecute` + `findPlantableSurface` (both declared on the target).
- **Don't plant over a mature dead crop** (`mctfc$keepMatureDeadCrops`, HEAD inject on `findPlantableSurface`):
  the base "is this cell occupied" test only matches vanilla `CropBlock`/`StemBlock`/`MinecoloniesCropBlock` — a
  `DeadCropBlock` reads as empty, so the farmer would overwrite a **mature** dead crop and lose its seeds (which
  Seeding mode exists to produce, and Fruiting also collects). Treat a mature dead crop as not-plantable so it
  survives for the harvest pass; non-mature dead crops (no worthwhile drops) stay plantable so the cell is reused.

## Decay-aware item stacking (TFC food freshness) — DONE & verified

Harvested TFC food was merging to the **oldest** creation date when it stacked, so fresh harvests aged
instantly onto older/rotten stacks. Recon pinned it to a **single chokepoint**, not a system-wide rewrite:

- **Why MineColonies is blind:** TFC stores a food's `creationDate` + decay traits in a Forge **capability**
  (serialized to the stack's `ForgeCaps`, synced separately via TFC's `ItemStackCapabilitySync`), **not** in the
  vanilla item `tag`. MineColonies' `ItemStackUtils.compareItemStacksIgnoreStackSize` only inspects
  `ItemStack#getTag()`, so two TFC crops of different freshness read as identical.
- **Where the rot happens:** `InventoryCitizen#insertItem` (a *custom* `IItemHandlerModifiable`, not vanilla
  `ItemStackHandler`) uses that comparison as its merge gate and then grows the **existing** slot — so a fresh
  harvest poured onto an older stack inherits the older caps. This is the only caps-blind physical-merge site:
  - **Racks/warehouse are already safe** — `AbstractTileEntityRack.RackInventory extends ItemStackHandler` and
    its `insertItem` calls the vanilla `super.insertItem`, which is caps-aware (`ItemHandlerHelper.canItemStacksStack`).
  - `InventoryUtils.mergeItemStackIntoNextBestSlotInItemHandlers` / `addItemStackToItemHandlerWithResult` and
    `CombinedItemHandler.insertItem` use the comparison only to *pre-select* a candidate slot, then delegate the
    real merge to `handler.insertItem` — so fixing the citizen handler covers the whole harvest → citizen → dump
    flow. The other ~26 `compareItemStacksIgnoreStackSize` call sites are **request/storage/recipe matching** and
    must stay freshness-agnostic (any wheat fulfils a wheat request) — deliberately untouched.
- **The fix** ([MixinInventoryCitizen](../compat/src/main/java/com/mctfc/mixin/MixinInventoryCitizen.java),
  `@Mixin(remap = false)` — MC's own method): a `@Redirect` of the single `compareItemStacksIgnoreStackSize`
  call in `insertItem` that AND-s in [FoodStackingHelper](../compat/src/main/java/com/mctfc/food/FoodStackingHelper.java)`#canMerge`.
  `canMerge` returns `true` for non-food (no behaviour change); for TFC food it defers to the vanilla caps-aware
  `ItemHandlerHelper.canItemStacksStack`, i.e. the same rule TFC uses for slot stacking — foods sharing a rounded
  decay window still stack, differently-aged ones don't. Nothing else (requests, storage keys, recipes) changes.

## Food spoilage management (colony-storage preservation + freshness-aware eating) — DONE, in-world test pending

TFC food spoils; MineColonies' food economy (bulk-request → hoard in racks → cook batches → citizens eat whenever)
assumes food is inert, so untouched colonies rot their warehouses and citizens eat rot. Three surgical pieces fix
the worst of it; all gate on the TFC food **capability** (`net.dries007.tfc.common.capabilities.food.FoodCapability`,
caps-aware — same blindness story as the stacking fix above). Design choices (all the user's): **suppress decay in
colony-owned storage only** (player racks inert), strength **live-configurable**; FIFO as a **tiebreaker** (keep MC's
diet-variety scoring); rotten handling is **skip-only** (disposal will be a future composter-request path).

- **A — colony-storage preservation** ([FoodPreservation](../compat/src/main/java/com/mctfc/food/FoodPreservation.java)
  + [MixinRackInventory](../compat/src/main/java/com/mctfc/mixin/MixinRackInventory.java) +
  [AbstractTileEntityRackAccessor](../compat/src/main/java/com/mctfc/mixin/AbstractTileEntityRackAccessor.java)):
  register **our own `FoodTrait`** `mctfc:colony_storage` (via `FoodTrait.register` + the `FoodTrait(Supplier<Float>,
  String)` ctor — TFC's own config-driven traits work the same way) whose decay modifier reads `Config.foodColonyStorageDecay`
  **live** (0 = frozen … 1 = normal, default 0.25; no restart needed). The trait is the TFC-idiomatic mechanism (same as
  sealed vessels) and is correct **across chunk unload** — decay = `creationDate` + trait modifiers at read time — which a
  tick-based clock nudge would not be. Registered from the mod ctor on `FMLCommonSetupEvent`.
  - **Where applied:** `AbstractTileEntityRack.RackInventory` (MC's rack inventory). A rack is **colony-owned** iff the outer
    `inWarehouse || !buildingPos.equals(BlockPos.ZERO)` (player-placed free-standing racks are both-false → untouched).
    The inner-class mixin reaches the outer rack via a `@Shadow(aliases="this$0")` on the synthetic outer ref (the AP
    *can't* see synthetic fields so it warns `Cannot find target for @Shadow field` at compile — **binds fine at
    runtime**, verified), and reads `inWarehouse`/`buildingPos` through the `@Accessor` interface (they're protected).
  - **Apply on entry / strip on exit:** `@Inject` HEAD of `insertItem` tags the incoming stack (so same-age food still
    stacks); `@Inject` TAIL of `onContentsChanged` tags whatever ends up in the slot; and a **soft-override** of
    `extractItem` (the mixin `extends ItemStackHandler` so `super.extractItem` resolves — `RackInventory` doesn't override
    it) **strips** the trait from withdrawn food, so withdrawn / player-moved food reverts to normal decay → player shelves
    stay honest. `markStored` mutates the live `IFood` cap in place (`FoodCapability.applyTrait(IFood,…)`, idempotent via
    `hasTrait`); `clearStored` uses the `ItemStack` overload of `removeTrait`. All gated server-side (`level != null &&
    !isClientSide`).
- **B — FIFO tiebreaker** + **C — skip-rotten**, one mixin
  ([MixinFoodUtils](../compat/src/main/java/com/mctfc/mixin/MixinFoodUtils.java), `@Mixin(remap=false)`): both citizen eating
  (`EntityAIEatTask`) and the cook (`EntityAIWorkCook`) funnel through `FoodUtils.canEat` + `FoodUtils.getBestFoodForCitizen`.
  - **Skip-rotten:** `@Inject` HEAD of `canEat` → `false` when `FoodCapability.isRotten(stack)` — covers eating, cooking and
    the building food scan at one choke (citizens just don't pick rot; if only rot exists they don't eat, same as no food).
  - **FIFO:** `@Inject` RETURN of `getBestFoodForCitizen` → after MC picks a slot, scan for another slot holding the **same
    `Item`** (hence identical desirability score — a *true* tiebreaker that never overrides MC's variety choice) and **not
    rotten**, and swap to the one with the soonest `IFood#getRottenDate()`. Per-stack only (citizen/cook inventory); the
    building rack scan aggregates by caps-blind `ItemStorage` so it can't see age — a known follow-up.
- **Config** ([Config](../compat/src/main/java/com/mctfc/Config.java)): `foodColonyStorageDecay` (`config/mctfc-common.toml`,
  default 0.25). Lang: the trait tooltip key `mctfc.food_trait.colony_storage` (TFC's `FoodTrait#addTooltipInfo` calls
  `Component.translatable(translationKey)` directly, so the key *is* the lang key).
- **Config** ([Config](../compat/src/main/java/com/mctfc/Config.java)): `foodColonyStorageDecay` (`config/mctfc-common.toml`,
  default 0.25). Lang: the trait tooltip key `mctfc.food_trait.colony_storage` (TFC's `FoodTrait#addTooltipInfo` calls
  `Component.translatable(translationKey)` directly, so the key *is* the lang key).
- **Verified to load:** compiles; all three mixins apply (`AbstractTileEntityRackAccessor`/`MixinRackInventory` into the
  rack, `MixinFoodUtils` into `FoodUtils`); trait registers; runs in a live colony world without crash. **In-world
  behaviour** (food actually preserving in racks, FIFO order, rotten skipped) still to be confirmed in gameplay.

## TFC food nutrition value (citizen saturation) — DONE & verified

TFC food fed MineColonies citizens almost no saturation (~0.83). **Why:** every TFC food item ships a *flat* vanilla
`FoodProperties` (`nutrition = 4`, `saturationMod = 0.3`) — its real nutrition lives in the TFC `FoodData` capability
(`hunger()`, `saturation()`, the 5 nutrients), which MineColonies never reads. `FoodUtils#getFoodValue(ItemStack,
FoodProperties, double)` then computes `nutrition × 0.25 (non-MC-food nerf) / 1.2`, i.e. `4 × 0.25 / 1.2 ≈ 0.83` — vs
MineColonies' own food (`IMinecoloniesFoodItem`, no nerf) at `nutrition/1.2 ≈ 4–10`.

- **The bridge** (third hook in [MixinFoodUtils](../compat/src/main/java/com/mctfc/mixin/MixinFoodUtils.java)): `@Inject` HEAD
  (cancellable) on the **core** `getFoodValue(ItemStack, FoodProperties, double)` — every saturation path funnels through
  it (`ItemStackUtils#consumeFood` for citizen self-eat / nether / player-fed; the cook's `increaseSaturation`; the qty
  calcs; the JEI/EMI tooltip), and the `getFoodValue(stack, citizen)` overload delegates to it. For TFC food (gated on
  `FoodCapability.has`) it recomputes from `FoodData`: `hunger × (1 + saturation) / 1.2 × (1 + researchBonus) ×
  Config.tfcFoodSaturationModifier`, dropping the 0.25 nerf and keeping the `/1.2` + research scaling so it matches MC's
  own food. `hunger` is a **flat 4** across all TFC food, so `saturation` (the real quality signal: berry 0.2 → cabbage
  0.5 → bread 1.0 → cooked_beef 2.0; meals higher) is what differentiates — landing blueberry ≈ 4.0, cooked_beef ≈ 10.0,
  right in MC's range. Non-TFC food falls through unchanged.
- **Config** ([Config](../compat/src/main/java/com/mctfc/Config.java)): `tfcFoodSaturationModifier` (default `1.0` = 100%,
  range 0–10), a live balance multiplier on the bridged value.

## MineColonies foods become TFC foods — DONE, in-world test pending

MineColonies registers ~60 of its own cooked dishes ([ModItemsInitializer](https://github.com/ldtteam/minecolonies),
`ItemFood`/`ItemBowlFood`, all `IMinecoloniesFoodItem`) in three flat nutrition tiers (vanilla `FoodProperties`
nutrition **5 / 7 / 9**). Out of the box they carry **no** TFC food data — they don't decay and add no TFC
nutrients, so in a TFC world they're an inert "premium" supply that sidesteps TFC's whole food economy. This
feature gives them real TFC food data, **datapack-only, no code**.

- **Why a datapack suffices:** TFC's `ForgeEventHandler.attachItemCapabilities` (subscribed to
  `AttachCapabilitiesEvent<ItemStack>`, fired for *every* stack from *any* mod) attaches the `FoodCapability` to
  any item for which `FoodCapability.getDefinition(stack) != null` — i.e. any item matched by a `food_items`
  definition's `ingredient` (item-agnostic). The definitions live in TFC's `DataManager` folder
  **`tfc/food_items`** (`DataManager` = a `SimpleJsonResourceReloadListener` over `domain.getNamespace()+"/"+domain.getPath()`),
  scanned across **all** namespaces — so files under `data/mctfc/tfc/food_items/` (note the doubled `tfc`) convert
  MineColonies items with no Java at all.
- **The pack** ([gen_tfc_food_items.sh](../compat/gen_tfc_food_items.sh) → `data/mctfc/tfc/food_items/*.json`, one
  per dish): `hunger 4` (TFC standard) for all; **saturation by vanilla-nutrition tier** (n5 → 0.25, n7 → 0.75,
  n9 → 1.25); per-dish `water` for soups/broths/teas; and per-dish nutrients assigned by what the dish *is*
  (bread → grain, stew → protein/veg, cheese → dairy, …). Grouped by **vanilla nutrition**, not the
  `IMinecoloniesFoodItem` tier — a few items (kebab, mutton_dinner) have a tier that disagrees with their
  nutrition, and feed value tracks nutrition. Static defs (**not** `dynamic_bowl`: MineColonies' bowl foods are
  pre-made meals, not TFC salad/soup-device output, so they need fixed stats).
- **Calibrated to the nutrition bridge (the load-bearing bit):** the [MixinFoodUtils](../compat/src/main/java/com/mctfc/mixin/MixinFoodUtils.java)
  `getFoodValue` HEAD-cancel fires for *any* `FoodCapability.has()` food, so the moment a MineColonies dish gains
  the cap it is valued by `hunger × (1 + saturation) / 1.2` instead of MineColonies' un-nerfed `nutrition / 1.2`.
  Keeping `hunger = 4` and the per-tier saturation above makes `4 × (1 + sat) == nutrition`, so **feed value is
  unchanged** while the food now also decays and carries nutrients. (Re-tune the saturation column if the bridge
  formula changes.)
- **Decay knob & interactions:** `decay_modifier` is a single variable in the generator (default `1.0` = normal
  TFC decay). Once converted, these dishes participate in the rest of the colony food stack — preserved in colony
  storage by the `mctfc:colony_storage` rack trait ([FoodPreservation](../compat/src/main/java/com/mctfc/food/FoodPreservation.java)),
  freshness-separated by the decay-aware stacking ([MixinInventoryCitizen](../compat/src/main/java/com/mctfc/mixin/MixinInventoryCitizen.java)),
  and skipped when rotten / served FIFO ([MixinFoodUtils](../compat/src/main/java/com/mctfc/mixin/MixinFoodUtils.java)).
- **Verify in-game:** TFC's `FoodCapability.markRecipeOutputsAsNonDecaying` (on `TagsUpdatedEvent`) marks recipe
  result *templates* non-decaying; confirm a freshly cooked MineColonies dish begins decaying as intended (those
  produced directly by the cook AI rather than a vanilla recipe should be fine), and that converted dishes feed
  citizens as before. Re-run `gen_tfc_food_items.sh` if MineColonies' food set changes. *(Confirmed in-game: the
  datapack makes MineColonies foods decay.)*

### Crafting carries ingredient freshness (copy_oldest_food)

Once MineColonies dishes decay, cooking must not *refresh* them — a meal made from a half-spoiled ingredient
should come out correspondingly aged (TFC's `copy_oldest_food`: interpolate the output's creation date toward the
oldest ingredient's). TFC applies that **only** inside `Recipe#assemble(CraftingContainer)`, which neither
MineColonies path reaches the same way, so this is split by who crafts:

- **Player crafting → datapack.** MineColonies' food recipes (which we rewrite to use TFC ingredients anyway) are
  authored as TFC advanced-crafting recipes carrying the modifier, so a player at a table runs TFC's real
  `assemble`. Pattern (overrides the stock recipe by sitting at the same `data/minecolonies/recipes/<path>.json`):
  ```jsonc
  { "type": "tfc:advanced_shapeless_crafting",   // or tfc:advanced_shaped_crafting (adds "pattern"/"key")
    "ingredients": [ /* TFC ingredients */ ],
    "result": { "stack": { "item": "minecolonies:flatbread" }, "modifiers": ["tfc:copy_oldest_food"] } }
  ```
  (`ItemStackProvider` reads `stack` + `modifiers`; with a `stack` present, `getResultItem()` is non-empty — the
  modifier applied to an empty input is a no-op — so MineColonies still sees a normal output for discovery.)
- **Worker crafting → code (the one place datapack can't reach).** `RecipeStorage` caches
  `primaryOutput = recipe.getPrimaryOutput()` and `fullfillRecipeAndCopy` (the single chokepoint — every
  `fullfillRecipe` overload delegates to it) inserts *copies* of it; it never builds a `CraftingContainer` or calls
  `assemble`, so the recipe's modifiers are dead for workers. [MixinRecipeStorage](../compat/src/main/java/com/mctfc/mixin/MixinRecipeStorage.java)
  fixes both modifier effects on TFC-food outputs. A `@Redirect` on the `extractItem` calls records each consumed
  TFC-food ingredient (a thread-local list cleared at method HEAD — the actual extracted stacks, since
  `getCleanedInput`'s `ItemStorage`s are caps-blind), the HEAD inject also stashes the level, and a `@Redirect` on
  the `getPrimaryOutput()` call feeding `insertCraftedItems` swaps in a fixed-up *copy* (cached template stays
  clean), branching on the output's `FoodDefinition.getHandlerType()`:
  - **static food** (e.g. our MineColonies dishes) → carry decay from the oldest ingredient
    ([CraftedFoodDecay](../compat/src/main/java/com/mctfc/food/CraftedFoodDecay.java)`#carryDecay` →
    `FoodCapability.updateFoodFromAllPrevious`). Matches the player path's `copy_oldest_food`.
  - **dynamic food** (TFC sandwiches, …) → the cached output is unrealized (`FoodData.EMPTY`); `#realizeFromRecipe`
    looks the recipe back up by `RecipeStorage#getRecipeSource()`, builds a throwaway 3×3 `TransientCraftingContainer`
    from the captured ingredients, and calls `CraftingRecipe#assemble` — running the `meal` modifier so the food
    comes out with its real, ingredient-derived nutrition (fresh, as TFC does for a hand-crafted meal). Any failure
    (unresolvable recipe, non-crafting type, wrong result item) falls back to the cached output. Because
    `AdvancedShaped/ShapelessRecipe#assemble` doesn't re-validate the grid and `meal` reads the input flat, a flat
    container suffices — so this is general across **any** crafting-table dynamic food (TFC's or an add-on's), not
    just sandwiches.
  - Gated to no-op unless the output is a TFC food. Non-food crafting is untouched.
- **Scope:** the `RecipeStorage` path is *all* colony crafters (Baker, cook-assistant, …) and covers crafting-table
  dynamic foods (sandwiches). **Not** covered: the cook's *furnace* output (`extractFromFurnace`, separate from
  `RecipeStorage`) and **dynamic foods made in TFC devices** — salads (TFC salad GUI) and soups (`tfc:pot`) — both
  of which belong with the Cook→TFC conversion (`docs/tfc-furnace-workers.md` §6).

**Knives match damage-agnostically (so the crafter doesn't stall on a worn knife).** The sandwich recipe wears a
TFC knife each craft (`tfc:damage_inputs_shaped_crafting`), and tool durability lives in the item's `Damage` NBT.
MineColonies matches recipe inputs with `matchNBT = true`, and for an item with no registered "checked NBT keys"
([`ItemStackUtils#compareItemStacksIgnoreStackSize`](https://github.com/ldtteam/minecolonies)) it compares the
**full** tag — so a once-damaged knife (now carrying a `Damage` tag) stops matching the recipe's fresh-knife input.
The crafter then can't see its own knife, makes exactly **one** craft, and stalls *without requesting* (its only
knife is the unmatchable damaged one). [ToolNbtMatching](../compat/src/main/java/com/mctfc/crafting/ToolNbtMatching.java)
registers every item in the **`tfc:knives` tag** into `ItemStackUtils.CHECKED_NBT_KEYS` with an **empty** key set
(⇒ the comparison ignores all NBT for it, so any-damage knife matches), on `TagsUpdatedEvent` (fires after the
datapack listener that owns the map has cleared+reloaded it, so it survives `/reload`). Tag-driven, because
MineColonies' own `compatibility` datapack format is item-id-only (no tags); `putIfAbsent` so a MineColonies-shipped
entry would win. The bread/fillings need no such fix — their freshness is in caps, not `getTag()`, so they already
match. Extend the same way for other damageable TFC tools used as colony-crafting ingredients.

## Dining hall stocks food to demand (not by the stackful) — DONE, in-world test pending

TFC food decays, but MineColonies' dining hall (Restaurant) was built for inert food: [RestaurantMenuModule](https://github.com/ldtteam/minecolonies)`#onColonyTick`
requests each **menu** item up to `target = itemStack.getMaxStackSize() × getExpectedStock()`, and the dining hall
registers `getExpectedStock = buildingLevel`. For 32-stacking TFC food at a level-5 hut that's **160 of every dish**,
which the Chef dutifully bulk-cooks into storage where it rots. (The request itself is a `MinimumStack` of
`min(STACKSIZE, maxStackSize, delta)` with minCount 1 — the "1–32" you see for 32-stack food; the Chef fulfils up to
that.) The same `maxStackSize × getExpectedStock()` also feeds `alterItemsToBeKept` (the keep/don't-dump amount).

[MixinRestaurantMenuModule](../compat/src/main/java/com/mctfc/mixin/MixinRestaurantMenuModule.java) replaces the
stack-size-driven target with a **demand-scaled** one: per menu item, `clamp(round(citizens × perCitizen), min, max)`
(config `diningHallStockPerCitizen` 0.5 / `diningHallStockMin` 4 / `diningHallStockMax` 16). Mechanism (vanilla Mixin,
no MixinExtras): `getExpectedStock` HEAD-cancellable → return the demand target; and two `@Redirect`s drop the
`getMaxStackSize()` **factor** to 1 — the target site (`onColonyTick` ordinal 0) and the keep sites
(`alterItemsToBeKept`) — leaving the per-request batch-cap `getMaxStackSize()` (ordinal 1) intact, so
`target = 1 × demand = demand`. All gated on the module's `canCook` flag, so **only the dining hall** changes; the
netherminer menu (the other `RestaurantMenuModule`, `canCook=false`) keeps vanilla behaviour. `getBuilding()` is
reached by casting `this` (it's inherited from `AbstractBuildingModule` — the Mixin AP can't `@Shadow` inherited
members).

**The Waiter doesn't hoard a stack either.** `EntityAIWorkCook#checkForImportantJobs` pulls food into the worker's
own inventory in `STACKSIZE` (64) batches (`needsCurrently = new Tuple<>(predicate, STACKSIZE)`, for citizens and
players). The worker inventory isn't a colony rack, so the `colony_storage` preservation trait doesn't cover it —
over-carried food rots in hand. [MixinEntityAIWorkCook](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkCook.java)
caps that gather at `Config.diningHallWorkerCarry` (default 16) via `@ModifyConstant` on the inlined `64` (the only
`64` in that method, from the compile-time-constant `STACKSIZE`).

<details><summary>Original tilling recon (kept for reference)</summary>

The MineColonies farmer (`com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer`)
was written for vanilla soil. Recon of the AI:
- `findHoeableSurface` only treats a surface block as hoeable if it's in `BlockTags.DIRT` (or is
  `MinecoloniesFarmland`/vanilla `FarmBlock`). TFC bare dirt **is** in `minecraft:dirt` (TFC ships
  `data/minecraft/tags/blocks/dirt.json` = `#tfc:dirt`), but TFC **grass** (`tfc:grass/<soil>`,
  `tfc:clay_grass/<soil>` — the actual surface in a TFC world) is only in `tfc:grass`, so the farmer
  ignored it.
- `createCorrectFarmlandForSeed` hardcodes vanilla `Blocks.FARMLAND` (or a MineColonies crop's preferred
  farmland) — never `tfc:farmland/<soil>`.
- Both TFC `DirtBlock` and `ConnectedGrassBlock` implement `getToolModifiedState(…, ToolActions.HOE_TILL, …)`
  → the matching `tfc:farmland/<soil>` (config-gated on `enableFarmlandCreation`, needs empty block above).
  This is the clean API to drive tilling. (TFC `FarmlandBlock extends Block`, **not** vanilla `FarmBlock`.)

**Scope: tilling only.** Two surgical hooks in
[MixinEntityAIWorkFarmer](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java)
(`@Mixin(remap = false)` — MineColonies' own class/methods; only the inner MC calls are remapped per their
`@At`), both `@Redirect` (no `@Shadow` — see the Mixins note about the inherited `world` field):
- **Recognition** — redirect the `BlockState.is(BlockTags.DIRT)` call in `findHoeableSurface` to also accept
  `#mctfc:farmer_tillable`
  ([data/mctfc/tags/blocks/farmer_tillable.json](../compat/src/main/resources/data/mctfc/tags/blocks/farmer_tillable.json):
  the 8 TFC grass variants that have a farmland twin; `peat_grass`/`kaolin_clay_grass` excluded — no
  farmland). TFC bare dirt already passes via `minecraft:dirt`.
- **Farmland type** — redirect the `Level.setBlockAndUpdate` call in `createCorrectFarmlandForSeed`. Ask
  the soil what a hoe would make of it (`getToolModifiedState(HOE_TILL)`,
  [TfcFarmlandHelper](../compat/src/main/java/com/mctfc/farming/TfcFarmlandHelper.java) — builds a throwaway
  `UseOnContext` with an iron hoe); if that's a **non-vanilla** farmland (TFC), place it, else place exactly
  what MineColonies intended (so vanilla soil + MineColonies-crop preferred farmland are untouched). The
  block-above is already cleared by the AI before this call, so TFC's air-above check passes.

(At the tilling-only stage the farmer then couldn't plant on TFC farmland — that's what the PLANT/HARVEST
hooks above added.)

</details>

## Miner shaft uses the hut fill-block setting — DONE

The MineColonies miner builds two ways: its node/tunnel/shaft **blueprints** go through Structurize's `StructurePlacer`
(already datapack-substituted by the engine, like the builder — unchanged), but the **vertical-shaft frame** is placed
raw. The main fill + water-walling + `getSolidSubstitution()` already read the hut's configurable **`FILL_BLOCK` setting**
(set it to a TFC block in the miner GUI — request, inventory-consume and placement then all agree on a block the player
can actually supply). The one gap: `EntityAIStructureMiner#getLadderBackFillBlock()` is **hardcoded** to
`Blocks.COBBLESTONE`/`NETHERRACK`, ignoring that setting — and vanilla cobblestone both *landslides* under TFC and isn't
obtainable in a TFC world, so the ladder backfill desynced (requested vanilla cobble, couldn't be fulfilled / collapsed).
Fix: [MixinEntityAIStructureMiner](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIStructureMiner.java) (`@Mixin(remap
= false)`, `@Inject` RETURN on the private `getLadderBackFillBlock`, shadowing the private `getMainFillBlock`) returns the
`FILL_BLOCK` setting so the **whole** shaft uses the GUI-chosen block. **Not** the substitution engine — this is the
vanilla MineColonies fill-block mechanism, just made consistent. (Default `FILL_BLOCK` is still cobblestone; the player
sets a TFC block — e.g. a cemented cobble or any TFC stone — in the hut GUI.) An earlier attempt that substituted the raw
placement via the engine was reverted: it placed a substituted block but still *requested* vanilla cobblestone, which
breaks in TFC.

## Builder fill-block defaults to TFC loam dirt — DONE

The **builder** fills solid-placeholder schematic blocks with the same configurable `FILL_BLOCK` hut setting (creative
paste turns them into dirt; the builder uses the setting). Its stock default is **vanilla dirt** — unobtainable in a
TFC world (and exactly what our `minecraft:dirt → tfc:dirt/loam` substitution rule maps away on the paste path), so a
fresh builder's hut would request a block the colony can never supply.
Fix: [MixinSettingsModule](../compat/src/main/java/com/mctfc/mixin/MixinSettingsModule.java) (`@Mixin(remap = false)`,
`@ModifyVariable` on `SettingsModule#with`) swaps the registered default to **`tfc:dirt/loam`**. `with` is the single
seam every module-producer default flows through; the shared `BuildingMiner.FILL_BLOCK` key is registered per building
type with a different default `BlockSetting`, and matching on the **dirt** default uniquely picks out the builder's
(the miner's cobblestone default is left alone — see the miner section above). Existing huts are unaffected:
`SettingsModule#deserializeNBT` replaces the registered setting with the saved one wholesale, so only newly created
huts pick up the loam default (players can still pick any other block in the hut GUI).

## Build areas are collapse-proof while being built (virtual TFC support) — DONE, in-world test pending

TFC raw stone **collapses** (cave-ins) when mined unsupported, which wrecks any MineColonies schematic with an underground
part — builder basements/cellars, the quarry pit, miner shafts/nodes. Rather than have workers place real TFC support
beams (too expensive/finicky), `:compat` makes the **active build area read as supported** for the duration of the build,
then releases it — by which point the walls are placed (non-falling substituted blocks) and stand on their own.

- **Recon of TFC's collapse/support** (decompiled, `net.dries007.tfc.util.Support` + `CollapseRecipe`): support is data-driven
  (`data/tfc/.../supports/horizontal_support_beam.json`: `support_up/down 2`, `support_horizontal 4`; only **horizontal**
  beams define a supported volume). There are **two** collapse trigger paths, each consulting support at a different method:
  (A) **mining a block** — `ForgeEventHandler.onBlockBroken` → `CollapseRecipe.tryTriggerCollapse`, which (only after a random
  `collapseTriggerChance` gate) calls `Support.findUnsupportedPositions(level, pos±rad)`; (B) **raw-rock random tick** —
  `RawRockBlock` (`random.nextInt(64)==0 && canStartCollapse && !Support.isSupported(level,pos)`). `canStartCollapse` itself
  does **no** support check. Both support queries are rare (behind probability gates) and never per-tick.
- **The implementation** ([BuildAreaSupport](../compat/src/main/java/com/mctfc/collapse/BuildAreaSupport.java) registry +
  [MixinAbstractEntityAIStructure](../compat/src/main/java/com/mctfc/mixin/MixinAbstractEntityAIStructure.java) +
  [MixinSupport](../compat/src/main/java/com/mctfc/mixin/MixinSupport.java)):
  - **Box registry** — `Map<UUID, (dimension, BoundingBox, stamp)>`, server-side, transient. Keyed by worker + tagged with
    dimension (so a box never shields the same coords in another dimension). A **1200-tick TTL** backstops removal: a box not
    refreshed for 60s is pruned lazily in `isProtected` (workers refresh far more often while working). Primary removal is the
    explicit `resetCurrentStructure` clear; the TTL covers the cases with no such signal (miner shaft excavation, removed/idle
    worker).
  - **Register/deregister (schematic path)** — builder/miner/quarrier all extend `AbstractEntityAIStructure`, so one mixin on the
    base covers all: `@Inject` HEAD of `structureStep` registers the schematic's world AABB (computed from `structurePlacer.getB()`
    — `getProgressPosInWorld(0,0,0)`..`(sizeX-1,…)` corners, padded by 1), refreshed every step (idempotent, self-heals after a
    reload); `@Inject` HEAD of `resetCurrentStructure` removes it (the single point MC nulls the structure on completion **and**
    every abandon/error path). Reads the protected `structurePlacer` (raw `Tuple` shadow) + shadowed `getWorker()`.
  - **Miner raw shaft excavation** — the miner digs the vertical shaft in its own AI states (`doShaftMining`/`repairLadder`/
    `doShaftBuilding`) with `structurePlacer == null`, so the schematic hook above never fires there.
    [MixinEntityAIStructureMiner](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIStructureMiner.java) `@Inject`s HEAD of those
    three and registers the **open-shaft column** — the 7×7 `SHAFT_RADIUS` cross-section (offset toward the dig direction, away
    from the cobble, matching the AI's own scan), from the current bottom (`WorkerUtil.getLastLadder`) up to the hut Y, padded 2 —
    keyed by the same worker UUID. Building is reached via `getWorker().getCitizenData().getWorkBuilding()` (not a deep
    `building`-field shadow). The box self-clears via the TTL (and the next node's `resetCurrentStructure`).
  - **Virtual support** — two `@Inject`s on `Support` (the exact methods TFC consults, so behaviour matches real beams for both
    paths, zero polling): `isSupported` HEAD-cancellable → `true` if pos ∈ a box (path B); `findUnsupportedPositions` RETURN →
    `removeIf` returned positions ∈ a box (path A). Added cost is a point-in-AABB test over the ~1–3 active boxes, short-circuited
    when nothing is building — only ever runs inside TFC's already-rare, gated queries.
  - All `@Mixin(remap = false)` (MineColonies/TFC own classes). Server-side. Once the build finishes the box is dropped and normal
    TFC collapse physics resume.

## Builder won't strip tagged world blocks (CLEAR-phase protection) — DONE, in-world test pending

When the MineColonies builder builds (or re-levels) a structure, its **CLEAR phase** removes any existing world block that sits
where the blueprint cell would be cleared — so a block a player placed (or a TFC feature that grew) at a structure position gets
torn out. MineColonies' only built-in exceptions are hardcoded: `IBuilderUndestroyable` blocks (hut cores), bedrock, already-air,
fluids, and Structurize's `blockFluidSubstitution` placeholder — there's no tag/datapack hook to protect arbitrary (especially
vanilla/TFC) blocks. We add one.

The single gate the CLEAR phase consults is `AbstractEntityAIStructure#skipClearing(info, pos, handler)` (returning `true` leaves
the world block in place); it's the only caller of `Operation.BLOCK_REMOVAL` for that phase.
[MixinAbstractEntityAIStructure](../compat/src/main/java/com/mctfc/mixin/MixinAbstractEntityAIStructure.java) (`@Mixin(remap = false)`)
`@Inject`s HEAD-cancellable into `skipClearing` and forces `true` when the **world** block is in the
`#mctfc:builder_dont_clear` block tag ([builder_dont_clear.json](../compat/src/main/resources/data/mctfc/tags/blocks/builder_dont_clear.json)).
- **Air-strip path only, by design.** `skipClearing` governs only the CLEAR (air-strip) phase. Normal placement — the blueprint
  laying a *real* block over the spot — is untouched (it's not a clear, so the protected block is overwritten as usual), and the
  deliberate building-demolition phase has its own `skipRemoval` gate we intentionally leave alone. So the tag means "don't *erase*
  this to empty space," not "this block is indestructible."
- **Covers the quarrier too.** `EntityAIQuarrier` overrides `skipClearing` but calls `super` first and returns early when it's
  `true`, so this base-class injection applies there as well.
- **Empty by default (opt-in).** The shipped tag is empty — the feature does nothing until a datapack adds block ids (or `#tag`
  references) and `/reload`s. Cost is one `BlockState#is(tag)` test per cleared position.

## Citizens rest for TFC's localized rain (not the global flag) — DONE, in-world test pending

MineColonies citizens stop working and "rest" (the `IDLE`/`BAD_WEATHER` branch of `CitizenAI#calculateNextState`) when
`Level#isRaining()` is true. That's the **global** dimension-wide vanilla flag. TFC keeps the vanilla weather cycle but
makes rain **localized**: `EnvironmentHelpers.isRainingOrSnowing(level, pos) = level.isRaining() && WorldTracker.get(level).isRaining(tick, Climate.getRainfall(pos))`
— precipitation only actually falls where the position's annual rainfall beats the current event intensity, and TFC's
temperature decides rain vs snow (`Climate.getPrecipitation`). TFC also redirects `Level#isRainingAt(pos)` through this
model (its `LevelMixin`). So under TFC the global flag over-triggers: citizens rest during any rain *cycle* even when their
colony sits in an arid cell where nothing falls (and `world.isRaining()` is likewise on during snow, exactly like vanilla).

[MixinCitizenAI](../compat/src/main/java/com/mctfc/mixin/MixinCitizenAI.java) (`@Mixin(remap = false)`, `@Redirect` the lone
`Level#isRaining()` call in `calculateNextState`; the vanilla target's `@At` is `remap = true`, the enclosing injector
isn't) replaces it with `EnvironmentHelpers.isRainingOrSnowing(world, anchor)` — **rain *or* snow** actually falling at the
anchor, preserving vanilla's stop-for-any-precipitation behaviour but localized to the colony.
- **Fixed anchor, not the live citizen position** — the anchor is the citizen's **work building** (`getCitizenData().getWorkBuilding().getPosition()`),
  else the **colony center** (`getCitizenColonyHandler().getColonyOrRegister().getCenter()`), else the citizen as a last
  resort (shadows the `private final EntityCitizen citizen` field). This is deliberate: TFC rain is localized, so sampling
  the *moving* citizen at a rain border creates a feedback loop (in rain → rest → wander to a dry spot → work → walk back
  into rain → rest …) that flip-flops every decide cycle (~10 ticks / 0.5 s; see the cadence note). Anchoring to the
  worksite makes the decision "is it raining on my job?" — stable wherever the citizen wanders, so transitions happen only
  when the local weather genuinely starts/stops (TFC's triangular intensity ramp crosses the site's rainfall threshold
  ~once up, once down per ~18000–24000-tick event). Per-hut localization is a feature: a hut on the dry side of a border
  keeps working while one on the wet side rests.
- **Cheap:** the redirect runs ~2×/s per active citizen (the `decideAiTask` cadence) and is an O(1) `isRaining()` field read
  AND a cached chunk-rainfall lookup — no throttling needed. `:compat` depends on TFC directly, so importing
  `net.dries007.tfc.util.EnvironmentHelpers` is fine. Guards bypass this branch entirely (they work in rain) so are
  unaffected. **Follow-up if wanted:** make it rain-only (`Climate.getPrecipitation(world, anchor) == RAIN`, a deliberate
  change from vanilla snow-parity), and/or expose a config toggle.

## TFC light sources never burn out inside a colony — DONE, in-world test pending

TFC light sources burn out: the **metal lamp** (`tfc:metal/lamp/<metal>` — there is **no** `tfc:lantern`; TFC removes the
vanilla lantern, the lamp is its equivalent) drains a fluid fuel (olive_oil/tallow; lava in a blue-steel lamp is the only
infinite vanilla case) and goes dark when empty; **torches** decay to `tfc:dead_torch` after `torchTicks` (~1h), **candles**
go out, **jack-o'-lanterns** revert to carved pumpkins. All burn-out is server-side, per-position, on `randomTick`, backed by
a `TickCounterBlockEntity` (calendar-delta, unload-safe); TFC's only "off switch" is global (`torchTicks/candleTicks/jackOLanternTicks = -1`),
not colony-scoped. So colonies go dark unless re-lit/refuelled.

`:compat` freezes the burn-out of **already-lit** sources while they sit inside a colony (it won't relight a dead one or fuel
an unlit lamp). Gate: [ColonyLights](../compat/src/main/java/com/mctfc/light/ColonyLights.java)`#keepLit(level, pos)` =
server-side && `Config.keepColonyLightsLit` && `IColonyManager.getColonyByPosFromWorld(level, pos) != null` (the
chunk-owning-colony cap). Two mixins (`@Mixin(remap=…)` as noted):
- [MixinTfcLightBlocks](../compat/src/main/java/com/mctfc/mixin/MixinTfcLightBlocks.java) — **one multi-target** `@Mixin({TFCTorchBlock,
  TFCWallTorchBlock, TFCCandleBlock, TFCCandleCakeBlock, JackOLanternBlock})`; all five override the same
  `randomTick(BlockState, ServerLevel, BlockPos, RandomSource)`, so a single `@Inject(HEAD, cancellable)` cancels the whole
  tick (burn-out, and for candles the rain-extinguish too) when in-colony. `randomTick` is a **vanilla** `Block` method →
  **remapped** (the default; do NOT set `remap=false` here, unlike most `:compat` mixins which target the mods' own members).
- [MixinLampBlockEntity](../compat/src/main/java/com/mctfc/mixin/MixinLampBlockEntity.java) — `@Mixin(remap=false)` (TFC's own
  method) `@Inject(HEAD, cancellable)` on `LampBlockEntity#checkHasRanOut` (the single fuel-drain chokepoint, also called from
  `use`/`fluidTankChanged`, so hooking it covers all drain paths). `level`/`pos` via casting `this` to vanilla `BlockEntity`.
- Cheap (random ticks are infrequent; the colony lookup is a chunk-cap read). Config
  [Config](../compat/src/main/java/com/mctfc/Config.java)`#keepColonyLightsLit` (default **true**; false ⇒ TFC burnout applies
  everywhere). **Verified to load** (both mixins bind — `defaultRequire:1` would fail otherwise — client reaches menu, 0
  injection failures); in-world behaviour (lamp/torch/candle/jack staying lit in a colony, decaying outside) still to confirm.

## Colonists avoid TFC heat sources (pathfinding) — DONE, in-world test pending

MineColonies' pathfinder treats certain blocks as "dangerous to stand on/in" and routes citizens around them
(`PathfindingUtils.isDangerous` → not passable/walkable unless `pathingOptions.canPassDanger()`). The vanilla
list is hardcoded (`FireBlock`, `CampfireBlock`, `MagmaBlock`, sweet berry bush, powder snow, lava cauldron)
**plus** anything in the `minecolonies:dangerousblocks` block tag (which MineColonies ships **empty**). TFC's
heat blocks aren't vanilla `CampfireBlock`/`FireBlock`, so without help colonists happily walk through a lit
firepit.

`:compat` adds TFC's **contact-damaging** heat blocks to that tag
([data/minecolonies/tags/blocks/dangerousblocks.json](../compat/src/main/resources/data/minecolonies/tags/blocks/dangerousblocks.json),
`"replace": false` so it merges): `tfc:firepit`, `tfc:grill`, `tfc:pot` (the latter two extend `FirepitBlock`),
`tfc:charcoal_forge`, and `tfc:molten`. These are exactly the TFC blocks whose block class overrides `stepOn`
to hurt the entity (confirmed by decompiling — `death.attack.tfc.grill`/`pot` are TFC's own death messages).
**Deliberately excluded** as non-damaging-to-stand-on: bloomery, crucible, blast furnace, and TFC torches
(interact-only devices / light blocks — tagging them would make citizens needlessly avoid harmless decoration).
Like the vanilla campfire treatment, the tag is block-level, so an *unlit* firepit/forge is avoided too
(conservative, but matches how MineColonies already treats `instanceof CampfireBlock`). Datapack-only — no code.

## Miner lucky-ore drops are TFC ores — DONE, in-world test pending

The MineColonies miner has a "lucky block" mechanic: each block it mines that's in `minecolonies:orechanceblocks`
(ships as `#forge:stone` + `#minecraft:base_stone_overworld/nether` — i.e. **stone**, not ore) rolls a
`luckyblockchance`% chance (server config, default 1; × the `MoreOres` research) to additionally drop from the
loot table `minecolonies:miner/lucky_ore<hutLevel>` (1–5). Stock MineColonies fills those tables with **vanilla
ore blocks** (coal/copper → … → diamond/emerald), which are off-progression in TFC — and since TFC ore is rare,
a colony miner mostly just chews through stone, so this lucky roll is effectively the miner's whole ore output.

`:compat` overrides all five tables ([data/minecolonies/loot_tables/miner/lucky_ore{1..5}.json](../compat/src/main/resources/data/minecolonies/loot_tables/miner/lucky_ore1.json),
last-datapack-wins) to drop TFC **rich** ore nuggets (`tfc:ore/rich_<ore>`, ≈⅓ ingot each when melted). The
trigger needs no change — TFC raw/hardened rock is already in `forge:stone`. Tiers are **cumulative** and map
**hut level → TFC metal age** (so upgrading the miner advances the colony's metallurgy), with weights by
geological rarity:
- **L1 Copper** — native copper / malachite / tetrahedrite (w48).
- **L2 + Bronze base** — cassiterite (tin) / bismuthinite / sphalerite (zinc) (w28).
- **L3 + Black bronze** — native silver / native gold (w6).
- **L4 + Iron** — hematite / magnetite / limonite (w32 — iron becomes the bulk find once unlocked).
- **L5 + Steel** — garnierite (nickel) (w3).

Each table is a single 1-roll pool (one nugget per successful proc), mirroring the vanilla structure it replaces.
**Rich** (not small/normal) was chosen deliberately: TFC ore scarcity means this is the miner's main metal
source, so the find should be worth it. Datapack-only — no code. Tune via the per-ore weights or vanilla's
`luckyblockchance` config.

## Blacksmith smiths TFC anvil products, gated by a Smithing research branch — DONE, in-world test pending

Two halves: a datapack-only **research branch** and a code **recipe bridge**.

**Research chain** (`mctfc:smithing/*`, [data/mctfc/researches/smithing/](../compat/src/main/resources/data/mctfc/researches/smithing/bronze.json)):
five researches living in the **stock Technology branch** (`"branch": "minecolonies:technology"` — no new tab),
chained under **Hitting Iron** (the stock research that unlocks the blacksmith hut) via `parentResearch`:
hittingiron → bronze → wrought iron → steel → black steel → red/blue steel. The loader requires
`researchLevel` = parent's + 1, so the chain runs levels 2–6 — exactly MineColonies' max depth of 6 (and 6 was
only available because hittingiron sits at level 1; a deeper anchor would not fit). Each consumes the matching
**TFC anvil** as its cost: bronze uses the `tfc:bronze_anvils` **item tag** (`item_tag` cost — any of the 3
bronzes; there is **no copper research** — stone/copper-tier work is unlocked by default, see below), wrought
iron / steel / black steel are `item_simple` single items, and the final tier is an `item_list` cost (red **or**
blue steel anvil — they share no tag). Each tier also requires the **blacksmith's hut at level 1–5** via a
`single-building` requirement (one hut at that level — deliberately not the cumulative `building` type, where
two level-2 huts would satisfy "level 4"). Each research grants a custom **unlock effect**
(`mctfc:effects/smithing_<tier>` — data-defined `{"effect": true}` files; descriptions via the auto-derived lang
keys `com.mctfc.research.effects.<name>.description` in
[en_us.json](../compat/src/main/resources/assets/mctfc/lang/en_us.json)). MineColonies' research tree is fully
datapack-driven (`ResearchListener`, a `SimpleJsonResourceReloadListener` over `researches/` in all namespaces),
so researches in our namespace merge straight into a stock branch.

**Recipe bridge** ([AnvilRecipeBridge](../compat/src/main/java/com/mctfc/smithing/AnvilRecipeBridge.java) +
[MixinCustomRecipeManager](../compat/src/main/java/com/mctfc/mixin/MixinCustomRecipeManager.java)): every
`tfc:anvil` and `tfc:welding` recipe in the loaded recipe set becomes a **blacksmith** (`blacksmith_crafting`)
`CustomRecipe`, research-gated via its `research-id` ← the unlock effect matching the recipe's **minimum anvil
tier** (≤1 → **ungated**, stone/copper-tier work is craftable out of the box; 2 → bronze, 3 → wrought iron,
4 → steel, 5 → black steel, 6 → red/blue). **Tools never appear by construction** — TFC smiths tool *heads* on the anvil (tool = head + stick in the grid), so the bridged set is
exactly heads, sheets, double ingots/sheets, rods, shears, tuyeres, plated blocks, etc. Inputs are taken verbatim
from the TFC recipe (first stack of each ingredient); **welding adds one `#tfc:flux`** item, mirroring the anvil
flux slot, and same-item welding inputs merge into one count-2 entry. Heating the work piece and the forging
minigame are abstracted away, like every other colony crafter's process. Works for *any* mod's TFC anvil recipes,
not just TFC's own — and needs no per-item datapack maintenance.

The injection seam (the hard-won bit): programmatic recipes must survive `CrafterRecipeListener.apply`'s
`reset()` on every reload and be present before the recipe map syncs to clients. MineColonies'
`DataPackSyncEventHandler` calls `CustomRecipeManager.resolveTemplates()` once per datapack sync — after the
listener reset+reload, with the server `RecipeManager` loaded and tags bound, right before
`sendCustomRecipeManagerPackets`. So the bridge injects from a `remap = false` TAIL mixin on `resolveTemplates`
(re-runs are idempotent — `addRecipe` overwrites by recipe id; bails when no server, e.g. client-side calls).
Recipe-gating semantics: `CustomRecipe.isUnlockEffectResearched` accepts a research id (completed-research check)
*or* an effect id (effect-strength check) — we use effect ids, matching the stock `assistanthammerunlock` pattern.

## Lumberjack fells + replants TFC trees — DONE, in-world test pending

The Lumberjack already **finds and chops** TFC trees with no help: TFC logs/leaves/saplings piggyback the vanilla
`#minecraft:logs` / `#minecraft:leaves` / `#minecraft:saplings` tags, and `Tree.checkTree`'s log gate is
`state.is(minecolonies:tree)` (= `#minecraft:logs` + …), its leaf gate counts `#minecraft:leaves`, and its
"on solid ground" gate is just *any solid that isn't vanilla cobblestone* — so TFC grass/dirt pass. (TFC **fruit**
trees are skipped — their branches aren't logs.) Auto-discovery of the leaf→sapling mapping and TFC axes both work.

The one gap was **replanting**. `EntityAIWorkLumberjack.placeSaplings` plants only when
`block.canSustainPlant(soilBelow, …)` is true, and Forge's default `canSustainPlant` for a `PlantType.PLAINS`
sapling requires the soil ∈ `BlockTags.DIRT`. Under TFC, `#minecraft:dirt` = `#tfc:dirt` (bare dirt/rooted/muddy)
and **excludes `#tfc:grass`** — but wild TFC trees stand on grass. So vanilla rejected every grass-grown tree and
the worker pulled the stump without replanting → forest depletion.

[MixinEntityAIWorkLumberjack](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkLumberjack.java)
`@WrapOperation`s that single `canSustainPlant` call (MixinExtras; `remap = false` — MineColonies' own method,
Forge's own `canSustainPlant`): keep the original result, else ask the plant itself —
`plantable.getPlant(level, soilPos.above()).canSurvive(reader, plantPos)`. For a TFC sapling that defers to
`TFCSaplingBlock.mayPlaceOn`, which accepts `#tfc:bush_plantable_on` (= `#minecraft:dirt` + `#tfc:grass` +
`#tfc:farmland`). General + conservative: it only allows a replant where the sapling can genuinely live, so it
never plants where it shouldn't and hard-codes no TFC tag. Chosen over adding `#tfc:grass` to `#minecraft:dirt`
(too broad — many systems read `BlockTags.DIRT`).

**Whole-tree felling.** TFC gives every axe a one-hit felling behavior, wired in `ForgeEventHandler.onBlockBroken`
to a `BlockEvent.BreakEvent` — which only fires for a real **player**. The citizen breaks via `world.removeBlock`,
so it never triggers; the worker climbs the trunk log-by-log (slow, and gets stuck on tall trees). The same mixin's
second `@WrapOperation` wraps the per-log `mineBlock` call in `chopTree`: when TFC's own
`AxeLoggingHelper.shouldLog(level, pos, state, axe)` approves (`isLoggingAxe && isLoggingBlock && !isPartOfLargerTrunk`)
it spends a chop delay scaled by the tree's log count (`AxeLoggingHelper.findLogs(...).size()` × the normal
per-log `getBlockMiningTime`, reached via the public method and the `mctfc$hasNotDelayed` invoker — so axe tier /
skill / research and tree size all matter), then calls `AxeLoggingHelper.doLogging(level, pos, fakePlayer, axe)` —
felling the whole connected trunk at once. During that delay it sets `currentWorkingLocation` to the base log (via a
`mctfc$setCurrentWorkingLocation` accessor) so MineColonies' own `waitingForSomething()` swings the axe each tick
(`hitBlockWithToolInHand`) — the worker chops visibly instead of standing idle, just like normal mining.
`doLogging` uses `level.destroyBlock(pos, true, breaker)` so logs drop **at their own positions** (the gathering
phase collects them) and `axe.hurtAndBreak` so the worker's axe wears per log, exactly like a player. The
**fake player** comes from MineColonies' own `AbstractEntityAIBasic.getFakePlayer()` (reached via the existing
[AbstractEntityAIBasicInvoker](../compat/src/main/java/com/mctfc/mixin/AbstractEntityAIBasicInvoker.java)), and the
axe from [AbstractAISkeletonAccessor](../compat/src/main/java/com/mctfc/mixin/AbstractAISkeletonAccessor.java) —
both avoiding the inherited-field `@Shadow` trap. The leaf path, non-trunk logs, and 2×2 trunks (where `shouldLog`
is false) fall through to the unchanged single-block break, so it's faithful to TFC by construction. No fake
`BlockEvent.BreakEvent` is posted, so there's no recursion.

**Cut from the base, not the top.** `Tree` sorts `woodBlocks` ascending by distance from the base but hands them
out from the end (`peekNextLog`/`pollNextLog` = `peekLast`/`pollLast`) — topmost first (vanilla's "work down so
nothing floats"). With felling that reads as nonsense (the worker reaches the *top* to drop the tree).
[MixinTree](../compat/src/main/java/com/mctfc/mixin/MixinTree.java) flips both ends to `peekFirst`/`pollFirst` so
the forester cuts the **base** log — where `shouldLog` fells from. peek and poll must flip together (peeking the
base but polling the top would never remove the base → infinite loop). `woodBlocks` is declared in `Tree` itself,
so the `@Shadow` is safe.

**Planted saplings grow on a timer.** A `TFCSaplingBlock` grows when its `TickCounterBlockEntity` passes
`daysToGrow`, and only `setPlacedBy` resets that counter. The worker plants with `Level#setBlockAndUpdate` (never
`setPlacedBy`), so the counter stayed at its sentinel → read as ancient → the sapling sprouted on its first random
tick. A third `@WrapOperation` (on the `setBlockAndUpdate` in `placeSaplings`; `@At` `remap = true` since it's
vanilla, injector `remap = false` for the MineColonies method) calls `TickCounterBlockEntity.reset(level, pos)`
after a successful placement — a no-op for blocks without that counter BE (vanilla saplings, the nylium under a
fungus). Planted TFC saplings now wait their normal grow time.

**TFC log crafting recipes (replacing the vanilla defaults).** MineColonies ships two `recipe-template`s for the
lumberjack — `strip_logs` and `strip_stems` (in `data/minecolonies/crafterrecipes/lumberjack/`) — that expand over
`#minecraft:logs` using vanilla `_log`/`_stem` naming into one recipe per vanilla log (`1 oak_log → {stripped_log,
oak_wood, stripped_oak_wood}`). TFC logs are named `tfc:wood/log/<wood>` (no `_log` suffix), so they're filtered out
— the vanilla recipes are dead clutter in a TFC world and TFC logs get none.

We ship [tfc_strip_logs.json](../compat/src/main/resources/data/mctfc/crafterrecipes/lumberjack/tfc_strip_logs.json)
— a `recipe-template` over `#minecraft:logs` filtered to the `tfc:wood/log/` path, whose `[PATH:wood/log/=…]`
substitutions produce each log's stripped-log / wood / stripped-wood (`alternate-output`, like vanilla). The
all-namespace `crafterrecipes` scan picks it up; non-existent forms are pruned by MineColonies.

The vanilla `strip_logs`/`strip_stems` children are removed in
[LumberjackRecipes](../compat/src/main/java/com/mctfc/crafting/LumberjackRecipes.java), called from
`MixinCustomRecipeManager` at the `resolveTemplates()` TAIL (after both templates expand). The `remove` recipe type
only deletes by exact id, impractical against a template's per-log fan-out, so we prune by id prefix
(`minecolonies:lumberjack/strip_logs/…`, `…/strip_stems/…`) — the bamboo recipe and our TFC recipes are kept.

**AFC + Beneath** woods share TFC's `<ns>:wood/log/<wood>` naming and join `#minecraft:logs`, so the same template
(namespace/filter swapped) is shipped in the mod-gated datapacks:
[afc_strip_logs.json](../compat/src/main/resources/afc_datapack/data/mctfc/crafterrecipes/lumberjack/afc_strip_logs.json)
(filter `afc:wood/log/`) and
[beneath_strip_logs.json](../compat/src/main/resources/beneath_datapack/data/mctfc/crafterrecipes/lumberjack/beneath_strip_logs.json)
(filter `beneath:wood/log/`). They only load when `afc`/`beneath` is present (AfcDataPack/BeneathDataPack), and
need no extra removal (MineColonies has no AFC/Beneath log recipes to begin with).

**Caveat — saplings come from leaves, not felling.** TFC logs never drop saplings (only leaves do, ~1.3% on
**break**), and TFC leaf **decay** removes leaves with *no drops* (`removeBlock(pos, false)`). So a felled trunk
yields no saplings on its own, and the leaves left behind decay to nothing — the forester only self-harvests
saplings if it **breaks** the leaves (the hut's "harvest leaves"/defoliate setting), or is stocked with saplings.

## Non-falling ("mortared"/"cemented") cobble — MOVED to firmavanilla

The cemented-cobble twin system (registry scan, runtime data pack, model delegation, in-world mortar
conversion) now lives in the standalone **firmavanilla** mod, which `:compat` hard-depends on — see
[docs/firmavanilla.md](firmavanilla.md). `:compat` still owns the **substitution** side (plain datapack, see
"TFC default substitutions" above): [tfc_stone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json)
fixes `minecraft:cobblestone → firmavanilla:mortared/tfc/rock/cobble/dacite` (the non-falling dacite twin) and
offers the `firmavanilla:mortared_cobblestone` pool keyed on that converted twin, so the player re-picks the
rock via the Replace GUI. **Gotcha:** the fixed default and the pool must have **distinct sources** (fixed on
`minecraft:cobblestone`, pool on the `firmavanilla:mortared_cobblestone` tag that matches the *converted* twin)
— a fixed `to` and a `to_tag` on the *same* source shadows the pool under converted-block semantics.

## Vanilla furnaces made decorative — DONE

TFC overhauls smelting/cooking (firepit/forge/bloomery/…), so the vanilla furnace, smoker and blast furnace
shouldn't be usable to bypass it — but MineColonies blueprints still place them.
[VanillaFurnaceHandler](../compat/src/main/java/com/mctfc/block/VanillaFurnaceHandler.java) (Forge bus,
annotation-registered like firmavanilla's [MortaredCobbleInteraction](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleInteraction.java))
cancels `PlayerInteractEvent.RightClickBlock` for the **three exact vanilla blocks** (`Blocks.FURNACE/SMOKER/
BLAST_FURNACE` — not `AbstractFurnaceBlock`, so modded furnaces are untouched) so their GUI never opens.
**Two dead ends, both verified in-game:** (1) cancelling only when not sneaking leaks the GUI on a sneak-click
with an empty hand (vanilla still calls `use()`); (2) `setUseBlock(DENY)` did **not** gate the menu in this
interaction path (furnaces stayed fully openable). So we cancel the whole event for **every** right-click on
these blocks. Cost: you can't place a block by aiming at a furnace face (place against a neighbour). The block
stays placed and breakable, and MineColonies worker AI drives furnaces via the block entity (not this
interaction) so automation is unaffected. Gated by `Config.decorativeVanillaFurnaces` (`config/mctfc-common.toml`, default **true**; set
false to restore vanilla furnace use). Scope note: blocks the **player GUI** only — it doesn't stop a hopper
from feeding a furnace or the block entity from ticking.

**TFC-flavored crafting recipes** ([data/mctfc/recipes/](../compat/src/main/resources/data/mctfc/recipes/)) so these
blocks are still obtainable in a TFC world (vanilla recipes need `minecraft:cobblestone`/`minecraft:iron_ingot`
that TFC players lack), all `mctfc:`-namespaced so they add to whatever TFC leaves: `furnace` = vanilla ring of
`#forge:cobblestone/normal` (includes vanilla + every TFC rock cobble); `smoker` = furnace + 4 `#minecraft:logs`
(TFC logs are in it); `blast_furnace` = furnace + 5 `#forge:ingots/wrought_iron` + 3 `tfc:ceramic/fire_brick`.
Also `bookshelf` = the vanilla recipe (6 `#minecraft:planks` + 3 `minecraft:book`) restored — the bookshelf→TFC
substitution was dropped, so vanilla bookshelves stay vanilla and need a recipe. (There is **no** `barrel` recipe:
firmavanilla now ships per-wood barrels, so the vanilla barrel is left vanilla and uncraftable in TFC.)

**Vanilla barrel is no longer touched.** Earlier a `MixinBarrelBlockEntity` here shrank the *vanilla* barrel to
`tfc:chest` rules (18 slots + item-size limit). That was **removed** along with the barrel recipe — the small-chest
behaviour now lives on firmavanilla's **own** barrel blocks (`firmavanilla:barrel/<wood>`, via `BarrelBlockEntityFV`,
reusing TFC's `RestrictedChestContainer`/`CHEST_9x2`/`TFCChestBlockEntity.isValid`), the way TFC ships its own small
chests rather than by mixing into vanilla. See [docs/firmavanilla.md](firmavanilla.md) → "Wood barrels".

## Optional per-mod datapacks (Beneath) — pattern

To ship data that should apply **only when an optional mod is present**, register a built-in datapack gated on
`ModList.isLoaded(...)` at `AddPackFindersEvent` — so when the mod is absent the pack isn't registered at all and
its `<thatmod>:*` rules never load or warn. [BeneathDataPack](../compat/src/main/java/com/mctfc/data/BeneathDataPack.java)
does this for **Beneath** (`beneath`): a `PathPackResources` rooted at the jar sub-folder
[beneath_datapack/](../compat/src/main/resources/beneath_datapack/) (its own `pack.mcmeta` + `data/mctfc/block_substitutions/beneath.json`),
forced-on `SERVER_DATA`, same mechanism as firmavanilla's [MortaredCobbleData](../firmavanilla/src/main/java/com/firmavanilla/data/MortaredCobbleData.java)
but conditional + static files. Registered from the mod ctor. Beneath itself is a **dev-run dependency**
(`implementation fg.deobf("curse.maven:beneath-1113980:7400831")` in [compat/build.gradle](../compat/build.gradle)).
Its `beneath.json` maps the vanilla **nether woods 1:1 to Beneath's crimson/warped wood** (planks/log→`wood/log`,
hyphae→`wood/wood`, stripped, stairs/slab/fence/door/etc.) — Beneath ships a full TFC-style crimson/warped wood
set (`beneath:wood/planks/crimson`, …), so these are real per-form swaps, not placeholders. Generated by the same
[gen_tfc_substitutions.sh](../compat/gen_tfc_substitutions.sh) (its `beneath_wood` section). (No nether-brick rule:
Beneath provides a way to craft vanilla nether bricks directly.)

The Beneath woods also **join the per-form candidate pools** like any normal wood: the Beneath pack ships
*additive* (`replace:false`) tag files at `data/mctfc/tags/blocks/subst/wood/<form>.json` that merge crimson/warped
into the base `mctfc:subst/wood/<form>` pools — but **only when Beneath is loaded** (the files live in the Beneath
pack), so the base pools stay TFC-only and error-free when it's absent. No new candidate rules are needed (the base
`from_tag=to_tag` rules already cover the merged tag), so a crimson plank can be re-picked to warped or any TFC
wood, and vice-versa.

