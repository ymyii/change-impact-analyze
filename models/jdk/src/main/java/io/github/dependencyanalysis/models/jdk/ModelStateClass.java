package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.FieldImpl;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrike.shrikeCT.ClassConstants;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synthetic class owning conservative model-only static state. */
final class ModelStateClass extends SyntheticClass {

    /** Synthetic type prefix. */
    private static final String TYPE_PREFIX =
            "Ldependencyanalysis/jdkmodel/";

    /** Fields by semantic slot. */
    private final Map<StateSlot, IField> slots;

    /** Fields by name. */
    private final Map<Atom, IField> fields;

    ModelStateClass(
            final IClassHierarchy hierarchy,
            final String modelId) {
        super(TypeReference.findOrCreate(
                hierarchy.getScope().getSyntheticLoader(),
                TYPE_PREFIX + modelId + "/State"),
                hierarchy);
        final Map<StateSlot, IField> slotValues =
                new EnumMap<>(StateSlot.class);
        final Map<Atom, IField> fieldValues = new LinkedHashMap<>();
        for (StateSlot slot : StateSlot.values()) {
            final Atom name = Atom.findOrCreateAsciiAtom(
                    slot.name().toLowerCase(java.util.Locale.ROOT));
            final FieldReference reference = FieldReference.findOrCreate(
                    getReference(), name, TypeReference.JavaLangObject);
            final IField field = new FieldImpl(this, reference,
                    ClassConstants.ACC_PUBLIC | ClassConstants.ACC_STATIC,
                    Collections.emptySet());
            slotValues.put(slot, field);
            fieldValues.put(name, field);
        }
        slots = Collections.unmodifiableMap(slotValues);
        fields = Collections.unmodifiableMap(fieldValues);
    }

    FieldReference field(final StateSlot slot) {
        final IField result = slots.get(slot);
        if (result == null) {
            throw new JdkModelException("Unknown state slot: " + slot);
        }
        return result.getReference();
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public int getModifiers() {
        return ClassConstants.ACC_PUBLIC | ClassConstants.ACC_FINAL
                | ClassConstants.ACC_SUPER;
    }

    @Override
    public IClass getSuperclass() {
        return getClassHierarchy().getRootClass();
    }

    @Override
    public Collection<? extends IClass> getDirectInterfaces() {
        return Collections.emptySet();
    }

    @Override
    public Collection<IClass> getAllImplementedInterfaces() {
        return Collections.emptySet();
    }

    @Override
    public IMethod getMethod(final Selector selector) {
        return null;
    }

    @Override
    public IField getField(final Atom name) {
        return fields.get(name);
    }

    @Override
    public IMethod getClassInitializer() {
        return null;
    }

    @Override
    public Collection<? extends IMethod> getDeclaredMethods() {
        return Collections.emptySet();
    }

    @Override
    public Collection<IField> getAllInstanceFields() {
        return Collections.emptySet();
    }

    @Override
    public Collection<IField> getAllStaticFields() {
        return fields.values();
    }

    @Override
    public Collection<IField> getAllFields() {
        return fields.values();
    }

    @Override
    public Collection<? extends IMethod> getAllMethods() {
        return Collections.emptySet();
    }

    @Override
    public Collection<IField> getDeclaredInstanceFields() {
        return Collections.emptySet();
    }

    @Override
    public Collection<IField> getDeclaredStaticFields() {
        return fields.values();
    }

    @Override
    public boolean isReferenceType() {
        return true;
    }
}
