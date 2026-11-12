package com.archer.xjson.light;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;


public abstract class TypeRef<T> {

    private final Type type;

    protected TypeRef() {
        Type sup = getClass().getGenericSuperclass();
        if (!(sup instanceof ParameterizedType)) {
            throw new IllegalArgumentException("TypeRef 必须以匿名子类形式携带泛型参数");
        }
        this.type = ((ParameterizedType) sup).getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }
}
