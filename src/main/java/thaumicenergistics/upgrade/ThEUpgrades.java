package thaumicenergistics.upgrade;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import net.minecraft.item.ItemStack;
import thaumicenergistics.api.IThEItems;
import thaumicenergistics.api.IThEUpgrade;
import thaumicenergistics.api.IThEUpgrades;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author BrockWS
 */
public class ThEUpgrades implements IThEUpgrades {

    private final IThEUpgrade arcaneCharger;
    private final IThEUpgrade knowledgeCore;
    private final IThEUpgrade blankKnowledgeCore;
    private final IThEUpgrade cardSpeed;
    private final List<IThEUpgrade> upgrades;

    public ThEUpgrades(IThEItems items) {
        this.upgrades = new ArrayList<>();

        this.upgrades.add(this.arcaneCharger = new ThEUpgrade(items.upgradeArcane()));
        this.upgrades.add(this.knowledgeCore = new ThEUpgrade(items.knowledgeCore()));
        this.upgrades.add(this.blankKnowledgeCore = new ThEUpgrade(items.blankKnowledgeCore()));
        this.upgrades.add(this.cardSpeed = new ThEUpgrade(AEApi.instance().definitions().materials().cardSpeed()));
    }

    @Override
    public IThEUpgrade arcaneCharger() {
        return this.arcaneCharger;
    }

    @Override
    public IThEUpgrade knowledgeCore() {
        return this.knowledgeCore;
    }

    @Override
    public IThEUpgrade blankKnowledgeCore() {
        return this.blankKnowledgeCore;
    }

    @Override
    public IThEUpgrade cardSpeed() {
        return cardSpeed;
    }

    @Override
    public Optional<IThEUpgrade> getUpgrade(ItemStack stack) {
        return this.getUpgrades().stream().filter(upgrade -> upgrade.getDefinition().isSameAs(stack)).findFirst();
    }

    @Override
    public List<IThEUpgrade> getUpgrades() {
        return this.upgrades;
    }

    @Override
    public void registerUpgrade(IItemDefinition upgradable, IThEUpgrade upgrade, int max) {
        upgradable.maybeStack(1).ifPresent(stack -> upgrade.registerItem(stack, max));
    }
}
