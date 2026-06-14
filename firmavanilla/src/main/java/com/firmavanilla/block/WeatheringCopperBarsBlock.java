package com.firmavanilla.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Copper bars that oxidise like vanilla copper. Identical wiring to {@link net.minecraft.world.level.block.WeatheringCopperFullBlock}
 * (random-tick oxidation via {@link WeatheringCopper}/{@code ChangeOverTimeBlock}), but extends {@link IronBarsBlock}
 * so it keeps the bar shape/connections. Axe-scraping, honeycomb-waxing and lightning de-oxidation all work
 * automatically because our blocks are spliced into vanilla's copper maps (see
 * {@link com.firmavanilla.weathering.WeatheringMaps}) and Forge's {@code AxeItem}/{@code HoneycombItem}/{@code LightningBolt}
 * read those maps.
 *
 * <p>The {@code UNAFFECTED} (bright) stage has <b>no item of its own</b> — it stands in for TFC's
 * {@code tfc:metal/bars/copper}: that item places this block (via a placement-swap event), this block's loot drops
 * the TFC item, and pick-block ({@link #getCloneItemStack}) returns it too. So bright firmavanilla copper bars are
 * indistinguishable from TFC's, while the aged stages are our own items.
 */
public class WeatheringCopperBarsBlock extends IronBarsBlock implements WeatheringCopper
{
    /** TFC's copper bars — the item the bright (UNAFFECTED) stage stands in for. */
    public static final ResourceLocation TFC_COPPER_BARS = new ResourceLocation("tfc", "metal/bars/copper");

    private final WeatheringCopper.WeatherState age;

    public WeatheringCopperBarsBlock(final WeatheringCopper.WeatherState age, final BlockBehaviour.Properties properties)
    {
        super(properties);
        this.age = age;
    }

    @Override
    public void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random)
    {
        this.onRandomTick(state, level, pos, random);
    }

    @Override
    public boolean isRandomlyTicking(final BlockState state)
    {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    public WeatheringCopper.WeatherState getAge()
    {
        return this.age;
    }

    /** Pick-block on the bright stage yields TFC's copper bars item (this stage has no item of its own). */
    @Override
    public ItemStack getCloneItemStack(final BlockState state, final HitResult target, final BlockGetter level, final BlockPos pos, final Player player)
    {
        if (this.age == WeatheringCopper.WeatherState.UNAFFECTED)
        {
            final Item tfc = ForgeRegistries.ITEMS.getValue(TFC_COPPER_BARS);
            if (tfc != null) return new ItemStack(tfc);
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }
}
