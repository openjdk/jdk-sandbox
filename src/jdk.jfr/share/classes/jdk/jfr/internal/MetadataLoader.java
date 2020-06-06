/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Experimental;
import jdk.jfr.Label;
import jdk.jfr.Period;
import jdk.jfr.Relational;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.TransitionFrom;
import jdk.jfr.TransitionTo;
import jdk.jfr.Unsigned;

public final class MetadataLoader {

    // <Event>, <Type> and <Relation>
    final static class TypeElement {
        final List<FieldElement> fields;
        final String name;
        final String label;
        final String description;
        final String category;
        final String period;
        final boolean thread;
        final boolean startTime;
        final boolean stackTrace;
        final boolean cutoff;
        final boolean isEvent;
        final boolean isRelation;
        final boolean experimental;
        final long id;

        public TypeElement(DataInputStream dis) throws IOException {
            int fieldCount = dis.readInt();
            fields = new ArrayList<>(fieldCount);
            for (int i = 0; i < fieldCount; i++) {
                fields.add(new FieldElement(dis));
            }
            name = dis.readUTF();
            label = dis.readUTF();
            description = dis.readUTF();
            category = dis.readUTF();
            thread = dis.readBoolean();
            stackTrace = dis.readBoolean();
            startTime = dis.readBoolean();
            period = dis.readUTF();
            cutoff = dis.readBoolean();
            experimental = dis.readBoolean();
            id = dis.readLong();
            isEvent = dis.readBoolean();
            isRelation = dis.readBoolean();
        }
    }

    // <Field>
    static class FieldElement {
        final String name;
        final String label;
        final String description;
        final String typeName;
        final String annotations;
        final String transition;
        final String relation;
        final boolean struct;
        final boolean array;
        final boolean experimental;
        final boolean unsigned;

        public FieldElement(DataInputStream dis) throws IOException {
            name = dis.readUTF();
            typeName = dis.readUTF();
            label = dis.readUTF();
            description = dis.readUTF();
            struct = dis.readBoolean();
            array = dis.readBoolean();
            unsigned = dis.readBoolean();
            annotations = dis.readUTF();
            transition = dis.readUTF();
            relation = dis.readUTF();
            experimental = dis.readBoolean();
        }
    }

    final List<TypeElement> types; /*= new HashMap<>(200);*/
    final Map<String, List<AnnotationElement>> anotationElements = new HashMap<>(20);

