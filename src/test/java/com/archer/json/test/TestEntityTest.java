package com.archer.json.test;

import com.archer.xjson.light.XJson;
import com.archer.xjson.light.XTypeRef;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TestEntity 全量功能测试：覆盖序列化/反序列化、null、容器、泛型、特殊字符、未知字段容忍。
 * 运行：java com.archer.json.test.TestEntityTest
 */
public class TestEntityTest {

    private static int passed;

    /** 泛型包装，验证 XTypeRef 路径 */
    public static class Base<T> {
        public int code;
        public String message;
        public T data;
    }

    public static void main(String[] args) {
        testFullRoundtrip();
        testJsonShape();
        testNullFields();
        testPartialNull();
        testSpecialChars();
        testAllEnumValues();
        testTopLevelContainers();
        testGenericWrapper();
        testGenericParseShape();
        testUnknownFieldTolerant();
        testBytesAndStringEntryEquivalence();
        testDateMultiFormat();
        System.out.println("ALL " + passed + " TESTS PASSED");
    }

    // ---------------- 用例 ----------------

    /** 全字段填充后的 bytes/String 两个入口 roundtrip，逐字段比对 */
    static void testFullRoundtrip() {
        TestEntity src = fullEntity();
        byte[] json = XJson.toJsonBytes(src);

        TestEntity back = XJson.parseObject(json, TestEntity.class);
        assertEntityEquals(src, back, "bytes roundtrip");

        TestEntity back2 = XJson.parseObject(new String(json, StandardCharsets.UTF_8), TestEntity.class);
        assertEntityEquals(src, back2, "string roundtrip");

        String s = XJson.toJson(src);
        assertEntityEquals(src, XJson.parseObject(s, TestEntity.class), "toJson roundtrip");
        ok("testFullRoundtrip");
    }

    /** JSON 文本形态：枚举=字符串、JSR310=ISO 字符串、sql.Date=millis 数字、字段名正确 */
    static void testJsonShape() {
        TestEntity e = fullEntity();
        String s = XJson.toJson(e);
        check(s.contains("\"name\":\"张三\""), "shape name");
        check(s.contains("\"createTime\":\"2026-11-12 10:20:30.123\""), "shape createTime");
        check(s.contains("\"importTime\":\"2026-11-01\""), "shape importTime");
        check(s.contains("\"exportTime\":1735689600000"), "shape exportTime millis");
        check(s.contains("\"status\":\"MSG_SUCCESS\""), "shape enum name");
        check(s.contains("\"statusMsg\":{\"status\":\"MSG_FAILED\",\"msg\":\"失败原因\"}"), "shape nested");
        // 手写新格式 JSON 字面量直接解析
        TestEntity lit = XJson.parseObject(
                "{\"createTime\":\"2026-11-12 10:20:30.123\",\"importTime\":\"2026-11-01\"}", TestEntity.class);
        check(LocalDateTime.of(2026, 11, 12, 10, 20, 30, 123000000).equals(lit.getCreateTime()), "literal createTime");
        check(LocalDate.of(2026, 11, 1).equals(lit.getImportTime()), "literal importTime");
        ok("testJsonShape");
    }

    /** 全 null 字段：序列化为 {}，反序列化回来仍全 null */
    static void testNullFields() {
        TestEntity e = new TestEntity();
        String s = XJson.toJson(e);
        check("{}".equals(s), "all-null serializes to {}, got: " + s);
        TestEntity back = XJson.parseObject(s, TestEntity.class);
        check(back.getName() == null && back.getCreateTime() == null && back.getImportTime() == null
                && back.getExportTime() == null && back.getStatus() == null && back.getStatusMsg() == null,
                "all-null roundtrip");
        ok("testNullFields");
    }

    /** 部分 null：JSON 中显式 null 与缺字段都应解析为 null */
    static void testPartialNull() {
        TestEntity back = XJson.parseObject(
                "{\"name\":\"abc\",\"createTime\":null,\"status\":\"MSG_PENDING\"}", TestEntity.class);
        check("abc".equals(back.getName()), "partial name");
        check(back.getCreateTime() == null, "partial createTime explicit null");
        check(back.getImportTime() == null && back.getExportTime() == null, "partial absent -> null");
        check(back.getStatus() == TestEntity.TestStatus.MSG_PENDING, "partial enum");
        check(back.getStatusMsg() == null, "partial nested null");
        ok("testPartialNull");
    }

