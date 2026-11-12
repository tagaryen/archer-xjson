package com.archer.xjson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class JSONCache {

	static final String INNER_CLASS_FIELD = "this$";

	static final ClassValue<ClassMeta> META_CACHE = new ClassValue<ClassMeta>() {
		@Override
		protected ClassMeta computeValue(Class<?> cls) {
			return buildMeta(cls);
		}
	};

	static final ConcurrentHashMap<Class<?>, XJSONConstructor> CONSTRUCTOR_CACHE =
			new ConcurrentHashMap<>();

	static ClassMeta getMeta(Class<?> cls) {
		return META_CACHE.get(cls);
	}

	static Field[] get(Class<?> cls) {
		return META_CACHE.get(cls).fields;
	}

	static void save(Class<?> cls, Field[] fields) {
		// ClassValue 按需自动计算，无需外部写入
	}

	static XJSONConstructor getConstructor(Class<?> cls) {
		return CONSTRUCTOR_CACHE.getOrDefault(cls, null);
	}

	static void saveConstructor(Class<?> cls, Constructor<?> constructor, Object[] params) {
		CONSTRUCTOR_CACHE.put(cls, new XJSONConstructor(cls, constructor, params));
	}

	private static ClassMeta buildMeta(Class<?> cls) {
		// 保持与原实现一致的字段顺序：父类字段在前，子类字段在后
		List<Class<?>> hierarchy = new ArrayList<>(4);
		Class<?> cur = cls;
		while (cur != null && !Object.class.equals(cur)) {
			hierarchy.add(cur);
			cur = cur.getSuperclass();
		}
		List<Field> collected = new ArrayList<>(32);
		for (int i = hierarchy.size() - 1; i >= 0; i--) {
			Field[] fields = hierarchy.get(i).getDeclaredFields();
			for (Field f : fields) {
				if (f.getName().startsWith(INNER_CLASS_FIELD)) {
					continue;
				}
				int mod = f.getModifiers();
				if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
					continue;
				}
				f.setAccessible(true);
				collected.add(f);
			}
		}
		return new ClassMeta(cls, collected.toArray(new Field[0]));
	}
}
