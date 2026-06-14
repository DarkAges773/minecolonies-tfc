#!/usr/bin/env bash
# One-off generator for the TFC default block-substitution rules + candidate-pool tags.
# Emits static JSON under compat/src/main/resources/data/mctfc/. Re-run if TFC's rock/wood set changes.
set -euo pipefail

RESROOT="$(cd "$(dirname "$0")" && pwd)/src/main/resources"
RES="$RESROOT/data/mctfc"
SUB="$RES/block_substitutions"
TAGS="$RES/tags/blocks/subst"
# Optional Beneath datapack (loaded only when the 'beneath' mod is present — see BeneathDataPack).
BENEATH_SUB="$RESROOT/beneath_datapack/data/mctfc/block_substitutions"

ROCKS="andesite basalt chalk chert claystone conglomerate dacite diorite dolomite gabbro gneiss granite limestone marble phyllite quartzite rhyolite schist shale slate"
WOODS="acacia ash aspen birch blackwood chestnut douglas_fir hickory kapok mangrove maple oak palm pine rosewood sequoia spruce sycamore white_cedar willow"

mkdir -p "$SUB" "$TAGS/wood" "$TAGS/rock"

# --- helpers ----------------------------------------------------------------
# emit a block tag json from a newline list of block ids
emit_tag() { # $1=file  (ids on stdin)
  local file="$1"; shift
  { echo '{'; echo '  "replace": false,'; echo '  "values": ['
    local first=1
    while IFS= read -r id; do [ -z "$id" ] && continue
      if [ $first -eq 1 ]; then first=0; else echo ','; fi
      printf '    "%s"' "$id"
    done
    echo; echo '  ]'; echo '}'
  } > "$file"
}

# --- WOOD candidate-pool tags ----------------------------------------------
for form in planks log wood stripped_log stripped_wood leaves chest trapped_chest lectern; do
  for w in $WOODS; do echo "tfc:wood/$form/$w"; done | emit_tag "$TAGS/wood/$form.json"
done
for suf in stairs slab fence fence_gate door trapdoor button pressure_plate workbench sign wall_sign; do
  for w in $WOODS; do echo "tfc:wood/planks/${w}_${suf}"; done | emit_tag "$TAGS/wood/$suf.json"
done
# hanging signs have a metal axis (default copper) -> pools offer the wood re-pick, keeping copper.
for form in hanging_sign wall_hanging_sign; do
  for w in $WOODS; do echo "tfc:wood/planks/$form/copper/$w"; done | emit_tag "$TAGS/wood/$form.json"
done
# decorative bookshelves are firmavanilla's (full-block enchanting bookshelf per wood; TFC only ships the
# chiseled 6-slot bookshelf) -> pool of all TFC-wood variants for the GUI re-pick.
for w in $WOODS; do echo "firmavanilla:bookshelf/$w"; done | emit_tag "$TAGS/wood/decorative_bookshelf.json"

# --- ROCK candidate-pool tags ----------------------------------------------
# full-block forms with stairs/slab/wall
for form in raw bricks smooth mossy_bricks cracked_bricks; do
  for r in $ROCKS; do echo "tfc:rock/$form/$r"; done | emit_tag "$TAGS/rock/$form.json"
  for suf in stairs slab wall; do
    for r in $ROCKS; do echo "tfc:rock/$form/${r}_${suf}"; done | emit_tag "$TAGS/rock/${form}_${suf}.json"
  done
done
# cobble + mossy_cobble: full block is the non-falling mortared twin (reuse firmavanilla:mortared_cobblestone);
# only the stairs/slab/wall sub-forms (which don't landslide) get plain-TFC pools here.
for form in cobble mossy_cobble; do
  for suf in stairs slab wall; do
    for r in $ROCKS; do echo "tfc:rock/$form/${r}_${suf}"; done | emit_tag "$TAGS/rock/${form}_${suf}.json"
  done
done
# chiseled + button + pressure_plate + gravel: single block, no sub-forms
for r in $ROCKS; do echo "tfc:rock/gravel/$r"; done | emit_tag "$TAGS/rock/gravel.json"
for r in $ROCKS; do echo "tfc:rock/chiseled/$r"; done | emit_tag "$TAGS/rock/chiseled.json"
for r in $ROCKS; do echo "tfc:rock/button/$r"; done | emit_tag "$TAGS/rock/button.json"
for r in $ROCKS; do echo "tfc:rock/pressure_plate/$r"; done | emit_tag "$TAGS/rock/pressure_plate.json"

# --- SANDSTONE candidate-pool tags (TFC colored sandstones; vanilla stays the default) ----------
SAND_COLORS="black brown green pink red white yellow"
mkdir -p "$TAGS/sandstone"
for form in raw_sandstone smooth_sandstone cut_sandstone; do
  short="${form%_sandstone}"   # raw / smooth / cut
  for c in $SAND_COLORS; do echo "tfc:$form/$c"; done | emit_tag "$TAGS/sandstone/$short.json"
  for suf in stairs slab wall; do
    for c in $SAND_COLORS; do echo "tfc:$form/${c}_${suf}"; done | emit_tag "$TAGS/sandstone/${short}_${suf}.json"
  done
