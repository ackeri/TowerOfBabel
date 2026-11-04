package com.tower.client;

import net.minecraft.block.Block;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import com.TowerOfBabel;
import com.babel.ActionBlocker;
import com.babel.Actions;
import com.babel.SkillCache;
import com.tower.Skill;
import com.tower.TowerBase;

/*
 * Explains on item tooltips what a block does in a tower, and what the player isn't allowed to do with an item
 */
@SideOnly(Side.CLIENT)
public class Tooltips {
	public static void register() {
		MinecraftForge.EVENT_BUS.register(new Tooltips());
	}

	@SubscribeEvent
	public void tooltip(ItemTooltipEvent e) {
		if(e.itemStack == null || e.itemStack.getItem() == null) return;
		List<String> tip = e.toolTip;

		Block block = Block.getBlockFromItem(e.itemStack.getItem());
		if(block instanceof TowerBase) {
			tip.add(EnumChatFormatting.GRAY + "Place to claim a tower; stack skill blocks on top");
			if(TowerOfBabel.towerCapacity > 0) {
				tip.add(EnumChatFormatting.GRAY + "A tower holds up to " + TowerOfBabel.towerCapacity + " skills");
			}
		}
		if(block != null) {
			for(Skill s : TowerOfBabel.skills.values()) {
				if(!s.matches(block, e.itemStack.getItemDamage())) continue;
				tip.add(EnumChatFormatting.AQUA + "Tower skill: " + s.name);
				String summary = s.summary();
				if(!summary.isEmpty()) {
					tip.add(EnumChatFormatting.GRAY + "  " + summary);
				}
				if(!s.prereqs.isEmpty()) {
					tip.add(EnumChatFormatting.GRAY + "  Needs lower in the tower: " + join(new ArrayList<String>(s.prereqs)));
				}
				if(s.heights.length > 0) {
					List<String> levels = new ArrayList<String>();
					for(int h : s.heights) levels.add(String.valueOf(h));
					tip.add(EnumChatFormatting.GRAY + "  Only counts at level " + join(levels));
				}
				if(s.seats > 0) {
					tip.add(EnumChatFormatting.GRAY + "  Only " + s.seats + " player" + (s.seats == 1 ? "" : "s") + " can hold this at once");
				}
				if(!s.excludes.isEmpty()) {
					tip.add(EnumChatFormatting.GRAY + "  Can't share a tower with: " + join(new ArrayList<String>(s.excludes)));
				}
				if(s.dimensionSpecific) {
					tip.add(EnumChatFormatting.GRAY + "  Only works in the tower's dimension");
				}
			}
		}

		//what the local player can't do with this item
		String name = SkillCache.nameOf(e.itemStack);
		List<String> denied = new ArrayList<String>();
		Actions first = null;
		for(Actions a : Actions.values()) {
			if(!SkillCache.canClient(a, name)) {
				denied.add(a.name().toLowerCase());
				if(first == null) first = a;
			}
		}
		if(denied.size() == Actions.values().length) {
			tip.add(EnumChatFormatting.RED + "Locked" + ActionBlocker.hint(first, name));
		} else if(first != null) {
			tip.add(EnumChatFormatting.RED + "Can't " + join(denied) + " this" + ActionBlocker.hint(first, name));
		}
	}

	private static String join(List<String> parts) {
		StringBuilder b = new StringBuilder();
		for(int i = 0; i < parts.size(); i++) {
			if(i > 0) b.append(i == parts.size() - 1 ? " or " : ", ");
			b.append(parts.get(i));
		}
		return b.toString();
	}
}
