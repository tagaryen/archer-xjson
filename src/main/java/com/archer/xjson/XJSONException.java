package com.archer.xjson;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class XJSONException extends RuntimeException {
    static final long serialVersionUID = -3321467993124229948L;
    
    static final int END_OFFSET = 32;
    
    public XJSONException(Throwable e) {
    	super(e.getMessage());
    }
    
    public XJSONException(String msg) {
    	super(msg);
    }
    

	static String getErrorMsg(String name, Class<?> clazz, boolean missing) {
		if(missing) {
			return "filed '"+name +"' requires a value in " + 
					clazz.getName() + " with strict class mode";
		} else {
			return "can not find field '"+name+"' in " + 
					clazz.getName() + " with strict json mode";
		}
	}
	
	static String getErrorMsg(Field f, Object val, String message) {
		return getErrorMsg(f, val) + "; " + message;
	}
	
	static String getErrorMsg(Field f, Object val) {
		String msg = "can not set ";
		if(val instanceof LinkedHashMap) {
			msg += "Object to " + f.getName() + 
					"("+f.getDeclaringClass().getName()+")";
		} else if(val instanceof String) {
			msg += "'" + val + "' to " + f.getName() + 
					"("+f.getDeclaringClass().getName()+")";
		} else {
			msg += val + " to " + f.getName() + 
					"("+f.getClass().getName()+")";
		}
		return msg;
	}
	
	static String getErrorMsg(char[] chars, int offset) {
		String base = "invalid json string near ";
		int end = offset + END_OFFSET > chars.length ? chars.length : offset + END_OFFSET;
		String msg = new String(
				Arrays.copyOfRange(chars, offset, end));
		return base + msg;
	}
}
