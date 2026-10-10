package com.babel;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
 * Answers "can this player do this action with this item".
 * The server works this out for every player, rebuilding a player's grants whenever their skills change or they
 * switch dimension, and sends each player their own grants so the client can give the same answers.
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

		boolean allows(Actions a, String name) {
			Integer allowed = memo.get(name);
			if(allowed == null) {
				allowed = 0;
				for(Grant g : grants) {
					if(g.pattern.matcher(name).matches()) {
						allowed |= g.actions;
					}
				}
				memo.put(name, allowed);
			}
			return (allowed & a.bit()) != 0;
		}
	}

	// server thread only
	private static final Map<UUID, Perms> cache = new HashMap<UUID, Perms>();
	// the local player's grants as last sent by the server, null until then
	private static volatile Perms clientPerms;

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

		return perms.allows(a, name);
	}

	/*
	 * Client side version of can for the local player. Allows everything until the server has sent grants,
	 * leaving the server to decide.
	 */
	public static boolean canClient(Actions a, String name) {
		Perms perms = clientPerms;
		return perms == null || name == null || perms.allows(a, name);
	}

	public static void setClientGrants(List<Grant> grants) {
		clientPerms = grants == null ? null : new Perms(0, grants);
	}

	/*
	 * Sends p their current grants
	 */
	public static void sync(EntityPlayerMP p) {
		TowerOfBabel.network.sendTo(new GrantSync(collectGrants(p.getUniqueID(), p.dimension)), p);
	}

	/*
	 * Called when a player's skills change
	 */
	public static void changed(UUID id) {
		invalidate(id);
		EntityPlayerMP p = TowerData.findPlayer(id);
		if(p != null) {
			sync(p);
		}
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
