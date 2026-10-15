package com.javadeobfuscator.deobfuscator.transformers.composed;

import com.javadeobfuscator.deobfuscator.api.transformer.ComposedTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.InvalidMethodCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.pool.InlineLocalVariablesTransformer;
import com.javadeobfuscator.deobfuscator.transformers.pool.InlineStaticFieldTransformer;
import com.javadeobfuscator.deobfuscator.transformers.universal.UniversalFlowTransformer;

public class ComposedGeneralFlowTransformer extends ComposedTransformer {

  public ComposedGeneralFlowTransformer() {
    super(
        // Preparation
        InvalidMethodCleanTransformer::new,
        InlineStaticFieldTransformer::new,
        InlineLocalVariablesTransformer::new,

        // Main transformer
        UniversalFlowTransformer::new,

        // Clean up garbage
        ComposedPeepholeCleanTransformer::new
    );
  }
}
