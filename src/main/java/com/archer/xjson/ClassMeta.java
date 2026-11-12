package com.archer.xjson;

import java.lang.reflect.Field;


public class ClassMeta {

	public final Class<?> cls;

	public final Field[] fields;

	/** 与 fields 对齐：预拼接的 "\"name\":" 输出片段 */
	public final String[] keys;

	/** 与 fields 对齐：字段声明类型 */
	public final Class<?>[] types;

	/** 字段名 → fields 下标的哈希索引（int 直返、缓存 hash/len，替代泛型 Map 装箱探测） */
	public final FieldIndex index;

	ClassMeta(Class<?> cls, Field[] fields) {
		this.cls = cls;
		this.fields = fields;
		this.keys = new String[fields.length];
		this.types = new Class<?>[fields.length];
		String[] names = new String[fields.length];
		int[] idxs = new int[fields.length];
		for (int i = 0; i < fields.length; i++) {
			this.keys[i] = "\"" + fields[i].getName() + "\":";
			this.types[i] = fields[i].getType();
			names[i] = fields[i].getName();
			idxs[i] = i;
		}
		this.index = new FieldIndex(names, idxs);
	}

	/**
	 * 字段名分派索引：开放寻址 + 缓存 hash/length，先比较缓存值再逐字符 equals，
	 * 命中直接返回 int 下标（-1 表示不存在），消除 Integer 装箱与 XMap 通用探测开销。
	 * 与 fields 对齐的去重语义：重复字段名首个下标生效。
	 */
	static final class FieldIndex {
		private final String[] keys;
		private final int[] vals;
		private final int[] hashes;
		private final int[] lens;
		private final int[] table;

		FieldIndex(String[] names, int[] idxs) {
			int n = names.length;
			int cap = 16;
			while(cap < n * 2) {
				cap <<= 1;
			}
			keys = new String[n];
			vals = new int[n];
			hashes = new int[n];
			lens = new int[n];
			table = new int[cap];
			int size = 0;
			int mask = cap - 1;
			for(int i = 0; i < n; i++) {
				String name = names[i];
				int h = name.hashCode();
				int l = name.length();
				int pos = (h ^ (h >>> 16)) & mask;
				for(;;) {
					int e = table[pos];
					if(e == 0) {
						keys[size] = name;
						vals[size] = idxs[i];
						hashes[size] = h;
						lens[size] = l;
						table[pos] = size + 1;
						size++;
						break;
					}
					int k = e - 1;
					if(hashes[k] == h && lens[k] == l && keys[k].equals(name)) {
						break;
					}
					pos = (pos + 1) & mask;
				}
			}
		}

		int get(String key) {
			int h = key.hashCode();
			int l = key.length();
			int mask = table.length - 1;
			int pos = (h ^ (h >>> 16)) & mask;
			for(;;) {
				int e = table[pos];
				if(e == 0) {
					return -1;
				}
				int k = e - 1;
				if(hashes[k] == h && lens[k] == l && keys[k].equals(key)) {
					return vals[k];
				}
				pos = (pos + 1) & mask;
			}
		}
	}
}
