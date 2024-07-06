package thaumicenergistics.network.packets;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
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
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.container.ActionType;
import thaumicenergistics.container.ContainerBase;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.ThELog;

import java.io.IOException;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @author BrockWS
 */
public class PacketUIAction implements IMessage {

    public ActionType action;
    public IAEStack requestedStack;
    public int index = -1;
    private static final List<IStorageChannel<? extends IAEStack<? extends IAEStack<?>>>> validChannels = newArrayList(AEUtil.getStorageChannel(IItemStorageChannel.class),
            AEUtil.getStorageChannel(IEssentiaStorageChannel.class));

    public PacketUIAction() {
    }

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
        // AE2 items etc use Cnt, Essentia uses Count
        if (nbt.hasKey("Cnt") || nbt.hasKey("Count")) {

            validChannels.forEach(channel -> {
                if (requestedStack == null) {
                    try {
                        requestedStack = channel.createFromNBT(nbt);
                    } catch (Throwable ignored) {
                        ThELog.error("Failed to read stack from packet, {}", channel.getClass().getSimpleName());
                    }
                }
            });
        }
    }

    @Override
    public void toBytes(ByteBuf outline) {

        PacketBuffer packetBuffer = new PacketBuffer(outline);

        NBTTagCompound nbt = new NBTTagCompound();
        if (requestedStack != null) {
            requestedStack.writeToNBT(nbt);
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
            thread.addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerBase) {
                    ((ContainerBase) player.openContainer).onAction(player, message);
                }
            });
            return null;
        }
    }
}
