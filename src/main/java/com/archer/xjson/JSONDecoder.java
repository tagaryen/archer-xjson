package com.archer.xjson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

abstract class JSONDecoder {

	/** 值字符串去重的最大长度（超出不进缓存） */
	static final int VALUE_INTERN_MAX_LEN = 32;
	/** 值字符串去重缓存的条目上限（防止病态数据撑爆内存） */
	static final int VALUE_INTERN_LIMIT = 4096;

	final int len;
	int pos;
	/** 单遍类型化解析时的映射器（提供 strict 模式与 fallback 转换） */
	JSONReflect reflector;

	StringTable keyTable;
	StringTable valueTable;

	JSONDecoder(int len) {
		this.len = len;
	}

	/** 字符访问：双模差异下沉到子类，热路径零分支 */
	abstract char at(int i);

	/** 对象 key：基于区间零分配去重，命中不产生临时字符串 */
	abstract String parseKey();

	/** 值位置字符串：长度 <=32 且缓存未满时去重复用 */
	abstract String parseStringValue();

	/** 带转义的字符串：逐段解码 */
	abstract String parseString();

	abstract XJSONException errAt(int offset);

	/** 纯 ASCII byte[] -> String，绕开 Charset.decoder 每次分配（-Xprof 8.4% 热点） */
	@SuppressWarnings("deprecation")
	static String asciiString(byte[] src, int start, int len) {
		return new String(src, 0, start, len);
	}

	/** String 输入模式 */
	static final class Str extends JSONDecoder {
		private final String json;

		Str(String json) {
			super(json.length());
			this.json = json;
		}

		@Override
		char at(int i) {
			return json.charAt(i);
		}

		@Override
		XJSONException errAt(int offset) {
			return new XJSONException(XJSONException.getErrorMsg(json, offset));
		}

		@Override
		String parseKey() {
			int start = pos + 1;
			int quote = json.indexOf('"', start);
			if(quote < 0) {
				throw errAt(start - 1);
			}
			for(int k = start; k < quote; k++) {
				if(json.charAt(k) == '\\') {
					pos = start - 1;
					return keyTable().intern(parseString());
				}
			}
			pos = quote + 1;
			return keyTable().intern(json, start, quote);
		}

		@Override
		String parseStringValue() {
			int start = pos + 1;
			int quote = json.indexOf('"', start);
			if(quote < 0) {
				throw errAt(start - 1);
			}
			for(int k = start; k < quote; k++) {
				if(json.charAt(k) == '\\') {
					return parseString();
				}
			}
			pos = quote + 1;
			int len = quote - start;
			if(len <= VALUE_INTERN_MAX_LEN && valueTable().size < VALUE_INTERN_LIMIT) {
				return valueTable().intern(json, start, quote);
			}
			return json.substring(start, quote);
		}

		@Override
		String parseString() {
			int start = ++pos;
			int quote = json.indexOf('"', start);
			if(quote < 0) {
				throw errAt(start - 1);
			}
			for(int k = start; k < quote; k++) {
				if(json.charAt(k) == '\\') {
					pos = start;
					StringBuilder sb = new StringBuilder(quote - start + 16);
					while(pos < len) {
						char c = json.charAt(pos++);
						if(c == '"') {
							return sb.toString();
						}
						if(c == '\\') {
							sb.append(parseEscape());
						} else {
							sb.append(c);
						}
					}
					throw errAt(start - 1);
				}
			}
			pos = quote + 1;
			return json.substring(start, quote);
		}
	}

	/** byte[] 输入模式（纯 ASCII） */
	static final class Byt extends JSONDecoder {
		private final byte[] bytes;

		Byt(byte[] bytes) {
			super(bytes.length);
			this.bytes = bytes;
		}

		@Override
		char at(int i) {
			return (char) bytes[i];
		}

		@Override
		XJSONException errAt(int offset) {
			int off = offset < 0 ? 0 : (offset > len ? len : offset);
			int end = off + XJSONException.END_OFFSET > len ? len : off + XJSONException.END_OFFSET;
			return new XJSONException("invalid json string near " +
					asciiString(bytes, off, end - off));
		}

		@Override
		String parseKey() {
			int start = pos + 1;
			int p = start;
			while(p < len) {
				byte b = bytes[p];
				if(b == '"') {
					pos = p + 1;
					return keyTable().intern(bytes, start, p);
				}
				if(b == '\\') {
					pos = start - 1;
					return keyTable().intern(parseString());
				}
				p++;
			}
			throw errAt(start - 1);
		}