    MetadataLoader(DataInputStream dis) throws IOException {
        int typeCount = dis.readInt();
        types = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            TypeElement t = new TypeElement(dis);
            types.add(t);
        }
    }

    private List<AnnotationElement> createAnnotationElements(String annotation) throws InternalError {
        String[] annotations = annotation.split(",");
        List<AnnotationElement> annotationElements = new ArrayList<>();
        for (String a : annotations) {
            a = a.trim();
            int leftParenthesis = a.indexOf("(");
            if (leftParenthesis == -1) {
                annotationElements.add(new AnnotationElement(createAnnotationClass(a)));
            } else {
                int rightParenthesis = a.lastIndexOf(")");
                if (rightParenthesis == -1) {
                    throw new InternalError("Expected closing parenthesis for 'XMLContentType'");
                }
                String value = a.substring(leftParenthesis + 1, rightParenthesis);
                String type = a.substring(0, leftParenthesis);
                annotationElements.add(new AnnotationElement(createAnnotationClass(type), value));
            }
        }
        return annotationElements;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Annotation> createAnnotationClass(String type) {
        try {
            if (!type.startsWith("jdk.jfr.")) {
                throw new IllegalStateException("Incorrect type " + type + ". Annotation class must be located in jdk.jfr package.");
            }
            Class<?> c = Class.forName(type, true, null);
            return (Class<? extends Annotation>) c;
        } catch (ClassNotFoundException cne) {
            throw new IllegalStateException(cne);
        }
    }

    public static Collection<Type> createTypes() throws IOException {
        try (DataInputStream dis =
                new DataInputStream(MetadataLoader.class.
                getResourceAsStream("/jdk/jfr/internal/types/metadata.bin"))) {
            MetadataLoader ml = new MetadataLoader(dis);
            return ml.buildTypes();
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private Collection<Type> buildTypes() {
        Map<String, Type> typeMap = buildTypeMap();
        Map<String, AnnotationElement> relationMap = buildRelationMap(typeMap);
        addFields(typeMap, relationMap);
        return typeMap.values();
    }

    private Map<String, AnnotationElement> buildRelationMap(Map<String, Type> typeMap) {
        Map<String, AnnotationElement> relationMap = new HashMap<>();
        for (TypeElement t : types) {
            if (t.isRelation) {
                Type relationType = typeMap.get(t.name);
                AnnotationElement ae = PrivateAccess.getInstance().newAnnotation(relationType, Collections.emptyList(), true);
                relationMap.put(t.name, ae);
            }
        }
        return relationMap;
    }

    private void addFields(Map<String, Type> lookup, Map<String, AnnotationElement> relationMap) {
        for (TypeElement te : types) {
            Type type = lookup.get(te.name);
            if (te.isEvent) {
                boolean periodic = !te.period.isEmpty();
                TypeLibrary.addImplicitFields(type, periodic, te.startTime && !periodic, te.thread, te.stackTrace && !periodic, te.cutoff);
            }
            for (FieldElement f : te.fields) {
                Type fieldType = Type.getKnownType(f.typeName);
                if (fieldType == null) {
                    fieldType = Objects.requireNonNull(lookup.get(f.typeName));
                }
                List<AnnotationElement> aes = new ArrayList<>();
                if (f.unsigned) {
                    aes.add(new AnnotationElement(Unsigned.class));
                }
                if (!f.annotations.isEmpty()) {
                    var ae = anotationElements.get(f.annotations);
                    if (ae == null) {
                        ae = createAnnotationElements(f.annotations);
                        anotationElements.put(f.annotations, ae);
                    }
                    aes.addAll(ae);
                }
                if (!f.relation.isEmpty()) {
                    AnnotationElement t = relationMap.get(f.relation);
                    aes.add(Objects.requireNonNull(t));
                }
                if (!f.label.isEmpty()) {
                    aes.add(new AnnotationElement(Label.class, f.label));
                }
                if (f.experimental) {
                    aes.add(new AnnotationElement(Experimental.class));
                }
                if (!f.description.isEmpty()) {
                    aes.add(new AnnotationElement(Description.class, f.description));
                }
                if ("from".equals(f.transition)) {
                    aes.add(new AnnotationElement(TransitionFrom.class));
                }
                if ("to".equals(f.transition)) {
                    aes.add(new AnnotationElement(TransitionTo.class));
                }
                boolean primitive = te.fields.isEmpty();
                boolean constantPool = !f.struct && !primitive;
                type.add(PrivateAccess.getInstance().newValueDescriptor(f.name, fieldType, aes, f.array ? 1 : 0, constantPool, null));
            }
        }
    }

    private Map<String, Type> buildTypeMap() {
        Map<String, Type> typeMap = new HashMap<>();
        Map<String, Type> knownTypeMap = new HashMap<>();
        for (Type kt : Type.getKnownTypes()) {
            typeMap.put(kt.getName(), kt);
            knownTypeMap.put(kt.getName(), kt);
        }
        for (TypeElement t : types) {
            List<AnnotationElement> aes = new ArrayList<>();
            if (!t.category.isEmpty()) {
                aes.add(new AnnotationElement(Category.class, buildCategoryArray(t.category)));
            }
            if (!t.label.isEmpty()) {
                aes.add(new AnnotationElement(Label.class, t.label));
            }
            if (!t.description.isEmpty()) {
                aes.add(new AnnotationElement(Description.class, t.description));
            }
            if (t.isEvent) {
                if (!t.period.isEmpty()) {
                    aes.add(new AnnotationElement(Period.class, t.period));
                } else {
                    if (t.startTime) {
                        aes.add(new AnnotationElement(Threshold.class, "0 ns"));
                    }
                    if (t.stackTrace) {
                        aes.add(new AnnotationElement(StackTrace.class, true));
                    }
                }
                if (t.cutoff) {
                    aes.add(new AnnotationElement(Cutoff.class, Cutoff.INFINITY));
                }
            }
            if (t.experimental) {
                aes.add(new AnnotationElement(Experimental.class));
            }
            Type type;
            if (t.isEvent) {
                aes.add(new AnnotationElement(Enabled.class, false));
                type = new PlatformEventType(t.name, t.id, false, true);
            } else {
                if (knownTypeMap.containsKey(t.name)) {
                    type = knownTypeMap.get(t.name);
                } else {
                    if (t.isRelation) {
                        type = new Type(t.name, Type.SUPER_TYPE_ANNOTATION, t.id);
                        aes.add(new AnnotationElement(Relational.class));
                    } else {
                        type = new Type(t.name, null, t.id);
                    }
                }
            }
            type.setAnnotations(aes);
            typeMap.put(t.name, type);
        }
        return typeMap;
    }

    private String[] buildCategoryArray(String category) {
        List<String> categories = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : category.toCharArray()) {
            if (c == ',') {
                categories.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        categories.add(sb.toString().trim());
        return categories.toArray(new String[0]);
    }
}
