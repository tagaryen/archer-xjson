package com.archer.xjson.test;

/**
 * 与 E:/projects/large.json 顶层数组元素结构对齐的实体。
 * 字段名与 JSON key 完全一致（XJSON 直接字段访问 / XNJSON 按 set+capitalize 找 setter），
 * 嵌套对象 meta / body / error 以 public static 嵌套类承载。
 */
public class LogEntry {

	private String _id;

	private long timestamp;

	private String level;

	private String message;

	private String service;

	private String host;

	private long pid;

	private Meta meta;

	private String req_id;

	private String method;

	private String path;

	private int status_code;

	private long duration_ms;

	private Body body;

	private long user_id;

	private ErrorInfo error;

	public LogEntry() {
	}

	// "_id" 首字符非小写字母，capitalize 后不变，故访问器名为 get_id / set_id
	public String get_id() {
		return _id;
	}

	public void set_id(String _id) {
		this._id = _id;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getLevel() {
		return level;
	}

	public void setLevel(String level) {
		this.level = level;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getService() {
		return service;
	}

	public void setService(String service) {
		this.service = service;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public long getPid() {
		return pid;
	}

	public void setPid(long pid) {
		this.pid = pid;
	}

	public Meta getMeta() {
		return meta;
	}

	public void setMeta(Meta meta) {
		this.meta = meta;
	}

	public String getReq_id() {
		return req_id;
	}

	public void setReq_id(String req_id) {
		this.req_id = req_id;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getStatus_code() {
		return status_code;
	}

	public void setStatus_code(int status_code) {
		this.status_code = status_code;
	}

	public long getDuration_ms() {
		return duration_ms;
	}

	public void setDuration_ms(long duration_ms) {
		this.duration_ms = duration_ms;
	}

	public Body getBody() {
		return body;
	}

	public void setBody(Body body) {
		this.body = body;
	}

	public long getUser_id() {
		return user_id;
	}

	public void setUser_id(long user_id) {
		this.user_id = user_id;
	}

	public ErrorInfo getError() {
		return error;
	}

	public void setError(ErrorInfo error) {
		this.error = error;
	}

	/** meta 嵌套对象：custom_field_* 在部分元素中缺省，保持默认值即可 */
	public static class Meta {

		private String version;

		private String environment;

		private String region;

		private String custom_field_1;

		private long custom_field_2;

		private boolean custom_field_3;

		private String theme_color;

		public Meta() {
		}

		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		public String getEnvironment() {
			return environment;
		}

		public void setEnvironment(String environment) {
			this.environment = environment;
		}

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public String getCustom_field_1() {
			return custom_field_1;
		}

		public void setCustom_field_1(String custom_field_1) {
			this.custom_field_1 = custom_field_1;
		}

		public long getCustom_field_2() {
			return custom_field_2;
		}

		public void setCustom_field_2(long custom_field_2) {
			this.custom_field_2 = custom_field_2;
		}

		public boolean isCustom_field_3() {
			return custom_field_3;
		}

		public void setCustom_field_3(boolean custom_field_3) {
			this.custom_field_3 = custom_field_3;
		}

		public String getTheme_color() {
			return theme_color;
		}

		public void setTheme_color(String theme_color) {
			this.theme_color = theme_color;
		}
	}

	/** body 嵌套对象：仅部分元素携带 */
	public static class Body {

		private String name;

		private double value;

		public Body() {
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public double getValue() {
			return value;
		}

		public void setValue(double value) {
			this.value = value;
		}
	}

	/** error 嵌套对象：仅部分元素携带，stack 可缺省 */
	public static class ErrorInfo {

		private String name;

		private String message;

		private String stack;

		public ErrorInfo() {
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getStack() {
			return stack;
		}

		public void setStack(String stack) {
			this.stack = stack;
		}
	}
}
