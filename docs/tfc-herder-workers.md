# TFC herder workers — design

How `:compat` (`mctfc`) makes MineColonies' **animal-herding workers** tend, breed, and harvest **TFC livestock**
instead of vanilla animals. One framework covers all five herding huts: **Cowhand**, **Shepherd**, **Swineherd**,
**Chicken Herder**, and the **Rabbit Hutch**.

Status legend: **DONE** (built & in-tree), **PLANNED** (designed here, not yet built).

**Phase 1 (recognition + familiarity breeding + meat) is DONE & in-tree** — see §8. Recognition tags
([data/mctfc/tags/entity_types/herding/](../compat/src/main/resources/data/mctfc/tags/entity_types/herding/)), the
[TfcHerd](../compat/src/main/java/com/mctfc/herding/TfcHerd.java) bridge, and the two mixins
([MixinAnimalHerdingModule](../compat/src/main/java/com/mctfc/mixin/MixinAnimalHerdingModule.java),
[MixinAbstractEntityAIHerder](../compat/src/main/java/com/mctfc/mixin/MixinAbstractEntityAIHerder.java)) are built.
**All three products are now DONE too** (§4): milk (Cowhand), wool (Shepherd) and eggs (Chicken Herder).

---

## 1. Motivation

MineColonies ships five herder huts, all built on a shared `AbstractEntityAIHerder` + an `AnimalHerdingModule`
on the building. Each hut is hardcoded — at three layers — to **vanilla** animals:

| Hut | AI | Vanilla animals (`instanceof`) | Breed item | Variant work |
|---|---|---|---|---|
| **Cowhand** | `EntityAIWorkCowboy` | `Cow`, `Goat` (+`MushroomCow` for stew) | wheat | milk (bucket→`MILK_BUCKET`), mooshroom stew |
| **Shepherd** | `EntityAIWorkShepherd` | `Sheep` | wheat | shear (`Sheep.isSheared`→wool), dye |
| **Swineherd** | `EntityAIWorkSwineHerder` | `Pig` | carrot | none (breed + butcher) |
| **Chicken Herder** | `EntityAIWorkChickenHerder` | `Chicken` | wheat seeds | none; eggs picked up off the ground |
| **Rabbit Hutch** | `EntityAIWorkRabbitHerder` | `Rabbit` | carrot | none (breed + butcher) |

In a TFC world this is almost entirely inert, because TFC replaces the whole animal model. The shared base and
each variant break on four axes, plus a fifth concern TFC adds that vanilla has no notion of.

### What breaks (grounded in the decompiled sources)

1. **Recognition.** `AnimalHerdingModule.isCompatible(Animal)` tests `instanceof Cow/Sheep/Pig/Chicken`. TFC's
   cow/goat/yak/sheep/alpaca/musk-ox/pig/chicken/duck/quail are all `TFCAnimal extends net.minecraft.world.entity.animal.Animal`
   — never the vanilla species — so the herd is **invisible** to four of the five huts.
   - **Exception — the Rabbit Hutch already works at this layer.** TFC's rabbit (`prey/TFCRabbit`) is the lone
     livestock that **extends vanilla `Rabbit`** (it reuses rabbit rendering/hop AI) while implementing
     `MammalProperties`. So `instanceof Rabbit` is **true** for TFC rabbits — the Rabbit Hutch recognizes them
     for free, and only needs the breeding + feed-item bridge below.

2. **Breeding.** `AbstractEntityAIHerder` drives **vanilla love-mode**: `isBreedAble` = `getAge()==0 &&
   (isInLove() || canFallInLove())`, then `canMate()` (it temporarily `setInLoveTime(5)`s both), then
   `setInLove(null)`. TFC animals never enter vanilla love. TFC breeding is **gender + familiarity + a brain
   `BreedBehavior`**: two opposite-gender `ADULT`s that are both `isReadyToMate()` (familiarity ≥ a configured
   threshold, off mating cooldown) mate **on their own** via the brain. The vanilla path triggers nothing.

