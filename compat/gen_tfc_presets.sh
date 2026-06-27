#!/usr/bin/env bash
# One-off generator for the TFC "rock type" palette presets (one per TFC rock). Each preset re-palettes a whole
# build onto a single rock: it maps every default-reachable rock form ({dacite, granite, diorite, andesite} — the
# blocks a vanilla MineColonies blueprint resolves to under the TFC substitution rules) to the target rock's form.
# Derived straight from the rock candidate-pool tags (tags/blocks/subst/rock/*.json) so every pick references a
# real pool member (magma stays igneous-only, the firmavanilla mortared-cobble twins are picked up automatically).
# Re-run after gen_tfc_substitutions.sh if TFC's rock set changes. Emits:
#   - data/mctfc/block_substitution_presets/<rock>.json
#   - prints the mctfc.preset.<rock> lang lines to stdout (paste into assets/mctfc/lang/en_us.json)
set -euo pipefail

RES="$(cd "$(dirname "$0")" && pwd)/src/main/resources/data/mctfc"
ROCK_TAGS="$RES/tags/blocks/subst/rock"
# Presets live under "builtin/rocks/" so the read-only built-ins appear grouped (and separate from the
# player's own root-level presets) in Palette Swap's navigable preset picker.
PRESETS="$RES/block_substitution_presets"
OUT="$PRESETS/builtin/rocks"
rm -rf "$PRESETS/rock_types" "$PRESETS/builtin"
mkdir -p "$OUT"

node - "$ROCK_TAGS" "$OUT" <<'NODE'
const fs = require('fs');
const path = require('path');
const [, , tagDir, outDir] = process.argv;

// The rocks a vanilla blueprint can resolve to (the override-map keys the engine actually sees): the stone
// family converts to dacite, and vanilla granite/diorite/andesite to their same TFC rock.
const SOURCES = ['dacite', 'granite', 'diorite', 'andesite'];
const SUFFIXES = ['_stairs', '_slab', '_wall'];

// rock name = last path segment, minus a form suffix (e.g. ".../cobble/dacite_stairs" -> "dacite").
const rockOf = (id) => {
  let seg = id.substring(id.lastIndexOf('/') + 1);
  for (const s of SUFFIXES) if (seg.endsWith(s)) { seg = seg.slice(0, -s.length); break; }
  return seg;
};

// Read each form tag as a rock -> blockId map, in sorted file order for deterministic output.
const tags = fs.readdirSync(tagDir).filter(f => f.endsWith('.json')).sort()
  .map(f => {
    const values = JSON.parse(fs.readFileSync(path.join(tagDir, f), 'utf8')).values || [];
    const map = {};
    for (const id of values) map[rockOf(id)] = id;
    return map;
  });

const rocks = [...new Set(tags.flatMap(t => Object.keys(t)))].sort();
const title = (r) => r.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');

const lang = {};
for (const target of rocks) {
  const picks = [];
  for (const map of tags) {
    const to = map[target];
    if (!to) continue;
    for (const s of SOURCES) {
      const from = map[s];
      if (from && s !== target) picks.push({ from, to });
    }
  }
  const preset = { name: `mctfc.preset.${target}`, icon: `tfc:rock/raw/${target}`, picks };
  fs.writeFileSync(path.join(outDir, `${target}.json`), JSON.stringify(preset, null, 2) + '\n');
  lang[`mctfc.preset.${target}`] = title(target);
}

console.error(`Wrote ${rocks.length} presets to ${outDir}`);
for (const [k, v] of Object.entries(lang)) console.log(`  ${JSON.stringify(k)}: ${JSON.stringify(v)},`);
NODE