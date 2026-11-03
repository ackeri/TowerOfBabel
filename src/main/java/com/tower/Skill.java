package com.tower;

import net.minecraft.block.Block;
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

	public Skill(String name, Block block, int meta, Set<String> prereqs, int[] heights, List<Grant> grants, boolean dimensionSpecific) {
		this.name = name;
		this.block = block;
		this.meta = meta;
		this.prereqs = prereqs;
		this.heights = heights;
		this.grants = grants;
		this.dimensionSpecific = dimensionSpecific;
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
		return new Skill(name, block, meta, new HashSet<String>(Arrays.asList(prereqs)), heights, parsed, dimSpecific);
	}
}