3. **Products** are each a vanilla mechanic the variant AI hardcodes:
   - **Cowhand** fills a `Items.BUCKET` to a `Items.MILK_BUCKET`. TFC milk is a **fluid**
     (`DairyAnimal.getMilkFluid()` → a `FluidStack` poured into a fluid-handler bucket), gated by `hasProduct()`
     / `isReadyForAnimalProduct()`, with `setProductsCooldown()` after. (Cow / Goat / Yak are `DairyAnimal`.)
   - **Shepherd** gates on `Sheep.isSheared()` and emits vanilla wool by `DyeColor`. TFC's wooly animals
     (Sheep / Alpaca / Musk Ox = `WoolyAnimal`) implement Forge's **`IForgeShearable`** — `onSheared(...)`
     returns the TFC wool item, gated by `hasProduct()`.
   - **Chicken Herder** relies on chickens dropping `minecraft:egg` items on the ground and vacuums all
     `ItemEntity`s in range. TFC chickens (`OviparousAnimal`) **lay eggs into a `tfc:nest_box` block**
     (`LayEggBehavior` → `NestBoxBlockEntity`); nothing drops on the ground.
   - **Meat** (all huts, incl. Swineherd & Rabbit Hutch) comes from butchering. This is the **one mechanic that
     already works once the animal is recognized**: `animal.hurt(...)` kills the TFC animal and its loot table
     drops TFC meat/hide.

4. **Feed / requests.** Each module requests its hardcoded vanilla breed item (wheat / carrot / seeds) ×8. TFC
   animals eat TFC food (grains, vegetables, seeds) defined by their own `isFood(ItemStack)`.

5. **Familiarity + age (the new concern).** Every TFC livestock is `TFCAnimalProperties`: it has a **gender**, a
   **familiarity** value (0..1, raised by feeding, decaying over time, capped lower for animals first fed as
   adults), an **`Age` of `CHILD`/`ADULT`/`OLD`** (`OLD` stops producing and breeding), and fertilization state.
   **Nothing breeds or yields a product until familiarized past a threshold** (`isReadyToMate()` /
   `isReadyForAnimalProduct()`). Vanilla MineColonies has no concept of this, so a naive bridge would produce a
   hut full of animals that never breed and never give milk/wool/eggs.

### The one unifying fact

Both `TFCAnimal` (cow/sheep/pig/chicken/…) **and** `TFCRabbit` (via `MammalProperties extends
TFCAnimalProperties`) implement **`TFCAnimalProperties`**. That single interface is the key the whole bridge
dispatches on — `animal instanceof TFCAnimalProperties` cleanly separates "TFC livestock, use the TFC path" from
"vanilla animal, leave MineColonies alone," across all five huts and any TFC add-on animal.

---

## 2. Per-hut target mapping

Recognition is datapack-driven via one **entity-type tag per job** (so TFC add-ons like AFC join by tag, no
code). The Rabbit Hutch tag is effectively a no-op (already matched by `instanceof Rabbit`) but is included for
uniformity / add-on rabbits.

| Hut | TFC species (tag `#mctfc:herding/<job>`) | TFC class | Bridge product | Feed (TFC) |
|---|---|---|---|---|
| **Cowhand** | `tfc:cow`, `tfc:goat`, `tfc:yak` | `DairyAnimal` | **milk fluid → native TFC milk bucket** + meat | grain |
| **Shepherd** | `tfc:sheep`, `tfc:alpaca`, `tfc:musk_ox` | `WoolyAnimal` (`IForgeShearable`) | **wool via `onSheared`** + meat | grain |
| **Swineherd** | `tfc:pig` | `Mammal` | meat | grain/veg |
| **Chicken Herder** | `tfc:chicken`, `tfc:duck`, `tfc:quail` | `OviparousAnimal` | **eggs harvested from `tfc:nest_box`** + meat | seeds/grain |
| **Rabbit Hutch** | `tfc:rabbit` (already `instanceof Rabbit`) | `TFCRabbit`/`MammalProperties` | meat | vegetables |

