package thaumicenergistics.block;

import appeng.util.Platform;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base class for wrenchable blocks.
 *
 * @author Nividica
 *
 */
public abstract class AbstractBlockAEWrenchable
		extends BlockBase implements ITileEntityProvider
{

	protected AbstractBlockAEWrenchable( final String material )
	{
		super( material );
	}

	/**
	 * Called when the block is right-clicked
	 *
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @param player
	 * @return
	 */
	protected boolean onBlockActivated( final World world, final int x, final int y, final int z, final EntityPlayer player )
	{
		return false;
	}

	/**
	 * Called when the block is being removed via AE wrench.
	 * Return an itemstack that represents the block. Can be null.
	 */
	protected ItemStack onDismantled( final World world, final int x, final int y, final int z )
	{
		return null;
	}

	/**
	 * Returning false will cancel onBlockActivated.
	 *
	 * @return
	 */
	public boolean canPlayerInteract( final EntityPlayer player )
	{
		return true;
	}


	/**
	 * The block was right-clicked
	 */
	@Override
	public final boolean onBlockActivated(final World world, final BlockPos pos, IBlockState state, final EntityPlayer player, final EnumHand hand, final EnumFacing side,
										  final float hitX, final float hitY, final float hitZ )
	{
		// Can the player interact with the block?
		if( !this.canPlayerInteract( player ) )
		{
			return false;
		}

		// Is the player holding an AE wrench item?
		if( Platform.isWrench( player, player.getHeldItem(hand), pos ) )
		{
			// Is the player sneaking?
			if( player.isSneaking() )
			{
				// Call on dismantled
				ItemStack representitive = this.onDismantled( world, pos.getX(), pos.getY(), pos.getZ() );

				// Call break
				this.breakBlock( world, pos, world.getBlockState( pos ) );

				// Is there an representative itemstack?
				if( representitive == null )
				{
					// Create a basic stack.
					representitive = new ItemStack( this );
				}

				// Drop the itemstack
				this.dropBlockAsItem( world, pos, getBlockFromItem(representitive.getItem()).getDefaultState(), 0 );

				// Set the block to air
				world.setBlockToAir( pos );
			}

			return true;
		}

		return this.onBlockActivated( world, pos.getX(), pos.getY(), pos.getZ(), player );

	}

}