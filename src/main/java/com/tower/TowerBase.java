package com.tower;

import net.minecraft.world.World;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.UUID;
import com.TowerOfBabel;

/*
 * The base of a skill tower. Whoever places it owns it, and every skill block stacked on top applies to them.
 * Each player can own one tower at a time.
 */
public class TowerBase extends BlockContainer {
	public TowerBase() {
		super(Material.rock);
		this.setBlockName("TowerBase");
		this.setBlockTextureName(TowerOfBabel.MODID.toLowerCase() + ":tower_base");
		this.setCreativeTab(CreativeTabs.tabMisc);
		this.setHardness(5.0f);
		this.setResistance(30.0f);
		this.setStepSound(soundTypePiston);
		this.setHarvestLevel("pickaxe", 2);
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new TowerBaseEntity();
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack is) {
		//only remember who placed it here; the tower is claimed in claim() once nothing cancelled the placement
		TileEntity te = world.getTileEntity(x, y, z);
		if(!world.isRemote && placer instanceof EntityPlayer && te instanceof TowerBaseEntity) {
			((TowerBaseEntity)te).setOwner((EntityPlayer)placer);
		}
	}

	@SubscribeEvent
	public void limitOne(BlockEvent.PlaceEvent e) {
		if(e.placedBlock != this || e.world.isRemote) return;

		TowerData.Tower old = TowerData.get().getTower(e.player.getUniqueID());
		if(old != null && stillExists(old, e.player.getUniqueID())) {
			e.player.addChatComponentMessage(new ChatComponentText("You already have a tower at " + old.describe()));
			e.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void claim(BlockEvent.PlaceEvent e) {
		if(e.placedBlock != this || e.world.isRemote || e.isCanceled()) return;

		UUID owner = e.player.getUniqueID();
		TowerData.get().setTower(owner, new TowerData.Tower(e.world.provider.dimensionId, e.x, e.y, e.z));
		e.player.addChatComponentMessage(new ChatComponentText("You now own this tower. Stack skill blocks on it to learn skills."));
	}

	/*
	 * Checks a recorded tower is still there, in case it vanished without being broken (world edits, other mods)
	 */
	private boolean stillExists(TowerData.Tower t, UUID owner) {
		World w = DimensionManager.getWorld(t.dim);
		if(w == null) {
			//dimension not loaded, so trust the record
			return true;
		}
		TileEntity te = w.getTileEntity(t.x, t.y, t.z);
		return te instanceof TowerBaseEntity && owner.equals(((TowerBaseEntity)te).getOwner());
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block b, int m) {
		TileEntity te = world.getTileEntity(x, y, z);
		if(!world.isRemote && te instanceof TowerBaseEntity) {
			UUID owner = ((TowerBaseEntity)te).getOwner();
			if(owner != null) {
				TowerData data = TowerData.get();
				TowerData.Tower t = data.getTower(owner);
				if(t != null && t.isAt(world.provider.dimensionId, x, y, z)) {
					data.removeTower(owner, world.provider.dimensionId, x, y, z);
					EntityPlayerMP p = TowerData.findPlayer(owner);
					if(p != null) {
						p.addChatComponentMessage(new ChatComponentText("Your tower was destroyed. You lost its skills."));
					}
				}
			}
		}
		super.breakBlock(world, x, y, z, b, m);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer p, int side, float hx, float hy, float hz) {
		//let players build onto the tower while holding blocks; only report with an empty hand
		if(p.getHeldItem() != null) {
			return false;
		}
		if(!world.isRemote) {
			TileEntity te = world.getTileEntity(x, y, z);
			if(te instanceof TowerBaseEntity) {
				p.addChatComponentMessage(new ChatComponentText(((TowerBaseEntity)te).describe()));
			}
		}
		return true;
	}
}
