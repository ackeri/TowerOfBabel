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
	// skills this tower has the totem for but whose seats are all taken, so the owner is told once
	private Set<String> waiting = new HashSet<String>();
	private Set<String> scanWaiting = new HashSet<String>();

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
				p.addChatComponentMessage(new ChatComponentText("Your tower is full: only " + TowerOfBabel.towerCapacity
						+ " skills count, so totems above that do nothing"));
			}
		}
		full = scanFull;
		for(String s : scanWaiting) {
			if(!waiting.contains(s)) {
				EntityPlayerMP p = TowerData.findPlayer(owner);
				if(p != null) {
					List<String> names = new ArrayList<String>();
					for(UUID id : TowerData.get().towerHoldersOf(s)) names.add(TowerData.nameOf(id));
					p.addChatComponentMessage(new ChatComponentText("Every seat of " + s + " is taken (" + String.join(", ", names)
							+ "); your totem will work when one frees up"));
				}
			}
		}
		waiting = scanWaiting;
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
		scanWaiting = new HashSet<String>();

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

			boolean atCapacity = TowerOfBabel.towerCapacity > 0 && found.size() >= TowerOfBabel.towerCapacity;
			if(atCapacity && !have.contains(skill.name)) {
				scanFull = true;
			}
			boolean eligible = !atCapacity && !have.contains(skill.name) && skill.allowedAt(level)
					&& have.containsAll(skill.prereqs) && !skill.excludedBy(have);
			if(eligible && !seatFree(skill)) {
				scanWaiting.add(skill.name);
			} else if(eligible) {
				have.add(skill.name);
				found.add(skill.name);
				levelsOut.add(level);
			}
		}
		return found;
	}

	/*
	 * Whether this tower's owner may hold a skill with limited seats. Only towers take seats (skills given by
	 * command don't). Someone already holding the skill keeps it while fewer than seats other holders rank ahead of
	 * them, so lowering seats in the config drops the same holders every time instead of flickering; anyone new
	 * waits until fewer than seats others hold it.
	 */
	private boolean seatFree(Skill skill) {
		if(skill.seats <= 0) {
			return true;
		}
		TowerData data = TowerData.get();
		List<UUID> others = data.towerHoldersOf(skill.name);
		boolean holding = others.remove(owner);
		if(!holding) {
			return others.size() < skill.seats;
		}
		int ahead = 0;
		for(UUID id : others) {
			if(id.toString().compareTo(owner.toString()) < 0) {
				ahead++;
			}
		}
		return ahead < skill.seats;
	}

	public String describe() {
		if(owner == null) {
			return "This tower has no owner";
		}
		String room = TowerOfBabel.towerCapacity > 0 ? " (" + skills.size() + "/" + TowerOfBabel.towerCapacity + " skills)" : "";
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
