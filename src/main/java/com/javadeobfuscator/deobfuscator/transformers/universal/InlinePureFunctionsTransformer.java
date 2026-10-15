package com.javadeobfuscator.deobfuscator.transformers.universal;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import com.javadeobfuscator.deobfuscator.api.asm.MethodRef;
import com.javadeobfuscator.deobfuscator.api.transformer.Transformer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transformer that inlines pure functions, which are functions that do not have side effects
 * and always return the same result for the same input.
 */
public class InlinePureFunctionsTransformer extends Transformer {
  // List of opcodes that may cause a side effect.
  private static final List<Integer> IMPURE_OPCODES = List.of(
      GETFIELD,
      GETSTATIC,
      INVOKEDYNAMIC,
      INVOKEINTERFACE,
      INVOKESPECIAL,
      INVOKESTATIC,
      INVOKEVIRTUAL,
      NEW,
      PUTFIELD,
      PUTSTATIC
  );

  private final boolean removePureMethods;

  public InlinePureFunctionsTransformer() {
    this(true);
  }

  public InlinePureFunctionsTransformer(boolean removePureMethods) {
    this.removePureMethods = removePureMethods;
  }

  @Override
  protected void transform() throws Exception {
    Set<MethodRef> pureMethods = new HashSet<>();
    scopedClasses().forEach(classWrapper -> classWrapper.methods().forEach(methodNode -> {
      if (methodNode.instructions.size() == 0) {
        return; // Skip empty methods
      }
      if (methodNode.name.equals("<init>") || methodNode.name.equals("<clinit>")) {
        return; // Skip constructors and class initializers
      }

      boolean isPure = Arrays.stream(methodNode.instructions.toArray()).noneMatch(insn -> IMPURE_OPCODES.contains(insn.getOpcode()));

      if (isPure) {
        LOGGER.info("Detected pure function: {}#{}{}", classWrapper.name(), methodNode.name, methodNode.desc);

        pureMethods.add(MethodRef.of(classWrapper.classNode(), methodNode));
      }
    }));

    Set<MethodRef> inlinedEverywhere = new HashSet<>(pureMethods);

    // Inline pure methods
    scopedClasses().forEach(classWrapper -> classWrapper.methods().forEach(methodNode -> {
      for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
        if (insn instanceof MethodInsnNode methodInsn) {
          MethodRef methodCallRef = MethodRef.of(methodInsn);
          if (pureMethods.contains(methodCallRef)) {
            LOGGER.info("Inlining pure method: {} in {}#{}{}", methodCallRef, classWrapper.name(), methodNode.name, methodNode.desc);
            inlinePureMethod(methodNode, methodCallRef, methodInsn);
            markChange();
          }
        }
      }
    }));

    // After inlining within scoped classes, check if any call sites remain in classes
    // outside the scope (e.g. partial jars). If so, keep the method — removing it would break those callers.
    context().classes().forEach(classWrapper -> classWrapper.methods().forEach(methodNode -> {
      for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
        if (insn instanceof MethodInsnNode methodInsn) {
          MethodRef ref = MethodRef.of(methodInsn);
          inlinedEverywhere.remove(ref);
        }
      }
    }));

    if (removePureMethods) inlinedEverywhere.forEach(pureMethodRef -> context().removeMethod(pureMethodRef));
  }

  /**
   * Inline a pure method into the caller method.
   */
  private void inlinePureMethod(MethodNode callerMethod, MethodRef pureMethodRef, MethodInsnNode methodCallerInsn) {
    MethodNode pureMethod = context().getMethodContext(pureMethodRef).orElseThrow().methodNode();

    Type[] paramTypes = Type.getArgumentTypes(pureMethod.desc);
    int[] varIndices = new int[paramTypes.length];
    int nextVarIndex = 0;
    for (int i = 0; i < paramTypes.length; i++) {
      varIndices[i] = callerMethod.maxLocals + nextVarIndex;
      nextVarIndex += paramTypes[i].getSize();
    }
    for (int i = paramTypes.length - 1; i >= 0; i--) {
      VarInsnNode storeInsn = new VarInsnNode(paramTypes[i].getOpcode(Opcodes.ISTORE), varIndices[i]);
      callerMethod.instructions.insertBefore(methodCallerInsn, storeInsn);
    }

    // Clone labels
    Map<LabelNode, LabelNode> clonedLabels = Arrays.stream(pureMethod.instructions.toArray())
        .filter(insn -> insn instanceof LabelNode)
        .map(labelNode -> (LabelNode) labelNode)
        .collect(Collectors.toMap(
            labelNode -> labelNode,
            labelNode -> new LabelNode()
        ));

    // Prepare end label
    LabelNode endLabel = new LabelNode();
    // Insert end label at the end of the caller method
    callerMethod.instructions.insert(methodCallerInsn, endLabel);

    // Copy the pure method instructions into the caller method
    for (AbstractInsnNode pureInsn : pureMethod.instructions.toArray()) {
      AbstractInsnNode clonedInsn = pureInsn.clone(clonedLabels);

      // Remap the variable index
      if (clonedInsn instanceof VarInsnNode varInsn) {
        varInsn.var += callerMethod.maxLocals;
      } else if (clonedInsn instanceof IincInsnNode iincInsn) {
        iincInsn.var += callerMethod.maxLocals;
      }

      if (clonedInsn.getOpcode() >= IRETURN && clonedInsn.getOpcode() <= RETURN) {
        // Instead of the return, insert jump to the end label
        callerMethod.instructions.insertBefore(methodCallerInsn, new JumpInsnNode(GOTO, endLabel));
      } else {
        // Clone instruction as it is
        callerMethod.instructions.insertBefore(methodCallerInsn, clonedInsn);
      }
    }

    // Remove the method call instruction
    callerMethod.instructions.remove(methodCallerInsn);

    // Recompute max locals
    callerMethod.maxLocals = computeMaxLocals(callerMethod);
  }

  private static int computeMaxLocals(final MethodNode method) {
    int maxLocals = Type.getArgumentsAndReturnSizes(method.desc) >> 2;
    if ((method.access & Opcodes.ACC_STATIC) != 0) {
      maxLocals -= 1;
    }
    for (AbstractInsnNode insnNode : method.instructions) {
      if (insnNode instanceof VarInsnNode) {
        int local = ((VarInsnNode) insnNode).var;
        int size =
            (insnNode.getOpcode() == Opcodes.LLOAD
                || insnNode.getOpcode() == Opcodes.DLOAD
                || insnNode.getOpcode() == Opcodes.LSTORE
                || insnNode.getOpcode() == Opcodes.DSTORE)
                ? 2
                : 1;
        maxLocals = Math.max(maxLocals, local + size);
      } else if (insnNode instanceof IincInsnNode) {
        int local = ((IincInsnNode) insnNode).var;
        maxLocals = Math.max(maxLocals, local + 1);
      }
    }
    return maxLocals;
  }
}
