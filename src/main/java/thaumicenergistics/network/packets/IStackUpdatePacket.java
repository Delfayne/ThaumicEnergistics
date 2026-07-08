package thaumicenergistics.network.packets;

import appeng.api.storage.data.IAEStack;

import java.io.IOException;
import java.nio.BufferOverflowException;

/**
 * Common shape a storage-channel update packet (item, essentia, ...) needs to expose so terminal
 * containers can batch and send them generically via {@link
 * thaumicenergistics.container.ThETerminalNetworkSync}, regardless of which channel's stacks it
 * carries.
 *
 * @author Alex811
 */
public interface IStackUpdatePacket<T extends IAEStack<T>> {

    void appendStack(T stack) throws IOException, BufferOverflowException;

    boolean isEmpty();
}
