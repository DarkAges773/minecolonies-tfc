# TFC forge multiblock — design + status (ALL slices IMPLEMENTED; in-game validation pending)

A custom **growing forge block** that replaces the vanilla furnaces MineColonies' furnace huts use (Smeltery, Cook,
Chef — Glassblower later) with a self-processing, TFC-flavoured device. The block does the heating/melting on its own
tick; the worker AI is reduced to **tending** it (stock input + fuel, pull finished goods, check state). Adjacent forge
blocks merge into **one multiblock** that shares fuel and presents as **one big furnace** in the GUI, tended as a single
aggregate.

Status: **all slices implemented + compiled** (block + BE + multiblock + cook/melt self-tick + GUI + Smelter/Cook
tend-AI + Chef driver + the furnace→forge switchover). The block + GUI are **in-game-verified standalone**; the full
**worker flow (Smelter/Cook/Chef on forges) is code-complete but not yet colony-tested** — that's the remaining step.
The `minecraft:furnace → mctfc:heat_forge_brick` substitution is **live** (every blueprint furnace becomes a forge), and
the vanilla-furnace driving has been **retired** (deleted). Two deviations from the original plan, noted below and in
§18: the block ships as **4 cosmetic variants** (brick/rustic/stone/tile) with the substitution defaulting to **brick**
(a global rule can't pick per-hut); and the **decorative-furnace interaction blocker** ([VanillaFurnaceHandler](../compat/src/main/java/com/mctfc/block/VanillaFurnaceHandler.java))
was **kept** (config-gated, off by default) rather than deleted — it's an orthogonal TFC-bypass fix for *player-placed*
vanilla furnaces, not part of the forge system. See §18 for the full done breakdown and the hard-won fidelity fixes.

---

## 1. Goal & motivation

Today `:compat` drives the huts' **vanilla furnaces** (kept player-decorative via
[VanillaFurnaceHandler](../compat/src/main/java/com/mctfc/block/VanillaFurnaceHandler.java)) by reusing MineColonies'
furnace AI and swapping only the heating underneath (see [tfc-furnace-workers.md](tfc-furnace-workers.md)). That works,
but the *driving* — ignition, fuel timing, completion — lives in the AI.

The goal here is to **move the driving into the block**:

- The forge **BE self-processes** (burn shared fuel → heat every position → produce output), so it keeps working while
  the worker is away.
- The **AI simplifies to a tend loop**: discover devices, stock input + fuel, pull finished goods, react to holds. No
  ignition, no litTime bookkeeping, no completion state machine.
- The forge is a **growing multiblock**: face-adjacent blocks merge into one device (cap 5) that **shares fuel** and is
  **tended as one** — one walk target, one fuel refill, all positions serviced in one interaction.
- Visually a **vanilla furnace**; a distinct, TFC-authentic device rather than a literal `minecraft:furnace`.

---

## 2. The constraint that dictates "fully custom"

