package com.archer.xjson.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.archer.xjson.asm.constantpool.ConstantPool;


class XNDeserializerFactory {

	private static final AtomicInteger SEQ = new AtomicInteger();

	private static final String GEN_PKG = "com/archer/xjson/asm/gen";
	private static final String RUNTIME = "com/archer/xjson/asm/XNJSONRuntime";
	private static final String MAP = "java/util/Map";
	private static final String GET_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";

	static XNDeserializer create(Class<?> beanCls) {
		String genName = safeName(beanCls) + "$XNDeser$" + SEQ.incrementAndGet();
		ClassBytecode cb = new ClassBytecode();
		cb.toEmptyClass(genName, GEN_PKG);
		cb.setAccessFlag(0x0021); // public super
		ConstantPool cp = cb.getConstantPool();
		cb.addInterfaceIndexArr(cp.addClass("com/archer/xjson/asm/XNDeserializer"));
		int codeIdx = cp.findName("Code");

		XNEmitter em = new XNEmitter(cp, codeIdx, 3);
		String owner = raw(beanCls);

		// EntityObj c = new EntityObj();
		if (hasPublicNoArgCtor(beanCls)) {
			em.newObject(owner);
			em.dup();
			em.invokespecial(owner, "<init>", "()V");
			em.astore(2);
		} else {
			em.ldcClass(owner);
			em.invokestatic(RUNTIME, "newInstance", "(Ljava/lang/Class;)Ljava/lang/Object;");
			em.checkcast(owner);
			em.astore(2);
		}

		for (Field f : XNJSONRuntime.fieldsOf(beanCls)) {
			Method setter = findSetter(beanCls, f);
			if (null != setter) {
				Class<?> pt = setter.getParameterTypes()[0];
				// c.setXxx( convert(map.get("field")) );
				em.aload(2);
				em.aload(1);
				em.ldcString(f.getName());
				em.invokeinterface(MAP, "get", GET_DESC);
				emitConvert(em, pt, f);
				em.invokevirtual(owner, setter.getName(), "(" + XNSerializerFactory.desc(pt) + ")V");
			} else {
				// XNJSONRuntime.setFieldReflect(c, "field", map.get("field"));
				em.aload(2);
				em.ldcString(f.getName());
				em.aload(1);
				em.ldcString(f.getName());
				em.invokeinterface(MAP, "get", GET_DESC);
				em.invokestatic(RUNTIME, "setFieldReflect",
						"(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V");
			}
		}

		em.aload(2);
		em.areturn();

		cb.addMethod("deserialize", new String[] { "Ljava/util/Map;" },
				"Ljava/lang/Object;", em.toCodeAttribute());

		try {
			return (XNDeserializer) cb.loadSelfClass().newInstance();
		} catch (Exception e) {
			throw new XNException("can not generate deserializer for " + beanCls.getName(), e);
		}
	}

	private static final String READER = "com/archer/xjson/asm/XNReader";
	private static final String FIELD_FILLER = "com/archer/xjson/asm/XNFieldFiller";
	private static final String READ_FIELD_DESC =
			"(Ljava/lang/Object;Ljava/lang/String;Lcom/archer/xjson/asm/XNReader;)V";

