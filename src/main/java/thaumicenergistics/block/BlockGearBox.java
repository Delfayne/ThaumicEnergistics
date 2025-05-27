package thaumicenergistics.block;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import thaumicenergistics.client.render.IThEModel;
import thaumicenergistics.tile.TileGearBox;

import java.util.Objects;

public class BlockGearBox
	extends AbstractBlockGearBoxBase implements IThEModel
{

	public BlockGearBox(String name)
	{
		super(name);
	}

	@Override
	public void registerTileEntity() {
		super.registerTileEntity();
		GameRegistry.registerTileEntity(TileGearBox.class, Objects.requireNonNull(this.getRegistryName()));
	}

	@Override
	public void initModel() {
		ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), 0, new ModelResourceLocation(Objects.requireNonNull(getRegistryName()), "inventory"));
	}
}