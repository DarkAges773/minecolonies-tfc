# TFC beekeeper worker — design

How `:compat` (`mctfc`) makes MineColonies' **Beekeeper** harvest a **FirmaLife apiary** instead of a vanilla one.

Status legend: **DONE** (built & in-tree), **PLANNED** (designed here, not yet built).

**Status: DONE & in-tree** (pending in-game verification) — the
[FlBeekeeping](../compat/src/main/java/com/mctfc/firmalife/FlBeekeeping.java) bridge, the two beekeeper mixins
([MixinEntityAIWorkBeekeeper](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkBeekeeper.java),
[MixinItemScepterBeekeeper](../compat/src/main/java/com/mctfc/mixin/MixinItemScepterBeekeeper.java)), and the
[BeeFrameSetting](../compat/src/main/java/com/mctfc/settings/BeeFrameSetting.java) (+ factory + layout) are all
built and compile. This document is both the design and the as-built reference.

> **This is FirmaLife-specific, optional compat.** Base TerraFirmaCraft has **no beekeeping at all** — no
> hive, no bees, no honey. The whole feature therefore targets **FirmaLife** (mod id `firmalife`) and is gated
> on `ModList.isLoaded("firmalife")`; in a TFC-without-FirmaLife world the Beekeeper hut simply has nothing to
> work (there are no hives to register), and none of the FirmaLife-referencing code is ever loaded. There is
> **no base-TFC path** to design, unlike the herder huts. We do **not** support a mixed world that also has
> vanilla bees/hives (locked decision) — the bridge assumes a FirmaLife apiary throughout.

---

## 1. Motivation

