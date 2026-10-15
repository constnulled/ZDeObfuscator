package com.javadeobfuscator.deobfuscator.transformers.clean;

import com.javadeobfuscator.deobfuscator.api.transformer.Transformer;

/**
 * Removes local variable names.
 */
public class LocalVariableNamesCleanTransformer extends Transformer {

  @Override
  protected void transform() throws Exception {
    scopedClasses().stream()
        .flatMap(classWrapper -> classWrapper.methods().stream())
        .forEach(methodNode -> {
          methodNode.localVariables = null;
          this.markChange();
        });
  }
}
