# Porting plan: 1.20.1-Forge + 1.21.1-NeoForge

## Decision

Support both targets via **separate branches**, not a shared-source monorepo:
- `1.20.1-forge` — the current default branch (ForgeGradle, `mods.toml`, Java 17).
- `1.21.1-neoforge` — a new branch (NeoForge tooling, `neoforge.mods.toml`, Java 21).

Rationale: the gap is two *different* MC versions **and** a loader change, and both mods are **mixin-heavy
against third-party internals** (Structurize / MineColonies / TFC) whose signatures differ per MC version —
so a "common" module would be thin and most code diverges anyway. Branches keep each side clean; port
forward and cherry-pick fixes across. Datapack JSON / lang copy across with minor id edits.

**Order:** scout deps → port `:replacements` (the standalone engine) → port `:compat` (`mctfc`) later
(`:compat` is the heavier port — TFC food *capabilities* become *data components* in 1.20.5+).

## Scout results — dependency availability (LDTTeam maven, verified)

All `:replacements` deps have 1.21.1-NeoForge artifacts:

| artifact (groupId `com.ldtteam`) | 1.21.1 version | note |
|---|---|---|
| `structurize` | `1.21.1-1.0.746-beta` | scheme `<mcver>-<modver>` |
| `blockui` | `1.21.1-1.0.182-beta` | scheme `<mcver>-<modver>` |
| `domum-ornamentum` | `1.21.1-1.0.200-BETA` | **artifactId renamed** from `domum_ornamentum` (underscore) → `domum-ornamentum` (hyphen) |
| `minecolonies` | **stable** via CurseForge: `curse.maven:minecolonies-245506:8138370` | LDTTeam maven also has `<modver>-<mcver>` snapshots (e.g. `1.1.1330-1.21.1-snapshot`), but the CurseForge build is a stable 1.21.1 release |

Gotchas found while scouting:
- **Two reversed version schemes** on the LDTTeam maven: structurize/blockui put the MC version *first*;
  minecolonies/domum put the mod version *first*. Grep accordingly when bumping.
- **MineColonies has a stable 1.21.1 build** on CurseForge (`curse.maven:minecolonies-245506:8138370`), so the
  optional MC integration can be ported **in full alongside the core** — no need to wait on a snapshot. (Pull
  it via CurseMaven, the repo already used for TFC/Patchouli.)
- `:compat` requires TFC + Patchouli on 1.21.1-NeoForge — **not yet scouted** (do before porting `:compat`).

## Mixin-target scout — Structurize `1.21.1-1.0.746-beta` (done, via `javap`)

All target **classes** still exist at the same package paths. Method-level findings:

**Ports as-is** (present, compatible — loader-type swaps aside): `util.BlockInfo` (unchanged record
`(BlockPos, BlockState, CompoundTag)`), `AbstractBlueprintIterator#iterateWithCondition` (private; its
`TriPredicate` is now `net.neoforged.neoforge.common.util.TriPredicate`), `BlueprintBlockAccess#getBlockState`,
`BlueprintRenderer#init` (body redirects to re-verify), `AbstractStructureHandler` (`getWorld`/`getWorldPos`/
`getBluePrint` present), `AbstractBlueprintManipulationWindow` (ctor `(String, BlockPos, int, String)`),
`BlueprintPlacementHandling#process(Blueprint, BuildToolPlacementMessage)` + `BuildToolPlacementMessage.pos`
(still a public `BlockPos`, so `StagedChoices` keying works), and the `PlacementHandlers$GeneralBlockPlacementHandler`
/`$BlockGrassPathPlacementHandler` inner classes.

**Needs rework** (real API changes):
- **CORE — `StructurePlacer#handleBlockPlacement` / `#getResourceRequirements`:** the `BlockInfo` arg is
  **decomposed** into separate `BlockState` + `CompoundTag` params (plus an extra `BlockPos`). New sigs:
  `handleBlockPlacement(Level, BlockPos, BlockPos, ChangeStorage, BlockState, CompoundTag)` and
  `getResourceRequirements(Level, BlockPos, BlockPos, BlockState, CompoundTag)`. Our two `@ModifyVariable`
  hooks that rewrite the single `BlockInfo` arg become a `BlockState` rewrite (+ a `CompoundTag` rewrite for
  the DO material NBT) — the engine already separates `applyState(state)` from `DomumMaterialRewriter.rewrite(block, tag)`,
  so the logic ports; only the injection points change. This is the main rework.
- **`PlacementHandlers.add`:** the 3-arg `add(handler, Class, AddType)` is gone — now `add(IPlacementHandler, Class<?>)`
  and `add(IPlacementHandler)`. Update the `TwoTallPlantPlacementHandler` registration (and `:compat`'s
  `TfcSoilPlacementHandler`) to the 2-arg form (confirm its before/after semantics).
- **`BlueprintUtils#instantiateTileEntities`:** now `(Blueprint, Level, Map<BlockPos, ModelData>)` (extra
  NeoForge `ModelData` map); `constructTileEntity` now threads a `HolderLookup.Provider`. The preview
  `@Redirect` of the `instantiateTileEntities` call updates to the new descriptor.

