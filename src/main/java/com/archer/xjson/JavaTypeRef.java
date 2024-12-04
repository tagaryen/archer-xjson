package com.archer.xjson;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class JavaTypeRef<T> {
	
	public JavaTypeRef() {}
	
	public Type getJavaType() {
		ParameterizedType t = (ParameterizedType) getClass().getGenericSuperclass();
		Type[] ts = t.getActualTypeArguments();
		return ts[0];
	}
}
