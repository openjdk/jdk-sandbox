/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import jdk.classfile.*;
import jdk.classfile.constantpool.*;
import jdk.classfile.attribute.ExceptionsAttribute;
import jdk.internal.misc.VM;
import sun.invoke.util.Wrapper;
import sun.security.action.GetBooleanAction;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static jdk.classfile.Classfile.*;

/**
 * ProxyGenerator contains the code to generate a dynamic proxy class
 * for the java.lang.reflect.Proxy API.
 * <p>
 * The external interface to ProxyGenerator is the static
 * "generateProxyClass" method.
 */
final class ProxyGenerator {
    private static final int CLASSFILE_VERSION = VM.classFileVersion();
    private static final ClassDesc JL_CLASS_NOT_FOUND_EX = ClassDesc.ofInternalName("java/lang/ClassNotFoundException");
    private static final ClassDesc JL_ILLEGAL_ACCESS_EX = ClassDesc.ofInternalName("java/lang/IllegalAccessException");

    private static final ClassDesc JL_NO_CLASS_DEF_FOUND_ERROR = ClassDesc.ofInternalName("java/lang/NoClassDefFoundError");
    private static final ClassDesc JL_NO_SUCH_METHOD_EX = ClassDesc.ofInternalName("java/lang/NoSuchMethodException");
    private static final ClassDesc JL_NO_SUCH_METHOD_ERROR = ClassDesc.ofInternalName("java/lang/NoSuchMethodError");
    private static final ClassDesc JLI_LOOKUP = ClassDesc.ofInternalName("java/lang/invoke/MethodHandles$Lookup");

    private static final ClassDesc JLR_INVOCATION_HANDLER = ClassDesc.ofInternalName("java/lang/reflect/InvocationHandler");
    private static final ClassDesc JLR_PROXY = ClassDesc.ofInternalName("java/lang/reflect/Proxy");
    private static final ClassDesc JLR_UNDECLARED_THROWABLE_EX = ClassDesc.ofInternalName("java/lang/reflect/UndeclaredThrowableException");

    private static final ClassDesc LJL_CLASSLOADER = ClassDesc.ofDescriptor("Ljava/lang/ClassLoader;");
    private static final ClassDesc LJLR_METHOD = ClassDesc.ofDescriptor("Ljava/lang/reflect/Method;");
    private static final ClassDesc LJLR_INVOCATION_HANDLER = ClassDesc.ofDescriptor("Ljava/lang/reflect/InvocationHandler;");

    private static final MethodTypeDesc MJLR_INVOCATIONHANDLER = MethodTypeDesc.of(CD_void, JLR_INVOCATION_HANDLER);

    private static final String NAME_CTOR = "<init>";
    private static final String NAME_CLINIT = "<clinit>";
    private static final String NAME_LOOKUP_ACCESSOR = "proxyClassLookup";

    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    /**
     * name of field for storing a proxy instance's invocation handler
     */
    private static final String handlerFieldName = "h";

    /**
     * debugging flag for saving generated class files
     */
    @SuppressWarnings("removal")
    private static final boolean saveGeneratedFiles =
            java.security.AccessController.doPrivileged(
                    new GetBooleanAction(
                            "jdk.proxy.ProxyGenerator.saveGeneratedFiles"));

    /* Preloaded ProxyMethod objects for methods in java.lang.Object */
    private static final ProxyMethod hashCodeMethod;
    private static final ProxyMethod equalsMethod;
    private static final ProxyMethod toStringMethod;

