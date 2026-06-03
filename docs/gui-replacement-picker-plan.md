# Plan: GUI per-placement replacement picker (`:replacements`)

Status: **designed, not yet implemented.** This is the detailed plan for the build-tool GUI that lets
players choose, per placement, what each source block is replaced with — from a candidate pool defined
by datapack rules. Summary lives in [../CLAUDE.md](../CLAUDE.md); this is the full record.

## 1. Goal & decided UX

Today substitution is deterministic (a rule maps a source to one fixed target). We add **candidate
rules** whose target is a *set*, and let the player pick per placement.

- **Rule model:** add `to_tag` (a target block tag = the candidate menu) alongside the existing fixed
  `to` (single block). Fixed rules keep auto-applying; **candidate rules are interactive** — they
  substitute nothing until the player makes a pick.
- **Granularity:** one picker per **distinct source block** present in the schematic that matches a
  candidate rule. Options = that rule's `to_tag` members.
- **Explicit:** no silent swap for candidate rules. Unset = unchanged.
- **Scope:** picks are remembered **per blueprint for the session** (a Reset clears them).
- **Cascade:** a pick that is **same-namespace** still fans out across forms via the existing
  family/shape logic (`oak_planks→spruce_planks` ⇒ `oak_stairs→spruce_stairs`). **Cross-namespace
  (vanilla→TFC) does NOT infer forms** — only forms that have their own candidate rule are
  substituted. (This deliberately avoids identifying "material" across TFC's path-style, wood/rock-mixed
  tags — the fragile problem we rejected.)

Example: datapack has `#minecraft:planks → #tfc:planks`. A schematic with `oak_planks` and
`spruce_planks` shows two pickers (oak, spruce), each listing `#tfc:planks` members. Player picks a
TFC plank for each; nothing else changes unless more candidate rules exist for other forms.

## 2. Architecture — mirror Structurize's `solidSubstitutionOverride`

Structurize already carries a player-chosen, per-placement block to the server. We copy that pattern.

- **`BlueprintPreviewData`** (`storage/rendering/types/`) is the per-placement state object. It is
  network-serializable (`writeToBuf(FriendlyByteBuf)` + `FriendlyByteBuf` constructor) and already
  holds `BlockState solidSubstitutionOverride` and `RenderingCacheKey renderKey`.
- Lifecycle of `solidSubstitutionOverride` (our template, to confirm in detail during impl):
  1. Set client-side from the build-tool GUI into the active `BlueprintPreviewData` (held in
     `storage/rendering/RenderingCache`).
  2. Synced to the server via **`SyncPreviewCacheToServer`** (server side: `ServerPreviewDistributor`).
  3. Applied at placement, driven by **`BuildToolPlacementMessage`** (server re-loads the blueprint
     by pack/path and places, consulting the synced preview data).

We attach our **choice map** to that same per-placement state and apply it through the substitution
engine's new override layer.

### Key Structurize references (from recon, structurize 1.20.1-1.0.816)
- Build-tool window: `client.gui.WindowExtendedBuildTool` extends
  `client.gui.AbstractBlueprintManipulationWindow` (BlockUI). Relevant members:
  `onOpened()`, `initSettings()`, `settingsList` / `placementOptionsList` (`blockui ScrollingList`),
  `confirmClicked()` → `handlePlacement(BuildToolPlacementMessage.HandlerType, String)`,
  `onButtonClicked(Button)`.
- Placement message: `network.messages.BuildToolPlacementMessage` (fields: HandlerType, pack/path
  strings, BlockPos, Rotation, Mirror). Does NOT itself carry `BlueprintPreviewData`.
- Preview sync: `network.messages.SyncPreviewCacheToServer` / `SyncPreviewCacheToClient`;
  `storage.rendering.RenderingCache`, `storage.rendering.ServerPreviewDistributor`.
- Render path (already mixed for preview): `client.BlueprintRenderer#init` reads
  `Blueprint#getBlockInfoAsList()` → `BlockInfo.getState()`.
- Server placement (already mixed): `placement.StructurePlacer#handleBlockPlacement(Level, BlockPos,
  ChangeStorage, BlockInfo)`.

## 3. Engine changes (`com.structurizereplacements.substitution`)

- **`SubstitutionRule` / loader:** support `to_tag` (resolve a `TagKey<Block>` as the candidate set).
  A candidate rule contributes NO automatic mapping; it registers a "choice point": `(source matcher,
  candidate tag)`. Keep fixed `to` rules and family cascade exactly as now.
