package com.archer.xjson.asm;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.archer.xjson.XMap;


/**
 * 双模 JSON 读取器：
 * - byte 模式：直接在 UTF-8 byte[] 上解析，SWAR 加速引号扫描；
 * - text 模式：直接在 String 上按 char 解析，零整篇编码、零副本。
 * 两种模式对外暴露完全相同的方法行为，ASM 生成的 filler 无需感知差异。
 */
public class XNReader {

	private static final int VALUE_INTERN_MAX_LEN = 32;
	private static final int VALUE_INTERN_LIMIT = 4096;

	// ---- SWAR 常量（byte 模式按 8 字节打包）----
	private static final long LO_BITS = 0x0101010101010101L;
	private static final long HI_BITS = 0x8080808080808080L;
	private static final long QUOTE_BCAST = '"' * LO_BITS;
	private static final long BSLASH_BCAST = '\\' * LO_BITS;

	/** byte 模式数据源（text 模式下为 null） */
	private final byte[] data;
	/** text 模式数据源（byte 模式下为 null） */
	private final String text;
	private final int end; // 结束位置（exclusive）
	private int pos;

	/** key 去重表：对象字段名高度重复，零分配 intern 后全篇复用同一实例 */
	private BytePool keyPoolB;
	private StrPool keyPoolT;
	/** 短值字符串去重表：枚举/类型名等短值大量重复 */
	private BytePool valuePoolB;
	private StrPool valuePoolT;

	public XNReader(byte[] data) {
		this(data, 0, null == data ? 0 : data.length);
	}

	public XNReader(byte[] data, int off, int len) {
		if (null == data) {
			throw new XNException("input json bytes can not be null.");
		}
		this.data = data;
		this.text = null;
		this.pos = off;
		this.end = off + len;
	}

	/** text 模式：直接持有 String，不做任何编码转换 */
	public XNReader(String text) {
		if (null == text) {
			throw new XNException("input json text can not be null.");
		}
		this.data = null;
		this.text = text;
		this.pos = 0;
		this.end = text.length();
	}

	/** 统一字符访问：byte 模式按无符号字节，text 模式直接 charAt */
	private char at(int i) {
		return null != data ? (char) (data[i] & 0xFF) : text.charAt(i);
	}

	public int pos() {
		return pos;
	}

	/** 本读取器可读区间的结束位置（绝对下标），供并行分块循环判断 */
	public int end() {
		return end;
	}

	public char peek() {
		return pos < end ? at(pos) : 0;
	}

