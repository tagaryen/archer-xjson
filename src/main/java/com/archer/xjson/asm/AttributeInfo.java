package com.archer.xjson.asm;

public class AttributeInfo {
   
	protected int nameIndex;
	protected int length;
	protected byte[] info;
	protected String name;
	
	public AttributeInfo(int nameIdx, int len, byte[] data) { nameIndex = nameIdx; length = len; info = data; }
   

	public int getNameIndex() {
		return nameIndex;
	}

	public int getLength() {
		return length;
	}

	public byte[] getInfo() {
		return info;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}


	public void setInfo(byte[] info) {
		this.info = info;
		this.length = info.length;
	}


	public static class ExceptionTable {

		int startPc, endPc, handlerPc, catchType;
	
		public ExceptionTable() {}

		public ExceptionTable(int startPc, int endPc, int handlerPc, int catchType) {
			this.startPc = startPc;
			this.endPc = endPc;
			this.handlerPc = handlerPc;
			this.catchType = catchType;
		}

		public int getStartPc() {
			return startPc;
		}
	
		public void setStartPc(int startPc) {
			this.startPc = startPc;
		}
	
		public int getEndPc() {
			return endPc;
		}
	
		public void setEndPc(int endPc) {
			this.endPc = endPc;
		}
	
		public int getHandlerPc() {
			return handlerPc;
		}
	
		public void setHandlerPc(int handlerPc) {
			this.handlerPc = handlerPc;
		}
	
		public int getCatchType() {
			return catchType;
		}
	
		public void setCatchType(int catchType) {
			this.catchType = catchType;
		}
	}

	public static class CodeAttributeWriter {
		int nameIndex;
		Bytes code = new Bytes();
		
		private CodeAttributeWriter(int nameIndex) {
			this.nameIndex = nameIndex;
		}
		
		public static CodeAttributeWriter of(int nameIndex) {
			return new CodeAttributeWriter(nameIndex);
		}
		
		public int currentPc() {
			return code.available();
		}
		
		public CodeAttributeWriter addInstruction(int instructionCode) {
			code.writeInt8(instructionCode);
			return this;
		}
		
		public CodeAttributeWriter addInstruction8(int instructionCode, int pos) {
			code.writeInt8(instructionCode);
			code.writeInt8(pos);
			return this;
		}
		
		public CodeAttributeWriter addInstruction16(int instructionCode, int pos) {
			code.writeInt8(instructionCode);
			code.writeInt16(pos);
			return this;
		}
		
		public CodeAttributeWriter addInstruction24(int instructionCode, int pos) {
			code.writeInt8(instructionCode);
			code.writeInt24(pos);
			return this;
		}
		
		public CodeAttributeWriter addInstruction32(int instructionCode, int pos) {
			code.writeInt8(instructionCode);
			code.writeInt32(pos);
			return this;
		}

		public CodeAttributeWriter addInstructions(int instructionCode, byte[] opnums) {
			code.writeInt8(instructionCode);
			code.write(opnums);
			return this;
		}
		
		public CodeAttributeWriter addInstruction(String instruction) {
			code.writeInt8(InstructionTable.getInstructionCode(instruction));
			return this;
		}
		
		public CodeAttributeWriter addInstruction8(String instruction, int pos) {
			code.writeInt8(InstructionTable.getInstructionCode(instruction));
			code.writeInt8(pos);
			return this;
		}
		
		public CodeAttributeWriter addInstruction16(String instruction, int pos) {
			code.writeInt8(InstructionTable.getInstructionCode(instruction));
			code.writeInt16(pos);
			return this;
		}

		public CodeAttributeWriter addInstruction24(String instruction, int pos) {
			code.writeInt8(InstructionTable.getInstructionCode(instruction));
			code.writeInt24(pos);
			return this;
		}
		
		public CodeAttributeWriter addInstruction32(String instruction, int pos) {
			code.writeInt8(InstructionTable.getInstructionCode(instruction));
			code.writeInt32(pos);
			return this;
		}

		public CodeAttributeWriter addInstructions(String instruction, byte[] opnums) {
			code.writeInt8(InstructionTable.getInstructionCode(instruction));
			code.write(opnums);
			return this;
		}
		
