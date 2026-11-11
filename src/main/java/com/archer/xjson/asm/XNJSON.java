package com.archer.xjson.asm;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class XNJSON {

	public String stringify(Object obj) {
		if (null == obj) {
			return "null";
		}
		StringBuilder sb = borrowBuffer();
		XNJSONRuntime.writeValue(new XNWriter(sb), obj);
		String out = sb.toString();
		releaseBuffer(sb);
		return out;
	}

	private static final ThreadLocal<StringBuilder> BUF =
			ThreadLocal.withInitial(() -> new StringBuilder(8192));
	private static final ThreadLocal<Boolean> BUF_IN_USE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private static StringBuilder borrowBuffer() {
		if (BUF_IN_USE.get()) {
			return new StringBuilder(8192);
		}
		BUF_IN_USE.set(Boolean.TRUE);
		StringBuilder sb = BUF.get();
		sb.setLength(0);
		return sb;
	}

	private static void releaseBuffer(StringBuilder sb) {
		if (sb == BUF.get()) {
			BUF_IN_USE.set(Boolean.FALSE);
		}
	}

	public byte[] stringifyBytes(Object obj) {
		return stringify(obj).getBytes(StandardCharsets.UTF_8);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> parse(String json) {
		return (Map<String, Object>) parseModel(json);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> parse(byte[] json) {
		return (Map<String, Object>) XNJSONRuntime.readGenericFromBytes(json);
	}

	@SuppressWarnings("unchecked")
	public List<Object> parseList(String json) {
		return (List<Object>) parseModel(json);
	}

	@SuppressWarnings("unchecked")
	public List<Object> parseList(byte[] json) {
		return (List<Object>) XNJSONRuntime.readGenericFromBytes(json);
	}

	@SuppressWarnings("unchecked")
	public <T> T parse(String json, Class<T> clazz) {
		return (T) XNJSONRuntime.readValueFromText(json, clazz);
	}

	@SuppressWarnings("unchecked")
	public <T> T parse(byte[] json, Class<T> clazz) {
		return (T) XNJSONRuntime.readValueFromBytes(json, clazz);
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> parseList(String json, Class<T> elemCls) {
		return (List<T>) XNJSONRuntime.readListFromText(json, elemCls);
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> parseList(byte[] json, Class<T> elemCls) {
		return (List<T>) XNJSONRuntime.readListFromBytes(json, elemCls);
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> parseListParallel(byte[] json, Class<T> elemCls) {
		return (List<T>) XNParallel.parseList(json, elemCls);
	}

	private Object parseModel(String json) {
		if (null == json || json.trim().isEmpty()) {
			throw new XNException("input json string can not be null or empty.");
		}
		return new XNReader(json).readGeneric();
	}
}
