package thaumicenergistics.part;

import appeng.api.parts.IPartModel;
import appeng.api.parts.PartItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.dv.minecraft.thaumicenergistics.Reference;
import thaumicenergistics.init.ModGUIs;
import thaumicenergistics.init.ModGlobals;
import thaumicenergistics.integration.appeng.ThEPartModel;
import thaumicenergistics.item.part.ItemArcaneInscriber;
import thaumicenergistics.util.ItemHandlerUtil;
import thaumicenergistics.util.inventory.ThEKnowledgeCoreInventory;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Alex811
 */
public class PartArcaneInscriber extends PartArcaneTerminal {

    public static ResourceLocation[] MODELS = new ResourceLocation[]{
            new ResourceLocation(Reference.MOD_ID, "part/arcane_inscriber/base"), // 0
            new ResourceLocation(Reference.MOD_ID, "part/arcane_inscriber/on"), // 1
            new ResourceLocation(Reference.MOD_ID, "part/arcane_inscriber/off"), // 2
            new ResourceLocation(ModGlobals.MOD_ID_AE2, "part/display_status_has_channel"), // 3
            new ResourceLocation(ModGlobals.MOD_ID_AE2, "part/display_status_on"), // 4
            new ResourceLocation(ModGlobals.MOD_ID_AE2, "part/display_status_off") // 5
    };

    private static final IPartModel MODEL_ON = new ThEPartModel(MODELS[0], MODELS[1], MODELS[4]);
    private static final IPartModel MODEL_OFF = new ThEPartModel(MODELS[0], MODELS[2], MODELS[5]);
    private static final IPartModel MODEL_HAS_CHANNEL = new ThEPartModel(MODELS[0], MODELS[1], MODELS[3]);

    public PartArcaneInscriber(ItemArcaneInscriber item) {
        super(item, ModGUIs.ARCANE_INSCRIBER);
        this.upgradeInventory = new ThEKnowledgeCoreInventory("upgrades", 1, 1, this.getItemStack(PartItemStack.NETWORK));
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isPowered())
            if (this.isActive())
                return MODEL_HAS_CHANNEL;
            else
                return MODEL_ON;
        return MODEL_OFF;
    }

    @Override
    public void getDrops(List<ItemStack> list, boolean b) {
        list.addAll(ItemHandlerUtil.getInventoryAsList(this.getInventoryByName("upgrades")));
    }
}
