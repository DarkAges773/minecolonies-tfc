# TFC bloomery Smelter — design

How `:compat` (`mctfc`) makes the MineColonies **Smelter** (Smeltery hut) tend **player-built TFC
bloomeries** for iron — the worker loads iron ore + charcoal, lights each bloomery, and collects the
`tfc:raw_iron_bloom` it produces. Bloomeries are marked by the player with a **wand taken from the hut**,
exactly like the (FirmaLife) Beekeeper marks hives — up to a per-hut-level cap.

Status legend: **DONE** (built & in-tree), **PLANNED** (designed here, not yet built).

**Status: ALL slices (1 marking, 2 bridge + tending, 3 overlay + polish) BUILT** — compiles + `:compat:build`
clean; in-game verification pending. The Smelter marks, loads, lights, and harvests player-built bloomeries, with
the marked-bloomery overlay and a malformed-structure warning. Only JEI display recipes are deferred. See §7.

> **This is base-TFC compat, always active.** Base TerraFirmaCraft ships the bloomery, so — unlike the
> FirmaLife-gated Beekeeper — this feature needs no `ModList` guard (TFC is a mandatory `:compat`
> dependency). Every TFC world with a Smeltery can use it.

---

## 1. Motivation & where it fits

The **heat-forge multiblock** (`docs/tfc-forge-multiblock.md`) gave the Smelter its cast-metal path —
copper/tin/bismuth/zinc/silver/gold/nickel melt into molds. **Iron was deliberately deferred** (§17 there):
iron doesn't pour into a mold, it reduces to a solid **bloom** hammered on an anvil, so it `COLD`-stalls in a
mold forge and the Smelter tend policy already **excludes iron ore** (`SmelterBehavior.accepts` → `!bloom()`).

This feature closes that gap the **TFC-authentic** way: instead of teaching the forge a synthetic bloom mode,
the Smelter tends **real TFC bloomeries the player builds**. The two paths are complementary and driven by the
**same Smelter worker**:

| Path | Device | Placed by | Discovery | Output |
|---|---|---|---|---|
| **cast metals** (existing) | heat-forge multiblock | the **builder** (blueprint furnace → forge substitution) | auto — `ForgeUserModule.onBlockPlacedInBuilding` | ingots (molds) |
| **iron** (this doc) | **TFC bloomery** | the **player** (a real TFC multiblock they build) | **wand-marked** — `BloomeryUserModule` | `raw_iron_bloom` |

Because a bloomery is a player-built multiblock (chimney + insulation shell), the builder can't place it and a
blanket substitution can't target it — so, like the FirmaLife apiary and the chicken-herder's
`tfc:nest_box`, the player **hand-marks** each bloomery with a scepter. That's the whole point of the wand.

---

## 2. TFC bloomery facts (verified against the deobf jar)

Grounded in `net.dries007.tfc.common.blockentities.BloomeryBlockEntity` /
`net.dries007.tfc.common.blocks.devices.BloomeryBlock` / `...blocks.BloomBlock` /
`...blockentities.BloomBlockEntity` / `...recipes.BloomeryRecipe`.

### The device
- **Block** `net.dries007.tfc.common.blocks.devices.BloomeryBlock` (`TFCBlocks.BLOOMERY`), blockstate
  `FACING` (points out the gate), `LIT` (burning — the single source of truth), `OPEN` (gate, cosmetic).
- **Multiblock**: an insulation shell (`#tfc` bloomery-insulation blocks: stone/cobble/brick/fire-brick/…)
  around an **internal** block (`be.getInternalBlockPos()` = one block behind the gate) plus a **chimney** of
  1–3 valid levels above it. `BloomeryBlock.isFormed(level, internalPos, facing)` = base structure present;
  `BloomeryBlock.getChimneyLevels(level, internalPos)` = 1..`bloomeryMaxChimneyHeight` (default **3**).
- **Capacity** = `getChimneyLevels × TFCConfig.SERVER.bloomeryCapacity` (default **16**/level ⇒ up to **48**
  loaded item-entries at a 3-tall chimney). The BE's own `calculateCapacity()` is private; we recompute it.

