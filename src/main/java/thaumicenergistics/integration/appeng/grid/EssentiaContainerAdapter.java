package thaumicenergistics.integration.appeng.grid;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IItemList;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.EssentiaFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps a IAspectContainer for use by a ME system
 *
 * <p>Used by Essentia Storage Bus
 *
 * @author BrockWS
 * @author Alex811
 */
public class EssentiaContainerAdapter implements IMEInventoryHandler<IAEEssentiaStack> {

    private IAspectContainer container;
    private EssentiaFilter config;
    private IncludeExclude whitelistMode;
    private AccessRestriction cachedAccessRestriction;
    private boolean hasReadAccess;
    private boolean hasWriteAccess;
    private boolean reportInaccessible;
    private int priority;

    // Snapshot of the container's contents as of our own last committed inject/extract (or
    // construction). Only ever compared against in pollForExternalChanges() -- kept in sync with
    // every MODULATE operation we perform, so the poll only ever surfaces changes that happened
    // through some other means (a player's hand, another mod, another part touching the same
    // container directly), never our own already-accounted-for transactions.
    private AspectList lastKnownAspects;

    public EssentiaContainerAdapter(
            IAspectContainer container,
            EssentiaFilter config,
            boolean whitelist,
            AccessRestriction access,
            StorageFilter filter,
            int priority) {
        this.container = container;
        this.config = config;
        this.setWhitelist(whitelist);
        this.setBaseAccess(access);
        this.setReportInaccessible(filter);
        this.priority = priority;
        this.lastKnownAspects = container.getAspects().copy();
    }

    public boolean isWhitelist() {
        return this.whitelistMode == IncludeExclude.WHITELIST;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelistMode = (whitelist ? IncludeExclude.WHITELIST : IncludeExclude.BLACKLIST);
    }

    public void setReportInaccessible(StorageFilter reportInaccessible) {
        this.reportInaccessible = reportInaccessible != StorageFilter.EXTRACTABLE_ONLY;
    }

    @Override
    public IAEEssentiaStack injectItems(
            IAEEssentiaStack input, Actionable type, IActionSource src) {
        if (input == null || !input.isMeaningful() || !this.canAccept(input)) return input;

        // Add to container to see how much it can store
        int notAdded = this.container.addToContainer(input.getAspect(), (int) input.getStackSize());
        if (type == Actionable.SIMULATE) {
            this.container.takeFromContainer(
                    input.getAspect(), (int) input.getStackSize() - notAdded);
        } else {
            this.resyncSnapshot(); // MODULATE actually committed a change
        }
        // Didn't add it all
        if (notAdded > 0) {
            return input.setStackSize(notAdded);
        }
        return null;
    }

    @Override
    public IAEEssentiaStack extractItems(
            IAEEssentiaStack request, Actionable mode, IActionSource src) {
        if (request == null || !request.isMeaningful()) return null;
        if (!this.hasReadAccess) return null;
        // Make sure the container actually contains it
        if (this.container.containerContains(request.getAspect()) <= 0) {
            return null;
        }

        Aspect aspect = request.getAspect();
        int max = (int) Math.min(this.container.containerContains(aspect), request.getStackSize());

        if (mode == Actionable.SIMULATE) return AEUtil.getAEStackFromAspect(aspect, max);

        boolean worked = this.container.takeFromContainer(aspect, max);
        if (!worked) return null;
        this.resyncSnapshot(); // MODULATE actually committed a change

        return request.setStackSize(max);
    }

    @Override
    public IItemList<IAEEssentiaStack> getAvailableItems(IItemList<IAEEssentiaStack> out) {
        if (this.container == null || (!this.hasReadAccess && !this.reportInaccessible)) return out;
        for (Aspect aspect : this.container.getAspects().getAspects())
            out.add(AEUtil.getAEStackFromAspect(aspect, this.container.containerContains(aspect)));
        return out;
    }

    /** Resyncs the change-detection snapshot to the container's actual current contents. */
    private void resyncSnapshot() {
        this.lastKnownAspects = this.container.getAspects().copy();
    }

    /**
     * Diffs the container's current contents against the last-known snapshot (kept in sync with our
     * own committed inject/extract calls) and returns the deltas for anything that changed through
     * some other means. Resyncs the snapshot to the current state before returning.
     */
    public List<IAEEssentiaStack> pollForExternalChanges() {
        if (this.container == null) return Collections.emptyList();

        AspectList current = this.container.getAspects();
        Set<Aspect> touchedAspects = new HashSet<>();
        Collections.addAll(touchedAspects, this.lastKnownAspects.getAspects());
        Collections.addAll(touchedAspects, current.getAspects());

        List<IAEEssentiaStack> deltas = new ArrayList<>();
        for (Aspect aspect : touchedAspects) {
            int delta = current.getAmount(aspect) - this.lastKnownAspects.getAmount(aspect);
            if (delta != 0) deltas.add(AEUtil.getAEStackFromAspect(aspect, delta));
        }

        this.lastKnownAspects = current.copy();
        return deltas;
    }

    public void setBaseAccess(AccessRestriction access) {
        this.cachedAccessRestriction = access;
        this.hasReadAccess = access.hasPermission(AccessRestriction.READ);
        this.hasWriteAccess = access.hasPermission(AccessRestriction.WRITE);
    }

    @Override
    public AccessRestriction getAccess() {
        return this.cachedAccessRestriction;
    }

    @Override
    public boolean isPrioritized(IAEEssentiaStack input) {
        return false; // Maybe check if container instanceof TileJar and check if jar has a label
        // with same aspect
    }

    @Override
    public boolean canAccept(IAEEssentiaStack input) {
        if (this.container == null || !this.hasWriteAccess) return false;
        boolean inFilter = this.config.isInFilter(input.getAspect());
        boolean containerCanAccept = this.container.doesContainerAccept(input.getAspect());
        if (this.whitelistMode == IncludeExclude.BLACKLIST) {
            if (inFilter) return false;
            return containerCanAccept;
        }
        // on empty whitelist, allow any
        if (!this.config.hasAspects()) {
            return containerCanAccept;
        }
        return inFilter && containerCanAccept;
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
        return true; // TODO: Verify requirement
    }

    @Override
    public IStorageChannel<IAEEssentiaStack> getChannel() {
        return AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);
    }
}
