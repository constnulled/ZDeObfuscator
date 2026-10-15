package com.javadeobfuscator.deobfuscator.transformers.composed;

import com.javadeobfuscator.deobfuscator.api.transformer.ComposedTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.DeadCodeCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.NopCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.UnUsedLabelCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.PopUnUsedLocalVariablesTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.UselessGotosCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.UselessPopCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.peephole.UselessTryCatchCleanTransformer;

/**
 * A transformer that cleans up garbage instructions.
 */
public class ComposedPeepholeCleanTransformer extends ComposedTransformer {

  public ComposedPeepholeCleanTransformer() {
    super(
        // Remove dead code
        DeadCodeCleanTransformer::new,
        // Some more garbage instructions cleanup
        NopCleanTransformer::new,
        UnUsedLabelCleanTransformer::new,
        UselessGotosCleanTransformer::new,
        UselessTryCatchCleanTransformer::new,

        () -> new ComposedTransformer(true,
            // Pop unused local variables stores
            PopUnUsedLocalVariablesTransformer::new,
            // Remove useless POP instructions. This also cleans up garbage var stores from the PopUnUsedLocalVariablesTransformer
            UselessPopCleanTransformer::new
        )
    );
  }
}
