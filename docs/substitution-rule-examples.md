# Block-substitution rule examples (`:replacements`)

These are **reference examples** for authoring substitution rules for the `structurizereplacements`
engine. They used to ship as an active datapack file
(`data/structurizereplacements/block_substitutions/examples.json`), but a published library must ship
**no active rules** — live rules rewrite every consumer's blueprints, and a fixed `to` example
(e.g. `oak_planks -> spruce_planks`) shadows downstream rules like `:compat`'s TFC wood/stone swaps.
So the examples now live here as copy-paste documentation only.

To try the engine in the standalone `:replacements:runClient`, drop any of the snippets below into a
file under `data/<your_namespace>/block_substitutions/<name>.json` (in a datapack or your own mod's
resources) and `/reload`.

## Rule format

Each entry in the `"replacements"` array has exactly one **source** and one **target**:

- Source: `"from"` (a block id) **or** `"from_tag"` (a block tag id).
- Target: `"to"` (a fixed block id → automatic swap at placement) **or** `"to_tag"` (a block tag id →
  an **interactive candidate pool** the player picks from in the build tool's *Replace* GUI; substitutes
  nothing on its own).
- Optional `"apply_properties"`: an object of blockstate property name → value, stamped onto the result
  after the swap (e.g. `{"no_gravity":"true"}` so a substituted TFC cobble is placed non-falling). A
  property the target block doesn't define is skipped.
- Optional `"copy_properties"`: an object of **source** property name → **target** property name. The
  source state's value of the first is carried (by its serialized name) into the second on the result —
  for when the source and target name "the same" property differently (e.g. `{"half":"part"}` maps a
  vanilla `DoublePlantBlock`'s `half=lower/upper` onto a mod's two-tall plant `part=lower/upper`). Skipped
  if either property is absent or the value doesn't parse on the target.

First match wins; unknown ids are logged and skipped. Substitution is **explicit** — a rule applies only
to the block(s) it names, with no implicit cascade to sibling forms, so list each form (planks/stairs/
slabs/…) you want swapped, or offer a `to_tag` pool and let the player pick.

## Examples

### Fixed swap (automatic)

```json
{ "from": "minecraft:oak_planks", "to": "minecraft:spruce_planks" }
```

Every oak-planks block in the blueprint is placed as spruce planks. (This is the kind of fixed rule a
consumer pack like `:compat` provides — vanilla → TFC — which is why the library itself must not ship
one that would collide.)

### Interactive candidate pools (`to_tag`)

```json
{ "from_tag": "minecraft:planks",          "to_tag": "minecraft:planks" },
{ "from_tag": "minecraft:wooden_stairs",   "to_tag": "minecraft:wooden_stairs" },
{ "from_tag": "minecraft:wooden_slabs",    "to_tag": "minecraft:wooden_slabs" },
{ "from_tag": "minecraft:wooden_fences",   "to_tag": "minecraft:wooden_fences" },
{ "from_tag": "minecraft:fence_gates",     "to_tag": "minecraft:fence_gates" },
{ "from_tag": "minecraft:wooden_doors",    "to_tag": "minecraft:wooden_doors" },
{ "from_tag": "minecraft:wooden_trapdoors","to_tag": "minecraft:wooden_trapdoors" }
```

Each adds a row to the *Replace* picker (one per distinct matching source block in the schematic) and
lets the player choose any block from the candidate tag. These pools auto-rewrite nothing, so they are
safe to keep active — but they're documented here rather than shipped so the published library stays
inert by default.

### `apply_properties` (stamp blockstate after the swap)

```json
{ "from_tag": "minecraft:logs", "to_tag": "minecraft:logs",
  "apply_properties": { "axis": "x" } }
```

The picked log is stamped `axis=x`, so it places lying on its side. This is the same mechanism a TFC
pack uses for `{"no_gravity":"true"}` on a substituted cobble.

### `copy_properties` (map a differently-named property — e.g. double plants)

```json
{ "from": "minecraft:lilac", "to": "othermod:tall_lilac",
  "copy_properties": { "half": "part" } }
```

A vanilla double-tall plant stores its two halves as separate blueprint cells differing by `half`
(`lower`/`upper`). If the target mod's two-tall plant uses a different property name (`part`), a plain
swap would leave both cells at the target's default and desync the halves. `copy_properties` carries the
per-cell `half` value into `part`, keeping the lower/upper halves aligned. (Both serialize `lower`/`upper`,
so the value transfers directly.)
