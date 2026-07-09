package thaumicenergistics.block;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;

import thaumicenergistics.client.render.IThEModel;
import thaumicenergistics.tile.TileEssentiaInterface;

import java.util.Objects;

import javax.annotation.Nullable;

public class BlockEssentiaInterface extends BlockNetwork implements IThEModel {

    public BlockEssentiaInterface(String id) {
        super(id);
    }

    @Override
    public void registerTileEntity() {
        super.registerTileEntity();
        GameRegistry.registerTileEntity(
                TileEssentiaInterface.class, Objects.requireNonNull(this.getRegistryName()));
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEssentiaInterface();
    }

    @Override
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(this),
                0,
                new ModelResourceLocation(
                        Objects.requireNonNull(this.getRegistryName()), "inventory"));
    }
}