### Loading — **no item handler; add directly to the live `inputStacks` list**
The BE is built with a **zero-slot** `ItemStackHandler`, so **nothing can be inserted through the Forge
capability**. The real store is a `protected List<ItemStack> inputStacks` (each entry a **size-1** stack). For a
*player*, items enter via the private `addItemsFromWorld` — each tick while unlit it vacuums matching
**`ItemEntity`s** from the chimney column (melting each ore via its `HeatingRecipe` to match the
`BloomeryRecipe`'s input fluid; charcoal is the catalyst). **We don't need that path.**

⇒ **The worker loads by mutating the list directly.** `getInputStacks()` returns the **live backing list**
(verified: `getfield inputStacks; areturn` — not a copy/unmodifiable), so the tender **adds size-1 ore +
charcoal stacks straight to `be.getInputStacks()`** and then calls `be.light(state)` — whose
`updateCachedRecipe()` reads that same list to match the recipe. This is **exactly TFC's own internal sequence**
(add size-1 stacks → `light` → `updateCachedRecipe`), minus the item-entity vacuum: **deterministic, no floor
litter, no absorption-tick lag, no reflection.** The only rule is to stay within `capacity`
(`chimneyLevels × TFCConfig.SERVER.bloomeryCapacity`) — one entry per item — so `serverTick`'s
`popItemsOffOverCapacity` never fires. After mutating, `setChanged()` so it persists.

Public read handles: `getInputCount()`, `getInputStacks()` (live), `getInternalBlockPos()`, `getExternalBlock()`.

### The recipe (stock: `data/tfc/recipes/bloomery/raw_iron_bloom.json`)
`100 mB molten cast iron (from iron ore) + 2 charcoal → 1 raw_iron_bloom`, burn **15000 calendar ticks**.
Blooms at completion = `min(totalMoltenFluid / 100, charcoalCount / 2)`; **any imbalance is wasted** —
`completeRecipe` clears `inputStacks` entirely, so both the `totalMoltenFluid mod 100` remainder **and** any
charcoal beyond `2 × blooms` are lost.

**mB per iron ore** (verified from `data/tfc/recipes/heating/ore/<grade>_{hematite,magnetite,limonite}.json` —
all → `tfc:metal/cast_iron` at 1535 °C):

| grade | mB / ore | divides 100 cleanly? | clean single-grade batch |
|---|---|---|---|
| small | 10 | yes | 10 ore → 1 bloom |
| poor | 15 | **no** | 20 ore → 3 blooms (LCM 300 mB) |
| normal | 25 | yes | 4 ore → 1 bloom |
| rich | 35 | **no** | 20 ore → 7 blooms (LCM 700 mB) |

**The key facts the loader must respect:**
- **small & normal align to the 100 mB bloom; poor & rich do not** — no whole number of poor(15)/rich(35) ores
  sums to 100, only to their LCM with 100 (poor 300, rich 700).
- **but all iron ore melts into one shared cast-iron pool** — the bloomery only sees the *sum*, so mixed grades
  combine freely to hit 100 (`2 rich + 3 small = 100`, `1 rich + 1 poor + 2 normal = 100`, …). A colony mining
  all grades aligns easily; only a **pure poor** or **pure rich** stockpile is awkward.
- **capacity bites the awkward grades**: a single zero-waste poor batch is 26 entries (needs a 2-level chimney),
  a single rich batch is 34 entries (needs the full 3-level chimney). Below that, poor/rich can't form a clean
  batch alone.

### The loader — accumulate to a 100-multiple, bounded-waste flush (locked)
Don't batch per grade — **track the running cast-iron total and align to 100 mB**:
1. From the carried iron ore, pick the subset summing to the **largest multiple of 100 that fits the free
   capacity** (`capacity − getInputCount()`, minus room for `2k` charcoal). Mixed grades make this near-always
   exact; leftover ore stays in inventory for next time. Add those size-1 ore stacks + exactly `2k` charcoal to
   `getInputStacks()`, `setChanged`, `be.light(state)` — zero waste.
2. If the carried ore can't reach any 100-multiple (pure poor/rich, too little), **don't light** — keep
   accumulating deliveries (into the worker's stock, or across visits) until it can (poor → 300, rich → 700).
3. **Bounded-waste flush** (the locked corner-case policy): if the bloomery is **at capacity**, *or* the colony
   has **no more iron ore incoming** and ≥ 100 mB is available, light anyway with `k = floor(total/100)` and eat
   the `< 100 mB` remainder — matching how a TFC player wastes it, and never leaving the worker idle on an
   un-alignable pile. Load exactly `2k` charcoal (never more — excess burns for nothing).

No device-temperature gating (unlike the forge): the 15000-tick charcoal burn does the reduction internally, so
the worker never has to check heat — only that meltable iron ore + charcoal are loaded.

### Ignition — public and direct
`be.light(be.getBlockState())` (server-side): if a valid recipe is cached (i.e. meltable ore is loaded) it
stamps `litTick`, sets `LIT=true, OPEN=false`, and returns true; returns **false** if nothing meltable is
loaded. (The player path is a `StartFireEvent` STRONG fire; we call `light` directly, as the design already
does for other TFC devices — no fake player, no flint & steel.)

### Progress & result
- **Done** = `LIT && getRemainingTicks() ≤ 0`. Runs on the **TFC calendar** (`ICalendarTickable`) — it keeps
  burning across chunk unloads and catches up on reload (`onCalendarUpdate`). The worker only needs to poll.
- On completion the BE spawns a **`BloomBlock` (`tfc:bloom`)** at the internal pos with a `BloomBlockEntity`
  holding `count` blooms (`getCount()`), the bloom item (`getItem()` = `tfc:raw_iron_bloom`, dropped hot), and
  flips `LIT=false`. **Extraction**: `dropBloom()` drops one bloom and decrements (the block re-renders with
  fewer layers, becoming AIR at 0); breaking the block calls `dropBloom()`. The worker loops
  `while (be.getCount() > 0) dropBloom()` (or breaks it repeatedly) and banks the blooms.

### No continuous tending
Unlike the forge (shared fuel column, keep-warm, gradual heat), a lit bloomery is **fire-and-forget**: charcoal
is the catalyst, consumed only at completion; there's no re-fuel and no re-light mid-burn. So bloomery tending
is just **load → light → wait → extract**.

---

## 3. Architecture — wand marking (reusing MineColonies' tool plumbing) + a TFC bridge

Three concerns, each mirroring an established pattern:

### 3a. Marking bloomeries — the scepter (mirrors the Beekeeper)
MineColonies' "click blocks to assign them to a hut" is four decoupled pieces; the reusable plumbing is the
**tool GUI trio** (`ToolModuleView` + `ToolModuleWindow` + `GiveToolMessage`) — we reuse all three **verbatim**
(zero new client GUI code) and supply our own scepter item + position store.

- **`ItemBloomeryScepter`** (new `mctfc:bloomery_scepter`, a plain `Item implements IBlockOverlayItem`) — its
  `useOn` (server-side) reads the colony id (`"id"`) + hut pos (`"pos"`) NBT that `GiveToolMessage` stamps onto
  the stack, resolves the `BuildingSmeltery` + its grafted `BloomeryUserModule`, and **toggles** the clicked
  bloomery. This is the Beekeeper's `useOn` retargeted from `instanceof BeehiveBlock` to `TfcBloomery.isBloomery`,
  with one addition — a **mark-time `isFormed` gate** (asymmetric with the lenient runtime pruning in §3b):
  ```
  not a BloomeryBlock  → (beekeeper convention) dismiss the tool
  already marked        → remove, chat "removed"
  !isFormed             → REJECT: chat "bloomery structure incomplete"; do NOT add, do NOT consume the wand
  marks.size() >= cap   → chat "max reached", consume the scepter
  otherwise             → add, success sound, chat "added"
  ```
  A malformed bloomery is a real target the player intended, so it's **rejected with feedback, not consumed** —
  let them finish the structure and re-click. `isFormed` is the correct gate because capacity is
  `isFormed ? chimneyLevels × 16 : 0`, so passing it guarantees the bloomery is actually loadable (≥16 cap). At
  runtime the mark then survives transient unformed states (§3b) — strict on entry, lenient on retention.
  `getOverlayBoxes` renders a **red** box on the bound hut (from the always-present client building view) plus a
  **yellow** box on each marked bloomery, read from the wand's own synced stack NBT — refreshed server-side by
  `inventoryTick` while the wand is held (packet-free; see §3b, slice 3).
- **The "give scepter" tab** — a reused `ToolModuleView(BLOOMERY_SCEPTER)` grafted onto the **Smeltery view**.
  Its window's `giveTool` button sends the generic `GiveToolMessage`, which writes hut-pos + colony-id onto a
  fresh scepter and drops it in the player's hotbar. No new message, no new window.

### 3b. Storing the marked positions — a grafted module (mirrors `ForgeUserModule`)
We can't add a field to `BuildingSmeltery`, so the positions live in a grafted **module** (like
`ForgeUserModule`).

> **As-built deviation — two producers, no module sync.** The design originally combined storage + the give-tab
> + overlay sync into one synced module (a `BloomeryModuleView extends ToolModuleView` fed by `serializeToView`).
> Recon of `BuildingEntry.ModuleProducer` killed that: module runtime ids come from a **global `++counter`**, and
> the client deserialize loop matches synced modules by that id — a mismatch doesn't just drop overlays, it
> **corrupts the entire building-GUI deserialize** (wrong module reads the bytes, or a null lookup aborts the
> whole sync). Too fragile to lean on. So the feature is split into **two never-synced producers** (in
> `BloomeryModules`), exactly how `ForgeUserModule` and MineColonies' own tool tabs avoid the issue:
> `STORAGE` (server-only, `viewProducer = null` → never serialized) and `TOOL` (client-only `ToolModuleView`,
> `moduleProducer = null` → server never serializes it, the client having it extra is harmless). The overlay's
> client positions ride the **wand's own stack NBT** instead (slice 3: `inventoryTick` refreshes it server-side
> while held → free inventory sync), not the module system — even simpler than the originally-planned packet.

