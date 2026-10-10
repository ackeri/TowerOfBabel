package com.babel;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.Event.Result;

import net.minecraft.block.Block;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.inventory.Container;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.CraftingManager;
import com.TowerOfBabel;

/*
 * Stops players from doing things with items they don't have the skills for.
 * All checks run on the server, which is authoritative; the client is resynced when it guessed wrong.
 */
public class ActionBlocker {
	public static final String OWNER_TAG = "TowerUser";

	public static void register() {
		ActionBlocker ab = new ActionBlocker();
		MinecraftForge.EVENT_BUS.register(ab);
		FMLCommonHandler.instance().bus().register(ab);
	}

	/*
	 * Wraps every crafting recipe so crafting checks skills. Safe to call more than once, which picks up
	 * recipes other mods (or MineTweaker scripts) added since the last call.
	 */
	public static void wrapRecipes() {
		List<IRecipe> rec = CraftingManager.getInstance().getRecipeList();
		for(int i = 0; i < rec.size(); i++) {
			if(!(rec.get(i) instanceof RecipeWrapper)) {
				rec.set(i, new RecipeWrapper(rec.get(i)));
			}
		}
	}

	private Map<UUID, Container> lastSeen = new HashMap<UUID, Container>();
	private static Map<UUID, String> lastMessage = new HashMap<UUID, String>();
	private static Map<UUID, Long> lastMessageTime = new HashMap<UUID, Long>();

	private static void setTag(UUID pid, ItemStack is) {
		if(is.stackTagCompound == null) {
			is.setTagCompound(new NBTTagCompound());
		}
		is.stackTagCompound.setString(OWNER_TAG, pid.toString());
	}

	@SubscribeEvent
	public void pickup(EntityItemPickupEvent e) {
		ItemStack is = e.item.getEntityItem();
		if(shouldCancel(e.entityPlayer, Actions.CARRY, is)) {
			e.setCanceled(true);
		} else if(TowerOfBabel.tagItemOwners && !e.entityPlayer.worldObj.isRemote) {
			setTag(e.entityPlayer.getUniqueID(), is);
		}
	}

	@SubscribeEvent
	public void markContainer(PlayerOpenContainerEvent e) {
		EntityPlayer p = e.entityPlayer;
		if(!TowerOfBabel.tagItemOwners || p.worldObj.isRemote) return;

		UUID pid = p.getUniqueID();
		Container c = p.openContainer;
		if(c != lastSeen.get(pid)) {
			lastSeen.put(pid, c);

			for(int slot = 0; slot < c.getInventory().size(); slot++) {
				ItemStack is = ((ItemStack)c.getInventory().get(slot));
				if(is != null) {
					setTag(pid, is);
				}
			}
		}
	}

	@SubscribeEvent
	public void attempt(PlayerInteractEvent e) {
		EntityPlayer p = e.entityPlayer;
		ItemStack i = p.getHeldItem();

		//Break
		if(e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
			if(shouldCancel(p, Actions.BREAK, i)) {
				e.setCanceled(true);
			}
			return;
		}

		//Place and Use
		if(i != null) {
			Actions a = i.getItem() instanceof ItemBlock ? Actions.PLACE : Actions.USE;
			if(shouldCancel(p, a, i)) {
				e.useItem = Result.DENY;
			}
		}

		//Interact
		if(e.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
			if(shouldCancel(p, Actions.INTERACT, p.worldObj.getBlock(e.x, e.y, e.z))) {
				e.useBlock = Result.DENY;
			}
		}
	}

	@SubscribeEvent
	public void attemptEntity(EntityInteractEvent e) {
		if(shouldCancel(e.entityPlayer, Actions.USE, e.entityPlayer.getHeldItem())) {
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void attack(AttackEntityEvent e) {
		if(shouldCancel(e.entityPlayer, Actions.ATTACK, e.entityPlayer.getHeldItem())) {
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void wear(PlayerTickEvent e) {
		EntityPlayer p = e.player;
		World w = p.worldObj;
		if(w.isRemote || e.phase != TickEvent.Phase.END || p.ticksExisted % 20 != 0) return;

		//armor
		ItemStack[] armor = p.inventory.armorInventory;
		for(int i = 0; i < armor.length; i++) {
			armor[i] = droppingCheck(w,p,Actions.WEAR,armor[i]);
		}
		//baubles
		//TODO
		//held item
		InventoryPlayer ip = p.inventory;
		ip.setInventorySlotContents(ip.currentItem, droppingCheck(w,p,Actions.HOLD,p.getHeldItem()));

		//carry
		for(int slot = 0; slot < ip.mainInventory.length; slot++) {
			ip.setInventorySlotContents(slot, droppingCheck(w,p,Actions.CARRY,ip.getStackInSlot(slot)));
		}
	}

	@SubscribeEvent
	public void logout(PlayerLoggedOutEvent e) {
		UUID id = e.player.getUniqueID();
		lastSeen.remove(id);
		lastMessage.remove(id);
		lastMessageTime.remove(id);
		SkillCache.invalidate(id);
	}

	private ItemStack droppingCheck(World w, EntityPlayer p, Actions a, ItemStack i) {
		if(shouldCancel(p, a, i)) {
			EntityItem item = new EntityItem(w,p.posX,p.posY+1,p.posZ,i);
			item.delayBeforeCanPickup = 40;
			w.spawnEntityInWorld(item);
			return null;
		}
		return i;
	}

	public static boolean shouldCancel(EntityPlayer p, Actions a, ItemStack i) {
		return i != null && shouldCancel(p, a, SkillCache.nameOf(i), i.getDisplayName());
	}

	public static boolean shouldCancel(EntityPlayer p, Actions a, Block b) {
		return shouldCancel(p, a, SkillCache.nameOf(b), b.getLocalizedName());
	}

	private static boolean shouldCancel(EntityPlayer p, Actions a, String name, String displayName) {
		//the server decides; the client doesn't know other players' skills on a dedicated server
		if(p.worldObj.isRemote || !(p instanceof EntityPlayerMP) || p instanceof FakePlayer) {
			return false;
		}
		if(SkillCache.can(p, a, name)) {
			return false;
		}

		tell(p, "Cannot " + a + " " + displayName);
		if(a == Actions.USE || a == Actions.PLACE || a == Actions.INTERACT) {
			//the client already predicted the action happening, so put its inventory back
			((EntityPlayerMP)p).sendContainerToPlayer(p.inventoryContainer);
		}
		return true;
	}

	/*
	 * Sends a chat message, without repeating the same one every tick while a button is held
	 */
	public static void tell(EntityPlayer p, String mes) {
		UUID id = p.getUniqueID();
		long now = p.worldObj.getTotalWorldTime();
		Long last = lastMessageTime.get(id);
		if(mes.equals(lastMessage.get(id)) && last != null && now - last < 40) {
			return;
		}
		lastMessage.put(id, mes);
		lastMessageTime.put(id, now);
		p.addChatComponentMessage(new ChatComponentText(mes));
	}
}