- **Per-placement override layer:** a `ReplacementChoices` value = `Map<Block, Block>` (source block →
  chosen target). Resolution precedence in `BlockSubstitutions`:
  1. per-placement override (exact source block → chosen target),
  2. same-namespace family cascade derived from override picks,
  3. existing datapack fixed rules + their family cascades.
  Candidate rules with no override pick = no-op.
- `applyState`/`apply` gain an overload taking the active `ReplacementChoices` (or read an
  ambient/threaded one). The override must be reachable from both the client preview mixins and the
  server `handleBlockPlacement`.

## 4. Getting choices to the server at placement — SOLVED (Phase 1 proven)

The placing player is NOT reachable from `handleBlockPlacement` directly (the `IStructureHandler`
only exposes `getWorld()`). But the build-tool server path is:
`BuildToolPlacementMessage.onExecute` (has `ServerPlayer`) → `BlueprintPlacementHandling.handlePlacement`
→ async blueprint load → `process(blueprint, msg)` → `new CreativeStructureHandler(...)` →
`new StructurePlacer(handler)` → **`new PlaceStructureOperation(structurePlacer, player)`** →
`Manager.addToQueue(...)` (ticked). The **`PlaceStructureOperation` constructor ties the placer to the
player** — that's the hook.

**Mechanism used (a refinement of B):** attach the choices to the `StructurePlacer` *instance*.
- [PlacementChoiceHolder](../replacements/src/main/java/com/structurizereplacements/placement/PlacementChoiceHolder.java)
  — interface mixed into `StructurePlacer` (a `@Unique Map<Block,Block>` field + accessors in
  [MixinStructurePlacer](../replacements/src/main/java/com/structurizereplacements/mixin/MixinStructurePlacer.java)).
- [MixinPlaceStructureOperation](../replacements/src/main/java/com/structurizereplacements/mixin/MixinPlaceStructureOperation.java)
  injects the constructor TAIL and calls `((PlacementChoiceHolder)(Object)placer).setReplacementChoices(PlacementChoices.forPlayer(player))`.
- `handleBlockPlacement` reads the choices off `this` (the placer) — survives the ticked placement, no
  ThreadLocal / player lookup needed.
- [PlacementChoices](../replacements/src/main/java/com/structurizereplacements/placement/PlacementChoices.java)
  is the source: **currently HARD-CODED** (`oak_planks → dark_oak_planks`) for both `forPlayer` (server)
  and `client` (preview). **Phase 2 replaces this** with a per-player server store populated by a sync
  packet, and a client per-blueprint store fed by the GUI.

Verified in-world (single-player, creative, Structurize build tool): oak planks → dark oak (override),
oak stairs → spruce (datapack family rule) — both layers coexist, preview matches placement.

Caveat found: this covers the **creative paste** path (`PlaceStructureOperation`). MineColonies' own
survival/builder placement uses `ISurvivalBlueprintHandler` and would NOT go through this operation —
out of scope for now; revisit if builder-driven substitution is wanted.

## 5. GUI (`client.gui` via BlockUI) — recon done

Building blocks found:
- **Reuse `WindowSelectRes`** instead of writing a picker: public ctor
  `WindowSelectRes(BOWindow origin, Component title, ItemStack current, List<ItemStack> pool,
  BiConsumer<ItemStack,Integer> onSelect)` — a searchable list with icons. Pass our candidate-tag
  members as the `pool`; `onSelect` records the choice. (`WindowReplaceBlock` is exactly this pattern.)
- Loaded blueprint client-side: `RenderingCache.getBlueprintsToRender()` →
  `BlueprintPreviewData.getBlueprint()` → `Blueprint.getBlockInfoAsList()` for distinct blocks.
- Candidate lookup: `BlockSubstitutions.candidateFor(Block)` → `CandidateRule.toTag()`; resolve members
  via `ForgeRegistries.BLOCKS.tags().getTag(toTag)`.
- Window base: `AbstractWindowSkeleton` (Structurize) with `registerButton(id, Runnable)`; BlockUI
  `View.addChild(Pane)` / `findPaneOfTypeByID`. Open with `BOWindow.open()`.
- Build-tool buttons are registered via `registerButton(...)` in `onOpened`; add ours by mixing
  `WindowExtendedBuildTool.onOpened` (create + `addChild` a Button, `registerButton` its handler).

Apply/refresh:
- On pick → `ClientPlacementChoices.set(map)` (already syncs to server). Preview refresh: the render
  cache is keyed by `RenderingCacheKey`; need to invalidate/re-queue so `MixinBlueprintRenderer`
  re-bakes with the new choice (TODO: find the refresh call — `RenderingCache.queue(...)` or dirtying
  the preview data).