MineColonies is **`FurnaceBlock`-locked**: [FurnaceUserModule](https://github.com/ldtteam/minecolonies)`#onBlockPlacedInBuilding`
registers a furnace only when `block instanceof FurnaceBlock`, and every furnace AI reads
`getBlockEntity(pos) instanceof FurnaceBlockEntity`, one position at a time.

You can register your **own Block class** and your **own BlockEntity class + type** freely. The *one* thing that is
impossible is a persistent BE that is **both a custom class and `instanceof FurnaceBlockEntity`**, because:

- `FurnaceBlockEntity`'s only constructor is `(BlockPos, BlockState)`, which internally hardcodes
  `BlockEntityType.FURNACE`. Any subclass can only call that super → `getType()` is always `FURNACE`.
  (`AbstractFurnaceBlockEntity` *does* take the type, but a subclass of it is **not** `instanceof FurnaceBlockEntity`.)
- On load a BE is rebuilt by its **type's** factory (`BlockEntity.loadStatic` → `type.create(...)`); `FURNACE`'s factory
  is `FurnaceBlockEntity::new`, so after one reload a `FurnaceBlockEntity` subclass is silently replaced by a plain one
  (and dropped entirely if the block isn't in `FURNACE.validBlocks`).

So the pick is: **plain `FurnaceBlockEntity` + `FURNACE` type** (is `instanceof`, keeps MineColonies' AI) **or**
**custom class + custom type** (not `instanceof`, needs a custom AI). Because our whole point is to **replace the furnace
AI with a tend loop**, that `instanceof` compatibility is worthless to us — so we go **fully custom** (custom block, BE,
type), free of every vanilla-furnace quirk, and do our own discovery + tending.

> Why a real block at all (not TFC's `CharcoalForgeBlockEntity` reused, nor a detached virtual BE): a real block has a
> free lifetime + drop-on-break and isn't world-coupled/multiblock-gated the way TFC's forge BE is.

---

## 3. Architecture at a glance

| Piece | Role |
|---|---|
| **`HeatForgeBlock`** (custom `Block`+`EntityBlock`, furnace-shaped) | placed instead of `minecraft:furnace`; members auto-merge |
| **`HeatForgeBlockEntity`** (custom BE + type) | the **controller** owns the aggregate + **self-ticks** the processing |
| **`ForgeController`** (the BE's worker-facing façade) | the single API the tend-AI + Chef driver program against |
| **`ForgeUserModule`** (grafted onto the huts) | discovery — mirrors `FurnaceUserModule` for our block |
| **Tend-AI** (Smelter/Cook via the existing dispatcher) | stock input + fuel, drain finished goods, react to holds |
| **Chef driver** (mixin on the request path) | same tending, gated to FURNACE-intermediate recipes, straight to `addDelivery` |
| **Merged container/screen** | the "one big furnace" GUI over the controller's aggregate |

Reused unchanged from today: the recipe/heat math ([CookRecipes](../compat/src/main/java/com/mctfc/cook/CookRecipes.java) /
[SmelterRecipes](../compat/src/main/java/com/mctfc/smelter/SmelterRecipes.java)), the fuel model
([FurnaceFuel](../compat/src/main/java/com/mctfc/furnace/FurnaceFuel.java) `Burn` pool + temp gate), and the
completion logic (today's [CookProcessing](../compat/src/main/java/com/mctfc/cook/CookProcessing.java)/`SmelterProcessing`,
now first-class BE code instead of a serverTick mixin).

### TFC reference & compatibility — a firepit × forge hybrid

The device is a deliberate **mix of TFC's two heat devices**, built from their real primitives (verified against the TFC
jar) — kept as vanilla-TFC-compatible as the primitives allow:

- **Each position ≈ a TFC firepit.** One heat slot + **two** output slots, applying a `HeatingRecipe` to the heating
  item and routing the result exactly like the firepit's `mergeOutputStack` (item output → cooked food) /
  `mergeOutputFluids` (fluid output → metal into a mold). TFC's `FirepitBlockEntity` already has `SLOT_ITEM_INPUT` +
  `SLOT_OUTPUT_1/2` and *both* merge paths — so the 1-in/2-out position is a faithful copy.
- **The multiblock ≈ a TFC charcoal forge.** N heating positions in parallel (`CharcoalForgeBlockEntity` has 5
  `SLOT_INPUT` + a per-slot `cachedRecipes[]`) over **one shared 5-slot fuel column** (`SLOT_FUEL_MIN..MAX`,
  cascade / bottom-burns).
- **Every primitive is TFC's:** `HeatingRecipe` (its `outputItem` **and** `outputFluid`, via `assemble`/`assembleFluid`),
  `IHeat`/`HeatCapability` (the item's own rising temperature *is* the progress, no synthetic bar), molds as `MoldLike`
  fluid containers + `CastingRecipe`, and `Fuel` + the fuel column.
- **One deliberate hybrid.** TFC melts *ore* into molds through a **crucible/vessel** intermediate (ore → crucible
  collects mB → pour into a mold → cast). Our device pours the heating recipe's fluid **straight into the position's
  molds**, doing the crucible's collection inline — so the mB amounts, molds, and casting are all TFC-real; only that
  collection step is compressed. That *is* the "mix."

---

## 4. The block & the multiblock

- **Members**: face-adjacent `HeatForge` blocks auto-join one instance, **capped at 5**. A deterministic **controller**
  (e.g. lowest `BlockPos`) owns the aggregate state; the others redirect to it. Breaking the controller re-elects from
  the survivors (or splits into sub-instances).
- **Per member block** contributes one input **position** = `{ heat slot, output slot, overflow slot }` — every slot a
  single item (max stack 1), TFC-forge style. Adding blocks scales **input positions only**, never fuel or stacking.
- **Fuel is a fixed 5-slot column on the controller** — a 1-to-1 copy of TFC's charcoal forge
  (`CharcoalForgeBlockEntity`, `SLOT_FUEL_MIN..SLOT_FUEL_MAX` = 5, `consumeFuel()` + `cascadeFuelSlots()`): fuel is added
  at the **top**, **cascades down** (like a firepit/forge), and **only the bottom slot burns**. So a 1-block forge and a
  5-block forge **both have exactly 5 fuel slots** — block count changes only how many things heat at once, not the fuel.
- **A real, player-usable device**, not a locked worker box, matching a TFC forge's flexibility:
  - **heat slots accept any TFC-heatable item** (not gated to ore/food) — a player can heat whatever they like;
  - **output + overflow slots accept any fluid container** (a mold, or a larger vessel) to catch molten metal, like the
    charcoal forge;
  - the colony worker just *also* tends it, feeding only its hut's **subset** (§13) — the broad slot acceptance is for
    the player.
- **Nothing to bypass.** Unlike the decorative vanilla furnace (blocked so players can't dodge TFC smelting), the forge
  *is* the legit TFC heating path — so its GUI is **fully usable**, shared by player and worker (no read-only lock).
- **Access is player-GUI + worker only — no hoppers/pipes** (a second deliberate handicap, with the no-bellows cap §5).
  The BE **exposes no item-handler capability to the world** and isn't a hopper-visible `Container`, so hoppers, droppers,
  and mod item-pipes can neither insert nor extract. Items move only through the **player's GUI** or the **worker's
  internal tending** (which reaches the slots via the `ForgeController` façade / direct BE access, not a sided capability,
  so it's unaffected). The point: the forge can't be built into a standalone auto-piped smelter/kitchen — its throughput
  stays tied to being **worker-tended** (or hand-tended), complementing the colony rather than trivialising automation.
  (Bonus: with no hopper force-feed, an `INVALID` heat-slot item can only come from a deliberate player placement — and
  the slot rejects non-heatable items on insert anyway — so jams are near-impossible; §6's `INVALID` is just a safety net.)
- **Visually** a vanilla furnace (front-facing, lit variant driven by the device burning); the merge can add a
  connection indicator but needn't.
- **Material variants**: a few cosmetic variants (furnace-shaped, different materials/textures) sharing **one BE +
  behavior** — palette matching. Open (§16): separate blocks vs. a `material` blockstate property; whether variants
  **merge freely** (material is cosmetic — simplest, my lean) or only within a material (visual cohesion); the blanket
  substitution places a **default** variant for now (palette-matched substitution is a later enhancement).
- **GUI** — a **custom merged container/screen** (a native `AbstractContainerMenu`/`Screen`, **not** BlockUI — it's a
  player world block), laid out as the "one big furnace":
  - **left:** the **5-slot fuel column** (vertical, the bottom slot = the one burning), with a **temperature gauge
    immediately to its right** (a vertical gauge spanning the column's height) — one gauge for the whole device (temp is
    shared), showing live `deviceTemp` climbing toward the ceiling;
  - **center/right:** up to **five vertically-stacked processing rows**, one per member position — each row is
    `heat slot → output slot → overflow slot`, with **no progress bar**: the heating item's own rising temperature (TFC's
    heat glow / tooltip on the item in the heat slot) *is* the progress, exactly like a real TFC forge. **Rows for absent
    members are hidden**, so the screen flexes from 1 to 5 rows with the device size;
  - **bottom:** the standard player inventory + hotbar.

  Built for the current member count when opened; a structure change (break/merge) while it's open refreshes or closes it.

**Membership, split, merge & loading.** A connected region of forge blocks is deterministically partitioned into
controllers of **≤5 members**: BFS from the region's lowest `BlockPos` claims up to 5 connected blocks (→ one
controller), then repeat on the remainder. Geometry-only, so membership is **stable across placement order and reload**;
any adjacency change re-partitions the affected region. Each member's slots **drop on break** (input + both molds).

- **Grow / merge**: placing a block re-partitions its region — a device grows until 5, then further adjacent blocks form
  the next controller. A region larger than 5 is simply multiple **adjacent-but-separate** devices, each with its own
  5-slot fuel column — the honest cap consequence (one column shouldn't drive unlimited positions; TFC's own forge caps
  at 5 inputs for the same reason), so more throughput needs more fuel columns. A merge that combines two fuelled columns
  keeps one and pops the excess fuel as drops.
- **Break / split**: the sub-region holding the **old controller keeps its device state** (fuel column, temp, lit,
  timer); others re-elect (their new lowest-pos) and init **fresh** (empty column, cold) with loaded positions intact.
  In a straight row the controller is always an **end**, so breaking the **middle** is graceful (fuel stays with the
  controller's side; the far side becomes a loaded-but-cold forge until re-fuelled). Breaking the **controller** migrates
  its state to the largest survivor first (a small break-hook), so **any** single break drops only that block's own
  slots. The tend-AI re-groups automatically (`ForgeUserModule` still lists every position).
- **Nothing fractional is lost on break.** The heat slot's ore/food drops **hot** (TFC heat NBT — cools in the world),
  each mold drops with its **real metal fluid** (partial or full), cooked food drops as items. Melted metal pours
  straight into molds and cooking is atomic at completion, so there's no buffer/half-item to lose — a **mid-melt or
  mid-cook** break costs only the burning fuel's partial tick and the items' dissipating heat.
- **Chunk loading**: a device runs only when its **whole region is loaded**; a member in an unloaded chunk **freezes** it
  (timestamp timers — keep-warm — resume exactly). Membership is **persisted** (a cache of the geometric partition,
  refreshed when fully loaded) so a partial load can't mis-partition at the chunk edge — it uses the cache and freezes,
  re-verifying on full reload. Members are ≤5 blocks apart, so they're near-always co-loaded; a device straddling a
  load boundary mid-unload is rare and handled by the freeze.

---

## 5. Shared device state — lit, gradually heating, one fuel pool

Three things are shared across the whole controller (the rest is per-position):

- **A lit/burning state, with keep-warm + explicit extinguish.** A forge is UNLIT (cold) until a worker **lights** it
  (§13). While lit it **burns fuel continuously whether or not it's processing** (the fire burns regardless), so the
  worker keeps it fed. It does **not** silently go out at idle: to mitigate the warm-up (below), the worker **keeps a lit
  forge running for a configured idle window after the last operation** (topping fuel so it stays hot and instantly
  ready), and only when that window elapses with no new work does it **explicitly `extinguish()`** — stopping the burn and
  retaining the unburned fuel, so an idle forge doesn't waste fuel forever. That window (`forgeKeepWarmTicks`, config;
  a per-hut setting later) is the **fuel-vs-latency knob**: long = jobs start hot with no warm-up but idle fuel burns;
  zero = extinguished the moment work stops, paying warm-up on the next job. (A forge whose fuel runs dry before the
  worker refeeds still goes cold on its own — extinguish is the *deliberate* stop.)
- **One 5-slot fuel column** (§4), the single shared fuel supply — a 1-to-1 copy of TFC's charcoal forge: fuel is added
  at the top, cascades down, and **only the bottom slot burns** (setting the current `burnTicks` + `burnTemperature`); on
  exhaustion `consumeFuel()`/`cascadeFuelSlots()` pull the spent item and shift the column down. The worker tops it at one
  point. Fixed at 5 regardless of block count — *that* is "share fuel." (`FurnaceFuel`'s per-item queries — `isFuel`,
  `fuelTemp`/`fuelDuration`, `isHotEnough` — still read each fuel item; only the pool-from-N-slots plumbing is replaced by
  the column.)
- **One device temperature that rises gradually**, like TFC's charcoal forge (its `temperature` climbing toward
  `burnTemperature`): while lit it climbs toward the **bottom fuel's `burnTemperature` + building-level bonus** (the
  ceiling) at a tunable rate, and falls when unlit. A position only *runs* once the live `deviceTemp ≥ input.requiredTemp`, so a freshly-lit forge has a **warm-up**
  first. Crucially, **feasibility is the ceiling, not the live temp**: whether a job can *ever* run is "can this fuel
  reach `requiredTemp`" — the climb only decides *when*. So the warm-up is just **added latency on the first craft**, not
  a separate condition to satisfy. (This layers on top of TFC's per-item heat model already baked into the melt/cook
  duration: the device sets the ambient ceiling, the item's heat capacity sets how fast it reaches its own transform
  temp.)

**Dual-fuel:** the forge accepts both `#tfc:forge_fuel` **and** `#tfc:firepit_fuel`; the temp gate decides feasibility
(a Smeltery stocks forge fuel for metal heat; a Kitchen/Restaurant stocks firepit fuel for ~200 °C cooking). One block
type therefore serves every hut — the operation's required temperature (and the shared ceiling it climbs toward) does
the sorting.

**No bellows — a deliberate balance handicap.** Our forge is *intentionally* **not** an `IBellowsConsumer`
(`net.dries007.tfc.common.blocks.devices.IBellowsConsumer` — the interface TFC's charcoal forge / firepit / blast furnace
implement to take bellows air; `HeatForgeBlock` *could* trivially `implements` it, so this is a **choice, not a gap**).
The forge is already strictly more convenient than a TFC forge (multiblock, self-processing, auto-tended, dual-fuel,
keep-warm); letting a player **also** bellows-boost it would make the vanilla TFC forge redundant. Withholding bellows
caps our device at its **fuel ceiling + building-level bonus — below bellows-boosted temperatures** — so the TFC charcoal
forge (+ bellows) keeps its niche for the hottest, bellows-dependent work. Our forge owns *convenient, automated bulk
cast-metal + cooking*; peak heat stays TFC's (dovetailing with iron/bloomery being out of scope, §17). **Tuning:** keep
the fuel-ceiling + max level-bonus **below the bellows peak** so the handicap actually bites.

---

## 6. Position lifecycle

Each position (one per member block) is in exactly one state; the controller self-tick advances the "running" ones, and
the tend-AI reacts to the holds:

```
EMPTY        no input loaded
HEATING      cooking 1 raw → 1 cooked (Cook/Chef)
ACCUMULATING melting ore → pouring metal into the output mold (Smelter)
CASTING      output mold full → spilling into the overflow mold
COLD         loaded, but deviceTemp < input.requiredTemp — no progress, no consumption
READY_NO_MOLD metal ready to pour but no mold seated (Smelter) — waiting on the worker
BLOCKED      finished, but output+overflow are full — waiting on a drain
INVALID      item has no heating recipe for this device — inert, worker ejects it
```

`deviceTemp` is shared (§5), so "is this position running *right now*?" is `deviceTemp ≥ input.requiredTemp`. But `COLD`
splits by cause: a position warming up under adequate fuel is **transiently `COLD`** (expected, self-clears as the device
climbs — the AI does nothing, it's just latency), whereas fuel whose *ceiling* can't reach `requiredTemp` is
**genuinely `COLD`** and never clears. The tend-AI prevents the latter by gating loads on the ceiling (§13), so genuine
`COLD` only arises from external insertion (hopper) or fuel that got downgraded mid-run.

---

## 7. Cook / Chef output — the trivial case

Cooking is `1 raw → 1 cooked`, no fill levels: `HEATING → DONE`, the cooked food lands in the position's output slot
(overflow catches a second finished item so the position keeps cooking a short batch). The worker drains it — to the
racks (Cook) or straight to the request (Chef, §11). No molds, no metal, no accumulation.

---

## 8. Smelter output — mold-based, with top-up

This is the substantive part. TFC molds are already fluid containers (up to **100 mB** of **one** metal, cast only when
full + solid, top-up-able with the same metal), so the forge just **pours into real containers** — partial metal is a
real item, not hidden BE state. The output + overflow slots accept **any fluid container** (a mold, or a larger vessel a
player might use); the colony worker uses **molds**, so the model below is written for molds (100 mB / one ingot).

**Fill → spill.** A melt position holds one ore in its heat slot and seats **two containers**: one in the **output**
slot, one in the **overflow** slot. As ore melts, liquid metal fills the **output container to capacity first** (100 mB
for a mold), then spills into the **overflow container**. Because heat slots take one ore at a time (§4), the metal
accumulates over successive single-ore melts. Because fill is
output-first, the overflow only ever receives metal *after* the output is full — so a position is always in exactly one
of:

| melt total | output mold | overflow mold |
|---|---|---|
| `< 100` | partial | empty |
| `100 … < 200` | full | partial |
| `≥ 200` | full | full → **excess spills (lost)** |

**⇒ there is never more than one partial mold in play per position.** Metal melted beyond both molds' 200 mB has no
container to catch it and **spills — it's lost** (TFC-authentic). The melt doesn't stop for lack of room, so it's on the
tend-AI to keep molds seated and drained; it sizes each ore load to the seated molds' free capacity so it isn't melting
metal it can't catch (§10).

**Collect → sort → top-up.** The worker pulls both molds and sorts:
- **full (100 mB)** → cool → `CastingRecipe.assemble` → ingot (today's `MOLD_UNLOAD` extraction, unchanged),
- **partial (< 100 mB)** → keep, and later **re-seat in the output slot** to be topped up.

**Seating rule** (falls straight out of the "≤ 1 partial" invariant): the worker seats **either**
`(one partial mold of matching metal → output, one empty → overflow)` **or** `(two empty molds)`. A partial never lands
in the overflow slot; two partials never coexist. Consequences:
- **Metal selection is deterministic** — a seated partial *dictates* the position's metal (the tend-AI pairs it with
  matching ore); empty-in-output = free choice (both molds lock to whatever is melted first). No mid-melt metal
  reconciliation, and **no mixing check anywhere** — the invariant guarantees purity.
- **Stepped completion falls out**: `">"` (250 mB → 2+ casts) pushes several molds out as molds are supplied; `"<"`
  (60 mB) leaves a partial that's topped up next batch. To switch a position's metal, seat two empties and pocket the
  partial for a later matching batch (a worker may carry partials of several metals, seating each opportunistically).

**Partial metal survives a break** because it lives in a mold item (drops on break), unlike an intangible mB buffer.

---

## 9. Multiple metals per controller

The `≤ 1 partial` invariant is **per position**, not per controller. Each position has its own heat slot + mold pair, so
a controller melts **up to N metals at once, one per position** — the molds never touch, so nothing mixes.

The only shared limit is the **one device temperature**, and it's a *ceiling*, not a per-metal lock: every position whose
metal melts at/below the current temp runs; hotter-melting ones `COLD`-stall. Overheating already-liquid metal is
harmless, so a hot device melting several metals at once is fine.

| Scope | Metal constraint |
|---|---|
| **one position** | one metal at a time (its output mold locks a metal; finish/clear the partial to switch) |
| **one controller** | up to N metals concurrently, all meltable at the current device temp |

So the shared temp just means "the fuel must be hot enough for the hottest metal you want running; cooler metals come
along for free."

---

## 10. Overflow, spill & back-pressure

The **overflow slot is the second mold** (Smelter) / a second finished-food landing (Cook), giving each position a
**2-deep buffer** so the block keeps processing a short batch autonomously. What happens when that fills differs by
output kind:

- **Smelter (fluid):** metal melted beyond the seated containers' capacity **spills and is lost** — molten metal with
  nothing to catch it, exactly like TFC. The melt never stops for lack of room, so the worker keeps containers seated and
  drains promptly; since heat slots hold **one ore at a time** (§4), the tend-AI simply **holds off feeding the next ore
  while the containers are near full** (`containerFreeCapacity`). Ore melted into spilled metal is wasted — the cost of
  falling behind, not a stall.
- **Cook (item):** a cooked-food item can't spill, so a position with both output + overflow full simply `BLOCKED`-holds
  until the worker drains it — nothing lost, just paused.

The worker drains promptly either way (hot molds staged to the racks to cool + extract; cooked food to racks / the
request).

---

## 11. The `ForgeController` API (worker-facing façade)

The tend-AI and Chef driver program against this and nothing else:

```
isLit() / light() / extinguish()                // worker lights before loading; explicit extinguish after keep-warm
lastActiveTick()                                // stored game-time of the last completed op (persists in NBT; a timestamp)
needsFuel() / addFuel(stack)                    // adds to the TOP of the fixed 5-slot column; only the bottom burns (§4/§5)
deviceTemp()                                    // LIVE temp — for rendering/timing only; climbs toward the ceiling
canReach(requiredTemp)                          // feasibility = the fuel CEILING ≥ requiredTemp (what the AI gates loads on)
freeHeatSlots() / loadInput(stack)              // per-position input; slot accepts ANY heatable item, 1 per slot
state(pos)                                      // the lifecycle enum (§6)
positionsNeedingContainer() / seatContainers(pos, out, overflow)  // Smelter — any fluid container; worker uses molds (§8)
containerFreeCapacity(pos)                      // Smelter — so the AI holds off feeding before a spill (§10)
takeFinished() / takeFinished(match)            // drain cooked food / cast-ready molds (match = the Chef's primaryOutput)
takeUnprocessable()                             // eject INVALID items
```

The controller **self-ticks** internally: if lit, burn shared fuel → climb `deviceTemp` toward the ceiling → advance
every runnable position (cook: heat 1 piece; melt: pour into output→overflow, spilling past 200 mB) → surface holds; if
the fuel runs out it goes unlit and cools. The worker never touches ignition timing — it only lights, feeds, seats molds,
and drains.

---

## 12. Discovery — grafted `ForgeUserModule`

The forge isn't a `FurnaceBlock`, so `FurnaceUserModule` won't see it. We graft a **`ForgeUserModule`** onto the
Smeltery/Restaurant/Kitchen (via the existing module-graft seam,
[MixinAbstractBuildingModule](../compat/src/main/java/com/mctfc/mixin/MixinAbstractBuildingModule.java)) that mirrors
`FurnaceUserModule#onBlockPlacedInBuilding` for our block, then folds adjacent members into `ForgeController`s. Chosen
over mixing into `FurnaceUserModule` so the native Chef path never sees forge positions it can't drive.

**Placement — substitute every furnace.** A blanket rule `minecraft:furnace → mctfc:heat_forge` (in the `:compat` TFC
datapack, `data/mctfc/block_substitutions/`, copying `facing`) swaps **every** blueprint-placed furnace to a forge;
adjacent ones auto-merge (§4) and `ForgeUserModule` registers them. *For now* it's blanket — any furnace, even a
decorative one in a house (it just becomes a player-usable forge with no worker); scoping it to the worker huts is a
later refinement. This rule ships **with** the forge implementation — it **supersedes** the current vanilla-furnace
Smelter/Cook/Chef path and the decorative-furnace GUI block for blueprint furnaces — so it lands as one coordinated
feature, not before.

**No migration needed** — the mod is **pre-release**, so there are no legacy worlds with placed vanilla furnaces. The
forge system therefore **fully replaces** the current vanilla-furnace Smelter/Cook/Chef path (no fallback, no on-load
migration, no coexistence): the substitution + forge block + `ForgeUserModule` + tend-AI land together as *the*
furnace-worker implementation. (The recipe/fuel/heat logic carries over — `CookRecipes`/`SmelterRecipes`, `FurnaceFuel`,
the completion code; only the vanilla-furnace *driving* — dispatcher, `litTime` mixin, the Chef ignite mixin — is
retired.)

---

## 13. Tend-AI — Smelter & Cook

Both are `AbstractEntityAIUsesFurnace`, so the existing [FurnaceBehavior](../compat/src/main/java/com/mctfc/furnace/FurnaceBehavior.java)
dispatcher keeps driving them; one bridge change: [FurnaceWorker](../compat/src/main/java/com/mctfc/furnace/FurnaceWorker.java)`.furnaces()`
→ `controllers()`. Because the block self-processes, the loop **loses ignition/timing/completion** and becomes pure tend:

- **`STAGE`** (at the hut): top the carried inventory with fuel + inputs, sized to `Σ freeHeatSlots()` across controllers
  (+ empty molds for the Smelter).
- **`TEND`** (one visit *per controller*): **drain first** — `takeFinished()` → inventory, `takeUnprocessable()` →
  racks; **then make it ready** — `if needsFuel()` → `addFuel()` to top the **one 5-slot column** (§4), and `if !isLit()`
  → `light()` (the forge must be lit *before* loading, and warms up while it runs); **then load** — into every free heat
  slot, gated on the fuel's
  **theoretical ceiling** (`FurnaceFuel.hasFuelHotEnough` / `canReach`), i.e. *can this fuel reach `requiredTemp`* — **not**
  the live `deviceTemp`. So the worker loads freely into a cold, just-lit forge and doesn't wait for it to be hot; the
  warm-up is absorbed as **longer craft time**, not an AI wait. **Smelter**: `seatMolds()` per the seating rule (§8),
  preferring to top up carried partials, and **size each ore load to `moldFreeCapacity(pos)`** so melted metal isn't
  spilled (§10).
- **`MOLD_UNLOAD`** (Smelter, unchanged): cooled **full** molds → TFC casting extraction; partials held for re-seating.

**Keep-warm / extinguish.** The controller stamps `lastActiveTick` each time a position finishes. When there's no work,
the AI doesn't drop the forge cold: it keeps it lit and fed while `now - lastActiveTick ≤ forgeKeepWarmTicks` (next job
starts hot, no warm-up); once `now - lastActiveTick > forgeKeepWarmTicks` with nothing queued, it calls `extinguish()`.
Just a stored timestamp compared to the threshold on each check — no per-tick counter, and it survives reload. This is
the one place the "tend + check state" AI spends fuel with no immediate output — the config trades that against warm-up
latency (§5).

Factor the common load/fuel/drain into a small **`ForgeTender`** shared by Smelter, Cook, and the Chef; each differs only
in its `selectInput` policy (ore+molds / menu-cookable raw / the request's input) and where output goes. Note the
worker's policy is **narrower than the slots accept**: the forge takes any heatable item / any fluid container (for the
player, §4), while the worker only ever feeds its hut's subset — and leaves player-placed items alone unless it needs the
slot.

---

## 14. Chef driver

The Chef is `AbstractEntityAIRequestSmelter` (a request crafter) — the furnace dispatcher can't reach it, and its native
FURNACE path iterates `FurnaceBlockEntity` positions our custom block isn't. So we **replace that path** (evolving
today's [MixinAbstractEntityAIRequestSmelter](../compat/src/main/java/com/mctfc/mixin/MixinAbstractEntityAIRequestSmelter.java)),
**gated to `currentRecipeStorage.getIntermediate() == FURNACE`** — grid crafting (sandwiches, composed salads/soups)
falls through to native `AbstractEntityAICrafting` untouched. A small state machine over a `ForgeController`, reusing
`ForgeTender`:

```
CHECK  → controller has finished output matching primaryOutput?  → RETRIEVE
       → controller low on fuel or unlit?  → gather fuel, addFuel(), light()   (make it ready before loading)
       → a lit forge with a free heat slot AND the raw input in inventory?  → LOAD
       → raw input missing?  → needsCurrently=(input,n); GATHERING_REQUIRED_MATERIALS   (native, reused)
LOAD   → controller.loadInput(input.copyWithCount(1))            (controller cooks it once warm)
RETRIEVE → for s in controller.takeFinished(match primaryOutput):
             currentRequest.addDelivery(requestStack(s.count))    // straight to addDelivery (decided)
             job.setCraftCounter(+ s.count)
             if counter ≥ maxCraftingCount: finalizeCraftingTask()
```

`addDelivery` / `craftCounter` / `finalizeCraftingTask` are lifted verbatim from the native
`retrieveProductFromFurnace`, so the colony still considers the request fulfilled. The input→output identity already
holds: the teach ([MixinContainerCraftingFurnace](../compat/src/main/java/com/mctfc/mixin/MixinContainerCraftingFurnace.java))
fixes the recipe's output to the TFC heating result, which is exactly what the controller produces. `COLD`/`INVALID`
positions never count as progress. The overflow lets the forge cook the whole batch while the Chef is off gathering.

---

## 15. What's shared vs. per-worker

| | Smelter | Cook | Chef |
|---|---|---|---|
| AI hook | dispatcher | dispatcher | mixin on the request path (FURNACE-gated) |
| Tends via | `ForgeTender` | `ForgeTender` | `ForgeTender` |
| `selectInput` | ore + molds | menu-cookable raw | the request's recipe input |
| output → | racks (+ `MOLD_UNLOAD`) | racks | `addDelivery` + craftCounter |
| trigger | proactive | proactive | request-driven |

---

## 16. Decisions

**Locked:** fully custom block/BE/type + custom tend-AI (§2); grafted `ForgeUserModule` (§12); one shared device
temperature (§5, §9); **fuel is a fixed 5-slot column on the controller** — a 1-to-1 copy of TFC's charcoal forge (add at
top, cascade down, only the bottom burns), **decoupled from block count** (blocks scale input positions only) (§4, §5);
Chef output straight to `addDelivery` (§14); per-position output+overflow **mold** pair, not a
shared overflow (§8) — a shared overflow mold would force one metal per device; the mold-based partial/top-up model
replacing any intangible mB buffer (§8); the forge **rises to temperature gradually** while lit (TFC charcoal-forge
style) and the **worker lights it before loading** (no auto-ignite from fuel+input) (§5, §13); the AI gates loading on the
fuel's **theoretical ceiling** (can it reach `requiredTemp`), **not** the live device temp — so the warm-up is just added
craft latency (§5, §13); metal melted **beyond both molds spills and is lost** (fluid, TFC-authentic), while cooked-food
outputs `BLOCKED`-hold (item) (§10); the AI **keeps a forge lit for a configured idle window** (`forgeKeepWarmTicks`)
after the last op to skip the warm-up, then **explicitly `extinguish()`es** it — the fuel-vs-latency knob (§5, §13); the
forge is a **player-usable** device (editable shared GUI — nothing to bypass) with **1-item slots** (TFC-forge style),
heat slots accepting **any TFC-heatable item** and output/overflow accepting **any fluid container**, while the worker
feeds only its hut's subset (§4, §8); **deliberately not an `IBellowsConsumer`** — a balance handicap (peak temp capped
below a bellows-boosted TFC forge) so our strictly-more-convenient device doesn't obsolete TFC's own forge (§5);
**no hopper/pipe access** — the BE exposes no item-handler capability, so item access is player-GUI + worker only, a
handicap that keeps the forge from being fully auto-piped (§4); membership is a **deterministic ≤5-member geometry
partition** (BFS from the region's min `BlockPos`), a device **runs only when its whole region is loaded** (else freezes,
timestamp timers resuming exactly), and **nothing fractional is lost on break** — hot items + real-fluid molds drop, no
buffer (§4).

**Open / minor:**
- Cook-side overflow depth (the 2-deep output+overflow, or wider if throughput needs it).
- **Membership storage** (§4): the ≤5 partition is geometry-derived, but it must be **persisted** as a cache for
  partial-chunk-load safety — the impl detail is how (per-block device-id vs. a controller-held member list) and when to
  recompute (on adjacency change while fully loaded).
- Whether a **large region re-partition on break** may reshuffle positions between controllers (deterministic but can
  interrupt more than the broken block's work) is acceptable, or worth minimising by favouring the incumbent controller.
- **Material variants** (§4): separate blocks vs. a `material` blockstate property; merge-freely vs. same-material-only;
  which variant the blanket substitution places (default now, palette-matched later); whether to eventually **scope** the
  substitution to worker huts rather than every furnace.
- Default `forgeKeepWarmTicks` and whether it's a global config or a per-hut setting (like the Smelter's `ore_threshold`).
- Device-temp rise rate **and** the fuel-ceiling + max level-bonus cap — tuned so it stays below the bellows peak (§5),
  keeping the no-bellows handicap meaningful.

---

## 17. Scope — out for v1

- **Iron / bloomery is deferred.** The mold-based output is inherently *cast-metal* machinery; iron produces no liquid to
  pour (`ore + 2 charcoal → tfc:raw_iron_bloom`, no mold, hammered on an anvil) and `COLD`-stalls in a mold forge anyway.
  It returns later as its own **bloom mode** (charcoal-only reductant, bloom into the output slot, no overflow) or a
  distinct device. Interim: the Smelter tend policy **must not load iron ore** into the forge (it'd jam a position) — a
  known gap, not a silent failure. v1 handles the cast metals only (copper/tin/bismuth/zinc/silver/gold/nickel).
- **Glassblower** — another `FurnaceUserModule` hut; it becomes a forge consumer once it has a glass-heating completer,
  orthogonal to this block.

---

## 18. Build order & status

**Done (slices 1–4) — compiled; block + GUI in-game-verified standalone; the worker flow goes live at the switchover:**

1. ✅ `HeatForgeBlock` + `HeatForgeBlockEntity` (+ type) + `ForgeMultiblock` (deterministic ≤5 BFS partition +
   controller election, stable across reload) + shared lit / gradually-rising `deviceTemp` / fixed 5-slot fuel column +
   **cook self-tick** + `ForgeController` façade. Files: `com.mctfc.forge.{HeatForgeBlock, HeatForgeBlockEntity,
   HeatForgeBlocks, ForgeController, ForgeState, ForgeMultiblock}`, `Config.forge*`, block assets.
2. ✅ `ForgeUserModule` (+ static `PRODUCER`) grafted onto Smeltery/Restaurant/Kitchen via `MixinAbstractBuilding`
   (ctor-TAIL `registerModule`); merged **interactive** GUI (`ForgeMenu`/`ForgeScreen`) — **not** read-only as first
   planned — over a **hand-paintable texture asset** (`assets/mctfc/textures/gui/heat_forge.png`); flint-and-steel
   player lighting. Substitution placement is **deferred to the switchover** (step 6).
3. ✅ `ForgeTender` (shared stage/drain/refuel/light/load/keep-warm) + `FurnaceWorker.controllers()` bridge (resolves
   the grafted `ForgeUserModule`); `CookBehavior` retargeted to forge controllers (serving + demand-scaled auto-request
   preserved).
4. ✅ Smelter **melt path** in the BE (ore → metal, pour output→overflow, spill past 200 mB, ≤1-partial `normalizeMolds`)
   + mold façade (`seatContainers`/`containerFreeCapacity`/`seatedMetal`/`outputHasMold`/…); `SmelterBehavior` retargeted
   (mold staging + seating, metal-matching capacity-sized ore loading, `MOLD_UNLOAD` casting). **Cast metals only** —
   `accepts` excludes iron.

**Done (slices 5–6) — compiled; colony-flow in-game validation still pending:**

5. ✅ **Chef driver** — `MixinAbstractEntityAIRequestSmelter` `@Inject`s `executeCraftingAction` (HEAD, cancellable),
   gated `BuildingKitchen` + `getIntermediate() == FURNACE` + forges present; tends the `ForgeController`s via a
   `ChefForgeTender` (`ForgeTender` Context+Policy keyed to the current recipe input) and delivers finished-matching-
   primary straight to the request (`addDelivery` + `setCraftCounter` + `finalizeCraftingTask`, mirroring native
   `retrieveProductFromFurnace`). Returns `CRAFT` to loop; non-FURNACE / non-Kitchen fall through to native.
   **Deviations from §14:** (a) input is **staged from the hut racks** (`tender.stage`) rather than gathered via
   `needsCurrently`/`GATHERING_REQUIRED_MATERIALS`; (b) the worker **tends remotely** (no walk-to-forge — `craft()`
   already walked it to the work pos); (c) `currentRecipeStorage`/`currentRequest`/`finalizeCraftingTask` are read via a
   **`CraftingAiAccess` interface** implemented by a second mixin on their declaring class `AbstractEntityAICrafting` —
   a `@Shadow` of these *inherited* members (even from the direct superclass) crashes at apply time in this setup (see
   the mixin note in CLAUDE.md; two crashes cost this). Files: `mixin.MixinAbstractEntityAIRequestSmelter`,
   `mixin.MixinAbstractEntityAICrafting`, `cook.CraftingAiAccess`, `cook.ChefForgeTender`.
6. ✅ **Final switchover** (§12): shipped the blanket `minecraft:furnace → mctfc:heat_forge_brick` substitution
   (`data/mctfc/block_substitutions/tfc_furnace.json`; the engine auto-copies `facing`) **and** retired the vanilla-
   furnace driving — **deleted** `FurnaceHeating`/`FurnaceProcess`/`FurnaceProcessing`/`FurnaceProcessings`/
   `FurnaceProcessCapability`/`CookProcessing`/`SmelterProcessing` + the `MixinAbstractFurnaceBlockEntity` (litTime) /
   `FurnaceBlockEntityAccessor` mixins (dropped from `mctfc.mixins.json`), removed the Chef ignite + the dead
   `FurnaceWorker.furnaces()`, trimmed `FurnaceFuel` to its live `isFuel`/`hasFuelHotEnough` predicate, and dropped the
   `FurnaceProcessCapability`/completer registrations from `MineColoniesTFC`. **Deviations:** (a) the substitution targets
   a **single default variant, `heat_forge_brick`** — a global block→block rule can't pick per-hut (per-hut variants would
   need the interactive `to_tag` picker or the per-building integration); (b) `VanillaFurnaceHandler` (the decorative
   interaction blocker) was **kept** — it's config-gated (`decorativeVanillaFurnaces`, default **false**) and guards
   *player-placed* vanilla furnaces (a TFC-progression bypass), orthogonal to the forge system, so deleting it would drop
   an unrelated safeguard. **Not yet colony-tested end-to-end** — that's the one remaining checkpoint.
7. **Deferred (post-v1):** iron/bloom mode (charcoal-only reductant, bloom into the output slot, no overflow — the
   Smelter policy already excludes iron ore so it can't jam a position); Glassblower (needs a glass-heating completer).

### Hard-won fidelity fixes (record so they aren't accidentally "re-fixed")

Found during in-game GUI/behaviour tuning against real TFC — verified against the TFC jar:
- **GUI is a hand-paintable texture**, fixed at the 5-row max size. A smaller forge shows painted-but-inactive wells for
  absent rows — the "flex to member count" idea (§4) is **not** implemented (would need section-blitting). The gauge
  gradient **and** the marker sprite both live in the asset (marker at `u=176`).
- **Gauge = TFC's own primitives:** static gradient (15 wide × 50 tall) in the asset; a sliding **marker sprite** blitted
  at `Heat.scaleTemperatureForGui(temp)` (TFC's 0..51 scale), **hidden at scale 0** like TFC (so it vanishes at TFC's
  visible-heat threshold, not at 0 °C), with **no bottom clamp** so it rides to the last pixel; hover tooltip =
  `TFCConfig.CLIENT.heatTooltipStyle.get().formatColored(temp)` — **null-guarded**, it returns null below visible heat —
  triggered by `RenderHelpers.isInside`. `GAUGE_Y/H` are bottom-anchored (`38`/`50`) to match a hand-raised gradient.
- **Fuel = TFC forge:** 1-item fuel slots; cascade **every tick** (placed fuel packs to the bottom even when unlit); the
  bottom item is **consumed at ignition** (`AbstractFirepitBlockEntity.consumeFuel` empties the slot), *not* kept visible
  until burnout.
- **Items heat continuously** toward `deviceTemp` (they glow during warm-up) and transform only once they reach their
  own recipe temperature — they do **not** wait for the device to reach the transform temp first.
- **Molds seat by item class** (`stack.getItem() instanceof MoldItem`) in the output/overflow filter, because TFC empty
  molds don't reliably expose `FLUID_HANDLER_ITEM` **client-side**, where the slot's `mayPlace` is first checked.
- **Forge discovery keys on the WORLD block, not the passed blockstate** (§12 assumption was wrong). MineColonies
  registers a built block under its **blueprint** state (`CreativeBuildingStructureHandler` calls
  `registerBlockPosition(blueprint.getBlockState(pos), …)`), which is still `minecraft:furnace` — the substitution swaps
  the *world* block, not the blueprint data. So `ForgeUserModule.onBlockPlacedInBuilding` is handed a *furnace* and its
  `instanceof HeatForgeBlock` check never matched → forges were placed but never registered → the Cook idled. Fix:
  `onBlockPlacedInBuilding` checks `world.getBlockState(pos)` (the block is already placed when it fires), **plus** a
  `reconcileFromFurnaces()` in `getControllers` that adopts any position the native `FurnaceUserModule` tracks (it *did*
  register them — the blueprint said furnace, so *its* `instanceof FurnaceBlock` passed) that now holds a forge in the
  world — so a hut built before this fix self-heals with no rebuild.
