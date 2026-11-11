package com.archer.xjson.asm;


import com.archer.xjson.asm.AttributeInfo.CodeAttribute;
import com.archer.xjson.asm.AttributeInfo.ExceptionAttribute;
import com.archer.xjson.asm.AttributeInfo.ExceptionTable;
import com.archer.xjson.asm.AttributeInfo.LineNumAttribute;
import com.archer.xjson.asm.AttributeInfo.LocalVarAttribute;
import com.archer.xjson.asm.constantpool.ConstantInfo;
import com.archer.xjson.asm.constantpool.ConstantPool;
import com.archer.xjson.asm.constantpool.ConstantUtf8;


public class MemberInfo {
	private boolean isField;
    private int accessFlags;
    private int nameIndex;
    private int descriptorIndex;
    private int attributesCount;
    
    private AttributeInfo[] attributes;
    

    private String name;
    private String desc;
    
    public MemberInfo(boolean isField) {
    	this.isField = isField;
    }
    
    public boolean isField() {
    	return isField;
    }
    
    public int getAccessFlags() {
		return accessFlags;
	}

	public int getNameIndex() {
		return nameIndex;
	}

	public int getDescriptorIndex() {
		return descriptorIndex;
	}

	public int getAttributesCount() {
		return attributesCount;
	}

	public AttributeInfo[] getAttributes() {
		return attributes;
	}

	public String getName() {
		return name;
	}

	public String getDesc() {
		return desc;
	}

	public void setAccessFlags(int accessFlags) {
		this.accessFlags = accessFlags;
	}

	public void setNameIndex(int nameIndex) {
		this.nameIndex = nameIndex;
	}

	public void setDescriptorIndex(int descriptorIndex) {
		this.descriptorIndex = descriptorIndex;
	}