MineColonies ships a **Beekeeper** hut
([EntityAIWorkBeekeeper](https://github.com/ldtteam/minecolonies), `BuildingBeekeeper`, `ItemScepterBeekeeper`)
hardcoded — at every layer — to the **vanilla** bee model: `BeehiveBlock` blocks in `#minecraft:beehives`, a
`BeehiveBlockEntity`, the `honey_level` blockstate (0–5), `Bee` entities, shears→`honeycomb` / glass
bottle→`honey_bottle`, and worker-driven love-mode breeding.

**FirmaLife's hive shares none of it.** It is a TFC-style device block + block entity with an integer honey
counter and a frame-and-capability bee model. So in a FirmaLife world the vanilla Beekeeper is entirely inert
(it can't even *register* a FirmaLife hive), and several of its code paths would **crash** (a
`ClassCastException` casting the FirmaLife BE to `BeehiveBlockEntity`).

### What breaks (grounded in the decompiled sources)

| Concern | Vanilla (what MineColonies expects) | FirmaLife (what a TFC world has) |
|---|---|---|
| **Block** | `net.minecraft.world.level.block.BeehiveBlock`, tagged `#minecraft:beehives` | `com.eerussianguy.firmalife.common.blocks.FLBeehiveBlock` (extends a TFC `FourWayDeviceBlock`), **not** in that tag |
| **Block entity** | `BeehiveBlockEntity` | `FLBeehiveBlockEntity` (extends TFC `TickableInventoryBlockEntity`) — **CCE** if cast as vanilla |
| **Honey level** | blockstate `BlockStateProperties.LEVEL_HONEY` 0–5 | BE field `int honey` (0..`getMaxHoney()`=12) + boolean blockstate props `HONEY`/`BEES` |
| **Bees** | `Bee` entities stored in the BE (`getOccupantCount`, `releaseAllOccupants`) | a **capability (`IBee`) on each frame item**; the in-world `FLBee` entities are cosmetic |
| **Honey product** | shears → `Items.HONEYCOMB`; glass bottle → `Items.HONEY_BOTTLE` | `tfc:empty_jar` → `firmalife:jar/honey`. **No honeycomb item exists.** |
| **Other product** | — | **beeswax** (`firmalife:beeswax`), scraped off a queened frame with a knife |
| **Breeding** | worker love-mode on two `Bee` entities (`setInLove`) + flowers in the hut | **autonomous** inside the hive: given ≥`MIN_FLOWERS`(10) flowers nearby, queens propagate into queenless frames on the calendar tick |

### The unifying facts

1. **All harvest reduces to reaching into `FLBeehiveBlockEntity`.** Honey is `be.getHoney()` / `be.takeHoney(n)`
   (both **public**). Wax/queen state is per-frame (`IBee.hasQueen()` via FirmaLife's `BeeCapability` on the
   frame item).
2. **FirmaLife breeds bees on its own.** The colony worker never breeds — it only **harvests and maintains**
   frames. This makes the worker's job *simpler* than the five herder huts (no familiarity ramp, no pairing).
3. **Scraping wax consumes a queen.** Verified in `BeehiveFrameItem.overrideOtherStackedOnMe`: a knife on a
   queened frame yields one `firmalife:beeswax`, **resets that frame slot to a fresh queenless frame in place**
   (no frame item is consumed), and damages the knife. So wax must be a *controlled* harvest — scraping every
   frame would slowly depopulate the apiary.

---

## 2. Architecture — a FirmaLife bridge behind runtime guards

Same shape as the other reworks: keep MineColonies' hut, GUI, job, scepter and AI state machine; insert
FirmaLife behaviour behind thin dispatch points. Two collaborators:

- **[FlBeekeeping](../compat/src/main/java/com/mctfc/firmalife/FlBeekeeping.java)** — the **only** class that
  names FirmaLife (and the bee-capability) types. Static helpers: hive recognition (`isHive`), honey readiness
  (`isReadyHive`/`firstReadyHive`), honey harvest (`takeHoney`), frame read / wax-scrape / empty-frame refill,
  queen detection (`IBee.hasQueen` via `BeeCapability`), and the item handles (`tfc:empty_jar`,
  `firmalife:jar/honey`, `firmalife:beeswax`, `firmalife:beehive_frame`). Because it is the sole FirmaLife-naming
  class, it is **loaded lazily** and every caller guards with `ModList.isLoaded("firmalife")` *before* touching
  it — so a non-FirmaLife install never loads it and never hits a `NoClassDefFoundError`. (FirmaLife is a
  `compile`-scope/`implementation` dependency of `:compat`, so this compiles; it is **not** mandatory in
  `mods.toml`.) Same optional-integration idiom as `:replacements`' `integration.minecolonies`.

- The **mixins** (all `@Mixin(remap = false)` — MineColonies'/TFC's own members) route the Beekeeper into the
  bridge. They are **always applied** (their targets — MineColonies/TFC classes — are always present); only the
  FirmaLife branch *inside* each is `ModList`-gated. There is no per-config mixin gating (unlike `:replacements`'
  optional MineColonies mixin config).

### Reaching the frame slots (the one non-obvious access)

FirmaLife's hive exposes only its **jar** slots through the Forge item-handler capability (horizontal faces
insert the empty jar into `SLOT_JAR_IN`=4; the DOWN face extracts the filled jar from `SLOT_JAR_OUT`=5). The
four **frame** slots (0–3) are **GUI-only** — not reachable through `getCapability`. They live in TFC's
`InventoryBlockEntity.inventory` (`protected final` `ItemStackHandler`).

So the bridge reaches frames via TFC's `InventoryBlockEntity.inventory` field — **read reflectively**, not with a
Mixin `@Accessor`. A Mixin accessor can't bind it: the field is declared as a **type variable** (`C inventory`),
which the Mixin annotation processor rejects at compile time ("could not locate target") even though its erased
descriptor is `IItemHandlerModifiable`. Reflection on the field name (TFC's own, never remapped, so it works in
dev and prod) is the pragmatic exception — a single cached `Field` lookup in `FlBeekeeping`. After mutating a
frame slot the bridge calls the BE's **public** `setAndUpdateSlots(int)` to refresh the cached bees, re-derive
the `HONEY`/`BEES` blockstate props, and mark for client sync. Queen state is read straight off the frame
`ItemStack` via FirmaLife's `BeeCapability.CAPABILITY` → `IBee.hasQueen()` (authoritative, independent of the
client-facing bee cache).

