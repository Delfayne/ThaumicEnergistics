package thaumicenergistics.container;

import net.minecraft.entity.player.EntityPlayer;

public abstract class ContainerBaseTerminal extends ContainerBaseConfigurable {

    protected final IThETerminalHost host;

    public ContainerBaseTerminal(EntityPlayer player, IThETerminalHost host) {
        super(player, host.getConfigManager());
        this.host = host;
    }

    public boolean isPowered() {
        return this.host.isPowered();
    }

    public boolean isActive() {
        return this.host.isActive();
    }
}
