/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Predicate;

import jdk.classfile.*;
import jdk.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.classfile.impl.Util;

import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Registered;
import jdk.jfr.SettingControl;
import jdk.jfr.SettingDefinition;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.event.EventWriter;

/**
 * Class responsible for adding instrumentation to a subclass of {@link Event}.
 *
 */
public final class EventInstrumentation {

    record SettingInfo(String fieldName, int index, ClassDesc paramType, String methodName, SettingControl settingControl) {
        /**
         * A malicious user must never be able to run a callback in the wrong
         * context. Methods on SettingControl must therefore never be invoked directly
         * by JFR, instead use jdk.jfr.internal.Control.
         */
        public SettingControl settingControl() {
            return this.settingControl;
        }
    }

    record FieldInfo(String fieldName, String fieldDescriptor, String internalClassName) {
    }

    public static final String FIELD_EVENT_THREAD = "eventThread";
    public static final String FIELD_STACK_TRACE = "stackTrace";
    public static final String FIELD_DURATION = "duration";

    static final String FIELD_EVENT_CONFIGURATION = "eventConfiguration";
    static final String FIELD_START_TIME = "startTime";

    private static final String ANNOTATION_NAME_DESCRIPTOR = Name.class.descriptorString();
    private static final String ANNOTATION_REGISTERED_DESCRIPTOR = Registered.class.descriptorString();
    private static final String ANNOTATION_ENABLED_DESCRIPTOR = Enabled.class.descriptorString();
    private static final ClassDesc TYPE_EVENT_CONFIGURATION = ClassDesc.ofDescriptor(EventConfiguration.class.descriptorString());
    private static final ClassDesc TYPE_EVENT_WRITER = ClassDesc.ofDescriptor(EventWriter.class.descriptorString());
    private static final ClassDesc TYPE_EVENT_WRITER_FACTORY = ClassDesc.ofDescriptor("Ljdk/jfr/internal/event/EventWriterFactory;");
    private static final ClassDesc TYPE_SETTING_CONTROL = ClassDesc.ofDescriptor(SettingControl.class.descriptorString());
    private static final String TYPE_OBJECT_DESCRIPTOR = Object.class.descriptorString();
    private static final String TYPE_EVENT_CONFIGURATION_DESCRIPTOR = TYPE_EVENT_CONFIGURATION.descriptorString();
    private static final String TYPE_SETTING_DEFINITION_DESCRIPTOR = SettingDefinition.class.descriptorString();
    private static final String METHOD_COMMIT = "commit";
    private static final MethodTypeDesc METHOD_COMMIT_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_BEGIN = "begin";
    private static final MethodTypeDesc METHOD_BEGIN_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_END = "end";
    private static final MethodTypeDesc METHOD_END_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_IS_ENABLED = "isEnabled";
    private static final MethodTypeDesc METHOD_IS_ENABLED_DESC = MethodTypeDesc.of(CD_boolean);
    private static final String METHOD_TIME_STAMP = "timestamp";
    private static final MethodTypeDesc METHOD_TIME_STAMP_DESC =  MethodTypeDesc.of(CD_long);
    private static final String METHOD_GET_EVENT_WRITER_KEY = "getEventWriter";
    private static final MethodTypeDesc METHOD_GET_EVENT_WRITER_KEY_DESC = MethodTypeDesc.of(TYPE_EVENT_WRITER, CD_long);
    private static final String METHOD_EVENT_SHOULD_COMMIT = "shouldCommit";
    private static final MethodTypeDesc METHOD_EVENT_SHOULD_COMMIT_DESC = MethodTypeDesc.of(CD_boolean);
    private static final String METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT = "shouldCommit";
    private static final MethodTypeDesc METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_DESC = MethodTypeDesc.of(CD_boolean, CD_long);
    private static final String METHOD_EVENT_CONFIGURATION_GET_SETTING = "getSetting";
    private static final MethodTypeDesc METHOD_EVENT_CONFIGURATION_GET_SETTING_DESC = MethodTypeDesc.of(TYPE_SETTING_CONTROL, CD_int);
    private static final String METHOD_DURATION = "duration";
    private static final MethodTypeDesc METHOD_DURATION_DESC = MethodTypeDesc.of(CD_long, CD_long);
    private static final String METHOD_RESET = "reset";
    private static final MethodTypeDesc METHOD_RESET_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_ENABLED = "enabled";
    private static final MethodTypeDesc METHOD_ENABLED_DESC = MethodTypeDesc.of(CD_boolean);
    private static final String METHOD_SHOULD_COMMIT_LONG = "shouldCommit";
    private static final MethodTypeDesc METHOD_SHOULD_COMMIT_LONG_DESC = MethodTypeDesc.of(CD_boolean, CD_long);

    private final ClassModel classNode;
    private final List<SettingInfo> settingInfos;
    private final List<FieldInfo> fieldInfos;
    private final String eventName;
    private final Class<?> superClass;
    private final boolean untypedEventConfiguration;
    private final MethodTypeDesc staticCommitMethodDesc;
    private final long eventTypeId;
    private final boolean guardEventConfiguration;
    private final boolean isJDK;

