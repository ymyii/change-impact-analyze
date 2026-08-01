package io.github.dependencyanalysis.callgraph;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.LinkedHashSet;
import java.util.Set;

/** Collects explicit binary type references from one class file. */
public final class BytecodeClassReferenceScanner {

    /** ASM API level. */
    private static final int API = Opcodes.ASM9;

    /**
     * Scans one class file while ignoring debug and frame metadata.
     *
     * @param classBytes class bytes
     * @return referenced internal binary names
     */
    public Set<String> scan(final byte[] classBytes) {
        final Set<String> result = new LinkedHashSet<>();
        new ClassReader(classBytes).accept(
                new ReferenceClassVisitor(result),
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return Set.copyOf(result);
    }

    /** Main visitor. */
    private static final class ReferenceClassVisitor
            extends ClassVisitor {

        /** Reference accumulator. */
        private final Set<String> references;

        /** @param values reference accumulator */
        ReferenceClassVisitor(final Set<String> values) {
            super(API);
            references = values;
        }

        @Override
        public void visit(
                final int version,
                final int access,
                final String name,
                final String signature,
                final String superName,
                final String[] interfaces) {
            addInternal(superName);
            if (interfaces != null) {
                for (String value : interfaces) {
                    addInternal(value);
                }
            }
            addSignature(signature);
        }

        @Override
        public void visitOuterClass(
                final String owner,
                final String name,
                final String descriptor) {
            addInternal(owner);
            addMethodDescriptor(descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(
                final String descriptor, final boolean visible) {
            addDescriptor(descriptor);
            return annotationVisitor();
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                final int typeRef,
                final org.objectweb.asm.TypePath typePath,
                final String descriptor,
                final boolean visible) {
            addDescriptor(descriptor);
            return annotationVisitor();
        }

        @Override
        public void visitNestHost(final String nestHost) {
            addInternal(nestHost);
        }

        @Override
        public void visitNestMember(final String nestMember) {
            addInternal(nestMember);
        }

        @Override
        public void visitPermittedSubclass(final String permittedSubclass) {
            addInternal(permittedSubclass);
        }

        @Override
        public void visitInnerClass(
                final String name,
                final String outerName,
                final String innerName,
                final int access) {
            addInternal(name);
            addInternal(outerName);
        }

        @Override
        public FieldVisitor visitField(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final Object value) {
            addDescriptor(descriptor);
            addSignature(signature);
            addConstant(value);
            return new FieldVisitor(API) {
                @Override
                public AnnotationVisitor visitAnnotation(
                        final String desc, final boolean visible) {
                    addDescriptor(desc);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String desc,
                        final boolean visible) {
                    addDescriptor(desc);
                    return annotationVisitor();
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            addMethodDescriptor(descriptor);
            addSignature(signature);
            if (exceptions != null) {
                for (String value : exceptions) {
                    addInternal(value);
                }
            }
            return methodVisitor();
        }

        private MethodVisitor methodVisitor() {
            return new MethodVisitor(API) {
                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                        final String descriptor,
                        final boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String descriptor,
                        final boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                        final int parameter,
                        final String descriptor,
                        final boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public void visitTypeInsn(
                        final int opcode, final String type) {
                    addTypeName(type);
                }

                @Override
                public void visitFieldInsn(
                        final int opcode,
                        final String owner,
                        final String name,
                        final String descriptor) {
                    addInternal(owner);
                    addDescriptor(descriptor);
                }

                @Override
                public void visitMethodInsn(
                        final int opcode,
                        final String owner,
                        final String name,
                        final String descriptor,
                        final boolean isInterface) {
                    addInternal(owner);
                    addMethodDescriptor(descriptor);
                }

                @Override
                public void visitInvokeDynamicInsn(
                        final String name,
                        final String descriptor,
                        final Handle bootstrapMethodHandle,
                        final Object... bootstrapMethodArguments) {
                    addMethodDescriptor(descriptor);
                    addHandle(bootstrapMethodHandle);
                    for (Object value : bootstrapMethodArguments) {
                        addConstant(value);
                    }
                }

                @Override
                public void visitLdcInsn(final Object value) {
                    addConstant(value);
                }

                @Override
                public void visitMultiANewArrayInsn(
                        final String descriptor, final int dimensions) {
                    addDescriptor(descriptor);
                }

                @Override
                public void visitTryCatchBlock(
                        final org.objectweb.asm.Label start,
                        final org.objectweb.asm.Label end,
                        final org.objectweb.asm.Label handler,
                        final String type) {
                    addInternal(type);
                }

                @Override
                public AnnotationVisitor visitInsnAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String descriptor,
                        final boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitTryCatchAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String descriptor,
                        final boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }
            };
        }

        private AnnotationVisitor annotationVisitor() {
            return new AnnotationVisitor(API) {
                @Override
                public void visit(final String name, final Object value) {
                    addConstant(value);
                }

                @Override
                public void visitEnum(
                        final String name,
                        final String descriptor,
                        final String value) {
                    addDescriptor(descriptor);
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                        final String name, final String descriptor) {
                    addDescriptor(descriptor);
                    return this;
                }

                @Override
                public AnnotationVisitor visitArray(final String name) {
                    return this;
                }
            };
        }

        private void addConstant(final Object value) {
            if (value instanceof Type) {
                addType((Type) value);
            } else if (value instanceof Handle) {
                addHandle((Handle) value);
            } else if (value instanceof ConstantDynamic) {
                final ConstantDynamic dynamic = (ConstantDynamic) value;
                addDescriptor(dynamic.getDescriptor());
                addHandle(dynamic.getBootstrapMethod());
                for (int index = 0;
                        index < dynamic.getBootstrapMethodArgumentCount();
                        index++) {
                    addConstant(dynamic.getBootstrapMethodArgument(index));
                }
            }
        }

        private void addHandle(final Handle handle) {
            addInternal(handle.getOwner());
            if (handle.getDesc().startsWith("(")) {
                addMethodDescriptor(handle.getDesc());
            } else {
                addDescriptor(handle.getDesc());
            }
        }

        private void addSignature(final String signature) {
            if (signature == null) {
                return;
            }
            final SignatureVisitor visitor =
                    new SignatureVisitor(API) {
                        @Override
                        public void visitClassType(final String name) {
                            addInternal(name);
                        }

                        @Override
                        public void visitInnerClassType(final String name) {
                            addInternal(name);
                        }
                    };
            final SignatureReader reader = new SignatureReader(signature);
            if (signature.startsWith("L")
                    || signature.startsWith("T")
                    || signature.startsWith("[")) {
                reader.acceptType(visitor);
            } else {
                reader.accept(visitor);
            }
        }

        private void addMethodDescriptor(final String descriptor) {
            if (descriptor == null) {
                return;
            }
            for (Type type : Type.getArgumentTypes(descriptor)) {
                addType(type);
            }
            addType(Type.getReturnType(descriptor));
        }

        private void addDescriptor(final String descriptor) {
            if (descriptor != null) {
                addType(Type.getType(descriptor));
            }
        }

        private void addTypeName(final String type) {
            if (type != null && type.startsWith("[")) {
                addDescriptor(type);
            } else {
                addInternal(type);
            }
        }

        private void addType(final Type type) {
            if (type.getSort() == Type.ARRAY) {
                addType(type.getElementType());
            } else if (type.getSort() == Type.OBJECT) {
                addInternal(type.getInternalName());
            } else if (type.getSort() == Type.METHOD) {
                addMethodDescriptor(type.getDescriptor());
            }
        }

        private void addInternal(final String value) {
            if (value != null && !value.isBlank()) {
                references.add(value);
            }
        }
    }
}
