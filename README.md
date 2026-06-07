# Palette Swap & MineColonies × TerraFirmaCraft

A Gradle **multi-project** repository containing **two Forge 1.20.1 mods**. The first is a standalone,
reusable building-block engine; the second builds on it to bridge MineColonies and TerraFirmaCraft.

| Subproject | Mod id | Name | What it is |
|---|---|---|---|
| [`:replacements`](replacements/) | `structurizereplacements` | **Palette Swap for MineColonies** | A standalone **Structurize** add-on for swapping the block palette of blueprints — via an in-game picker or datapack rules. **MineColonies is optional.** |
| [`:compat`](compat/) | `mctfc` | **MineColonies × TerraFirmaCraft** | A compatibility bridge that makes MineColonies playable in a TerraFirmaCraft world. Depends on `:replacements`. |

---

## Palette Swap for MineColonies (`:replacements`)

Change the block palette of any MineColonies or Structurize build — block-by-block with an in-game
picker, or automatically with datapack rules. Non-destructive and applied at placement, so original
blueprints are never modified.

- **In-game palette editor** — a *Replace* button on the Structurize build wand and on MineColonies'
  *Build Options* window opens the *Edit Block Palette* picker, with a live holographic preview.
- **Per-building palettes** — picks are saved to the colony, synced, and re-applied on every build/upgrade,
  with a *Current / Upgrade* tier toggle.
- **Datapack rules** — fixed swaps or interactive candidate pools, matched by block or tag, with
  property carry-over and priorities.
- **Builder integration** — substitutions apply consistently to material requests, placement, and
  build-progress for the builder, miner and quarrier.
- **Domum Ornamentum aware** — rewrites the materials stored in DO "materialized" blocks too.
- **Optional default pack** — an opt-in datapack of ready-made vanilla pick pools (wood, wool, terracotta,
  glass, concrete, beds, flowers, leaves, saplings, sand/sandstone, …), **off by default**.

It depends only on Structurize, so it works as a pure Structurize substitution mod; when MineColonies is
present, the per-building and builder integration activates. Full store description:
[docs/curseforge-description.md](docs/curseforge-description.md).

## MineColonies × TerraFirmaCraft (`:compat`, `mctfc`)

Makes a MineColonies colony work inside TerraFirmaCraft's harsher, overhauled world. It ships TFC
substitution rules (built on `:replacements`) plus a set of behavioural bridges:

- **TFC material builds** — colony blueprints are re-skinned into TFC stone/wood/sandstone families, with
  a non-falling ("mortared") cobble twin so cobble builds survive TFC gravity.
- **Farming** — the farmer tills TFC soil, plants and harvests TFC crops, and fertilizes them with TFC
  nutrients (with a per-field Fruiting/Seeding mode).
- **Food** — decay-aware stacking, colony-storage preservation, freshness-aware eating (skip rotten / FIFO),
  and a TFC-food nutrition bridge so citizens get proper saturation.
- **World survival** — active build areas are collapse-proof, the miner shaft uses the hut fill-block,
  citizens rest for TFC's *localized* rain, and colony light sources don't burn out.
- **Decorative vanilla furnaces** and TFC-flavored recipes so vanilla blocks remain obtainable.
- **Optional per-mod datapacks** — extra rules auto-enabled only when Beneath, ArborFirmaCraft or FirmaLife
  are installed.

## Why two mods?

The substitution engine (and its optional MineColonies builder integration) is generally useful to anyone
using Structurize, independent of TerraFirmaCraft — so it lives in its own standalone mod. `:compat` is the
TFC-specific consumer: it ships TFC rules as a datapack and houses the MC↔TFC behavioural bridging.

---

## Requirements

- **Minecraft** 1.20.1 · **Forge** 47.4.10+ · **Java** 17
- `:replacements` requires **Structurize** (+ BlockUI, Domum Ornamentum). **MineColonies** is optional.
- `:compat` additionally requires **MineColonies**, **TerraFirmaCraft** (and its dependency **Patchouli**).

## Building & running

ForgeGradle 6 via the wrapper. First setup decompiles Minecraft and downloads the dependency mods (slow,
needs network).

```sh
./gradlew build                 # build both mods (jars in <sub>/build/libs)
./gradlew :replacements:build   # build only the standalone mod

# Run ONLY the standalone mod (Structurize/BlockUI, no MineColonies/TFC):
./gradlew :replacements:runClient

# Run the FULL stack (both mods + MineColonies/Structurize/TFC/...):
./gradlew :compat:runClient
./gradlew :compat:runServer
```

> **Always qualify the run task with its subproject** (`:replacements:runClient` / `:compat:runClient`).
> A bare `gradlew runClient` launches two Minecraft instances (one per subproject).

## Project layout

```
settings.gradle        includes :replacements and :compat
build.gradle           shared ForgeGradle config
gradle.properties      shared MC/Forge/mapping/dependency versions
replacements/          the standalone Structurize add-on (com.structurizereplacements)
compat/                the MineColonies × TFC bridge (com.mctfc)
docs/                  store description, datapack rule examples, design notes
```

Per-mod identity (id/name/version) lives in each subproject's `build.gradle`; shared versions live in
`gradle.properties`.

## License

All Rights Reserved (see each subproject's `build.gradle`). These are independent addons and are not
affiliated with the MineColonies or TerraFirmaCraft teams.
