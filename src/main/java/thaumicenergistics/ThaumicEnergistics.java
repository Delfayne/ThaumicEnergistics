package thaumicenergistics;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import org.apache.logging.log4j.Logger;
import org.dv.minecraft.thaumicenergistics.Reference;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.items.casters.ItemCaster;

import thaumicenergistics.api.IThEBlocks;
import thaumicenergistics.api.IThEItems;
import thaumicenergistics.api.IThEUpgrades;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.client.ThEItemColors;
import thaumicenergistics.client.gui.GuiHandler;
import thaumicenergistics.client.render.ArcaneAssemblerRenderer;
import thaumicenergistics.client.render.EssentiaInterfaceRenderer;
import thaumicenergistics.command.CommandAddVis;
import thaumicenergistics.command.CommandDrainVis;
import thaumicenergistics.init.ModGlobals;
import thaumicenergistics.integration.ThEIntegrationLoader;
import thaumicenergistics.item.ItemPartBase;
import thaumicenergistics.network.PacketHandler;
import thaumicenergistics.tile.TileArcaneAssembler;
import thaumicenergistics.tile.TileEssentiaInterface;
import thaumicenergistics.util.ForgeUtil;

/**
 * <strong>Thaumic Energistics</strong> <hr> A bridge between Thaumcraft and Applied Energistics.
 * Essentia storage management, transportation, and application.
 *
 * @author Nividica
 */
@Mod(
        modid = Reference.MOD_ID,
        name = Reference.NAME,
        version = Reference.VERSION,
        dependencies = ModGlobals.MOD_DEPENDENCIES)
@Mod.EventBusSubscriber
public class ThaumicEnergistics {

    /** Singleton instance */
    @Mod.Instance(value = Reference.MOD_ID)
    public static ThaumicEnergistics INSTANCE;

    /** Proxy class that runs code that should be strictly on the physical client */
    @SidedProxy public static IProxy proxy;

    /** Thaumic Energistics Logger */
    public static Logger LOGGER;

