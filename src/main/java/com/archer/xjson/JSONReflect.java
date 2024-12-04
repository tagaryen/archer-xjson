package com.archer.xjson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

class JSONReflect {

	static final String BOOL_TYPE = "boolean";
	static final String BYTE_TYPE = "byte";
	static final String CHAR_TYPE = "char";
	static final String SHORT_TYPE = "short";
	static final String INT_TYPE = "int";
	static final String LONG_TYPE = "long";
	static final String FLOAT_TYPE = "float";
	static final String DOUBLE_TYPE = "double";
	

	static final String BOOL_ARR = "[Z";
	static final String BYTE_ARR = "[B";
	static final String CHAR_ARR = "[C";
	static final String SHORT_ARR = "[S";
	static final String INT_ARR = "[I";
	static final String LONG_ARR = "[J";
	static final String FLOAT_ARR = "[F";
	static final String DOUBLE_ARR = "[D";
	
	static final char GENERIC_L = '<';
	static final char GENERIC_R = '>';
	static final String COMMA = ",";

	static final DateFormat DEFAULT_DATE_FORMAT = 
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static final DateTimeFormatter DEFAULT_TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


	final ConcurrentHashMap<Class<?>, XJSONDeserializer> CODER_MAP = 
			new ConcurrentHashMap<>();
	
	static final String INNER_CLASS_FIELD = "this$";

	boolean strictJsonMode = false;
	boolean strictClassMode = false;
	
	protected JSONReflect() {}
	