    static {
        try {
            hashCodeMethod = new ProxyMethod(Object.class.getMethod("hashCode"), "m0");
            equalsMethod = new ProxyMethod(Object.class.getMethod("equals", Object.class), "m1");
            toStringMethod = new ProxyMethod(Object.class.getMethod("toString"), "m2");
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    /**
     * Class loader
     */
    private final ClassLoader loader;

    /**
     * Name of proxy class
     */
    private final String className;

    /**
     * Proxy interfaces
     */
    private final List<Class<?>> interfaces;

    /**
     * Proxy class access flags
     */
    private final int accessFlags;

    /**
     * Maps method signature string to list of ProxyMethod objects for
     * proxy methods with that signature.
     * Kept in insertion order to make it easier to compare old and new.
     */
    private final Map<String, List<ProxyMethod>> proxyMethods = new LinkedHashMap<>();

    /**
     * Ordinal of next ProxyMethod object added to proxyMethods.
     * Indexes are reserved for hashcode(0), equals(1), toString(2).
     */
    private int proxyMethodCount = 3;

    /**
     * Construct a ProxyGenerator to generate a proxy class with the
     * specified name and for the given interfaces.
     * <p>
     * A ProxyGenerator object contains the state for the ongoing
     * generation of a particular proxy class.
     */
    private ProxyGenerator(ClassLoader loader, String className, List<Class<?>> interfaces,
                           int accessFlags) {
        this.loader = loader;
        this.className = className;
        this.interfaces = interfaces;
        this.accessFlags = accessFlags;
    }

    /**
     * Generate a proxy class given a name and a list of proxy interfaces.
     *
     * @param name        the class name of the proxy class
     * @param interfaces  proxy interfaces
     * @param accessFlags access flags of the proxy class
     */
    @SuppressWarnings("removal")
    static byte[] generateProxyClass(ClassLoader loader,
                                     final String name,
                                     List<Class<?>> interfaces,
                                     int accessFlags) {
        ProxyGenerator gen = new ProxyGenerator(loader, name, interfaces, accessFlags);
        final byte[] classFile = gen.generateClassFile();

        if (saveGeneratedFiles) {
            java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<Void>() {
                        public Void run() {
                            try {
                                int i = name.lastIndexOf('.');
                                Path path;
                                if (i > 0) {
                                    Path dir = Path.of(dotToSlash(name.substring(0, i)));
                                    Files.createDirectories(dir);
                                    path = dir.resolve(name.substring(i + 1) + ".class");
                                } else {
                                    path = Path.of(name + ".class");
                                }
                                Files.write(path, classFile);
                                return null;
                            } catch (IOException e) {
                                throw new InternalError(
                                        "I/O exception saving generated file: " + e);
                            }
                        }
                    });
        }

        return classFile;
    }

    /**
     * Return an array of the class and interface names from an array of Classes.
     *
     * @param classes an array of classes or interfaces
     * @return the array of class and interface names; or null if classes is
     * null or empty
     */
    private static List<ClassDesc> typeNames(List<Class<?>> classes) {
        if (classes == null || classes.isEmpty())
            return List.of();
        return classes.stream().map(cls -> ClassDesc.ofDescriptor(cls.descriptorString())).toList();
    }

    /**
     * For a given set of proxy methods with the same signature, check
     * that their return types are compatible according to the Proxy
     * specification.
     *
     * Specifically, if there is more than one such method, then all
     * of the return types must be reference types, and there must be
     * one return type that is assignable to each of the rest of them.
     */
    private static void checkReturnTypes(List<ProxyMethod> methods) {
        /*
         * If there is only one method with a given signature, there
         * cannot be a conflict.  This is the only case in which a
         * primitive (or void) return type is allowed.
         */
        if (methods.size() < 2) {
            return;
        }

        /*
         * List of return types that are not yet known to be
         * assignable from ("covered" by) any of the others.
         */
        List<Class<?>> uncoveredReturnTypes = new ArrayList<>(1);

        nextNewReturnType:
        for (ProxyMethod pm : methods) {
            Class<?> newReturnType = pm.returnType;
            if (newReturnType.isPrimitive()) {
                throw new IllegalArgumentException(
                        "methods with same signature " +
                                pm.shortSignature +
                                " but incompatible return types: " +
                                newReturnType.getName() + " and others");
            }
            boolean added = false;

            /*
             * Compare the new return type to the existing uncovered
             * return types.
             */
            ListIterator<Class<?>> liter = uncoveredReturnTypes.listIterator();
            while (liter.hasNext()) {
                Class<?> uncoveredReturnType = liter.next();

                /*
                 * If an existing uncovered return type is assignable
                 * to this new one, then we can forget the new one.
                 */
                if (newReturnType.isAssignableFrom(uncoveredReturnType)) {
                    assert !added;
                    continue nextNewReturnType;
                }

                /*
                 * If the new return type is assignable to an existing
                 * uncovered one, then should replace the existing one
                 * with the new one (or just forget the existing one,
                 * if the new one has already be put in the list).
                 */
                if (uncoveredReturnType.isAssignableFrom(newReturnType)) {
                    // (we can assume that each return type is unique)
                    if (!added) {
                        liter.set(newReturnType);
                        added = true;
                    } else {
                        liter.remove();
                    }
                }
            }

            /*
             * If we got through the list of existing uncovered return
             * types without an assignability relationship, then add
             * the new return type to the list of uncovered ones.
             */
            if (!added) {
                uncoveredReturnTypes.add(newReturnType);
            }
        }

        /*
         * We shouldn't end up with more than one return type that is
         * not assignable from any of the others.
         */
        if (uncoveredReturnTypes.size() > 1) {
            ProxyMethod pm = methods.get(0);
            throw new IllegalArgumentException(
                    "methods with same signature " +
                            pm.shortSignature +
                            " but incompatible return types: " + uncoveredReturnTypes);
        }
    }

    /**
     * Given the exceptions declared in the throws clause of a proxy method,
     * compute the exceptions that need to be caught from the invocation
     * handler's invoke method and rethrown intact in the method's
     * implementation before catching other Throwables and wrapping them
     * in UndeclaredThrowableExceptions.
     *
     * The exceptions to be caught are returned in a List object.  Each
     * exception in the returned list is guaranteed to not be a subclass of
     * any of the other exceptions in the list, so the catch blocks for
     * these exceptions may be generated in any order relative to each other.
     *
     * Error and RuntimeException are each always contained by the returned
     * list (if none of their superclasses are contained), since those
     * unchecked exceptions should always be rethrown intact, and thus their
     * subclasses will never appear in the returned list.
     *
     * The returned List will be empty if java.lang.Throwable is in the
     * given list of declared exceptions, indicating that no exceptions
     * need to be caught.
     */
    private static List<Class<?>> computeUniqueCatchList(Class<?>[] exceptions) {
        List<Class<?>> uniqueList = new ArrayList<>();
        // unique exceptions to catch

        uniqueList.add(Error.class);            // always catch/rethrow these
        uniqueList.add(RuntimeException.class);

        nextException:
        for (Class<?> ex : exceptions) {
            if (ex.isAssignableFrom(Throwable.class)) {
                /*
                 * If Throwable is declared to be thrown by the proxy method,
                 * then no catch blocks are necessary, because the invoke
                 * can, at most, throw Throwable anyway.
                 */
                uniqueList.clear();
                break;
            } else if (!Throwable.class.isAssignableFrom(ex)) {
                /*
                 * Ignore types that cannot be thrown by the invoke method.
                 */
                continue;
            }
            /*
             * Compare this exception against the current list of
             * exceptions that need to be caught:
             */
            for (int j = 0; j < uniqueList.size(); ) {
                Class<?> ex2 = uniqueList.get(j);
                if (ex2.isAssignableFrom(ex)) {
                    /*
                     * if a superclass of this exception is already on
                     * the list to catch, then ignore this one and continue;
                     */
                    continue nextException;
                } else if (ex.isAssignableFrom(ex2)) {
                    /*
                     * if a subclass of this exception is on the list
                     * to catch, then remove it;
                     */
                    uniqueList.remove(j);
                } else {
                    j++;        // else continue comparing.
                }
            }
            // This exception is unique (so far): add it to the list to catch.
            uniqueList.add(ex);
        }
        return uniqueList;
    }

    /**
     * Convert a fully qualified class name that uses '.' as the package
     * separator, the external representation used by the Java language
     * and APIs, to a fully qualified class name that uses '/' as the
     * package separator, the representation used in the class file
     * format (see JVMS section {@jvms 4.2}).
     */
    private static String dotToSlash(String name) {
        return name.replace('.', '/');
    }

    /**
     * Return the number of abstract "words", or consecutive local variable
     * indexes, required to contain a value of the given type.  See JVMS
     * section {@jvms 3.6.1}.
     * <p>
     * Note that the original version of the JVMS contained a definition of
     * this abstract notion of a "word" in section 3.4, but that definition
     * was removed for the second edition.
     */
    private static int getWordsPerType(Class<?> type) {
        if (type == long.class || type == double.class) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * Add to the given list all of the types in the "from" array that
     * are not already contained in the list and are assignable to at
     * least one of the types in the "with" array.
     * <p>
     * This method is useful for computing the greatest common set of
     * declared exceptions from duplicate methods inherited from
     * different interfaces.
     */
    private static void collectCompatibleTypes(Class<?>[] from,
                                               Class<?>[] with,
                                               List<Class<?>> list) {
        for (Class<?> fc : from) {
            if (!list.contains(fc)) {
                for (Class<?> wc : with) {
                    if (wc.isAssignableFrom(fc)) {
                        list.add(fc);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Generate a class file for the proxy class.  This method drives the
     * class file generation process.
     */
    private byte[] generateClassFile() {
        var localCache = new HashMap<ClassDesc, ClassHierarchyResolver.ClassHierarchyInfo>();
        return Classfile.build(ClassDesc.of(className), List.of(Classfile.Option.classHierarchyResolver(classDesc ->
                localCache.computeIfAbsent(classDesc, cd -> {
                    try {
                        var desc = cd.descriptorString();
                        var cls = Class.forName(desc.substring(1, desc.length() - 1).replace('/', '.'), false, loader);
                        var superCls = cls.getSuperclass();
                        return new ClassHierarchyResolver.ClassHierarchyInfo(cd,
                                cls.isInterface(),
                                superCls == null ? null : ClassDesc.ofDescriptor(superCls.descriptorString()));
                    } catch (ClassNotFoundException e) {
                        throw new TypeNotPresentException(cd.descriptorString(), e);
                    }
                })
        )), clb -> {
            clb.withFlags(accessFlags);
            clb.withSuperclass(JLR_PROXY);
            clb.withInterfaceSymbols(typeNames(interfaces));
            clb.withVersion(CLASSFILE_VERSION, 0);

            /*
             * Add proxy methods for the hashCode, equals,
             * and toString methods of java.lang.Object.  This is done before
             * the methods from the proxy interfaces so that the methods from
             * java.lang.Object take precedence over duplicate methods in the
             * proxy interfaces.
             */
            addProxyMethod(hashCodeMethod);
            addProxyMethod(equalsMethod);
            addProxyMethod(toStringMethod);

            /*
             * Accumulate all of the methods from the proxy interfaces.
             */
            for (Class<?> intf : interfaces) {
                for (Method m : intf.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers())) {
                        addProxyMethod(m, intf);
                    }
                }
            }

            /*
             * For each set of proxy methods with the same signature,
             * verify that the methods' return types are compatible.
             */
            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                checkReturnTypes(sigmethods);
            }

            generateConstructor(clb);

            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                for (ProxyMethod pm : sigmethods) {
                    // add static field for the Method object
                    clb.withField(pm.methodFieldName, LJLR_METHOD, ACC_PRIVATE | ACC_STATIC | ACC_FINAL);

                    // Generate code for proxy method
                    pm.generateMethod(clb, className);
                }
            }

            generateStaticInitializer(clb);
            generateLookupAccessor(clb);
        });
    }

    /**
     * Add another method to be proxied, either by creating a new
     * ProxyMethod object or augmenting an old one for a duplicate
     * method.
     *
     * "fromClass" indicates the proxy interface that the method was
     * found through, which may be different from (a subinterface of)
     * the method's "declaring class".  Note that the first Method
     * object passed for a given name and descriptor identifies the
     * Method object (and thus the declaring class) that will be
     * passed to the invocation handler's "invoke" method for a given
     * set of duplicate methods.
     */
    private void addProxyMethod(Method m, Class<?> fromClass) {
        Class<?> returnType = m.getReturnType();
        Class<?>[] exceptionTypes = m.getSharedExceptionTypes();

        String sig = m.toShortSignature();
        List<ProxyMethod> sigmethods = proxyMethods.computeIfAbsent(sig,
                (f) -> new ArrayList<>(3));
        for (ProxyMethod pm : sigmethods) {
            if (returnType == pm.returnType) {
                /*
                 * Found a match: reduce exception types to the
                 * greatest set of exceptions that can be thrown
                 * compatibly with the throws clauses of both
                 * overridden methods.
                 */
                List<Class<?>> legalExceptions = new ArrayList<>();
                collectCompatibleTypes(
                        exceptionTypes, pm.exceptionTypes, legalExceptions);
                collectCompatibleTypes(
                        pm.exceptionTypes, exceptionTypes, legalExceptions);
                pm.exceptionTypes = legalExceptions.toArray(EMPTY_CLASS_ARRAY);
                return;
            }
        }
        sigmethods.add(new ProxyMethod(m, sig, m.getSharedParameterTypes(), returnType,
                exceptionTypes, fromClass,
                "m" + proxyMethodCount++));
    }

    /**
     * Add an existing ProxyMethod (hashcode, equals, toString).
     *
     * @param pm an existing ProxyMethod
     */
    private void addProxyMethod(ProxyMethod pm) {
        String sig = pm.shortSignature;
        List<ProxyMethod> sigmethods = proxyMethods.computeIfAbsent(sig,
                (f) -> new ArrayList<>(3));
        sigmethods.add(pm);
    }

    /**
     * Generate the constructor method for the proxy class.
     */
    private void generateConstructor(ClassBuilder clb) {
        clb.withMethodBody(NAME_CTOR, MJLR_INVOCATIONHANDLER, ACC_PUBLIC, cob ->
            cob.aload(0)
               .aload(1)
               .invokespecial(JLR_PROXY, NAME_CTOR, MJLR_INVOCATIONHANDLER)
               .return_());
    }

    /**
     * Generate the static initializer method for the proxy class.
     */
    private void generateStaticInitializer(ClassBuilder clb) {
        clb.withMethodBody(NAME_CLINIT, MethodTypeDesc.of(CD_void), ACC_STATIC, cob -> {
            // Put ClassLoader at local variable index 0, used by
            // Class.forName(String, boolean, ClassLoader) calls
            cob.constantInstruction(ClassDesc.of(className))
               .invokevirtual(CD_Class, "getClassLoader", MethodTypeDesc.of(LJL_CLASSLOADER))
               .astore(0);

            cob.trying(tryb -> {
                for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                    for (ProxyMethod pm : sigmethods) {
                        pm.codeFieldInitialization(tryb, className);
                    }
                }
                tryb.return_();
            }, cb -> cb
                .catching(JL_NO_SUCH_METHOD_EX, nsmb -> nsmb
                        .astore(1)
                        .new_(JL_NO_SUCH_METHOD_ERROR)
                        .dup()
                        .aload(1)
                        .invokevirtual(CD_Throwable, "getMessage", MethodTypeDesc.of(CD_String))
                        .invokespecial(JL_NO_SUCH_METHOD_ERROR, "<init>", MethodTypeDesc.of(CD_void, CD_String))
                        .athrow())
                .catching(JL_CLASS_NOT_FOUND_EX, cnfb -> cnfb
                        .astore(1)
                        .new_(JL_NO_CLASS_DEF_FOUND_ERROR)
                        .dup()
                        .aload(1)
                        .invokevirtual(CD_Throwable, "getMessage", MethodTypeDesc.of(CD_String))
                        .invokespecial(JL_NO_CLASS_DEF_FOUND_ERROR, "<init>", MethodTypeDesc.of(CD_void, CD_String))
                        .athrow()));
        });
    }

    /**
     * Generate the static lookup accessor method that returns the Lookup
     * on this proxy class if the caller's lookup class is java.lang.reflect.Proxy;
     * otherwise, IllegalAccessException is thrown
     */
    private void generateLookupAccessor(ClassBuilder clb) {
        clb.withMethod(NAME_LOOKUP_ACCESSOR, MethodTypeDesc.ofDescriptor("(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;"),
                ACC_PRIVATE | ACC_STATIC, mb -> {
                    mb.with(ExceptionsAttribute.of(List.of(mb.constantPool().classEntry(JL_ILLEGAL_ACCESS_EX))));
                    mb.withCode(cob -> {
                        var L_illegalAccess = cob.newLabel();
                        cob.aload(0)
                           .invokevirtual(JLI_LOOKUP, "lookupClass", MethodTypeDesc.of(CD_Class))
                           .constantInstruction(Opcode.LDC, Proxy.class.describeConstable().get())
                           .if_acmpne(L_illegalAccess)
                           .aload(0)
                           .invokevirtual(JLI_LOOKUP, "hasFullPrivilegeAccess", MethodTypeDesc.of(CD_boolean))
                           .ifeq(L_illegalAccess)
                           .invokestatic(CD_MethodHandles, "lookup", MethodTypeDesc.of(ClassDesc.ofDescriptor("Ljava/lang/invoke/MethodHandles$Lookup;")))
                           .areturn()
                           .labelBinding(L_illegalAccess)
                           .new_(JL_ILLEGAL_ACCESS_EX)
                           .dup()
                           .aload(0)
                           .invokevirtual(JLI_LOOKUP, "toString", MethodTypeDesc.of(CD_String))
                           .invokespecial(JL_ILLEGAL_ACCESS_EX, "<init>", MethodTypeDesc.of(CD_void, CD_String))
                           .athrow();
                    });
                });
    }

    /**
     * A ProxyMethod object represents a proxy method in the proxy class
     * being generated: a method whose implementation will encode and
     * dispatch invocations to the proxy instance's invocation handler.
     */
    private static class ProxyMethod {

        private final Method method;
        private final String shortSignature;
        private final Class<?> fromClass;
        private final Class<?>[] parameterTypes;
        private final Class<?> returnType;
        private final String methodFieldName;
        private Class<?>[] exceptionTypes;

        private ProxyMethod(Method method, String sig, Class<?>[] parameterTypes,
                            Class<?> returnType, Class<?>[] exceptionTypes,
                            Class<?> fromClass, String methodFieldName) {
            this.method = method;
            this.shortSignature = sig;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.fromClass = fromClass;
            this.methodFieldName = methodFieldName;
        }

        /**
         * Create a new specific ProxyMethod with a specific field name
         *
         * @param method          The method for which to create a proxy
         * @param methodFieldName the fieldName to generate
         */
        private ProxyMethod(Method method, String methodFieldName) {
            this(method, method.toShortSignature(),
                    method.getSharedParameterTypes(), method.getReturnType(),
                    method.getSharedExceptionTypes(), method.getDeclaringClass(), methodFieldName);
        }

        /**
         * Generate this method, including the code and exception table entry.
         */
        private void generateMethod(ClassBuilder clb, String className) {
            MethodType mt = MethodType.methodType(returnType, parameterTypes);
            MethodTypeDesc desc = MethodTypeDesc.ofDescriptor(mt.toMethodDescriptorString());
            int accessFlags = (method.isVarArgs()) ? ACC_VARARGS | ACC_PUBLIC | ACC_FINAL : ACC_PUBLIC | ACC_FINAL;
            clb.withMethod(method.getName(), desc, accessFlags, mb -> {
                ConstantPoolBuilder cpb = mb.constantPool();
                List<ClassEntry> exceptionClassEntries = typeNames(Arrays.asList(exceptionTypes))
                        .stream()
                        .map(cpb::classEntry)
                        .toList();
                mb.with(ExceptionsAttribute.of(exceptionClassEntries));
                mb.withCode(cob ->
                    cob.trying(tryb -> {
                        tryb.aload(0)
                            .getfield(JLR_PROXY, handlerFieldName, LJLR_INVOCATION_HANDLER)
                            .aload(0)
                            .getstatic(ClassDesc.of(className), methodFieldName, LJLR_METHOD);

                        if (parameterTypes.length > 0) {
                            // Create an array and fill with the parameters converting primitives to wrappers
                            tryb.constantInstruction(parameterTypes.length)
                                .anewarray(CD_Object);
                            for (int i = 0; i < parameterTypes.length; i++) {
                                tryb.dup()
                                    .constantInstruction(i);
                                codeWrapArgument(tryb, parameterTypes[i], tryb.parameterSlot(i));
                                tryb.aastore();
                            }
                        } else {
                            tryb.aconst_null();
                        }

                        tryb.invokeinterface(JLR_INVOCATION_HANDLER, "invoke",
                                MethodTypeDesc.of(CD_Object, CD_Object, LJLR_METHOD, CD_Object.arrayType()));

                        if (returnType == void.class) {
                            tryb.pop()
                                .return_();
                        } else {
                            codeUnwrapReturnValue(tryb, returnType);
                        }

                    }, catchBuilder -> {
                        List<Class<?>> catchList = computeUniqueCatchList(exceptionTypes);
                        if (!catchList.isEmpty()) {
                            catchBuilder.catchingMulti(catchList.stream().map(ex -> ClassDesc.ofDescriptor(ex.descriptorString())).toList(), ehb -> ehb
                                    .athrow());   // just rethrow the exception

                            catchBuilder.catching(CD_Throwable, ehb -> ehb
                                    .astore(1)
                                    .new_(JLR_UNDECLARED_THROWABLE_EX)
                                    .dup()
                                    .aload(1)
                                    .invokespecial(JLR_UNDECLARED_THROWABLE_EX, "<init>", MethodTypeDesc.of(CD_void, CD_Throwable))
                                    .athrow());
                        }
                    }));
            });
        }

        /**
         * Generate code for wrapping an argument of the given type
         * whose value can be found at the specified local variable
         * index, in order for it to be passed (as an Object) to the
         * invocation handler's "invoke" method.
         */
        private void codeWrapArgument(CodeBuilder cob, Class<?> type, int slot) {
            if (type.isPrimitive()) {
                cob.loadInstruction(TypeKind.fromDescriptor(type.descriptorString()).asLoadable(), slot);
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);
                cob.invokestatic(ClassDesc.ofInternalName(prim.wrapperClassName), "valueOf", MethodTypeDesc.ofDescriptor(prim.wrapperValueOfDesc));
            } else {
                cob.aload(slot);
            }
        }

        /**
         * Generate code for unwrapping a return value of the given
         * type from the invocation handler's "invoke" method (as type
         * Object) to its correct type.
         */
        private void codeUnwrapReturnValue(CodeBuilder cob, Class<?> type) {
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);

                cob.typeCheckInstruction(Opcode.CHECKCAST, ClassDesc.ofInternalName(prim.wrapperClassName))
                   .invokevirtual(ClassDesc.ofInternalName(prim.wrapperClassName),
                        prim.unwrapMethodName, MethodTypeDesc.ofDescriptor(prim.unwrapMethodDesc))
                   .returnInstruction(TypeKind.fromDescriptor(type.descriptorString()).asLoadable());
            } else {
                cob.checkcast(ClassDesc.ofDescriptor(type.descriptorString()))
                   .areturn();
            }
        }

