#!/usr/bin/env bash
# One-off generator for the TFC default block-substitution rules + candidate-pool tags.
# Emits static JSON under compat/src/main/resources/data/mctfc/. Re-run if TFC's rock/wood set changes.
set -euo pipefail

RES="$(cd "$(dirname "$0")" && pwd)/src/main/resources/data/mctfc"
SUB="$RES/block_substitutions"
TAGS="$RES/tags/blocks/subst"

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
for form in planks log wood stripped_log stripped_wood chest trapped_chest; do
  for w in $WOODS; do echo "tfc:wood/$form/$w"; done | emit_tag "$TAGS/wood/$form.json"
done
for suf in stairs slab fence fence_gate door trapdoor button pressure_plate workbench; do
  for w in $WOODS; do echo "tfc:wood/planks/${w}_${suf}"; done | emit_tag "$TAGS/wood/$suf.json"
done

# --- ROCK candidate-pool tags ----------------------------------------------
# full-block forms with stairs/slab/wall
for form in raw bricks smooth mossy_bricks cracked_bricks; do
  for r in $ROCKS; do echo "tfc:rock/$form/$r"; done | emit_tag "$TAGS/rock/$form.json"
  for suf in stairs slab wall; do
    for r in $ROCKS; do echo "tfc:rock/$form/${r}_${suf}"; done | emit_tag "$TAGS/rock/${form}_${suf}.json"
  done
done
# cobble + mossy_cobble: full block is the non-falling mortared twin (reuse mctfc:mortared_cobblestone);
# only the stairs/slab/wall sub-forms (which don't landslide) get plain-TFC pools here.
for form in cobble mossy_cobble; do
  for suf in stairs slab wall; do
    for r in $ROCKS; do echo "tfc:rock/$form/${r}_${suf}"; done | emit_tag "$TAGS/rock/${form}_${suf}.json"
  done
done
# chiseled + button + pressure_plate: single block, no sub-forms
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

# --- rule emission helpers --------------------------------------------------
# print a fixed rule line:  from -> to
RULES=""
fixed() { RULES="$RULES    { \"from\": \"$1\", \"to\": \"$2\" },"$'\n'; }
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
wood_map() { case "$1" in
  oak) echo oak;; acacia) echo acacia;; mangrove) echo mangrove;;
  spruce) echo chestnut;; birch) echo douglas_fir;; jungle) echo spruce;;
  dark_oak) echo hickory;; cherry) echo kapok;; bamboo) echo palm;;
  crimson) echo rosewood;; warped) echo willow;; esac; }

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
}

# a standard wood: planks + log/wood (noun differs: log/wood overworld, stem/hyphae nether) + plank forms
wood_rules() {
  local v=$1 t=$2 logn=$3 woodn=$4
  fixed "minecraft:${v}_planks"            "tfc:wood/planks/$t"
  fixed "minecraft:${v}_${logn}"           "tfc:wood/log/$t"
  fixed "minecraft:${v}_${woodn}"          "tfc:wood/wood/$t"
  fixed "minecraft:stripped_${v}_${logn}"  "tfc:wood/stripped_log/$t"
  fixed "minecraft:stripped_${v}_${woodn}" "tfc:wood/stripped_wood/$t"
  plank_forms "$v" "$t"
}

for v in oak spruce birch jungle acacia dark_oak mangrove cherry; do wood_rules "$v" "$(wood_map "$v")" log  wood;   done
for v in crimson warped;                                            do wood_rules "$v" "$(wood_map "$v")" stem hyphae; done

# bamboo is special: no log/wood (uses *_block), plus a mosaic family -> TFC palm mosaic
B="$(wood_map bamboo)"
fixed "minecraft:bamboo_planks"          "tfc:wood/planks/$B"
fixed "minecraft:bamboo_block"           "tfc:wood/log/$B"
fixed "minecraft:stripped_bamboo_block"  "tfc:wood/stripped_log/$B"
fixed "minecraft:bamboo_mosaic"          "tfc:wood/planks/${B}_mosaic"
fixed "minecraft:bamboo_mosaic_stairs"   "tfc:wood/planks/${B}_mosaic_stairs"
fixed "minecraft:bamboo_mosaic_slab"     "tfc:wood/planks/${B}_mosaic_slab"
plank_forms bamboo "$B"

# singletons: vanilla has one variant -> default oak; the pool lets the player re-pick the wood
fixed "minecraft:chest"          "tfc:wood/chest/oak"
fixed "minecraft:trapped_chest"  "tfc:wood/trapped_chest/oak"
fixed "minecraft:crafting_table" "tfc:wood/planks/oak_workbench"

