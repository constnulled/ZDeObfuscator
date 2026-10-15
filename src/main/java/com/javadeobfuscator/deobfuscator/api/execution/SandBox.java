package com.javadeobfuscator.deobfuscator.api.execution;

import dev.xdark.ssvm.LinkResolver;
import dev.xdark.ssvm.RuntimeResolver;
import dev.xdark.ssvm.VirtualMachine;
import dev.xdark.ssvm.api.VMInterface;
import dev.xdark.ssvm.classloading.BootClassFinder;
import dev.xdark.ssvm.classloading.SupplyingClassLoaderInstaller;
import dev.xdark.ssvm.execution.ExecutionEngine;
import dev.xdark.ssvm.filesystem.FileManager;
import dev.xdark.ssvm.invoke.InvocationUtil;
import dev.xdark.ssvm.memory.management.MemoryManager;
import dev.xdark.ssvm.mirror.type.JavaClass;
import dev.xdark.ssvm.operation.VMOperations;
import dev.xdark.ssvm.symbol.Primitives;
import dev.xdark.ssvm.symbol.Symbols;
import dev.xdark.ssvm.thread.ThreadManager;
import dev.xdark.ssvm.util.Reflection;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.javadeobfuscator.deobfuscator.api.asm.ClassWrapper;
import com.javadeobfuscator.deobfuscator.api.classpath.CombinedClassProvider;
import com.javadeobfuscator.deobfuscator.api.context.Context;
import com.javadeobfuscator.deobfuscator.api.environment.RtJarLocator;
import com.javadeobfuscator.deobfuscator.api.environment.JavaVersion;

/**
 * A wrapper for {@link VirtualMachine} with some additional features and patches
 */
public class SandBox {

  private static final Logger LOGGER = LoggerFactory.getLogger(SandBox.class);

  /**
   * Minimum JVM version the sandbox supports. SSVM targets Java 8 bytecode semantics.
   */
  private static final int MIN_SUPPORTED_JVM_VERSION = 8;

  /**
   * Maximum JVM version the sandbox can emulate. SSVM's bootstrap requires the host to run
   * on Java 17, and its emulation layer supports up to Java 17 class file features.
   */
  private static final int MAX_SUPPORTED_JVM_VERSION = 17;

  private final VirtualMachine vm;
  private final SupplyingClassLoaderInstaller.DataSupplier dataSupplier;
  private final MemoryManager memoryManager;
  private final SupplyingClassLoaderInstaller.Helper helper;
  private final InvocationUtil invocationUtil;

  public SandBox(Context context) {
    this(
        new ClasspathDataSupplier(
            new CombinedClassProvider(context.getCompiledClasses(), context.getLibraries())
        ),
        RtJarLocator.resolve(context.getOptions().rtJarPath()),
        detectTargetJvmVersion(context)
    );
  }

  public SandBox(SupplyingClassLoaderInstaller.DataSupplier dataSupplier, Path rtJarPath) {
    this(dataSupplier, rtJarPath, MIN_SUPPORTED_JVM_VERSION);
  }

  public SandBox(SupplyingClassLoaderInstaller.DataSupplier dataSupplier, Path rtJarPath, int jvmVersion) {
    this(dataSupplier, new VirtualMachine() {
      @Override
      protected BootClassFinder createBootClassFinder() {
        if (rtJarPath == null) {
          throw new IllegalStateException("rt.jar is required for sandbox to run. Please see README.md for instructions");
        }
        return new JarBootClassFinder(rtJarPath);
      }

      @Override
      public int getJvmVersion() {
        return jvmVersion;
      }
    });
  }

  /**
   * Scans all loaded classes in the context and returns the highest Java release version found,
   * clamped to the range [{@link #MIN_SUPPORTED_JVM_VERSION}, {@link #MAX_SUPPORTED_JVM_VERSION}].
   *
   * <p>ASM stores the class file major version in {@code ClassNode.version}. The Java release
   * number is derived by subtracting {@link JavaVersion#VERSION_OFFSET} (44). For example,
   * major version 52 corresponds to Java 8, 55 to Java 11, 61 to Java 17.
   *
   * <p>If the context contains no classes, or all detected versions fall below the minimum,
   * {@link #MIN_SUPPORTED_JVM_VERSION} is returned as a safe default.
   */
  private static int detectTargetJvmVersion(Context context) {
    int maxJavaVersion = context.classes().stream()
        .map(ClassWrapper::classNode)
        .mapToInt(classNode -> classNode.version & 0xFFFF)
        .map(majorVersion -> majorVersion - JavaVersion.VERSION_OFFSET)
        .filter(javaVersion -> javaVersion >= MIN_SUPPORTED_JVM_VERSION)
        .max()
        .orElse(MIN_SUPPORTED_JVM_VERSION);

    int clamped = Math.min(maxJavaVersion, MAX_SUPPORTED_JVM_VERSION);

    if (maxJavaVersion > MAX_SUPPORTED_JVM_VERSION) {
      LOGGER.warn(
          "Target jar requires Java {} but the sandbox only supports up to Java {}. Clamping to Java {}.",
          maxJavaVersion, MAX_SUPPORTED_JVM_VERSION, MAX_SUPPORTED_JVM_VERSION
      );
    } else {
      LOGGER.info("Detected target JVM version: Java {}", clamped);
    }

    return clamped;
  }

  public SandBox(SupplyingClassLoaderInstaller.DataSupplier dataSupplier, VirtualMachine vm) {
    LOGGER.info("Initializing SSVM sandbox...");
    this.dataSupplier = dataSupplier;
    this.vm = vm;

    try {
      this.vm.initialize();
      this.vm.bootstrap();
      this.memoryManager = vm.getMemoryManager();
      // Install all classes from data supplier
      this.helper = SupplyingClassLoaderInstaller.install(vm, this.dataSupplier);
      this.invocationUtil = InvocationUtil.create(vm);
    } catch (Exception ex) {
      LOGGER.error("SSVM bootstrap failed. Make sure that you run this deobfuscator on java 17");

      throw new RuntimeException(ex);
    }
    LOGGER.info("Initialized SSVM sandbox");
  }

  public VirtualMachine vm() {
    return vm;
  }

  public VMInterface getVMInterface() {
    return vm.getInterface();
  }

  public MemoryManager getMemoryManager() {
    return memoryManager;
  }

  public SupplyingClassLoaderInstaller.Helper getHelper() {
    return helper;
  }

  public InvocationUtil getInvocationUtil() {
    return invocationUtil;
  }

  public Symbols getSymbols() {
    return vm.getSymbols();
  }

  public Primitives getPrimitives() {
    return vm.getPrimitives();
  }

  public VMOperations getOperations() {
    return vm.getOperations();
  }

  public LinkResolver getLinkResolver() {
    return vm.getLinkResolver();
  }

  public RuntimeResolver getRuntimeResolver() {
    return vm.getRuntimeResolver();
  }

  public Reflection getReflection() {
    return vm.getReflection();
  }

  public ThreadManager getThreadManager() {
    return vm.getThreadManager();
  }

  public FileManager getFileManager() {
    return vm.getFileManager();
  }

  public ExecutionEngine getExecutionEngine() {
    return vm.getExecutionEngine();
  }

  public List<JavaClass> getUsedCustomClasses() {
    return this.vm.getClassStorage().list().stream()
        .filter(clazz -> this.dataSupplier.getClass(clazz.getName()) != null)
        .toList();
  }
}
