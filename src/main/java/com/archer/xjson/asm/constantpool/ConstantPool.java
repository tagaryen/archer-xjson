package com.archer.xjson.asm.constantpool;

import com.archer.xjson.asm.Bytes;
import com.archer.xjson.asm.BytecodeException;
import com.archer.xjson.asm.ClassBytecode;
import com.archer.xjson.asm.util.DescriptorUtil;

public class ConstantPool {
	
	private static final String SOURCE_FILE = "SourceFile";
	
	private ConstantInfo[] cpInfo;

    public ConstantPool() {
    	this(new ConstantInfo[1]);
    }

    public ConstantPool(ConstantInfo[] cpInfo) {
		this.cpInfo = cpInfo;
	}

	public int getConstantPoolCount() {
		return cpInfo.length;
	}

	public ConstantInfo[] getCpInfo() {
		return cpInfo;
	}

	public void setCpInfo(ConstantInfo[] cpInfo) {
		this.cpInfo = cpInfo;
	}



	public void read(Bytes bytes) {
        int constantPoolCount = bytes.readInt16();
        cpInfo = new ConstantInfo[constantPoolCount];
        for (int i = 1; i < constantPoolCount; i++) {
            int tag = bytes.readInt8();
            ConstantInfo constantInfo = ConstantInfo.getConstantInfo(tag);
            constantInfo.read(bytes);
            cpInfo[i] = constantInfo;
    		if(ClassBytecode.debugMode()) {
				System.out.println("#"+i + "=" + ConstantInfo.ConstantPoolNameIndex[cpInfo[i].tag]);
    		}
            if (tag == ConstantInfo.CONSTANT_Double || tag == ConstantInfo.CONSTANT_Long) {
                i++;
            }
        }
    }
    
    public void writeInto(Bytes bytes) {
    	bytes.writeInt16(cpInfo.length);
        for (int i = 1; i < cpInfo.length; i++) {
            ConstantInfo constantInfo = cpInfo[i];
            int tag = constantInfo.tag;
        	bytes.writeInt8(constantInfo.tag);
            constantInfo.write(bytes);
            if (tag == ConstantInfo.CONSTANT_Double || tag == ConstantInfo.CONSTANT_Long) {
                i++;
            }
        }
    }
    
    public int addName(String name) {
    	int idx = findName(name);
    	if(idx != 0) {
    		return idx;
    	}

    	ConstantInfo[] newCps = new ConstantInfo[cpInfo.length + 1];
    	
    	int sourceIndex = findSourceIndex();
    	
    	System.arraycopy(cpInfo, 0, newCps, 0, sourceIndex);
    	System.arraycopy(cpInfo, sourceIndex, newCps, sourceIndex + 1, cpInfo.length - sourceIndex);

    	ConstantUtf8 nameUtf8 = new ConstantUtf8();
    	nameUtf8.setValue(name);
    	newCps[sourceIndex] = nameUtf8;
    	
    	cpInfo = newCps;
    	
    	return sourceIndex;
    }
    
    public int addField(String name, Class<?> type) {
    	return addField(name, DescriptorUtil.getClassDescription(type));
    }
    

