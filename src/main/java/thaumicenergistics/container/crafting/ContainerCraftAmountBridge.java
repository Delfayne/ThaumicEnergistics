package thaumicenergistics.container.crafting;

import appeng.container.implementations.ContainerCraftAmount;
import net.minecraft.entity.player.InventoryPlayer;
import thaumicenergistics.part.PartSharedTerminal;

/**
 * @author BrockWS
 */
public class ContainerCraftAmountBridge extends ContainerCraftAmount {

    public ContainerCraftAmountBridge(InventoryPlayer ip, PartSharedTerminal te) {
        super(ip, te);
    }
}
