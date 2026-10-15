package com.javadeobfuscator.deobfuscator.api.context;

import java.nio.file.Path;

import org.objectweb.asm.ClassWriter;

public record DeobfuscatorOptions(
    int classWriterFlags,
    boolean printStacktraces,
    boolean continueOnError,
    boolean verifyBytecode,
    Path rtJarPath,
    int sandboxMaxJvmVersion) {

  public static DeobfuscatorOptions defaults() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int classWriterFlags = ClassWriter.COMPUTE_FRAMES;
    private boolean printStacktraces = true;
    private boolean continueOnError = false;
    private boolean verifyBytecode = false;
    private Path rtJarPath = null;
    private int sandboxMaxJvmVersion = 17;

    private Builder() {
    }

    public Builder classWriterFlags(int classWriterFlags) {
      this.classWriterFlags = classWriterFlags;
      return this;
    }

    public Builder printStacktraces(boolean printStacktraces) {
      this.printStacktraces = printStacktraces;
      return this;
    }

    public Builder continueOnError(boolean continueOnError) {
      this.continueOnError = continueOnError;
      return this;
    }

    public Builder verifyBytecode(boolean verifyBytecode) {
      this.verifyBytecode = verifyBytecode;
      return this;
    }

    public Builder rtJarPath(Path rtJarPath) {
      this.rtJarPath = rtJarPath;
      return this;
    }

    public Builder sandboxMaxJvmVersion(int sandboxMaxJvmVersion) {
      this.sandboxMaxJvmVersion = sandboxMaxJvmVersion;
      return this;
    }

    public DeobfuscatorOptions build() {
      return new DeobfuscatorOptions(classWriterFlags, printStacktraces, continueOnError, verifyBytecode, rtJarPath, sandboxMaxJvmVersion);
    }
  }
}
