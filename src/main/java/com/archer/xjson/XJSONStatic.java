package com.archer.xjson;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.LinkedList;

public class XJSONStatic {
	
	private static final JSONReflect reflector = new JSONReflect();
	
	private static final JSONStuff stuff = new JSONStuff();
	
	public static LinkedHashMap<String, Object> parse(String json) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToMap(json);
	}

	public static LinkedList<Object> parseList(String json) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToList(json);
	}
	
	public static <T> T parse(String json, Class<T> clazz) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json,  clazz, reflector);
	}
	public static <T> T parse(String json, JavaTypeRef<T> ref) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json,  ref, reflector);
	}
	public static <T> T parse(String json, Type type) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json,  type, reflector);
	}

	public static <T> LinkedList<T> parseList(String json, Class<T> clazz) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, clazz, reflector);
	}
	public static <T> LinkedList<T> parseList(String json, JavaTypeRef<T> ref) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, ref, reflector);
	}
	public static <T> LinkedList<T> parseList(String json, Type type) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, type, reflector);
	}
	
	public static String stringify(Object data) throws XJSONException  {
		return JSONEncoder.stringifyOneObject(data, stuff);
	}
	
	public static void setSerializer(Class<?> cls, XJSONSerializer serializer) {
		stuff.CODER_MAP.put(cls,  serializer);
	}
	
	public static void setDeserializer(Class<?> cls, XJSONDeserializer deserializer) {
		reflector.CODER_MAP.put(cls,  deserializer);
	}
	
	public static void useStrictJsonMode(boolean mode) {
		reflector.strictJsonMode = mode;
	}
	
	public static void useStrictClassMode(boolean mode) {
		reflector.strictClassMode = mode;
	}

	public static void useBeautifyMode(boolean mode) {
		stuff.beautify = mode;
	}
}
