package com.archer.xjson.asm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import com.archer.xjson.asm.AttributeInfo.*;
import com.archer.xjson.asm.constantpool.ConstantClass;
import com.archer.xjson.asm.constantpool.ConstantInfo;
import com.archer.xjson.asm.constantpool.ConstantMemberRef;
import com.archer.xjson.asm.constantpool.ConstantNameAndType;
import com.archer.xjson.asm.constantpool.ConstantPool;
import com.archer.xjson.asm.constantpool.ConstantUtf8;
import com.archer.xjson.asm.util.DescriptorUtil;


public class ClassBytecode {
	

	private static String debug = null;
	private static boolean debugMode = false;
	
	private static BytecodeClassLoader loader = new BytecodeClassLoader();
	public	static int	ACC_PRIVATE =	0x0002;
	public	static int	ACC_PUBLIC =	0x0001;
	public	static int	ACC_PROTECTED =	0x0004;
	
	private int magic;
    private int minorVersion;
    private int majorVersion;
    private ConstantPool constantPool;
    private int accessFlag;
    private int classIndex;
    private int superIndex;
    private int interfaceCount;
    private int[] interfaceIndexArr = new int[0];
    
    private int fieldCount;
    private MemberInfo[] fields;
    private int methodCount;
    private MemberInfo[] methods;
    private int attributeCount;
    private AttributeInfo[] attributes;
   
    
    private String rawClassName;
    private String simpleName;
    private String className;
    private String superClass;
    private String[] interfaces;
    
    private boolean isInterface;
    
	public ClassBytecode() {
		this.constantPool = new ConstantPool();
		this.fields = new MemberInfo[0];
		this.methods = new MemberInfo[0];
		this.interfaceIndexArr  = new int[0];
		this.interfaces = new String[0];
		this.attributes = new AttributeInfo[0];
	}
	
	public ClassBytecode(Class<?> cls) {
		this();
		readAndDecodeClass(cls);
	}
	
	public int getMagic() {
		return magic;
	}
	public int getMinorVersion() {
		return minorVersion;
	}
	public int getMajorVersion() {
		return majorVersion;
	}
	public ConstantPool getConstantPool() {
		return constantPool;
	}
	public int getAccessFlag() {
		return accessFlag;
	}
	public String getClassName() {
		return className;
	}
	public String getRawClassName() {
		return rawClassName;
	}
	public String getSimpleName() {
		return simpleName;
	}
	public String getSuperClass() {
		return superClass;
	}
	public int getInterfaceCount() {
		return interfaceCount;
	}
	public String[] getInterfaces() {
		return interfaces;
	}
	public int getFieldCount() {
		return fieldCount;
	}
	public MemberInfo[] getFields() {
		return fields;
	}
	public int getMethodCount() {
		return methodCount;
	}
	public MemberInfo[] getMethods() {
		return methods;
	}
	public int getClassIndex() {
		return classIndex;
	}

	public int getSuperIndex() {
		return superIndex;
	}

	public int[] getInterfaceIndexArr() {
		return interfaceIndexArr;
	}

	public AttributeInfo[] getAttributes() {
		return attributes;
	}

	public void setMagic(int magic) {
		this.magic = magic;
	}

	public void setMinorVersion(int minorVersion) {
		this.minorVersion = minorVersion;
	}

	public void setMajorVersion(int majorVersion) {
		this.majorVersion = majorVersion;
	}

	public void setConstantPool(ConstantPool constantPool) {
		this.constantPool = constantPool;
	}

	public void setAccessFlag(int accessFlag) {
		this.accessFlag = accessFlag;
	}

	public void setClassIndex(int classIndex) {
		this.classIndex = classIndex;
	}

	public void setSuperIndex(int superIndex) {
		this.superIndex = superIndex;
	}

	public void addInterfaceIndexArr(int interfaceIndex) {
		int[] newInterfaceArr = new int[this.interfaceCount + 1];
		System.arraycopy(this.interfaceIndexArr, 0, newInterfaceArr, 0, this.interfaceCount);
		newInterfaceArr[this.interfaceCount++] = interfaceIndex;
		this.interfaceIndexArr = newInterfaceArr;
	}

	public void setFields(MemberInfo[] fields) {
		this.fields = fields;
		this.fieldCount = fields.length;
	}

	public void setMethods(MemberInfo[] methods) {
		this.methods = methods;
		this.methodCount = methods.length;
	}

