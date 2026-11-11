package com.archer.xjson.test;

import java.util.List;

import com.archer.xjson.XJSON;
import com.archer.xjson.XJSONStatic;

public class JsonCompare {
	
	public static void xjsonTest() {
		String json = "{\"name\":\"你好\",\"age\":\"18\",\"income\":7.2687132222E7,\"children\":[\"儿子1\",\"儿子2\"]}";
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

	static void test2() {
		String json = "[{\"paramName\":\"rowNames\",\"enDescription\":\"行别名\",\"required\":false,\"readable\":false},{\"paramName\":\"cellSplit\",\"enDescription\":\"列分割符\",\"required\":false,\"readable\":false,\"defaultValue\":\"   \",\"value\":\"   \"},{\"paramName\":\"rowSplit\",\"enDescription\":\"行分割符\",\"required\":false,\"readable\":false,\"defaultValue\":\"\\r\\n\",\"value\":\"\\r\\n\"},{\"paramName\":\"fileNameField\",\"enDescription\":\"文件名字段\",\"required\":false,\"readable\":false}]";
		XJSON xjson = new XJSON();
		try {
			List<ParamDef> params = xjson.parseList(json, ParamDef.class);
			for(ParamDef p : params) {
				System.out.println("****:"+p.getDefaultValue()+":****");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void test1() {

		String json = "{\"name\":\"你好\",\"age\":18,\"income\":7.2687132222E7,\"children\":[\"儿子1\",\"儿子2\"]}";

		EntityObj obj0 = XJSONStatic.parse(json, EntityObj.class);
		System.out.println(obj0.getChildren().get(1));
		

		Obj ob = XJSONStatic.parse(json, Obj.class);
		System.out.println(ob.getName());
	}

	public static void main(String args[]) {
		test2();
	}
	
}
