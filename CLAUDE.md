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
could not be read" error). The game then loads, does substitution + the build-wand GUI, logs the
`minecolonies` mixin config skipping its targets (`@Mixin target … was not found`), and does not crash —
**verified**. `:compat` adds EMI.

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
- **`:compat` owns `mctfc.mixins.json`** (package `com.mctfc.mixin`) — currently one mixin,
  [MixinEntityAIWorkFarmer](compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java) (the
  farmer-tills-TFC-soil bridge, below). Applying MixinGradle here ALSO injects the runtime refmap
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
match wins; unknown ids are logged and skipped. Example/dev rules (vanilla) ship in
[replacements .../block_substitutions/examples.json](replacements/src/main/resources/data/structurizereplacements/block_substitutions/examples.json)
— two exact `to` swaps plus `to_tag` candidate pools for the wooden families (planks/stairs/slabs/fences/
gates/doors/trapdoors/logs); **delete that file for a clean published library** (active rules would rewrite
any consumer's blueprints). TFC rules ship in
[compat .../block_substitutions/defaults.json](compat/src/main/resources/data/mctfc/block_substitutions/defaults.json)
(vanilla example values for now — swap to `tfc:…` ids; with no cascade, list each form explicitly or use
`to_tag` pools).

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
- Real TFC rule sets (verified `tfc:` ids), then the broader MC↔TFC bridging (food/nutrition,
  requests/progression, farming/animals).

### Farmer farms TFC crops (till → plant → harvest) — DONE & verified

The MineColonies farmer (`com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer`)
now tills TFC soil, plants TFC crops on the resulting TFC farmland, and harvests them. All four hooks live in
[MixinEntityAIWorkFarmer](compat/src/main/java/com/mctfc/mixin/MixinEntityAIWorkFarmer.java) +
[TfcFarmlandHelper](compat/src/main/java/com/mctfc/farming/TfcFarmlandHelper.java) (`@Mixin(remap = false)` —
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

**Pending follow-up — per-field harvest mode (Fruiting/Seeding) chosen in the field GUI:** *Fruiting* (default,
= current behaviour: harvest ripe for produce + any dead for seeds); *Seeding* (skip ripe crops, let them die,
harvest only the dead/mature stage for max seeds). Needs a mode stored on `FarmField` (it has public
`serializeNBT`/`deserializeNBT` + `serialize`/`deserialize(FriendlyByteBuf)` for save + client sync — same class
both sides), a toggle in the field window, a network message, and the harvest hook reading that mode (skip the
ripe-crop branch in Seeding). Not built yet.

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

### Non-falling ("mortared") cobble — DONE & verified

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
- **Substitution** is plain datapack: [defaults.json](compat/src/main/resources/data/mctfc/block_substitutions/defaults.json)
  ships `minecraft:cobblestone → to_tag #mctfc:mortared_cobblestone` (player picks the rock type via the
  Replace GUI). **Gotcha:** a fixed `to` rule on the same source (e.g. a leftover `cobblestone→mossy`
  example) converts first under converted-block semantics and shadows this candidate pool — keep sources
  unique across rules.

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