	public void skipWs() {
		while (pos < end) {
			char c = at(pos);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
				pos++;
			} else {
				break;
			}
		}
	}

	/** 消费期望字符，否则抛错 */
	public void expectChar(char expected) {
		if (pos >= end || at(pos) != expected) {
			throw error("expect '" + expected + "'");
		}
		pos++;
	}

	/** 若当前字符匹配则消费并返回 true */
	public boolean eatChar(char expected) {
		if (pos < end && at(pos) == expected) {
			pos++;
			return true;
		}
		return false;
	}

	/** 读取字符串（key 或 value），pos 停在闭引号之后 */
	public String readString() {
		skipWs();
		if (pos >= end || at(pos) != '"') {
			throw error("expect string");
		}
		int start = pos + 1;
		int quote = scanQuoteOrEscape(start);
		if (quote < 0) {
			throw error("unterminated string");
		}
		if (at(quote) == '\\') {
			return readStringEscaped(start, quote);
		}
		pos = quote + 1;
		return materialize(start, quote);
	}

	/** 对象 key：区间零分配 intern，全篇复用同一实例，消除逐字段 decode 垃圾 */
	public String readKey() {
		skipWs();
		if (pos >= end || at(pos) != '"') {
			throw error("expect string");
		}
		int start = pos + 1;
		int quote = scanQuoteOrEscape(start);
		if (quote < 0) {
			throw error("unterminated string");
		}
		if (at(quote) == '\\') {
			String decoded = readStringEscaped(start, quote);
			return null != data ? keyPoolB().intern(decoded) : keyPoolT().intern(decoded);
		}
		pos = quote + 1;
		return null != data ? keyPoolB().intern(data, start, quote)
				: keyPoolT().intern(text, start, quote);
	}

	/**
	 * 实体路径专用 key 读取：按已知字段名表（KeyTable）区间直接匹配，
	 * 命中返回字段名常量全篇复用；未知 key materialize 新 String，均不经过通用 keyPool。
	 * 调用前需已 skipWs（readBean 在 '}' 判断之后调用，位置必在 '"'）。
	 */
	public String readKeyIn(KeyTable kt) {
		if (pos >= end || at(pos) != '"') {
			throw error("expect string");
		}
		int start = pos + 1;
		int quote = scanQuoteOrEscape(start);
		if (quote < 0) {
			throw error("unterminated string");
		}
		if (at(quote) == '\\') {
			// 实体字段名不含转义，兜底 decode 即可（filler equals 按内容匹配）
			return readStringEscaped(start, quote);
		}
		pos = quote + 1;
		String hit = kt.match(data, text, start, quote);
		return null != hit ? hit : materialize(start, quote);
	}

	/**
	 * skipWs + expectChar 融合：readBean 冒号间隙单次入口。
	 * 值前空白由后续标量读取（readLong/readDouble/readStringValue/skipValue 等）内部 skipWs 兜底。
	 */
	public void skipWsExpect(char expected) {
		skipWs();
		if (pos >= end || at(pos) != expected) {
			throw error("expect '" + expected + "'");
		}
		pos++;
	}

	/**
	 * 实体已知字段名匹配表（开放寻址）：按 String.hashCode 兼容 hash 探测 + 区间逐字符比对，
	 * 命中返回 names 常量。表容量按字段数 2 倍起步，远小于通用 keyPool，探测热点稳定驻留 L1。
	 */
	public static final class KeyTable {
		private final String[] names;
		private final int[] hashes;
		private final int[] slots; // names 下标+1，0 为空槽

		public KeyTable(String[] names) {
			this.names = names;
			this.hashes = new int[names.length];
			int cap = 16;
			while (cap < names.length * 2) {
				cap <<= 1;
			}
			this.slots = new int[cap];
			int mask = cap - 1;
			for (int i = 0; i < names.length; i++) {
				int h = names[i].hashCode();
				hashes[i] = h;
				int s = (h ^ (h >>> 16)) & mask;
				while (0 != slots[s]) {
					s = (s + 1) & mask;
				}
				slots[s] = i + 1;
			}
		}

		/** 区间匹配字段名：命中返回常量，未命中返回 null。byte 模式遇非 ASCII 直接放弃（调用方 materialize 后由 filler equals 按内容兜底） */
		String match(byte[] data, String text, int start, int end) {
			int len = end - start;
			int h = 0;
			if (null != data) {
				int high = 0;
				for (int i = start; i < end; i++) {
					int b = data[i] & 0xFF;
					high |= b;
					h = 31 * h + b;
				}
				if (high >= 0x80) {
					return null;
				}
				int mask = slots.length - 1;
				int s = (h ^ (h >>> 16)) & mask;
				while (true) {
					int slot = slots[s];
					if (0 == slot) {
						return null;
					}
					String name = names[slot - 1];
					if (hashes[slot - 1] == h && name.length() == len && regionEqualsBytes(name, data, start)) {
						return name;
					}
					s = (s + 1) & mask;
				}
			}
			for (int i = start; i < end; i++) {
				h = 31 * h + text.charAt(i);
			}
			int mask = slots.length - 1;
			int s = (h ^ (h >>> 16)) & mask;
			while (true) {
				int slot = slots[s];
				if (0 == slot) {
					return null;
				}
				String name = names[slot - 1];
				if (hashes[slot - 1] == h && name.length() == len && name.regionMatches(0, text, start, len)) {
					return name;
				}
				s = (s + 1) & mask;
			}
		}

		private static boolean regionEqualsBytes(String name, byte[] d, int start) {
			for (int i = 0; i < name.length(); i++) {
				if (name.charAt(i) != (char) (d[start + i] & 0xFF)) {
					return false;
				}
			}
			return true;
		}
	}

	/** 读取字符串值（可能是 null）：短值去重复用，长值直接 materialize */
	public String readStringValue() {
		skipWs();
		if (pos + 4 <= end && at(pos) == 'n'
				&& at(pos + 1) == 'u' && at(pos + 2) == 'l' && at(pos + 3) == 'l') {
			pos += 4;
			return null;
		}
		if (pos >= end || at(pos) != '"') {
			throw error("expect string");
		}
		int start = pos + 1;
		int quote = scanQuoteOrEscape(start);
		if (quote < 0) {
			throw error("unterminated string");
		}
		if (at(quote) == '\\') {
			String decoded = readStringEscaped(start, quote);
			return decoded.length() <= VALUE_INTERN_MAX_LEN
					? (null != data ? valuePoolB().intern(decoded) : valuePoolT().intern(decoded))
					: decoded;
		}
		pos = quote + 1;
		if (quote - start <= VALUE_INTERN_MAX_LEN) {
			return null != data ? valuePoolB().intern(data, start, quote)
					: valuePoolT().intern(text, start, quote);
		}
		return materialize(start, quote);
	}

	/** 区间物化为 String：byte 模式 UTF-8 解码，text 模式 substring（纯 memcpy） */
	private String materialize(int start, int endExclusive) {
		return null != data
				? new String(data, start, endExclusive - start, StandardCharsets.UTF_8)
				: text.substring(start, endExclusive);
	}

	/**
	 * 扫描：从 from 开始找最近的 '"' 或 '\\'，返回其下标；找不到返回 -1。
	 * byte 模式 SWAR 8 字节一步位并行探测，text 模式逐字符扫描。
	 */
	private int scanQuoteOrEscape(int from) {
		return null != data ? scanQuoteOrEscapeBytes(from) : scanQuoteOrEscapeText(from);
	}

	/** SWAR 扫描（byte 模式）：命中后逐位确认（排除 SWAR 借位误报） */
	private int scanQuoteOrEscapeBytes(int from) {
		int p = from;
		while (p + 8 <= end) {
			long w = loadLongLE(p);
			long m = zeroByteMask(w ^ QUOTE_BCAST) | zeroByteMask(w ^ BSLASH_BCAST);
			if (m != 0) {
				int idx = p + (Long.numberOfTrailingZeros(m) >>> 3);
				byte b = data[idx];
				if (b == '"' || b == '\\') {
					return idx;
				}
				// SWAR 借位误报：从下一字节继续
				p = idx + 1;
				continue;
			}
			p += 8;
		}
		while (p < end) {
			byte b = data[p];
			if (b == '"' || b == '\\') {
				return p;
			}
			p++;
		}
		return -1;
	}

	/** 逐字符扫描（text 模式）：key/短值为主，字符串普遍很短 */
	private int scanQuoteOrEscapeText(int from) {
		int p = from;
		while (p < end) {
			char c = text.charAt(p);
			if (c == '"' || c == '\\') {
				return p;
			}
			p++;
		}
		return -1;
	}

	/** SWAR：返回 w 中值为 0 的字节的最高位掩码 */
	private static long zeroByteMask(long w) {
		return (w - LO_BITS) & ~w & HI_BITS;
	}

	/** 小端序装配 8 字节为 long（第 i 字节位于 long 的第 i 个字节位） */
	private long loadLongLE(int p) {
		return ((long) data[p] & 0xFF)
				| (((long) data[p + 1] & 0xFF) << 8)
				| (((long) data[p + 2] & 0xFF) << 16)
				| (((long) data[p + 3] & 0xFF) << 24)
				| (((long) data[p + 4] & 0xFF) << 32)
				| (((long) data[p + 5] & 0xFF) << 40)
				| (((long) data[p + 6] & 0xFF) << 48)
				| (((long) data[p + 7] & 0xFF) << 56);
	}

	private BytePool keyPoolB() {
		if (null == keyPoolB) {
			keyPoolB = new BytePool(64, -1);
		}
		return keyPoolB;
	}

	private StrPool keyPoolT() {
		if (null == keyPoolT) {
			keyPoolT = new StrPool(64, -1);
		}
		return keyPoolT;
	}

	private BytePool valuePoolB() {
		if (null == valuePoolB) {
			valuePoolB = new BytePool(512, VALUE_INTERN_LIMIT);
		}
		return valuePoolB;
	}

	private StrPool valuePoolT() {
		if (null == valuePoolT) {
			valuePoolT = new StrPool(512, VALUE_INTERN_LIMIT);
		}
		return valuePoolT;
	}

	/** 若当前位置是 null 字面量则消费并返回 true，否则不消费返回 false */
	public boolean readNull() {
		skipWs();
		if (pos + 4 <= end && at(pos) == 'n'
				&& at(pos + 1) == 'u' && at(pos + 2) == 'l' && at(pos + 3) == 'l') {
			pos += 4;
			return true;
		}
		return false;
	}

	private String readStringEscaped(int start, int escPos) {
		return null != data ? readStringEscapedBytes(start, escPos)
				: readStringEscapedText(start, escPos);
	}

	private String readStringEscapedBytes(int start, int escPos) {
		StringBuilder sb = new StringBuilder(escPos - start + 16);
		appendUtf8(sb, start, escPos);
		pos = escPos;
		while (pos < end) {
			byte c = data[pos++];
			if (c == '"') {
				return sb.toString();
			}
			if (c == '\\') {
				if (pos >= end) {
					throw error("unexpected end of input in string escape");
				}
				byte esc = data[pos++];
				switch (esc) {
					case '"': sb.append('"'); break;
					case '\\': sb.append('\\'); break;
					case '/': sb.append('/'); break;
					case 'b': sb.append('\b'); break;
					case 'f': sb.append('\f'); break;
					case 'n': sb.append('\n'); break;
					case 'r': sb.append('\r'); break;
					case 't': sb.append('\t'); break;
					case 'u':
						if (pos + 4 > end) {
							throw error("invalid unicode escape");
						}
						sb.append((char) Integer.parseInt(
								new String(data, pos, 4, StandardCharsets.US_ASCII), 16));
						pos += 4;
						break;
					default:
						throw error("invalid escape character '\\" + (char) esc + "'");
				}
			} else if ((c & 0x80) == 0) {
				sb.append((char) c);
			} else {
				// UTF-8 多字节序列解码
				pos--;
				pos += decodeUtf8Char(sb, pos);
			}
		}
		throw error("unterminated string");
	}

	/** text 模式转义慢路径：源字符已完成解码，直接追加即可 */
	private String readStringEscapedText(int start, int escPos) {
		StringBuilder sb = new StringBuilder(escPos - start + 16);
		sb.append(text, start, escPos);
		pos = escPos;
		while (pos < end) {
			char c = text.charAt(pos++);
			if (c == '"') {
				return sb.toString();
			}
			if (c == '\\') {
				if (pos >= end) {
					throw error("unexpected end of input in string escape");
				}
				char esc = text.charAt(pos++);
				switch (esc) {
					case '"': sb.append('"'); break;
					case '\\': sb.append('\\'); break;
					case '/': sb.append('/'); break;
					case 'b': sb.append('\b'); break;
					case 'f': sb.append('\f'); break;
					case 'n': sb.append('\n'); break;
					case 'r': sb.append('\r'); break;
					case 't': sb.append('\t'); break;
					case 'u':
						if (pos + 4 > end) {
							throw error("invalid unicode escape");
						}
						sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
						pos += 4;
						break;
					default:
						throw error("invalid escape character '\\" + esc + "'");
				}
			} else {
				sb.append(c);
			}
		}
		throw error("unterminated string");
	}

	/** 将 [start, end) 区间 UTF-8 解码追加到 sb（byte 模式转义慢路径专用） */
	private void appendUtf8(StringBuilder sb, int start, int end) {
		int p = start;
		while (p < end) {
			byte c = data[p];
			if ((c & 0x80) == 0) {
				sb.append((char) c);
				p++;
			} else {
				p += decodeUtf8Char(sb, p);
			}
		}
	}

	/** 解码 pos 处的一个 UTF-8 字符到 sb，返回消费的字节数 */
	private int decodeUtf8Char(StringBuilder sb, int p) {
		int b0 = data[p] & 0xFF;
		if (b0 < 0xE0) {
			sb.append((char) (((b0 & 0x1F) << 6) | (data[p + 1] & 0x3F)));
			return 2;
		}
		if (b0 < 0xF0) {
			sb.append((char) (((b0 & 0x0F) << 12) | ((data[p + 1] & 0x3F) << 6)
					| (data[p + 2] & 0x3F)));
			return 3;
		}
		int cp = ((b0 & 0x07) << 18) | ((data[p + 1] & 0x3F) << 12)
				| ((data[p + 2] & 0x3F) << 6) | (data[p + 3] & 0x3F);
		sb.append(Character.highSurrogate(cp));
		sb.append(Character.lowSurrogate(cp));
		return 4;
	}

	/** 跳过任意 JSON 值，零分配（不构建树） */
	public void skipValue() {
		skipWs();
		if (pos >= end) {
			throw error("unexpected end of input");
		}
		char c = at(pos);
		switch (c) {
			case '{': skipObject(); break;
			case '[': skipArray(); break;
			case '"': skipString(); break;
			case 't': expectLiteral("true"); break;
			case 'f': expectLiteral("false"); break;
			case 'n': expectLiteral("null"); break;
			default: skipNumber(); break;
		}
	}

	private void skipObject() {
		pos++; // '{'
		skipWs();
		if (eatChar('}')) {
			return;
		}
		while (true) {
			skipWs();
			skipString(); // key
			skipWs();
			if (pos >= end || at(pos) != ':') {
				throw error("expect ':' in object");
			}
			pos++;
			skipValue();
			skipWs();
			if (eatChar('}')) {
				return;
			}
			expectChar(',');
		}
	}

	private void skipArray() {
		pos++; // '['
		skipWs();
		if (eatChar(']')) {
			return;
		}
		while (true) {
			skipValue();
			skipWs();
			if (eatChar(']')) {
				return;
			}
			expectChar(',');
		}
	}

	/** 跳过字符串：byte 模式 SWAR 零分配，text 模式逐字符，仅校验合法性并推进游标 */
	public void skipString() {
		skipWs();
		if (pos >= end || at(pos) != '"') {
			throw error("expect string");
		}
		if (null == data) {
			skipStringText();
			return;
		}
		int p = pos + 1;
		while (p + 8 <= end) {
			long w = loadLongLE(p);
			long m = zeroByteMask(w ^ QUOTE_BCAST) | zeroByteMask(w ^ BSLASH_BCAST);
			if (m != 0) {
				int idx = p + (Long.numberOfTrailingZeros(m) >>> 3);
				byte b = data[idx];
				if (b == '"') {
					pos = idx + 1;
					return;
				}
				if (b == '\\') {
					p = idx + 2; // 跳过转义对，避免 \" 被误判为结束引号
					continue;
				}
				p = idx + 1; // SWAR 误报
				continue;
			}
			p += 8;
		}
		while (p < end) {
			byte b = data[p];
			if (b == '"') {
				pos = p + 1;
				return;
			}
			if (b == '\\') {
				p += 2;
				continue;
			}
			p++;
		}
		throw error("unterminated string");
	}

	private void skipStringText() {
		int p = pos + 1;
		while (p < end) {
			char c = text.charAt(p);
			if (c == '"') {
				pos = p + 1;
				return;
			}
			if (c == '\\') {
				p += 2;
				continue;
			}
			p++;
		}
		throw error("unterminated string");
	}

	private void skipNumber() {
		if (pos >= end) {
			throw error("unexpected end of input");
		}
		if (at(pos) == '-') {
			pos++;
		}
		while (pos < end) {
			char c = at(pos);
			if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
					|| c == '+' || c == '-') {
				pos++;
			} else {
				break;
			}
		}
	}

	public boolean readBoolean() {
		skipWs();
		char c = at(pos);
		if (c == 't') {
			expectLiteral("true");
			return true;
		}
		if (c == 'f') {
			expectLiteral("false");
			return false;
		}
		throw error("expect boolean");
	}

	public Boolean readBooleanObj() {
		if (readNull()) {
			return null;
		}
		return readBoolean();
	}

	public byte readByte() {
		return (byte) readLong();
	}

	public Byte readByteObj() {
		if (readNull()) {
			return null;
		}
		return readByte();
	}

	public short readShort() {
		return (short) readLong();
	}

	public Short readShortObj() {
		if (readNull()) {
			return null;
		}
		return readShort();
	}

	public int readInt() {
		return (int) readLong();
	}

	public Integer readInteger() {
		if (readNull()) {
			return null;
		}
		return readInt();
	}

	public long readLong() {
		skipWs();
		boolean neg = at(pos) == '-';
		if (neg) {
			pos++;
		}
		int digitStart = pos;
		long ival = 0;
		boolean overflow = false;
		while (pos < end) {
			char c = at(pos);
			if (c < '0' || c > '9') {
				break;
			}
			int d = c - '0';
			if (!overflow) {
				if (ival > (Long.MAX_VALUE - d) / 10) {
					overflow = true;
				} else {
					ival = ival * 10 + d;
				}
			}
			pos++;
		}
		if (pos == digitStart) {
			throw error("invalid number");
		}
		char next = pos < end ? at(pos) : 0;
		if (next == '.' || next == 'e' || next == 'E') {
			throw error("expect integer but got floating number");
		}
		if (overflow) {
			throw error("integer overflow");
		}
		return neg ? -ival : ival;
	}

	public Long readLongObj() {
		if (readNull()) {
			return null;
		}
		return readLong();
	}

	public float readFloat() {
		return (float) readDouble();
	}

	public Float readFloatObj() {
		if (readNull()) {
			return null;
		}
		return readFloat();
	}

	public double readDouble() {
		skipWs();
		boolean neg = at(pos) == '-';
		if (neg) {
			pos++;
		}
		int digitStart = pos;
		double dval = 0;
		while (pos < end) {
			char c = at(pos);
			if (c < '0' || c > '9') {
				break;
			}
			dval = dval * 10 + (c - '0');
			pos++;
		}
		if (pos == digitStart) {
			throw error("invalid number");
		}
		if (pos < end && at(pos) == '.') {
			pos++;
			int fracStart = pos;
			double factor = 0.1;
			while (pos < end) {
				char c = at(pos);
				if (c < '0' || c > '9') {
					break;
				}
				dval += (c - '0') * factor;
				factor *= 0.1;
				pos++;
			}
			if (pos == fracStart) {
				throw error("invalid number");
			}
		}
		if (pos < end && (at(pos) == 'e' || at(pos) == 'E')) {
			pos++;
			boolean eneg = false;
			if (pos < end && (at(pos) == '+' || at(pos) == '-')) {
				eneg = at(pos) == '-';
				pos++;
			}
			int expStart = pos;
			int exp = 0;
			while (pos < end) {
				char c = at(pos);
				if (c < '0' || c > '9') {
					break;
				}
				exp = exp * 10 + (c - '0');
				pos++;
			}
			if (pos == expStart) {
				throw error("invalid number");
			}
			dval *= Math.pow(10.0, eneg ? -exp : exp);
		}
		return neg ? -dval : dval;
	}

	public Double readDoubleObj() {
		if (readNull()) {
			return null;
		}
		return readDouble();
	}

	public char readChar() {
		String s = readString();
		return s.isEmpty() ? '\0' : s.charAt(0);
	}

	public Character readCharObj() {
		if (readNull()) {
			return null;
		}
		return readChar();
	}

	/** 读取数字原始文本（供 BigInteger/BigDecimal/Date 等转换） */
	public String readNumberAsString() {
		skipWs();
		int start = pos;
		if (pos < end && at(pos) == '-') {
			pos++;
		}
		while (pos < end) {
			char c = at(pos);
			if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
					|| c == '+' || c == '-') {
				pos++;
			} else {
				break;
			}
		}
		if (pos == start) {
			throw error("invalid number");
		}
		return null != data
				? new String(data, start, pos - start, StandardCharsets.US_ASCII)
				: text.substring(start, pos);
	}

	public Object readGeneric() {
		skipWs();
		if (pos >= end) {
			throw error("unexpected end of input");
		}
		char c = at(pos);
		switch (c) {
			case '{': return readObjectGeneric();
			case '[': return readArrayGeneric();
			case '"': return readStringValue();
			case 't': expectLiteral("true"); return Boolean.TRUE;
			case 'f': expectLiteral("false"); return Boolean.FALSE;
			case 'n': expectLiteral("null"); return null;
			default: return readNumberGeneric();
		}
	}

	private Map<String, Object> readObjectGeneric() {
		pos++; // '{'
		Map<String, Object> map = new XMap<>();
		skipWs();
		if (eatChar('}')) {
			return map;
		}
		while (true) {
			skipWs();
			String key = readKey();
			skipWs();
			expectChar(':');
			skipWs();
			map.put(key, readGeneric());
			skipWs();
			if (eatChar('}')) {
				return map;
			}
			expectChar(',');
		}
	}

	private List<Object> readArrayGeneric() {
		pos++; // '['
		List<Object> list = new ArrayList<>(32);
		skipWs();
		if (eatChar(']')) {
			return list;
		}
		while (true) {
			skipWs();
			list.add(readGeneric());
			skipWs();
			if (eatChar(']')) {
				return list;
			}
			expectChar(',');
		}
	}

	private Object readNumberGeneric() {
		int start = pos;
		boolean neg = at(pos) == '-';
		if (neg) {
			pos++;
		}
		int digitStart = pos;
		long ival = 0;
		boolean overflow = false;
		boolean floating = false;
		while (pos < end) {
			char c = at(pos);
			if (c < '0' || c > '9') {
				break;
			}
			int d = c - '0';
			if (!overflow) {
				if (ival > (Long.MAX_VALUE - d) / 10) {
					overflow = true;
				} else {
					ival = ival * 10 + d;
				}
			}
			pos++;
		}
		if (pos == digitStart) {
			throw error("invalid number");
		}
		if (pos < end && at(pos) == '.') {
			floating = true;
			pos++;
			while (pos < end) {
				char c = at(pos);
				if (c < '0' || c > '9') {
					break;
				}
				pos++;
			}
		}
		if (pos < end && (at(pos) == 'e' || at(pos) == 'E')) {
			floating = true;
			pos++;
			if (pos < end && (at(pos) == '+' || at(pos) == '-')) {
				pos++;
			}
			while (pos < end) {
				char c = at(pos);
				if (c < '0' || c > '9') {
					break;
				}
				pos++;
			}
		}
		if (!floating) {
			if (!overflow) {
				long v = neg ? -ival : ival;
				if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
					return (int) v;
				}
				return v;
			}
			// 冷路径：超出 long 的整数，物化原文本转 BigDecimal（保持原有语义）
			String num = null != data
					? new String(data, start, pos - start, StandardCharsets.US_ASCII)
					: text.substring(start, pos);
			return new BigDecimal(num);
		}
		pos = start;
		return readDouble();
	}

	private void expectLiteral(String literal) {
		if (pos + literal.length() > end) {
			throw error("expect '" + literal + "'");
		}
		for (int i = 0; i < literal.length(); i++) {
			if (at(pos + i) != literal.charAt(i)) {
				throw error("expect '" + literal + "'");
			}
		}
		pos += literal.length();
	}

	private XNException error(String msg) {
		return new XNException(msg + " at pos " + pos);
	}

	/**
	 * 字节区间零分配字符串去重池（开放寻址）。
	 * 键以 UTF-8 字节精确比对：hash 用字节序列计算，命中桶后逐字节相等才复用，
	 * 对 ASCII 与非 ASCII key 均正确，全程不产生临时 String。
	 */
	static final class BytePool {
		/** 池满后的 miss 采样窗口：窗口内命中率 <25% 判定去重纯赔本，熔断后直通 materialize */
		private static final int ADAPT_SAMPLE = 2048;

		private String[] strs;
		private byte[][] bytes;
		private int[] hashes;
		private int size;
		private final int maxSize;
		private int hits;
		private int misses;
		private boolean off;

		BytePool(int capacity, int maxSize) {
			int cap = 16;
			while (cap < capacity) cap <<= 1;
			this.strs = new String[cap];
			this.bytes = new byte[cap][];
			this.hashes = new int[cap];
			this.maxSize = maxSize;
		}

		String intern(byte[] d, int start, int end) {
			if (off) {
				return new String(d, start, end - start, StandardCharsets.UTF_8);
			}
			int h = 0;
			for (int i = start; i < end; i++) {
				h = 31 * h + (d[i] & 0xFF);
			}
			int mask = strs.length - 1;
			int i = h & mask;
			int keyLen = end - start;
			while (true) {
				String s = strs[i];
				if (null == s) {
					String str = new String(d, start, keyLen, StandardCharsets.UTF_8);
					if (maxSize < 0 || size < maxSize) {
						put(str, copyOf(d, start, keyLen), h);
					} else {
						onFullMiss();
					}
					return str;
				}
				byte[] pb = bytes[i];
				if (hashes[i] == h && pb.length == keyLen && bytesEqual(pb, d, start, keyLen)) {
					if (maxSize >= 0 && size >= maxSize) {
						hits++;
					}
					return s;
				}
				i = (i + 1) & mask;
			}
		}

		String intern(String str) {
			if (null == str) return null;
			if (off) return str;
			byte[] enc = str.getBytes(StandardCharsets.UTF_8);
			int h = 0;
			for (byte b : enc) {
				h = 31 * h + (b & 0xFF);
			}
			int mask = strs.length - 1;
			int i = h & mask;
			while (true) {
				String s = strs[i];
				if (null == s) {
					if (maxSize < 0 || size < maxSize) {
						put(str, enc, h);
					} else {
						onFullMiss();
					}
					return str;
				}
				byte[] pb = bytes[i];
				if (hashes[i] == h && pb.length == enc.length && bytesEqual(pb, enc, 0, enc.length)) {
					return s;
				}
				i = (i + 1) & mask;
			}
		}

		private void onFullMiss() {
			if (++misses >= ADAPT_SAMPLE) {
				if (hits * 4 < misses) {
					off = true;
				}
				hits = 0;
				misses = 0;
			}
		}

		private static boolean bytesEqual(byte[] pooled, byte[] d, int start, int len) {
			for (int j = 0; j < len; j++) {
				if (pooled[j] != d[start + j]) {
					return false;
				}
			}
			return true;
		}

		private static byte[] copyOf(byte[] d, int start, int len) {
			byte[] out = new byte[len];
			System.arraycopy(d, start, out, 0, len);
			return out;
		}

		private void put(String str, byte[] enc, int h) {
			if ((size + 1) * 4 > strs.length * 3) {
				resize();
			}
			int mask = strs.length - 1;
			int i = h & mask;
			while (null != strs[i]) {
				i = (i + 1) & mask;
			}
			strs[i] = str;
			bytes[i] = enc;
			hashes[i] = h;
			size++;
		}

		private void resize() {
			String[] oldStrs = strs;
			byte[][] oldBytes = bytes;
			int[] oldHashes = hashes;
			strs = new String[oldStrs.length << 1];
			bytes = new byte[strs.length][];
			hashes = new int[strs.length];
			size = 0;
			for (int j = 0; j < oldStrs.length; j++) {
				if (null != oldStrs[j]) {
					putRaw(oldStrs[j], oldBytes[j], oldHashes[j]);
				}
			}
		}

		private void putRaw(String str, byte[] enc, int h) {
			int mask = strs.length - 1;
			int i = h & mask;
			while (null != strs[i]) {
				i = (i + 1) & mask;
			}
			strs[i] = str;
			bytes[i] = enc;
			hashes[i] = h;
			size++;
		}
	}

	/**
	 * 文本区间零分配字符串去重池（开放寻址，text 模式专用）。
	 * hash 与 String.hashCode 兼容（31 累加），命中桶缓存 hash 快速否定，
	 * 逐字符相等才复用，全程不产生临时 String。
	 */
	static final class StrPool {
		/** 池满后的 miss 采样窗口：窗口内命中率 <25% 判定去重纯赔本，熔断后直通 materialize */
		private static final int ADAPT_SAMPLE = 2048;

		private String[] keys;
		private int[] hashes;
		private int size;
		private final int maxSize;
		private int hits;
		private int misses;
		private boolean off;

		StrPool(int capacity, int maxSize) {
			int cap = 16;
			while (cap < capacity) cap <<= 1;
			this.keys = new String[cap];
			this.hashes = new int[cap];
			this.maxSize = maxSize;
		}

		/** 基于源字符串区间 [start, end) 的零分配 intern */
		String intern(String src, int start, int end) {
			if (off) {
				return src.substring(start, end);
			}
			int h = 0;
			for (int i = start; i < end; i++) {
				h = 31 * h + src.charAt(i);
			}
			int mask = keys.length - 1;
			int i = (h ^ (h >>> 16)) & mask;
			int len = end - start;
			while (true) {
				String k = keys[i];
				if (null == k) {
					String s = src.substring(start, end);
					if (maxSize < 0 || size < maxSize) {
						put(s, h);
					} else {
						onFullMiss();
					}
					return s;
				}
				if (hashes[i] == h && k.length() == len && regionMatches(k, src, start, len)) {
					if (maxSize >= 0 && size >= maxSize) {
						hits++;
					}
					return k;
				}
				i = (i + 1) & mask;
			}
		}

		/** 整串 intern（转义字符串等已 materialize 的场景） */
		String intern(String str) {
			if (null == str) return null;
			if (off) return str;
			int h = str.hashCode();
			int mask = keys.length - 1;
			int i = (h ^ (h >>> 16)) & mask;
			while (true) {
				String k = keys[i];
				if (null == k) {
					if (maxSize < 0 || size < maxSize) {
						put(str, h);
					} else {
						onFullMiss();
					}
					return str;
				}
				if (hashes[i] == h && k.equals(str)) {
					return k;
				}
				i = (i + 1) & mask;
			}
		}

		private void onFullMiss() {
			if (++misses >= ADAPT_SAMPLE) {
				if (hits * 4 < misses) {
					off = true;
				}
				hits = 0;
				misses = 0;
			}
		}

		private static boolean regionMatches(String k, String src, int start, int len) {
			for (int j = 0; j < len; j++) {
				if (k.charAt(j) != src.charAt(start + j)) {
					return false;
				}
			}
			return true;
		}

		private void put(String str, int h) {
			if ((size + 1) * 4 > keys.length * 3) {
				resize();
			}
			int mask = keys.length - 1;
			int i = (h ^ (h >>> 16)) & mask;
			while (null != keys[i]) {
				i = (i + 1) & mask;
			}
			keys[i] = str;
			hashes[i] = h;
			size++;
		}

		private void resize() {
			String[] oldKeys = keys;
			int[] oldHashes = hashes;
			keys = new String[oldKeys.length << 1];
			hashes = new int[keys.length];
			size = 0;
			for (int j = 0; j < oldKeys.length; j++) {
				if (null != oldKeys[j]) {
					put(oldKeys[j], oldHashes[j]);
				}
			}
		}
	}
}
