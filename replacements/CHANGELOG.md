# Changelog — Palette Swap for MineColonies

All notable changes to this mod are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/). Releases are published as `1.20.1-<version>`
(the Minecraft version prefixes the SemVer).

## [Unreleased]

## [0.1.42] - 2026-06-11

### Fixed
- **Substitution broken in single-player (regression in 0.1.41)** — the active ruleset was wiped during
  single-player world load (the disconnect-cleanup ran on the load-time teardown and erased the rules the
  datapack reload had just loaded), so no substitution applied: the build-wand *Replace* picker showed no
  pools and datapack rules did nothing. Rules are now cleared only on a genuine remote disconnect.
- **Build Options *Replace* picker on high-level buildings** — the picker now lists the correct upgrade
  tier's blocks for buildings at level 10 or above (the next-level blueprint path was mis-constructed for
  two-digit levels), and falls back to the current tier instead of showing an empty picker if MineColonies'
  blueprint naming ever changes.
- **MineColonies client-sync hardening** — the per-building choice data appended to MineColonies' building
  view sync now degrades gracefully if a future MineColonies update desynchronizes it, instead of risking a
  client crash on colony load.

## [0.1.41] - 2026-06-10

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
- **Stale palette adoption** — picks staged at structure placement now expire after a short window and are
  scoped to the dimension (and cleared on server stop), so a hut placed at the same coordinates in another
  dimension or a later single-player world no longer silently adopts an old palette.
- **`/reload` with tag rules** — `from_tag` rule matches are no longer cached against the pre-reload tag
  contents during the brief window before tags rebind.
- **Deterministic rule conflicts** — equal-priority rules for the same source block from *different* datapack
  files now resolve in a stable (sorted file id) order instead of an arbitrary one. To override a rule from
  another pack, give yours a higher `"priority"`.
- **Malformed rule files** — a broken entry mid-file now skips the whole file (as the log always claimed)
  instead of keeping the entries parsed before the error.

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