> **Why direct BE access and not "simulate the player".** FirmaLife's player paths (`FLBeehiveBlock.use` for
> the empty-jar fill; `BeehiveFrameItem.overrideOtherStackedOnMe` for the knife scrape) are awkward to invoke
> headlessly and the jar path's auto-fill is timing-gated. Calling `takeHoney` / writing the frame handler
> directly is how the herder bridges already reach TFC block entities (nest box, etc.), and — conveniently —
> **neither** the empty-jar fill nor the knife scrape angers the bees (only the block `use`/break paths do), so
> the worker needs no calming mechanism (no firepit, no beekeeper suit).

---

## 3. The hive service (worker behaviour)

The AI keeps its vanilla state machine
(`IDLE → START_WORKING → PREPARING → DECIDE → BEEKEEPER_HARVEST`; the `HERDER_BREED` branch is bypassed for
FirmaLife). One **DECIDE → BEEKEEPER_HARVEST** visit "services" a ready hive, doing up to three things in one
trip (the worker walks to the hive once):

1. **Harvest honey.** Hold a `tfc:empty_jar`; `taken = be.takeHoney(min(honey, emptyJarsHeld))`; bank
   `taken × firmalife:jar/honey`, consuming `taken` empty jars. Readiness = `be.getHoney() > 0`.
2. **Scrape wax** from the **designated frame slots** (see §4). For a slot whose toggle is on, holding a queened
   frame (`IBee.hasQueen()`), with a **TFC knife** in hand: bank one `firmalife:beeswax`, reset that frame to a
   fresh queenless `firmalife:beehive_frame` (no frame item consumed), damage the knife, `setAndUpdateSlots`.
3. **Refill empty frame slots.** Any slot holding *no frame at all* is topped up with an empty
   `firmalife:beehive_frame` from the worker's stock (consumed), `setAndUpdateSlots` — so every slot can host a
   queen and breed.

Each performed sub-action counts via `incrementActionsDoneAndDecSaturation()` (the hut dumps to storage after
`getActionsDoneUntilDumping()`=5, unchanged). XP per harvest reuses the vanilla `EXP_PER_HARVEST`(5.0).