    /** name 含引号、换行、反斜杠、中文、emoji 的 roundtrip */
    static void testSpecialChars() {
        TestEntity e = new TestEntity();
        e.setName("引号\"换行\n反斜杠\\中文😀emoji");
        TestEntity back = XJson.parseObject(XJson.toJsonBytes(e), TestEntity.class);
        check(Objects.equals(e.getName(), back.getName()), "special chars name");
        ok("testSpecialChars");
    }

    /** 枚举所有取值 + 顶层枚举单独 roundtrip */
    static void testAllEnumValues() {
        for (TestEntity.TestStatus st : TestEntity.TestStatus.values()) {
            TestEntity e = new TestEntity();
            e.setStatus(st);
            TestEntity back = XJson.parseObject(XJson.toJsonBytes(e), TestEntity.class);
            check(back.getStatus() == st, "enum " + st);
            // 顶层枚举
            TestEntity.TestStatus top = XJson.parseObject(XJson.toJsonBytes(st), TestEntity.TestStatus.class);
            check(top == st, "top-level enum " + st);
        }
        // 非法枚举值应抛异常
        boolean thrown = false;
        try {
            XJson.parseObject("{\"status\":\"NOT_EXIST\"}", TestEntity.class);
        } catch (RuntimeException ex) {
            thrown = true;
        }
        check(thrown, "illegal enum value should throw");
        ok("testAllEnumValues");
    }

    /** 顶层 List / 数组 / Map 容器 via XTypeRef */
    static void testTopLevelContainers() {
        TestEntity a = fullEntity();
        TestEntity b = new TestEntity();
        b.setName("李四");
        b.setStatus(TestEntity.TestStatus.MSG_FAILED);

        List<TestEntity> list = XJson.parseObject(XJson.toJsonBytes(Arrays.asList(a, b)),
                new XTypeRef<List<TestEntity>>() {});
        check(list.size() == 2, "list size");
        assertEntityEquals(a, list.get(0), "list[0]");
        check("李四".equals(list.get(1).getName()), "list[1].name");

        TestEntity[] arr = XJson.parseObject(XJson.toJsonBytes(new TestEntity[]{a, b}), TestEntity[].class);
        check(arr.length == 2, "array size");
        assertEntityEquals(a, arr[0], "arr[0]");

        Map<String, TestEntity> map = XJson.parseObject(XJson.toJsonBytes(
                java.util.Collections.singletonMap("k", a)), new XTypeRef<Map<String, TestEntity>>() {});
        assertEntityEquals(a, map.get("k"), "map value");
        ok("testTopLevelContainers");
    }

    /** 泛型包装 Base<T>：直接实体与嵌套 List */
    static void testGenericWrapper() {
        TestEntity src = fullEntity();

        Base<TestEntity> r1 = XJson.parseObject(
                ("{\"code\":0,\"message\":\"成功\",\"data\":" + XJson.toJson(src) + "}").getBytes(StandardCharsets.UTF_8),
                new XTypeRef<Base<TestEntity>>() {});
        check(r1.code == 0 && "成功".equals(r1.message), "wrapper meta");
        assertEntityEquals(src, r1.data, "wrapper data");

        Base<List<TestEntity>> r2 = XJson.parseObject(
                ("{\"code\":1,\"message\":null,\"data\":[" + XJson.toJson(src) + "]}").getBytes(StandardCharsets.UTF_8),
                new XTypeRef<Base<List<TestEntity>>>() {});
        check(r2.code == 1 && r2.message == null, "wrapper2 meta");
        check(r2.data.size() == 1, "wrapper2 list size");
        assertEntityEquals(src, r2.data.get(0), "wrapper2 data");
        ok("testGenericWrapper");
    }

