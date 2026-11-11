package com.archer.xjson.test;


/**
 * 参数定义：描述转换脚本支持的自定义参数
 */
public class ParamDef {
    private String paramName;
    private String enDescription;
    private String usDescription;
    private String type;
    private boolean required;
    private boolean readable = false;
    private String defaultValue;
    private String value;

    public String getParamName() {
        return paramName;
    }
    public String getEnDescription() {
        return enDescription;
    }
    public String getUsDescription() {
        return usDescription;
    }
    public String getType() {
        return type;
    }
    public boolean isRequired() {
        return required;
    }
    public boolean isReadable() {
        return readable;
    }
    public String getDefaultValue() {
        return defaultValue;
    }
    public String getValue() {
        return value;
    }
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }
    public void setEnDescription(String enDescription) {
        this.enDescription = enDescription;
    }
    public void setUsDescription(String usDescription) {
        this.usDescription = usDescription;
    }
    public void setType(String type) {
        this.type = type;
    }
    public void setRequired(boolean required) {
        this.required = required;
    }
    public void setReadable(boolean readable) {
        this.readable = readable;
    }
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    public void setValue(String value) {
        this.value = value;
    }
}