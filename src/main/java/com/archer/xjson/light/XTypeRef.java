package com.archer.xjson.light;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;


public abstract class XTypeRef<T> {

    private final Type type;

    protected XTypeRef() {
        Type sup = getClass().getGenericSuperclass();
        if (!(sup instanceof ParameterizedType)) {
            throw new IllegalArgumentException("XTypeRef must be parameterized");
        }
        this.type = ((ParameterizedType) sup).getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }
}
