package com.javadeobfuscator.deobfuscator.api.environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RtJarLocator {

  private static final Logger LOGGER = LoggerFactory.getLogger(RtJarLocator.class);

  private RtJarLocator() {
  }

  public static Path resolve(Path configured) {
    if (configured != null) {
      return configured;
    }
    String property = System.getProperty("rtJarPath");
    if (property != null) {
      return Path.of(property);
    }
    Optional<JavaInstall> install = JavaEnv.getJavaInstalls().stream()
        .filter(candidate -> candidate.version() == 8)
        .findFirst();
    if (install.isPresent()) {
      Path candidate = install.get().javaExecutable().getParent().getParent().resolve("jre").resolve("lib").resolve("rt.jar");
      if (Files.exists(candidate)) {
        LOGGER.info("Auto-detected rt.jar path: {}", candidate);
        return candidate;
      }
    }
    LOGGER.error("Failed to auto-detect rt.jar path. Set rtJarPath in the config or install Java 8");
    return null;
  }
}
