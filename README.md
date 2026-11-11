# archer-xjson

高性能 JSON 库，纯 Java 实现，零第三方依赖。内置双引擎：

- **XJSON**：反射驱动，功能全集（泛型 `JavaTypeRef` / `Type`、自定义编解码器、严格模式、美化输出、`InputStream` 输入），String / byte[] 双模解析；
- **XNJSON**（`com.archer.xjson.asm`）：ASM 字节码生成强类型编解码器，面向极致性能——byte[] 零拷贝直扫、SWAR 8 字节并行扫描、字符串零分配去重池、多线程并行解析。

maven:
```xml
<dependency>
  <groupId>io.github.tagaryen</groupId>
  <artifactId>archer-xjson</artifactId>
  <version>1.2.0</version>
</dependency>
```

## 快速上手

静态门面 `XJSONStatic` 适合最简单的场景：

```java
// 实体 → JSON
String json = XJSONStatic.stringify(user);

// JSON → 实体
User user = XJSONStatic.parse(json, User.class);

// JSON → 实体列表
List<User> users = XJSONStatic.parseList(json, User.class);

// 无类型通用解析：Map / List
Map<String, Object> map = XJSONStatic.parse(json);
List<Object> list = XJSONStatic.parseList(json);
```

## XJSON 使用实例

实例式 `XJSON` 支持配置与自定义编解码器。所有解析方法均提供 `String` / `byte[]` / `InputStream` 三种输入形态。

### 基本解析与序列化

```java
XJSON xjson = new XJSON();

// String 或 byte[] 输入，行为一致；byte[] 为纯 ASCII 时零 char[] 副本直扫
byte[] bytes = Files.readAllBytes(Paths.get("data.json"));
User user = xjson.parse(bytes, User.class);
List<User> users = xjson.parseList(bytes, User.class);

// 流式输入
try (InputStream in = new FileInputStream("data.json")) {
    List<User> list = xjson.parseList(in, User.class);
}

// 序列化
String json = xjson.stringify(user);
```

### 泛型集合（JavaTypeRef）

```java
Map<String, User> map = xjson.parse(json, new JavaTypeRef<Map<String, User>>() {});
List<Queue<User>> complex = xjson.parseList(json, new JavaTypeRef<List<Queue<User>>>() {});
```

### 配置项

```java
XJSON xjson = new XJSON();
xjson.useBeautifyMode(true);      // 序列化输出缩进美化
xjson.useStrictJsonMode(true);    // 严格 JSON：重复 key 报错
xjson.useStrictClassMode(true);   // 严格类型：JSON 中出现类没有的字段时报错
```

### 自定义编解码器

```java
// 自定义序列化：LocalDateTime → 带引号的 ISO 文本
xjson.setSerializer(LocalDateTime.class, obj ->
        "\"" + ((LocalDateTime) obj).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"");

// 自定义反序列化：入参为 JSON 字符串 token（不含引号）
xjson.setDeserializer(LocalDateTime.class, text ->
        LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
```

## XNJSON 使用实例（高性能引擎）

`XNJSON` 面向大数据量场景，API 与 XJSON 对齐，解析目标支持 `Class` 与无类型 Map/List。

```java
import com.archer.xjson.asm.XNJSON;

XNJSON xn = new XNJSON();

// 序列化：String / byte[] 双出口
String json = xn.stringify(users);
byte[] out = xn.stringifyBytes(users);

// 通用解析（String / byte[] 双模）
Map<String, Object> map = xn.parse(bytes);
List<Object> list = xn.parseList(bytes);

// 实体解析：ASM 生成强类型反序列化器，字段直写零反射
List<HD3D> entities = xn.parseList(bytes, HD3D.class);

// 并行解析：SWAR 行边界索引 + 多线程分块，大数据量首选
List<HD3D> parallel = xn.parseListParallel(bytes, HD3D.class);
```

## 性能基准

测试数据：44.6MB JSON（11 万个 实体，7 字段含中文转义），10 轮平均，JVM `-Xmx4g -XX:+UseG1GC`，对照 Jackson 2.13.5。

### 通用 Map 解析（parseList，无类型）

| 实现 | avg 时间 | avg 内存 |
|---|---|---|
| Jackson (String) | 571.94 ms | 284.80 MB |
| XJSON (String) | 221.48 ms | 180.11 MB |
| XJSON (byte[]) | 237.80 ms | 142.95 MB |
| XNJSON (String) | ~280 ms | 178.92 MB |
| XNJSON (byte[]) | 267.96 ms | 144.59 MB |

### 实体解析（parseList(..., HD3D.class)）

| 实现 | avg 时间 | avg 内存 |
|---|---|---|
| Jackson (String) | 468.03 ms | 195.75 MB |
| XJSON (String) | 265.80 ms | 175.88 MB |
| XJSON (byte[]) | 229.88 ms | 86.05 MB |
| XNJSON (String) | ~265 ms | 154.76 MB |
| XNJSON (byte[]) | 204.19 ms | 65.61 MB |
| **XNJSON (byte[] 并行)** | **129.29 ms** | **79.71 MB** |

### 实体序列化（stringify）

| 实现 | avg 时间 | avg 内存 |
|---|---|---|
| Jackson | 227.23 ms | 332.58 MB |
| XJSON | 166.02 ms | 81.79 MB |
| **XNJSON** | **145.12 ms** | **81.79 MB** |

## 选型建议

| 场景 | 推荐 |
|---|---|
| 大 JSON 实体列表，追求极限速度 | `XNJSON.parseListParallel(byte[], Class)` |
| 大 JSON 实体列表，单线程 | `XNJSON.parseList(byte[], Class)` |
| 需要泛型（`Map<String, T>` 等）/ 自定义编解码 / 严格模式 | `XJSON` |
| 无类型通用解析（Map/List 树） | 两者均可，byte[] 输入内存更低 |
| 简单调用、免实例化 | `XJSONStatic` |

内存敏感场景优先选 **byte[] 输入 + 实体类型解析**：通用 Map 路径的结果集（Map 结构 + 数值装箱）本身约是实体路径内存的 2 倍。

## 设计要点

- **byte[] 双模直扫**：纯 ASCII 输入直接在字节数组上解析，消除整篇 UTF-8 解码产生的双倍 char[] 副本；含非 ASCII 自动回退标准解码，行为完全一致；
- **字符串零分配去重池**：开放寻址区间池，对象 key 与重复字符串值直接复用已有实例，不产生中间 String 垃圾；
- **ASM 强类型编解码器**（XNJSON）：按类生成专用字节码，字段直读直写，消除反射调用与数值装箱分派；
- **SWAR 并行扫描**（XNJSON）：8 字节批量探测引号/转义/行边界，加速字符串定位与并行分块；
- **序列化零分配链路**：ThreadLocal 缓冲复用、字段类型特化直写、double 整数 fast-path、转义段批量拷贝，内存占用收敛到输出串本身大小；
- **XMap**：开放寻址哈希 Map（顺序数组 + 槽位索引），替代 HashMap 消除 Node 对象分配。
