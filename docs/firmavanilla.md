# firmavanilla — TFC Vanilla Building Blocks

A **standalone** TerraFirmaCraft companion (`firmavanilla`, package `com.firmavanilla`) that ships
TFC-palette decorative building blocks — the variants bare TFC doesn't include but MineColonies (and any
TFC) builds want. Depends **only on TerraFirmaCraft** (no MineColonies, no `:replacements`). The
MineColonies × TerraFirmaCraft bridge (`:compat`/`mctfc`) hard-depends on this mod and points its
Structurize substitution rules at these blocks.

Two kinds of content live here, registered very differently:

| | Cemented cobble | Chiseled sandstone |
|---|---|---|
| Source | mirrors an **existing** TFC/vanilla cobble | **net-new** form TFC lacks |
| Set | unknown at build time (registry scan) | fixed (7 TFC sand colours) |
| Registration | runtime `RegisterEvent` scan | static `DeferredRegister` |
| Assets | none (model delegated to source) | machine-generated, checked in |

---

## Non-falling ("mortared"/"cemented") cobble — DONE & verified

> Migrated here from `:compat` unchanged (package `com.mctfc` → `com.firmavanilla`); the twin ids changed
> `mctfc:mortared/*` → `firmavanilla:mortared/*` and the tag `mctfc:mortared_cobblestone` →
> `firmavanilla:mortared_cobblestone`. `:compat`'s substitution rules ([tfc_stone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_stone.json))
> were repointed to match.

TFC makes cobble collapse (gravity), which wrecks cobble builds. `firmavanilla` registers a **non-falling
twin** of every cobble block and `:compat` substitutes builds onto it.

- **Why a twin block, not a property/mixin:** TFC's falling is **tag-gated** — `tfc:can_landslide` lists
  `minecraft:cobblestone`/`mossy_cobblestone` and every `tfc:rock/cobble|mossy_cobble/<rock>`, checked per
  *block* (not per state). You can't add a blockstate property to an existing block (its `StateDefinition`
  is frozen at construction), and even if you could, TFC reads the tag, not a property. So the surgical fix
  is a separate block that simply isn't in `can_landslide`. (Same technique as MehVahdJukaar's
  StoneZone/Moonlight: registry scan + naming detection + runtime-generated assets.)
- **Scan + register** ([MortaredCobbleRegistry](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleRegistry.java)):
  on `RegisterEvent`, iterate `ForgeRegistries.BLOCKS` and register a
  [MortaredCobbleBlock](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleBlock.java)
  (`extends Block`, `Properties.copy(source)`, drops self, name "Cemented &lt;source&gt;") + a
  [MortaredCobbleBlockItem](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleBlockItem.java)
  per cobble, id `firmavanilla:mortared/<source-ns>/<source-path>`. **Detection is a name heuristic**
  (`isCobble`: path ends `cobblestone` or contains a `cobble/` segment, minus
  `_stairs/_slab/_wall/_button/_pressure_plate` and `infested`) — tags are unavailable at registration; the
  heuristic is anchored to reproduce `forge:cobblestone/normal`. **Only sees blocks registered before
  `firmavanilla`** (mods.toml orders it AFTER tfc) — a cobble mod loading after us isn't covered.
- **Client model delegation** ([MortaredCobbleClient](../firmavanilla/src/main/java/com/firmavanilla/client/MortaredCobbleClient.java)):
  twins ship no blockstate/model JSON, so `ModelEvent.ModifyBakingResult` repoints each twin's baked block +
  item model at its source's. The bakery logs a benign "missing model" per twin during load — expected,
  overwritten here. (`getModels()` is keyed by `ResourceLocation`, not `ModelResourceLocation`.)
