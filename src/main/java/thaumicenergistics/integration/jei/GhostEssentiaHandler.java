package thaumicenergistics.integration.jei;

import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.inventory.Slot;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumicenergistics.client.gui.part.GuiSharedEssentiaBus;
import thaumicenergistics.container.slot.SlotGhost;
import thaumicenergistics.container.slot.SlotGhostEssentia;
import thaumicenergistics.network.PacketHandler;
import thaumicenergistics.network.packets.PacketGhostEssentia;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class GhostEssentiaHandler implements IGhostIngredientHandler<GuiSharedEssentiaBus> {

    @Override
    @Nonnull
    public <I> List<Target<I>> getTargets(
            @Nonnull GuiSharedEssentiaBus gui,
            @Nonnull I ingredient,
            boolean doStart) {

        Stream<Target<I>> essentiaTargets = getForAspectList(gui, ingredient);

        return Stream.concat(essentiaTargets, Stream.empty())
                .collect(toList());
    }

    private <I> Stream<Target<I>> getForAspectList(GuiSharedEssentiaBus gui, I ingredient) {

        if (ingredient instanceof AspectList) {
            return gui.inventorySlots.inventorySlots.stream()
                    .filter(it -> it instanceof SlotGhostEssentia)
                    .filter(Slot::isEnabled)
                    .map(it -> (SlotGhost) it)
                    .map(it -> new Target<I>() {

                        @Override
                        @Nonnull
                        public Rectangle getArea() {
                            return new Rectangle(
                                    gui.getGuiLeft() + it.xPos,
                                    gui.getGuiTop() + it.yPos,
                                    17,
                                    17
                            );
                        }

                        @Override
                        public void accept(@Nonnull I ingredient) {
                            AspectList aspectList = (AspectList) ingredient;
                            Aspect aspect = aspectList.getAspects()[0];
                            PacketHandler.sendToServer(new PacketGhostEssentia(aspect, it.slotNumber));
                        }
                    });
        }
        return Stream.empty();
    }

    @Override
    public void onComplete() {

    }
}
