package thaumicenergistics.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import org.dv.minecraft.thaumicenergistics.Reference;
import thaumicenergistics.api.ThEApi;

/**
 * Contains useful constant values
 *
 * @author BrockWS
 */
public class ModGlobals {

    public static final String MOD_DEPENDENCIES =
            "required-after:appliedenergistics2@[v0.56.5,);" +
            "required-after:thaumcraft@[6.1.BETA26,);" +
            "after:thaumicjei;" +
            "after:inventorytweaks;" +
            "after:waila;" +
            "after:theoneprobe";

    /**
     * Creative tab.
     */
    public static CreativeTabs CREATIVE_TAB = new CreativeTabs("ThaumicEnergistics") {

        @Override
        public ItemStack createIcon() {
            ItemStack icon = ThEApi.instance().items().essentiaCell1k().maybeStack(1).orElse(ItemStack.EMPTY);
            if (icon.isEmpty())
                throw new NullPointerException("Unable to use essentiaCell1k for creative tab!");
            return icon;
        }
    };

    public static final String RESEARCH_CATEGORY = Reference.MOD_ID.toUpperCase();

    public static final String MOD_ID_AE2 = "appliedenergistics2";

    public static final boolean DEBUG_MODE = System.getProperties().containsKey("thaumicenergisticsdebug");
}
