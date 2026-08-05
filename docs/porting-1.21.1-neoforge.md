# Porting plan: 1.20.1-Forge + 1.21.1-NeoForge

> **Status (2026-08-05): `:replacements` ports clean and loads.** It compiles and jars on NeoForge 1.21.1
> (`structurizereplacements-1.21.1-0.4.68.jar`), and `gradlew :replacements:runClient` boots to the main menu
> with **zero mixin-apply failures**, MineColonies integration active. What's done and what's left is in
> [§ Port status](#port-status) at the bottom — read that first; the scout sections below are kept for
> context and are **annotated where the re-scout contradicted them**.

## Decision

Support both targets via **separate branches**, not a shared-source monorepo:
- `main` — the 1.20.1-Forge branch (ForgeGradle, `mods.toml`, Java 17).
- `neoforge-1.21.1` — this branch (ModDevGradle, `neoforge.mods.toml`, Java 21).

Rationale: the gap is two *different* MC versions **and** a loader change, and both mods are **mixin-heavy
against third-party internals** (Structurize / MineColonies / TFC) whose signatures differ per MC version —
so a "common" module would be thin and most code diverges anyway. Branches keep each side clean; port
forward and cherry-pick fixes across. Datapack JSON / lang copy across with minor id edits.

**Order:** scout deps → port `:replacements` (the standalone engine) → port `:compat` (`mctfc`) later
(`:compat` is the heavier port — TFC food *capabilities* become *data components* in 1.20.5+).

## Scout results — dependency availability (LDTTeam maven)

> ⚠️ **SUPERSEDED — the versions in the table below are wrong.** The original scout picked the
> `<mcver>-<modver>` "beta" line (`structurize 1.21.1-1.0.746-beta`, `blockui 1.21.1-1.0.182-beta`). Re-scouting
> at port time showed LDTTeam **abandoned that scheme**: current 1.21.1 development lives under the *reversed*
> `<modver>-<mcver>` scheme, ~90 versions further on, and MineColonies 1.21.1 declares **those** as its deps.
> The pinned-and-building set is in [§ Port status](#port-status); the table stays only to explain the two schemes.

| artifact (groupId `com.ldtteam`) | originally scouted (stale line) | note |
|---|---|---|
| `structurize` | `1.21.1-1.0.746-beta` | scheme `<mcver>-<modver>` — dead end; use `1.0.830-1.21.1` |
| `blockui` | `1.21.1-1.0.182-beta` | scheme `<mcver>-<modver>` — dead end; use `1.0.209-1.21.1` |
| `domum-ornamentum` | `1.21.1-1.0.200-BETA` | **still correct** — DO has no release under the new scheme yet. **artifactId renamed** from `domum_ornamentum` (underscore) → `domum-ornamentum` (hyphen) |
| `minecolonies` | CurseForge `curse.maven:minecolonies-245506:8138370` | unnecessary — `1.1.1368-1.21.1` is a plain **release** on the LDTTeam maven, so CurseMaven isn't needed at all |

Gotchas found while scouting:
- **Two reversed version schemes** on the LDTTeam maven, and *which* an artifact uses is not a per-artifact
  fact but a per-*era* one: everything moved to `<modver>-<mcver>` for 1.21.1. When bumping, list both and
  take the newest non-snapshot.
- **MineColonies has a stable 1.21.1 build** (`1.1.1368-1.21.1`), so the optional MC integration ports **in
  full alongside the core** — no need to wait on a snapshot.
- **SlimColonies has NO 1.21.1 build** — every published CurseForge file is 1.20.1. The fork twin is therefore
  **parked** on this branch (see [§ Port status](#port-status)).
- `:compat` requires TFC + Patchouli on 1.21.1-NeoForge — **not yet scouted** (do before porting `:compat`).

## Mixin-target scout — Structurize `1.21.1-1.0.746-beta` (SUPERSEDED)

> ⚠️ **Scouted against the abandoned beta line — its two headline "needs rework" items are WRONG for the
> line we actually build against (`1.0.830-1.21.1`).** Verified with `javap` at port time:
> - `StructurePlacer#handleBlockPlacement` is **still** `(Level, BlockPos, ChangeStorage, BlockInfo)` — the
>   `BlockInfo` is *not* decomposed, so both `@ModifyVariable` hooks and the `@Inject` port **unchanged**.
>   (`getResourceRequirements` *is* `(Level, BlockPos, BlockPos, BlockState, CompoundTag)`, but the existing
>   `argsOnly` `BlockState` hook still resolves.) `BlockInfo` is the same record.
> - `PlacementHandlers.add(handler, Class, AddType)` — the 3-arg form **still exists**; no change needed.
> - `IPlacementHandler#doesWorldStateMatchBlueprintState(BlockInfo, BlockPos, IStructureHandler)` and
>   `#getRequiredItems(Level, BlockPos, BlockState, CompoundTag, IPlacementContext)` are byte-identical, so
>   all four `@Redirect` descriptors port verbatim.
>
> Net effect: **the "main rework" this section predicted evaporated.** What actually needed work was
> elsewhere — BlockUI's render hook and item NBT (see [§ Port status](#port-status)).

### Original text (beta line)

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
  `neoforge-1.21.1` branch's jars are `1.21.1-<MAJOR.MINOR.patch>` automatically (the `minecraft_version`
  property drives the prefix) — **verified**: the first port build came out `1.21.1-0.4.68`.
- Keep file layout parallel between branches so cherry-picks apply cleanly. (This is why the SlimColonies
  sources are *excluded from the build* rather than deleted — see below.)

## Port status

### `:replacements` — compiles + jars on NeoForge 1.21.1

**Pinned stack** ([gradle.properties](../gradle.properties)): NeoForge `21.1.248`, ModDevGradle `2.0.143`,
Java 21, structurize `1.0.830-1.21.1`, blockui `1.0.209-1.21.1`, minecolonies `1.1.1368-1.21.1`,
domum-ornamentum `1.0.231`. These are **floors** — MineColonies pulls structurize up to
`1.0.832-1.21.1-snapshot`. Check what actually loads with
`gradlew :replacements:dependencies --configuration runtimeClasspath`.

> ⚠️ **The two LDTTeam version schemes sort against each other, and the OLD one always wins.** Gradle
> compares version parts left to right, so `1.21.1-1.0.200-BETA` > `1.0.223-snapshot` (21 > 0 at part two).
> A leftover old-scheme pin therefore **silently downgrades** the module below what MineColonies needs and
> beats its transitive requirement with no conflict warning. This bit us: domum-ornamentum stayed on the
> 2024-08 `1.0.200-BETA` jar, which predates `DynamicTimberFrameBlock` — a class MineColonies 1.21.1 calls
> from `DoBlockPlacementHandler`. The result was a `NoClassDefFoundError` **crash** the first time a hut's
> material list was computed (placing a town hall → Build Options), far from the version pin that caused it.
> Keep every LDTTeam pin on the new scheme, and note domum-ornamentum publishes its mod jar under a `main`
> classifier (no plain `.jar`) — it resolves only because it also publishes Gradle module metadata.

**Build plumbing.** ForgeGradle + MixinGradle → **ModDevGradle**. Three consequences worth remembering:
- **No refmap, no reobf, no MixinGradle.** NeoForge runs Mojang mappings in dev *and* production, so mixins
  reference one set of names everywhere. The `"refmap"` key was dropped from both configs and
  `compatibilityLevel` raised to `JAVA_21`.
- **Mixin configs are declared in `neoforge.mods.toml`** (`[[mixins]] config=…`), not in a `MixinConfigs`
  jar-manifest attribute. The cross-project `--mixin.config` dance `:compat` needs on 1.20.1 has no analogue
  here yet (only one subproject is in the build).
- **Third-party mods are plain dependencies** — they ship Mojmap-mapped, so there is no `fg.deobf(...)`.
- `settings.gradle` currently **includes only `:replacements`**; `:firmavanilla` and `:compat` are commented
  out until ported (they're still 1.20.1-Forge source and would fail every Gradle invocation).

**Loader migration** (all of it mechanical, done): `net.minecraftforge.*` → `net.neoforged.*` /
`net.neoforged.neoforge.*`, `ForgeConfigSpec` → `ModConfigSpec`, `ForgeRegistries.BLOCKS` →
`BuiltInRegistries.BLOCK`, `new ResourceLocation(a, b)` → `ResourceLocation.fromNamespaceAndPath(a, b)`,
`@Mod.EventBusSubscriber(bus=…)` → `@EventBusSubscriber` (the `bus` argument is deprecated-for-removal —
NeoForge infers the bus from whether the event implements `IModBusEvent`), and the `@Mod` ctor now receives
`(IEventBus, ModContainer)` with config registration on the container.

**The genuinely non-mechanical bits** — none of which the original scout predicted:
- **Networking → payloads.** Each message implements `CustomPacketPayload` with a `TYPE` +
  `StreamCodec.ofMember(::encode, ::new)` over `RegistryFriendlyByteBuf`, registered per-direction on
  `RegisterPayloadHandlersEvent`. Because registration is a *mod-bus event*, the bus has to be threaded from
  the mod ctor down through `MineColoniesBridge.init` → `ColonyIntegration.init` → `ColonyNetwork.register`.
  `NetworkEvent.Context` → `IPayloadContext` (`ctx.player()` is the sender; `ctx.connection()` still answers
  `isMemoryConnection()`; there is no `setPacketHandled`).
- **`FriendlyByteBuf#read/writeComponent` is gone** (1.20.5) — use `ComponentSerialization.STREAM_CODEC`,
  which *requires* the registry-aware buffer. The plain `FriendlyByteBuf` helper methods can stay as-is
  because `RegistryFriendlyByteBuf` extends it.
- **Item NBT → data components.** `stack.getOrCreateTag().put("textureData", …)` no longer exists. Domum
  1.21.1 exposes `MaterialTextureData.deserializeFromNBT(tag).writeToItemStack(stack)`, which writes wherever
  DO now reads — better than reproducing the component plumbing ourselves. (It's deprecated-for-removal in DO
  but is the only NBT-side entry point; revisit if DO drops it.)
- **BlockUI's render hook changed shape**: `postDrawBackground(PoseStack, ResourceLocation, x, y, w, h, u, v,
  …)` → `postDrawBackground(BOGuiGraphics, double mx, double my)`. The geometry arguments are gone, so
  [ButtonImageWithIcon](../replacements/src/main/java/com/structurizereplacements/client/gui/ButtonImageWithIcon.java)
  reads the protected `Pane` `x/y/width/height` fields instead and blits via `UiRenderMacros.blit`.
  Also `ButtonImage#setImage(ResourceLocation, boolean)` lost its boolean overload.
- **BlockUI removed the `<buttonimage>` GUI-XML element** — `<button>` now constructs a `ButtonImage`
  (which absorbed the vanilla look via `setVanillaButton()`/`VANILLA_BUTTON`). Attributes are unchanged
  (`source`, `label`, `textcolor`, `texthovercolor`), so it's a pure element rename — but see the warning
  below, because it is the one break in this port that **nothing catches at compile time**. The registered
  element names live in `com.ldtteam.blockui.Loader`'s ctor (`javap -c` it); the authoritative example is
  Structurize's own `assets/structurize/gui/windowbuildtool.xml`.

> ⚠️ **GUI XML is resolved at runtime and fails SILENTLY.** BlockUI skips unknown elements without an error,
> so all 15 of our `<buttonimage>` panes simply did not exist: the window opened fine (title/list/input are
> other elements) and the first unguarded `findPaneOfTypeByID(...).hide()` NPE'd and **crashed the client**
> the moment the *Replace* button was clicked. Nothing in the build, the mixin log, or the boot log hinted at
> it. After any BlockUI version jump, **click through every window** (`windowreplacements`, `windowpresetlist`,
> `windowconfirm`) rather than trusting a clean compile and a green boot.
- **Blueprint loading takes a `HolderLookup.Provider`**: `StructurePacks.getBlueprintFuture(pack, path,
  registries)`. Both call sites are client GUI paths, so they pass `Minecraft.getInstance().level.registryAccess()`
  and bail when there is no level.
- **Pack registration reshuffled**: `Pack.readMetaAndCreate` now takes a `PackLocationInfo` +
  `PackSelectionConfig` instead of loose id/title/required/position arguments, `Pack.ResourcesSupplier` gained
  a second abstract method (so it is **no longer a lambda target**), and `PathPackResources` moved from
  NeoForge into vanilla (`net.minecraft.server.packs`) taking the location info instead of an id + flag.
- **1.21 renames**: `FlowerPotBlock#getContent` → `#getPotted`, `ItemStack.isSameItemSameTags` →
  `isSameItemSameComponents`.
- **Datapack layout**: tag folders went singular — `data/<ns>/tags/blocks/` → `tags/block/`. Done for the
  opt-in default pack's 26 tag files. `pack_format`: the mod's own resources use **34** (1.21.1 *resource*
  format), the opt-in datapack **48** (1.21.1 *data* format) — they diverged in 1.21, so one number no longer
  serves both.

**SlimColonies is PARKED, not ported.** No 1.21.1 build exists (every published file is 1.20.1), so
`integration/slimcolonies/**` and `mixin/slimcolonies/**` are **excluded from the source set** and
`structurizereplacements.slimcolonies.mixins.json` is excluded from the jar and left out of
`neoforge.mods.toml`. The files stay on disk deliberately, as **unported 1.20.1-Forge source**, so the layout
matches `main` and cherry-picks keep applying. To revive: drop the excludes in
[replacements/build.gradle](../replacements/build.gradle), restore the dependency coordinates, re-add the
`[[mixins]]` entry + the optional dependency in `neoforge.mods.toml`, restore the `else if
(ModList.get().isLoaded("slimcolonies"))` arm in `StructurizeReplacements`, and port those 7 files.
**This suspends the "colony-fork twin rule"** on this branch — a change to the MineColonies bridge/mixins here
has no SlimColonies twin to mirror into.

**Load check (done).** `gradlew :replacements:runClient` reaches the main menu with structurize, blockui,
minecolonies, domum-ornamentum and `structurizereplacements` all loaded and **no mixin-apply failures**. The
first run did surface one — the only mixin whose target genuinely moved — and it's the one the scout predicted:
`AbstractBuilding#serializeNBT()` → `serializeNBT(HolderLookup$Provider)`. Note it failed as a *warning*, not a
crash, because the MineColonies config is `required:false`; per-building choice persistence would have been
silently dead. **When touching that config's mixins, always grep the run log for `Mixin apply … failed` — a
green boot proves nothing there.** Fixed together with `deserializeNBT(Provider, CompoundTag)`,
`serializeToView(RegistryFriendlyByteBuf, boolean)` and `AbstractBuildingView#deserialize(RegistryFriendlyByteBuf)`.
`AbstractWorkOrder` and `RegisteredStructureManager` were unchanged and ported verbatim.

### Not done yet

- **Functional in-game verification** — the mod loads and its mixins apply, but nothing has been *exercised*:
  place a blueprint with the build tool, open the *Replace* picker, check a per-building palette survives a
  save/reload, and confirm the payload round-trip on a dedicated server.
- **MixinExtras `@Redirect` → `@WrapOperation`** conversion (the deferred arch-review item). NeoForge bundles
  `mixinextras-neoforge` 0.5.3, so the annotations are already on the classpath with no dependency of ours —
  but the four `@Redirect`s are still `@Redirect`.
- **`:firmavanilla` and `:compat`** — not started; both still commented out of `settings.gradle`. `:compat` also
  needs TFC + Patchouli scouted on 1.21.1, and its TFC food *capabilities* become *data components*.
- EMI (dev-run-only recipe viewer) was dropped — no 1.21.1 file id looked up.
