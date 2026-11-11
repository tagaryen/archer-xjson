package com.archer.xjson.asm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class XNWriter {

	static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
			ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
	static final DateTimeFormatter LOCAL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	static final DateTimeFormatter LOCAL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
	static final DateTimeFormatter LOCAL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private final StringBuilder sb;

	public XNWriter(StringBuilder sb) {
		this.sb = sb;
	}

	public StringBuilder raw() {
		return sb;
	}

	public void writeStartObject() {
		sb.append('{');
	}

	public void writeEndObject() {
		sb.append('}');
	}

	public void writeStartArray() {
		sb.append('[');
	}

	public void writeEndArray() {
		sb.append(']');
	}

	public void writeComma() {
		sb.append(',');
	}

	public void writeColon() {
		sb.append(':');
	}

	public void writeNull() {
		sb.append("null");
	}

	/** 输出 "name": 字段名，Java 标识符无需转义 */
	public void writeName(String name) {
		sb.append('"').append(name).append('"').append(':');
	}

	/** 输出 "key": map 的 key 需要转义 */
	public void writeKey(String key) {
		if (null == key) {
			sb.append("\"\":");
			return;
		}
		sb.append('"');
		escape(key);
		sb.append('"').append(':');
	}

	/** 原样写入已拼接好的静态片段（如 "\"name\":"），零处理开销 */
	public void writeRaw(String raw) {
		sb.append(raw);
	}

	public void writeString(String val) {
		if (null == val) {
			writeNull();
			return;
		}
		sb.append('"');
		escape(val);
		sb.append('"');
	}

	public void writeInt(int val) {
		sb.append(val);
	}

	public void writeInteger(Integer val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.intValue());
		}
	}

	public void writeLong(long val) {
		sb.append(val);
	}

	public void writeLongObj(Long val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.longValue());
		}
	}

	public void writeDouble(double val) {
		// 整数值 fast-path：数字直写缓冲，零分配；
		// 非整数回退 Double.toString（JDK8 最短表示，无零分配实现可用）
		if (val == Math.floor(val) && !Double.isInfinite(val) && Math.abs(val) < 1e15) {
			sb.append((long) val);
			sb.append(".0");
		} else {
			sb.append(val);
		}
	}

	public void writeDoubleObj(Double val) {
		if (null == val) {
			writeNull();
		} else {
			writeDouble(val.doubleValue());
		}
	}

	public void writeFloat(float val) {
		sb.append(val);
	}

	public void writeFloatObj(Float val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.floatValue());
		}
	}

	public void writeBool(boolean val) {
		sb.append(val);
	}

	public void writeBoolObj(Boolean val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.booleanValue());
		}
	}

	public void writeByte(byte val) {
		sb.append(val);
	}

	public void writeByteObj(Byte val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.byteValue());
		}
	}

	public void writeShort(short val) {
		sb.append(val);
	}

	public void writeShortObj(Short val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.shortValue());
		}
	}

	public void writeChar(char val) {
		sb.append('"');
		escapeChar(val);
		sb.append('"');
	}

	public void writeCharObj(Character val) {
		if (null == val) {
			writeNull();
		} else {
			writeChar(val.charValue());
		}
	}

	public void writeBigInteger(BigInteger val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.toString());
		}
	}

	public void writeBigDecimal(BigDecimal val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append(val.toString());
		}
	}

	public void writeDate(Date val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append('"').append(DATE_FORMAT.get().format(val)).append('"');
		}
	}

	public void writeLocalDate(LocalDate val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append('"').append(LOCAL_DATE_FORMAT.format(val)).append('"');
		}
	}

	public void writeLocalTime(LocalTime val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append('"').append(LOCAL_TIME_FORMAT.format(val)).append('"');
		}
	}

	public void writeLocalDateTime(LocalDateTime val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append('"').append(LOCAL_DATE_TIME_FORMAT.format(val)).append('"');
		}
	}

	public void writeEnum(Enum<?> val) {
		if (null == val) {
			writeNull();
		} else {
			sb.append('"').append(val.name()).append('"');
		}
	}

	private void escape(String val) {
		int len = val.length();
		int segStart = 0;
		for (int i = 0; i < len; i++) {
			char c = val.charAt(i);
			if (c == '"' || c == '\\' || c < 0x20) {
				// 先批量拷贝无需转义的段，再写转义序列
				sb.append(val, segStart, i);
				escapeChar(c);
				segStart = i + 1;
			}
		}
		if (segStart == 0) {
			// 全串无需转义（绝大多数情况）：一次整体拷贝
			sb.append(val);
		} else if (segStart < len) {
			sb.append(val, segStart, len);
		}
	}

	private void escapeChar(char c) {
		switch (c) {
			case '"': sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			case '\b': sb.append("\\b"); break;
			case '\f': sb.append("\\f"); break;
			default:
				if (c < 0x20) {
					sb.append("\\u00").append(HEX[(c >> 4) & 0xf]).append(HEX[c & 0xf]);
				} else {
					sb.append(c);
				}
		}
	}
}
