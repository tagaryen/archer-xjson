package com.archer.xjson;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class XJSON {
	
	private JSONReflect reflector;
	
	private JSONStuff stuff;
	
	public XJSON() {
		reflector = new JSONReflect();
		stuff = new JSONStuff();
	}

	public Map<String, Object> parse(String json) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToMap(json);
	}

	public List<Object> parseList(String json) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToList(json);
	}

	public Map<String, Object> parse(byte[] json) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToMap(json);
	}

	public List<Object> parseList(byte[] json) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToList(json);
	}

	public <T> T parse(byte[] json, Class<T> clazz) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json, clazz, reflector);
	}

	public <T> T parse(byte[] json, Type type) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json, type, reflector);
	}

	public <T> T parse(byte[] json, JavaTypeRef<T> ref) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json, ref, reflector);
	}

	public <T> List<T> parseList(byte[] json, Class<T> clazz) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, clazz, reflector);
	}

	public <T> List<T> parseList(byte[] json, JavaTypeRef<T> ref) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, ref, reflector);
	}

	public <T> List<T> parseList(byte[] json, Type type) throws XJSONException {
		if(null == json || json.length == 0) {
			throw new XJSONException("input json bytes can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, type, reflector);
	}

	public Map<String, Object> parse(InputStream in) throws XJSONException, IOException {
		if(null == in) {
			throw new XJSONException("input json stream can not be null.");
		}
		return JSONDecoder.parseToMap(JSONDecoder.readAllBytes(in));
	}

	public List<Object> parseList(InputStream in) throws XJSONException, IOException {
		if(null == in) {
			throw new XJSONException("input json stream can not be null.");
		}
		return JSONDecoder.parseToList(JSONDecoder.readAllBytes(in));
	}

	public <T> T parse(InputStream in, Class<T> clazz) throws XJSONException, IOException {
		if(null == in) {
			throw new XJSONException("input json stream can not be null.");
		}
		return JSONDecoder.parseToClass(JSONDecoder.readAllBytes(in), clazz, reflector);
	}

	public <T> List<T> parseList(InputStream in, Class<T> clazz) throws XJSONException, IOException {
		if(null == in) {
			throw new XJSONException("input json stream can not be null.");
		}
		return JSONDecoder.parseToClassList(JSONDecoder.readAllBytes(in), clazz, reflector);
	}
	
	public <T> T parse(String json, Class<T> clazz) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json,  clazz, reflector);
	}
	
	public <T> T parse(String json, Type type) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json, type, reflector);
	}
	
	public <T> T parse(String json, JavaTypeRef<T> ref) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClass(json,  ref, reflector);
	}

	public <T> List<T> parseList(String json, Class<T> clazz) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, clazz, reflector);
	}
	
	public <T> List<T> parseList(String json, JavaTypeRef<T> ref) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, ref, reflector);
	}

	public <T> List<T> parseList(String json, Type type) throws XJSONException {
		if(null == json || json.trim().isEmpty()) {
			throw new XJSONException("input json string can not be null or empty.");
		}
		return JSONDecoder.parseToClassList(json, type, reflector);
	}
	
	public String stringify(Object data) throws XJSONException  {
		return JSONEncoder.stringifyOneObject(data, stuff);
	}
	
	public void setSerializer(Class<?> cls, XJSONSerializer serializer) {
		stuff.CODER_MAP.put(cls,  serializer);
	}
	
	public void setDeserializer(Class<?> cls, XJSONDeserializer deserializer) {
		reflector.CODER_MAP.put(cls,  deserializer);
	}
	
	public void useStrictJsonMode(boolean mode) {
		reflector.strictJsonMode = mode;
	}
	
	public void useStrictClassMode(boolean mode) {
		reflector.strictClassMode = mode;
	}

	public void useBeautifyMode(boolean mode) {
		stuff.beautify = mode;
	}

}
