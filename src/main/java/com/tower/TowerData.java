package com.tower;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.babel.SkillCache;

/*
 * Server-wide record of who owns which tower and which skills each player has, saved with the world
 * so skills keep applying while a tower's chunk is unloaded or its owner is offline.
 */
public class TowerData extends WorldSavedData {
	public static final String NAME = "TowerOfBabel";

	public static class Tower {
		public final int dim, x, y, z;
		public List<String> skills = new ArrayList<String>();

		public Tower(int dim, int x, int y, int z) {
			this.dim = dim;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public boolean isAt(int dim, int x, int y, int z) {
			return this.dim == dim && this.x == x && this.y == y && this.z == z;
		}

		public String describe() {
			return "(" + x + ", " + y + ", " + z + ") in dimension " + dim;
		}
	}

	private final Map<UUID, Tower> towers = new HashMap<UUID, Tower>();
	// skills given by the /tower command
	private final Map<UUID, Set<String>> adminSkills = new HashMap<UUID, Set<String>>();

	public TowerData(String name) {
		super(name);
	}

	public static TowerData get() {
		World w = MinecraftServer.getServer().worldServerForDimension(0);
		TowerData data = (TowerData)w.mapStorage.loadData(TowerData.class, NAME);
		if(data == null) {
			data = new TowerData(NAME);
			w.mapStorage.setData(NAME, data);
		}
		return data;
	}

	public static EntityPlayerMP findPlayer(UUID id) {
		for(Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
			EntityPlayerMP p = (EntityPlayerMP)o;
			if(p.getUniqueID().equals(id)) {
				return p;
			}
		}
		return null;
	}

	public Tower getTower(UUID owner) {
		return towers.get(owner);
	}

	public void setTower(UUID owner, Tower t) {
		towers.put(owner, t);
		changed(owner);
	}

	/*
	 * Forgets owner's tower if it is the one at this location
	 */
	public void removeTower(UUID owner, int dim, int x, int y, int z) {
		Tower t = towers.get(owner);
		if(t != null && t.isAt(dim, x, y, z)) {
			towers.remove(owner);
			changed(owner);
		}
	}

	/*
	 * Records the skills owner's tower at this location grants. Returns false if that isn't owner's tower.
	 */
	public boolean setTowerSkills(UUID owner, int dim, int x, int y, int z, List<String> skills) {
		Tower t = towers.get(owner);
		if(t == null || !t.isAt(dim, x, y, z)) {
			return false;
		}
		t.skills = new ArrayList<String>(skills);
		changed(owner);
		return true;
	}

	public Set<String> getAdminSkills(UUID player) {
		Set<String> s = adminSkills.get(player);
		return s == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(s);
	}

	public boolean addAdminSkill(UUID player, String skill) {
		Set<String> s = adminSkills.get(player);
		if(s == null) {
			s = new HashSet<String>();
			adminSkills.put(player, s);
		}
		boolean added = s.add(skill);
		changed(player);
		return added;
	}

	public boolean removeAdminSkill(UUID player, String skill) {
		Set<String> s = adminSkills.get(player);
		boolean removed = s != null && s.remove(skill);
		changed(player);
		return removed;
	}

	private void changed(UUID player) {
		markDirty();
		SkillCache.invalidate(player);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		towers.clear();
		adminSkills.clear();

		NBTTagList list = tag.getTagList("towers", 10);
		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound c = list.getCompoundTagAt(i);
			Tower t = new Tower(c.getInteger("dim"), c.getInteger("x"), c.getInteger("y"), c.getInteger("z"));
			t.skills = readStrings(c.getTagList("skills", 8));
			towers.put(UUID.fromString(c.getString("owner")), t);
		}

		list = tag.getTagList("adminSkills", 10);
		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound c = list.getCompoundTagAt(i);
			adminSkills.put(UUID.fromString(c.getString("player")), new HashSet<String>(readStrings(c.getTagList("skills", 8))));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		NBTTagList list = new NBTTagList();
		for(Map.Entry<UUID, Tower> e : towers.entrySet()) {
			Tower t = e.getValue();
			NBTTagCompound c = new NBTTagCompound();
			c.setString("owner", e.getKey().toString());
			c.setInteger("dim", t.dim);
			c.setInteger("x", t.x);
			c.setInteger("y", t.y);
			c.setInteger("z", t.z);
			c.setTag("skills", writeStrings(t.skills));
			list.appendTag(c);
		}
		tag.setTag("towers", list);

		list = new NBTTagList();
		for(Map.Entry<UUID, Set<String>> e : adminSkills.entrySet()) {
			NBTTagCompound c = new NBTTagCompound();
			c.setString("player", e.getKey().toString());
			c.setTag("skills", writeStrings(e.getValue()));
			list.appendTag(c);
		}
		tag.setTag("adminSkills", list);
	}

	public static List<String> readStrings(NBTTagList list) {
		List<String> out = new ArrayList<String>();
		for(int i = 0; i < list.tagCount(); i++) {
			out.add(list.getStringTagAt(i));
		}
		return out;
	}

	public static NBTTagList writeStrings(Iterable<String> strings) {
		NBTTagList list = new NBTTagList();
		for(String s : strings) {
			list.appendTag(new NBTTagString(s));
		}
		return list;
	}
}
