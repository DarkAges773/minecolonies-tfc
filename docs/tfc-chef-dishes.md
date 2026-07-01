# TFC Chef dishes — teaching pot soups & salads — design

How `:compat` (`mctfc`) lets the MineColonies **Chef** (Kitchen) make TFC's **pot soups** and **salads** — two dynamic
"device" foods that are *not* vanilla crafting or furnace recipes — by letting the player **compose** an ingredient
set in a custom window, which is taught to the Kitchen as a normal colony recipe the Chef then makes (abstracting the
pot/firepit, exactly like the Blacksmith abstracts the anvil).

Status: **IMPLEMENTED** (salads + soups built; in-game verification pending a dev run). Build order / status in §8.

---

## 1. Motivation & the core challenge

We already gave the **Restaurant Cook** TFC furnace-cooking (raw → cooked food; see
[tfc-furnace-workers.md](tfc-furnace-workers.md) §6). The remaining TFC foods a colony can't make are the **dynamic
device dishes**:

- **Salads** — up to 5 chosen foods + a bowl, assembled in TFC's `SaladContainer` GUI (no recipe — computed live).
- **Pot soups** — 3–5 chosen foods + water, boiled in a pot over a firepit at 300 °C (`tfc:pot_soup` recipes).

Both are **dynamic and combinatorial**: *any* set of category-appropriate foods → a soup/salad whose **category**
(`{grain,fruit,vegetables,protein,dairy}_salad|_soup`) and **food data** are *computed from the chosen ingredients*.
That rules out the obvious "inject discrete recipes" approach (the [AnvilRecipeBridge](../compat/src/main/java/com/mctfc/smithing/AnvilRecipeBridge.java)
model) — there is no finite recipe set, and the output depends on the inputs.

> The *static* pot foods (boiled egg, cooked rice, … — fixed `tfc:pot` recipes with an item output) are **not** dynamic,
> so they *do* use the inject-discrete-recipes model: [`PotRecipeBridge`](../compat/src/main/java/com/mctfc/cook/PotRecipeBridge.java)
> auto-adds them to the Chef (see [compat-features.md](compat-features.md) → "Chef makes TFC pot foods"). This doc is
> only about the two dynamic dishes, which need the compose UI below.

**The unlock:** the player **composing** the ingredients in a teach window *is* the act that fixes a discrete recipe.
The player picks the ingredients once; we compute that specific output; it becomes a taught recipe the Chef repeats.
So the dynamic problem is solved at teach-time, and everything downstream is a normal MineColonies recipe.

### Why the Chef, not the Cook
The **Chef** works in `BuildingKitchen` (`EntityAIWorkChef extends AbstractEntityAIRequestSmelter`) and **is a
teachable crafter** — its `chef_craft` (`CraftingModule`) and `chef_smelt` (`SmeltingModule`) are real
`AbstractCraftingBuildingModule`s with teach windows. (The Restaurant **Cook** is a `NoPrivateCrafterWorkerModule` and
can't be taught — a different worker entirely.) The Chef already crafts dynamic foods (sandwiches) the same way.

---

## 2. Architecture overview

| Piece | Approach | New? |
|---|---|---|
| **Compute** (≤5 ingredients → soup/salad `ItemStack`) | Replicate TFC's ~25-line algorithm headlessly (§3) | ✅ `TfcDishes` |
| **Compose menu+screen** | A native **container** (`AbstractContainerMenu`/`Screen`) mirroring MineColonies' `ContainerCrafting`/`WindowCrafting`: ghost ingredient slots + the player's inventory; result computed by `TfcDishes` (§4) | ✅ menu + screen + 2 msgs |
| **Surface it** | Reuse the Kitchen crafting recipe-list tab; inject a "Compose TFC Dish" button (`chef_craft`-gated) → opens the menu (§5) | small mixin |
| **Register** | Server-side teach from the menu's authoritative slots → `chef_craft` (per-building, listed/removable) (§6) | new msg, reused store |
| **Chef makes it** | Abstract craft: `RecipeStorage.fullfillRecipeAndCopy` → `getPrimaryOutput()` (§7) | reused |
| **Fresh food data** | `MixinRecipeStorage` re-stamps the dynamic output's creation date on craft (§7) | reused |

The **compute** and the **compose menu/screen** are the genuinely-new parts. Decisions taken (with the user):
per-building taught recipe (not colony-wide `CustomRecipe`); and — crucially — the compose UI is a **native container
screen, NOT BlockUI** (an earlier BlockUI window was rejected). It mirrors MineColonies' own teach UI exactly: the
player drags TFC foods into ghost slots with their inventory present, just like the crafting/furnace teach grids.
Salad and soup are **two separate screens** with an identical layout (5 ingredients + a bowl — a TFC soup is extracted
from the pot with a bowl too), so **both reuse TFC's `salad.png` directly** (the TFC jar is a hard dep — nothing to
ship); a button switches between them.

