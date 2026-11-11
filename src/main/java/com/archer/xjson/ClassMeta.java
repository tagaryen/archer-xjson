package com.archer.xjson;

import java.lang.reflect.Field;


public class ClassMeta {

	public final Class<?> cls;

	public final Field[] fields;

	/** 与 fields 对齐：预拼接的 "\"name\":" 输出片段 */
	public final String[] keys;

	/** 与 fields 对齐：字段声明类型 */
	public final Class<?>[] types;

	ClassMeta(Class<?> cls, Field[] fields) {
		this.cls = cls;
		this.fields = fields;
		this.keys = new String[fields.length];
		this.types = new Class<?>[fields.length];
		for (int i = 0; i < fields.length; i++) {
			this.keys[i] = "\"" + fields[i].getName() + "\":";
			this.types[i] = fields[i].getType();
		}
	}
}
