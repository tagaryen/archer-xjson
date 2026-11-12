package com.archer.xjson.light;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@SuppressWarnings("restriction")
final class BeanInfo {

    static final sun.misc.Unsafe UNSAFE;
    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final int T_BOOL = 0, T_BYTE = 1, T_SHORT = 2, T_CHAR = 3, T_INT = 4,
            T_LONG = 5, T_FLOAT = 6, T_DOUBLE = 7, T_STRING = 8, T_OBJ = 9;

    private static final ConcurrentHashMap<TypeNode, BeanInfo> CACHE = new ConcurrentHashMap<>();

    final Class<?> type;
    final FieldInfo[] fields;
    private final FieldInfo[] table; // 开放寻址哈希表：字段名 UTF-8 字节 -> FieldInfo
    private final int mask;

    static BeanInfo of(Class<?> c) {
        return of(TypeNode.of(c));
    }

    static BeanInfo of(TypeNode t) {
        BeanInfo bi = CACHE.get(t);
        if (bi == null) {
            bi = new BeanInfo(t);
            BeanInfo prev = CACHE.putIfAbsent(t, bi);
            if (prev != null) bi = prev;
        }
        return bi;
    }

    private BeanInfo(TypeNode t) {
        this.type = t.raw;
        // 绑定本类声明的类型变量（如 Base<T> 的 T -> Point）；父类字段沿用其原始声明
        Map<TypeVariable<?>, TypeNode> binding = null;
        if (t.args != null) {
            TypeVariable<?>[] ps = t.raw.getTypeParameters();
            binding = new HashMap<>();
            for (int i = 0; i < ps.length && i < t.args.length; i++) {
                binding.put(ps[i], t.args[i]);
            }
        }
        List<FieldInfo> list = new ArrayList<>();
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) continue;
                list.add(new FieldInfo(f, binding));
            }
        }
        this.fields = list.toArray(new FieldInfo[0]);
        int cap = 16;
        while (cap < fields.length * 2) cap <<= 1;
        this.mask = cap - 1;
        this.table = new FieldInfo[cap];
        for (FieldInfo f : fields) {
            int i = hash(f.nameUtf8, 0, f.nameUtf8.length) & mask;
            while (table[i] != null) i = (i + 1) & mask;
            table[i] = f;
        }
    }

    /** 与 JsonParser.readKey 内联计算保持一致的字节哈希 */
    static int hash(byte[] b, int s, int len) {
        int h = 0;
        for (int i = 0; i < len; i++) h = h * 31 + b[s + i];
        return h;
    }

    Object newInstance() {
        try {
            return UNSAFE.allocateInstance(type);
        } catch (InstantiationException e) {
            throw new IllegalStateException("cannot allocate " + type.getName(), e);
        }
    }

    /** 零分配字段查找：开放寻址哈希探测，按 UTF-8 字节直接比对，O(1) */
    FieldInfo find(byte[] b, int s, int len, int h) {
        int i = h & mask;
        FieldInfo f;
        while ((f = table[i]) != null) {
            byte[] nb = f.nameUtf8;
            if (nb.length == len) {
                boolean eq = true;
                for (int j = 0; j < len; j++) {
                    if (nb[j] != b[s + j]) { eq = false; break; }
                }
                if (eq) return f;
            }
            i = (i + 1) & mask;
        }
        return null;
    }

    FieldInfo find(String name) {
        for (FieldInfo f : fields) {
            if (f.name.equals(name)) return f;
        }
        return null;
    }

    static final class FieldInfo {
        final String name;
        final String nameQuoted;   // 预拼接 "name":，序列化直接写
        final byte[] nameUtf8;
        final long offset;
        final int type;
        final Class<?> rawType;     // T_OBJ 时的字段声明类型
        final TypeNode node;        // T_OBJ 时的字段完整泛型类型（类型变量已绑定）

        FieldInfo(Field f, Map<TypeVariable<?>, TypeNode> binding) {
            this.name = f.getName();
            this.nameQuoted = '"' + name + "\":";
            this.nameUtf8 = name.getBytes(StandardCharsets.UTF_8);
            this.offset = UNSAFE.objectFieldOffset(f);
            this.rawType = f.getType();
            this.type = classify(rawType);
            this.node = (type == T_OBJ) ? TypeNode.of(f.getGenericType(), binding) : null;
        }

        private static int classify(Class<?> c) {
            if (c == int.class) return T_INT;
            if (c == long.class) return T_LONG;
            if (c == double.class) return T_DOUBLE;
            if (c == float.class) return T_FLOAT;
            if (c == short.class) return T_SHORT;
            if (c == byte.class) return T_BYTE;
            if (c == boolean.class) return T_BOOL;
            if (c == char.class) return T_CHAR;
            if (c == String.class) return T_STRING;
            return T_OBJ;
        }
    }
}
