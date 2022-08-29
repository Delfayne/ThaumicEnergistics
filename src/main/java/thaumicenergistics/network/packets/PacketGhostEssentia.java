package thaumicenergistics.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.container.part.ContainerSharedEssentiaBus;
import thaumicenergistics.container.slot.SlotGhostEssentia;
import thaumicenergistics.util.ThELog;

public class PacketGhostEssentia implements IMessage {

    private Aspect aspect;
    private int slot;

    @SuppressWarnings("unused")
    public PacketGhostEssentia() {
    }

    public PacketGhostEssentia(Aspect aspect, int slot) {
        this.aspect = aspect;
        this.slot = slot;
    }


    @Override
    public void fromBytes(ByteBuf buf) {
        String tag = ByteBufUtils.readUTF8String(buf);
        ThELog.debug("Read aspect '{}' from bytes", tag);
        aspect = Aspect.getAspect(tag);
        slot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, aspect.getTag());
        buf.writeInt(slot);
    }

    public static class Handler implements IMessageHandler<PacketGhostEssentia, IMessage> {

        @Override
        public IMessage onMessage(PacketGhostEssentia message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;


            IThreadListener threadListener = (IThreadListener) player.world;
            threadListener.addScheduledTask(() -> {
                Container openContainer = player.openContainer;
                ThELog.debug("Server received aspect '{}' using slot '{}'", message.aspect.getName(), openContainer);
                if ((!(openContainer instanceof ContainerSharedEssentiaBus))) {
                    return;
                }

                if (message.slot >= 0 && message.slot < openContainer.inventorySlots.size()) {
                    Slot invSlot = openContainer.getSlot(message.slot);
                    if (invSlot instanceof SlotGhostEssentia) {
                        SlotGhostEssentia ghostEssentiaSlot = (SlotGhostEssentia) invSlot;
                        ghostEssentiaSlot.setAspect(message.aspect);
                    }
                }
            });

            return null;
        }
    }
}