	/** 生成流式字段填充器：fill(Object, String, XNReader) 返回是否命中，命中即边读边写 setter */
	static XNFieldFiller createFiller(Class<?> beanCls) {
		String genName = safeName(beanCls) + "$XNFiller$" + SEQ.incrementAndGet();
		ClassBytecode cb = new ClassBytecode();
		cb.toEmptyClass(genName, GEN_PKG);
		cb.setAccessFlag(0x0021); // public super
		ConstantPool cp = cb.getConstantPool();
		cb.addInterfaceIndexArr(cp.addClass(FIELD_FILLER));
		int codeIdx = cp.findName("Code");

		String owner = raw(beanCls);

		XNEmitter em = new XNEmitter(cp, codeIdx, 4);
		List<Field> fields = XNJSONRuntime.fieldsOf(beanCls);
		XNEmitter.Label end = em.newLabel();

		if (fields.isEmpty()) {
			em.iconst(0);
			em.ireturn();
		} else {
			// switch (field.hashCode()) —— O(1) 分派替代 equals 链；
			// 同 hash 分组内仍用 equals 逐个确认，哈希冲突安全
			em.aload(2);
			em.invokevirtual("java/lang/String", "hashCode", "()I");

			Map<Integer, List<Field>> buckets = new TreeMap<>();
			for (Field f : fields) {
				List<Field> group = buckets.get(f.getName().hashCode());
				if (null == group) {
					group = new ArrayList<>(32);
					buckets.put(f.getName().hashCode(), group);
				}
				group.add(f);
			}
			int[] keys = new int[buckets.size()];
			XNEmitter.Label[] caseLabels = new XNEmitter.Label[buckets.size()];
			int ci = 0;
			for (Integer h : buckets.keySet()) {
				keys[ci] = h;
				caseLabels[ci] = em.newLabel();
				ci++;
			}
			em.lookupswitch(end, keys, caseLabels);

			for (int i = 0; i < keys.length; i++) {
				em.mark(caseLabels[i]);
				List<Field> group = buckets.get(keys[i]);
				for (int j = 0; j < group.size(); j++) {
					Field f = group.get(j);
					XNEmitter.Label miss = (j + 1 < group.size()) ? em.newLabel() : end;

					// if (field.equals("name"))
					em.aload(2);
					em.ldcString(f.getName());
					em.invokevirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
					em.ifeq(miss);

					Method setter = findSetter(beanCls, f);
					Class<?> pt = (null != setter) ? setter.getParameterTypes()[0] : f.getType();

					if (null != setter) {
						// bean.setXxx(...): 全部类型直读 + setter 直调，无 Field 反射与中间树
						em.aload(1);
						em.checkcast(owner);
						em.aload(3);
						if (isScalar(pt)) {
							emitReadScalar(em, pt);
						} else {
							// XNJSONRuntime.readTyped(reader, pt.class, elemCls.class)
							em.ldcClass(classConst(pt));
							em.ldcClass(classConst(elemClass(f)));
							em.invokestatic(RUNTIME, "readTyped",
									"(Lcom/archer/xjson/asm/XNReader;Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;");
							em.checkcast(classConst(pt));
						}
						em.invokevirtual(owner, setter.getName(),
								"(" + XNSerializerFactory.desc(pt) + ")V");
					} else {
						// XNJSONRuntime.readFieldValue(bean, field, reader);  （无 setter 兜底）
						em.aload(1);
						em.aload(2);
						em.aload(3);
						em.invokestatic(RUNTIME, "readFieldValue", READ_FIELD_DESC);
					}

					em.iconst(1);
					em.ireturn();
					if (miss != end) {
						em.mark(miss);
					}
				}
			}

			em.mark(end);
			em.iconst(0);
			em.ireturn();
		}

		cb.addMethod("fill",
				new String[] { "Ljava/lang/Object;", "Ljava/lang/String;",
						"Lcom/archer/xjson/asm/XNReader;" },
				"Z", em.toCodeAttribute());

		// create(): 有公共无参构造直发 new，否则回退运行时装配
		XNEmitter emC = new XNEmitter(cp, codeIdx, 1);
		if (hasPublicNoArgCtor(beanCls)) {
			emC.newObject(owner);
			emC.dup();
			emC.invokespecial(owner, "<init>", "()V");
		} else {
			emC.ldcClass(owner);
			emC.invokestatic(RUNTIME, "newInstance", "(Ljava/lang/Class;)Ljava/lang/Object;");
		}
		emC.areturn();
		cb.addMethod("create", new String[] {}, "Ljava/lang/Object;", emC.toCodeAttribute());

		XNEmitter emType = new XNEmitter(cp, codeIdx, 1);
		emType.ldcClass(owner);
		emType.areturn();
		cb.addMethod("type", new String[] {}, "Ljava/lang/Class;", emType.toCodeAttribute());

		try {
			return (XNFieldFiller) cb.loadSelfClass().newInstance();
		} catch (Exception e) {
			throw new XNException("can not generate filler for " + beanCls.getName(), e);
		}
	}

