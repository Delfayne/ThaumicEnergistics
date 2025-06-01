package thaumicenergistics.item;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.items.contents.CellUpgrades;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
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
import org.dv.minecraft.thaumicenergistics.Reference;
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
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player, final EnumHand hand) {
        this.disassembleDrive(player.getHeldItem(hand), world, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private boolean disassembleDrive(final ItemStack stack, final World world, final EntityPlayer player) {
        if (player.isSneaking()) {
            if (Platform.isClient()) {
                return false;
            }

            Optional<ItemStack> cellComponentOptional = this.getComponentOfCell(stack);
            Optional<ItemStack> emptyCasingOptional = this.getComponentOfCasing(stack);
            if (cellComponentOptional.isPresent() && emptyCasingOptional.isPresent()) {
                final InventoryPlayer playerInventory = player.inventory;
                final IMEInventoryHandler inv = AEApi.instance().registries().cell().getCellInventory(stack, null, this.getChannel());
                if (inv != null && playerInventory.getCurrentItem() == stack) {
                    final InventoryAdaptor ia = InventoryAdaptor.getAdaptor(player);
                    final IItemList<IAEItemStack> list = inv.getAvailableItems(this.getChannel().createList());
                    if (list.isEmpty() && ia != null) {
                        playerInventory.setInventorySlotContents(playerInventory.currentItem, ItemStack.EMPTY);

                        // drop core
                        final ItemStack extraB = ia.addItems(cellComponentOptional.get());
                        if (!extraB.isEmpty()) {
                            player.dropItem(extraB, false);
                        }

                        // drop upgrades
                        final IItemHandler upgradesInventory = this.getUpgradesInventory(stack);
                        for (int upgradeIndex = 0; upgradeIndex < upgradesInventory.getSlots(); upgradeIndex++) {
                            final ItemStack upgradeStack = upgradesInventory.getStackInSlot(upgradeIndex);
                            final ItemStack leftStack = ia.addItems(upgradeStack);
                            if (!leftStack.isEmpty() && upgradeStack.getItem() instanceof IUpgradeModule) {
                                player.dropItem(upgradeStack, false);
                            }
                        }

                        // drop empty storage cell case
                        final ItemStack extraA = ia.addItems(emptyCasingOptional.get());
                        if (!extraA.isEmpty()) {
                            player.dropItem(extraA, false);
                        }

                        if (player.inventoryContainer != null) {
                            player.inventoryContainer.detectAndSendChanges();
                        }

                        return true;
                    }
                }
            }
        }
        return false;
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
            case "256k":
                return ThEApi.instance().items().essentiaComponent256k().maybeStack(1);
            case "1024k":
                return ThEApi.instance().items().essentiaComponent1024k().maybeStack(1);
            case "4096k":
                return ThEApi.instance().items().essentiaComponent4096k().maybeStack(1);
            case "16384k":
                return ThEApi.instance().items().essentiaComponent16384k().maybeStack(1);
            default:
                return Optional.empty();
        }
    }

    private Optional<ItemStack> getComponentOfCasing(ItemStack stack) {
        Preconditions.checkNotNull(stack);
        Preconditions.checkNotNull(stack.getItem());
        Preconditions.checkNotNull(stack.getItem().getRegistryName());
        Preconditions.checkNotNull(stack.getItem().getRegistryName().getPath());
        switch (stack.getItem().getRegistryName().getPath().split("_")[2]) {
            case "1k":
            case "4k":
            case "16k":
            case "64k":
                return AEApi.instance().definitions().materials().emptyStorageCell().maybeStack(1);
            case "256k":
            case "1024k":
            case "4096k":
            case "16384k":
                return ThEApi.instance().items().advancedEssentiaHousing().maybeStack(1);
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
