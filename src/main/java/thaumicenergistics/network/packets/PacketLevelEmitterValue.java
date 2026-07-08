package thaumicenergistics.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import thaumicenergistics.client.gui.part.GuiEssentiaLevelEmitter;
import thaumicenergistics.container.part.ContainerEssentiaLevelEmitter;

/**
 * Client -> Server: sets the level emitter's configured threshold. Server -> Client: informs the
 * open GUI of the emitter's current threshold.
 */
public class PacketLevelEmitterValue implements IMessage {

    private long value;

    public PacketLevelEmitterValue() {}

    public PacketLevelEmitterValue(long value) {
        this.value = value;
    }

    public long getValue() {
        return this.value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.value = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.value);
    }

    public static class HandlerServer
            implements IMessageHandler<PacketLevelEmitterValue, IMessage> {

        @Override
        public IMessage onMessage(PacketLevelEmitterValue message, MessageContext ctx) {
            NetHandlerPlayServer handler = ctx.getServerHandler();
            EntityPlayerMP player = handler.player;
            IThreadListener thread = (IThreadListener) player.world;
            thread.addScheduledTask(
                    () -> {
                        if (player.openContainer instanceof ContainerEssentiaLevelEmitter)
                            ((ContainerEssentiaLevelEmitter) player.openContainer)
                                    .setLevel(message.getValue());
                    });
            return null;
        }
    }

    public static class HandlerClient
            implements IMessageHandler<PacketLevelEmitterValue, IMessage> {

        @Override
        public IMessage onMessage(PacketLevelEmitterValue message, MessageContext ctx) {
            Minecraft.getMinecraft()
                    .addScheduledTask(
                            () -> {
                                GuiScreen gui = Minecraft.getMinecraft().currentScreen;
                                if (gui instanceof GuiEssentiaLevelEmitter)
                                    ((GuiEssentiaLevelEmitter) gui).setLevel(message.getValue());
                            });
            return null;
        }
    }
}