		@Override
		String parseStringValue() {
			int start = pos + 1;
			int p = start;
			while(p < len) {
				byte b = bytes[p];
				if(b == '"') {
					pos = p + 1;
					int len = p - start;
					if(len <= VALUE_INTERN_MAX_LEN && valueTable().size < VALUE_INTERN_LIMIT) {
						return valueTable().intern(bytes, start, p);
					}
					return asciiString(bytes, start, len);
				}
				if(b == '\\') {
					pos = start - 1;
					return parseString();
				}
				p++;
			}
			throw errAt(start - 1);
		}

		@Override
		String parseString() {
			int start = ++pos;
			int p = start;
			while(p < len) {
				byte b = bytes[p];
				if(b == '"') {
					pos = p + 1;
					return asciiString(bytes, start, p - start);
				}
				if(b == '\\') {
					break;
				}
				p++;
			}
			if(p >= len) {
				throw errAt(start - 1);
			}
			pos = start;
			StringBuilder sb = new StringBuilder(p - start + 16);
			while(pos < len) {
				char c = (char) bytes[pos++];
				if(c == '"') {
					return sb.toString();
				}
				if(c == '\\') {
					sb.append(parseEscape());
				} else {
					sb.append(c);
				}
			}
			throw errAt(start - 1);
		}
	}

	final StringTable keyTable() {
		if(keyTable == null) {
			keyTable = new StringTable(64, -1);
		}
		return keyTable;
	}

	final StringTable valueTable() {
		if(valueTable == null) {
			valueTable = new StringTable(512, VALUE_INTERN_LIMIT);
		}
		return valueTable;
	}

	/**
	 * 开放寻址字符串去重表：线性探测，命中时零分配。
	 * hash 与 String.hashCode 兼容（31 累加），可直接用缓存实例的
	 * hashCode 快速否定；limit >= 0 表示插入上限（超限后只查找不插入）。
	 */
	private static final class StringTable {
		private String[] keys;
		private int[] hashes;
		private int size;
		private final int limit;

		StringTable(int capacity, int limit) {
			this.keys = new String[capacity];
			this.hashes = new int[capacity];
			this.limit = limit;
		}

		/** 基于源字符串区间 (start, end) 的零分配 intern */
		String intern(String src, int start, int end) {
			int len = end - start;
			int h = 0;
			for(int i = start; i < end; i++) {
				h = 31 * h + src.charAt(i);
			}
			String[] ks = keys;
			int mask = ks.length - 1;
			int idx = (h ^ (h >>> 16)) & mask;
			for(;;) {
				String k = ks[idx];
				if(k == null) {
					break;
				}
				if(hashes[idx] == h && k.length() == len && rangeEquals(k, src, start, len)) {
					return k;
				}
				idx = (idx + 1) & mask;
			}
			String s = src.substring(start, end);
			if(limit >= 0 && size >= limit) {
				return s;
			}
			ks[idx] = s;
			hashes[idx] = h;
			size++;
			if(size > ks.length - (ks.length >> 2)) {
				resize();
			}
			return s;
		}

		/** 基于 byte[] 区间的零分配 intern（纯 ASCII） */
		String intern(byte[] src, int start, int end) {
			int len = end - start;
			int h = 0;
			for(int i = start; i < end; i++) {
				h = 31 * h + (char) src[i];
			}
			String[] ks = keys;
			int mask = ks.length - 1;
			int idx = (h ^ (h >>> 16)) & mask;
			for(;;) {
				String k = ks[idx];
				if(k == null) {
					break;
				}
				if(hashes[idx] == h && k.length() == len && rangeEquals(k, src, start, len)) {
					return k;
				}
				idx = (idx + 1) & mask;
			}
			String s = asciiString(src, start, len);
			if(limit >= 0 && size >= limit) {
				return s;
			}
			ks[idx] = s;
			hashes[idx] = h;
			size++;
			if(size > ks.length - (ks.length >> 2)) {
				resize();
			}
			return s;
		}

		/** 整串 intern（转义字符串等已 materialize 的场景） */
		String intern(String s) {
			int h = s.hashCode();
			int len = s.length();
			String[] ks = keys;
			int mask = ks.length - 1;
			int idx = (h ^ (h >>> 16)) & mask;
			for(;;) {
				String k = ks[idx];
				if(k == null) {
					break;
				}
				if(hashes[idx] == h && k.length() == len && rangeEquals(k, s, 0, len)) {
					return k;
				}
				idx = (idx + 1) & mask;
			}
			if(limit >= 0 && size >= limit) {
				return s;
			}
			ks[idx] = s;
			hashes[idx] = h;
			size++;
			if(size > ks.length - (ks.length >> 2)) {
				resize();
			}
			return s;
		}

		private static boolean rangeEquals(String k, String src, int start, int len) {
			for(int i = 0; i < len; i++) {
				if(k.charAt(i) != src.charAt(start + i)) {
					return false;
				}
			}
			return true;
		}

		private static boolean rangeEquals(String k, byte[] src, int start, int len) {
			for(int i = 0; i < len; i++) {
				if(k.charAt(i) != (char) src[start + i]) {
					return false;
				}
			}
			return true;
		}