### STATUS: Phase 2 functionally COMPLETE (verified in single-player)

All slices done and verified in-world: candidate rules parse, the build-tool button opens the
multi-row picker window, each row opens the reused `WindowSelectRes` (candidate-tag pool), picks sync
client→server and apply at placement, and the preview re-bakes live on each pick. Key pieces:
- Engine: [CandidateRule](../replacements/src/main/java/com/structurizereplacements/substitution/CandidateRule.java),
  `BlockSubstitutions.candidateFor` + the override layer in `applyState(state, overrides)`.
- Choices: [ClientPlacementChoices](../replacements/src/main/java/com/structurizereplacements/placement/ClientPlacementChoices.java)
  / [ServerPlacementChoices](../replacements/src/main/java/com/structurizereplacements/placement/ServerPlacementChoices.java)
  + [Network](../replacements/src/main/java/com/structurizereplacements/network/Network.java) sync.
- GUI: [WindowReplacements](../replacements/src/main/java/com/structurizereplacements/client/gui/WindowReplacements.java)
  (+ `gui/windowreplacements.xml`), button via
  [MixinAbstractBlueprintManipulationWindow](../replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractBlueprintManipulationWindow.java).
- Live preview refresh: `BlueprintHandler.getInstance().clearCache()` after a pick.
- Labels localized via existing translations (Structurize `gui.scan.replace.title`/`scantool.replace`/
  `scantool.select`; vanilla `gui.done`).

**Remaining (optional / follow-up):**
- Per-blueprint session memory (today `ClientPlacementChoices` is one global map; picks could bleed
  across blueprints sharing block types).
- Row polish: affected-block counts, nicer empty state.
- **Dedicated-server rule sync** — candidate rules (and datapack rules generally) load server-side
  only, so on a dedicated server the client GUI has no candidate rules to show and the preview can't
  substitute. Single-player works (shared JVM). Syncing the loaded ruleset to clients fixes both this
  and the earlier preview caveat.

### 2C build slices (historical)
1. Add a candidate rule to the example datapack so the picker has something to show
   (vanilla-only: `{ "from_tag": "minecraft:planks", "to_tag": "minecraft:planks" }`).
2. Entry point + picker: open `WindowSelectRes(pool = candidate members)` for a source block; `onSelect`
   updates `ClientPlacementChoices`. Remove `DebugChoiceSeed`.
3. Multi-row list window (one row per distinct matching source block) — the full decided UX — using a
   `ScrollingList`; each row's "change" button opens the `WindowSelectRes` picker.
4. Preview refresh on pick; per-blueprint session memory; counts/labels.

## 6. Phased implementation

- **Phase 1 — plumbing (no UI). ✅ DONE.** Engine override layer (`BlockSubstitutions.applyState(state,
  overrides)`) consulted before datapack rules; choices attached to the placer (see §4); preview mixins
  consult `PlacementChoices.client()`. Verified in-world. *(`to_tag` rule parsing was deferred to Phase
  2, where the GUI defines the candidate pools — not needed to prove the plumbing.)*
- **Phase 2 — GUI.** The picker window/section, populated from candidate rules + the loaded blueprint;
  writes picks into the per-placement state; live preview update; session persistence; Reset.
- **Phase 3 — polish.** Counts/labels/icons, dedicated-server rule availability (depends on the
  separate rule-sync follow-up), edge cases (rotation/mirror interplay, multiple candidate rules per
  block).

## 8. Builder follows GUI choices (per-building substitutions)

Goal: when a player places a **hut** with GUI substitution picks, the **builder** builds it with those
picks (not just datapack rules), and the picks persist for upgrades/rebuilds.

Key recon: builder/quarrier use Structurize's `StructurePlacer` + a `BuildingStructureHandler` (created
in `AbstractEntityAIStructure.loadStructure(IBuilderWorkOrder, …)`). Both `CreativeStructureHandler`
(build tool) and `BuildingStructureHandler` (builder) extend `AbstractStructureHandler`, and the
`AbstractBlueprintIterator` holds the same handler — so the **handler is the shared place to store the
choice map** for all three phases (place / request / match).

