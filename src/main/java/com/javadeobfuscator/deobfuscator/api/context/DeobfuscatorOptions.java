package com.javadeobfuscator.deobfuscator.api.context;

import org.objectweb.asm.ClassWriter;

public record DeobfuscatorOptions(
    int classWriterFlags,
    boolean printStacktraces,
    boolean continueOnError,
    boolean verifyBytecode) {

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

    public DeobfuscatorOptions build() {
      return new DeobfuscatorOptions(classWriterFlags, printStacktraces, continueOnError, verifyBytecode);
    }
  }
}