	public void setAttributes(AttributeInfo[] attributes) {
		this.attributes = attributes;
		this.attributeCount = attributes.length;
	}
	
	
	public void setClassName(String className) {
		if(className.indexOf('.') == -1 && className.indexOf('/') == -1) {
			if(className.equals(this.simpleName)) {
				throw new BytecodeException("duplicated calss name " + className);
			}
			if(this.className == null || this.rawClassName == null) {
				throw new BytecodeException("package is required");
			}
			this.simpleName = className;
			this.className = this.className.substring(0, this.className.lastIndexOf('.') + 1) + className;
			this.rawClassName = this.rawClassName.substring(0, this.rawClassName.lastIndexOf('/') + 1) + className;
		} else if(className.indexOf('.') == -1) {
			if(className.equals(this.rawClassName)) {
				throw new BytecodeException("duplicated class name " + className);
			}
			if(this.rawClassName != null && !this.rawClassName.substring(0, this.rawClassName.lastIndexOf('/')).equals(className.substring(0, className.lastIndexOf('/')))) {
				throw new BytecodeException("new class must be in the save package with old class");
			}
			this.rawClassName = className;
			this.className = DescriptorUtil.replaceSlash2Dot(className);
	        this.simpleName = className.substring(className.lastIndexOf('/') + 1);
		} else if(className.indexOf('/') == -1) {
			if(className.equals(this.className)) {
				throw new BytecodeException("duplicated class name " + className);
			}
			if(this.className != null && !this.className.substring(0, this.className.lastIndexOf('/')).equals(className.substring(0, className.lastIndexOf('/')))) {
				throw new BytecodeException("new class must be in the save package with old class");
			}
			this.className = className;
			this.rawClassName = DescriptorUtil.replaceDot2Slash(className);
	        this.simpleName = className.substring(className.lastIndexOf('.') + 1);
		}
	}

	public void setInterfaces(String[] interfaces) {
		this.interfaces = interfaces;
	}

	public boolean isInterface() {
		return isInterface;
	}

	public void setInterface(boolean isInterface) {
		this.isInterface = isInterface;
	}
	
    public ClassBytecode readAndDecodeClass(Class<?> cls) {
    	this.className = cls.getName();
    	this.rawClassName = DescriptorUtil.replaceDot2Slash(cls.getName());
    	this.simpleName = cls.getSimpleName();
    	this.isInterface = cls.isInterface();
		try(InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(rawClassName + ".class")) {
			Bytes rawClass = new Bytes();
			byte[] buf = new byte[1024];
			int off = 0;
			while((off = in.read(buf)) >= 0) {
				rawClass.write(buf, 0, off);
			}
			decodeClassBytes(rawClass);
		} catch(Exception ignore) {};
		return this;
	}
    
    public ClassBytecode readAndDecodeClass(String classFilePath) throws IOException {
    	byte[] content = Files.readAllBytes(Paths.get(classFilePath));
		decodeClassBytes(new Bytes(content));
		return this;
	}
    
