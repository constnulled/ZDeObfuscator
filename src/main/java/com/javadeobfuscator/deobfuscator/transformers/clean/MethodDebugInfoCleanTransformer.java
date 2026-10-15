package com.javadeobfuscator.deobfuscator.transformers.clean;

import com.javadeobfuscator.deobfuscator.api.transformer.ComposedTransformer;

public class MethodDebugInfoCleanTransformer extends ComposedTransformer {
  public MethodDebugInfoCleanTransformer() {
    super(
        LocalVariableNamesCleanTransformer::new,
        ParametersInfoCleanTransformer::new
    );
  }
}