	@SuppressWarnings("unchecked")
	<T> LinkedList<T> reflectOneList(LinkedList<Object> obj, Class<T> clazz) 
			throws XJSONException {
		LinkedList<T> ret = new LinkedList<T>();
		for(Object val: obj) {
			ret.add((T) fromJavaType(clazz, val));
		}
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	<T> LinkedList<T> reflectOneList(LinkedList<Object> obj, JavaTypeRef<T> ref) 
			throws XJSONException {
		LinkedList<T> ret = new LinkedList<T>();
		for(Object val: obj) {
			ret.add((T) fromJavaType(ref.getJavaType(), val));
		}
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	<T> LinkedList<T> reflectOneList(LinkedList<Object> obj, Type type) 
			throws XJSONException {
		LinkedList<T> ret = new LinkedList<T>();
		for(Object val: obj) {
			ret.add((T) fromJavaType(type, val));
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	<T> T reflectOneClass(LinkedHashMap<String, Object> obj, Class<T> clazz) 
			throws XJSONException {
		return (T) fromJavaType(clazz, obj);
	}
	
	@SuppressWarnings("unchecked")
	<T> T reflectOneClass(LinkedHashMap<String, Object> obj, JavaTypeRef<T> ref) 
			throws XJSONException {
		return (T) fromJavaType(ref.getJavaType(), obj);
	}
	
	@SuppressWarnings("unchecked")
	<T> T reflectOneClass(LinkedHashMap<String, Object> obj, Type type) 
			throws XJSONException {
		return (T) fromJavaType(type, obj);
	}
	
	void reflectToField(Field f, Object classObj, Object jsonData) 
			throws Exception {
		Type type = f.getGenericType();
		Object data = fromJavaType(type, jsonData);
		if(data == null) {
			throw new XJSONException("unknown field type '" + 
					f.getName() + "'(" + f.getType().getName()+")");
		}
		f.setAccessible(true);
		f.set(classObj, data);
	}
	
	@SuppressWarnings("unchecked")
	Object fromJavaType(Type javaType, Object val) 
			throws XJSONException {
		if(javaType.getClass().equals(Class.class)) {
			Class<?> cls = (Class<?>) javaType;
			String clsName = cls.getName();
			if(BOOL_ARR.equals(clsName)) {
				return reflectToBoolArr((LinkedList<Boolean>)val);
			}
			if(BYTE_ARR.equals(clsName)) {
				return reflectToByteArr((String)val);
			}
			if(CHAR_ARR.equals(clsName)) {
				return reflectToCharArr((LinkedList<Character>)val);
			}
			if(SHORT_ARR.equals(clsName)) {
				return reflectToShortArr((LinkedList<Long>)val);
			}
			if(INT_ARR.equals(clsName)) {
				return reflectToIntArr((LinkedList<Long>)val);
			}
			if(LONG_ARR.equals(clsName)) {
				return reflectToLongArr((LinkedList<Long>)val);
			}
			if(FLOAT_ARR.equals(clsName)) {
				return reflectToFloatArr((LinkedList<Double>)val);
			}
			if(DOUBLE_ARR.equals(clsName)) {
				return reflectToDoubleArr((LinkedList<Double>)val);
			}
			if(cls.isPrimitive()) {
				return reflectToPrimitive(cls, val);
			} else {
				return reflectToPopularClass(cls, val);
			}
		} else if(javaType instanceof ParameterizedType) {
			ParameterizedType pType = (ParameterizedType)javaType;
			Class<?> cls = (Class<?>)pType.getRawType();
			if(Collection.class.isAssignableFrom(cls)) {
				return reflectCollection(cls, (ParameterizedType)javaType, 
						(LinkedList<Object>) val);
			}
			if(Map.class.isAssignableFrom(cls)) {
				return reflectMap(cls, (ParameterizedType)javaType, 
						(LinkedHashMap<String, Object>) val);
			}
			return reflectUnknownClass(cls, (LinkedHashMap<String, Object>)val, 
					pType.getActualTypeArguments());
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	Collection<Object> reflectCollection(Class<?> cls, 
			ParameterizedType javaType, LinkedList<Object> val) 
			throws XJSONException {
		Collection<Object> instance;
		if(List.class.isAssignableFrom(cls)) {
			if(cls.isInterface()) {
				instance = new LinkedList<>();
			} else {
				try {
					instance = (Collection<Object>) cls.newInstance();
				} catch(Exception ignore) {
					throw new XJSONException("can not construct class '" +
							cls.getName() + "'");
				}
			}
		} else if(Set.class.isAssignableFrom(cls)) {
			if(cls.isInterface()) {
				instance = new LinkedHashSet<>();
			} else {
				try {
					instance = (Collection<Object>) cls.newInstance();
				} catch(Exception ignore) {
					throw new XJSONException("can not construct class '" +
							cls.getName() + "'");
				}
			}
		} else if(Queue.class.isAssignableFrom(cls)) {
			if(cls.isInterface()) {
				instance = new ArrayBlockingQueue<>(val.size());
			} else {
				try {
					instance = (Collection<Object>) cls.newInstance();
				} catch(Exception ignore) {
					throw new XJSONException("can not construct class '" +
							cls.getName() + "'");
				}
			}
		} else {
			throw new XJSONException("unknown collection like type '" +
					cls.getName() + "'");
		}
		Type[] genericTypes = javaType.getActualTypeArguments();
		if(genericTypes == null || genericTypes.length != 1) {
			throw new XJSONException("invalid collection like type '" + 
						javaType.getTypeName() + "'");
		}
		for(Object o: val) {
			instance.add(fromJavaType(genericTypes[0], o));
		}
		return instance;
	}
	

	@SuppressWarnings("unchecked")
	Map<Object, Object> reflectMap(Class<?> cls, 
			ParameterizedType javaType, LinkedHashMap<String, Object> val) 
			throws XJSONException {
		Map<Object, Object> instance;
		if(cls.isInterface()) {
			instance = new LinkedHashMap<>();
		} else {
			try {
				instance = (Map<Object, Object>) cls.newInstance();
			} catch(Exception ignore) {
				throw new XJSONException("can not construct class '" +
						cls.getName() + "'");
			}
		}
		Type[] genericTypes = ((ParameterizedType) javaType).getActualTypeArguments();
		if(genericTypes == null || genericTypes.length != 2) {
			throw new XJSONException("invalid collection like type '" + 
						javaType.getTypeName() + "'");
		}
		for(Map.Entry<String, Object> entry: val.entrySet()) {
			instance.put(fromJavaType(genericTypes[0], entry.getKey()),
					fromJavaType(genericTypes[1], entry.getValue()));
		}
		
		return instance;
	}
	
	static boolean[] reflectToBoolArr(LinkedList<Boolean> val) {
		boolean[] ret = new boolean[val.size()];
		int i = 0;
		for(Boolean b: val) {
			ret[i++] = b.booleanValue();
		}
		return ret;
	}

	static byte[] reflectToByteArr(String val) {
		return Base64.getDecoder().decode(val.getBytes());
//		int i = 0;
//		for(Long b: val) {
//			ret[i++] = (byte) b.intValue();
//		}
//		return ret;
	}

	static char[] reflectToCharArr(LinkedList<Character> val) {
		char[] ret = new char[val.size()];
		int i = 0;
		for(Character b: val) {
			ret[i++] = b.charValue();
		}
		return ret;
	}

	static short[] reflectToShortArr(LinkedList<Long> val) {
		short[] ret = new short[val.size()];
		int i = 0;
		for(Long b: val) {
			ret[i++] = b.shortValue();
		}
		return ret;
	}

	static int[] reflectToIntArr(LinkedList<Long> val) {
		int[] ret = new int[val.size()];
		int i = 0;
		for(Long b: val) {
			ret[i++] = b.intValue();
		}
		return ret;
	}

	static long[] reflectToLongArr(LinkedList<Long> val) {
		long[] ret = new long[val.size()];
		int i = 0;
		for(Long b: val) {
			ret[i++] = b.longValue();
		}
		return ret;
	}

	static float[] reflectToFloatArr(LinkedList<Double> val) {
		float[] ret = new float[val.size()];
		int i = 0;
		for(Double b: val) {
			ret[i++] = b.floatValue();
		}
		return ret;
	}

	static double[] reflectToDoubleArr(LinkedList<Double> val) {
		double[] ret = new double[val.size()];
		int i = 0;
		for(Double b: val) {
			ret[i++] = b.doubleValue();
		}
		return ret;
	}
	
	static Object reflectToPrimitive(Class<?> cls,  Object val) 
			throws XJSONException {
		if(BOOL_TYPE.equals(cls.getName())) {
			return (boolean) val;
		} else if(BYTE_TYPE.equals(cls.getName())) {
			if(val instanceof Long) {
				return ((Long) val).byteValue();
			}
			return (byte)((int) val);
		} else if(CHAR_TYPE.equals(cls.getName())) {
			return (char) val;
		} else if(SHORT_TYPE.equals(cls.getName())) {
			if(val instanceof Long) {
				return ((Long) val).shortValue();
			}
			return (short)((int) val);
		} else if(INT_TYPE.equals(cls.getName())) {
			if(val instanceof Long) {
				return ((Long) val).intValue();
			}
			return (int) val;
		} else if(LONG_TYPE.equals(cls.getName())) {
			return (long) val;
		} else if(FLOAT_TYPE.equals(cls.getName())) {
			if(val instanceof Double) {
				return ((Double) val).floatValue();
			}
			return (float) val;
		} else if(DOUBLE_TYPE.equals(cls.getName())) {
			return (double) val;
		}
		throw new XJSONException("unknown primitive type '" 
				+ cls.getName() + "'");
	}
	
	@SuppressWarnings("unchecked")
	Object reflectToPopularClass(Class<?> cls,  Object val) 
			throws XJSONException {
		if(cls.equals(String.class)) {
			return (String) val;
		}
		if(cls.equals(Byte.class)) {
			if(Integer.class.equals(val.getClass())) {
				return ((Integer) val).byteValue();
			}
			return ((Long) val).byteValue();
		}
		if(cls.equals(Character.class)) {
			return (Character) val;
		}
		if(cls.equals(Integer.class)) {
			if(Integer.class.equals(val.getClass())) {
				return (Integer) val;
			}
			return ((Long) val).intValue();
		}
		if(cls.equals(Long.class)) {
			return (Long) val;
		}
		if(cls.equals(Float.class)) {
			return ((Double) val).floatValue();
		}
		if(cls.equals(Double.class)) {
			return (Double) val;
		}
		if(Date.class.isAssignableFrom(cls)) {
			try {
				return DEFAULT_DATE_FORMAT.parse((String)val);
			} catch(Exception ignore) {
				throw new XJSONException("can not parse '"+
						((String) val)+"' to Date");
			}
		} else if(cls.equals(LocalDate.class)) {
			try {
				return LocalDate.parse((String)val, DEFAULT_TIME_FORMAT);
			} catch(Exception ignore) {
				throw new XJSONException("can not parse '"+
						((String) val)+"' to LocalDate");
			}
		} else if(cls.equals(LocalTime.class)) {
			try {
				return LocalTime.parse((String)val, DEFAULT_TIME_FORMAT);
			} catch(Exception ignore) {
				throw new XJSONException("can not parse '"+
						((String) val)+"' to LocalTime");
			}
		} else if(cls.equals(LocalDateTime.class)) {
			try {
				return LocalDateTime.parse((String)val, DEFAULT_TIME_FORMAT);
			} catch(Exception ignore) {
				throw new XJSONException("can not parse '"+
						((String) val)+"' to LocalDateTime");
			}
		} else if(cls.equals(BigInteger.class)) {
			try {
				return BigInteger.valueOf((long) val);
			} catch(Exception ignore) {
				throw new XJSONException("can not parse '"+
						((String) val)+"' to BigInteger");
			}
		} else if(cls.equals(BigDecimal.class)) {
			try {
				return BigDecimal.valueOf((double) val);
			} catch(Exception ignore) {
				throw new XJSONException("can not parse '"+
						((String) val)+"' to BigDecimal");
			}
		}
		if(val instanceof LinkedHashMap) {
			return reflectUnknownClass(cls, (LinkedHashMap<String, Object>) val, null);
		}
		return val;
	}
	
	Object reflectUnknownClass(Class<?> cls, LinkedHashMap<String, Object> val, Type[] childTypes) 
			throws XJSONException {
		Object instance = newInstance(cls);
		Field[] fields = JSONCache.get(cls);
		if(fields == null) {
			fields = cls.getDeclaredFields();
			JSONCache.save(cls, fields);
		}
		TreeSet<String> fieldNameSet = null;
		if(strictJsonMode) {
			fieldNameSet = new TreeSet<>();
		}
		String[] generics = null;
		if(childTypes != null) {
			generics = parseGenericString(cls.toGenericString());
		}
		int count = 0;
		for(Field f: fields) {
			if(f.getName().startsWith(INNER_CLASS_FIELD)) {
				continue;
			}
			if(Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			if(Modifier.isTransient(f.getModifiers())) {
				continue;
			}
			count++;
			Object fieldVal = val.getOrDefault(f.getName(), null);
			if(fieldVal != null) {
				Type ft = f.getGenericType();
				if(childTypes != null && ft instanceof TypeVariable) {
					for(int i = 0; i < childTypes.length; i++) {
						if(generics[i].equals(f.getGenericType().getTypeName())) {
							ft = childTypes[i];
							break ;
						}
					}
				}
				try {
					f.setAccessible(true);
					f.set(instance, fromJavaType(ft, fieldVal));
				} catch(Exception e) {
					e.printStackTrace();
					throw new XJSONException(
							XJSONException.getErrorMsg(f, val.get(f.getName())));
				}
			} else {
				if(strictClassMode) {
					throw new XJSONException(
							XJSONException.getErrorMsg(f.getName(), cls, true));
				}
			}
			if(strictJsonMode) {
				fieldNameSet.add(f.getName());
			}
		}
		if(count < val.size()) {
			reflectToSupperCls(cls.getSuperclass(), val, childTypes, count, fieldNameSet, instance);
		}
		
		if(strictJsonMode) {
			for(String k: val.keySet()) {
				if(!fieldNameSet.contains(k)) {
					throw new XJSONException(
							XJSONException.getErrorMsg(k, cls, false));
				}
			}
		}
		return instance;
	}
	
	void reflectToSupperCls(Class<?> superCls, LinkedHashMap<String, Object> val, Type[] childTypes, int oldCount, TreeSet<String> fieldNameSet, Object instance) {
		if(Object.class.equals(superCls)) {
			return ;
		}
		Field[] fields = JSONCache.get(superCls);
		if(fields == null) {
			fields = superCls.getDeclaredFields();
			JSONCache.save(superCls, fields);
		}
		if(strictJsonMode) {
			fieldNameSet = new TreeSet<>();
		}
		String[] generics = null;
		if(childTypes != null) {
			generics = parseGenericString(superCls.toGenericString());
		}
		int count = oldCount;
		for(Field f: fields) {
			if(f.getName().startsWith(INNER_CLASS_FIELD)) {
				continue;
			}
			if(Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			if(Modifier.isTransient(f.getModifiers())) {
				continue;
			}
			count++;
			Object fieldVal = val.getOrDefault(f.getName(), null);
			if(fieldVal != null) {
				Type ft = f.getGenericType();
				if(childTypes != null && ft instanceof TypeVariable) {
					for(int i = 0; i < childTypes.length; i++) {
						if(generics[i].equals(f.getGenericType().getTypeName())) {
							ft = childTypes[i];
							break ;
						}
					}
				}
				try {
					f.setAccessible(true);
					f.set(instance, fromJavaType(ft, fieldVal));
				} catch(Exception e) {
					e.printStackTrace();
					throw new XJSONException(
							XJSONException.getErrorMsg(f, val.get(f.getName())));
				}
			} else {
				if(strictClassMode) {
					throw new XJSONException(
							XJSONException.getErrorMsg(f.getName(), superCls, true));
				}
			}
			if(strictJsonMode) {
				fieldNameSet.add(f.getName());
			}
		}
		if(count < val.size()) {
			reflectToSupperCls(superCls.getSuperclass(), val, childTypes, count, fieldNameSet, instance);
		}
	}
	
	static String[] parseGenericString(String genericClassString) {
		char[] chars = genericClassString.toCharArray();
		if(chars[chars.length - 1] != GENERIC_R) {
			throw new XJSONException("invalid generic type " + genericClassString);
		}
		int i = 0;
		for(; i < chars.length - 2; i++) {
			if(chars[i] == GENERIC_L) {
				i++;
				break ;
			}
		}
		String generic = new String(Arrays.copyOfRange(chars, i, chars.length - 1));
		return generic.split(COMMA);
	}
	
	static Object newInstance(Class<?> cls) {
		XJSONConstructor xjsonConstructor = JSONCache.getConstructor(cls);
		if(xjsonConstructor != null) {
			return xjsonConstructor.newInstance();
		}
		Constructor<?>[] constructors = cls.getConstructors();
		for(Constructor<?> constructor : constructors) {
			Object[] params = null;
			if(constructor.getParameterCount() == 0) {
				params = new Object[0];
			} else if(cls.isMemberClass() && constructor.getParameterCount() == 1) { //non-static inner class
				params = new Object[1];
			}
			if(params != null) {
				JSONCache.saveConstructor(cls, constructor, params);
				try {
					constructor.setAccessible(true);
					return constructor.newInstance(params);
				} catch (Exception e) {
					throw new RuntimeException("can not construct class '" +
							cls.getName() + "'", e);
				}
			}
		}
		throw new XJSONException(
				"no arguments constructor is required with class '" 
				+ cls.getName() + "'"); 
	}
}
