package com.javadeobfuscator.deobfuscator.bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.objectweb.asm.tree.ClassNode;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.asm.ConstantPool;
import com.javadeobfuscator.deobfuscator.api.asm.ClassWrapper;
import com.javadeobfuscator.deobfuscator.api.classpath.ClassInfoStorage;
import com.javadeobfuscator.deobfuscator.api.context.Context;
import com.javadeobfuscator.deobfuscator.api.context.DeobfuscatorOptions;
import com.javadeobfuscator.deobfuscator.api.transformer.ProgressReporter;
import com.javadeobfuscator.deobfuscator.api.transformer.Transformer;

public final class XBridge {

  private XBridge() {
  }

  public static Context createContext(Deobfuscator deobfuscator, Map<String, ClassNode> classes, Map<String, ClassNode> classpath, DeobfuscatorOptions options) {
    ClassInfoStorage libraries = new NodeBackedClassStorage(classpath, name -> !classes.containsKey(name));
    Context context = new Context(options, new NodeBackedClassStorage(classes, name -> true), libraries);
    for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
      ClassNode node = entry.getValue();
      context.getClassesMap().put(node.name, new ClassWrapper(node.name + ".class", node, null));
    }
    return context;
  }

  public static int run(Deobfuscator deobfuscator, Map<String, ClassNode> classes, Map<String, ClassNode> classpath, Supplier<Transformer> supplier, DeobfuscatorOptions options) {
    Context context = createContext(deobfuscator, classes, classpath, options);
    ProgressReporter reporter = new ProgressReporter(context.getClassesMap().size());
    Transformer.setProgressReporter(reporter);
    int changes;
    try {
      changes = Transformer.transform(supplier, null, context);
    } finally {
      Transformer.setProgressReporter(null);
      syncBack(deobfuscator, classes, classpath, context);
    }
    return changes;
  }

  private static void syncBack(Deobfuscator deobfuscator, Map<String, ClassNode> classes, Map<String, ClassNode> classpath, Context context) {
    Map<String, ClassNode> before = new HashMap<>(classes);
    Map<ClassNode, ConstantPool> pools = new HashMap<>();
    for (ClassNode node : before.values()) {
      ConstantPool pool = deobfuscator.getConstantPool(node);
      if (pool != null) {
        pools.put(node, pool);
      }
    }
    Map<String, ClassNode> after = new LinkedHashMap<>();
    for (ClassWrapper wrapper : context.classes()) {
      after.put(wrapper.name(), wrapper.classNode());
    }
    List<String> removed = new ArrayList<>();
    for (String name : before.keySet()) {
      if (!after.containsKey(name)) {
        removed.add(name);
      }
    }
    for (String name : removed) {
      classes.remove(name);
      classpath.remove(name);
    }
    for (Map.Entry<String, ClassNode> entry : after.entrySet()) {
      ClassNode node = entry.getValue();
      ClassNode previous = before.get(entry.getKey());
      classes.put(entry.getKey(), node);
      classpath.put(entry.getKey(), node);
      if (previous != node && previous != null && pools.containsKey(previous)) {
        deobfuscator.setConstantPool(node, pools.get(previous));
      }
    }
  }
}
