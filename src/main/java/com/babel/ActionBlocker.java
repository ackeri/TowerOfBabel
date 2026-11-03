package com.babel;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.CraftingManager;
import com.TowerOfBabel;

/*
 * Stops players from doing things with items they don't have the skills for.
 * The server decides. The client is sent the same grants and cancels the same actions itself, so it doesn't show
 * blocks breaking, being placed or items being crafted that the server then puts back.
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
	// shared by the client and integrated server threads in singleplayer
	private static Map<UUID, String> lastMessage = new ConcurrentHashMap<UUID, String>();
	private static Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<UUID, Long>();

	private static void setTag(UUID pid, ItemStack is) {
		if(is.stackTagCompound == null) {
			is.setTagCompound(new NBTTagCompound());
		}
		is.stackTagCompound.setString(OWNER_TAG, pid.toString());
	}

	@SubscribeEvent
	public void login(PlayerLoggedInEvent e) {
		if(e.player instanceof EntityPlayerMP) {
			SkillCache.sync((EntityPlayerMP)e.player);
		}
	}

	@SubscribeEvent
	public void changedDimension(PlayerChangedDimensionEvent e) {
		//dimension specific skills may have turned on or off
		if(e.player instanceof EntityPlayerMP) {
			SkillCache.sync((EntityPlayerMP)e.player);
		}
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

	/*
	 * Mining a block takes no time if the held item can't break blocks, so neither side ever finishes breaking it
	 */
	@SubscribeEvent
	public void breakSpeed(BreakSpeed e) {
		ItemStack i = e.entityPlayer.getHeldItem();
		if(i != null && denied(e.entityPlayer, Actions.BREAK, SkillCache.nameOf(i))) {
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void attempt(PlayerInteractEvent e) {
		EntityPlayer p = e.entityPlayer;
		ItemStack i = p.getHeldItem();

		//Break (only fired on the server)
		if(e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
			if(shouldCancel(p, Actions.BREAK, i)) {
				e.setCanceled(true);
			}
			return;
		}

		if(p.worldObj.isRemote) {
			attemptClient(e, p, i);
			return;
		}

		//Place and Use
		if(i != null) {
			if(shouldCancel(p, useAction(i), i)) {
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

	/*
	 * The client can only cancel a right click entirely, which also stops it reaching the server.
	 * So cancel it when the server would let nothing happen, and otherwise let the server sort it out.
	 */
	private void attemptClient(PlayerInteractEvent e, EntityPlayer p, ItemStack i) {
		boolean itemDenied = i != null && denied(p, useAction(i), SkillCache.nameOf(i));

		if(e.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
			if(itemDenied) {
				cancelClient(e, p, useAction(i), i.getDisplayName());
			}
			return;
		}

		Block b = p.worldObj.getBlock(e.x, e.y, e.z);
		boolean blockDenied = denied(p, Actions.INTERACT, SkillCache.nameOf(b));
		//vanilla skips activating the block when sneaking with an item in hand
		boolean blockCanReact = (!p.isSneaking() || i == null) && hasActivation(b);

		if(blockDenied && (i == null || itemDenied)) {
			cancelClient(e, p, Actions.INTERACT, b.getLocalizedName());
		} else if(itemDenied && (blockDenied || !blockCanReact)) {
			cancelClient(e, p, useAction(i), i.getDisplayName());
		}
	}

	private void cancelClient(PlayerInteractEvent e, EntityPlayer p, Actions a, String displayName) {
		e.setCanceled(true);
		//the server never hears about this click, so it can't send the message
		tell(p, "Cannot " + a + " " + displayName);
	}

	private static Actions useAction(ItemStack i) {
		return i.getItem() instanceof ItemBlock ? Actions.PLACE : Actions.USE;
	}

	private static Map<Class<?>, Boolean> activation = new ConcurrentHashMap<Class<?>, Boolean>();

	/*
	 * Whether a block does anything when right clicked, judged by whether it overrides onBlockActivated
	 */
	private static boolean hasActivation(Block b) {
		Boolean has = activation.get(b.getClass());
		if(has == null) {
			has = false;
			for(Class<?> c = b.getClass(); c != Block.class && c != null && !has; c = c.getSuperclass()) {
				for(Method m : c.getDeclaredMethods()) {
					if((m.getName().equals("onBlockActivated") || m.getName().equals("func_149727_a")) && m.getParameterTypes().length == 9) {
						has = true;
						break;
					}
				}
			}
			activation.put(b.getClass(), has);
		}
		return has;
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

	/*
	 * Whether p may not do a with the thing named name. On the client this is only known for the local player.
	 */
	public static boolean denied(EntityPlayer p, Actions a, String name) {
		if(p instanceof FakePlayer) {
			return false;
		}
		if(p.worldObj.isRemote) {
			return TowerOfBabel.proxy.isLocalPlayer(p) && !SkillCache.canClient(a, name);
		}
		return p instanceof EntityPlayerMP && !SkillCache.can(p, a, name);
	}

	/*
	 * Checks an action that both sides see. The server tells the player why; the client stays quiet so the
	 * message isn't shown twice.
	 */
	public static boolean shouldCancel(EntityPlayer p, Actions a, ItemStack i) {
		return i != null && shouldCancel(p, a, SkillCache.nameOf(i), i.getDisplayName());
	}

	public static boolean shouldCancel(EntityPlayer p, Actions a, Block b) {
		return shouldCancel(p, a, SkillCache.nameOf(b), b.getLocalizedName());
	}

	private static boolean shouldCancel(EntityPlayer p, Actions a, String name, String displayName) {
		if(!denied(p, a, name)) {
			return false;
		}
		if(!p.worldObj.isRemote) {
			tell(p, "Cannot " + a + " " + displayName);
			if(a == Actions.USE || a == Actions.PLACE || a == Actions.INTERACT) {
				//the client may have predicted the action happening, so put its inventory back
				((EntityPlayerMP)p).sendContainerToPlayer(p.inventoryContainer);
			}
		}
		return true;
	}

	/*
	 * Sends a chat message, without repeating the same one every tick while a button is held
	 */
	public static void tell(EntityPlayer p, String mes) {
		UUID id = p.getUniqueID();
		long now = System.currentTimeMillis();
		Long last = lastMessageTime.get(id);
		if(mes.equals(lastMessage.get(id)) && last != null && now - last < 2000) {
			return;
		}
		lastMessage.put(id, mes);
		lastMessageTime.put(id, now);
		p.addChatComponentMessage(new ChatComponentText(mes));
	}
}