	public void setAttributes(AttributeInfo[] attributes) {
		this.attributesCount = attributes.length;
		this.attributes = attributes;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

    public void read(Bytes bytes, ConstantPool pool) {
    	accessFlags = bytes.readInt16();
    	nameIndex = bytes.readInt16();
    	descriptorIndex = bytes.readInt16();
    	attributesCount = bytes.readInt16();
    	attributes = new AttributeInfo[attributesCount];
    	
    	ConstantInfo[] cpInfo = pool.getCpInfo();
    	
    	this.name = ((ConstantUtf8) cpInfo[nameIndex]).getValue();
    	this.desc = ((ConstantUtf8) cpInfo[descriptorIndex]).getValue();
    	
		for (int j = 0; j < attributes.length; j++) {
    		int nameIndex = bytes.readInt16();
    		int length = bytes.readInt32();
            byte[] info = bytes.read(length);
    		String name = ((ConstantUtf8) cpInfo[nameIndex]).getValue();
    		AttributeInfo attr;
    		if("Code".equals(name)) {
    			attr = new CodeAttribute(nameIndex, length, info);
    		} else if("LineNumberTable".equals(name)) {
    			attr = new LineNumAttribute(nameIndex, length, info);
    		} else if("LocalVariableTable".equals(name)) {
    			attr = new LocalVarAttribute(nameIndex, length, info);
    		} else if("Exceptions".equals(name)) {
    			attr = new ExceptionAttribute(nameIndex, length, info);
    		} else {
    			attr = new AttributeInfo(nameIndex, length, info);
    		}
			attr.setName(name);
			attributes[j] = attr;
		}
    }

    public void writeInto(Bytes bytes) {
    	bytes.writeInt16(accessFlags);
    	bytes.writeInt16(nameIndex);
    	bytes.writeInt16(descriptorIndex);
    	bytes.writeInt16(attributesCount);

		for (int j = 0; j < attributes.length; j++) {
			AttributeInfo attr = attributes[j];
			bytes.writeInt16(attr.getNameIndex());
			bytes.writeInt32(attr.getLength());
			bytes.write(attr.getInfo());
        }
    }

    /**
     * @deprecated use read(Bytes, ConstantPool) instead
     * */
    @Deprecated
    public void read(Bytes bytes, ConstantInfo[] cpInfo) {
    	accessFlags = bytes.readInt16();
    	nameIndex = bytes.readInt16();
    	descriptorIndex = bytes.readInt16();
    	attributesCount = bytes.readInt16();
    	attributes = new AttributeInfo[attributesCount];
    	
    	this.name = ((ConstantUtf8) cpInfo[nameIndex]).getValue();
    	this.desc = ((ConstantUtf8) cpInfo[descriptorIndex]).getValue();

    	parseAttributes(bytes, attributes, cpInfo);
    }

    @Deprecated
	private void parseAttributes(Bytes input, AttributeInfo[] attributes, ConstantInfo[] cpInfo) {
		for (int j = 0; j < attributes.length; j++) {
    		int nameIndex = input.readInt16();
    		int length = input.readInt32();
            byte[] info = input.read(length);
            Bytes bytes = new Bytes(info);
    		String name = ((ConstantUtf8) cpInfo[nameIndex]).getValue();
    		if("Code".equals(name)) {
    			CodeAttribute attr = new CodeAttribute(nameIndex, length, info);
    			attr.setName(name);
    			
    			attr.setMaxStack(bytes.readInt16()); //2bytes
    			attr.setMaxLocals(bytes.readInt16()); //2bytes
    			int codeLength = bytes.readInt32();   //4bytes
    			attr.setCode(bytes.read(codeLength)); //length bytes
    			
    			int exceptionTableLength = bytes.readInt16();    // 2bytes
    			ExceptionTable[] exceptions = new ExceptionTable[exceptionTableLength];
                for (int i = 0; i < exceptionTableLength; i++) {
                    ExceptionTable e = new ExceptionTable();
                    e.startPc = bytes.readInt16();
                    e.endPc = bytes.readInt16();
                    e.handlerPc = bytes.readInt16();
                    e.catchType = bytes.readInt16();
                    exceptions[i] = e;
                }
                
    			int attrCount = bytes.readInt16(); //2bytes
    			attr.setAttributes(new AttributeInfo[attrCount]);
    			parseAttributes(bytes, attr.getAttributes(), cpInfo);
    			
    			attributes[j] = attr;
    		} else if("LineNumberTable".equals(name)) {
    			LineNumAttribute attr = new LineNumAttribute(nameIndex, length, info);
    			attr.setName(name);
    			
    			attr.setLineNumTableCount(bytes.readInt16());
    			int[][] lineNumTable = new int[attr.getLineNumTableCount()][2];
    			attr.setLineNumTable(lineNumTable);
    			for(int i = 0; i < attr.getLineNumTableCount(); i++) {
    				lineNumTable[i][0] = bytes.readInt16();
    				lineNumTable[i][1] = bytes.readInt16();
    			}
    			attributes[j] = attr;
    		} else if("LocalVariableTable".equals(name)) {
    			LocalVarAttribute attr = new LocalVarAttribute(nameIndex, length, info);
    			attr.setName(name);
    			
    			attr.setLocalVarTableCount(bytes.readInt16());
    			int[][] localVarTable = new int[attr.getLocalVarTableCount()][5];
    			attr.setLocalVarTable(localVarTable);
    			for(int i = 0; i < attr.getLocalVarTableCount(); i++) {
    				localVarTable[i][0] = bytes.readInt16();
    				localVarTable[i][1] = bytes.readInt16();
    				localVarTable[i][2] = bytes.readInt16();
    				localVarTable[i][3] = bytes.readInt16();
    				localVarTable[i][4] = bytes.readInt16();
    			}
    			attributes[j] = attr;
    		} else {
    			AttributeInfo attr = new AttributeInfo(nameIndex, length, info);
    			attr.setName(name);
    			attributes[j] = attr;
    		}
        }
	}
}
