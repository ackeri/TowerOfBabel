package com.babel;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.inventory.Container;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.util.List;
import java.util.UUID;
import com.TowerOfBabel;

/*
 * Wraps a crafting recipe so it only matches for players allowed to craft its output
 */
public class RecipeWrapper implements IRecipe {
	private IRecipe recipe;

	public RecipeWrapper(IRecipe rec) {
		recipe = rec;
	}

	public IRecipe getWrapped() {
		return recipe;
	}

	public ItemStack getCraftingResult(InventoryCrafting ic) {
		return recipe.getCraftingResult(ic);
	}

	public ItemStack getRecipeOutput() {
		return recipe.getRecipeOutput();
	}

	public int getRecipeSize() {
		return recipe.getRecipeSize();
	}

	public boolean matches(InventoryCrafting ic, World w) {
		if(!recipe.matches(ic, w)) {
			return false;
		}
		if(w == null) {
			return true;
		}

		ItemStack out = recipe.getRecipeOutput();
		if(out == null) {
			out = recipe.getCraftingResult(ic);
		}
		if(out == null || out.getItem() == null) {
			return true;
		}

		//both sides check, so the client doesn't show an output the server won't give
		EntityPlayer crafter = findCrafter(ic, w);
		if(crafter != null) {
			return !ActionBlocker.shouldCancel(crafter, Actions.CRAFT, out);
		}

		//no player is looking at this crafting grid, so it is automation
		if(w.isRemote || !TowerOfBabel.tagItemOwners) {
			return true;
		}
		return taggedOwnerCan(ic, w, SkillCache.nameOf(out));
	}

	/*
	 * Allows crafting if any ingredient was last handled by a player who can craft the output
	 */
	private boolean taggedOwnerCan(InventoryCrafting ic, World w, String name) {
		for(int slot = 0; slot < ic.getSizeInventory(); slot++) {
			ItemStack is = ic.getStackInSlot(slot);
			if(is != null && is.stackTagCompound != null && is.stackTagCompound.hasKey(ActionBlocker.OWNER_TAG)) {
				try {
					UUID owner = UUID.fromString(is.stackTagCompound.getString(ActionBlocker.OWNER_TAG));
					if(SkillCache.can(owner, w.provider.dimensionId, Actions.CRAFT, name)) {
						return true;
					}
				} catch(IllegalArgumentException ex) {
					//not a tag we wrote
				}
			}
		}
		return false;
	}

	private EntityPlayer findCrafter(InventoryCrafting ic, World w) {
		Container cont;
		try {
			cont = ReflectionHelper.getPrivateValue(InventoryCrafting.class, ic, "eventHandler", "field_70465_c");
		} catch(Exception ex) {
			return null;
		}
		if(cont == null) {
			return null;
		}

		List<EntityPlayer> ps = w.playerEntities;
		for(EntityPlayer p : ps) {
			if(p.openContainer == cont) {
				return p;
			}
		}
		return null;
	}
}