- **`BloomeryUserModule`** (server, `IPersistentModule`) — a persisted `Set<BlockPos>` of marked bloomeries,
  `add/remove/contains/getBloomeries`, `getMaximumBloomeries()` = `Config.maxBloomeries(buildingLevel)` (§4 — the
  gentle `{0,1,1,2,3}` table, unlocks at L2, caps at 3), and NBT round-trip. **No `serializeToView`** — its
  producer (`BloomeryModules.STORAGE`) has no view, so it's server-only, not synced. Grafted onto
  `BuildingSmeltery` in `MixinAbstractBuilding.<init>` (TAIL), exactly like `ForgeUserModule`.
  **Pruning — self-healing but conservative** (the Beekeeper strips a hive the instant `world.getBlockState`
  isn't a beehive, with no chunk-load guard and on *any* non-beehive state; we drop both those edges):
  - **chunk-load guard first** — `if (!level.hasChunkAt(pos))` → **keep** (don't force-load a remote chunk just
    to validate a mark, and never prune on an unverifiable read; bloomeries can be marked far from the hut,
    unlike hut-adjacent hives).
  - **keep a present-but-unformed bloomery** — the `BloomeryBlock` is there but `!isFormed` (chimney
    incomplete / mid-rebuild) → keep the mark, just skip loading (optional "not built right" warning). A big
    multiblock shouldn't lose its mark for a transient structural gap.
  - **strip only when genuinely gone** — chunk loaded **and** the block at `pos` is no longer a `BloomeryBlock`
    (player removed/replaced it) → prune, freeing a cap slot. This is the only auto-removal; otherwise only the
    wand unmarks.
- **The give-tab** — a reused `ToolModuleView(BLOOMERY_SCEPTER)` (`BloomeryModules.TOOL`, client-only) grafted
  onto the Smeltery **view** in `MixinBuildingEntry` (`produceBuildingView` TAIL, matched by the entry's
  **registry name `"smeltery"`** — the Smeltery uses the shared `EmptyView`, so there's no `BuildingSmeltery.View`
  to `instanceof`). Registered via `.setProducer(BloomeryModules.TOOL)`, mirroring MineColonies' own
  `produceBuildingView`. Its producer's runtime id is globally unique, so registering the extra client view can't
  collide with any MineColonies module.

### 3c. Tending the bloomery — the `TfcBloomery` bridge + AI
- **`TfcBloomery`** — the **only** class naming TFC bloomery types (loaded eagerly; TFC is mandatory, no
  guard). Static helpers over the public API in §2: `isBloomery(state)/isBloomeryBlock(block)`,
  `bloomeryAt(level,pos)`, `isFormed`, `capacity`, `isLit(state)`, `remainingTicks`, `inputCount`,
  `internalPos/externalPos`, `light`, `loadInput(be,stack)` (add a size-1 stack to the live `getInputStacks()`
  list + `setChanged`), `freeCapacity(level,be)`, `bloomAt`/`bloomCount`/`extractOneBloom`, and the ore/catalyst
  predicates (`isIronOre` = melts to cast iron / is a `SmelterRecipes` bloom ore; `isCatalyst` = charcoal).
- **AI** — a **bloomery tend loop** added to `SmelterBehavior` (which already tends forges), reading the marked
  positions from the building's `BloomeryUserModule`. One action per bloomery visit (walk to its front pos):

  ```
  bloom present (count > 0)      → EXTRACT: dropBloom() loop → bank raw_iron_bloom to racks (then it's empty)
  LIT                            → in progress → skip (self-runs on the calendar)
  unlit, has valid input         → LIGHT: be.light(state)
  unlit, empty, formed, room,    → LOAD: add whole-bloom batches (ore totalling 100 mB + 2 charcoal each) to
    carrying ore                     be.getInputStacks(), sized to free capacity, then setChanged
  unlit, empty, not formed       → (skip; optional worker warning "bloomery not built right")
  ```

  The Smeltery **requests iron ore + charcoal** (via `SmelterBehavior.requestMissing`) whenever ≥1 bloomery is
  marked, so couriers keep the worker stocked — reusing the existing low-water restock machinery.

- **The ore list gates iron too.** Which iron ores the bloomery path may request/stage/load runs through
  `SmelterBehavior.usableIronOre` = `isIronOre && oreEnabled` — the same `ORE_LIST` deny-list the forge cast-metal
  path checks (`accepts` → `oreEnabled`). `MixinCompatibilityManager` populates the Smeltery's *smeltable ores* GUI
  with **all** TFC ores (cast + iron) via `SmelterRecipes.oreStacks()`, so toggling an iron grade off there now
  actually stops the bloomeries requesting/consuming it (previously the bloomery path keyed on bare `isIronOre` and
  ignored the list, making its iron entries inert). One honest control over everything the Smeltery melts.

- **Zero-overhead when unused.** The bloomery work-check's first step is an in-memory guard —
  `getFirstModuleOccurance(BloomeryUserModule.class)` + `getBloomeries().isEmpty()` — *before any world access*.
  A Smeltery with no bloomeries marked (the common case, since marking is opt-in) pays only a module lookup +
  `Set.isEmpty()`: no `getBlockState`, no `isFormed`, no throttle timer. The layered gate + throttle below
  engage only once ≥1 bloomery is actually marked.
- **Work-check cost — layer the gate, keep `isFormed` off the hot path.** `hasWork()`/`canGoIdle()` are polled
  continuously, so once bloomeries exist the per-bloomery check is ordered cheapest-first: (1) `getBlockState(pos).getValue(LIT)` —
  burning ⇒ skip (**1 lookup**; the state for ~all of the 12.5-min burn); (2) a bloom present at the internal
  pos ⇒ extractable (~1 lookup + BE); (3) only an **unlit + empty + worker-carrying-aligned-ore** bloomery
  reaches `isFormed` + `getChimneyLevels`. `BloomeryBlock.isFormed` is one short-circuiting `MultiBlock.test`
  (~1–2 `getBlockState` when malformed, ~8–12 when formed) — trivial per call but not per-tick×N, so the
  loadability pass is **throttled** (cached `bloomeryWorkPending`, refreshed on the existing
  `REQUEST_CHECK_INTERVAL` ≈ 5 s). The continuous poll then costs ~1 lookup/bloomery; the multiblock scan runs
  only on the throttle. ~5 s latency is nothing against a 12.5-min burn.

The Smelter worker interleaves **forge tending** (cast metals) and **bloomery tending** (iron) across its AI
cycle; the two are independent (different devices, different inputs, different outputs).

---

## 4. Restrictions tied to the hut level

- **Count cap** — a **gentle, low, explicit table** (bloomeries are an *upgrade* perk, not a starter feature),
  `getMaximumBloomeries(level)`:

  | Smeltery level | 1 | 2 | 3 | 4 | 5 |
  |---|---|---|---|---|---|
  | **max bloomeries** | **0** | 1 | 1 | 2 | 3 |

  So the iron line **unlocks at L2** (L1 rejects marking with a "requires level 2" chat), ramps gently, and
  **caps at 3** (~60 iron/hr at a fully-built L5) — keeping raw iron a boutique supplement to the forge's cast
  metals, with a small build footprint. The last +1 lands at **L5**, so maxing the hut earns the third bloomery.

  **Config-driven** (not hardcoded): a `List<Integer>` in the existing common config
  [com.mctfc.Config](../compat/src/main/java/com/mctfc/Config.java), `bloomeryCapPerLevel` (default
  `[0, 1, 1, 2, 3]`, index 0 = level 1), mirroring the sibling `furnaceFuelTempBonusByLevel`. A getter
  `Config.maxBloomeries(int buildingLevel)` clamps exactly like `Config.furnaceFuelTempBonus`: a level beyond
  the list uses the last entry, and an **empty list disables bloomery marking entirely** (a clean kill-switch).
  Both the scepter's `useOn` (reject past the cap, like the Beekeeper's max-hives message) and
  `BloomeryUserModule.getMaximumBloomeries` read `Config.maxBloomeries(level)`, so a server owner retunes the
  curve — or extends it past level 5 — without recompiling.
