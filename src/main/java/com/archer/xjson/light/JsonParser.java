package com.archer.xjson.light;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@SuppressWarnings("restriction") 
final class JsonParser {

    /** JSR-310 默认文本格式：yyyy-MM-dd HH:mm:ss.SSS（毫秒精度） */
    static final java.time.format.DateTimeFormatter LDT_FMT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    static final java.time.format.DateTimeFormatter LD_FMT  = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final java.time.format.DateTimeFormatter LT_FMT  = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private byte[] buf;
    private int pos;
    private int end;

    // 当前 key 的原始字节区间（POJO 字段名比对用，避免分配 String）
    private int keyStart;
    private int keyLen;
    private int keyHash;
    private boolean keyRaw;
    private String keyDecoded;

    // ---- Map 路径专用：键符号表（开放寻址），命中时零分配直接返回驻留键 ----
    private byte[][] symBytes = new byte[256][];
    private String[] syms = new String[256];
    private int symCount;

    void init(byte[] b, int off, int len) {
        this.buf = b;
        this.pos = off;
        this.end = off + len;
    }

    void release() {
        this.buf = null;
        this.keyDecoded = null;
        if (scratch != null && scratch.length > (1 << 16)) scratch = new char[256];
    }

    Object readValue() {
        skipWs();
        byte c = buf[pos];
        switch (c) {
            case '{': return readMap(null);
            case '[': return readList(null);
            case '"': return readString();
            case 't': pos += 4; return Boolean.TRUE;
            case 'f': pos += 5; return Boolean.FALSE;
            case 'n': pos += 4; return null;
            default:  return readNumber();
        }
    }

