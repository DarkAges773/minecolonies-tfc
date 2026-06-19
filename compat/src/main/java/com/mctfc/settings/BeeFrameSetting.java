package com.mctfc.settings;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.ICommonSettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A MineColonies hut setting that selects a <b>subset of four slots</b> (a 4-bit mask) in a <b>single</b>
 * settings row carrying four toggle buttons — so a per-slot choice doesn't spam the settings menu with four
 * separate boolean rows.
 *
 * <p>Used by the FirmaLife Beekeeper to mark which of a hive's four frame slots the worker may scrape for
 * <b>beeswax</b> (each scrape consumes that frame's queen, so the player reserves the rest for honey/breeding).
 * The default is <b>0</b> — no slots, i.e. pure honey + autonomous breeding, no queen ever sacrificed. Read
 * server-side with {@code building.getSetting(BeeFrameSetting.KEY).getValue()} (a bitmask; bit {@code i} =
 * frame slot {@code i}); see {@code docs/tfc-beekeeper-worker.md}.
 *
 * <p>The class itself names <b>no</b> FirmaLife type — it is a generic 4-toggle setting — so it loads in any
 * world; only its <i>attachment</i> to the Beekeeper (in {@code MineColoniesTFC}) is FirmaLife-gated. State is
 * a single int (low 4 bits); its {@link BeeFrameSettingFactory} round-trips it through NBT and the network
 * buffer. The per-button handler mutates the mask then calls {@code trigger(key)} to sync the whole setting
 * (the {@code IntSetting} pattern — no per-button trigger, no custom network message).
 */
public class BeeFrameSetting implements ISetting<Integer>
{
    /** Number of toggle slots (FirmaLife hives have four frame slots). */
    public static final int SLOTS = 4;

    /** The setting key. The id's path doubles as the GUI lang key suffix ({@code com.minecolonies.coremod.setting.mctfc:wax_frames}). */
    public static final ISettingKey<BeeFrameSetting> KEY =
      new SettingKey<>(BeeFrameSetting.class, new ResourceLocation("mctfc", "wax_frames"));

    /** Low {@link #SLOTS} bits: bit i set = slot i selected. */
    private int mask;

    public BeeFrameSetting(final int mask)
    {
        this.mask = mask & ((1 << SLOTS) - 1);
    }

    @Override
    public Integer getValue()
    {
        return mask;
    }

    /** Whether frame slot {@code slot} (0..{@link #SLOTS}-1) is selected. */
    public boolean isSet(final int slot)
    {
        return (mask & (1 << slot)) != 0;
    }

    @Override
    public ResourceLocation getLayoutItem()
    {
        return new ResourceLocation("mctfc", "gui/layoutbeeframesetting.xml");
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void setupHandler(
      final ISettingKey<?> key,
      final Pane pane,
      final ICommonSettingsModule settingsModuleView,
      final IBuildingView building,
      final BOWindow window)
    {
        for (int i = 0; i < SLOTS; i++)
        {
            final int slot = i;
            final ButtonImage button = pane.findPaneOfTypeByID("slot" + i, ButtonImage.class);
            if (button != null)
            {
                button.setHandler(b -> {
                    this.mask ^= (1 << slot);
                    settingsModuleView.trigger(key);
                });
            }
        }
    }

    @Override
    public void render(
      final ISettingKey<?> key,
      final Pane pane,
      final ICommonSettingsModule settingsModuleView,
      final IBuildingView building,
      final BOWindow window)
    {
        for (int i = 0; i < SLOTS; i++)
        {
            final ButtonImage button = pane.findPaneOfTypeByID("slot" + i, ButtonImage.class);
            if (button != null)
            {
                button.setText(Component.literal(String.valueOf(i + 1))
                                 .withStyle(isSet(i) ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY));
            }
        }
        final Text desc = pane.findPaneOfTypeByID("desc", Text.class);
        if (desc != null)
        {
            setHoverPane(key, desc, settingsModuleView);
        }
    }

    @Override
    public void copyValue(final ISetting<?> setting)
    {
        if (setting instanceof final BeeFrameSetting other)
        {
            this.mask = other.mask;
        }
    }
}
