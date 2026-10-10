package com;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.common.config.Configuration;
import net.minecraft.block.Block;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.tower.Skill;
import com.tower.TowerBaseEntity;
import com.tower.TowerBase;
import com.babel.ActionBlocker;
import com.babel.Grant;
import com.babel.GrantSync;
import com.babel.SkillCache;


/*
 * Root file for mod, that causes all other Components to link up
 */
@Mod(modid = TowerOfBabel.MODID, name = TowerOfBabel.NAME, version = TowerOfBabel.VERSION)
public class TowerOfBabel
{
	public static final String NAME = "Tower of Babel";
	public static final String MODID = "TowerOfBabel";
	public static final String VERSION = "1.0";

	public static final String GENERAL = "general";

	/**
	 * Configuration fields, referenced by other parts of the code as constants
	 */
	public static Configuration config;
	public static Logger log;
	public static boolean dimensionSpecific;
	public static boolean tagItemOwners;
	public static int maxTowerHeight;
	public static List<Grant> defaultGrants = new ArrayList<Grant>();
	public static Map<String, Skill> skills = new LinkedHashMap<String, Skill>();

	public static Block towerBase;

	@SidedProxy(clientSide = "com.ClientProxy", serverSide = "com.CommonProxy")
	public static CommonProxy proxy;
	public static SimpleNetworkWrapper network;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		log = event.getModLog();
		config = new Configuration(event.getSuggestedConfigurationFile());

		network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
		network.registerMessage(GrantSync.Handler.class, GrantSync.class, 0, Side.CLIENT);
		proxy.init();

		towerBase = new TowerBase();
		GameRegistry.registerBlock(towerBase, "TowerBase");
		GameRegistry.registerTileEntity(TowerBaseEntity.class, "TowerBaseE");
	}

	/*
	 * Stage 2 initialization, after all items and entities exist
	 */
	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		GameRegistry.addRecipe(new ItemStack(towerBase), "SSS", "SGS", "SSS", 'S', Blocks.stonebrick, 'G', Blocks.gold_block);
		ActionBlocker.register();
	}

	/*
	 * Skills name blocks from other mods, so they are read once every mod has registered its blocks
	 */
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		syncConfig();
		ActionBlocker.wrapRecipes();
	}

	@EventHandler
	public void start(FMLServerStartingEvent event) {
		event.registerServerCommand(new CLI());
	}

	@EventHandler
	public void started(FMLServerStartedEvent event) {
		//catch recipes added since postInit, like MineTweaker scripts
		ActionBlocker.wrapRecipes();
	}

	@EventHandler
	public void stopped(FMLServerStoppedEvent event) {
		//singleplayer can open a different world next
		SkillCache.clear();
	}


	/*
	 * Load configuration data from file
	 */
	public static void syncConfig() {
		try {
			config.load();
			if(config.getCategoryNames().isEmpty()) {
				generateConfig();
			}

			config.setCategoryComment(GENERAL, "Every other category in this file defines a skill");
			dimensionSpecific = config.getBoolean("dimensionSpecific", GENERAL, false,
					"Default for skills that don't set it: only grant the skill while the owner is in the same dimension as their tower");
			maxTowerHeight = config.getInt("maxTowerHeight", GENERAL, 32, 1, 255, "How many blocks above the base are checked for skill blocks");
			tagItemOwners = config.getBoolean("tagItemOwners", GENERAL, false,
					"Tag items with the last player to pick them up so automated crafting uses that player's skills. "
					+ "Tagged items don't stack with untagged ones. When false, automated crafting is never blocked");

			defaultGrants.clear();
			for(String g : config.getStringList("defaultGrants", GENERAL, new String[0],
					"What every player may do without any skills, as actions:regex like the grants of a skill")) {
				try {
					defaultGrants.add(Grant.parse(g));
				} catch(Exception e) {
					log.error("Skipping default grant '" + g + "': " + e);
				}
			}

			skills.clear();
			for(String s : config.getCategoryNames()) {
				if(s.equals(GENERAL)) continue;
				try {
					skills.put(s, Skill.load(config, s));
				} catch(Exception e) {
					log.error("Skipping skill " + s + ": " + e);
				}
			}
			log.info("Loaded " + skills.size() + " skills");
		} catch (Exception e) {
			log.error("Failed to load config", e);
		} finally {
			if(config.hasChanged()) {
				config.save();
			}
		}
	}

	/*
	 * Example config for a new install: iron and diamond gear are locked behind tower skills
	 */
	private static void generateConfig() {
		String gear = "_(sword|shovel|pickaxe|axe|hoe|helmet|chestplate|leggings|boots)";

		config.get(GENERAL, "defaultGrants", new String[] {"all:(?!minecraft:(iron|diamond)" + gear + "$).*"});

		config.get("ironworking", "block", "minecraft:iron_block");
		config.get("ironworking", "grants", new String[] {"all:minecraft:iron" + gear});

		config.get("diamondworking", "block", "minecraft:diamond_block");
		config.get("diamondworking", "prereqs", new String[] {"ironworking"});
		config.get("diamondworking", "grants", new String[] {"all:minecraft:diamond" + gear});
	}
}
