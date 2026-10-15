package com.javadeobfuscator.deobfuscator.bridge;

import java.nio.file.Path;
import java.util.function.Supplier;

import com.javadeobfuscator.deobfuscator.api.context.DeobfuscatorOptions;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class XBackedTransformer extends Transformer<XBackedTransformer.Config> {

    @Override
    public boolean transform() throws Throwable {
        Class<? extends com.javadeobfuscator.deobfuscator.api.transformer.Transformer> target = getConfig().getTarget();
        logger.info("Running {}", target.getSimpleName());
        Supplier<com.javadeobfuscator.deobfuscator.api.transformer.Transformer> supplier = () -> {
            try {
                return target.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Could not instantiate " + target.getName(), e);
            }
        };
        String rtJar = getDeobfuscator().getConfig().getRtJarPath();
        DeobfuscatorOptions options = DeobfuscatorOptions.builder()
                .continueOnError(getDeobfuscator().getConfig().isContinueOnError())
                .verifyBytecode(getDeobfuscator().getConfig().isVerifyEach())
                .rtJarPath(rtJar == null ? null : Path.of(rtJar))
                .build();
        int changes = XBridge.run(getDeobfuscator(), classes, classpath, supplier, options);
        getDeobfuscator().resetHierachy();
        return changes > 0;
    }

    public static class Config extends TransformerConfig {
        private final Class<? extends com.javadeobfuscator.deobfuscator.api.transformer.Transformer> target;

        public Config(Class<? extends com.javadeobfuscator.deobfuscator.api.transformer.Transformer> target) {
            super(XBackedTransformer.class);
            this.target = target;
        }

        public Class<? extends com.javadeobfuscator.deobfuscator.api.transformer.Transformer> getTarget() {
            return target;
        }
    }
}