**Breeding** stays **FirmaLife-autonomous** — the worker never touches bees. The player sets up the apiary
(initial frames + queens + ≥10 flowers near each hive) and **scepter-registers** the hives, exactly as a player
would; the worker then harvests and keeps frames topped up. (This is the apiary analogue of the chicken
herder's player-placed `tfc:nest_box`.)

### Dispatch points

- **[MixinItemScepterBeekeeper](../compat/src/main/java/com/mctfc/mixin/MixinItemScepterBeekeeper.java)**
  (`useOn`) — the scepter only registers a clicked block when it is `instanceof BeehiveBlock`, so FirmaLife
  hives can't be assigned. A MixinExtras `@ModifyExpressionValue` on that `INSTANCEOF` ORs in
  `FlBeekeeping.isHive(...)` (FirmaLife-gated), so the player can register FirmaLife hives with the unchanged
  scepter UX. (MixinExtras is available in `:compat`.)
- **[MixinEntityAIWorkBeekeeper](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkBeekeeper.java)** —
  three HEAD-cancellable injects, each a no-op unless FirmaLife is loaded **and** the building has a FirmaLife
  hive:
  - `prepareForHerding` — request the worker's consumables per the building settings: `tfc:empty_jar` (always),
    a **TFC knife** (only if any wax slot is enabled), `firmalife:beehive_frame` (a small buffer for refills).
    Replaces the vanilla shears/glass-bottle requests for FirmaLife apiaries.
  - `decideWhatToDo` — FirmaLife decision: a ready hive → `BEEKEEPER_HARVEST`, else `START_WORKING`. This
    **bypasses** the vanilla flower-list / `NO_BEES` / breeding branches (which key on `Bee` entities and a
    hut flower list FirmaLife doesn't use) — so the misleading "no flowers"/"no bees" blocking interactions
    never fire for a FirmaLife apiary.
  - `harvestHoney` — the three-step hive service above, returning `START_WORKING`.

The vanilla `getHiveToHarvest` (auto-removes any hive not in `#minecraft:beehives` and reads
`BeehiveBlockEntity.getHoneyLevel`) and `getBeesInHives` (the `BeehiveBlockEntity` cast → **CCE**) are **never
reached** on the FirmaLife path, because the FirmaLife `decideWhatToDo`/`harvestHoney` injects take over before
them. No redirect/guard on those two is needed.

---

## 4. The frame-slot setting (which slots produce wax)

Wax is a *destructive* harvest (each scrape kills a queen), so the player must control it per frame. Locked
decision: **per-slot selection, but as a single GUI row** (so the settings menu isn't spammed with four
booleans). Implemented as a custom MineColonies setting:

- **`BeeFrameSetting implements ISetting<Integer>`** — a 4-bit mask (one bit per frame slot 0–3; default **0** =
  no wax slots, i.e. pure honey + autonomous breeding, no queen ever sacrificed). Renders as **one settings
  row** carrying four small toggle buttons (`slot0..slot3`) via its own blockui layout
  (`assets/mctfc/gui/.../layoutbeeframesetting.xml`: a hidden `id` text + a `desc` + four buttons within the
  ~164×45 row). Each button's handler flips its bit and calls `settingsModuleView.trigger(key)` (the
  `IntSetting` mutate-then-sync pattern — no custom network message; the whole setting re-serializes to the
  server). `render` recolours each button to show **on = wax / off = keep**. A tooltip warns that enabling all
  four risks **depopulating** the hive (no breeding reserve).
- **`BeeFrameSettingFactory implements IFactory<FactoryVoidInput, BeeFrameSetting>`** — round-trips the bitmask
  through NBT **and** the network buffer (a single byte). Registered with `StandardFactoryController` in common
  setup (`MineColoniesTFC::onCommonSetup`), **unconditionally** (the setting names no FirmaLife type) so a world
  that loses FirmaLife can still deserialize a saved Beekeeper. Its `getSerializationId()` uses a
  **mctfc-private** short (`9201`) well clear of MineColonies' range (MC uses 0–60) to avoid the controller's
  "two factories with the same serialization id" crash.
- **Attachment** — registered via `BuildingSettings.register(b -> b instanceof BuildingBeekeeper, KEY, () -> new
  BeeFrameSetting(0))` (the project's existing per-hut-settings registry), which the existing
  `MixinAbstractBuildingModule` grafts onto each Beekeeper's `SettingsModule` as it's built — **no new mixin**.
  The `register` call is **FirmaLife-gated** (base TFC has no apiary). Key:
  `SettingKey<>(BeeFrameSetting.class, new ResourceLocation("mctfc","wax_frames"))`.
- **Lang** — `com.minecolonies.coremod.setting.mctfc:wax_frames` (row label) + `.tooltip.` (the depopulation
  warning) in `en_us.json`.

The same custom-setting machinery is reusable if we later want other multi-toggle hut settings.

### Vanilla apiary cruft hidden (FirmaLife-gated)

The vanilla Beekeeper also carries several things meaningless to a FirmaLife apiary (no combs; the harvest is
always honey; breeding is autonomous from *world* flowers, not a hut list). When FirmaLife is loaded these are
suppressed:

- **MODE** (honeycomb/honey/both) — its row is dropped from `getSettingsToShow` in `MixinSettingsModuleView`
  (key path `beekeeper`), alongside the existing Cowhand-stewing / Shepherd-dyeing removals. The setting still
  registers/serializes (save-compat intact); only the row is hidden.
- **The flower-list tab** — `MixinItemListModuleView` overrides `isPageVisible()` to `false` for the beekeeper's
  `"flowers"` list (`AbstractBuildingWindow` honours it). Gated to `BuildingBeekeeper.View` so the Florist's
  flower list is untouched.
- **The kept consumables** (1 shears / 4 glass bottles / a stack of flowers) — `MixinBuildingBeekeeper` redirects
  the three `keepX.put` calls in the constructor to no-ops, so the hut stops requesting them. (`keepX` is rebuilt
  in `<init>` on every load, so pre-existing huts are cleaned up too.) The worker requests its own
  jars/knife/frames via `prepareForHerding`.

The **BREEDING** toggle is left visible: it's a **shared** key across all five herder huts *and* the beekeeper,
so it can't be globally dropped, and it's harmless (the FirmaLife decision path ignores it). Beekeeper-specific
hiding of it remains a possible follow-up.

---

## 5. Items & requests

Requested from the hut by the `prepareForHerding` inject (so the courier keeps the Beekeeper stocked), mirroring
how the Cowhand requests milk containers:

| Item | When requested | Consumed by |
|---|---|---|
| `tfc:empty_jar` | always (FirmaLife apiary) | honey harvest (one per jar of honey) |
| a **TFC knife** (`#tfc:knives`) | only when ≥1 wax slot is enabled | wax scrape (durability, like the Shepherd's shears) |
| `firmalife:beehive_frame` | a small buffer | refilling genuinely-empty frame slots |

Honey is banked as `firmalife:jar/honey`; wax as `firmalife:beeswax`. (No vanilla honeycomb/honey-bottle is ever
produced.) `getRecipesForDisplayPurposesOnly` (JEI/GUI) could later be updated to advertise the FirmaLife
outputs instead of vanilla honey — minor polish, not v1.

---

## 6. Substitutions

So a Beekeeper/apiary blueprint resolves to FirmaLife hives when placed in a FirmaLife world, the optional
FirmaLife substitution datapack maps the vanilla hive blocks to `firmalife:beehive`
(`compat/.../firmalife_datapack`, enabled only when `firmalife` is loaded, via
[FirmaLifeDataPack](../compat/src/main/java/com/mctfc/data/FirmaLifeDataPack.java)):

- `minecraft:beehive → firmalife:beehive` (already in-tree)
- `minecraft:bee_nest → firmalife:beehive` (to add — both vanilla hive blocks map to the one FirmaLife hive)

The placed hive is empty; the player adds frames + queens + flowers and registers it with the scepter, as above.

---

## 7. Mixin inventory (in `mctfc.mixins.json`)

| Mixin | Target | Purpose |
|---|---|---|
| `MixinEntityAIWorkBeekeeper` (`remap=false`) | `prepareForHerding` + `decideWhatToDo` + `harvestHoney` (HEAD-cancellable) | request jar/knife/frames; FirmaLife harvest-or-idle decision; the honey + wax + refill hive service |
| `MixinItemScepterBeekeeper` (**remap on**) | `ItemScepterBeekeeper#useOn` (`@ModifyExpressionValue` on the `BlockState#getBlock()` call) | hand back vanilla `Blocks.BEEHIVE` for a FirmaLife hive so the `instanceof BeehiveBlock` passes → scepter registers it. (Mixin 0.8.5 has no `INSTANCEOF` injection point, hence the `getBlock` seam; both targets are vanilla methods, so remap stays on.) |
| `MixinBuildingBeekeeper` (`remap=false`, server/common) | `BuildingBeekeeper#<init>` (`@Redirect` on `Map.put`) | skip the 3 vanilla `keepX` consumable keeps (shears/glass bottle/flowers) for a FirmaLife apiary |
| `MixinItemListModuleView` (`remap=false`, client) | `ItemListModuleView#isPageVisible` (added override) | hide the beekeeper's `"flowers"` list tab when FirmaLife is loaded |
| `MixinSettingsModuleView` (`remap=false`, client; existing) | `getSettingsToShow` | drop the `beekeeper` MODE row (FirmaLife-gated) alongside the existing stewing/dyeing removals |
| `MixinAbstractBuildingModule` (existing) | `AbstractBuildingModule#setBuilding` | attaches the `wax_frames` setting registered via `BuildingSettings.register(...)` (no new mixin needed) |
| *(no mixin)* TFC `InventoryBlockEntity#inventory` | read reflectively in `FlBeekeeping` | read/write the four frame slots (GUI-only, not on the Forge capability; the field is a type variable, which a Mixin `@Accessor` can't bind) |

Reused existing accessors/invokers: `AbstractAISkeletonAccessor#mctfc$worker`,
`AbstractEntityAIBasicInvoker#{mctfc$building, mctfc$walkToWorkPos}`. The inherited
`setDelay`/`getState`/`incrementActionsDoneAndDecSaturation`/`checkIfRequestForItemExistOrCreateAsync`/
`checkIfRequestForTagExistOrCreateAsync` are `public final` on `AbstractEntityAIBasic`/`AbstractAISkeleton`,
called via a cast.

Server-side; all behaviour is AI-tick logic. This is **MineColonies-only** bridging in `:compat` — no
SlimColonies twin (that fork rule applies to `:replacements`' colony integration, not `:compat`'s mctfc mixins).

---

## 8. Status

| Piece | Status |
|---|---|
| `FlBeekeeping` bridge (honey + frame read/scrape/refill + queen detection) | **DONE** |
| Scepter accepts FirmaLife hives | **DONE** |
| Honey harvest (jar → honey jar) | **DONE** |
| Frame read/write via reflection on `InventoryBlockEntity#inventory` | **DONE** |
| Wax scrape + empty-frame refill | **DONE** |
| `BeeFrameSetting` (+ factory, layout, lang, attach) | **DONE** |
| Substitution `+bee_nest`, docs, changelog | **DONE** |

Compiles in `:compat`. **Remaining: in-game verification** in `:compat:runClient` (FirmaLife is on the dev
classpath) — set up a FirmaLife apiary (hive + frames + queens + flowers), register it with the beekeeper
scepter, and confirm the worker harvests honey jars, scrapes wax from the toggled frames, refills empties, and
that the **Beeswax frames** setting row renders/persists.

---

## 9. Open questions / risks

- **Honey throughput.** FirmaLife honey accrues slowly (one per successful day-tick, gated on ≥10 flowers,
  temperature, and an unobstructed front face). A colony apiary's output tracks how well the *player* sites the
  hives (flowers, climate) — the worker can't help here. Worth surfacing in-game/docs so it doesn't read as a
  bug.
- **Wax sustainability.** Enabling wax on a slot scrapes its queen whenever one breeds in; with too many wax
  slots (especially all four) the breeding reserve can't keep up and the hive depopulates. The default (no wax
  slots) is safe; the tooltip warns; but the exact "safe number of wax slots" depends on flower density and is
  left to the player.
- **Frame-write side effects.** Writing the frame handler directly then calling `setAndUpdateSlots` must keep
  the `HONEY`/`BEES` blockstate props and the cached-bee array consistent. Confirm in-world that a scraped slot
  shows the right `BEES` state and that FirmaLife's own daily tick still breeds into refilled frames.
- **Serialization-id collision.** The `BeeFrameSetting` factory id must stay clear of MineColonies' ids (0–60)
  *and* any other addon's — pick from a mctfc-private range and document it next to the constant.
- **Blueprint hive count vs `getMaximumHives()`.** The Beekeeper registers hives manually via the scepter, up
  to `2^(level-1)`. Large apiary blueprints may place more hives than a low-level hut can register; that's
  vanilla behaviour, just noted.
