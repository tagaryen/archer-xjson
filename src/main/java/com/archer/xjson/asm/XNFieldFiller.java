package com.archer.xjson.asm;

public interface XNFieldFiller {

	/** 返回 true 表示已处理（无论值是否实际写入）；返回 false 表示未匹配该字段名，需调用方跳过值 */
	boolean fill(Object bean, String field, XNReader reader);

	/** 创建目标实体实例：生成类直发 new 指令，避免反射与参数数组分配 */
	Object create();

	Class<?> type();
}