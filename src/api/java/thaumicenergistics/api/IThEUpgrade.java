package thaumicenergistics.api;

import appeng.api.definitions.IItemDefinition;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * @author BrockWS
 */
public interface IThEUpgrade {

    void registerItem(IItemDefinition item, int max);

    void registerItem(ItemStack item, int max);

    IItemDefinition getDefinition();

    Map<ItemStack, Integer> getSupported();

    int getSupported(ItemStack stack);

    boolean isSupported(ItemStack stack);
}
