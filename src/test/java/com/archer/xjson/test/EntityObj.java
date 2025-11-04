package com.archer.xjson.test;

import java.util.Arrays;
import java.util.List;

public class EntityObj {
	private String name;
	
	private Integer age;
	
	private Double income;
	
	private List<String> children;
	
	
	private TypeEnum type;

	public EntityObj(String name, Integer age) {
		this.name= name;
	}
	
//	public EntityObj() {
//		this.name = "你好";
//		this.age = 18;
//		this.income = 72687132.222D;
//		this.children = Arrays.asList("儿子1", "儿子2");
//		this.type = TypeEnum.TYPE0;
//	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getAge() {
		return age;
	}

	public void setAge(Integer age) {
		this.age = age;
	}

	public Double getIncome() {
		return income;
	}

	public void setIncome(Double income) {
		this.income = income;
	}

	public List<String> getChildren() {
		return children;
	}

	public void setChildren(List<String> children) {
		this.children = children;
	}

	public TypeEnum getType() {
		return type;
	}

	public void setType(TypeEnum type) {
		this.type = type;
	}
	
	
}
