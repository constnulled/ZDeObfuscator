package com.javadeobfuscator.deobfuscator.transformers.universal;

import com.javadeobfuscator.deobfuscator.api.transformer.Transformer;

public class RecoverSyntheticsTransformer extends Transformer {
  @Override
  protected void transform() throws Exception {
    scopedClasses().forEach(classWrapper -> {
      classWrapper.methods().forEach(methodNode -> {
        // Recover by name
        if (methodNode.name.startsWith("lambda$")) {
          // Mark as synthetic
          methodNode.access |= ACC_SYNTHETIC;
          markChange();
        }
      });
    });
  }
}
