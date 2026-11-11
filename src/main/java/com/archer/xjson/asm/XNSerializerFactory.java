package com.archer.xjson.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import com.archer.xjson.asm.constantpool.ConstantPool;


class XNSerializerFactory {

	private static final AtomicInteger SEQ = new AtomicInteger();

	private static final String GEN_PKG = "com/archer/xjson/asm/gen";
	private static final String WRITER = "com/archer/xjson/asm/XNWriter";
	private static final String RUNTIME = "com/archer/xjson/asm/XNJSONRuntime";

	static XNSerializer create(Class<?> beanCls) {
		String genName = safeName(beanCls) + "$XNSer$" + SEQ.incrementAndGet();
		ClassBytecode cb = new ClassBytecode();
		cb.toEmptyClass(genName, GEN_PKG);
		cb.setAccessFlag(0x0021); // public super
		ConstantPool cp = cb.getConstantPool();
		cb.addInterfaceIndexArr(cp.addClass("com/archer/xjson/asm/XNSerializer"));
		int codeIdx = cp.findName("Code");

		XNEmitter em = new XNEmitter(cp, codeIdx, 4);
		String owner = raw(beanCls);

		// EntityObj c = (EntityObj) bean;
		em.aload(2);
		em.checkcast(owner);
		em.astore(3);
		// out.writeStartObject();
		em.aload(1);
		em.invokevirtual(WRITER, "writeStartObject", "()V");

		boolean first = true;
		for (Field f : XNJSONRuntime.fieldsOf(beanCls)) {
			if (!first) {
				em.aload(1);
				em.invokevirtual(WRITER, "writeComma", "()V");
			}
			first = false;
			emitNameAnd(em, f);

			Method getter = findGetter(beanCls, f);
			if (null == getter) {
				// XNJSONRuntime.writeFieldReflect(out, c, "field");
				em.aload(1);
				em.aload(3);
				em.ldcString(f.getName());
				em.invokestatic(RUNTIME, "writeFieldReflect",
						"(Lcom/archer/xjson/asm/XNWriter;Ljava/lang/Object;Ljava/lang/String;)V");
			} else {
				// out.writeXxx(c.getXxx());
				em.aload(1);
				em.aload(3);
				em.invokevirtual(owner, getter.getName(), "()" + desc(getter.getReturnType()));
				emitTypedWrite(em, getter.getReturnType());
			}
		}

		em.aload(1);
		em.invokevirtual(WRITER, "writeEndObject", "()V");
		em.vreturn();

		cb.addMethod("serialize",
				new String[] { "Lcom/archer/xjson/asm/XNWriter;", "Ljava/lang/Object;" },
				"V", em.toCodeAttribute());

		try {
			return (XNSerializer) cb.loadSelfClass().newInstance();
		} catch (Exception e) {
			throw new XNException("can not generate serializer for " + beanCls.getName(), e);
		}
	}

	/** 字段名无需转义时预拼接 "\"name\":" 一次写入，否则回退 writeName */
	private static void emitNameAnd(XNEmitter em, Field f) {
		em.aload(1);
		String name = f.getName();
		if (needsNoEscape(name)) {
			em.ldcString("\"" + name + "\":");
			em.invokevirtual(WRITER, "writeRaw", "(Ljava/lang/String;)V");
		} else {
			em.ldcString(name);
			em.invokevirtual(WRITER, "writeName", "(Ljava/lang/String;)V");
		}
	}

	private static boolean needsNoEscape(String s) {
		for (int i = 0, len = s.length(); i < len; i++) {
			char c = s.charAt(i);
			if (c == '"' || c == '\\' || c < 0x20) {
				return false;
			}
		}
		return true;
	}

