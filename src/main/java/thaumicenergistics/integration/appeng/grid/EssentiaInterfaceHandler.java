package thaumicenergistics.integration.appeng.grid;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IItemList;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.EssentiaFilter;

/**
 * Wraps a remote network's essentia storage (exposed via IEssentiaStorageMonitorable) for use as a
 * cell by a PartEssentiaStorageBus on a DIFFERENT network -- the subnetworking/bridging path.
 *
 * <p>Unlike EssentiaContainerAdapter (which wraps a small, bounded IAspectContainer like a jar),
 * this delegates directly to the target's live IMEMonitor rather than being polled/diffed every
 * tick -- the target's own monitor already tracks changes and notifies listeners itself, and the
 * owning part registers itself as one of those listeners instead of ticking this handler.
 */
public class EssentiaInterfaceHandler implements IMEInventoryHandler<IAEEssentiaStack> {

    private final IEssentiaStorageMonitorable target;
    private final IGrid requesterGrid;
    private final IActionSource requesterSource;
    private final EssentiaFilter config;
    private IncludeExclude whitelistMode;
    private int priority;
    private AccessRestriction access;

    public EssentiaInterfaceHandler(
            IEssentiaStorageMonitorable target,
            IGrid requesterGrid,
            IActionSource requesterSource,
            EssentiaFilter config,
            boolean whitelist,
            int priority,
            AccessRestriction access) {
        this.target = target;
        this.requesterGrid = requesterGrid;
        this.requesterSource = requesterSource;
        this.config = config;
        this.setWhitelist(whitelist);
        this.priority = priority;
        this.access = access;
    }

    public void setAccess(AccessRestriction access) {
        this.access = access;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelistMode = whitelist ? IncludeExclude.WHITELIST : IncludeExclude.BLACKLIST;
    }

    private boolean passesFilter(Aspect aspect) {
        boolean inFilter = this.config.isInFilter(aspect);
        if (this.whitelistMode == IncludeExclude.BLACKLIST) return !inFilter;
        if (!this.config.hasAspects()) return true; // empty whitelist -> allow any
        return inFilter;
    }

    /**
     * Re-resolved on every call rather than cached: the same-grid guard inside
     * IEssentiaStorageMonitorable#getEssentiaInventory must be re-checked every time, since grid
     * topology (e.g. two networks merging) can change after this handler was built.
     */
    private IMEMonitor<IAEEssentiaStack> monitor() {
        return this.target.getEssentiaInventory(this.requesterGrid, this.requesterSource);
    }

    @Override
    public IAEEssentiaStack injectItems(
            IAEEssentiaStack input, Actionable type, IActionSource src) {
        if (input == null
                || !this.access.hasPermission(AccessRestriction.WRITE)
                || !this.passesFilter(input.getAspect())) return input;
        IMEMonitor<IAEEssentiaStack> monitor = this.monitor();
        if (monitor == null) return input;
        // Deliberately no manual notify-own-network call here, matching extractItems() below: the
        // remote monitor's own postAlterationOfStoredItems (triggered inside whatever cell
        // provider backs it, e.g. EssentiaContainerAdapter) already reaches our own network
        // through the listener registration set up in PartEssentiaStorageBus#getHandler().
        return monitor.injectItems(input, type, src);
    }

    @Override
    public IAEEssentiaStack extractItems(
            IAEEssentiaStack request, Actionable mode, IActionSource src) {
        if (request == null
                || !this.access.hasPermission(AccessRestriction.READ)
                || !this.passesFilter(request.getAspect())) return null;
        IMEMonitor<IAEEssentiaStack> monitor = this.monitor();
        if (monitor == null) return null;
        // Deliberately no manual notify-own-network call here: the owning PartEssentiaStorageBus
        // is already registered as a listener directly on this (the remote/target) monitor (see
        // PartEssentiaStorageBus#getHandler()), and forwards any change to our own network via its
        // own postChange(). Posting again here would double-count every extraction -- the remote
        // monitor's own postAlterationOfStoredItems (triggered inside EssentiaContainerAdapter, or
        // whatever cell provider backs it) already reaches us through that listener registration.
        return monitor.extractItems(request, mode, src);
    }

    @Override
    public IItemList<IAEEssentiaStack> getAvailableItems(IItemList<IAEEssentiaStack> out) {
        IMEMonitor<IAEEssentiaStack> monitor = this.monitor();
        if (monitor == null) return out;
        for (IAEEssentiaStack stack : monitor.getStorageList())
            if (this.passesFilter(stack.getAspect())) out.add(stack);
        return out;
    }

    @Override
    public AccessRestriction getAccess() {
        return this.access;
    }

    @Override
    public boolean isPrioritized(IAEEssentiaStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEEssentiaStack input) {
        if (input == null || !this.access.hasPermission(AccessRestriction.WRITE)) return false;
        if (!this.passesFilter(input.getAspect())) return false;
        IMEMonitor<IAEEssentiaStack> monitor = this.monitor();
        return monitor != null && monitor.canAccept(input);
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public IStorageChannel<IAEEssentiaStack> getChannel() {
        return AEUtil.getStorageChannel(IEssentiaStorageChannel.class);
    }
}