	/** 流式直读标量（消费 reader 引用并压入值） */
	private static void emitReadScalar(XNEmitter em, Class<?> pt) {
		if (pt == int.class) em.invokevirtual(READER, "readInt", "()I");
		else if (pt == Integer.class) em.invokevirtual(READER, "readInteger", "()Ljava/lang/Integer;");
		else if (pt == long.class) em.invokevirtual(READER, "readLong", "()J");
		else if (pt == Long.class) em.invokevirtual(READER, "readLongObj", "()Ljava/lang/Long;");
		else if (pt == double.class) em.invokevirtual(READER, "readDouble", "()D");
		else if (pt == Double.class) em.invokevirtual(READER, "readDoubleObj", "()Ljava/lang/Double;");
		else if (pt == float.class) em.invokevirtual(READER, "readFloat", "()F");
		else if (pt == Float.class) em.invokevirtual(READER, "readFloatObj", "()Ljava/lang/Float;");
		else if (pt == boolean.class) em.invokevirtual(READER, "readBoolean", "()Z");
		else if (pt == Boolean.class) em.invokevirtual(READER, "readBooleanObj", "()Ljava/lang/Boolean;");
		else if (pt == byte.class) em.invokevirtual(READER, "readByte", "()B");
		else if (pt == Byte.class) em.invokevirtual(READER, "readByteObj", "()Ljava/lang/Byte;");
		else if (pt == short.class) em.invokevirtual(READER, "readShort", "()S");
		else if (pt == Short.class) em.invokevirtual(READER, "readShortObj", "()Ljava/lang/Short;");
		else if (pt == char.class) em.invokevirtual(READER, "readChar", "()C");
		else if (pt == Character.class) em.invokevirtual(READER, "readCharObj", "()Ljava/lang/Character;");
		else if (pt == String.class) em.invokevirtual(READER, "readStringValue", "()Ljava/lang/String;");
		else throw new IllegalArgumentException("not scalar: " + pt);
	}

	private static boolean isScalar(Class<?> pt) {
		return pt == String.class || pt.isPrimitive()
				|| pt == Integer.class || pt == Long.class || pt == Double.class
				|| pt == Float.class || pt == Boolean.class || pt == Byte.class
				|| pt == Short.class || pt == Character.class;
	}

	/** ldc 指令使用的类常量名：数组类型必须用描述符，其余用内部名 */
	private static String classConst(Class<?> cls) {
		return cls.isArray() ? XNSerializerFactory.desc(cls) : raw(cls);
	}