done
# chiseled sandstone is firmavanilla's (TFC ships none) — a pool of all 7 generated colours.
for c in $SAND_COLORS; do echo "firmavanilla:chiseled_sandstone/$c"; done | emit_tag "$TAGS/sandstone/chiseled.json"
# sand candidate-pool tag (all TFC colored sands)
for c in $SAND_COLORS; do echo "tfc:sand/$c"; done | emit_tag "$TAGS/sand.json"

# --- rule emission helpers --------------------------------------------------
# print a fixed rule line:  from -> to
RULES=""
fixed() { RULES="$RULES    { \"from\": \"$1\", \"to\": \"$2\" },"$'\n'; }
# fixed rule with an explicit conflict priority (higher wins) — used by optional override packs:  from -> to @prio
fixedp() { RULES="$RULES    { \"from\": \"$1\", \"to\": \"$2\", \"priority\": $3 },"$'\n'; }
# print a candidate-pool rule: from_tag -> to_tag (same pool)
pool() { RULES="$RULES    { \"from_tag\": \"$1\", \"to_tag\": \"$1\" },"$'\n'; }
# pool keyed on a specific vanilla block (no implicit swap; default stays vanilla, player may pick from $2)
canpool() { RULES="$RULES    { \"from\": \"$1\", \"to_tag\": \"$2\" },"$'\n'; }

write_file() { # $1=file  $2=comment   (RULES global, trailing comma trimmed)
  local body="${RULES%,$'\n'}"
  { echo '{'
    echo "  \"__comment\": \"$2\","
    echo '  "replacements": ['
    echo "$body"
    echo '  ]'
    echo '}'
  } > "$1"
  RULES=""
}

# --- WOOD rules -------------------------------------------------------------
# vanilla wood -> tfc wood, chosen so the TFC wood resembles the vanilla counterpart.
# (crimson/warped are NOT mapped to TFC here — Beneath adds real crimson/warped wood, so those rules live in
#  the optional Beneath datapack instead; see the BENEATH section below.)
wood_map() { case "$1" in
  oak) echo oak;; acacia) echo acacia;; mangrove) echo mangrove;;
  spruce) echo pine;; birch) echo douglas_fir;; jungle) echo spruce;;
  dark_oak) echo hickory;; cherry) echo kapok;; bamboo) echo palm;; esac; }

# all the planks-suffixed forms a wood shares (stairs/slab/fence/.../pressure_plate)
plank_forms() {
  local v=$1 t=$2
  fixed "minecraft:${v}_stairs"          "tfc:wood/planks/${t}_stairs"
  fixed "minecraft:${v}_slab"            "tfc:wood/planks/${t}_slab"
  fixed "minecraft:${v}_fence"           "tfc:wood/planks/${t}_fence"
  fixed "minecraft:${v}_fence_gate"      "tfc:wood/planks/${t}_fence_gate"
  fixed "minecraft:${v}_door"            "tfc:wood/planks/${t}_door"
  fixed "minecraft:${v}_trapdoor"        "tfc:wood/planks/${t}_trapdoor"
  fixed "minecraft:${v}_button"          "tfc:wood/planks/${t}_button"
  fixed "minecraft:${v}_pressure_plate"  "tfc:wood/planks/${t}_pressure_plate"
  fixed "minecraft:${v}_sign"            "tfc:wood/planks/${t}_sign"
  fixed "minecraft:${v}_wall_sign"       "tfc:wood/planks/${t}_wall_sign"
}

# hanging signs: TFC (and Beneath) key them as hanging_sign/<metal>/<wood>; we default the metal to copper and
# map the wood per the wood map. $3 = namespace (tfc by default; 'beneath' for the optional nether-wood pack).
hanging_signs() {
  local v=$1 t=$2 ns=${3:-tfc}
  fixed "minecraft:${v}_hanging_sign"      "${ns}:wood/planks/hanging_sign/copper/${t}"
  fixed "minecraft:${v}_wall_hanging_sign" "${ns}:wood/planks/wall_hanging_sign/copper/${t}"
}

# a standard wood: planks + log/wood (noun differs: log/wood overworld, stem/hyphae nether) + plank forms
wood_rules() {
  local v=$1 t=$2 logn=$3 woodn=$4
  fixed "minecraft:${v}_planks"            "tfc:wood/planks/$t"
  fixed "minecraft:${v}_${logn}"           "tfc:wood/log/$t"
  fixed "minecraft:${v}_${woodn}"          "tfc:wood/wood/$t"
  fixed "minecraft:stripped_${v}_${logn}"  "tfc:wood/stripped_log/$t"
  fixed "minecraft:stripped_${v}_${woodn}" "tfc:wood/stripped_wood/$t"
  fixed "minecraft:${v}_leaves"            "tfc:wood/leaves/$t"
  plank_forms "$v" "$t"
  hanging_signs "$v" "$t"
}

for v in oak spruce birch jungle acacia dark_oak mangrove cherry; do wood_rules "$v" "$(wood_map "$v")" log  wood;   done

# bamboo is special: no log/wood (uses *_block), plus a mosaic family -> TFC palm mosaic
B="$(wood_map bamboo)"
fixed "minecraft:bamboo_planks"          "tfc:wood/planks/$B"
fixed "minecraft:bamboo_block"           "tfc:wood/log/$B"
fixed "minecraft:stripped_bamboo_block"  "tfc:wood/stripped_log/$B"
fixed "minecraft:bamboo_mosaic"          "tfc:wood/planks/${B}_mosaic"
fixed "minecraft:bamboo_mosaic_stairs"   "tfc:wood/planks/${B}_mosaic_stairs"
fixed "minecraft:bamboo_mosaic_slab"     "tfc:wood/planks/${B}_mosaic_slab"
plank_forms bamboo "$B"
hanging_signs bamboo "$B"

