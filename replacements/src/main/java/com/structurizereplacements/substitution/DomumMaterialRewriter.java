package com.structurizereplacements.substitution;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.entity.block.IMateriallyTexturedBlockEntity;
import com.ldtteam.structurize.util.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
     * DO's <b>dynamic timber frame</b> ({@code DynamicTimberFrameBlockEntity}: the {@code plain} /
     * {@code double_crossed} / … blocks) stores its materials <b>redundantly</b>: besides the base
     * {@code textureData}, its own save writes this second component map plus the two block-id strings
     * below — and its {@code load()} derives the effective materials from THOSE, ignoring
     * {@code textureData} (whose {@code getTextureData()} also returns the {@code originalTextureData}
     * map). Rewriting {@code textureData} alone is therefore a no-op for these blocks: the requested item
     * and the placed block would keep the original material. All four keys must stay consistent.
     */
    private static final String ORIGINAL_TEXTURE_DATA_KEY = "originalTextureData";
    private static final String PRIMARY_BLOCK_KEY = "primaryBlock";
    private static final String SECONDARY_BLOCK_KEY = "secondaryBlock";

    /** The material-map compounds to rewrite (same component-id -> block-id shape). */
    private static final List<String> TEXTURE_COMPOUND_KEYS = List.of(TEXTURE_DATA_KEY, ORIGINAL_TEXTURE_DATA_KEY);

    /** The bare block-id string keys to rewrite (dynamic timber frame only). */
    private static final List<String> MATERIAL_STRING_KEYS = List.of(PRIMARY_BLOCK_KEY, SECONDARY_BLOCK_KEY);

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
     * A representative display {@link ItemStack} for a blueprint host block. Normally just
     * {@code new ItemStack(host)}, but for a Domum Ornamentum materialized host it also copies the entry's
     * {@code textureData} material map onto the stack — which is exactly where DO's {@code BlockItem.getName}
     * reads it (`getOrCreateTagElement("textureData")`), so the stack reports its real material-aware name
     * (e.g. "Oak Panel", "Dynamic Framed Oak") and renders the textured item, instead of the bare block's
     * dynamic, unlocalized descriptionId. Returns an empty stack for an item-less host.
     */
    public static ItemStack hostDisplayStack(@Nullable final Block host, @Nullable final CompoundTag tileEntityData)
    {
        final ItemStack stack = new ItemStack(host);
        if (stack.isEmpty()
                || !(host instanceof IMateriallyTexturedBlock)
                || tileEntityData == null
                || !tileEntityData.contains(TEXTURE_DATA_KEY, Tag.TAG_COMPOUND))
        {
            return stack;
        }
        final CompoundTag textureData = tileEntityData.getCompound(TEXTURE_DATA_KEY);
        if (!textureData.isEmpty())
        {
            stack.getOrCreateTag().put(TEXTURE_DATA_KEY, textureData.copy());
        }
        return stack;
    }

    /**
     * Add every block a DO host carries inside its material NBT to {@code out} (for GUI source
     * enumeration) — the {@code textureData}/{@code originalTextureData} maps plus the dynamic timber
     * frame's block-id strings. No-op when {@code host} is not a DO block or carries no material data.
     */
    public static void collectContainedBlocks(@Nullable final Block host,
                                              @Nullable final CompoundTag tileEntityData,
                                              final Collection<Block> out)
    {
        if (!(host instanceof IMateriallyTexturedBlock) || tileEntityData == null)
        {
            return;
        }
        for (final String compoundKey : TEXTURE_COMPOUND_KEYS)
        {
            if (!tileEntityData.contains(compoundKey, Tag.TAG_COMPOUND))
            {
                continue;
            }
            final CompoundTag textureData = tileEntityData.getCompound(compoundKey);
            for (final String key : textureData.getAllKeys())
            {
                final Block contained = block(textureData.getString(key));
                if (contained != null)
                {
                    out.add(contained);
                }
            }
        }
        for (final String stringKey : MATERIAL_STRING_KEYS)
        {
            if (tileEntityData.contains(stringKey, Tag.TAG_STRING))
            {
                final Block contained = block(tileEntityData.getString(stringKey));
                if (contained != null)
                {
                    out.add(contained);
                }
            }
        }
    }

    /**
     * Rewrite <b>every material-bearing key</b> of a DO block's tile-entity NBT — the {@code textureData}
     * and {@code originalTextureData} compounds plus the dynamic timber frame's {@code primaryBlock}/
     * {@code secondaryBlock} strings (see {@link #ORIGINAL_TEXTURE_DATA_KEY}) — replacing each contained
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
        if (!(host instanceof IMateriallyTexturedBlock materiallyTextured) || tileEntityData == null)
        {
            return tileEntityData;
        }

        // component-id -> the tag of blocks it legally accepts, for the skip-illegal check.
        final Map<ResourceLocation, TagKey<Block>> validSkins = new HashMap<>();
        for (final IMateriallyTexturedBlockComponent component : materiallyTextured.getComponents())
        {
            validSkins.put(component.getId(), component.getValidSkins());
        }

        CompoundTag result = null; // lazily copied on first actual change

        // The material-map compound(s): textureData + (dynamic timber frame) originalTextureData.
        for (final String compoundKey : TEXTURE_COMPOUND_KEYS)
        {
            if (!tileEntityData.contains(compoundKey, Tag.TAG_COMPOUND))
            {
                continue;
            }
            final CompoundTag rewritten =
                    rewriteTextureCompound(tileEntityData.getCompound(compoundKey), validSkins, resolver);
            if (rewritten != null)
            {
                if (result == null)
                {
                    result = tileEntityData.copy();
                }
                result.put(compoundKey, rewritten);
            }
        }

        // The dynamic timber frame's bare block-id strings (its load() derives the materials from THESE,
        // not from textureData). Gated on the union of the components' valid-skins tags — the per-component
        // mapping for these keys is the BE's private knowledge, and an over-permissive swap here only
        // mirrors what the compound rewrite above already accepted for the same material.
        for (final String stringKey : MATERIAL_STRING_KEYS)
        {
            if (!tileEntityData.contains(stringKey, Tag.TAG_STRING))
            {
                continue;
            }
            final Block from = block(tileEntityData.getString(stringKey));
            if (from == null)
            {
                continue;
            }
            final Block to = resolver.apply(from);
            if (to == null || to == from || !legalInAnyComponent(to, validSkins.values()))
            {
                continue;
            }
            if (result == null)
            {
                result = tileEntityData.copy();
            }
            result.putString(stringKey, idOf(to));
        }

        return result == null ? tileEntityData : result;
    }

    /**
     * Rewrite one component-id -> block-id compound through {@code resolver} (skip-illegal per component).
     * Returns the rewritten copy, or {@code null} when nothing changed.
     */
    @Nullable
    private static CompoundTag rewriteTextureCompound(final CompoundTag textureData,
                                                      final Map<ResourceLocation, TagKey<Block>> validSkins,
                                                      final UnaryOperator<Block> resolver)
    {
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
        return rewritten;
    }

    /** Whether {@code block} is a legal skin for at least one of the host's components. */
    private static boolean legalInAnyComponent(final Block block, final Collection<TagKey<Block>> skins)
    {
        final BlockState probe = block.defaultBlockState();
        for (final TagKey<Block> tag : skins)
        {
            if (tag == null || probe.is(tag))
            {
                return true;
            }
        }
        return skins.isEmpty();
    }

    /**
     * Whether the world block entity's DO materials already match a (substituted) blueprint tile tag —
     * the supplement for the builder's "is this position already built?" scan. Some colony forks' DO
     * placement handlers compare only block <i>states</i>, so a substitution that lives entirely in the
     * tile NBT reads as "already built" and repair never touches the block; the iterator mixin calls this
     * when our rewrite actually changed the blueprint tag, restoring the material comparison (mirroring
     * Structurize's own {@code DoBlockPlacementHandler#compareBEData} semantics).
     *
     * <p>Compares {@code originalTextureData} first when present and non-empty: for the dynamic timber
     * frame the world side's {@code getTextureData()} returns exactly that map, while its base
     * {@code textureData} can be stale — comparing the base map could disagree forever and rebuild-loop.
     * Returns {@code true} when there is nothing to verify (no DO world BE / no material data in the tag):
     * "no evidence of mismatch" must not flip a match to a rebuild.
     */
    public static boolean materialsMatchWorld(@Nullable final BlockEntity worldBe, @Nullable final CompoundTag blueprintTag)
    {
        if (!(worldBe instanceof IMateriallyTexturedBlockEntity mtbe) || blueprintTag == null)
        {
            return true;
        }
        final CompoundTag source = materialSource(blueprintTag);
        if (source == null)
        {
            return true;
        }
        return mtbe.getTextureData().equals(MaterialTextureData.deserializeFromNBT(source));
    }

    /**
     * Force the world block entity's DO materials to match a (substituted) blueprint tile tag, after a
     * placement that reported success. Needed because a colony mod's DO placement handler may skip the
     * tile-entity write entirely when the block <i>state</i> was already correct in the world
     * (SlimColonies' handler bails out before {@code handleTileEntityPlacement} when its
     * {@code setBlockState} call is a no-op) — an NBT-only substitution then never reaches the world.
     * Writes via DO's own {@link IMateriallyTexturedBlockEntity#updateTextureDataWith} (which also maps
     * the dynamic timber frame's internal fields) and re-syncs the block to clients.
     *
     * @return true when it actually rewrote the materials (false = nothing to fix / not a DO block entity).
     */
    public static boolean enforceWorldMaterials(final Level world, final BlockPos pos, @Nullable final CompoundTag blueprintTag)
    {
        if (blueprintTag == null || world.isClientSide)
        {
            return false;
        }
        final BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof IMateriallyTexturedBlockEntity mtbe) || materialsMatchWorld(be, blueprintTag))
        {
            return false;
        }
        final CompoundTag source = materialSource(blueprintTag);
        if (source == null)
        {
            return false;
        }
        mtbe.updateTextureDataWith(MaterialTextureData.deserializeFromNBT(source));
        be.setChanged();
        final BlockState state = world.getBlockState(pos);
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return true;
    }

    /**
     * The authoritative material map of a DO tile tag: {@code originalTextureData} when present and
     * non-empty (the dynamic timber frame's {@code getTextureData()} returns exactly that map, while its
     * base {@code textureData} can be stale), else a non-empty {@code textureData}, else null.
     */
    @Nullable
    private static CompoundTag materialSource(final CompoundTag tag)
    {
        if (tag.contains(ORIGINAL_TEXTURE_DATA_KEY, Tag.TAG_COMPOUND) && !tag.getCompound(ORIGINAL_TEXTURE_DATA_KEY).isEmpty())
        {
            return tag.getCompound(ORIGINAL_TEXTURE_DATA_KEY);
        }
        if (tag.contains(TEXTURE_DATA_KEY, Tag.TAG_COMPOUND) && !tag.getCompound(TEXTURE_DATA_KEY).isEmpty())
        {
            return tag.getCompound(TEXTURE_DATA_KEY);
        }
        return null;
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
