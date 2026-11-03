package com.babel;

import java.util.regex.Pattern;

/*
 * A set of actions allowed on every item/block whose registry name (e.g. minecraft:iron_sword) matches a regex.
 * Written in config as "actions:regex", where actions is "all" or a comma list like "wear,hold,carry".
 */
public class Grant {
	public final int actions;
	public final Pattern pattern;

	public Grant(int actions, Pattern pattern) {
		this.actions = actions;
		this.pattern = pattern;
	}

	public static Grant parse(String s) {
		int split = s.indexOf(':');
		if(split < 0) {
			throw new IllegalArgumentException("expected actions:regex but got " + s);
		}

		int mask = 0;
		for(String a : s.substring(0, split).split(",")) {
			a = a.trim().toUpperCase();
			if(a.equals("ALL")) {
				mask |= Actions.ALL;
			} else {
				mask |= Actions.valueOf(a).bit();
			}
		}
		return new Grant(mask, Pattern.compile(s.substring(split + 1).trim()));
	}
}
