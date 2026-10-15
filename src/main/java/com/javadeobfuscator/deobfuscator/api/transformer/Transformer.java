package com.javadeobfuscator.deobfuscator.api.transformer;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import com.javadeobfuscator.deobfuscator.api.asm.ClassWrapper;
import com.javadeobfuscator.deobfuscator.api.context.Context;
import com.javadeobfuscator.deobfuscator.api.exception.TransformerException;
import com.javadeobfuscator.deobfuscator.api.helper.AsmHelper;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public abstract class Transformer extends AsmHelper implements Opcodes {

    protected static final Logger LOGGER = LoggerFactory.getLogger(Transformer.class);

    private static final ThreadLocal<Integer> NESTING_DEPTH = ThreadLocal.withInitial(() -> 0);

    private static volatile ProgressReporter activeReporter = null;

    public static void setProgressReporter(ProgressReporter reporter) {
        activeReporter = reporter;
    }

    protected static void reportClassProgress(String className) {
        ProgressReporter r = activeReporter;
        if (r != null) r.reportClass(className);
    }

    private Context context = null;
    private ClassWrapper scope = null;

    private boolean hasRan = false;
    public final AtomicInteger changes = new AtomicInteger(0);

    protected boolean rerunOnChange = false;

    private static void increaseDepth() {
        int newDepth = NESTING_DEPTH.get() + 1;
        NESTING_DEPTH.set(newDepth);
        updateIndentContext(newDepth);
    }

    private static void decreaseDepth() {
        int depth = NESTING_DEPTH.get();
        if (depth > 0) {
            int newDepth = depth - 1;
            NESTING_DEPTH.set(newDepth);
            updateIndentContext(newDepth);
        }
    }

    private static void updateIndentContext(int depth) {
    }

    public boolean shouldRerunOnChange() {
        return rerunOnChange;
    }

    protected abstract void transform() throws Exception;

    protected void markChange() {
        this.changes.incrementAndGet();
    }

    public int getChangesCount() {
        return this.changes.get();
    }

    public boolean isChanged() {
        return this.getChangesCount() > 0;
    }

    public String name() {
        return this.getClass().getSimpleName();
    }

    protected List<ClassWrapper> scopedClasses() {
        List<ClassWrapper> classes = this.context.scopedClasses(this.scope);
        ProgressReporter r = activeReporter;
        if (r == null) return classes;
        return new AbstractList<>() {
            @Override
            public ClassWrapper get(int index) {
                ClassWrapper cw = classes.get(index);
                r.reportClass(cw.name());
                return cw;
            }

            @Override
            public int size() {
                return classes.size();
            }
        };
    }

    private void ensureInitialized() {
        if (this.context == null) {
            throw new IllegalStateException("Transformer is not initialized");
        }
    }

    @NotNull
    public Context context() {
        ensureInitialized();
        return context;
    }

    @Nullable
    public ClassWrapper scope() {
        ensureInitialized();
        return scope;
    }

    private void init(Context context, ClassWrapper scope) {
        this.context = context;
        this.scope = scope;
    }

    public static int transform(Supplier<@Nullable Transformer> transformerSupplier, @Nullable ClassWrapper scope, Context context) {
        return transform(transformerSupplier, scope, context, 0);
    }

    private static int transform(
            Supplier<@Nullable Transformer> transformerSupplier,
            @Nullable ClassWrapper scope,
            Context context,
            int rerunsCount
    ) {
        Transformer transformer = transformerSupplier.get();
        if (transformer == null) {
            return 0;
        }

        if (transformer.hasRan) {
            throw new IllegalArgumentException("transformerSupplier tried to reuse transformer instance. You must pass a new instance of transformer");
        }

        transformer.init(context, scope);

        LOGGER.info("▶ {} started", transformer.name());
        long start = System.currentTimeMillis();

        ProgressReporter reporter = activeReporter;
        if (reporter != null) reporter.setTransformerName(transformer.name());

        increaseDepth();

        try {
            try {
                transformer.transform();
                if (reporter != null) reporter.reportDone();
            } catch (TransformerException e) {
                LOGGER.error("! {}: {}", transformer.name(), e.getMessage());
                return 0;
            } catch (Exception e) {
                LOGGER.error("Error occurred when transforming {}", transformer.name(), e);
                if (!context.getOptions().continueOnError()) {
                    throw new RuntimeException(e);
                }
                return 0;
            } finally {
                transformer.hasRan = true;
            }

            LOGGER.info("Made {} changes", transformer.getChangesCount());
        } finally {
            decreaseDepth();
            LOGGER.info("◀ {} ended in {} ms", transformer.name(), (System.currentTimeMillis() - start));
        }

        int changesCount = transformer.getChangesCount();

        if (transformer.isChanged() && transformer.shouldRerunOnChange()) {
            LOGGER.info("\uD83D\uDD04 Changes detected. Rerunning {} transformer (rerun {})", transformer.name(), rerunsCount + 1);
            changesCount += Transformer.transform(transformerSupplier, scope, context, ++rerunsCount);
        }

        if (context.getOptions().verifyBytecode() && rerunsCount == 0 && transformer.isChanged()) {
            try {
                verifyBytecode(scope, context);
            } catch (RuntimeException e) {
                LOGGER.error("Transformer {} produced invalid bytecode", transformer.name(), e);
            }
        }

        return changesCount;
    }

    private static void verifyBytecode(@Nullable ClassWrapper scope, Context context) throws IllegalStateException {
        for (ClassWrapper classWrapper : context.scopedClasses(scope)) {
            for (MethodNode methodNode : classWrapper.methods()) {
                Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicVerifier());
                try {
                    analyzer.analyzeAndComputeMaxs(classWrapper.name(), methodNode);
                } catch (AnalyzerException e) {
                    throw new IllegalStateException("Invalid bytecode in " + classWrapper.name() + "#" + methodNode.name + methodNode.desc, e);
                }
            }
        }
    }
}