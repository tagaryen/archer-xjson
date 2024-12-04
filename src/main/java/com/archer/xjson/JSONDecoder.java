package com.archer.xjson;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;

class JSONDecoder {	
	
	static final char BRACES_L = '[';
	static final char BRACES_R = ']';
	static final char B_BRACES_L = '{';
	static final char B_BRACES_R = '}';
	static final char COMMA = ',';
	static final char COLON = ':';
	static final char DOT = '.';
	static final char SPACE = ' ';
	static final char ENTER = '\n';
	static final char LINEB = '\r';
	static final char TAB = '\t';
	static final char QUOTE = '"';
	static final char SINGLE_QUOTE = '\'';
	static final char BACKSLASH = '\\';
	static final char NEG = '-';

	static final char[] NULL = {'n', 'u', 'l', 'l'};
	static final char[] TRUE = {'t', 'r', 'u', 'e'};
	static final char[] FALSE = {'f', 'a', 'l', 's', 'e'};
	
	static final short K_DEFAULT = 0;
	static final short K_STARTED = 1;
	static final short K_ENDED = 2;
	static final short V_DEFAULT = 3;
	static final short V_STARTED = 4;
	static final short V_ENDED = 5;
	static final short OBJ_ENDED = 6;
	

	static final short A_D = 0;
	static final short A_NUM_S = 1;
	static final short A_STR_S = 2;
	static final short A_ARR_S = 3;
	static final short A_OBJ_S = 4;
	static final short A_VAL_E = 5;
	static final short A_E = 6;
	
	static int parseOneObj(char[] chars, int offset, LinkedHashMap<String, Object> ret) 
			throws XJSONException {
		int keyL = 0, keyR = 0, valL = 0, valR = 0;
		short state = K_DEFAULT;
		int i = offset;
		boolean objBegin = false;
		boolean objVal = false, arrVal = false, numVal = false, numDot = false;
		for(; i < chars.length; i++) {
			if(!numVal) {
				if(SPACE == chars[i] || ENTER == chars[i] || 
					LINEB == chars[i] || TAB == chars[i]) {
					continue;
				}
			}
			if(!objBegin) {
				if(B_BRACES_L != chars[i]) {
					throw new XJSONException(XJSONException.getErrorMsg(chars, i));
				} else {
					objBegin = true;
					continue;
				}
			}
			if(K_DEFAULT == state) {
				if(QUOTE != chars[i]) { 
					if(B_BRACES_R == chars[i]) {
						state = OBJ_ENDED;
						continue;
					}
					throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
				} else {
					state = K_STARTED;
					keyL = i + 1;
					continue;
				}
			}
			if(K_STARTED == state) {
				if(QUOTE != chars[i]) {
					continue;
				} else {
					if(BACKSLASH != chars[i-1]) {
						state = K_ENDED;
						keyR = i;
					}
					continue;
				}
			}
			if(K_ENDED == state) {
				if(COLON != chars[i]) {
					throw new XJSONException(XJSONException.getErrorMsg(chars, keyL-1));
				} else {
					state = V_DEFAULT;
					continue;
				}
			}
			if(V_DEFAULT == state) {
				if(NULL[0] == chars[i]) {
					String key = new String(
							Arrays.copyOfRange(chars, keyL, keyR));
					for(int j = 0; j < NULL.length; j++) {
						if(i >= chars.length || NULL[j] != chars[i + j]) {
							throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
						}
					}
					ret.put(key, null);
					i += NULL.length - 1;
					state = V_ENDED;
					continue;
				}
				if(TRUE[0] == chars[i]) {
					String key = new String(
							Arrays.copyOfRange(chars, keyL, keyR));
					for(int j = 0; j < TRUE.length; j++) {
						if(i >= chars.length || TRUE[j] != chars[i + j]) {
							throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
						}
					}
					ret.put(key, true);
					i += TRUE.length - 1;
					state = V_ENDED;
					continue;
				}
				if(FALSE[0] == chars[i]) {
					String key = new String(
							Arrays.copyOfRange(chars, keyL, keyR));
					for(int j = 0; j < FALSE.length; j++) {
						if(i >= chars.length || FALSE[j] != chars[i + j]) {
							throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
						}
					}
					ret.put(key, false);
					i += FALSE.length - 1;
					state = V_ENDED;
					continue;
				}
				if(48 <= chars[i] && chars[i] <= 57) {
					state = V_STARTED;
					valL = i;
					numVal = true;
					continue;
				}
				if(NEG == chars[i]) {
					state = V_STARTED;
					valL = i;
					numVal = true;
					continue;
				}
				if(SINGLE_QUOTE == chars[i]) {
					String key = new String(
							Arrays.copyOfRange(chars, keyL, keyR));
					if(i + 2 < chars.length && SINGLE_QUOTE == chars[i + 2]) {
						ret.put(key, (Character) chars[i + 1]);
						i += 2;
						state = V_ENDED;
						continue;
					}
					throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
				}
				if(QUOTE == chars[i]) {
					state = V_STARTED;
					valL = i + 1;
					continue;
				} 
				if(BRACES_L == chars[i]) {
					state = V_STARTED;
					valL = i;
					arrVal = true;
					continue;
				}
				if(B_BRACES_L == chars[i]) {
					state = V_STARTED;
					valL = i;
					objVal = true;
					continue;
				}
				throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
			}
			if(V_STARTED == state) {
				if(objVal) {
					String key = new String(
							Arrays.copyOfRange(chars, keyL, keyR));
					LinkedHashMap<String, Object> child = new LinkedHashMap<>();
					int index = parseOneObj(chars, valL, child);
					ret.put(key, child);
					i = index - 1;
					objVal = false;
					state = V_ENDED;
					continue;
				} else if(arrVal) {
					String key = new String(
							Arrays.copyOfRange(chars, keyL, keyR));
					LinkedList<Object> child = new LinkedList<>();
					int index = parseOneArr(chars, valL, child);
					ret.put(key, child);
					i = index - 1;
					arrVal = false;
					state = V_ENDED;
					continue;
				} else if(numVal) {
					if(48 <= chars[i] && chars[i] <= 57) {
						continue;
					}
					if(!numDot && DOT == chars[i]) {
						numDot = true;
						continue;
					}
					if(numDot && DOT == chars[i]) {
						throw new XJSONException(XJSONException.getErrorMsg(chars, valL-1));
					}
					valR = i;
					String key = new String(
							Arrays.copyOfRange(chars, keyL, keyR));
					ret.put(key, parseToNumber(
							Arrays.copyOfRange(chars, valL, valR)));
					numVal = false;
					numDot = false;
					if(B_BRACES_R == chars[i]) {
						state = OBJ_ENDED;
						continue;
					} else if(COMMA == chars[i]) {
						state = K_DEFAULT;
						continue;
					} else {
						state = V_ENDED;
						continue;
					}
				} else {
					if(QUOTE != chars[i]) {
						continue;
					} else {
						if(BACKSLASH != chars[i-1]) {
							state = K_ENDED;
							valR = i;

							String key = new String(
									Arrays.copyOfRange(chars, keyL, keyR));
							ret.put(key, 
									parseToString(Arrays.copyOfRange(chars, valL, valR)));
							state = V_ENDED;
						}
						continue;
					}
				}
			}
			if(V_ENDED == state) {
				if(COMMA == chars[i]) {
					state = K_DEFAULT;
					continue;
				}
				if(B_BRACES_R == chars[i]) {
					state = OBJ_ENDED;
					continue;
				}
				throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
			}
			if(OBJ_ENDED == state) {
				break;
			}
			throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
		}
		if(offset == 0 && i < chars.length) {
			throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
		}
		
		return i;
	}

