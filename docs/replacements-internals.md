# Palette Swap (`:replacements`) — engine internals & implementation history

Detailed internals for the substitution engine: the Structurize integration recon, and the implemented
preview / GUI picker / builder per-building pieces (with the mixin gotchas each cost a crash). Moved out
of CLAUDE.md. See [CLAUDE.md](../CLAUDE.md) for the overview and the substitution-feature summary.

## Structurize integration points (recon, structurize 1.20.1-1.0.816)


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

## Implementation history (preview, GUI picker, builder per-building)

In `:replacements` (generic):
- ~~**Family/material-token cascade**~~ — **REMOVED** (was form-gated via `CascadeShapes`). Dropped in
  favour of explicit per-form rules + the GUI candidate pools, for predictable, explicit control. Don't
  reintroduce implicit cascading without explicit instruction.
- ~~**Client preview mixin**~~ — DONE ([MixinBlueprintRenderer](../replacements/src/main/java/com/structurizereplacements/mixin/MixinBlueprintRenderer.java)
  redirects the render-loop `BlockInfo.getState()`; [MixinBlueprintBlockAccess](../replacements/src/main/java/com/structurizereplacements/mixin/MixinBlueprintBlockAccess.java)
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
- **GUI per-placement picker — DONE & verified (single-player).** Players open a "Replace"
  button on Structurize's build-tool window → a multi-row picker (one row per distinct source block in
  the schematic matching a `to_tag` candidate rule) → each row opens the reused `WindowSelectRes`
  (candidate-tag pool). Picks sync client→server and apply at placement; the preview re-bakes live.
  Pieces: `to_tag` [CandidateRule](../replacements/src/main/java/com/structurizereplacements/substitution/CandidateRule.java)
  + `BlockSubstitutions.candidateFor`; override layer `applyState(state, overrides)` (override beats
  datapack rules); choices in [ClientPlacementChoices](../replacements/src/main/java/com/structurizereplacements/placement/ClientPlacementChoices.java)/[ServerPlacementChoices](../replacements/src/main/java/com/structurizereplacements/placement/ServerPlacementChoices.java)
  synced via [Network](../replacements/src/main/java/com/structurizereplacements/network/Network.java);
  reach placement by attaching the choice map to the `StructurePlacer` at `PlaceStructureOperation`
  creation ([MixinPlaceStructureOperation](../replacements/src/main/java/com/structurizereplacements/mixin/MixinPlaceStructureOperation.java)
  → read in `handleBlockPlacement`); GUI in [WindowReplacements](../replacements/src/main/java/com/structurizereplacements/client/gui/WindowReplacements.java)
  + button [MixinAbstractBlueprintManipulationWindow](../replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractBlueprintManipulationWindow.java);
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
  [MixinStructurePlacer](../replacements/src/main/java/com/structurizereplacements/mixin/MixinStructurePlacer.java)
  + [MixinAbstractBlueprintIterator](../replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractBlueprintIterator.java).
  Per-building player choices (carry GUI picks into the builder) — **DONE & verified in-game** (builder
  requests + places + completes with the player's GUI picks; Build Options material list reflects them too).
  - **Part A** (in `:replacements`, Structurize-only): the choice map lives on the shared
    `AbstractStructureHandler` ([MixinAbstractStructureHandler](../replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractStructureHandler.java)),
    so place/request/match all read `handler.getReplacementChoices()`. Resolution is **lazy in the getter**
    (NOT a constructor inject — `@Inject(method="<init>")` into these Structurize handlers silently never
    fires, even though the mixin applies and the `PlacementChoiceHolder` interface works; debugging-confirmed,
    do not re-attempt ctor injection here): (1) explicit choices set on the handler win (build-tool attaches
    the player's picks via `placer.getHandler()` at `PlaceStructureOperation`); else (2) the pluggable
    [ChoiceResolver](../replacements/src/main/java/com/structurizereplacements/placement/ChoiceResolver.java)
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
    ([MixinAbstractBuilding](../replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinAbstractBuilding.java):
    `serializeNBT`/`deserializeNBT` + a self-describing trailer on `serializeToView`;
    [MixinAbstractBuildingView](../replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinAbstractBuildingView.java)
    reads the trailer; shared MC-free buffer codec
    [ChoiceCodec](../replacements/src/main/java/com/structurizereplacements/placement/ChoiceCodec.java)).
    Capture the placing player's choices at hut placement via `BlueprintPlacementHandling#process`
    ([MixinBlueprintPlacementHandling](../replacements/src/main/java/com/structurizereplacements/mixin/MixinBlueprintPlacementHandling.java)
    — in the **main** config since it targets Structurize, no-ops unless MC is loaded →
    [StagedChoices](../replacements/src/main/java/com/structurizereplacements/placement/StagedChoices.java)
    keyed by `msg.pos`); **adopt staged onto the building at creation** (`RegisteredStructureManager#addNewBuilding`,
    [MixinRegisteredStructureManager](../replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinRegisteredStructureManager.java))
    so it persists + syncs immediately at placement (not first build). The
    [BuildingChoiceResolver](../replacements/src/main/java/com/structurizereplacements/integration/minecolonies/BuildingChoiceResolver.java)
    (registered as the `ChoiceResolver` by
    [MineColoniesIntegration](../replacements/src/main/java/com/structurizereplacements/integration/minecolonies/MineColoniesIntegration.java)`#init`,
    called from the guarded `StructurizeReplacements` ctor) resolves the building's choices: **server** via
    `getColonyByPosFromWorld→getCommonBuildingManager().getBuilding(pos)` (+ adopt-staged fallback);
    **client** via `IColonyManager.getBuildingView(dimension, pos)` (the chunk owning-colony cap that
    `getColonyByPosFromWorld` relies on is NOT reliably synced client-side — use `getBuildingView`).
    The MC mixins live in `structurizereplacements.minecolonies.mixins.json` (`required:false`). Caveat: a
    restart between placement and building creation could lose unadopted staged choices, but adoption is now
    at creation (same tick), so in practice they persist from placement onward. Not yet covered:
    creative-anchor hut placement (`ISpecialCreativeHandlerAnchorBlock.setup`).
  - **Per-building editing in Build Options — DONE & verified.** A bottom-left "Replace" button on
    MineColonies' `WindowBuildBuilding` ([MixinWindowBuildBuilding](../replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinWindowBuildBuilding.java),
    ctor TAIL — note `onOpened` is inherited so can't be targeted; shadows `building` + `updateResources`)
    opens the shared picker scoped to that building. The picker
    ([WindowReplacements](../replacements/src/main/java/com/structurizereplacements/client/gui/WindowReplacements.java))
    is generalized over a [ReplacementChoiceContext](../replacements/src/main/java/com/structurizereplacements/client/gui/ReplacementChoiceContext.java):
    [BuildWandChoiceContext](../replacements/src/main/java/com/structurizereplacements/client/gui/BuildWandChoiceContext.java)
    (global session picks) vs [BuildingChoiceContext](../replacements/src/main/java/com/structurizereplacements/integration/minecolonies/BuildingChoiceContext.java)
    (one building — sources from the building's blueprint loaded via `StructurePacks.getBlueprintFuture`;
    current from the synced view; on pick: optimistic view update + `SetBuildingChoicesMessage`
    ([McNetwork](../replacements/src/main/java/com/structurizereplacements/integration/minecolonies/McNetwork.java),
    a separate channel registered only when MC is present) → server sets+`markDirty` (persist + re-sync) →
    `updateResources()` + `clearCache()` refresh). Picks are **per-building only** (don't touch the session
    picks) and apply on the next build/upgrade. "Done" reopens the parent window (build tool / Build Options)
    instead of closing to the game.
- **GUI toggle** in `WindowExtendedBuildTool` for per-placement opt-in.

In `:compat`:
- ~~Real TFC rule sets~~ — **DONE** (see [compat-features.md](compat-features.md) → "TFC default
  substitutions"). Next: the broader MC↔TFC bridging (food/nutrition, requests/progression, animals).
