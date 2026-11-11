package com.archer.xjson.asm;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class Bytes {
	
    public static final int BUFFER_SIZE = 4 * 1024;
    
	private byte[] data;
	
	private volatile int read;
	
	private volatile int write;
	
	public Bytes() {
		this(BUFFER_SIZE);
	}
	
	public Bytes(int cap) {
		data = new byte[cap];
		read = 0;
		write = 0;
	}

	public Bytes(byte[] data) {
		this.data = data;
		read = 0;
		write = data.length;
	}
	
	public Bytes(byte[] data, int off, int len) {
		this.data = Arrays.copyOfRange(data, off, off + len);
		read = 0;
		write = len;
	}
	
	public int readPos() {
		return read;
	}

	public void readPos(int pos) {
		read = pos;
	}
	
	public int writePos() {
		return write;
	}

	public void writePos(int pos) {
		write = pos;
	}
	
	public int available() {
		return write - read;
	}
	
	public int cap() {
		return data.length;
	}
	
	public byte[] array() {
		return data;
	}
	
	public byte at(int index) {
		return data[read + index];
	}
	
	public byte[] copyOfRange(int from , int to) {
		if(from < 0 || to > write - read) {
			throw new IllegalArgumentException("index out of range, from = " + 
					from +", to = " + to);
		}
		return Arrays.copyOfRange(data, read + from, read + to);
	}
	
	public byte[] read(int len) {
		if(len > write - read) {
			throw new IllegalArgumentException("length out of range, max = " + 
						(write - read) +", provide = " + len);
		}
		byte[] ret = Arrays.copyOfRange(data, read, read+len);
		read = read + len;
		return ret;
	}
	
	public byte[] readAll() {
		return read(write - read);
	}
	
	public int read(byte[] out) {
		return read(out, 0, out.length);
	}
	
	public int read(byte[] out, int off, int len) {
		if(len > write - read) {
			throw new IllegalArgumentException("length out of range, max = " + 
						(write - read) +", provide = " + len);
		}
		if(off + len > out.length) {
			throw new IllegalArgumentException("length out of range " + len);
		}
		System.arraycopy(data, read, out, off, len);
		read = read + len;
		return len;
	}
	
	public int readInt8() {
		byte[] bytes = read(1);
    	int i0 = bytes[0];
    	i0 = i0 < 0 ? i0 + 256 : i0;
    	return i0;	
	}
	
	public int readInt16() {
		byte[] bytes = read(2);
    	int i0 = bytes[0], i1 = bytes[1];
    	i0 = i0 < 0 ? i0 + 256 : i0;
    	i1 = i1 < 0 ? i1 + 256 : i1;
    	return (i0 << 8) | i1;	
	}
	
	public int readInt24() {
		byte[] bytes = read(3);
    	int i0 = bytes[0], i1 = bytes[1], i2 = bytes[2];
    	i0 = i0 < 0 ? i0 + 256 : i0;
    	i1 = i1 < 0 ? i1 + 256 : i1;
    	i2 = i2 < 0 ? i2 + 256 : i2;
    	return (i0 << 16) | (i1 << 8) | i2;	
	}
	
	public int readInt32() {
		byte[] bytes = read(4);
    	int i0 = bytes[0], i1 = bytes[1], i2 = bytes[2], i3 = bytes[3];
    	i0 = i0 < 0 ? i0 + 256 : i0;
    	i1 = i1 < 0 ? i1 + 256 : i1;
    	i2 = i2 < 0 ? i2 + 256 : i2;
    	i3 = i3 < 0 ? i3 + 256 : i3;
    	return (i0 << 24) | (i1 << 16) | (i2 << 8) | i3;	
	}
	
	public void write(byte[] in) {
		write(in, 0, in.length);
	}
	
	public void write(byte[] in, int off, int len) {
		if(off + len > in.length) {
			throw new IllegalArgumentException("length out range, max = " + 
						(in.length - off) +", provide = " + len);
		}
		if(data.length - write + read < len) {
			int newLen = data.length << 1;
			while(newLen - write + read < len) {
				newLen <<= 1;
			}
			byte[] tmp = new byte[newLen];
			System.arraycopy(data, read, tmp, 0, write - read);
			data = tmp;
			write = write - read;
			read = 0;
		} else if(data.length - write < len) {
			System.arraycopy(data, read, data, 0, write - read);
			write = write - read;
			read = 0;
		}
		System.arraycopy(in, off, data, write, len);
		write += len;
	}
	
	public void writeInt8(int i) {
		write(new byte[] {(byte) i});
	}
	
	public void writeInt16(int i) {
		byte b0 = (byte) (i >> 8);
		byte b1 = (byte) i;
		write(new byte[] {b0, b1});
	}
	
	public void writeInt24(int i) {
		byte b0 = (byte) (i >> 16);
		byte b1 = (byte) (i >> 8);
		byte b2 = (byte) i;
		write(new byte[] {b0, b1, b2});
	}
	
	public void writeInt32(int i) {
		byte b0 = (byte) (i >> 24);
		byte b1 = (byte) (i >> 16);
		byte b2 = (byte) (i >> 8);
		byte b3 = (byte) i;
		write(new byte[] {b0, b1, b2, b3});
	}
	
	public void clear() {
		read = 0;
		write = 0;
	}
	
	
	public ByteBuffer toByteBuffer() {
		ByteBuffer buf = ByteBuffer.allocate(write-read);
		buf.put(data, read, write-read);
		return buf;
	}
	
	public int writeToBytes(Bytes in) {
		int len = available();
		if(len > 0) {
			in.write(readAll());
		}
		return len;
	}
	
	public int writeToBytes(Bytes in, int len) {
		int all = available();
		if(all > 0) {
			in.write(read(len));
		}
		return len;
	}
	
	public int readFromBytes(Bytes out) {
		int len = out.available();
		if(len > 0) {
			write(out.readAll());
		}
		return len;
	}
	
	public int readFromBytes(Bytes out, int len) {
		if(len < 0) {
			return 0;
		}
		write(out.read(len));
		return len;
	}
}
