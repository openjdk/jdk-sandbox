/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.AccessFlag;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jdk.classfile.ClassModel;
import jdk.classfile.Classfile;
import jdk.classfile.CodeElement;
import jdk.classfile.CodeModel;
import jdk.classfile.CodeTransform;
import jdk.classfile.FieldModel;
import jdk.classfile.MethodModel;
import jdk.classfile.TypeKind;
import jdk.classfile.impl.Util;
import jdk.classfile.instruction.BranchInstruction;
import jdk.classfile.instruction.InvokeInstruction;
import jdk.classfile.instruction.LookupSwitchInstruction;
import jdk.classfile.instruction.TableSwitchInstruction;
import jdk.classfile.transforms.ClassRemapper;
import jdk.classfile.transforms.CodeLocalsShifter;
import jdk.classfile.transforms.LabelsRemapper;

import jdk.jfr.internal.SecuritySupport;

/**
 * This class will perform byte code instrumentation given an "instrumentor" class.
 *
 * @see JITracer
 *
 * @author Staffan Larsen
 */
@Deprecated
final class JIClassInstrumentation {
    private final Class<?> instrumentor;
    private final String targetName;
    private final String instrumentorName;
    private final byte[] newBytes;
    private final ClassModel targetClassModel;
    private final ClassModel instrClassModel;

    /**
     * Creates an instance and performs the instrumentation.
     *
     * @param instrumentor instrumentor class
     * @param target target class
     * @param old_target_bytes bytes in target
     *
     * @throws ClassNotFoundException
     * @throws IOException
     */
    JIClassInstrumentation(Class<?> instrumentor, Class<?> target, byte[] old_target_bytes) throws ClassNotFoundException, IOException {
        instrumentorName = instrumentor.getName();
        this.targetName = target.getName();
        this.instrumentor = instrumentor;
        this.targetClassModel = Classfile.parse(old_target_bytes);
        this.instrClassModel = Classfile.parse(getOriginalClassBytes(instrumentor));
        //target model have invalid stack maps, so it needs to be extra scanned to resolve all labels
        for (var m : targetClassModel.methods()) {
            m.code().ifPresent(c -> c.forEachElement(el -> {
                if (el instanceof BranchInstruction br) {
                    br.target();
                } else if (el instanceof TableSwitchInstruction ts) {
                    ts.defaultTarget();
                    ts.cases();
                } else if (el instanceof LookupSwitchInstruction ls) {
                    ls.defaultTarget();
                    ls.cases();
                }
            }));
        }
        this.newBytes = makeBytecode();
    }

    private static byte[] getOriginalClassBytes(Class<?> clazz) throws IOException {
        String name = "/" + clazz.getName().replace(".", "/") + ".class";
        try (InputStream is = SecuritySupport.getResourceAsStream(name)) {
            return is.readAllBytes();
        }
    }

    private byte[] makeBytecode() throws IOException, ClassNotFoundException {

        // Find the methods to instrument and inline

        final Set<String> instrumentationMethods = new HashSet<>();
        for (final Method m : instrumentor.getDeclaredMethods()) {
            JIInstrumentationMethod im = m.getAnnotation(JIInstrumentationMethod.class);
            if (im != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(m.getName()).append('(');
                for (var parameter : m.getParameterTypes()) {
                    sb.append(parameter.descriptorString());
                }
                sb.append(')').append(m.getReturnType().descriptorString());
                instrumentationMethods.add(sb.toString());
            }
        }

        return instrument(targetClassModel, instrClassModel, mm -> instrumentationMethods.contains(mm.methodName().stringValue() + mm.methodType().stringValue()));
    }

    /**
     * Get the instrumented byte codes that can be used to retransform the class.
     *
     * @return bytes
     */
    public byte[] getNewBytes() {
        return newBytes.clone();
    }

