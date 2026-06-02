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

## 5. GUI (`client.gui` via BlockUI)

- Add an entry point in `WindowExtendedBuildTool` — a button or a section in the existing
  `settingsList` — opening our picker (mixin into `onOpened`/`initSettings`, or a new BlockUI window
  loaded from our XML).
- Build rows by scanning the loaded `Blueprint` (`getBlockInfoAsList()`) for distinct blocks matching
  a candidate rule; for each, a dropdown/list of the `to_tag` members (label by block name + maybe a
  small icon). Show affected-block counts.
- On change: write picks into the active `BlueprintPreviewData` (client) so the preview re-renders
  (our `MixinBlueprintRenderer` consults the override); persist per blueprint for the session.
- On confirm/place: ensure picks are synced before/with `BuildToolPlacementMessage`.

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

## 7. Open questions / to confirm during impl
- Exact `solidSubstitutionOverride` set→sync→apply call path (lock the mechanism before copying it).
- Whether `handleBlockPlacement` (or the surrounding `StructurePlacer`/`IStructureHandler`) exposes the
  placing player or the active `BlueprintPreviewData`.
- BlockUI specifics for a dropdown/scrolling picker and loading addon XML into another mod's window.
- Interaction with the dedicated-server **rule sync** follow-up (preview needs rules client-side; picks
  are client-chosen so they're already client-side, but candidate *tags* must resolve on the client).
