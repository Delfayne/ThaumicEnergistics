package thaumicenergistics.client.gui.crafting;

import appeng.client.gui.MathExpressionParser;
import appeng.client.gui.implementations.GuiCraftAmount;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiText;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import thaumicenergistics.mixin.ae2.CraftAmountAccessor;
import thaumicenergistics.network.PacketHandler;
import thaumicenergistics.network.packets.PacketCraftRequest;
import thaumicenergistics.network.packets.PacketOpenGUI;
import thaumicenergistics.part.PartSharedTerminal;

import java.io.IOException;

/**
 * @author BrockWS
 */
public class GuiCraftAmountBridge extends GuiCraftAmount {

    private final EntityPlayer player;
    private final PartSharedTerminal part;

    public GuiCraftAmountBridge(EntityPlayer player, PartSharedTerminal part) {
        super(player.inventory, part);
        this.player = player;
        this.part = part;
    }

    @Override
    public void initGui() {
        super.initGui();

        ItemStack icon = part.getRepr();
        if (!icon.isEmpty())
            this.buttonList.add(new GuiTabButton(this.guiLeft + 154, this.guiTop, icon, icon.getDisplayName(), this.itemRender));
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        if (btn.displayString.equals(GuiText.Next.getLocal()) || btn.displayString.equals(GuiText.Start.getLocal())) {
            PacketHandler.sendToServer(new PacketCraftRequest(getAmountToCraft(), isShiftKeyDown()));
            return;
        }

        String name = part.getRepr().getDisplayName();
        if (btn instanceof GuiTabButton && ((GuiTabButton) btn).getMessage().equals(name)) {
            PacketHandler.sendToServer(new PacketOpenGUI(this.part.getGui(), this.part.getLocation().getPos(), this.part.side));
            return;
        }

        super.actionPerformed(btn);
    }

    private int getAmountToCraft() {
        String craftQuantityExpression = ((CraftAmountAccessor) this).getAmountToCraft().getText();
        double resultD = MathExpressionParser.parse(craftQuantityExpression);
        int result;
        if (resultD <= 0 || Double.isNaN(resultD)) {
            result = 1;
        } else {
            result = (int) MathExpressionParser.round(resultD, 0);
        }
        return result;
    }
}