        /**
         * Generate code for initializing the static field that stores
         * the Method object for this proxy method. A class loader is
         * anticipated at local variable index 0.
         */
        private void codeFieldInitialization(CodeBuilder cob, String className) {
            codeClassForName(cob, fromClass);

            cob.constantInstruction(Opcode.LDC, method.getName())
               .constantInstruction(parameterTypes.length)
               .anewarray(CD_Class);

            // Construct an array with the parameter types mapping primitives to Wrapper types
            for (int i = 0; i < parameterTypes.length; i++) {
                cob.dup()
                   .constantInstruction(i);
                if (parameterTypes[i].isPrimitive()) {
                    PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(parameterTypes[i]);
                    cob.getstatic(ClassDesc.ofInternalName(prim.wrapperClassName), "TYPE", CD_Class);
                } else {
                    codeClassForName(cob, parameterTypes[i]);
                }
                cob.arrayStoreInstruction(TypeKind.ReferenceType);
            }
            // lookup the method
            cob.invokevirtual(CD_Class, "getMethod", MethodTypeDesc.of(LJLR_METHOD, CD_String, CD_Class.arrayType()))
               .putstatic(ClassDesc.of(className), methodFieldName, LJLR_METHOD);
        }

        /*
         * =============== Code Generation Utility Methods ===============
         */

