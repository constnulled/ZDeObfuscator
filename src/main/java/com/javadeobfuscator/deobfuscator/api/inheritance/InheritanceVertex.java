package com.javadeobfuscator.deobfuscator.api.inheritance;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InheritanceVertex {

    private final Function<String, InheritanceVertex> lookup;
    private final Function<String, Collection<String>> childrenLookup;
    private final boolean isPrimary;
    private volatile Set<InheritanceVertex> parents;
    private volatile Set<InheritanceVertex> children;
    private ClassNode value;

    public InheritanceVertex(@NotNull ClassNode value,
                             @NotNull Function<String, InheritanceVertex> lookup,
                             @NotNull Function<String, Collection<String>> childrenLookup,
                             boolean isPrimary) {
        this.value = value;
        this.lookup = lookup;
        this.childrenLookup = childrenLookup;
        this.isPrimary = isPrimary;
    }

    public boolean hasField(@NotNull String name, @NotNull String desc) {
        for (FieldNode fn : value.fields)
            if (fn.name.equals(name) && fn.desc.equals(desc))
                return true;
        return false;
    }

    public boolean hasFieldInSelfOrParents(@NotNull String name, @NotNull String desc) {
        if (hasField(name, desc))
            return true;
        return allParents()
                .filter(v -> v != this)
                .anyMatch(parent -> parent.hasFieldInSelfOrParents(name, desc));
    }

    public boolean hasFieldInSelfOrChildren(@NotNull String name, @NotNull String desc) {
        if (hasField(name, desc))
            return true;
        return allChildren()
                .filter(v -> v != this)
                .anyMatch(parent -> parent.hasFieldInSelfOrChildren(name, desc));
    }

    public boolean hasMethod(@NotNull String name, @NotNull String desc) {
        for (MethodNode mn : value.methods)
            if (mn.name.equals(name) && mn.desc.equals(desc))
                return true;
        return false;
    }

    public boolean hasMethodInSelfOrParents(@NotNull String name, @NotNull String desc) {
        if (hasMethod(name, desc))
            return true;
        return allParents()
                .filter(v -> v != this)
                .anyMatch(parent -> parent.hasMethodInSelfOrParents(name, desc));
    }

    public boolean hasMethodInSelfOrChildren(@NotNull String name, @NotNull String desc) {
        if (hasMethod(name, desc))
            return true;
        return allChildren()
                .filter(v -> v != this)
                .anyMatch(parent -> parent.hasMethodInSelfOrChildren(name, desc));
    }

    public boolean isLibraryVertex() {
        return !isPrimary;
    }

    public boolean isJavaLangObject() {
        return getName().equals("java/lang/Object");
    }

    public boolean isLoop() {
        String name = getName();
        return allParents()
                .anyMatch(v -> name.equals(v.getName()));
    }

    public boolean isModule() {
        return (getValue().access & Opcodes.ACC_MODULE) != 0 && getValue().superName == null;
    }

    public boolean isLibraryMethod(@NotNull String name, @NotNull String desc) {
        if (!isPrimary && hasMethod(name, desc))
            return true;

        for (InheritanceVertex parent : getParents())
            if (parent.isLibraryMethod(name, desc))
                return true;

        return false;
    }

    public boolean isParentOf(@NotNull InheritanceVertex vertex) {
        return vertex.getAllParents().contains(this);
    }

    public boolean isChildOf(@NotNull InheritanceVertex vertex) {
        return getAllParents().contains(vertex);
    }

    public boolean isIndirectFamilyMember(@NotNull InheritanceVertex vertex) {
        return isIndirectFamilyMember(getFamily(true), vertex);
    }

    public boolean isIndirectFamilyMember(@NotNull Set<InheritanceVertex> family, @NotNull InheritanceVertex vertex) {
        return this != vertex &&
                family.contains(vertex) &&
                !isChildOf(vertex) &&
                !isParentOf(vertex);
    }

    public boolean hasParent(@NotNull String name) {
        for (InheritanceVertex parent : getAllParents())
            if (name.equals(parent.getName()))
                return true;

        return false;
    }

    public boolean hasChild(@NotNull String name) {
        for (InheritanceVertex child : getAllChildren())
            if (name.equals(child.getName()))
                return true;

        return false;
    }

    @NotNull
    public Set<InheritanceVertex> getFamily(boolean includeObject) {
        Set<InheritanceVertex> vertices = new LinkedHashSet<>();
        visitFamily(vertices);
        if (!includeObject)
            vertices.removeIf(InheritanceVertex::isJavaLangObject);
        return vertices;
    }

    private void visitFamily(@NotNull Set<InheritanceVertex> vertices) {
        if (isModule())
            return;
        if (vertices.add(this) && !isJavaLangObject())
            for (InheritanceVertex vertex : getAllDirectVertices())
                vertex.visitFamily(vertices);
    }

    @NotNull
    public Set<InheritanceVertex> getAllParents() {
        return allParents().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @NotNull
    public Stream<InheritanceVertex> allParents() {
        return Streams.recurseWithoutCycles(this, InheritanceVertex::getParents)
                .skip(1);
    }

    @NotNull
    public Set<InheritanceVertex> getParents() {
        Set<InheritanceVertex> parents = this.parents;
        if (parents == null) {
            synchronized (this) {
                if (isModule()) {
                    parents = Collections.emptySet();
                    this.parents = parents;
                    return parents;
                }
                parents = this.parents;
                if (parents == null) {
                    String name = getName();
                    parents = new LinkedHashSet<>();
                    String superName = value.superName;
                    if (superName != null && !name.equals(superName)) {
                        InheritanceVertex parentVertex = lookup.apply(superName);
                        if (parentVertex != null)
                            parents.add(parentVertex);
                    }
                    for (String itf : value.interfaces) {
                        InheritanceVertex itfVertex = lookup.apply(itf);
                        if (itfVertex != null && !name.equals(itf))
                            parents.add(itfVertex);
                    }
                    this.parents = parents;
                }
            }
        }
        return parents;
    }

    @NotNull
    public Set<InheritanceVertex> getAllChildren() {
        return allChildren().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @NotNull
    public Stream<InheritanceVertex> allChildren() {
        return Streams.recurseWithoutCycles(this, InheritanceVertex::getChildren)
                .skip(1);
    }

    @NotNull
    public Set<InheritanceVertex> getChildren() {
        Set<InheritanceVertex> children = this.children;
        if (children == null) {
            synchronized (this) {
                if (isModule()) {
                    children = Collections.emptySet();
                    this.children = children;
                    return children;
                }
                children = this.children;
                if (children == null) {
                    String name = getName();
                    children = childrenLookup.apply(value.name)
                            .stream()
                            .filter(childName -> !name.equals(childName))
                            .map(lookup)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    this.children = children;
                }
            }
        }
        return children;
    }

    @NotNull
    public Set<InheritanceVertex> getAllDirectVertices() {
        Set<InheritanceVertex> set = new HashSet<>(getParents());
        set.addAll(getChildren());
        return set;
    }

    public void clearCachedVertices() {
        synchronized (this) {
            parents = null;
            children = null;
        }
    }

    @NotNull
    public String getName() {
        return value.name;
    }

    @NotNull
    public ClassNode getValue() {
        return value;
    }

    public void setValue(@NotNull ClassNode value) {
        this.value = value;

        synchronized (this) {
            children = null;
            parents = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InheritanceVertex vertex = (InheritanceVertex) o;
        return Objects.equals(getName(), vertex.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }
}