		private void resize() {
			String[] oldKeys = keys;
			int[] oldHashes = hashes;
			int newCap = oldKeys.length << 1;
			String[] ks = new String[newCap];
			int[] hs = new int[newCap];
			int mask = newCap - 1;
			for(int i = 0; i < oldKeys.length; i++) {
				String k = oldKeys[i];
				if(k == null) {
					continue;
				}
				int h = oldHashes[i];
				int idx = (h ^ (h >>> 16)) & mask;
				while(ks[idx] != null) {
					idx = (idx + 1) & mask;
				}
				ks[idx] = k;
				hs[idx] = h;
			}
			keys = ks;
			hashes = hs;
		}
	}

	static XMap<String, Object> parseToMap(String json)
			throws XJSONException {
		JSONDecoder dec = new Str(json);
		dec.skipWs();
		if(dec.pos >= dec.len || dec.at(dec.pos) != '{') {
			throw dec.errAt(dec.pos);
		}
		XMap<String, Object> ret = dec.parseObject();
		dec.skipWs();
		if(dec.pos < dec.len) {
			throw dec.errAt(dec.pos);
		}
		return ret;
	}

	static ArrayList<Object> parseToList(String json)
			throws XJSONException {
		JSONDecoder dec = new Str(json);
		dec.skipWs();
		if(dec.pos >= dec.len || dec.at(dec.pos) != '[') {
			throw dec.errAt(dec.pos);
		}
		ArrayList<Object> ret = dec.parseArray();
		dec.skipWs();
		if(dec.pos < dec.len) {
			throw dec.errAt(dec.pos);
		}
		return ret;
	}

	static XMap<String, Object> parseToMap(byte[] json)
			throws XJSONException {
		JSONDecoder dec = decoderOf(json);
		dec.skipWs();
		if(dec.pos >= dec.len || dec.at(dec.pos) != '{') {
			throw dec.errAt(dec.pos);
		}
		XMap<String, Object> ret = dec.parseObject();
		dec.skipWs();
		if(dec.pos < dec.len) {
			throw dec.errAt(dec.pos);
		}
		return ret;
	}

	static ArrayList<Object> parseToList(byte[] json)
			throws XJSONException {
		JSONDecoder dec = decoderOf(json);
		dec.skipWs();
		if(dec.pos >= dec.len || dec.at(dec.pos) != '[') {
			throw dec.errAt(dec.pos);
		}
		ArrayList<Object> ret = dec.parseArray();
		dec.skipWs();
		if(dec.pos < dec.len) {
			throw dec.errAt(dec.pos);
		}
		return ret;
	}

	/** 纯 ASCII 走 byte[] 直扫（零 String 构造），否则按 UTF-8 解码 */
	private static JSONDecoder decoderOf(byte[] data) {
		for(int i = 0; i < data.length; i++) {
			if(data[i] < 0) {
				return new Str(new String(data, StandardCharsets.UTF_8));
			}
		}
		return new Byt(data);
	}

	static <T> T parseToClass(String json, Class<T> clazz, JSONReflect reflector)
			throws XJSONException {
		return new Str(json).parseTopAsType(clazz, reflector);
	}

	static <T> T parseToClass(String json, JavaTypeRef<T> ref, JSONReflect reflector) {
		return new Str(json).parseTopAsType(ref.getJavaType(), reflector);
	}

	static <T> T parseToClass(String json, Type type, JSONReflect reflector)
			throws XJSONException {
		return new Str(json).parseTopAsType(type, reflector);
	}

	static <T> T parseToClass(byte[] json, Class<T> clazz, JSONReflect reflector)
			throws XJSONException {
		return decoderOf(json).parseTopAsType(clazz, reflector);
	}

	static <T> T parseToClass(byte[] json, JavaTypeRef<T> ref, JSONReflect reflector) {
		return decoderOf(json).parseTopAsType(ref.getJavaType(), reflector);
	}

	static <T> T parseToClass(byte[] json, Type type, JSONReflect reflector)
			throws XJSONException {
		return decoderOf(json).parseTopAsType(type, reflector);
	}

	static <T> ArrayList<T> parseToClassList(String json, Class<T> clazz, JSONReflect reflector)
			throws XJSONException {
		return new Str(json).parseTopAsList(clazz, reflector);
	}

	static <T> ArrayList<T> parseToClassList(String json, JavaTypeRef<T> ref, JSONReflect reflector)
			throws XJSONException {
		return new Str(json).parseTopAsList(ref.getJavaType(), reflector);
	}

	static <T> ArrayList<T> parseToClassList(String json, Type type, JSONReflect reflector)
			throws XJSONException {
		return new Str(json).parseTopAsList(type, reflector);
	}

