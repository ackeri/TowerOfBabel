package com.tower;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.TowerOfBabel;

/*
 * Totem blocks and their recipes, defined in TowerOfBabel-totems.cfg so a pack can add its own.
 * Each one is registered as TowerOfBabel:totem_<name>, which is what a skill's block setting refers to.
 */
public class Totems {
	public static final Map<String, TotemBlock> totems = new LinkedHashMap<String, TotemBlock>();
	private static Configuration config;

	/*
	 * Blocks have to be registered during preInit
	 */
	public static void registerBlocks(File configDir) {
		config = new Configuration(new File(configDir, "TowerOfBabel-totems.cfg"));
		config.load();
		if(config.getCategoryNames().isEmpty()) {
			generateConfig();
		}

		for(String name : config.getCategoryNames()) {
			try {
				String display = config.getString("name", name, name, "Name shown in game");
				String texture = config.getString("texture", name, "towerofbabel:totem_" + name,
						"Block texture as domain:name, found at assets/<domain>/textures/blocks/<name>.png. A resource pack can supply new ones");
				String kind = config.getString("material", name, "stone", "What the block is made of, which sets its sound and tool", new String[] {"wood", "stone", "glass"});
				float hardness = config.getFloat("hardness", name, 2.5f, 0, 100, "How long the block takes to break");
				int light = config.getInt("light", name, 0, 0, 15, "Light level the block gives off");

				TotemBlock b = new TotemBlock(name, TotemBlock.Kind.valueOf(kind.toUpperCase()), texture, hardness, light);
				GameRegistry.registerBlock(b, "totem_" + name);
				LanguageRegistry.instance().addStringLocalization(b.getUnlocalizedName() + ".name", display);
				totems.put(name, b);
			} catch(Exception e) {
				TowerOfBabel.log.error("Skipping totem " + name + ": " + e);
			}
		}
		if(config.hasChanged()) {
			config.save();
		}
	}

	/*
	 * Recipes can use items from any mod, so they are added once every mod has registered its items
	 */
	public static void registerRecipes() {
		for(TotemBlock b : totems.values()) {
			String[] shape = config.getStringList("recipe", b.id, new String[0],
					"Crafting grid rows, up to 3 rows of up to 3 characters with spaces for empty slots. Empty means it can't be crafted");
			String[] keys = config.getStringList("ingredients", b.id, new String[0],
					"What each character in the recipe stands for, as X=modid:name, X=modid:name:meta or X=ore:oreDictName");
			if(shape.length == 0) continue;

			try {
				List<Object> args = new ArrayList<Object>();
				for(String row : shape) {
					args.add(row);
				}
				for(String k : keys) {
					String[] parts = k.split("=", 2);
					if(parts.length != 2 || parts[0].length() != 1) {
						throw new IllegalArgumentException("expected X=ingredient but got " + k);
					}
					args.add(parts[0].charAt(0));
					args.add(ingredient(parts[1].trim()));
				}
				GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(b), args.toArray()));
			} catch(Exception e) {
				TowerOfBabel.log.error("Skipping recipe for totem " + b.id + ": " + e);
			}
		}
		if(config.hasChanged()) {
			config.save();
		}
	}

	private static Object ingredient(String s) {
		if(s.startsWith("ore:")) {
			return s.substring(4);
		}
		String[] parts = s.split(":");
		int meta = 0;
		if(parts.length == 3) {
			meta = parts[2].equals("*") ? OreDictionary.WILDCARD_VALUE : Integer.parseInt(parts[2]);
		} else if(parts.length != 2) {
			throw new IllegalArgumentException("expected modid:name but got " + s);
		}
		String name = parts[0] + ":" + parts[1];
		Item item = (Item)Item.itemRegistry.getObject(name);
		if(item == null && Block.blockRegistry.containsKey(name)) {
			item = Item.getItemFromBlock((Block)Block.blockRegistry.getObject(name));
		}
		if(item == null) {
			throw new IllegalArgumentException("unknown item " + name);
		}
		return new ItemStack(item, 1, meta);
	}

	/*
	 * Totems drawn for the mod. Each symbol comes in wood, cobblestone and stone, and each is crafted from the one
	 * before it, so a tract's levels have to be worked through in order.
	 */
	private static void generateConfig() {
		symbol("running_rabbit", "Running Rabbit", "minecraft:sugar");
		symbol("sitting_rabbit", "Sitting Rabbit", "minecraft:carrot");
		symbol("rabbit_head", "Rabbit Head", "minecraft:golden_carrot");
		symbol("bull", "Bull", "minecraft:blaze_powder");
		symbol("tools", "Tools", "minecraft:iron_pickaxe");
		symbol("pipes", "Pipes", "minecraft:hopper");

		//alternate drawings of the running rabbit, without recipes
		totem("running_rabbit_wood_alt", "Running Rabbit Totem (Wood, Alternate)", "wood", 2.5f, 0, new String[0], new String[0]);
		totem("running_rabbit_stone_alt", "Running Rabbit Totem (Stone, Alternate)", "stone", 3.5f, 0, new String[0], new String[0]);

		totem("magic_glass", "Magic Totem", "glass", 1.0f, 7,
				new String[] {"GGG", "GLG", "GGG"}, new String[] {"G=ore:blockGlassColorless", "L=minecraft:dye:4"});
	}

	private static void symbol(String id, String name, String item) {
		String[] ring = {"XXX", "XYX", "XXX"};
		totem(id + "_wood", name + " Totem (Wood)", "wood", 2.5f, 0, ring, new String[] {"X=ore:logWood", "Y=" + item});
		totem(id + "_cobblestone", name + " Totem (Cobblestone)", "stone", 3.0f, 0, ring,
				new String[] {"X=minecraft:cobblestone", "Y=TowerOfBabel:totem_" + id + "_wood"});
		totem(id + "_stone", name + " Totem (Stone)", "stone", 3.5f, 0, ring,
				new String[] {"X=ore:stone", "Y=TowerOfBabel:totem_" + id + "_cobblestone"});
	}

	private static void totem(String id, String name, String material, float hardness, int light, String[] recipe, String[] ingredients) {
		config.get(id, "name", name);
		config.get(id, "material", material);
		config.get(id, "hardness", hardness);
		config.get(id, "light", light);
		config.get(id, "recipe", recipe);
		config.get(id, "ingredients", ingredients);
	}
}