		/** 回写 2 字节分支位移，用于 ifeq / goto 等跳转指令的偏移回填 */
		public void patchInt16(int pc, int value) {
			byte[] d = code.array();
			d[pc] = (byte) (value >> 8);
			d[pc + 1] = (byte) value;
		}

		/** 回写 4 字节分支位移，用于 lookupswitch 偏移回填 */
		public void patchInt32(int pc, int value) {
			byte[] d = code.array();
			d[pc] = (byte) (value >> 24);
			d[pc + 1] = (byte) (value >> 16);
			d[pc + 2] = (byte) (value >> 8);
			d[pc + 3] = (byte) value;
		}

		/** switch 指令 4 字节对齐填充 */
		public void padByte() {
			code.writeInt8(0);
		}

		/** 写入无操作码的原始 4 字节（switch 操作数） */
		public void writeRawInt32(int value) {
			code.writeInt32(value);
		}

		public CodeAttributeWriter append(byte opcode) {
			int c = (int) opcode;
			code.writeInt8(c < 0 ? (c + 256) : c);
			return this;
		}
		public CodeAttributeWriter addCodes(byte[] codes) {
			code.write(codes);
			return this;
		}
		
		public CodeAttribute toCodeAttribute() {
			byte [] codeBytes = code.readAll();
			Pair<Integer, Integer> maxs = InstructionTable.calculateMaxs(codeBytes);
			Bytes data = new Bytes();
			data.writeInt16(maxs.getFirst());
			data.writeInt16(maxs.getSecond());
			data.writeInt32(codeBytes.length);
			data.write(codeBytes);
			data.writeInt16(0);// 0 exceptions
			data.writeInt16(0);// 0 attributes
			return new CodeAttribute(nameIndex, data.available(), data.readAll());
		}
	}
	
	public static class CodeAttribute extends AttributeInfo {
	
		public CodeAttribute(int nameIdx, int len, byte[] data) {
			super(nameIdx, len, data);
			parse();
		}
	    
		private int maxStack, maxLocals;
		private int codeLength;
		private byte[] code = new byte[0];
		private int exceptionTableLength;
	    private ExceptionTable[] exceptionTable;
		private int attributesCount;
		private AttributeInfo[] attributes;
		public int getMaxStack() {
			return maxStack;
		}
		public int getMaxLocals() {
			return maxLocals;
		}
		public int getCodeLength() {
			return codeLength;
		}
		public byte[] getCode() {
			return code;
		}
		public int getExcepetionTableLength() {
			return exceptionTableLength;
		}
		public int getAttributesCount() {
			return attributesCount;
		}
		public AttributeInfo[] getAttributes() {
			return attributes;
		}
		public ExceptionTable[] getExceptionTable() {
			return exceptionTable;
		}
		public void setMaxStack(int maxStack) {
			this.maxStack = maxStack;
			refreshInfo();
		}
		public void setMaxLocals(int maxLocals) {
			this.maxLocals = maxLocals;
			refreshInfo();
		}
		public void setCode(byte[] code) {
			this.code = code;
			this.codeLength = code.length;
			refreshInfo();
		}
		public void setAttributes(AttributeInfo[] attributes) {
			this.attributes = attributes;
			this.attributesCount = attributes.length;
			refreshInfo();
		}
		public void setExceptionTable(ExceptionTable[] exceptionTable) {
			this.exceptionTable = exceptionTable;
			this.exceptionTableLength = exceptionTable.length;
			refreshInfo();
		}
		
		private void refreshInfo() {
			Bytes data = new Bytes();
			data.writeInt16(maxStack);
			data.writeInt16(maxLocals);
			data.writeInt32(code.length);
			data.write(code);
			data.writeInt16(exceptionTableLength);
			for(int i = 0; i < exceptionTableLength; i++) {
	            ExceptionTable e = exceptionTable[i];
	            data.writeInt16(e.startPc);
	            data.writeInt16(e.endPc);
	            data.writeInt16(e.handlerPc);
	            data.writeInt16(e.catchType);
			}
			data.writeInt16(attributesCount);
			for (int j = 0; j < attributesCount; j++) {
				data.writeInt16(attributes[j].nameIndex);
				data.writeInt32(attributes[j].length);
				data.write(attributes[j].info);
			}
			setInfo(data.readAll());
		}
		