    /** 通用 parse(byte[])：字段类型形态符合约定（时间=字符串、sql.Date=数字、枚举=字符串、嵌套=Map） */
    static void testGenericParseShape() {
        Object v = XJson.parse(XJson.toJsonBytes(fullEntity()));
        check(v instanceof Map, "generic root is Map");
        Map<?, ?> m = (Map<?, ?>) v;
        check("张三".equals(m.get("name")), "generic name");
        check("2026-11-12 10:20:30.123".equals(m.get("createTime")), "generic createTime string");
        check("2026-11-01".equals(m.get("importTime")), "generic importTime string");
        check(Objects.equals(m.get("exportTime"), 1735689600000L), "generic exportTime long");
        check("MSG_SUCCESS".equals(m.get("status")), "generic status string");
        Object sm = m.get("statusMsg");
        check(sm instanceof Map && "失败原因".equals(((Map<?, ?>) sm).get("msg")), "generic nested msg");
        ok("testGenericParseShape");
    }

    /** JSON 含未知字段时不影响已知字段解析 */
    static void testUnknownFieldTolerant() {
        String s = "{\"unknown1\":[1,2,{\"x\":\"y\"}],\"name\":\"王五\",\"unknown2\":{\"a\":null},"
                + "\"status\":\"MSG_SUCCESS\",\"unknown3\":\"txt\"}";
        TestEntity back = XJson.parseObject(s, TestEntity.class);
        check("王五".equals(back.getName()), "unknown-tolerant name");
        check(back.getStatus() == TestEntity.TestStatus.MSG_SUCCESS, "unknown-tolerant status");
        ok("testUnknownFieldTolerant");
    }

    /** bytes 入口与 String 入口结果一致 */
    static void testBytesAndStringEntryEquivalence() {
        String s = XJson.toJson(fullEntity());
        TestEntity a = XJson.parseObject(s, TestEntity.class);
        TestEntity b = XJson.parseObject(s.getBytes(StandardCharsets.UTF_8), TestEntity.class);
        assertEntityEquals(a, b, "entry equivalence");
        ok("testBytesAndStringEntryEquivalence");
    }

