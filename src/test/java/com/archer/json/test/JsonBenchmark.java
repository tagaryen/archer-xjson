package com.archer.json.test;

import com.archer.xjson.light.XJson;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * archer-json vs Jackson 性能 / 内存对比基准。
 * 指标：耗时（min/avg）、每轮分配量（ThreadMXBean 精确统计）、结果驻留堆内存（GC 后差值）。
 * 建议运行参数：java -Xmx4g
 */
public class JsonBenchmark {

    // ---------------- POJO 模型 ----------------

    /** hd3D.json：扁平坐标点（Crossline/Survey_Name/Inline 全为 null，已核实） */
    public static class Point {
        public String Crossline;
        public double X;
        public double Y;
        public String Survey_Name;
        public double Z;
        public String Inline;
        public String Strata_Name;
    }

    /** large.json：日志条目 */
    public static class LogEntry {
        public String _id;
        public long timestamp;
        public String level;
        public String message;
        public String service;
        public String host;
        public Integer pid;
        public Map<String, Object> meta;
        public String req_id;
        public String method;
        public String path;
        public Integer status_code;
        public Integer duration_ms;
        public Integer user_id;
        public Err error;
    }

    public static class Err {
        public String name;
        public String message;
    }

    // ---------------- 基准框架 ----------------

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final com.sun.management.ThreadMXBean MX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static final int WARMUP = 2;
    private static final int ITER = 5;

    private interface Task { Object run() throws Exception; }

    private static class Stat {
        long minMs = Long.MAX_VALUE;
        long totalMs;
        long totalAlloc;
        void add(long ms, long alloc) {
            minMs = Math.min(minMs, ms);
            totalMs += ms;
            totalAlloc += alloc;
        }
        long avgMs() { return totalMs / ITER; }
        double avgAllocMB() { return totalAlloc / (double) ITER / 1048576.0; }
    }

    public static void main(String[] args) throws Exception {
        if (!MX.isThreadAllocatedMemorySupported()) {
            System.out.println("ThreadAllocatedMemory 不支持，分配量指标不可用");
        }
        MX.setThreadAllocatedMemoryEnabled(true);
        System.out.printf("heap max = %.0f MB%n%n", Runtime.getRuntime().maxMemory() / 1048576.0);
        bench("hd3D.json", Point[].class);
        bench("large.json", LogEntry[].class);
    }

    private static void bench(String file, Class<?> pojoArrayType) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(file));
        System.out.printf("======== %s (%.2f MB) ========%n", file, data.length / 1048576.0);

        // 先各解析一次，得到序列化用的对象（通用结构 & POJO）
        Object archerPojo = XJson.parseObject(data, pojoArrayType);
        Object jackPojo = MAPPER.readValue(data, pojoArrayType);
        Object archerGen = XJson.parse(data);
        Object jackGen = MAPPER.readValue(data, Object.class);

        printHeader("反序列化");
        row("archer  -> POJO", run(() -> XJson.parseObject(data, pojoArrayType)),
                "jackson -> POJO", run(() -> MAPPER.readValue(data, pojoArrayType)));
        row("archer  -> Map/List", run(() -> XJson.parse(data)),
                "jackson -> Map/List", run(() -> MAPPER.readValue(data, Object.class)));

        printHeader("序列化");
        row("archer  POJO->bytes", run(() -> XJson.toJsonBytes(archerPojo)),
                "jackson POJO->bytes", run(() -> MAPPER.writeValueAsBytes(jackPojo)));
        row("archer  Map ->bytes", run(() -> XJson.toJsonBytes(archerGen)),
                "jackson Map ->bytes", run(() -> MAPPER.writeValueAsBytes(jackGen)));

        printHeader("驻留堆内存（结果对象图，GC 后差值）");
        System.out.printf("  POJO    : archer %7.1f MB | jackson %7.1f MB%n",
                retainedMB(() -> XJson.parseObject(data, pojoArrayType)),
                retainedMB(() -> MAPPER.readValue(data, pojoArrayType)));
        System.out.printf("  Map/List: archer %7.1f MB | jackson %7.1f MB%n%n",
                retainedMB(() -> XJson.parse(data)),
                retainedMB(() -> MAPPER.readValue(data, Object.class)));
    }

    private static void printHeader(String title) {
        System.out.println("-- " + title);
        System.out.printf("  %-22s %10s %10s | %14s %14s%n",
                "", "min(ms)", "avg(ms)", "alloc/轮(MB)", "");
    }

    private static void row(String nameA, Stat a, String nameB, Stat b) {
        System.out.printf("  %-22s %10d %10d | %14.1f%n", nameA, a.minMs, a.avgMs(), a.avgAllocMB());
        System.out.printf("  %-22s %10d %10d | %14.1f%n", nameB, b.minMs, b.avgMs(), b.avgAllocMB());
        System.out.printf("  %-22s %9.2fx %9.2fx | %13.2fx%n", "=> archer/jackson",
                (double) b.minMs / a.minMs, (double) b.avgMs() / a.avgMs(),
                b.avgAllocMB() / Math.max(a.avgAllocMB(), 0.0001));
    }

    private static Stat run(Task t) throws Exception {
        for (int i = 0; i < WARMUP; i++) t.run();
        Stat s = new Stat();
        long tid = Thread.currentThread().getId();
        for (int i = 0; i < ITER; i++) {
            long a0 = MX.getThreadAllocatedBytes(tid);
            long t0 = System.nanoTime();
            t.run();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            s.add(ms, MX.getThreadAllocatedBytes(tid) - a0);
        }
        return s;
    }

    /** 多次 GC 后测量结果对象图的驻留堆占用，取 2 次测量的较小值降低噪音 */
    private static double retainedMB(Task t) throws Exception {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            gcSettle();
            long base = usedBytes();
            Object hold = t.run();
            gcSettle();
            long retained = usedBytes() - base;
            best = Math.min(best, retained / 1048576.0);
            hold = null;
            gcSettle();
        }
        return best;
    }

    private static void gcSettle() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(30);
        }
    }

    private static long usedBytes() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }
}
