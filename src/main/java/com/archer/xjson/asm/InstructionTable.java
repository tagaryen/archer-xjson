package com.archer.xjson.asm;


public class InstructionTable {
	private static final String[] TABLE = {
		    "nop",
		    "aconst_null",
		    "iconst_m1",
		    "iconst_0",
		    "iconst_1",
		    "iconst_2",
		    "iconst_3",
		    "iconst_4",
		    "iconst_5",
		    "lconst_0",
		    "lconst_1",
		    "fconst_0",
		    "fconst_1",
		    "fconst_2",
		    "dconst_0",
		    "dconst_1",
		    "bipush",
		    "sipush",
		    "ldc",
		    "ldc_w",
		    "ldc2_w",
		    "iload",
		    "lload",
		    "fload",
		    "dload",
		    "aload",
		    "iload_0",
		    "iload_1",
		    "iload_2",
		    "iload_3",
		    "lload_0",
		    "lload_1",
		    "lload_2",
		    "lload_3",
		    "fload_0",
		    "fload_1",
		    "fload_2",
		    "fload_3",
		    "dload_0",
		    "dload_1",
		    "dload_2",
		    "dload_3",
		    "aload_0",
		    "aload_1",
		    "aload_2",
		    "aload_3",
		    "iaload",
		    "laload",
		    "faload",
		    "daload",
		    "aaload",
		    "baload",
		    "caload",
		    "saload",
		    "istore",
		    "lstore",
		    "fstore",
		    "dstore",
		    "astore",
		    "istore_0",
		    "istore_1",
		    "istore_2",
		    "istore_3",
		    "lstore_0",
		    "lstore_1",
		    "lstore_2",
		    "lstore_3",
		    "fstore_0",
		    "fstore_1",
		    "fstore_2",
		    "fstore_3",
		    "dstore_0",
		    "dstore_1",
		    "dstore_2",
		    "dstore_3",
		    "astore_0",
		    "astore_1",
		    "astore_2",
		    "astore_3",
		    "iastore",
		    "lastore",
		    "fastore",
		    "dastore",
		    "aastore",
		    "bastore",
		    "castore",
		    "sastore",
		    "pop",
		    "pop2",
		    "dup",
		    "dup_x1",
		    "dup_x2",
		    "dup2",
		    "dup2_x1",
		    "dup2_x2",
		    "swap",
		    "iadd",
		    "ladd",
		    "fadd",
		    "dadd",
		    "isub",
		    "lsub",
		    "fsub",
		    "dsub",
		    "imul",
		    "lmul",
		    "fmul",
		    "dmul",
		    "idiv",
		    "ldiv",
		    "fdiv",
		    "ddiv",
		    "irem",
		    "lrem",
		    "frem",
		    "drem",
		    "ineg",
		    "lneg",
		    "fneg",
		    "dneg",
		    "ishl",
		    "lshl",
		    "ishr",
		    "lshr",
		    "iushr",
		    "lushr",
		    "iand",
		    "land",
		    "ior",
		    "lor",
		    "ixor",
		    "lxor",
		    "iinc",
		    "i2l",
		    "i2f",
		    "i2d",
		    "l2i",
		    "l2f",
		    "l2d",
		    "f2i",
		    "f2l",
		    "f2d",
		    "d2i",
		    "d2l",
		    "d2f",
		    "i2b",
		    "i2c",
		    "i2s",
		    "lcmp",
		    "fcmpl",
		    "fcmpg",
		    "dcmpl",
		    "dcmpg",
		    "ifeq",
		    "ifne",
		    "iflt",
		    "ifge",
		    "ifgt",
		    "ifle",
		    "if_icmpeq",
		    "if_icmpne",
		    "if_icmplt",
		    "if_icmpge",
		    "if_icmpgt",
		    "if_icmple",
		    "if_acmpeq",
		    "if_acmpne",
		    "goto",
		    "jsr",
		    "ret",
		    "tableswitch",
		    "lookupswitch",
		    "ireturn",
		    "lreturn",
		    "freturn",
		    "dreturn",
		    "areturn",
		    "return",
		    "getstatic",
		    "putstatic",
		    "getfield",
		    "putfield",
		    "invokevirtual",
		    "invokespecial",
		    "invokestatic",
		    "invokeinterface",
		    "--",
		    "new",
		    "newarray",
		    "anewarray",
		    "arraylength",
		    "athrow",
		    "checkcast",
		    "instanceof",
		    "monitorenter",
		    "monitorexit",
		    "wide",
		    "multianewarray",
		    "ifnull",
		    "ifnonnull",
		    "goto_w",
		    "jsr_w"
		   };
	
	public static String get(int index) {
		return TABLE[index];
	}
	
	public static String get(byte opcode) {
		int idx = (int)opcode;
		return TABLE[idx < 0 ? idx + 256 : idx];
	}
	
