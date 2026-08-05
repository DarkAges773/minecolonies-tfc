package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.config.TFCConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * "Soul" torches — a vanilla-soul-torch flavour with TFC's burn-out mechanic. Each is a {@link SoulTorchBlock} /
 * {@link SoulWallTorchBlock} that extends TFC's own torch blocks (so it reuses the whole burn-out machinery — a
 * {@code TFCBlockEntities.TICK_COUNTER} block-entity timing the burn, the placement reset, the convert-to-dead-torch
 * swap) but is handed vanilla's {@code SOUL_FIRE_FLAME} particle and rendered with vanilla soul-torch models, and
 * burns <b>twice as long</b> ({@link #BURN_MULT}) before turning into TFC's burned-out torch.
 *
 * <p>Obtained by <b>converting a lit TFC torch</b>: craft it with a catalyst (shapeless {@code tfc:torch} + a
 * {@link SoulLamps#CATALYST} item — the same tag the soul lamps use), or right-click a placed TFC torch while
 * holding a catalyst ({@link SoulTorchInteraction}). Standing ({@code firmavanilla:soul_torch}) and wall
 * ({@code firmavanilla:soul_wall_torch}) variants share one item, like vanilla.
 */
public final class SoulTorches
{
    private SoulTorches() {}

    /** Soul torches burn this many times longer than a normal TFC torch (its {@code torchTicks} config). */
    public static final int BURN_MULT = 2;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    public static final RegistryObject<Block> SOUL_TORCH =
            BLOCKS.register("soul_torch", () -> new SoulTorchBlock(props("torch")));
    public static final RegistryObject<Block> SOUL_WALL_TORCH =
            BLOCKS.register("soul_wall_torch", () -> new SoulWallTorchBlock(props("wall_torch")));
    // One item for both, placed standing on the floor / as a wall torch on walls — exactly like vanilla soul torch.
    public static final RegistryObject<Item> SOUL_TORCH_ITEM = ITEMS.register("soul_torch",
            () -> new StandingAndWallBlockItem(SOUL_TORCH.get(), SOUL_WALL_TORCH.get(), new Item.Properties(), Direction.DOWN));

    /** normal TFC torch → our soul torch (for the right-click conversion); resolved at setup. */
    private static final Map<Block, Block> NORMAL_TO_SOUL = new HashMap<>();
    /** TFC's burned-out torches a soul torch turns into; resolved at setup. */
    @Nullable private static Block deadTorch, deadWallTorch;

    /**
     * Copy the matching TFC torch's properties wholesale (instabreak, no-collision, random-ticks, sound, push
     * reaction — so the burn-out tick fires and it breaks like a torch), wire back TFC's {@code TICK_COUNTER}
     * block-entity, and dim the light to vanilla soul-torch level (10, vs a normal torch's brighter glow).
     */
    private static ExtendedProperties props(String tfcPath)
    {
        final Block tfcTorch = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", tfcPath));
        final BlockBehaviour.Properties base = (tfcTorch != null && tfcTorch != Blocks.AIR)
                ? BlockBehaviour.Properties.copy(tfcTorch)
                : BlockBehaviour.Properties.copy(Blocks.SOUL_TORCH).randomTicks(); // TFC absent (mandatory dep) — fallback
        base.lightLevel(s -> 10);
        return ExtendedProperties.of(base).blockEntity(TFCBlockEntities.TICK_COUNTER);
    }

    /** The soul torch a normal TFC torch converts into (null if the block isn't a convertible TFC torch). */
    @Nullable
    public static Block soulFor(Block normalTorch)
    {
        return NORMAL_TO_SOUL.get(normalTorch);
    }

    /** Burn-out shared by both variants: once the tick counter passes {@code BURN_MULT ×} TFC's torchTicks, swap to
     *  the given dead-torch state (standing default / wall with facing copied). torchTicks ≤ 0 disables burn-out. */
    public static void tryBurnOut(ServerLevel level, BlockPos pos, @Nullable BlockState deadState)
    {
        if (deadState == null) return;
        level.getBlockEntity(pos, TFCBlockEntities.TICK_COUNTER.get()).ifPresent(te -> {
            final int t = TFCConfig.SERVER.torchTicks.get();
            if (t > 0 && te.getTicksSinceUpdate() > (long) BURN_MULT * t)
            {
                level.setBlockAndUpdate(pos, deadState);
            }
        });
    }

    @Nullable public static Block deadTorch() { return deadTorch; }
    @Nullable public static Block deadWallTorch() { return deadWallTorch; }

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(SoulTorches::onCommonSetup);
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> {
            final Block tfcTorch = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", "torch"));
            final Block tfcWallTorch = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", "wall_torch"));
            deadTorch = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", "dead_torch"));
            deadWallTorch = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", "dead_wall_torch"));
            if (tfcTorch != null) NORMAL_TO_SOUL.put(tfcTorch, SOUL_TORCH.get());
            if (tfcWallTorch != null) NORMAL_TO_SOUL.put(tfcWallTorch, SOUL_WALL_TORCH.get());
        });
    }
}
