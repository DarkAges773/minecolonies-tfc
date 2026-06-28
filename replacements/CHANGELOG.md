# Changelog — Palette Swap for MineColonies

All notable changes to this mod are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/). Releases are published as `1.20.1-<version>`
(the Minecraft version prefixes the SemVer).

## [Unreleased]

### Added
- **Palette presets** — the *Replace* picker now has a **Presets** button opening a hub where you can **save**
  the current picks under a name, **load** a saved set into any build (it merges, keeping picks it doesn't
  mention), **edit** a saved preset (retarget or delete individual picks), **clone** a built-in preset into your
  library, and **delete** your own. Your library is stored per-installation, so presets persist between sessions
  and follow you across worlds, servers and colonies — build a palette once and reuse it everywhere. Presets are
  organised into **folders** (the hub is navigable — drill into a folder, "Up" to climb out; save into the current
  folder, or use a `folder/name` name to nest) and each shows an **icon**. Mods and datapacks can ship
  **read-only built-in presets** (`data/<namespace>/block_substitution_presets/[<folder>/]*.json`, with an optional
  `"icon"`).
- The *Replace* picker rows now have a **per-row reset** (a small red cross) to clear just that row's pick back to
  the default — the bottom *Reset* still clears the whole palette.
- The opt-in default datapack now ships **built-in presets** — a **planks** and a **logs** preset per vanilla wood
  (Oak, Spruce, …, Crimson, Warped, Bamboo), under a *Built-in* folder in the *Presets* picker — so a whole build
  can be re-woned in one click once the default pack is enabled.
- The opt-in default pick pools gained several families: **stained glass panes**, **candles**, **froglights**, **wool
  carpets**, Domum Ornamentum **floating carpets**, **banners** (standing and wall as separate pools), the
  colored Domum Ornamentum **brick** and **cobblestone "extra"** blocks, and its **bricks** and **stone
  bricks** (roan/beige/brown/cream/sand). The tall-flowers pool now also includes the **pitcher plant**, and
  the terracotta pool offers plain terracotta.

### Security
- **The server now validates replacement picks** sent by clients (both the build-wand session picks and the
  per-building palette) against the loaded candidate pools, dropping any that the GUI couldn't legitimately
  offer. Previously a modified client could submit an arbitrary source→target swap (e.g. a hand-crafted preset)
  and the server would apply it, bypassing the candidate pools; picks outside a pool are now rejected server-side.
- A malformed or modified client packet can no longer **wipe a building's saved palette**: a per-building update
  whose picks all fail validation is now dropped instead of being treated as a "clear all".

### Fixed
- **Editing a saved preset in a world that's missing a mod it references** (e.g. a TFC preset opened without TFC)
  no longer deletes that mod's picks. Picks for blocks not present in the current game are now preserved untouched
  and re-saved, so the preset stays intact when you reopen it where those blocks exist again.
- The *Replace* picker now shows a brief message instead of opening an **empty selection window** when a row's
  candidate pool has nothing pickable.
- Presets whose names are entirely **non-Latin** (Cyrillic, CJK, …) now each get a distinct file on disk instead
  of all collapsing onto the same one (the in-game preset name was always preserved either way).

## [0.3.56] - 2026-06-27

### Added
- The **Replace** picker now shows, per row, which schematic blocks a swap affects — a hover tooltip lists
  each affected block (with its icon) and a `(N)` badge marks rows that touch more than one. This surfaces
  materials hidden inside Domum Ornamentum blocks: e.g. an *Oak Planks* row that also recolours a panel/framed
  block whose material is oak planks now reads "affects 2 block types". Affected Domum Ornamentum blocks show
  their actual textured material and are named accordingly (e.g. "Oak Panel"), not a generic placeholder.

### Changed
- The **Replace** picker window is now the standard MineColonies window size (matching Build Options), giving
  the name columns more room and showing more rows at once.

### Fixed
- Long block names in the **Replace** picker no longer wrap onto a second line and overlap the row borders —
  each name stays on one line, shrinking slightly to fit and, if still too long, truncating with an ellipsis
  (the full name shows on hover). The "(N)" affected-count badge always stays fully visible.

## [0.2.48] - 2026-06-12

### Added
- **SlimColonies support** — the full per-building integration (builder substitution, Build Options
  *Replace* picker, miner mineshaft palette, choice persistence/sync) now also works with the
  [SlimColonies](https://www.curseforge.com/minecraft/mc-mods/slimcolonies) fork of MineColonies
  (1.20.1-17.4+). Like MineColonies, it is an optional dependency — install either colony mod (or neither).

### Changed
- Per-building choice edits now travel on one shared network channel for both colony mods (previously a
  MineColonies-specific channel) — update client and server to the same mod version together.

### Fixed
- **Domum Ornamentum blocks (timber frames etc.) now follow replacement choices in builder
  builds/repairs** (previously the builder skipped them on repairs — or looped forever recalculating
  its material list — on MineColonies and SlimColonies alike). Three stacked fixes: the material
  rewrite now covers all of the dynamic timber frame's NBT (it stores its materials outside the
  `textureData` map the engine previously rewrote); every "already built?" decision — including the one
  that builds the material list, which previously compared the raw, unsubstituted blueprint and so
  never requested the swapped materials — is now substitution-aware, with DO materials verified
  directly where the colony mod's DO placement handler compares only block states (SlimColonies); and
  after a placement the engine verifies the world block actually received the substituted materials and
  writes them itself when the colony mod's handler skipped the tile-entity write (SlimColonies skips it
  whenever the block state was already correct). The re-texture is paid for like any other replacement:
  the substituted materialized item appears in the builder's material list and is requested/consumed by
  the builder, and the removal refunds the old-texture item; creative placements stay free.

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