    EventInstrumentation(Class<?> superClass, byte[] bytes, long id, boolean isJDK, boolean guardEventConfiguration) {
        this.eventTypeId = id;
        this.superClass = superClass;
        this.classNode = Classfile.parse(bytes);
        this.settingInfos = buildSettingInfos(superClass, classNode);
        this.fieldInfos = buildFieldInfos(superClass, classNode);
        String n = annotationValue(classNode, ANNOTATION_NAME_DESCRIPTOR, 's');
        this.eventName = n == null ? classNode.thisClass().asInternalName().replace("/", ".") : n;
        this.staticCommitMethodDesc = isJDK ? findStaticCommitMethodDesc(classNode, fieldInfos) : null;
        this.untypedEventConfiguration = hasUntypedConfiguration();
        // Corner case when we are forced to generate bytecode (bytesForEagerInstrumentation)
        // We can't reference EventConfiguration::isEnabled() before event class has been registered,
        // so we add a guard against a null reference.
        this.guardEventConfiguration = guardEventConfiguration;
        this.isJDK = isJDK;
    }

    public static MethodTypeDesc findStaticCommitMethodDesc(ClassModel classNode, List<FieldInfo> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (FieldInfo v : fields) {
            sb.append(v.fieldDescriptor);
        }
        sb.append(")V");
        String desc = sb.toString();
        for (var method : classNode.methods()) {
            if ("commit".equals(method.methodName().stringValue()) && method.methodType().stringValue().equals(desc)) {
                return method.methodTypeSymbol();
            }
        }
        return null;
    }

    private boolean hasUntypedConfiguration() {
        for (var field : classNode.fields()) {
            if (FIELD_EVENT_CONFIGURATION.equals(field.fieldName().stringValue())) {
                return field.fieldType().stringValue().equals(TYPE_OBJECT_DESCRIPTOR);
            }
        }
        throw new InternalError("Class missing configuration field");
    }

    public String getClassName() {
        return classNode.thisClass().name().stringValue().replace("/",".");
    }

    boolean isRegistered() {
        Integer result = annotationValue(classNode, ANNOTATION_REGISTERED_DESCRIPTOR, 'Z');
        if (result != null) {
            return result != 0;
        }
        if (superClass != null) {
            Registered r = superClass.getAnnotation(Registered.class);
            if (r != null) {
                return r.value();
            }
        }
        return true;
    }

