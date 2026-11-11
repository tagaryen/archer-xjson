package com.archer.xjson.asm;

import java.util.List;
import java.util.Map;

public class XNJSONStatic {

	private static final XNJSON XNJSON = new XNJSON();

	public static String stringify(Object obj) {
		return XNJSON.stringify(obj);
	}

	public static byte[] stringifyBytes(Object obj) {
		return XNJSON.stringifyBytes(obj);
	}

	public static Map<String, Object> parse(String json) {
		return XNJSON.parse(json);
	}

	public static Map<String, Object> parse(byte[] json) {
		return XNJSON.parse(json);
	}

	public static List<Object> parseList(String json) {
		return XNJSON.parseList(json);
	}

	public static List<Object> parseList(byte[] json) {
		return XNJSON.parseList(json);
	}

	public static <T> T parse(String json, Class<T> clazz) {
		return XNJSON.parse(json, clazz);
	}

	public static <T> T parse(byte[] json, Class<T> clazz) {
		return XNJSON.parse(json, clazz);
	}

	public static <T> List<T> parseList(String json, Class<T> elemCls) {
		return XNJSON.parseList(json, elemCls);
	}

	public static <T> List<T> parseList(byte[] json, Class<T> elemCls) {
		return XNJSON.parseList(json, elemCls);
	}
}