### Part A — DONE (handler refactor, behavior-neutral, verified loads)
Choice map moved from `StructurePlacer` to the handler:
[MixinAbstractStructureHandler](../replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractStructureHandler.java)
implements `PlacementChoiceHolder` on `AbstractStructureHandler`; `MixinStructurePlacer`
(place + `getResourceRequirements`) and `MixinAbstractBlueprintIterator` (match) read
`handler.getReplacementChoices()`; `MixinPlaceStructureOperation` attaches the player's choices to
`placer.getHandler()`. Builder handler choices are still null → datapack rules (until Part B).

### Part B — TODO. **Lives in `:compat`, NOT `:replacements`** (it targets MineColonies classes; keep
the standalone mod MineColonies-free). `:compat` already compiles against MineColonies and depends on
`project(':replacements')`, so it reuses `PlacementChoiceHolder` + `ServerPlacementChoices`. `:compat`
currently has no real mixins → add a `mctfc.mixins.json` + the mixin AP to its build.

Decision: **persist on the building** (`AbstractBuilding` colony NBT) — survives restarts, outlives a
single work order (upgrades/rebuilds). **DONE & verified in-game.** As-built design (differs from the
original sketch — see the dead end below):
- `MixinAbstractBuilding` — @Unique field + `implements PlacementChoiceHolder` + serialize/deserialize the
  `Map<Block,Block>` under key `mctfc_choices` in `serializeNBT()/deserializeNBT(CompoundTag)` (colony NBT,
  **not** the hut block-entity NBT).
- **Capture:** `MixinBlueprintPlacementHandling#process` (HEAD) — the single server entry for build-tool
  placement (survival build / delegate-to-builder / creative-anchor / paste). Stage the placing player's
  choices (`ServerPlacementChoices.forPlayer(msg.player)`) in `StagedChoices`, keyed by `msg.pos`. (The
  building is created asynchronously, so we can't write it directly here.)
- **Adopt at creation:** `MixinRegisteredStructureManager#addNewBuilding` (RETURN) takes the staged
  choices for the new building's position and sets them on the building (+ `markDirty`), so they persist
  and sync to the client view immediately at placement (not first build).
- **Apply:** a pluggable `ChoiceResolver` (in `:replacements`) that `AbstractStructureHandler`'s getter
  calls lazily on **both** sides. `:compat` registers `BuildingChoiceResolver`: **server** looks up the
  building at the handler's `worldPos` (adopt-staged fallback) and returns its choices; **client** uses
  `IColonyManager.getBuildingView(dim, pos)` (the chunk owning-colony cap is not reliably synced
  client-side). Covers BOTH builder handlers (`WorkerLoadOnlyStructureHandler` for the material request,
  `BuildingStructureHandler` for placement) and the client Build Options list/preview with one hook.
- **Sync + edit:** the building's choice map is appended to its view buffer
  (`MixinAbstractBuilding#serializeToView` ↔ `MixinAbstractBuildingView#deserialize`, shared `ChoiceCodec`),
  and a "Replace" button on `WindowBuildBuilding` opens the generalized `WindowReplacements`
  (`ReplacementChoiceContext`: `BuildWandChoiceContext` vs `BuildingChoiceContext`) to edit per-building
  choices, persisted via `SetBuildingChoicesMessage` on `:compat`'s own network channel.

**Dead end (do not retry):** the original plan was to copy choices onto the handler in
`BuildingStructureHandler#<init>` / a shared `AbstractStructureHandler#<init>` inject. `@Inject(method="<init>")`
into these Structurize handlers **silently never fires** (the mixin applies and the `PlacementChoiceHolder`
interface works, but no ctor callback runs — no error, just zero invocations). Resolution was moved into the
**getter** (`MixinAbstractStructureHandler#getReplacementChoices`, lazy + cached) instead — see CLAUDE.md
Part A. The client "Build Options" material-list preview is handled by the same getter defaulting to
`ClientPlacementChoices` on `isClientSide`.

Caveat: a server restart between placement and the *first* build loses the staged (not-yet-adopted)
choices → that build falls back to datapack rules (acceptable; once adopted onto the building it
persists). Creative-anchor hut placement (`ISpecialCreativeHandlerAnchorBlock.setup`) = later.

## 7. Open questions / to confirm during impl
- Exact `solidSubstitutionOverride` set→sync→apply call path (lock the mechanism before copying it).
- Whether `handleBlockPlacement` (or the surrounding `StructurePlacer`/`IStructureHandler`) exposes the
  placing player or the active `BlueprintPreviewData`.
- BlockUI specifics for a dropdown/scrolling picker and loading addon XML into another mod's window.
- Interaction with the dedicated-server **rule sync** follow-up (preview needs rules client-side; picks
  are client-chosen so they're already client-side, but candidate *tags* must resolve on the client).