	static int parseOneArr(char[] chars, int offset, LinkedList<Object> ret) 
			throws XJSONException {
		int i = offset, valL = 0, valR = 0;
		short state = A_D;
		boolean arrBegin = false, numVal = false, numDot = false;
		for(; i < chars.length; i++) {
			if(SPACE == chars[i] || ENTER == chars[i] || 
			   LINEB == chars[i] || TAB == chars[i]) {
				if(numVal) {
					throw new XJSONException(XJSONException.getErrorMsg(chars, valL-1));
				}
				continue;
			}
			if(!arrBegin) {
				if(BRACES_L != chars[i]) {
					throw new XJSONException(XJSONException.getErrorMsg(chars, i));
				} else {
					arrBegin = true;
					continue;
				}
			}
			if(A_D == state) {
				valL = i;
				if(NULL[0] == chars[i]) {
					for(int j = 0; j < NULL.length; j++) {
						if(i >= chars.length || NULL[j] != chars[i + j]) {
							throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
						}
					}
					ret.add(null);
					i += TRUE.length;
					state = A_VAL_E;
					continue;
				}
				if(TRUE[0] == chars[i]) {
					for(int j = 0; j < TRUE.length; j++) {
						if(i >= chars.length || TRUE[j] != chars[i + j]) {
							throw new XJSONException(XJSONException.getErrorMsg(chars, i));
						}
					}
					ret.add(true);
					i += TRUE.length;
					state = A_VAL_E;
					continue;
				}
				if(FALSE[0] == chars[i]) {
					for(int j = 0; j < FALSE.length; j++) {
						if(i >= chars.length || FALSE[j] != chars[i + j]) {
							throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
						}
					}
					ret.add(false);
					i += FALSE.length;
					state = A_VAL_E;
					continue;
				}
				if(48 <= chars[i] && chars[i] <= 57) {
					state = A_NUM_S;
					numVal = true;
					continue;
				}
				if(SINGLE_QUOTE == chars[i]) {
					if(i + 2 < chars.length && SINGLE_QUOTE == chars[i + 2]) {
						ret.add((Character) chars[i + 1]);
						i += 2;
						state = A_VAL_E;
						continue;
					}
					throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
				}
				if(QUOTE == chars[i]) {
					state = A_STR_S;
					continue;
				}
				if(BRACES_L == chars[i]) {
					state = A_ARR_S;
					continue;
				}
				if(B_BRACES_L == chars[i]) {
					state = A_OBJ_S;
					continue;
				}
				if(BRACES_R == chars[i]) {
					state = A_E;
					continue;
				}
				throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
			}
			if(A_NUM_S == state) {
				if(48 <= chars[i] && chars[i] <= 57) {
					continue;
				}
				if(!numDot && DOT == chars[i]) {
					numDot = true;
					continue;
				}
				if(numDot && DOT == chars[i]) {
					throw new XJSONException(XJSONException.getErrorMsg(chars, valL-1));
				}
				valR = i;
				ret.add(parseToNumber(
						Arrays.copyOfRange(chars, valL, valR)));
				numVal = false;
				numDot = false;
				if(COMMA == chars[i]) {
					state = A_D;
					continue;
				}
				if(BRACES_R == chars[i]) {
					state = A_E;
					continue;
				}
				throw new XJSONException(XJSONException.getErrorMsg(chars, valL-1));
			}
			if(A_STR_S == state) {
				if(QUOTE != chars[i]) {
					continue;
				} else {
					if(BACKSLASH != chars[i-1]) {
						state = A_VAL_E;
						valR = i;
						ret.add(
							parseToString(Arrays.copyOfRange(chars, valL + 1, valR)));
					}
					continue;
				}
			}
			if(A_ARR_S == state) {
				LinkedList<Object> child = new LinkedList<>();
				int index = parseOneArr(chars, valL, child);
				ret.add(child);
				i = index - 1;
				state = A_VAL_E;
				continue;
			}
			if(A_OBJ_S == state) {
				LinkedHashMap<String, Object> child = new LinkedHashMap<>();
				int index = parseOneObj(chars, valL, child);
				ret.add(child);
				i = index - 1;
				state = A_VAL_E;
				continue;
			}
			if(A_VAL_E == state) {
				if(COMMA == chars[i]) {
					state = A_D;
					continue;
				}
				if(BRACES_R == chars[i]) {
					state = A_E;
					continue;
				}
				throw new XJSONException(XJSONException.getErrorMsg(chars, valL-1));
			}
			if(A_E == state) {
				break;
			}
			throw new XJSONException(XJSONException.getErrorMsg(chars, valL-1));
		}
		if(offset == 0 && i < chars.length) {
			throw new XJSONException(XJSONException.getErrorMsg(chars, i-1));
		}
		return i;
	}
	
