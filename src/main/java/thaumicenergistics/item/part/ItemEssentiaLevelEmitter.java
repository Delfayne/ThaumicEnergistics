package thaumicenergistics.item.part;

import appeng.api.AEApi;
import appeng.api.parts.IPart;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.model.ModelLoader;

import org.dv.minecraft.thaumicenergistics.Reference;

import thaumicenergistics.item.ItemPartBase;
import thaumicenergistics.part.PartEssentiaLevelEmitter;

import javax.annotation.Nullable;

public class ItemEssentiaLevelEmitter extends ItemPartBase {

    public ItemEssentiaLevelEmitter(String id) {
        super(id);
    }

    @Nullable
    @Override
    public IPart createPartFromItemStack(ItemStack is) {
        return new PartEssentiaLevelEmitter(this);
    }

    @Override
    public void initModel() {
        AEApi.instance().registries().partModels().registerModels(PartEssentiaLevelEmitter.MODELS);
        ModelLoader.setCustomModelResourceLocation(
                this,
                0,
                new ModelResourceLocation(Reference.MOD_ID + ":part/essentia_level_emitter"));
    }
}
