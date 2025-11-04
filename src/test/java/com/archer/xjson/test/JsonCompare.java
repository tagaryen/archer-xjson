package com.archer.xjson.test;

import java.lang.reflect.Field;

import com.archer.xjson.XJSON;
import com.archer.xjson.XJSONStatic;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonCompare {
	
	public static void objectMapperTest() {
		String json = "{\"name\":\"你好\",\"age\":18,\"income\":7.2687132222E7,\"children\":[\"儿子1\",\"儿子2\"]}";
		
		ObjectMapper om = new ObjectMapper();
		long start = System.currentTimeMillis();
		for(int i = 0; i < 40000; i++) {
			try {
				om.readValue(json, EntityObj.class);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("object mapper cost = " + (end - start) + "ms");
	}
	
	public static void xjsonTest() {
		String json = "{\"name\":\"你好\",\"age\":18,\"income\":7.2687132222E7,\"children\":[\"儿子1\",\"儿子2\"]}";
		XJSON xjson = new XJSON();
		long start = System.currentTimeMillis();
		for(int i = 0; i < 40000; i++) {
			try {
				xjson.parse(json, EntityObj.class);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("xjson cost = " + (end - start) + "ms");
	}
	
	public static void main(String args[]) {

		String json = "{\"name\":\"你好\",\"age\":18,\"income\":7.2687132222E7,\"children\":[\"儿子1\",\"儿子2\"]}";

		EntityObj obj0 = XJSONStatic.parse(json, EntityObj.class);
		System.out.println(obj0.getName());
		

		Obj ob = XJSONStatic.parse(json, Obj.class);
		System.out.println(ob.getName());
		
		ObjectMapper om = new ObjectMapper();
		try {
			EntityObj obj1 = om.readValue(json, EntityObj.class);
			System.out.println(obj1.getName());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
