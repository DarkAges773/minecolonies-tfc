# TFC furnace workers — design

How `:compat` (`mctfc`) replaces the vanilla-furnace behaviour of MineColonies' furnace-using workers with
TFC-flavoured processes, starting with the **Smelter** and built so the **Cook** (and other furnace huts) can
reuse the same machinery.

Status legend: **DONE** (built & in-tree), **PLANNED** (designed here, not yet built).

---

## 1. Motivation

Seven MineColonies buildings drive **vanilla furnaces** (`FurnaceBlock`/`FurnaceBlockEntity` only — never
smoker/blast furnace): Smeltery, Cook, Bakery, Kitchen, Dyer, Glassblower, Stone Smeltery. Their AI loads a
smeltable + fuel into the furnace block entity, lets it vanilla-smelt, and extracts the result.

In a TFC world that's wrong on two counts: TFC's recipes aren't vanilla furnace recipes, and TFC metallurgy
isn't "ore in → ingot out" at all (it's heat→liquid→cast, with iron via a bloomery). `:compat` already makes
the vanilla furnace **player-decorative** ([VanillaFurnaceHandler](../compat/src/main/java/com/mctfc/block/VanillaFurnaceHandler.java)),
so the goal here is to give the *workers* TFC-correct processing while keeping the existing buildings/GUIs.

Most furnace huts are tractable (they fulfil specific craftable recipes); the **Smelter** and **Cook** are the
hard, novel ones. This doc covers the smelter in full and the shared framework the cook will reuse.

---

## 2. Architecture — a behaviour controller, not a mixin chokepoint  — **DONE**

The smelter/cook AIs both extend `AbstractEntityAIUsesFurnace`, whose loop lives in **private** base methods.
Rather than grow an `instanceof` chain inside one base mixin (a chokepoint as we convert more huts), the base
mixin is a thin **dispatcher** that routes to a pluggable strategy:

- **`FurnaceBehavior`** ([src](../compat/src/main/java/com/mctfc/furnace/FurnaceBehavior.java)) — strategy per
  worker: `targets()` (its custom AI states) + `startWorking()` (its loop entry). One instance per AI (holds
  per-worker progress).
- **`FurnaceWorker`** ([src](../compat/src/main/java/com/mctfc/furnace/FurnaceWorker.java)) — bridge the
  behaviour drives the worker through: `worker()/building()/world()/buildingLevel()/furnaces()`,
  `gotoBuilding()/gotoWorkPos()/delay()/state()`.
- **`FurnaceBehaviors`** ([src](../compat/src/main/java/com/mctfc/furnace/FurnaceBehaviors.java)) — registry,
  keyed by AI class: `register(EntityAIWorkSmelter.class, SmelterBehavior::new)`. **Adding a converted hut =
  one registration line, no new mixin code.**
- **`MixinAbstractEntityAIUsesFurnace`** ([src](../compat/src/main/java/com/mctfc/mixin/MixinAbstractEntityAIUsesFurnace.java))
  — ctor TAIL: build the behaviour for this AI's class (if registered) and register its states; `startWorking`
  HEAD cancellable: route to `behaviour.startWorking()`. Unconverted huts (Cook today) run vanilla untouched.

### Mixin gotcha (cost a crash to learn)
`@Shadow` of **inherited** members fails to apply (`"method … was not located in the target class"`) — same
class of failure as the `world` field in `MixinEntityAIWorkFarmer`. So the dispatcher:
- derives `worker`/`building`/`world` from the **captured `job` ctor arg** (no field shadow);
- reaches **public** helpers (`setDelay`/`getState`/`registerTarget`) by casting to their declaring class;
- reaches **protected** `walkToBuilding`/`walkToWorkPos` via an `@Invoker` on the class that *declares* them
  ([AbstractEntityAIBasicInvoker](../compat/src/main/java/com/mctfc/mixin/AbstractEntityAIBasicInvoker.java)).

Registering the behaviour at mod-construction (`EntityAIWorkSmelter.class` reference) forces the dispatcher to
apply at startup, so shadow/inject failures surface immediately rather than lazily.

