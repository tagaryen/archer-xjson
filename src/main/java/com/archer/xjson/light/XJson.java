package com.archer.xjson.light;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Map;


@SuppressWarnings("restriction")
public final class XJson {

    private XJson() {}

    private static final ThreadLocal<JsonWriter> WRITERS = ThreadLocal.withInitial(JsonWriter::new);
    private static final ThreadLocal<JsonParser> PARSERS = ThreadLocal.withInitial(JsonParser::new);


    public static String toJson(Object obj) {
        JsonWriter w = WRITERS.get();
        w.reset();
        writeValue(w, obj);
        return w.toString();
    }

    public static byte[] toJsonBytes(Object obj) {
        JsonWriter w = WRITERS.get();
        w.reset();
        writeValue(w, obj);
        return w.toUtf8Bytes();
    }

    /** 解析为通用结构：Map / List / String / Integer / Long / Double / Boolean / null */
    public static Object parse(byte[] data) {
        return parse(data, 0, data.length);
    }

    public static Object parse(String s) {
        return parse(s.getBytes(StandardCharsets.UTF_8));
    }

    public static Object parse(byte[] data, int off, int len) {
        JsonParser p = PARSERS.get();
        try {
            p.init(data, off, len);
            Object v = p.readValue();
            p.expectEnd();
            return v;
        } finally {
            p.release();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T parseObject(byte[] data, Class<T> type) {
        JsonParser p = PARSERS.get();
        try {
            p.init(data, 0, data.length);
            T v = (T) p.readTyped(TypeNode.of(type));
            p.expectEnd();
            return v;
        } finally {
            p.release();
        }
    }

    public static <T> T parseObject(String s, Class<T> type) {
        return parseObject(s.getBytes(StandardCharsets.UTF_8), type);
    }

    /** 参数化类型入口：{@code Json.parseObject(data, new TypeRef<Base<Point>>() {})} */
    @SuppressWarnings("unchecked")
    public static <T> T parseObject(byte[] data, XTypeRef<T> ref) {
        JsonParser p = PARSERS.get();
        try {
            p.init(data, 0, data.length);
            T v = (T) p.readTyped(TypeNode.of(ref.getType()));
            p.expectEnd();
            return v;
        } finally {
            p.release();
        }
    }

    public static <T> T parseObject(String s, XTypeRef<T> ref) {
        return parseObject(s.getBytes(StandardCharsets.UTF_8), ref);
    }

    @SuppressWarnings("unchecked")
    static void writeValue(JsonWriter w, Object v) {
        if (v == null) { w.write("null"); return; }
        if (v instanceof String) { w.writeQuoted((String) v); return; }
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            w.writeLong(((Number) v).longValue());
            return;
        }
        if (v instanceof Double || v instanceof Float) {
            w.writeDouble(((Number) v).doubleValue());
            return;
        }
        if (v instanceof BigDecimal || v instanceof BigInteger) {
            w.write(v.toString());
            return;
        }
        if (v instanceof Boolean) {
            w.write((Boolean) v ? "true" : "false");
            return;
        }
        if (v instanceof Character) {
            w.writeQuoted(v.toString());
            return;
        }
        if (v instanceof Enum) {
            w.writeQuoted(((Enum<?>) v).name());
            return;
        }
        if (v instanceof Map) {
            writeMap(w, (Map<Object, Object>) v);
            return;
        }
        if (v instanceof Collection) {
            writeColl(w, (Collection<Object>) v);
            return;
        }
        Class<?> c = v.getClass();
        if (c.isArray()) {
            int len = Array.getLength(v);
            w.write('[');
            for (int i = 0; i < len; i++) {
                if (i > 0) w.write(',');
                writeValue(w, Array.get(v, i));
            }
            w.write(']');
            return;
        }
        if (v instanceof LocalDateTime) {
            w.writeQuoted(JsonParser.LDT_FMT.format((LocalDateTime) v)); // yyyy-MM-dd HH:mm:ss.SSS
            return;
        }
        if (v instanceof LocalDate) {
            w.writeQuoted(JsonParser.LD_FMT.format((LocalDate) v)); // yyyy-MM-dd
            return;
        }
        if (v instanceof LocalTime) {
            w.writeQuoted(JsonParser.LT_FMT.format((LocalTime) v)); // HH:mm:ss.SSS
            return;
        }
        if (v instanceof java.util.Date) {
            w.writeLong(((java.util.Date) v).getTime()); 
            return;
        }
        writePojo(w, v);
    }

    private static void writeMap(JsonWriter w, Map<Object, Object> m) {
        w.write('{');
        boolean first = true;
        for (Map.Entry<Object, Object> e : m.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) w.write(',');
            first = false;
            w.writeQuoted(String.valueOf(e.getKey()));
            w.write(':');
            writeValue(w, e.getValue());
        }
        w.write('}');
    }

    private static void writeColl(JsonWriter w, Collection<Object> coll) {
        w.write('[');
        boolean first = true;
        for (Object o : coll) {
            if (!first) w.write(',');
            first = false;
            writeValue(w, o);
        }
        w.write(']');
    }

    private static void writePojo(JsonWriter w, Object o) {
        BeanInfo bi = BeanInfo.of(o.getClass());
        w.write('{');
        boolean first = true;
        for (BeanInfo.FieldInfo f : bi.fields) {
            long off = f.offset;
            switch (f.type) {
                case BeanInfo.T_INT:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.writeLong(BeanInfo.UNSAFE.getInt(o, off)); break;
                case BeanInfo.T_LONG:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.writeLong(BeanInfo.UNSAFE.getLong(o, off)); break;
                case BeanInfo.T_DOUBLE:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.writeDouble(BeanInfo.UNSAFE.getDouble(o, off)); break;
                case BeanInfo.T_FLOAT:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.writeDouble(BeanInfo.UNSAFE.getFloat(o, off)); break;
                case BeanInfo.T_SHORT:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.writeLong(BeanInfo.UNSAFE.getShort(o, off)); break;
                case BeanInfo.T_BYTE:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.writeLong(BeanInfo.UNSAFE.getByte(o, off)); break;
                case BeanInfo.T_BOOL:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.write(BeanInfo.UNSAFE.getBoolean(o, off) ? "true" : "false"); break;
                case BeanInfo.T_CHAR:
                    if (!first) w.write(','); first = false;
                    w.write(f.nameQuoted); w.writeQuoted(String.valueOf(BeanInfo.UNSAFE.getChar(o, off))); break;
                case BeanInfo.T_STRING: {
                    Object val = BeanInfo.UNSAFE.getObject(o, off);
                    if (val != null) {
                        if (!first) w.write(','); first = false;
                        w.write(f.nameQuoted); w.writeQuoted((String) val);
                    }
                    break;
                }
                default: {
                    Object val = BeanInfo.UNSAFE.getObject(o, off);
                    if (val != null) {
                        if (!first) w.write(','); first = false;
                        w.write(f.nameQuoted); writeValue(w, val);
                    }
                }
            }
        }
        w.write('}');
    }
}