# singletons: vanilla has one variant -> default oak; the pool lets the player re-pick the wood
fixed "minecraft:chest"          "tfc:wood/chest/oak"
fixed "minecraft:trapped_chest"  "tfc:wood/trapped_chest/oak"
fixed "minecraft:crafting_table" "tfc:wood/planks/oak_workbench"
fixed "minecraft:lectern"        "tfc:wood/lectern/oak"
# vanilla decorative bookshelf -> firmavanilla's full-block enchanting bookshelf (oak default + any-wood pool).
# TFC/AFC/Beneath only add the chiseled 6-slot bookshelf; firmavanilla adds the vanilla-style one per wood.
fixed "minecraft:bookshelf"      "firmavanilla:bookshelf/oak"

# saplings (potted + standing) -> TFC saplings (per wood map); implicit, AFC overrides them at priority 1.
for v in oak spruce birch jungle acacia dark_oak cherry; do
  fixed "minecraft:${v}_sapling"        "tfc:wood/sapling/$(wood_map "$v")"
  fixed "minecraft:potted_${v}_sapling" "tfc:wood/potted_sapling/$(wood_map "$v")"
done
fixed "minecraft:mangrove_propagule"        "tfc:wood/sapling/mangrove"
fixed "minecraft:potted_mangrove_propagule" "tfc:wood/potted_sapling/mangrove"

# candidate pools (player re-picks the wood per form, keyed on the converted TFC block)
for form in planks log wood stripped_log stripped_wood leaves stairs slab fence fence_gate door trapdoor button pressure_plate chest trapped_chest workbench sign wall_sign hanging_sign wall_hanging_sign lectern decorative_bookshelf; do
  pool "mctfc:subst/wood/$form"
done
write_file "$SUB/tfc_wood.json" "TFC wood substitutions: vanilla -> look-alike TFC wood (spruce->pine, birch->douglas_fir, jungle->spruce, dark_oak->hickory, cherry->kapok, bamboo->palm; oak/acacia/mangrove keep their name), across all forms incl. leaves, chest/workbench, signs/wall-signs, and hanging/wall-hanging signs (copper metal default). Bamboo also maps its mosaic family to TFC palm mosaic. Plus per-form candidate pools so the player can pick any TFC wood. The nether woods (crimson/warped) are handled by the optional Beneath datapack instead. Generated by gen_tfc_substitutions.sh."

# --- BENEATH optional datapack: vanilla nether wood -> Beneath's crimson/warped wood (1:1) ----------
# Written to beneath_datapack/, which BeneathDataPack registers only when the 'beneath' mod is loaded.
mkdir -p "$BENEATH_SUB"
beneath_wood() { # $1 = vanilla nether wood (crimson|warped); maps 1:1 to the same Beneath wood
  local v=$1
  fixed "minecraft:${v}_planks"             "beneath:wood/planks/$v"
  fixed "minecraft:${v}_stem"               "beneath:wood/log/$v"
  fixed "minecraft:${v}_hyphae"             "beneath:wood/wood/$v"
  fixed "minecraft:stripped_${v}_stem"      "beneath:wood/stripped_log/$v"
  fixed "minecraft:stripped_${v}_hyphae"    "beneath:wood/stripped_wood/$v"
  fixed "minecraft:${v}_stairs"             "beneath:wood/planks/${v}_stairs"
  fixed "minecraft:${v}_slab"               "beneath:wood/planks/${v}_slab"
  fixed "minecraft:${v}_fence"              "beneath:wood/planks/${v}_fence"
  fixed "minecraft:${v}_fence_gate"         "beneath:wood/planks/${v}_fence_gate"
  fixed "minecraft:${v}_door"               "beneath:wood/planks/${v}_door"
  fixed "minecraft:${v}_trapdoor"           "beneath:wood/planks/${v}_trapdoor"
  fixed "minecraft:${v}_button"             "beneath:wood/planks/${v}_button"
  fixed "minecraft:${v}_pressure_plate"     "beneath:wood/planks/${v}_pressure_plate"
  fixed "minecraft:${v}_sign"               "beneath:wood/planks/${v}_sign"
  fixed "minecraft:${v}_wall_sign"          "beneath:wood/planks/${v}_wall_sign"
  hanging_signs "$v" "$v" beneath
}
beneath_wood crimson
beneath_wood warped
write_file "$BENEATH_SUB/beneath.json" "Beneath-only substitutions (this whole datapack loads only when the 'beneath' mod is present, see BeneathDataPack). Vanilla crimson/warped nether wood -> Beneath's matching crimson/warped wood, 1:1 across all forms. Generated by gen_tfc_substitutions.sh."

