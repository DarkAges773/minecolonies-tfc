# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

A Gradle **multi-project** repo containing **two Forge 1.20.1 mods**:

| Subproject | Mod id | Package | Purpose |
|---|---|---|---|
| `:replacements` | `structurizereplacements` | `com.structurizereplacements` | **Standalone** Structurize add-on: datapack-driven, explicit block/tag substitution (+ interactive GUI pools) when placing blueprints. **MineColonies is an OPTIONAL dependency** — when present, the builder/Build-Options per-building integration activates (`com.structurizereplacements.integration.minecolonies.*` + the optional `structurizereplacements.minecolonies.mixins.json` config); when absent, it's a pure Structurize substitution mod. No TFC dependency. |
| `:compat` | `mctfc` | `com.mctfc` | **MineColonies × TerraFirmaCraft** bridge. Depends on `:replacements`; ships TFC substitution rules as a datapack and houses the MC↔TFC bridging (food/nutrition, farming, smithing, …) — including its own mixins (`mctfc.mixins.json`, currently the farmer-tilling bridge). |

The split exists so the substitution engine (and its optional MineColonies builder integration) is
reusable by anyone, independent of TFC. **`:replacements` may reference MineColonies, but only as an
optional dependency** — all MineColonies-referencing classes live under
`integration.minecolonies`/`mixin.minecolonies` and load only when MineColonies is present (guarded by
`ModList.isLoaded("minecolonies")` in the mod ctor; the MC mixins sit in a `required:false` config that
Mixin skips when targets are absent). Never make MineColonies *mandatory* for `:replacements`.

### Hard constraints (do not change without explicit instruction)
- **Loader:** Forge (FML). Not Fabric, not NeoForge.
- **Minecraft:** 1.20.1 only. **Forge:** 47.4.10 (`[47,)`). **Java:** 17 (toolchain). **Mappings:** official 1.20.1.

## Layout

```
/ (root)            settings.gradle (includes :replacements, :compat), build.gradle (ForgeGradle
                    declared `apply false`), gradle.properties (shared versions), gradlew, gradle/
replacements/       build.gradle, src/main/java/com/structurizereplacements/**, resources/**
compat/             build.gradle, src/main/java/com/mctfc/**, resources/** (incl. TFC datapack)
```
Per-mod identity (id/name/version/group) is defined in each subproject's `build.gradle` (Groovy
`def`s), NOT in `gradle.properties` — only shared things (MC/Forge/mappings/dep versions) live there.

## Build & run

ForgeGradle 6 via the wrapper. First setup decompiles MC and downloads the dependency mods (slow,
needs network).

```
gradlew build                      # build both mods (jars in <sub>/build/libs)
gradlew :replacements:build         # build only the standalone mod

# Run ONLY the standalone mod (structurizereplacements + Structurize/BlockUI; no MineColonies/TFC):
gradlew :replacements:runClient     # faster; proves the mod works independently
gradlew :replacements:runData

# Run the FULL stack (both our mods + MineColonies/Structurize/TFC/...):
gradlew :compat:runClient
gradlew :compat:runServer
gradlew :compat:runData             # data generators -> compat/src/generated/resources

gradlew --refresh-dependencies
```
**Both** subprojects define run configs. `:replacements`'s runs load only itself (+ Structurize/
BlockUI from its deobf deps) — use these to develop/test the substitution mod in isolation.
`:compat`'s runs register **both** our mods (`structurizereplacements` sourced from
`project(':replacements').sourceSets.main`) and pull the full third-party stack from its deobf deps.
In the VS Code Gradle panel each `runClient`/`runServer` task appears under its own subproject
(refresh the panel after editing a `build.gradle`).

**Always qualify the run task with its subproject.** A bare `gradlew runClient` (unqualified) runs the
task in BOTH subprojects and launches **two** Minecraft instances. Use `:replacements:runClient` or
`:compat:runClient` — and in the VS Code panel run the task under the specific subproject node, not an
aggregated/root one.

## Dependencies

Shared versions in [gradle.properties](gradle.properties). Sources:
- **LDTTeam maven** (`https://ldtteam.jfrog.io/ldtteam/modding/`): `com.ldtteam:structurize`,
  `:minecolonies`, `:blockui`, `:domum_ornamentum`.
- **CurseMaven**: TerraFirmaCraft (`curse.maven:terrafirmacraft-302973:<tfc_file_id>`) and its
  **mandatory** dep Patchouli (`curse.maven:patchouli-306770:<patchouli_file_id>`).

