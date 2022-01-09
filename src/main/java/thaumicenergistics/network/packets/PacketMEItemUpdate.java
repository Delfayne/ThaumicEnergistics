package thaumicenergistics.network.packets;

import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import thaumicenergistics.client.gui.part.GuiArcaneInscriber;
import thaumicenergistics.client.gui.part.GuiArcaneTerminal;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.ThELog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author BrockWS
 */
public class PacketMEItemUpdate implements IMessage {

    private static final int UNCOMPRESSED_PACKET_BYTE_LIMIT = 16 * 1024 * 1024;
    private static final int OPERATION_BYTE_LIMIT = 2 * 1024;
    private static final int TEMP_BUFFER_SIZE = 1024;

    private IItemList<IAEItemStack> list;

    private final ByteBuf data;

    private final GZIPOutputStream compressFrame;

    private int writtenBytes = 0;
    private boolean empty = true;

    public PacketMEItemUpdate() throws IOException {
        this.list = AEUtil.getStorageChannel(IItemStorageChannel.class).createList();

        this.data = Unpooled.buffer(OPERATION_BYTE_LIMIT);
        compressFrame = new GZIPOutputStream(new OutputStream() {
            @Override
            public void write(int value) {
                data.writeByte(value);
            }
        });
    }

    @Override
    public void fromBytes(ByteBuf buf) {
//        ThELog.info("fromBytes Readable Bytes : " + buf.readableBytes());
//        ThELog.info("fromBytes isReadable : " + buf.isReadable());
        if (!buf.isReadable()) {
            return;
        }
        try (GZIPInputStream gzReader = new GZIPInputStream(new InputStream() {
            @Override
            public int read() throws IOException {
                if (buf.readableBytes() <= 0) {
                    return -1;
                }

                return buf.readByte() & 0xff;
            }
        })) {
            ByteBuf uncompressed = Unpooled.buffer(buf.readableBytes());
            byte[] tmp = new byte[TEMP_BUFFER_SIZE];
            while (gzReader.available() != 0) {
                int bytes = gzReader.read(tmp);
                if (bytes > 0) {
                    uncompressed.writeBytes(tmp, 0, bytes);
                }
            }
            while (uncompressed.readableBytes() > 0) {
                this.list.add(AEItemStack.fromPacket(uncompressed));
            }
        } catch (IOException e) {
            ThELog.error("fromBytes IOException", e);
        }
        this.empty = this.list.isEmpty();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!data.isReadable()) {
            ThELog.info("No readable bytes, skipping");
            return;
        }
        try {
            compressFrame.close();
            data.capacity(data.readableBytes());
            if (data.array().length > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("Sorry, ThE made a " + data.array().length + " byte packet by accident!");
            }
//            ThELog.info("toBytes Readable Bytes : " + data.readableBytes());
            buf.writeBytes(data);
        } catch (IOException e) {
            ThELog.error("toBytes IOException", e);
        }
    }

    public void appendStack(IAEItemStack stack) throws IOException, BufferOverflowException {

        ByteBuf tmp = Unpooled.buffer(OPERATION_BYTE_LIMIT);
        stack.writeToPacket(tmp);

        compressFrame.flush();
        if (writtenBytes + tmp.readableBytes() > UNCOMPRESSED_PACKET_BYTE_LIMIT) {
            throw new BufferOverflowException();
        } else {
            writtenBytes += tmp.readableBytes();
            compressFrame.write(tmp.array(), 0, tmp.readableBytes());
            this.empty = false;
        }
    }

    public static class Handler implements IMessageHandler<PacketMEItemUpdate, IMessage> {

        @Override
        public IMessage onMessage(PacketMEItemUpdate message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof GuiArcaneTerminal) {
                    GuiArcaneTerminal gui = (GuiArcaneTerminal) Minecraft.getMinecraft().currentScreen;
                    gui.onMEStorageUpdate(message.list);
                }
                if (Minecraft.getMinecraft().currentScreen instanceof GuiArcaneInscriber) {
                    GuiArcaneInscriber gui = (GuiArcaneInscriber) Minecraft.getMinecraft().currentScreen;
                    gui.onMEStorageUpdate(message.list);
                }
            });
            return null;
        }
    }

    public boolean isEmpty() {
        return this.empty;
    }
}
