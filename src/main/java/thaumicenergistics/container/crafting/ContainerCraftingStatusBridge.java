package thaumicenergistics.container.crafting;

import appeng.container.implementations.ContainerCraftingStatus;
import net.minecraft.entity.player.InventoryPlayer;
import thaumicenergistics.part.PartSharedTerminal;

/**
 * @author BrockWS
 */
public class ContainerCraftingStatusBridge extends ContainerCraftingStatus {
    public ContainerCraftingStatusBridge(InventoryPlayer ip, PartSharedTerminal te) {
        super(ip, te);
    }
}