Who depends on what: `:replacements` → Structurize + blockui (`implementation`) + MineColonies
(**`compileOnly`** — optional integration compiles against it but isn't bundled). `:compat` →
`project(':replacements')` + the full stack (structurize/blockui/minecolonies/domum/tfc/patchouli) so
the dev run loads everything.

**Dev-run-only test mods** (`runtimeOnly` — NOT real mandatory deps): `:replacements` adds MineColonies
(also `compileOnly`) + Domum Ornamentum + EMI so its standalone `runClient` exercises the **optional
MineColonies integration** (Domum is MineColonies' mandatory dep). **To dev-test the MineColonies-absent
path, comment out ONLY `:replacements`' `runtimeOnly minecolonies` line** (keep `domum_ornamentum` — it's a
*mandatory dep of Structurize itself*, so removing it crashes Structurize with the misleading "mixin config
could not be read" error). The game then loads, opens the build-wand GUI, logs the
`minecolonies` mixin config skipping its targets (`@Mixin target … was not found`), and does not crash —
**verified**. `:compat` adds EMI. (Note: `:replacements` ships **no active substitution rules** — the old
`examples.json` is now copy-paste documentation in [docs/substitution-rule-examples.md](docs/substitution-rule-examples.md);
drop a snippet into a datapack to exercise substitution in the standalone run.)

**Bumping versions:** edit the `*_version` / `*_file_id` properties. Verify LDTTeam versions against
`<artifact>/maven-metadata.xml`; TFC/Patchouli use CurseForge **file ids** (from the file URL).
**A missing mandatory dep** (e.g. Patchouli for TFC) shows up as a *misleading* mixin
`could not be read` crash — check earlier in the log for `Missing or unsupported mandatory
dependencies` first.

## Mixins

SpongePowered MixinGradle (refmap generation). Notes that each cost a debugging crash:

- **`:replacements` owns TWO mixin configs**, both listed in its `MixinConfigs` jar manifest +
  MixinGradle's `mixin { config }`:
  - [structurizereplacements.mixins.json](replacements/src/main/resources/structurizereplacements.mixins.json)
    (`required:true`, package `com.structurizereplacements.mixin`) — the core Structurize mixins (always apply).
  - [structurizereplacements.minecolonies.mixins.json](replacements/src/main/resources/structurizereplacements.minecolonies.mixins.json)
    (**`required:false`**, package `com.structurizereplacements.mixin.minecolonies`) — the optional
    MineColonies-integration mixins. Mixin **skips** these when the MineColonies target classes are absent
    (same graceful-skip TFC relies on for its JEI/Sodium mixins), so the standalone (MC-absent) case is fine.
- Mixins targeting **another mod's own methods** (Structurize/MineColonies, not Minecraft) need
  **`remap = false`** on the injector — those names are stable and have no SRG mapping. See
  [MixinStructurePlacer](replacements/src/main/java/com/structurizereplacements/mixin/MixinStructurePlacer.java).
- **Cross-project mixin registration in dev:** `:compat`'s `runClient` must explicitly register **both**
  `:replacements` configs — its run config passes two `args '--mixin.config', '…'` lines. In dev,
  `:replacements` is loaded from a source set (no jar `MixinConfigs` manifest), and MixinGradle only
  auto-registers a project's OWN config into its OWN runs. Without this, NONE of the replacements mixins
  (button, substitution, preview, the per-building MineColonies integration) apply in the `:compat` run.
  (Production is fine: each mod's jar carries its own `MixinConfigs` manifest.)
- **`:compat` owns `mctfc.mixins.json`** (package `com.mctfc.mixin`) — the TFC bridge mixins:
  `MixinEntityAIWorkFarmer` (till/plant/harvest), `MixinFarmField` (per-field harvest-mode state + sync),
  the client `MixinWindowField` (the field-GUI mode toggle) — all under the "Farmer farms TFC crops" section
  below — `MixinInventoryCitizen` (decay-aware food stacking; see "Decay-aware item stacking" below),
  `MixinBarrelBlockEntity` (vanilla barrel → tfc:chest size/restrictions; see "Vanilla furnaces made decorative"),
  the food-spoilage trio `AbstractTileEntityRackAccessor` + `MixinRackInventory` + `MixinFoodUtils`
  (colony-storage preservation + FIFO/skip-rotten eating + the TFC-food saturation bridge; see "Food spoilage management"
  below), `MixinEntityAIStructureMiner` (ladder backfill honours the hut fill-block setting; see "Miner shaft uses the hut
  fill-block setting"), the collapse-support pair `MixinAbstractEntityAIStructure` + `MixinSupport` (build areas are
  collapse-proof while built; see "Build areas are collapse-proof"), `MixinCitizenAI` (rest for TFC's *localized* rain at
  a fixed worksite anchor, not the global flag; see "Citizens rest for TFC's localized rain"), and the keep-colony-lights-lit
  pair `MixinTfcLightBlocks` (multi-target on the TFC torch/candle/jack-o'-lantern `randomTick`) + `MixinLampBlockEntity` (the
  metal-lamp fuel drain; see "TFC light sources never burn out inside a colony"). Applying MixinGradle here ALSO injects the runtime refmap
  remapping (`mixin.env.remapRefMap`) into `:compat`'s dev runs — needed even when this config was empty,
  because without it *other* mods' SRG-named mixins (e.g. Patchouli's `AccessorScreen`) fail to apply in
  the official-mapped dev env (`InvalidAccessorException`). MixinGradle auto-registers `:compat`'s own
  config into its own runs (the `:replacements` configs are registered explicitly via `--mixin.config`
  args; see [compat/build.gradle](compat/build.gradle)).
- **Don't `@Shadow` a deeply-inherited field of another mod's class.** `MixinEntityAIWorkFarmer` first
  tried `@Shadow protected Level world;` — `world` is declared ~4 superclasses up in `AbstractAISkeleton`,
  and at APPLY time Mixin threw `@Shadow field world was not located in the target class` (crash the moment
  the first farmer AI loaded; the AP only warns at compile). Fix: don't shadow — use a `@Redirect` whose
  redirected call hands you the object you need. We redirect the `Level#setBlockAndUpdate` call inside the
  target method, so the handler receives the `Level` as its receiver. No shadow required.
- The runtime Mixin logs `Compatibility level JAVA_17 ... higher than max supported (JAVA_13)` as
  DEBUG — benign (config still selected).

### Structurize integration points (recon, structurize 1.20.1-1.0.816)

- **`com.ldtteam.structurize.placement.StructurePlacer#handleBlockPlacement(Level, BlockPos, ChangeStorage, BlockInfo)`**
  — the single chokepoint where the blueprint's stored state becomes the placed block
  (`localState = blockInfo.getState()` → `PlacementHandlers.getHandler(...)` → `handler.handle(...)`).
  Our `@ModifyVariable(remap=false)` rewrites the `BlockInfo` arg at HEAD.
- **`com.ldtteam.structurize.util.BlockInfo`** — immutable record `(BlockPos, BlockState, CompoundTag)`.
- For builder material requests (future): also apply substitution in
  `IPlacementHandler#getRequiredItems` / `StructurePlacer#getResourceRequirements`.
- Preview (DONE): the hologram is baked in `client.BlueprintRenderer#init`, which iterates
  `Blueprint#getBlockInfoAsList()` and renders each `BlockInfo.getState()` via `renderBatched(...)` —
  the fake level (`BlueprintBlockAccess`) is passed only as the tint/light context, NOT the rendered
  block source. So the preview hook is a **`@Redirect` of that `BlockInfo.getState()` read in
  `init`** (MixinBlueprintRenderer); substituting `BlueprintBlockAccess#getBlockState` alone does
  nothing visible (that was the first wrong attempt — it only changes context). Dead ends ruled out:
  `Blueprint#buildBlockInfoCaches` is shared with server placement (can't substitute there without
  double-applying), and `BlueprintBlockInfoTransformHandler` only runs during tile-entity
  instantiation (`BlueprintUtils`), not the block-state bake.
- GUI toggle (future): `client.gui.WindowExtendedBuildTool` (placement window).

## The substitution feature (in :replacements)

Placement-time, non-destructive, datapack-driven. **Global** while enabled (`Config.enableSubstitution`,
default on) — applies to every Structurize placement, not opt-in per build (a GUI toggle is planned).

- [SubstitutionRule](replacements/src/main/java/com/structurizereplacements/substitution/SubstitutionRule.java) —
  match by exact block or block tag → replacement block. **Explicit only:** a rule applies solely to the
  block(s) it names — there is **no** implicit cascade to sibling forms. To swap a whole wood set, write a
  rule per form (planks/stairs/slabs/…), or offer a `to_tag` candidate pool and let the player pick.
  (The old `FamilyRule`/`CascadeShapes` material-token cascade — `oak_planks→spruce_planks` auto-applying
  to oak stairs/slabs/etc. — was **removed** in favour of explicit control; do not re-add it without
  explicit instruction.)
- [CandidateRule](replacements/src/main/java/com/structurizereplacements/substitution/CandidateRule.java) —
  an interactive `to_tag` pool (source block/tag → a tag of candidates) that drives the GUI "Replace"
  picker; substitutes nothing on its own.
- [BlockSubstitutions](replacements/src/main/java/com/structurizereplacements/substitution/BlockSubstitutions.java) —
  `apply(BlockInfo)`/`applyState(state, overrides)` swaps state, **copies shared properties**
  (facing/axis/half…), memoized, cleared on reload. Resolution order: per-placement override (player pick)
  → datapack exact/tag rules.
- [BlockSubstitutionReloadListener](replacements/src/main/java/com/structurizereplacements/substitution/BlockSubstitutionReloadListener.java) —
  loads `data/<namespace>/block_substitutions/*.json` across **all** datapacks/namespaces; registered on
  `AddReloadListenerEvent` in [event/ModEvents](replacements/src/main/java/com/structurizereplacements/event/ModEvents.java).

**Rule JSON** — each entry has exactly one source (`"from"` block id or `"from_tag"` block tag id) and one
target (`"to"` block id → auto-substitution, or `"to_tag"` block tag id → interactive GUI pool); first
match wins; unknown ids are logged and skipped. `:replacements` ships **no active rules** (a published
library must not rewrite consumers' blueprints) — the old `examples.json` is now copy-paste reference in
[docs/substitution-rule-examples.md](docs/substitution-rule-examples.md) (fixed `to` swaps, `to_tag` wooden
candidate pools, an `apply_properties` example). TFC rules ship in `:compat` (see "TFC default substitutions" below):
the bulk stone/wood rules + candidate-pool tags are in
[tfc_stone.json](compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json) /
[tfc_wood.json](compat/src/main/resources/data/mctfc/block_substitutions/tfc_wood.json) (+ the pool tags under
`data/mctfc/tags/blocks/subst/`); [defaults.json](compat/src/main/resources/data/mctfc/block_substitutions/defaults.json)
is now just a (curated/empty) home for hand-picked overrides.

## Verified

`gradlew build` (both jars) and `gradlew :compat:runClient` both succeed: all 7 mods load
(structurizereplacements, mctfc, structurize, blockui, minecolonies, domum_ornamentum, tfc, patchouli),
mixin config selects, both mods construct, reaches main menu. **Block swap on placement was confirmed
in-game** (planks→spruce planks, cobblestone→mossy cobblestone) before the split; the logic is
unchanged since, but re-confirm after any change by placing a blueprint via the Structurize build tool
and `/reload`-ing edited JSON.

## Roadmap / not yet done

In `:replacements` (generic):
- ~~**Family/material-token cascade**~~ — **REMOVED** (was form-gated via `CascadeShapes`). Dropped in
  favour of explicit per-form rules + the GUI candidate pools, for predictable, explicit control. Don't
  reintroduce implicit cascading without explicit instruction.
- ~~**Client preview mixin**~~ — DONE ([MixinBlueprintRenderer](replacements/src/main/java/com/structurizereplacements/mixin/MixinBlueprintRenderer.java)
  redirects the render-loop `BlockInfo.getState()`; [MixinBlueprintBlockAccess](replacements/src/main/java/com/structurizereplacements/mixin/MixinBlueprintBlockAccess.java)
  keeps the tint/light context consistent). Works in single-player (integrated server shares the JVM,
  so client sees the rules). **Dedicated servers:** rules load server-side only, so the client preview
  won't substitute until rules are synced to clients — a follow-up (network packet on join / on reload).
  - **Block entities in preview (chests, etc.) — DONE.** The `getState` redirect only fixes the *static block model*
    (`renderBatched`); block entities preview via their own `BlockEntityRenderer` from the BE **instance** built in
    `BlueprintUtils.instantiateTileEntities`, which keys the BE type off the blueprint's stored TE-NBT id — so a
    substituted chest still previewed as the original even though it *built* correctly. We can't touch the shared
    `constructTileEntity` (also used by `Blueprint#getBlockEntity` on build paths), so a second `@Redirect` in
    `MixinBlueprintRenderer#init` wraps the `instantiateTileEntities` call (its **only** caller is this client preview
    renderer) and post-processes the returned BE map: where a block was substituted to a *different* `EntityBlock`, swap
    in a fresh BE of the substituted type (`EntityBlock#newBlockEntity(pos, subState)` — state carries copied
    facing/chest-type) so the correct renderer/model is used; if it became a non-BE block, drop the preview TE (the
    substituted block model covers it). Uses `PlacementChoices.client()` like the model redirect.
- **GUI per-placement picker — DONE & verified (single-player).** Full design/recon/status in
  [docs/gui-replacement-picker-plan.md](docs/gui-replacement-picker-plan.md). Players open a "Replace"
  button on Structurize's build-tool window → a multi-row picker (one row per distinct source block in
  the schematic matching a `to_tag` candidate rule) → each row opens the reused `WindowSelectRes`
  (candidate-tag pool). Picks sync client→server and apply at placement; the preview re-bakes live.
  Pieces: `to_tag` [CandidateRule](replacements/src/main/java/com/structurizereplacements/substitution/CandidateRule.java)
  + `BlockSubstitutions.candidateFor`; override layer `applyState(state, overrides)` (override beats
  datapack rules); choices in [ClientPlacementChoices](replacements/src/main/java/com/structurizereplacements/placement/ClientPlacementChoices.java)/[ServerPlacementChoices](replacements/src/main/java/com/structurizereplacements/placement/ServerPlacementChoices.java)
  synced via [Network](replacements/src/main/java/com/structurizereplacements/network/Network.java);
  reach placement by attaching the choice map to the `StructurePlacer` at `PlaceStructureOperation`
  creation ([MixinPlaceStructureOperation](replacements/src/main/java/com/structurizereplacements/mixin/MixinPlaceStructureOperation.java)
  → read in `handleBlockPlacement`); GUI in [WindowReplacements](replacements/src/main/java/com/structurizereplacements/client/gui/WindowReplacements.java)
  + button [MixinAbstractBlueprintManipulationWindow](replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractBlueprintManipulationWindow.java);
  live refresh via `BlueprintHandler.getInstance().clearCache()`. Labels reuse existing translations
  (Structurize + vanilla `gui.done`).
  **Caveats / follow-ups:** the per-placement GUI choice applies to creative-paste placement; per-blueprint
  session memory + row counts are unpolished; and **dedicated servers need rule sync** — candidate/datapack
  rules load server-side only, so on a dedicated server the client GUI shows no rows and the preview can't
  substitute (single-player works, shared JVM).
- **Builder placement — DONE & verified (datapack rules).** MineColonies builder/quarrier use Structurize's
  `StructurePlacer`, so datapack substitution applies to builder-built structures across all three
  phases, kept consistent: **place** (`handleBlockPlacement`, already), **request materials**
  (`getResourceRequirements` arg — so the builder requests what it'll place), and **build-progress match**
  (`AbstractBlueprintIterator#iterateWithCondition` redirects the static
  `IPlacementHandler.doesWorldStateMatchBlueprintState(BlockInfo,…)` so a placed substituted block counts
  as built and the builder completes). All three use datapack rules only (the builder has no per-placement
  GUI choice; `StructurePlacer.choices` is null there). See
  [MixinStructurePlacer](replacements/src/main/java/com/structurizereplacements/mixin/MixinStructurePlacer.java)
  + [MixinAbstractBlueprintIterator](replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractBlueprintIterator.java).
  Per-building player choices (carry GUI picks into the builder) — **DONE & verified in-game** (builder
  requests + places + completes with the player's GUI picks; Build Options material list reflects them too).
  - **Part A** (in `:replacements`, Structurize-only): the choice map lives on the shared
    `AbstractStructureHandler` ([MixinAbstractStructureHandler](replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractStructureHandler.java)),
    so place/request/match all read `handler.getReplacementChoices()`. Resolution is **lazy in the getter**
    (NOT a constructor inject — `@Inject(method="<init>")` into these Structurize handlers silently never
    fires, even though the mixin applies and the `PlacementChoiceHolder` interface works; debugging-confirmed,
    do not re-attempt ctor injection here): (1) explicit choices set on the handler win (build-tool attaches
    the player's picks via `placer.getHandler()` at `PlaceStructureOperation`); else (2) the pluggable
    [ChoiceResolver](replacements/src/main/java/com/structurizereplacements/placement/ChoiceResolver.java)
    (consulted on **both** sides — a generic hook so the engine core doesn't hard-reference MineColonies;
    the MineColonies impl registers into it only when MC is loaded) returns the choices of whatever
    is at `worldPos`. A **non-null** result (even empty) means "a building is here" → use it (empty ⇒ no
    override ⇒ datapack rules) and DON'T fall back; `null` means "no building here". Cached per handler on
    the server only. Else (3) **client-only** fall back to `ClientPlacementChoices.current()` — the
    build-wand preview path, where no building exists at the position.
  - **Part B** (in `:replacements`, the **optional** MineColonies integration under
    `com.structurizereplacements.integration.minecolonies` + `mixin.minecolonies` — loaded only when MC is
    present): persist the choice map on `AbstractBuilding` NBT (key `structurizereplacements_choices`, in
    colony save — NOT the hut block-entity NBT) and sync it to the client building **view**
    ([MixinAbstractBuilding](replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinAbstractBuilding.java):
    `serializeNBT`/`deserializeNBT` + a self-describing trailer on `serializeToView`;
    [MixinAbstractBuildingView](replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinAbstractBuildingView.java)
    reads the trailer; shared MC-free buffer codec
    [ChoiceCodec](replacements/src/main/java/com/structurizereplacements/placement/ChoiceCodec.java)).
    Capture the placing player's choices at hut placement via `BlueprintPlacementHandling#process`
    ([MixinBlueprintPlacementHandling](replacements/src/main/java/com/structurizereplacements/mixin/MixinBlueprintPlacementHandling.java)
    — in the **main** config since it targets Structurize, no-ops unless MC is loaded →
    [StagedChoices](replacements/src/main/java/com/structurizereplacements/placement/StagedChoices.java)
    keyed by `msg.pos`); **adopt staged onto the building at creation** (`RegisteredStructureManager#addNewBuilding`,
    [MixinRegisteredStructureManager](replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinRegisteredStructureManager.java))
    so it persists + syncs immediately at placement (not first build). The
    [BuildingChoiceResolver](replacements/src/main/java/com/structurizereplacements/integration/minecolonies/BuildingChoiceResolver.java)
    (registered as the `ChoiceResolver` by
    [MineColoniesIntegration](replacements/src/main/java/com/structurizereplacements/integration/minecolonies/MineColoniesIntegration.java)`#init`,
    called from the guarded `StructurizeReplacements` ctor) resolves the building's choices: **server** via
    `getColonyByPosFromWorld→getCommonBuildingManager().getBuilding(pos)` (+ adopt-staged fallback);
    **client** via `IColonyManager.getBuildingView(dimension, pos)` (the chunk owning-colony cap that
    `getColonyByPosFromWorld` relies on is NOT reliably synced client-side — use `getBuildingView`).
    The MC mixins live in `structurizereplacements.minecolonies.mixins.json` (`required:false`). Caveat: a
    restart between placement and building creation could lose unadopted staged choices, but adoption is now
    at creation (same tick), so in practice they persist from placement onward. Not yet covered:
    creative-anchor hut placement (`ISpecialCreativeHandlerAnchorBlock.setup`).
  - **Per-building editing in Build Options — DONE & verified.** A bottom-left "Replace" button on
    MineColonies' `WindowBuildBuilding` ([MixinWindowBuildBuilding](replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinWindowBuildBuilding.java),
    ctor TAIL — note `onOpened` is inherited so can't be targeted; shadows `building` + `updateResources`)
    opens the shared picker scoped to that building. The picker
    ([WindowReplacements](replacements/src/main/java/com/structurizereplacements/client/gui/WindowReplacements.java))
    is generalized over a [ReplacementChoiceContext](replacements/src/main/java/com/structurizereplacements/client/gui/ReplacementChoiceContext.java):
    [BuildWandChoiceContext](replacements/src/main/java/com/structurizereplacements/client/gui/BuildWandChoiceContext.java)
    (global session picks) vs [BuildingChoiceContext](replacements/src/main/java/com/structurizereplacements/integration/minecolonies/BuildingChoiceContext.java)
    (one building — sources from the building's blueprint loaded via `StructurePacks.getBlueprintFuture`;
    current from the synced view; on pick: optimistic view update + `SetBuildingChoicesMessage`
    ([McNetwork](replacements/src/main/java/com/structurizereplacements/integration/minecolonies/McNetwork.java),
    a separate channel registered only when MC is present) → server sets+`markDirty` (persist + re-sync) →
    `updateResources()` + `clearCache()` refresh). Picks are **per-building only** (don't touch the session
    picks) and apply on the next build/upgrade. "Done" reopens the parent window (build tool / Build Options)
    instead of closing to the game.
- **GUI toggle** in `WindowExtendedBuildTool` for per-placement opt-in.

In `:compat`:
- ~~Real TFC rule sets~~ — **DONE** (see "TFC default substitutions" below). Next: the broader MC↔TFC
  bridging (food/nutrition, requests/progression, animals).

### TFC default substitutions (stone + wood families) — DONE

Vanilla MineColonies blueprints are built from vanilla blocks; these rules retexture colony builds into TFC
materials. The design uses the engine's two-stage resolution (fixed rule converts vanilla→TFC first, then a
candidate pool keyed on the **converted** block lets the player re-pick — see
[BlockSubstitutions](replacements/src/main/java/com/structurizereplacements/substitution/BlockSubstitutions.java)`#applyState`/`resolveBlock`).
So every covered source gets **both** a fixed default **and** a pick pool, with unique sources (fixed on the
vanilla block, pool on the TFC-result tag) — never a fixed `to` + `to_tag` on the same source (that shadows).

- **Wood** ([tfc_wood.json](compat/src/main/resources/data/mctfc/block_substitutions/tfc_wood.json)): vanilla →
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
- **Stone** ([tfc_stone.json](compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json)): vanilla
  stone family → **dacite** forms (closest look) — `stone→raw`, `stone_bricks→bricks`, `smooth_stone→smooth`,
  mossy/cracked/chiseled likewise, all with stairs/slabs/walls. Cobble and mossy-cobble map to the **non-falling
  mortared dacite twin** (`mctfc:mortared/tfc/rock/.../dacite`) so builds survive TFC gravity; their stairs/slabs/
  walls (which don't landslide) use plain TFC. `minecraft:stone_button` → `tfc:rock/button/dacite`. Vanilla
  **granite/diorite/andesite** (which are real TFC rock types) map to the **same** rock — plain → `tfc:rock/raw/<rock>`,
  polished → `tfc:rock/smooth/<rock>` (+ stairs/slabs/walls). Per-form candidate pools let the player pick any
  TFC rock — the cobble/mossy-cobble full-block pick reuses the runtime `mctfc:mortared_cobblestone` pool, and
  granite/diorite/andesite reuse the existing `raw`/`smooth` pools.
- **Sandstone** ([tfc_sandstone.json](compat/src/main/resources/data/mctfc/block_substitutions/tfc_sandstone.json)):
  **pool-only, no implicit swap** — vanilla sandstone is accessible in TFC so it stays the default, but every
  variant (normal + red, raw/cut/smooth + stairs/slabs/walls) offers a *Replace* pool of TFC colored sandstones
  (`tfc:{raw,smooth,cut}_sandstone/<color>`, all 7 colors) of the matching form. This is the `from` → `to_tag`
  pattern (pool keyed directly on the vanilla block, since nothing converts it first).
- **Pool tags** live under `data/mctfc/tags/blocks/subst/{wood,rock}/*.json` (one per form, listing every TFC
  variant). The rule files and tags are emitted by [gen_tfc_substitutions.sh](compat/gen_tfc_substitutions.sh)
  (re-run if TFC's rock/wood set changes); they're plain static JSON, so `/reload`-able and editable. Validated:
  every fixed-rule target and all pool-tag members (880) resolve to real TFC blocks.

### Farmer farms TFC crops (till → plant → fertilize → harvest) — DONE & verified

The MineColonies farmer (`com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer`)
now tills TFC soil, plants TFC crops on the resulting TFC farmland, keeps the soil's nutrients up with TFC
fertilizers, and harvests them (with a per-field Fruiting/Seeding mode). All hooks live in
[MixinEntityAIWorkFarmer](compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java) +
[TfcFarmlandHelper](compat/src/main/java/com/mctfc/farming/TfcFarmlandHelper.java) +
[FertilizerHelper](compat/src/main/java/com/mctfc/farming/FertilizerHelper.java) (`@Mixin(remap = false)` —
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
  ([farmer_tillable.json](compat/src/main/resources/data/mctfc/tags/blocks/farmer_tillable.json): the 8 TFC
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
[HarvestMode](compat/src/main/java/com/mctfc/farming/HarvestMode.java): *Fruiting* (default — harvest ripe
crops for produce + any dead crop for seeds); *Seeding* (leave ripe crops to die, then harvest only the
mature dead stage for max seeds). Pieces:
- **State on `FarmField`** — [MixinFarmField](compat/src/main/java/com/mctfc/mixin/MixinFarmField.java) adds a
  `HarvestMode` field + the [FarmFieldHarvestMode](compat/src/main/java/com/mctfc/farming/FarmFieldHarvestMode.java)
  duck-type interface, and carries it through both of `FarmField`'s existing serialization paths: NBT
  (`serializeNBT`@RETURN / `deserializeNBT`@TAIL, colony save) and the buffer (`serialize`/`deserialize`@TAIL,
  client sync). The buffer hooks append/consume the enum **last** on both sides so the stream stays aligned
  with the base seed/radii/stage payload. Same class both sides, so the client GUI reads the synced mode.
- **GUI toggle** — [MixinWindowField](compat/src/main/java/com/mctfc/mixin/MixinWindowField.java) (client) adds
  a `ButtonImage` below the seed icon (shadows the window's own private `farmField`/`getCurrentColony()` — both
  declared on `WindowField`, so they resolve). Mirrors the seed selector: optimistic client update + a server
  message; the label (current mode) refreshes each `onUpdate` (the client `farmField` resolves a tick after
  ctor). **`ButtonImage` gotcha:** a programmatic `ButtonImage()` won't draw its label — its `setSize` rescales
  the text-render box *proportionally from the previous value*, and the no-arg ctor leaves that at 0, so the box
  stays 0 and the text is clipped to nothing; also the default text colour is white (invisible on the light
  button). Fix: `setTextRenderBox(w, h)` after `setSize`, and `setColors(0x000000)`.
- **Network** — own channel [McFarmingNetwork](compat/src/main/java/com/mctfc/network/McFarmingNetwork.java) +
  [SetHarvestModeMessage](compat/src/main/java/com/mctfc/network/SetHarvestModeMessage.java) (client→server;
  resolves the colony via `getColonyByDimension(id, dim)`, finds the field via
  `getServerBuildingManager().getMatchingBuildingExtension(pos)`, sets the mode, `markBuildingExtensionsDirty()`
  to persist + re-sync). Registered from the mod ctor.
- **Harvest reads the mode** — [MixinEntityAIWorkFarmer](compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java)
  captures the worked field's mode into a `@Unique` field via two `@Redirect`s on the extension module's
  `getExtensionToWorkOn()` (in `prepareForFarming`) / `getCurrentExtension()` (in `workAtField`) — fetched right
  before the harvest dispatch on each side. The `findHarvestableSurface` hook then: harvests mature dead crops
  (both modes); and in *Seeding*, returns `null` for live `CropBlock`s so ripe crops are left to go to seed.

**Fertilizing (TFC soil nutrients), best-match auto-request — DONE & verified.** Vanilla MineColonies uses
"fertilizer" (its compost / bone meal) as a **growth accelerator** (`findHarvestableSurface` → `crop.growCrops`).
TFC is different: fertilizers don't speed growth, they top up the farmland's N/P/K nutrients (`IFarmland`); each
crop drains its own `primaryNutrient` (`ICropBlock.getPrimaryNutrient`) and low nutrient → low yield/death.
[FertilizerHelper](compat/src/main/java/com/mctfc/farming/FertilizerHelper.java) bridges this; the model is
data-driven (`Fertilizer.MANAGER` maps items → N/P/K; `Fertilizer.get(stack)`):
- **No more growth-cheat for TFC crops** — the `findHarvestableSurface` hook now fully *owns* the decision for a
  live TFC `CropBlock` (harvest only when ripe + Fruiting; else `null`), so the base AI's `growCrops` compost
  path never runs on them.
- **Apply at plant + opportunistically on visit** — `fertilizeForSeed` (HEAD inject on `plantCrop`) and a
  `fertilize(...)` call in the `findHarvestableSurface` hook (which the harvest scan runs for every cell of a
  planted field) top up the crop's primary nutrient. `fertilize` re-picks the **best-matching** fertilizer the
  farmer carries (most of the needed nutrient — so guano/compost beat a pure powder) and applies until the
  nutrient reaches `Config.fertilizeTarget`, only kicking in below `Config.fertilizeBelow` (hysteresis). Config
  in [Config](compat/src/main/java/com/mctfc/Config.java) (`config/mctfc-common.toml`: `fertilizeBelow` 0.4,
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

### Decay-aware item stacking (TFC food freshness) — DONE & verified

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
- **The fix** ([MixinInventoryCitizen](compat/src/main/java/com/mctfc/mixin/MixinInventoryCitizen.java),
  `@Mixin(remap = false)` — MC's own method): a `@Redirect` of the single `compareItemStacksIgnoreStackSize`
  call in `insertItem` that AND-s in [FoodStackingHelper](compat/src/main/java/com/mctfc/food/FoodStackingHelper.java)`#canMerge`.
  `canMerge` returns `true` for non-food (no behaviour change); for TFC food it defers to the vanilla caps-aware
  `ItemHandlerHelper.canItemStacksStack`, i.e. the same rule TFC uses for slot stacking — foods sharing a rounded
  decay window still stack, differently-aged ones don't. Nothing else (requests, storage keys, recipes) changes.

### Food spoilage management (colony-storage preservation + freshness-aware eating) — DONE, in-world test pending

TFC food spoils; MineColonies' food economy (bulk-request → hoard in racks → cook batches → citizens eat whenever)
assumes food is inert, so untouched colonies rot their warehouses and citizens eat rot. Three surgical pieces fix
the worst of it; all gate on the TFC food **capability** (`net.dries007.tfc.common.capabilities.food.FoodCapability`,
caps-aware — same blindness story as the stacking fix above). Design choices (all the user's): **suppress decay in
colony-owned storage only** (player racks inert), strength **live-configurable**; FIFO as a **tiebreaker** (keep MC's
diet-variety scoring); rotten handling is **skip-only** (disposal will be a future composter-request path).

- **A — colony-storage preservation** ([FoodPreservation](compat/src/main/java/com/mctfc/food/FoodPreservation.java)
  + [MixinRackInventory](compat/src/main/java/com/mctfc/mixin/MixinRackInventory.java) +
  [AbstractTileEntityRackAccessor](compat/src/main/java/com/mctfc/mixin/AbstractTileEntityRackAccessor.java)):
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
  ([MixinFoodUtils](compat/src/main/java/com/mctfc/mixin/MixinFoodUtils.java), `@Mixin(remap=false)`): both citizen eating
  (`EntityAIEatTask`) and the cook (`EntityAIWorkCook`) funnel through `FoodUtils.canEat` + `FoodUtils.getBestFoodForCitizen`.
  - **Skip-rotten:** `@Inject` HEAD of `canEat` → `false` when `FoodCapability.isRotten(stack)` — covers eating, cooking and
    the building food scan at one choke (citizens just don't pick rot; if only rot exists they don't eat, same as no food).
  - **FIFO:** `@Inject` RETURN of `getBestFoodForCitizen` → after MC picks a slot, scan for another slot holding the **same
    `Item`** (hence identical desirability score — a *true* tiebreaker that never overrides MC's variety choice) and **not
    rotten**, and swap to the one with the soonest `IFood#getRottenDate()`. Per-stack only (citizen/cook inventory); the
    building rack scan aggregates by caps-blind `ItemStorage` so it can't see age — a known follow-up.
- **Config** ([Config](compat/src/main/java/com/mctfc/Config.java)): `foodColonyStorageDecay` (`config/mctfc-common.toml`,
  default 0.25). Lang: the trait tooltip key `mctfc.food_trait.colony_storage` (TFC's `FoodTrait#addTooltipInfo` calls
  `Component.translatable(translationKey)` directly, so the key *is* the lang key).
- **Config** ([Config](compat/src/main/java/com/mctfc/Config.java)): `foodColonyStorageDecay` (`config/mctfc-common.toml`,
  default 0.25). Lang: the trait tooltip key `mctfc.food_trait.colony_storage` (TFC's `FoodTrait#addTooltipInfo` calls
  `Component.translatable(translationKey)` directly, so the key *is* the lang key).
- **Verified to load:** compiles; all three mixins apply (`AbstractTileEntityRackAccessor`/`MixinRackInventory` into the
  rack, `MixinFoodUtils` into `FoodUtils`); trait registers; runs in a live colony world without crash. **In-world
  behaviour** (food actually preserving in racks, FIFO order, rotten skipped) still to be confirmed in gameplay.

### TFC food nutrition value (citizen saturation) — DONE & verified

TFC food fed MineColonies citizens almost no saturation (~0.83). **Why:** every TFC food item ships a *flat* vanilla
`FoodProperties` (`nutrition = 4`, `saturationMod = 0.3`) — its real nutrition lives in the TFC `FoodData` capability
(`hunger()`, `saturation()`, the 5 nutrients), which MineColonies never reads. `FoodUtils#getFoodValue(ItemStack,
FoodProperties, double)` then computes `nutrition × 0.25 (non-MC-food nerf) / 1.2`, i.e. `4 × 0.25 / 1.2 ≈ 0.83` — vs
MineColonies' own food (`IMinecoloniesFoodItem`, no nerf) at `nutrition/1.2 ≈ 4–10`.

- **The bridge** (third hook in [MixinFoodUtils](compat/src/main/java/com/mctfc/mixin/MixinFoodUtils.java)): `@Inject` HEAD
  (cancellable) on the **core** `getFoodValue(ItemStack, FoodProperties, double)` — every saturation path funnels through
  it (`ItemStackUtils#consumeFood` for citizen self-eat / nether / player-fed; the cook's `increaseSaturation`; the qty
  calcs; the JEI/EMI tooltip), and the `getFoodValue(stack, citizen)` overload delegates to it. For TFC food (gated on
  `FoodCapability.has`) it recomputes from `FoodData`: `hunger × (1 + saturation) / 1.2 × (1 + researchBonus) ×
  Config.tfcFoodSaturationModifier`, dropping the 0.25 nerf and keeping the `/1.2` + research scaling so it matches MC's
  own food. `hunger` is a **flat 4** across all TFC food, so `saturation` (the real quality signal: berry 0.2 → cabbage
  0.5 → bread 1.0 → cooked_beef 2.0; meals higher) is what differentiates — landing blueberry ≈ 4.0, cooked_beef ≈ 10.0,
  right in MC's range. Non-TFC food falls through unchanged.
- **Config** ([Config](compat/src/main/java/com/mctfc/Config.java)): `tfcFoodSaturationModifier` (default `1.0` = 100%,
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
[MixinEntityAIWorkFarmer](compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java)
(`@Mixin(remap = false)` — MineColonies' own class/methods; only the inner MC calls are remapped per their
`@At`), both `@Redirect` (no `@Shadow` — see the Mixins note about the inherited `world` field):
- **Recognition** — redirect the `BlockState.is(BlockTags.DIRT)` call in `findHoeableSurface` to also accept
  `#mctfc:farmer_tillable`
  ([data/mctfc/tags/blocks/farmer_tillable.json](compat/src/main/resources/data/mctfc/tags/blocks/farmer_tillable.json):
  the 8 TFC grass variants that have a farmland twin; `peat_grass`/`kaolin_clay_grass` excluded — no
  farmland). TFC bare dirt already passes via `minecraft:dirt`.
- **Farmland type** — redirect the `Level.setBlockAndUpdate` call in `createCorrectFarmlandForSeed`. Ask
  the soil what a hoe would make of it (`getToolModifiedState(HOE_TILL)`,
  [TfcFarmlandHelper](compat/src/main/java/com/mctfc/farming/TfcFarmlandHelper.java) — builds a throwaway
  `UseOnContext` with an iron hoe); if that's a **non-vanilla** farmland (TFC), place it, else place exactly
  what MineColonies intended (so vanilla soil + MineColonies-crop preferred farmland are untouched). The
  block-above is already cleared by the AI before this call, so TFC's air-above check passes.

(At the tilling-only stage the farmer then couldn't plant on TFC farmland — that's what the PLANT/HARVEST
hooks above added.)

</details>

### Miner shaft uses the hut fill-block setting — DONE

The MineColonies miner builds two ways: its node/tunnel/shaft **blueprints** go through Structurize's `StructurePlacer`
(already datapack-substituted by the engine, like the builder — unchanged), but the **vertical-shaft frame** is placed
raw. The main fill + water-walling + `getSolidSubstitution()` already read the hut's configurable **`FILL_BLOCK` setting**
(set it to a TFC block in the miner GUI — request, inventory-consume and placement then all agree on a block the player
can actually supply). The one gap: `EntityAIStructureMiner#getLadderBackFillBlock()` is **hardcoded** to
`Blocks.COBBLESTONE`/`NETHERRACK`, ignoring that setting — and vanilla cobblestone both *landslides* under TFC and isn't
obtainable in a TFC world, so the ladder backfill desynced (requested vanilla cobble, couldn't be fulfilled / collapsed).
Fix: [MixinEntityAIStructureMiner](compat/src/main/java/com/mctfc/mixin/MixinEntityAIStructureMiner.java) (`@Mixin(remap
= false)`, `@Inject` RETURN on the private `getLadderBackFillBlock`, shadowing the private `getMainFillBlock`) returns the
`FILL_BLOCK` setting so the **whole** shaft uses the GUI-chosen block. **Not** the substitution engine — this is the
vanilla MineColonies fill-block mechanism, just made consistent. (Default `FILL_BLOCK` is still cobblestone; the player
sets a TFC block — e.g. a cemented cobble or any TFC stone — in the hut GUI.) An earlier attempt that substituted the raw
placement via the engine was reverted: it placed a substituted block but still *requested* vanilla cobblestone, which
breaks in TFC.

### Build areas are collapse-proof while being built (virtual TFC support) — DONE, in-world test pending

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
- **The implementation** ([BuildAreaSupport](compat/src/main/java/com/mctfc/collapse/BuildAreaSupport.java) registry +
  [MixinAbstractEntityAIStructure](compat/src/main/java/com/mctfc/mixin/MixinAbstractEntityAIStructure.java) +
  [MixinSupport](compat/src/main/java/com/mctfc/mixin/MixinSupport.java)):
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
    [MixinEntityAIStructureMiner](compat/src/main/java/com/mctfc/mixin/MixinEntityAIStructureMiner.java) `@Inject`s HEAD of those
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

### Citizens rest for TFC's localized rain (not the global flag) — DONE, in-world test pending

MineColonies citizens stop working and "rest" (the `IDLE`/`BAD_WEATHER` branch of `CitizenAI#calculateNextState`) when
`Level#isRaining()` is true. That's the **global** dimension-wide vanilla flag. TFC keeps the vanilla weather cycle but
makes rain **localized**: `EnvironmentHelpers.isRainingOrSnowing(level, pos) = level.isRaining() && WorldTracker.get(level).isRaining(tick, Climate.getRainfall(pos))`
— precipitation only actually falls where the position's annual rainfall beats the current event intensity, and TFC's
temperature decides rain vs snow (`Climate.getPrecipitation`). TFC also redirects `Level#isRainingAt(pos)` through this
model (its `LevelMixin`). So under TFC the global flag over-triggers: citizens rest during any rain *cycle* even when their
colony sits in an arid cell where nothing falls (and `world.isRaining()` is likewise on during snow, exactly like vanilla).

[MixinCitizenAI](compat/src/main/java/com/mctfc/mixin/MixinCitizenAI.java) (`@Mixin(remap = false)`, `@Redirect` the lone
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

### TFC light sources never burn out inside a colony — DONE, in-world test pending

TFC light sources burn out: the **metal lamp** (`tfc:metal/lamp/<metal>` — there is **no** `tfc:lantern`; TFC removes the
vanilla lantern, the lamp is its equivalent) drains a fluid fuel (olive_oil/tallow; lava in a blue-steel lamp is the only
infinite vanilla case) and goes dark when empty; **torches** decay to `tfc:dead_torch` after `torchTicks` (~1h), **candles**
go out, **jack-o'-lanterns** revert to carved pumpkins. All burn-out is server-side, per-position, on `randomTick`, backed by
a `TickCounterBlockEntity` (calendar-delta, unload-safe); TFC's only "off switch" is global (`torchTicks/candleTicks/jackOLanternTicks = -1`),
not colony-scoped. So colonies go dark unless re-lit/refuelled.

`:compat` freezes the burn-out of **already-lit** sources while they sit inside a colony (it won't relight a dead one or fuel
an unlit lamp). Gate: [ColonyLights](compat/src/main/java/com/mctfc/light/ColonyLights.java)`#keepLit(level, pos)` =
server-side && `Config.keepColonyLightsLit` && `IColonyManager.getColonyByPosFromWorld(level, pos) != null` (the
chunk-owning-colony cap). Two mixins (`@Mixin(remap=…)` as noted):
- [MixinTfcLightBlocks](compat/src/main/java/com/mctfc/mixin/MixinTfcLightBlocks.java) — **one multi-target** `@Mixin({TFCTorchBlock,
  TFCWallTorchBlock, TFCCandleBlock, TFCCandleCakeBlock, JackOLanternBlock})`; all five override the same
  `randomTick(BlockState, ServerLevel, BlockPos, RandomSource)`, so a single `@Inject(HEAD, cancellable)` cancels the whole
  tick (burn-out, and for candles the rain-extinguish too) when in-colony. `randomTick` is a **vanilla** `Block` method →
  **remapped** (the default; do NOT set `remap=false` here, unlike most `:compat` mixins which target the mods' own members).
- [MixinLampBlockEntity](compat/src/main/java/com/mctfc/mixin/MixinLampBlockEntity.java) — `@Mixin(remap=false)` (TFC's own
  method) `@Inject(HEAD, cancellable)` on `LampBlockEntity#checkHasRanOut` (the single fuel-drain chokepoint, also called from
  `use`/`fluidTankChanged`, so hooking it covers all drain paths). `level`/`pos` via casting `this` to vanilla `BlockEntity`.
- Cheap (random ticks are infrequent; the colony lookup is a chunk-cap read). Config
  [Config](compat/src/main/java/com/mctfc/Config.java)`#keepColonyLightsLit` (default **true**; false ⇒ TFC burnout applies
  everywhere). **Verified to load** (both mixins bind — `defaultRequire:1` would fail otherwise — client reaches menu, 0
  injection failures); in-world behaviour (lamp/torch/candle/jack staying lit in a colony, decaying outside) still to confirm.

### Non-falling ("mortared"/"cemented") cobble — DONE & verified

TFC makes cobble collapse (gravity), which wrecks MineColonies cobble builds. `:compat` registers a
**non-falling twin** of every cobble block and substitutes builds onto it.

- **Why a twin block, not a property/mixin:** TFC's falling is **tag-gated** — `tfc:can_landslide` lists
  `minecraft:cobblestone`/`mossy_cobblestone` and every `tfc:rock/cobble|mossy_cobble/<rock>`, checked per
  *block* (not per state). You can't add a blockstate property to an existing block (its `StateDefinition`
  is frozen at construction), and even if you could, TFC reads the tag, not a property. So the surgical
  fix is a separate block that simply isn't in `can_landslide`. (Same technique as MehVahdJukaar's
  StoneZone/Moonlight: registry scan + naming detection + runtime-generated assets.)
- **Scan + register** ([MortaredCobbleRegistry](compat/src/main/java/com/mctfc/block/MortaredCobbleRegistry.java)):
  on `RegisterEvent`, iterate `ForgeRegistries.BLOCKS` and register a
  [MortaredCobbleBlock](compat/src/main/java/com/mctfc/block/MortaredCobbleBlock.java) (`extends Block`,
  `Properties.copy(source)`, drops self, name "Mortared &lt;source&gt;") + a
  [MortaredCobbleBlockItem](compat/src/main/java/com/mctfc/block/MortaredCobbleBlockItem.java) per cobble,
  id `mctfc:mortared/<source-ns>/<source-path>`. **Detection is a name heuristic** (`isCobble`: path ends
  `cobblestone` or contains a `cobble/` segment, minus `_stairs/_slab/_wall/_button/_pressure_plate` and
  `infested`) — tags are unavailable at registration; the heuristic is anchored to reproduce
  `forge:cobblestone/normal`. **Only sees blocks registered before `mctfc`** (mods.toml orders it AFTER
  tfc) — a cobble mod loading after us isn't covered.
- **Client model delegation** ([MortaredCobbleClient](compat/src/main/java/com/mctfc/client/MortaredCobbleClient.java)):
  twins ship no blockstate/model JSON, so `ModelEvent.ModifyBakingResult` repoints each twin's baked block
  + item model at its source's. The bakery logs a benign "missing model" per twin during load — expected,
  overwritten here. (`getModels()` is keyed by `ResourceLocation`, not `ModelResourceLocation`.)
- **Runtime data pack** ([GeneratedDataPack](compat/src/main/java/com/mctfc/data/GeneratedDataPack.java) +
  [MortaredCobbleData](compat/src/main/java/com/mctfc/data/MortaredCobbleData.java)): the twins are dynamic
  so the tag/recipes can't be static JSON. At `AddPackFindersEvent` (twins already registered) we serve an
  in-memory **forced built-in** `SERVER_DATA` pack with `mctfc:mortared_cobblestone` (all twins) + a
  **shaped** recipe per twin (the cobble surrounded by 4 `#tfc:mortar`, cross pattern). The pack also makes
  twins behave/identify like normal cobble by adding `#mctfc:mortared_cobblestone` (tag-of-tags) to the
  block tags real cobble sits in — `minecraft:mineable/pickaxe`, `forge:cobblestone/normal`,
  `tfc:can_carve`, `tfc:toughness_2` — but deliberately **not** `tfc:can_landslide` (that's the gravity
  we're escaping).
- **In-world conversion** ([MortaredCobbleInteraction](compat/src/main/java/com/mctfc/block/MortaredCobbleInteraction.java),
  Forge bus): right-click a cobble holding `#tfc:mortar` → swap to its twin, consume 4 mortar (free in
  creative). Cancels the interaction; server-authoritative.
- **Substitution** is plain datapack (see "TFC default substitutions" above):
  [tfc_stone.json](compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json) fixes
  `minecraft:cobblestone → mctfc:mortared/tfc/rock/cobble/dacite` (the non-falling dacite twin) and offers the
  `mctfc:mortared_cobblestone` pool keyed on that converted twin, so the player re-picks the rock via the Replace
  GUI. **Gotcha:** the fixed default and the pool must have **distinct sources** (fixed on `minecraft:cobblestone`,
  pool on the `mctfc:mortared_cobblestone` tag that matches the *converted* twin) — a fixed `to` and a `to_tag` on
  the *same* source shadows the pool under converted-block semantics.

### Vanilla furnaces made decorative — DONE

TFC overhauls smelting/cooking (firepit/forge/bloomery/…), so the vanilla furnace, smoker and blast furnace
shouldn't be usable to bypass it — but MineColonies blueprints still place them.
[VanillaFurnaceHandler](compat/src/main/java/com/mctfc/block/VanillaFurnaceHandler.java) (Forge bus,
annotation-registered like [MortaredCobbleInteraction](compat/src/main/java/com/mctfc/block/MortaredCobbleInteraction.java))
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

**TFC-flavored crafting recipes** ([data/mctfc/recipes/](compat/src/main/resources/data/mctfc/recipes/)) so these
blocks are still obtainable in a TFC world (vanilla recipes need `minecraft:cobblestone`/`minecraft:iron_ingot`
that TFC players lack), all `mctfc:`-namespaced so they add to whatever TFC leaves: `furnace` = vanilla ring of
`#forge:cobblestone/normal` (includes vanilla + every TFC rock cobble); `smoker` = furnace + 4 `#minecraft:logs`
(TFC logs are in it); `blast_furnace` = furnace + 5 `#forge:ingots/wrought_iron` + 3 `tfc:ceramic/fire_brick`.
Also `bookshelf` = the vanilla recipe (6 `#minecraft:planks` + 3 `minecraft:book`) restored — the bookshelf→TFC
substitution was dropped, so vanilla bookshelves stay vanilla and need a recipe. `barrel` = vanilla shape with
`#minecraft:planks` + `#minecraft:wooden_slabs`.

**Vanilla barrel matches `tfc:chest`** ([MixinBarrelBlockEntity](compat/src/main/java/com/mctfc/mixin/MixinBarrelBlockEntity.java),
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

### Optional per-mod datapacks (Beneath) — pattern

To ship data that should apply **only when an optional mod is present**, register a built-in datapack gated on
`ModList.isLoaded(...)` at `AddPackFindersEvent` — so when the mod is absent the pack isn't registered at all and
its `<thatmod>:*` rules never load or warn. [BeneathDataPack](compat/src/main/java/com/mctfc/data/BeneathDataPack.java)
does this for **Beneath** (`beneath`): a `PathPackResources` rooted at the jar sub-folder
[beneath_datapack/](compat/src/main/resources/beneath_datapack/) (its own `pack.mcmeta` + `data/mctfc/block_substitutions/beneath.json`),
forced-on `SERVER_DATA`, same mechanism as [MortaredCobbleData](compat/src/main/java/com/mctfc/data/MortaredCobbleData.java)
but conditional + static files. Registered from the mod ctor. Beneath itself is a **dev-run dependency**
(`implementation fg.deobf("curse.maven:beneath-1113980:7400831")` in [compat/build.gradle](compat/build.gradle)).
Its `beneath.json` maps the vanilla **nether woods 1:1 to Beneath's crimson/warped wood** (planks/log→`wood/log`,
hyphae→`wood/wood`, stripped, stairs/slab/fence/door/etc.) — Beneath ships a full TFC-style crimson/warped wood
set (`beneath:wood/planks/crimson`, …), so these are real per-form swaps, not placeholders. Generated by the same
[gen_tfc_substitutions.sh](compat/gen_tfc_substitutions.sh) (its `beneath_wood` section). (No nether-brick rule:
Beneath provides a way to craft vanilla nether bricks directly.)

The Beneath woods also **join the per-form candidate pools** like any normal wood: the Beneath pack ships
*additive* (`replace:false`) tag files at `data/mctfc/tags/blocks/subst/wood/<form>.json` that merge crimson/warped
into the base `mctfc:subst/wood/<form>` pools — but **only when Beneath is loaded** (the files live in the Beneath
pack), so the base pools stay TFC-only and error-free when it's absent. No new candidate rules are needed (the base
`from_tag=to_tag` rules already cover the merged tag), so a crimson plank can be re-picked to warped or any TFC
wood, and vice-versa.

## Conventions

- Prefer public APIs / events; when reaching another mod's internals use a mixin (with `remap=false`
  for that mod's own members), not reflection.
- Keep logic side-aware and data-driven (tags/datapack JSON) where possible.
- `DeferredRegister`/`RegistryObject` for any registered content. Match surrounding style.

## Git: never commit

**Do NOT create git commits — ever, under any circumstances, even if asked in passing.** The user
manages all commits themselves. Make and leave changes in the working tree only; never run
`git commit` (or `git add` with intent to commit, amend, rebase, etc.). You may run read-only git
commands (`git status`, `git diff`, `git log`) when helpful.