(TFC also has poultry-ish wild birds — grouse/pheasant/turkey/peafowl — and the horse family; out of scope for
these five huts. The Stablemaster is not part of this design.)

---

## 3. Architecture — a herd bridge, dispatched like the furnace behaviour

Same shape as the furnace-worker rework ([tfc-furnace-workers.md](tfc-furnace-workers.md)): keep the buildings,
GUIs, jobs, and the AI state machine; insert TFC behaviour behind thin dispatch points. Two collaborating
abstractions:

- **`HerdBridge`** — an **animal-level** strategy resolved per animal:
  - `recognizes(Animal)` — is this one of *my hut's* animals;
  - `breedingFoods()` / `isBreedingFood(ItemStack)`;
  - `isBreedable(Animal)` and `tendBreeding(worker, a)` — the familiarity/feeding step (see §5);
  - `isReadyForProduct(Animal)` + `collectProduct(worker, a)` — milk / wool / egg / nothing;
  - `butchers(Animal)` — true for all (meat path is shared).

  Two implementations: **`VanillaHerd`** (pure pass-through — original MineColonies behaviour, so mixed/non-TFC
  worlds are untouched) and **`TfcHerd`** (keyed on `instanceof TFCAnimalProperties`; the **product** branch is
  chosen by `instanceof DairyAnimal / WoolyAnimal / OviparousAnimal`, else meat-only). One `TfcHerd` instance
  covers **all five huts** for breeding/familiarity; only the product branch differs.

- **`HerdBridges`** — registry/resolver: given an `Animal` (and the hut's job), return the right `HerdBridge`.
  Keyed primarily on `instanceof TFCAnimalProperties`; falls back to `VanillaHerd`.

### Dispatch points (mixins, all `@Mixin(remap = false)` — MineColonies' own members)

1. **`MixinAnimalHerdingModule` → `isCompatible(Animal)`** *(recognition)*. HEAD, `@Inject` cancellable: if the
   animal's `EntityType` is in this module's per-job tag (`#mctfc:herding/<job>` resolved from the module's
   `jobEntry`), return `true`. Original predicate still runs for vanilla animals (and already catches TFC
   rabbits). This single hook also fixes herd-counting, feed-target search, and butcher-target search, since they
   all funnel through `isCompatible`.

2. **`MixinAbstractEntityAIHerder`** *(two-phase husbandry: familiarize → breed)*. TFC's `isReadyToMate` requires an
   adult that's familiar (≥ 0.3), not pregnant, mate-cooldown-elapsed, **and fed that day** (`!isHungry`); its brain
   `BreedBehavior` then pairs a male with an opposite-gender partner. And `eatFood` only raises familiarity while an
   animal can still gain it (a child, or an adult below its `adultFamiliarityCap`). So two phases:
   - **FEED (individual familiarization)** — the FEED state's `searchForAnimals` is filtered to TFC animals that are
     hungry and `TfcHerd.shouldFamiliarize` (child or below cap, accepts the held grain); the fed animal is
     familiarized on the broadcast-eat event (`TfcHerd.familiarize` → TFC `eatFood`), and `ageUp` is suppressed for
     TFC babies. This builds familiarity up to the cap; at-cap animals are skipped (no benefit). FEED chance is
     raised to `TfcHerd.FEED_CHANCE` (0.33) via `@ModifyConstant`.
   - **BREED (pair mating)** — once an adult is mate-ready (≥ 0.3), `breedAnimals` finds it a `canMate` partner and
     feeds both. `isBreedAble` → `TfcHerd.isBreedingCandidate` (fertile, non-pregnant, hungry, **mate-ready** adult);
     `canMate` → `TfcHerd.canPair` (opposite genders, so a fitting **pair is fed at once** and partnerless animals
     are skipped — no waste); `setInLove` → feed the animal. Both fed today + ≥ 0.3 → TFC mates them. BREED is
     checked before FEED, so a ready pair breeds while everyone else familiarizes.

   **The hut's "Breeding" setting and TFC.** `decideWhatToDo` only enters `HERDER_BREED` when `canBreedChildren()`
   (= `building.getSetting(AbstractBuilding.BREEDING)`) is on, and we leave that gate untouched — so the **BREED**
   phase already honours the toggle. The **FEED** (familiarization) phase is deliberately **not** gated on it
   (design decision): familiarity also unlocks products (milk/wool) and tameness, so the worker keeps familiarizing
   regardless. Note this makes the toggle a *soft* control for TFC herds — because TFC's `BreedBehavior` mates
   adults **autonomously** the moment both are familiar + fed-that-day (no worker BREED state needed), feeding alone
   can still produce offspring while the toggle is off. Honouring it strictly would mean suppressing FEED on
   mate-ready adults, which we chose not to do.

   The held grain is read via `AbstractAISkeletonAccessor` (`@Accessor` for the deeply-inherited `worker` field);
   familiarizing with the **real held stack** keeps TFC's rotten-food rule authentic (`isFood` inside `eatFood`). The
   same mixin also reworks **butchering** for TFC herds on two axes: *which* — redirects
   `butcherAnimals`' `searchForAnimals` to pick the cull target by husbandry priority (**OLD first, then
   least-familiar**, `TfcHerd.pickButcherTarget`), handing the butcher loop a one-element list of that animal; and
   *whether* — `@Inject`s `chanceToButcher` (`TfcHerd.butcherChance`) to replace MineColonies' "more than 3 adults
   over a `level × 2` cap" gate with: **always cull when an OLD animal is present** (even below any threshold),
   otherwise cull while some **species** exceeds its reserve. The reserve is **per species and per gender**, scaling
   with hut level and **female-weighted** (`TfcHerd.maleReserve`/`femaleReserve`: females `max(1, level)`, males
   `max(1, ceil(level/2))` — so L1 1♂/1♀, L3 2♂/3♀, L5 3♂/5♀). *Per species* matters because the multi-animal huts
   pool species into one `DairyAnimal`/`WoolyAnimal`/poultry class — without it, a Cowhand with a cow and a goat
   would see "2 females" and butcher the last goat. The target trims the species+gender that **most overshoots its
   reserve**, picking OLD first then least-familiar. When the picker finds no valid target for a TFC herd the butcher
   redirect culls **nothing** (never falls back to vanilla selection, which would ignore the reserve). Hut level sets
   the reserve via the `building` `@Accessor` on `AbstractEntityAIBasicInvoker`. Vanilla animals fall through
   unchanged. See §5.

