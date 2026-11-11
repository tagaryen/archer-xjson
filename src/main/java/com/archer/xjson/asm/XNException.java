package com.archer.xjson.asm;


public class XNException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public XNException(String msg) {
		super(msg);
	}

	public XNException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
