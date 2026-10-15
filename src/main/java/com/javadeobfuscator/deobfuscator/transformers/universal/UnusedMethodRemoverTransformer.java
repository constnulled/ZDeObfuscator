package com.javadeobfuscator.deobfuscator.transformers.universal;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.api.asm.ClassWrapper;
import com.javadeobfuscator.deobfuscator.api.asm.MethodRef;
import com.javadeobfuscator.deobfuscator.api.inheritance.InheritanceGraph;
import com.javadeobfuscator.deobfuscator.api.inheritance.InheritanceVertex;
import com.javadeobfuscator.deobfuscator.api.transformer.Transformer;

import java.util.HashSet;
import java.util.Set;

public class UnusedMethodRemoverTransformer extends Transformer {

  private static final String MAIN_DESC = "([Ljava/lang/String;)V";

  @Override
  protected void transform() throws Exception {
    InheritanceGraph inheritanceGraph = new InheritanceGraph(context());

    Set<MethodRef> calledRefs = collectCalledRefs();

    scopedClasses().forEach(classWrapper -> {
      boolean classIsPublicOrProtected = isAccess(classWrapper.classNode().access, ACC_PUBLIC)
          || isAccess(classWrapper.classNode().access, ACC_PROTECTED);

      classWrapper.methods().removeIf(methodNode -> {
        if (isMandatoryMethod(methodNode)) {
          return false;
        }

        MethodRef ref = MethodRef.of(classWrapper.classNode(), methodNode);

        if (calledRefs.contains(ref)) {
          return false;
        }

        if (classIsPublicOrProtected && isPublicOrProtected(methodNode)) {
          return false;
        }

        if (isLibraryOverride(inheritanceGraph, classWrapper, methodNode)) {
          return false;
        }

        LOGGER.info("Removing unused method: {}", ref);
        markChange();
        return true;
      });
    });

    LOGGER.info("Removed {} unused methods", getChangesCount());
  }

  private Set<MethodRef> collectCalledRefs() {
    Set<MethodRef> called = new HashSet<>();

    context().classes().forEach(classWrapper ->
        classWrapper.methods().forEach(methodNode -> {
          for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
            if (insn instanceof MethodInsnNode methodInsn) {
              called.add(MethodRef.of(methodInsn));
            } else if (insn instanceof InvokeDynamicInsnNode indyInsn) {
              called.add(MethodRef.of(indyInsn.bsm));
              for (Object arg : indyInsn.bsmArgs) {
                if (arg instanceof Handle handle) {
                  called.add(MethodRef.of(handle));
                }
              }
            }
          }
        })
    );

    return called;
  }

  private static boolean isMandatoryMethod(MethodNode methodNode) {
    return methodNode.name.equals("<init>")
        || methodNode.name.equals("<clinit>")
        || (methodNode.name.equals("main") && methodNode.desc.equals(MAIN_DESC)
            && isAccess(methodNode.access, ACC_PUBLIC) && isAccess(methodNode.access, ACC_STATIC));
  }

  private static boolean isPublicOrProtected(MethodNode methodNode) {
    return isAccess(methodNode.access, ACC_PUBLIC) || isAccess(methodNode.access, ACC_PROTECTED);
  }

  private static boolean isLibraryOverride(
      InheritanceGraph inheritanceGraph,
      ClassWrapper classWrapper,
      MethodNode methodNode
  ) {
    InheritanceVertex vertex = inheritanceGraph.getVertex(classWrapper.name());
    if (vertex == null) {
      return true;
    }
    return vertex.isLibraryMethod(methodNode.name, methodNode.desc);
  }
}
