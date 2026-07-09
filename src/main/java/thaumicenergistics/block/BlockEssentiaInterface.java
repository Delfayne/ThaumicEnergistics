package thaumicenergistics.block;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;

import thaumicenergistics.client.render.IThEModel;
import thaumicenergistics.tile.TileEssentiaInterface;
import thaumicenergistics.util.AEUtil;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

public class BlockEssentiaInterface extends BlockNetwork implements IThEModel {

    public BlockEssentiaInterface(String id) {
        super(id);
        // AE2's own AEBaseBlock sets SoundType.METAL for its (default, non-glass/rock/wood)
        // machine blocks; BlockBase/BlockNetwork don't set one, leaving vanilla's STONE default.
        this.setSoundType(SoundType.METAL);
    }

    @Override
    public void registerTileEntity() {
        super.registerTileEntity();
        GameRegistry.registerTileEntity(
                TileEssentiaInterface.class, Objects.requireNonNull(this.getRegistryName()));
    }

    @Override
    public boolean onBlockActivated(
            World world,
            BlockPos pos,
            IBlockState state,
            EntityPlayer player,
            EnumHand hand,
            EnumFacing facing,
            float hitX,
            float hitY,
            float hitZ) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote && player.isSneaking() && AEUtil.isWrench(heldItem, player, pos)) {
            // Sneak + wrench dismantles the block cleanly instead of requiring a normal mine,
            // mirroring AE2's own AEBaseTileBlock#onBlockActivated wrench branch.
            List<ItemStack> drops = this.getDrops(world, pos, state, 0);
            if (this.removedByPlayer(state, world, pos, player, false)) {
                BlockBase.spawnDrops(world, pos, drops);
                world.setBlockToAir(pos);
            }
            return true;
        }

        return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
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
