package thaumicenergistics.integration.appeng.grid;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;

import thaumicenergistics.api.storage.IAEEssentiaStack;

/**
 * Implemented by network-facing tiles (e.g. TileEssentiaInterface) that expose their own AE2
 * network's essentia storage to a PartEssentiaStorageBus connected from a different network,
 * mirroring AE2's own storage-bus-on-Interface subnetworking pattern.
 */
public interface IEssentiaStorageMonitorable {

    /**
     * @return a live view of this host's own network essentia storage, or null if access should be
     *     refused: same grid as the requester, inactive/unpowered, or the requester lacks
     *     permission on this host's own network's security system.
     */
    IMEMonitor<IAEEssentiaStack> getEssentiaInventory(
            IGrid requesterGrid, IActionSource requesterSource);
}
