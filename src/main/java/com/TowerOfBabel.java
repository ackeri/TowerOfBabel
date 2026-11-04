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
import com.tower.Totems;
import com.tower.SkillEffects;
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
	public static int towerCapacity;
	public static int towerVariance;
	public static int towerActiveDays;
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
		Totems.registerBlocks(event.getModConfigurationDirectory());
	}

	/*
	 * Stage 2 initialization, after all items and entities exist
	 */
	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		GameRegistry.addRecipe(new ItemStack(towerBase), "SSS", "SGS", "SSS", 'S', Blocks.stonebrick, 'G', Blocks.gold_block);
		ActionBlocker.register();
		SkillEffects.register();
	}

	/*
	 * Skills name blocks from other mods, so they are read once every mod has registered its blocks
	 */
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		syncConfig();
		Totems.registerRecipes();
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
			towerVariance = config.getInt("towerVariance", GENERAL, 3, 0, 255,
					"A tower can hold at most this many more skills than the shortest other active tower, so nobody gets far ahead "
					+ "and players specialize instead of learning everything. 0 turns it off");
			towerActiveDays = config.getInt("towerActiveDays", GENERAL, 7, 1, 3650,
					"Towers of players who haven't been online for this many days don't count towards towerVariance");
			towerCapacity = config.getInt("towerCapacity", GENERAL, 0, 0, 255,
					"A fixed limit on how many skills one tower can hold, on top of towerVariance. 0 means no fixed limit");
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
	 * Example config for a new install: iron and diamond gear are locked behind separate making and using skills, so players trade, and a few totems give effects
	 */
	private static void generateConfig() {
		String gear = "_(sword|shovel|pickaxe|axe|hoe|helmet|chestplate|leggings|boots)";

		config.get(GENERAL, "defaultGrants", new String[] {"all:(?!minecraft:(iron|diamond)" + gear + "$).*"});

		//making and using are separate skills, so smiths and fighters need each other
		config.get("smithing", "block", "TowerOfBabel:totem_tools_wood");
		config.get("smithing", "grants", new String[] {"craft,carry:minecraft:iron" + gear});
		config.get("smithing", "description", "The tools: forge iron tools and armor for others");
		config.get("mastersmithing", "block", "TowerOfBabel:totem_tools_cobblestone");
		config.get("mastersmithing", "prereqs", new String[] {"smithing"});
		config.get("mastersmithing", "grants", new String[] {"craft,carry:minecraft:diamond" + gear});
		config.get("mastersmithing", "description", "The tools, carved deeper: forge diamond tools and armor");
		config.get("mastersmithing", "seats", 2);

		config.get("ironclad", "block", "minecraft:iron_block");
		config.get("ironclad", "grants", new String[] {"wear,hold,carry,break,attack,use:minecraft:iron" + gear});
		config.get("ironclad", "description", "Wear and wield iron tools and armor");
		config.get("diamondclad", "block", "minecraft:diamond_block");
		config.get("diamondclad", "prereqs", new String[] {"ironclad"});
		config.get("diamondclad", "grants", new String[] {"wear,hold,carry,break,attack,use:minecraft:diamond" + gear});
		config.get("diamondclad", "description", "Wear and wield diamond tools and armor");

		//totem skills that give effects, after the sketches they were drawn from
		config.get("swiftness", "block", "TowerOfBabel:totem_running_rabbit_wood");
		config.get("swiftness", "effects", new String[] {"speed"});
		config.get("swiftness", "description", "The running rabbit: Speed");
		config.get("swiftness2", "block", "TowerOfBabel:totem_running_rabbit_cobblestone");
		config.get("swiftness2", "prereqs", new String[] {"swiftness"});
		config.get("swiftness2", "effects", new String[] {"speed:2"});
		config.get("swiftness2", "description", "The running rabbit, carved deeper: Speed II");
		config.get("might", "block", "TowerOfBabel:totem_bull_wood");
		config.get("might", "effects", new String[] {"strength"});
		config.get("might", "description", "The bull: Strength");
		//the rabbit and the bull pull in different directions; a tower can follow one
		config.get("might", "excludes", new String[] {"swiftness"});
		config.get("nighteyes", "block", "TowerOfBabel:totem_rabbit_head_wood");
		config.get("nighteyes", "effects", new String[] {"night_vision"});
		config.get("nighteyes", "description", "The rabbit's wide eyes: Night Vision");
	}
}