	public void decodeClassBytes(Bytes bytes) {
    	this.magic = bytes.readInt32();
    	this.minorVersion = bytes.readInt16();
    	this.majorVersion = bytes.readInt16();
    	this.constantPool.read(bytes);
        
        ConstantInfo[] cpInfo = constantPool.getCpInfo();
        
        //获取类信息
        this.accessFlag = bytes.readInt16();
        this.classIndex = bytes.readInt16();
        ConstantClass clazz = (ConstantClass) cpInfo[classIndex];
        ConstantUtf8 className = (ConstantUtf8) cpInfo[clazz.getNameIndex()];
        this.rawClassName = className.getValue();
        this.className = DescriptorUtil.replaceSlash2Dot(className.getValue());
        this.simpleName = this.className.substring(this.className.lastIndexOf('.') + 1);
        
        //获取父类信息
        this.superIndex = bytes.readInt16();
        ConstantClass superClazz = (ConstantClass)cpInfo[superIndex];
        ConstantUtf8 superclassName = (ConstantUtf8)cpInfo[superClazz.getNameIndex()];
        this.superClass = superclassName.getValue();
    	
        //获取接口信息
        this.interfaceCount = bytes.readInt16();
        this.interfaceIndexArr = new int[interfaceCount];
        String[] interfaceArr = new String[interfaceCount];
        if(debugMode()) {
        	System.out.println("Interface: interfaceCount="+interfaceCount);
        }
        for (int i = 0; i < interfaceCount; i++) {
            int interfaceIndex = bytes.readInt16();
            interfaceIndexArr[i] = interfaceIndex;
            ConstantClass interfaceClazz = (ConstantClass)cpInfo[interfaceIndex];
            ConstantUtf8 interfaceName = (ConstantUtf8)cpInfo[interfaceClazz.getNameIndex()];
            interfaceArr[i] = interfaceName.getValue();
        }
        this.interfaces = interfaceArr;

        //获取字段信息
        this.fieldCount = bytes.readInt16();
        MemberInfo[] fields = new MemberInfo[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
        	fields[i] = new MemberInfo(true);
        	fields[i].read(bytes, constantPool);
        }
        this.fields = fields;

        //获取方法信息
        this.methodCount = bytes.readInt16();
        MemberInfo[] methods = new MemberInfo[methodCount];
        for (int i = 0; i < methodCount; i++) {
        	methods[i] = new MemberInfo(false);
        	methods[i].read(bytes, constantPool);
        }
        this.methods = methods;
        

        // 类属性
        this.attributeCount = bytes.readInt16();
        attributes = new AttributeInfo[this.attributeCount];
		if(debugMode()) {
			System.out.println("Class Attribute:\n  attributeCount:"+attributeCount);
		}
		for (int j = 0; j < attributes.length; j++) {
    		int nameIndex = bytes.readInt16();
    		int length = bytes.readInt32();
            byte[] info = bytes.read(length);
    		String name = ((ConstantUtf8) cpInfo[nameIndex]).getValue();
    		if(debugMode()) {
    			System.out.println("  " + name + " data="+Arrays.toString(info));
    		}
    		AttributeInfo attr;
    		if("SourceFile".equals(name)) {
    			attr = new SourceFileAttribute(nameIndex, length, info);
    		} else if("InnerClass".equals(name)) {
    			attr = new InnerClassAttribute(nameIndex, length, info);
    		} else {
    			attr = new AttributeInfo(nameIndex, length, info);
    		}
			attr.setName(name);
			attributes[j] = attr;
		}
    }
	
	
	public Bytes encodeClassBytes() {
		Bytes opcode = new Bytes();
		//写入头部
		opcode.writeInt32(magic);
		opcode.writeInt16(minorVersion);
		opcode.writeInt16(majorVersion);
		
		//写入常量池
		constantPool.writeInto(opcode);
		
		
		//写入类信息
		opcode.writeInt16(accessFlag);
		opcode.writeInt16(classIndex);

		//写入父类信息
		opcode.writeInt16(superIndex);
		
		//写入接口信息
		opcode.writeInt16(interfaceCount);
		for(int i = 0; i < interfaceCount; i++) {
			opcode.writeInt16(interfaceIndexArr[i]);
		}

		//写入获取字段信息
		opcode.writeInt16(fieldCount);
		for (int i = 0; i < fieldCount; i++) {
			fields[i].writeInto(opcode);
	    }

		//写入获取方法信息
		opcode.writeInt16(methodCount);
		for (int i = 0; i < methodCount; i++) {
			methods[i].writeInto(opcode);
	    }
		
		freshClassAttribute();
		
		//写入类属性
		opcode.writeInt16(attributeCount);
		for (int j = 0; j < attributes.length; j++) {
			AttributeInfo attr = attributes[j];
			opcode.writeInt16(attr.getNameIndex());
			opcode.writeInt32(attr.getLength());
			opcode.write(attr.getInfo());
        }
		
		return opcode;
	}
	