    public int addField(String name, String typeDesc) {
    	int idx = findField(name, typeDesc);
    	if(idx != 0) {
    		return idx;
    	}
    	int off = 0, fieldIndex = 0;
    	ConstantInfo[] appends = new ConstantInfo[4];
    	
    	int sourceIndex = findSourceIndex();
    	
    	int nameIndex = findName(name);
    	if(nameIndex == 0) {
    		nameIndex = sourceIndex + off;
        	ConstantUtf8 nameUtf8 = new ConstantUtf8();
        	nameUtf8.setValue(name);
        	appends[off++] = nameUtf8;
    	}
    	
    	int descIndex = findName(typeDesc);
    	if(descIndex == 0) {
    		descIndex = sourceIndex + off;
        	ConstantUtf8 typeUtf8 = new ConstantUtf8();
        	typeUtf8.setValue(typeDesc);
        	appends[off++] = typeUtf8;
    	}

    	fieldIndex = sourceIndex + off;
    	ConstantMemberRef member = new ConstantMemberRef(ConstantInfo.CONSTANT_Fieldref);
    	member.setClassIndex(1);
    	member.setNameAndTypeIndex(fieldIndex + 1);
    	appends[off++] = member;
    	
    	ConstantNameAndType nameType = new ConstantNameAndType();
    	nameType.setNameIndex(nameIndex);
    	nameType.setDescIndex(descIndex);
    	appends[off++] = nameType;
    	
    	ConstantInfo[] newCpInfos = new ConstantInfo[cpInfo.length + off];
    	System.arraycopy(cpInfo, 0, newCpInfos, 0, sourceIndex);
    	System.arraycopy(appends, 0, newCpInfos, sourceIndex, off);
    	System.arraycopy(cpInfo, sourceIndex, newCpInfos, sourceIndex + off, cpInfo.length - sourceIndex);
    	
    	cpInfo = newCpInfos;
    	
    	return fieldIndex;
    }
    
    public int addConstructor(Class<?>[] params, Class<?> refClass) {
    	String[] paramDesces = null;
    	if(params != null) {
    		paramDesces = new String[params.length];
    		for(int i = 0; i < params.length; i++) {
    			paramDesces[i] = DescriptorUtil.getClassDescription(params[i]);
    		}
    	}
    	String refClassDesc = DescriptorUtil.replaceDot2Slash(refClass.getName());
    	return addMethod("<init>", paramDesces, "V", refClassDesc);
    }
    
    public int addConstructor(String[] paramDesces, String refClassName) {
    	return addMethod("<init>", paramDesces, "V", refClassName);
    }

    public int addConstructor(String constructorDesc, String refClassName) {
    	return addMethod("<init>", constructorDesc, refClassName);
    }
    
    public int addMethod(String name, Class<?>[] params, Class<?> returnType, Class<?> refClass) {
    	String[] paramDesces = null;
    	if(params != null) {
    		paramDesces = new String[params.length];
    		for(int i = 0; i < params.length; i++) {
    			paramDesces[i] = DescriptorUtil.getClassDescription(params[i]);
    		}
    	}
    	String returnTypeDesc = null;
    	if(returnType != null) {
    		returnTypeDesc = DescriptorUtil.getClassDescription(returnType);
    	}
    	String refClassDesc = DescriptorUtil.replaceDot2Slash(refClass.getName());
    	return addMethod(name, paramDesces, returnTypeDesc, refClassDesc);
    }
    
    public int addMethod(String name, String[] paramDesces, String returnTypeDesc, String refClassName) {
    	String desc = DescriptorUtil.getMethodDescription(paramDesces, returnTypeDesc);
    	return addMethod(name, desc, refClassName);
    }
    
