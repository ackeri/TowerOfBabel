package com;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.CommandException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.babel.ActionBlocker;
import com.babel.SkillCache;
import java.util.UUID;
import com.tower.TowerData;

public class CLI extends CommandBase{

	private static final String USAGE = "/tower [player] <add|remove> <skill>  or  /tower [player] list  or  /tower who [skill]  or  /tower reload";
	private static final List<String> OPS = Arrays.asList("add", "remove", "list");

	@Override
	public String getCommandName() {
		return "tower";
	}

	@Override
	public String getCommandUsage(ICommandSender ics) {
		return USAGE;
	}

	/*
	 * Anyone can look up who has which skills; changing skills or reloading needs op
	 */
	@Override
	public boolean canCommandSenderUseCommand(ICommandSender ics) {
		return true;
	}

	private static void requireOp(ICommandSender ics) {
		if(!ics.canCommandSenderUseCommand(2, "tower")) {
			throw new CommandException("Only ops can do that");
		}
	}

	@Override
	public void processCommand(ICommandSender ics, String[] args) throws CommandException {
		if(args.length == 1 && "reload".equals(args[0])) {
			requireOp(ics);
			TowerOfBabel.syncConfig();
			SkillCache.clear();
			for(Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
				SkillCache.sync((EntityPlayerMP)o);
			}
			ics.addChatMessage(new ChatComponentText("Reloaded " + TowerOfBabel.skills.size() + " skills; totem blocks need a restart"));
			return;
		}

		if(args.length >= 1 && "who".equals(args[0])) {
			TowerData data = TowerData.get();
			List<String> skills = args.length > 1 ? Arrays.asList(args[1]) : new ArrayList<String>(TowerOfBabel.skills.keySet());
			for(String skill : skills) {
				List<String> names = new ArrayList<String>();
				for(UUID id : data.holdersOf(skill)) {
					names.add(TowerData.nameOf(id));
				}
				ics.addChatMessage(new ChatComponentText(skill + ": " + (names.isEmpty() ? "nobody" : ActionBlocker.join(names, " and "))));
			}
			return;
		}

		//align arguments
		EntityPlayerMP p;
		if(args.length > 0 && OPS.contains(args[0])) {
			p = getCommandSenderAsPlayer(ics);
		} else if(args.length > 1) {
			p = getPlayer(ics, args[0]);
			args = Arrays.copyOfRange(args, 1, args.length);
		} else {
			throw new WrongUsageException(USAGE);
		}

		TowerData data = TowerData.get();
		String op = args[0];
		if("list".equals(op) && args.length == 1) {
			TowerData.Tower t = data.getTower(p.getUniqueID());
			ics.addChatMessage(new ChatComponentText(p.getCommandSenderName() + " was given: " + data.getAdminSkills(p.getUniqueID())));
			ics.addChatMessage(new ChatComponentText(t == null ? "No tower" : "Tower at " + t.describe() + " grants: " + t.skills));
			return;
		}

		if(args.length != 2 || !OPS.contains(op)) {
			throw new WrongUsageException(USAGE);
		}
		requireOp(ics);
		String skill = args[1];
		if(!TowerOfBabel.skills.containsKey(skill)) {
			throw new CommandException("Unknown skill " + skill + ", expected one of " + TowerOfBabel.skills.keySet());
		}

		if("add".equals(op)) {
			data.addAdminSkill(p.getUniqueID(), skill);
			ics.addChatMessage(new ChatComponentText(p.getCommandSenderName() + " learned " + skill));
		} else {
			data.removeAdminSkill(p.getUniqueID(), skill);
			ics.addChatMessage(new ChatComponentText(p.getCommandSenderName() + " forgot " + skill));
		}
	}

	@Override
	public List addTabCompletionOptions(ICommandSender ics, String[] args) {
		if(args.length == 1) {
			List<String> options = new ArrayList<String>(OPS);
			options.add("reload");
			options.add("who");
			options.addAll(Arrays.asList(MinecraftServer.getServer().getAllUsernames()));
			return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
		}
		String prev = args[args.length - 2];
		if("add".equals(prev) || "remove".equals(prev) || "who".equals(prev)) {
			return getListOfStringsMatchingLastWord(args, TowerOfBabel.skills.keySet().toArray(new String[0]));
		}
		if(args.length == 2) {
			return getListOfStringsMatchingLastWord(args, OPS.toArray(new String[0]));
		}
		return null;
	}
}