	public Class<?> loadSelfClass() {
		freshClassAttribute();
		Bytes codeBs = encodeClassBytes();
		try {
			return loader.defineBytecodeClass(className, codeBs.array(), 0, codeBs.available()); 
			//return (Class<?>) defineClass.invoke(loader, className, codeBs.array(), 0, codeBs.available());
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	

	public void toEmptyClass(String name, String pkg) {
		setMagic(-889275714);
		setMinorVersion(0);
		setMajorVersion(49);
		setClassName(getFullClassName(name, pkg));
		setAccessFlag(1);
		setClassIndex(1);
		setSuperIndex(3);

		
		int index = 1;
		ConstantInfo[] constants = new ConstantInfo[65536];
		
		ConstantClass clazzInfo = new ConstantClass();
		clazzInfo.setNameIndex(2);
		constants[index++] = clazzInfo;
		
		ConstantUtf8 clazzUtf8Info = new ConstantUtf8();
		clazzUtf8Info.setValue(rawClassName);
		constants[index++] = clazzUtf8Info;
		
		ConstantClass superInfo = new ConstantClass();
		superInfo.setNameIndex(4);
		constants[index++] = superInfo;
		
		ConstantUtf8 superUtf8Info = new ConstantUtf8();
		superUtf8Info.setValue("java/lang/Object");
		constants[index++] = superUtf8Info;

		int codeIndex = index;
		ConstantUtf8 mcode = new ConstantUtf8();
		mcode.setValue("Code");
		constants[index++] = mcode;

		ConstantUtf8 lnt = new ConstantUtf8();
		lnt.setValue("LineNumberTable");
		constants[index++] = lnt;

		ConstantUtf8 lvt = new ConstantUtf8();
		lvt.setValue("LocalVariableTable");
		constants[index++] = lvt;
		

		ConstantUtf8 thi = new ConstantUtf8();
		thi.setValue("this");
		constants[index++] = thi;
		
		String classTypeName = "L"+rawClassName + ";";
		ConstantUtf8 thiType = new ConstantUtf8();
		thiType.setValue(classTypeName);
		constants[index++] = thiType;
		
		int consNameIndex = index;
		ConstantUtf8 consName = new ConstantUtf8();
		consName.setValue("<init>");
		constants[index++] = consName;
		

		int consDescIndex = index;
		ConstantUtf8 consDesc = new ConstantUtf8();
		consDesc.setValue("()V");
		constants[index++] = consDesc;
		
		int consMethodIndex = index;
		ConstantMemberRef consMethod = new ConstantMemberRef(ConstantInfo.CONSTANT_Methodref);
		consMethod.setClassIndex(3);
		consMethod.setNameAndTypeIndex(index + 1);
		constants[index++] = consMethod;
		
		ConstantNameAndType consNameType = new ConstantNameAndType();
		consNameType.setNameIndex(consNameIndex);
		consNameType.setDescIndex(consDescIndex);
		constants[index++] = consNameType;
		

		int sourceIndex = index;
		ConstantUtf8 source = new ConstantUtf8();
		source.setValue("SourceFile");
		constants[index++] = source;

		int fileNameIndex = index;
		ConstantUtf8 sourceName = new ConstantUtf8();
		sourceName.setValue(simpleName+".java");
		constants[index++] = sourceName;
		
		ConstantPool cp = new ConstantPool();
		cp.setCpInfo(Arrays.copyOfRange(constants, 0, index));
		setConstantPool(cp);

		MemberInfo cons = new MemberInfo(false);
		cons.setAccessFlags(ACC_PUBLIC);
		cons.setNameIndex(consNameIndex);
		cons.setDescriptorIndex(consDescIndex);
		CodeAttribute codeAttr = CodeAttributeWriter.of(codeIndex)
			.addInstruction("aload_0")
			.addInstruction16("invokespecial", consMethodIndex)
			.addInstruction("return").toCodeAttribute();
		cons.setAttributes(new AttributeInfo[] {codeAttr});
		setMethods(new MemberInfo[] {cons});
		
		AttributeInfo[] attrs = new AttributeInfo[1];
		attrs[0] = new SourceFileAttribute(sourceIndex, fileNameIndex);
		setAttributes(attrs);
	}

	public void toEmptyImplClass(String name, String pkg, Class<?> parentClass) {
		toEmptyImplClass(name, pkg, DescriptorUtil.replaceDot2Slash(parentClass.getName()), parentClass.isInterface());
	}

	public void toEmptyImplClass(String name, String pkg, String parentClassName, boolean parentIsInterface) {
		if(parentClassName.indexOf('/') < 0) {
			throw new IllegalArgumentException("invalid parentClassName '" + parentClassName + "'");
		}
		
		setMagic(-889275714);
		setMinorVersion(0);
		setMajorVersion(49);
		setClassName(getFullClassName(name, pkg));
		setAccessFlag(1);
		setClassIndex(1);
		setSuperIndex(3);

		int index = 1;
		ConstantInfo[] constants = new ConstantInfo[65536];
		
		ConstantClass clazzInfo = new ConstantClass();
		clazzInfo.setNameIndex(2);
		constants[index++] = clazzInfo;
		
		ConstantUtf8 clazzUtf8Info = new ConstantUtf8();
		clazzUtf8Info.setValue(rawClassName);
		constants[index++] = clazzUtf8Info;
		
		if(parentIsInterface) {
			ConstantClass superInfo = new ConstantClass();
			superInfo.setNameIndex(4);
			constants[index++] = superInfo;
			
			ConstantUtf8 superUtf8Info = new ConstantUtf8();
			superUtf8Info.setValue("java/lang/Object");
			constants[index++] = superUtf8Info;
		}
		ConstantClass superInfo = new ConstantClass();
		superInfo.setNameIndex(index + 1);
		constants[index++] = superInfo;
		
		ConstantUtf8 superUtf8Info = new ConstantUtf8();
		superUtf8Info.setValue(parentClassName);
		constants[index++] = superUtf8Info;

		int codeIndex = index;
		ConstantUtf8 mcode = new ConstantUtf8();
		mcode.setValue("Code");
		constants[index++] = mcode;

		ConstantUtf8 lnt = new ConstantUtf8();
		lnt.setValue("LineNumberTable");
		constants[index++] = lnt;

		ConstantUtf8 lvt = new ConstantUtf8();
		lvt.setValue("LocalVariableTable");
		constants[index++] = lvt;
		

		ConstantUtf8 thi = new ConstantUtf8();
		thi.setValue("this");
		constants[index++] = thi;
		
		String classTypeName = "L"+rawClassName + ";";
		ConstantUtf8 thiType = new ConstantUtf8();
		thiType.setValue(classTypeName);
		constants[index++] = thiType;
		
		int consNameIndex = index;
		ConstantUtf8 consName = new ConstantUtf8();
		consName.setValue("<init>");
		constants[index++] = consName;
		

		int consDescIndex = index;
		ConstantUtf8 consDesc = new ConstantUtf8();
		consDesc.setValue("()V");
		constants[index++] = consDesc;
		
		int consMethodIndex = index;
		ConstantMemberRef consMethod = new ConstantMemberRef(ConstantInfo.CONSTANT_Methodref);
		consMethod.setClassIndex(3);
		consMethod.setNameAndTypeIndex(index + 1);
		constants[index++] = consMethod;
		
		ConstantNameAndType consNameType = new ConstantNameAndType();
		consNameType.setNameIndex(consNameIndex);
		consNameType.setDescIndex(consDescIndex);
		constants[index++] = consNameType;
		

		int sourceIndex = index;
		ConstantUtf8 source = new ConstantUtf8();
		source.setValue("SourceFile");
		constants[index++] = source;

		int fileNameIndex = index;
		ConstantUtf8 sourceName = new ConstantUtf8();
		sourceName.setValue(simpleName+".java");
		constants[index++] = sourceName;
		
		ConstantPool cp = new ConstantPool();
		cp.setCpInfo(Arrays.copyOfRange(constants, 0, index));
		setConstantPool(cp);
		
		if(parentIsInterface) {
			int interfaceIndex = cp.findClass(parentClassName);
			addInterfaceIndexArr(interfaceIndex);
		}

		if(debugMode()) {
			System.out.println("Method "+this.rawClassName+".<init>()V Code:");
		}
		
		MemberInfo cons = new MemberInfo(false);
		cons.setAccessFlags(ACC_PUBLIC);
		cons.setNameIndex(consNameIndex);
		cons.setDescriptorIndex(consDescIndex);
		CodeAttribute codeAttr = CodeAttributeWriter.of(codeIndex)
			.addInstruction("aload_0")
			.addInstruction16("invokespecial", consMethodIndex)
			.addInstruction("return").toCodeAttribute();
		cons.setAttributes(new AttributeInfo[] {codeAttr});
		setMethods(new MemberInfo[] {cons});
		
		AttributeInfo[] attrs = new AttributeInfo[1];
		attrs[0] = new SourceFileAttribute(sourceIndex, fileNameIndex);
		setAttributes(attrs);
	}

	
	public ClassBytecode generateImplClass(String name, String pkg) {
		ClassBytecode newcls = new ClassBytecode();
		newcls.setClassName(getFullClassName(name, pkg));
		newcls.setMagic(getMagic());
		newcls.setMinorVersion(getMinorVersion());
		newcls.setMajorVersion(getMajorVersion());
		newcls.setAccessFlag(getAccessFlag());
		newcls.setClassIndex(1);
		newcls.setSuperIndex(3);

		
		int index = 1;
		ConstantInfo[] constants = new ConstantInfo[256];
		
		ConstantClass clazzInfo = new ConstantClass();
		clazzInfo.setNameIndex(2);
		constants[index++] = clazzInfo;
		
		ConstantUtf8 clazzUtf8Info = new ConstantUtf8();
		clazzUtf8Info.setValue(newcls.rawClassName);
		constants[index++] = clazzUtf8Info;
		
		if(this.isInterface) {
			ConstantClass interfaceInfo = new ConstantClass();
			interfaceInfo.setNameIndex(index+1);
			constants[index++] = interfaceInfo;
			
			ConstantUtf8 interfaceUtf8Info = new ConstantUtf8();
			interfaceUtf8Info.setValue("java/lang/Object");
			constants[index++] = interfaceUtf8Info;
		}
		
		ConstantClass superInfo = new ConstantClass();
		superInfo.setNameIndex(index+1);
		constants[index++] = superInfo;
		
		ConstantUtf8 superUtf8Info = new ConstantUtf8();
		superUtf8Info.setValue(this.rawClassName);
		constants[index++] = superUtf8Info;
		
		int codeNameIndex = index;
		ConstantUtf8 mcode = new ConstantUtf8();
		mcode.setValue("Code");
		constants[index++] = mcode;

		ConstantUtf8 lnt = new ConstantUtf8();
		lnt.setValue("LineNumberTable");
		constants[index++] = lnt;

		ConstantUtf8 lvt = new ConstantUtf8();
		lvt.setValue("LocalVariableTable");
		constants[index++] = lvt;
		

		ConstantUtf8 thi = new ConstantUtf8();
		thi.setValue("this");
		constants[index++] = thi;
		
		String classTypeName = "L"+newcls.rawClassName + ";";
		ConstantUtf8 thiType = new ConstantUtf8();
		thiType.setValue(classTypeName);
		constants[index++] = thiType;
		
		ConstantUtf8 source = new ConstantUtf8();
		source.setValue("SourceFile");
		constants[index++] = source;

		ConstantUtf8 sourceName = new ConstantUtf8();
		sourceName.setValue(newcls.simpleName+".java");
		constants[index++] = sourceName;
		
		ConstantPool cp = new ConstantPool();
		cp.setCpInfo(Arrays.copyOfRange(constants, 0, index));
		newcls.setConstantPool(cp);

		int interfaceIndex = cp.findClass(this.rawClassName);
		addInterfaceIndexArr(interfaceIndex);
		
		MemberInfo[] newmethods = new MemberInfo[256];
		int newmethodOff = 0;
		
		ConstantInfo[] superConst = constantPool.getCpInfo();
        for (int i = 0; i < methodCount; i++) {
        	String methodName = ((ConstantUtf8)superConst[methods[i].getNameIndex()]).getValue();
        	if(!"<init>".equals(methodName)) {
        		continue;
        	}
        	String descName = ((ConstantUtf8)superConst[methods[i].getDescriptorIndex()]).getValue();
        	
			int consIndex = cp.addConstructor(descName, this.rawClassName);
			
			MemberInfo cons = new MemberInfo(false);
			cons.setAccessFlags(ACC_PUBLIC);
			cons.setNameIndex(cp.findName("<init>"));
			cons.setDescriptorIndex(cp.findName(descName));

			AttributeInfo[] methodAttrs = new AttributeInfo[256];
			int methodAttrOff = 0;

			String[] argDescs = DescriptorUtil.methodDescToArgDescs(descName);
			CodeAttributeWriter writer = CodeAttributeWriter.of(codeNameIndex)
				.addInstruction("aload_0");
			int slot = 1;
			for(int j = 0; j < argDescs.length; j++) {
				cp.addName(argDescs[j]);
				Pair<byte[], Integer> pair = implConstructorCodes(argDescs[j], slot);
				slot += pair.getSecond();
				writer.addCodes(pair.getFirst());
			}
			writer.addInstruction16("invokespecial", consIndex);
			writer.addInstruction("return");

			methodAttrs[methodAttrOff++] = writer.toCodeAttribute();
			
			int exIndex = 0;
			for(AttributeInfo attr: methods[i].getAttributes()) {
				if(attr instanceof ExceptionAttribute) {
					if(exIndex == 0) {
						exIndex = cp.addName("Exceptions");
					}
					ExceptionAttribute ex = (ExceptionAttribute) attr;
					int classIndex = cp.findClass(newcls.getRawClassName());
					ConstantClass excls = (ConstantClass) superConst[ex.getExceptionClassIndex()];
					String exClsName = ((ConstantUtf8)superConst[excls.getNameIndex()]).getValue();
					int exClassIndex = cp.addClass(exClsName);
					
					methodAttrs[methodAttrOff++] = new ExceptionAttribute(exIndex, classIndex, exClassIndex);
				}
			}
			
			cons.setAttributes(Arrays.copyOf(methodAttrs, methodAttrOff));
			newmethods[newmethodOff++] = cons;
        }
		newcls.setMethods(Arrays.copyOf(newmethods, newmethodOff));
		

		int fileNameIndex = cp.findFileNameIndex();
		if(fileNameIndex == 0) {
			throw new BytecodeException("Invalid bytescode");
		}
		AttributeInfo[] attrs = new AttributeInfo[1];
		attrs[0] = new SourceFileAttribute(cp.findSourceIndex(), fileNameIndex);
		newcls.setAttributes(attrs);
		
		return newcls;
	}
	
	public int findField(String name) {
		for(MemberInfo f: fields) {
			String fName = ((ConstantUtf8)constantPool.getCpInfo()[f.getNameIndex()]).getValue();
			if(fName.equals(name)) {
				String desc = ((ConstantUtf8)constantPool.getCpInfo()[f.getDescriptorIndex()]).getValue();
				return constantPool.findField(name, desc);
			}
		}
		return 0;
	}
	
	public int addField(String name, Class<?> type, int accessFlag) {
		return addField(name, DescriptorUtil.replaceDot2Slash(type.getName()), accessFlag);
	}
	
	public int addField(String name, String type, int accessFlag) {
		String typeDesc = DescriptorUtil.getClassDescription(type);
		int fieldIndex = constantPool.addField(name, typeDesc);
		ConstantMemberRef field = (ConstantMemberRef) constantPool.getCpInfo()[fieldIndex];
		ConstantNameAndType nameType = (ConstantNameAndType) constantPool.getCpInfo()[field.getNameAndTypeIndex()];
		MemberInfo[] newFields = new MemberInfo[fields.length + 1];
		System.arraycopy(fields, 0, newFields, 0, fields.length);
		MemberInfo newField = new MemberInfo(true);
		newField.setAccessFlags(accessFlag);
		newField.setName(name);
		newField.setDesc(typeDesc);
		newField.setNameIndex(nameType.getNameIndex());
		newField.setDescriptorIndex(nameType.getDescIndex());
		newField.setAttributes(new AttributeInfo[0]);
		newFields[newFields.length - 1] = newField;
		setFields(newFields);
		return fieldIndex;
	}
	
	public void addConstructor(Class<?>[] params, CodeAttribute codeAttr, ExceptionAttribute... exAttrs) {
    	String[] paramDesces = null;
    	if(params != null) {
    		paramDesces = new String[params.length];
    		for(int i = 0; i < params.length; i++) {
    			paramDesces[i] = DescriptorUtil.getClassDescription(params[i]);
    		}
    	}
    	addConstructor(paramDesces, codeAttr, exAttrs);
	}
	
	public void addConstructor(String[] paramDesces, CodeAttribute codeAttr, ExceptionAttribute... exAttrs) {
    	String desc = DescriptorUtil.getMethodDescription(paramDesces, "V");
    	int nameIndex = constantPool.addName("<init>");
    	int descIndex = constantPool.addName(desc);
		MemberInfo[] newMethods = new MemberInfo[methods.length + 1];
		System.arraycopy(methods, 0, newMethods, 0, methods.length);
		MemberInfo method = new MemberInfo(false);
		method.setAccessFlags(1);
		method.setName("<init>");
		method.setDesc(desc);
		method.setNameIndex(nameIndex);
		method.setDescriptorIndex(descIndex);
		if(exAttrs.length == 0) {
			method.setAttributes(new AttributeInfo[] {codeAttr});
		} else {
			AttributeInfo[] attrs = new AttributeInfo[exAttrs.length + 1];
			attrs[0] = codeAttr;
			System.arraycopy(exAttrs, 0, attrs, 1, exAttrs.length);
			method.setAttributes(attrs);
		}
		
		newMethods[methods.length] = method;
		
		setMethods(newMethods);
	}
	
	public void addMethod(String name, Class<?>[] params, Class<?> returnType, CodeAttribute codeAttr, ExceptionAttribute... exAttrs) {
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
    	addMethod(name, paramDesces, returnTypeDesc, codeAttr, exAttrs);
	}
	
	public void addMethod(String name, String[] paramDesces, String returnTypeDesc, CodeAttribute codeAttr, ExceptionAttribute... exAttrs) {
    	String desc = DescriptorUtil.getMethodDescription(paramDesces, returnTypeDesc);
    	int nameIndex = constantPool.addName(name);
    	int descIndex = constantPool.addName(desc);
		MemberInfo[] newMethods = new MemberInfo[methods.length + 1];
		System.arraycopy(methods, 0, newMethods, 0, methods.length);
		MemberInfo method = new MemberInfo(false);
		method.setAccessFlags(1);
		method.setName(name);
		method.setDesc(desc);
		method.setNameIndex(nameIndex);
		method.setDescriptorIndex(descIndex);
		if(exAttrs.length == 0) {
			method.setAttributes(new AttributeInfo[] {codeAttr});
		} else {
			AttributeInfo[] attrs = new AttributeInfo[exAttrs.length + 1];
			attrs[0] = codeAttr;
			System.arraycopy(exAttrs, 0, attrs, 1, exAttrs.length);
			method.setAttributes(attrs);
		}
		
		newMethods[methods.length] = method;
		
		setMethods(newMethods);
	}
	
	public void renameMethod(String oldName, String[] paramDesces, String returnTypeDesc, String newName) {
    	String desc = DescriptorUtil.getMethodDescription(paramDesces, returnTypeDesc);
		for(int i = 0; i < methods.length; i++) {
			String theName = ((ConstantUtf8)constantPool.getCpInfo()[methods[i].getNameIndex()]).getValue();
			String theDesc = ((ConstantUtf8)constantPool.getCpInfo()[methods[i].getDescriptorIndex()]).getValue();
			if(oldName.equals(theName) && desc.equals(theDesc)) {
				int newIdx = constantPool.addName(newName);
				methods[i].setNameIndex(newIdx);
			}
		}
	}
	
	public void freshClassAttribute() {
		for(int i = 0; i < attributes.length; i++) {
			if(attributes[i] instanceof SourceFileAttribute) {
				SourceFileAttribute attr = (SourceFileAttribute)attributes[i];
				attr.refresh(constantPool.findSourceIndex(), constantPool.findFileNameIndex());
			}
		}
	}
	
	
	public static Pair<byte[], Integer> implConstructorCodes(String type, int slot) {
		if(type.length() == 1) {
			byte c = type.getBytes()[0];
			if(c == 'Z' || c == 'B' || c == 'C' || c == 'S' || c == 'I') {
				if(slot == 1) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("iload_1")}, 1);
				}
				if(slot == 2) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("iload_2")}, 1);
				}
				if(slot == 3) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("iload_3")}, 1);
				}
				return new Pair<>(new byte[] {InstructionTable.getInstructionCode("iload"), (byte)slot}, 1);
			}
			if(c == 'J') {
				if(slot == 1) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("lload_1")}, 2);
				}
				if(slot == 2) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("lload_2")}, 2);
				}
				if(slot == 3) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("lload_3")}, 2);
				}
				return new Pair<>(new byte[] {InstructionTable.getInstructionCode("lload"), (byte)slot}, 2);
			}
			if(c == 'F') {
				if(slot == 1) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("fload_1")}, 1);
				}
				if(slot == 2) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("fload_2")}, 1);
				}
				if(slot == 3) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("fload_3")}, 1);
				}
				return new Pair<>(new byte[] {InstructionTable.getInstructionCode("fload"), (byte)slot}, 1);
			} 
			if(c == 'D') {
				if(slot == 1) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("dload_1")}, 2);
				}
				if(slot == 2) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("dload_2")}, 2);
				}
				if(slot == 3) {
					return new Pair<>(new byte[] {InstructionTable.getInstructionCode("dload_3")}, 2);
				}
				return new Pair<>(new byte[] {InstructionTable.getInstructionCode("dload"), (byte)slot}, 2);
			} 
			throw new IllegalArgumentException("invalid instruction load type " + type);
		}
		if(slot == 1) {
			return new Pair<>(new byte[] {InstructionTable.getInstructionCode("aload_1")}, 1);
		}
		if(slot == 2) {
			return new Pair<>(new byte[] {InstructionTable.getInstructionCode("aload_2")}, 1);
		}
		if(slot == 3) {
			return new Pair<>(new byte[] {InstructionTable.getInstructionCode("aload_3")}, 1);
		}
		return new Pair<>(new byte[] {InstructionTable.getInstructionCode("aload"), (byte)slot}, 1);
	}
	
	
	private String getFullClassName(String className, String pkg) {
		if(pkg.indexOf('.') == -1 && pkg.indexOf('/') == -1) {
			return pkg + '/' + className;
		} else if(pkg.indexOf('.') == -1) {
			return pkg + '/' + className;
		} else {
			return DescriptorUtil.replaceDot2Slash(pkg) + '/' + className;
		}
	}
	
	public static boolean debugMode() {
		if(debug == null) {
			debug = System.getProperty("archer.bytecode.debug", "false");
			try {
				debugMode = Boolean.valueOf(debug);
			} catch(Exception ignore) {}
		}
		return debugMode;
	}
}
