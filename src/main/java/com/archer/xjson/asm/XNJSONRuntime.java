package com.archer.xjson.asm;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.archer.xjson.XMap;

public class XNJSONRuntime {

	private static final ConcurrentHashMap<Class<?>, XNSerializer> SERIALIZERS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Class<?>, XNDeserializer> DESERIALIZERS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Class<?>, XNFieldFiller> FILLERS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Class<?>, Constructor<?>> CTORS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Class<?>, ValueReader> VALUE_READERS = new ConcurrentHashMap<>();

	public static XNSerializer serializer(Class<?> cls) {
		XNSerializer ser = SERIALIZERS.get(cls);
		if (null == ser) {
			// 非 public 类无法被生成类直接访问，走反射兜底
			if (!Modifier.isPublic(cls.getModifiers())) {
				ser = reflectSerializer();
			} else {
				ser = XNSerializerFactory.create(cls);
			}
			SERIALIZERS.put(cls, ser);
		}
		return ser;
	}

	public static XNDeserializer deserializer(Class<?> cls) {
		XNDeserializer deser = DESERIALIZERS.get(cls);
		if (null == deser) {
			if (!Modifier.isPublic(cls.getModifiers())) {
				deser = reflectDeserializer(cls);
			} else {
				deser = XNDeserializerFactory.create(cls);
			}
			DESERIALIZERS.put(cls, deser);
		}
		return deser;
	}

	/** 流式字段填充器：为每个实体类生成强类型 setter 直调实现 */
	public static XNFieldFiller filler(Class<?> cls) {
		XNFieldFiller f = FILLERS.get(cls);
		if (null == f) {
			if (!Modifier.isPublic(cls.getModifiers())) {
				f = reflectFiller(cls);
			} else {
				f = XNDeserializerFactory.createFiller(cls);
			}
			XNFieldFiller existing = FILLERS.putIfAbsent(cls, f);
			if (null != existing) {
				f = existing;
			}
		}
		return f;
	}

	private static XNFieldFiller reflectFiller(Class<?> cls) {
		return new XNFieldFiller() {
			@Override
			public boolean fill(Object bean, String field, XNReader reader) {
				for (Field f : fieldsOf(cls)) {
					if (f.getName().equals(field)) {
						readFieldValue(bean, field, reader);
						return true;
					}
				}
				return false;
			}

			@Override
			public Object create() {
				return newInstance(cls);
			}

			@Override
			public Class<?> type() {
				return cls;
			}
		};
	}

	/** 实体读缓存：filler + KeyTable 单次 CHM 查找，避免 readBean 每 bean 双查表 */
	private static final ConcurrentHashMap<Class<?>, BeanReader> BEANS = new ConcurrentHashMap<>();

	private static final class BeanReader {
		final XNFieldFiller filler;
		final XNReader.KeyTable keys;

		BeanReader(XNFieldFiller filler, XNReader.KeyTable keys) {
			this.filler = filler;
			this.keys = keys;
		}
	}

	private static BeanReader beanReader(Class<?> cls) {
		BeanReader br = BEANS.get(cls);
		if (null == br) {
			java.util.List<Field> fields = fieldsOf(cls);
			String[] names = new String[fields.size()];
			for (int i = 0; i < fields.size(); i++) {
				names[i] = fields.get(i).getName();
			}
			br = new BeanReader(filler(cls), new XNReader.KeyTable(names));
			BeanReader existing = BEANS.putIfAbsent(cls, br);
			if (null != existing) {
				br = existing;
			}
		}
		return br;
	}

	/** 从流中读取一个实体对象（根为对象），边扫边写、不构建中间树 */
	public static Object readBean(XNReader r, Class<?> cls) {
		BeanReader br = beanReader(cls);
		Object bean = br.filler.create();
		r.skipWs();
		r.expectChar('{');
		while (true) {
			r.skipWs();
			if (r.eatChar('}')) {
				break;
			}
			// 位置已在 '"'：KeyTable 直匹配，免 readKey 内部重复 skipWs、免通用 keyPool 探测
			String key = r.readKeyIn(br.keys);
			// 融合入口：扫空白 + 吃冒号；值前空白由标量读取/skipValue 内部 skipWs 兜底
			r.skipWsExpect(':');
			if (!br.filler.fill(bean, key, r)) {
				r.skipValue();
			}
			r.skipWs();
			if (r.eatChar('}')) {
				break;
			}
			r.expectChar(',');
		}
		return bean;
	}