    /**
     * Called before the load event.
     *
     * @param event FMLPreInitializationEvent
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ThaumicEnergistics.LOGGER = event.getModLog();
        ThEApi.instance(); // Make sure to init the api
        MinecraftForge.EVENT_BUS.register(this);
        PacketHandler.register();

        proxy.preInit(event);

        ThEIntegrationLoader.preInit();
    }

    /**
     * Called after the preInit event, and before the post init event.
     *
     * @param event FMLInitializationEvent
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(ThaumicEnergistics.INSTANCE, new GuiHandler());
        if (ForgeUtil.isClient()) {
            ThEItemColors.registerItemColors();
        }

        IThEUpgrades upgrades = ThaumicEnergisticsApi.instance().upgrades();
        IThEItems items = ThaumicEnergisticsApi.instance().items();
        IThEBlocks blocks = ThaumicEnergisticsApi.instance().blocks();

        upgrades.registerUpgrade(items.arcaneTerminal(), upgrades.arcaneCharger(), 1);
        upgrades.registerUpgrade(items.arcaneInscriber(), upgrades.blankKnowledgeCore(), 1);
        upgrades.registerUpgrade(items.arcaneInscriber(), upgrades.knowledgeCore(), 1);
        upgrades.registerUpgrade(blocks.arcaneAssembler(), upgrades.knowledgeCore(), 1);
        upgrades.registerUpgrade(blocks.arcaneAssembler(), upgrades.arcaneCharger(), 1);
        upgrades.registerUpgrade(blocks.arcaneAssembler(), upgrades.cardSpeed(), 5);

        proxy.init(event);

        ThEIntegrationLoader.init();
    }

    /**
     * Called after the load event.
     *
     * @param event FMLPostInitializationEvent
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);

        ThEIntegrationLoader.postInit();
    }

    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        if (ModGlobals.DEBUG_MODE) {
            event.registerServerCommand(new CommandAddVis());
            event.registerServerCommand(new CommandDrainVis());
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // Temporary alpha warning
        // TextComponentString s1 = new TextComponentString("Thaumic Energistics is currently in
        // alpha. Post issues to GitHub");
        // s1.getStyle().setColor(TextFormatting.RED);
        // TextComponentString link = new
        // TextComponentString("https://github.com/Nividica/ThaumicEnergistics");
        // link.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
        // "https://github.com/Nividica/ThaumicEnergistics")).setColor(TextFormatting.GOLD);
        //
        // event.player.sendMessage(s1.appendSibling(link));
    }

    @SubscribeEvent
    public void onConfigChangedEvent(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(Reference.MOD_ID))
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPartPlacementOnJar(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!player.isSneaking()) return;
        TileEntity te = event.getWorld().getTileEntity(event.getPos());
        if (!(te instanceof IAspectContainer)) return;

        if (event.getHand() == EnumHand.OFF_HAND) {
            // The off-hand's companion RightClickBlock event for this click always fires after
            // the main-hand one and is redundant here either way: if a part was being placed,
            // the main-hand event already denied block use; if the player genuinely wants to
            // sneak-dump the jar with an empty hand, the main-hand event (which fires first)
            // already triggered that.
            event.setUseBlock(Event.Result.DENY);
            return;
        }

        boolean holdingPart =
                player.getHeldItemMainhand().getItem() instanceof ItemPartBase
                        || player.getHeldItemOffhand().getItem() instanceof ItemPartBase;
        if (holdingPart) {
            // Thaumcraft's own jar dumps its contents on sneak + empty hand; sneaking while
            // holding a part is meant to place the part instead, but the block's click handler
            // always fires regardless and doesn't check what's actually held. Deny it explicitly
            // so placing a part can't trigger that.
            event.setUseBlock(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEssentiaInterfaceSideConfigure(PlayerInteractEvent.RightClickBlock event) {
        TileEntity te = event.getWorld().getTileEntity(event.getPos());
        if (!(te instanceof TileEssentiaInterface) || event.getWorld().isRemote) return;
        EnumFacing side = event.getFace();
        if (side == null) return;

        TileEssentiaInterface tile = (TileEssentiaInterface) te;
        EntityPlayer player = event.getEntityPlayer();
        ItemStack heldItem = event.getItemStack();

        // If we have a caster gauntlet then configure the sides
        if (heldItem.getItem() instanceof ItemCaster) {
            if (player.isSneaking()) tile.disableSide(side, player);
            else tile.setSideInput(side, player);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
            // Per Forge's own documented contract on PlayerInteractEvent.RightClickBlock: "If ...
            // result is not SUCCESS, the client will then try RightClickItem" -- a SEPARATE event,
            // fired as a fallback specifically because ItemCaster puts its actual spell-casting
            // logic in onItemRightClick (not onItemUseFirst/onItemUse), a pattern many items use.
            // Cancelling this event with SUCCESS prevents that fallback from being attempted at
            // all,
            // but if it still is (e.g. a client/server ordering edge case),
            // onEssentiaInterfaceCasterFallback below independently re-checks (via its own
            // raytrace)
            // whether the player is still looking at a TileEssentiaInterface and suppresses it too.
            return;
        }

        if (heldItem.getItem() instanceof IEssentiaContainerItem) {
            AspectList aspects = ((IEssentiaContainerItem) heldItem.getItem()).getAspects(heldItem);
            if (aspects != null && aspects.size() == 1) {
                if (player.isSneaking()) tile.disableSide(side, player);
                else tile.setSideOutput(side, aspects.getAspects()[0], player);
                event.setCancellationResult(EnumActionResult.SUCCESS);
                event.setCanceled(true);
            } else if (aspects != null && aspects.size() > 1) {
                player.sendMessage(
                        new TextComponentTranslation(
                                "tooltip.thaumicenergistics.essentia_interface.multi_aspect_item"));
                event.setCancellationResult(EnumActionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEssentiaInterfaceCasterFallback(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote) return;
        if (!(event.getItemStack().getItem() instanceof ItemCaster)) return;
        EntityPlayer player = event.getEntityPlayer();
        RayTraceResult trace = rayTraceLookedAtBlock(player);
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) return;
        if (!(event.getWorld().getTileEntity(trace.getBlockPos()) instanceof TileEssentiaInterface))
            return;
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Where a player is currently looking, out to their interaction reach, blocks only. */
    private static RayTraceResult rayTraceLookedAtBlock(EntityPlayer player) {
        double reach =
                player instanceof EntityPlayerMP
                        ? ((EntityPlayerMP) player).interactionManager.getBlockReachDistance()
                        : 5.0;
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLook(1.0F);
        Vec3d endPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        return player.world.rayTraceBlocks(eyePos, endPos, false, false, true);
    }

    public static class ClientProxy implements IProxy {
        public void init(FMLInitializationEvent event) {
            // Init TESR
            ClientRegistry.bindTileEntitySpecialRenderer(
                    TileArcaneAssembler.class, new ArcaneAssemblerRenderer());
            ClientRegistry.bindTileEntitySpecialRenderer(
                    TileEssentiaInterface.class, new EssentiaInterfaceRenderer());
        }

        public EntityPlayer getPlayerEntFromCtx(MessageContext ctx) {
            return ctx.side.isClient()
                    ? Minecraft.getMinecraft().player
                    : ctx.getServerHandler().player;
        }
    }

    public static class ServerProxy implements IProxy {
        public EntityPlayer getPlayerEntFromCtx(MessageContext ctx) {
            return ctx.getServerHandler().player;
        }
    }

    public interface IProxy {
        default void preInit(FMLPreInitializationEvent event) {}

        default void init(FMLInitializationEvent event) {}

        default void postInit(FMLPostInitializationEvent event) {}

        EntityPlayer getPlayerEntFromCtx(MessageContext ctx);
    }
}
