package thaumicenergistics.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.tile.TileEssentiaInterface;
import thaumicenergistics.tile.TileEssentiaInterface.SideMode;

import java.awt.Color;

/**
 * Draws whichever essentia is configured on each face directly onto the block's raised screen: the
 * assigned {@link Aspect}'s own icon (tinted its own color, same technique as {@link
 * DummyAspectRenderer}) for an OUTPUT side, or a generic inflow glyph for an INPUT side. A DISABLED
 * side is left showing the plain screen texture baked into the model.
 */
@SideOnly(Side.CLIENT)
public class EssentiaInterfaceRenderer extends TileEntitySpecialRenderer<TileEssentiaInterface> {

    private static final ResourceLocation INPUT_GLYPH =
            new ResourceLocation(
                    "thaumicenergistics", "textures/block/essentia_interface_input_glyph.png");

    // Screen boxes protrude 0.75/16 past the casing (see essentia_interface_base.json) -- sit the
    // icon a hair further out still so it never z-fights with the screen surface underneath it.
    private static final double FACE_OFFSET = 0.5D + 0.75D / 16D + 0.01D;
    private static final double HALF_SIZE = 0.22D;

    @Override
    public void render(
            TileEssentiaInterface te,
            double x,
            double y,
            double z,
            float partialTicks,
            int destroyStage,
            float alpha) {
        for (EnumFacing side : EnumFacing.values()) {
            SideMode mode = te.getSideMode(side);
            if (mode == SideMode.DISABLED) continue;

            ResourceLocation texture;
            float r, g, b;
            if (mode == SideMode.OUTPUT) {
                Aspect aspect = te.getOutputAspect(side);
                if (aspect == null) continue;
                texture = aspect.getImage();
                Color c = new Color(aspect.getColor());
                r = c.getRed() / 255.0F;
                g = c.getGreen() / 255.0F;
                b = c.getBlue() / 255.0F;
            } else {
                texture = INPUT_GLYPH;
                r = g = b = 1.0F;
            }

            this.renderFaceIcon(x, y, z, side, texture, r, g, b);
        }
    }

    private void renderFaceIcon(
            double x,
            double y,
            double z,
            EnumFacing side,
            ResourceLocation texture,
            float r,
            float g,
            float b) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        // POSITION_TEX_COLOR carries no lightmap (texture unit 1) coordinate, so without this the
        // combiner samples whatever u/v unit 1 was last left at by the surrounding block render --
        // usually landing near the lightmap's dark corner and multiplying the icon down to black.
        // Force full brightness instead, same as vanilla's own always-lit overlays (e.g. beacon
        // beams).
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);

        double lo = 0.5D - HALF_SIZE;
        double hi = 0.5D + HALF_SIZE;
        double d = FACE_OFFSET;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        switch (side) {
            case UP:
                vertex(buf, lo, 0.5D + d, lo, 0, 0, r, g, b);
                vertex(buf, lo, 0.5D + d, hi, 0, 1, r, g, b);
                vertex(buf, hi, 0.5D + d, hi, 1, 1, r, g, b);
                vertex(buf, hi, 0.5D + d, lo, 1, 0, r, g, b);
                break;
            case DOWN:
                vertex(buf, lo, 0.5D - d, lo, 0, 0, r, g, b);
                vertex(buf, hi, 0.5D - d, lo, 1, 0, r, g, b);
                vertex(buf, hi, 0.5D - d, hi, 1, 1, r, g, b);
                vertex(buf, lo, 0.5D - d, hi, 0, 1, r, g, b);
                break;
            case NORTH:
                vertex(buf, lo, lo, 0.5D - d, 0, 0, r, g, b);
                vertex(buf, hi, lo, 0.5D - d, 1, 0, r, g, b);
                vertex(buf, hi, hi, 0.5D - d, 1, 1, r, g, b);
                vertex(buf, lo, hi, 0.5D - d, 0, 1, r, g, b);
                break;
            case SOUTH:
                vertex(buf, lo, lo, 0.5D + d, 0, 0, r, g, b);
                vertex(buf, lo, hi, 0.5D + d, 0, 1, r, g, b);
                vertex(buf, hi, hi, 0.5D + d, 1, 1, r, g, b);
                vertex(buf, hi, lo, 0.5D + d, 1, 0, r, g, b);
                break;
            case WEST:
                vertex(buf, 0.5D - d, lo, lo, 0, 0, r, g, b);
                vertex(buf, 0.5D - d, lo, hi, 1, 0, r, g, b);
                vertex(buf, 0.5D - d, hi, hi, 1, 1, r, g, b);
                vertex(buf, 0.5D - d, hi, lo, 0, 1, r, g, b);
                break;
            case EAST:
                vertex(buf, 0.5D + d, lo, lo, 0, 0, r, g, b);
                vertex(buf, 0.5D + d, hi, lo, 0, 1, r, g, b);
                vertex(buf, 0.5D + d, hi, hi, 1, 1, r, g, b);
                vertex(buf, 0.5D + d, lo, hi, 1, 0, r, g, b);
                break;
        }

        tess.draw();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void vertex(
            BufferBuilder buf,
            double x,
            double y,
            double z,
            double u,
            double v,
            float r,
            float g,
            float b) {
        buf.pos(x, y, z).tex(u, v).color(r, g, b, 1.0F).endVertex();
    }
}