# candidate pools (player re-picks the wood per form, keyed on the converted TFC block)
for form in planks log wood stripped_log stripped_wood stairs slab fence fence_gate door trapdoor button pressure_plate chest trapped_chest workbench; do
  pool "mctfc:subst/wood/$form"
done
write_file "$SUB/tfc_wood.json" "TFC wood substitutions: vanilla -> look-alike TFC wood (spruce->chestnut, birch->douglas_fir, jungle->spruce, dark_oak->hickory, cherry->kapok, bamboo->palm, crimson->rosewood, warped->willow; oak/acacia/mangrove keep their name), across all forms incl. chest/workbench. Plus per-form candidate pools so the player can pick any TFC wood. Generated by gen_tfc_substitutions.sh."

# --- STONE rules ------------------------------------------------------------
D=dacite
# stone -> raw dacite
fixed "minecraft:stone"                    "tfc:rock/raw/$D"
fixed "minecraft:stone_stairs"             "tfc:rock/raw/${D}_stairs"
fixed "minecraft:stone_slab"               "tfc:rock/raw/${D}_slab"
# cobblestone -> non-falling mortared dacite cobble
fixed "minecraft:cobblestone"              "mctfc:mortared/tfc/rock/cobble/$D"
fixed "minecraft:cobblestone_stairs"       "tfc:rock/cobble/${D}_stairs"
fixed "minecraft:cobblestone_slab"         "tfc:rock/cobble/${D}_slab"
fixed "minecraft:cobblestone_wall"         "tfc:rock/cobble/${D}_wall"
# mossy cobblestone -> non-falling mortared dacite mossy cobble
fixed "minecraft:mossy_cobblestone"        "mctfc:mortared/tfc/rock/mossy_cobble/$D"
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
pool "mctfc:mortared_cobblestone"
for form in raw raw_stairs raw_slab raw_wall \
            cobble_stairs cobble_slab cobble_wall \
            mossy_cobble_stairs mossy_cobble_slab mossy_cobble_wall \
            bricks bricks_stairs bricks_slab bricks_wall \
            mossy_bricks mossy_bricks_stairs mossy_bricks_slab mossy_bricks_wall \
            cracked_bricks cracked_bricks_stairs cracked_bricks_slab cracked_bricks_wall \
            smooth smooth_stairs smooth_slab smooth_wall \
            chiseled button pressure_plate; do
  pool "mctfc:subst/rock/$form"
done
write_file "$SUB/tfc_stone.json" "TFC stone substitutions: vanilla stone family -> dacite forms (closest look), cobble/mossy_cobble -> non-falling mortared twin, vanilla granite/diorite/andesite -> the same TFC rock (raw/smooth), plus per-form candidate pools so the player can pick any TFC rock. Generated by gen_tfc_substitutions.sh."

# --- SANDSTONE rules --------------------------------------------------------
# Pool-only (no implicit swap): vanilla sandstone is craftable/accessible in TFC, so it stays the default;
# the candidate pool just lets the player optionally pick a TFC colored sandstone of the matching form.
# Both the normal and red vanilla families offer the same TFC pools (which include every color).
for v in "" red_; do
  canpool "minecraft:${v}sandstone"            "mctfc:subst/sandstone/raw"
  canpool "minecraft:${v}sandstone_stairs"     "mctfc:subst/sandstone/raw_stairs"
  canpool "minecraft:${v}sandstone_slab"       "mctfc:subst/sandstone/raw_slab"
  canpool "minecraft:${v}sandstone_wall"       "mctfc:subst/sandstone/raw_wall"
  canpool "minecraft:chiseled_${v}sandstone"   "mctfc:subst/sandstone/cut"
  canpool "minecraft:cut_${v}sandstone"        "mctfc:subst/sandstone/cut"
  canpool "minecraft:cut_${v}sandstone_slab"   "mctfc:subst/sandstone/cut_slab"
  canpool "minecraft:smooth_${v}sandstone"         "mctfc:subst/sandstone/smooth"
  canpool "minecraft:smooth_${v}sandstone_stairs"  "mctfc:subst/sandstone/smooth_stairs"
  canpool "minecraft:smooth_${v}sandstone_slab"    "mctfc:subst/sandstone/smooth_slab"
done
write_file "$SUB/tfc_sandstone.json" "TFC sandstone candidate pools: vanilla sandstone (normal + red) stays the default (it's accessible in TFC), but each variant offers a 'Replace' pool of TFC colored sandstones (raw/smooth/cut, all colors) of the matching form. Pool-only, no implicit swap. Generated by gen_tfc_substitutions.sh."

echo "Generated:"
echo "  rules: $(ls "$SUB"/tfc_*.json)"
echo "  tags : $(find "$TAGS" -name '*.json' | wc -l) files"
