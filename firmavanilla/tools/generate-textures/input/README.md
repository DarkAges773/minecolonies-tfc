# Generator source textures (NOT committed)

The asset generator (`../generate.cs`) reads its source textures from this folder. These are **vanilla
Minecraft and TerraFirmaCraft textures** — third-party assets we do **not** redistribute, so everything
under `input/` is git-ignored. You only need them to (re)generate; the generated derivatives under
`firmavanilla/src/main/resources` are what ship.

## Required files

```
input/vanilla/chiseled_sandstone.png        (the "creeper" relief)
input/vanilla/cut_sandstone.png             (its flat counterpart)
input/vanilla/chiseled_red_sandstone.png    (the "wither" relief)
input/vanilla/cut_red_sandstone.png         (its flat counterpart)
input/tfc/cut_sandstone/{black,brown,green,pink,red,white,yellow}.png   (the TFC colour bases)
```

## Extracting them from the dev dependency jars

After at least one Gradle run has populated the caches:

**Vanilla** — `assets/minecraft/textures/block/<name>.png` inside
`~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar`.

**TFC** — note the on-disk path differs from the in-game id: the cut texture lives at
`assets/tfc/textures/block/sandstone/cut/<colour>.png` (in-game id `tfc:cut_sandstone/<colour>`) inside the
TFC jar under `~/.gradle/caches/modules-2/files-2.1/curse.maven/terrafirmacraft-302973/<fileId>/`. Copy each
`sandstone/cut/<colour>.png` to `input/tfc/cut_sandstone/<colour>.png`.

## Running

```
cd firmavanilla/tools/generate-textures
dotnet run generate.cs          # needs the .NET 10 SDK
```