	/** 按字段声明类型流式直读（生成的 filler 调用），避免 Field 反射查找与中间树 */
	public static Object readTyped(XNReader r, Class<?> pt, Class<?> elemCls) {
		r.skipWs();
		if (r.peek() == 'n') {
			r.readNull();
			return null;
		}
		if (List.class.isAssignableFrom(pt)) {
			return readList(r, elemCls);
		}
		if (Set.class.isAssignableFrom(pt)) {
			return readSet(r, elemCls);
		}
		if (Map.class.isAssignableFrom(pt)) {
			return readMap(r, elemCls);
		}
		if (pt.isArray()) {
			return readArray(r, pt.getComponentType());
		}
		return readValue(r, pt);
	}

	/** 无 setter 字段的反射兜底直填：读值 + Field.set */
	public static void readFieldValue(Object bean, String fieldName, XNReader r) {
		Field f = fieldOf(bean.getClass(), fieldName);
		Object value = readTyped(r, f.getType(), elemClassOf(f));
		try {
			f.set(bean, value);
		} catch (IllegalAccessException e) {
			throw new XNException("can not write field " + fieldName + " of " + bean.getClass().getName(), e);
		}
	}

	/** 值读取器：把 Class 一次性解析为具体读取动作，readValue 走 O(1) 缓存命中 */
	private interface ValueReader {
		Object read(XNReader r);
	}

	private static ValueReader valueReader(Class<?> pt) {
		ValueReader vr = VALUE_READERS.get(pt);
		if (null == vr) {
			vr = resolveValueReader(pt);
			ValueReader existing = VALUE_READERS.putIfAbsent(pt, vr);
			if (null != existing) {
				vr = existing;
			}
		}
		return vr;
	}

	/** 仅在 class 首次出现时执行一次类型链，结果缓存后按 Class 恒等 O(1) 命中 */
	private static ValueReader resolveValueReader(Class<?> pt) {
		if (pt == String.class) return r -> r.readStringValue();
		if (pt == int.class) return r -> r.readInt();
		if (pt == Integer.class) return r -> r.readInteger();
		if (pt == long.class) return r -> r.readLong();
		if (pt == Long.class) return r -> r.readLongObj();
		if (pt == double.class) return r -> r.readDouble();
		if (pt == Double.class) return r -> r.readDoubleObj();
		if (pt == float.class) return r -> r.readFloat();
		if (pt == Float.class) return r -> r.readFloatObj();
		if (pt == boolean.class) return r -> r.readBoolean();
		if (pt == Boolean.class) return r -> r.readBooleanObj();
		if (pt == byte.class) return r -> r.readByte();
		if (pt == Byte.class) return r -> r.readByteObj();
		if (pt == short.class) return r -> r.readShort();
		if (pt == Short.class) return r -> r.readShortObj();
		if (pt == char.class) return r -> r.readChar();
		if (pt == Character.class) return r -> r.readCharObj();
		if (pt == BigInteger.class) return r -> toBigInteger(r.readNumberAsString());
		if (pt == BigDecimal.class) return r -> toBigDecimal(r.readNumberAsString());
		if (Date.class.isAssignableFrom(pt)) return r -> toDate(r.readNumberAsString());
		if (pt == LocalDate.class) return r -> toLocalDate(r.readString());
		if (pt == LocalTime.class) return r -> toLocalTime(r.readString());
		if (pt == LocalDateTime.class) return r -> toLocalDateTime(r.readString());
		if (pt.isEnum()) return r -> toEnum(r.readString(), pt);
		if (pt == Object.class) return r -> r.readGeneric();
		return r -> readBean(r, pt);
	}

	/** 流式读取单个指定类型的值（不建树） */
	public static Object readValue(XNReader r, Class<?> pt) {
		r.skipWs();
		if (r.peek() == 'n' && !pt.isPrimitive()) {
			r.readNull();
			return null;
		}
		return valueReader(pt).read(r);
	}

	/** 流式读取 JSON 数组为 List */
	public static List<Object> readList(XNReader r, Class<?> elemCls) {
		List<Object> list = new ArrayList<>(64);
		r.skipWs();
		r.expectChar('[');
		r.skipWs();
		if (r.eatChar(']')) {
			return list;
		}
		while (true) {
			r.skipWs();
			list.add(readValue(r, elemCls));
			r.skipWs();
			if (r.eatChar(']')) {
				return list;
			}
			r.expectChar(',');
		}
	}

