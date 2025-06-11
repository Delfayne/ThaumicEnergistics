package thaumicenergistics.block;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import thaumicenergistics.client.render.IThEModel;

import java.util.Objects;

public class BlockGolemGearBox
	extends AbstractBlockGearBoxBase implements IThEModel
{

	public BlockGolemGearBox(String name)
	{
		super(name);

		// Set that golems are allowed to interact with the gearbox.
		this.allowGolemInteraction = true;
	}



	@Override
	public void initModel() {
		ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), 0, new ModelResourceLocation(Objects.requireNonNull(this.getRegistryName()), "inventory"));
	}
}