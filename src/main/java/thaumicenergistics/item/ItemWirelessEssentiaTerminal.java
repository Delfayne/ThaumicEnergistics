package thaumicenergistics.item;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.items.IAEItemPowerStorage;

import baubles.api.BaubleType;
import baubles.api.IBauble;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.dv.minecraft.thaumicenergistics.Reference;

import thaumicenergistics.api.ThEApi;
import thaumicenergistics.client.gui.GuiHandler;
import thaumicenergistics.client.render.IThEModel;
import thaumicenergistics.config.AESettings;
import thaumicenergistics.init.ModGUIs;
import thaumicenergistics.integration.appeng.util.ThEConfigManager;

import java.text.MessageFormat;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Alex811
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class ItemWirelessEssentiaTerminal extends ItemBase
        implements IAEItemPowerStorage, IWirelessTermHandler, IThEModel, IBauble {

    private static final String TAG_POWER = "internalCurrentPower";
    private static final String TAG_ENCRYPTION_KEY = "encryptionKey";

    public ItemWirelessEssentiaTerminal(String id) {
        super(id);
        this.setMaxStackSize(1);
    }

    @Override
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(
                this,
                0,
                new ModelResourceLocation(
                        Reference.MOD_ID + ":" + this.getRegistryName().getPath(), "inventory"));
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(
            World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        String encKey = this.getEncryptionKey(stack);
        if (encKey.isEmpty()) {
            player.sendMessage(
                    new TextComponentTranslation(
                            ThEApi.instance().lang().deviceNotLinked().getUnlocalizedKey()));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        if (this.getLinkedStation(stack) == null) {
            player.sendMessage(
                    new TextComponentTranslation(
                            ThEApi.instance().lang().deviceStationNotFound().getUnlocalizedKey()));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        if (!this.hasPower(player, 0.5, stack)) {
            player.sendMessage(
                    new TextComponentTranslation(
                            ThEApi.instance().lang().deviceNotPowered().getUnlocalizedKey()));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        GuiHandler.openGUI(ModGUIs.WIRELESS_ESSENTIA_TERMINAL, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Nullable
    private ILocatable getLinkedStation(ItemStack stack) {
        String encKey = this.getEncryptionKey(stack);
        if (encKey.isEmpty()) return null;
        try {
            return AEApi.instance().registries().locatable().getLocatableBy(Long.parseLong(encKey));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(
            ItemStack stack, @Nullable World world, List<String> lines, ITooltipFlag flag) {
        super.addInformation(stack, world, lines, flag);

        double current = this.getAECurrentPower(stack);
        double max = this.getAEMaxPower(stack);
        double percent = max > 0 ? current / max : 0;
        lines.add(
                ThEApi.instance().lang().storedEnergy().getLocalizedKey()
                        + ':'
                        + MessageFormat.format(" {0,number,#} ", current)
                        + I18n.format(PowerUnits.AE.unlocalizedName)
                        + " - "
                        + MessageFormat.format(" {0,number,#.##%} ", percent));

        boolean linked = !this.getEncryptionKey(stack).isEmpty();
        lines.add(
                linked
                        ? ThEApi.instance().lang().deviceLinked().getLocalizedKey()
                        : ThEApi.instance().lang().deviceUnlinked().getLocalizedKey());
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(ItemStack itemStack) {
        return BaubleType.TRINKET;
    }

    @Override
    public boolean canHandle(ItemStack is) {
        return is.getItem() == this;
    }

    @Override
    public boolean usePower(EntityPlayer player, double amount, ItemStack is) {
        return this.extractAEPower(is, amount, Actionable.MODULATE) >= amount - 0.5;
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amount, ItemStack is) {
        return this.getAECurrentPower(is) >= amount;
    }

    @Override
    public ThEConfigManager getConfigManager(ItemStack is) {
        ThEConfigManager cm = new ThEConfigManager();
        cm.registerSettings(AESettings.SUBJECT.ESSENTIA_TERMINAL);
        if (is.hasTagCompound()) cm.readFromNBT(is.getTagCompound());
        return cm;
    }

    @Nullable
    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        return null;
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        if (!item.hasTagCompound()) return "";
        return item.getTagCompound().getString(TAG_ENCRYPTION_KEY);
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        NBTTagCompound tag = item.hasTagCompound() ? item.getTagCompound() : new NBTTagCompound();
        tag.setString(TAG_ENCRYPTION_KEY, encKey);
        item.setTagCompound(tag);
    }

    @Override
    public double injectAEPower(ItemStack stack, double amount, Actionable mode) {
        double current = this.getAECurrentPower(stack);
        double newTotal = Math.min(this.getAEMaxPower(stack), current + amount);
        double accepted = newTotal - current;
        if (mode == Actionable.MODULATE) this.setCurrentPower(stack, newTotal);
        return amount - accepted;
    }

    @Override
    public double extractAEPower(ItemStack stack, double amount, Actionable mode) {
        double current = this.getAECurrentPower(stack);
        double extracted = Math.min(current, amount);
        if (mode == Actionable.MODULATE) this.setCurrentPower(stack, current - extracted);
        return extracted;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return ThEApi.instance().config().wirelessEssentiaTerminalMaxPower();
    }

    @Override
    public double getAECurrentPower(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getDouble(TAG_POWER);
    }

    @Override
    public AccessRestriction getPowerFlow(ItemStack stack) {
        return AccessRestriction.READ_WRITE;
    }

    private void setCurrentPower(ItemStack stack, double power) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setDouble(TAG_POWER, power);
        stack.setTagCompound(tag);
    }
}
