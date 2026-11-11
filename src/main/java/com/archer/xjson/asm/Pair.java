package com.archer.xjson.asm;

public class Pair<F, S> {
	private F first;
	private S second;
	
	public Pair(F first, S second) {
		this.first = first;
		this.second = second;
	}

	public F getFirst() {
		return first;
	}

	public void setFirst(F first) {
		this.first = first;
	}

	public S getSecond() {
		return second;
	}

	public void setSecond(S second) {
		this.second = second;
	}
	
	public String toString() {
		return first.toString() + ":" + second.toString();
	}
	
	public String toString(CharSequence join) {
		return first.toString() + join + second.toString();
	}
	
}
