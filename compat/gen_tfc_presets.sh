#!/usr/bin/env bash
# One-off generator for the TFC built-in palette presets. Each preset re-palettes a whole build onto a single
# material: it maps every default-reachable source form (the blocks a vanilla MineColonies blueprint resolves to
# under the TFC substitution rules) to the target material's form. Derived straight from the candidate-pool tags
# (tags/blocks/subst/*) so every pick references a real pool member — which also means each pick passes Palette
# Swap's server-side choice validation. Groups (each a folder in the navigable preset picker, under "builtin/"):
#   - rocks   one per TFC rock          (sources: dacite/granite/diorite/andesite)
#   - planks  one per wood, plank family (sources: every wood a vanilla blueprint can resolve to — see below)
#   - logs    one per wood, log family   (same wood sources)
#   - dirt    one per TFC soil type      (source: loam — all vanilla soil converts to loam)
#
# Wood sources span mods: ArborFirmaCraft RE-ROUTES some vanilla woods (e.g. spruce->cypress, birch->eucalyptus)
# and Beneath adds crimson/warped, so the actual override keys depend on which mods are installed. The wood source
# set is therefore the UNION of every vanilla->wood conversion target across tfc_wood.json + afc.json + beneath.json
# (16 woods across the tfc/afc/beneath namespaces). Each wood preset includes a pick from every one of them; the
# preset loader drops picks whose source block is absent in the current install, and a source that isn't actually a
# conversion target in some install is simply never triggered — so one static preset works in every mod combination.
# Wood presets are emitted for base TFC woods (main datapack) and AFC/Beneath woods (into those mods' optional
# datapacks); all share the mctfc namespace + builtin/planks|logs folders so they merge into one Planks/Logs group.
# Re-run after gen_tfc_substitutions.sh if the rock/wood/soil set changes. Emits the preset JSON plus, to stdout,
# the lang lines (merge into assets/mctfc/lang/en_us.json).
set -euo pipefail

RESROOT="$(cd "$(dirname "$0")" && pwd)/src/main/resources"

node - "$RESROOT" <<'NODE'
const fs = require('fs');
const path = require('path');
const [, , RES] = process.argv;

// Form suffixes that can trail the material name in a pool member's last path segment (e.g.
// ".../cobble/dacite_stairs" -> "dacite", ".../planks/oak_wall_sign" -> "oak"). Longest-first so a longer
// suffix wins over a prefix of it (_wall_sign before _wall, _fence_gate before _fence).
const SUFFIXES = ['_pressure_plate', '_fence_gate', '_workbench', '_wall_sign', '_trapdoor', '_stairs',
                  '_button', '_fence', '_door', '_slab', '_sign', '_wall'];
const keyOf = (id) => {
  const seg = id.substring(id.lastIndexOf('/') + 1);
  for (const s of SUFFIXES) if (seg.endsWith(s)) return seg.slice(0, -s.length);
  return seg;
};

const SUBST = (root) => path.join(root, 'data/mctfc/tags/blocks/subst');
const SUB = (root) => path.join(root, 'data/mctfc/block_substitutions');
const OUT = (root) => path.join(root, 'data/mctfc/block_substitution_presets/builtin');
const MAIN = RES, AFC = path.join(RES, 'afc_datapack'), BENEATH = path.join(RES, 'beneath_datapack');

// material-key -> blockId for one form tag (missing file -> empty).
const readTag = (dir, form) => {
  const file = path.join(dir, `${form}.json`);
  if (!fs.existsSync(file)) return {};
  const map = {};
  for (const id of (JSON.parse(fs.readFileSync(file, 'utf8')).values || [])) map[keyOf(id)] = id;
  return map;
};

const PLANK_FORMS = ['planks', 'stairs', 'slab', 'fence', 'fence_gate', 'door', 'trapdoor', 'button',
                     'pressure_plate', 'sign', 'wall_sign', 'hanging_sign', 'wall_hanging_sign',
                     // plank-made furniture/utility blocks (some are oak-only singletons from vanilla, e.g. chest /
                     // lectern / crafting_table -> workbench; others have no vanilla source but are kept for builds
                     // that place the TFC block directly).
                     'chest', 'trapped_chest', 'lectern', 'scribing_table', 'decorative_bookshelf', 'barrel', 'workbench'];
const LOG_FORMS = ['log', 'wood', 'stripped_log', 'stripped_wood'];
const SOIL_FORMS = ['dirt', 'coarse_dirt', 'rooted_dirt', 'grass', 'grass_path'];
// firmavanilla deepslate-tile rock forms (tags live under subst/firmavanilla, not subst/rock). Vanilla deepslate
// resolves to basalt (so basalt is a rock source); its tile forms resolve to firmavanilla:<form>/basalt.
const TILE_FORMS = ['tiles', 'cracked_tiles', 'tile_stairs', 'tile_slab', 'tile_wall'];