    private static byte[] instrument(ClassModel target, ClassModel instrumentor, Predicate<MethodModel> instrumentedMethodsFilter) {
        var instrumentorCodeMap = instrumentor.methods().stream()
                                              .filter(instrumentedMethodsFilter)
                                              .collect(Collectors.toMap(mm -> mm.methodName().stringValue() + mm.methodType().stringValue(), mm -> mm.code().orElse(null)));
        var targetFieldNames = target.fields().stream().map(f -> f.fieldName().stringValue()).collect(Collectors.toSet());
        var targetMethods = target.methods().stream().map(m -> m.methodName().stringValue() + m.methodType().stringValue()).collect(Collectors.toSet());
        var instrumentorClassRemapper = ClassRemapper.of(Map.of(instrumentor.thisClass().asSymbol(), target.thisClass().asSymbol()));
        return Classfile.build(target.thisClass().asSymbol(), clb -> {
            target.forEachElement(cle -> {
                CodeModel instrumentorCodeModel;
                if (cle instanceof MethodModel mm && ((instrumentorCodeModel = instrumentorCodeMap.get(mm.methodName().stringValue() + mm.methodType().stringValue())) != null)) {
                    clb.withMethod(mm.methodName().stringValue(), mm.methodTypeSymbol(), mm.flags().flagsMask(), mb -> mm.forEachElement(me -> {
                        if (me instanceof CodeModel targetCodeModel) {
                            //instrumented methods are merged
                            var instrumentorLocalsShifter = new CodeLocalsShifter(mm.flags(), mm.methodTypeSymbol());
                            var instrumentorCodeRemapperAndShifter =
                                    instrumentorClassRemapper.codeTransform()
                                                             .andThen(instrumentorLocalsShifter);
                            CodeTransform invokeInterceptor
                                    = (codeBuilder, instrumentorCodeElement) -> {
                                if (instrumentorCodeElement instanceof InvokeInstruction inv
                                    && instrumentor.thisClass().asInternalName().equals(inv.owner().asInternalName())
                                    && mm.methodName().stringValue().equals(inv.name().stringValue())
                                    && mm.methodType().stringValue().equals(inv.type().stringValue())) {
                                    //store stacked arguments (in reverse order)
                                    record Arg(TypeKind tk, int slot) {}
                                    var storeStack = new LinkedList<Arg>();
                                    int slot = 0;
                                    if (!mm.flags().has(AccessFlag.STATIC)) {
                                        storeStack.add(new Arg(TypeKind.ReferenceType, slot++));
                                    }
                                    var it = Util.parameterTypes(mm.methodType().stringValue());
                                    while (it.hasNext()) {
                                        var tk = TypeKind.fromDescriptor(it.next());
                                        storeStack.add(new Arg(tk, slot));
                                        slot += tk.slotSize();
                                    }
                                    while (!storeStack.isEmpty()) {
                                        var arg = storeStack.removeLast();
                                        codeBuilder.storeInstruction(arg.tk, arg.slot);
                                    }
                                    var endLabel = codeBuilder.newLabel();
                                    //inlined target locals must be shifted based on the actual instrumentor locals shifter next free slot, relabeled and returns must be replaced with goto
                                    var sequenceTransform =
                                            instrumentorLocalsShifter.fork()
                                                                     .andThen(LabelsRemapper.remapLabels())
                                                                     .andThen((innerBuilder, shiftedRelabeledTargetCode) -> {
                                                                         if (shiftedRelabeledTargetCode.codeKind() == CodeElement.Kind.RETURN) {
                                                                             innerBuilder.goto_w(endLabel);
                                                                         }
                                                                         else
                                                                             innerBuilder.with(shiftedRelabeledTargetCode);
                                                                     })
                                                                     .andThen(CodeTransform.endHandler(b -> codeBuilder.labelBinding(endLabel)));
                                    codeBuilder.transform(targetCodeModel, sequenceTransform);
                                }
                                else
                                    codeBuilder.with(instrumentorCodeElement);
                            };
                            mb.transformCode(instrumentorCodeModel,
                                         invokeInterceptor.andThen(instrumentorCodeRemapperAndShifter));
                        }
                        else {
                            mb.with(me);
                        }
                    }));
                }
                else {
                    clb.with(cle);
                }
            });
            var remapperConsumer = instrumentorClassRemapper.classTransform().resolve(clb).consumer();
            instrumentor.forEachElement(cle -> {
                //remaining instrumentor fields and methods are remapped and moved
                if (cle instanceof FieldModel fm && !targetFieldNames.contains(fm.fieldName().stringValue())) {
                    remapperConsumer.accept(cle);
                }
                else if (cle instanceof MethodModel mm && !"<init>".equals(mm.methodName().stringValue()) && !targetMethods.contains(mm.methodName().stringValue() + mm.methodType().stringValue())) {
                    remapperConsumer.accept(cle);
                }
            });
        });
    }
}
