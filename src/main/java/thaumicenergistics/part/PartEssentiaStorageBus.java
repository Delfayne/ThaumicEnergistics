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
import appeng.api.networking.ticking.ITickManager;
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
        if (setting == Settings.ACCESS) {
            AccessRestriction access =
                    (AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS);
            if (this.handler instanceof EssentiaContainerAdapter)
                ((EssentiaContainerAdapter) this.handler).setBaseAccess(access);
            else if (this.handler instanceof EssentiaInterfaceHandler)
                ((EssentiaInterfaceHandler) this.handler).setAccess(access);
            else return;
        } else if (setting == Settings.STORAGE_FILTER) {
            // STORAGE_FILTER (report-inaccessible) only applies to the direct-container path --
            // the bridge always reports what the remote monitor itself reports, there's no
            // separate "hidden but present" concept for it.
            if (!(this.handler instanceof EssentiaContainerAdapter)) return;
            ((EssentiaContainerAdapter) this.handler)
                    .setReportInaccessible(
                            (StorageFilter)
                                    this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
        } else return;
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
    protected void filterChanged() {
        super.filterChanged();
        // An aspect being added to, removed from, or cleared from the filter changes what this
        // cell contributes to the network (e.g. EssentiaInterfaceHandler#passesFilter() gates
        // getAvailableItems()), the same class of change ACCESS/STORAGE_FILTER settings already
        // force a refresh for above. Without this, the filter logic itself is still correctly
        // enforced live on every extractItems()/getAvailableItems() call, but the network's own
        // cached/displayed list (what a terminal shows) never gets told to recompute -- it was
        // only ever refreshed by coincidence, whenever some unrelated event happened to post a
        // MENetworkCellArrayUpdate around the same time.
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
        // This isSleeping value is only ever consulted once, at the moment AE2's TickManagerCache
        // first registers this node -- it is NOT re-evaluated afterwards. If the connected
        // container isn't resolvable yet at that exact moment (e.g. grid still booting), we'd be
        // marked permanently asleep and never polled again unless something explicitly calls
        // ITickManager#wakeDevice(). See setTickingAwake(), called from getHandler()/wipeHandler(),
        // for the ongoing half of this (mirroring AE2's own PartSharedItemBus#updateState()).
        return new TickingRequest(
                ThEApi.instance().config().tickTimeEssentiaStorageBusMin(),
                ThEApi.instance().config().tickTimeEssentiaStorageBusMax(),
                !this.needsTicking(),
                false);
    }

    /**
     * True if we should be actively ticking: either polling an EssentiaContainerAdapter for
     * external changes, or retrying a subnetworking connection that hasn't succeeded yet. The
     * latter matters because, unlike a jar (a plain tile, connectable the instant it exists), an
     * Essentia Interface is itself a grid node -- freshly placed, its OWN grid needs at least a
     * tick to boot before IEssentiaStorageMonitorable#getEssentiaInventory() will stop refusing us.
     * onNeighborChanged fires synchronously with placement, before that boot completes, so the
     * first getHandler() attempt is expected to fail; without retrying, we'd be stuck permanently
     * disconnected since nothing on the remote grid notifies OUR grid when it finishes booting.
     */
    private boolean needsTicking() {
        IMEInventoryHandler<IAEEssentiaStack> handler = this.getHandler();
        if (handler instanceof EssentiaContainerAdapter) return true;
        return handler == null && this.getConnectedTE() != null;
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
        if (handler == null && this.getConnectedTE() != null) {
            // Something's physically connected (e.g. an Essentia Interface whose grid hasn't
            // finished booting yet) but we couldn't build a handler for it -- keep retrying at a
            // backed-off rate instead of sleeping with nothing left to wake us back up.
            return TickRateModulation.SLOWER;
        }
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
        // Reached only via the subnetworking path (EssentiaInterfaceHandler), when the remote
        // network's own NetworkMonitor#forceUpdate() fires after a MENetworkCellArrayUpdate on
        // THAT grid (e.g. a jar backing it was broken) -- AE2 calls onListUpdate() rather than
        // postChange() in that case specifically because it has no discrete delta to report, only
        // "the storage structure changed, re-sync". We can't diff the remote monitor ourselves
        // without polling its (potentially huge) full contents every tick -- exactly what this
        // session's original lag fix removed -- so instead treat it the same way AE2 treats a
        // genuine structural change on our OWN network: post our own MENetworkCellArrayUpdate,
        // which forces our own monitor to recompute and, in turn, calls onListUpdate() on OUR OWN
        // listeners (e.g. a subnet terminal), cascading the re-sync the same way.
        this.triggerUpdate();
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
        // A priority change alone doesn't restructure our own handler, but AE2's network only
        // sorts cell providers into priority buckets when it rescans getCellArray() (on a
        // MENetworkCellArrayUpdate) -- without this, the network keeps using whatever ordering it
        // captured at the last structural update, ignoring later priority changes entirely.
        // Mirrors AE2's own PartStorageBus#setPriority(), which calls resetCache(true) for the
        // same reason.
        this.triggerUpdate();
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
                            this.priority,
                            (AccessRestriction)
                                    this.getConfigManager().getSetting(Settings.ACCESS));
            this.handler = ifaceHandler;
            this.registeredMonitor = monitor;
            // Event-driven rather than polled: the remote monitor calls our postChange() whenever
            // its contents change, instead of us diffing its (potentially huge) contents every
            // tick -- see tickingRequest().
            monitor.addListener(this, ifaceHandler);
            this.setTickingAwake(false);
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
            // TickingRequest#isSleeping is only consulted once, at initial node registration --
            // if this handler is being (re)built afterwards (e.g. a jar placed against an already
            // -registered bus), the tick manager may still think we're asleep from that first
            // call. Explicitly wake it, mirroring AE2's own PartSharedItemBus#updateState().
            this.setTickingAwake(true);
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
        // Keep ticking if something is still physically connected -- e.g. an Essentia Interface
        // was just placed but its grid hasn't booted yet, so the very next getHandler() attempt
        // is expected to fail; tickingRequest()'s retry branch needs to keep running until it
        // eventually succeeds. Only truly sleep once nothing is connected at all.
        this.setTickingAwake(this.getConnectedTE() != null);
    }

    /**
     * Explicitly wakes/sleeps this node's ticking via ITickManager, since TickingRequest#isSleeping
     * is only read once at initial registration and never re-polled afterwards -- AE2's own
     * PartSharedItemBus#updateState() does the same thing whenever its handler validity changes.
     */
    private void setTickingAwake(boolean awake) {
        if (this.gridNode == null) return;
        IGrid grid = this.gridNode.getGrid();
        //noinspection ConstantConditions
        if (grid == null) return;
        ITickManager tickManager = grid.getCache(ITickManager.class);
        if (tickManager == null) return;
        if (awake) tickManager.wakeDevice(this.gridNode);
        else tickManager.sleepDevice(this.gridNode);
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