		private void parse() {
	        Bytes bytes = new Bytes(getInfo());
			
			maxStack = bytes.readInt16(); //2bytes
			maxLocals = bytes.readInt16(); //2bytes
			codeLength = bytes.readInt32();   //4bytes
			code = bytes.read(codeLength);  //length bytes
			exceptionTableLength = bytes.readInt16();    // 2bytes
			exceptionTable = new ExceptionTable[exceptionTableLength];
			
			for (int i = 0; i < exceptionTableLength; i++) {
	            ExceptionTable e = new ExceptionTable();
	            e.startPc = bytes.readInt16();
	            e.endPc = bytes.readInt16();
	            e.handlerPc = bytes.readInt16();
	            e.catchType = bytes.readInt16();
	            exceptionTable[i] = e;
	        }
	        
	        attributesCount = bytes.readInt16(); //2bytes
	        attributes = new AttributeInfo[attributesCount];

			for (int j = 0; j < attributes.length; j++) {
	    		int nameIndex = bytes.readInt16();
	    		int length = bytes.readInt32();
	            byte[] info = bytes.read(length);

				AttributeInfo attr = new AttributeInfo(nameIndex, length, info);
				attributes[j] = attr;
			}
		}
	}
	
	public static class LineNumAttribute extends AttributeInfo {
		
		public LineNumAttribute(int nameIdx, int len, byte[] data) {
			super(nameIdx, len, data);
			parse();
		}
		
		private int lineNumTableCount;
		/*
		 * {
		 * 		u2 start_pc  字节码位置   
		 *      u2 line_number   源代码位置
		 * } []
		 * */
		private int lineNumTable[][];
		public int getLineNumTableCount() {
			return lineNumTableCount;
		}
		public int[][] getLineNumTable() {
			return lineNumTable;
		}
		public void setLineNumTableCount(int lineNumTableCount) {
			this.lineNumTableCount = lineNumTableCount;
		}
		public void setLineNumTable(int[][] lineNumTable) {
			this.lineNumTable = lineNumTable;
		}
	
		private void parse() {
	        Bytes bytes = new Bytes(getInfo());
	
			setLineNumTableCount(bytes.readInt16());
			int[][] lineNumTable = new int[lineNumTableCount][2];
			setLineNumTable(lineNumTable);
			for(int i = 0; i < lineNumTableCount; i++) {
				lineNumTable[i][0] = bytes.readInt16();
				lineNumTable[i][1] = bytes.readInt16();
			}
		}
	}
	
	public static class LocalVarAttribute extends AttributeInfo {
	
		public LocalVarAttribute(int nameIdx, int len, byte[] data) {
			super(nameIdx, len, data);
			parse();
		}
		private int localVarTableCount;
		/*
		 * {
		 * 		u2 start_pc  字节码位置   
		 *      u2 length   源代码位置
		 *      u2 name_index 
		 *      u2 desc_index
		 *      u2 index
		 * } []
		 * */
		private int localVarTable[][];
		public int getLocalVarTableCount() {
			return localVarTableCount;
		}
		public int[][] getLocalVarTable() {
			return localVarTable;
		}
		public void setLocalVarTableCount(int localVarTableCount) {
			this.localVarTableCount = localVarTableCount;
		}
		public void setLocalVarTable(int[][] localVarTable) {
			this.localVarTable = localVarTable;
		}
	
		private void parse() {
	        Bytes bytes = new Bytes(getInfo());
	
			setLocalVarTableCount(bytes.readInt16());
			int[][] localVarTable = new int[localVarTableCount][5];
			setLocalVarTable(localVarTable);
			for(int i = 0; i < localVarTableCount; i++) {
				localVarTable[i][0] = bytes.readInt16();
				localVarTable[i][1] = bytes.readInt16();
				localVarTable[i][2] = bytes.readInt16();
				localVarTable[i][3] = bytes.readInt16();
				localVarTable[i][4] = bytes.readInt16();
			}
		}
	}
	
	public static class ExceptionAttribute extends AttributeInfo {

		public ExceptionAttribute(int nameIdx, int len, byte[] data) {
			super(nameIdx, len, data);
			parse();
		}
		
		public ExceptionAttribute(int nameIdx, int classIndex, int exceptionClassIndex) {
			super(nameIdx, 0, null);
			this.classIndex = classIndex;
			this.exceptionClassIndex = exceptionClassIndex;
			refreshInfo();
		}

		int classIndex, exceptionClassIndex;
	
		
		public int getClassIndex() {
			return classIndex;
		}