    /** 按目标类型读取一个值（POJO 字段绑定用），类型变量已在 TypeNode 中绑定 */
    Object readTyped(TypeNode n) {
        skipWs();
        byte c = buf[pos];
        if (c == 'n') { pos += 4; return null; }
        Class<?> type = n.raw;
        if (type == Object.class) return readValue();
        if (type == String.class) return readString();
        if (type == int.class || type == Integer.class) return (int) readLong();
        if (type == long.class || type == Long.class) return readLong();
        if (type == double.class || type == Double.class) return readDouble();
        if (type == float.class || type == Float.class) return (float) readDouble();
        if (type == short.class || type == Short.class) return (short) readLong();
        if (type == byte.class || type == Byte.class) return (byte) readLong();
        if (type == boolean.class || type == Boolean.class) return readBool();
        if (type == char.class || type == Character.class) {
            String s = readString();
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (Map.class.isAssignableFrom(type)) {
            return readMap(n.args != null && n.args.length == 2 ? n.args[1] : null);
        }
        if (List.class.isAssignableFrom(type)) {
            return readList(n.args != null && n.args.length == 1 ? n.args[0] : null);
        }
        if (type == java.time.LocalDateTime.class) return DateTimes.parseLdt(readString());
        if (type == java.time.LocalDate.class) return DateTimes.parseLd(readString());
        if (type == java.time.LocalTime.class) return DateTimes.parseLt(readString());
        if (java.util.Date.class.isAssignableFrom(type)) {
            skipWs();
            long ms = buf[pos] == '"' ? DateTimes.epochMillis(readString()) : readLong();
            if (type == java.sql.Date.class) return new java.sql.Date(ms);
            if (type == java.sql.Time.class) return new java.sql.Time(ms);
            if (type == java.sql.Timestamp.class) return new java.sql.Timestamp(ms);
            return new java.util.Date(ms);
        }
        if (type.isEnum()) return enumOf(type, readString());
        if (type.isArray()) {
            TypeNode elem = TypeNode.of(type.getComponentType());
            List<Object> list = readList(elem);
            Object arr = java.lang.reflect.Array.newInstance(type.getComponentType(), list.size());
            for (int i = 0; i < list.size(); i++) {
                java.lang.reflect.Array.set(arr, i, list.get(i));
            }
            return arr;
        }
        return readPojo(BeanInfo.of(n));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumOf(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private Map<String, Object> readMap(TypeNode valType) {
        Map<String, Object> m = new LinkedHashMap<>();
        pos++; // '{'
        skipWs();
        if (buf[pos] == '}') { pos++; return m; }
        while (true) {
            readKey();
            String k;
            if (keyRaw) {
                k = internKey();
            } else {
                String c = keyPool.get(keyDecoded);
                if (c == null) { keyPool.put(keyDecoded, keyDecoded); k = keyDecoded; }
                else k = c;
            }
            skipWs();
            pos++; // ':'
            m.put(k, valType == null ? readValue() : readTyped(valType));
            skipWs();
            if (buf[pos++] == '}') return m;
        }
    }

    private List<Object> readList(TypeNode elemType) {
        List<Object> l = new ArrayList<>();
        pos++; // '['
        skipWs();
        if (buf[pos] == ']') { pos++; return l; }
        while (true) {
            l.add(elemType == null ? readValue() : readTyped(elemType));
            skipWs();
            if (buf[pos++] == ']') return l;
        }
    }

    private Object readPojo(BeanInfo bi) {
        Object inst = bi.newInstance();
        pos++; // '{'
        skipWs();
        if (buf[pos] == '}') { pos++; return inst; }
        while (true) {
            readKey();
            skipWs();
            pos++; // ':'
            BeanInfo.FieldInfo f = keyRaw ? bi.find(buf, keyStart, keyLen, keyHash) : bi.find(keyDecoded);
            if (f == null) {
                skipValue();
            } else {
                readField(inst, f);
            }
            skipWs();
            if (buf[pos++] == '}') return inst;
        }
    }

    private void readField(Object inst, BeanInfo.FieldInfo f) {
        long off = f.offset;
        switch (f.type) {
            case BeanInfo.T_INT:     BeanInfo.UNSAFE.putInt(inst, off, (int) readLong()); break;
            case BeanInfo.T_LONG:    BeanInfo.UNSAFE.putLong(inst, off, readLong()); break;
            case BeanInfo.T_DOUBLE:  BeanInfo.UNSAFE.putDouble(inst, off, readDouble()); break;
            case BeanInfo.T_FLOAT:   BeanInfo.UNSAFE.putFloat(inst, off, (float) readDouble()); break;
            case BeanInfo.T_SHORT:   BeanInfo.UNSAFE.putShort(inst, off, (short) readLong()); break;
            case BeanInfo.T_BYTE:    BeanInfo.UNSAFE.putByte(inst, off, (byte) readLong()); break;
            case BeanInfo.T_BOOL:    BeanInfo.UNSAFE.putBoolean(inst, off, readBool()); break;
            case BeanInfo.T_CHAR: {
                String s = readNullableString();
                BeanInfo.UNSAFE.putChar(inst, off, (s == null || s.isEmpty()) ? '\0' : s.charAt(0));
                break;
            }
            case BeanInfo.T_STRING:  BeanInfo.UNSAFE.putObject(inst, off, readNullableString()); break;
            default:                 BeanInfo.UNSAFE.putObject(inst, off, readTyped(f.node));
        }
    }

    private void readKey() {
        skipWs();
        if (buf[pos] != '"') throw err("expected key string");
        int start = ++pos;
        int h = 0;
        while (pos < end) {
            byte b = buf[pos];
            if (b == '"') {
                keyStart = start;
                keyLen = pos - start;
                keyHash = h;
                keyRaw = true;
                pos++;
                return;
            }
            if (b == '\\' || (b & 0x80) != 0) {
                // 含转义或非 ASCII，回退到完整解码
                pos = start - 1;
                keyDecoded = readString();
                keyRaw = false;
                return;
            }
            h = h * 31 + b;
            pos++;
        }
        throw err("unterminated key");
    }

    /** Map 路径键驻留：符号表命中零分配；未命中解码一次并插入 */
    private String internKey() {
        int s = keyStart, len = keyLen;
        int mask = symBytes.length - 1;
        int i = keyHash & mask;
        while (true) {
            byte[] kb = symBytes[i];
            if (kb == null) {
                String str = asciiString(buf, s, len);
                byte[] copy = new byte[len];
                System.arraycopy(buf, s, copy, 0, len);
                symBytes[i] = copy;
                syms[i] = str;
                if (++symCount > (mask >> 1)) growSyms();
                return str;
            }
            if (kb.length == len) {
                boolean eq = true;
                for (int j = 0; j < len; j++) {
                    if (kb[j] != buf[s + j]) { eq = false; break; }
                }
                if (eq) return syms[i];
            }
            i = (i + 1) & mask;
        }
    }

    private void growSyms() {
        int nCap = symBytes.length << 1;
        int nMask = nCap - 1;
        byte[][] nb = new byte[nCap][];
        String[] ns = new String[nCap];
        for (int j = 0; j < symBytes.length; j++) {
            byte[] kb = symBytes[j];
            if (kb == null) continue;
            int i = BeanInfo.hash(kb, 0, kb.length) & nMask;
            while (nb[i] != null) i = (i + 1) & nMask;
            nb[i] = kb;
            ns[i] = syms[j];
        }
        symBytes = nb;
        syms = ns;
    }

    private String readNullableString() {
        skipWs();
        if (buf[pos] == 'n') { pos += 4; return null; }
        return readString();
    }

    String readString() {
        skipWs();
        int start = ++pos; // 跳过 '"'
        while (pos < end) {
            byte b = buf[pos];
            if (b == '"') {
                String s = asciiString(buf, start, pos - start);
                pos++;
                return s;
            }
            if (b == '\\' || (b & 0x80) != 0) {
                return readStringSlow(start);
            }
            pos++;
        }
        throw err("unterminated string");
    }

    /**
     * 纯 ASCII 快路径建串：使用 hibyte=0 的古老构造器，内部只是单遍 widen 循环，
     * 绕开 StringCoding 每字符串一次 CharsetDecoder 分配（实测占字符串解码 ~43% CPU）
     */
    @SuppressWarnings("deprecation")
    private static String asciiString(byte[] b, int s, int len) {
        return new String(b, 0, s, len);
    }

    // 慢速路径复用的解码缓冲区（随实例池化，避免每次分配）
    private char[] scratch = new char[256];

    // Map key 去重池：避免每个 Map 都为相同 key 保留一份 String，显著降低驻留内存
    private final java.util.HashMap<String, String> keyPool = new java.util.HashMap<>();

    /** 慢速路径：处理转义与 UTF-8 多字节 */
    private String readStringSlow(int start) {
        // 先扫描到结束引号，得到字符串字节长度上界
        int i = start;
        while (true) {
            byte b = buf[i];
            if (b == '"') break;
            i += (b == '\\') ? 2 : 1;
        }
        int byteLen = i - start;
        char[] tmp = scratch;
        if (tmp.length < byteLen) {
            int c = tmp.length;
            while (c < byteLen) c <<= 1;
            scratch = tmp = new char[c];
        }
        int n = 0;
        i = start;
        while (true) {
            int b = buf[i] & 0xFF;
            if (b == '"') {
                pos = i + 1;
                return new String(tmp, 0, n);
            }
            if (b == '\\') {
                int e = buf[++i] & 0xFF;
                switch (e) {
                    case 'n': tmp[n++] = '\n'; i++; break;
                    case 't': tmp[n++] = '\t'; i++; break;
                    case 'r': tmp[n++] = '\r'; i++; break;
                    case 'b': tmp[n++] = '\b'; i++; break;
                    case 'f': tmp[n++] = '\f'; i++; break;
                    case '/': tmp[n++] = '/';  i++; break;
                    case 'u':
                        tmp[n++] = (char) Integer.parseInt(new String(buf, i + 1, 4, StandardCharsets.ISO_8859_1), 16);
                        i += 5;
                        break;
                    default:    tmp[n++] = (char) e; i++; break; // \" \\ 等
                }
                continue;
            }
            if (b < 0x80) {
                tmp[n++] = (char) b;
                i++;
            } else if ((b >> 5) == 0x6) {
                tmp[n++] = (char) (((b & 0x1F) << 6) | (buf[i + 1] & 0x3F));
                i += 2;
            } else if ((b >> 4) == 0xE) {
                tmp[n++] = (char) (((b & 0x0F) << 12) | ((buf[i + 1] & 0x3F) << 6) | (buf[i + 2] & 0x3F));
                i += 3;
            } else {
                int cp = ((b & 0x07) << 18) | ((buf[i + 1] & 0x3F) << 12)
                        | ((buf[i + 2] & 0x3F) << 6) | (buf[i + 3] & 0x3F);
                cp -= 0x10000;
                tmp[n++] = (char) (0xD800 + (cp >> 10));
                tmp[n++] = (char) (0xDC00 + (cp & 0x3FF));
                i += 4;
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        if (buf[pos] == '-') pos++;
        boolean isDouble = false;
        while (pos < end) {
            byte b = buf[pos];
            if (b >= '0' && b <= '9') {
                pos++;
            } else if (b == '.' || b == 'e' || b == 'E' || b == '+'
                    || (b == '-' && (buf[pos - 1] == 'e' || buf[pos - 1] == 'E'))) {
                isDouble = true;
                pos++;
            } else {
                break;
            }
        }
        if (!isDouble) {
            boolean neg = buf[start] == '-';
            long v = 0;
            for (int i = neg ? start + 1 : start; i < pos; i++) v = v * 10 + (buf[i] - '0');
            if (neg) v = -v;
            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                return Integer.valueOf((int) v);
            }
            return Long.valueOf(v);
        }
        return fastDouble(start, pos);
    }

    long readLong() {
        skipWs();
        boolean neg = false;
        if (buf[pos] == '-') { neg = true; pos++; }
        long v = 0;
        while (pos < end) {
            byte b = buf[pos];
            if (b < '0' || b > '9') break;
            v = v * 10 + (b - '0');
            pos++;
        }
        return neg ? -v : v;
    }

    double readDouble() {
        skipWs();
        int start = pos;
        while (pos < end) {
            byte b = buf[pos];
            if ((b >= '0' && b <= '9') || b == '-' || b == '+' || b == '.' || b == 'e' || b == 'E') {
                pos++;
            } else {
                break;
            }
        }
        return fastDouble(start, pos);
    }

    private static final double[] POW10 = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };

    /**
     * 快速 double 解析：有效位 mantissa < 2^53 且 |十进制指数| <= 22 时，
     * 走单次 IEEE 乘/除（操作数均可精确表示，单次舍入 = 正确舍入，与 Double.parseDouble 位级一致）；
     * 否则回退 JDK 解析。
     */
    private double fastDouble(int s, int e) {
        int i = s;
        boolean neg = false;
        if (buf[i] == '-') { neg = true; i++; }
        long m = 0;
        int digits = 0, frac = 0, exp = 0;
        boolean seenNz = false;
        while (i < e) { // 整数部分
            byte b = buf[i];
            if (b < '0' || b > '9') break;
            if (seenNz || b != '0') {
                seenNz = true;
                if (++digits > 17) return slowDouble(s, e);
                m = m * 10 + (b - '0');
            }
            i++;
        }
        if (i < e && buf[i] == '.') { // 小数部分
            i++;
            while (i < e) {
                byte b = buf[i];
                if (b < '0' || b > '9') break;
                frac++;
                if (seenNz || b != '0') {
                    seenNz = true;
                    if (++digits > 17) return slowDouble(s, e);
                    m = m * 10 + (b - '0');
                }
                i++;
            }
        }
        if (i < e && (buf[i] == 'e' || buf[i] == 'E')) { // 指数部分
            i++;
            boolean eneg = false;
            if (buf[i] == '-') { eneg = true; i++; }
            else if (buf[i] == '+') { i++; }
            while (i < e) {
                byte b = buf[i];
                if (b < '0' || b > '9') break;
                exp = exp * 10 + (b - '0');
                if (exp > 300) return slowDouble(s, e);
                i++;
            }
            if (eneg) exp = -exp;
        }
        int e10 = exp - frac;
        while (m != 0 && m % 10 == 0) { m /= 10; e10++; } // 去尾零提升快路径命中
        if (m < (1L << 53) && e10 >= -22 && e10 <= 22) {
            double v = (e10 == 0) ? (double) m : (e10 > 0) ? m * POW10[e10] : m / POW10[-e10];
            return neg ? -v : v;
        }
        return slowDouble(s, e);
    }

    private double slowDouble(int s, int e) {
        return Double.parseDouble(new String(buf, s, e - s, StandardCharsets.ISO_8859_1));
    }

    boolean readBool() {
        skipWs();
        if (buf[pos] == 't') { pos += 4; return true; }
        pos += 5; // false
        return false;
    }

    /** 跳过任意一棵子树，仅移动游标，零分配 */
    void skipValue() {
        skipWs();
        byte c = buf[pos];
        if (c == '"') { skipString(); return; }
        if (c == '{' || c == '[') {
            int depth = 0;
            while (pos < end) {
                byte b = buf[pos++];
                if (b == '"') { pos--; skipString(); }
                else if (b == '{' || b == '[') depth++;
                else if (b == '}' || b == ']') { if (--depth == 0) return; }
            }
            throw err("unexpected end");
        }
        while (pos < end) {
            byte b = buf[pos];
            if (b == ',' || b == '}' || b == ']' || b == ' ' || b == '\t' || b == '\n' || b == '\r') return;
            pos++;
        }
    }

    private void skipString() {
        pos++; // 跳过 '"'
        while (pos < end) {
            byte b = buf[pos++];
            if (b == '"') return;
            if (b == '\\') pos++;
        }
        throw err("unterminated string");
    }

    private void skipWs() {
        while (pos < end) {
            byte b = buf[pos];
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r') pos++;
            else return;
        }
    }

    void expectEnd() {
        skipWs();
        if (pos != end) throw err("trailing characters at " + pos);
    }

    private XJsonException err(String msg) {
        return new XJsonException(msg + " (pos=" + pos + ")");
    }
}