    public int addMethod(String name, String methodDesc, String refClassName) {
    	int methodIndex = findMethod(name, methodDesc, refClassName);
    	if(methodIndex != 0) {
    		return methodIndex;
    	}
    	int off = 0, theMethodIndex = 0;
    	ConstantInfo[] appends = new ConstantInfo[16];

    	int firstIndex = findSourceIndex();
    	
    	int nameIndex = findName(name);
    	if(nameIndex == 0) {
    		nameIndex = firstIndex + off;

        	ConstantUtf8 typeUtf8 = new ConstantUtf8();
        	typeUtf8.setValue(name);
        	appends[off++] = typeUtf8;
    	}
    	
    	int descIndex = findName(methodDesc);
    	if(descIndex == 0) {
    		descIndex = firstIndex + off;
        	ConstantUtf8 typeUtf8 = new ConstantUtf8();
        	typeUtf8.setValue(methodDesc);
        	appends[off++] = typeUtf8;
    	}

    	theMethodIndex = firstIndex + off;
    	ConstantMemberRef member = new ConstantMemberRef(ConstantInfo.CONSTANT_Methodref);
    	member.setNameAndTypeIndex(theMethodIndex + 1);
    	appends[off++] = member;
    	
    	ConstantNameAndType nameType = new ConstantNameAndType();
    	nameType.setNameIndex(nameIndex);
    	nameType.setDescIndex(descIndex);
    	appends[off++] = nameType;

    	int refClassIndex = findClass(refClassName);
    	if(refClassIndex == 0) {
        	member.setClassIndex(firstIndex + off);
        	ConstantClass refClassCon = new ConstantClass();
        	appends[off++] = refClassCon;
        	int refClassNameIndex = findName(refClassName);
        	if(refClassNameIndex == 0) {
        		refClassCon.setNameIndex(firstIndex + off);
            	ConstantUtf8 refClassNameUtf8 = new ConstantUtf8();
            	refClassNameUtf8.setValue(refClassName);
            	appends[off++] = refClassNameUtf8;
        	} else {
        		refClassCon.setNameIndex(refClassNameIndex);
        	}
        	
    	} else {
        	member.setClassIndex(refClassIndex);
    	}
    	
    	ConstantInfo[] newCpInfos = new ConstantInfo[cpInfo.length + off];
    	System.arraycopy(cpInfo, 0, newCpInfos, 0, firstIndex);
    	System.arraycopy(appends, 0, newCpInfos, firstIndex, off);
    	System.arraycopy(cpInfo, firstIndex, newCpInfos, firstIndex + off, cpInfo.length - firstIndex);

    	cpInfo = newCpInfos;

    	return theMethodIndex;
    }

    /** 生成 CONSTANT_InterfaceMethodref（tag 11），供 invokeinterface 使用 */
    public int addInterfaceMethod(String name, String methodDesc, String refClassName) {
    	int methodIndex = findInterfaceMethod(name, methodDesc, refClassName);
    	if(methodIndex != 0) {
    		return methodIndex;
    	}
    	int off = 0, theMethodIndex = 0;
    	ConstantInfo[] appends = new ConstantInfo[16];

    	int firstIndex = findSourceIndex();

    	int nameIndex = findName(name);
    	if(nameIndex == 0) {
    		nameIndex = firstIndex + off;

        	ConstantUtf8 typeUtf8 = new ConstantUtf8();
        	typeUtf8.setValue(name);
        	appends[off++] = typeUtf8;
    	}

    	int descIndex = findName(methodDesc);
    	if(descIndex == 0) {
    		descIndex = firstIndex + off;
        	ConstantUtf8 typeUtf8 = new ConstantUtf8();
        	typeUtf8.setValue(methodDesc);
        	appends[off++] = typeUtf8;
    	}

    	theMethodIndex = firstIndex + off;
    	ConstantMemberRef member = new ConstantMemberRef(ConstantInfo.CONSTANT_InterfaceMethodref);
    	member.setNameAndTypeIndex(theMethodIndex + 1);
    	appends[off++] = member;

    	ConstantNameAndType nameType = new ConstantNameAndType();
    	nameType.setNameIndex(nameIndex);
    	nameType.setDescIndex(descIndex);
    	appends[off++] = nameType;

    	int refClassIndex = findClass(refClassName);
    	if(refClassIndex == 0) {
        	member.setClassIndex(firstIndex + off);
        	ConstantClass refClassCon = new ConstantClass();
        	appends[off++] = refClassCon;
        	int refClassNameIndex = findName(refClassName);
        	if(refClassNameIndex == 0) {
        		refClassCon.setNameIndex(firstIndex + off);
            	ConstantUtf8 refClassNameUtf8 = new ConstantUtf8();
            	refClassNameUtf8.setValue(refClassName);
            	appends[off++] = refClassNameUtf8;
        	} else {
        		refClassCon.setNameIndex(refClassNameIndex);
        	}

    	} else {
        	member.setClassIndex(refClassIndex);
    	}

    	ConstantInfo[] newCpInfos = new ConstantInfo[cpInfo.length + off];
    	System.arraycopy(cpInfo, 0, newCpInfos, 0, firstIndex);
    	System.arraycopy(appends, 0, newCpInfos, firstIndex, off);
    	System.arraycopy(cpInfo, firstIndex, newCpInfos, firstIndex + off, cpInfo.length - firstIndex);

    	cpInfo = newCpInfos;

    	return theMethodIndex;
    }

