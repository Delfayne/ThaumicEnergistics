package thaumicenergistics.network.packets;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import thaumicenergistics.container.ActionType;
import thaumicenergistics.container.ContainerBase;
import thaumicenergistics.util.ThELog;

import java.io.IOException;

/**
 * @author BrockWS
 */
public class PacketUIAction implements IMessage {

    public ActionType action;
    public int index = -1;

    /**
     * On the sending side (client), this is the concrete stack to serialize - the sender always
     * knows its real type, same as AE2's PacketInventoryAction always deals in a single concrete
     * stack type. On the receiving side it starts null and is resolved lazily by {@link
     * #getStack(IStorageChannel)}: the handling container already knows which channel it deals in
     * (item, essentia, ...), so that channel - not the packet - decides how the raw NBT is decoded.
     */
    public IAEStack requestedStack;

    private NBTTagCompound stackNbt;

    public PacketUIAction() {}

    public PacketUIAction(ActionType action) {
        this.action = action;
    }

    public PacketUIAction(ActionType action, IAEStack stack) {
        this(action);
        this.requestedStack = stack;
    }

    public PacketUIAction(ActionType action, IAEStack stack, int index) {
        this(action);
        this.requestedStack = stack;
        this.index = index;
    }

    public PacketUIAction(ActionType action, int index) {
        this(action);
        this.index = index;
    }

    @Override
    public void fromBytes(ByteBuf in) {

        PacketBuffer packetBuffer = new PacketBuffer(in);
        NBTTagCompound nbt;
        try {
            nbt = packetBuffer.readCompoundTag();
        } catch (IOException e) {
            ThELog.error("Failed to read from packet, {}", e);
            return;
        }

        this.action = ActionType.values()[nbt.getInteger("action")];
        if (nbt.hasKey("index")) {
            this.index = nbt.getInteger("index");
        }
        if (nbt.hasKey("stack")) {
            this.stackNbt = nbt.getCompoundTag("stack");
        }
    }

    /**
     * Decodes the stack this packet carries using the given channel. The caller (a container)
     * always already knows which channel it deals in, so this never has to guess between item,
     * essentia, etc. Returns null if no stack was sent, or if it fails to decode against the given
     * channel (e.g. the wrong channel was passed in).
     *
     * <p>Deliberately not cached: this packet is only ever decoded a handful of times per receipt,
     * and caching by the first channel asked would silently hand back the wrong stack type to a
     * second caller asking with a different channel.
     */
    public IAEStack getStack(IStorageChannel<?> channel) {
        if (this.requestedStack != null) return this.requestedStack;
        if (channel == null || this.stackNbt == null) return null;
        try {
            return channel.createFromNBT(this.stackNbt);
        } catch (Throwable e) {
            ThELog.error(
                    "Failed to read stack from packet, {}", channel.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public void toBytes(ByteBuf outline) {

        PacketBuffer packetBuffer = new PacketBuffer(outline);

        NBTTagCompound nbt = new NBTTagCompound();
        if (requestedStack != null) {
            NBTTagCompound stackTag = new NBTTagCompound();
            requestedStack.writeToNBT(stackTag);
            nbt.setTag("stack", stackTag);
        }
        if (this.index > -1) {
            nbt.setInteger("index", index);
        }
        nbt.setInteger("action", action.ordinal());

        packetBuffer.writeCompoundTag(nbt);
    }

    public static class Handler implements IMessageHandler<PacketUIAction, IMessage> {

        @Override
        public IMessage onMessage(PacketUIAction message, MessageContext ctx) {
            NetHandlerPlayServer handler = ctx.getServerHandler();
            EntityPlayerMP player = handler.player;
            IThreadListener thread = (IThreadListener) player.world;
            thread.addScheduledTask(
                    () -> {
                        if (player.openContainer instanceof ContainerBase) {
                            ((ContainerBase) player.openContainer).onAction(player, message);
                        }
                    });
            return null;
        }
    }
}
