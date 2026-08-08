package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.jar.JarLease;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// Wiki: wiki/features/bytecode-diff-engine.md - Bytecode diff engine
/**
 * Core bytecode diff engine that
 * compares old and new jar files
 * of a version-changed dependency
 * and produces a list of
 * {@link ChangePoint} instances.
 */
public final class BytecodeDiffEngine {

    /** Set of included change
     * point kinds. */
    private final Set<ChangePointKind>
            includedKinds;

    /**
     * Creates a new bytecode diff
     * engine with default included
     * kinds.
     */
    public BytecodeDiffEngine() {
        this(ChangePointKind
                .DEFAULT_INCLUDED_KINDS);
    }

    /**
     * Creates a new bytecode diff
     * engine with the specified
     * included change point kinds.
     * Only change points whose kind
     * is in the given set will be
     * produced.
     *
     * @param kinds set of included
     *  change point kinds
     */
    public BytecodeDiffEngine(
            final Set<ChangePointKind>
                    kinds) {
        Objects.requireNonNull(
                kinds, "kinds");
        if (kinds.isEmpty()) {
            this.includedKinds =
                    Collections.emptySet();
        } else {
            this.includedKinds =
                    Collections.unmodifiableSet(
                            EnumSet.copyOf(
                                    kinds));
        }
    }

    /**
     * Computes a bytecode diff through logical repository identities.
     *
     * @param change version change
     * @param repository command-scoped JAR repository
     * @return stable change points
     * @throws BytecodeDiffException on invalid class content
     * @throws IOException on repository access failure
     */
    public List<ChangePoint> diff(
            final DependencyChange change,
            final IJarRepository repository)
            throws BytecodeDiffException, IOException {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(repository, "repository");
        try (JarLease oldLease = repository.open(change.getOldArtifact());
             JarLease newLease = repository.open(change.getNewArtifact())) {
            final Map<String, ClassInfo> oldIndex =
                    JarClassIndexer.index(oldLease.jarFile());
            final Map<String, ClassInfo> newIndex =
                    JarClassIndexer.index(newLease.jarFile());
            final List<ChangePoint> result = new ArrayList<>();
            diffClasses(oldIndex, newIndex, change.getNewArtifact(), result);
            return Collections.unmodifiableList(result);
        }
    }

    /**
     * Computes class-level diffs.
     *
     * @param oldIdx old class index
     * @param newIdx new class index
     * @param art    artifact coordinate
     * @param result change point list
     */
    private void diffClasses(
            final Map<String, ClassInfo>
                    oldIdx,
            final Map<String, ClassInfo>
                    newIdx,
            final ArtifactCoord art,
            final List<ChangePoint> result) {
        final Set<String> allNames =
                new TreeSet<>();
        allNames.addAll(oldIdx.keySet());
        allNames.addAll(newIdx.keySet());
        for (final String name
                : allNames) {
            final ClassInfo oldC =
                    oldIdx.get(name);
            final ClassInfo newC =
                    newIdx.get(name);
            if (oldC == null) {
                if (includedKinds.contains(
                        ChangePointKind
                                .CLASS_ADDED)) {
                    result.add(ChangePoint.withDescriptors(
                            art,
                            ChangePointKind
                                    .CLASS_ADDED,
                            name,
                            null,
                            new MemberDescriptors(
                                    null, null),
                            null, null));
                }
                continue;
            }
            if (newC == null) {
                if (includedKinds.contains(
                        ChangePointKind
                                .CLASS_REMOVED)) {
                    result.add(ChangePoint.withDescriptors(
                            art,
                            ChangePointKind
                                    .CLASS_REMOVED,
                            name,
                            null,
                            new MemberDescriptors(
                                    null, null),
                            null, null));
                }
                continue;
            }
            if (includedKinds.contains(
                    ChangePointKind.CLASS_ACCESS_NARROWED)
                    && oldC.getAccess().narrowsTo(newC.getAccess())) {
                result.add(ChangePoint.accessNarrowed(
                        art, ChangePointKind.CLASS_ACCESS_NARROWED,
                        name, null, null,
                        new AccessTransition(oldC.getAccess(),
                                newC.getAccess())));
            }
            diffMethods(
                    oldC, newC, art,
                    name, result);
            diffFields(
                    oldC, newC, art,
                    name, result);
        }
    }

