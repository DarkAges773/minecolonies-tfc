#!/usr/bin/env bash
# One-off generator for TFC food-item definitions over MineColonies' cooked foods.
# Emits static JSON under compat/src/main/resources/data/mctfc/tfc/food_items/ — re-run if
# MineColonies' food set changes. See docs/compat-features.md ("MineColonies foods become TFC foods").
#
# WHY this works with no code: TFC's ForgeEventHandler.attachItemCapabilities (on
# AttachCapabilitiesEvent<ItemStack>, fired for every stack from any mod) attaches the FoodCapability
# to any item matched by a `food_items` definition's `ingredient`. The definitions live in TFC's
# DataManager folder `tfc/food_items` (note the doubled `tfc`: data/<ns>/tfc/food_items/), scanned
# across ALL namespaces — so dropping ours under data/mctfc/ converts MineColonies foods.
#
# CALIBRATION (do not lose this): :compat's MixinFoodUtils#getFoodValue HEAD-cancels for any
# FoodCapability.has() food and recomputes citizen saturation as `hunger * (1 + saturation) / 1.2`
# (times research/config). MineColonies' own food (IMinecoloniesFoodItem, no nerf) currently feeds at
# `nutrition / 1.2`. To preserve feed value we keep hunger = 4 (TFC standard) and pick saturation per
# vanilla-nutrition tier so 4*(1+sat) == nutrition:  n5 -> 0.25, n7 -> 0.75, n9 -> 1.25.
# The tier is grouped by the food's *vanilla nutrition* (5/7/9), NOT its IMinecoloniesFoodItem tier
# (a few items, e.g. kebab/mutton_dinner, have a tier that disagrees with their nutrition).
set -euo pipefail

RES="$(cd "$(dirname "$0")" && pwd)/src/main/resources/data/mctfc/tfc/food_items"
mkdir -p "$RES"

# Single tuning knob: decay speed for all converted MineColonies meals (1.0 = normal TFC decay;
# >1 faster, <1 slower). Cooked colony meals are preserved in colony storage anyway via the
# `mctfc:colony_storage` rack trait (see FoodPreservation), so 1.0 keeps them honest in transit.
DECAY=1.0

# saturation for a given vanilla nutrition (see CALIBRATION above)
sat_for() { case "$1" in 5) echo 0.25;; 7) echo 0.75;; 9) echo 1.25;; *) echo 0.5;; esac; }

# emit one food_items json.  args: name nutrition water grain fruit veg protein dairy
emit() {
  local name="$1" nutr="$2" water="$3" grain="$4" fruit="$5" veg="$6" prot="$7" dairy="$8"
  local sat; sat="$(sat_for "$nutr")"
  { printf '{\n'
    printf '  "ingredient": { "item": "minecolonies:%s" },\n' "$name"
    printf '  "hunger": 4,\n'
    printf '  "saturation": %s,\n' "$sat"
    printf '  "water": %s,\n' "$water"
    printf '  "decay_modifier": %s' "$DECAY"
    [ "$grain" != 0 ] && printf ',\n  "grain": %s'      "$grain"
    [ "$fruit" != 0 ] && printf ',\n  "fruit": %s'      "$fruit"
    [ "$veg"   != 0 ] && printf ',\n  "vegetables": %s' "$veg"
    [ "$prot"  != 0 ] && printf ',\n  "protein": %s'    "$prot"
    [ "$dairy" != 0 ] && printf ',\n  "dairy": %s'      "$dairy"
    printf '\n}\n'
  } > "$RES/$name.json"
}

# ---------------------------------------------------------------------------
# name                     nutr water grain fruit veg  prot dairy
# --- nutrition 5 (sat 0.25) ------------------------------------------------
emit cheddar_cheese          5   0    0    0    0    0    2
emit feta_cheese             5   0    0    0    0    0    2
emit cooked_rice             5   0    2    0    0    0    0
emit tofu                    5   0    0    0    0.5  1.5  0
emit flatbread               5   0    2    0    0    0    0
emit cheese_ravioli          5   0    1.5  0    0    0    1
emit chicken_broth           5   15   0    0    0    1.5  0
emit meat_ravioli            5   0    1.5  0    0    1    0
emit mint_jelly              5   0    0    1.5  0    0    0
emit mint_tea                5   25   0    0.5  0    0    0
emit polenta                 5   0    2    0    0    0    0
emit potato_soup             5   15   0    0    2    0    0
emit veggie_ravioli          5   0    1.5  0    1    0    0
emit yogurt                  5   0    0    0    0    0    2
emit squash_soup             5   15   0    0    2    0    0
emit pea_soup                5   15   0    0    2    0    0
emit corn_chowder            5   10   1.5  0    1    0    0
emit tortillas               5   0    2    0    0    0    0
emit spicy_grilled_chicken   5   0    0    0    0    2    0
# --- nutrition 7 (sat 0.75) ------------------------------------------------
emit manchet_bread           7   0    3    0    0    0    0
emit lembas_scone            7   0    2.5  0.5  0    0    0
emit muffin                  7   0    2.5  0.5  0    0    0
emit pottage                 7   15   0    0    2.5  0    0
emit pasta_plain             7   0    3    0    0    0    0
emit apple_pie               7   0    1.5  2    0    0    0
emit plain_cheesecake        7   0    1    0    0    0    2
emit baked_salmon            7   0    0    0    0    3    0
emit eggdrop_soup            7   15   0    0    0    2    0
emit fish_n_chips            7   0    0    0    1.5  2    0
emit pierogi                 7   0    2    0    1    0    0.5
emit veggie_soup             7   15   0    0    3    0    0
emit yogurt_with_berries     7   0    0    1.5  0    0    1.5
emit cabochis                7   0    0    0    2.5  1    0
emit veggie_quiche           7   0    0.5  0    2    0    1
emit rice_ball               7   0    3    0    0    0    0
emit mutton_dinner           7   0    0    0    0    3    0
emit pasta_tomato            7   0    2.5  0    1    0    0
emit cheese_pizza            7   0    2    0    0    0    1.5
emit pepper_hummus           7   0    0    0    2    1    0
emit kebab                   7   0    0    0    0.5  2.5  0
emit congee                  7   10   3    0    0    0    0
emit kimchi                  7   0    0    0    3    0    0
# --- nutrition 9 (sat 1.25) ------------------------------------------------
emit hand_pie                9   0    2    0    0    2    0
emit mintchoco_cheesecake    9   0    1    0    0    0    2.5
emit borscht                 9   15   0    0    3    0    0
emit schnitzel               9   0    1    0    0    3.5  0
emit steak_dinner            9   0    0    0    0    4    0
emit lamb_stew               9   15   0    0    1.5  3    0
emit fish_dinner             9   0    0    0    0    4    0
emit sushi_roll              9   0    1.5  0    0.5  2.5  0
emit ramen                   9   15   2.5  0    0    1.5  0
emit fried_rice              9   0    2.5  0    1    1    0
emit eggplant_dolma          9   0    1    0    3    0    0
emit stuffed_pita            9   0    2    0    1    1.5  0
emit mushroom_pizza          9   0    2    0    1.5  0    1
emit pita_hummus             9   0    2    0    1    1.5  0
emit spicy_eggplant          9   0    0    0    3.5  0    0
emit stew_trencher           9   0    1.5  0    1    2.5  0
emit stuffed_pepper          9   0    1    0    2.5  1    0
emit tacos                   9   0    1.5  0    1    2    0

echo "Wrote $(ls -1 "$RES" | wc -l) food_items JSON files to $RES"
