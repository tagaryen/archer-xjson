package com.archer.xjson.asm;

import java.util.ArrayList;
import java.util.List;

import com.archer.xjson.asm.AttributeInfo.CodeAttribute;
import com.archer.xjson.asm.AttributeInfo.CodeAttributeWriter;
import com.archer.xjson.asm.constantpool.ConstantPool;


class XNEmitter {

	static final class Label {
		int pc = -1;
	}

	private static final class Branch {
		final int operandPc;
		final Label label;

		Branch(int operandPc, Label label) {
			this.operandPc = operandPc;
			this.label = label;
		}
	}

	private static final class SwitchBranch {
		final int base;
		final int operandPc;
		final Label label;

		SwitchBranch(int base, int operandPc, Label label) {
			this.base = base;
			this.operandPc = operandPc;
			this.label = label;
		}
	}

	private final ConstantPool cp;
	private final CodeAttributeWriter writer;
	private final List<Branch> branches = new ArrayList<>(32);
	private final List<SwitchBranch> switchBranches = new ArrayList<>(32);
	private int depth = 0;
	private int maxDepth = 0;
	private int maxLocals;

	XNEmitter(ConstantPool cp, int codeNameIndex, int initialLocals) {
		this.cp = cp;
		this.writer = CodeAttributeWriter.of(codeNameIndex);
		this.maxLocals = initialLocals;
	}

	Label newLabel() {
		return new Label();
	}

	void mark(Label l) {
		l.pc = writer.currentPc();
	}

	void ifeq(Label l) {
		writer.addInstruction16("ifeq", 0);
		branches.add(new Branch(writer.currentPc() - 2, l));
		pop(1);
	}

	void gotoLabel(Label l) {
		writer.addInstruction16("goto", 0);
		branches.add(new Branch(writer.currentPc() - 2, l));
	}

	void lookupswitch(Label dflt, int[] keys, Label[] labels) {
		writer.addInstruction("lookupswitch");
		int base = writer.currentPc() - 1;
		while (writer.currentPc() % 4 != 0) {
			writer.padByte();
		}
		switchBranches.add(new SwitchBranch(base, writer.currentPc(), dflt));
		writer.writeRawInt32(0);
		writer.writeRawInt32(keys.length);
		for (int i = 0; i < keys.length; i++) {
			writer.writeRawInt32(keys[i]);
			switchBranches.add(new SwitchBranch(base, writer.currentPc(), labels[i]));
			writer.writeRawInt32(0);
		}
		pop(1);
	}

