package com.javadeobfuscator.deobfuscator.transformers.universal.number;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.OriginalSourceValue;
import com.javadeobfuscator.deobfuscator.api.helper.AsmHelper;
import com.javadeobfuscator.deobfuscator.api.helper.AsmMathHelper;
import com.javadeobfuscator.deobfuscator.api.helper.FramedInstructionsStream;
import com.javadeobfuscator.deobfuscator.api.transformer.Transformer;

public class ConstantFoldingTransformer extends Transformer {

  @Override
  protected void transform() throws Exception {
    FramedInstructionsStream.of(this).forEach(insnContext -> {
      AbstractInsnNode insn = insnContext.insn();
      int opcode = insn.getOpcode();

      if (AsmMathHelper.isMathBinaryOperation(opcode)) {
        foldBinary(insnContext, opcode);
      } else if (AsmMathHelper.isMathUnaryOperation(opcode)) {
        foldUnary(insnContext, opcode);
      }
    });
  }

  private void foldBinary(com.javadeobfuscator.deobfuscator.api.asm.InsnContext insnContext, int opcode) {
    var frame = insnContext.frame();
    int stackSize = frame.getStackSize();
    if (stackSize < 2) return;

    OriginalSourceValue top = frame.getStack(stackSize - 1);
    OriginalSourceValue second = frame.getStack(stackSize - 2);

    OriginalSourceValue.ConstantValue topConst = top.getConstantValue();
    OriginalSourceValue.ConstantValue secondConst = second.getConstantValue();

    if (topConst == null || secondConst == null) return;
    if (!(topConst.value() instanceof Number topNum) || !(secondConst.value() instanceof Number secondNum)) return;

    if (isDivisionByZero(opcode, topNum)) return;

    Number result;
    try {
      result = AsmMathHelper.mathBinaryOperation(secondNum, topNum, opcode);
    } catch (ArithmeticException e) {
      return;
    }

    AbstractInsnNode replacement = AsmHelper.toConstantInsn(result);
    insnContext.placePops();
    insnContext.methodNode().instructions.set(insnContext.insn(), replacement);
    markChange();
  }

  private void foldUnary(com.javadeobfuscator.deobfuscator.api.asm.InsnContext insnContext, int opcode) {
    var frame = insnContext.frame();
    int stackSize = frame.getStackSize();
    if (stackSize < 1) return;

    OriginalSourceValue top = frame.getStack(stackSize - 1);
    OriginalSourceValue.ConstantValue topConst = top.getConstantValue();

    if (topConst == null) return;
    if (!(topConst.value() instanceof Number num)) return;

    Number result;
    try {
      result = AsmMathHelper.mathUnaryOperation(num, opcode);
    } catch (ArithmeticException e) {
      return;
    }

    AbstractInsnNode replacement = AsmHelper.toConstantInsn(result);
    insnContext.placePops();
    insnContext.methodNode().instructions.set(insnContext.insn(), replacement);
    markChange();
  }

  private boolean isDivisionByZero(int opcode, Number divisor) {
    return switch (opcode) {
      case IDIV, IREM -> divisor.intValue() == 0;
      case LDIV, LREM -> divisor.longValue() == 0L;
      default -> false;
    };
  }
}
