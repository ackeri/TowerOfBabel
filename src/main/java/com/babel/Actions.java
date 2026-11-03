package com.babel;

public enum Actions {
	WEAR("wear"),
	HOLD("hold"),
	CARRY("carry"),
	BREAK("break blocks with"),
	ATTACK("attack with"),
	USE("use"),
	INTERACT("interact with"),
	PLACE("place"),
	CRAFT("craft");

	public static final int ALL = (1 << values().length) - 1;

	//mes should complete the sentence:
	//Cannot ___ itemname
	String mes;
	private Actions(String mes) {
		this.mes = mes;	
	}

	public int bit() {
		return 1 << ordinal();
	}

	public String toString() {
		return mes;
	}
}
