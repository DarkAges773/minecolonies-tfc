# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

A Gradle **multi-project** repo containing **two Forge 1.20.1 mods**:

| Subproject | Mod id | Package | Purpose |
|---|---|---|---|
| `:replacements` | `structurizereplacements` | `com.structurizereplacements` | **Standalone** Structurize add-on: datapack-driven, tag/family block substitution when placing blueprints. No MineColonies/TFC dependency. |
| `:compat` | `mctfc` | `com.mctfc` | **MineColonies × TerraFirmaCraft** bridge. Depends on `:replacements`, ships TFC substitution rules as a datapack, and will house the rest of the MC↔TFC bridging. |

The split exists so the substitution engine is reusable by anyone, independent of TFC.

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

Who depends on what: `:replacements` → Structurize (+ blockui runtime) only. `:compat` →
`project(':replacements')` + the full stack (structurize/blockui/minecolonies/domum/tfc/patchouli) so
the dev run loads everything.

**Dev-run-only test mods** (`runtimeOnly` — they do NOT appear in any mods.toml and are not real
dependencies, they only enrich `runClient`): `:replacements` adds MineColonies + Domum Ornamentum (so
its standalone run can use the MineColonies build tool — Domum is MineColonies' mandatory dep) and EMI;
`:compat` adds EMI. Remove these before shipping if you want lean dev runs.

**Bumping versions:** edit the `*_version` / `*_file_id` properties. Verify LDTTeam versions against
`<artifact>/maven-metadata.xml`; TFC/Patchouli use CurseForge **file ids** (from the file URL).
**A missing mandatory dep** (e.g. Patchouli for TFC) shows up as a *misleading* mixin
`could not be read` crash — check earlier in the log for `Missing or unsupported mandatory
dependencies` first.

## Mixins

SpongePowered MixinGradle (refmap generation). Notes that each cost a debugging crash:

- **`:replacements`** owns the only mixin config,
  [structurizereplacements.mixins.json](replacements/src/main/resources/structurizereplacements.mixins.json)
  (package `com.structurizereplacements.mixin`). Registered via `MixinConfigs` jar manifest +
  MixinGradle's `mixin { config }`.
- Mixins targeting **another mod's own methods** (Structurize, not Minecraft) need **`remap = false`**
  on the injector — those names are stable and have no SRG mapping. See
  [MixinStructurePlacer](replacements/src/main/java/com/structurizereplacements/mixin/MixinStructurePlacer.java).
- **`:compat` also applies MixinGradle even though it has no mixins.** The dev runs live in `:compat`,
  and applying the plugin in the run-owning project is what injects the runtime refmap remapping
  (`mixin.env.remapRefMap`). Without it, *other* mods' SRG-named mixins (e.g. Patchouli's
  `AccessorScreen`) fail to apply in the official-mapped dev env (`InvalidAccessorException`).
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
- GUI/preview (future): `client.gui.WindowExtendedBuildTool` (placement window),
  `storage/rendering` + `BlueprintPreviewData` (the placement hologram — client-side, which is why the
  current server-side mixin doesn't change the preview).

## The substitution feature (in :replacements)

Placement-time, non-destructive, datapack-driven. **Global** while enabled (`Config.enableSubstitution`,
default on) — applies to every Structurize placement, not opt-in per build (a GUI toggle is planned).

- [SubstitutionRule](replacements/src/main/java/com/structurizereplacements/substitution/SubstitutionRule.java) —
  match by exact block or block tag → replacement block.
- [FamilyRule](replacements/src/main/java/com/structurizereplacements/substitution/FamilyRule.java) —
  **material-token cascade.** A `from → to` block rule whose ids differ by exactly one path token
  (split on `_` and `/`) derives a material swap (`oak_planks→spruce_planks` ⇒ `oak→spruce`), also
  TFC's `wood/planks/oak→wood/planks/spruce`. On by default; `"family": false` forces exact-only.
  Rules differing by more than one token (e.g. `cobblestone→mossy_cobblestone`) derive no cascade;
  tag rules never cascade.
- [CascadeShapes](replacements/src/main/java/com/structurizereplacements/substitution/CascadeShapes.java) —
  **the cascade is gated by block FORM, not name.** A swap only applies when the candidate and its
  token-swapped target are the same building shape — `instanceof` one of `StairBlock, SlabBlock,
  WallBlock, FenceBlock, FenceGateBlock, DoorBlock, TrapDoorBlock, ButtonBlock, PressurePlateBlock`.
  Modded blocks extend these (TFC `TFCStairBlock extends StairBlock`), so it works for vanilla + TFC,
  and it EXCLUDES logs/wood (`RotatedPillarBlock`), leaves (`LeavesBlock` and TFC's plain-`Block`
  leaves), saplings, and the plank base — fixing the earlier name-only over-matching. Decision record:
  `BlockFamily` (vanilla datagen) was evaluated and rejected — verified TFC has zero `BlockFamily`
  references, so it can't drive TFC cascades; plain-`Block` variants (stone polished/bricks) are
  intentionally out of scope (use explicit rules). Signs are easy to add to the allowlist if wanted.
- [BlockSubstitutions](replacements/src/main/java/com/structurizereplacements/substitution/BlockSubstitutions.java) —
  `apply(BlockInfo)` swaps state, **copies shared properties** (facing/axis/half…), memoized, cleared on
  reload. Resolution order: exact/tag rules first, then family cascades.
- [BlockSubstitutionReloadListener](replacements/src/main/java/com/structurizereplacements/substitution/BlockSubstitutionReloadListener.java) —
  loads `data/<namespace>/block_substitutions/*.json` across **all** datapacks/namespaces; registered on
  `AddReloadListenerEvent` in [event/ModEvents](replacements/src/main/java/com/structurizereplacements/event/ModEvents.java).

**Rule JSON** — each entry has `"to"` (block id) + exactly one of `"from"` (block id) or `"from_tag"`
(block tag id), plus optional `"family"` (bool, default true) on `from→to` rules; first match wins;
unknown ids are logged and skipped. Example/dev rules (vanilla, demonstrate the cascade) ship in
[replacements .../block_substitutions/examples.json](replacements/src/main/resources/data/structurizereplacements/block_substitutions/examples.json)
— **delete that for a clean published library** (active rules would rewrite any consumer's blueprints).
TFC rules ship in
[compat .../block_substitutions/defaults.json](compat/src/main/resources/data/mctfc/block_substitutions/defaults.json)
(vanilla example values for now — swap to `tfc:…` ids; note cross-namespace vanilla→TFC pairs won't
auto-cascade, so TFC needs per-form rules or same-namespace `tfc:…→tfc:…` family rules).

## Verified

`gradlew build` (both jars) and `gradlew :compat:runClient` both succeed: all 7 mods load
(structurizereplacements, mctfc, structurize, blockui, minecolonies, domum_ornamentum, tfc, patchouli),
mixin config selects, both mods construct, reaches main menu. **Block swap on placement was confirmed
in-game** (planks→spruce planks, cobblestone→mossy cobblestone) before the split; the logic is
unchanged since, but re-confirm after any change by placing a blueprint via the Structurize build tool
and `/reload`-ing edited JSON.

## Roadmap / not yet done

In `:replacements` (generic):
- ~~**Family substitution**~~ — DONE, now **form-gated** by [CascadeShapes](replacements/src/main/java/com/structurizereplacements/substitution/CascadeShapes.java)
  (cascades to stairs/slabs/walls/fences/gates/doors/trapdoors/buttons/pressure-plates; excludes
  logs/wood/leaves/saplings). Not yet manually verified in-game — place a blueprint with oak
  stairs/slabs/fences AND oak logs/leaves; the shapes should become spruce while logs/leaves stay oak.
  Possible follow-ups: cross-namespace family mapping (vanilla→TFC); add signs/plain-Block variants.
- **Client preview mixin** — so the placement hologram shows replacements (client-side render path).
- **GUI toggle** in `WindowExtendedBuildTool` for per-placement opt-in.

In `:compat`:
- Real TFC rule sets (verified `tfc:` ids), then the broader MC↔TFC bridging (food/nutrition,
  requests/progression, farming/animals).

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