    /**
     * Computes method-level diffs
     * for a single class.
     *
     * @param oldC   old class info
     * @param newC   new class info
     * @param art    artifact coordinate
     * @param owner  internal class name
     * @param result change point list
     */
    private void diffMethods(
            final ClassInfo oldC,
            final ClassInfo newC,
            final ArtifactCoord art,
            final String owner,
            final List<ChangePoint> result) {
        final Map<String, MethodInfo> oldMap =
                toMethodMap(
                        oldC.getMethods());
        final Map<String, MethodInfo> newMap =
                toMethodMap(
                        newC.getMethods());
        final Set<String> descriptorChangedNames = includedKinds.contains(
                ChangePointKind.METHOD_DESCRIPTOR_CHANGED)
                ? descriptorChangedNames(
                oldC.getMethods(), newC.getMethods()) : Set.of();
        final Set<String> allKeys =
                new TreeSet<>();
        allKeys.addAll(oldMap.keySet());
        allKeys.addAll(newMap.keySet());
        for (final String key
                : allKeys) {
            final MethodInfo oldM =
                    oldMap.get(key);
            final MethodInfo newM =
                    newMap.get(key);
            if (oldM == null) {
                if (descriptorChangedNames.contains(newM.getName())) {
                    continue;
                }
                if (includedKinds.contains(
                        ChangePointKind
                                .METHOD_ADDED)) {
                    result.add(ChangePoint.withDescriptors(
                            art,
                            ChangePointKind
                                    .METHOD_ADDED,
                            owner,
                            newM.getName(),
                            new MemberDescriptors(
                                    null,
                                    newM.getDescriptor()),
                            null, null));
                }
                continue;
            }
            if (newM == null) {
                if (descriptorChangedNames.contains(oldM.getName())) {
                    continue;
                }
                if (includedKinds.contains(
                        ChangePointKind
                                .METHOD_REMOVED)) {
                    result.add(ChangePoint.withDescriptors(
                            art,
                            ChangePointKind
                                    .METHOD_REMOVED,
                            owner,
                            oldM.getName(),
                            new MemberDescriptors(
                                    oldM.getDescriptor(),
                                    null),
                            null, null));
                }
                continue;
            }
            if (includedKinds.contains(
                    ChangePointKind
                            .METHOD_BODY_CHANGED)
                    && oldM.getBodyHash() != null
                    && newM.getBodyHash()
                            != null
                    && !oldM.getBodyHash()
                            .equals(newM
                                    .getBodyHash())) {
                result.add(ChangePoint.withDescriptors(
                        art,
                        ChangePointKind
                                .METHOD_BODY_CHANGED,
                        owner,
                        oldM.getName(),
                        new MemberDescriptors(
                                oldM.getDescriptor(),
                                newM.getDescriptor()),
                        oldM.getBodyHash(),
                        newM.getBodyHash()));
            }
            if (includedKinds.contains(
                    ChangePointKind.METHOD_ACCESS_NARROWED)
                    && !"<clinit>".equals(oldM.getName())
                    && oldM.getAccess().narrowsTo(newM.getAccess())) {
                result.add(ChangePoint.accessNarrowed(
                        art, ChangePointKind.METHOD_ACCESS_NARROWED,
                        owner, oldM.getName(), oldM.getDescriptor(),
                        new AccessTransition(oldM.getAccess(),
                                newM.getAccess())));
            }
        }
        if (includedKinds.contains(
                ChangePointKind
                        .METHOD_DESCRIPTOR_CHANGED)) {
            detectDescriptorChanges(
                    oldC.getMethods(),
                    newC.getMethods(),
                    art, owner, result);
        }
    }

    /**
     * Detects method descriptor
     * changes by matching on name.
     * Only reports when both old and new
     * have exactly one method with the
     * same name but different descriptor.
     *
     * @param oldMethods old methods
     * @param newMethods new methods
     * @param art        artifact coord
     * @param owner      class name
     * @param result     change points
     */
    private void detectDescriptorChanges(
            final List<MethodInfo>
                    oldMethods,
            final List<MethodInfo>
                    newMethods,
            final ArtifactCoord art,
            final String owner,
            final List<ChangePoint>
                    result) {
        final Map<String, List<MethodInfo>>
                oldByName = groupByName(oldMethods);
        final Map<String, List<MethodInfo>>
                newByName = groupByName(newMethods);
        for (String name : descriptorChangedNames(
                oldMethods, newMethods)) {
            final List<MethodInfo> oldList = oldByName.get(name);
            final List<MethodInfo> newList = newByName.get(name);
            result.add(ChangePoint.withDescriptors(
                    art,
                    ChangePointKind
                            .METHOD_DESCRIPTOR_CHANGED,
                    owner,
                    name,
                    new MemberDescriptors(
                            oldList.get(0)
                                    .getDescriptor(),
                            newList.get(0)
                                    .getDescriptor()),
                    null, null));
        }
    }