	static <T> ArrayList<T> parseToClassList(byte[] json, Class<T> clazz, JSONReflect reflector)
			throws XJSONException {
		return decoderOf(json).parseTopAsList(clazz, reflector);
	}

	static <T> ArrayList<T> parseToClassList(byte[] json, JavaTypeRef<T> ref, JSONReflect reflector)
			throws XJSONException {
		return decoderOf(json).parseTopAsList(ref.getJavaType(), reflector);
	}

	static <T> ArrayList<T> parseToClassList(byte[] json, Type type, JSONReflect reflector)
			throws XJSONException {
		return decoderOf(json).parseTopAsList(type, reflector);
	}

	/** 顶层对象 + 目标类型：单遍直填 */
	@SuppressWarnings("unchecked")
	final <T> T parseTopAsType(Type type, JSONReflect reflector) {
		this.reflector = reflector;
		skipWs();
		if(pos >= len || at(pos) != '{') {
			throw errAt(pos);
		}
		Object ret;
		if(type instanceof Class) {
			ret = parseBean((Class<?>) type, null);
		} else if(type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			Class<?> raw = (Class<?>) pt.getRawType();
			Type[] args = pt.getActualTypeArguments();
			if(Map.class.isAssignableFrom(raw)) {
				ret = parseMapAsType(raw,
						args != null && args.length == 2 ? args[0] : String.class,
						args != null && args.length == 2 ? args[1] : Object.class);
			} else {
				ret = parseBean(raw, args);
			}
		} else {
			// TypeVariable / Wildcard 等：树路径兜底
			ret = reflector.fromJavaType(type, parseObject());
		}
		skipWs();
		if(pos < len) {
			throw errAt(pos);
		}
		return (T) ret;
	}

	/** 顶层数组 + 元素类型：单遍直填 */
	final <T> ArrayList<T> parseTopAsList(Type elemType, JSONReflect reflector) {
		this.reflector = reflector;
		skipWs();
		if(pos >= len || at(pos) != '[') {
			throw errAt(pos);
		}
		pos++; // consume '['
		ArrayList<T> ret = new ArrayList<>(64);
		for(;;) {
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			if(at(pos) == ']') {
				pos++;
				break;
			}
			@SuppressWarnings("unchecked")
			T v = (T) parseValueAsType(elemType);
			// 元素级 null 与树路径 reflectOneList 一致：经 fromJavaType 转换（异常传播）
			if(v == NULL) {
				@SuppressWarnings("unchecked")
				T nv = (T) reflector.fromJavaType(elemType, null);
				ret.add(nv);
			} else {
				ret.add(v);
			}
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			char c = at(pos);
			if(c == ',') {
				pos++;
				continue;
			}
			if(c == ']') {
				pos++;
				break;
			}
			throw errAt(pos);
		}
		skipWs();
		if(pos < len) {
			throw errAt(pos);
		}
		return ret;
	}

