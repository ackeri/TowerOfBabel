package com.tower;

import net.minecraft.block.Block;
import net.minecraft.potion.Potion;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.config.Configuration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.TowerOfBabel;
import com.babel.Grant;

/*
 * One skill, granted by having its block in your tower. Defined by a category in the config file.
 */
public class Skill {
	public final String name;
	public final Block block;
	// -1 matches any metadata
	public final int meta;
	public final Set<String> prereqs;
	// tower levels the block may sit at, 1 being directly on the base; empty means any level
	public final int[] heights;
	public final List<Grant> grants;
	public final boolean dimensionSpecific;
	// potion effects held while the skill is active, as {potion id, amplifier}
	public final List<int[]> effects;
	public final String description;

	public Skill(String name, Block block, int meta, Set<String> prereqs, int[] heights, List<Grant> grants, boolean dimensionSpecific,
			List<int[]> effects, String description) {
		this.name = name;
		this.block = block;
		this.meta = meta;
		this.prereqs = prereqs;
		this.heights = heights;
		this.grants = grants;
		this.dimensionSpecific = dimensionSpecific;
		this.effects = effects;
		this.description = description;
	}

	public boolean matches(Block b, int m) {
		return b == block && (meta == -1 || meta == m);
	}

	public boolean allowedAt(int level) {
		if(heights.length == 0) {
			return true;
		}
		for(int h : heights) {
			if(h == level) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Reads the skill in config category name, throwing IllegalArgumentException if it is malformed
	 */
	public static Skill load(Configuration config, String name) {
		String blockName = config.getString("block", name, "", "Block that grants this skill when stacked on a tower base, as modid:name or modid:name:meta");
		String[] prereqs = config.getStringList("prereqs", name, new String[0], "Skills that must be lower in the same tower for this one to count");
		int[] heights = config.get(name, "heights", new int[0], "Tower levels this block counts at, 1 being directly on the base. Empty means any level").getIntList();
		String[] grants = config.getStringList("grants", name, new String[0], "What this skill allows, as actions:regex. Actions are all or a comma list of "
				+ "wear,hold,carry,break,attack,use,interact,place,craft and the regex matches registry names like minecraft:iron_sword");
		String[] effects = config.getStringList("effects", name, new String[0], "Potion effects held while the skill is active, as name or name:level, "
				+ "e.g. speed, strength:2, night_vision, haste, jump_boost, resistance, regeneration, fire_resistance, water_breathing, or a potion id");
		String description = config.getString("description", name, "", "Shown on totem tooltips and in /tower list");
		boolean dimSpecific = config.getBoolean("dimensionSpecific", name, TowerOfBabel.dimensionSpecific, "Only grant this skill while the owner is in the same dimension as their tower");

		String[] parts = blockName.split(":");
		int meta = -1;
		if(parts.length == 3) {
			meta = Integer.parseInt(parts[2]);
			blockName = parts[0] + ":" + parts[1];
		}
		if(!Block.blockRegistry.containsKey(blockName)) {
			throw new IllegalArgumentException("unknown block '" + blockName + "'");
		}
		Block block = (Block)Block.blockRegistry.getObject(blockName);

		List<Grant> parsed = new ArrayList<Grant>();
		for(String g : grants) {
			parsed.add(Grant.parse(g));
		}
		List<int[]> parsedEffects = new ArrayList<int[]>();
		for(String e : effects) {
			parsedEffects.add(parseEffect(e));
		}
		return new Skill(name, block, meta, new HashSet<String>(Arrays.asList(prereqs)), heights, parsed, dimSpecific, parsedEffects, description);
	}

	/*
	 * Reads name or name:level, where level 1 is the weakest, into {potion id, amplifier}
	 */
	static int[] parseEffect(String spec) {
		String[] parts = spec.trim().split(":");
		int level = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
		String want = parts[0].trim().toLowerCase().replace("_", "");
		if(want.equals("speed")) want = "movespeed";
		else if(want.equals("slowness")) want = "moveslowdown";
		else if(want.equals("haste")) want = "digspeed";
		else if(want.equals("strength")) want = "damageboost";
		else if(want.equals("jumpboost")) want = "jump";
		else if(want.equals("instanthealth")) want = "heal";

		for(Potion p : Potion.potionTypes) {
			if(p == null) continue;
			String have = p.getName().toLowerCase().replace("potion.", "");
			if(have.equals(want) || String.valueOf(p.id).equals(want)) {
				return new int[] {p.id, Math.max(0, level - 1)};
			}
		}
		throw new IllegalArgumentException("unknown potion effect " + spec);
	}

	/*
	 * A short description for tooltips: the configured one, or the effects it gives
	 */
	public String summary() {
		if(!description.isEmpty()) {
			return description;
		}
		StringBuilder b = new StringBuilder();
		for(int[] e : effects) {
			if(b.length() > 0) b.append(", ");
			b.append(effectName(e));
		}
		return b.toString();
	}

	public static String effectName(int[] effect) {
		String name = StatCollector.translateToLocal(Potion.potionTypes[effect[0]].getName());
		return effect[1] > 0 ? name + " " + StatCollector.translateToLocal("potion.potency." + effect[1]).trim() : name;
	}
}
