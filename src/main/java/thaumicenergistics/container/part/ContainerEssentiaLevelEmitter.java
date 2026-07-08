package thaumicenergistics.container.part;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import thaumicenergistics.config.AESettings;
import thaumicenergistics.container.ContainerBaseConfigurable;
import thaumicenergistics.container.IPartContainer;
import thaumicenergistics.container.slot.SlotGhostEssentia;
import thaumicenergistics.network.PacketHandler;
import thaumicenergistics.network.packets.PacketEssentiaFilter;
import thaumicenergistics.network.packets.PacketLevelEmitterValue;
import thaumicenergistics.part.PartBase;
import thaumicenergistics.part.PartEssentiaLevelEmitter;
import thaumicenergistics.util.EssentiaFilter;
import thaumicenergistics.util.ForgeUtil;

public class ContainerEssentiaLevelEmitter extends ContainerBaseConfigurable
        implements IPartContainer {

    private final PartEssentiaLevelEmitter part;
    private long lastSentValue = Long.MIN_VALUE;

    public ContainerEssentiaLevelEmitter(EntityPlayer player, PartEssentiaLevelEmitter part) {
        super(player, part.getConfigManager());
        this.part = part;

        this.addSlotToContainer(
                new SlotGhostEssentia(
                        this.part.getConfig(),
                        new InventoryBasic("null", false, 1),
                        0,
                        124,
                        40,
                        0));
        this.bindPlayerInventory(new PlayerMainInvWrapper(player.inventory), 0, 100);

        this.sendFilter();
    }

    @Override
    protected AESettings.SUBJECT getAESettingSubject() {
        return AESettings.SUBJECT.ESSENTIA_LEVEL_EMITTER;
    }

    @Override
    public PartBase getPart() {
        return this.part;
    }

    @Override
    public EssentiaFilter getEssentiaFilter() {
        return this.part.getConfig();
    }

    public void setLevel(long value) {
        this.part.setReportingValue(value);
    }

    private void sendFilter() {
        if (this.player instanceof EntityPlayerMP)
            PacketHandler.sendToPlayer(
                    (EntityPlayerMP) this.player,
                    new PacketEssentiaFilter(this.getEssentiaFilter()));
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (ForgeUtil.isServer()) {
            long current = this.part.getReportingValue();
            if (current != this.lastSentValue) {
                this.lastSentValue = current;
                for (IContainerListener listener : this.listeners)
                    if (listener instanceof EntityPlayerMP)
                        PacketHandler.sendToPlayer(
                                (EntityPlayerMP) listener, new PacketLevelEmitterValue(current));
            }
        }
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        this.sendFilter();
        if (listener instanceof EntityPlayerMP) {
            this.lastSentValue = this.part.getReportingValue();
            PacketHandler.sendToPlayer(
                    (EntityPlayerMP) listener, new PacketLevelEmitterValue(this.lastSentValue));
        }
    }
}