# Beneath's nether woods also JOIN the per-form candidate pools (so the player can re-pick them like any
# normal wood, and pick crimson<->warped<->any TFC wood). These additive (replace:false) tag files live in the
# Beneath pack, so they merge into the base mctfc:subst/wood/<form> pools only when Beneath is loaded — when it's
# absent the base pools stay TFC-only (no missing-block errors). The base candidate rules already cover them.
BENEATH_TAGS="$RESROOT/beneath_datapack/data/mctfc/tags/blocks/subst/wood"
mkdir -p "$BENEATH_TAGS"
for form in planks log wood stripped_log stripped_wood chest trapped_chest lectern; do
  { echo "beneath:wood/$form/crimson"; echo "beneath:wood/$form/warped"; } | emit_tag "$BENEATH_TAGS/$form.json"
done
for suf in stairs slab fence fence_gate door trapdoor button pressure_plate workbench sign wall_sign; do
  { echo "beneath:wood/planks/crimson_${suf}"; echo "beneath:wood/planks/warped_${suf}"; } | emit_tag "$BENEATH_TAGS/$suf.json"
done
# Beneath hanging signs join the base hanging-sign pools too (copper metal default, matching TFC).
for form in hanging_sign wall_hanging_sign; do
  { echo "beneath:wood/planks/$form/copper/crimson"; echo "beneath:wood/planks/$form/copper/warped"; } | emit_tag "$BENEATH_TAGS/$form.json"
done
# Beneath decorative bookshelves (firmavanilla blocks for crimson/warped) join the decorative-bookshelf pool.
{ echo "firmavanilla:bookshelf/crimson"; echo "firmavanilla:bookshelf/warped"; } | emit_tag "$BENEATH_TAGS/decorative_bookshelf.json"
# Beneath full-block woods as legal Domum Ornamentum skins (only when Beneath is loaded). Same DO material tags.
BENEATH_DOTAGS="$RESROOT/beneath_datapack/data/domum_ornamentum/tags/blocks"
mkdir -p "$BENEATH_DOTAGS"
beneath_do_materials() { for w in crimson warped; do for f in planks log wood stripped_log stripped_wood; do echo "beneath:wood/$f/$w"; done; done; }
for t in default slab_materials stairs_materials wall_materials; do
  { beneath_do_materials
    if [ "$t" = "slab_materials" ]; then echo "firmavanilla:bookshelf/crimson"; echo "firmavanilla:bookshelf/warped"; fi
  } | emit_tag "$BENEATH_DOTAGS/$t.json"
done
# Beneath decorative bookshelves join the MineColonies integration tags (only when Beneath is loaded).
BENEATH_MCTAGS="$RESROOT/beneath_datapack/data/minecolonies/tags"
mkdir -p "$BENEATH_MCTAGS/items" "$BENEATH_MCTAGS/blocks"
{ echo "firmavanilla:bookshelf/crimson"; echo "firmavanilla:bookshelf/warped"; } | emit_tag "$BENEATH_MCTAGS/items/reduceable_product_excluded.json"
{ echo "firmavanilla:bookshelf/crimson"; echo "firmavanilla:bookshelf/warped"; } | emit_tag "$BENEATH_MCTAGS/blocks/tier2blocks.json"

# --- ARBORFIRMACRAFT (AFC) optional datapack: vanilla wood -> AFC wood, overriding the base TFC mapping ------
# Written to afc_datapack/, which AfcDataPack registers only when the 'afc' mod is present. The overrides use
# priority 1 so they beat the base tfc_wood.json rules (priority 0) when AFC is loaded; absent AFC, this pack
# isn't registered and the base TFC mapping applies. AFC has NO hanging signs, so those stay on the base TFC
# mapping (not overridden here).
AFC_SUB="$RESROOT/afc_datapack/data/mctfc/block_substitutions"
AFC_TAGS="$RESROOT/afc_datapack/data/mctfc/tags/blocks/subst/wood"
mkdir -p "$AFC_SUB" "$AFC_TAGS"
AFC_WOODS="baobab cypress eucalyptus fig hevea ipe ironwood mahogany teak tualang"