        /**
         * Generate code to invoke the Class.forName with the name of the given
         * class to get its Class object at runtime.  The code is written to
         * the supplied stream.  Note that the code generated by this method
         * may cause the checked ClassNotFoundException to be thrown. A class
         * loader is anticipated at local variable index 0.
         */
        private void codeClassForName(CodeBuilder cob, Class<?> cl) {
            cob.constantInstruction(Opcode.LDC, cl.getName())
               .iconst_0() // false
               .aload(0)// classLoader
               .invokestatic(CD_Class, "forName", MethodTypeDesc.of(CD_Class, CD_String, CD_boolean, LJL_CLASSLOADER));
        }

        @Override
        public String toString() {
            return method.toShortString();
        }
    }

    /**
     * A PrimitiveTypeInfo object contains bytecode-related information about
     * a primitive type in its instance fields. The struct for a particular
     * primitive type can be obtained using the static "get" method.
     */
    private enum PrimitiveTypeInfo {
        BYTE(byte.class, ILOAD, IRETURN),
        CHAR(char.class, ILOAD, IRETURN),
        DOUBLE(double.class, DLOAD, DRETURN),
        FLOAT(float.class, FLOAD, FRETURN),
        INT(int.class, ILOAD, IRETURN),
        LONG(long.class, LLOAD, LRETURN),
        SHORT(short.class, ILOAD, IRETURN),
        BOOLEAN(boolean.class, ILOAD, IRETURN);

        /**
         * internal name of corresponding wrapper class
         */
        private final String wrapperClassName;
        /**
         * method descriptor for wrapper class "valueOf" factory method
         */
        private final String wrapperValueOfDesc;
        /**
         * name of wrapper class method for retrieving primitive value
         */
        private final String unwrapMethodName;
        /**
         * descriptor of same method
         */
        private final String unwrapMethodDesc;
        /**
         * Load opcode used by this primitive
         */
        private final int loadOpcode;
        /**
         * Return opcode used by this primitive
         */
        private final int returnOpcode;

        PrimitiveTypeInfo(Class<?> primitiveClass, int loadOpcode, int returnOpcode) {
            assert primitiveClass.isPrimitive();
            assert returnOpcode - IRETURN == loadOpcode - ILOAD;

            Wrapper wrapper = Wrapper.forPrimitiveType(primitiveClass);
            // single-char BaseType descriptor (see JVMS section 4.3.2)
            String baseTypeString = wrapper.basicTypeString();
            var wrapperType = wrapper.wrapperType();
            wrapperClassName = dotToSlash(wrapperType.getName());
            wrapperValueOfDesc =
                    "(" + baseTypeString + ")" + wrapperType.descriptorString();
            unwrapMethodName = primitiveClass.getName() + "Value";
            unwrapMethodDesc = "()" + baseTypeString;
            this.loadOpcode = loadOpcode;
            this.returnOpcode = returnOpcode;
        }

        public static PrimitiveTypeInfo get(Class<?> cl) {
            // Uses if chain for speed: 8284880
            if (cl == int.class)     return INT;
            if (cl == long.class)    return LONG;
            if (cl == boolean.class) return BOOLEAN;
            if (cl == short.class)   return SHORT;
            if (cl == byte.class)    return BYTE;
            if (cl == char.class)    return CHAR;
            if (cl == float.class)   return FLOAT;
            if (cl == double.class)  return DOUBLE;
            throw new AssertionError(cl);
        }
    }
}
