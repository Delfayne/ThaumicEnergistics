package thaumicenergistics.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.dv.minecraft.thaumicenergistics.Reference;
import org.lwjgl.opengl.GL11;
import thaumicenergistics.client.model.ModelGearbox;
import thaumicenergistics.tile.TileGearBox;

/**
 * Renders the {@link TileGearBox}
 *
 * @author Nividica
 *
 */
@SideOnly(Side.CLIENT)
public class TileGearboxRender
	extends TileEntitySpecialRenderer<TileGearBox>
{
	/**
	 * Gearbox model
	 */
	private final ModelGearbox gearboxModel = new ModelGearbox();

	/**
	 * Textures
	 */
	private final ResourceLocation TEX_IRON = new ResourceLocation( Reference.MOD_ID, "textures/block/gearboxiron.png" ),
					TEX_THAUMIUM = new ResourceLocation( Reference.MOD_ID, "textures/block/gearboxthaumium.png" );

	private void renderGearbox(final TileGearBox gearboxTile)
	{
		// Push the matrix
		GL11.glPushMatrix();

		// Center the model
		GL11.glTranslatef( 0.5F, 0.5F, 0.5F );

		// Bind the model texture
		if( gearboxTile.isThaumiumGearbox() )
		{
			Minecraft.getMinecraft().renderEngine.bindTexture( this.TEX_THAUMIUM );
		}
		else
		{
			Minecraft.getMinecraft().renderEngine.bindTexture( this.TEX_IRON );
		}

		// Scale down
		GL11.glScalef( 0.12F, 0.12F, 0.12F );

		// Update the model.
		this.gearboxModel.updateToTileEntity( gearboxTile );

		// Render the gearbox
		this.gearboxModel.render( null, 0, 0, -0.1F, 0, 0, 0.625F );

		// Pop the matrix
		GL11.glPopMatrix();
	}

	/**
	 * Called when a gearbox tile entity needs to be rendered.
	 *
	 * @param tileEntity
	 * @param d
	 * @param d1
	 * @param d2
	 * @param f
	 */
	@Override
	public void render( final TileGearBox tileEntity, final double d, final double d1, final double d2, final float f, int destroyStage, float alpha)
	{
		// Push the GL matrix
		GL11.glPushMatrix();

		// Computes the proper place to draw
		GL11.glTranslatef( (float)d, (float)d1, (float)d2 );

		// Get the gearbox
		TileGearBox gearBox = tileEntity;

		// Render the gearbox
		this.renderGearbox( gearBox );

		// Pop the GL matrix
		GL11.glPopMatrix();
	}
}