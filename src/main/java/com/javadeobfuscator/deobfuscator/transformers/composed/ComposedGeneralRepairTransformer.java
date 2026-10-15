package com.javadeobfuscator.deobfuscator.transformers.composed;

import com.javadeobfuscator.deobfuscator.api.transformer.ComposedTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.ClassDebugInfoCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.MethodDebugInfoCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.SignatureCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.clean.UnknownAttributeCleanTransformer;
import com.javadeobfuscator.deobfuscator.transformers.universal.*;


/**
 * If methods are hidden from decompiler, or something uncommon happen, then this transformer will try to fix it.
 */
public class ComposedGeneralRepairTransformer extends ComposedTransformer {

  public ComposedGeneralRepairTransformer() {
    super(
        AccessRepairTransformer::new,
        AnnotationFilterTransformer::new,
        TryCatchRepairTransformer::new,
        UnknownAttributeCleanTransformer::new,
        SignatureCleanTransformer::new,
        MethodDebugInfoCleanTransformer::new,
        ClassDebugInfoCleanTransformer::new,
        UnusedMethodRemoverTransformer::new
    );
  }
}