---

## 3. The furnace itself is the container  — **DONE**

The work lives in the **vanilla furnace's own slots** — so it **drops when the furnace is broken** and is
**visible in the furnace GUI** (which the player can enable via `decorativeVanillaFurnaces=false`) — and the
melt timer/flame is the vanilla **`litTime`**. The only state that can't ride in those is the carried fuel
pool, which rides in a small capability attached to the furnace BE.

| Where | Holds |
|---|---|
| Furnace **input** slot (0) | the ore being melted — drops on break, re-read at collect for the metal |
| Furnace **fuel** slot (1) | the fuel — consumed from here, restocked from the racks; iron's path skips it |
| Furnace **result** slot (2) | the empty mold → **filled in place** with the hot ingot (iron → a hot bloom) |
| Vanilla **`litTime`** (BE NBT) | melt timer **and** flame |
| [`FurnaceProcess`](../compat/src/main/java/com/mctfc/furnace/FurnaceProcess.java) cap | the **phase** (`IDLE`/`MELTING`/`DONE`) + the **carried fuel pool** `(ticks, temp)` |

> **Why not a standalone custom block+BE, or our own item handler:** MineColonies blueprints place
> `minecraft:furnace` (`FurnaceUserModule` registers `instanceof FurnaceBlock`), so a custom block would mean
> substituting/replacing furnaces everywhere — invasive, breaks the decorative-furnace feature, needs
> migration. And an item handler on *our* capability wouldn't drop on break or show in the GUI. Using the
> vanilla slots gives drop-on-break + visibility for free; the cap carries only the one thing that can't (the
> partial-burn pool). (Earlier drafts stored everything in the cap or in building NBT — both invisible and
> non-dropping.)

### `litTime` as timer + flame — why it's safe
The vanilla furnace BE decrements `litTime` **every tick unconditionally** while lit, so setting
`litTime = meltDuration` (via the `FurnaceBlockEntityAccessor` `@Accessor` mixin) *is* the melt countdown, and
the BE flips `LIT` off when it expires. TFC ore has **no vanilla smelting recipe**, so the BE's
auto-smelt/auto-fuel-burn branch never fires — it never touches our ore, fuel, or result; it only counts down.
Because `litTime` and the cap both live in furnace BE NBT, an in-progress melt **resumes exactly** after reload.

### The furnace finishes itself; the worker only loads and hauls out
Completion is **furnace-driven, not worker-driven**: a small `@Inject` at the tail of
`AbstractFurnaceBlockEntity.serverTick` ([MixinAbstractFurnaceBlockEntity](../compat/src/main/java/com/mctfc/mixin/MixinAbstractFurnaceBlockEntity.java))
watches for a furnace whose cap is `MELTING` and whose `litTime` has hit 0, and runs
[SmelterProcessing.complete](../compat/src/main/java/com/mctfc/smelter/SmelterProcessing.java) — which turns the
ore + mold in the slots into the finished casting **in place** (the result-slot mold fills with the cast ingot;
iron becomes a hot bloom), consumes the input, and flips the cap to `DONE`. So the moment the flame dies the
mold is filled, wherever the worker happens to be (and it resumes correctly after a reload). The inject is gated
cheaply (unlit **and** something in the input slot) so idle furnaces cost almost nothing.

### Worker flow — stage a batch, load, retrieve
Like the vanilla furnace workers, the worker **carries a batch** of materials in its own inventory and loads
furnaces from there (it keeps no per-furnace job map; it reads each furnace's cap phase):
- **stage** — when it can't load anything from the batch it's carrying, it pulls a fresh batch of one makeable
  metal's materials from the **racks** into its **inventory**: up to a stack of ore + the molds (cast) / fuel,
  or charcoal (iron). Bounded (a few stacks) so it never trips the worker's full-inventory dump.
- **`IDLE`** furnace → load it from the **carried batch**: one ore grade (≥100 mB) → input, an empty mold →
  result, burn fuel through the fuel slot; light it (`litTime = meltDuration`) and set `MELTING`.
- **`MELTING`** → leave it; the furnace finishes on its own.
- **`DONE`** → haul the finished item out of the result slot into the **racks**, award XP, set `IDLE`.

The metal/fluid is re-derived from the **input-slot ore** (at completion). Ore is consumed as a **single grade
per melt** (so the input slot holds the exact stack that drops on break); the carried fuel pool persists in the
cap. Staging into the inventory is the seam the colony **request system** will later feed (deliver to the
building → worker stages → loads).

### Caveats
- The furnace's slots are exposed on its faces, so a **hopper could pull ore/fuel/mold mid-melt** (the furnaces
  sit in the hut → low risk; block sided access later if it matters).