		public void setClassIndex(int classIndex) {
			this.classIndex = classIndex;
			refreshInfo();
		}

		public int getExceptionClassIndex() {
			return exceptionClassIndex;
		}

		public void setExceptionClassIndex(int exceptionClassIndex) {
			this.exceptionClassIndex = exceptionClassIndex;
			refreshInfo();
		}

		private void parse() {
			Bytes bytes = new Bytes(info);
			classIndex = bytes.readInt16();
			exceptionClassIndex = bytes.readInt16();
		}
		
		private void refreshInfo() {
			Bytes data = new Bytes();
            data.writeInt16(classIndex);
            data.writeInt16(exceptionClassIndex);
            setInfo(data.readAll());
		}
	}
	
	public static class StackMapEntry {
		int frameType, offsetDelta;
		int[] tag, cpPoolIndex, offset;
		int numberOfLocals, numberOfStack;
		
		int off = 0;
		
		public StackMapEntry(int frameType) {
			this.frameType = frameType;
			this.tag = new int[512];
			this.cpPoolIndex = new int[512];
			this.offset = new int[512];   
		}
		
		public StackMapEntry(Bytes data) {
			frameType = data.readInt8();
	        if (frameType >= 0 && frameType <= 63) {               // same_frame
	            offsetDelta = frameType;
	            //no more data
	        } else if (frameType >= 64 && frameType <= 127) {        // same_locals_1_stack_item_frame
	        	offsetDelta = frameType - 64;
	        	tag = new int[1];
	        	cpPoolIndex = new int[1];
	        	offset = new int[1];
	        	parseVerificationTypeInfo(data, 0);
	        } else if (frameType == 247) {                           // same_locals_1_stack_item_frame_extended
	        	offsetDelta = frameType - 64;
	        	tag = new int[1];
	        	cpPoolIndex = new int[1];
	        	offset = new int[1];
	        	offsetDelta = data.readInt16();
	        	parseVerificationTypeInfo(data, 0);
	        } else if (frameType >= 248 && frameType <= 250) {       // chop_frame
	            offsetDelta = 251 - frameType;                       // 隐含偏移增量
	            //no more data
	        } else if (frameType == 251) {                           // same_frame_extended
	            offsetDelta = data.readInt16();
	            //no more data
	        } else if (frameType >= 252 && frameType <= 254) {       // append_frame
	            offsetDelta = data.readInt16();
	            int k = frameType - 251;     
	        	tag = new int[k];
	        	cpPoolIndex = new int[k];
	        	offset = new int[k];                        // 新增的局部变量个数
	            for (int i = 0; i < k; i++) {
	                parseVerificationTypeInfo(data, i);
	            }
	        } else if (frameType == 255) {                           // full_frame
	            offsetDelta = data.readInt16();
	            numberOfLocals = data.readInt16();
	        	tag = new int[512];
	        	cpPoolIndex = new int[512];
	        	offset = new int[512];   
	            for (int i = 0; i < numberOfLocals; i++) {
	                parseVerificationTypeInfo(data, i);
	            }
	            numberOfStack = data.readInt16();
	            for (int i = 0; i < numberOfStack; i++) {
	                parseVerificationTypeInfo(data, i + numberOfLocals);
	            }
	        } else {
	            throw new BytecodeException("Invalid StackMap frame_type: " + frameType);
	        }
		}
		
		private void parseVerificationTypeInfo(Bytes data, int idx) {
            tag[idx] = data.readInt8();
            if(0 <= tag[idx] && tag[idx] <= 6) { //TOP INTEGER FLOAT DOUBLE LONG NULL UNINITIALIZED_THIS
            	//no more data
            } else if(tag[idx] == 7) {      //OBJECT
            	cpPoolIndex[idx] = data.readInt16();
            } else if(tag[idx] == 8) {      //UNINITIALIZED
            	offset[idx] = data.readInt16();
            } else {
            	throw new BytecodeException("Invalid StackMap tag " + tag);
            }
		}
		
