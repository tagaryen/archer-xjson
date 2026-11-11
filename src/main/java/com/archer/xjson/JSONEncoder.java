package com.archer.xjson;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

class JSONEncoder {

	static final String DATE_PATTERN = "yyyy-MM-dd";
	static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

	static final char BRACES_L = '[';
	static final char BRACES_R = ']';
	static final char B_BRACES_L = '{';
	static final char B_BRACES_R = '}';
	static final char COMMA = ',';
	static final char COLON = ':';
	static final char SPACE = ' ';
	static final char ENTER = '\n';
	static final char QUOTE = '"';
	static final char SINGLE_QUOTE = '\'';
	static final String TAB = "  ";

	static final String DECIMAL_PATTERN = "#.00";

	/** 线程安全的日期格式化器缓存（SimpleDateFormat 非线程安全） */
	static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_TL =
			ThreadLocal.withInitial(() -> new SimpleDateFormat(DATE_PATTERN));
	static final DateTimeFormatter LOCAL_DATE_FORMAT =
			DateTimeFormatter.ofPattern(TIME_PATTERN);
	static final DateTimeFormatter LOCAL_TIME_FORMAT =
			DateTimeFormatter.ofPattern(TIME_PATTERN);
	static final DateTimeFormatter LOCAL_DATE_TIME_FORMAT =
			DateTimeFormatter.ofPattern(TIME_PATTERN);
	static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT_TL =
			ThreadLocal.withInitial(() -> new DecimalFormat(DECIMAL_PATTERN));

	private static final char[] HEX = "0123456789abcdef".toCharArray();

	/** ThreadLocal 复用输出缓冲：跨调用保留容量，消除反复增长拷贝与缓冲垃圾 */
	private static final ThreadLocal<StringBuilder> BUF =
			ThreadLocal.withInitial(() -> new StringBuilder(8192));
	/** 重入标记（getter 内嵌套 stringify 时回退新建缓冲，互不干扰） */
	private static final ThreadLocal<Boolean> BUF_IN_USE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	static String stringifyOneObject(Object data, JSONStuff stuff) {
		StringBuilder sb;
		boolean borrowed = false;
		if (!BUF_IN_USE.get()) {
			sb = BUF.get();
			sb.setLength(0);
			BUF_IN_USE.set(Boolean.TRUE);
			borrowed = true;
		} else {
			sb = new StringBuilder(8192);
		}
		try {
			writeObject(sb, data, 0, true, stuff);
			return sb.toString();
		} finally {
			if (borrowed) {
				BUF_IN_USE.set(Boolean.FALSE);
			}
		}
	}

