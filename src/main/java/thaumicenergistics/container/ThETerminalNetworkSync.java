package thaumicenergistics.container;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IContainerListener;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import thaumicenergistics.network.PacketHandler;
import thaumicenergistics.network.packets.IStackUpdatePacket;
import thaumicenergistics.util.ThELog;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared network-sync behavior for terminal containers, mirroring AE2's own {@code
 * ContainerMEMonitorable}: batches per-tick deltas accumulated from {@code postChange} into a
 * single packet ({@link #sendDelta}), and can push a full snapshot of a monitor's storage list,
 * splitting across multiple packets if the packet size cap is hit ({@link #sendFull}).
 *
 * <p>Used by {@code ContainerArcaneTerminal}, {@code ContainerEssentiaTerminal}, and {@code
 * ContainerWirelessEssentiaTerminal} so all three terminals share one copy of this logic instead of
 * three independently-maintained ones - which is exactly how essentia's version of this fell out of
 * sync with arcane's for a long time.
 *
 * @author Alex811
 */
public final class ThETerminalNetworkSync<
        T extends IAEStack<T>, P extends IMessage & IStackUpdatePacket<T>> {

    /**
     * A packet constructor - kept separate from {@link java.util.function.Supplier} since packet
     * constructors declare {@code throws IOException}.
     */
    @FunctionalInterface
    public interface PacketFactory<P> {
        P create() throws IOException;
    }

    private final PacketFactory<P> packetFactory;

    public ThETerminalNetworkSync(PacketFactory<P> packetFactory) {
        this.packetFactory = packetFactory;
    }

    /**
     * Sends only the stacks accumulated in {@code items} (via {@code postChange}) since the last
     * call, looked up fresh against the monitor's current values, then clears the accumulator.
     * Splits across multiple packets if the packet size cap is hit, same as {@link #sendFull}.
     */
    public void sendDelta(
            IItemList<T> items, IMEMonitor<T> monitor, List<IContainerListener> listeners) {
        if (items.isEmpty()) return;

        try {
            IItemList<T> monitorCache = monitor.getStorageList();
            List<P> packets = new ArrayList<>();
            P packet = this.packetFactory.create();
            packets.add(packet);

            for (T is : items) {
                T send = monitorCache.findPrecise(is);
                if (send == null) {
                    is.setStackSize(0);
                    send = is;
                }

                try {
                    packet.appendStack(send);
                } catch (BufferOverflowException e) {
                    packet = this.packetFactory.create();
                    packet.appendStack(send);
                    packets.add(packet);
                }
            }

            packets.removeIf(P::isEmpty);
            if (!packets.isEmpty()) {
                items.resetStatus();

                for (P p : packets) {
                    for (IContainerListener c : listeners) {
                        if (c instanceof EntityPlayer) {
                            PacketHandler.sendToPlayer((EntityPlayerMP) c, p);
                        }
                    }
                }
            }
        } catch (IOException e) {
            ThELog.error("detectAndSendChanges", e);
        }
    }

    /**
     * Sends the whole monitor's current storage list to a single listener, splitting into multiple
     * packets if the packet size cap is hit.
     */
    public void sendFull(IContainerListener listener, IMEMonitor<T> monitor) {
        if (!(listener instanceof EntityPlayerMP)) return;

        try {
            P packet = this.packetFactory.create();
            IItemList<T> storage = monitor.getStorageList();

            for (T stack : storage) {
                try {
                    packet.appendStack(stack);
                } catch (BufferOverflowException e) {
                    PacketHandler.sendToPlayer((EntityPlayerMP) listener, packet);

                    packet = this.packetFactory.create();
                    packet.appendStack(stack);
                }
            }
            PacketHandler.sendToPlayer((EntityPlayerMP) listener, packet);
        } catch (IOException e) {
            ThELog.error("sendInventory", e);
        }
    }
}