    /** 多格式日期时间：- / . 分隔、T 或空格连接、1~9 位小数秒、Z/±hh:mm 时区后缀、util.Date 字符串/数字双入口 */
    static void testDateMultiFormat() {
        LocalDateTime expect = LocalDateTime.of(2026, 11, 12, 10, 20, 30, 123000000);
        String[] forms = {
                "2026-11-12 10:20:30.123",   // 默认格式
                "2026/11/12 10:20:30.123",   // 斜杠分隔
                "2026.11.12 10:20:30.123",   // 点分隔
                "2026-11-12T10:20:30.123",   // ISO T 连接
                "2026-11-12T10:20:30.123Z",  // Z 时区
                "2026-11-12T10:20:30.123+08:00", // 偏移时区
                "2026-11-12 10:20:30.123+0800",
                "2026/11/12T10:20:30.123",
        };
        for (String f : forms) {
            TestEntity e = XJson.parseObject("{\"createTime\":\"" + f + "\"}", TestEntity.class);
            check(expect.equals(e.getCreateTime()), "ldt fmt: " + f + " -> " + e.getCreateTime());
            check(expect.equals(XJson.parseObject("\"" + f + "\"", LocalDateTime.class)), "top-level ldt: " + f);
        }
        // 无毫秒 / 仅日期 / 纳秒 9 位 / 单位小数
        check(LocalDateTime.of(2026, 11, 12, 10, 20, 30).equals(
                XJson.parseObject("\"2026-11-12 10:20:30\"", LocalDateTime.class)), "ldt no fraction");
        check(LocalDateTime.of(2026, 11, 12, 0, 0).equals(
                XJson.parseObject("\"2026-11-12\"", LocalDateTime.class)), "ldt date-only");
        check(LocalDateTime.of(2026, 11, 12, 10, 20, 30, 123456789).equals(
                XJson.parseObject("\"2026-11-12 10:20:30.123456789\"", LocalDateTime.class)), "ldt nanos");
        check(LocalDateTime.of(2026, 11, 12, 10, 20, 30, 500000000).equals(
                XJson.parseObject("\"2026-11-12 10:20:30.5\"", LocalDateTime.class)), "ldt .5 -> 500ms");

        // LocalDate：纯日期 + 斜杠 + 容忍尾部时间
        LocalDate expectD = LocalDate.of(2026, 11, 1);
        for (String f : new String[]{"2026-11-01", "2026/11/01", "2026-11-01 10:20:30", "2026-11-01T10:20:30.123Z"}) {
            check(expectD.equals(XJson.parseObject("\"" + f + "\"", LocalDate.class)), "ld fmt: " + f);
        }

        // LocalTime：带/不带毫秒
        check(LocalTime.of(10, 20, 30, 123000000).equals(
                XJson.parseObject("\"10:20:30.123\"", LocalTime.class)), "lt fraction");
        check(LocalTime.of(10, 20, 30).equals(
                XJson.parseObject("\"10:20:30\"", LocalTime.class)), "lt plain");

        // java.util.Date：数字毫秒（原有行为）+ 字符串日期时间（按系统时区解释）
        long ms = 1735689600000L;
        TestEntity d1 = XJson.parseObject("{\"exportTime\":" + ms + "}", TestEntity.class);
        check(d1.getExportTime().getTime() == ms, "util.Date numeric millis");
        long expectedMs = LocalDateTime.of(2025, 1, 1, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        TestEntity d2 = XJson.parseObject("{\"exportTime\":\"2025-01-01 00:00:00\"}", TestEntity.class);
        check(d2.getExportTime().getTime() == expectedMs, "util.Date string");
        java.sql.Timestamp ts = XJson.parseObject("\"2025-01-01 00:00:00.123\"", java.sql.Timestamp.class);
        check(ts.getTime() == expectedMs + 123, "sql.Timestamp string with millis");

        // 非法时间应抛异常
        boolean thrown = false;
        try {
            XJson.parseObject("\"2026-13-45 99:99:99\"", LocalDateTime.class);
        } catch (RuntimeException ex) {
            thrown = true;
        }
        check(thrown, "invalid date-time should throw");

        // 多格式解析后 roundtrip：序列化输出仍是标准格式
        TestEntity back = XJson.parseObject(XJson.toJsonBytes(fullEntity()), TestEntity.class);
        check(expect.equals(XJson.parseObject(
                "\"" + XJson.toJson(back.getCreateTime()).replace("\"", "") + "\"", LocalDateTime.class)),
                "serialize keeps standard format");
        ok("testDateMultiFormat");
    }

    // ---------------- 工具 ----------------

    static TestEntity fullEntity() {
        TestEntity e = new TestEntity();
        e.setName("张三");
        e.setCreateTime(LocalDateTime.of(2026, 11, 12, 10, 20, 30, 123000000)); // yyyy-MM-dd HH:mm:ss.SSS 毫秒精度
        e.setImportTime(LocalDate.of(2026, 11, 1));
        e.setExportTime(new Date(1735689600000L)); // 2025-01-01
        e.setStatus(TestEntity.TestStatus.MSG_SUCCESS);
        e.setStatusMsg(new TestEntity.TestStatusMsg(TestEntity.TestStatus.MSG_FAILED, "失败原因"));
        return e;
    }

    static void assertEntityEquals(TestEntity exp, TestEntity act, String what) {
        check(act != null, what + ": null result");
        check(Objects.equals(exp.getName(), act.getName()), what + ".name: exp=" + exp.getName() + " act=" + act.getName());
        check(Objects.equals(exp.getCreateTime(), act.getCreateTime()), what + ".createTime: exp=" + exp.getCreateTime() + " act=" + act.getCreateTime());
        check(Objects.equals(exp.getImportTime(), act.getImportTime()), what + ".importTime: exp=" + exp.getImportTime() + " act=" + act.getImportTime());
        check(exp.getExportTime() == null ? act.getExportTime() == null
                : exp.getExportTime().getTime() == act.getExportTime().getTime(), what + ".exportTime");
        check(exp.getStatus() == act.getStatus(), what + ".status: exp=" + exp.getStatus() + " act=" + act.getStatus());
        if (exp.getStatusMsg() == null) {
            check(act.getStatusMsg() == null, what + ".statusMsg should be null");
        } else {
            check(act.getStatusMsg() != null, what + ".statusMsg null");
            check(exp.getStatusMsg().getStatus() == act.getStatusMsg().getStatus(), what + ".statusMsg.status");
            check(Objects.equals(exp.getStatusMsg().getMsg(), act.getStatusMsg().getMsg()), what + ".statusMsg.msg");
        }
    }

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError("FAILED: " + what);
    }

    static void ok(String name) {
        passed++;
        System.out.println("[PASS] " + name);
    }
}
