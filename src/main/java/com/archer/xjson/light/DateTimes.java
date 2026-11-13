package com.archer.xjson.light;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;


final class DateTimes {

    private DateTimes() {}

    static LocalDateTime parseLdt(String s) {
        long r = readInt(s, 0);
        int y = (int) (r >>> 32);
        int i = sep(s, (int) r);
        r = readInt(s, i);
        int mo = (int) (r >>> 32);
        i = sep(s, (int) r);
        r = readInt(s, i);
        int d = (int) (r >>> 32);
        i = (int) r;
        int h = 0, mi = 0, sec = 0, nano = 0;
        if (i < s.length()) {
            char c = s.charAt(i); // 日期与时间之间：空格或 T
            if (c == ' ' || c == 'T' || c == 't') i++;
            r = readInt(s, i);
            h = (int) (r >>> 32);
            i = sep(s, (int) r);
            r = readInt(s, i);
            mi = (int) (r >>> 32);
            i = sep(s, (int) r);
            r = readInt(s, i);
            sec = (int) (r >>> 32);
            i = (int) r;
            int[] fr = fraction(s, i);
            nano = fr[0];
            i = skipZone(s, fr[1]);
        }
        if (i != s.length()) throw bad(s);
        return LocalDateTime.of(y, mo, d, h, mi, sec, nano);
    }

    /** LocalDate：取前三个数字段，尾部若带时间部分直接忽略 */
    static LocalDate parseLd(String s) {
        long r = readInt(s, 0);
        int y = (int) (r >>> 32);
        int i = sep(s, (int) r);
        r = readInt(s, i);
        int mo = (int) (r >>> 32);
        i = sep(s, (int) r);
        r = readInt(s, i);
        int d = (int) (r >>> 32);
        return LocalDate.of(y, mo, d);
    }

    static LocalTime parseLt(String s) {
        long r = readInt(s, 0);
        int h = (int) (r >>> 32);
        int i = sep(s, (int) r);
        r = readInt(s, i);
        int mi = (int) (r >>> 32);
        i = sep(s, (int) r);
        r = readInt(s, i);
        int sec = (int) (r >>> 32);
        i = (int) r;
        int[] fr = fraction(s, i);
        i = skipZone(s, fr[1]);
        if (i != s.length()) throw bad(s);
        return LocalTime.of(h, mi, sec, fr[0]);
    }

    /** java.util.Date 家族的字符串入口：全数字按毫秒时间戳，否则按本地时区解释日期时间串 */
    static long epochMillis(String s) {
        int n = s.length();
        boolean digits = n > 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') { digits = false; break; }
        }
        if (digits) return Long.parseLong(s);
        return parseLdt(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ---------------- 内部 ----------------

    /** 从 i 起读一个非负十进制整数（至少 1 位），返回高 32 位=value、低 32 位=nextIndex 的打包 long */
    private static long readInt(String s, int i) {
        int n = s.length();
        if (i >= n) throw bad(s);
        int v = 0;
        char c = s.charAt(i);
        if (c < '0' || c > '9') throw bad(s);
        while (i < n) {
            c = s.charAt(i);
            if (c < '0' || c > '9') break;
            v = v * 10 + (c - '0');
            i++;
        }
        return ((long) v << 32) | i;
    }

    /** 期待一个非数字分隔符并越过它（- / . : 等均可，宽松适配多格式） */
    private static int sep(String s, int i) {
        if (i >= s.length()) throw bad(s);
        char c = s.charAt(i);
        if (c >= '0' && c <= '9') throw bad(s);
        return i + 1;
    }

    /** 可选小数秒：'.' 后 1~9 位 → 纳秒；返回 [nano, nextIndex]，无小数时 nano=0 */
    private static int[] fraction(String s, int i) {
        int n = s.length();
        if (i >= n || s.charAt(i) != '.') return new int[]{0, i};
        int j = i + 1, f = 0, digits = 0;
        while (j < n && digits < 9) {
            char c = s.charAt(j);
            if (c < '0' || c > '9') break;
            f = f * 10 + (c - '0');
            digits++;
            j++;
        }
        while (j < n) { // 超出纳秒精度的尾部数字直接截断
            char c = s.charAt(j);
            if (c < '0' || c > '9') break;
            j++;
        }
        if (digits == 0) throw bad(s);
        while (digits < 9) { f *= 10; digits++; }
        return new int[]{f, j};
    }

    /** 可选时区后缀：Z 或 ±hh[:mm] / ±hhmm / ±hh，仅容忍跳过，字段值按本地时间解释 */
    private static int skipZone(String s, int i) {
        int n = s.length();
        if (i >= n) return i;
        char c = s.charAt(i);
        if (c == 'Z' || c == 'z') return i + 1;
        if (c == '+' || c == '-') {
            i++;
            int d = 0;
            while (i < n && d < 4) {
                char x = s.charAt(i);
                if (x >= '0' && x <= '9') { i++; d++; continue; }
                if (x == ':') { i++; continue; }
                break;
            }
        }
        return i;
    }

    private static XJsonException bad(String s) {
        return new XJsonException("unsupported date-time format: " + s);
    }
}
