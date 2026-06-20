#!/usr/bin/env dotnet
#:package SixLabors.ImageSharp@3.1.10
#:property EnableDefaultCompileItems=true

// TFC Vanilla Building Blocks — asset generator (.NET 10 file-based app, multi-file).
//
// Run:  dotnet run generate.cs            (from this folder; needs the .NET 10 SDK)
//       dotnet run generate.cs -- regen-mask   (also rebuild the rock-tile grain masks)
//
// `#:property EnableDefaultCompileItems=true` makes the file-based run compile every sibling .cs in this folder,
// so the generator is split one file per feature while keeping the single `dotnet run generate.cs` command:
//
//   common.cs            the shared engine (`Gen`): config, texture pipeline (CLUT/grain/mask), file/tag I/O,
//                        and the JSON emitters used across features (stair/slab/wall shapes, generic recipes).
//   chiseledsandstone.cs bookshelves.cs  rocktiles.cs  alabaster.cs  copper.cs
//   prismarinedeposits.cs  soullamps.cs  quartz.cs  coarsedirt.cs
//                        one self-contained feature each (its generation logic + its own JSON emitters).
//
// This file is just the entry point: it runs each feature in turn, then writes the cross-cutting tags that more
// than one feature contributes to. Source PNGs are read from ./input (see input/README.md); generated assets are
// written into ../../src/main/resources (checked in).

using static Gen;

int sandstone   = ChiseledSandstone.Generate();
int books       = Bookshelves.Generate();
int tiles       = RockTiles.Generate();
int alab        = Alabaster.Generate();
var (patina, copperBars, copperForms) = Copper.Generate();
int prismarine  = PrismarineDeposits.Generate();   // before CoarseDirt: both feed the shovel/landslide accumulators
SoulLamps.Generate();
SoulTorches.Generate();
Quartz.Generate();
CoarseDirt.Generate();
Beeswax.Generate();    // block of beeswax — honeycomb_block motif CLUT'd through FirmaLife beeswax's palette
SignalCampfire.Generate();  // signal campfires (normal + soul) — JSON only, reuse vanilla campfire art
int barrels    = Barrels.Generate();

// ---- cross-cutting tags written ONCE (a mod can ship only one file per tag path) ----
// minecraft:mineable/pickaxe + the vanilla stairs/slabs/walls shape tags are pure functions of the config lists.
WriteTag("minecraft", "blocks", "mineable/pickaxe", MineableTag());
WriteTag("minecraft", "blocks", "stairs", ShapeTag("tile_stairs", "alabaster_tile_stairs"));
WriteTag("minecraft", "blocks", "slabs", ShapeTag("tile_slab", "alabaster_tile_slab"));
WriteTag("minecraft", "blocks", "walls", ShapeTag("tile_wall", "alabaster_tile_wall"));
// minecraft:mineable/shovel + tfc:can_landslide get prismarine deposits AND coarse dirt (accumulated above).
WriteTag("minecraft", "blocks", "mineable/shovel", IdsTagBody(shovelMineable));
WriteTag("tfc", "blocks", "can_landslide", IdsTagBody(canLandslide));
// minecraft:mineable/axe gets the decorative bookshelves AND the wood barrels (accumulated above).
WriteTag("minecraft", "blocks", "mineable/axe", ValuesTag(axeMineable));

Console.WriteLine($"Done: {sandstone} chiseled-sandstone + {books} bookshelf + {tiles} rock-tiles + {alab} alabaster variants + {patina} patina palettes + {copperBars} copper-bar stages + {copperForms} copper-form blocks + {prismarine} prismarine deposits + {barrels} barrel wood-variants written to {resRoot}");
