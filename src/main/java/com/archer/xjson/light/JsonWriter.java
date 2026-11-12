package com.archer.xjson.light;


final class JsonWriter {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char[] ESCAPES = new char[128];
    static {
        ESCAPES['"'] = '"';
        ESCAPES['\\'] = '\\';
        ESCAPES['\b'] = 'b';
        ESCAPES['\f'] = 'f';
        ESCAPES['\n'] = 'n';
        ESCAPES['\r'] = 'r';
        ESCAPES['\t'] = 't';
        for (int i = 0; i < 0x20; i++) {
            if (ESCAPES[i] == 0) ESCAPES[i] = 1; // 控制字符转义为 \\u00XX 形式
        }
    }

    char[] buf = new char[1024];
    int count;

    void reset() {
        // 池化语义：常规容量全部保留复用；仅当缓冲超过 64M chars(128MB) 时才收缩，
        // 避免热路径上反复 几何扩容 产生大量中间垃圾数组
        if (buf.length > (1 << 26)) buf = new char[65536];
        count = 0;
    }

    private void ensure(int n) {
        if (count + n > buf.length) {
            int cap = buf.length;
            while (cap < count + n) cap <<= 1;
            char[] nb = new char[cap];
            System.arraycopy(buf, 0, nb, 0, count);
            buf = nb;
        }
    }

    void write(char c) {
        ensure(1);
        buf[count++] = c;
    }

    void write(String s) {
        int l = s.length();
        ensure(l);
        s.getChars(0, l, buf, count);
        count += l;
    }

    void writeQuoted(String s) {
        int l = s.length();
        ensure(l + 2); // 干净字符 + 引号的容量；转义的额外开销在分支内各自 ensure
        buf[count++] = '"';
        int start = 0; // 干净区段起点：c >= 128 或无需转义的 ASCII 连续段，整段 getChars 批量拷贝
        for (int i = 0; i < l; i++) {
            char c = s.charAt(i);
            if (c < 128 && ESCAPES[c] != 0) {
                if (i > start) {
                    s.getChars(start, i, buf, count);
                    count += i - start;
                }
                char e = ESCAPES[c];
                if (e != 1) {
                    ensure(2);
                    buf[count++] = '\\';
                    buf[count++] = e;
                } else {
                    ensure(6);
                    buf[count++] = '\\';
                    buf[count++] = 'u';
                    buf[count++] = '0';
                    buf[count++] = '0';
                    buf[count++] = HEX[c >> 4];
                    buf[count++] = HEX[c & 15];
                }
                start = i + 1;
            }
        }
        if (l > start) {
            s.getChars(start, l, buf, count);
            count += l - start;
        }
        buf[count++] = '"';
    }

    void writeLong(long v) {
        if (v == Long.MIN_VALUE) {
            write("-9223372036854775808");
            return;
        }
        ensure(20);
        if (v < 0) {
            buf[count++] = '-';
            v = -v;
        }
        int start = count;
        do {
            buf[count++] = (char) ('0' + (int) (v % 10));
            v /= 10;
        } while (v != 0);
        for (int i = start, j = count - 1; i < j; i++, j--) {
            char t = buf[i]; buf[i] = buf[j]; buf[j] = t;
        }
    }

    void writeDouble(double v) {
        write(Double.toString(v));
    }

    @Override
    public String toString() {
        return new String(buf, 0, count);
    }

    /** 两遍扫描直出 UTF-8，无中间 byte[] 增长拷贝 */
    byte[] toUtf8Bytes() {
        int n = 0;
        for (int i = 0; i < count; i++) {
            char c = buf[i];
            if (c < 0x80) n++;
            else if (c < 0x800) n += 2;
            else if (c >= 0xD800 && c <= 0xDBFF) { n += 4; i++; }
            else n += 3;
        }
        byte[] out = new byte[n];
        int p = 0;
        for (int i = 0; i < count; i++) {
            char c = buf[i];
            if (c < 0x80) {
                out[p++] = (byte) c;
            } else if (c < 0x800) {
                out[p++] = (byte) (0xC0 | (c >> 6));
                out[p++] = (byte) (0x80 | (c & 0x3F));
            } else if (c >= 0xD800 && c <= 0xDBFF) {
                int cp = 0x10000 + ((c - 0xD800) << 10) + (buf[++i] - 0xDC00);
                out[p++] = (byte) (0xF0 | (cp >> 18));
                out[p++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                out[p++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                out[p++] = (byte) (0x80 | (cp & 0x3F));
            } else {
                out[p++] = (byte) (0xE0 | (c >> 12));
                out[p++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                out[p++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        return out;
    }
}