# AFC plank-suffixed forms at priority 1 (incl. hanging signs — AFC has them, copper metal default like TFC)
afc_plank_forms() {
  local v=$1 t=$2
  fixedp "minecraft:${v}_stairs"            "afc:wood/planks/${t}_stairs"                   1
  fixedp "minecraft:${v}_slab"              "afc:wood/planks/${t}_slab"                     1
  fixedp "minecraft:${v}_fence"             "afc:wood/planks/${t}_fence"                    1
  fixedp "minecraft:${v}_fence_gate"        "afc:wood/planks/${t}_fence_gate"               1
  fixedp "minecraft:${v}_door"              "afc:wood/planks/${t}_door"                     1
  fixedp "minecraft:${v}_trapdoor"          "afc:wood/planks/${t}_trapdoor"                 1
  fixedp "minecraft:${v}_button"            "afc:wood/planks/${t}_button"                   1
  fixedp "minecraft:${v}_pressure_plate"    "afc:wood/planks/${t}_pressure_plate"           1
  fixedp "minecraft:${v}_sign"              "afc:wood/planks/${t}_sign"                     1
  fixedp "minecraft:${v}_wall_sign"         "afc:wood/planks/${t}_wall_sign"                1
  fixedp "minecraft:${v}_hanging_sign"      "afc:wood/planks/hanging_sign/copper/${t}"      1
  fixedp "minecraft:${v}_wall_hanging_sign" "afc:wood/planks/wall_hanging_sign/copper/${t}" 1
}
# a full AFC wood mapping: planks + log/wood + stripped + leaves + the plank forms (priority 1)
afc_wood() { # $1=vanilla  $2=afc wood
  local v=$1 t=$2
  fixedp "minecraft:${v}_planks"           "afc:wood/planks/$t"          1
  fixedp "minecraft:${v}_log"              "afc:wood/log/$t"             1
  fixedp "minecraft:${v}_wood"             "afc:wood/wood/$t"            1
  fixedp "minecraft:stripped_${v}_log"     "afc:wood/stripped_log/$t"    1
  fixedp "minecraft:stripped_${v}_wood"    "afc:wood/stripped_wood/$t"   1
  fixedp "minecraft:${v}_leaves"           "afc:wood/leaves/$t"          1
  afc_plank_forms "$v" "$t"
}
afc_wood birch  eucalyptus
afc_wood spruce cypress
afc_wood cherry  fig
afc_wood acacia  baobab
afc_wood jungle  teak
# sapling overrides (potted + standing, priority 1) — same wood mapping as above
fixedp "minecraft:birch_sapling"         "afc:wood/sapling/eucalyptus"        1
fixedp "minecraft:spruce_sapling"        "afc:wood/sapling/cypress"           1
fixedp "minecraft:cherry_sapling"        "afc:wood/sapling/fig"               1
fixedp "minecraft:acacia_sapling"        "afc:wood/sapling/baobab"            1
fixedp "minecraft:jungle_sapling"        "afc:wood/sapling/teak"              1
fixedp "minecraft:potted_birch_sapling"  "afc:wood/potted_sapling/eucalyptus" 1
fixedp "minecraft:potted_spruce_sapling" "afc:wood/potted_sapling/cypress"    1
fixedp "minecraft:potted_cherry_sapling" "afc:wood/potted_sapling/fig"        1
fixedp "minecraft:potted_acacia_sapling" "afc:wood/potted_sapling/baobab"     1
fixedp "minecraft:potted_jungle_sapling" "afc:wood/potted_sapling/teak"       1
write_file "$AFC_SUB/afc.json" "AFC-only substitutions (this whole datapack loads only when the 'afc'/ArborFirmaCraft mod is present, see AfcDataPack). Vanilla wood -> AFC wood (birch->eucalyptus, spruce->cypress, cherry->fig, acacia->baobab, jungle->teak) at priority 1, overriding the base TFC wood mapping (tfc_wood.json, priority 0) when AFC is present. Covers all forms incl. leaves and hanging/wall-hanging signs (copper metal default). Generated by gen_tfc_substitutions.sh."

# AFC woods JOIN the per-form candidate pools (additive replace:false, only loaded with AFC), so any wood can be
# re-picked to an AFC wood and vice-versa. The base candidate rules already cover the merged tags.
for form in planks log wood stripped_log stripped_wood leaves chest trapped_chest lectern; do
  for w in $AFC_WOODS; do echo "afc:wood/$form/$w"; done | emit_tag "$AFC_TAGS/$form.json"
done
for suf in stairs slab fence fence_gate door trapdoor button pressure_plate workbench sign wall_sign; do
  for w in $AFC_WOODS; do echo "afc:wood/planks/${w}_${suf}"; done | emit_tag "$AFC_TAGS/$suf.json"
done
for form in hanging_sign wall_hanging_sign; do
  for w in $AFC_WOODS; do echo "afc:wood/planks/$form/copper/$w"; done | emit_tag "$AFC_TAGS/$form.json"
done
# AFC decorative bookshelves (firmavanilla blocks for AFC woods) join the decorative-bookshelf pool.
for w in $AFC_WOODS; do echo "firmavanilla:bookshelf/$w"; done | emit_tag "$AFC_TAGS/decorative_bookshelf.json"
# AFC full-block woods must also be legal Domum Ornamentum skins (only when AFC is loaded), so a DO block
# skinned with vanilla wood that AFC overrides resolves to a valid skin. Same DO material tags as the base.
AFC_DOTAGS="$RESROOT/afc_datapack/data/domum_ornamentum/tags/blocks"
mkdir -p "$AFC_DOTAGS"
afc_do_materials() { for w in $AFC_WOODS; do for f in planks log wood stripped_log stripped_wood leaves; do echo "afc:wood/$f/$w"; done; done; }
for t in default slab_materials stairs_materials wall_materials; do
  { afc_do_materials
    if [ "$t" = "slab_materials" ]; then for w in $AFC_WOODS; do echo "firmavanilla:bookshelf/$w"; done; fi
  } | emit_tag "$AFC_DOTAGS/$t.json"
done
# AFC decorative bookshelves join the MineColonies integration tags (only when AFC is loaded).
AFC_MCTAGS="$RESROOT/afc_datapack/data/minecolonies/tags"
mkdir -p "$AFC_MCTAGS/items" "$AFC_MCTAGS/blocks"
for w in $AFC_WOODS; do echo "firmavanilla:bookshelf/$w"; done | emit_tag "$AFC_MCTAGS/items/reduceable_product_excluded.json"
for w in $AFC_WOODS; do echo "firmavanilla:bookshelf/$w"; done | emit_tag "$AFC_MCTAGS/blocks/tier2blocks.json"

