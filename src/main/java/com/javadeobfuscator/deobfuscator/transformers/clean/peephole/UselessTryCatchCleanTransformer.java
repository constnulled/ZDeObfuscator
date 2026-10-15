package com.javadeobfuscator.deobfuscator.transformers.clean.peephole;

import org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.api.transformer.Transformer;

public class UselessTryCatchCleanTransformer extends Transformer {
  @Override
  protected void transform() throws Exception {
    scopedClasses().forEach(classWrapper -> classWrapper.methods().forEach(methodNode -> {
      methodNode.tryCatchBlocks.removeIf(tryCatchBlock -> {
        AbstractInsnNode nextInsn = tryCatchBlock.handler.next();
        if (nextInsn != null && nextInsn.getOpcode() == ATHROW) {
          // Useless try-catch block
          markChange();
          return true;
        }
        return false;
      });
    }));
  }
}