	/**
	 * 递归序列化入口：所有结果直接追加到同一个 StringBuilder，
	 * 不再为每层递归创建中间字符串。
	 */
	@SuppressWarnings("unchecked")
	static void writeObject(StringBuilder sb, Object data, int tabCount, boolean isVal, JSONStuff stuff) {
		if (data == null) {
			sb.append("null");
			return;
		}
		Class<?> cls = data.getClass();
		if (cls.isPrimitive()) {
			writePrimitive(sb, data);
			return;
		}
		XJSONSerializer customCoder = stuff.CODER_MAP.get(cls);
		if (customCoder != null) {
			sb.append(customCoder.serialize(data));
			return;
		}
		if (data instanceof Boolean) {
			sb.append((Boolean) data ? "true" : "false");
			return;
		}
		if (data instanceof String) {
			if (isVal) {
				sb.append(QUOTE);
				appendEscaped(sb, (String) data);
				sb.append(QUOTE);
			} else {
				appendEscaped(sb, (String) data);
			}
			return;
		}
		if (data instanceof Byte) {
			sb.append((byte) (Byte) data);
			return;
		}
		if (data instanceof Character) {
			sb.append(SINGLE_QUOTE).append((char) (Character) data).append(SINGLE_QUOTE);
			return;
		}
		if (data instanceof Short) {
			sb.append((short) (Short) data);
			return;
		}
		if (data instanceof Integer) {
			sb.append((int) (Integer) data);
			return;
		}
		if (data instanceof Long) {
			sb.append((long) (Long) data);
			return;
		}
		if (data instanceof Float) {
			sb.append((float) (Float) data);
			return;
		}
		if (data instanceof Double) {
			writeDoubleVal(sb, (Double) data);
			return;
		}
		if (data instanceof BigInteger) {
			sb.append(((BigInteger) data).toString(10));
			return;
		}
		if (data instanceof BigDecimal) {
			sb.append(((BigDecimal) data).toPlainString());
			return;
		}
		if (data instanceof Number) {
			sb.append(DECIMAL_FORMAT_TL.get().format(data));
			return;
		}
		if (data instanceof Date) {
			String ret;
			try {
				ret = DATE_FORMAT_TL.get().format((Date) data);
			} catch (Exception ignored) {
				throw new XJSONException("cannot format date " + data);
			}
			if (isVal) {
				sb.append(QUOTE).append(ret).append(QUOTE);
			} else {
				sb.append(ret);
			}
			return;
		}
		if (data instanceof LocalDate) {
			String ret;
			try {
				ret = ((LocalDate) data).format(LOCAL_DATE_FORMAT);
			} catch (Exception ignored) {
				throw new XJSONException("cannot format localDate " + data);
			}
			if (isVal) {
				sb.append(QUOTE).append(ret).append(QUOTE);
			} else {
				sb.append(ret);
			}
			return;
		}
		if (data instanceof LocalTime) {
			String ret;
			try {
				ret = ((LocalTime) data).format(LOCAL_TIME_FORMAT);
			} catch (Exception ignored) {
				throw new XJSONException("cannot format localTime " + data);
			}
			if (isVal) {
				sb.append(QUOTE).append(ret).append(QUOTE);
			} else {
				sb.append(ret);
			}
			return;
		}
		if (data instanceof LocalDateTime) {
			String ret;
			try {
				ret = ((LocalDateTime) data).format(LOCAL_DATE_TIME_FORMAT);
			} catch (Exception ignored) {
				throw new XJSONException("cannot format localDateTime " + data);
			}
			if (isVal) {
				sb.append(QUOTE).append(ret).append(QUOTE);
			} else {
				sb.append(ret);
			}
			return;
		}
		if (cls.isArray()) {
			writeArray(sb, data, tabCount, stuff);
			return;
		}
		if (data instanceof Collection) {
			writeCollection(sb, (Collection<?>) data, tabCount, stuff);
			return;
		}
		if (cls.isEnum()) {
			if (isVal) {
				sb.append(QUOTE).append(((Enum<?>) data).name()).append(QUOTE);
			} else {
				sb.append(((Enum<?>) data).name());
			}
			return;
		}
		if (data instanceof Map) {
			try {
				writeMap(sb, (Map<String, ?>) data, tabCount, stuff);
			} catch (XJSONException e) {
				throw e;
			} catch (Exception e) {
				throw new XJSONException("cannot format map object, " + e.getLocalizedMessage());
			}
			return;
		}
		try {
			writeClass(sb, data, tabCount, stuff);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new XJSONException("cannot format class " + cls.getName() + ", " + e.getLocalizedMessage());
		}
	}

	static void writeCollection(StringBuilder sb, Collection<?> data, int tabCount, JSONStuff stuff) {
		String base = stuff.beautify ? buildIndent(tabCount) : null;
		sb.append(BRACES_L);
		if (stuff.beautify) {
			sb.append(ENTER);
		}
		int index = 0;
		int size = data.size();
		for (Object o : data) {
			++index;
			if (stuff.beautify) {
				sb.append(base).append(TAB);
			}
			if (o instanceof String) {
				sb.append(QUOTE);
				appendEscaped(sb, (String) o);
				sb.append(QUOTE);
			} else {
				writeObject(sb, o, tabCount + 1, false, stuff);
			}
			if (index != size) {
				sb.append(COMMA);
			}
			if (stuff.beautify) {
				sb.append(ENTER);
			}
		}
		if (stuff.beautify) {
			sb.append(base);
		}
		sb.append(BRACES_R);
	}

	static void writeMap(StringBuilder sb, Map<String, ?> data, int tabCount, JSONStuff stuff) {
		String base = stuff.beautify ? buildIndent(tabCount) : null;
		int size = data.size();
		sb.append(B_BRACES_L);
		if (stuff.beautify) {
			sb.append(ENTER);
		}
		int index = 0;
		for (Entry<String, ?> val : data.entrySet()) {
			++index;
			if (stuff.beautify) {
				sb.append(base).append(TAB);
			}
			sb.append(QUOTE);
			appendEscaped(sb, val.getKey());
			sb.append(QUOTE).append(COLON);
			Object fv = val.getValue();
			if (fv == data) {
				throw new XJSONException("field " + val.getKey() + " is self-referencing");
			}
			writeObject(sb, fv, tabCount + 1, true, stuff);
			if (index != size) {
				sb.append(COMMA);
			}
			if (stuff.beautify) {
				sb.append(ENTER);
			}
		}
		if (stuff.beautify) {
			sb.append(base);
		}
		sb.append(B_BRACES_R);
	}