# --- FIRMALIFE optional datapack: vanilla carved pumpkin / jack o'lantern -> FirmaLife's faced variants -----
# Written to firmalife_datapack/, registered only when 'firmalife' is present (see FirmaLifeDataPack). priority 1
# so jack_o_lantern overrides the base tfc:jack_o_lantern when FirmaLife is loaded; carved_pumpkin has no base
# rule (TFC has no carved pumpkin), so this is its only mapping. FirmaLife's faced variants default to 'none'.
FL_SUB="$RESROOT/firmalife_datapack/data/mctfc/block_substitutions"
mkdir -p "$FL_SUB"
fixedp "minecraft:carved_pumpkin" "firmalife:carved_pumpkin/none" 1
fixedp "minecraft:jack_o_lantern" "firmalife:lit_pumpkin/none"    1
write_file "$FL_SUB/firmalife.json" "FirmaLife-only substitutions (this datapack loads only when the 'firmalife' mod is present, see FirmaLifeDataPack). Vanilla carved pumpkin -> FirmaLife faced carved pumpkin (no-face variant); jack o'lantern -> FirmaLife lit pumpkin (no-face), at priority 1 so it overrides the base tfc:jack_o_lantern. Generated by gen_tfc_substitutions.sh."

# --- STONE rules ------------------------------------------------------------
D=dacite
# stone -> raw dacite
fixed "minecraft:stone"                    "tfc:rock/raw/$D"
fixed "minecraft:stone_stairs"             "tfc:rock/raw/${D}_stairs"
fixed "minecraft:stone_slab"               "tfc:rock/raw/${D}_slab"
# cobblestone -> non-falling mortared dacite cobble (firmavanilla mod's runtime twin)
fixed "minecraft:cobblestone"              "firmavanilla:mortared/tfc/rock/cobble/$D"
fixed "minecraft:cobblestone_stairs"       "tfc:rock/cobble/${D}_stairs"
fixed "minecraft:cobblestone_slab"         "tfc:rock/cobble/${D}_slab"
fixed "minecraft:cobblestone_wall"         "tfc:rock/cobble/${D}_wall"
# mossy cobblestone -> non-falling mortared dacite mossy cobble (firmavanilla mod's runtime twin)
fixed "minecraft:mossy_cobblestone"        "firmavanilla:mortared/tfc/rock/mossy_cobble/$D"
fixed "minecraft:mossy_cobblestone_stairs" "tfc:rock/mossy_cobble/${D}_stairs"
fixed "minecraft:mossy_cobblestone_slab"   "tfc:rock/mossy_cobble/${D}_slab"
fixed "minecraft:mossy_cobblestone_wall"   "tfc:rock/mossy_cobble/${D}_wall"
# stone bricks -> dacite bricks
fixed "minecraft:stone_bricks"             "tfc:rock/bricks/$D"
fixed "minecraft:stone_brick_stairs"       "tfc:rock/bricks/${D}_stairs"
fixed "minecraft:stone_brick_slab"         "tfc:rock/bricks/${D}_slab"
fixed "minecraft:stone_brick_wall"         "tfc:rock/bricks/${D}_wall"
# mossy stone bricks -> dacite mossy bricks
fixed "minecraft:mossy_stone_bricks"       "tfc:rock/mossy_bricks/$D"
fixed "minecraft:mossy_stone_brick_stairs" "tfc:rock/mossy_bricks/${D}_stairs"
fixed "minecraft:mossy_stone_brick_slab"   "tfc:rock/mossy_bricks/${D}_slab"
fixed "minecraft:mossy_stone_brick_wall"   "tfc:rock/mossy_bricks/${D}_wall"
# cracked / chiseled stone bricks
fixed "minecraft:cracked_stone_bricks"     "tfc:rock/cracked_bricks/$D"
fixed "minecraft:chiseled_stone_bricks"    "tfc:rock/chiseled/$D"
# smooth stone
fixed "minecraft:smooth_stone"             "tfc:rock/smooth/$D"
fixed "minecraft:smooth_stone_slab"        "tfc:rock/smooth/${D}_slab"
# stone button + pressure plate
fixed "minecraft:stone_button"             "tfc:rock/button/$D"
fixed "minecraft:stone_pressure_plate"     "tfc:rock/pressure_plate/$D"
# gravel -> dacite gravel (single block; pool lets the player pick any rock's gravel)
fixed "minecraft:gravel"                   "tfc:rock/gravel/$D"

# Vanilla igneous decorative stones are TFC rock types -> map to the SAME rock (raw for plain, smooth for
# polished). The existing raw/smooth candidate pools then let the player re-pick any rock (explicit).
for rk in granite diorite andesite; do
  fixed "minecraft:${rk}"                  "tfc:rock/raw/${rk}"
  fixed "minecraft:${rk}_stairs"           "tfc:rock/raw/${rk}_stairs"
  fixed "minecraft:${rk}_slab"             "tfc:rock/raw/${rk}_slab"
  fixed "minecraft:${rk}_wall"             "tfc:rock/raw/${rk}_wall"
  fixed "minecraft:polished_${rk}"         "tfc:rock/smooth/${rk}"
  fixed "minecraft:polished_${rk}_stairs"  "tfc:rock/smooth/${rk}_stairs"
  fixed "minecraft:polished_${rk}_slab"    "tfc:rock/smooth/${rk}_slab"
done

