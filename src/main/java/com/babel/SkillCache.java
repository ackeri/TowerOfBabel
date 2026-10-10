package com.babel;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.TowerOfBabel;
import com.tower.Skill;
import com.tower.TowerData;

/*
 * Answers "can this player do this action with this item", server side only.
 * A player's grants are rebuilt whenever their skills change or they switch dimension.
 */
public class SkillCache {
	private static class Perms {
		final int dim;
		final List<Grant> grants;
		// registry name -> bit list of allowed actions
		final Map<String, Integer> memo = new HashMap<String, Integer>();

		Perms(int dim, List<Grant> grants) {
			this.dim = dim;
			this.grants = grants;
		}
	}

	private static final Map<UUID, Perms> cache = new HashMap<UUID, Perms>();

	/*
	 * Checks if player p can do action a with the item/block named name
	 */
	public static boolean can(EntityPlayer p, Actions a, String name) {
		return can(p.getUniqueID(), p.dimension, a, name);
	}

	/*
	 * Checks if player id, while in dimension dim, can do action a with the item/block named name
	 */
	public static boolean can(UUID id, int dim, Actions a, String name) {
		if(name == null) {
			//unregistered things can't be configured, so don't block them
			return true;
		}

		Perms perms = cache.get(id);
		if(perms == null || perms.dim != dim) {
			perms = new Perms(dim, collectGrants(id, dim));
			cache.put(id, perms);
		}

		Integer allowed = perms.memo.get(name);
		if(allowed == null) {
			allowed = 0;
			for(Grant g : perms.grants) {
				if(g.pattern.matcher(name).matches()) {
					allowed |= g.actions;
				}
			}
			perms.memo.put(name, allowed);
		}
		return (allowed & a.bit()) != 0;
	}

	private static List<Grant> collectGrants(UUID id, int dim) {
		List<Grant> grants = new ArrayList<Grant>(TowerOfBabel.defaultGrants);
		TowerData data = TowerData.get();

		for(String name : data.getAdminSkills(id)) {
			Skill s = TowerOfBabel.skills.get(name);
			if(s != null) {
				grants.addAll(s.grants);
			}
		}

		TowerData.Tower t = data.getTower(id);
		if(t != null) {
			for(String name : t.skills) {
				Skill s = TowerOfBabel.skills.get(name);
				if(s != null && (!s.dimensionSpecific || t.dim == dim)) {
					grants.addAll(s.grants);
				}
			}
		}
		return grants;
	}

	public static void invalidate(UUID id) {
		cache.remove(id);
	}

	public static void clear() {
		cache.clear();
	}

	public static String nameOf(ItemStack i) {
		return (String)Item.itemRegistry.getNameForObject(i.getItem());
	}

	public static String nameOf(Block b) {
		return (String)Block.blockRegistry.getNameForObject(b);
	}
}
