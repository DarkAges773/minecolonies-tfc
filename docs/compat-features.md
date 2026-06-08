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
  `tfc:wood/lectern/<wood>`). Plus a per-form candidate pool so the player can pick any TFC wood. The
  **nether woods (crimson/warped) are NOT mapped to TFC here** — they're handled 1:1 by the optional Beneath
  datapack (Beneath ships real crimson/warped wood); see "Optional per-mod datapacks".
- **Stone** ([tfc_stone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json)): vanilla
  stone family → **dacite** forms (closest look) — `stone→raw`, `stone_bricks→bricks`, `smooth_stone→smooth`,
  mossy/cracked/chiseled likewise, all with stairs/slabs/walls. Cobble and mossy-cobble map to the **non-falling
  mortared dacite twin** (`mctfc:mortared/tfc/rock/.../dacite`) so builds survive TFC gravity; their stairs/slabs/
  walls (which don't landslide) use plain TFC. `minecraft:stone_button` → `tfc:rock/button/dacite`. Vanilla
  **granite/diorite/andesite** (which are real TFC rock types) map to the **same** rock — plain → `tfc:rock/raw/<rock>`,
  polished → `tfc:rock/smooth/<rock>` (+ stairs/slabs/walls). Per-form candidate pools let the player pick any
  TFC rock — the cobble/mossy-cobble full-block pick reuses the runtime `mctfc:mortared_cobblestone` pool, and
  granite/diorite/andesite reuse the existing `raw`/`smooth` pools.
- **Sandstone** ([tfc_sandstone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_sandstone.json)):
  **pool-only, no implicit swap** — vanilla sandstone is accessible in TFC so it stays the default, but every
  variant (normal + red, raw/cut/smooth + stairs/slabs/walls) offers a *Replace* pool of TFC colored sandstones
  (`tfc:{raw,smooth,cut}_sandstone/<color>`, all 7 colors) of the matching form. This is the `from` → `to_tag`
  pattern (pool keyed directly on the vanilla block, since nothing converts it first).
- **Pool tags** live under `data/mctfc/tags/blocks/subst/{wood,rock}/*.json` (one per form, listing every TFC
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

## Non-falling ("mortared"/"cemented") cobble — DONE & verified

TFC makes cobble collapse (gravity), which wrecks MineColonies cobble builds. `:compat` registers a
**non-falling twin** of every cobble block and substitutes builds onto it.

- **Why a twin block, not a property/mixin:** TFC's falling is **tag-gated** — `tfc:can_landslide` lists
  `minecraft:cobblestone`/`mossy_cobblestone` and every `tfc:rock/cobble|mossy_cobble/<rock>`, checked per
  *block* (not per state). You can't add a blockstate property to an existing block (its `StateDefinition`
  is frozen at construction), and even if you could, TFC reads the tag, not a property. So the surgical
  fix is a separate block that simply isn't in `can_landslide`. (Same technique as MehVahdJukaar's
  StoneZone/Moonlight: registry scan + naming detection + runtime-generated assets.)
- **Scan + register** ([MortaredCobbleRegistry](../compat/src/main/java/com/mctfc/block/MortaredCobbleRegistry.java)):
  on `RegisterEvent`, iterate `ForgeRegistries.BLOCKS` and register a
  [MortaredCobbleBlock](../compat/src/main/java/com/mctfc/block/MortaredCobbleBlock.java) (`extends Block`,
  `Properties.copy(source)`, drops self, name "Mortared &lt;source&gt;") + a
  [MortaredCobbleBlockItem](../compat/src/main/java/com/mctfc/block/MortaredCobbleBlockItem.java) per cobble,
  id `mctfc:mortared/<source-ns>/<source-path>`. **Detection is a name heuristic** (`isCobble`: path ends
  `cobblestone` or contains a `cobble/` segment, minus `_stairs/_slab/_wall/_button/_pressure_plate` and
  `infested`) — tags are unavailable at registration; the heuristic is anchored to reproduce
  `forge:cobblestone/normal`. **Only sees blocks registered before `mctfc`** (mods.toml orders it AFTER
  tfc) — a cobble mod loading after us isn't covered.
- **Client model delegation** ([MortaredCobbleClient](../compat/src/main/java/com/mctfc/client/MortaredCobbleClient.java)):
  twins ship no blockstate/model JSON, so `ModelEvent.ModifyBakingResult` repoints each twin's baked block
  + item model at its source's. The bakery logs a benign "missing model" per twin during load — expected,
  overwritten here. (`getModels()` is keyed by `ResourceLocation`, not `ModelResourceLocation`.)
