package thaumicenergistics.part;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.*;
import appeng.api.util.AECableType;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import org.dv.minecraft.thaumicenergistics.Reference;

import thaumcraft.api.aspects.IAspectContainer;

import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.client.gui.GuiHandler;
import thaumicenergistics.config.AESettings;
import thaumicenergistics.init.ModGUIs;
import thaumicenergistics.integration.appeng.ThEPartModel;
import thaumicenergistics.integration.appeng.grid.EssentiaContainerAdapter;
import thaumicenergistics.integration.appeng.grid.EssentiaInterfaceHandler;
import thaumicenergistics.integration.appeng.grid.IEssentiaStorageMonitorable;
import thaumicenergistics.item.part.ItemEssentiaStorageBus;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.ForgeUtil;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author BrockWS
 * @author Alex811
 */
public class PartEssentiaStorageBus extends PartSharedEssentiaBus
        implements ICellContainer, IMEMonitorHandlerReceiver<IAEEssentiaStack>, IPriorityHost {

    public static ResourceLocation[] MODELS =
            new ResourceLocation[] {
                new ResourceLocation(Reference.MOD_ID, "part/essentia_storage_bus/base"),
                new ResourceLocation(Reference.MOD_ID, "part/essentia_storage_bus/on"),
                new ResourceLocation(Reference.MOD_ID, "part/essentia_storage_bus/off"),
                new ResourceLocation(Reference.MOD_ID, "part/essentia_storage_bus/has_channel")
            };

    private static final IPartModel MODEL_ON = new ThEPartModel(MODELS[0], MODELS[1]);
    private static final IPartModel MODEL_OFF = new ThEPartModel(MODELS[0], MODELS[2]);
    private static final IPartModel MODEL_HAS_CHANNEL = new ThEPartModel(MODELS[0], MODELS[3]);

    private IMEInventoryHandler<IAEEssentiaStack> handler;
    // Only set (and non-null) while handler is an EssentiaInterfaceHandler, so it can be
    // unregistered when the handler is wiped -- see wipeHandler().
    private IMEMonitor<IAEEssentiaStack> registeredMonitor;
    private boolean wasActive = false;
    private TileEntity lastConnectedTE = null;
    private int priority = 0;

    public PartEssentiaStorageBus(ItemEssentiaStorageBus item) {
        super(item, 63, 5);
    }

    @Override
    protected AESettings.SUBJECT getAESettingSubject() {
        return AESettings.SUBJECT.ESSENTIA_STORAGE_BUS;
    }

    @Override
    public void settingChanged(Settings setting) {
        super.settingChanged(setting);
        // ACCESS/STORAGE_FILTER only apply to the direct-container path (EssentiaContainerAdapter)
        // for now; the subnetworking path (EssentiaInterfaceHandler) is fixed read-only until
        // insertion is added.
        if (!(this.handler instanceof EssentiaContainerAdapter)) return;
        EssentiaContainerAdapter handler = (EssentiaContainerAdapter) this.handler;
        if (setting == Settings.ACCESS)
            handler.setBaseAccess(
                    (AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS));
        else if (setting == Settings.STORAGE_FILTER)
            handler.setReportInaccessible(
                    (StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
        else return;
        this.triggerUpdate();
    }

    protected void upgradesChanged() {
        IMEInventoryHandler<IAEEssentiaStack> handler = this.getHandler();
        boolean whitelist = !this.hasInverterCard();
        if (handler instanceof EssentiaContainerAdapter)
            ((EssentiaContainerAdapter) handler).setWhitelist(whitelist);
        else if (handler instanceof EssentiaInterfaceHandler)
            ((EssentiaInterfaceHandler) handler).setWhitelist(whitelist);
        this.triggerUpdate();
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.lastConnectedTE = this.getConnectedTE();
        this.upgradeChangeListeners.add(this::upgradesChanged);
        this.upgradesChanged();
    }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        this.upgradeChangeListeners.clear();
        this.wipeHandler();
    }

    @Nonnull
    @Override
    public TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        return new TickingRequest(
                ThEApi.instance().config().tickTimeEssentiaStorageBusMin(),
                ThEApi.instance().config().tickTimeEssentiaStorageBusMax(),
                this.getHandler() == null,
                false);
    }

    @Override
    public boolean canWork() {
        return false;
    }

    @Override
    protected TickRateModulation doWork() {
        return TickRateModulation.SLOWER;
    }

    @Nonnull
    @Override
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
        // Only the direct-container path (EssentiaContainerAdapter) needs polling: detect
        // essentia added to/removed from the connected container by anything other than our own
        // inject/extract calls (a player's hand, another mod, another part touching the same
        // container) and inform the network, mirroring AE2's own PartStorageBus, which polls its
        // wrapped inventory the same way rather than relying on canWork()/doWork(). The
        // subnetworking path (EssentiaInterfaceHandler) is event-driven via postChange() instead
        // (registered as a listener on the remote network's own monitor in getHandler()), since
        // polling/diffing a whole other network's storage every tick would be expensive and is
        // exactly the kind of thing that caused the lag bug this session started by fixing.
        IMEInventoryHandler<IAEEssentiaStack> handler = this.getHandler();
        if (!(handler instanceof EssentiaContainerAdapter)) return TickRateModulation.SLEEP;

        List<IAEEssentiaStack> changes =
                ((EssentiaContainerAdapter) handler).pollForExternalChanges();
        if (changes.isEmpty()) return TickRateModulation.SLOWER;

        if (this.gridNode == null) return TickRateModulation.SLOWER;
        IGrid grid = this.gridNode.getGrid();
        //noinspection ConstantConditions
        if (grid != null) {
            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid != null) {
                storageGrid.postAlterationOfStoredItems(this.getChannel(), changes, this.source);
            }
        }
        return TickRateModulation.URGENT;
    }

    @Override
    public void postChange(
            IBaseMonitor<IAEEssentiaStack> monitor,
            Iterable<IAEEssentiaStack> change,
            IActionSource actionSource) {
        // Reached only via the subnetworking path (EssentiaInterfaceHandler), when we've
        // registered ourselves as a listener on a remote network's monitor (see getHandler());
        // forward the change to our OWN network, mirroring AE2's own PartStorageBus#postChange.
        if (this.gridNode == null) return;
        IGrid grid = this.gridNode.getGrid();
        //noinspection ConstantConditions
        if (grid == null) return;
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid != null) {
            storageGrid.postAlterationOfStoredItems(this.getChannel(), change, actionSource);
        }
    }

    @Override
    public void onListUpdate() {
        // Ignored
    }

    @Override
    public void saveChanges(ICellInventory<?> iCellInventory) {
        // Ignored
    }

    @Override
    public void onNeighborChanged(IBlockAccess access, BlockPos pos, BlockPos neighbor) {
        if (pos == null || neighbor == null) return;
        if (pos.offset(this.side.getFacing()).equals(neighbor)) {
            TileEntity connectedTE = this.getConnectedTE();
            if (this.lastConnectedTE != connectedTE) {
                this.lastConnectedTE = connectedTE;
                this.wipeHandler(); // wipe cached handler, so it gets reconstructed
                // markForUpdate() is skipped here: super.onNeighborChanged(...) below re-checks
                // this same pos/neighbor/side match and will call it for us.
                this.triggerUpdate(false);
            }
        }
        super.onNeighborChanged(access, pos, neighbor);
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, Vec3d vec3d) {
        if ((player.isSneaking()
                && AEUtil.isWrench(player.getHeldItem(hand), player, this.getTile().getPos())))
            return false;

        if (ForgeUtil.isServer())
            GuiHandler.openGUI(
                    ModGUIs.ESSENTIA_STORAGE_BUS, player, this.hostTile.getPos(), this.side);

        return true;
    }

    @Override
    public void blinkCell(int slot) {
        // Ignored
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        // ThELog.info("getCellArray");
        if (channel != this.getChannel() || this.getHandler() == null)
            return Collections.emptyList();
        // We need to "open" the connected IAspectContainer as a "cell" (IMEInventoryHandler)
        return Collections.singletonList(this.getHandler());
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int i) {
        this.priority = i;
        if (this.handler instanceof EssentiaContainerAdapter)
            ((EssentiaContainerAdapter) this.handler).setPriority(i);
        else if (this.handler instanceof EssentiaInterfaceHandler)
            ((EssentiaInterfaceHandler) this.handler).setPriority(i);
        this.host.markForSave();
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return this.getRepr();
    }

    @Override
    public GuiBridge getGuiBridge() {
        return null;
    }

    @Override
    public boolean isValid(Object verificationToken) {
        return this.handler == verificationToken;
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isPowered())
            if (this.isActive()) return MODEL_HAS_CHANNEL;
            else return MODEL_ON;
        return MODEL_OFF;
    }

    @Nullable
    private IMEInventoryHandler<IAEEssentiaStack> getHandler() {
        if (this.handler != null) return this.handler;

        TileEntity connectedTE = this.getConnectedTE();

        // Subnetworking path: the connected tile exposes another network's essentia storage
        // directly (e.g. TileEssentiaInterface) rather than being a plain essentia container.
        // Checked first since a tile could conceivably implement both.
        if (connectedTE instanceof IEssentiaStorageMonitorable) {
            if (this.gridNode == null) return null;
            IGrid myGrid = this.gridNode.getGrid();
            //noinspection ConstantConditions
            if (myGrid == null) return null;

            IEssentiaStorageMonitorable remote = (IEssentiaStorageMonitorable) connectedTE;
            IMEMonitor<IAEEssentiaStack> monitor = remote.getEssentiaInventory(myGrid, this.source);
            if (monitor == null) return null; // refused: same grid, inactive, or no permission

            EssentiaInterfaceHandler ifaceHandler =
                    new EssentiaInterfaceHandler(
                            remote,
                            myGrid,
                            this.source,
                            this.config,
                            !this.hasInverterCard(),
                            this.priority);
            this.handler = ifaceHandler;
            this.registeredMonitor = monitor;
            // Event-driven rather than polled: the remote monitor calls our postChange() whenever
            // its contents change, instead of us diffing its (potentially huge) contents every
            // tick -- see tickingRequest().
            monitor.addListener(this, ifaceHandler);
            this.triggerUpdate();
            return this.handler;
        }

        if (connectedTE instanceof IAspectContainer) {
            this.handler =
                    new EssentiaContainerAdapter(
                            (IAspectContainer) connectedTE,
                            this.config,
                            !this.hasInverterCard(),
                            (AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS),
                            (StorageFilter)
                                    this.getConfigManager().getSetting(Settings.STORAGE_FILTER),
                            this.priority,
                            this.gridNode); // init and cache handler
            // AE2's own PartStorageBus#getInternalHandler forces a cell update whenever it
            // (re)builds its handler, rather than trusting a caller to have already done so;
            // mirror that here so a handler built from a getCellArray() call outside an
            // active cellUpdate() scan (e.g. a node added while inactive) still registers.
            this.triggerUpdate();
            return this.handler;
        }

        return null;
    }

    /** Wipes the cached handler, unregistering it as a listener first if it was registered. */
    private void wipeHandler() {
        if (this.registeredMonitor != null) {
            this.registeredMonitor.removeListener(this);
            this.registeredMonitor = null;
        }
        this.handler = null;
    }

    @Override
    public void getBoxes(IPartCollisionHelper box) {
        box.addBox(3, 3, 15, 13, 13, 16);
        box.addBox(2, 2, 14, 14, 14, 15);
        box.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public float getCableConnectionLength(AECableType aeCableType) {
        return 4;
    }

    @Override
    @MENetworkEventSubscribe
    public void updateBootStatus(MENetworkBootingStatusChange event) {
        super.updateBootStatus(event);
        this.triggerBootUpdate();
    }

    @Override
    @MENetworkEventSubscribe
    public void updatePowerStatus(MENetworkPowerStatusChange event) {
        super.updatePowerStatus(event);
        this.triggerBootUpdate();
    }

    public void triggerBootUpdate() {
        final boolean currentActive = this.getGridNode().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            this.triggerUpdate();
        }
    }

    public void triggerUpdate() {
        this.triggerUpdate(true);
    }

    private void triggerUpdate(boolean alsoMarkForUpdate) {
        if (this.gridNode == null) {
            return;
        }
        IGrid grid = this.gridNode.getGrid();
        // IGridNode#getGrid() is declared @Nonnull, but AE2's own internal code (e.g.
        // CraftingCPUCluster#getGrid()) doesn't trust that contract either; guard defensively.
        //noinspection ConstantConditions
        if (grid != null) {
            grid.postEvent(new MENetworkCellArrayUpdate());
        }
        if (alsoMarkForUpdate) {
            this.host.markForUpdate();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.priority = tag.getInteger("priority");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("priority", this.priority);
    }
}
