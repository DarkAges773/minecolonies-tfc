package com.mctfc;

import com.mctfc.block.MortaredCobbleBlock;
import com.mctfc.block.MortaredCobbleRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's creative-mode tab. Beyond letting players grab the content, being in a creative tab is what makes
 * blocks <i>discoverable by MineColonies</i>: its {@code CompatibilityManager} builds its master item list
 * (used by the fill-block setting, candidate pickers, recipe UI, …) purely from the union of all creative
 * tabs' contents — an item in no tab is invisible to those menus. The mortared-cobble twins are registered
 * dynamically with no tab of their own, so without this they never showed up as a miner fill block.
 *
 * <p>Contents are generated lazily from {@link MortaredCobbleRegistry#twins()} (populated at
 * {@code RegisterEvent}, well before tabs are built), so every twin — across vanilla, TFC and any earlier
 * mod — appears automatically.
 */
public final class MctfcCreativeTab
{
    private MctfcCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineColoniesTFC.MODID);

    public static final RegistryObject<CreativeModeTab> MCTFC = TABS.register("mctfc", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mctfc"))
                    .icon(MctfcCreativeTab::icon)
                    .displayItems((params, output) -> {
                        for (final MortaredCobbleBlock twin : MortaredCobbleRegistry.twins().values())
                        {
                            output.accept(twin);
                        }
                    })
                    .build());

    /** Tab icon: the mortared twin of vanilla cobblestone, falling back to plain cobblestone if absent. */
    private static ItemStack icon()
    {
        final MortaredCobbleBlock vanillaTwin = MortaredCobbleRegistry.twinFor(Blocks.COBBLESTONE);
        return new ItemStack(vanillaTwin != null ? vanillaTwin : Blocks.COBBLESTONE);
    }
}
