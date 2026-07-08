package thaumicenergistics.client.gui.part;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiNumberBox;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import thaumicenergistics.api.ThEApi;
import thaumicenergistics.client.gui.GuiConfigurable;
import thaumicenergistics.container.part.ContainerEssentiaLevelEmitter;
import thaumicenergistics.init.ModGlobals;
import thaumicenergistics.network.PacketHandler;
import thaumicenergistics.network.packets.PacketLevelEmitterValue;

import java.io.IOException;

public class GuiEssentiaLevelEmitter extends GuiConfigurable {

    private GuiNumberBox level;

    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    public GuiEssentiaLevelEmitter(ContainerEssentiaLevelEmitter container) {
        super(container);
        this.xSize = 211;
        this.ySize = 184;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.level =
                new GuiNumberBox(
                        this.fontRenderer,
                        this.guiLeft + 24,
                        this.guiTop + 43,
                        79,
                        this.fontRenderer.FONT_HEIGHT,
                        Long.class);
        this.level.setEnableBackgroundDrawing(false);
        this.level.setMaxStringLength(16);
        this.level.setTextColor(0xFFFFFF);
        this.level.setVisible(true);
        this.level.setFocused(true);

        this.buttonList.add(
                this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 17, 22, 20, "+1"));
        this.buttonList.add(
                this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 17, 28, 20, "+10"));
        this.buttonList.add(
                this.plus100 =
                        new GuiButton(0, this.guiLeft + 82, this.guiTop + 17, 32, 20, "+100"));
        this.buttonList.add(
                this.plus1000 =
                        new GuiButton(0, this.guiLeft + 120, this.guiTop + 17, 38, 20, "+1000"));

        this.buttonList.add(
                this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 59, 22, 20, "-1"));
        this.buttonList.add(
                this.minus10 =
                        new GuiButton(0, this.guiLeft + 48, this.guiTop + 59, 28, 20, "-10"));
        this.buttonList.add(
                this.minus100 =
                        new GuiButton(0, this.guiLeft + 82, this.guiTop + 59, 32, 20, "-100"));
        this.buttonList.add(
                this.minus1000 =
                        new GuiButton(0, this.guiLeft + 120, this.guiTop + 59, 38, 20, "-1000"));

        this.buttonList.add(
                new GuiImgButton(
                        this.guiLeft - 18,
                        this.guiTop + 8,
                        Settings.REDSTONE_EMITTER,
                        RedstoneMode.HIGH_SIGNAL));
    }

    public void setLevel(long value) {
        if (this.level != null) this.level.setText(String.valueOf(value));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        this.fontRenderer.drawString(
                ThEApi.instance().lang().guiEssentiaLevelEmitter().getLocalizedKey(),
                8,
                6,
                4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(this.getGuiBackground());
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
        this.level.drawTextBox();
    }

    @Override
    protected ResourceLocation getGuiBackground() {
        return new ResourceLocation(ModGlobals.MOD_ID_AE2, "textures/guis/lvlemitter.png");
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        super.actionPerformed(btn);

        boolean isPlus =
                btn == this.plus1
                        || btn == this.plus10
                        || btn == this.plus100
                        || btn == this.plus1000;
        boolean isMinus =
                btn == this.minus1
                        || btn == this.minus10
                        || btn == this.minus100
                        || btn == this.minus1000;

        if (isPlus || isMinus) this.addQty(this.getQty(btn));
    }

    private long getQty(GuiButton btn) {
        if (btn == this.plus1 || btn == this.minus1) return btn == this.plus1 ? 1 : -1;
        if (btn == this.plus10 || btn == this.minus10) return btn == this.plus10 ? 10 : -10;
        if (btn == this.plus100 || btn == this.minus100) return btn == this.plus100 ? 100 : -100;
        return btn == this.plus1000 ? 1000 : -1000;
    }

    private void addQty(long delta) {
        long result;
        try {
            result = Long.parseLong(this.level.getText());
        } catch (NumberFormatException e) {
            result = 0;
        }
        result = Math.max(0, result + delta);
        this.level.setText(String.valueOf(result));
        PacketHandler.sendToServer(new PacketLevelEmitterValue(result));
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if ((key == 211
                            || key == 205
                            || key == 203
                            || key == 14
                            || Character.isDigit(character))
                    && this.level.textboxKeyTyped(character, key)) {
                long value;
                try {
                    value = Long.parseLong(this.level.getText());
                } catch (NumberFormatException e) {
                    value = 0;
                }
                PacketHandler.sendToServer(new PacketLevelEmitterValue(value));
            } else {
                super.keyTyped(character, key);
            }
        }
    }
}
