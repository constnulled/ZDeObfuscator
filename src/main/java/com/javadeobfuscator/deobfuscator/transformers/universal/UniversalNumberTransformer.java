package com.javadeobfuscator.deobfuscator.transformers.universal;

import com.javadeobfuscator.deobfuscator.api.transformer.ComposedTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.UselessPopCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.universal.number.*;

/**
 * Simplifies number operations on constant values.
 */
public class UniversalNumberTransformer extends ComposedTransformer {
  public UniversalNumberTransformer() {
    super(
        ConstantFoldingTransformer::new,
        InlineConstantValuesTransformer::new,
        EmptyArrayLengthTransformer::new,
        UselessPopCleanTransformer::new
    );
  }
}
