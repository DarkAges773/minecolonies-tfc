package com.structurizereplacements.substitution;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.structurize.util.BlockInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Domum Ornamentum-aware extension to the substitution engine.
 *
 * <p>DO "materialized" blocks (panel, brick, shingle, framed blocks, …) are a single block whose real
 * material(s) live in <b>block-entity NBT</b> — a compound under the key {@code "textureData"} mapping each
 * {@link IMateriallyTexturedBlockComponent}'s id to the contained block's id (e.g. a framed block stores
 * {@code frame -> oak_planks}, {@code body -> cobblestone}). Plain {@code BlockState} substitution never
 * sees those, so we rewrite the compound here, swapping each contained block via the same resolver the
 * engine uses for ordinary blocks. Every component is rewritten independently, so a framed block's frame
 * and body each follow their own material's rule/choice.
 *
 * <p>DO is a <b>mandatory</b> dependency of Structurize itself (always present at runtime), so this class
 * references DO directly with no {@code ModList} guard — it's {@code compileOnly} purely so we don't bundle it.
 */
public final class DomumMaterialRewriter
{
    private DomumMaterialRewriter() {}

    /** Block-entity NBT key under which DO stores its component -> contained-block map. */
    private static final String TEXTURE_DATA_KEY = "textureData";

    /**
     * Whether {@code block} is a Domum Ornamentum "materialized" block (panel, brick, framed, DO door, …).
     * These carry their real material(s) in tile-entity NBT, so they aren't meaningful plain-substitution
     * sources or candidate targets — a bare DO block has no chosen material, renders as a cycling preview,
     * and the player can't specify how it would look. DO registers some of them into vanilla tags (e.g. its
     * door is in {@code minecraft:wooden_doors}), so the GUI must filter them out of candidate pools/rows.
     */
    public static boolean isMaterializedBlock(@Nullable final Block block)
    {
        return block instanceof IMateriallyTexturedBlock;
    }

    /**
     * Whether this blueprint entry is a Domum Ornamentum "materialized" block carrying texture data — i.e.
     * one whose preview material lives in tile-entity NBT. Used as the predicate for the preview-render
     * blueprint transform (so it only touches DO blocks, leaving the host state untouched).
     */
    public static boolean isMaterialized(@Nullable final BlockInfo info)
    {
        final BlockState state = info == null ? null : info.getState();
        return state != null
                && state.getBlock() instanceof IMateriallyTexturedBlock
                && info.hasTileEntityData()
                && info.getTileEntityData().contains(TEXTURE_DATA_KEY, Tag.TAG_COMPOUND);
    }

    /**
     * Add every block a DO host carries inside its {@code textureData} to {@code out} (for GUI source
     * enumeration). No-op when {@code host} is not a DO block or carries no texture data.
     */
    public static void collectContainedBlocks(@Nullable final Block host,
                                              @Nullable final CompoundTag tileEntityData,
                                              final Collection<Block> out)
    {
        if (!(host instanceof IMateriallyTexturedBlock) || tileEntityData == null
                || !tileEntityData.contains(TEXTURE_DATA_KEY, Tag.TAG_COMPOUND))
        {
            return;
        }
        final CompoundTag textureData = tileEntityData.getCompound(TEXTURE_DATA_KEY);
        for (final String key : textureData.getAllKeys())
        {
            final Block contained = block(textureData.getString(key));
            if (contained != null)
            {
                out.add(contained);
            }
        }
    }

    /**
     * Rewrite the {@code textureData} compound of a DO block's tile-entity NBT, replacing each contained
     * block via {@code resolver} (which returns the same block when nothing applies). A replacement that
     * isn't a legal skin for its component (not in {@link IMateriallyTexturedBlockComponent#getValidSkins()})
     * is <b>skipped</b>, since DO would reject/misrender it.
     *
     * <p>Note: DO's valid-skins tags are curated and TFC ships nothing into them, so our TFC substitution
     * targets are added to the relevant DO material tags by a datapack (see {@code DomumMaterialTags}); that
     * keeps this guard meaningful (genuinely-incompatible skins still skip) while letting TFC materials
     * substitute inside DO blocks.
     *
     * @return a new tile-entity tag when at least one component changed, otherwise the same
     *         {@code tileEntityData} reference (possibly {@code null}).
     */
    @Nullable
    public static CompoundTag rewrite(@Nullable final Block host,
                                      @Nullable final CompoundTag tileEntityData,
                                      final UnaryOperator<Block> resolver)
    {
        if (!(host instanceof IMateriallyTexturedBlock materiallyTextured) || tileEntityData == null
                || !tileEntityData.contains(TEXTURE_DATA_KEY, Tag.TAG_COMPOUND))
        {
            return tileEntityData;
        }

        final CompoundTag textureData = tileEntityData.getCompound(TEXTURE_DATA_KEY);

        // component-id -> the tag of blocks it legally accepts, for the skip-illegal check.
        final Map<ResourceLocation, TagKey<Block>> validSkins = new HashMap<>();
        for (final IMateriallyTexturedBlockComponent component : materiallyTextured.getComponents())
        {
            validSkins.put(component.getId(), component.getValidSkins());
        }

        CompoundTag rewritten = null; // lazily copied on first actual change
        for (final String key : textureData.getAllKeys())
        {
            final Block from = block(textureData.getString(key));
            if (from == null)
            {
                continue;
            }
            final Block to = resolver.apply(from);
            if (to == null || to == from)
            {
                continue;
            }
            // skip-illegal: only substitute within what the component accepts (TFC targets are tagged in via
            // the datapack so they pass here).
            final TagKey<Block> skins = validSkins.get(ResourceLocation.tryParse(key));
            if (skins != null && !to.defaultBlockState().is(skins))
            {
                continue;
            }
            if (rewritten == null)
            {
                rewritten = textureData.copy();
            }
            rewritten.putString(key, idOf(to));
        }

        if (rewritten == null)
        {
            return tileEntityData; // nothing changed
        }
        final CompoundTag result = tileEntityData.copy();
        result.put(TEXTURE_DATA_KEY, rewritten);
        return result;
    }

    @Nullable
    private static Block block(final String id)
    {
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl))
        {
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(rl);
    }

    private static String idOf(final Block block)
    {
        return ForgeRegistries.BLOCKS.getKey(block).toString();
    }
}
