package thaumicenergistics.item;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.ICellInventoryHandler;
import appeng.items.contents.CellUpgrades;
import appeng.util.InventoryAdaptor;
import com.google.common.base.Preconditions;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.items.IItemHandler;
import org.dv.minecraft.thaumicenergistics.thaumicenergistics.Reference;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.client.render.IThEModel;
import thaumicenergistics.init.ModGlobals;
import thaumicenergistics.util.inventory.EssentiaCellConfig;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * @author BrockWS
 */
public class ItemEssentiaCell extends ItemBase implements IStorageCell<IAEEssentiaStack>, IThEModel {

    private final String size;
    private final int bytes;
    private final int types;

    public ItemEssentiaCell(String size, int bytes, int types) {
        super("essentia_cell_" + size);

        this.size = size;
        this.bytes = bytes;
        this.types = types;

        this.setMaxStackSize(1);
        this.setMaxDamage(0);
        this.setHasSubtypes(false);
        this.setCreativeTab(ModGlobals.CREATIVE_TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!player.isSneaking())
            return super.onItemRightClick(world, player, hand);
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty())
            return super.onItemRightClick(world, player, hand);
        ICellInventoryHandler<IAEEssentiaStack> handler = AEApi.instance().registries().cell().getCellInventory(held, null, this.getChannel());
        if (handler == null)
            throw new NullPointerException("Couldn't get ICellInventoryHandler for Essentia Cell");
        if (!handler.getAvailableItems(this.getChannel().createList()).isEmpty()) // Only try to separate cell if empty
            return super.onItemRightClick(world, player, hand);

        Optional<ItemStack> cellComponentOptional = this.getComponentOfCell(held);
        Optional<ItemStack> emptyCasingOptional = AEApi.instance().definitions().materials().emptyStorageCell().maybeStack(1);
        if (!cellComponentOptional.isPresent() || !emptyCasingOptional.isPresent())
            return super.onItemRightClick(world, player, hand);

        ItemStack emptyCasing = emptyCasingOptional.get();
        ItemStack cellComponent = cellComponentOptional.get();
        InventoryPlayer inv = player.inventory;
        InventoryAdaptor invAdaptor = InventoryAdaptor.getAdaptor(player);

        if (hand == EnumHand.MAIN_HAND) // Prevent accidental deletion when in off hand
            inv.setInventorySlotContents(inv.currentItem, ItemStack.EMPTY);

        ItemStack cellRemainder = invAdaptor.addItems(cellComponent);
        if (!cellRemainder.isEmpty())
            player.dropItem(cellRemainder, false);

        ItemStack casingRemainder = invAdaptor.addItems(emptyCasing);
        if (!casingRemainder.isEmpty())
            player.dropItem(emptyCasing, false);

        if (player.inventoryContainer != null)
            player.inventoryContainer.detectAndSendChanges();

        return ActionResult.newResult(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private Optional<ItemStack> getComponentOfCell(ItemStack stack) {
        Preconditions.checkNotNull(stack);
        Preconditions.checkNotNull(stack.getItem());
        Preconditions.checkNotNull(stack.getItem().getRegistryName());
        Preconditions.checkNotNull(stack.getItem().getRegistryName().getPath());
        switch (stack.getItem().getRegistryName().getPath().split("_")[2]) {
            case "1k":
                return ThEApi.instance().items().essentiaComponent1k().maybeStack(1);
            case "4k":
                return ThEApi.instance().items().essentiaComponent4k().maybeStack(1);
            case "16k":
                return ThEApi.instance().items().essentiaComponent16k().maybeStack(1);
            case "64k":
                return ThEApi.instance().items().essentiaComponent64k().maybeStack(1);
            default:
                return Optional.empty();
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        ICellInventoryHandler<IAEEssentiaStack> cellInventory = AEApi.instance().registries().cell().getCellInventory(stack, null, this.getChannel());
        AEApi.instance().client().addCellInformation(cellInventory, tooltip);
    }

    @Override
    public int getBytes(ItemStack itemStack) {
        return this.bytes;
    }

    @Override
    public int getBytesPerType(ItemStack itemStack) {
        return 8;
    }

    @Override
    public int getTotalTypes(ItemStack itemStack) {
        return this.types;
    }

    @Override
    public boolean isBlackListed(ItemStack itemStack, IAEEssentiaStack iaeEssentiaStack) {
        return false;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(ItemStack itemStack) {
        return true;
    }

    @Override
    public double getIdleDrain() {
        return 1;
    }

    @Override
    public IEssentiaStorageChannel getChannel() {
        return AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);
    }

    @Override
    public boolean isEditable(ItemStack itemStack) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(ItemStack itemStack) {
        return new CellUpgrades(itemStack, 0);
    }

    @Override
    public IItemHandler getConfigInventory(ItemStack itemStack) {
        return new EssentiaCellConfig(itemStack);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) {

    }

    @Override
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(Reference.MOD_ID + ":cell/essentia_cell_" + this.size, "inventory"));
    }
}
