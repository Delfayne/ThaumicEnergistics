package thaumicenergistics.item;

import net.minecraft.item.Item;
import org.dv.minecraft.thaumicenergistics.Reference;
import thaumicenergistics.init.ModGlobals;

/**
 * @author BrockWS
 */
public abstract class ItemBase extends Item {

    public ItemBase(String id) {
        this(id, true);
    }

    public ItemBase(String id, boolean setCreativeTab) {
        this.setRegistryName(id);
        this.setTranslationKey(Reference.MOD_ID + "." + id);
        if (setCreativeTab)
            this.setCreativeTab(ModGlobals.CREATIVE_TAB);
    }
}
