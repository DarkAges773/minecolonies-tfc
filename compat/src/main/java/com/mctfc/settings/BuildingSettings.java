package com.mctfc.settings;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A tiny registry for grafting <b>our own settings</b> onto existing MineColonies buildings' Settings tab,
 * without forking the building or its registration. Register once at mod setup; {@code MixinAbstractBuildingModule}
 * then attaches every matching setting to a building's {@link SettingsModule} as it's created.
 *
 * <p>This works because MineColonies' settings are fully data-driven on the wire: {@code SettingsModule}
 * serializes each setting as <i>(key {@link net.minecraft.resources.ResourceLocation}, value via the factory
 * controller)</i> and reconstructs the {@code SettingKey} from that on load/sync — so a brand-new key needs
 * <b>no central registration</b>, only that the setting <i>type</i> (e.g. {@code IntSetting}) has its standard
 * factory (the built-ins all do). Serialization, client sync, GUI rendering and persistence then come for free.
 *
 * <p><b>Adding a setting</b> (the whole recipe):
 * <ol>
 *   <li>Define a {@code public static final ISettingKey<…> KEY = new SettingKey<>(Type.class, new
 *       ResourceLocation("mctfc", "your_setting"));}</li>
 *   <li>{@code BuildingSettings.register(b -> b instanceof BuildingX, KEY, () -> new IntSetting(default));} in
 *       the mod's setup (must run before any building is created).</li>
 *   <li>Add a lang entry for the GUI label under the key
 *       {@code "com.minecolonies.coremod.setting.mctfc:your_setting"} (MineColonies builds the label from
 *       {@code "com.minecolonies.coremod.setting." + key.getUniqueId()}).</li>
 *   <li>Read it server-side with {@code building.getSetting(KEY).getValue()}.</li>
 * </ol>
 */
public final class BuildingSettings
{
    private BuildingSettings() {}

    private record Registration(Predicate<IBuilding> appliesTo, ISettingKey<?> key, Supplier<? extends ISetting<?>> factory) {}

    private static final List<Registration> REGISTRATIONS = new ArrayList<>();

    /**
     * Register a setting to auto-attach to every building matching {@code appliesTo}. Call during mod setup
     * (before colonies/buildings load). {@code factory} supplies a fresh default-valued setting per building.
     */
    public static void register(final Predicate<IBuilding> appliesTo, final ISettingKey<?> key,
            final Supplier<? extends ISetting<?>> factory)
    {
        REGISTRATIONS.add(new Registration(appliesTo, key, factory));
    }

    /** Attach all matching registered settings to a building's settings module (idempotent — won't clobber a
     * value already present, e.g. one just loaded from NBT). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void attach(final SettingsModule module, final IBuilding building)
    {
        for (final Registration registration : REGISTRATIONS)
        {
            if (registration.appliesTo().test(building) && module.getSetting((ISettingKey) registration.key()) == null)
            {
                module.with(registration.key(), registration.factory().get());
            }
        }
    }
}