---

## 3. Compute — replicate TFC's algorithm (`TfcDishes`)  — the keystone

TFC's output math is **not callable headlessly**: the salad math is private inside `SaladContainer.setAndUpdateSlots`,
and `SoupPotRecipe.getOutput(PotInventory)` (public, pure) needs a live `PotBlockEntity` (Level-coupled). Both
algorithms are short and use only **public, common (non-client) primitives**, so we replicate them — producing a
byte-identical output stack, server-safe. Put it in `com.mctfc.cook.TfcDishes` (pure, no world/AI state).

### Shared primitives (all public)
- `net.dries007.tfc.common.capabilities.food.FoodCapability` — `get(ItemStack) → IFood`, `getRoundedCreationDate()`.
- `IFood` — `getData() → FoodData`, `isRotten()`.
- `FoodData` — `water()`, `saturation()`, `nutrient(Nutrient)`; build with
  `FoodData.create(int hunger, float water, float saturation, float[] nutrients, float decayModifier)`.
- `Nutrient` — enum, **ordinal order GRAIN=0, FRUIT=1, VEGETABLES=2, PROTEIN=3, DAIRY=4**; `Nutrient.TOTAL == 5`,
  `Nutrient.VALUES`. The `float[]` is indexed by ordinal — order is load-bearing.
- Output item: a `dynamic_bowl` food whose cap is `FoodHandler.Dynamic` / `DynamicBowlHandler`. Stamp it via the
  cap from `FoodCapability.get(outputStack)`: `setFood(FoodData)` + `setIngredients(List<ItemStack>)` (on
  `FoodHandler.Dynamic`), `setBowl(ItemStack)` (on `DynamicBowlHandler`, salad only), `setCreationDate(long)` (on
  base `FoodHandler`).
- **Resolve the output item by id** (`TFCItems.SALADS/SOUPS` did not resolve via javap — don't depend on it):
  `ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc", "food/" + nutrient + "_salad"))` (lowercase nutrient
  name), likewise `_soup`.

### Salad
- **Ingredients:** 1–5 items in `#tfc:foods/usable_in_salad` (= fruits + vegetables + **cooked** meats), each with a
  TFC food cap. **Bowl:** 1 item in `#tfc:salad_bowls` (= `tfc:ceramic/bowl` or `minecraft:bowl`).
  `TFCTags.Items.USABLE_IN_SALAD` and `SALAD_BOWLS` exist as constants.
- **Algorithm:** abort (no output) if any ingredient `isRotten()`. Accumulate `water`, `saturation`, and
  `nutrients[n.ordinal()]` over the ingredients. Then **×0.75** on water, saturation, every nutrient (the salad
  blending penalty). **Dominant nutrient = argmax(nutrients)** → that category's `*_salad` item (no positive max →
  no output). Output count = `min(min ingredient stack count, bowl count)` (for a single colony craft with 1 of each:
  1). Food data = `FoodData.create(4, water, saturation, nutrients, 4.0f)` (**hunger 4, decay 4.0**). Stamp ingredients
  as `stack.copyWithCount(1)`, bowl as `bowl.copy().split(1)`, creation date = `getRoundedCreationDate()`.

