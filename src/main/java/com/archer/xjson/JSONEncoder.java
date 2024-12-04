package com.archer.xjson;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class JSONEncoder {

	static final String DATE_PATTERN = "yyyy-MM-dd";
	static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
	
	static final char BRACES_L = '[';
	static final char BRACES_R = ']';
	static final char B_BRACES_L = '{';
	static final char B_BRACES_R = '}';
	static final char COMMA = ',';
	static final char COLON = ':';
	static final char DOT = '.';
	static final char SPACE = ' ';
	static final char ENTER = '\n';
	static final char QUOTE = '"';
	static final char SINGLE_QUOTE = '\'';
	static final String TAB = "  ";
	static final String SRC_QOUTE = "\"";
	static final String DST_QOUTE = "\\\"";

	static final String BOOL_TYPE = "boolean";
	static final String BYTE_TYPE = "byte";
	static final String CHAR_TYPE = "char";
	static final String SHORT_TYPE = "short";
	static final String INT_TYPE = "int";
	static final String LONG_TYPE = "long";
	static final String FLOAT_TYPE = "float";
	static final String DOUBLE_TYPE = "double";
	
	static final String DECIMAL_PATTERN = "#.00";
	/**
	 * inner class to super
	 * */
	static final String INNER_CLASS_FIELD = "this$";
	

	static final int DEFAULT_FIELD_COUNT = 24;
	static final int DEFAULT_ARRAY_LEN = 16;
	static final int BASE_MAP_LINE_LENGTH = 256;
	static final int BASE_COLLECTION_LINE_LENGTH = 256;
	
	@SuppressWarnings("unchecked")
	static String formatObject(Object data, int tabCount, boolean isVal, JSONStuff stuff) {
		if(data == null) {
			return "null";
		}
		if(data.getClass().isPrimitive()) {
			return formatPrimitive(data);
		}
		if(stuff.CODER_MAP.contains(data.getClass())) {
			return formatString(stuff.CODER_MAP.get(data.getClass()).serialize(data));
		}
		if(data instanceof Boolean) {
			return ((Boolean) data?"true":"false");
		}
        if(data instanceof String) {
        	if(isVal) {
        		return QUOTE + formatString((String) data) + QUOTE;
        	}
            return formatString((String) data);
        }
        if(data instanceof Byte) {
            return String.valueOf((Byte)data);
        }	
        if(data instanceof Character) {
            return SINGLE_QUOTE + 
            		String.valueOf((Character)data)
            	+ SINGLE_QUOTE;
        }
        if(data instanceof Short) {
            return String.valueOf((Short)data);
        }
        if(data instanceof Integer) {
            return String.valueOf((Integer)data);
        }
        if(data instanceof Long) {
            return String.valueOf((Long)data);
        }
        if(data instanceof Float) {
            return String.valueOf((Float)data);
        }
        if(data instanceof Double) {
            return String.valueOf((Double)data);
        }
        if(data instanceof BigInteger) {
            return ((BigInteger) data).toString(10);
        }
        if(data instanceof BigDecimal) {
            return ((BigDecimal) data).toPlainString();
        }
        if(data instanceof Number) {
            DecimalFormat df = new DecimalFormat(DECIMAL_PATTERN);
            return df.format(data);
        }
        if(data instanceof Number) {
            DecimalFormat df = new DecimalFormat(DECIMAL_PATTERN);
            return df.format(data);
        }
        if(data instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
            try {
            	String ret = sdf.format((Date)data);
            	if(isVal) {
            		return QUOTE + ret + QUOTE;
            	}
                return ret;
            } catch(Exception ignored) {}
            throw new XJSONException("cannot format date "+data);
        }
        if(data instanceof LocalDate) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(TIME_PATTERN);
            try {
                String ret = ((LocalDate)data).format(dtf);
            	if(isVal) {
            		return QUOTE + ret + QUOTE;
            	}
                return ret;
            } catch(Exception ignored) {}
            throw new XJSONException("cannot format localDate "+data);
        }
        if(data instanceof LocalTime) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(TIME_PATTERN);
            try {
                String ret = ((LocalTime)data).format(dtf);
            	if(isVal) {
            		return QUOTE + ret + QUOTE;
            	}
                return ret;
            } catch(Exception ignored) {}
            throw new XJSONException("cannot format localTime "+data);
        }
        if(data instanceof LocalDateTime) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(TIME_PATTERN);
            try {
                String ret = ((LocalDateTime)data).format(dtf);
            	if(isVal) {
            		return QUOTE + ret + QUOTE;
            	}
                return ret;
            } catch(Exception ignored) {}
            throw new XJSONException("cannot format localDateTime "+data);
        }
        if(data.getClass().isArray()) {
        	return formatArray(data, tabCount, isVal, stuff);
        }
        if(data instanceof Collection) {
        	return formatCollection((Collection<?>)data, tabCount, isVal, stuff);
        }
        if(data instanceof Map) {
            try {
            	return formatMap((Map<String, ?>)data, tabCount, isVal, stuff);
    		} catch (Exception e) {
                throw new XJSONException("cannot format map object, " + e.getLocalizedMessage());
    		}
        }
        try {
			return formatClass(data, tabCount, isVal, stuff);
		} catch (IllegalArgumentException | IllegalAccessException e) {
            throw new XJSONException("cannot format class " + data.getClass().getName() + ", " + e.getLocalizedMessage());
		}
	}

	static String formatCollection(Collection<?> data, int tabCount, boolean isVal, JSONStuff stuff) {
		String base = "";
		if(stuff.beautify) {
			for(int i = 0; i < tabCount; i++) {
				base += TAB;
			}
		}
		StringBuilder sb = new StringBuilder(data.size() * BASE_COLLECTION_LINE_LENGTH);
		sb.append(BRACES_L);
		if(stuff.beautify) {
			sb.append(ENTER);
		}
		int index = 1;
		for(Object o: data) {
			if(stuff.beautify) {
				sb.append(base).append(TAB);
			}
			if(o instanceof String) {
	    		sb.append(QUOTE).append((String)o).append(QUOTE);
	    		if(index != data.size()) {
	    			sb.append(COMMA);
	    		}
	    		if(stuff.beautify) {
	    			sb.append(ENTER);
	    		}
			} else {
				sb.append(formatObject(o, tabCount + 1, false, stuff));
	    		if(index != data.size()) {
	    			sb.append(COMMA);
	    		}
	    		if(stuff.beautify) {
	    			sb.append(ENTER);
	    		}
			}
			++index;
		}
		if(stuff.beautify) {
			sb.append(base);
		}
		sb.append(BRACES_R);
    	
    	return sb.toString();
	}
	
	static String formatMap(Map<String, ?> data, int tabCount, boolean isVal, JSONStuff stuff) {
		String base = "";
		if(stuff.beautify) {
			for(int i = 0; i < tabCount; i++) {
				base += TAB;
			}
		}
		int size = data.size();
		StringBuilder sb = new StringBuilder(size * BASE_MAP_LINE_LENGTH);
		sb.append(B_BRACES_L);
		if(stuff.beautify) {
			sb.append(ENTER);
		}
		int index = 1;
		for(Entry<String, ?> val: data.entrySet()) {
			if(stuff.beautify) {
				sb.append(base).append(TAB);
			}
			sb.append(QUOTE).append(val.getKey())
			.append(QUOTE).append(COLON);
			Object fv = val.getValue();
			if(fv == data) {
				throw new XJSONException("field " + val.getKey() +
						" is self-referencing");
			}
			sb.append(formatObject(fv, tabCount + 1, true, stuff));
			if(index != size) {
				sb.append(COMMA);
			}
			if(stuff.beautify) {
				sb.append(ENTER);
			}
			++index;
		}
		if(stuff.beautify) {
			sb.append(base);
		}
		sb.append(B_BRACES_R);
		return sb.toString();
	}
	
	static void collectAllFields(Class<?> clazz, List<Field> collected) {
		if(Object.class.equals(clazz)) {
			return ;
		}
		Class<?> superCls = clazz.getSuperclass();
		collectAllFields(superCls, collected);
		Field[] fields = JSONCache.get(clazz);
		if(fields == null) {
			fields = clazz.getDeclaredFields();
			JSONCache.save(clazz, fields);
		}
		for(Field f: fields) {
			if(f.getName().startsWith(INNER_CLASS_FIELD)) {
				continue;
			}
			if(Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			if(Modifier.isTransient(f.getModifiers())) {
				continue;
			}
			collected.add(f);
		}
	}
	
	static String formatClass(Object data, int tabCount, boolean isVal, JSONStuff stuff) 
			throws IllegalArgumentException, IllegalAccessException {
		String base = "";
		if(stuff.beautify) {
			for(int i = 0; i < tabCount; i++) {
				base += TAB;
			}
		}
		List<Field> fields = new ArrayList<>(DEFAULT_FIELD_COUNT);
		collectAllFields(data.getClass(), fields);
		int len = fields.size();
		StringBuilder sb = new StringBuilder(len * BASE_MAP_LINE_LENGTH);
		sb.append(B_BRACES_L);
		if(stuff.beautify) {
			sb.append(ENTER);
		}
		int index = 0;
		for(Field f: fields) {
			++index;
			if(stuff.beautify) {
				sb.append(base).append(TAB);
			}
			sb.append(QUOTE).append(f.getName())
				.append(QUOTE).append(COLON);
			f.setAccessible(true);
			Object fv = f.get(data);
			if(fv == data) {
				throw new XJSONException("field " + f.getName() +
						" is self-referencing");
			}
			sb.append(formatObject(fv, tabCount + 1, true, stuff));
			if(index != len) {
				sb.append(COMMA);
			}
			if(stuff.beautify) {
				sb.append(ENTER);
			}
		}
		if(stuff.beautify) {
			sb.append(base);
		}
		sb.append(B_BRACES_R);
		return sb.toString();
	}
	
	static String formatArray(Object data, int tabCount, boolean isVal, JSONStuff stuff) {
		String base = "";
		if(stuff.beautify) {
			for(int i = 0; i < tabCount; i++) {
				base += TAB;
			}
		}
		
		StringBuilder sb = new StringBuilder(DEFAULT_ARRAY_LEN * BASE_COLLECTION_LINE_LENGTH);
		if(data instanceof byte[]) {
    		sb.append(QUOTE).append(new String(Base64.getEncoder().encode((byte[])data)))
    			.append(QUOTE);
    		return sb.toString();
		}
		sb.append(BRACES_L);
		if(stuff.beautify) {
			sb.append(ENTER);
		}
		boolean finalSpace = false;
		if(data instanceof boolean[]) {
			int index = ((boolean[])data).length;
    		if(stuff.beautify) {
    			sb.deleteCharAt(sb.length() - 1);
    		}
	    	for(boolean b: (boolean[])data) {
	    		sb.append(b);
	    		if(index != 1) {
	    			sb.append(COMMA).append(SPACE);
	    		}
	    		--index;
	    	}
		} else if(data instanceof int[]) {
			int index = ((int[])data).length;
    		if(stuff.beautify) {
    			sb.deleteCharAt(sb.length() - 1);
    		}
	    	for(int b: (int[])data) {
	    		sb.append(b);
	    		if(index != 1) {
	    			sb.append(COMMA).append(SPACE);
	    		}
	    		--index;
	    	}
		} else if(data instanceof long[]) {
			int index = ((long[])data).length;
    		if(stuff.beautify) {
    			sb.deleteCharAt(sb.length() - 1);
    		}
	    	for(long b: (long[])data) {
	    		sb.append(b);
	    		if(index != 1) {
	    			sb.append(COMMA).append(SPACE);
	    		}
	    		--index;
	    	}
		} else if(data instanceof char[]) {
			int index = ((char[])data).length;
    		if(stuff.beautify) {
    			sb.deleteCharAt(sb.length() - 1);
    		}
	    	for(char b: (char[])data) {
	    		sb.append(SINGLE_QUOTE).append(b).append(SINGLE_QUOTE);
	    		if(index != 1) {
	    			sb.append(COMMA).append(SPACE);
	    		}
	    		--index;
	    	}
		} else if(data instanceof float[]) {
			int index = ((float[])data).length;
    		if(stuff.beautify) {
    			sb.deleteCharAt(sb.length() - 1);
    		}
	    	for(float b: (float[])data) {
	    		sb.append(b);
	    		if(index != 1) {
	    			sb.append(COMMA).append(SPACE);
	    		}
	    		--index;
	    	}
		} else if(data instanceof double[]) {
			int index = ((double[])data).length;
    		if(stuff.beautify) {
    			sb.deleteCharAt(sb.length() - 1);
    		}
	    	for(double b: (double[])data) {
	    		sb.append(b);
	    		if(index != 1) {
	    			sb.append(COMMA).append(SPACE);
	    		}
	    		--index;
	    	}
		} else if(data instanceof String[]) {
			finalSpace = true;
			int index = ((String[])data).length;
	    	for(String b: (String[])data) {
	    		if(stuff.beautify) {
	    			sb.append(base).append(TAB);
	    		}
	    		sb.append(QUOTE).append(b).append(QUOTE);
	    		if(index != 1) {
	    			sb.append(COMMA);
	    		}
	    		if(stuff.beautify) {
	    			sb.append(ENTER);
	    		}
	    		--index;
	    	}
		} else {
			finalSpace = true;
			int index = ((Object[])data).length;
	    	for(Object b: (Object[])data) {
	    		if(stuff.beautify) {
	    			sb.append(base).append(TAB);
	    		}
	    		sb.append(formatObject(b, tabCount + 1,  false, stuff));
	    		if(index != 1) {
	    			sb.append(COMMA);
	    		}
	    		if(stuff.beautify) {
	    			sb.append(ENTER);
	    		}
	    		--index;
	    	}
		}
		if(stuff.beautify && finalSpace) {
			sb.append(base);
		}
    	sb.append(BRACES_R);
    	
    	return sb.toString();
	}

	static String formatString(String src) {
		return src.replace(SRC_QOUTE, DST_QOUTE);
	}

	static String formatPrimitive(Object val) 
			throws XJSONException {
		Class<?> cls = val.getClass();
		if(BOOL_TYPE.equals(cls.getName())) {
			return String.valueOf((boolean) val);
		} else if(BYTE_TYPE.equals(cls.getName())) {
			return String.valueOf((byte) val);
		} else if(CHAR_TYPE.equals(cls.getName())) {
			return SINGLE_QUOTE+String.valueOf((char) val)+SINGLE_QUOTE;
		} else if(SHORT_TYPE.equals(cls.getName())) {
			return String.valueOf((short) val);
		} else if(INT_TYPE.equals(cls.getName())) {
			return String.valueOf((int) val);
		} else if(LONG_TYPE.equals(cls.getName())) {
			return String.valueOf((long) val);
		} else if(FLOAT_TYPE.equals(cls.getName())) {
			return String.valueOf((float) val);
		} else if(DOUBLE_TYPE.equals(cls.getName())) {
			return String.valueOf((double) val);
		}
		throw new XJSONException("unknown primitive type '" 
				+ cls.getName() + "'");
	}
	
	static String stringifyOneObject(Object data, JSONStuff stuff) {
		return formatObject(data, 0, true, stuff);
	}
}