		private void writeVerificationTypeInfo(Bytes data, int idx) {
			data.writeInt8(tag[idx]);
            if(0 <= tag[idx] && tag[idx] <= 6) { //TOP INTEGER FLOAT DOUBLE LONG NULL UNINITIALIZED_THIS
            	//no more data
            } else if(tag[idx] == 7) {      //OBJECT
            	data.writeInt16(cpPoolIndex[idx]);
            } else if(tag[idx] == 8) {      //UNINITIALIZED
            	data.writeInt16(offset[idx]);
            } else {
            	throw new BytecodeException("Invalid StackMap tag " + tag);
            }
		}
		
		public Bytes toBytes() {
			Bytes data = new Bytes(1024);
			data.writeInt8(frameType);
			if (frameType >= 0 && frameType <= 63) {                 // same_frame
	            //no more data
	        } else if (frameType >= 64 && frameType <= 127) {        // same_locals_1_stack_item_frame
	        	writeVerificationTypeInfo(data, 0);
	        } else if (frameType == 247) {                           // same_locals_1_stack_item_frame_extended
	        	writeVerificationTypeInfo(data, 0);
	        } else if (frameType >= 248 && frameType <= 250) {       // chop_frame
	            offsetDelta = 251 - frameType;                       // 隐含偏移增量
	            //no more data
	        } else if (frameType == 251) {                           // same_frame_extended
	        	data.writeInt16(offsetDelta);
	            //no more data
	        } else if (frameType >= 252 && frameType <= 254) {       // append_frame
	        	data.writeInt16(offsetDelta);
	            int k = frameType - 251;                             // 新增的局部变量个数
	            for (int i = 0; i < k; i++) {
	            	writeVerificationTypeInfo(data, i);
	            }
	        } else if (frameType == 255) {                           // full_frame
	        	data.writeInt16(offsetDelta);
	        	data.writeInt16(numberOfLocals); 
	            for (int i = 0; i < numberOfLocals; i++) {
	            	writeVerificationTypeInfo(data, i);
	            }
	        	data.writeInt16(numberOfStack);
	            for (int i = 0; i < numberOfStack; i++) {
	            	writeVerificationTypeInfo(data, i + numberOfLocals);
	            }
	        } else {
	            throw new BytecodeException("Invalid StackMap frame_type: " + frameType);
	        }
			return data;
		}

		public int getFrameType() {
			return frameType;
		}

		public int getOffsetDelta() {
			return offsetDelta;
		}

		public void setOffsetDelta(int offsetDelta) {
			this.offsetDelta = offsetDelta;
		}

		public StackMapEntry addFrame(int tag, int cpPoolIndex, int offset) {
        	this.tag[this.off] = tag;
            if(0 <= tag && tag <= 6) { //TOP INTEGER FLOAT DOUBLE LONG NULL UNINITIALIZED_THIS
            	//no more data
            } else if(tag == 7) {      //OBJECT
            	this.cpPoolIndex[this.off] = cpPoolIndex;
            } else if(tag == 8) {      //UNINITIALIZED
            	this.offset[this.off] = offset;
            } else {
            	throw new BytecodeException("Invalid StackMap tag " + tag);
            }
			this.off++;
			return this;
		}

		public int getNumberOfLocals() {
			return numberOfLocals;
		}

		public void setNumberOfLocals(int numberOfLocals) {
			this.numberOfLocals = numberOfLocals;
		}

		public int getNumberOfStack() {
			return numberOfStack;
		}

		public void setNumberOfStack(int numberOfStack) {
			this.numberOfStack = numberOfStack;
		}
		
	}
	
	public static class StackMapAttributeWriter {
		
		int nameIndex;
		int entryCount;
		StackMapEntry[] entries;
		
		private StackMapAttributeWriter(int nameIndex) {
			this.nameIndex = nameIndex;
			this.entryCount = 0;
			this.entries = new StackMapEntry[128];
		}
		
		public static StackMapAttributeWriter of(int nameIndex) {
			return new StackMapAttributeWriter(nameIndex);
		}
		
		public StackMapAttributeWriter add(StackMapEntry entry) {
			entries[entryCount++] = entry;
			return this;
		}
		
		public StackMapAttribute toStackMapAttribute() {
			Bytes data = new Bytes(1024);
			data.writeInt16(entryCount);
			for(int i = 0; i < entryCount; i++) {
				data.readFromBytes(entries[i].toBytes());
			}
			return new StackMapAttribute(nameIndex, data.available(), data.readAll());
		}
	}
	
	public static class StackMapAttribute extends AttributeInfo {