### Soup
- **Ingredients:** 1–5 items in `#tfc:foods/usable_in_soup` (= fruits + vegetables + **raw** meats + cooked meats +
  `tfc:food/cooked_rice`) **+ a bowl** (`#tfc:bowls`, same as salad). Water is implicit (abstracted — the colony
  doesn't model the 100 mB; see §6 note). No `TFCTags.Items.USABLE_IN_SOUP` constant exists — build it:
  `TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "foods/usable_in_soup"))`.
- **Algorithm (parallel to salad):** abort if any rotten. **Seed `water = 20f`, `saturation = 2f`** (soup starts
  wetter), then accumulate. Multiplier `m = 1f - 0.05f * count`; **×m** on water, saturation, nutrients. **Dominant
  nutrient seeded to GRAIN**, then argmax (ties/empty → `grain_soup`). Food data = `FoodData.create(4, water,
  saturation, nutrients, 3.5f)` (**hunger 4, decay 3.5**). **Sets the bowl** (`setBowl(bowl.copy().split(1))`) — a TFC
  soup is extracted from the pot *with* a bowl, so the item carries it and eating returns it, exactly like the salad.
  Output count `min(min ingredient count, bowl count)` (one bowl → one soup; we drop TFC's pot-internal `count/2+1`
  yield to keep it bowl-conserving and parallel to the salad).

> The recipe the Chef learns consumes **1 of each chosen ingredient** and yields this computed stack. Food *values*
> are deterministic (fixed ingredients), so the only per-craft variation is freshness — handled in §7.

---

## 4. The compose menu + screen (native container, NOT BlockUI)

A native **container** that mirrors MineColonies' own teach UI (`ContainerCrafting` + `WindowCrafting`) — the player
drags TFC foods into the slots with their inventory present, exactly like the crafting/furnace teach grids. The
earlier BlockUI window was **rejected**; do not reintroduce it.

- **[`ComposeDishMenu`](../compat/src/main/java/com/mctfc/inventory/ComposeDishMenu.java)** (`extends
  AbstractContainerMenu`, registered as a `MenuType` in [`ModMenus`](../compat/src/main/java/com/mctfc/inventory/ModMenus.java)).
  **Ghost slots** like `ContainerCrafting`: `maxStackSize=1`, `mayPickup=false`, a click stamps a count-1 *copy* of the
  carried item (nothing is consumed — it's a recipe-template editor); `clicked()` is overridden to do the stamping and
  recompute. Layout mirrors TFC's `SaladContainer`: 5 ingredient slots at `(44+18i, 24)`, bowl at `(44, 56)`, result at
  `(116, 56)`, then the player inventory/hotbar at the standard `(8, 84)`/`(8, 142)`. Slot validity from
  `TfcDishes.isSaladIngredient`/`isSoupIngredient`/`isDishBowl`. Two modes by `dishType` with an **identical** layout
  (both have the bowl slot — only the valid-ingredient tag and the compute function differ). The result preview is
  computed by `TfcDishes` **server-side** and synced as the result-slot icon.
- **[`ComposeDishScreen`](../compat/src/main/java/com/mctfc/client/gui/ComposeDishScreen.java)** (`extends
  AbstractContainerScreen`): blits TFC's `tfc:textures/gui/salad.png` (referenced directly from the TFC jar — both
  dishes share the layout, so there's nothing to copy or ship), plus a **Teach** button and a **switch** button
  (salad ⇄ soup). Bound to the menu type in
  [`ClientSetup`](../compat/src/main/java/com/mctfc/client/ClientSetup.java) via `MenuScreens.register`.
- **Opened** by [`OpenComposeDishMessage`](../compat/src/main/java/com/mctfc/network/OpenComposeDishMessage.java) →
  `NetworkHooks.openScreen` (exactly how `OpenCraftingGUIMessage` opens MineColonies' teach GUIs), carrying
  `(dishType, pos, moduleId)`. The switch button reopens with the other `dishType`.

> **Why server-side compute/teach.** ItemStack *capabilities* (TFC food data) do **not** ride container slot-sync
> packets (Forge's `getShareTag` carries only NBT), so the client's view of the slots is caps-stripped. Both the
> result preview and the teach are therefore computed on the **server** from its authoritative cap-bearing slots; the
> client only previews the icon. This is cleaner and more correct than `WindowCrafting`'s client→server output stack.

---

## 5. Surfacing it on the Kitchen

MineColonies' own teach tabs are just *a list of taught recipes of that type + a button that opens the teach UI*. So we
**don't** add a new building module (the risky graft — no public API, order-sensitive module runtime ids). Instead:
[`MixinWindowListRecipes`](../compat/src/main/java/com/mctfc/mixin/MixinWindowListRecipes.java) injects a "Compose TFC
Dish" button into the Kitchen's existing crafting recipe-list window (`WindowListRecipes`, from
`CraftingModuleView.getWindow()`), gated on the module producer key `"chef_craft"` (unique to the Kitchen craft tab —
the `CraftingModuleView` is read off the injected `<init>` argument, no `@Shadow`). The button fires
`OpenComposeDishMessage` (salad mode); taught soup/salad recipes then appear in that same crafting list
(removable/toggleable there).

If a dedicated tab is wanted later, it'd mean grafting a view-only `ModuleProducer` onto the Kitchen `BuildingEntry`'s
module list (static, unique key `mctfc_chef_dish`, deterministic registration order) — deferred as higher-risk.

---

## 6. Registering the taught recipe

The teach is **server-side** (caps live only there): the screen's Teach button sends the payload-free
[`TeachComposedDishMessage`](../compat/src/main/java/com/mctfc/network/TeachComposedDishMessage.java); the server reads
the player's open `ComposeDishMenu` and calls `menu.teach(player)`, which:
- computes the dish from the authoritative cap-bearing slots (`TfcDishes`),
- builds a `RecipeStorage` via `RecipeStorage.builder().withInputs(...).withPrimaryOutput(dish).withGridSize(3)
  .withIntermediate(Blocks.AIR)` — **gridSize 3 → AIR → the `chef_craft` crafting module** (a soup/salad is neither a
  grid nor a furnace recipe; AIR is the generic home),
- `checkOrAddRecipe(storage)` then `module.addRecipe(token)` (resolving the building from the menu's `pos`/`moduleId`),
  with a success/error sound + chat — mirroring `AddRemoveRecipeMessage.onExecute`.

### The one gotcha — output must be EDIBLE
`BuildingKitchen.CraftingModule.isRecipeCompatible` accepts a recipe only if its output passes `FoodUtils.EDIBLE`
(= `ISFOOD && !ISCOOKABLE`). `ISFOOD` needs a non-null vanilla `getFoodProperties` — and **TFC food carries a flat
vanilla `FoodProperties` (4 / 0.3)** (see `MixinFoodUtils`), so salads/soups pass. `addRecipe` returning `false` plays
the error sound (verify in-game).

### Water (soup) abstraction
TFC soup needs 100 mB water; colony crafters consume **items**, not fluids, and abstract the device. v1: **abstract
water away** (free, like the anvil abstracts heating). Optional later: add a water proxy item (bucket / TFC fluid
container) to the recipe inputs.

---

## 7. Execution & dynamic food — already covered

- `EntityAIWorkChef` → `AbstractEntityAICrafting`: the Chef walks to the hut, consumes the recipe inputs, and emits
  the output via **`AbstractEntityAICrafting.executeCraftingAction` → `RecipeStorage.fullfillRecipeAndCopy(...)` →
  `getPrimaryOutput()`**. No real pot/firepit — the device is abstracted, like the Blacksmith's anvil.
- **`getPrimaryOutput()` is exactly where our existing [MixinRecipeStorage](../compat/src/main/java/com/mctfc/mixin/MixinRecipeStorage.java)
  already hooks** to realize/decay-carry dynamic TFC food (the sandwich path). The soup/salad output carries the
  `DynamicBowlHandler` cap, so its **freshness re-realization is already handled** — no new execution code.
- **To verify:** that the dynamic-bowl output's creation date is (re-)stamped fresh at craft time rather than frozen at
  teach time (so a colony soup made months later isn't born stale). If the existing mixin doesn't already cover the
  `DynamicBowlHandler` case, extend it to re-stamp `getRoundedCreationDate()` on the realized output.

---

## 8. Build order / status

1. ✅ **`TfcDishes` compute** (§3) — [`com.mctfc.cook.TfcDishes`](../compat/src/main/java/com/mctfc/cook/TfcDishes.java):
   `salad(List<ItemStack>, ItemStack bowl)` and `soup(List<ItemStack>)` → computed `ItemStack` (empty on
   invalid/rotten/no-dominant). Pure. **Transcribed byte-for-byte** from TFC's `SaladContainer#setAndUpdateSlots` /
   `SoupPotRecipe#getOutput` bytecode (constants/handler-stamping verified). Uses `TFCItems.SALADS`/`SOUPS` directly.
2. ✅ **Compose menu + screen** (§4) — native container (NOT BlockUI):
   [`ComposeDishMenu`](../compat/src/main/java/com/mctfc/inventory/ComposeDishMenu.java) (ghost slots, `TfcDishes`
   result, server-side `teach`), [`ModMenus`](../compat/src/main/java/com/mctfc/inventory/ModMenus.java) (MenuType),
   [`ComposeDishScreen`](../compat/src/main/java/com/mctfc/client/gui/ComposeDishScreen.java) (salad/soup texture +
   Teach + switch), [`ClientSetup`](../compat/src/main/java/com/mctfc/client/ClientSetup.java) (`MenuScreens.register`),
   and the open/teach messages on [`ComposeDishNetwork`](../compat/src/main/java/com/mctfc/network/ComposeDishNetwork.java).
3. ✅ **Button injection** (§5) — [`MixinWindowListRecipes`](../compat/src/main/java/com/mctfc/mixin/MixinWindowListRecipes.java)
   (`@Inject` `<init>` TAIL, reads the ctor's `CraftingModuleView` — no `@Shadow`). Gated on
   `module.getProducer().key == "chef_craft"` (unique to the Kitchen craft tab). Button fires `OpenComposeDishMessage`
   (salad mode). Registered in `mctfc.mixins.json` `client`.
4. ✅ **Register** (§6) — `TeachComposedDishMessage` → `ComposeDishMenu.teach(player)` builds the `RecipeStorage`
   server-side (gridSize 3 → AIR → `chef_craft`) and adds it. ⬜ **Verify in-game**: the recipe survives the EDIBLE gate
   (TFC food has flat vanilla `FoodProperties`, so it should — see `MixinFoodUtils`) and the Chef makes the salad/soup.
5. ✅ **Soups** (§3 soup path; §6 water abstraction) — second screen (Soup mode), 1–5 ingredients **+ bowl** (a TFC
   soup is extracted with a bowl), reusing TFC's `salad.png` directly. ⬜ verify in-game after salads.
6. ✅ **Freshness** (§7) — our taught recipes have a `null` recipeSource, so `realizeFromRecipe` returns null;
   [`MixinRecipeStorage`](../compat/src/main/java/com/mctfc/mixin/MixinRecipeStorage.java) now falls back to
   [`CraftedFoodDecay#refreshCreationDate`](../compat/src/main/java/com/mctfc/food/CraftedFoodDecay.java) — keeps the
   baked food data but re-stamps the creation date fresh, so a long-ago-taught dish isn't crafted already-stale.
   ⬜ confirm in-game (craft a dish months after teaching; it should be fresh).

---

## 9. Reference (key classes/APIs)

**TFC (compute):** `FoodCapability`, `IFood`, `FoodData.create`, `Nutrient` (GRAIN/FRUIT/VEGETABLES/PROTEIN/DAIRY,
TOTAL=5), `FoodHandler.Dynamic` / `DynamicBowlHandler` (`setFood`/`setIngredients`/`setBowl`/`setCreationDate`),
`TFCTags.Items.USABLE_IN_SALAD`/`SALAD_BOWLS`/`SOUP_BOWLS` (no `USABLE_IN_SOUP` constant — build the tag), output items
`tfc:food/<nutrient>_salad|_soup` by id. Recipe shape `tfc:pot_soup` (water 100 mB, temp 300; soup_3/4/5 by count).

**MineColonies (teach/store/craft):** `BuildingKitchen` (`CraftingModule`/`SmeltingModule`, `isRecipeCompatible` →
`FoodUtils.EDIBLE`), `ContainerCrafting`/`WindowCrafting` + `OpenCraftingGUIMessage`/`NetworkHooks.openScreen` (the
native teach-UI pattern we mirror), `AbstractCraftingBuildingModule.addRecipe`, `IColonyManager.getRecipeManager()
.checkOrAddRecipe`, `RecipeStorage` (`builder().withInputs/withPrimaryOutput/withGridSize/withIntermediate`,
`fullfillRecipeAndCopy`), `AbstractEntityAICrafting.executeCraftingAction`, `CraftingModuleView`/`WindowListRecipes`
(the tab + teach button), `CustomRecipe`/`CustomRecipeManager` (the colony-wide alternative we are *not* using here).

**Ours (reused):** `MixinRecipeStorage` (dynamic-food realization on `getPrimaryOutput`), `MixinCustomRecipeManager`
(injection seam, if we ever switch to the colony-wide path), `AnvilRecipeBridge` (the bridge pattern, for reference).
