package com.babel;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import com.TowerOfBabel;
import com.tower.Skill;

/*
 * The confusion of tongues: players understand each other only as far as they share skills.
 * Two players with the same skills chat normally; the more their towers differ, the more of each
 * other's words come out as babble. Players with no skills at all understand each other fine.
 */
public class BabelChat {
	public static void register() {
		MinecraftForge.EVENT_BUS.register(new BabelChat());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void chat(ServerChatEvent e) {
		if(!TowerOfBabel.babelChat || e.isCanceled()) return;
		e.setCanceled(true);

		MinecraftServer server = MinecraftServer.getServer();
		server.addChatMessage(e.component);
		Set<String> speaker = skillNames(e.player);
		boolean speakerPolyglot = polyglot(e.player);
		for(Object o : server.getConfigurationManager().playerEntityList) {
			EntityPlayerMP listener = (EntityPlayerMP)o;
			boolean understood = listener == e.player || speakerPolyglot || polyglot(listener);
			double babble = understood ? 0 : babble(speaker, skillNames(listener));
			String heard = scramble(e.message, babble, e.player.getCommandSenderName());
			listener.addChatMessage(new ChatComponentTranslation("chat.type.text", e.player.func_145748_c_(), heard));
		}
	}

	private static boolean polyglot(EntityPlayerMP p) {
		for(Skill s : SkillCache.activeSkills(p.getUniqueID(), p.dimension)) {
			if(s.polyglot) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> skillNames(EntityPlayerMP p) {
		Set<String> names = new HashSet<String>();
		for(Skill s : SkillCache.activeSkills(p.getUniqueID(), p.dimension)) {
			names.add(s.name);
		}
		return names;
	}

	/*
	 * The share of words that come out as babble: none for identical skills, all for nothing in common
	 */
	public static double babble(Set<String> a, Set<String> b) {
		Set<String> union = new HashSet<String>(a);
		union.addAll(b);
		if(union.isEmpty()) {
			return 0;
		}
		Set<String> shared = new HashSet<String>(a);
		shared.retainAll(b);
		return 1.0 - (double)shared.size() / union.size();
	}

	/*
	 * Replaces about that share of the words with babble. Which words, and what they turn into, depend only on the
	 * word and the speaker, so a speaker's babble is consistent, like a language you don't know.
	 */
	public static String scramble(String message, double babble, String speaker) {
		if(babble <= 0) {
			return message;
		}
		StringBuilder out = new StringBuilder();
		for(String word : message.split("(?<=\\s)|(?=\\s)")) {
			if(word.trim().isEmpty()) {
				out.append(word);
				continue;
			}
			Random rand = new Random((speaker + "|" + word.toLowerCase()).hashCode());
			out.append(rand.nextDouble() < babble ? babbleWord(word, rand) : word);
		}
		return out.toString();
	}

	private static final String CONSONANTS = "bdgklmnrstvzh";
	private static final String VOWELS = "aeiou";

	private static String babbleWord(String word, Random rand) {
		StringBuilder b = new StringBuilder();
		boolean vowel = rand.nextBoolean();
		for(int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if(!Character.isLetter(c)) {
				b.append(c);
				continue;
			}
			String pool = vowel ? VOWELS : CONSONANTS;
			char n = pool.charAt(rand.nextInt(pool.length()));
			b.append(Character.isUpperCase(c) ? Character.toUpperCase(n) : n);
			vowel = !vowel;
		}
		return b.toString();
	}
}