    public int findInterfaceMethod(String name, String desc, String refClassName) {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i] != null && cpInfo[i].tag == ConstantInfo.CONSTANT_InterfaceMethodref) {
    			ConstantMemberRef cons = (ConstantMemberRef) cpInfo[i];
    			ConstantClass refClass = (ConstantClass) cpInfo[cons.getClassIndex()];
    			if(!((ConstantUtf8)cpInfo[refClass.getNameIndex()]).getValue().equals(refClassName)) {
    				continue ;
    			}
    			ConstantNameAndType consNameType = (ConstantNameAndType) cpInfo[cons.getNameAndTypeIndex()];
    			if(((ConstantUtf8)cpInfo[consNameType.getNameIndex()]).getValue().equals(name)
    				&& ((ConstantUtf8)cpInfo[consNameType.getDescIndex()]).getValue().equals(desc)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }


    public int addClass(Class<?> cls) {
    	return addClass(DescriptorUtil.replaceDot2Slash(cls.getName()));
    }
    

    public int addClass(String className) {
    	int idx = findClass(className);
    	if(idx != 0) {
    		return idx;
    	}

    	int sourceIndex = findSourceIndex();
    	ConstantInfo[] appends = new ConstantInfo[2];
    	
    	
    	int off = 0, classIndex = 0;
    	ConstantClass clsCon = new ConstantClass();
    	classIndex = sourceIndex + off;
    	appends[off++] = clsCon;
    	

    	int nameIndex = findName(className);
    	if(nameIndex == 0) {
    		clsCon.setNameIndex(sourceIndex + off);
        	ConstantUtf8 nameUtf8 = new ConstantUtf8();
        	nameUtf8.setValue(className);
        	appends[off++] = nameUtf8;
    	} else {
    		clsCon.setNameIndex(nameIndex);
    	}
    	
    	ConstantInfo[] newCpInfos = new ConstantInfo[cpInfo.length + off];
    	System.arraycopy(cpInfo, 0, newCpInfos, 0, sourceIndex);
    	System.arraycopy(appends, 0, newCpInfos, sourceIndex, off);
    	System.arraycopy(cpInfo, sourceIndex, newCpInfos, sourceIndex + off, cpInfo.length - sourceIndex);
    	
    	cpInfo = newCpInfos;
    	
    	return classIndex;
    }
    
    public int addNameAndType(String name, String desc) {
    	int idx = findNameAndType(name, desc);
    	if(idx != 0) {
    		return idx;
    	}
    	int nameIndex = addName(name);
    	int descIndex = addName(desc);
    	
    	
    	ConstantNameAndType info = new ConstantNameAndType();
    	info.setNameIndex(nameIndex);
    	info.setDescIndex(descIndex);

    	ConstantInfo[] newCps = new ConstantInfo[cpInfo.length + 1];
    	int sourceIndex = findSourceIndex();
    	System.arraycopy(cpInfo, 0, newCps, 0, sourceIndex);
    	newCps[sourceIndex] = info;
    	System.arraycopy(cpInfo, sourceIndex, newCps, sourceIndex + 1, cpInfo.length - sourceIndex);
        return sourceIndex;
    }
    
    public int addMethodRef(Class<?> cls, String name, String desc) {
    	String clsName = DescriptorUtil.replaceDot2Slash(cls.getName());
    	int idx = findMethod(name, desc, clsName);
    	if(idx != 0) {
    		return idx;
    	}
    	int clsIndex = addClass(cls);
    	int nameTypeIndex = addNameAndType(name, desc);


    	ConstantMemberRef info = new ConstantMemberRef(ConstantInfo.CONSTANT_Methodref);
    	info.setClassIndex(clsIndex);
    	info.setNameAndTypeIndex(nameTypeIndex);
    	
    	ConstantInfo[] newCps = new ConstantInfo[cpInfo.length + 1];
    	int sourceIndex = findSourceIndex();
    	System.arraycopy(cpInfo, 0, newCps, 0, sourceIndex);
    	newCps[sourceIndex] = info;
    	System.arraycopy(cpInfo, sourceIndex, newCps, sourceIndex + 1, cpInfo.length - sourceIndex);
        return sourceIndex;
    }
    
    public void resetUtf8Name(String oldName, String newName) {
    	int idx = findName(oldName);
    	if(idx == 0) {
    		throw new BytecodeException("can not found name " + oldName);
    	}
    	((ConstantUtf8) cpInfo[idx]).setValue(newName);
    }

	public int addString(String value) {
		int idx = findString(value);
		if(idx != 0) {
			return idx;
		}
		int nameIndex = addName(value);
		ConstantString info = new ConstantString();
		info.setNameIndex(nameIndex);
		return insertAtSource(info, 1);
	}

	public int findString(String value) {
		int nameIndex = findName(value);
		if(nameIndex == 0) {
			return 0;
		}
		for(int i = 1; i < cpInfo.length; i++) {
			if(cpInfo[i] != null && cpInfo[i].tag == ConstantInfo.CONSTANT_String) {
				if(((ConstantString) cpInfo[i]).getNameIndex() == nameIndex) {
					return i;
				}
			}
		}
		return 0;
	}

	public int addInteger(int value) {
		int idx = findInteger(value);
		if(idx != 0) {
			return idx;
		}
		ConstantInteger info = new ConstantInteger();
		info.setValue(value);
		return insertAtSource(info, 1);
	}

	public int findInteger(int value) {
		for(int i = 1; i < cpInfo.length; i++) {
			if(cpInfo[i] != null && cpInfo[i].tag == ConstantInfo.CONSTANT_Integer) {
				if(((ConstantInteger) cpInfo[i]).getValue() == value) {
					return i;
				}
			}
		}
		return 0;
	}

	public int addLong(long value) {
		int idx = findLong(value);
		if(idx != 0) {
			return idx;
		}
		ConstantLong info = new ConstantLong();
		info.setHighValue((int) (value >>> 32));
		info.setLowValue((int) value);
		return insertAtSource(info, 2);
	}

	public int findLong(long value) {
		for(int i = 1; i < cpInfo.length; i++) {
			if(cpInfo[i] != null && cpInfo[i].tag == ConstantInfo.CONSTANT_Long) {
				ConstantLong con = (ConstantLong) cpInfo[i];
				long v = ((long) con.getHighValue() << 32) | (con.getLowValue() & 0xFFFFFFFFL);
				if(v == value) {
					return i;
				}
			}
		}
		return 0;
	}

	public int addFloat(float value) {
		int bits = Float.floatToRawIntBits(value);
		int idx = findFloat(bits);
		if(idx != 0) {
			return idx;
		}
		ConstantFloat info = new ConstantFloat();
		info.setValue(bits);
		return insertAtSource(info, 1);
	}

	public int findFloat(int bits) {
		for(int i = 1; i < cpInfo.length; i++) {
			if(cpInfo[i] != null && cpInfo[i].tag == ConstantInfo.CONSTANT_Float) {
				if(((ConstantFloat) cpInfo[i]).getValue() == bits) {
					return i;
				}
			}
		}
		return 0;
	}

	public int addDouble(double value) {
		long bits = Double.doubleToRawLongBits(value);
		int idx = findDouble(bits);
		if(idx != 0) {
			return idx;
		}
		ConstantDouble info = new ConstantDouble();
		info.setHighValue((int) (bits >>> 32));
		info.setLowValue((int) bits);
		return insertAtSource(info, 2);
	}

	public int findDouble(long bits) {
		for(int i = 1; i < cpInfo.length; i++) {
			if(cpInfo[i] != null && cpInfo[i].tag == ConstantInfo.CONSTANT_Double) {
				ConstantDouble con = (ConstantDouble) cpInfo[i];
				long v = ((long) con.getHighValue() << 32) | (con.getLowValue() & 0xFFFFFFFFL);
				if(v == bits) {
					return i;
				}
			}
		}
		return 0;
	}

	private int insertAtSource(ConstantInfo info, int slots) {
		ConstantInfo[] newCps = new ConstantInfo[cpInfo.length + slots];
		int sourceIndex = findSourceIndex();
		System.arraycopy(cpInfo, 0, newCps, 0, sourceIndex);
		newCps[sourceIndex] = info;
		if(slots == 2) {
			newCps[sourceIndex + 1] = null;
		}
		System.arraycopy(cpInfo, sourceIndex, newCps, sourceIndex + slots, cpInfo.length - sourceIndex);
		cpInfo = newCps;
		return sourceIndex;
	}
    
    public int findSourceIndex() {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i].tag == ConstantInfo.CONSTANT_Utf8) {
    			if(((ConstantUtf8)cpInfo[i]).getValue().equals(SOURCE_FILE)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }
    
    public int findFileNameIndex() {
		ConstantClass currentCls = (ConstantClass) cpInfo[1];
		String name = ((ConstantUtf8)cpInfo[currentCls.getNameIndex()]).getValue();
		String fileName = DescriptorUtil.getLastName(name) + ".java";
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i].tag == ConstantInfo.CONSTANT_Utf8) {
    			if(((ConstantUtf8)cpInfo[i]).getValue().equals(fileName)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }
    

    public int findClass(Class<?> cls) {
    	return findClass(DescriptorUtil.replaceDot2Slash(cls.getName()));
    }
    
    public int findClass(String clazz) {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i].tag == ConstantInfo.CONSTANT_Class) {
    			ConstantClass clsCon = (ConstantClass) cpInfo[i];
    			if(((ConstantUtf8)cpInfo[clsCon.getNameIndex()]).getValue().equals(clazz)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }
    
    public int findName(String name) {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i].tag == ConstantInfo.CONSTANT_Utf8) {
    			if(((ConstantUtf8)cpInfo[i]).getValue().equals(name)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }
    
    public int findField(String name, Class<?> type) {
    	return findField(name, DescriptorUtil.getClassDescription(type));
    }
    
    public int findField(String name, String fieldDesc) {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i].tag == ConstantInfo.CONSTANT_Fieldref) {
    			ConstantMemberRef cons = (ConstantMemberRef) cpInfo[i];
    			ConstantNameAndType consNameType = (ConstantNameAndType) cpInfo[cons.getNameAndTypeIndex()];
    			if(((ConstantUtf8)cpInfo[consNameType.getNameIndex()]).getValue().equals(name)
        				&& ((ConstantUtf8)cpInfo[consNameType.getDescIndex()]).getValue().equals(fieldDesc)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }
    
    public int findMethod(String name, String[] paramDesces, String returnTypeDesc, String refClassName) {
    	String desc = DescriptorUtil.getMethodDescription(paramDesces, returnTypeDesc);
    	return findMethod(name, desc, refClassName);
    }
    
    public int findMethod(String name, String desc, String refClassName) {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i] != null && cpInfo[i].tag == ConstantInfo.CONSTANT_Methodref) {
    			ConstantMemberRef cons = (ConstantMemberRef) cpInfo[i];
    			ConstantClass refClass = (ConstantClass) cpInfo[cons.getClassIndex()];
    			if(!((ConstantUtf8)cpInfo[refClass.getNameIndex()]).getValue().equals(refClassName)) {
    				continue ;
    			}
    			ConstantNameAndType consNameType = (ConstantNameAndType) cpInfo[cons.getNameAndTypeIndex()];
    			if(((ConstantUtf8)cpInfo[consNameType.getNameIndex()]).getValue().equals(name)
    				&& ((ConstantUtf8)cpInfo[consNameType.getDescIndex()]).getValue().equals(desc)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }
    
    public int findNameAndType(String name, String desc) {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i].tag == ConstantInfo.CONSTANT_NameAndType) {
    			ConstantNameAndType consNameType = (ConstantNameAndType) cpInfo[i];
    			if(((ConstantUtf8)cpInfo[consNameType.getNameIndex()]).getValue().equals(name)
    				&& ((ConstantUtf8)cpInfo[consNameType.getDescIndex()]).getValue().equals(desc)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }

    /** 生成指向任意类的 CONSTANT_Fieldref（classIndex 指向 refClassName），与 addField 不同后者固定 classIndex=1 */
    public int addFieldRef(String refClassName, String name, String fieldDesc) {
    	int idx = findFieldRef(refClassName, name, fieldDesc);
    	if(idx != 0) {
    		return idx;
    	}
    	int off = 0, theFieldIndex = 0;
    	ConstantInfo[] appends = new ConstantInfo[16];

    	int firstIndex = findSourceIndex();

    	int nameIndex = findName(name);
    	if(nameIndex == 0) {
    		nameIndex = firstIndex + off;
        	ConstantUtf8 typeUtf8 = new ConstantUtf8();
        	typeUtf8.setValue(name);
        	appends[off++] = typeUtf8;
    	}

    	int descIndex = findName(fieldDesc);
    	if(descIndex == 0) {
    		descIndex = firstIndex + off;
        	ConstantUtf8 typeUtf8 = new ConstantUtf8();
        	typeUtf8.setValue(fieldDesc);
        	appends[off++] = typeUtf8;
    	}

    	theFieldIndex = firstIndex + off;
    	ConstantMemberRef member = new ConstantMemberRef(ConstantInfo.CONSTANT_Fieldref);
    	member.setNameAndTypeIndex(theFieldIndex + 1);
    	appends[off++] = member;

    	ConstantNameAndType nameType = new ConstantNameAndType();
    	nameType.setNameIndex(nameIndex);
    	nameType.setDescIndex(descIndex);
    	appends[off++] = nameType;

    	int refClassIndex = findClass(refClassName);
    	if(refClassIndex == 0) {
        	member.setClassIndex(firstIndex + off);
        	ConstantClass refClassCon = new ConstantClass();
        	appends[off++] = refClassCon;
        	int refClassNameIndex = findName(refClassName);
        	if(refClassNameIndex == 0) {
        		refClassCon.setNameIndex(firstIndex + off);
            	ConstantUtf8 refClassNameUtf8 = new ConstantUtf8();
            	refClassNameUtf8.setValue(refClassName);
            	appends[off++] = refClassNameUtf8;
        	} else {
        		refClassCon.setNameIndex(refClassNameIndex);
        	}
    	} else {
        	member.setClassIndex(refClassIndex);
    	}

    	ConstantInfo[] newCpInfos = new ConstantInfo[cpInfo.length + off];
    	System.arraycopy(cpInfo, 0, newCpInfos, 0, firstIndex);
    	System.arraycopy(appends, 0, newCpInfos, firstIndex, off);
    	System.arraycopy(cpInfo, firstIndex, newCpInfos, firstIndex + off, cpInfo.length - firstIndex);

    	cpInfo = newCpInfos;

    	return theFieldIndex;
    }

    public int findFieldRef(String refClassName, String name, String fieldDesc) {
    	for(int i = 1; i < cpInfo.length; i++) {
    		if(cpInfo[i] == null) {
    			continue;
    		}
    		if(cpInfo[i].tag == ConstantInfo.CONSTANT_Fieldref) {
    			ConstantMemberRef cons = (ConstantMemberRef) cpInfo[i];
    			ConstantClass refClass = (ConstantClass) cpInfo[cons.getClassIndex()];
    			if(!((ConstantUtf8)cpInfo[refClass.getNameIndex()]).getValue().equals(refClassName)) {
    				continue;
    			}
    			ConstantNameAndType consNameType = (ConstantNameAndType) cpInfo[cons.getNameAndTypeIndex()];
    			if(((ConstantUtf8)cpInfo[consNameType.getNameIndex()]).getValue().equals(name)
    				&& ((ConstantUtf8)cpInfo[consNameType.getDescIndex()]).getValue().equals(fieldDesc)) {
    				return i;
    			}
    		}
    	}
    	return 0;
    }
}
