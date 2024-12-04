package com.archer.xjson;

import java.lang.reflect.Constructor;

class XJSONConstructor {
	
	Class<?> cls;
	
	Constructor<?> constructor;
	
	Object[] params;

	public XJSONConstructor(Class<?> cls, Constructor<?> constructor, Object[] params) {
		super();
		this.cls = cls;
		this.constructor = constructor;
		this.params = params;
	}
	
	public Object newInstance() {
		try {
			return constructor.newInstance(params);
		} catch (Exception e) {
			throw new XJSONException("can not construct class '" +
					cls.getName() + "'");
		}
	}
}