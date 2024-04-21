package thaumicenergistics.integration.thaumcraft.research;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumicenergistics.init.ModGlobals;

import java.util.Random;

/**
 * @author BrockWS
 */
public class CardTinkerAE extends TheorycraftCard {

    @Override
    public int getInspirationCost() {
        return 1;
    }

    @Override
    public String getLocalizedName() {
        return I18n.format("card.tinkerae.name");
    }

    @Override
    public String getLocalizedText() {
        return I18n.format("card.tinkerae.text");
    }

    @Override
    public boolean activate(EntityPlayer player, ResearchTableData data) {
        data.addTotal(ModGlobals.RESEARCH_CATEGORY, new Random().nextInt(15) + 10);
        return true;
    }
}
