package thaumicenergistics.api;

import appeng.api.definitions.IItemDefinition;

/**
 * Contains functions that return the Item Definition for each item in Thaumic Energistics
 *
 * @author BrockWS
 * @version 1.0.0
 * @since 1.0.0
 */
public interface IThEItems {

    // Cells

    IItemDefinition essentiaCell1k();

    IItemDefinition essentiaCell4k();

    IItemDefinition essentiaCell16k();

    IItemDefinition essentiaCell64k();

    IItemDefinition essentiaCell256k();

    IItemDefinition essentiaCell1024k();

    IItemDefinition essentiaCell4096k();

    IItemDefinition essentiaCell16384k();

    IItemDefinition essentiaCellCreative();

    // Parts

    IItemDefinition essentiaImportBus();

    IItemDefinition essentiaExportBus();

    IItemDefinition essentiaStorageBus();

    IItemDefinition essentiaTerminal();

    IItemDefinition arcaneTerminal();

    IItemDefinition arcaneInscriber();

    // Materials

    IItemDefinition diffusionCore();

    IItemDefinition coalescenceCore();

    IItemDefinition essentiaComponent1k();

    IItemDefinition essentiaComponent4k();

    IItemDefinition essentiaComponent16k();

    IItemDefinition essentiaComponent64k();

    IItemDefinition essentiaComponent256k();

    IItemDefinition essentiaComponent1024k();

    IItemDefinition essentiaComponent4096k();

    IItemDefinition essentiaComponent16384k();

    IItemDefinition upgradeArcane();

    IItemDefinition knowledgeCore();

    IItemDefinition blankKnowledgeCore();

    IItemDefinition ironGear();
    IItemDefinition advancedEssentiaHousing();

    // Other

    IItemDefinition dummyAspect();
}
