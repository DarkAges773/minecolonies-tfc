package com.mctfc.mixin;

import com.mctfc.herding.TfcHerd;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.settings.BlockSetting;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCowboy;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Defaults the builder's <b>Fill Block</b> setting to TFC loam dirt instead of vanilla dirt. The builder fills
 * solid-placeholder schematic blocks with the hut's {@code FILL_BLOCK} setting, whose stock default — vanilla
 * dirt — is unobtainable in a TFC world (and is exactly what our {@code minecraft:dirt → tfc:dirt/loam}
 * substitution rule maps away on the creative-paste path), so a fresh hut would request a block the colony can
 * never supply.
 *
 * <p>We intercept the setting's <i>registration</i> ({@code SettingsModule.with}, the single seam every
 * module-producer default flows through) rather than any one building's producer: the shared
 * {@code BuildingMiner.FILL_BLOCK} key is registered per building type with a different default, and the
 * dirt-default {@link BlockSetting} is the builder's (the miner's is cobblestone — left alone, see
 * {@link MixinEntityAIStructureMiner}). Existing huts are unaffected: {@code deserializeNBT} replaces the
 * registered setting with the saved one wholesale, so only newly created huts pick up the loam default.
 *
 * <p>{@code remap = false} — {@code with} is MineColonies' own method. The registry lookup runs at building
 * creation (registries long frozen) and falls back to the stock dirt default if TFC's loam is ever missing.
 */
@Mixin(value = SettingsModule.class, remap = false)
public class MixinSettingsModule
{
    @ModifyVariable(method = "with", at = @At("HEAD"), argsOnly = true)
    private ISetting<?> mctfc$loamFillBlockDefault(final ISetting<?> setting, final ISettingKey<?> key)
    {
        if (key == BuildingMiner.FILL_BLOCK && setting instanceof BlockSetting block && block.getDefault() == Items.DIRT)
        {
            final Item loam = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc", "dirt/loam"));
            if (loam instanceof BlockItem loamItem)
            {
                return new BlockSetting(loamItem);
            }
        }
        return setting;
    }

    /**
     * Replaces the Cowhand's <b>Milk Item</b> options (vanilla milk bucket / large milk bottle — neither can hold
     * TFC's milk <i>fluid</i>) with TFC fluid containers that actually hold milk (ceramic jug by default, wooden /
     * steel buckets), so the player can pick which container the Cowhand stocks and milks into. Same {@code with}
     * seam as the fill-block default; existing huts pick the new options up too, because {@code StringSetting}'s
     * {@code updateSetting} refreshes the option list from this (registered) setting on load while preserving the
     * saved index — hence {@link TfcHerd#milkContainerOptions()} keeps a stable order.
     */
    @ModifyVariable(method = "with", at = @At("HEAD"), argsOnly = true)
    private ISetting<?> mctfc$tfcMilkContainers(final ISetting<?> setting, final ISettingKey<?> key)
    {
        if (key == BuildingCowboy.MILK_ITEM && setting instanceof StringSetting)
        {
            final String[] options = TfcHerd.milkContainerOptions();
            if (options.length > 0)
            {
                return new StringSetting(options);
            }
        }
        return setting;
    }
}