	/** 流式读取 JSON 数组为 Set */
	public static Set<Object> readSet(XNReader r, Class<?> elemCls) {
		Set<Object> set = new LinkedHashSet<>();
		r.skipWs();
		r.expectChar('[');
		r.skipWs();
		if (r.eatChar(']')) {
			return set;
		}
		while (true) {
			r.skipWs();
			set.add(readValue(r, elemCls));
			r.skipWs();
			if (r.eatChar(']')) {
				return set;
			}
			r.expectChar(',');
		}
	}

	/** 流式读取 JSON 数组为原始类型数组 */
	public static Object readArray(XNReader r, Class<?> elemCls) {
		List<Object> list = readList(r, elemCls);
		Object arr = Array.newInstance(elemCls, list.size());
		for (int i = 0; i < list.size(); i++) {
			Array.set(arr, i, list.get(i));
		}
		return arr;
	}

	/** 流式读取 JSON 对象为 Map（val 类型由 elemCls 指定，key 恒为字符串） */
	public static Map<Object, Object> readMap(XNReader r, Class<?> valCls) {
		Map<Object, Object> map = new XMap<>();
		r.skipWs();
		r.expectChar('{');
		r.skipWs();
		if (r.eatChar('}')) {
			return map;
		}
		while (true) {
			r.skipWs();
			String key = r.readKey();
			r.skipWs();
			r.expectChar(':');
			r.skipWs();
			map.put(key, readValue(r, valCls));
			r.skipWs();
			if (r.eatChar('}')) {
				return map;
			}
			r.expectChar(',');
		}
	}

	/** 顶层 JSON 数组的流式读取 */
	public static List<Object> readTypedList(XNReader r, Class<?> elemCls) {
		return readList(r, elemCls);
	}

	private static Class<?> elemClassOf(Field f) {
		Type g = f.getGenericType();
		if (g instanceof ParameterizedType) {
			Type[] args = ((ParameterizedType) g).getActualTypeArguments();
			// Map<K,V> 取 V，集合取元素类型
			int idx = Map.class.isAssignableFrom(f.getType()) && args.length > 1 ? 1 : 0;
			if (args.length > idx) {
				if (args[idx] instanceof Class) {
					return (Class<?>) args[idx];
				}
				if (args[idx] instanceof ParameterizedType
						&& ((ParameterizedType) args[idx]).getRawType() instanceof Class) {
					return (Class<?>) ((ParameterizedType) args[idx]).getRawType();
				}
			}
		}
		return Object.class;
	}

	/** 从 JSON 文本流式读取指定类型的根值（bean / 集合 / 标量），不构建中间树 */
	public static Object readValueFromText(String json, Class<?> cls) {
		if (null == json || json.trim().isEmpty()) {
			throw new XNException("input json string can not be null or empty.");
		}
		return readRootValue(new XNReader(json), cls);
	}

	public static List<Object> readListFromText(String json, Class<?> elemCls) {
		if (null == json || json.trim().isEmpty()) {
			throw new XNException("input json string can not be null or empty.");
		}
		return readList(new XNReader(json), elemCls);
	}

	/** 字节零拷贝入口：直接在 UTF-8 byte[] 上解析，跳过整篇解码 */
	public static Object readValueFromBytes(byte[] json, Class<?> cls) {
		if (null == json || json.length == 0) {
			throw new XNException("input json bytes can not be null or empty.");
		}
		return readRootValue(new XNReader(json), cls);
	}

	public static List<Object> readListFromBytes(byte[] json, Class<?> elemCls) {
		if (null == json || json.length == 0) {
			throw new XNException("input json bytes can not be null or empty.");
		}
		return readList(new XNReader(json), elemCls);
	}

	public static Object readGenericFromBytes(byte[] json) {
		if (null == json || json.length == 0) {
			throw new XNException("input json bytes can not be null or empty.");
		}
		return new XNReader(json).readGeneric();
	}

	private static Object readRootValue(XNReader r, Class<?> cls) {
		if (cls == Object.class) {
			return r.readGeneric();
		}
		if (List.class.isAssignableFrom(cls)) {
			return readList(r, Object.class);
		}
		if (Set.class.isAssignableFrom(cls)) {
			return readSet(r, Object.class);
		}
		if (Map.class.isAssignableFrom(cls)) {
			return readMap(r, Object.class);
		}
		if (cls.isArray()) {
			return readArray(r, cls.getComponentType());
		}
		return readValue(r, cls);
	}

