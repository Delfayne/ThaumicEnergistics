package thaumicenergistics.config;

import appeng.api.config.*;
import appeng.api.util.IConfigManager;

import java.util.HashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages AE settings for Parts and TileEntities.
 *
 * @author Alex811
 */
public final class AESettings {
    private static final HashMap<SUBJECT, HashMap<Settings, Enum<?>>> SETTINGS = new HashMap<>();

    public enum SUBJECT {
        ARCANE_TERMINAL,
        ESSENTIA_TERMINAL,
        ESSENTIA_IMPORT_BUS,
        ESSENTIA_EXPORT_BUS,
        ESSENTIA_STORAGE_BUS,
        ESSENTIA_LEVEL_EMITTER
    }

    static {
        addSetting(SUBJECT.ARCANE_TERMINAL, Settings.SORT_BY, SortOrder.NAME);
        addSetting(SUBJECT.ARCANE_TERMINAL, Settings.VIEW_MODE, ViewItems.ALL);
        addSetting(SUBJECT.ARCANE_TERMINAL, Settings.SORT_DIRECTION, SortDir.ASCENDING);

        addSetting(SUBJECT.ESSENTIA_TERMINAL, Settings.SORT_BY, SortOrder.NAME);
        addSetting(SUBJECT.ESSENTIA_TERMINAL, Settings.SORT_DIRECTION, SortDir.ASCENDING);

        addSetting(SUBJECT.ESSENTIA_IMPORT_BUS, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);

        addSetting(SUBJECT.ESSENTIA_EXPORT_BUS, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);

        addSetting(SUBJECT.ESSENTIA_STORAGE_BUS, Settings.ACCESS, AccessRestriction.READ_WRITE);
        addSetting(
                SUBJECT.ESSENTIA_STORAGE_BUS,
                Settings.STORAGE_FILTER,
                StorageFilter.EXTRACTABLE_ONLY);

        addSetting(
                SUBJECT.ESSENTIA_LEVEL_EMITTER,
                Settings.REDSTONE_EMITTER,
                RedstoneMode.HIGH_SIGNAL);
    }

    private static void addSetting(SUBJECT settingSubject, Settings setting, Enum<?> def) {
        if (!SETTINGS.containsKey(settingSubject)) SETTINGS.put(settingSubject, new HashMap<>());
        SETTINGS.get(settingSubject).put(setting, def);
    }

    public static void registerSettings(
            @Nullable SUBJECT settingSubject, @Nonnull IConfigManager configManager) {
        if (settingSubject != null)
            SETTINGS.get(settingSubject).forEach(configManager::registerSetting);
    }
}