- **Fuel-temperature bonus**: none needed — a bloomery self-heats from its charcoal catalyst; there's no shared
  device temperature to boost (that's a forge concept).
- (Future) a per-hut **setting** for the cap or an on/off toggle, like the Smelter's `ore_threshold`.

---

## 5. Items, requests & assets

| Item | Role |
|---|---|
| `mctfc:bloomery_scepter` | the marking wand (given from the Smeltery GUI; not consumed on normal add/remove) |
| iron ore (`tfc:ore/<grade>_{hematite,magnetite,limonite}`) | dropped into a marked bloomery (100 mB/bloom) |
| `minecraft:charcoal` | the bloomery catalyst (2/bloom) |
| `tfc:raw_iron_bloom` | banked to the racks; the player hammers it to wrought iron on a TFC anvil (existing TFC gameplay) |

New assets: a scepter item model + texture (a simple wand, palette-matched to the MineColonies scepters), plus
lang: the item name, the tool-tab desc (`com.minecolonies.coremod.gui.tooldesc.bloomery_scepter`), and the
add/remove/max chat lines. The scepter goes in the `TOOLS_AND_UTILITIES` creative tab (grabbable + discoverable).

---

## 6. Mixin & file inventory

| New/edited file | Kind | Purpose |
|---|---|---|
| `com.mctfc.item.ModItems` | new ✅ | `DeferredRegister<Item>` for `bloomery_scepter` (+ `TOOLS_AND_UTILITIES` tab) |
| `com.mctfc.item.ItemBloomeryScepter` | new ✅ | the wand — `useOn` mark/unmark + `isFormed` gate + cap; `inventoryTick` marks→NBT; `IBlockOverlayItem` red hut + yellow marked-bloomery boxes |
| `com.mctfc.bloomery.TfcBloomery` | new ✅ | the sole TFC-bloomery-naming bridge (slice 1: `isBloomery`/`isFormed`/`bloomeryAt`; load/light/extract in slice 2) |
| `com.mctfc.bloomery.BloomeryUserModule` | new ✅ | server position store (NBT + cap + conservative prune); **no `serializeToView`** (not synced) |
| `com.mctfc.bloomery.BloomeryModules` | new ✅ | the two never-synced producers: `STORAGE` (server) + `TOOL` (client `ToolModuleView`) |
| `com.mctfc.Config` | edit ✅ | `bloomeryCapPerLevel` list config + `maxBloomeries(level)` getter (empty = disabled) |
| `com.mctfc.smelter.SmelterBehavior` | edit (slice 2) | add the bloomery tend loop + iron-ore/charcoal requests |
| `com.mctfc.mixin.MixinAbstractBuilding` | edit ✅ | graft `BloomeryUserModule` onto `BuildingSmeltery` (server) |
| `com.mctfc.mixin.MixinBuildingEntry` | new mixin ✅ | graft the `ToolModuleView` tab onto the Smeltery view (client, matched by registry name) |
| `mctfc.mixins.json` | edit ✅ | register `MixinBuildingEntry` (client section) |
| assets/lang | new ✅ | scepter model + placeholder texture (beekeeper's), item name, tool desc, mark/remove/notformed/level/max chat |

Reused MineColonies plumbing (no new code): `ToolModuleView`, `ToolModuleWindow`, `GiveToolMessage`,
`IBlockOverlayItem`, `InventoryUtils.getOrCreateItemAndPutToHotbarAndSelectOrDrop`, `BlockPosUtil.read/write`,
`NBTUtils` list-of-BlockPos round-trip. NBT keys: `"id"` (colony), `"pos"` (hut). (The overlay-sync slice will add
a dedicated packet + `ColonyViewBuildingViewMessage`-style refresh for the yellow marked-bloomery boxes.)

This is **MineColonies-only** bridging in `:compat` — no SlimColonies twin.

---

## 7. Build order (slices)

1. ✅ **Marking infrastructure (built, compiles + builds clean; in-game verification pending)** — `ModItems` +
   `ItemBloomeryScepter` (useOn + red-hut overlay) + `TfcBloomery` (`isBloomery`/`isFormed`) + `BloomeryUserModule`
   + `BloomeryModules` (two never-synced producers) + the two grafts (`MixinAbstractBuilding` line,
   `MixinBuildingEntry`) + reused tool tab + `Config` + assets/lang. **Verify in-game**: take the wand from the
   Smeltery's tool tab, right-click a formed bloomery (chat "marked" + success sound), a malformed one ("not built
   right", wand kept), re-click to unmark; the cap bites (`{0,1,1,2,3}` — L1 rejects with "upgrade", caps at 3);
   marks persist across `/reload` + save-reload; the red hut box shows while holding the wand.
2. ✅ **Bridge + tending (built, compiles + `:compat:build` clean; in-game verification pending)** — extended
   `TfcBloomery` (`capacity`/`freeCapacity`/`isLit`/`light`/`loadInput` via the live `getInputStacks()` list/
   `bloomAt`/`extractBlooms`/`isIronOre`/`oreMb`/`isCatalyst`); a `BLOOMERY_TEND` state added to `SmelterBehavior`
   (threaded after `MOLD_UNLOAD`) — stages iron ore + charcoal, walks each actionable marked bloomery, and per visit
   **extracts** a finished bloom straight into the racks (`extractBlooms` → `removeBlock`, no floor drops) **or**
   loads + lights an unlit formed one; plus iron-ore/charcoal auto-requests and `pruneStale` on the tend read.
   `hasWork()`/`bloomeryWork()` use the layered gate + `REQUEST_CHECK_INTERVAL` throttle (zero-cost when no marks).
   **Deviation** — the loader ships the **always-flush-at-k≥1 with trim** interpretation of bounded-waste flush
   (§2): it aligns the pooled cast-iron total to 100 mB from mixed grades, takes `k=⌊mb/100⌋` blooms (also capped
   by carried charcoal), and **trims the smallest ores** while the total stays ≥100k to minimise the sub-100
   remainder — lighting whenever ≥1 whole bloom is makeable rather than the design's stricter "accumulate unless
   full/starved". Clean batches still fall out when the colony has enough of an aligning grade (staging pulls a
   capacity batch); the simpler rule just never idles the worker and keeps waste <100 mB. Loads nothing when <1
   bloom is makeable (ore keeps accumulating in storage). **Verify in-game**: build a real TFC bloomery, mark it,
   watch the Smelter load/light it and bank `raw_iron_bloom` — no floor litter, bounded waste.
3. ✅ **Overlay + polish (built, compiles + `:compat:build` clean; in-game verification pending)** — the **yellow
   marked-bloomery boxes** now render, fed **packet-free via the wand's own stack NBT**: `ItemBloomeryScepter`'s
   server-side `inventoryTick` refreshes the marked positions onto the held stack (~1/s, only when changed), which
   syncs to the client for free, and `getOverlayBoxes` reads them (decoupled from the fragile module-id sync — a
   simpler win than the planned dedicated packet). Plus the **"bloomery not built right" worker warning** — a
   `BloomeryUserModule.hasMalformed` check surfaced from the building's colony tick (`MixinAbstractBuilding`) with an
   auto-clearing `InteractionValidatorRegistry` predicate (mirrors the herder's mixed-species warning), and its lang.
   **Deferred:** JEI/GUI display recipes advertising the bloomery outputs (pure advertisement; the feature works
   without it) and any playtest cap tuning.

---

## 8. Decisions & risks

**Locked (baked into this design):**
- **Wand marking, not substitution** (bloomeries are player-built) — the whole reason for the scepter.
- **Reuse the MineColonies tool trio verbatim** (`ToolModuleView`/`ToolModuleWindow`/`GiveToolMessage`) — no
  new client GUI; one server module + one `ToolModuleView` subclass carry storage + sync + tab + overlay.
- **Direct `be.light(state)`** ignition (public, server-safe) — no fake player / fire event.
- **Load by adding size-1 stacks to the live `getInputStacks()` list + `light`** (verified: the getter returns
  the field, and `light`→`updateCachedRecipe` reads it) — deterministic, no item-entities on the floor, no
  reflection. (The item-entity vacuum is the *player's* intake path; the worker doesn't use it.)
- **Loader = accumulate-to-a-100-multiple with a bounded-waste flush** (§2): align the pooled cast-iron total to
  100 mB from mixed grades (small/normal divide 100; poor/rich only realign at 300/700), load `2k` charcoal;
  flush the `<100 mB` remainder only when the bloomery is full or no more ore is incoming.
- **Cap = `buildingLevel`** (1→5).
- **No fuel column / keep-warm** — a bloomery self-runs on the calendar once lit.

**Open / risks:**
- **Structure validity**: a marked-but-malformed bloomery (`!isFormed`, capacity 0) can't be loaded — surface
  a worker warning rather than silently idling.
- **Bloom heat**: `raw_iron_bloom` drops hot; banking it to a rack is fine (it cools), but confirm the rack
  doesn't reject a hot stack.
- **Overlay client positions**: depend on the `serializeToView`/`deserialize` round-trip landing on the same
  runtime id — verify the marks actually render after a `ColonyViewBuildingViewMessage`.
- **Cap balance**: `buildingLevel` may be too tight or too loose once the burn throughput is felt in-game;
  it's a one-line curve change.
</content>
</invoke>