3. **Per-variant product hooks** — HEAD-cancellable `@Inject` on each variant's product method, routing TFC
   animals to `bridge.collectProduct` and leaving vanilla animals to the original code:
   - `MixinEntityAIWorkCowboy` → `milkCows` (and skip `milkMooshrooms`: TFC has no mooshroom);
   - `MixinEntityAIWorkShepherd` → `shearSheep` (and the `Sheep.isSheared` gate);
   - `MixinEntityAIWorkChickenHerder` (or the base pickup) → replace ground-egg pickup with **nest-box harvest**
     (see §4).
   - Swineherd & Rabbit Hutch need **no** product hook — meat only, which the shared butcher already does.

Registration mirrors the furnace pattern: a small registry maps each herder AI / job to its product handling, so
adding a hut is a registration line, not new mixin plumbing.

---

## 4. Products

- **Milk (Cowhand).** *Implemented by driving TFC's own `mobInteract`* — not a fixed item swap, because
  **FirmaLife varies the milk per animal via TFC's `AnimalProductEvent`** (its `FLForgeEvents.onAnimalProduce`
  calls `event.setProduct(GOAT_MILK/YAK_MILK/…)`), and that event fires **only inside `mobInteract`**. Base TFC's
  `getMilkFluid()` is just `ForgeMod.MILK`; relying on it would miss every FirmaLife variant. So the worker, when a
  TFC `DairyAnimal` is `isReadyForAnimalProduct()` (familiarity + product cooldown + adult/female — exactly the
  player gate), holds an **empty generic TFC fluid container** (ceramic jug default, but **any** held
  `IFluidHandlerItem` — jug/wooden/metal bucket — works) and calls `animal.mobInteract(fakePlayer, hand)`. TFC then
  fires the event (FirmaLife swaps in the right milk), fills the container, and sets the cooldown; the worker banks
  the filled container. A **vanilla bucket is deliberately not used** — it can't hold FirmaLife's milk variants.
  MineColonies' hardcoded `instanceof Cow || Goat` search and fixed `getMilkOutputItem` swap are both bypassed for
  TFC dairy. The mooshroom-stew path (`COWBOY_STEW`) is disabled for TFC (no mooshrooms in TFC).
  - **The hut's "Milk Item" setting picks the container.** MineColonies' `MILK_ITEM` `StringSetting` (stock options:
    vanilla milk bucket / large milk bottle — neither holds TFC milk) is repopulated, via the same
    [MixinSettingsModule](../compat/src/main/java/com/mctfc/mixin/MixinSettingsModule.java) `with` seam used for the
    builder's fill-block, with the **TFC fluid containers that actually hold milk** (`TfcHerd.milkContainerOptions()`
    probes each candidate's `IFluidHandlerItem` with Forge's milk fluid): ceramic jug (default), wooden bucket,
    red/blue steel bucket. The selected value resolves back to a container via `TfcHerd.milkContainerFor(value)`,
    which is what `milkCows` prefers (`findEmptyMilkContainer(inv, preferred)`) and what `getExtraItemsNeeded`
    requests — at **`MILKING_AMOUNT`** count, since each milking consumes an empty container. Option order is
    **stable** (the setting persists the selected *index*; `StringSetting.updateSetting` refreshes the list from our
    registered setting on load, so existing huts adopt the TFC options while keeping their saved pick).
  - **Mooshroom stew is dropped.** The Cowhand's `decideWhatToDo` routes to `COWBOY_STEW` purely on `canTryToStew()`
    (it never checks a `MushroomCow` exists), so in a mooshroom-free TFC world the worker keeps entering a dead stew
    state. A `decideWhatToDo` RETURN inject in `MixinEntityAIWorkCowboy` rewrites a `COWBOY_STEW` result back to
    `START_WORKING` when no `MushroomCow` is present (the vanilla path survives if one ever is), and the inert
    **Stewing Amount** setting is hidden from the GUI by [MixinSettingsModuleView](../compat/src/main/java/com/mctfc/mixin/MixinSettingsModuleView.java).
