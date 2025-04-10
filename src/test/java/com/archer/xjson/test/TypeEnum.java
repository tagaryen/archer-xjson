package com.archer.xjson.test;

public enum TypeEnum {
	TYPE0(0),
	TYPE1(1);
	
	private int code;
	
	TypeEnum(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
}
