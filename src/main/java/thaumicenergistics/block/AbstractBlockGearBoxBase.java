package thaumicenergistics.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import thaumicenergistics.tile.TileGearBox;

public abstract class AbstractBlockGearBoxBase
        extends AbstractBlockAEWrenchable {
    /**
     * Determines if thaumcraft golems are allowed to interact with the gearbox.
     */
    protected boolean allowGolemInteraction;

    /**
     * Creates the block.
     */
    public AbstractBlockGearBoxBase(String name) {
        // Set material type
        super(name);

        // Set hardness
        this.setHardness(0.6F);
    }

    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return new AxisAlignedBB(0.23F, 0.23F, 0.23F, 0.77F, 0.77F, 0.77F);
    }

    /**
     * Crank the gearbox.
     */
    @Override
    protected boolean onBlockActivated(final World world, final int x, final int y, final int z, final EntityPlayer player) {

        // Get the tile
        TileGearBox gearBox = (TileGearBox) world.getTileEntity(new BlockPos(x, y, z));

        // Crank it
        return gearBox.crankGearbox();
    }

    @Override
    public boolean canPlayerInteract(final EntityPlayer player) {
        // Fake player?
        if (player instanceof FakePlayer) {
            // Are golems allowed to interact?
            if (!this.allowGolemInteraction) {
                // Golem interaction not allowed
                return false;
            }

            // Is the fake player a golem?
            if (!player.getGameProfile().getName().equalsIgnoreCase("FakeThaumcraftGolem")) {
                // Not a golem
                return false;
            }
        }

        return true;
    }

    /**
     * Is not opaque.
     */
    @Override
    public final boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState p_isFullCube_1_) {
        return false;
    }

    /**
     * Dependent on config setting.
     */
    @Override
    public final boolean isSideSolid(IBlockState base_state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    /**
     * Creates the gear box tile.
     */
    @Override
    public TileEntity createNewTileEntity(final World w, final int meta) {
        return new TileGearBox();
    }

    /**
     * One of the adjacent blocks has changed.
     */
    @Override
    public void onNeighborChange(final IBlockAccess w, final BlockPos pos, final BlockPos neighbor) {
        // Get the tile
        TileEntity gearBox = w.getTileEntity(pos);

        // Update it
        if (gearBox instanceof TileGearBox) {
            ((TileGearBox) gearBox).updateCrankables();

            ((World) w).notifyBlockUpdate(pos, this.getDefaultState(), w.getBlockState(pos), 3);
        }
    }

    /**
     * Dependent on config setting.
     */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    /**
     * Prevents MC from using the default block renderer.
     * Dependent on config setting.
     */
    @Override
    public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
        return true;
    }

}