	public static byte getInstructionCode(String instruction) {
		for(int i = 0; i < TABLE.length; i++) {
			if(TABLE[i].equals(instruction)) {
				return (byte) i;
			}
		}
		throw new IllegalArgumentException("invalid instruction " + instruction);
	}

	public static Pair<Integer, Integer> calculateMaxs(byte[] code) {
		int maxStack = 0, maxLocals = 0;
		int padding;
		for(int i = 0; i < code.length; i++) {
			switch(code[i]) {
			    case 0:        //nop
			        break;
			    case 1:        //aconst_null
			    case 2:        //iconst_m1
			    case 3:        //iconst_0
			    case 4:        //iconst_1
			    case 5:        //iconst_2
			    case 6:        //iconst_3
			    case 7:        //iconst_4
			    case 8:        //iconst_5
			    	maxStack++;
			    	maxLocals++;
			    	break;
			    case 9:        //lconst_0
			    case 10:        //lconst_1
			    	maxStack += 2;
			    	maxLocals += 2;
			    	break;
			    case 11:        //fconst_0
			    case 12:        //fconst_1
			    case 13:        //fconst_2
			    case 14:        //dconst_0
			    case 15:        //dconst_1
			    case 16:        //bipush
			    	i++;
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case 17:        //sipush
			    	i += 2;
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case 18:        //ldc
		    	i++;
		    	maxStack += 2;
		    	maxLocals += 2;
		        break;
		    case 19:        //ldc_w
		    case 20:        //ldc2_w
		    	i += 2;
		    	maxStack += 2;
		    	maxLocals += 2;
		        break;
		    case 21:        //iload
		    	i++;
		    	maxStack++;
		    	maxLocals++;
		        break;
		    case 22:        //lload
		    case 23:        //fload
		    case 24:        //dload
		    case 25:        //aload
		    	i++;
		    	maxStack += 2;
		    	maxLocals += 2;
		        break;
			    case 26:        //iload_0
			    case 27:        //iload_1
			    case 28:        //iload_2
			    case 29:        //iload_3
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case 30:        //lload_0
			    case 31:        //lload_1
			    case 32:        //lload_2
			    case 33:        //lload_3
			    case 34:        //fload_0
			    case 35:        //fload_1
			    case 36:        //fload_2
			    case 37:        //fload_3
			    case 38:        //dload_0
			    case 39:        //dload_1
			    case 40:        //dload_2
			    case 41:        //dload_3
			    	maxStack += 2;
			    	maxLocals += 2;
			        break;
			    case 42:        //aload_0
			    case 43:        //aload_1
			    case 44:        //aload_2
			    case 45:        //aload_3
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case 46:        //iaload
			    case 47:        //laload
			    case 48:        //faload
			    case 49:        //daload
			    case 50:        //aaload
			    case 51:        //baload
			    case 52:        //caload
			    case 53:        //saload
			    	maxStack += 2;
			    	maxLocals += 2;
			        break;
			    case 54:        //istore
			    case 55:        //lstore
			    case 56:        //fstore
			    case 57:        //dstore
			    case 58:        //astore
			    	i++;
			    	break;
			    case 59:        //istore_0
			    case 60:        //istore_1
			    case 61:        //istore_2
			    case 62:        //istore_3
			    case 63:        //lstore_0
			    case 64:        //lstore_1
			    case 65:        //lstore_2
			    case 66:        //lstore_3
			    case 67:        //fstore_0
			    case 68:        //fstore_1
			    case 69:        //fstore_2
			    case 70:        //fstore_3
			    case 71:        //dstore_0
			    case 72:        //dstore_1
			    case 73:        //dstore_2
			    case 74:        //dstore_3
			    case 75:        //astore_0
			    case 76:        //astore_1
			    case 77:        //astore_2
			    case 78:        //astore_3
			    case 79:        //iastore
			    case 80:        //lastore
			    case 81:        //fastore
			    case 82:        //dastore
			    case 83:        //aastore
			    case 84:        //bastore
			    case 85:        //castore
			    case 86:        //sastore
			    case 87:        //pop
			    case 88:        //pop2
			    	break;
			    case 89:        //dup
			    case 90:        //dup_x1
			    case 91:        //dup_x2
			    case 92:        //dup2
			    case 93:        //dup2_x1
			    case 94:        //dup2_x2
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case 95:        //swap
			    case 96:        //iadd
			    case 97:        //ladd
			    case 98:        //fadd
			    case 99:        //dadd
			    case 100:        //isub
			    case 101:        //lsub
			    case 102:        //fsub
			    case 103:        //dsub
			    case 104:        //imul
			    case 105:        //lmul
			    case 106:        //fmul
			    case 107:        //dmul
			    case 108:        //idiv
			    case 109:        //ldiv
			    case 110:        //fdiv
			    case 111:        //ddiv
			    case 112:        //irem
			    case 113:        //lrem
			    case 114:        //frem
			    case 115:        //drem
			    case 116:        //ineg
			    case 117:        //lneg
			    case 118:        //fneg
			    case 119:        //dneg
			    case 120:        //ishl
			    case 121:        //lshl
			    case 122:        //ishr
			    case 123:        //lshr
			    case 124:        //iushr
			    case 125:        //lushr
			    case 126:        //iand
			    case 127:        //land
			    case -128:        //ior
			    case -127:        //lor
			    case -126:        //ixor
			    case -125:        //lxor
			    case -124:        //iinc
			    	i += 2;
			        break;
			    case -123:        //i2l
			    case -122:        //i2f
			    case -121:        //i2d
			    case -120:        //l2i
			    case -119:        //l2f
			    case -118:        //l2d
			    case -117:        //f2i
			    case -116:        //f2l
			    case -115:        //f2d
			    case -114:        //d2i
			    case -113:        //d2l
			    case -112:        //d2f
			    case -111:        //i2b
			    case -110:        //i2c
			    case -109:        //i2s
			    case -108:        //lcmp
			    case -107:        //fcmpl
			    case -106:        //fcmpg
			    case -105:        //dcmpl
			    case -104:        //dcmpg
			        break;
			    case -103:        //ifeq
			    case -102:        //ifne
			    case -101:        //iflt
			    case -100:        //ifge
			    case -99:        //ifgt
			    case -98:        //ifle
			    case -97:        //if_icmpeq
			    case -96:        //if_icmpne
			    case -95:        //if_icmplt
			    case -94:        //if_icmpge
			    case -93:        //if_icmpgt
			    case -92:        //if_icmple
			    case -91:        //if_acmpeq
			    case -90:        //if_acmpne
			    case -89:        //goto
			    	i += 2;
			        break;
			    case -88:        //jsr
			    	i += 2;
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case -87:        //ret
			    	i++;
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case -86:        //tableswitch
					padding = (4 - (i + 1) % 4) % 4;
					int tsBase = i + 1 + padding;
					int low = readInt32(code, tsBase + 4);
					int high = readInt32(code, tsBase + 8);
					i = tsBase + 11 + (high - low + 1) * 4;
					maxStack++;
					maxLocals++;
					break;
				case -85:        //lookupswitch
					padding = (4 - (i + 1) % 4) % 4;
					int lsBase = i + 1 + padding;
					int npairs = readInt32(code, lsBase + 4);
					i = lsBase + 7 + npairs * 8;
					maxStack++;
					maxLocals++;
					break;
			    case -84:        //ireturn
			        break;
			    case -83:        //lreturn
			    case -82:        //freturn
			    case -81:        //dreturn
			    case -80:        //areturn
			    case -79:        //return
			    	break;
			    case -78:        //getstatic
			    case -77:        //putstatic
			    case -76:        //getfield
			    case -75:        //putfield
			    case -74:        //invokevirtual
			    case -73:        //invokespecial
			    case -72:        //invokestatic
			    	i += 2;
			        break;
			    case -71:        //invokeinterface
					i += 4;
					maxStack += 2;
					maxLocals += 2;
					break;
				case -70:        //invokedynamic
					i += 4;
					break;
				case -69:        //new
					i += 2;
					maxStack += 2;
					maxLocals += 2;
					break;
				case -68:        //newarray
					i++;
					maxStack += 2;
					maxLocals += 2;
					break;
				case -67:        //anewarray
					i += 2;
					maxStack += 2;
					maxLocals += 2;
					break;
			    case -66:        //arraylength
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case -65:        //athrow
			    	maxStack += 2;
			    	maxLocals += 2;
			        break;
			    case -64:        //checkcast
			    case -63:        //instanceof
			    	i += 2;
			    	maxStack += 2;
			    	maxLocals += 2;
			        break;
			    case -62:        //monitorenter
			    case -61:        //monitorexit
			    	maxStack += 2;
			    	maxLocals += 2;
			        break;
			    case -60:        //wide
		    	i += ((code[i + 1] & 0xff) == 132) ? 5 : 3;
		    	maxStack += 2;
		    	maxLocals += 2;
		        break;
			    case -59:        //multianewarray
			    	i += 3;
			    	maxStack += 2;
			    	maxLocals += 2;
			        break;
			    case -58:        //ifnull
			    case -57:        //ifnonnull
			    	i += 2;
			    	maxStack++;
			    	maxLocals++;
			        break;
			    case -56:        //goto_w
			    case -55:        //jsr_w
			    	i += 4;
			        break;
			    default:
					throw new BytecodeException("Invalid code instruction " + code[i]);
			}
		}
		return new Pair<>(maxStack, maxLocals);
	}

	private static int readInt32(byte[] code, int off) {
		return ((code[off] & 0xff) << 24) | ((code[off + 1] & 0xff) << 16)
				| ((code[off + 2] & 0xff) << 8) | (code[off + 3] & 0xff);
	}
}