	private static XNSerializer reflectSerializer() {
		return (out, bean) -> {
			out.writeStartObject();
			boolean first = true;
			for (Field f : fieldsOf(bean.getClass())) {
				if (!first) {
					out.writeComma();
				}
				first = false;
				out.writeName(f.getName());
				writeFieldReflect(out, bean, f.getName());
			}
			out.writeEndObject();
		};
	}

	private static XNDeserializer reflectDeserializer(Class<?> cls) {
		return (map) -> {
			Object bean = newInstance(cls);
			for (Field f : fieldsOf(cls)) {
				Object val = map.get(f.getName());
				if (null != val) {
					setFieldReflect(bean, f.getName(), val);
				}
			}
			return bean;
		};
	}

	/**
	 * 扫描类的可序列化字段（非 static / transient / 合成字段），
	 * 按父类优先顺序，子类同名字段覆盖父类。
	 */
	public static List<Field> fieldsOf(Class<?> cls) {
		List<Field> fields = FIELDS.get(cls);
		if (null == fields) {
			List<Class<?>> hierarchy = new ArrayList<>(32);
			Class<?> cur = cls;
			while (null != cur && cur != Object.class) {
				hierarchy.add(0, cur);
				cur = cur.getSuperclass();
			}
			Map<String, Field> ordered = new XMap<>();
			for (Class<?> c : hierarchy) {
				for (Field f : c.getDeclaredFields()) {
					int mod = f.getModifiers();
					if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
						continue;
					}
					f.setAccessible(true);
					ordered.put(f.getName(), f);
				}
			}
			fields = new ArrayList<>(ordered.values());
			FIELDS.put(cls, fields);
		}
		return fields;
	}

	public static void writeValue(XNWriter out, Object val) {
		if (null == val) {
			out.writeNull();
		} else if (val instanceof String) {
			out.writeString((String) val);
		} else if (val instanceof Integer) {
			out.writeInteger((Integer) val);
		} else if (val instanceof Long) {
			out.writeLongObj((Long) val);
		} else if (val instanceof Boolean) {
			out.writeBoolObj((Boolean) val);
		} else if (val instanceof Double) {
			out.writeDoubleObj((Double) val);
		} else if (val instanceof Float) {
			out.writeFloatObj((Float) val);
		} else if (val instanceof Short) {
			out.writeShortObj((Short) val);
		} else if (val instanceof Byte) {
			out.writeByteObj((Byte) val);
		} else if (val instanceof Character) {
			out.writeCharObj((Character) val);
		} else if (val instanceof BigInteger) {
			out.writeBigInteger((BigInteger) val);
		} else if (val instanceof BigDecimal) {
			out.writeBigDecimal((BigDecimal) val);
		} else if (val instanceof Date) {
			out.writeDate((Date) val);
		} else if (val instanceof LocalDateTime) {
			out.writeLocalDateTime((LocalDateTime) val);
		} else if (val instanceof LocalDate) {
			out.writeLocalDate((LocalDate) val);
		} else if (val instanceof LocalTime) {
			out.writeLocalTime((LocalTime) val);
		} else if (val instanceof Map) {
			writeMap(out, (Map<?, ?>) val);
		} else if (val instanceof Collection) {
			writeCollection(out, (Collection<?>) val);
		} else if (val.getClass().isArray()) {
			writeArray(out, val);
		} else if (val instanceof Enum) {
			out.writeEnum((Enum<?>) val);
		} else if (val instanceof Number) {
			out.raw().append(val.toString());
		} else {
			serializer(val.getClass()).serialize(out, val);
		}
	}

	private static void writeMap(XNWriter out, Map<?, ?> map) {
		out.writeStartObject();
		boolean first = true;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!first) {
				out.writeComma();
			}
			first = false;
			Object key = entry.getKey();
			out.writeKey(null == key ? null : key.toString());
			writeValue(out, entry.getValue());
		}
		out.writeEndObject();
	}

	private static void writeCollection(XNWriter out, Collection<?> coll) {
		out.writeStartArray();
		boolean first = true;
		for (Object item : coll) {
			if (!first) {
				out.writeComma();
			}
			first = false;
			writeValue(out, item);
		}
		out.writeEndArray();
	}

	private static void writeArray(XNWriter out, Object array) {
		if (array instanceof byte[]) {
			out.writeString(Base64.getEncoder().encodeToString((byte[]) array));
			return;
		}
		if (array instanceof char[]) {
			out.writeString(new String((char[]) array));
			return;
		}
		out.writeStartArray();
		int len = Array.getLength(array);
		for (int i = 0; i < len; i++) {
			if (i > 0) {
				out.writeComma();
			}
			writeValue(out, Array.get(array, i));
		}
		out.writeEndArray();
	}

	/** 无 getter 的字段反射读取兜底，生成字节码中直接调用 */
	public static void writeFieldReflect(XNWriter out, Object bean, String fieldName) {
		Field f = fieldOf(bean.getClass(), fieldName);
		try {
			writeValue(out, f.get(bean));
		} catch (IllegalAccessException e) {
			throw new XNException("can not read field " + fieldName + " of " + bean.getClass().getName(), e);
		}
	}

	/** 无 setter 的字段反射写入兜底，生成字节码中直接调用 */
	public static void setFieldReflect(Object bean, String fieldName, Object value) {
		Field f = fieldOf(bean.getClass(), fieldName);
		try {
			if (null != value && !f.getType().isPrimitive() && !f.getType().isInstance(value)) {
				value = toValue(value, f.getType());
			}
			f.set(bean, value);
		} catch (IllegalAccessException e) {
			throw new XNException("can not write field " + fieldName + " of " + bean.getClass().getName(), e);
		}
	}

	/** 字段查找：直扫 fieldsOf 缓存，零字符串拼接分配 */
	private static Field fieldOf(Class<?> cls, String name) {
		for (Field f : fieldsOf(cls)) {
			if (f.getName().equals(name)) {
				return f;
			}
		}
		throw new XNException("no field " + name + " found in " + cls.getName());
	}

	/** 无公开无参构造时的实例化兜底：选取参数最少的构造器，以空参数调用；失败则用 Unsafe 分配实例 */
    public static Object newInstance(Class<?> cls) {
		try {
			Constructor<?> ctor = CTORS.get(cls);
			if (null == ctor) {
				Constructor<?>[] ctors = cls.getDeclaredConstructors();
				if (ctors.length == 0) {
					throw new XNException("no constructor found for " + cls.getName());
				}
				ctor = ctors[0];
				for (Constructor<?> c : ctors) {
					if (c.getParameterCount() < ctor.getParameterCount()) {
						ctor = c;
					}
				}
				ctor.setAccessible(true);
				CTORS.put(cls, ctor);
			}
			Class<?>[] types = ctor.getParameterTypes();
			Object[] args = new Object[types.length];
			for (int i = 0; i < types.length; i++) {
				if (types[i].isPrimitive()) {
					args[i] = primitiveDefault(types[i]);
				}
			}
			return ctor.newInstance(args);
		} catch (Exception e) {
			try {
				return UnsafeHolder.allocate(cls);
			} catch (Exception ue) {
				throw new XNException("can not instantiate " + cls.getName(), ue);
			}
		}
	}

	/** 绕过构造器直接分配实例（供无构造器的 ASM 行视图等场景使用） */
	public static Object allocateInstance(Class<?> cls) {
		try {
			return UnsafeHolder.allocate(cls);
		} catch (Exception e) {
			throw new XNException("can not allocate " + cls.getName(), e);
		}
	}

	private static final class UnsafeHolder {
		private static final Object UNSAFE;
		private static final java.lang.reflect.Method ALLOCATE_INSTANCE;

		static {
			try {
				Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
				Field f = unsafeClass.getDeclaredField("theUnsafe");
				f.setAccessible(true);
				UNSAFE = f.get(null);
				ALLOCATE_INSTANCE = unsafeClass.getMethod("allocateInstance", Class.class);
			} catch (Exception e) {
				throw new XNException("can not get sun.misc.Unsafe instance.", e);
			}
		}

		static Object allocate(Class<?> cls) throws Exception {
			return ALLOCATE_INSTANCE.invoke(UNSAFE, cls);
		}
	}

	private static Object primitiveDefault(Class<?> type) {
		if (type == boolean.class) return false;
		if (type == char.class) return (char) 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0f;
		if (type == double.class) return 0d;
		return 0;
	}

	public static String asString(Object val) {
		return null == val ? null : val.toString();
	}

	public static int toInt(Object val) {
		Integer v = toInteger(val);
		return null == v ? 0 : v;
	}

	public static Integer toInteger(Object val) {
		if (null == val) return null;
		if (val instanceof Number) return ((Number) val).intValue();
		return Integer.valueOf(val.toString());
	}

	public static long toLong(Object val) {
		Long v = toLongObj(val);
		return null == v ? 0L : v;
	}

	public static Long toLongObj(Object val) {
		if (null == val) return null;
		if (val instanceof Number) return ((Number) val).longValue();
		return Long.valueOf(val.toString());
	}

	public static double toDouble(Object val) {
		Double v = toDoubleObj(val);
		return null == v ? 0d : v;
	}

	public static Double toDoubleObj(Object val) {
		if (null == val) return null;
		if (val instanceof Number) return ((Number) val).doubleValue();
		return Double.valueOf(val.toString());
	}

	public static float toFloat(Object val) {
		Float v = toFloatObj(val);
		return null == v ? 0f : v;
	}

	public static Float toFloatObj(Object val) {
		if (null == val) return null;
		if (val instanceof Number) return ((Number) val).floatValue();
		return Float.valueOf(val.toString());
	}

	public static boolean toBool(Object val) {
		Boolean v = toBoolObj(val);
		return null != v && v;
	}

	public static Boolean toBoolObj(Object val) {
		if (null == val) return null;
		if (val instanceof Boolean) return (Boolean) val;
		return Boolean.valueOf(val.toString());
	}

	public static byte toByte(Object val) {
		Byte v = toByteObj(val);
		return null == v ? 0 : v;
	}

	public static Byte toByteObj(Object val) {
		if (null == val) return null;
		if (val instanceof Number) return ((Number) val).byteValue();
		return Byte.valueOf(val.toString());
	}

	public static short toShort(Object val) {
		Short v = toShortObj(val);
		return null == v ? 0 : v;
	}

	public static Short toShortObj(Object val) {
		if (null == val) return null;
		if (val instanceof Number) return ((Number) val).shortValue();
		return Short.valueOf(val.toString());
	}

	public static char toChar(Object val) {
		Character v = toCharObj(val);
		return null == v ? '\0' : v;
	}

	public static Character toCharObj(Object val) {
		if (null == val) return null;
		String s = val.toString();
		return s.isEmpty() ? null : s.charAt(0);
	}

	public static BigInteger toBigInteger(Object val) {
		if (null == val) return null;
		if (val instanceof BigInteger) return (BigInteger) val;
		if (val instanceof Number) return BigInteger.valueOf(((Number) val).longValue());
		return new BigInteger(val.toString());
	}

	public static BigDecimal toBigDecimal(Object val) {
		if (null == val) return null;
		if (val instanceof BigDecimal) return (BigDecimal) val;
		return new BigDecimal(val.toString());
	}

	public static Date toDate(Object val) {
		if (null == val) return null;
		if (val instanceof Date) return (Date) val;
		if (val instanceof Number) return new Date(((Number) val).longValue());
		String s = val.toString();
		try {
			return XNWriter.DATE_FORMAT.get().parse(s);
		} catch (Exception e) {
			throw new XNException("can not parse date '" + s + "'", e);
		}
	}

	public static LocalDate toLocalDate(Object val) {
		if (null == val) return null;
		if (val instanceof LocalDate) return (LocalDate) val;
		return LocalDate.parse(val.toString(), XNWriter.LOCAL_DATE_FORMAT);
	}

	public static LocalTime toLocalTime(Object val) {
		if (null == val) return null;
		if (val instanceof LocalTime) return (LocalTime) val;
		return LocalTime.parse(val.toString(), XNWriter.LOCAL_TIME_FORMAT);
	}

	public static LocalDateTime toLocalDateTime(Object val) {
		if (null == val) return null;
		if (val instanceof LocalDateTime) return (LocalDateTime) val;
		return LocalDateTime.parse(val.toString(), XNWriter.LOCAL_DATE_TIME_FORMAT);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Enum<?> toEnum(Object val, Class<?> enumCls) {
		if (null == val) return null;
		if (enumCls.isInstance(val)) return (Enum<?>) val;
		return Enum.valueOf((Class) enumCls, val.toString());
	}

	@SuppressWarnings("unchecked")
	public static Object toBean(Object val, Class<?> cls) {
		if (null == val) return null;
		if (cls.isInstance(val)) return val;
		if (val instanceof Map) {
			// 直接消费源 Map（XMap 或任意 Map），不再复制一份中间树
			return deserializer(cls).deserialize((Map<String, Object>) val);
		}
		throw new XNException("can not convert " + val.getClass().getName() + " to " + cls.getName());
	}

	public static List<Object> toList(Object val, Class<?> elemCls) {
		if (null == val) return null;
		if (!(val instanceof List)) {
			throw new XNException("can not convert " + val.getClass().getName() + " to List");
		}
		List<?> src = (List<?>) val;
		List<Object> ret = new ArrayList<>(src.size());
		for (Object item : src) {
			ret.add(toValue(item, elemCls));
		}
		return ret;
	}

	public static Set<Object> toSet(Object val, Class<?> elemCls) {
		if (null == val) return null;
		if (!(val instanceof Collection)) {
			throw new XNException("can not convert " + val.getClass().getName() + " to Set");
		}
		Set<Object> ret = new LinkedHashSet<>();
		for (Object item : (Collection<?>) val) {
			ret.add(toValue(item, elemCls));
		}
		return ret;
	}

	public static Object toArray(Object val, Class<?> elemCls) {
		if (null == val) return null;
		if (!(val instanceof List)) {
			throw new XNException("can not convert " + val.getClass().getName() + " to array");
		}
		List<?> src = (List<?>) val;
		Object arr = Array.newInstance(elemCls, src.size());
		for (int i = 0; i < src.size(); i++) {
			Array.set(arr, i, toValue(src.get(i), elemCls));
		}
		return arr;
	}

	/** 以数组类为目标的转换（生成字节码中 ldc 数组类常量后调用） */
	public static Object toArrayOf(Object val, Class<?> arrayCls) {
		if (null == val) return null;
		return toArray(val, arrayCls.getComponentType());
	}

	/**
	 * 通用类型转换入口，按目标类型分发到具体的转换逻辑
	 */
	@SuppressWarnings("unchecked")
	public static <T> T toValue(Object val, Class<T> cls) {
		if (null == val) {
			return null;
		}
		if (cls == Object.class) {
			return (T) val;
		}
		if (cls == String.class) {
			return (T) asString(val);
		}
		if (cls.isPrimitive()) {
			if (cls == int.class) return (T) toInteger(val);
			if (cls == long.class) return (T) toLongObj(val);
			if (cls == double.class) return (T) toDoubleObj(val);
			if (cls == float.class) return (T) toFloatObj(val);
			if (cls == boolean.class) return (T) toBoolObj(val);
			if (cls == byte.class) return (T) toByteObj(val);
			if (cls == short.class) return (T) toShortObj(val);
			if (cls == char.class) return (T) toCharObj(val);
		}
		if (cls == Integer.class) return (T) toInteger(val);
		if (cls == Long.class) return (T) toLongObj(val);
		if (cls == Double.class) return (T) toDoubleObj(val);
		if (cls == Float.class) return (T) toFloatObj(val);
		if (cls == Boolean.class) return (T) toBoolObj(val);
		if (cls == Byte.class) return (T) toByteObj(val);
		if (cls == Short.class) return (T) toShortObj(val);
		if (cls == Character.class) return (T) toCharObj(val);
		if (cls == BigInteger.class) return (T) toBigInteger(val);
		if (cls == BigDecimal.class) return (T) toBigDecimal(val);
		if (Date.class.isAssignableFrom(cls)) return (T) toDate(val);
		if (cls == LocalDate.class) return (T) toLocalDate(val);
		if (cls == LocalTime.class) return (T) toLocalTime(val);
		if (cls == LocalDateTime.class) return (T) toLocalDateTime(val);
		if (cls.isEnum()) return (T) toEnum(val, cls);
		if (cls.isArray()) return (T) toArray(val, cls.getComponentType());
		if (Set.class.isAssignableFrom(cls)) return (T) toSet(val, Object.class);
		if (List.class.isAssignableFrom(cls)) return (T) toList(val, Object.class);
		if (Map.class.isAssignableFrom(cls)) {
			if (val instanceof Map) return (T) val;
			throw new XNException("can not convert " + val.getClass().getName() + " to Map");
		}
		if (cls.isInstance(val)) {
			return (T) val;
		}
		return (T) toBean(val, cls);
	}
}
