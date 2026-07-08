package thaumicenergistics.part;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.cache.NetworkMonitor;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.dv.minecraft.thaumicenergistics.Reference;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.client.gui.GuiHandler;
import thaumicenergistics.config.AESettings;
import thaumicenergistics.init.ModGUIs;
import thaumicenergistics.init.ModGlobals;
import thaumicenergistics.integration.appeng.ThEPartModel;
import thaumicenergistics.integration.appeng.grid.GridUtil;
import thaumicenergistics.item.part.ItemEssentiaLevelEmitter;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.EssentiaFilter;
import thaumicenergistics.util.ForgeUtil;

import java.util.Random;

import javax.annotation.Nonnull;

public class PartEssentiaLevelEmitter extends PartBase
        implements IStackWatcherHost, IMEMonitorHandlerReceiver<IAEEssentiaStack> {

    public static final ResourceLocation MODEL_BASE_OFF =
            new ResourceLocation(Reference.MOD_ID, "part/essentia_level_emitter/base_off");
    public static final ResourceLocation MODEL_BASE_ON =
            new ResourceLocation(Reference.MOD_ID, "part/essentia_level_emitter/base_on");
    public static final ResourceLocation MODEL_STATUS_OFF =
            new ResourceLocation(ModGlobals.MOD_ID_AE2, "part/level_emitter_status_off");
    public static final ResourceLocation MODEL_STATUS_ON =
            new ResourceLocation(ModGlobals.MOD_ID_AE2, "part/level_emitter_status_on");
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL =
            new ResourceLocation(ModGlobals.MOD_ID_AE2, "part/level_emitter_status_has_channel");

    public static final ResourceLocation[] MODELS =
            new ResourceLocation[] {
                MODEL_BASE_OFF,
                MODEL_BASE_ON,
                MODEL_STATUS_OFF,
                MODEL_STATUS_ON,
                MODEL_STATUS_HAS_CHANNEL
            };

    private static final IPartModel MODEL_OFF_OFF =
            new ThEPartModel(MODEL_BASE_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODEL_OFF_ON =
            new ThEPartModel(MODEL_BASE_OFF, MODEL_STATUS_ON);
    private static final IPartModel MODEL_OFF_HAS_CHANNEL =
            new ThEPartModel(MODEL_BASE_OFF, MODEL_STATUS_HAS_CHANNEL);
    private static final IPartModel MODEL_ON_OFF =
            new ThEPartModel(MODEL_BASE_ON, MODEL_STATUS_OFF);
    private static final IPartModel MODEL_ON_ON = new ThEPartModel(MODEL_BASE_ON, MODEL_STATUS_ON);
    private static final IPartModel MODEL_ON_HAS_CHANNEL =
            new ThEPartModel(MODEL_BASE_ON, MODEL_STATUS_HAS_CHANNEL);

    private final EssentiaFilter config =
            new EssentiaFilter(1) {
                @Override
                protected void onContentsChanged() {
                    super.onContentsChanged();
                    PartEssentiaLevelEmitter.this.host.markForSave();
                    PartEssentiaLevelEmitter.this.configureWatchers();
                }
            };

    private boolean prevState = false;
    private long lastReportedValue = 0;
    private long reportingValue = 0;
    private IStackWatcher stackWatcher;

    public PartEssentiaLevelEmitter(ItemEssentiaLevelEmitter item) {
        super(item);
    }

    @Override
    protected AESettings.SUBJECT getAESettingSubject() {
        return AESettings.SUBJECT.ESSENTIA_LEVEL_EMITTER;
    }

    public EssentiaFilter getConfig() {
        return this.config;
    }

    public long getReportingValue() {
        return this.reportingValue;
    }

    public void setReportingValue(long value) {
        this.reportingValue = value;
        this.updateState();
    }

    @Override
    public double getIdlePowerUsage() {
        return 1;
    }

    @Override
    public void settingChanged(Settings setting) {
        super.settingChanged(setting);
        this.updateState();
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        this.stackWatcher = newWatcher;
        this.configureWatchers();
    }

    @Override
    public void onStackChange(
            IItemList<?> o,
            IAEStack<?> fullStack,
            IAEStack<?> diffStack,
            IActionSource src,
            IStorageChannel<?> chan) {
        Aspect target = this.config.getAspect(0);
        if (target != null
                && chan == this.getChannel()
                && fullStack.equals(AEUtil.getAEStackFromAspect(target, 0))) {
            this.lastReportedValue = fullStack.getStackSize();
            this.updateState();
        }
    }

    @MENetworkEventSubscribe
    public void levelEmitterPowerRender(MENetworkPowerStatusChange event) {
        if (this.isActive()) this.onListUpdate();
        this.updateState();
    }

    @MENetworkEventSubscribe
    public void levelEmitterChanRender(MENetworkChannelsChanged event) {
        if (this.isActive()) this.onListUpdate();
        this.updateState();
    }

    @Override
    public boolean isValid(Object effectiveGrid) {
        return this.gridNode != null && this.gridNode.getGrid() == effectiveGrid;
    }

    @Override
    public void postChange(
            IBaseMonitor<IAEEssentiaStack> monitor,
            Iterable<IAEEssentiaStack> change,
            IActionSource actionSource) {
        this.updateReportingValue((IMEMonitor<IAEEssentiaStack>) monitor);
    }

    @Override
    public void onListUpdate() {
        IMEMonitor<IAEEssentiaStack> inventory = this.getInventory();
        if (inventory != null) this.updateReportingValue(inventory);
    }

    private IEssentiaStorageChannel getChannel() {
        return AEUtil.getStorageChannel(IEssentiaStorageChannel.class);
    }

    private IMEMonitor<IAEEssentiaStack> getInventory() {
        if (this.gridNode == null) return null;
        try {
            return GridUtil.getStorageGrid(this.gridNode).getInventory(this.getChannel());
        } catch (GridAccessException e) {
            return null;
        }
    }

    private void configureWatchers() {
        if (this.stackWatcher == null) return;
        this.stackWatcher.reset();

        IMEMonitor<IAEEssentiaStack> inventory = this.getInventory();
        if (inventory == null) return;

        Aspect target = this.config.getAspect(0);
        if (target != null) {
            inventory.removeListener(this);
            this.stackWatcher.add(AEUtil.getAEStackFromAspect(target, 0));
        } else {
            inventory.addListener(this, this.gridNode.getGrid());
        }

        this.updateReportingValue(inventory);
    }

    private void updateReportingValue(IMEMonitor<IAEEssentiaStack> monitor) {
        Aspect target = this.config.getAspect(0);
        if (target == null) {
            if (monitor instanceof NetworkMonitor) {
                this.lastReportedValue =
                        ((NetworkMonitor<IAEEssentiaStack>) monitor).getGridCurrentCount();
            }
        } else {
            IAEEssentiaStack found =
                    monitor.getStorageList().findPrecise(AEUtil.getAEStackFromAspect(target, 0));
            this.lastReportedValue = found == null ? 0 : found.getStackSize();
        }
        this.updateState();
    }

    private void updateState() {
        boolean isOn = this.calculateIsOn();
        if (this.prevState != isOn) {
            this.prevState = isOn;
            this.host.markForUpdate();
            this.host.notifyNeighbors();
        }
    }

    private boolean calculateIsOn() {
        if (!this.isActive()) return false;
        boolean flipState =
                this.getConfigManager().getSetting(Settings.REDSTONE_EMITTER)
                        == RedstoneMode.LOW_SIGNAL;
        return flipState == (this.reportingValue > this.lastReportedValue);
    }

    @Override
    public int isProvidingStrongPower() {
        return this.prevState ? 15 : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return this.prevState ? 15 : 0;
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(7, 7, 11, 9, 9, 16);
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, Random r) {
        if (this.prevState) {
            AEPartLocation d = this.side;
            double d0 = d.xOffset * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            double d1 = d.yOffset * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            double d2 = d.zOffset * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            world.spawnParticle(
                    EnumParticleTypes.REDSTONE,
                    0.5 + pos.getX() + d0,
                    0.5 + pos.getY() + d1,
                    0.5 + pos.getZ() + d2,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, Vec3d vec3d) {
        if (ForgeUtil.isServer())
            GuiHandler.openGUI(
                    ModGUIs.ESSENTIA_LEVEL_EMITTER, player, this.hostTile.getPos(), this.side);
        return true;
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered())
            return this.prevState ? MODEL_ON_HAS_CHANNEL : MODEL_OFF_HAS_CHANNEL;
        else if (this.isPowered()) return this.prevState ? MODEL_ON_ON : MODEL_OFF_ON;
        return this.prevState ? MODEL_ON_OFF : MODEL_OFF_OFF;
    }

    @Override
    public void writeToStream(ByteBuf buf) {
        super.writeToStream(buf);
        buf.writeBoolean(this.prevState);
    }

    @Override
    public boolean readFromStream(ByteBuf buf) {
        boolean ret = super.readFromStream(buf);
        boolean old = this.prevState;
        this.prevState = buf.readBoolean();
        return ret || old != this.prevState;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.lastReportedValue = tag.getLong("lastReportedValue");
        this.reportingValue = tag.getLong("reportingValue");
        this.prevState = tag.getBoolean("prevState");
        if (tag.hasKey("config")) this.config.deserializeNBT(tag.getCompoundTag("config"));
        this.getConfigManager().readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("lastReportedValue", this.lastReportedValue);
        tag.setLong("reportingValue", this.reportingValue);
        tag.setBoolean("prevState", this.prevState);
        tag.setTag("config", this.config.serializeNBT());
        this.getConfigManager().writeToNBT(tag);
    }
}
