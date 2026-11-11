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
		int failures = 0;
		try {
			List<ParamDef> params = xjson.parseList(json, ParamDef.class);
			for(ParamDef p : params) {
				System.out.println("****:"+p.getDefaultValue()+":****");
			}
			// 码点级断言：rowSplit 的 defaultValue/value 必须恰好是 CR LF 两个字符
			ParamDef rowSplit = params.get(2);
			failures += assertEscapes("defaultValue", rowSplit.getDefaultValue());
			failures += assertEscapes("value", rowSplit.getValue());
			// cellSplit 的三个空格必须原样保留
			ParamDef cellSplit = params.get(1);
			if(!"   ".equals(cellSplit.getDefaultValue())) {
				System.out.println("FAIL: cellSplit.defaultValue 应为 3 个空格, 实际长度="
						+ (cellSplit.getDefaultValue() == null ? "null" : cellSplit.getDefaultValue().length()));
				failures++;
			}
			// round-trip：序列化后 CR/LF 必须重新转义为 \\r \\n，再次解析结果一致
			String out = xjson.stringify(params);
			if(!out.contains("\\r\\n")) {
				System.out.println("FAIL: 序列化输出未包含 \\r\\n 转义: " + out);
				failures++;
			}
			List<ParamDef> reparsed = xjson.parseList(out, ParamDef.class);
			failures += assertEscapes("round-trip defaultValue", reparsed.get(2).getDefaultValue());
		} catch (Exception e) {
			e.printStackTrace();
			failures++;
		}
		if(failures == 0) {
			System.out.println("TEST2 PASSED: \\r\\n 等特殊字符解码/再编码均正确");
		} else {
			System.out.println("TEST2 FAILED: " + failures + " 处断言失败");
		}
	}

	/** 断言值恰好为 CR(0x0D) LF(0x0A) 两字符，并打印码点 */
	static int assertEscapes(String field, String val) {
		if("\r\n".equals(val)) {
			System.out.println("OK: " + field + " = [0x0D, 0x0A] (\\r\\n 已正确解码)");
			return 0;
		}
		StringBuilder sb = new StringBuilder("FAIL: " + field + " 码点=[");
		if(val != null) {
			for(int i = 0; i < val.length(); i++) {
				sb.append(String.format("0x%02X ", (int) val.charAt(i)));
			}
		} else {
			sb.append("null");
		}
		System.out.println(sb.append(']'));
		return 1;
	}
	
	static void test1() {

		String json = "{\"name\":\"你好\",\"age\":18,\"income\":7.2687132222E7,\"children\":[\"儿子1\",\"儿子2\"]}";

		EntityObj obj0 = XJSONStatic.parse(json, EntityObj.class);
		System.out.println(obj0.getChildren().get(1));
		

		Obj ob = XJSONStatic.parse(json, Obj.class);
		System.out.println(ob.getName());
	}

	public static void main(String args[]) {
		test1();
		test2();
	}
	
}
