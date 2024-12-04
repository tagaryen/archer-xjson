package com.archer.xjson;

import java.util.concurrent.ConcurrentHashMap;

class JSONStuff {

	ConcurrentHashMap<Class<?>, XJSONSerializer> CODER_MAP = 
			new ConcurrentHashMap<>();
	
	boolean beautify = false;
	
}
