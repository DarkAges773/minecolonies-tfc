# TFC herder workers — design

How `:compat` (`mctfc`) makes MineColonies' **animal-herding workers** tend, breed, and harvest **TFC livestock**
instead of vanilla animals. One framework covers all five herding huts: **Cowhand**, **Shepherd**, **Swineherd**,
**Chicken Herder**, and the **Rabbit Hutch**.

Status legend: **DONE** (built & in-tree), **PLANNED** (designed here, not yet built). Everything here is **PLANNED**.

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

2. **`MixinAbstractEntityAIHerder`** *(breeding → familiarity)*. The base breeding helpers (`isBreedAble`,
   `canMate`, `breedTwoAnimals`) assume vanilla love. Redirect them through the resolved `HerdBridge` for TFC
   animals: `isBreedAble`/`canMate` → `bridge.isBreedable` + TFC `isReadyToMate`/gender checks; the breed action
   → `bridge.tendBreeding` (feed-to-familiarize) instead of `setInLove(null)`. Vanilla animals fall through
   unchanged. (The "BREED" AI state effectively becomes "familiarize" for TFC herds — see §5.)

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

- **Milk (Cowhand).** Decision: **native TFC milk bucket.** When `isReadyForAnimalProduct()`, drive the same path
  TFC's `DairyAnimal.mobInteract` uses — pull `getMilkFluid()` into a fluid-handler bucket the worker carries,
  yielding TFC's milk bucket, then `setProductsCooldown()`. The hut requests **empty buckets** (TFC bucket, via a
  tag) rather than vanilla buckets; downstream colony recipes consuming milk must accept TFC milk (already
  partly handled by the food bridge). Honour the `AnimalProductEvent` TFC fires so other mods' hooks still run.
- **Wool (Shepherd).** TFC wooly animals are `IForgeShearable`; call `onSheared(fakePlayer, shears, level, pos,
  fortune)` when `hasProduct()` and bank the returned TFC wool. The shepherd already carries shears. Drop the
  vanilla dye step (TFC wool colour is per-species, not dyed-on-sheep). Keep the existing skill→quantity scaling
  where it still maps.
- **Eggs (Chicken Herder).** Eggs are laid into `tfc:nest_box` blocks, so harvesting = the worker visiting nest
  boxes in the building bounds and pulling eggs out of the `NestBoxBlockEntity`, **not** ground pickup. This is
  why `tfc:nest_box` was added to `#mctfc:builder_dont_clear` — the builder must not strip nest boxes the herder
  depends on. (Placement of nest boxes in the hut blueprint / by the worker is an open item — see §7.)
- **Meat (all).** Unchanged: `butcherAnimals` → `animal.hurt`. Works as soon as recognition lands. Population cap
  (`level × 2`, `chanceToButcher`) is reused as-is.

---

## 5. Familiarity — authentic husbandry (locked decision)

Chosen approach: **the worker familiarizes animals the real TFC way; TFC's own rules then gate breeding and
products.** No bypass — a colony herd ramps up like a player's would.

- The worker's feed step (the repurposed BREED state + the existing `feedAnimal` step) feeds **TFC food**
  (`bridge.isBreedingFood`, tag-driven) to animals via TFC's `eatFood(stack, hand, fakePlayer)`, which raises
  familiarity at most ~once/day and respects the age-based familiarity cap (so animals raised from `CHILD`
  reach high familiarity; adults cap low — exactly TFC's mechanic).
- Once two opposite-gender `ADULT`s clear `isReadyToMate()`, **TFC's brain `BreedBehavior` mates them with no
  colony involvement.** The herder doesn't force `setInLove`; it just keeps the herd fed/familiar and culls to
  the cap. Likewise products flow only when `isReadyForAnimalProduct()` is satisfied.
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
| `MixinAbstractEntityAIHerder` | breeding helpers (`isBreedAble`/`canMate`/`breedTwoAnimals`) + feed step | route TFC animals to familiarity-based breeding |
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