**Verdict: no blockers** for the `:replacements` standalone core — the substitution engine, GUI, placement
handlers and datapack all port; the work is re-pointing a handful of mixins (chiefly the `handleBlockPlacement`
decomposition) and the Forge→NeoForge plumbing below.

## Mixin-target scout — MineColonies 1.21.1 stable (done, via `javap`; `curse.maven:minecolonies-245506:8138370`)

All MC-integration target classes present. Method-level findings:

**Ports as-is** (same signatures): `RegisteredStructureManager#addNewBuilding(AbstractTileEntityColonyBuilding, Level)`;
`AbstractWorkOrder#onAdded(IColony, boolean)` / `#write(CompoundTag)` / `#read(CompoundTag, IWorkManager)` /
`#getLocation()`; and — importantly — **`WindowBuildBuilding`** keeps its ctor `(IColonyView, IBuildingView)`,
`canBeUpgraded()`, private `updateResources()`, and the private fields `building`/`styles`/`stylesDropDownList`,
so the per-building picker **and the new Current/Upgrade palette toggle** (which shadows those fields) port cleanly.

**Needs rework** (the 1.21 registry/buffer changes, not structural):
- **`AbstractBuilding#serializeNBT`/`#deserializeNBT`** now thread a `net.minecraft.core.HolderLookup$Provider`
  (`serializeNBT(Provider)`, `deserializeNBT(Provider, CompoundTag)`) — update the `MixinAbstractBuilding` hook sigs.
- **`AbstractBuilding#serializeToView`** is now `(RegistryFriendlyByteBuf, boolean)`, and the view read is
  `IBuildingView#deserialize(RegistryFriendlyByteBuf)` (was `FriendlyByteBuf`). Update the choice-trailer
  hooks and [ChoiceCodec](../replacements/src/main/java/com/structurizereplacements/placement/ChoiceCodec.java)
  to `RegistryFriendlyByteBuf`.

**Verdict: no blockers** for the optional MC integration either — it's the same `RegistryFriendlyByteBuf` /
`HolderLookup.Provider` migration as the rest of the 1.21 port.

## Still to scout (before `:compat`)

- **TFC + Patchouli** on 1.21.1-NeoForge (for `:compat`), and the `:compat` mixin targets (TFC food caps →
  data components is the big one).
- Confirm NeoForge build tooling: **ModDevGradle (MDG)** is the modern plugin (recommended); NeoGradle is older.
  Java **21** toolchain. NeoForge mixins via MDG's mixin support.

## Migration hazards (Forge 1.20.1 → NeoForge 1.21.1), `:replacements`

- **Loader plumbing:** `mods.toml` → `META-INF/neoforge.mods.toml` (slightly different schema; `logoFile`
  still works). `net.minecraftforge.*` → `net.neoforged.*` (+ NeoForge's `bus`/event model; `@Mod` ctor
  takes the event bus / `IEventBus`).
- **Registries:** `RegistryObject` → `DeferredHolder`; `DeferredRegister` API tweaks.
- **`ResourceLocation`:** constructor removed → `ResourceLocation.fromNamespaceAndPath(...)` / `parse(...)`
  (we already hit the deprecation warnings on 1.20.1; on 1.21.1 it's mandatory).
- **Networking:** Forge `SimpleChannel` is gone → NeoForge's payload-based registrar
  (`RegisterPayloadHandlersEvent` + `CustomPacketPayload`). Rewrite [Network](../replacements/src/main/java/com/structurizereplacements/network/Network.java)
  and the MC-integration [McNetwork](../replacements/src/main/java/com/structurizereplacements/integration/minecolonies/McNetwork.java).
- **Buffers:** `FriendlyByteBuf` → `RegistryFriendlyByteBuf` for registry-aware payloads; affects
  [ChoiceCodec](../replacements/src/main/java/com/structurizereplacements/placement/ChoiceCodec.java).
- **Data pack format:** the opt-in default pack's `pack.mcmeta` `pack_format` 15 → the 1.21.1 value (48).
- **Mixins still work** (SpongePowered), but registered via MDG + neoforge.mods.toml; keep the two-config
  split (`required:false` for the MC-integration config). The `remap=false`-on-other-mods'-members rule is
  unchanged.
- **Config** (`ModConfigSpec`) namespace/API moved under NeoForge; light edits.
- Mostly portable as-is: the substitution engine logic, the datapack JSON rules, lang, the GUI XML.

## Branch workflow

- Bug fixes / features that aren't version-specific: apply to both branches (the user cherry-picks).
- The rolling-patch versioning (per-subproject commit count) works per branch independently; the
  `1.21.1-neoforge` branch's jars are `1.21.1-<MAJOR.MINOR.patch>` automatically (the `minecraft_version`
  property drives the prefix).
- Keep file layout parallel between branches so cherry-picks apply cleanly.