- With the GUI enabled the player can **hand-edit** a working furnace; the worker tolerates it (it re-reads
  slot state each pass and degrades gracefully — e.g. a missing mold at collect just returns nothing to fill).

---

## 4. Fuel — reusable, temperature-gated, duration-pooled  — **DONE**

[`FurnaceFuel`](../compat/src/main/java/com/mctfc/furnace/FurnaceFuel.java) is shared by every behaviour. The
"which hut can burn what / make what" split is driven by **required temperature per operation**, never by
hard-coded per-hut fuel lists.

- **Fuel** = any TFC fuel (`Fuel.get != null`); each has a TFC `duration` (ticks) and `temperature` (°C).
- **Effective heat** at a furnace = `fuel.temperature +` a **per-level bonus** (config
  `furnaceFuelTempBonusByLevel`, a concrete value per building level — default `[0, 15, 30, 45, 60]`, so +60 at
  L5; levels beyond the list use the last entry, an empty list disables it — `Config.furnaceFuelTempBonus(level)`).
- **Eligibility gate** (orthogonal to duration): an operation needing `requiredTemp` can only run if a fuel
  with `effectiveHeat ≥ requiredTemp` is (or can be) burning. TFC-authentic: the heat-up *rate* is fixed by
  `heat_capacity`, the device temperature is only the *ceiling* — so hotter fuel doesn't melt faster, it just
  decides **whether** the metal can melt.
- **Longevity** — fuel is consumed by **duration with carry-over**: one charcoal (1800 ticks) covers ~4 copper
  melts (~450 ticks each). The pool tracks the current fuel's remaining ticks.

Each behaviour supplies its own `requiredTemp`, which *is* the hut split — for free:
- **Smelter** → the metal's melt temp (from the heating recipe).
- **Cook** → the food's cooking temp (~200°C), so logs/peat work for cooking but never for metal.

Fuel reference (TFC data): charcoal 1800 t / 1350 °C · coal 2200 t / 1415 °C · logs ~1000–1750 t / ~600–720 °C.

**As built (§3):** fuel physically sits in the furnace's **fuel slot** (so it drops on break and shows in the
GUI), consumed from there and restocked from the racks; the partial-burn pool `(ticks, temp)` rides in the
furnace's `FurnaceProcess` cap, so it **persists across reload**. `FurnaceFuel` is **stateless** — the pool and
the furnace's fuel slot are passed in, a new pool returned — and the temp gate decides which rack fuel is hot
enough to stock for the metal. Iron skips this (its 2 charcoal are consumed from the racks as the bloomery).

---

## 5. Smelter specifics  — **DONE**

### Collapsed metallurgy
[`SmelterRecipes`](../compat/src/main/java/com/mctfc/smelter/SmelterRecipes.java) collapses TFC's
heat→liquid→cast pipeline into one worker action. Melt amounts are uniform per ore grade (small 10 / poor 15 /
normal 25 / rich 35 mB; **100 mB = one output**), so `N` ore → 1 output is rich 3 / normal 4 / poor 7 / small 10.

- **Cast metals** (copper/tin/bismuth/zinc/silver/gold/nickel): 100 mB ore + a mold → one ingot, **delivered
  in the mold, filled and heated to just below its melting point** (just solidified, glowing). The mold breaks
  later, on the player's own extraction (TFC handles that).