	/**
	 * 实体序列化：按字段声明类型特化直写。
	 * 原始类型经 Field.getLong/getDouble 等直读直写，零装箱、零 instanceof 分派；
	 * 字段名输出复用 ClassMeta 预算的 "name": 常量，一次 append 完成；
	 * 对象类型字段回退通用 writeObject 路径，行为与原实现一致。
	 */
	static void writeClass(StringBuilder sb, Object data, int tabCount, JSONStuff stuff)
			throws IllegalArgumentException, IllegalAccessException {
		String base = stuff.beautify ? buildIndent(tabCount) : null;
		ClassMeta meta = JSONCache.getMeta(data.getClass());
		Field[] fields = meta.fields;
		String[] keys = meta.keys;
		Class<?>[] types = meta.types;
		int len = fields.length;
		sb.append(B_BRACES_L);
		if (stuff.beautify) {
			sb.append(ENTER);
		}
		for (int i = 0; i < len; i++) {
			if (stuff.beautify) {
				sb.append(base).append(TAB);
			}
			sb.append(keys[i]);
			Field f = fields[i];
			Class<?> t = types[i];
			if (t == long.class) {
				sb.append(f.getLong(data));
			} else if (t == int.class) {
				sb.append(f.getInt(data));
			} else if (t == double.class) {
				writeDoubleVal(sb, f.getDouble(data));
			} else if (t == float.class) {
				sb.append(f.getFloat(data));
			} else if (t == boolean.class) {
				sb.append(f.getBoolean(data));
			} else if (t == short.class) {
				sb.append(f.getShort(data));
			} else if (t == byte.class) {
				sb.append(f.getByte(data));
			} else if (t == char.class) {
				sb.append(SINGLE_QUOTE).append(f.getChar(data)).append(SINGLE_QUOTE);
			} else if (t == String.class) {
				String v = (String) f.get(data);
				if (null == v) {
					sb.append("null");
				} else {
					sb.append(QUOTE);
					appendEscaped(sb, v);
					sb.append(QUOTE);
				}
			} else {
				Object fv = f.get(data);
				if (fv == data) {
					throw new XJSONException("field " + f.getName() + " is self-referencing");
				}
				writeObject(sb, fv, tabCount + 1, true, stuff);
			}
			if (i != len - 1) {
				sb.append(COMMA);
			}
			if (stuff.beautify) {
				sb.append(ENTER);
			}
		}
		if (stuff.beautify) {
			sb.append(base);
		}
		sb.append(B_BRACES_R);
	}

	/**
	 * double 直写：整数值 fast-path 走 long 直写拼 ".0"，零分配；
	 * 非整数回退 StringBuilder.append(double)（与原行为一致）。
	 * 排除 -0.0：原路径输出 "-0.0"，fast-path 会丢失负号。
	 */
	static void writeDoubleVal(StringBuilder sb, double val) {
		if (val == Math.floor(val) && !Double.isInfinite(val) && Math.abs(val) < 1e15
				&& !(val == 0 && Double.doubleToRawLongBits(val) != 0)) {
			sb.append((long) val);
			sb.append(".0");
		} else {
			sb.append(val);
		}
	}