- **Wool (Shepherd).** *Implemented* — TFC wooly animals (sheep/alpaca/musk ox) are `IForgeShearable`, and TFC's
  `onSheared` itself fires the `AnimalProductEvent` (so FirmaLife/add-ons can vary the wool) and **returns the wool
  drops directly** — simpler than milk (no container). [MixinEntityAIWorkShepherd](../compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkShepherd.java)
  has two injects, because the Shepherd's `decideWhatToDo` only enters `SHEPHERD_SHEAR` when its hardcoded
  `findShearableSheep()` (`instanceof Sheep`) is non-null: (1) a `decideWhatToDo` RETURN inject routes an idle
  worker to `SHEPHERD_SHEAR` when `SHEARING` is on and a *ready* TFC `WoolyAnimal` is present; (2) a `shearSheep`
  HEAD inject shears a ready `WoolyAnimal` via [TfcHerd.shear](../compat/src/main/java/com/mctfc/herding/TfcHerd.java)
  (Forge `onSheared` — respects familiarity + cooldown), banks the wool, damages the shears. Both injects honour the
  **`SHEARING`** setting (the HEAD inject re-checks it, so toggling shearing off mid-state still stops the worker).
  Auto-**dyeing** never applies to TFC: it only runs inside the *vanilla* `shearSheep` body (`dyeSheepChance` on a
  vanilla `Sheep`), which the HEAD inject skips for TFC wooly animals — so the inert **Dyeing** setting is hidden
  from the GUI by [MixinSettingsModuleView](../compat/src/main/java/com/mctfc/mixin/MixinSettingsModuleView.java).
  Vanilla sheep fall through to the vanilla (shear + maybe-dye) path.