- **Runtime data pack** ([GeneratedDataPack](../compat/src/main/java/com/mctfc/data/GeneratedDataPack.java) +
  [MortaredCobbleData](../compat/src/main/java/com/mctfc/data/MortaredCobbleData.java)): the twins are dynamic
  so the tag/recipes can't be static JSON. At `AddPackFindersEvent` (twins already registered) we serve an
  in-memory **forced built-in** `SERVER_DATA` pack with `mctfc:mortared_cobblestone` (all twins) + a
  **shaped** recipe per twin (the cobble surrounded by 4 `#tfc:mortar`, cross pattern). The pack also makes
  twins behave/identify like normal cobble by adding `#mctfc:mortared_cobblestone` (tag-of-tags) to the
  block tags real cobble sits in — `minecraft:mineable/pickaxe`, `forge:cobblestone/normal`,
  `tfc:can_carve`, `tfc:toughness_2` — but deliberately **not** `tfc:can_landslide` (that's the gravity
  we're escaping).
- **In-world conversion** ([MortaredCobbleInteraction](../compat/src/main/java/com/mctfc/block/MortaredCobbleInteraction.java),
  Forge bus): right-click a cobble holding `#tfc:mortar` → swap to its twin, consume 4 mortar (free in
  creative). Cancels the interaction; server-authoritative.
- **Substitution** is plain datapack (see "TFC default substitutions" above):
  [tfc_stone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json) fixes
  `minecraft:cobblestone → mctfc:mortared/tfc/rock/cobble/dacite` (the non-falling dacite twin) and offers the
  `mctfc:mortared_cobblestone` pool keyed on that converted twin, so the player re-picks the rock via the Replace
  GUI. **Gotcha:** the fixed default and the pool must have **distinct sources** (fixed on `minecraft:cobblestone`,
  pool on the `mctfc:mortared_cobblestone` tag that matches the *converted* twin) — a fixed `to` and a `to_tag` on
  the *same* source shadows the pool under converted-block semantics.

## Vanilla furnaces made decorative — DONE

TFC overhauls smelting/cooking (firepit/forge/bloomery/…), so the vanilla furnace, smoker and blast furnace
shouldn't be usable to bypass it — but MineColonies blueprints still place them.
[VanillaFurnaceHandler](../compat/src/main/java/com/mctfc/block/VanillaFurnaceHandler.java) (Forge bus,
annotation-registered like [MortaredCobbleInteraction](../compat/src/main/java/com/mctfc/block/MortaredCobbleInteraction.java))
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
substitution was dropped, so vanilla bookshelves stay vanilla and need a recipe. `barrel` = vanilla shape with
`#minecraft:planks` + `#minecraft:wooden_slabs`.

**Vanilla barrel matches `tfc:chest`** ([MixinBarrelBlockEntity](../compat/src/main/java/com/mctfc/mixin/MixinBarrelBlockEntity.java),
targets a vanilla class so it's remapped): **18 slots** (two rows) instead of 27, and the same item-size limit
(items at/below `TFCConfig.SERVER.chestMaximumItemSize`, default LARGE). **Gotcha (cost a wrong first attempt):**
`Container#canPlaceItem` does **not** gate the chest GUI — vanilla `ChestMenu` slots use base `Slot#mayPlace`
(always `true`) and never call it, so overriding `canPlaceItem` alone let oversized items in. The fix reuses
TFC's own `RestrictedChestContainer` (its `RestrictedSlot#mayPlace` calls the static `TFCChestBlockEntity.isValid`)
via the `TFCContainerTypes.CHEST_9x2` menu type, so opening the barrel shows TFC's 2-row chest screen with the
size restriction. Pieces: backing list (`@ModifyConstant` 27→18 in `<init>`) + `getContainerSize` (→18) — both
required since the `RestrictedChestContainer` ctor asserts an 18-slot container; `createMenu` → the TFC menu; and
`canPlaceItem` → the same static `isValid` so hoppers / the Forge item-handler wrapper honour it too. No runtime
config toggle: the list size is fixed at construction, so a live flip would desync.

## Optional per-mod datapacks (Beneath) — pattern

To ship data that should apply **only when an optional mod is present**, register a built-in datapack gated on
`ModList.isLoaded(...)` at `AddPackFindersEvent` — so when the mod is absent the pack isn't registered at all and
its `<thatmod>:*` rules never load or warn. [BeneathDataPack](../compat/src/main/java/com/mctfc/data/BeneathDataPack.java)
does this for **Beneath** (`beneath`): a `PathPackResources` rooted at the jar sub-folder
[beneath_datapack/](../compat/src/main/resources/beneath_datapack/) (its own `pack.mcmeta` + `data/mctfc/block_substitutions/beneath.json`),
forced-on `SERVER_DATA`, same mechanism as [MortaredCobbleData](../compat/src/main/java/com/mctfc/data/MortaredCobbleData.java)
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

