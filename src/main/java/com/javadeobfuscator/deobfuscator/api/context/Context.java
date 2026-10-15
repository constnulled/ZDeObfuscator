package com.javadeobfuscator.deobfuscator.api.context;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import software.coley.cafedude.InvalidClassException;
import com.javadeobfuscator.deobfuscator.api.asm.ClassWrapper;
import com.javadeobfuscator.deobfuscator.api.asm.FieldRef;
import com.javadeobfuscator.deobfuscator.api.asm.MethodContext;
import com.javadeobfuscator.deobfuscator.api.asm.MethodRef;
import com.javadeobfuscator.deobfuscator.api.classpath.ClassProvider;
import com.javadeobfuscator.deobfuscator.api.classpath.ClassInfoStorage;
import com.javadeobfuscator.deobfuscator.api.classpath.CombinedClassProvider;
import com.javadeobfuscator.deobfuscator.api.classpath.JvmClassProvider;
import com.javadeobfuscator.deobfuscator.api.helper.ClassHelper;
import com.javadeobfuscator.deobfuscator.api.inheritance.InheritanceGraph;

public class Context implements ClassProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(Context.class);

  private final Map<String, ClassWrapper> classesMap = new ConcurrentHashMap<>();
  private final Map<String, byte[]> filesMap = new ConcurrentHashMap<>();

  private final DeobfuscatorOptions options;

  private final ClassInfoStorage compiledClasses;
  private final ClassInfoStorage libraries;

  /**
   * Creates a new {@link Context} instance from its options
   *
   * @param options Deobfuscator options
   * @param compiledClasses {@link ClassInfoStorage} that holds the original classes of the primary jar
   * @param libraries {@link ClassInfoStorage} that holds the libraries' classes
   */
  public Context(DeobfuscatorOptions options, ClassInfoStorage compiledClasses, ClassInfoStorage libraries) {
    this.options = options;

    this.compiledClasses = compiledClasses;
    this.libraries = libraries;
  }

  public DeobfuscatorOptions getOptions() {
    return options;
  }

  /**
   * Class storage that holds already compiled classes from original jar
   */
  public ClassInfoStorage getCompiledClasses() {
    return compiledClasses;
  }

  /**
   * Class storage that holds libraries' classes
   */
  public ClassInfoStorage getLibraries() {
    return libraries;
  }

  public Collection<ClassWrapper> classes() {
    return classesMap.values();
  }

  public void removeMethod(MethodRef methodRef) {
    ClassWrapper classWrapper = this.getClassesMap().get(methodRef.owner());
    classWrapper.methods().removeIf(methodNode -> methodNode.name.equals(methodRef.name()) && methodNode.desc.equals(methodRef.desc()));
  }

  public void removeField(FieldRef fieldRef) {
    ClassWrapper classWrapper = this.getClassesMap().get(fieldRef.owner());
    classWrapper.fields().removeIf(fieldNode -> fieldNode.name.equals(fieldRef.name()) && fieldNode.desc.equals(fieldRef.desc()));
  }

  public Optional<MethodContext> getMethodContext(MethodRef methodRef) {
    ClassWrapper classWrapper = this.getClassesMap().get(methodRef.owner());
    if (classWrapper == null) return Optional.empty();
    Optional<MethodNode> methodNode = classWrapper.findMethod(methodRef);

    return methodNode.map(node -> MethodContext.of(classWrapper, node));
  }

  @UnmodifiableView
  public List<ClassWrapper> scopedClasses(ClassWrapper scope) {
    return classesMap.values().stream()
        .filter(classWrapper -> scope == null || classWrapper.name().equals(scope.name()))
        .toList();
  }

  public void addCompiledClass(String pathInJar, byte[] bytes) {
    try {
      // Fix class bytes
      bytes = ClassHelper.fixClass(bytes);

      // Class is always a file, not a directory. Remove last slash if it exists
      pathInJar = pathInJar.replaceAll("/$", "");

      ClassWrapper classWrapper = ClassHelper.loadClass(pathInJar, bytes, ClassReader.SKIP_FRAMES);
      this.classesMap.putIfAbsent(classWrapper.name(), classWrapper);

      this.compiledClasses.addRawClass(bytes);
    } catch (InvalidClassException e) {
      LOGGER.error("Failed to load class {}", pathInJar);
    }
  }

  public void addFile(String path, byte[] bytes) {
    this.filesMap.put(path, bytes);
    this.compiledClasses.files().put(path, bytes);
  }

  @Override
  public byte @Nullable [] getClass(String name) {
    byte[] fromCompiled = compiledClasses.getClass(name);
    if (fromCompiled != null) {
      return fromCompiled;
    }
    ClassWrapper classWrapper = classesMap.get(name);
    if (classWrapper == null) {
      return null;
    }
    try {
      InheritanceGraph inheritanceGraph = new InheritanceGraph(this);
      return classWrapper.compileToBytes(inheritanceGraph, org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public byte @Nullable [] getFile(String path) {
    return filesMap.get(path);
  }

  @Override
  public @Nullable ClassNode getClassInfo(String name) {
    ClassWrapper classWrapper = classesMap.get(name);
    if (classWrapper == null) return null;
    return classWrapper.classNode();
  }

  @Override
  public Collection<String> getLoadedClasses() {
    return this.classesMap.keySet();
  }

  public Map<String, ClassWrapper> getClassesMap() {
    return classesMap;
  }

  public Map<String, byte[]> getFilesMap() {
    return filesMap;
  }

  public ClassProvider getFullClassProvider() {
    return new CombinedClassProvider(this, this.getLibraries(), JvmClassProvider.INSTANCE);
  }
}
