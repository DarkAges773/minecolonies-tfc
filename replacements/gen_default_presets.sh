#!/usr/bin/env bash
# One-off generator for the opt-in default datapack's built-in palette presets: one "log" and one "planks" preset
# per vanilla wood, mirroring how :compat does it for TFC. Each maps every other vanilla wood's form -> the target
# wood's form (the default pack's pools are pure from_tag==to_tag, so a vanilla blueprint's block is its own
# override key and any wood is a valid source). Targets every wood in the pack's wood pools, so every pick is a
# real pool member (passes Palette Swap's server-side choice validation). Vanilla wood naming is irregular — the
# wood is a prefix, the nether woods use _stem/_hyphae for logs, and bamboo has no log in minecraft:logs — so this
# uses explicit templates rather than tag scraping. Emits into default_rules_datapack/.../builtin/{planks,logs}/ and
# prints the structurizereplacements.preset.wood.<wood> lang lines (merge into assets/.../lang/en_us.json).
set -euo pipefail

OUT="$(cd "$(dirname "$0")" && pwd)/src/main/resources/default_rules_datapack/data/structurizereplacements/block_substitution_presets/builtin"
rm -rf "$OUT"
mkdir -p "$OUT"

node - "$OUT" <<'NODE'
const fs = require('fs');
const path = require('path');
const [, , OUT] = process.argv;

// All 11 vanilla woods have the plank family; bamboo is excluded from logs (bamboo_block isn't in minecraft:logs).
const PLANK_WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'crimson', 'warped', 'bamboo'];
const LOG_WOODS = PLANK_WOODS.filter(w => w !== 'bamboo');
const NETHER = new Set(['crimson', 'warped']);

const PLANK_FORMS = ['planks', 'stairs', 'slab', 'fence', 'fence_gate', 'door', 'trapdoor', 'button',
                     'pressure_plate', 'sign', 'wall_sign', 'hanging_sign', 'wall_hanging_sign'];
const plankId = (w, form) => `minecraft:${w}_${form}`;

// Log variants map across the log/stem (and wood/hyphae) naming split so a nether stem swaps with an overworld log.
const LOG_VARIANTS = ['log', 'wood', 'stripped_log', 'stripped_wood'];
const logId = (w, variant) => {
  const base = NETHER.has(w)
    ? { log: `${w}_stem`, wood: `${w}_hyphae`, stripped_log: `stripped_${w}_stem`, stripped_wood: `stripped_${w}_hyphae` }
    : { log: `${w}_log`, wood: `${w}_wood`, stripped_log: `stripped_${w}_log`, stripped_wood: `stripped_${w}_wood` };
  return `minecraft:${base[variant]}`;
};

const title = (w) => w.split('_').map(s => s[0].toUpperCase() + s.slice(1)).join(' ');
const lang = {};
const write = (folder, wood, icon, picks) => {
  fs.mkdirSync(path.join(OUT, folder), { recursive: true });
  fs.writeFileSync(path.join(OUT, folder, `${wood}.json`),
    JSON.stringify({ name: `structurizereplacements.preset.wood.${wood}`, icon, picks }, null, 2) + '\n');
  lang[`structurizereplacements.preset.wood.${wood}`] = title(wood);
};

for (const target of PLANK_WOODS) {
  const picks = [];
  for (const form of PLANK_FORMS)
    for (const src of PLANK_WOODS) if (src !== target) picks.push({ from: plankId(src, form), to: plankId(target, form) });
  write('planks', target, plankId(target, 'planks'), picks);
}
for (const target of LOG_WOODS) {
  const picks = [];
  for (const variant of LOG_VARIANTS)
    for (const src of LOG_WOODS) if (src !== target) picks.push({ from: logId(src, variant), to: logId(target, variant) });
  write('logs', target, logId(target, 'log'), picks);
}

console.error(`Wrote ${PLANK_WOODS.length} planks + ${LOG_WOODS.length} logs presets to ${OUT}`);
for (const [k, v] of Object.entries(lang).sort()) console.log(`  ${JSON.stringify(k)}: ${JSON.stringify(v)},`);
NODE
