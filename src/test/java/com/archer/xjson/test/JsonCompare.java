package com.archer.xjson.test;

import com.archer.xjson.XJSON;
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
		Double a = 1271267643.543D;
		System.out.println(a.toString());
		objectMapperTest();
		xjsonTest();
	}
	
}
