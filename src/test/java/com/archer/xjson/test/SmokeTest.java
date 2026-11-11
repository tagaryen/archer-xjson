package com.archer.xjson.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.archer.xjson.asm.XNJSON;

/**
 * P1/P3 冒烟正确性验证：
 * byte[] 直解析串行 / SWAR 边界并行 双路径交叉比对。
 */
public class SmokeTest {

	static int failures = 0;

	public static void main(String[] args) {
		// 构造带转义、null、边界值的数据集
		List<HD3D> src = new ArrayList<>();
		for (int i = 0; i < 30000; i++) {
			HD3D h = new HD3D();
			h.setCrossline((long) i);
			h.setX(i * 1.5);
			h.setY(i * -2.5);
			h.setZ(i % 17 == 0 ? 0.0 : i * 3.75);
			h.setSurvey_Name(i % 7 == 0 ? null : "survey\"\\中\n文" + i);
			h.setStrata_Name("strata" + (i % 11));
			h.setInline(i % 13 == 0 ? null : (long) (i * 10));
			src.add(h);
		}
		XNJSON xn = new XNJSON();
		byte[] json = xn.stringifyBytes(src);
		System.out.println("json bytes = " + json.length);

		// 1) 串行实体列表（P1 byte[] 零拷贝路径）
		List<HD3D> list = xn.parseList(json, HD3D.class);
		check(list.size() == src.size(), "serial list size");
		for (int i = 0; i < src.size(); i += 1) {
			checkEq(list.get(i), src.get(i), "serial list[" + i + "]");
			if (i > 300) break;
		}
		for (int i = 0; i < src.size(); i += 997) {
			checkEq(list.get(i), src.get(i), "serial list[" + i + "]");
		}

		// 2) 并行实体列表（P3）
		List<HD3D> plist = xn.parseListParallel(json, HD3D.class);
		check(plist.size() == src.size(), "parallel list size");
		for (int i = 0; i < src.size(); i += 997) {
			checkEq(plist.get(i), src.get(i), "parallel list[" + i + "]");
		}

		// 3) 通用树 byte[] 路径
		Object tree = xn.parseList(json);
		check(tree instanceof List && ((List<?>) tree).size() == src.size(), "generic tree size");

		if (failures > 0) {
			System.out.println("SMOKE TEST FAILED: " + failures + " failures");
			System.exit(1);
		}
		System.out.println("ALL SMOKE TESTS PASSED");
	}

	static void checkEq(HD3D a, HD3D b, String where) {
		check(Objects.equals(a.getCrossline(), b.getCrossline()), where + " Crossline");
		check(a.getX() == b.getX(), where + " X");
		check(a.getY() == b.getY(), where + " Y");
		check(a.getZ() == b.getZ(), where + " Z");
		check(Objects.equals(a.getSurvey_Name(), b.getSurvey_Name()), where + " Survey_Name");
		check(Objects.equals(a.getStrata_Name(), b.getStrata_Name()), where + " Strata_Name");
		check(Objects.equals(a.getInline(), b.getInline()), where + " Inline");
	}

	static void check(boolean ok, String what) {
		if (!ok) {
			failures++;
			System.out.println("FAIL: " + what);
		}
	}
}
