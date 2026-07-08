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

import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.container.ActionType;
import thaumicenergistics.container.ContainerBase;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.ThELog;

import java.io.IOException;

/**
 * @author BrockWS
 */
public class PacketUIAction implements IMessage {

    // Marks which storage channel requestedStack's NBT belongs to. Essentia and item stacks
    // don't reliably use distinct NBT keys (e.g. both can end up with a "Cnt"/"Count" tag
    // depending on the AE2 build), so guessing the channel from the NBT shape is unreliable -
    // instead the sender, which always knows the real type, tags it explicitly.
    private static final String STACK_CHANNEL_KEY = "stackChannel";

    public ActionType action;
    public IAEStack requestedStack;
    public int index = -1;

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
        if (nbt.hasKey(STACK_CHANNEL_KEY)) {
            IStorageChannel<?> channel =
                    nbt.getBoolean(STACK_CHANNEL_KEY)
                            ? AEUtil.getStorageChannel(IEssentiaStorageChannel.class)
                            : AEUtil.getStorageChannel(IItemStorageChannel.class);
            try {
                requestedStack = channel.createFromNBT(nbt);
            } catch (Throwable e) {
                ThELog.error(
                        "Failed to read stack from packet, {}", channel.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf outline) {

        PacketBuffer packetBuffer = new PacketBuffer(outline);

        NBTTagCompound nbt = new NBTTagCompound();
        if (requestedStack != null) {
            requestedStack.writeToNBT(nbt);
            nbt.setBoolean(STACK_CHANNEL_KEY, requestedStack instanceof IAEEssentiaStack);
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
