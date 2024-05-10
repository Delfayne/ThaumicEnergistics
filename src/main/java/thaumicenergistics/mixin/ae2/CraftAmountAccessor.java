package thaumicenergistics.mixin.ae2;

import appeng.client.gui.implementations.GuiCraftAmount;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import thaumicenergistics.annotation.ClientOnlyMixin;
import thaumicenergistics.annotation.LateMixin;

@Mixin(GuiCraftAmount.class)
@ClientOnlyMixin
@LateMixin
public interface CraftAmountAccessor {
    @Accessor(remap = false)
    GuiTextField getAmountToCraft();
}
