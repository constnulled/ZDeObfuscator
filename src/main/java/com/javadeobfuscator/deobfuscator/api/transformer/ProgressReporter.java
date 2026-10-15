package com.javadeobfuscator.deobfuscator.api.transformer;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public final class ProgressReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressReporter.class);

    private final int total;
    private final AtomicInteger done = new AtomicInteger(0);
    private volatile String transformerName = "";

    public ProgressReporter(int total) {
        this.total = total;
    }

    void setTransformerName(String name) {
        this.transformerName = name;
        done.set(0);
    }

    void reportClass(String className) {
        int current = done.incrementAndGet();
        int pct = total == 0 ? 100 : (int) ((current * 100L) / total);
        LOGGER.debug("[{}/{}  {}%] [{}] {}", current, total, pct, transformerName, className);
    }

    void reportDone() {
        LOGGER.info("[{}/{}  100%] [{}] done", total, total, transformerName);
    }
}
