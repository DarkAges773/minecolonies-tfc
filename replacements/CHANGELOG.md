# Changelog — Palette Swap for MineColonies

All notable changes to this mod are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/). Releases are published as `1.20.1-<version>`
(the Minecraft version prefixes the SemVer).

## [Unreleased]

### Fixed
- **Multiplayer (dedicated servers) now works** — the active substitution ruleset is synced server → client on
  join and after `/reload`, so the build-wand preview and the *Replace* picker work on remote servers (they
  previously only worked in single-player, where client and server share the loaded rules).
- **Stale picks after rejoining** — replacement choices are now cleared on disconnect, so after rejoining a
  server the preview no longer shows substitutions that placement wouldn't apply.
- **Duplicate *Replace* button** — the shape tool no longer stacks an extra *Replace* button each time its
  block picker is used.
- **Network hardening** — malformed/hostile packets with absurd element counts are now rejected at decode
  instead of forcing huge allocations (could crash a server or client), and the choices message only accepts
  the client → server direction.

## [0.1.37] - 2026-06-09

### Added
- **Mineshaft palette (miner)** — the miner hut's settings list now has an *Edit Mineshaft Palette* row
  that opens the picker for the materials of the tunnels/shafts the miner digs (the `infrastructure/mineshafts/*`
  schematics, aggregated across all node types). This palette is stored separately from the hut building's
  palette and applies to every mineshaft the miner builds.

## [0.1.0] - 2026-06-08
First release.

### Added
- **In-game block palette editor** — an *Edit Block Palette* picker, opened by a *Replace* button on the
  Structurize build wand and on MineColonies' *Build Options* window. One row per material in the build,
  with a live holographic preview, a *Reset* button, and (in Build Options) a *Current / Upgrade* tier toggle.
- **Datapack substitution rules** — `data/<namespace>/block_substitutions/*.json`: fixed swaps (`to`) and
  interactive candidate pools (`to_tag`), matched by block or block tag, with `apply_properties`,
  `copy_properties`, and rule `priority`.
- **Per-building palettes (MineColonies)** — picks are saved to the colony, synced to clients, and re-applied
  every time the builder builds or upgrades the building.
- **Builder integration** — substitution stays consistent across material requests, placement, and
  build-progress matching for the builder, miner and quarrier.
- **Client preview** — the build-wand hologram reflects substitutions, including block entities (e.g. chests).
- **Domum Ornamentum support** — rewrites the materials stored in DO "materialized" blocks; DO blocks are
  filtered out of candidate pools (they can't be picked as plain blocks).
- **Optional default datapack** — an opt-in, off-by-default pack of ready-made vanilla pick pools (wood, wool,
  terracotta, glazed terracotta, stained glass, concrete & powder, beds, flowers, leaves, saplings, mushrooms,
  potted plants, sand & sandstone).

### Notes
- MineColonies is an **optional** dependency: without it the mod is a pure Structurize substitution add-on.
- On dedicated servers, datapack rules and builder substitution apply server-side; live client-preview/picker
  sync is planned.

[Unreleased]: https://github.com/DarkAges773/minecolonies-tfc/compare/palette-swap/v0.1.0...HEAD
[0.1.0]: https://github.com/DarkAges773/minecolonies-tfc/releases/tag/palette-swap/v0.1.0