// A wood form's source map merges all three namespaces' tags, so a source id resolves to whichever mod owns that
// wood (tfc:.../oak, afc:.../cypress, beneath:.../crimson).
const WOOD_DIRS = [path.join(SUBST(MAIN), 'wood'), path.join(SUBST(AFC), 'wood'), path.join(SUBST(BENEATH), 'wood')];
const mergedWood = (form) => Object.assign({}, ...WOOD_DIRS.map(d => readTag(d, form)));

// The woods a vanilla blueprint can resolve to (= the override keys): every vanilla->wood conversion target across
// the base + AFC + Beneath rule files, restricted to real wood-pool members (drops mosaic/workbench artifacts).
const realWoods = new Set(Object.keys(mergedWood('planks')));
const RULE_FILES = [path.join(SUB(MAIN), 'tfc_wood.json'), path.join(SUB(AFC), 'afc.json'), path.join(SUB(BENEATH), 'beneath.json')];
const WOOD_SRC = [...new Set(RULE_FILES.flatMap(f => fs.existsSync(f)
  ? (JSON.parse(fs.readFileSync(f, 'utf8')).replacements || [])
      .filter(e => e.from && e.from.startsWith('minecraft:') && /:wood\/(planks|log)\//.test(e.to || ''))
      .map(e => keyOf(e.to))
  : []))].filter(w => realWoods.has(w)).sort();

const ROCK_FORMS = fs.readdirSync(path.join(SUBST(MAIN), 'rock')).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5));
const ROCK_DIR = path.join(SUBST(MAIN), 'rock'), FV_DIR = path.join(SUBST(MAIN), 'firmavanilla'), SOIL_DIR = path.join(SUBST(MAIN), 'soil');

const GROUPS = [
  // Rocks include firmavanilla's deepslate tile forms, and basalt is a source (vanilla deepslate -> basalt), so a
  // rock preset re-styles a deepslate build too.
  { folder: 'rocks', out: OUT(MAIN), tags: [...ROCK_FORMS, ...TILE_FORMS],
    dirOf: f => TILE_FORMS.includes(f) ? FV_DIR : ROCK_DIR,
    sources: ['dacite', 'granite', 'diorite', 'andesite', 'basalt'], iconTpl: r => `tfc:rock/raw/${r}`, nameKey: r => `mctfc.preset.${r}` },
  { folder: 'dirt', out: OUT(MAIN), tags: SOIL_FORMS, dirOf: () => SOIL_DIR,
    sources: ['loam'], iconTpl: s => `tfc:dirt/${s}`, nameKey: s => `mctfc.preset.soil.${s}` },

  // Wood: union-of-namespaces sources -> base TFC / AFC / Beneath wood targets (target tags pick the namespace).
  ...[['planks', PLANK_FORMS, 'planks'], ['logs', LOG_FORMS, 'log']].flatMap(([folder, forms, iconForm]) =>
    [[OUT(MAIN), path.join(SUBST(MAIN), 'wood')], [OUT(AFC), path.join(SUBST(AFC), 'wood')], [OUT(BENEATH), path.join(SUBST(BENEATH), 'wood')]]
      .map(([out, tgtDir]) => ({
        folder, out, wood: true, tgtDir, tags: forms, sources: WOOD_SRC,
        iconForm, nameKey: w => `mctfc.preset.wood.${w}`,
      }))),
];

const title = (k) => k.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
const lang = {};
const counts = {};

for (const g of GROUPS) {
  const srcMaps = {}, tgtMaps = {};
  for (const t of g.tags) {
    if (g.wood) { srcMaps[t] = mergedWood(t); tgtMaps[t] = readTag(g.tgtDir, t); }
    else { srcMaps[t] = tgtMaps[t] = readTag(g.dirOf(t), t); }
  }
  const targets = [...new Set(g.tags.flatMap(t => Object.keys(tgtMaps[t])))].sort();
  fs.mkdirSync(path.join(g.out, g.folder), { recursive: true });

  for (const target of targets) {
    const picks = [];
    for (const form of g.tags) {
      const to = tgtMaps[form][target];
      if (!to) continue;
      for (const s of g.sources) {
        const from = srcMaps[form][s];
        if (from && from !== to) picks.push({ from, to });
      }
    }
    if (!picks.length) continue; // e.g. the loam dirt preset is all identity -> skip
    const icon = g.iconTpl ? g.iconTpl(target) : tgtMaps[g.iconForm][target];
    fs.writeFileSync(path.join(g.out, g.folder, `${target}.json`),
      JSON.stringify({ name: g.nameKey(target), icon, picks }, null, 2) + '\n');
    lang[g.nameKey(target)] = title(target);
    const key = `${path.relative(RES, g.out)}/${g.folder}`;
    counts[key] = (counts[key] || 0) + 1;
  }
}

console.error(`Wood source set (${WOOD_SRC.length}): ${WOOD_SRC.join(' ')}`);
for (const [k, v] of Object.entries(counts)) console.error(`  ${v}\t${k}`);
console.error(`Total: ${Object.values(counts).reduce((a, b) => a + b, 0)} presets, ${Object.keys(lang).length} lang keys.`);
for (const [k, v] of Object.entries(lang).sort()) console.log(`  ${JSON.stringify(k)}: ${JSON.stringify(v)},`);
NODE
