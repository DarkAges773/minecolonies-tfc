# Generator source textures

The asset generator (`../generate.cs`) reads its source textures from this folder. They're used only to
(re)generate — everything under `input/` is **git-ignored**; the derivatives under
`firmavanilla/src/main/resources` are what ship. All are **third-party textures** (vanilla Minecraft, TFC, AFC,
Beneath). Most are copied straight from the dev jars; `vanilla/bookshelf_overlay.png` is **derived from
Mojang's `bookshelf.png`** — the book-spine pixels lifted out, transparent elsewhere — so it's re-creatable
from the vanilla texture rather than committed.

## Required files

```
# chiseled sandstone (third-party — extract):
input/vanilla/chiseled_sandstone.png        (the "creeper" relief)
input/vanilla/cut_sandstone.png             (its flat counterpart)
input/vanilla/chiseled_red_sandstone.png    (the "wither" relief)
input/vanilla/cut_red_sandstone.png         (its flat counterpart)
input/tfc/cut_sandstone/{black,brown,green,pink,red,white,yellow}.png   (the TFC colour bases)

# decorative bookshelves:
input/vanilla/bookshelf_overlay.png         (book-spine pixels lifted from vanilla bookshelf.png, transparent elsewhere)
input/{tfc,afc,beneath}/bookshelf_empty/<wood>.png   (third-party — each wood's empty bookshelf frame)
```

## Extracting them from the dev dependency jars

After at least one Gradle run has populated the caches:

**Vanilla** — `assets/minecraft/textures/block/<name>.png` inside
`~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar`.

**TFC** — note the on-disk path differs from the in-game id: the cut texture lives at
`assets/tfc/textures/block/sandstone/cut/<colour>.png` (in-game id `tfc:cut_sandstone/<colour>`) inside the
TFC jar under `~/.gradle/caches/modules-2/files-2.1/curse.maven/terrafirmacraft-302973/<fileId>/`. Copy each
`sandstone/cut/<colour>.png` to `input/tfc/cut_sandstone/<colour>.png`.

**Bookshelf empty frames** — `assets/<ns>/textures/block/wood/planks/<wood>_bookshelf_empty.png` inside the
TFC / AFC (`arborfirmacraft-877545`) / Beneath (`beneath-1113980`) jars. Copy each to
`input/<ns>/bookshelf_empty/<wood>.png` (drop the `_bookshelf_empty` suffix). Wood lists are in `generate.cs`.

## Running

```
cd firmavanilla/tools/generate-textures
dotnet run generate.cs          # needs the .NET 10 SDK
```
