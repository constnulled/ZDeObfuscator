package com.javadeobfuscator.deobfuscator.transformers.universal;

import com.javadeobfuscator.deobfuscator.api.transformer.ComposedTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.DeadCodeCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.universal.flow.CleanRedundantJumpsTransformer;
import com.javadeobfuscator.deobfuscator.transformers.universal.flow.CleanRedundantSwitchesTransformer;

public class UniversalFlowTransformer extends ComposedTransformer {
  public UniversalFlowTransformer() {
    super(
        // Clean up redundant ifs and switches
        CleanRedundantJumpsTransformer::new,
        CleanRedundantSwitchesTransformer::new,

        // Resolve all number operations
        UniversalNumberTransformer::new,

        // Last thing will be to clean up all dead code that is unreachable
        DeadCodeCleanTransformer::new
    );
  }
}