	void iconst(int v) {
		if (v == 0) {
			writer.addInstruction("iconst_0");
		} else if (v == 1) {
			writer.addInstruction("iconst_1");
		} else if (v == -1) {
			writer.addInstruction("iconst_m1");
		} else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
			writer.addInstruction8("bipush", v);
		} else {
			writer.addInstruction16("sipush", v);
		}
		push(1);
	}

	void ireturn() {
		writer.addInstruction("ireturn");
		pop(1);
	}

	void popStack() {
		writer.addInstruction("pop");
		pop(1);
	}

	CodeAttribute toCodeAttribute() {
		patchBranches();
		CodeAttribute attr = writer.toCodeAttribute();
		attr.setMaxStack(Math.max(maxDepth, 1));
		attr.setMaxLocals(Math.max(maxLocals, 1));
		return attr;
	}

	private void patchBranches() {
		for (Branch b : branches) {
			if (b.label.pc < 0) {
				throw new IllegalStateException("unresolved jump label");
			}
			writer.patchInt16(b.operandPc, b.label.pc - (b.operandPc - 1));
		}
		for (SwitchBranch b : switchBranches) {
			if (b.label.pc < 0) {
				throw new IllegalStateException("unresolved switch label");
			}
			writer.patchInt32(b.operandPc, b.label.pc - b.base);
		}
	}

	void aload(int slot) {
		switch (slot) {
			case 0: writer.addInstruction("aload_0"); break;
			case 1: writer.addInstruction("aload_1"); break;
			case 2: writer.addInstruction("aload_2"); break;
			case 3: writer.addInstruction("aload_3"); break;
			default: writer.addInstruction8("aload", slot);
		}
		push(1);
		touchLocals(slot + 1);
	}

	void astore(int slot) {
		switch (slot) {
			case 0: writer.addInstruction("astore_0"); break;
			case 1: writer.addInstruction("astore_1"); break;
			case 2: writer.addInstruction("astore_2"); break;
			case 3: writer.addInstruction("astore_3"); break;
			default: writer.addInstruction8("astore", slot);
		}
		pop(1);
		touchLocals(slot + 1);
	}

	void iload(int slot) {
		load("iload", slot, 1);
	}

	void lload(int slot) {
		load("lload", slot, 2);
	}

	void fload(int slot) {
		load("fload", slot, 1);
	}

	void dload(int slot) {
		load("dload", slot, 2);
	}

	private void load(String mnemonic, int slot, int width) {
		switch (slot) {
			case 0: writer.addInstruction(mnemonic + "_0"); break;
			case 1: writer.addInstruction(mnemonic + "_1"); break;
			case 2: writer.addInstruction(mnemonic + "_2"); break;
			case 3: writer.addInstruction(mnemonic + "_3"); break;
			default: writer.addInstruction8(mnemonic, slot);
		}
		push(width);
		touchLocals(slot + width);
	}

	/** 数值类型转换指令（栈深度不变），如 i2l / l2i / f2d / d2f / l2d / i2b / i2s */
	void conv(String mnemonic) {
		writer.addInstruction(mnemonic);
	}

	void aconstNull() {
		writer.addInstruction("aconst_null");
		push(1);
	}

	void ldcString(String val) {
		int idx = cp.addString(val);
		if (idx <= 0xFF) {
			writer.addInstruction8("ldc", idx);
		} else {
			writer.addInstruction16("ldc_w", idx);
		}
		push(1);
	}

	void ldcClass(String rawClassName) {
		int idx = cp.addClass(rawClassName);
		if (idx <= 0xFF) {
			writer.addInstruction8("ldc", idx);
		} else {
			writer.addInstruction16("ldc_w", idx);
		}
		push(1);
	}

	void invokevirtual(String owner, String name, String desc) {
		int idx = cp.addMethod(name, desc, owner);
		writer.addInstruction16("invokevirtual", idx);
		pop(argSlots(desc) + 1);
		push(retSlots(desc));
	}

	/** 接口方法调用：操作数为 2 字节常量池索引 + count 参数槽数 + 0 保留字节（Java 8 规范） */
	void invokeinterface(String owner, String name, String desc) {
		int idx = cp.addInterfaceMethod(name, desc, owner);
		int count = argSlots(desc) + 1; // 参数槽 + this
		writer.addInstruction32("invokeinterface", (idx << 16) | (count << 8));
		pop(count);
		push(retSlots(desc));
	}

	void invokespecial(String owner, String name, String desc) {
		int idx = cp.addMethod(name, desc, owner);
		writer.addInstruction16("invokespecial", idx);
		pop(argSlots(desc) + 1);
		push(retSlots(desc));
	}

	void invokestatic(String owner, String name, String desc) {
		int idx = cp.addMethod(name, desc, owner);
		writer.addInstruction16("invokestatic", idx);
		pop(argSlots(desc));
		push(retSlots(desc));
	}

	void newObject(String rawClassName) {
		int idx = cp.addClass(rawClassName);
		writer.addInstruction16("new", idx);
		push(1);
	}

	void getfield(String owner, String name, String desc) {
		int idx = cp.addFieldRef(owner, name, desc);
		writer.addInstruction16("getfield", idx);
		pop(1);
		push(fieldSlots(desc));
	}

	void putfield(String owner, String name, String desc) {
		int idx = cp.addFieldRef(owner, name, desc);
		writer.addInstruction16("putfield", idx);
		pop(fieldSlots(desc) + 1);
	}

	void dreturn() {
		writer.addInstruction("dreturn");
		pop(2);
	}

	void freturn() {
		writer.addInstruction("freturn");
		pop(1);
	}

	void lreturn() {
		writer.addInstruction("lreturn");
		pop(2);
	}

	private static int fieldSlots(String desc) {
		return ("D".equals(desc) || "J".equals(desc)) ? 2 : 1;
	}

	void dup() {
		writer.addInstruction("dup");
		push(1);
	}

	void checkcast(String type) {
		int idx = cp.addClass(type);
		writer.addInstruction16("checkcast", idx);
	}

	void areturn() {
		writer.addInstruction("areturn");
		pop(1);
	}

	void vreturn() {
		writer.addInstruction("return");
	}

	private void push(int n) {
		depth += n;
		if (depth > maxDepth) {
			maxDepth = depth;
		}
	}

	private void pop(int n) {
		depth -= n;
	}

	private void touchLocals(int n) {
		if (n > maxLocals) {
			maxLocals = n;
		}
	}

	static int argSlots(String desc) {
		int slots = 0;
		int i = 1;
		while (desc.charAt(i) != ')') {
			char c = desc.charAt(i);
			if (c == 'J' || c == 'D') {
				slots += 2;
				i++;
			} else if (c == 'L') {
				slots++;
				i = desc.indexOf(';', i) + 1;
			} else if (c == '[') {
				slots++;
				while (desc.charAt(i) == '[') {
					i++;
				}
				if (desc.charAt(i) == 'L') {
					i = desc.indexOf(';', i) + 1;
				} else {
					i++;
				}
			} else {
				slots++;
				i++;
			}
		}
		return slots;
	}

	static int retSlots(String desc) {
		char c = desc.charAt(desc.indexOf(')') + 1);
		if (c == 'V') {
			return 0;
		}
		return (c == 'J' || c == 'D') ? 2 : 1;
	}
}