# candidate pools (player re-picks the rock per form, keyed on the converted TFC block)
# cobble + mossy_cobble full blocks resolve to a mortared twin -> the mortared pool offers the rock choice
pool "firmavanilla:mortared_cobblestone"
for form in raw raw_stairs raw_slab raw_wall \
            cobble_stairs cobble_slab cobble_wall \
            mossy_cobble_stairs mossy_cobble_slab mossy_cobble_wall \
            bricks bricks_stairs bricks_slab bricks_wall \
            mossy_bricks mossy_bricks_stairs mossy_bricks_slab mossy_bricks_wall \
            cracked_bricks cracked_bricks_stairs cracked_bricks_slab cracked_bricks_wall \
            smooth smooth_stairs smooth_slab smooth_wall \
            chiseled button pressure_plate gravel; do
  pool "mctfc:subst/rock/$form"
done
write_file "$SUB/tfc_stone.json" "TFC stone substitutions: vanilla stone family -> dacite forms (closest look), cobble/mossy_cobble -> non-falling mortared twin, vanilla granite/diorite/andesite -> the same TFC rock (raw/smooth), gravel -> dacite gravel, plus per-form candidate pools so the player can pick any TFC rock. Generated by gen_tfc_substitutions.sh."

# --- SANDSTONE + SAND rules -------------------------------------------------
# Vanilla sandstone is INACCESSIBLE in TFC (TFC ships its own colored sandstones), so implicitly convert it to
# the matching TFC color (normal -> yellow, red -> red) and offer the colored pool to re-pick. TFC has no
# chiseled sandstone, so chiseled -> firmavanilla:chiseled_sandstone/<color> (generated per color from the
# vanilla creeper/wither relief recoloured onto TFC's cut sandstone; see the firmavanilla mod).
sandstone_rules() { # $1 = vanilla prefix ("" or "red_"), $2 = TFC color
  local v=$1 c=$2
  fixed "minecraft:${v}sandstone"                "tfc:raw_sandstone/$c"
  fixed "minecraft:${v}sandstone_stairs"         "tfc:raw_sandstone/${c}_stairs"
  fixed "minecraft:${v}sandstone_slab"           "tfc:raw_sandstone/${c}_slab"
  fixed "minecraft:${v}sandstone_wall"           "tfc:raw_sandstone/${c}_wall"
  fixed "minecraft:chiseled_${v}sandstone"       "firmavanilla:chiseled_sandstone/$c"  # TFC has none -> firmavanilla's
  fixed "minecraft:cut_${v}sandstone"            "tfc:cut_sandstone/$c"
  fixed "minecraft:cut_${v}sandstone_slab"       "tfc:cut_sandstone/${c}_slab"
  fixed "minecraft:smooth_${v}sandstone"         "tfc:smooth_sandstone/$c"
  fixed "minecraft:smooth_${v}sandstone_stairs"  "tfc:smooth_sandstone/${c}_stairs"
  fixed "minecraft:smooth_${v}sandstone_slab"    "tfc:smooth_sandstone/${c}_slab"
}
sandstone_rules ""   yellow
sandstone_rules red_ red
# pools keyed on the converted TFC block, so the player can re-pick any color/form
for form in raw raw_stairs raw_slab raw_wall cut cut_slab smooth smooth_stairs smooth_slab chiseled; do
  pool "mctfc:subst/sandstone/$form"
done
# sand -> matching TFC colored sand (normal -> yellow, red -> red) + pool of all colors
fixed "minecraft:sand"     "tfc:sand/yellow"
fixed "minecraft:red_sand" "tfc:sand/red"
pool  "mctfc:subst/sand"
write_file "$SUB/tfc_sandstone.json" "TFC sandstone + sand substitutions: vanilla sandstone is inaccessible in TFC, so it implicitly converts to the matching TFC colored sandstone (normal->yellow, red->red) across raw/cut/smooth forms + a colored re-pick pool. Chiseled sandstone -> firmavanilla:chiseled_sandstone/<color> (TFC ships no chiseled; firmavanilla generates it per color) + its own re-pick pool. Vanilla sand/red_sand -> TFC yellow/red sand + an all-color pool. Generated by gen_tfc_substitutions.sh."

# --- METAL rules (bars / anvils / bells) ------------------------------------
METALS="bismuth_bronze black_bronze black_steel blue_steel bronze copper red_steel steel wrought_iron"
mkdir -p "$TAGS/metal"
# candidate-pool tags
for m in $METALS; do echo "tfc:metal/bars/$m";  done | emit_tag "$TAGS/metal/bars.json"
for m in $METALS; do echo "tfc:metal/anvil/$m"; done | emit_tag "$TAGS/metal/anvil.json"
{ echo "tfc:brass_bell"; echo "tfc:bronze_bell"; }   | emit_tag "$TAGS/metal/bell.json"
# iron bars -> wrought iron bars (default); pool picks any TFC metal bars
fixed "minecraft:iron_bars"      "tfc:metal/bars/wrought_iron"
pool  "mctfc:subst/metal/bars"
# anvils (all vanilla damage variants) -> wrought iron anvil (default); pool picks any TFC anvil metal
fixed "minecraft:anvil"          "tfc:metal/anvil/wrought_iron"
fixed "minecraft:chipped_anvil"  "tfc:metal/anvil/wrought_iron"
fixed "minecraft:damaged_anvil"  "tfc:metal/anvil/wrought_iron"
pool  "mctfc:subst/metal/anvil"
# bells: vanilla persists (no implicit swap), but offer a TFC bell (brass/bronze) as an explicit pick
canpool "minecraft:bell"         "mctfc:subst/metal/bell"
write_file "$SUB/tfc_metal.json" "TFC metal substitutions: iron bars -> wrought iron bars (default) + any-metal pool; vanilla anvils (incl. chipped/damaged) -> wrought iron anvil (default) + any-metal pool; vanilla bell stays vanilla but offers a TFC bell (brass/bronze) pick. Generated by gen_tfc_substitutions.sh."