- **Eggs (Chicken Herder).** *Implemented* — TFC chickens lay into `tfc:nest_box` blocks, not on the ground, so the
  vanilla pickup (ground `ItemEntity`s) finds nothing. We reuse the existing `HERDER_PICKUP` state via two injects in
  [MixinAbstractEntityAIHerder](../compat/src/main/java/com/mctfc/mixin/MixinAbstractEntityAIHerder.java) gated to
  `EntityAIWorkChickenHerder` (the chicken herder overrides no egg/pickup method, so the hooks live on the shared base
  with an `instanceof` gate): (1) a `decideWhatToDo` RETURN inject routes an idle herder to `HERDER_PICKUP` when
  [TfcHerd.findEggNestBox](../compat/src/main/java/com/mctfc/herding/TfcHerd.java) finds a box with a collectable egg
  (scans the building bounds via `getCorners()` for a `NestBoxBlockEntity`); (2) a `pickupItems` HEAD inject walks to
  that box and pulls its **non-fertilized (food) eggs** out of the BE's `ITEM_HANDLER` (`TfcHerd.collectFoodEggs`),
  banks them, and counts an action. **Fertilized eggs are left in the box** — the nest box's own `serverTick`
  incubates and hatches them into chicks, so that's how the flock grows (the egg analogue of breeding). When no box
  has eggs we fall through to vanilla ground pickup. `tfc:nest_box` is in `#mctfc:builder_dont_clear` so the builder
  won't strip player-placed boxes. Fertilized vs food is read from TFC's `EggCapability` (`IEgg.isFertilized`).
- **Meat (all).** Unchanged: `butcherAnimals` → `animal.hurt`. Works as soon as recognition lands. Population cap
  (`level × 2`, `chanceToButcher`) is reused as-is.

---

## 5. Familiarity — authentic husbandry (locked decision)

Chosen approach: **the worker familiarizes animals the real TFC way; TFC's own rules then gate breeding and
products.** No bypass — a colony herd ramps up like a player's would.

- **Familiarize first.** The individual FEED step feeds **TFC food** (a grain via the swapped `getBreedingItems`)
  to any hungry animal that can still gain familiarity — a child, or an adult below its `adultFamiliarityCap` —
  via TFC's `eatFood`, ~once/day, respecting the age-based cap. This is the default feeding, run until the cap;
  at-cap animals are skipped (no benefit).
- **Then breed in pairs.** Once an adult is familiar enough (≥ 0.3), the BREED step pair-feeds it together with an
  opposite-gender partner; an animal with no partner is skipped (no waste). Both being fed today + ≥ 0.3 satisfies
  TFC's `isReadyToMate()` (adult, familiar, not pregnant, **fed that day**, cooldown elapsed) and **TFC's brain
  `BreedBehavior` mates them**. Products likewise flow only when `isReadyForAnimalProduct()` is satisfied.
- `OLD` animals are preferred butcher targets (they no longer breed/produce) — a natural fit for the existing
  "furthest-from-centre, prefers shaded" butcher selection, extended with an age check.

Consequences to surface in-game/docs: a freshly-built hut won't produce immediately (familiarity must ramp), and
throughput tracks how well the worker keeps the herd fed. Two tunables worth exposing as colony config: the
worker's feeding cadence, and the familiarity threshold treated as "ready" (default = TFC's). A future
**hybrid** option (a small colony-managed anti-decay floor once an animal reaches the threshold) is left as a
possible follow-up, not part of v1.

---

## 6. Feed items & building GUI

- Each hut's breeding `ItemStorage` (wheat/carrot/seeds) is replaced by a TFC-food **tag** per hut
  (`bridge.breedingFoods`), and the `itemsNiceToHave` / request path (×8 bulk) requests from it. Cowhand also
  requests **empty TFC buckets**; Shepherd keeps **shears**; Chicken Herder requests **grain/seeds**.
