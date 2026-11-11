package com.archer.xjson;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;


public class XMap<K, V> implements Map<K, V> {

	private static final int MIN_TABLE_CAP = 16;
	private static final float LOAD_FACTOR = 0.65f;

	private Object[] keys;
	private Object[] vals;
	/** 开放寻址哈希表，槽位值 = 顺序数组下标 + 1，0 表示空槽 */
	private int[] table;
	private int size;
	private int threshold;

	public XMap() {
		this(8);
	}

	public XMap(int cap) {
		if(cap <= 0) {
			throw new IllegalArgumentException("cap must be bigger than zero");
		}
		int tableCap = MIN_TABLE_CAP;
		while((int)(tableCap * LOAD_FACTOR) < cap) {
			tableCap <<= 1;
		}
		int entryCap = (int)(tableCap * LOAD_FACTOR);
		keys = new Object[entryCap];
		vals = new Object[entryCap];
		table = new int[tableCap];
		threshold = entryCap;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return size == 0;
	}

	@Override
	public boolean containsKey(Object key) {
		return key != null && findIndex(key) >= 0;
	}

	@Override
	public boolean containsValue(Object value) {
		for(int i = 0; i < size; i++) {
			if(value == null ? vals[i] == null : value.equals(vals[i])) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		if(key == null) {
			return null;
		}
		int i = findIndex(key);
		return i < 0 ? null : (V) vals[i];
	}

	@SuppressWarnings("unchecked")
	@Override
	public V put(K key, V value) {
		if(key == null) {
			throw new NullPointerException();
		}
		if(size + 1 > threshold) {
			resize();
		}
		int mask = table.length - 1;
		int pos = spread(key.hashCode()) & mask;
		for(;;) {
			int e = table[pos];
			if(e == 0) {
				keys[size] = key;
				vals[size] = value;
				table[pos] = size + 1;
				size++;
				return null;
			}
			int i = e - 1;
			if(keys[i].equals(key)) {
				V old = (V) vals[i];
				vals[i] = value;
				return old;
			}
			pos = (pos + 1) & mask;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public V remove(Object key) {
		if(key == null) {
			return null;
		}
		int i = findIndex(key);
		if(i < 0) {
			return null;
		}
		V old = (V) vals[i];
		int moved = size - i - 1;
		if(moved > 0) {
			System.arraycopy(keys, i + 1, keys, i, moved);
			System.arraycopy(vals, i + 1, vals, i, moved);
		}
		size--;
		// 清空尾部引用，避免内存泄漏，并重建哈希索引
		keys[size] = null;
		vals[size] = null;
		rebuildTable();
		return old;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for(Entry<? extends K, ? extends V> en : m.entrySet()) {
			put(en.getKey(), en.getValue());
		}
	}

	@Override
	public void clear() {
		for(int i = 0; i < size; i++) {
			keys[i] = null;
			vals[i] = null;
		}
		size = 0;
		Arrays.fill(table, 0);
	}

	@Override
	public Set<K> keySet() {
		return new KeySetView();
	}

	@Override
	public Collection<V> values() {
		return new ValuesView();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return new EntrySetView();
	}

	@SuppressWarnings("unchecked")
	public Map.Entry<K, V> getEntryByIndex(int index) {
		if(index >= size || index < 0) {
			return null;
		}
		return new Pair((K) keys[index], (V) vals[index]);
	}

	@SuppressWarnings("unchecked")
	public K getKeyByIndex(int index) {
		if(index >= size || index < 0) {
			return null;
		}
		return (K) keys[index];
	}

	@SuppressWarnings("unchecked")
	public V getValueByIndex(int index) {
		if(index >= size || index < 0) {
			return null;
		}
		return (V) vals[index];
	}

	@Override
	public boolean equals(Object o) {
		if(o == this) {
			return true;
		}
		if(!(o instanceof Map)) {
			return false;
		}
		Map<?, ?> m = (Map<?, ?>) o;
		if(m.size() != size) {
			return false;
		}
		for(int i = 0; i < size; i++) {
			Object v = vals[i];
			if(null == v) {
				if(!(null == m.get(keys[i]) && m.containsKey(keys[i]))) {
					return false;
				}
			} else if(!v.equals(m.get(keys[i]))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int h = 0;
		for(int i = 0; i < size; i++) {
			h += (null == keys[i] ? 0 : keys[i].hashCode()) ^ (null == vals[i] ? 0 : vals[i].hashCode());
		}
		return h;
	}

	private int findIndex(Object key) {
		int mask = table.length - 1;
		int pos = spread(key.hashCode()) & mask;
		for(;;) {
			int e = table[pos];
			if(e == 0) {
				return -1;
			}
			int i = e - 1;
			if(keys[i].equals(key)) {
				return i;
			}
			pos = (pos + 1) & mask;
		}
	}

	private static int spread(int h) {
		return h ^ (h >>> 16);
	}

	/** 统一扩容：entries 数组与哈希表同步翻倍，只做一次 rehash */
	private void resize() {
		int newTableCap = table.length << 1;
		int[] newTable = new int[newTableCap];
		int mask = newTableCap - 1;
		for(int i = 0; i < size; i++) {
			int pos = spread(keys[i].hashCode()) & mask;
			while(newTable[pos] != 0) {
				pos = (pos + 1) & mask;
			}
			newTable[pos] = i + 1;
		}
		table = newTable;
		int newEntryCap = (int)(newTableCap * LOAD_FACTOR);
		keys = Arrays.copyOf(keys, newEntryCap);
		vals = Arrays.copyOf(vals, newEntryCap);
		threshold = newEntryCap;
	}

	private void rebuildTable() {
		Arrays.fill(table, 0);
		int mask = table.length - 1;
		for(int i = 0; i < size; i++) {
			int pos = spread(keys[i].hashCode()) & mask;
			while(table[pos] != 0) {
				pos = (pos + 1) & mask;
			}
			table[pos] = i + 1;
		}
	}

	final class KeySetView implements Set<K> {

		@Override
		public int size() {
			return size;
		}

		@Override
		public boolean isEmpty() {
			return size == 0;
		}

		@Override
		public boolean contains(Object o) {
			return containsKey(o);
		}

		@Override
		public Iterator<K> iterator() {
			return new KeyIterator();
		}

		@Override
		public Object[] toArray() {
			return Arrays.copyOf(keys, size);
		}

		@Override
		public <T> T[] toArray(T[] a) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean add(K e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends K> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	final class ValuesView implements Collection<V> {

		@Override
		public int size() {
			return size;
		}

		@Override
		public boolean isEmpty() {
			return size == 0;
		}

		@Override
		public boolean contains(Object o) {
			return containsValue(o);
		}

		@Override
		public Iterator<V> iterator() {
			return new ValueIterator();
		}

		@Override
		public Object[] toArray() {
			return Arrays.copyOf(vals, size);
		}

		@Override
		public <T> T[] toArray(T[] a) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean add(V e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	final class EntrySetView implements Set<Map.Entry<K, V>> {

		@Override
		public int size() {
			return size;
		}

		@Override
		public boolean isEmpty() {
			return size == 0;
		}

		@Override
		public boolean contains(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterator<Map.Entry<K, V>> iterator() {
			return new EntryIterator();
		}

		@Override
		public Object[] toArray() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean add(Entry<K, V> e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends Entry<K, V>> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	final class KeyIterator implements Iterator<K> {

		private int i = 0;

		@Override
		public boolean hasNext() {
			return i < size;
		}

		@SuppressWarnings("unchecked")
		@Override
		public K next() {
			if(i >= size) {
				throw new NoSuchElementException();
			}
			return (K) keys[i++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	final class ValueIterator implements Iterator<V> {

		private int i = 0;

		@Override
		public boolean hasNext() {
			return i < size;
		}

		@SuppressWarnings("unchecked")
		@Override
		public V next() {
			if(i >= size) {
				throw new NoSuchElementException();
			}
			return (V) vals[i++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	final class EntryIterator implements Iterator<Map.Entry<K, V>> {

		private int i = 0;

		@Override
		public boolean hasNext() {
			return i < size;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Map.Entry<K, V> next() {
			if(i >= size) {
				throw new NoSuchElementException();
			}
			return new Pair((K) keys[i], (V) vals[i++]);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	final class Pair implements Map.Entry<K, V> {

		private final K k;
		private final V v;

		Pair(K k, V v) {
			this.k = k;
			this.v = v;
		}

		@Override
		public K getKey() {
			return k;
		}

		@Override
		public V getValue() {
			return v;
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException();
		}
	}
}