	/** 读尽输入流（Java 8 无 InputStream.readAllBytes） */
	static byte[] readAllBytes(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
		byte[] buf = new byte[8192];
		int n;
		while((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	private void skipWs() {
		while(pos < len) {
			char c = at(pos);
			if(c != ' ' && c != '\n' && c != '\r' && c != '\t') {
				break;
			}
			pos++;
		}
	}

	private Object parseValue() {
		if(pos >= len) {
			throw errAt(len - 1);
		}
		char c = at(pos);
		switch(c) {
		case '{':
			return parseObject();
		case '[':
			return parseArray();
		case '"':
			return parseStringValue();
		case '\'':
			return parseChar();
		case 'n':
			return parseLiteral("null", null);
		case 't':
			return parseLiteral("true", Boolean.TRUE);
		case 'f':
			return parseLiteral("false", Boolean.FALSE);
		default:
			if((c >= '0' && c <= '9') || c == '-') {
				return parseNumber();
			}
			throw errAt(pos);
		}
	}

	private XMap<String, Object> parseObject() {
		pos++; // consume '{'
		// 初始容量 8：常见业务对象 5~8 个 key，避免 7 键场景必触发的扩容垃圾
		XMap<String, Object> ret = new XMap<>(8);
		for(;;) {
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			char c = at(pos);
			if(c == '}') {
				pos++;
				return ret;
			}
			if(c != '"') {
				throw errAt(pos);
			}
			String key = parseKey();
			skipWs();
			if(pos >= len || at(pos) != ':') {
				throw errAt(pos);
			}
			pos++;
			skipWs();
			Object val = parseValue();
			ret.put(key, val);
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			c = at(pos);
			if(c == ',') {
				pos++;
				continue;
			}
			if(c == '}') {
				pos++;
				return ret;
			}
			throw errAt(pos);
		}
	}

	private ArrayList<Object> parseArray() {
		pos++; // consume '['
		ArrayList<Object> ret = new ArrayList<>(64);
		for(;;) {
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			char c = at(pos);
			if(c == ']') {
				pos++;
				return ret;
			}
			ret.add(parseValue());
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			c = at(pos);
			if(c == ',') {
				pos++;
				continue;
			}
			if(c == ']') {
				pos++;
				return ret;
			}
			throw errAt(pos);
		}
	}

	/** pos 位于反斜杠之后，解码一个转义序列 */
	final char parseEscape() {
		if(pos >= len) {
			throw errAt(pos - 1);
		}
		char e = at(pos++);
		switch(e) {
		case '"': return '"';
		case '\\': return '\\';
		case '/': return '/';
		case 'b': return '\b';
		case 'f': return '\f';
		case 'n': return '\n';
		case 'r': return '\r';
		case 't': return '\t';
		case 'u':
			if(pos + 4 > len) {
				throw errAt(pos - 2);
			}
			int code = 0;
			for(int k = 0; k < 4; k++) {
				int d = hexDigit(at(pos++));
				if(d < 0) {
					throw errAt(pos - 5);
				}
				code = (code << 4) | d;
			}
			return (char) code;
		default:
			throw new XJSONException("invalid escape char '\\" + e + "' in json string");
		}
	}

	private static int hexDigit(char c) {
		if(c >= '0' && c <= '9') {
			return c - '0';
		}
		if(c >= 'a' && c <= 'f') {
			return c - 'a' + 10;
		}
		if(c >= 'A' && c <= 'F') {
			return c - 'A' + 10;
		}
		return -1;
	}

	/** 单引号字符值，如 'a' -> Character */
	private Character parseChar() {
		if(pos + 2 < len && at(pos + 2) == '\'') {
			Character c = at(pos + 1);
			pos += 3;
			return c;
		}
		throw errAt(pos);
	}

	private Object parseLiteral(String literal, Object val) {
		int n = literal.length();
		if(pos + n > len) {
			throw errAt(pos);
		}
		for(int i = 0; i < n; i++) {
			if(at(pos + i) != literal.charAt(i)) {
				throw errAt(pos);
			}
		}
		pos += n;
		return val;
	}

	/**
	 * 单遍手写数字解析，不依赖异常判断类型、不产生 substring 中间数组。
	 * 整数优先返回 Integer，超出则 Long，再超出或含小数/指数则 Double。
	 */
	private Number parseNumber() {
		int start = pos;
		boolean neg = at(pos) == '-';
		if(neg) {
			pos++;
		}
		int digitStart = pos;

		long ival = 0;
		double dval = 0.0;
		boolean isDouble = false;

		// 整数部分：单遍累加，超出 long 时自动转 double
		while(pos < len) {
			char c = at(pos);
			if(c < '0' || c > '9') {
				break;
			}
			int d = c - '0';
			if(!isDouble) {
				if(ival > (Long.MAX_VALUE - d) / 10) {
					isDouble = true;
					dval = (double) ival * 10.0 + d;
				} else {
					ival = ival * 10 + d;
				}
			} else {
				dval = dval * 10.0 + d;
			}
			pos++;
		}
		if(pos == digitStart) {
			throw errAt(start);
		}

		// 小数部分
		if(pos < len && at(pos) == '.') {
			if(!isDouble) {
				isDouble = true;
				dval = (double) ival;
			}
			pos++;
			double factor = 0.1;
			while(pos < len) {
				char c = at(pos);
				if(c < '0' || c > '9') {
					break;
				}
				dval += (c - '0') * factor;
				factor *= 0.1;
				pos++;
			}
		}

		// 指数部分
		if(pos < len && (at(pos) == 'e' || at(pos) == 'E')) {
			pos++;
			boolean eneg = false;
			if(pos < len && (at(pos) == '+' || at(pos) == '-')) {
				eneg = at(pos) == '-';
				pos++;
			}
			int exp = 0;
			while(pos < len) {
				char c = at(pos);
				if(c < '0' || c > '9') {
					break;
				}
				exp = exp * 10 + (c - '0');
				pos++;
			}
			if(!isDouble) {
				isDouble = true;
				dval = (double) ival;
			}
			dval *= Math.pow(10.0, eneg ? -exp : exp);
		}

		if(isDouble) {
			return neg ? -dval : dval;
		}
		long v = neg ? -ival : ival;
		if(v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
			return (int) v;
		}
		return v;
	}

	/** 内部哨兵：表示 JSON null，区别于 null（未支持/解析失败时可能返回 null） */
	private static final Object NULL = new Object();

	/**
	 * 解析一个 JSON 值并直接转换为目标类型。
	 * 快路径覆盖：String/数字族/Boolean/Character/日期族/enum/数组/集合/Map/POJO；
	 * 未覆盖形态自动回退 parseValue() 树 + reflector.fromJavaType()，
	 * 保证与"先建树再映射"完全一致的语义与错误处理。
	 */
	private Object parseValueAsType(Type type) {
		// JSON null：统一返回哨兵，由调用方决定跳过字段或向集合填 null
		if(pos < len && at(pos) == 'n') {
			parseLiteral("null", null);
			return NULL;
		}
		if(type instanceof Class) {
			return parseValueAsClass((Class<?>) type);
		}
		if(type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			Class<?> raw = (Class<?>) pt.getRawType();
			Type[] args = pt.getActualTypeArguments();
			if(Collection.class.isAssignableFrom(raw)) {
				if(pos < len && at(pos) == '[') {
					return parseCollectionAsType(raw,
							args != null && args.length == 1 ? args[0] : Object.class);
				}
				return reflector.fromJavaType(type, parseValue());
			}
			if(Map.class.isAssignableFrom(raw)) {
				if(pos < len && at(pos) == '{') {
					return parseMapAsType(raw,
							args != null && args.length == 2 ? args[0] : String.class,
							args != null && args.length == 2 ? args[1] : Object.class);
				}
				return reflector.fromJavaType(type, parseValue());
			}
			// 泛型 POJO：Result<User>，按类型实参解析字段
			if(pos < len && at(pos) == '{') {
				return parseBean(raw, args);
			}
			return reflector.fromJavaType(type, parseValue());
		}
		// TypeVariable / Wildcard / GenericArrayType 等：走树路径兜底
		return reflector.fromJavaType(type, parseValue());
	}

	private Object parseValueAsClass(Class<?> clazz) {
		if(clazz == Object.class) {
			return parseValue();
		}
		if(clazz == String.class) {
			if(pos < len && at(pos) == '"') {
				return parseStringValue();
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(isNumberTarget(clazz)) {
			if(pos < len) {
				char c = at(pos);
				if((c >= '0' && c <= '9') || c == '-') {
					return convertNumber(parseNumber(), clazz);
				}
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(clazz == Boolean.class || clazz == boolean.class) {
			if(pos < len && (at(pos) == 't' || at(pos) == 'f')) {
				return at(pos) == 't'
						? parseLiteral("true", Boolean.TRUE)
						: parseLiteral("false", Boolean.FALSE);
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(clazz == Character.class || clazz == char.class) {
			if(pos < len && at(pos) == '\'') {
				return parseChar();
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(Date.class.isAssignableFrom(clazz) || clazz == LocalDate.class
				|| clazz == LocalTime.class || clazz == LocalDateTime.class) {
			if(pos < len && at(pos) == '"') {
				return reflector.fromJavaType(clazz, parseStringValue());
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(clazz.isEnum()) {
			if(pos < len && at(pos) == '"') {
				@SuppressWarnings("unchecked")
				Object e = JSONReflect.reflectToEnum(
						(Class<Enum<?>>) clazz, parseStringValue());
				return e;
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(clazz.isArray()) {
			// byte[] 特例：Base64 字符串（对齐 reflectToByteArr）
			if(clazz == byte[].class) {
				if(pos < len && at(pos) == '"') {
					return Base64.getDecoder().decode(parseStringValue());
				}
				return reflector.fromJavaType(clazz, parseValue());
			}
			if(pos < len && at(pos) == '[') {
				return parseArrayAsType(clazz.getComponentType());
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(Collection.class.isAssignableFrom(clazz)) {
			if(pos < len && at(pos) == '[') {
				return parseCollectionAsType(clazz, Object.class);
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		if(Map.class.isAssignableFrom(clazz)) {
			if(pos < len && at(pos) == '{') {
				return parseMapAsType(clazz, String.class, Object.class);
			}
			return reflector.fromJavaType(clazz, parseValue());
		}
		// 普通 POJO
		if(pos < len && at(pos) == '{') {
			return parseBean(clazz, null);
		}
		return reflector.fromJavaType(clazz, parseValue());
	}

	private static boolean isNumberTarget(Class<?> clazz) {
		return clazz == Integer.class || clazz == int.class
				|| clazz == Long.class || clazz == long.class
				|| clazz == Double.class || clazz == double.class
				|| clazz == Float.class || clazz == float.class
				|| clazz == Short.class || clazz == short.class
				|| clazz == Byte.class || clazz == byte.class
				|| clazz == BigInteger.class || clazz == BigDecimal.class;
	}

	/**
	 * 树值（Integer/Long/Double）到目标数字类型的转换，
	 * 分支逻辑对齐 JSONReflect.reflectToPopularClass / reflectToPrimitive。
	 */
	private static Object convertNumber(Number n, Class<?> target) {
		if(target == Integer.class || target == int.class) {
			return n instanceof Integer ? n : (int) n.longValue();
		}
		if(target == Long.class || target == long.class) {
			return n instanceof Long ? n : n.longValue();
		}
		if(target == Double.class || target == double.class) {
			return n instanceof Double ? n : n.doubleValue();
		}
		if(target == Float.class || target == float.class) {
			return n instanceof Float ? n : n.floatValue();
		}
		if(target == Short.class || target == short.class) {
			return n.shortValue();
		}
		if(target == Byte.class || target == byte.class) {
			return n.byteValue();
		}
		if(target == BigInteger.class) {
			if(n instanceof Long) {
				return BigInteger.valueOf(n.longValue());
			}
			if(n instanceof Integer) {
				return BigInteger.valueOf(n.intValue());
			}
			return new BigInteger(n.toString());
		}
		// BigDecimal
		if(n instanceof Double) {
			return BigDecimal.valueOf(n.doubleValue());
		}
		if(n instanceof Long) {
			return BigDecimal.valueOf(n.longValue());
		}
		return BigDecimal.valueOf(n.intValue());
	}

	/** 深度计数跳过一个完整 JSON 值（未知字段/形态不匹配时不建树） */
	private void skipValue() {
		skipWs();
		if(pos >= len) {
			throw errAt(len - 1);
		}
		char c = at(pos);
		if(c == '"') {
			pos++;
			while(pos < len) {
				char s = at(pos++);
				if(s == '\\' && pos < len) {
					pos++;
					continue;
				}
				if(s == '"') {
					return;
				}
			}
			throw errAt(len - 1);
		}
		if(c == '{' || c == '[') {
			int depth = 0;
			while(pos < len) {
				char d = at(pos++);
				if(d == '"') {
					while(pos < len) {
						char s = at(pos++);
						if(s == '\\' && pos < len) {
							pos++;
							continue;
						}
						if(s == '"') {
							break;
						}
					}
				} else if(d == '{' || d == '[') {
					depth++;
				} else if(d == '}' || d == ']') {
					if(--depth == 0) {
						return;
					}
				}
			}
			throw errAt(len - 1);
		}
		// 数字 / true / false / null：扫到值分隔符
		while(pos < len) {
			char d = at(pos);
			if(d == ',' || d == '}' || d == ']' || d == ' ' || d == '\n'
					|| d == '\r' || d == '\t') {
				break;
			}
			pos++;
		}
	}

	/**
	 * POJO 单遍填充，语义对齐 JSONReflect.reflectUnknownClass + reflectFields：
	 * - JSON null / 缺失字段：跳过写入（strictClassMode 下报 missing）
	 * - 字段转换异常：非 strict 静默忽略该字段
	 * - 未知 key：skipValue（strictJsonMode 下报错）
	 * - 泛型字段按 childTypes 解析 TypeVariable（声明类维度）
	 */
	private Object parseBean(Class<?> clazz, Type[] childTypes) {
		Object instance = JSONReflect.newInstance(clazz);
		ClassMeta meta = JSONCache.getMeta(clazz);
		Field[] fields = meta.fields;
		boolean strictJson = reflector.strictJsonMode;
		boolean strictClass = reflector.strictClassMode;
		ArrayList<String> seenKeys = strictJson ? new ArrayList<>(32) : null;
		BitSet assigned = strictClass ? new BitSet(fields.length) : null;

		pos++; // consume '{'
		for(;;) {
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			char c = at(pos);
			if(c == '}') {
				pos++;
				break;
			}
			if(c != '"') {
				throw errAt(pos);
			}
			String key = parseKey();
			if(strictJson) {
				seenKeys.add(key);
			}
			skipWs();
			if(pos >= len || at(pos) != ':') {
				throw errAt(pos);
			}
			pos++;
			skipWs();

			int fi = meta.index.get(key);
			if(fi >= 0) {
				Field f = fields[fi];
				Type ft = resolveFieldType(f, childTypes);
				try {
					Object v = parseValueAsType(ft);
					if(v != NULL) {
						f.set(instance, v);
						if(assigned != null) {
							assigned.set(fi);
						}
					}
				} catch(Exception e) {
					// 对齐 reflectFields：转换/写入失败在非 strict 模式下静默忽略该字段
					if(strictClass) {
						throw new XJSONException(
								XJSONException.getErrorMsg(f, null), e);
					}
				}
			} else {
				skipValue();
			}

			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			c = at(pos);
			if(c == ',') {
				pos++;
				continue;
			}
			if(c == '}') {
				pos++;
				break;
			}
			throw errAt(pos);
		}

		if(strictJson) {
			for(String k : seenKeys) {
				if(meta.index.get(k) < 0) {
					throw new XJSONException(
							XJSONException.getErrorMsg(k, clazz, false));
				}
			}
		}
		if(assigned != null) {
			for(int i = 0; i < fields.length; i++) {
				if(!assigned.get(i)) {
					throw new XJSONException(
							XJSONException.getErrorMsg(fields[i].getName(), clazz, true));
				}
			}
		}
		return instance;
	}

	/** 泛型字段解析：TypeVariable 按声明类的类型变量名匹配 childTypes（对齐 reflectFields） */
	private static Type resolveFieldType(Field f, Type[] childTypes) {
		if(childTypes == null) {
			return f.getGenericType();
		}
		Type ft = f.getGenericType();
		if(ft instanceof TypeVariable) {
			String[] declGenerics =
					JSONReflect.parseGenericTypeNames(f.getDeclaringClass());
			for(int i = 0; i < childTypes.length && i < declGenerics.length; i++) {
				if(declGenerics[i].equals(ft.getTypeName())) {
					return childTypes[i];
				}
			}
		}
		return ft;
	}

	/** 数组单遍填充；原始数组经 Array.set 自动拆箱，规避树路径的强转 bug */
	private Object parseArrayAsType(Class<?> compType) {
		pos++; // consume '['
		ArrayList<Object> tmp = new ArrayList<>(32);
		for(;;) {
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			if(at(pos) == ']') {
				pos++;
				break;
			}
			Object v = parseValueAsType(compType);
			tmp.add(v == NULL ? null : v);
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			char c = at(pos);
			if(c == ',') {
				pos++;
				continue;
			}
			if(c == ']') {
				pos++;
				break;
			}
			throw errAt(pos);
		}
		Object arr = Array.newInstance(compType, tmp.size());
		for(int i = 0; i < tmp.size(); i++) {
			Array.set(arr, i, tmp.get(i));
		}
		return arr;
	}

	/** 集合单遍填充，实例选择对齐 JSONReflect.reflectCollection */
	@SuppressWarnings("unchecked")
	private Collection<Object> parseCollectionAsType(Class<?> raw, Type elemType) {
		pos++; // consume '['
		if(List.class.isAssignableFrom(raw)) {
			ArrayList<Object> list = raw.isInterface()
					? new ArrayList<>(32) : (ArrayList<Object>) JSONReflect.newInstance(raw);
			collectElements(elemType, list);
			return list;
		}
		if(Set.class.isAssignableFrom(raw)) {
			Set<Object> set = raw.isInterface()
					? new LinkedHashSet<>() : (Set<Object>) JSONReflect.newInstance(raw);
			collectElements(elemType, set);
			return set;
		}
		if(Queue.class.isAssignableFrom(raw)) {
			// 无法预知元素数：先收集再定容（对齐树路径 ABQ 容量 = 元素数）
			ArrayList<Object> tmp = new ArrayList<>(32);
			collectElements(elemType, tmp);
			Queue<Object> queue = raw.isInterface()
					? new ArrayBlockingQueue<>(Math.max(1, tmp.size()))
					: (Queue<Object>) JSONReflect.newInstance(raw);
			queue.addAll(tmp);
			return queue;
		}
		throw new XJSONException("unknown collection like type '" + raw.getName() + "'");
	}

	private void collectElements(Type elemType, Collection<Object> out) {
		for(;;) {
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			if(at(pos) == ']') {
				pos++;
				return;
			}
			Object v = parseValueAsType(elemType);
			// 元素级 null 与树路径一致：经 fromJavaType 转换（异常传播）
			out.add(v == NULL ? reflector.fromJavaType(elemType, null) : v);
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			char c = at(pos);
			if(c == ',') {
				pos++;
				continue;
			}
			if(c == ']') {
				pos++;
				return;
			}
			throw errAt(pos);
		}
	}

	/** Map 单遍填充，实例选择对齐 JSONReflect.reflectMap */
	@SuppressWarnings("unchecked")
	private Map<Object, Object> parseMapAsType(Class<?> raw, Type keyType, Type valType) {
		Map<Object, Object> instance = raw.isInterface()
				? new XMap<>() : (Map<Object, Object>) JSONReflect.newInstance(raw);
		pos++; // consume '{'
		for(;;) {
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			char c = at(pos);
			if(c == '}') {
				pos++;
				return instance;
			}
			if(c != '"') {
				throw errAt(pos);
			}
			String key = parseKey();
			skipWs();
			if(pos >= len || at(pos) != ':') {
				throw errAt(pos);
			}
			pos++;
			skipWs();
			Object v = parseValueAsType(valType);
			Object k = keyType == String.class
					? key : reflector.fromJavaType(keyType, key);
			instance.put(k, v == NULL ? reflector.fromJavaType(valType, null) : v);
			skipWs();
			if(pos >= len) {
				throw errAt(len - 1);
			}
			c = at(pos);
			if(c == ',') {
				pos++;
				continue;
			}
			if(c == '}') {
				pos++;
				return instance;
			}
			throw errAt(pos);
		}
	}
}
