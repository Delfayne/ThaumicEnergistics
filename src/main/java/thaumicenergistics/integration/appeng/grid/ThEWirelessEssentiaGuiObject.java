package thaumicenergistics.integration.appeng.grid;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.DimensionalCoord;
import appeng.tile.networking.TileWireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import thaumicenergistics.container.IThETerminalHost;
import thaumicenergistics.integration.appeng.util.ThEConfigManager;
import thaumicenergistics.item.ItemWirelessEssentiaTerminal;

import javax.annotation.Nullable;

/**
 * The essentia analogue of AE2's {@code appeng.helpers.WirelessTerminalGuiObject} - resolves a held
 * wireless essentia terminal's encryption key to a security station's grid, then re-derives the
 * nearest in-range, active wireless access point on every call rather than caching the connection,
 * matching AE2's own dynamic-resolution behavior.
 *
 * @author Alex811
 */
public class ThEWirelessEssentiaGuiObject implements IThETerminalHost, IActionHost {

    private final ItemWirelessEssentiaTerminal handler;
    private final ItemStack stack;
    private final EntityPlayer player;
    private IGrid targetGrid;
    private IStorageGrid storageGrid;
    private IWirelessAccessPoint myWap;

    public ThEWirelessEssentiaGuiObject(
            ItemWirelessEssentiaTerminal handler, ItemStack stack, EntityPlayer player) {
        this.handler = handler;
        this.stack = stack;
        this.player = player;

        ILocatable located = null;
        try {
            long encKey = Long.parseLong(handler.getEncryptionKey(stack));
            located = AEApi.instance().registries().locatable().getLocatableBy(encKey);
        } catch (NumberFormatException ignored) {
        }

        if (located instanceof IActionHost) {
            IGridNode node = ((IActionHost) located).getActionableNode();
            if (node != null) {
                this.targetGrid = node.getGrid();
                if (this.targetGrid != null) {
                    this.storageGrid = this.targetGrid.getCache(IStorageGrid.class);
                }
            }
        }
    }

    /**
     * Re-scans the target grid's wireless access points for the nearest active one within range of
     * the player. Returns false if unlinked or none are in range.
     */
    public boolean rangeCheck() {
        this.myWap = null;
        if (this.targetGrid == null) return false;

        double bestRangeSq = Double.MAX_VALUE;
        IMachineSet waps = this.targetGrid.getMachines(TileWireless.class);
        for (IGridNode node : waps) {
            IWirelessAccessPoint wap = (IWirelessAccessPoint) node.getMachine();
            if (!wap.isActive()) continue;

            double rangeLimit = wap.getRange();
            rangeLimit *= rangeLimit;

            DimensionalCoord loc = wap.getLocation();
            if (loc.getWorld() != this.player.world) continue;

            double offX = loc.x - this.player.posX;
            double offY = loc.y - this.player.posY;
            double offZ = loc.z - this.player.posZ;
            double distSq = offX * offX + offY * offY + offZ * offZ;

            if (distSq < rangeLimit && distSq < bestRangeSq) {
                bestRangeSq = distSq;
                this.myWap = wap;
            }
        }
        return this.myWap != null;
    }

    public boolean usePower(double amount) {
        return this.handler.usePower(this.player, amount, this.stack);
    }

    public boolean hasPower(double amount) {
        return this.handler.hasPower(this.player, amount, this.stack);
    }

    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T> channel) {
        if (this.storageGrid == null) return null;
        return this.storageGrid.getInventory(channel);
    }

    @Override
    public ThEConfigManager getConfigManager() {
        return this.handler.getConfigManager(this.stack);
    }

    @Override
    public boolean isPowered() {
        if (this.targetGrid == null) return false;
        IEnergyGrid energy = this.targetGrid.getCache(IEnergyGrid.class);
        return energy != null && energy.isNetworkPowered();
    }

    @Override
    public boolean isActive() {
        return this.myWap != null && this.myWap.isActive();
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        this.rangeCheck();
        return this.myWap != null ? this.myWap.getActionableNode() : null;
    }
}
