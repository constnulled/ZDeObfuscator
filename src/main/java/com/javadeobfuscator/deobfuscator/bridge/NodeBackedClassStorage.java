package com.javadeobfuscator.deobfuscator.bridge;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.javadeobfuscator.deobfuscator.api.classpath.ClassInfoStorage;

public class NodeBackedClassStorage extends ClassInfoStorage {

  private final Map<String, ClassNode> nodes;
  private final Predicate<String> visible;

  public NodeBackedClassStorage(Map<String, ClassNode> nodes, Predicate<String> visible) {
    this.nodes = nodes;
    this.visible = visible;
  }

  @Override
  public byte[] getClass(String name) {
    ClassNode node = getClassInfo(name);
    if (node == null) {
      return null;
    }
    try {
      ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      node.accept(writer);
      return writer.toByteArray();
    } catch (Throwable t) {
      return null;
    }
  }

  @Override
  public ClassNode getClassInfo(String name) {
    if (!visible.test(name)) {
      return null;
    }
    return nodes.get(name);
  }

  @Override
  public Collection<String> getLoadedClasses() {
    return nodes.keySet().stream().filter(visible).collect(Collectors.toList());
  }
}
