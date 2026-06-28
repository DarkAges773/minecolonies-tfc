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
  - **Affected-blocks tooltip (per row).** Each row hovers a tooltip listing the distinct blueprint blocks a
    swap of that source touches, plus a `(N)` name badge when `N>1`. A material can reach the world through
    more than one blueprint block — the bare block *and* any Domum Ornamentum host carrying it — so the engine
    tracks, per candidate source, its set of **host** blocks (`BlockSubstitutions.collectCandidateSourcesWithHosts`
    / `candidateSourceHosts`: bare block ⇒ itself; DO block ⇒ host of each contained material). Hosts are carried
    as material-aware **`ItemStack`s** (`DomumMaterialRewriter.hostDisplayStack` copies the entry's `textureData`
    onto the stack — exactly where DO's `BlockItem.getName` reads it), so a DO host reports its real name
    (e.g. "Oak Panel") via `stack.getHoverName()` instead of the bare block's unlocalized dynamic descriptionId;
    they're deduped at material granularity (so the same DO block with two material combos is two affected
    blocks, and the badge counts material combos). Contexts expose this via
    `ReplacementChoiceContext.affectedBlocks()` (built from the same blueprint scan that yields `sources()`);
    `WindowReplacements.attachAffectsTooltip` mounts it on the row (rebuilt each `updateRow`, so recycled rows
    never carry a stale tooltip). The host stacks also stand ready to drive per-affected-block **icons** if/when
    a richer hover panel replaces the text-only BlockUI `Tooltip`.
  **Caveats / follow-ups:** the per-placement GUI choice applies to creative-paste placement; per-blueprint
  session memory + row counts are unpolished. **Dedicated-server rule sync — DONE:** rules load server-side
  only, so the server pushes the active ruleset to clients via
  [SyncSubstitutionRulesMessage](../replacements/src/main/java/com/structurizereplacements/network/SyncSubstitutionRulesMessage.java)
  on `OnDatapackSyncEvent` (per player on join, all players after `/reload` — fires after the reload listener,
  so the snapshot is current); the client just `setRules`-es the snapshot, and tag-based matching works because
  vanilla syncs tag contents on the same triggers. Cleared client-side on disconnect
  ([ClientForgeEvents](../replacements/src/main/java/com/structurizereplacements/event/ClientForgeEvents.java))
  so rules never leak across sessions.
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
    so it persists + syncs immediately at placement (not first build). The shared
    [ColonyChoiceResolver](../replacements/src/main/java/com/structurizereplacements/integration/colony/ColonyChoiceResolver.java)
    (registered as the `ChoiceResolver` by
    [ColonyIntegration](../replacements/src/main/java/com/structurizereplacements/integration/colony/ColonyIntegration.java)`#init`,
    called via the fork bridge's `init()` from the guarded `StructurizeReplacements` ctor) resolves the
    building's choices through the [ColonyBridge](../replacements/src/main/java/com/structurizereplacements/integration/colony/ColonyBridge.java):
    **server** via `bridge.buildingAt` (MC: `getColonyByPosFromWorld→getCommonBuildingManager().getBuilding(pos)`)
    (+ adopt-staged fallback); **client** via `bridge.buildingViewAt` (`IColonyManager.getBuildingView(dimension,
    pos)` — the chunk owning-colony cap that `getColonyByPosFromWorld` relies on is NOT reliably synced
    client-side, so the client must use `getBuildingView`).
    The MC mixins live in `structurizereplacements.minecolonies.mixins.json` (`required:false`). Caveat: a
    restart between placement and building creation could lose unadopted staged choices, but adoption is now
    at creation (same tick), so in practice they persist from placement onward. Not yet covered:
    creative-anchor hut placement (`ISpecialCreativeHandlerAnchorBlock.setup`).
  - **The fork-agnostic bridge layer + SlimColonies twin.** All Part-B <i>logic</i> lives once in
    `com.structurizereplacements.integration.colony` (resolver, network channel
    `structurizereplacements:colony` + [SetBuildingChoicesMessage](../replacements/src/main/java/com/structurizereplacements/integration/colony/SetBuildingChoicesMessage.java),
    both GUI choice contexts, the miner settings-list provider, the
    [LevelPaths](../replacements/src/main/java/com/structurizereplacements/integration/colony/LevelPaths.java)
    blueprint-path surgery) — fork-free, classloadable always; buildings/views are passed as `Object` and
    cast to the mixed-in holder interfaces. Each fork contributes exactly TWO duplicated pieces: a
    [ColonyBridge](../replacements/src/main/java/com/structurizereplacements/integration/colony/ColonyBridge.java)
    impl ([MineColoniesBridge](../replacements/src/main/java/com/structurizereplacements/integration/minecolonies/MineColoniesBridge.java) /
    [SlimColoniesBridge](../replacements/src/main/java/com/structurizereplacements/integration/slimcolonies/SlimColoniesBridge.java),
    the only non-mixin classes touching fork types) and its six mixins + `required:false` mixin config
    (mixins can't be shared — each targets one concrete class). **Twin rule: a change to one fork's
    bridge/mixins is mirrored to the other in the same change; new logic goes in `integration.colony`.**
    The [SlimColonies](https://www.curseforge.com/minecraft/mc-mods/slimcolonies) fork (mod id
    `slimcolonies`, repackaged to `no.monopixel.slimcolonies.*`, compiled against
    `curse.maven:slimcolonies-1353551`, compileOnly) inits via `else if (isLoaded("slimcolonies"))` in the
    ctor (single-slot; MineColonies wins if both are somehow present);
    `MixinBlueprintPlacementHandling` stages choices for either fork. The **four fork deltas** (verified
    against 1.20.1-17.4.1 with javap): SlimColonies predates the `ICommonBuilding` split, so its bridge uses
    `IColony#getBuildingManager().getBuilding(pos)` → `IBuilding` (with `markDirty()` directly on it);
    `SettingsModuleWindow` lives in `core.client.gui.modules` (no `.building` subpackage) and its ctor takes
    `(String, IBuildingView, SettingsModuleView)` — the capture inject mirrors all three args; GUI frame
    textures come from the `slimcolonies` asset namespace (`ColonyBridge#assetNamespace`); the work-order
    `read` descriptor names the fork's `IWorkManager` FQN. Everything else (member names, signatures, the
    `serializeToView(buf, fullSync)` arity) is identical to MineColonies. Dev-run selection:
    `-PcolonyMod=minecolonies|slim|none` on `:replacements:runClient`.
  - **Per-building editing in Build Options — DONE & verified.** A bottom-left "Replace" button on
    MineColonies' `WindowBuildBuilding` ([MixinWindowBuildBuilding](../replacements/src/main/java/com/structurizereplacements/mixin/minecolonies/MixinWindowBuildBuilding.java),
    ctor TAIL — note `onOpened` is inherited so can't be targeted; shadows `building` + `updateResources`)
    opens the shared picker scoped to that building. The picker
    ([WindowReplacements](../replacements/src/main/java/com/structurizereplacements/client/gui/WindowReplacements.java))
    is generalized over a [ReplacementChoiceContext](../replacements/src/main/java/com/structurizereplacements/client/gui/ReplacementChoiceContext.java):
    [BuildWandChoiceContext](../replacements/src/main/java/com/structurizereplacements/client/gui/BuildWandChoiceContext.java)
    (global session picks) vs [BuildingChoiceContext](../replacements/src/main/java/com/structurizereplacements/integration/colony/BuildingChoiceContext.java)
    (one building — sources from the building's blueprint loaded via `StructurePacks.getBlueprintFuture`;
    current from the synced view; on pick: optimistic view update + `SetBuildingChoicesMessage`
    ([ColonyNetwork](../replacements/src/main/java/com/structurizereplacements/integration/colony/ColonyNetwork.java),
    a separate shared channel registered only when a colony mod is present) → server sets+`markDirty` (persist + re-sync) →
    `updateResources()` + `clearCache()` refresh). Picks are **per-building only** (don't touch the session
    picks) and apply on the next build/upgrade. "Done" reopens the parent window (build tool / Build Options)
    instead of closing to the game.
- **GUI toggle** in `WindowExtendedBuildTool` for per-placement opt-in.

## Domum Ornamentum material substitution (gotchas that cost a bug each)

[DomumMaterialRewriter](../replacements/src/main/java/com/structurizereplacements/substitution/DomumMaterialRewriter.java)
swaps the material block(s) stored in DO "materialized" blocks' tile NBT. Hard-won specifics:

- **The dynamic timber frame (`domum_ornamentum:plain` & friends) stores its materials in FOUR NBT keys**:
  the base `textureData` compound, its own `originalTextureData` compound, and the `primaryBlock`/
  `secondaryBlock` block-id strings. Its `DynamicTimberFrameBlockEntity.load()` derives the effective
  materials from the latter three and **ignores `textureData`**; `getTextureData()` returns
  `originalTextureData`. Rewriting `textureData` alone is a complete no-op for these blocks (wrong item
  requested, original material placed) — the rewriter rewrites all four, and the world-vs-blueprint
  material compare prefers `originalTextureData` (comparing the possibly-stale base map can disagree
  forever → builder rebuild-loop).
- **SlimColonies' `DoBlockPlacementHandler.doesWorldStateMatchBlueprintState` compares only block STATES**
  (decompiled: `worldState.equals(blueprintState)` — the fork dropped MineColonies'/Structurize's
  `compareBEData` call). Since a DO substitution changes only NBT, the builder's repair scan would skip
  every substituted DO block as "already built". [MixinAbstractBlueprintIterator](../replacements/src/main/java/com/structurizereplacements/mixin/MixinAbstractBlueprintIterator.java)
  therefore verifies the world block's DO materials itself (`DomumMaterialRewriter#materialsMatchWorld`) —
  but **only when the substitution actually changed the blueprint tile tag** (reference inequality from
  `BlockSubstitutions.apply`) **and the loaded fork declares it needs the compensation**
  (`ColonyBridge#placementIgnoresDoMaterials`, true only on `SlimColoniesBridge`) — so core behavior on
  MineColonies and in the standalone case is exactly the pre-workaround code path.
- **SlimColonies' DO placement handler also SKIPS the tile-entity write when the block state was already
  correct in the world** (diagnosed live: placement returned SUCCESS with the substituted tag in hand, but
  `handleTileEntityPlacement` was never invoked — the handler bails when its `setBlockState` is a no-op).
  An NBT-only substitution therefore never reached the world even after the scan fix. The engine now
  self-heals in [MixinStructurePlacer](../replacements/src/main/java/com/structurizereplacements/mixin/MixinStructurePlacer.java)
  (`handleBlockPlacement` RETURN): if a successful DO placement left the world materials unequal to the
  substituted tag, it writes them via DO's own `IMateriallyTexturedBlockEntity#updateTextureDataWith`
  (which also maps the dynamic frame's internal fields) + `sendBlockUpdated`. **Economy:** the re-texture
  is paid — the substituted materialized item must be in the builder's inventory (else the result is
  rewritten to `MISSING_ITEMS` so the builder requests it) and is consumed; the preceding removal refunded
  the old item; creative placements are free. Note the DO material-equality compare prefers
  `originalTextureData` (what the dynamic frame's `getTextureData()` returns) over the possibly-stale base
  `textureData` — comparing the base map can disagree forever and rebuild-loop.
- **`StructurePlacer#getResourceRequirements` calls the static match ITSELF** (separately from the
  iterator scan that `MixinAbstractBlueprintIterator` covers), building its BlockInfo from the RAW
  blueprint tag, and returns "needs nothing" for positions it deems already built. Missing that call site
  cost a debugging marathon — and it bites on **every fork, vanilla MineColonies included** (verified
  in-game on both): the comparator may be perfect, but raw-blueprint-vs-world compares equal for an
  NBT-only substitution, so the substituted frame item never entered the builder's material list → the
  colony AI's `hasListOfResInInvOrRequest` consistency check (item not in inventory NOR in the building's
  resource bucket → `RECALC`) looped `LOAD_STRUCTURE→START_BUILDING` forever. Both call sites now route
  through the shared
  [SubstitutedMatch](../replacements/src/main/java/com/structurizereplacements/placement/SubstitutedMatch.java)
  — the substitution-awareness is the fork-agnostic core fix; only the direct DO-material verification
  inside it is SlimColonies-gated.
  The paid enforcement (above) charges exactly the items the colony mod's own placement handler computes
  (`PlacementHandlers.getHandler(...).getRequiredItems(...)` on the substituted tag), so the demanded stack
  identity always agrees with what the request scan put into the material list.
- The colony mods register their **own** DO placement handlers that take priority over Structurize's —
  when debugging DO placement/match behavior, read `<fork>.core.placementhandlers.DoBlockPlacementHandler`,
  not Structurize's.
- **Red herring (cost a detour): SlimColonies' builder *scavenging***
  (`builderscavengingintervalminutes` in `slimcolonies-server.toml`, default 2, 0 = off) makes idle
  builders periodically "find" 1–5 of their needed materials — which looks exactly like substitution
  conjuring items out of thin air / requests partially filling themselves. It applies to all materials and
  has nothing to do with this mod. The refund economy itself is sound: item matching for DO frames proved
  NBT-strict in testing, so refunded old-texture frames do NOT satisfy new-texture requirements.

## Palette presets (named, reusable pick sets)

A **preset** is a named `Map<Block,Block>` of picks. Because the engine keys overrides by the *datapack-converted*
block (`applyState` looks up `overrides.get(datapackTarget(source))`) — the same key the GUI rows already store — a
preset is **not tied to one blueprint**: loading it substitutes whatever sources it recognises and ignores the
rest, so one preset re-palettes any building or a whole colony. No engine change was needed; presets ride the
existing per-context `choose()` apply path and both sync channels.

- **Folders + icons.** Presets carry a `folder` (the user library's subdirectory; a built-in's datapack subpath
  under `block_substitution_presets/`) and an optional `icon` block (explicit, else derived from the first pick).
  [WindowPresetList](../replacements/src/main/java/com/structurizereplacements/client/gui/WindowPresetList.java) is
  navigable — it tracks the current folder, lists subfolders (drill-in) + an "Up" row + the presets in that folder,
  each with its icon; **Save** lands in the current folder (a `folder/name` name nests further), **Clone** copies
  into the **library root** (the player's own space, never the read-only folder). The `:compat` presets ship under
  **`builtin/`** (`rocks`, `planks`, `logs`, `dirt` subfolders) so the read-only built-ins stay grouped and out of
  the root.
- **Two sources, merged in the picker** ([WindowPresetList](../replacements/src/main/java/com/structurizereplacements/client/gui/WindowPresetList.java)):
  - **User library** — editable, client-only JSON under `config/structurizereplacements/presets/` (one file per
    preset), CRUD in [PresetLibrary](../replacements/src/main/java/com/structurizereplacements/client/preset/PresetLibrary.java).
    Lives in the client config so it travels with the player across worlds/servers (never touches the server; a
    preset only reaches the world when *applied*, through the already-permission-checked choice channels).
  - **Built-in** — read-only, shipped as datapack data (`data/<ns>/block_substitution_presets/*.json`), loaded by
    [BuiltinPresetReloadListener](../replacements/src/main/java/com/structurizereplacements/preset/BuiltinPresetReloadListener.java)
    (mirrors the rule listener; a pick whose block id is absent is skipped, a preset left empty is dropped — so a
    TFC preset never shows up without TFC) and synced server→client by
    [SyncBuiltinPresetsMessage](../replacements/src/main/java/com/structurizereplacements/network/SyncBuiltinPresetsMessage.java)
    on the **same** join/`/reload` trigger as the rule sync (memory-connection-guarded like it). Stored in
    [BuiltinPresets](../replacements/src/main/java/com/structurizereplacements/preset/BuiltinPresets.java).
- **GUI.** A **Presets** button on [WindowReplacements](../replacements/src/main/java/com/structurizereplacements/client/gui/WindowReplacements.java)
  (offered for the build-wand and building contexts, hidden inside the editor) opens the hub: **Save current**
  (snapshot the originating context's picks → new library preset), **Load** (merge a preset's picks into the
  originating context, then return — the picker re-reads on open), **Edit** (library presets → reopen
  `WindowReplacements` on a [PresetEditChoiceContext](../replacements/src/main/java/com/structurizereplacements/client/gui/PresetEditChoiceContext.java)
  whose rows *are* the preset's picks; retarget reuses the candidate picker, and a per-row **delete** drops a
  pick, persisting straight to the library JSON), **Clone** (built-in → editable copy), **Delete** (library only).
  The per-row delete button is gated on `ReplacementChoiceContext.allowRowDelete()` (only the editor); the hub
  button on `offersPresetMenu()`.
- Built-in presets ship in `:compat` (per-TFC-rock-type stone presets — see
  [compat-features.md](compat-features.md)); `:replacements` ships the mechanism only.

In `:compat`:
- ~~Real TFC rule sets~~ — **DONE** (see [compat-features.md](compat-features.md) → "TFC default
  substitutions"). Next: the broader MC↔TFC bridging (food/nutrition, requests/progression, animals).