	static Number parseToNumber(char[] in) throws XJSONException {
		String s = new String(in);
		try {
			return Integer.parseInt(s);
		} catch(Exception ignore) {}
		try {
			return Long.parseLong(s);
		} catch(Exception ignore) {}
		try {
			return Double.parseDouble(s);
		} catch(Exception ignore) {}
		throw new XJSONException(XJSONException.getErrorMsg(in, 0));
	}
	
	static Object parseToString(char[] in) {
		StringBuilder sb = new StringBuilder(in.length);
		for(int i = 0; i < in.length; i++) {
			if(in[i] == BACKSLASH) {
				continue;
			}
			sb.append(in[i]);
		}
		return sb.toString();
	}

	static LinkedHashMap<String, Object> parseToMap(String json) 
			throws XJSONException {
		LinkedHashMap<String, Object> ret = new LinkedHashMap<>();
		parseOneObj(json.toCharArray(), 0, ret);
		return ret;
	}
	
	static LinkedList<Object> parseToList(String json) 
			throws XJSONException {
		LinkedList<Object> ret = new LinkedList<>();
		parseOneArr(json.toCharArray(), 0, ret);
		return ret;
	}
	
	static <T> T parseToClass(String json, Class<T> clazz, JSONReflect reflector) 
			throws XJSONException {
		LinkedHashMap<String, Object> obj = parseToMap(json);
		return reflector.reflectOneClass(obj, clazz);
	}
	
	static <T> T parseToClass(String json, JavaTypeRef<T> ref, JSONReflect reflector) {
		LinkedHashMap<String, Object> obj = parseToMap(json);
		return reflector.reflectOneClass(obj, ref);
	}
	
	static <T> T parseToClass(String json, Type type, JSONReflect reflector) 
			throws XJSONException {
		LinkedHashMap<String, Object> obj = parseToMap(json);
		return reflector.reflectOneClass(obj, type);
	}
	
	static <T> LinkedList<T> parseToClassList(String json, Class<T> clazz, JSONReflect reflector) 
			throws XJSONException {
		LinkedList<Object> obj = parseToList(json);
		return reflector.reflectOneList(obj, clazz);
	}
	
	static <T> LinkedList<T> parseToClassList(String json, JavaTypeRef<T> ref, JSONReflect reflector) 
			throws XJSONException {
		LinkedList<Object> obj = parseToList(json);
		return reflector.reflectOneList(obj, ref);
	}
	
	static <T> LinkedList<T> parseToClassList(String json, Type type, JSONReflect reflector) 
			throws XJSONException {
		LinkedList<Object> obj = parseToList(json);
		return reflector.reflectOneList(obj, type);
	}
}
