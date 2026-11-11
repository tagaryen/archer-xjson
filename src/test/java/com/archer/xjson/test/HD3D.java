package com.archer.xjson.test;

// import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 与 E:/projects/hd3D.json 顶层数组元素结构对齐的实体。
 * 字段名与 JSON key 完全一致（XJSON / XNJSON 均按精确字段名匹配），
 * 对 getter/setter 显式标注 @JsonProperty 以让 Jackson 正确映射。
 */
public class HD3D {

	private Long Crossline;

	private double X;

	private double Y;

	private String Survey_Name;

	private double Z;

	private Long Inline;

	private String Strata_Name;

	public HD3D() {
	}

	// @JsonProperty("Crossline")
	public Long getCrossline() {
		return Crossline;
	}

	// @JsonProperty("Crossline")
	public void setCrossline(Long crossline) {
		this.Crossline = crossline;
	}

	// @JsonProperty("X")
	public double getX() {
		return X;
	}

	// @JsonProperty("X")
	public void setX(double x) {
		this.X = x;
	}

	// @JsonProperty("Y")
	public double getY() {
		return Y;
	}

	// @JsonProperty("Y")
	public void setY(double y) {
		this.Y = y;
	}

	// @JsonProperty("Survey_Name")
	public String getSurvey_Name() {
		return Survey_Name;
	}

	// @JsonProperty("Survey_Name")
	public void setSurvey_Name(String survey_Name) {
		this.Survey_Name = survey_Name;
	}

	// @JsonProperty("Z")
	public double getZ() {
		return Z;
	}

	// @JsonProperty("Z")
	public void setZ(double z) {
		this.Z = z;
	}

	// @JsonProperty("Inline")
	public Long getInline() {
		return Inline;
	}

	// @JsonProperty("Inline")
	public void setInline(Long inline) {
		this.Inline = inline;
	}

	// @JsonProperty("Strata_Name")
	public String getStrata_Name() {
		return Strata_Name;
	}

	// @JsonProperty("Strata_Name")
	public void setStrata_Name(String strata_Name) {
		this.Strata_Name = strata_Name;
	}
}