- **Runtime data pack** ([GeneratedDataPack](../firmavanilla/src/main/java/com/firmavanilla/data/GeneratedDataPack.java) +
  [MortaredCobbleData](../firmavanilla/src/main/java/com/firmavanilla/data/MortaredCobbleData.java)): the
  twins are dynamic so the tag/recipes can't be static JSON. At `AddPackFindersEvent` (twins already
  registered) we serve an in-memory **forced built-in** `SERVER_DATA` pack with
  `firmavanilla:mortared_cobblestone` (all twins) + a **shaped** recipe per twin (the cobble surrounded by 4
  `#tfc:mortar`, cross pattern). The pack also makes twins behave/identify like normal cobble by adding
  `#firmavanilla:mortared_cobblestone` (tag-of-tags) to the block tags real cobble sits in —
  `minecraft:mineable/pickaxe`, `forge:cobblestone/normal`, `tfc:can_carve`, `tfc:toughness_2` (+ Domum
  Ornamentum material tags) — but deliberately **not** `tfc:can_landslide` (the gravity we're escaping). The
  DO tag joins are harmless when DO is absent (the tag files just go unread).
- **In-world conversion** ([MortaredCobbleInteraction](../firmavanilla/src/main/java/com/firmavanilla/block/MortaredCobbleInteraction.java),
  Forge bus): right-click a cobble holding `#tfc:mortar` → swap to its twin, consume 4 mortar (free in
  creative). Cancels the interaction; server-authoritative.
- **Creative tab** ([FirmaVanillaCreativeTab](../firmavanilla/src/main/java/com/firmavanilla/FirmaVanillaCreativeTab.java)):
  holds the static chiseled blocks + every dynamic twin. Beyond grabbing, being in a creative tab is what
  makes blocks **discoverable by MineColonies** (its `CompatibilityManager` item list — fill-block setting,
  pickers — is the union of all creative tabs' contents).

---

## Chiseled sandstone — 7 TFC colours

TFC ships `raw`/`cut`/`smooth` sandstone in 7 colours (black/brown/green/pink/red/white/yellow) but **no
chiseled** form, so `:compat` used to degrade `minecraft:chiseled_sandstone` → `tfc:cut_sandstone/yellow`
(losing the relief). `firmavanilla` fills the gap.

- **Static registration** ([SandstoneBlocks](../firmavanilla/src/main/java/com/firmavanilla/block/SandstoneBlocks.java)):
  a `DeferredRegister<Block>`/`<Item>` registers `firmavanilla:chiseled_sandstone/<colour>` for each of the
  7 colours (plain `Block`, `Properties.copy(Blocks.CHISELED_SANDSTONE)`). Static lang keys
  (`block.firmavanilla.chiseled_sandstone.<colour>`; `/` → `.` in description ids).
- **Motif split:** vanilla ships two chiseled reliefs — the *creeper* face (normal sandstone) and the
  *wither* motif (red sandstone). Each TFC colour wears one: **creeper** = yellow/white/pink/green,
  **wither** = red/black/brown. The split lives only in the texture generator, not the block code.
- **`:compat` substitution** ([tfc_sandstone.json](../compat/src/main/resources/data/mctfc/block_substitutions/tfc_sandstone.json)):
  `chiseled_sandstone` → `firmavanilla:chiseled_sandstone/yellow`, `chiseled_red_sandstone` →
  `firmavanilla:chiseled_sandstone/red`, plus a `mctfc:subst/sandstone/chiseled` re-pick pool (all 7) so the
  player can pick any colour in the Replace GUI.
- **Recipe:** two `tfc:cut_sandstone/<colour>_slab` stacked → the chiseled block (mirrors vanilla).

### Asset generator (`tools/generate-textures`)

A **.NET 10 file-based app** ([generate.cs](../firmavanilla/tools/generate-textures/generate.cs), ImageSharp)
generates the side textures + all per-block JSON (blockstate / cube_column model / item model / loot table /
recipe / `minecraft:mineable/pickaxe` tag), checked into `src/main/resources`. Run:

```
cd firmavanilla/tools/generate-textures
dotnet run generate.cs            # needs the .NET 10 SDK
```

**Technique — CLUT (palette remap), the default.** Build a 256-entry luminance→colour ramp by sampling every
pixel of TFC's real `cut_sandstone/<colour>` (average the colours at each brightness; interpolate the gaps),
then repaint vanilla's chiseled art through it. **Normalization is the key step:** each vanilla pixel's
luminance is mapped from vanilla's range onto that colour's *actual* TFC tonal range before the lookup —
without it, palettes that don't overlap vanilla's luminance (dark **black**, low-contrast **green/pink**) clamp
to a single endpoint and the emblem flattens to a plain colour. With it, the full emblem contrast spans
whatever range the colour has. The emblem stays **crisp** (it's vanilla's exact pixels) and every colour is
**authentically TFC** (sampled from the real texture). This clearly beat the earlier multiply approach on the strongly-recoloured
colours — e.g. red, where vanilla's bright-orange wither motif becomes TFC's muted red-brown and stays fully
legible. (Same family as TFC's own `manual_palette_swap`, but the source→target table is built automatically
from the two textures instead of hand-authored palette strips.)

A second mode, **MULTIPLY (relief-transfer)**, is kept behind the `MODE` switch: `out = tfc_base ×
(chiseled ÷ cut)`, clamped `[0.45, 1.55]`. It preserves TFC's spatial grain but the emblem reads fainter and
frame differences between the two vanilla designs leak in as edge artifacts — so CLUT is the default.

Only the **side** texture is generated; the top/bottom reference TFC's smooth sandstone
(`tfc:block/sandstone/top/<colour>`) directly — matching vanilla, whose chiseled sandstone caps with the
smooth top, not the cut face (no point shipping a verbatim copy of TFC's asset).

> **Sources are not committed.** The generator reads vanilla + TFC textures from `tools/generate-textures/input/`
> (git-ignored — third-party assets we don't redistribute). See
> [input/README.md](../firmavanilla/tools/generate-textures/input/README.md) for the exact files and how to
> extract them from the dev dependency jars. The generated **derivatives** under `src/main/resources` are
> what ship.
