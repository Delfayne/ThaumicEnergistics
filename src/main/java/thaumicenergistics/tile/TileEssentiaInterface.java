package thaumicenergistics.tile;

import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.me.GridAccessException;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;

import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.grid.GridUtil;
import thaumicenergistics.integration.appeng.grid.IEssentiaStorageMonitorable;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.ForgeUtil;
import thaumicenergistics.util.ThELog;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TileEssentiaInterface extends TileNetwork
        implements IEssentiaStorageMonitorable, IEssentiaTransport, IGridTickable {

    /**
     * Per-side Thaumcraft tube configuration. Entirely independent of the AE2-facing bridge below
     * (getEssentiaInventory/hasAccess) -- a storage bus reads that surface, a tube reads this one,
     * and neither shares state with the other.
     */
    public enum SideMode {
        DISABLED,
        INPUT,
        OUTPUT
    }

    // INPUT comfortably outpulls jars/tubes so essentia in a tube network flows toward us; OUTPUT
    // sits below every real consumer (Thaumcraft's pull comparisons are strict "<", and most
    // devices sit at suction 0 or above) so it never wins a pull contest -- it's a pure source,
    // only ever drained by whatever's on the other end.
    private static final int INPUT_SUCTION = 128;
    private static final int OUTPUT_SUCTION = -1;
    private static final int MINIMUM_SUCTION = 1;

    private final Map<EnumFacing, SideMode> sideModes = new EnumMap<>(EnumFacing.class);
    private final Map<EnumFacing, Aspect> outputAspects = new EnumMap<>(EnumFacing.class);

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

    // ---- Per-side configuration, driven by BlockEssentiaInterface#onBlockActivated ----

    public SideMode getSideMode(EnumFacing side) {
        return this.sideModes.getOrDefault(side, SideMode.DISABLED);
    }

    @Nullable
    public Aspect getOutputAspect(EnumFacing side) {
        return this.outputAspects.get(side);
    }

    public void setSideInput(EnumFacing side, @Nullable EntityPlayer player) {
        this.sideModes.put(side, SideMode.INPUT);
        this.outputAspects.remove(side);
        this.markDirty();
        this.notifyNeighborOfConnectivityChange();
        this.updateTickingState();
        this.notifyModeChange(
                side,
                player,
                "tooltip.thaumicenergistics.essentia_interface.side_input",
                side.getName());
    }

    public void setSideOutput(EnumFacing side, Aspect aspect, @Nullable EntityPlayer player) {
        this.sideModes.put(side, SideMode.OUTPUT);
        this.outputAspects.put(side, aspect);
        this.markDirty();
        this.notifyNeighborOfConnectivityChange();
        this.updateTickingState();
        this.notifyModeChange(
                side,
                player,
                "tooltip.thaumicenergistics.essentia_interface.side_output",
                side.getName(),
                aspect.getName());
    }

    public void disableSide(EnumFacing side, @Nullable EntityPlayer player) {
        this.sideModes.remove(side);
        this.outputAspects.remove(side);
        this.markDirty();
        this.notifyNeighborOfConnectivityChange();
        this.updateTickingState();
        this.notifyModeChange(
                side,
                player,
                "tooltip.thaumicenergistics.essentia_interface.side_disabled",
                side.getName());
    }

    /**
     * AE2 only reads {@link #getTickingRequest}'s isSleeping once, at initial grid-node
     * registration -- an interface placed with no INPUT sides registers asleep and never wakes on
     * its own just because sideModes changed afterward. Mirrors AE2's own
     * PartSharedItemBus#updateState(): explicitly wake/sleep the tick manager whenever a side
     * change could flip {@link #hasInputSides()}.
     */
    private void updateTickingState() {
        try {
            IGrid grid = GridUtil.getGrid(this);
            if (grid == null) return;
            IGridNode node = this.getActionableNode();
            if (node == null) return;
            ITickManager tickManager = grid.getCache(ITickManager.class);
            if (tickManager == null) return;
            if (this.hasInputSides()) tickManager.wakeDevice(node);
            else tickManager.sleepDevice(node);
        } catch (GridAccessException e) {
            // No grid yet -- nothing to wake/sleep.
        }
    }

    /**
     * A side's IEssentiaTransport connectability just changed, but that's only reflected in this
     * tile's own Java fields -- no actual IBlockState changed anywhere, so nothing tells a
     * neighboring tube to re-derive its connections. BlockTube bakes its NORTH/EAST/SOUTH/WEST/UP/
     * DOWN connection properties fresh inside getActualState(), which the client only re-queries
     * when that block's render cache is invalidated -- normally an automatic side effect of a real
     * world.setBlockState() call nearby, which we're not doing. notifyNeighborsOfStateChange()
     * alone only fires the neighborChanged() logic callback, not a render refresh, so we also have
     * to explicitly invalidate the render cache for the 3x3x3 area around us (matching the range
     * vanilla's own block-change pipeline uses) or the tube's connection will never visually
     * update.
     */
    private void notifyNeighborOfConnectivityChange() {
        if (this.world == null) return;
        this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), false);
        this.world.markBlockRangeForRenderUpdate(this.pos.add(-1, -1, -1), this.pos.add(1, 1, 1));
        // Belt-and-suspenders: explicitly poke each neighbor's own block-update packet too, in case
        // a range-based render mark alone doesn't reliably force that specific tile (e.g. a tube)
        // to re-derive its actual/rendered state client-side.
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos neighborPos = this.pos.offset(side);
            IBlockState neighborState = this.world.getBlockState(neighborPos);
            this.world.notifyBlockUpdate(neighborPos, neighborState, neighborState, 3);
        }
    }

    // TODO: this is a placeholder debug readout until per-side graphics are added -- see #47.
    private void notifyModeChange(
            EnumFacing side, @Nullable EntityPlayer player, String langKey, Object... args) {
        if (player != null) player.sendMessage(new TextComponentTranslation(langKey, args));
        ThELog.debug("Essentia Interface [" + side + "]: " + langKey + " " + Arrays.toString(args));
    }

    // ---- Side-config (de)serialization, shared between world-save NBT and the live client-sync
    // path (getUpdateTag/handleUpdateTag/getUpdatePacket, already used by TileNetwork for
    // powered/active) -- both need the same data, just via different TileEntity hooks.

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("sideConfig", this.writeSideConfig());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.readSideConfig(compound);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setTag("sideConfig", this.writeSideConfig());
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        this.readSideConfig(tag);
    }

    private NBTTagList writeSideConfig() {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<EnumFacing, SideMode> entry : this.sideModes.entrySet()) {
            NBTTagCompound sideTag = new NBTTagCompound();
            sideTag.setInteger("side", entry.getKey().ordinal());
            sideTag.setInteger("mode", entry.getValue().ordinal());
            Aspect aspect = this.outputAspects.get(entry.getKey());
            if (aspect != null) sideTag.setString("aspect", aspect.getTag());
            list.appendTag(sideTag);
        }
        return list;
    }

    private void readSideConfig(NBTTagCompound compound) {
        this.sideModes.clear();
        this.outputAspects.clear();
        if (!compound.hasKey("sideConfig")) return;
        NBTTagList list =
                compound.getTagList(
                        "sideConfig", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound sideTag = list.getCompoundTagAt(i);
            EnumFacing side = EnumFacing.values()[sideTag.getInteger("side")];
            SideMode mode = SideMode.values()[sideTag.getInteger("mode")];
            this.sideModes.put(side, mode);
            if (sideTag.hasKey("aspect")) {
                Aspect aspect = Aspect.getAspect(sideTag.getString("aspect"));
                if (aspect != null) this.outputAspects.put(side, aspect);
            }
        }
    }

    // ---- Shared AE2 monitor lookup for the transport surface, independent of the bridge above
    // ----

    @Nullable
    private IMEMonitor<IAEEssentiaStack> getOwnMonitor() {
        if (ForgeUtil.isClient() || !this.isActive()) return null;
        try {
            IStorageGrid storage = GridUtil.getStorageGrid(this);
            return storage.getInventory(this.getChannel());
        } catch (GridAccessException e) {
            return null;
        }
    }

    // ---- IEssentiaTransport: passive responders, queried by neighboring tubes/devices ----

    @Override
    public boolean isConnectable(EnumFacing side) {
        return this.getSideMode(side) != SideMode.DISABLED;
    }

    @Override
    public boolean canInputFrom(EnumFacing side) {
        return this.getSideMode(side) == SideMode.INPUT;
    }

    @Override
    public boolean canOutputTo(EnumFacing side) {
        return this.getSideMode(side) == SideMode.OUTPUT;
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {
        // This is a storage endpoint, not a transport hop -- ignore externally-set suction, the
        // same way Thaumcraft's own sourcing devices (jars, alembics) do.
    }

    @Override
    public Aspect getSuctionType(EnumFacing side) {
        return null; // untyped suction in both directions -- see getSuctionAmount
    }

    @Override
    public int getSuctionAmount(EnumFacing side) {
        return this.getSideMode(side) == SideMode.INPUT ? INPUT_SUCTION : OUTPUT_SUCTION;
    }

    @Override
    public int getMinimumSuction() {
        return MINIMUM_SUCTION;
    }

    @Override
    public Aspect getEssentiaType(EnumFacing side) {
        if (this.getSideMode(side) != SideMode.OUTPUT) return null;
        Aspect aspect = this.outputAspects.get(side);
        if (aspect == null || this.getEssentiaAmount(side) <= 0) return null;
        return aspect;
    }

    @Override
    public int getEssentiaAmount(EnumFacing side) {
        if (this.getSideMode(side) != SideMode.OUTPUT) return 0;
        Aspect aspect = this.outputAspects.get(side);
        if (aspect == null) return 0;
        IMEMonitor<IAEEssentiaStack> monitor = this.getOwnMonitor();
        if (monitor == null) return 0;
        IAEEssentiaStack available =
                monitor.extractItems(
                        AEUtil.getAEStackFromAspect(aspect, Integer.MAX_VALUE),
                        Actionable.SIMULATE,
                        this.src);
        return available == null ? 0 : (int) Math.min(Integer.MAX_VALUE, available.getStackSize());
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing side) {
        if (this.getSideMode(side) != SideMode.OUTPUT || aspect == null) return 0;
        if (!aspect.equals(this.outputAspects.get(side))) return 0;
        IMEMonitor<IAEEssentiaStack> monitor = this.getOwnMonitor();
        if (monitor == null) return 0;
        IAEEssentiaStack extracted =
                monitor.extractItems(
                        AEUtil.getAEStackFromAspect(aspect, amount), Actionable.MODULATE, this.src);
        return extracted == null ? 0 : (int) extracted.getStackSize();
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, EnumFacing side) {
        if (this.getSideMode(side) != SideMode.INPUT || aspect == null) return 0;
        IMEMonitor<IAEEssentiaStack> monitor = this.getOwnMonitor();
        if (monitor == null) return 0;
        IAEEssentiaStack notInserted =
                monitor.injectItems(
                        AEUtil.getAEStackFromAspect(aspect, amount), Actionable.MODULATE, this.src);
        long rejected = notInserted == null ? 0 : notInserted.getStackSize();
        return (int) (amount - rejected);
    }

    // ---- IGridTickable: INPUT sides have to actively pull, nothing ever pushes to us ----

    @Nonnull
    @Override
    public TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        return new TickingRequest(
                ThEApi.instance().config().tickTimeEssentiaInterfaceMin(),
                ThEApi.instance().config().tickTimeEssentiaInterfaceMax(),
                !this.hasInputSides(),
                false);
    }

    private boolean hasInputSides() {
        return this.sideModes.containsValue(SideMode.INPUT);
    }

    @Nonnull
    @Override
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
        if (!this.isActive() || !this.hasInputSides()) return TickRateModulation.SLEEP;

        boolean movedAny = false;
        for (EnumFacing side : EnumFacing.values())
            if (this.getSideMode(side) == SideMode.INPUT && this.pullFromNeighbor(side))
                movedAny = true;

        return movedAny ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    /**
     * Mirrors the exact pull idiom every Thaumcraft essentia device uses (TileJarFillable,
     * TileEssentiaInput, TileCentrifuge, ...): essentia flows toward higher suction, so an INPUT
     * side has to actively out-suck its neighbor rather than wait for anything to be pushed to it.
     */
    private boolean pullFromNeighbor(EnumFacing side) {
        TileEntity neighborTE = ThaumcraftApiHelper.getConnectableTile(this.world, this.pos, side);
        if (!(neighborTE instanceof IEssentiaTransport)) return false;
        IEssentiaTransport neighbor = (IEssentiaTransport) neighborTE;
        EnumFacing opposite = side.getOpposite();
        if (!neighbor.canOutputTo(opposite)) return false;

        int ourSuction = this.getSuctionAmount(side);
        if (neighbor.getSuctionAmount(opposite) >= ourSuction) return false;
        if (ourSuction < neighbor.getMinimumSuction()) return false;

        Aspect aspect = neighbor.getEssentiaType(opposite);
        if (aspect == null || neighbor.getEssentiaAmount(opposite) <= 0) return false;

        IMEMonitor<IAEEssentiaStack> monitor = this.getOwnMonitor();
        if (monitor == null) return false;
        // Simulate first -- never take from the neighbor unless the network can actually hold it,
        // essentia must not be voided.
        IAEEssentiaStack notAccepted =
                monitor.injectItems(
                        AEUtil.getAEStackFromAspect(aspect, 1), Actionable.SIMULATE, this.src);
        if (notAccepted != null && notAccepted.getStackSize() > 0) return false;

        int taken = neighbor.takeEssentia(aspect, 1, opposite);
        if (taken <= 0) return false;

        monitor.injectItems(
                AEUtil.getAEStackFromAspect(aspect, taken), Actionable.MODULATE, this.src);
        return true;
    }
}
