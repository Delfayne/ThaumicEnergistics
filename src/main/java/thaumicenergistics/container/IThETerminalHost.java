package thaumicenergistics.container;

import appeng.api.storage.ITerminalHost;

import thaumicenergistics.integration.appeng.util.ThEConfigManager;

/**
 * Shared surface a terminal container needs from whatever hosts it - a cable-bus part or a wireless
 * item.
 */
public interface IThETerminalHost extends ITerminalHost {
    ThEConfigManager getConfigManager();

    boolean isPowered();

    boolean isActive();
}
