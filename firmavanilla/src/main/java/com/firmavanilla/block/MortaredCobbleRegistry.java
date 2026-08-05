package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans the block registry at registration time and creates a non-falling {@link MortaredCobbleBlock}
 * twin for every "cobble" block already registered (vanilla, TFC, and any mod loaded before
 * {@code firmavanilla} — which, since this mod is ordered AFTER tfc, includes the whole TFC rock set).
 *
 * <p>Twins are registered under {@code firmavanilla:mortared/<source-namespace>/<source-path>}. The
 * {@link #SOURCE_TO_TWIN} map drives the runtime-generated {@code firmavanilla:mortared_cobblestone} tag,
 * the mortar recipes, and the client model delegation.
 *
 * <p><b>Limitation:</b> the scan only sees blocks registered before {@code firmavanilla}; a mod that adds
 * cobble and loads <i>after</i> us won't be covered (Minecraft freezes block registration before tags load,
 * so a tag-driven scan isn't possible).
 */
public final class MortaredCobbleRegistry
{
    private MortaredCobbleRegistry() {}

    /** source cobble block -> its non-falling twin, insertion-ordered for stable iteration. */
    private static final Map<Block, MortaredCobbleBlock> SOURCE_TO_TWIN = new LinkedHashMap<>();

    public static Map<Block, MortaredCobbleBlock> twins()
    {
        return Collections.unmodifiableMap(SOURCE_TO_TWIN);
    }

    /** The non-falling twin for a source cobble block, or {@code null} if {@code source} isn't a cobble we twinned. */
    @org.jetbrains.annotations.Nullable
    public static MortaredCobbleBlock twinFor(final Block source)
    {
        return SOURCE_TO_TWIN.get(source);
    }

    public static void onRegister(final RegisterEvent event)
    {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> registerBlocks());
        event.register(ForgeRegistries.Keys.ITEMS, helper -> registerItems());
    }

    private static void registerBlocks()
    {
        // Snapshot first: we're about to register into the same registry we're iterating.
        final List<Block> sources = new ArrayList<>();
        for (final Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : ForgeRegistries.BLOCKS.getEntries())
        {
            if (isCobble(entry.getKey().location()))
            {
                sources.add(entry.getValue());
            }
        }

        for (final Block source : sources)
        {
            final ResourceLocation sourceId = ForgeRegistries.BLOCKS.getKey(source);
            if (sourceId == null)
            {
                continue;
            }
            final MortaredCobbleBlock twin = new MortaredCobbleBlock(source);
            ForgeRegistries.BLOCKS.register(twinId(sourceId), twin);
            SOURCE_TO_TWIN.put(source, twin);
        }
        FirmaVanilla.LOGGER.info("Registered {} non-falling cobble twin(s).", SOURCE_TO_TWIN.size());
    }

    private static void registerItems()
    {
        for (final Map.Entry<Block, MortaredCobbleBlock> entry : SOURCE_TO_TWIN.entrySet())
        {
            final ResourceLocation sourceId = ForgeRegistries.BLOCKS.getKey(entry.getKey());
            if (sourceId == null)
            {
                continue;
            }
            ForgeRegistries.ITEMS.register(twinId(sourceId), new MortaredCobbleBlockItem(entry.getValue()));
        }
    }

    /** {@code firmavanilla:mortared/<ns>/<path>} — unique and stable per source block. */
    public static ResourceLocation twinId(final ResourceLocation sourceId)
    {
        return new ResourceLocation(FirmaVanilla.MODID, "mortared/" + sourceId.getNamespace() + "/" + sourceId.getPath());
    }

    /**
     * Heuristic for "is this a <b>full</b> cobble block" without tags (unavailable at registration). Include
     * a block whose path ends with {@code cobblestone} (vanilla {@code cobblestone}/{@code mossy_cobblestone})
     * or contains a {@code cobble/} segment (TFC {@code rock/cobble/<rock>} and {@code rock/mossy_cobble/<rock>}),
     * but exclude decorative variants (stairs/slabs/walls/buttons/pressure plates — TFC names them
     * {@code rock/cobble/<rock>_slab} etc.) and {@code infested_cobblestone}. {@code cobbled_deepslate} has no
     * matching segment and is naturally excluded.
     */
    private static boolean isCobble(final ResourceLocation id)
    {
        final String path = id.getPath();
        if (path.contains("infested")
                || path.endsWith("_stairs") || path.endsWith("_slab") || path.endsWith("_wall")
                || path.endsWith("_button") || path.endsWith("_pressure_plate"))
        {
            return false;
        }
        return path.endsWith("cobblestone") || path.contains("cobble/");
    }
}