	/** 按 getter 返回类型生成强类型写入调用 */
	private static void emitTypedWrite(XNEmitter em, Class<?> rt) {
		if (rt == String.class) {
			em.invokevirtual(WRITER, "writeString", "(Ljava/lang/String;)V");
		} else if (rt == int.class) {
			em.invokevirtual(WRITER, "writeInt", "(I)V");
		} else if (rt == Integer.class) {
			em.invokevirtual(WRITER, "writeInteger", "(Ljava/lang/Integer;)V");
		} else if (rt == long.class) {
			em.invokevirtual(WRITER, "writeLong", "(J)V");
		} else if (rt == Long.class) {
			em.invokevirtual(WRITER, "writeLongObj", "(Ljava/lang/Long;)V");
		} else if (rt == double.class) {
			em.invokevirtual(WRITER, "writeDouble", "(D)V");
		} else if (rt == Double.class) {
			em.invokevirtual(WRITER, "writeDoubleObj", "(Ljava/lang/Double;)V");
		} else if (rt == float.class) {
			em.invokevirtual(WRITER, "writeFloat", "(F)V");
		} else if (rt == Float.class) {
			em.invokevirtual(WRITER, "writeFloatObj", "(Ljava/lang/Float;)V");
		} else if (rt == boolean.class) {
			em.invokevirtual(WRITER, "writeBool", "(Z)V");
		} else if (rt == Boolean.class) {
			em.invokevirtual(WRITER, "writeBoolObj", "(Ljava/lang/Boolean;)V");
		} else if (rt == byte.class) {
			em.invokevirtual(WRITER, "writeByte", "(B)V");
		} else if (rt == Byte.class) {
			em.invokevirtual(WRITER, "writeByteObj", "(Ljava/lang/Byte;)V");
		} else if (rt == short.class) {
			em.invokevirtual(WRITER, "writeShort", "(S)V");
		} else if (rt == Short.class) {
			em.invokevirtual(WRITER, "writeShortObj", "(Ljava/lang/Short;)V");
		} else if (rt == char.class) {
			em.invokevirtual(WRITER, "writeChar", "(C)V");
		} else if (rt == Character.class) {
			em.invokevirtual(WRITER, "writeCharObj", "(Ljava/lang/Character;)V");
		} else if (rt == BigInteger.class) {
			em.invokevirtual(WRITER, "writeBigInteger", "(Ljava/math/BigInteger;)V");
		} else if (rt == BigDecimal.class) {
			em.invokevirtual(WRITER, "writeBigDecimal", "(Ljava/math/BigDecimal;)V");
		} else if (Date.class.isAssignableFrom(rt)) {
			em.invokevirtual(WRITER, "writeDate", "(Ljava/util/Date;)V");
		} else if (rt == LocalDate.class) {
			em.invokevirtual(WRITER, "writeLocalDate", "(Ljava/time/LocalDate;)V");
		} else if (rt == LocalTime.class) {
			em.invokevirtual(WRITER, "writeLocalTime", "(Ljava/time/LocalTime;)V");
		} else if (rt == LocalDateTime.class) {
			em.invokevirtual(WRITER, "writeLocalDateTime", "(Ljava/time/LocalDateTime;)V");
		} else if (rt.isEnum()) {
			em.invokevirtual(WRITER, "writeEnum", "(Ljava/lang/Enum;)V");
		} else {
			em.invokestatic(RUNTIME, "writeValue",
					"(Lcom/archer/xjson/asm/XNWriter;Ljava/lang/Object;)V");
		}
	}

	static Method findGetter(Class<?> beanCls, Field f) {
		String cap = capitalize(f.getName());
		Method m = findNoArg(beanCls, "get" + cap);
		if (null == m && (f.getType() == boolean.class || f.getType() == Boolean.class)) {
			m = findNoArg(beanCls, "is" + cap);
		}
		return m;
	}

	private static Method findNoArg(Class<?> beanCls, String name) {
		Class<?> cur = beanCls;
		while (null != cur && cur != Object.class) {
			try {
				Method m = cur.getDeclaredMethod(name);
				// 生成类与实体类不在同一包，只有 public 方法才能被直接调用
				if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())
						&& m.getParameterCount() == 0
						&& m.getReturnType() != void.class && m.getReturnType() != Void.class) {
					return m;
				}
				return null;
			} catch (NoSuchMethodException e) {
				cur = cur.getSuperclass();
			}
		}
		return null;
	}

	static String capitalize(String name) {
		if (name.isEmpty() || !Character.isLowerCase(name.charAt(0))) {
			return name;
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	static String raw(Class<?> cls) {
		return cls.getName().replace('.', '/');
	}

	static String desc(Class<?> cls) {
		if (cls.isArray()) {
			return cls.getName().replace('.', '/');
		}
		if (cls == int.class) return "I";
		if (cls == long.class) return "J";
		if (cls == double.class) return "D";
		if (cls == float.class) return "F";
		if (cls == boolean.class) return "Z";
		if (cls == byte.class) return "B";
		if (cls == short.class) return "S";
		if (cls == char.class) return "C";
		if (cls == void.class) return "V";
		return "L" + cls.getName().replace('.', '/') + ";";
	}

	private static String safeName(Class<?> cls) {
		return cls.getSimpleName().replace('$', '_');
	}
}