- **Iron** (hematite/magnetite/limonite): the bloomery path — 100 mB ore + 2 charcoal → one
  `tfc:raw_iron_bloom` (no mold; the player hammers it on an anvil). Nothing reaches the 1535 °C cast-iron
  melt, so iron is bloom-only — authentic.

### Melt duration (from TFC's heat model)
`≈ meltTemp × heat_capacity / 3` ticks (TFC heats an item by ~`3/heat_capacity` °C/tick), shortened by Strength.
Read live from `HeatCapability.get(ore).getHeatCapacity()` + the ore's `HeatingRecipe.getTemperature()`.

### Temperature-by-metal (default bonus `[0, 15, 30, 45, 60]`)
| Metal(s) | melt °C | wood (≤720) | charcoal (1350) | coal (1415) |
|---|---|---|---|---|
| Tin / Bismuth / Zinc | 232–420 | ✅ | ✅ | ✅ |
| Silver / Gold / Copper | 961–1080 | ❌ | ✅ | ✅ |
| Nickel | 1453 | ❌ | ❌ (max 1410) | ✅ at L4+ (1415+45) |
| Iron | bloom | — needs charcoal (bloomery) — | ✅ | — |

### Parallel furnaces & storage
Colony storage is the building's **racks** (`getContainers()`), not the hut-block inventory; the worker stages
batches out of the racks into its **own inventory** and loads furnaces from there (results go back to the
racks — see §3 "Worker flow"). All furnaces run in parallel: the worker loads each idle furnace, they cook
independently (`litTime`), and it hauls each result out as it finishes — so a 5-furnace hut runs five melts at
once.

### Skills
Strength shortens melt duration (primary skill); Stamina is the secondary. (Lucky-ore drops while mining are a
separate feature — see [compat-features.md](compat-features.md).)

---

## 6. Cook — reuse target  — **PLANNED**

The Cook converts by: a `CookBehavior implements FurnaceBehavior` + one `FurnaceBehaviors.register` line. It
reuses `FurnaceFuel` unchanged (passing the food's cook temp, ~200 °C, so cheap firepit fuels work) and the
furnace-container model (raw food in input, cooked food in result). Recipe source = TFC `heating` food recipes
(raw → cooked, preserving food data via `copy_food`); optional later: `pot` soups.

---

## 7. Decorative-furnace interaction

`VanillaFurnaceHandler` blocks the furnace **player GUI** (so players can't bypass TFC smelting), but the
worker drives the block entity directly, so automation is unaffected. The building's **fuel-list config tab**
(`ITEMLIST_FUEL`) becomes meaningful again now that workers consume fuel — keep it (no hiding).

---

## 8. Open items / future

- **Auto-requesting** ore / molds / charcoal / fuel through the colony (mirror MineColonies' `StackList`
  fallback, behind the `hasBuildingEnoughElseCount` + `hasWorkerOpenRequestsOfType` duplicate-guard). Today the
  smelter only works off stock in the racks.
- **Hopper protection** on furnace faces (§3 wrinkle).
- **Tuning** — `furnaceFuelTempBonusByLevel` (config, per-level list) and the melt-duration formula are easy knobs.
- **Fuel-pool persistence** — the longevity pool resets on reload (minor waste); could persist to building NBT
  if it matters.
- The other crafter huts (Bakery, Kitchen, Dyer, Glassblower, Stone Smeltery) — map their recipe lists to TFC
  equivalents; lower priority than Cook.

---

## 9. Build order

1. ✅ Controller + dispatcher + recipe model + first `SmelterBehavior` (rack storage, parallel furnaces,
   heat-model duration, filled-mold output, temp-gated/duration-pooled fuel from racks).
2. ✅ Furnace-as-container (§3): work in the vanilla furnace slots (ore/fuel/mold, drop-on-break + GUI-visible),
   `litTime` as the melt timer + flame (`FurnaceBlockEntityAccessor`), the `FurnaceProcess` cap carrying only
   the fuel pool; load/collect read slot state (no job map), exact reload resume; `FurnaceFuel` made stateless
   and slot-based.
3. ⬜ Auto-requesting (§8).
4. ⬜ `CookBehavior` (§6).
