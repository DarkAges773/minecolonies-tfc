package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * <b>Signal campfires</b> — a normal ({@code firmavanilla:signal_campfire}) and a soul
 * ({@code firmavanilla:soul_signal_campfire}) variant that look like vanilla (soul) campfires but <b>can't cook</b>
 * and <b>burn out like a TFC torch</b> (extinguishing to their unlit state after {@link #BURN_MULT}× TFC's
 * {@code torchTicks}). All behaviour is in {@link SignalCampfireBlock}; this class registers the pair, their items,
 * and the shared burn-out helper.
 *
 * <p>Standalone (TFC-only, mixin-free). Constructor params mirror vanilla {@code CampfireBlock}: the normal variant
 * is {@code (spawnParticles=true, fireDamage=1)} off {@code Blocks.CAMPFIRE}, the soul variant
 * {@code (false, 2)} off {@code Blocks.SOUL_CAMPFIRE}; both add {@code .randomTicks()} so the burn-out fires. No
 * crafting recipe yet (creative-/loot-obtainable for now).
 */
public final class SignalCampfires
{
    private SignalCampfires() {}

    /** Normal signal campfire burns out like a <b>TFC torch</b> (1× {@code torchTicks}). */
    public static final int BURN_MULT = 1;
    /** Soul signal campfire burns out like a <b>soul torch</b> — twice as long as the normal one ({@link SoulTorches#BURN_MULT}). */
    public static final int SOUL_BURN_MULT = 2 * BURN_MULT;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FirmaVanilla.MODID);

    public static final RegistryObject<Block> SIGNAL_CAMPFIRE = register("signal_campfire",
            () -> new SignalCampfireBlock(true, 1, BURN_MULT, BlockBehaviour.Properties.copy(Blocks.CAMPFIRE)));
    public static final RegistryObject<Block> SOUL_SIGNAL_CAMPFIRE = register("soul_signal_campfire",
            () -> new SignalCampfireBlock(false, 2, SOUL_BURN_MULT, BlockBehaviour.Properties.copy(Blocks.SOUL_CAMPFIRE)));

    /** Our own BE type (calendar burn timer + smoke), valid for both campfires — so the client reliably ticks it. */
    public static final RegistryObject<BlockEntityType<SignalCampfireBlockEntity>> SIGNAL_CAMPFIRE_BE =
            BLOCK_ENTITY_TYPES.register("signal_campfire", () -> BlockEntityType.Builder.of(
                    SignalCampfireBlockEntity::new, SIGNAL_CAMPFIRE.get(), SOUL_SIGNAL_CAMPFIRE.get()).build(null));

    private static RegistryObject<Block> register(final String name, final java.util.function.Supplier<Block> block)
    {
        final RegistryObject<Block> obj = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
        return obj;
    }

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
    }
}
