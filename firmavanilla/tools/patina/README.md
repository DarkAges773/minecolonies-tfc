# patina — copper-oxidation CLUT tool

A **self-contained** .NET CLI (file-based, ImageSharp) that recolours any texture through copper's weathering
palette, producing `exposed` / `weathered` / `oxidized` variants. Extracted from the firmavanilla asset generator
so the workflow is reusable on its own — no dependency on `generate-textures/`.

## Requirements
.NET 10 SDK (same as the asset generator). ImageSharp is restored automatically by `dotnet run`.

## Apply — texture → patina variants

```
cd firmavanilla/tools/patina
dotnet run patina.cs -- <input.png|dir> [outDir] [--unaffected] [--stages exposed,weathered,oxidized]
```

- `input` — a PNG, or a directory (every `*.png` in it is processed).
- `outDir` — where variants are written (default: the input's own folder).
- For each input `foo.png` writes `foo_exposed.png`, `foo_weathered.png`, `foo_oxidized.png`.
- `--unaffected` also copies the input verbatim as `foo_unaffected.png` (stage 0).
- `--stages` restricts/orders the stages.

Example:
```
dotnet run patina.cs -- myblock.png ./out --unaffected
```

## Extract — rebuild the LUTs from vanilla copper

```
dotnet run patina.cs -- extract <exposed_copper.png> <weathered_copper.png> <oxidized_copper.png>
```

Re-samples the three bundled LUT strips in [`lut/`](lut/) straight from vanilla copper's weathering-stage block
textures (`assets/minecraft/textures/block/{exposed,weathered,oxidized}_copper.png`, from the dev `client-extra.jar`).
The palette comes from each stage's **own pixels** — no `copper_block` and no subtraction. This is exactly how the
committed `lut/*.png` were produced, so re-extracting is byte-stable.

## How it works (CLUT)

Each `lut/<stage>.png` is a **256×16 luminance→colour ramp** of that oxidation stage. To recolour a texture, every
input pixel's luminance — normalised from the texture's own range onto the ramp's range — indexes the ramp; the
result keeps the input's relief/contrast and its **alpha** (transparent pixels stay transparent, so grates/chains
work). Because the normalisation spans whatever tonal range the palette has, low-contrast palettes don't flatten.

## Relationship to the asset generator

`generate-textures/generate.cs` uses the **same** LUTs and technique inline (for the firmavanilla copper bars /
chains / trapdoors). The strips here are identical to its `patina_*.png`; this tool is the standalone, reusable
front-end for ad-hoc texture work or future blocks.