    private Set<String> descriptorChangedNames(
            final List<MethodInfo> oldMethods,
            final List<MethodInfo> newMethods) {
        final Map<String, List<MethodInfo>> oldByName =
                groupByName(oldMethods);
        final Map<String, List<MethodInfo>> newByName =
                groupByName(newMethods);
        final Set<String> result = new TreeSet<>();
        for (Map.Entry<String, List<MethodInfo>> entry
                : oldByName.entrySet()) {
            final List<MethodInfo> oldList = entry.getValue();
            final List<MethodInfo> newList =
                    newByName.get(entry.getKey());
            if (oldList.size() == 1 && newList != null
                    && newList.size() == 1
                    && !oldList.get(0).getDescriptor().equals(
                    newList.get(0).getDescriptor())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Groups methods by name.
     *
     * @param methods method list
     * @return map of name to methods
     */
    private Map<String, List<MethodInfo>>
            groupByName(
            final List<MethodInfo> methods) {
        final Map<String, List<MethodInfo>>
                result = new TreeMap<>();
        for (final MethodInfo m : methods) {
            result.computeIfAbsent(
                    m.getName(),
                    k -> new ArrayList<>())
                    .add(m);
        }
        return result;
    }

    /**
     * Computes field-level diffs
     * for a single class.
     *
     * @param oldC   old class info
     * @param newC   new class info
     * @param art    artifact coordinate
     * @param owner  internal class name
     * @param result change point list
     */
    private void diffFields(
            final ClassInfo oldC,
            final ClassInfo newC,
            final ArtifactCoord art,
            final String owner,
            final List<ChangePoint> result) {
        final Map<String, FieldInfo> oldMap =
                toFieldMap(
                        oldC.getFields());
        final Map<String, FieldInfo> newMap =
                toFieldMap(
                        newC.getFields());
        final Set<String> allNames =
                new TreeSet<>();
        allNames.addAll(oldMap.keySet());
        allNames.addAll(newMap.keySet());
        for (final String name
                : allNames) {
            final FieldInfo oldF =
                    oldMap.get(name);
            final FieldInfo newF =
                    newMap.get(name);
            if (oldF == null) {
                if (includedKinds.contains(
                        ChangePointKind
                                .FIELD_ADDED)) {
                    result.add(ChangePoint.withDescriptors(
                            art,
                            ChangePointKind
                                    .FIELD_ADDED,
                            owner,
                            newF.getName(),
                            new MemberDescriptors(
                                    null,
                                    newF.getDescriptor()),
                            null, null));
                }
                continue;
            }
            if (newF == null) {
                if (includedKinds.contains(
                        ChangePointKind
                                .FIELD_REMOVED)) {
                    result.add(ChangePoint.withDescriptors(
                            art,
                            ChangePointKind
                                    .FIELD_REMOVED,
                            owner,
                            oldF.getName(),
                            new MemberDescriptors(
                                    oldF.getDescriptor(),
                                    null),
                            null, null));
                }
                continue;
            }
            if (includedKinds.contains(
                    ChangePointKind
                            .FIELD_DESCRIPTOR_CHANGED)
                    && !oldF.getDescriptor()
                            .equals(newF
                                    .getDescriptor())) {
                result.add(ChangePoint.withDescriptors(
                        art,
                        ChangePointKind
                                .FIELD_DESCRIPTOR_CHANGED,
                        owner,
                        newF.getName(),
                        new MemberDescriptors(
                                oldF.getDescriptor(),
                                newF.getDescriptor()),
                        null, null));
            }
            if (oldF.getDescriptor().equals(newF.getDescriptor())
                    && includedKinds.contains(
                    ChangePointKind.FIELD_ACCESS_NARROWED)
                    && oldF.getAccess().narrowsTo(newF.getAccess())) {
                result.add(ChangePoint.accessNarrowed(
                        art, ChangePointKind.FIELD_ACCESS_NARROWED,
                        owner, oldF.getName(), oldF.getDescriptor(),
                        new AccessTransition(oldF.getAccess(),
                                newF.getAccess())));
            }
        }
    }

    /**
     * Converts a method list to a
     * map keyed by name:descriptor.
     *
     * @param methods method list
     * @return map by key
     */
    private Map<String, MethodInfo>
            toMethodMap(
            final List<MethodInfo>
                    methods) {
        final Map<String, MethodInfo> map =
                new HashMap<>();
        for (final MethodInfo m
                : methods) {
            map.put(m.key(), m);
        }
        return map;
    }

    /**
     * Converts a field list to a
     * map keyed by name.
     *
     * @param fields field list
     * @return map by name
     */
    private Map<String, FieldInfo>
            toFieldMap(
            final List<FieldInfo>
                    fields) {
        final Map<String, FieldInfo> map =
                new HashMap<>();
        for (final FieldInfo f
                : fields) {
            map.put(f.getName(), f);
        }
        return map;
    }
}
