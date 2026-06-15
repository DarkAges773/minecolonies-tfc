package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.items.LampBlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Soul" variants of TFC's metal lamps (teal glow, dimmer light) — a vanilla-soul-lantern flavour for every TFC
 * lamp. Each {@code firmavanilla:soul_lamp/<metal>} is a {@link SoulLampBlock} that reuses TFC's lamp block-entity
 * ({@code TFCBlockEntities.LAMP}) so it keeps the full fuel/lit/fill behaviour; only the glass texture, the light
 * level (10) and the burn-out revert differ.
 *
 * <p>Obtained by <b>converting a finished lamp</b>: right-click a normal TFC lamp holding any item in the
 * {@link #CATALYST} tag (preserving fuel + lit state — see {@link SoulLampInteraction}), or craft a lamp with a
 * catalyst item. The normal↔soul block maps are resolved at common setup once both mods' lamps are registered.
 */
public final class SoulLamps
{
    private SoulLamps() {}

    /** TFC's 9 lamp metals (matches {@code tfc:metal/lamp/<metal>}). */
    public static final List<String> METALS = List.of(
            "copper", "bronze", "bismuth_bronze", "black_bronze", "wrought_iron",
            "steel", "black_steel", "blue_steel", "red_steel");

    /** Items that convert a lamp to its soul variant (right-click + crafting). Datapack-overridable. */
    public static final TagKey<Item> CATALYST = ItemTags.create(new ResourceLocation(FirmaVanilla.MODID, "soul_lamp_catalyst"));

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** Soul lamp block per metal, in {@link #METALS} order (drives the creative tab). */
    public static final List<RegistryObject<Block>> SOUL = new ArrayList<>();

    /** normal TFC lamp → our soul lamp (for the right-click/craft conversion); resolved at setup. */
    private static final Map<Block, Block> NORMAL_TO_SOUL = new HashMap<>();
    /** our soul lamp → normal TFC lamp (for the burn-out revert); resolved at setup. */
    private static final Map<Block, Block> SOUL_TO_NORMAL = new HashMap<>();

    static
    {
        for (final String metal : METALS)
        {
            final RegistryObject<Block> block = BLOCKS.register("soul_lamp/" + metal, () -> new SoulLampBlock(props(metal)));
            // TFC's LampBlockItem so the soul lamp item carries its fuel NBT, exactly like a normal lamp.
            ITEMS.register("soul_lamp/" + metal, () -> new LampBlockItem(block.get(), new Item.Properties()));
            SOUL.add(block);
        }
    }

    /**
     * Inherit the matching TFC metal lamp's properties wholesale — no lamp detail is hand-authored here. At
     * registration (TFC loads before us, so its lamp is already in the registry) we
     * {@code BlockBehaviour.Properties.copy} the real {@code tfc:metal/lamp/<metal>} block, so its exact
     * strength / sound / map-color / push-reaction / random-ticks / occlusion / (no) tool requirement carry over
     * per metal and track any future TFC change, and the block-entity is wired back to TFC's own {@code LAMP}
     * type. The soul-specific change (dimmer light) lives in {@link SoulLampBlock}, not here.
     */
    private static ExtendedProperties props(String metal)
    {
        final Block tfcLamp = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", "metal/lamp/" + metal));
        final BlockBehaviour.Properties base = (tfcLamp != null && tfcLamp != Blocks.AIR)
                ? BlockBehaviour.Properties.copy(tfcLamp)
                : BlockBehaviour.Properties.of().sound(SoundType.LANTERN).strength(4.0f, 10.0f).randomTicks().noOcclusion(); // TFC absent (mandatory dep, so practically unreachable)
        return ExtendedProperties.of(base).blockEntity(TFCBlockEntities.LAMP);
    }

    /** The normal TFC lamp a soul lamp reverts to on burn-out (null before setup / if unmapped). */
    @Nullable
    public static Block normalFor(Block soulLamp)
    {
        return SOUL_TO_NORMAL.get(soulLamp);
    }

    /** The soul lamp a normal TFC lamp converts into (null if the block isn't a convertible lamp). */
    @Nullable
    public static Block soulFor(Block normalLamp)
    {
        return NORMAL_TO_SOUL.get(normalLamp);
    }

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(SoulLamps::onCommonSetup);
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> {
            for (int i = 0; i < METALS.size(); i++)
            {
                final Block normal = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", "metal/lamp/" + METALS.get(i)));
                final Block soul = SOUL.get(i).get();
                if (normal == null) continue; // TFC absent/renamed — skip; soul lamp still works, just no auto-revert/convert
                NORMAL_TO_SOUL.put(normal, soul);
                SOUL_TO_NORMAL.put(soul, normal);
            }
        });
    }
}