- `getRecipesForDisplayPurposesOnly` (JEI/GUI) is updated per hut to show the TFC outputs (TFC milk bucket, TFC
  wool, TFC egg) and TFC feed, so the hut GUI doesn't advertise vanilla items the worker never makes.

---

## 7. Mixin inventory (all in `mctfc.mixins.json`, `remap = false`)

| Mixin | Target | Purpose |
|---|---|---|
| `MixinAnimalHerdingModule` | `AnimalHerdingModule#isCompatible` | tag-based recognition of TFC species (all huts) |
| `MixinAbstractEntityAIHerder` | `isBreedAble` (skip BREED) + `feedAnimal` (select/familiarize/no-age) + `decideWhatToDo` (feed chance) + `butcherAnimals`/`chanceToButcher` (cull target + gate) | familiarize hungry TFC animals via FEED (rotten rule); cull old-first, gate on breeding-pair reserve; TFC self-breeds |
| `AbstractAISkeletonAccessor` | `AbstractAISkeleton#worker` (`@Accessor`) | read the worker's held grain (deeply-inherited field) for the rotten-food check |
| `MixinEntityAIWorkCowboy` | `milkCows` (skip `milkMooshrooms`) | TFC milk-fluid → TFC bucket |
| `MixinEntityAIWorkShepherd` | `shearSheep` + `isSheared` gate | TFC `IForgeShearable` wool |
| `MixinEntityAIWorkChickenHerder` | egg pickup | nest-box harvest instead of ground pickup |

Swineherd & Rabbit Hutch need no dedicated mixin (recognition + shared butcher cover them; the rabbit's breeding
is handled by the shared `MixinAbstractEntityAIHerder` familiarity path).

This is **MineColonies-only** bridging in `:compat` — no SlimColonies twin (that fork rule applies to
`:replacements`' colony integration, not `:compat`'s mctfc mixins). Server-side; all behaviour is AI-tick logic.

---

## 8. Per-variant status & suggested phasing

| Hut | Recognition | Breeding (familiarity) | Product | Effort |
|---|---|---|---|---|
| **Rabbit Hutch** | free (`instanceof Rabbit`) | shared bridge | meat (free) | **smallest** |
| **Swineherd** | tag | shared bridge | meat (free) | small |
| **Cowhand** | tag | shared bridge | milk fluid → TFC bucket | medium |
| **Shepherd** | tag | shared bridge | wool via `IForgeShearable` | medium |
| **Chicken Herder** | tag | shared bridge | nest-box egg harvest | medium |

Recommended order: **(1)** recognition tags + the `TFCAnimalProperties` bridge skeleton + familiarity breeding +
meat — this alone makes **Rabbit Hutch and Swineherd fully work and the other three breed/butcher**; verify in
`:compat:runClient`. **(2)** Cowhand milk. **(3)** Shepherd wool. **(4)** Chicken eggs (incl. the nest-box
question below).

## 9. Open questions / risks

- **Nest-box provision for the Chicken Herder.** Eggs require `tfc:nest_box` blocks in range. Does the hut
  blueprint include them, does the worker place them, or do we document that the player must? (We already
  protect them from the builder's clear phase.)
- **Familiarity ramp pacing.** Authentic familiarity is slow; confirm the worker's feeding cadence gives a
  reasonable colony-scale ramp, and decide the two config knobs in §5.
- **TFC milk downstream.** Cowhand emitting TFC milk buckets only helps if colony/TFC recipes consume TFC milk;
  cross-check against the food bridge so milk isn't stranded.
- **Familiarity decay vs. butcher churn.** Constantly butchering to the cap and breeding replacements resets the
  familiarity investment each generation; the `CHILD`-feeding loop must keep up. Worth watching in testing.
- **`fakePlayer` interactions.** Milk-fill, shearing, and `eatFood` all want a player; reuse the herder's
  existing `FakePlayer` (as the butcher/looting path already does) and confirm TFC's events accept it.