	static void writeArray(StringBuilder sb, Object data, int tabCount, JSONStuff stuff) {
		if (data instanceof byte[]) {
			sb.append(QUOTE).append(new String(Base64.getEncoder().encode((byte[]) data))).append(QUOTE);
			return;
		}
		String base = stuff.beautify ? buildIndent(tabCount) : null;
		sb.append(BRACES_L);
		boolean finalSpace = false;
		if (data instanceof boolean[]) {
			boolean[] arr = (boolean[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (i != 0) {
					sb.append(COMMA).append(SPACE);
				}
				sb.append(arr[i]);
			}
		} else if (data instanceof int[]) {
			int[] arr = (int[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (i != 0) {
					sb.append(COMMA).append(SPACE);
				}
				sb.append(arr[i]);
			}
		} else if (data instanceof long[]) {
			long[] arr = (long[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (i != 0) {
					sb.append(COMMA).append(SPACE);
				}
				sb.append(arr[i]);
			}
		} else if (data instanceof char[]) {
			char[] arr = (char[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (i != 0) {
					sb.append(COMMA).append(SPACE);
				}
				sb.append(SINGLE_QUOTE).append(arr[i]).append(SINGLE_QUOTE);
			}
		} else if (data instanceof float[]) {
			float[] arr = (float[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (i != 0) {
					sb.append(COMMA).append(SPACE);
				}
				sb.append(arr[i]);
			}
		} else if (data instanceof double[]) {
			double[] arr = (double[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (i != 0) {
					sb.append(COMMA).append(SPACE);
				}
				sb.append(arr[i]);
			}
		} else if (data instanceof String[]) {
			if (stuff.beautify) {
				sb.append(ENTER);
			}
			finalSpace = true;
			String[] arr = (String[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (stuff.beautify) {
					sb.append(base).append(TAB);
				}
				sb.append(QUOTE);
				appendEscaped(sb, arr[i]);
				sb.append(QUOTE);
				if (i != arr.length - 1) {
					sb.append(COMMA);
				}
				if (stuff.beautify) {
					sb.append(ENTER);
				}
			}
		} else {
			if (stuff.beautify) {
				sb.append(ENTER);
			}
			finalSpace = true;
			Object[] arr = (Object[]) data;
			for (int i = 0; i < arr.length; i++) {
				if (stuff.beautify) {
					sb.append(base).append(TAB);
				}
				writeObject(sb, arr[i], tabCount + 1, false, stuff);
				if (i != arr.length - 1) {
					sb.append(COMMA);
				}
				if (stuff.beautify) {
					sb.append(ENTER);
				}
			}
		}
		if (stuff.beautify && finalSpace) {
			sb.append(base);
		}
		sb.append(BRACES_R);
	}

	/**
	 * 转义字符串内容（双引号、反斜杠、控制字符）。
	 * 段拷贝策略：先扫描定位需转义字符，批量拷贝其间无需转义的整段；
	 * 全串无需转义（绝大多数情况）时一次整体拷贝，零逐字符 switch 开销。
	 */
	static void appendEscaped(StringBuilder sb, String src) {
		int len = src.length();
		int segStart = 0;
		for (int i = 0; i < len; i++) {
			char c = src.charAt(i);
			if (c == '"' || c == '\\' || c < 0x20) {
				sb.append(src, segStart, i);
				appendEscapeChar(sb, c);
				segStart = i + 1;
			}
		}
		if (segStart == 0) {
			sb.append(src);
		} else if (segStart < len) {
			sb.append(src, segStart, len);
		}
	}

	private static void appendEscapeChar(StringBuilder sb, char c) {
		switch (c) {
		case '"':
			sb.append("\\\"");
			break;
		case '\\':
			sb.append("\\\\");
			break;
		case '\n':
			sb.append("\\n");
			break;
		case '\r':
			sb.append("\\r");
			break;
		case '\t':
			sb.append("\\t");
			break;
		case '\b':
			sb.append("\\b");
			break;
		case '\f':
			sb.append("\\f");
			break;
		default:
			// 仅 < 0x20 的控制字符会走到这里：直写  序列，替代 String.format
			sb.append("\\u00").append(HEX[(c >> 4) & 0xf]).append(HEX[c & 0xf]);
		}
	}

	static void writePrimitive(StringBuilder sb, Object val) throws XJSONException {
		Class<?> cls = val.getClass();
		if (Boolean.class.equals(cls) || boolean.class.equals(cls)) {
			sb.append((boolean) val);
		} else if (Byte.class.equals(cls) || byte.class.equals(cls)) {
			sb.append((byte) val);
		} else if (Character.class.equals(cls) || char.class.equals(cls)) {
			sb.append(SINGLE_QUOTE).append((char) val).append(SINGLE_QUOTE);
		} else if (Short.class.equals(cls) || short.class.equals(cls)) {
			sb.append((short) val);
		} else if (Integer.class.equals(cls) || int.class.equals(cls)) {
			sb.append((int) val);
		} else if (Long.class.equals(cls) || long.class.equals(cls)) {
			sb.append((long) val);
		} else if (Float.class.equals(cls) || float.class.equals(cls)) {
			sb.append((float) val);
		} else if (Double.class.equals(cls) || double.class.equals(cls)) {
			sb.append((double) val);
		} else {
			throw new XJSONException("unknown primitive type '" + cls.getName() + "'");
		}
	}

	/** 预计算缩进字符串，避免每个字段都循环拼接 */
	static String buildIndent(int tabCount) {
		if (tabCount <= 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder(tabCount * TAB.length());
		for (int i = 0; i < tabCount; i++) {
			sb.append(TAB);
		}
		return sb.toString();
	}
}
