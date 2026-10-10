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
import com.tower.TowerData;

public class CLI extends CommandBase{

	private static final String USAGE = "/tower [player] <add|remove> <skill>  or  /tower [player] list";
	private static final List<String> OPS = Arrays.asList("add", "remove", "list");

	@Override
	public String getCommandName() {
		return "tower";
	}

	@Override
	public String getCommandUsage(ICommandSender ics) {
		return USAGE;
	}

	@Override
	public void processCommand(ICommandSender ics, String[] args) throws CommandException {
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
			options.addAll(Arrays.asList(MinecraftServer.getServer().getAllUsernames()));
			return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
		}
		String prev = args[args.length - 2];
		if("add".equals(prev) || "remove".equals(prev)) {
			return getListOfStringsMatchingLastWord(args, TowerOfBabel.skills.keySet().toArray(new String[0]));
		}
		if(args.length == 2) {
			return getListOfStringsMatchingLastWord(args, OPS.toArray(new String[0]));
		}
		return null;
	}
}
