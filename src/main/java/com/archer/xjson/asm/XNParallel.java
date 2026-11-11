package com.archer.xjson.asm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public final class XNParallel {

	private static final int MIN_PARALLEL_BYTES = 1 << 20;
	private static final int MIN_PARALLEL_ELEMS = 2048;

	private XNParallel() {
	}

	/** 并行解析顶层 JSON 数组为 bean 列表（保持元素顺序） */
	public static List<Object> parseList(byte[] json, Class<?> cls) {
		int[] bounds = findElementBounds(json);
		if (null == bounds) {
			return XNJSONRuntime.readListFromBytes(json, cls);
		}
		int elems = bounds.length >>> 1;
		int tasks = taskCount(elems);
		if (tasks <= 1) {
			return XNJSONRuntime.readListFromBytes(json, cls);
		}
		@SuppressWarnings("unchecked")
		CompletableFuture<List<Object>>[] futs = new CompletableFuture[tasks];
		int per = (elems + tasks - 1) / tasks;
		for (int t = 0; t < tasks; t++) {
			final int e0 = t * per;
			final int e1 = Math.min(elems, e0 + per);
			if (e0 >= e1) {
				futs[t] = CompletableFuture.completedFuture(new ArrayList<>());
				continue;
			}
			final int s = bounds[e0 << 1];
			final int e = bounds[(e1 << 1) - 1];
			futs[t] = CompletableFuture.supplyAsync(() -> parseListChunk(json, s, e, cls));
		}
		List<Object> out = new ArrayList<>(elems);
		for (CompletableFuture<List<Object>> f : futs) {
			out.addAll(f.join());
		}
		return out;
	}

	private static List<Object> parseListChunk(byte[] json, int s, int e, Class<?> cls) {
		XNReader r = new XNReader(json, s, e - s);
		List<Object> out = new ArrayList<>();
		while (true) {
			out.add(XNJSONRuntime.readValue(r, cls));
			r.skipWs();
			if (r.pos() >= r.end()) {
				break;
			}
			r.expectChar(',');
			r.skipWs();
		}
		return out;
	}

	private static int taskCount(int elems) {
		int cores = Runtime.getRuntime().availableProcessors();
		if (cores <= 1) {
			return 1;
		}
		return Math.min(cores, Math.max(2, elems / MIN_PARALLEL_ELEMS));
	}

	private static final long LO_BITS = 0x0101010101010101L;
	private static final long HI_BITS = 0x8080808080808080L;
	private static final long B_QUOTE = '"' * LO_BITS;
	private static final long B_BSLASH = '\\' * LO_BITS;
	private static final long B_LBRACE = '{' * LO_BITS;
	private static final long B_RBRACE = '}' * LO_BITS;
	private static final long B_LBRACKET = '[' * LO_BITS;
	private static final long B_RBRACKET = ']' * LO_BITS;

	private static long loadLongLE(byte[] d, int p) {
		return (d[p] & 0xFFL)
				| ((d[p + 1] & 0xFFL) << 8)
				| ((d[p + 2] & 0xFFL) << 16)
				| ((d[p + 3] & 0xFFL) << 24)
				| ((d[p + 4] & 0xFFL) << 32)
				| ((d[p + 5] & 0xFFL) << 40)
				| ((d[p + 6] & 0xFFL) << 48)
				| ((d[p + 7] & 0xFFL) << 56);
	}

	private static long zeroByteMask(long w) {
		return (w - LO_BITS) & ~w & HI_BITS;
	}

	/**
	 * 扫描顶层数组的元素边界，返回扁平数组 [s0,e0, s1,e1, ...]。
	 * 仅支持元素为 {...}（或嵌套数组 [...]）的同构数组；遇标量元素或
	 * 结构异常返回 null（调用方回退串行）。
	 */
	static int[] findElementBounds(byte[] d) {
		if (null == d || d.length < MIN_PARALLEL_BYTES) {
			return null;
		}
		int p = 0;
		// 顶层 '['
		while (p < d.length && isWs(d[p])) {
			p++;
		}
		if (p >= d.length || d[p] != '[') {
			return null;
		}
		p++;

		int[] bounds = new int[4096];
		int count = 0;
		int depth = 1;
		int elemStart = -1;
		boolean inStr = false;
		boolean esc = false;

		while (p < d.length) {
			if (p + 8 <= d.length) {
				long w = loadLongLE(d, p);
				long m = zeroByteMask(w ^ B_QUOTE) | zeroByteMask(w ^ B_BSLASH)
						| zeroByteMask(w ^ B_LBRACE) | zeroByteMask(w ^ B_RBRACE)
						| zeroByteMask(w ^ B_LBRACKET) | zeroByteMask(w ^ B_RBRACKET);
				if (m == 0) {
					// 8 字节内无结构字符：深度1上出现非空白即标量元素
					if (depth == 1 && elemStart < 0 && !inStr && hasNonWsScalar(d, p, p + 8)) {
						return null;
					}
					p += 8;
					continue;
				}
				int checked = p; // 位之间间隙的标量检查起点
				while (m != 0) {
					int idx = p + (Long.numberOfTrailingZeros(m) >>> 3);
					m &= m - 1;
					if (depth == 1 && elemStart < 0 && !inStr && hasNonWsScalar(d, checked, idx)) {
						return null;
					}
					checked = idx + 1;
					byte b = d[idx];
					if (inStr) {
						if (esc) {
							esc = false;
						} else if (b == '\\') {
							esc = true;
						} else if (b == '"') {
							inStr = false;
						}
						continue;
					}
					switch (b) {
						case '"':
							if (depth == 1 && elemStart < 0) {
								return null; // 顶层数组的字符串标量元素
							}
							inStr = true;
							break;
						case '{':
						case '[':
							depth++;
							if (depth == 2) {
								elemStart = idx;
							}
							break;
						case '}':
						case ']':
							depth--;
							if (depth == 1) {
								if (elemStart < 0) {
									return null;
								}
								if (count + 2 > bounds.length) {
									int[] nb = new int[bounds.length << 1];
									System.arraycopy(bounds, 0, nb, 0, bounds.length);
									bounds = nb;
								}
								bounds[count++] = elemStart;
								bounds[count++] = idx + 1;
								elemStart = -1;
							} else if (depth == 0) {
								return finish(bounds, count);
							}
							break;
						default:
							break;
					}
				}
				// 窗口最后一个位之后的间隙
				if (depth == 1 && elemStart < 0 && !inStr && hasNonWsScalar(d, checked, p + 8)) {
					return null;
				}
				p += 8;
			} else {
				// 尾部不足 8 字节，逐字节收尾
				byte b = d[p];
				if (inStr) {
					if (esc) {
						esc = false;
					} else if (b == '\\') {
						esc = true;
					} else if (b == '"') {
						inStr = false;
					}
				} else {
					switch (b) {
						case '"':
							if (depth == 1 && elemStart < 0) {
								return null;
							}
							inStr = true;
							break;
						case '{':
						case '[':
							depth++;
							if (depth == 2) {
								elemStart = p;
							}
							break;
						case '}':
						case ']':
							depth--;
							if (depth == 1) {
								if (elemStart < 0) {
									return null;
								}
								if (count + 2 > bounds.length) {
									int[] nb = new int[bounds.length << 1];
									System.arraycopy(bounds, 0, nb, 0, bounds.length);
									bounds = nb;
								}
								bounds[count++] = elemStart;
								bounds[count++] = p + 1;
								elemStart = -1;
							} else if (depth == 0) {
								return finish(bounds, count);
							}
							break;
						default:
							if (depth == 1 && elemStart < 0 && !isWs(b) && b != ',') {
								return null;
							}
					}
				}
				p++;
			}
		}
		return null; // 数组未闭合
	}

	private static int[] finish(int[] bounds, int count) {
		if (count < (MIN_PARALLEL_ELEMS << 1)) {
			return null;
		}
		int[] out = new int[count];
		System.arraycopy(bounds, 0, out, 0, count);
		return out;
	}

	private static boolean isWs(byte b) {
		return b == ' ' || b == '\t' || b == '\n' || b == '\r';
	}

	/** 深度1空白区扫描：8 字节窗口内出现非空白、非逗号字符即标量元素 */
	private static boolean hasNonWsScalar(byte[] d, int s, int e) {
		for (int i = s; i < e; i++) {
			byte b = d[i];
			if (!isWs(b) && b != ',') {
				return true;
			}
		}
		return false;
	}
}
