package com.archer.xjson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

class JSONReflect {

	/** 线程安全：SimpleDateFormat 非线程安全，改为每线程一份 */
	static final ThreadLocal<SimpleDateFormat> DEFAULT_DATE_FORMAT =
			ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
	static final DateTimeFormatter DEFAULT_TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	final ConcurrentHashMap<Class<?>, XJSONDeserializer> CODER_MAP =
			new ConcurrentHashMap<>();

	boolean strictJsonMode = false;
	boolean strictClassMode = false;

	JSONReflect() {}

	@SuppressWarnings("unchecked")
	<T> ArrayList<T> reflectOneList(ArrayList<Object> obj, Class<T> clazz)
			throws XJSONException {
		ArrayList<T> ret = new ArrayList<>(obj.size());
		for (Object val : obj) {
			ret.add((T) fromJavaType(clazz, val));
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	<T> ArrayList<T> reflectOneList(ArrayList<Object> obj, JavaTypeRef<T> ref)
			throws XJSONException {
		Type type = ref.getJavaType();
		ArrayList<T> ret = new ArrayList<>(obj.size());
		for (Object val : obj) {
			ret.add((T) fromJavaType(type, val));
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	<T> ArrayList<T> reflectOneList(ArrayList<Object> obj, Type type)
			throws XJSONException {
		ArrayList<T> ret = new ArrayList<>(obj.size());
		for (Object val : obj) {
			ret.add((T) fromJavaType(type, val));
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	<T> T reflectOneClass(XMap<String, Object> obj, Class<T> clazz)
			throws XJSONException {
		return (T) fromJavaType(clazz, obj);
	}

	@SuppressWarnings("unchecked")
	<T> T reflectOneClass(XMap<String, Object> obj, JavaTypeRef<T> ref)
			throws XJSONException {
		return (T) fromJavaType(ref.getJavaType(), obj);
	}

	@SuppressWarnings("unchecked")
	<T> T reflectOneClass(XMap<String, Object> obj, Type type)
			throws XJSONException {
		return (T) fromJavaType(type, obj);
	}

	@SuppressWarnings("unchecked")
	public Object fromJavaType(Type javaType, Object val)
			throws XJSONException {
		if (javaType instanceof Class) {
			Class<?> cls = (Class<?>) javaType;
			if (cls.isArray()) {
				String clsName = cls.getName();
				switch (clsName) {
				case "[Z":
					return reflectToBoolArr((ArrayList<Boolean>) val);
				case "[B":
					return reflectToByteArr((String) val);
				case "[C":
					return reflectToCharArr((ArrayList<Character>) val);
				case "[S":
					return reflectToShortArr((ArrayList<Long>) val);
				case "[I":
					return reflectToIntArr((ArrayList<Long>) val);
				case "[J":
					return reflectToLongArr((ArrayList<Long>) val);
				case "[F":
					return reflectToFloatArr((ArrayList<Double>) val);
				case "[D":
					return reflectToDoubleArr((ArrayList<Double>) val);
				default:
					break;
				}
			}
			if (cls.isPrimitive()) {
				return reflectToPrimitive(cls, val);
			}
			if (cls.isEnum()) {
				return reflectToEnum((Class<Enum<?>>) cls, (String) val);
			}
			return reflectToPopularClass(cls, val);
		}
		if (javaType instanceof ParameterizedType) {
			ParameterizedType pType = (ParameterizedType) javaType;
			Class<?> cls = (Class<?>) pType.getRawType();
			if (Collection.class.isAssignableFrom(cls)) {
				return reflectCollection(cls, pType, (ArrayList<Object>) val);
			}
			if (Map.class.isAssignableFrom(cls)) {
				return reflectMap(cls, pType, (XMap<String, Object>) val);
			}
			return reflectUnknownClass(cls, (XMap<String, Object>) val,
					pType.getActualTypeArguments());
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	Collection<Object> reflectCollection(Class<?> cls,
			ParameterizedType javaType, ArrayList<Object> val)
			throws XJSONException {
		Collection<Object> instance;
		if (List.class.isAssignableFrom(cls)) {
			if (cls.isInterface()) {
				instance = new ArrayList<>(val.size());
			} else {
				try {
					instance = (Collection<Object>) newInstance(cls);
				} catch (Exception ignore) {
					throw new XJSONException("can not construct class '" +
							cls.getName() + "'");
				}
			}
		} else if (Set.class.isAssignableFrom(cls)) {
			if (cls.isInterface()) {
				instance = new LinkedHashSet<>();
			} else {
				try {
					instance = (Collection<Object>) newInstance(cls);
				} catch (Exception ignore) {
					throw new XJSONException("can not construct class '" +
							cls.getName() + "'");
				}
			}
		} else if (Queue.class.isAssignableFrom(cls)) {
			if (cls.isInterface()) {
				instance = new ArrayBlockingQueue<>(val.size());
			} else {
				try {
					instance = (Collection<Object>) newInstance(cls);
				} catch (Exception ignore) {
					throw new XJSONException("can not construct class '" +
							cls.getName() + "'");
				}
			}
		} else {
			throw new XJSONException("unknown collection like type '" +
					cls.getName() + "'");
		}
		Type[] genericTypes = javaType.getActualTypeArguments();
		if (genericTypes == null || genericTypes.length != 1) {
			throw new XJSONException("invalid collection like type '" +
					javaType.getTypeName() + "'");
		}
		Type elemType = genericTypes[0];
		for (Object o : val) {
			instance.add(fromJavaType(elemType, o));
		}
		return instance;
	}

	@SuppressWarnings("unchecked")
	Map<Object, Object> reflectMap(Class<?> cls,
			ParameterizedType javaType, XMap<String, Object> val)
			throws XJSONException {
		Map<Object, Object> instance;
		if (cls.isInterface()) {
			instance = new XMap<>();
		} else {
			try {
				instance = (Map<Object, Object>) newInstance(cls);
			} catch (Exception ignore) {
				throw new XJSONException("can not construct class '" +
						cls.getName() + "'");
			}
		}
		Type[] genericTypes = javaType.getActualTypeArguments();
		if (genericTypes == null || genericTypes.length != 2) {
			throw new XJSONException("invalid collection like type '" +
					javaType.getTypeName() + "'");
		}
		Type keyType = genericTypes[0];
		Type valType = genericTypes[1];
		for (Map.Entry<String, Object> entry : val.entrySet()) {
			instance.put(fromJavaType(keyType, entry.getKey()),
					fromJavaType(valType, entry.getValue()));
		}
		return instance;
	}

	static boolean[] reflectToBoolArr(ArrayList<Boolean> val) {
		boolean[] ret = new boolean[val.size()];
		for (int i = 0; i < val.size(); i++) {
			ret[i] = val.get(i).booleanValue();
		}
		return ret;
	}

	static byte[] reflectToByteArr(String val) {
		return Base64.getDecoder().decode(val.getBytes());
	}

	static char[] reflectToCharArr(ArrayList<Character> val) {
		char[] ret = new char[val.size()];
		for (int i = 0; i < val.size(); i++) {
			ret[i] = val.get(i).charValue();
		}
		return ret;
	}

	static short[] reflectToShortArr(ArrayList<Long> val) {
		short[] ret = new short[val.size()];
		for (int i = 0; i < val.size(); i++) {
			ret[i] = val.get(i).shortValue();
		}
		return ret;
	}

	static int[] reflectToIntArr(ArrayList<Long> val) {
		int[] ret = new int[val.size()];
		for (int i = 0; i < val.size(); i++) {
			ret[i] = val.get(i).intValue();
		}
		return ret;
	}

	static long[] reflectToLongArr(ArrayList<Long> val) {
		long[] ret = new long[val.size()];
		for (int i = 0; i < val.size(); i++) {
			ret[i] = val.get(i).longValue();
		}
		return ret;
	}

	static float[] reflectToFloatArr(ArrayList<Double> val) {
		float[] ret = new float[val.size()];
		for (int i = 0; i < val.size(); i++) {
			ret[i] = val.get(i).floatValue();
		}
		return ret;
	}

	static double[] reflectToDoubleArr(ArrayList<Double> val) {
		double[] ret = new double[val.size()];
		for (int i = 0; i < val.size(); i++) {
			ret[i] = val.get(i).doubleValue();
		}
		return ret;
	}

	static Object reflectToPrimitive(Class<?> cls, Object val)
			throws XJSONException {
		if (cls == boolean.class) {
			return (boolean) val;
		}
		if (cls == byte.class) {
			if (val instanceof Long) {
				return ((Long) val).byteValue();
			}
			return (byte) ((int) val);
		}
		if (cls == char.class) {
			return (char) val;
		}
		if (cls == short.class) {
			if (val instanceof Long) {
				return ((Long) val).shortValue();
			}
			return (short) ((int) val);
		}
		if (cls == int.class) {
			if (val instanceof Long) {
				return ((Long) val).intValue();
			}
			return (int) val;
		}
		if (cls == long.class) {
			if (val instanceof Integer) {
				return ((Integer) val).longValue();
			}
			return (long) val;
		}
		if (cls == float.class) {
			if (val instanceof Double) {
				return ((Double) val).floatValue();
			}
			return (float) val;
		}
		if (cls == double.class) {
			if (val instanceof Float) {
				return ((Float) val).doubleValue();
			}
			return (double) val;
		}
		throw new XJSONException("unknown primitive type '"
				+ cls.getName() + "'");
	}

	static Object reflectToEnum(Class<Enum<?>> cls, String val) {
		for (Enum<?> f : cls.getEnumConstants()) {
			if (f.name().equals(val)) {
				return f;
			}
		}
		throw new XJSONException("unknown enum type '"
				+ val + "' in '" + cls.getName() + "'");
	}

	@SuppressWarnings("unchecked")
	Object reflectToPopularClass(Class<?> cls, Object val)
			throws XJSONException {
		if (cls == String.class) {
			return (String) val;
		}
		if (cls == Byte.class) {
			if (val instanceof Integer) {
				return ((Integer) val).byteValue();
			}
			return ((Long) val).byteValue();
		}
		if (cls == Character.class) {
			return (Character) val;
		}
		if (cls == Integer.class) {
			if (val instanceof Integer) {
				return (Integer) val;
			}
			return ((Long) val).intValue();
		}
		if (cls == Long.class) {
			if (val instanceof Integer) {
				return ((Integer) val).longValue();
			}
			return (Long) val;
		}
		if (cls == Float.class) {
			if (val instanceof Float) {
				return (Float) val;
			}
			return ((Double) val).floatValue();
		}
		if (cls == Double.class) {
			if (val instanceof Float) {
				return ((Float) val).doubleValue();
			}
			return (Double) val;
		}
		if (cls == Short.class) {
			if (val instanceof Long) {
				return ((Long) val).shortValue();
			}
			return ((Integer) val).shortValue();
		}
		if (Date.class.isAssignableFrom(cls)) {
			try {
				return DEFAULT_DATE_FORMAT.get().parse((String) val);
			} catch (Exception ignore) {
				throw new XJSONException("can not parse '" +
						((String) val) + "' to Date");
			}
		}
		if (cls == LocalDate.class) {
			try {
				return LocalDate.parse((String) val, DEFAULT_TIME_FORMAT);
			} catch (Exception ignore) {
				throw new XJSONException("can not parse '" +
						((String) val) + "' to LocalDate");
			}
		}
		if (cls == LocalTime.class) {
			try {
				return LocalTime.parse((String) val, DEFAULT_TIME_FORMAT);
			} catch (Exception ignore) {
				throw new XJSONException("can not parse '" +
						((String) val) + "' to LocalTime");
			}
		}
		if (cls == LocalDateTime.class) {
			try {
				return LocalDateTime.parse((String) val, DEFAULT_TIME_FORMAT);
			} catch (Exception ignore) {
				throw new XJSONException("can not parse '" +
						((String) val) + "' to LocalDateTime");
			}
		}
		if (cls == BigInteger.class) {
			try {
				if (val instanceof Long) {
					return BigInteger.valueOf((Long) val);
				}
				if (val instanceof Integer) {
					return BigInteger.valueOf((Integer) val);
				}
				return new BigInteger(val.toString());
			} catch (Exception ignore) {
				throw new XJSONException("can not parse '" +
						val + "' to BigInteger");
			}
		}
		if (cls == BigDecimal.class) {
			try {
				if (val instanceof Double) {
					return BigDecimal.valueOf((Double) val);
				}
				if (val instanceof Long) {
					return BigDecimal.valueOf((Long) val);
				}
				if (val instanceof Integer) {
					return BigDecimal.valueOf((Integer) val);
				}
				return new BigDecimal(val.toString());
			} catch (Exception ignore) {
				throw new XJSONException("can not parse '" +
						val + "' to BigDecimal");
			}
		}
		if (val instanceof XMap) {
			return reflectUnknownClass(cls, (XMap<String, Object>) val, null);
		}
		return val;
	}

	Object reflectUnknownClass(Class<?> cls, XMap<String, Object> val, Type[] childTypes)
			throws XJSONException {
		Object instance = newInstance(cls);
		TreeSet<String> fieldNameSet = strictJsonMode ? new TreeSet<String>() : null;
		String[] generics = childTypes != null ? parseGenericTypeNames(cls) : null;
		// 先子类后父类逐层映射，保留原有的"本层映射满则跳过父层"语义
		reflectFields(cls, val, childTypes, generics, fieldNameSet, instance);
		if (strictJsonMode) {
			for (String k : val.keySet()) {
				if (!fieldNameSet.contains(k)) {
					throw new XJSONException(
							XJSONException.getErrorMsg(k, cls, false));
				}
			}
		}
		return instance;
	}

	private void reflectFields(Class<?> cls, XMap<String, Object> val, Type[] childTypes,
			String[] generics, TreeSet<String> fieldNameSet, Object instance) {
		if (cls == null || Object.class.equals(cls)) {
			return;
		}
		Field[] fields = JSONCache.getMeta(cls).fields;
		Class<?> lastDecl = null;
		String[] declGenerics = generics;
		for (Field f : fields) {
			Object fieldVal = val.getOrDefault(f.getName(), null);
			if (fieldVal != null) {
				Type ft = f.getGenericType();
				if (childTypes != null && ft instanceof TypeVariable) {
					// 泛型按字段所属的声明类解析，父类字段使用父类自己的类型变量
					Class<?> decl = f.getDeclaringClass();
					if (decl != lastDecl) {
						declGenerics = parseGenericTypeNames(decl);
						lastDecl = decl;
					}
					for (int i = 0; i < childTypes.length && i < declGenerics.length; i++) {
						if (declGenerics[i].equals(ft.getTypeName())) {
							ft = childTypes[i];
							break;
						}
					}
				}
				try {
					f.set(instance, fromJavaType(ft, fieldVal));
				} catch (Exception e) {
					if (strictClassMode) {
						throw new XJSONException(
								XJSONException.getErrorMsg(f, val.get(f.getName())));
					}
				}
			} else {
				if (strictClassMode) {
					throw new XJSONException(
							XJSONException.getErrorMsg(f.getName(), cls, true));
				}
			}
			if (strictJsonMode) {
				fieldNameSet.add(f.getName());
			}
		}
	}

	/** 提取类声明的类型变量名列表，结果缓存避免每次 toGenericString 文本解析 */
	static String[] parseGenericTypeNames(Class<?> cls) {
		TypeVariable<?>[] vars = cls.getTypeParameters();
		String[] names = new String[vars.length];
		for (int i = 0; i < vars.length; i++) {
			names[i] = vars[i].getName();
		}
		return names;
	}

	static Object newInstance(Class<?> cls) {
		XJSONConstructor xjsonConstructor = JSONCache.getConstructor(cls);
		if (xjsonConstructor != null) {
			return xjsonConstructor.newInstance();
		}
		Constructor<?>[] constructors = cls.getConstructors();
		if (constructors.length < 1) {
			throw new XJSONException("can not found constructor in class '" +
					cls.getName() + "'");
		}
		Constructor<?> constructor = constructors[0];
		int paramCount = constructors[0].getParameterCount();
		for (int i = 1; i < constructors.length; i++) {
			if (constructors[i].getParameterCount() < paramCount) {
				paramCount = constructors[i].getParameterCount();
				constructor = constructors[i];
			}
		}
		constructor.setAccessible(true);
		Object[] params = new Object[paramCount];
		JSONCache.saveConstructor(cls, constructor, params);
		try {
			return constructor.newInstance(params);
		} catch (Exception e) {
			throw new XJSONException("can not construct class '" +
					cls.getName() + "'", e);
		}
	}
}
