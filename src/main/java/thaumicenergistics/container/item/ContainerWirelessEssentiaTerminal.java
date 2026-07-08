package thaumicenergistics.container.item;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigurableObject;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.items.consumables.ItemPhial;

import thaumicenergistics.api.IThELangKey;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.config.AESettings;
import thaumicenergistics.container.ActionType;
import thaumicenergistics.container.ContainerBaseTerminal;
import thaumicenergistics.integration.appeng.grid.ThEWirelessEssentiaGuiObject;
import thaumicenergistics.integration.appeng.util.ThEActionSource;
import thaumicenergistics.network.PacketHandler;
import thaumicenergistics.network.packets.PacketInvHeldUpdate;
import thaumicenergistics.network.packets.PacketMEEssentiaUpdate;
import thaumicenergistics.network.packets.PacketUIAction;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.ForgeUtil;
import thaumicenergistics.util.ThELog;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wireless (held-item) counterpart of {@link
 * thaumicenergistics.container.part.ContainerEssentiaTerminal} - same fill/empty essentia behavior,
 * but drains the terminal's power and re-checks wireless access point range every tick instead of
 * tracking a placed part's liveness, mirroring AE2's own {@code ContainerMEPortableTerminal}.
 *
 * @author Alex811
 */
public class ContainerWirelessEssentiaTerminal extends ContainerBaseTerminal
        implements IMEMonitorHandlerReceiver<IAEEssentiaStack>, IConfigurableObject {

    private static final double POWER_DRAIN_PER_10_TICKS = 5.0;

    private final ThEWirelessEssentiaGuiObject wirelessHost;
    private final IEssentiaStorageChannel channel;
    private final IActionSource playerSource;
    private final IItemList<IAEEssentiaStack> items =
            AEApi.instance()
                    .storage()
                    .getStorageChannel(IEssentiaStorageChannel.class)
                    .createList();
    private IMEMonitor<IAEEssentiaStack> monitor;
    private boolean isValidContainer = true;
    private int ticksSinceCheck = 0;

    public ContainerWirelessEssentiaTerminal(
            EntityPlayer player, ThEWirelessEssentiaGuiObject host) {
        super(player, host);
        this.wirelessHost = host;
        this.playerSource = new ThEActionSource(player);
        this.channel = AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);

        if (ForgeUtil.isServer()) {
            this.wirelessHost.rangeCheck();
            this.monitor = this.wirelessHost.getInventory(this.channel);
            if (this.monitor != null) {
                this.monitor.addListener(this, null);
            }
        }

        this.bindPlayerInventory(new PlayerMainInvWrapper(player.inventory), 0, 30);
    }

    @Override
    protected AESettings.SUBJECT getAESettingSubject() {
        return AESettings.SUBJECT.ESSENTIA_TERMINAL;
    }

    @Override
    public void onAction(EntityPlayerMP player, PacketUIAction packet) {
        if (this.monitor == null) return;
        InventoryPlayer inv = player.inventory;
        IAEEssentiaStack requestedStack = (IAEEssentiaStack) packet.getStack(this.channel);
        if (packet.action == ActionType.FILL_ESSENTIA_ITEM && requestedStack != null) {
            ItemStack toFill = inv.getItemStack().copy();
            ResourceLocation registryName = toFill.getItem().getRegistryName();
            if (toFill.isEmpty()
                    || !(toFill.getItem() instanceof IEssentiaContainerItem)
                    || registryName == null) return;
            toFill.setCount(1);

            IEssentiaContainerItem containerItem = (IEssentiaContainerItem) toFill.getItem();
            int max =
                    ThEApi.instance()
                            .config()
                            .essentiaContainerCapacity()
                            .getOrDefault(registryName.toString(), 0);
            if (max < 1
                    || (containerItem.getAspects(toFill) != null
                            && containerItem.getAspects(toFill).size() > 0)) return;

            IAEEssentiaStack stack =
                    this.monitor.extractItems(
                            requestedStack, Actionable.SIMULATE, this.playerSource);
            if (stack == null || stack.getStackSize() < max) return;
            stack.setStackSize(max);
            containerItem.setAspects(toFill, new AspectList().add(stack.getAspect(), max));
            if (toFill.getItem() instanceof ItemPhial) {
                toFill.setItemDamage(1);
            }
            boolean filledItem = false;
            if (inv.getItemStack().getCount() > 1) { // Player tried to fill multiple at once
                if (inv.addItemStackToInventory(toFill)) {
                    filledItem = true;
                    ItemStack held = inv.getItemStack();
                    held.setCount(held.getCount() - 1);
                    inv.setItemStack(held);
                    PacketHandler.sendToPlayer(player, new PacketInvHeldUpdate(held));
                }
            } else {
                player.inventory.setItemStack(toFill);
                filledItem = true;
                PacketHandler.sendToPlayer(player, new PacketInvHeldUpdate(toFill));
            }
            if (filledItem)
                this.monitor.extractItems(stack, Actionable.MODULATE, this.playerSource);
        } else if (packet.action == ActionType.EMPTY_ESSENTIA_ITEM) {
            ItemStack toEmpty = inv.getItemStack().copy();
            ResourceLocation registryName = toEmpty.getItem().getRegistryName();
            if (toEmpty.isEmpty()
                    || !(toEmpty.getItem() instanceof IEssentiaContainerItem)
                    || registryName == null) return;
            IEssentiaContainerItem containerItem = (IEssentiaContainerItem) toEmpty.getItem();
            AspectList list = containerItem.getAspects(toEmpty);
            if (list == null
                    || list.size() < 1
                    || ThEApi.instance()
                                    .config()
                                    .essentiaContainerCapacity()
                                    .getOrDefault(registryName.toString(), 0)
                            < 1) return;
            AtomicBoolean canInsert = new AtomicBoolean(true);
            list.aspects.forEach(
                    (aspect, amount) -> {
                        IAEEssentiaStack stack =
                                this.monitor.injectItems(
                                        AEUtil.getAEStackFromAspect(aspect, amount),
                                        Actionable.SIMULATE,
                                        this.playerSource);
                        if (stack != null && stack.getStackSize() > 0) canInsert.set(false);
                    });
            if (!canInsert.get()) return;

            if (toEmpty.getCount() > 1) {
                toEmpty.setCount(1);
                toEmpty.setTagCompound(null);
                toEmpty.setItemDamage(0);
                if (!inv.addItemStackToInventory(toEmpty)) return;
                ItemStack held = inv.getItemStack();
                held.setCount(held.getCount() - 1);
                inv.setItemStack(held);
                PacketHandler.sendToPlayer(player, new PacketInvHeldUpdate(held));
            } else {
                toEmpty.setTagCompound(null);
                toEmpty.setItemDamage(0);
                inv.setItemStack(toEmpty);
                PacketHandler.sendToPlayer(player, new PacketInvHeldUpdate(toEmpty));
            }
            list.aspects.forEach(
                    (aspect, amount) ->
                            this.monitor.injectItems(
                                    AEUtil.getAEStackFromAspect(aspect, amount),
                                    Actionable.MODULATE,
                                    this.playerSource));
        }
        super.onAction(player, packet);
    }

    @Override
    public boolean isValid(Object o) {
        return true;
    }

    @Override
    public void postChange(
            IBaseMonitor<IAEEssentiaStack> iBaseMonitor,
            Iterable<IAEEssentiaStack> iterable,
            IActionSource iActionSource) {
        for (IAEEssentiaStack stack : iterable) {
            this.items.add(stack);
        }
    }

    @Override
    public void onListUpdate() {
        for (IContainerListener c : this.listeners) {
            this.sendInventory(c);
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (ForgeUtil.isServer() && this.isValidContainer()) {
            if (this.monitor != this.wirelessHost.getInventory(this.channel)) {
                this.setValidContainer(false);
            }

            // Mirrors ContainerEssentiaTerminal/ContainerArcaneTerminal/AE2's own
            // ContainerMEMonitorable: postChange only accumulates what changed into this.items,
            // and the actual packet is built and sent here, once per tick, containing only those
            // changed stacks - not the whole network's essentia list on every change notification.
            if (!this.items.isEmpty()) {
                try {
                    IItemList<IAEEssentiaStack> monitorCache = this.monitor.getStorageList();
                    PacketMEEssentiaUpdate packet = new PacketMEEssentiaUpdate();

                    for (IAEEssentiaStack is : this.items) {
                        IAEEssentiaStack send = monitorCache.findPrecise(is);
                        if (send == null) {
                            is.setStackSize(0);
                            packet.appendStack(is);
                        } else {
                            packet.appendStack(send);
                        }
                    }

                    this.items.resetStatus();

                    for (IContainerListener c : this.listeners) {
                        if (c instanceof EntityPlayer) {
                            PacketHandler.sendToPlayer((EntityPlayerMP) c, packet);
                        }
                    }
                } catch (IOException e) {
                    ThELog.error("detectAndSendChanges", e);
                }
            }

            this.ticksSinceCheck++;
            if (this.ticksSinceCheck >= 10) {
                this.ticksSinceCheck = 0;
                if (!this.wirelessHost.hasPower(POWER_DRAIN_PER_10_TICKS)) {
                    this.closeWithMessage(ThEApi.instance().lang().deviceNotPowered());
                } else {
                    this.wirelessHost.usePower(POWER_DRAIN_PER_10_TICKS);
                }
            }

            if (this.isValidContainer() && !this.wirelessHost.rangeCheck()) {
                this.closeWithMessage(ThEApi.instance().lang().deviceOutOfRange());
            }
        }
        super.detectAndSendChanges();
    }

    private void closeWithMessage(IThELangKey message) {
        this.setValidContainer(false);
        if (this.player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) this.player)
                    .sendMessage(new TextComponentTranslation(message.getUnlocalizedKey()));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.isValidContainer();
    }

    public boolean isValidContainer() {
        return this.isValidContainer;
    }

    public void setValidContainer(boolean validContainer) {
        this.isValidContainer = validContainer;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        if (this.monitor != null) {
            this.monitor.removeListener(this);
        }
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        this.sendInventory(listener);
    }

    private void sendInventory(IContainerListener listener) {
        if (ForgeUtil.isClient() || !(listener instanceof EntityPlayer) || this.monitor == null)
            return;

        try {
            PacketMEEssentiaUpdate packet = new PacketMEEssentiaUpdate();
            IItemList<IAEEssentiaStack> storage = this.monitor.getStorageList();

            for (IAEEssentiaStack stack : storage) {
                try {
                    packet.appendStack(stack);
                } catch (BufferOverflowException e) {
                    PacketHandler.sendToPlayer((EntityPlayerMP) listener, packet);

                    packet = new PacketMEEssentiaUpdate();
                    packet.appendStack(stack);
                }
            }
            PacketHandler.sendToPlayer((EntityPlayerMP) listener, packet);
        } catch (IOException e) {
            ThELog.error("sendInventory", e);
        }
    }
}
