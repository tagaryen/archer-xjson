package com.archer.xjson.test;

import com.archer.xjson.XJSON;
import com.archer.xjson.XJSONStatic;
import com.archer.xjson.asm.XNJSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.*;

public class JsonTest {

    static XJSON xjson = new XJSON();

    static XNJSON xnjson = new XNJSON();

    // large.json 部分元素带有 LogEntry 未声明的扩展字段（如 query），忽略未知字段以与 XJSON/XNJSON 行为对齐
    static ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 防止 JIT 把结果判为未使用而优化的接收点 */
    static volatile Object sink;

    /** 序列化基准用的实体源数据，首次访问时载入一次（不计时） */
    static List<LogEntry> logSource;


    static String readJson() throws IOException {
        return new String(Files.readAllBytes(Paths.get("E:/projects/large.json")), StandardCharsets.UTF_8);
    }

    static byte[] readJsonBytes() throws IOException {
        return Files.readAllBytes(Paths.get("E:/projects/large.json"));
    }


    static void XJsonTest() throws IOException {
        xjson.parseList(readJson());
    }

    static void XNJsonTest() throws IOException {
        xnjson.parseList(readJson());
    }

    /** XJSON byte[] 直扫通用解析（纯 ASCII 零 String 构造） */
    static void XJsonBytesTest() throws IOException {
        xjson.parseList(readJsonBytes());
    }

    /** XNJSON byte[] 直扫通用解析（双模 byte 路径 + valuePool） */
    static void XNJsonBytesTest() throws IOException {
        xnjson.parseList(readJsonBytes());
    }

    static void OMTest() throws IOException {
        om.readValue(readJson(), new TypeReference<List<Map<String, Object>>>() {});
    }

    static List<LogEntry> loadSource() throws Exception {
        if (logSource == null) {
            logSource = XJSONStatic.parseList(readJson(), LogEntry.class);
        }
        return logSource;
    }

    static void XNEntityParseTest() throws Exception {
        sink = xnjson.parseList(readJson(), LogEntry.class);
    }

    static void XEntityParseTest() throws Exception {
        sink = xjson.parseList(readJson(), LogEntry.class);
    }

    static void OMEntityParseTest() throws Exception {
        sink = om.readValue(readJson(), new TypeReference<List<LogEntry>>() {});
    }

    /** XJSON byte[] 直扫实体解析 */
    static void XEntityBytesParseTest() throws Exception {
        sink = xjson.parseList(readJsonBytes(), LogEntry.class);
    }

    /** byte[] 零拷贝串行实体解析 */
    static void XNBytesEntityParseTest() throws Exception {
        sink = xnjson.parseList(readJsonBytes(), LogEntry.class);
    }

    /** SWAR 行边界索引 + 并行分块实体解析 */
    static void XNParallelEntityParseTest() throws Exception {
        sink = xnjson.parseListParallel(readJsonBytes(), LogEntry.class);
    }

    static void XNStringifyTest() throws Exception {
        sink = xnjson.stringify(loadSource());
    }

    static void XStringifyTest() throws Exception {
        sink = xjson.stringify(loadSource());
    }

    static void OMStringifyTest() throws Exception {
        sink = om.writeValueAsString(loadSource());
    }

    /**
     * 性能与内存基准：预热 warmup 轮后，执行 rounds 轮
     * 每轮记录耗时与堆内存增量，输出单轮明细与汇总统计
     */
    static void benchmark(String name, ThrowingTask task, int warmup, int rounds) throws Exception {
        // 预热：消除 JIT 编译 / 类加载带来的干扰
        for (int i = 0; i < warmup; i++) {
            task.run();
        }
        System.gc();
        Thread.sleep(200);

        long totalTime = 0;
        long bestTime = Long.MAX_VALUE;
        long worstTime = 0;
        long totalMem = 0;

        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < rounds; i++) {
            System.gc();
            Thread.sleep(50);
            long memBefore = rt.totalMemory() - rt.freeMemory();
            long t0 = System.nanoTime();

            task.run();

            long cost = System.nanoTime() - t0;
            long memAfter = rt.totalMemory() - rt.freeMemory();
            long memUsed = Math.max(memAfter - memBefore, 0);

            totalTime += cost;
            totalMem += memUsed;
            bestTime = Math.min(bestTime, cost);
            worstTime = Math.max(worstTime, cost);

            System.out.printf("[%s] round %d: time=%.2f ms, mem=%.2f MB%n",
                    name, i + 1, cost / 1e6, memUsed / 1048576.0);
        }

        System.out.printf("== %s 汇总(rounds=%d): avg=%.2f ms, best=%.2f ms, worst=%.2f ms, avgMem=%.2f MB%n%n",
                name, rounds, totalTime / rounds / 1e6, bestTime / 1e6, worstTime / 1e6,
                totalMem / rounds / 1048576.0);
    }

    @FunctionalInterface
    interface ThrowingTask {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        try {
            int warmup = 3;
            int rounds = 10;

            benchmark("OMTest", JsonTest::OMTest, warmup, rounds);
            benchmark("XJsonTest", JsonTest::XJsonTest, warmup, rounds);
            benchmark("XJsonBytesTest", JsonTest::XJsonBytesTest, warmup, rounds);
            benchmark("XNJsonTest", JsonTest::XNJsonTest, warmup, rounds);
            benchmark("XNJsonBytesTest", JsonTest::XNJsonBytesTest, warmup, rounds);

            benchmark("OMEntityParseTest", JsonTest::OMEntityParseTest, warmup, rounds);
            benchmark("XEntityParseTest", JsonTest::XEntityParseTest, warmup, rounds);
            benchmark("XEntityBytesParseTest", JsonTest::XEntityBytesParseTest, warmup, rounds);
            benchmark("XNEntityParseTest", JsonTest::XNEntityParseTest, warmup, rounds);
            benchmark("XNBytesEntityParseTest", JsonTest::XNBytesEntityParseTest, warmup, rounds);
            benchmark("XNParallelEntityParseTest", JsonTest::XNParallelEntityParseTest, warmup, rounds);

            benchmark("XNStringifyTest", JsonTest::XNStringifyTest, warmup, rounds);
            benchmark("XStringifyTest", JsonTest::XStringifyTest, warmup, rounds);
            benchmark("OMStringifyTest", JsonTest::OMStringifyTest, warmup, rounds);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
