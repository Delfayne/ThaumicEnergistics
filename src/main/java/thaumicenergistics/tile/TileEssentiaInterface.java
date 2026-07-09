package thaumicenergistics.tile;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.me.GridAccessException;

import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.grid.GridUtil;
import thaumicenergistics.integration.appeng.grid.IEssentiaStorageMonitorable;
import thaumicenergistics.util.ForgeUtil;

public class TileEssentiaInterface extends TileNetwork implements IEssentiaStorageMonitorable {

    @Override
    public IMEMonitor<IAEEssentiaStack> getEssentiaInventory(
            IGrid requesterGrid, IActionSource requesterSource) {
        if (ForgeUtil.isClient() || !this.isActive()) return null;

        IGrid myGrid;
        try {
            myGrid = GridUtil.getGrid(this);
        } catch (GridAccessException e) {
            return null;
        }
        // Refuse same-grid access: a storage bus on the SAME network reading this tile's own
        // network would create a circular total (the network's reported storage would include
        // whatever the storage bus reports, which is itself a view of the network's storage).
        // Capacity/buffering does not protect against this -- only a same-grid refusal does.
        if (myGrid == null || myGrid == requesterGrid) return null;

        if (!this.hasAccess(myGrid, requesterSource)) return null;

        try {
            IStorageGrid storage = GridUtil.getStorageGrid(this);
            return storage.getInventory(this.getChannel());
        } catch (GridAccessException e) {
            return null;
        }
    }

    /** Mirrors AE2's own Platform#canAccess: checks the requester against THIS tile's own grid. */
    private boolean hasAccess(IGrid myGrid, IActionSource requesterSource) {
        ISecurityGrid security = myGrid.getCache(ISecurityGrid.class);
        if (security == null || !security.isAvailable()) return true;

        if (requesterSource.player().isPresent()) {
            return security.hasPermission(
                    requesterSource.player().get(), SecurityPermissions.BUILD);
        }
        if (requesterSource.machine().isPresent()) {
            IActionHost te = requesterSource.machine().get();
            IGridNode node = te.getActionableNode();
            if (node == null) return false;
            return security.hasPermission(node.getPlayerID(), SecurityPermissions.BUILD);
        }
        return false;
    }
}