	/** 生成从 Object 到目标类型的转换调用 */
	private static void emitConvert(XNEmitter em, Class<?> pt, Field f) {
		if (pt == String.class) {
			em.checkcast("java/lang/String");
		} else if (pt == int.class) {
			em.invokestatic(RUNTIME, "toInt", "(Ljava/lang/Object;)I");
		} else if (pt == Integer.class) {
			em.invokestatic(RUNTIME, "toInteger", "(Ljava/lang/Object;)Ljava/lang/Integer;");
		} else if (pt == long.class) {
			em.invokestatic(RUNTIME, "toLong", "(Ljava/lang/Object;)J");
		} else if (pt == Long.class) {
			em.invokestatic(RUNTIME, "toLongObj", "(Ljava/lang/Object;)Ljava/lang/Long;");
		} else if (pt == double.class) {
			em.invokestatic(RUNTIME, "toDouble", "(Ljava/lang/Object;)D");
		} else if (pt == Double.class) {
			em.invokestatic(RUNTIME, "toDoubleObj", "(Ljava/lang/Object;)Ljava/lang/Double;");
		} else if (pt == float.class) {
			em.invokestatic(RUNTIME, "toFloat", "(Ljava/lang/Object;)F");
		} else if (pt == Float.class) {
			em.invokestatic(RUNTIME, "toFloatObj", "(Ljava/lang/Object;)Ljava/lang/Float;");
		} else if (pt == boolean.class) {
			em.invokestatic(RUNTIME, "toBool", "(Ljava/lang/Object;)Z");
		} else if (pt == Boolean.class) {
			em.invokestatic(RUNTIME, "toBoolObj", "(Ljava/lang/Object;)Ljava/lang/Boolean;");
		} else if (pt == byte.class) {
			em.invokestatic(RUNTIME, "toByte", "(Ljava/lang/Object;)B");
		} else if (pt == Byte.class) {
			em.invokestatic(RUNTIME, "toByteObj", "(Ljava/lang/Object;)Ljava/lang/Byte;");
		} else if (pt == short.class) {
			em.invokestatic(RUNTIME, "toShort", "(Ljava/lang/Object;)S");
		} else if (pt == Short.class) {
			em.invokestatic(RUNTIME, "toShortObj", "(Ljava/lang/Object;)Ljava/lang/Short;");
		} else if (pt == char.class) {
			em.invokestatic(RUNTIME, "toChar", "(Ljava/lang/Object;)C");
		} else if (pt == Character.class) {
			em.invokestatic(RUNTIME, "toCharObj", "(Ljava/lang/Object;)Ljava/lang/Character;");
		} else if (pt == BigInteger.class) {
			em.invokestatic(RUNTIME, "toBigInteger", "(Ljava/lang/Object;)Ljava/math/BigInteger;");
		} else if (pt == BigDecimal.class) {
			em.invokestatic(RUNTIME, "toBigDecimal", "(Ljava/lang/Object;)Ljava/math/BigDecimal;");
		} else if (Date.class.isAssignableFrom(pt)) {
			em.invokestatic(RUNTIME, "toDate", "(Ljava/lang/Object;)Ljava/util/Date;");
			if (pt != Date.class) {
				em.checkcast(raw(pt));
			}
		} else if (pt == LocalDate.class) {
			em.invokestatic(RUNTIME, "toLocalDate", "(Ljava/lang/Object;)Ljava/time/LocalDate;");
		} else if (pt == LocalTime.class) {
			em.invokestatic(RUNTIME, "toLocalTime", "(Ljava/lang/Object;)Ljava/time/LocalTime;");
		} else if (pt == LocalDateTime.class) {
			em.invokestatic(RUNTIME, "toLocalDateTime", "(Ljava/lang/Object;)Ljava/time/LocalDateTime;");
		} else if (pt.isEnum()) {
			em.ldcClass(raw(pt));
			em.invokestatic(RUNTIME, "toEnum", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Enum;");
			em.checkcast(raw(pt));
		} else if (pt.isArray()) {
			em.ldcClass(arrayClassConst(pt));
			em.invokestatic(RUNTIME, "toArrayOf", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
			em.checkcast(XNSerializerFactory.desc(pt));
		} else if (Set.class.isAssignableFrom(pt)) {
			em.ldcClass(elemClassConst(elemClass(f)));
			em.invokestatic(RUNTIME, "toSet", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/util/Set;");
			em.checkcast(raw(pt));
		} else if (List.class.isAssignableFrom(pt)) {
			em.ldcClass(elemClassConst(elemClass(f)));
			em.invokestatic(RUNTIME, "toList", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/util/List;");
			em.checkcast(raw(pt));
		} else if (Map.class.isAssignableFrom(pt)) {
			em.checkcast(raw(pt));
		} else if (pt == Object.class) {
			// 无需转换
		} else {
			em.ldcClass(raw(pt));
			em.invokestatic(RUNTIME, "toBean", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
			em.checkcast(raw(pt));
		}
	}

	static Method findSetter(Class<?> beanCls, Field f) {
		String name = "set" + XNSerializerFactory.capitalize(f.getName());
		Class<?> cur = beanCls;
		while (null != cur && cur != Object.class) {
			for (Method m : cur.getDeclaredMethods()) {
				if (m.getName().equals(name) && m.getParameterCount() == 1
						&& Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())
						&& (m.getReturnType() == void.class || m.getReturnType() == Void.class)) {
					return m;
				}
			}
			cur = cur.getSuperclass();
		}
		return null;
	}

	private static boolean hasPublicNoArgCtor(Class<?> cls) {
		try {
			return Modifier.isPublic(cls.getConstructor().getModifiers());
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	/** 集合元素的原始类型，取不到时为 Object */
	private static Class<?> elemClass(Field f) {
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

	/** ldc 数组类常量使用数组描述符形式，如 [I、[Ljava/lang/String; */
	private static String arrayClassConst(Class<?> arrayCls) {
		return XNSerializerFactory.desc(arrayCls);
	}

	private static String elemClassConst(Class<?> elemCls) {
		return elemCls.isArray() ? XNSerializerFactory.desc(elemCls) : raw(elemCls);
	}

	static String raw(Class<?> cls) {
		return cls.getName().replace('.', '/');
	}

	private static String safeName(Class<?> cls) {
		return cls.getSimpleName().replace('$', '_');
	}
}
