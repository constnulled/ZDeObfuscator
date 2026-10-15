package com.javadeobfuscator.deobfuscator.api.classpath;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.api.helper.ClassHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classpath that fetches default JVM classes
 */
public class JvmClassProvider implements ClassProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(JvmClassProvider.class);

  public static final JvmClassProvider INSTANCE = new JvmClassProvider();

  private final Map<String, byte[]> classesCache = new ConcurrentHashMap<>();
  private final Map<String, ClassNode> classInfoCache = new ConcurrentHashMap<>();

  private JvmClassProvider() {
  }

  @Override
  public byte @Nullable [] getClass(String name) {
    if (classesCache.containsKey(name)) {
      return classesCache.get(name);
    }

    // Try to find it in classloader
    byte[] value = null;
    try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
      if (in != null) {
        value = in.readAllBytes();
      }
    } catch (IOException ex) {
      LOGGER.error("Failed to fetch runtime bytecode of class: {}", name, ex);
    }

    if (value == null) return null;

    // Cache it!
    classesCache.put(name, value);

    return value;
  }

  @Override
  public byte @Nullable [] getFile(String path) {
    // JVM classpath doesn't have files
    return null;
  }

  @Override
  public @Nullable ClassNode getClassInfo(String name) {
    if (classInfoCache.containsKey(name)) {
      return classInfoCache.get(name);
    }

    byte[] bytes = getClass(name);
    if (bytes == null) return null;

    ClassNode classNode = ClassHelper.loadClassInfo(bytes);

    // Cache it!
    classInfoCache.put(name, classNode);

    return classNode;
  }

  @Override
  public Collection<String> getLoadedClasses() {
    // We cannot determine all classes in JVM classpath
    return List.of();
  }
}
