package com.tower;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.babel.SkillCache;

/*
 * Keeps the potion effects of a player's active skills on them, like a beacon does
 */
public class SkillEffects {
	// long enough that night vision never starts flickering between refreshes
	private static final int DURATION = 300;
	private static final int REFRESH = 20;

	// potions this applied to each player last time, so they can be taken away when the skill is lost
	private final Map<UUID, Set<Integer>> applied = new HashMap<UUID, Set<Integer>>();

	public static void register() {
		FMLCommonHandler.instance().bus().register(new SkillEffects());
	}

	@SubscribeEvent
	public void tick(PlayerTickEvent e) {
		EntityPlayer p = e.player;
		if(p.worldObj.isRemote || e.phase != TickEvent.Phase.END || p.ticksExisted % REFRESH != 0) return;

		Set<Integer> now = new HashSet<Integer>();
		for(Skill s : SkillCache.activeSkills(p.getUniqueID(), p.dimension)) {
			for(int[] effect : s.effects) {
				now.add(effect[0]);
				p.addPotionEffect(new PotionEffect(effect[0], DURATION, effect[1], true));
			}
		}

		Set<Integer> before = applied.put(p.getUniqueID(), now);
		if(before != null) {
			for(int id : before) {
				PotionEffect active = p.getActivePotionEffect(Potion.potionTypes[id]);
				//only remove what this put there, not a potion the player drank
				if(!now.contains(id) && active != null && active.getIsAmbient() && active.getDuration() <= DURATION) {
					p.removePotionEffect(id);
				}
			}
		}
	}

	@SubscribeEvent
	public void logout(PlayerLoggedOutEvent e) {
		applied.remove(e.player.getUniqueID());
	}
}
