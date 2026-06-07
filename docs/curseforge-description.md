# Palette Swap for MineColonies

Tired of every MineColonies village being built from the same vanilla blocks? **Palette Swap** lets you
re-skin the block palette of any **MineColonies** or **Structurize** blueprint — change materials
block-by-block with an in-game picker, or define automatic rules in a datapack. It's **non-destructive**
and applied **at placement**, so your original schematics and packs are never modified.

> *Screenshots: the "Edit Block Palette" window, a before/after build, and the "Replace" button on the
> Build Options screen.*

## Features

- **🎨 In-game palette editor.** A **Replace** button on the Structurize build wand *and* on MineColonies'
  **Build Options** window opens the *Edit Block Palette* picker — one row per material in the build. Pick a
  replacement from a candidate pool and the holographic preview updates instantly. A **Reset** button puts
  everything back to default.
- **🏘️ Per-building palettes (MineColonies).** Set a building's palette once: your picks are saved to the
  colony, synced to everyone, and re-applied every time the builder builds or upgrades it. A
  **Current / Upgrade** toggle lets you edit the tier you already have or the tier you're about to build.
- **📜 Datapack rules.** Define automatic substitutions in JSON — fixed swaps or interactive "pick from a
  pool" rules, matched by block or block tag, with blockstate property carry-over and priorities. Perfect
  for modpacks that want builds to use their own materials.
- **👷 Full builder support.** MineColonies' builder, miner and quarrier all place through Structurize, so
  substitutions apply to everything they build — material requests, placement, and build-progress tracking
  all stay in sync.
- **🧱 Domum Ornamentum aware.** DO "materialized" blocks (panels, bricks, framed blocks, …) have their
  stored materials swapped too, not just plain blocks.
- **🔄 Non-destructive.** Blueprints are never altered on disk; substitution happens only as blocks are placed.

## Optional: ready-made palettes

The mod ships an **opt-in** default datapack — **off by default** (enable it from the world's *Data Packs*
screen) — that adds interactive pick pools for common vanilla families:

> wood (planks, logs, stairs, slabs, fences, gates, doors, trapdoors, buttons, plates, signs) · wool ·
> terracotta · glazed terracotta · stained glass · concrete & concrete powder · beds · flowers (small & tall) ·
> leaves · saplings · mushrooms · potted plants · sand & sandstone

Turn it on for an instant working picker, or leave it off and ship your own rules.

## For modpack & datapack authors

Drop rules in `data/<namespace>/block_substitutions/<name>.json`:

```json
{
  "replacements": [
    { "from": "minecraft:cobblestone", "to": "minecraft:mossy_cobblestone" },
    { "from_tag": "minecraft:planks", "to_tag": "minecraft:planks" }
  ]
}
```

`"to"` is an automatic swap; `"to_tag"` is an interactive pool the player chooses from in the *Replace* GUI.
Optional `apply_properties` / `copy_properties` carry blockstate values across, and `priority` lets one pack
override another. Everything is `/reload`-able.

## Compatibility

- **Minecraft** 1.20.1 · **Forge**
- **Requires:** Structurize (plus BlockUI and Domum Ornamentum — Structurize's own dependencies)
- **Optional:** **MineColonies** — when present, the per-building Build Options editor and the builder
  integration activate. Without it, you still get the full Structurize build-wand experience.

> **Note:** In single-player everything works out of the box. On dedicated servers, datapack rules and
> builder substitution apply server-side; live client-preview sync is on the roadmap.

## Links

- 📦 **MineColonies** — *(link)*
- 🏗️ **Structurize** — *(link)*

---

*Palette Swap for MineColonies is an independent addon and is not affiliated with the MineColonies team.*