		public StackMapAttribute(int nameIdx, int len, byte[] data) {
			super(nameIdx, len, data);
			parse();
		}
		
		private int entryCount;
		private byte[] data;
//		private StackMapEntry[] entries;
		
		private void parse() {
			Bytes data = new Bytes(info);
			this.entryCount = data.readInt16();
			this.data = data.readAll();
//			this.entries = new StackMapEntry[this.entryCount];
//			for(int i = 0; i < entryCount; i++) {
//				this.entries[i] = new StackMapEntry(data);
//			}
		}

		public int getEntryCount() {
			return entryCount;
		}

		public byte[] getData() {
			return data;
		}
		
	}

	
	public static class SourceFileAttribute extends AttributeInfo {
		
		public SourceFileAttribute(int nameIdx, int len, byte[] data) {
			super(nameIdx, len, data);
			parse();
		}
		

		public SourceFileAttribute(int nameIdx, int fileNameIndex) {
			super(nameIdx, 0, null);
			setInfo(new byte[] {(byte)((fileNameIndex >> 8) & 0xff), (byte)(fileNameIndex & 0xff)});
		}
		
		private int fileNameIndex;
		
		private void parse() {
			byte[] data = getInfo();
			int b0 = data[0], b1 = data[1];
			if(b0 < 0) b0 = 256 + b0;
			if(b0 < 0) b0 = 256 + b0;
			this.fileNameIndex = (b0 << 8) | b1;
		}

		public void refresh(int nameIdx, int fileNameIndex) {
			super.nameIndex = nameIdx;
			setFileNameIndex(fileNameIndex);
		}
		
		public int getFileNameIndex() {
			return fileNameIndex;
		}

		public void setFileNameIndex(int fileNameIndex) {
			this.fileNameIndex = fileNameIndex;
			setInfo(new byte[] {(byte)((fileNameIndex >> 8) & 0xff), (byte)(fileNameIndex & 0xff)});
		}
	}
	
	public static class InnerClass {

	    private int classIndex;
	    private int outerClassIndex;
	    private int innerNameIndex;
	    private int accessFlag;
	    
	    
	    public void decode(Bytes data) {
	    	classIndex = data.readInt16();
	    	outerClassIndex = data.readInt16();
	    	innerNameIndex = data.readInt16();
	    	accessFlag = data.readInt16();
	    }
	    
	    public Bytes encode() {
	    	Bytes data = new Bytes();
	    	data.writeInt16(classIndex);
	    	data.writeInt16(outerClassIndex);
	    	data.writeInt16(innerNameIndex);
	    	data.writeInt16(accessFlag);
	    	return data;
	    }
	    
		public int getClassIndex() {
			return classIndex;
		}
		public void setClassIndex(int classIndex) {
			this.classIndex = classIndex;
		}
		public int getOuterClassIndex() {
			return outerClassIndex;
		}
		public void setOuterClassIndex(int outerClassIndex) {
			this.outerClassIndex = outerClassIndex;
		}
		public int getInnerNameIndex() {
			return innerNameIndex;
		}
		public void setInnerNameIndex(int innerNameIndex) {
			this.innerNameIndex = innerNameIndex;
		}
		public int getAccessFlag() {
			return accessFlag;
		}
		public void setAccessFlag(int accessFlag) {
			this.accessFlag = accessFlag;
		}
	}
	
	
	public static class InnerClassAttribute extends AttributeInfo {
		
		public InnerClassAttribute(int nameIdx, int len, byte[] data) {
			super(nameIdx, len, data);
			parse();
		}
		
		public InnerClassAttribute(int nameIdx, InnerClass[] innerClass) {
			super(nameIdx, 0, null);
			Bytes data = new Bytes();
			data.writeInt16(innerClass.length);
			for(InnerClass in : innerClass) {
				data.readFromBytes(in.encode());
			}
			length = data.available();
			info = data.readAll();
			
			this.innerClassCount = innerClass.length;
			this.innerClass = innerClass;
		}

		
	    private int innerClassCount;
	    private InnerClass[] innerClass;
	    
		
		private void parse() {
			Bytes data = new Bytes(getInfo());
			innerClassCount = data.readInt16();
			innerClass = new InnerClass[innerClassCount];
			for(int i = 0; i < innerClassCount; i++) {
				innerClass[i] = new InnerClass();
				innerClass[i].decode(data);
			}
		}

	}
}

