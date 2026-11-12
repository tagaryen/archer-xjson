package com.archer.xjson.light;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Map;


final class TypeNode {

    final Class<?> raw;
    final TypeNode[] args; // 无实参时为 null

    private TypeNode(Class<?> raw, TypeNode[] args) {
        this.raw = raw;
        this.args = args;
    }

    static TypeNode of(Type t) {
        return of(t, null);
    }

    static TypeNode of(Type t, Map<TypeVariable<?>, TypeNode> binding) {
        if (t instanceof Class) {
            return new TypeNode((Class<?>) t, null);
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) t;
            Type[] as = pt.getActualTypeArguments();
            TypeNode[] args = new TypeNode[as.length];
            for (int i = 0; i < as.length; i++) {
                args[i] = of(as[i], binding);
            }
            return new TypeNode((Class<?>) pt.getRawType(), args);
        }
        if (t instanceof TypeVariable) {
            TypeNode b = binding == null ? null : binding.get(t);
            return b != null ? b : of(Object.class);
        }
        if (t instanceof GenericArrayType) {
            // 泛型数组退化为 Object[] 处理（极少出现）
            return of(Object[].class);
        }
        // WildcardType 等一律视为 Object，走通用路径
        return of(Object.class);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeNode)) return false;
        TypeNode n = (TypeNode) o;
        return raw == n.raw && Arrays.equals(args, n.args);
    }

    @Override
    public int hashCode() {
        return raw.hashCode() * 31 + Arrays.hashCode(args);
    }
}
