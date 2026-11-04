package com.tower;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.TowerOfBabel;

/*
 * Watches the column of blocks above a tower base and keeps its owner's skills in sync with it
 */
public class TowerBaseEntity extends TileEntity {
	private UUID owner;
	private String ownerName = "";
	private List<String> skills = new ArrayList<String>();
	// tower levels holding a skill block that counts, for the client's particles
	private List<Integer> levels = new ArrayList<Integer>();
	// whether the last scan found skill blocks past the tower's capacity, so the owner is told once
	private boolean full;
	private boolean scanFull;
	private TowerData.Capacity capacity;

	public UUID getOwner() {
		return owner;
	}

	public void setOwner(EntityPlayer p) {
		owner = p.getUniqueID();
		ownerName = p.getCommandSenderName();
		markDirty();
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote || owner == null || worldObj.getTotalWorldTime() % 10 != 0) return;

		List<Integer> nowLevels = new ArrayList<Integer>();
		List<String> now = scan(nowLevels);
		if(scanFull && !full) {
			EntityPlayerMP p = TowerData.findPlayer(owner);
			if(p != null) {
				String why = capacity.limitedBy != null
						? "it can't be more than " + TowerOfBabel.towerVariance + " skills taller than " + TowerData.nameOf(capacity.limitedBy) + "'s"
						: "only " + capacity.limit + " skills count";
				p.addChatComponentMessage(new ChatComponentText("Your tower is full: " + why + ", so totems above that do nothing for now"));
			}
		}
		full = scanFull;
		if(!nowLevels.equals(levels)) {
			levels = nowLevels;
			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
		if(now.equals(skills)) return;

		List<String> before = skills;
		skills = now;
		markDirty();
		if(!TowerData.get().setTowerSkills(owner, worldObj.provider.dimensionId, xCoord, yCoord, zCoord, now)) {
			//placement was cancelled, or the owner has since claimed another tower
			return;
		}

		EntityPlayerMP p = TowerData.findPlayer(owner);
		if(p != null) {
			for(String s : now) {
				if(!before.contains(s)) {
					p.addChatComponentMessage(new ChatComponentText("Your tower grants you " + s));
				}
			}
			for(String s : before) {
				if(!now.contains(s)) {
					p.addChatComponentMessage(new ChatComponentText("Your tower no longer grants you " + s));
				}
			}
		}
	}

	/*
	 * Reads skill blocks upward from the base until something that isn't a skill block.
	 * A skill only counts at its allowed heights and once its prerequisites are lower in the tower.
	 */
	public List<String> scan(List<Integer> levelsOut) {
		List<String> found = new ArrayList<String>();
		Set<String> have = new HashSet<String>();
		scanFull = false;
		capacity = TowerData.get().capacityFor(owner);

		for(int level = 1; level <= TowerOfBabel.maxTowerHeight && yCoord + level < worldObj.getHeight(); level++) {
			Block b = worldObj.getBlock(xCoord, yCoord + level, zCoord);
			int meta = worldObj.getBlockMetadata(xCoord, yCoord + level, zCoord);

			Skill skill = null;
			for(Skill s : TowerOfBabel.skills.values()) {
				if(s.matches(b, meta)) {
					skill = s;
					break;
				}
			}
			if(skill == null) {
				break;
			}

			boolean atCapacity = capacity.limit >= 0 && found.size() >= capacity.limit;
			if(atCapacity && !have.contains(skill.name)) {
				scanFull = true;
			}
			if(!atCapacity && skill.allowedAt(level) && have.containsAll(skill.prereqs) && !skill.excludedBy(have) && have.add(skill.name)) {
				found.add(skill.name);
				levelsOut.add(level);
			}
		}
		return found;
	}

	public String describe() {
		if(owner == null) {
			return "This tower has no owner";
		}
		String room = capacity != null && capacity.limit >= 0 ? " (" + skills.size() + "/" + capacity.limit + " skills)" : "";
		return ownerName + "'s tower grants: " + (skills.isEmpty() ? "nothing yet" : String.join(", ", skills)) + room;
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		if(owner != null) {
			tag.setString("owner", owner.toString());
			tag.setString("ownerName", ownerName);
		}
		tag.setTag("skills", TowerData.writeStrings(skills));
		int[] l = new int[levels.size()];
		for(int i = 0; i < l.length; i++) l[i] = levels.get(i);
		tag.setIntArray("levels", l);
	}

	public List<Integer> getLevels() {
		return levels;
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound tag = new NBTTagCompound();
		writeToNBT(tag);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		readFromNBT(pkt.func_148857_g());
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		owner = tag.hasKey("owner") ? UUID.fromString(tag.getString("owner")) : null;
		ownerName = tag.getString("ownerName");
		skills = TowerData.readStrings(tag.getTagList("skills", 8));
		levels = new ArrayList<Integer>();
		for(int l : tag.getIntArray("levels")) levels.add(l);
	}
}