# --- MUD rules --------------------------------------------------------------
MUD_VARIANTS="loam sandy_loam silt silty_loam"
mkdir -p "$TAGS/mud"
for v in $MUD_VARIANTS; do echo "tfc:mud_bricks/$v"; done | emit_tag "$TAGS/mud/bricks.json"
for suf in stairs slab wall; do
  for v in $MUD_VARIANTS; do echo "tfc:mud_bricks/${v}_${suf}"; done | emit_tag "$TAGS/mud/bricks_${suf}.json"
done
# mud bricks -> loam mud bricks (default) + any-variant pool, across stairs/slab/wall
fixed "minecraft:mud_bricks"        "tfc:mud_bricks/loam"
fixed "minecraft:mud_brick_stairs"  "tfc:mud_bricks/loam_stairs"
fixed "minecraft:mud_brick_slab"    "tfc:mud_bricks/loam_slab"
fixed "minecraft:mud_brick_wall"    "tfc:mud_bricks/loam_wall"
pool  "mctfc:subst/mud/bricks"
pool  "mctfc:subst/mud/bricks_stairs"
pool  "mctfc:subst/mud/bricks_slab"
pool  "mctfc:subst/mud/bricks_wall"
# packed mud -> TFC smooth mud bricks (single block; implicit, no pool)
fixed "minecraft:packed_mud"        "tfc:smooth_mud_bricks"
write_file "$SUB/tfc_mud.json" "TFC mud substitutions: vanilla mud bricks (+stairs/slab/wall) -> loam mud bricks (default) + any-variant candidate pool; packed mud -> tfc:smooth_mud_bricks (implicit, single block). Generated by gen_tfc_substitutions.sh."

# --- DOMUM ORNAMENTUM material tags: make our TFC full-block targets legal DO skins ------------------
# DomumMaterialRewriter only substitutes a DO material (the full block in a DO block's textureData NBT) to a
# block in the component's valid-skins tag. DO's tags are curated and TFC ships none, so add our full-block
# building materials here (additive, replace:false). 'default' is referenced by most components (panels,
# bricks, framed, fences, fence_gates, posts, pillars, doors, trapdoors, timber-frames); slab/stairs/wall
# _materials are independent lists, so extend those too. Only FULL-cube materials (DO skins are full blocks);
# falling cobble is excluded — cobblestone substitutes to the runtime mortared twins (firmavanilla mod), which
# its MortaredCobbleData adds to these same DO tags.
DOTAGS="$RESROOT/data/domum_ornamentum/tags/blocks"
mkdir -p "$DOTAGS"
do_materials() { # every TFC full-block building material we substitute to
  for r in $ROCKS; do for f in raw smooth bricks mossy_bricks cracked_bricks chiseled gravel; do echo "tfc:rock/$f/$r"; done; done
  for w in $WOODS; do for f in planks log wood stripped_log stripped_wood leaves; do echo "tfc:wood/$f/$w"; done; done
  for v in $MUD_VARIANTS; do echo "tfc:mud_bricks/$v"; done
  echo "tfc:smooth_mud_bricks"
  for c in $SAND_COLORS; do for f in raw_sandstone smooth_sandstone cut_sandstone; do echo "tfc:$f/$c"; done; done
}
for t in default slab_materials stairs_materials wall_materials; do
  { do_materials
    # firmavanilla decorative bookshelves are a valid DO SLAB skin material (full cube) — slab tag only.
    if [ "$t" = "slab_materials" ]; then for w in $WOODS; do echo "firmavanilla:bookshelf/$w"; done; fi
  } | emit_tag "$DOTAGS/$t.json"
done

# --- MineColonies integration tags for the firmavanilla decorative bookshelves ------------------------------
# Exclude them from the builder's "reduceable products" (so a placed bookshelf is requested as itself, not
# reduced to its crafting ingredients) and mark them tier-2 building blocks (matching vanilla bookshelf). TFC
# woods here; AFC/Beneath join via their datapacks above. MineColonies-namespaced, hence in :compat.
MCTAGS="$RESROOT/data/minecolonies/tags"
mkdir -p "$MCTAGS/items" "$MCTAGS/blocks"
for w in $WOODS; do echo "firmavanilla:bookshelf/$w"; done | emit_tag "$MCTAGS/items/reduceable_product_excluded.json"
for w in $WOODS; do echo "firmavanilla:bookshelf/$w"; done | emit_tag "$MCTAGS/blocks/tier2blocks.json"

echo "Generated:"
echo "  rules: $(ls "$SUB"/tfc_*.json)"
echo "  tags : $(find "$TAGS" -name '*.json' | wc -l) files"
