package com.tower;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
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

		List<String> now = scan();
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
	public List<String> scan() {
		List<String> found = new ArrayList<String>();
		Set<String> have = new HashSet<String>();

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

			if(skill.allowedAt(level) && have.containsAll(skill.prereqs) && have.add(skill.name)) {
				found.add(skill.name);
			}
		}
		return found;
	}

	public String describe() {
		if(owner == null) {
			return "This tower has no owner";
		}
		return ownerName + "'s tower grants: " + (skills.isEmpty() ? "nothing yet" : String.join(", ", skills));
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		if(owner != null) {
			tag.setString("owner", owner.toString());
			tag.setString("ownerName", ownerName);
		}
		tag.setTag("skills", TowerData.writeStrings(skills));
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		owner = tag.hasKey("owner") ? UUID.fromString(tag.getString("owner")) : null;
		ownerName = tag.getString("ownerName");
		skills = TowerData.readStrings(tag.getTagList("skills", 8));
	}
}