    boolean isEnabled() {
        Integer result = annotationValue(classNode, ANNOTATION_ENABLED_DESCRIPTOR, 'Z');
        if (result != null) {
            return result != 0;
        }
        if (superClass != null) {
            Enabled e = superClass.getAnnotation(Enabled.class);
            if (e != null) {
                return e.value();
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> T annotationValue(ClassModel classNode, String typeDescriptor, char tag) {
        for (var attr : classNode.attributes()) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute aa) {
                for (var a : aa.annotations()) {
                    if (typeDescriptor.equals(a.className().stringValue())) {
                        var values = a.elements();
                        if (values != null && values.size() == 1) {
                            var vp = values.get(0);
                            if (vp.name().stringValue().equals("value") && (vp.value() instanceof AnnotationValue.OfConstant con) && con.tag() == tag) {
                                return (T) con.constantValue();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<SettingInfo> buildSettingInfos(Class<?> superClass, ClassModel classNode) {
        Set<String> methodSet = new HashSet<>();
        List<SettingInfo> settingInfos = new ArrayList<>();
        for (var m : classNode.methods()) {
            for (var attr : m.attributes()) {
                if (attr instanceof RuntimeVisibleAnnotationsAttribute aa) {
                    for (var an : aa.annotations()) {
                        // We can't really validate the method at this
                        // stage. We would need to check that the parameter
                        // is an instance of SettingControl.
                        if (TYPE_SETTING_DEFINITION_DESCRIPTOR.equals(an.className().stringValue())) {
                            String name = m.methodName().stringValue();
                            for (var nameCandidate : aa.annotations()) {
                                if (ANNOTATION_NAME_DESCRIPTOR.equals(nameCandidate.className().stringValue())) {
                                    var values = nameCandidate.elements();
                                    if (values != null && values.size() == 1) {
                                        var vp = values.get(0);
                                        if (vp.name().stringValue().equals("value") && vp.value() instanceof AnnotationValue.OfString s) {
                                            name = Utils.validJavaIdentifier(s.stringValue(), name);
                                        }
                                    }
                                }
                            }
                            var mDesc = m.methodTypeSymbol();
                            if (mDesc.returnType().descriptorString().equals("Z")) {
                                if (mDesc.parameterCount() == 1) {
                                    var paramType = mDesc.parameterType(0);
                                    String fieldName = EventControl.FIELD_SETTING_PREFIX + settingInfos.size();
                                    int index = settingInfos.size();
                                    methodSet.add(m.methodName().stringValue());
                                    settingInfos.add(new SettingInfo(fieldName, index, paramType, m.methodName().stringValue(), null));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Class<?> c = superClass; c != jdk.internal.event.Event.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method method : c.getDeclaredMethods()) {
                if (!methodSet.contains(method.getName())) {
                    // skip private method in base classes
                    if (!Modifier.isPrivate(method.getModifiers())) {
                        if (method.getReturnType().equals(Boolean.TYPE)) {
                            if (method.getParameterCount() == 1) {
                                Parameter param = method.getParameters()[0];
                                var paramType = param.getType();
                                String fieldName = EventControl.FIELD_SETTING_PREFIX + settingInfos.size();
                                int index = settingInfos.size();
                                methodSet.add(method.getName());
                                settingInfos.add(new SettingInfo(fieldName, index, ClassDesc.ofDescriptor(paramType.descriptorString()), method.getName(), null));
                            }
                        }
                    }
                }
            }
        }
        return settingInfos;
    }

    private static String className(String descriptor) {
        var tk = TypeKind.fromDescriptor(descriptor);
        return switch (tk) {
            case ReferenceType -> Util.descriptorToClass(descriptor).replaceAll("/", ".");
            default -> tk.typeName();
        };
    }

    private static List<FieldInfo> buildFieldInfos(Class<?> superClass, ClassModel classNode) {
        Set<String> fieldSet = new HashSet<>();
        List<FieldInfo> fieldInfos = new ArrayList<>(classNode.fields().size());
        // These two fields are added by native as 'transient' so they will be
        // ignored by the loop below.
        // The benefit of adding them manually is that we can
        // control in which order they occur and we can add @Name, @Description
        // in Java, instead of in native. It also means code for adding implicit
        // fields for native can be reused by Java.
        fieldInfos.add(new FieldInfo("startTime", "J", classNode.thisClass().name().stringValue()));
        fieldInfos.add(new FieldInfo("duration", "J", classNode.thisClass().name().stringValue()));
        for (var field : classNode.fields()) {
            if (!fieldSet.contains(field.fieldName().stringValue()) && isValidField(field.flags().flagsMask(), className(field.fieldType().stringValue()))) {
                FieldInfo fi = new FieldInfo(field.fieldName().stringValue(), field.fieldType().stringValue(), classNode.thisClass().name().stringValue());
                fieldInfos.add(fi);
                fieldSet.add(field.fieldName().stringValue());
            }
        }
        for (Class<?> c = superClass; c != jdk.internal.event.Event.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                // skip private field in base classes
                if (!Modifier.isPrivate(field.getModifiers())) {
                    if (isValidField(field.getModifiers(), field.getType().getName())) {
                        String fieldName = field.getName();
                        if (!fieldSet.contains(fieldName)) {
                            String fieldType = field.getType().descriptorString();
                            String internalClassName = ASMToolkit.getInternalName(c.getName());
                            fieldInfos.add(new FieldInfo(fieldName, fieldType, internalClassName));
                            fieldSet.add(fieldName);
                        }
                    }
                }
            }
        }
        return fieldInfos;
    }

    public static boolean isValidField(int access, String className) {
        if (Modifier.isTransient(access) || Modifier.isStatic(access)) {
            return false;
        }
        return jdk.jfr.internal.Type.isValidJavaFieldType(className);
    }

    public byte[] buildInstrumented() {
        return classNode.transform(makeInstrumented());
    }

    public byte[] buildUninstrumented() {
        return classNode.transform(makeUninstrumented());
    }

    private Predicate<MethodModel> adaptPredicate = new Predicate<MethodModel>() {
        @Override
        public boolean test(MethodModel methodModel) {
            String methodName = methodModel.methodName().stringValue();
            MethodTypeDesc methodDesc = methodModel.methodTypeSymbol();
            if (methodName.equals(METHOD_IS_ENABLED) && methodDesc.equals(METHOD_IS_ENABLED_DESC))
                return true;
            else if (methodName.equals(METHOD_BEGIN) && methodDesc.equals(METHOD_BEGIN_DESC))
                return true;
            else if (methodName.equals(METHOD_END) && methodDesc.equals(METHOD_END_DESC))
                return true;
            else if (methodName.equals(METHOD_COMMIT) && methodDesc.equals(METHOD_COMMIT_DESC))
                return true;
            else if (methodName.equals(METHOD_EVENT_SHOULD_COMMIT) && methodDesc.equals(METHOD_EVENT_SHOULD_COMMIT_DESC))
                return true;

            return false;
        }
    };

    private ClassTransform makeInstrumented() {
        return ClassTransform.transformingMethods(adaptPredicate, (mb, me) -> {
            if (!(me instanceof CodeModel)) mb.accept(me);
            MethodModel mm = mb.original().orElseThrow();
            String methodName = mm.methodName().stringValue();
            String methodDesc = mm.methodType().stringValue();

            // MyEvent#isEnabled()
            if (methodName.equals(METHOD_IS_ENABLED) && methodDesc.equals(METHOD_IS_ENABLED_DESC)) {
                updateEnabledMethod(mb);

            // MyEvent#begin()
            } else if (methodName.equals(METHOD_BEGIN) && methodDesc.equals(METHOD_BEGIN_DESC)) {
                mb.withCode(cob -> {
                    cob.aload(0);
                    cob.invokestatic(TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP, METHOD_TIME_STAMP_DESC);
                    cob.putfield(ClassDesc.ofInternalName(getInternalClassName()), FIELD_START_TIME, CD_long);
                    cob.return_();
                });

            // MyEvent#end()
            } else if (methodName.equals(METHOD_END) && methodDesc.equals(METHOD_END_DESC)) {
                mb.withCode(cob -> {
                    cob.aload(0);
                    cob.aload(0);
                    cob.getfield(ClassDesc.ofInternalName(getInternalClassName()), FIELD_START_TIME, CD_long);
                    cob.invokestatic(TYPE_EVENT_CONFIGURATION, METHOD_DURATION, METHOD_DURATION_DESC);
                    cob.putfield(ClassDesc.ofInternalName(getInternalClassName()), FIELD_DURATION, CD_long);
                    cob.return_();
                });

            } else if (methodName.equals(METHOD_COMMIT)) {
                // MyEvent#commit() or static MyEvent#commit(...)
                if (staticCommitMethodDesc != null) {
                    if (methodDesc.equals(METHOD_COMMIT_DESC)) {
                        updateExistingWithEmptyVoidMethod(mb);
                    } else if (methodDesc.equals(staticCommitMethodDesc)) {
                        mb.withCode(cob -> {
                            // indexes the argument type array, the argument type array does not include
                            // 'this'
                            int argIndex = 0;
                            // indexes the proper slot in the local variable table, takes type size into
                            // account, therefore sometimes argIndex != slotIndex
                            int slotIndex = 0;
                            int fieldIndex = 0;
                            ClassDesc[] argumentTypes = staticCommitMethodDesc.parameterArray();
                            Label start = cob.newLabel();
                            Label endTryBlock = cob.newLabel();
                            Label exceptionHandler = cob.newLabel();
//                            mv.visitTryCatchBlock(start, endTryBlock, exceptionHandler, "java/lang/Throwable");
//                            mv.visitLabel(start);
//                            getEventWriter(mv);
//                            // stack: [EW]
//                            mv.visitInsn(Opcodes.DUP);
//                            // stack: [EW], [EW]
//                            // write begin event
//                            getEventConfiguration(mv);
//                            // stack: [EW], [EW], [EventConfiguration]
//                            mv.visitLdcInsn(eventTypeId);
//                            // stack: [EW], [EW], [EventConfiguration] [long]
//                            visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.asASM());
//                            // stack: [EW], [integer]
//                            Label excluded = new Label();
//                            mv.visitJumpInsn(Opcodes.IFEQ, excluded);
//                            // stack: [EW]
//                            // write startTime
//                            mv.visitInsn(Opcodes.DUP);
//                            // stack: [EW], [EW]
//                            mv.visitVarInsn(argumentTypes[argIndex].getOpcode(Opcodes.ILOAD), slotIndex);
//                            // stack: [EW], [EW], [long]
//                            slotIndex += argumentTypes[argIndex++].getSize();
//                            visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asASM());
//                            // stack: [EW]
//                            fieldIndex++;
//                            // write duration
//                            mv.visitInsn(Opcodes.DUP);
//                            // stack: [EW], [EW]
//                            mv.visitVarInsn(argumentTypes[argIndex].getOpcode(Opcodes.ILOAD), slotIndex);
//                            // stack: [EW], [EW], [long]
//                            slotIndex += argumentTypes[argIndex++].getSize();
//                            visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asASM());
//                            // stack: [EW]
//                            fieldIndex++;
//                            // write eventThread
//                            mv.visitInsn(Opcodes.DUP);
//                            // stack: [EW], [EW]
//                            visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.asASM());
//                            // stack: [EW]
//                            // write stackTrace
//                            mv.visitInsn(Opcodes.DUP);
//                            // stack: [EW], [EW]
//                            visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.asASM());
//                            // stack: [EW]
//                            // write custom fields
//                            while (fieldIndex < fieldInfos.size()) {
//                                mv.visitInsn(Opcodes.DUP);
//                                // stack: [EW], [EW]
//                                mv.visitVarInsn(argumentTypes[argIndex].getOpcode(Opcodes.ILOAD), slotIndex);
//                                // stack:[EW], [EW], [field]
//                                slotIndex += argumentTypes[argIndex++].getSize();
//                                FieldInfo field = fieldInfos.get(fieldIndex);
//                                EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
//                                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, eventMethod.asASM());
//                                // stack: [EW]
//                                fieldIndex++;
//                            }
//                            // stack: [EW]
//                            // write end event (writer already on stack)
//                            visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.asASM());
//                            // stack [integer]
//                            // notified -> restart event write attempt
//                            mv.visitJumpInsn(Opcodes.IFEQ, start);
//                            // stack:
//                            mv.visitLabel(endTryBlock);
//                            Label end = new Label();
//                            mv.visitJumpInsn(Opcodes.GOTO, end);
//                            mv.visitLabel(exceptionHandler);
//                            // stack: [ex]
//                            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
//                            getEventWriter(mv);
//                            // stack: [ex] [EW]
//                            mv.visitInsn(Opcodes.DUP);
//                            // stack: [ex] [EW] [EW]
//                            Label rethrow = new Label();
//                            mv.visitJumpInsn(Opcodes.IFNULL, rethrow);
//                            // stack: [ex] [EW]
//                            mv.visitInsn(Opcodes.DUP);
//                            // stack: [ex] [EW] [EW]
//                            visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, METHOD_RESET);
//                            mv.visitLabel(rethrow);
//                            // stack:[ex] [EW]
//                            mv.visitFrame(Opcodes.F_SAME, 0, null, 2, new Object[] { "java/lang/Throwable", TYPE_EVENT_WRITER.getInternalName() });
//                            mv.visitInsn(Opcodes.POP);
//                            // stack:[ex]
//                            mv.visitInsn(Opcodes.ATHROW);
//                            mv.visitLabel(excluded);
//                            // stack: [EW]
//                            mv.visitFrame(Opcodes.F_SAME, 0, null, 1, new Object[] { TYPE_EVENT_WRITER.getInternalName() });
//                            mv.visitInsn(Opcodes.POP);
//                            mv.visitLabel(end);
//                            // stack:
//                            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                            cob.return_();
                        });
                    }
                } else {
                    mb.withCode(cob -> {
//                        // if (!isEnable()) {
//                        // return;
//                        // }
//                        methodVisitor.visitCode();
//                        Label start = new Label();
//                        Label endTryBlock = new Label();
//                        Label exceptionHandler = new Label();
//                        methodVisitor.visitTryCatchBlock(start, endTryBlock, exceptionHandler, "java/lang/Throwable");
//                        methodVisitor.visitLabel(start);
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, getInternalClassName(), METHOD_IS_ENABLED.getName(), METHOD_IS_ENABLED.getDescriptor(), false);
//                        Label l0 = new Label();
//                        methodVisitor.visitJumpInsn(Opcodes.IFNE, l0);
//                        methodVisitor.visitInsn(Opcodes.RETURN);
//                        methodVisitor.visitLabel(l0);
//                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
//                        // if (startTime == 0) {
//                        // startTime = EventWriter.timestamp();
//                        // } else {
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_START_TIME, "J");
//                        methodVisitor.visitInsn(Opcodes.LCONST_0);
//                        methodVisitor.visitInsn(Opcodes.LCMP);
//                        Label durationalEvent = new Label();
//                        methodVisitor.visitJumpInsn(Opcodes.IFNE, durationalEvent);
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_TIME_STAMP.getName(), METHOD_TIME_STAMP.getDescriptor(), false);
//                        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, getInternalClassName(), FIELD_START_TIME, "J");
//                        Label commit = new Label();
//                        methodVisitor.visitJumpInsn(Opcodes.GOTO, commit);
//                        // if (duration == 0) {
//                        // duration = EventWriter.timestamp() - startTime;
//                        // }
//                        // }
//                        methodVisitor.visitLabel(durationalEvent);
//                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_DURATION, "J");
//                        methodVisitor.visitInsn(Opcodes.LCONST_0);
//                        methodVisitor.visitInsn(Opcodes.LCMP);
//                        methodVisitor.visitJumpInsn(Opcodes.IFNE, commit);
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_TIME_STAMP.getName(), METHOD_TIME_STAMP.getDescriptor(), false);
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_START_TIME, "J");
//                        methodVisitor.visitInsn(Opcodes.LSUB);
//                        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, getInternalClassName(), FIELD_DURATION, "J");
//                        methodVisitor.visitLabel(commit);
//                        // if (shouldCommit()) {
//                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        invokeVirtual(methodVisitor, getInternalClassName(), METHOD_EVENT_SHOULD_COMMIT);
//                        Label end = new Label();
//                        methodVisitor.visitJumpInsn(Opcodes.IFEQ, end);
//                        getEventWriter(methodVisitor);
//                        // stack: [EW]
//                        methodVisitor.visitInsn(Opcodes.DUP);
//                        // stack: [EW] [EW]
//                        getEventConfiguration(methodVisitor);
//                        // stack: [EW] [EW] [EC]
//                        methodVisitor.visitLdcInsn(eventTypeId);
//                        invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.asmMethod);
//                        Label excluded = new Label();
//                        // stack: [EW] [int]
//                        methodVisitor.visitJumpInsn(Opcodes.IFEQ, excluded);
//                        // stack: [EW]
//                        int fieldIndex = 0;
//                        methodVisitor.visitInsn(Opcodes.DUP);
//                        // stack: [EW] [EW]
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        // stack: [EW] [EW] [this]
//                        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_START_TIME, "J");
//                        // stack: [EW] [EW] [long]
//                        invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asmMethod);
//                        // stack: [EW]
//                        fieldIndex++;
//                        methodVisitor.visitInsn(Opcodes.DUP);
//                        // stack: [EW] [EW]
//                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                        // stack: [EW] [EW] [this]
//                        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_DURATION, "J");
//                        // stack: [EW] [EW] [long]
//                        invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asmMethod);
//                        // stack: [EW]
//                        fieldIndex++;
//                        methodVisitor.visitInsn(Opcodes.DUP);
//                        // stack: [EW] [EW]
//                        invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.asASM());
//                        // stack: [EW]
//                        methodVisitor.visitInsn(Opcodes.DUP);
//                        // stack: [EW] [EW]
//                        invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.asASM());
//                        // stack: [EW]
//                        while (fieldIndex < fieldInfos.size()) {
//                            FieldInfo field = fieldInfos.get(fieldIndex);
//                            methodVisitor.visitInsn(Opcodes.DUP);
//                            // stack: [EW] [EW]
//                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
//                            // stack: [EW] [EW] [this]
//                            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), field.fieldName, field.fieldDescriptor);
//                            // stack: [EW] [EW] <T>
//                            EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
//                            invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, eventMethod.asmMethod);
//                            // stack: [EW]
//                            fieldIndex++;
//                        }
//                        // stack:[EW]
//                        invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.asASM());
//                        // stack [int]
//                        // notified -> restart event write attempt
//                        methodVisitor.visitJumpInsn(Opcodes.IFEQ, start);
//                        methodVisitor.visitLabel(endTryBlock);
//                        methodVisitor.visitJumpInsn(Opcodes.GOTO, end);
//                        methodVisitor.visitLabel(exceptionHandler);
//                        // stack: [ex]
//                        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
//                        getEventWriter(methodVisitor);
//                        // stack: [ex] [EW]
//                        methodVisitor.visitInsn(Opcodes.DUP);
//                        // stack: [ex] [EW] [EW]
//                        Label rethrow = new Label();
//                        methodVisitor.visitJumpInsn(Opcodes.IFNULL, rethrow);
//                        // stack: [ex] [EW]
//                        methodVisitor.visitInsn(Opcodes.DUP);
//                        // stack: [ex] [EW] [EW]
//                        visitMethod(methodVisitor, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, METHOD_RESET);
//                        methodVisitor.visitLabel(rethrow);
//                        // stack:[ex] [EW]
//                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 2, new Object[] { "java/lang/Throwable", TYPE_EVENT_WRITER.getInternalName() });
//                        methodVisitor.visitInsn(Opcodes.POP);
//                        // stack:[ex]
//                        methodVisitor.visitInsn(Opcodes.ATHROW);
//                        methodVisitor.visitLabel(excluded);
//                        // stack: [EW]
//                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 1, new Object[] { TYPE_EVENT_WRITER.getInternalName() });
//                        methodVisitor.visitInsn(Opcodes.POP);
//                        methodVisitor.visitLabel(end);
//                        // stack:
//                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        cob.return_();


//                        // if (!isEnable()) {
//                        // return;
//                        // }
//                        cob.loadInstruction(TypeKind.ReferenceType, 0);
//                        cob.invokeInstruction(Opcode.INVOKEVIRTUAL, ClassDesc.ofInternalName(getInternalClassName()), METHOD_IS_ENABLED, MethodTypeDesc.ofDescriptor(METHOD_IS_ENABLED_DESC), false);
//                        var l0 = cob.newLabel();
//                        cob.branchInstruction(Opcode.IFNE, l0);
//                        cob.returnInstruction(TypeKind.VoidType);
//                        cob.labelBinding(l0);
//    //                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
//                        // if (startTime == 0) {
//                        // startTime = EventWriter.timestamp();
//                        // } else {
//                        cob.loadInstruction(TypeKind.ReferenceType, 0);
//                        cob.fieldInstruction(Opcode.GETFIELD, ClassDesc.ofInternalName(getInternalClassName()), FIELD_START_TIME, ConstantDescs.CD_long);
//                        cob.constantInstruction(Opcode.LCONST_0, 0l);
//                        cob.operatorInstruction(Opcode.LCMP);
//                        var durationalEvent = cob.newLabel();
//                        cob.branchInstruction(Opcode.IFNE, durationalEvent);
//                        cob.loadInstruction(TypeKind.ReferenceType, 0);
//                        cob.invokeInstruction(Opcode.INVOKESTATIC, ClassDesc.ofInternalName(TYPE_EVENT_CONFIGURATION_NAME), METHOD_TIME_STAMP, MethodTypeDesc.ofDescriptor(METHOD_TIME_STAMP_DESC), false);
//                        cob.fieldInstruction(Opcode.PUTFIELD, ClassDesc.ofInternalName(getInternalClassName()), FIELD_START_TIME, ConstantDescs.CD_long);
//                        var commit = cob.newLabel();
//                        cob.branchInstruction(Opcode.GOTO, commit);
//                        // if (duration == 0) {
//                        // duration = EventWriter.timestamp() - startTime;
//                        // }
//                        // }
//                        cob.labelBinding(durationalEvent);
//    //                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
//                        cob.loadInstruction(TypeKind.ReferenceType, 0);
//                        cob.fieldInstruction(Opcode.GETFIELD, ClassDesc.ofInternalName(getInternalClassName()), FIELD_DURATION, ConstantDescs.CD_long);
//                        cob.constantInstruction(Opcode.LCONST_0, 0l);
//                        cob.operatorInstruction(Opcode.LCMP);
//                        cob.branchInstruction(Opcode.IFNE, commit);
//                        cob.loadInstruction(TypeKind.ReferenceType, 0);
//                        cob.invokeInstruction(Opcode.INVOKESTATIC, ClassDesc.ofInternalName(TYPE_EVENT_CONFIGURATION_NAME), METHOD_TIME_STAMP, MethodTypeDesc.ofDescriptor(METHOD_TIME_STAMP_DESC), false);
//                        cob.loadInstruction(TypeKind.ReferenceType, 0);
//                        cob.fieldInstruction(Opcode.GETFIELD, ClassDesc.ofInternalName(getInternalClassName()), FIELD_START_TIME, ConstantDescs.CD_long);
//                        cob.operatorInstruction(Opcode.LSUB);
//                        cob.fieldInstruction(Opcode.PUTFIELD, ClassDesc.ofInternalName(getInternalClassName()), FIELD_DURATION, ConstantDescs.CD_long);
//                        cob.labelBinding(commit);
//                        // if (shouldCommit()) {
//    //                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
//                        cob.loadInstruction(TypeKind.ReferenceType, 0);
//                        cob.invokeInstruction(Opcode.INVOKEVIRTUAL, ClassDesc.ofInternalName(getInternalClassName()), METHOD_EVENT_SHOULD_COMMIT, MethodTypeDesc.ofDescriptor(METHOD_EVENT_SHOULD_COMMIT_DESC), false);
//                        var end = cob.newLabel();
//                        // eventHandler.write(...);
//                        // }
//                        cob.branchInstruction(Opcode.IFEQ, end);
//                        getEventHandler(cob);
//
//                        cob.typeCheckInstruction(Opcode.CHECKCAST, ClassDesc.ofInternalName(TYPE_EVENT_CONFIGURATION_NAME));
//                        for (FieldInfo fi : fieldInfos) {
//                            cob.loadInstruction(TypeKind.ReferenceType, 0);
//                            cob.fieldInstruction(Opcode.GETFIELD, ClassDesc.ofInternalName(fi.internalClassName), fi.fieldName, ClassDesc.ofDescriptor(fi.fieldDescriptor));
//                        }
//
//                        cob.invokeInstruction(Opcode.INVOKEVIRTUAL, ClassDesc.ofInternalName(TYPE_EVENT_CONFIGURATION_NAME), METHOD_WRITE, MethodTypeDesc.ofDescriptor(writeMethodDesc), false);
//                        cob.labelBinding(end);
//    //                        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
//                        cob.returnInstruction(TypeKind.VoidType);
                    });
                }

            // MyEvent#shouldCommit()
            } else if (methodName.equals(METHOD_EVENT_SHOULD_COMMIT) && methodDesc.equals(METHOD_EVENT_SHOULD_COMMIT_DESC)) {
                mb.withCode(cob -> {
                    Label fail = cob.newLabel();
                    if (guardEventConfiguration) {
                        getEventConfiguration(cob);
                        cob.if_null(fail);
                    }
                    // if (!eventHandler.shouldCommit(duration) goto fail;
                    getEventConfiguration(cob);
                    cob.aload(0);
                    cob.getfield(ClassDesc.ofInternalName(getInternalClassName()), FIELD_DURATION, CD_long);
                    cob.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_DESC);
                    cob.ifeq(fail);
                    int index = 0;
                    for (SettingInfo si : settingInfos) {
                        // if (!settingsMethod(eventHandler.settingX)) goto fail;
                        cob.aload(0);
                        if (untypedEventConfiguration) {
                            cob.getstatic(ClassDesc.ofInternalName(getInternalClassName()), FIELD_EVENT_CONFIGURATION, ClassDesc.ofDescriptor(TYPE_OBJECT_DESCRIPTOR));
                        } else {
                            cob.getstatic(ClassDesc.ofInternalName(getInternalClassName()), FIELD_EVENT_CONFIGURATION, ClassDesc.ofDescriptor(TYPE_EVENT_CONFIGURATION_DESCRIPTOR));
                        }
                        cob.checkcast(TYPE_EVENT_CONFIGURATION);
                        cob.constantInstruction(index);
                        cob.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_GET_SETTING, METHOD_EVENT_CONFIGURATION_GET_SETTING_DESC);
                        cob.checkcast(si.paramType());
                        cob.invokevirtual(ClassDesc.ofInternalName(getInternalClassName()), si.methodName, MethodTypeDesc.of(CD_boolean, si.paramType()));
                        cob.ifeq(fail);
                        index++;
                    }
                    // return true
                    cob.iconst_1();
                    cob.ireturn();
                    // return false
                    cob.labelBinding(fail);
                    cob.iconst_0();
                    cob.ireturn();
                });
//        if (isJDK) {
//            if (hasStaticMethod(METHOD_ENABLED)) {
//                updateEnabledMethod(METHOD_ENABLED);
//            };
//            updateIfStaticMethodExists(METHOD_SHOULD_COMMIT_LONG, methodVisitor -> {
//                Label fail = new Label();
//                if (guardEventConfiguration) {
//                    // if (eventConfiguration == null) goto fail;
//                    getEventConfiguration(methodVisitor);
//                    methodVisitor.visitJumpInsn(Opcodes.IFNULL, fail);
//                }
//                // return eventConfiguration.shouldCommit(duration);
//                getEventConfiguration(methodVisitor);
//                methodVisitor.visitVarInsn(Opcodes.LLOAD, 0);
//                invokeVirtual(methodVisitor, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT);
//                methodVisitor.visitInsn(Opcodes.IRETURN);
//                // fail:
//                methodVisitor.visitLabel(fail);
//                // return false
//                methodVisitor.visitInsn(Opcodes.ICONST_0);
//                methodVisitor.visitInsn(Opcodes.IRETURN);
//                methodVisitor.visitMaxs(0, 0);
//                methodVisitor.visitEnd();
//            });
//            updateIfStaticMethodExists(METHOD_TIME_STAMP, methodVisitor -> {
//                invokeStatic(methodVisitor, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_TIME_STAMP);
//                methodVisitor.visitInsn(Opcodes.LRETURN);
//                methodVisitor.visitMaxs(0, 0);
//                methodVisitor.visitEnd();
//            });
//        }
            } else {
                mb.accept(me);
            }
        });
    }

    private void updateEnabledMethod(MethodBuilder mb) {
        mb.withCode(cob -> {
            Label nullLabel = cob.newLabel();
            if (guardEventConfiguration) {
                getEventConfiguration(cob);
                cob.if_null(nullLabel);
            }
            getEventConfiguration(cob);
            cob.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_IS_ENABLED, METHOD_IS_ENABLED_DESC);
            cob.ireturn();
            if (guardEventConfiguration) {
                cob.labelBinding(nullLabel);
//                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                cob.iconst_0();
                cob.ireturn();
            }
        });
    }

    private void getEventConfiguration(CodeBuilder cob) {
        if (untypedEventConfiguration) {
            cob.getstatic(ClassDesc.ofInternalName(getInternalClassName()), FIELD_EVENT_CONFIGURATION, CD_Object);
        } else {
            cob.getstatic(ClassDesc.ofInternalName(getInternalClassName()), FIELD_EVENT_CONFIGURATION, TYPE_EVENT_CONFIGURATION);
        }
    }

    private ClassTransform makeUninstrumented() {
        return ClassTransform.transformingMethods(adaptPredicate, (mb, me) -> {
            MethodModel mm = mb.original().orElseThrow();
            String methodName = mm.methodName().stringValue();
            String methodDesc = mm.methodType().stringValue();

            if ((methodName.equals(METHOD_EVENT_SHOULD_COMMIT) && methodDesc.equals(METHOD_EVENT_SHOULD_COMMIT_DESC))
                        || (methodName.equals(METHOD_IS_ENABLED) && methodDesc.equals(METHOD_IS_ENABLED_DESC))) {
                    mb.withCode(cob ->
                            cob.constantInstruction(Opcode.ICONST_0, 0).returnInstruction(TypeKind.IntType));
                } else if ((methodName.equals(METHOD_COMMIT) && methodDesc.equals(METHOD_COMMIT_DESC))
                        || (methodName.equals(METHOD_BEGIN) && methodDesc.equals(METHOD_BEGIN_DESC))
                        || (methodName.equals(METHOD_END) && methodDesc.equals(METHOD_END_DESC))){
                    mb.withCode(cob ->
                            cob.returnInstruction(TypeKind.VoidType));
                } else {
                    mb.accept(me);
                }
        });
    }

    private final void updateExistingWithEmptyVoidMethod(MethodBuilder voidMethod) {
        voidMethod.withCode(cob -> cob.return_());
    }

    public static String makeWriteMethodDesc(List<FieldInfo> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (FieldInfo v : fields) {
            sb.append(v.fieldDescriptor);
        }
        sb.append(")V");
        return sb.toString();
     }

    private String getInternalClassName() {
        return classNode.thisClass().name().stringValue();
    }

    public String getEventName() {
        return eventName;
    }
}
