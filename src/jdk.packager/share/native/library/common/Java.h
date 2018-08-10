/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef JAVA_H
#define JAVA_H

#include "Platform.h"
#include "Messages.h"

#include "jni.h"


class JavaClass;
class JavaStaticMethod;
class JavaMethod;
class JavaStringArray;


class JavaException : public Exception {
// Prohibit Heap-Based Classes.
private:
    static void *operator new(size_t size);

private:
#ifdef DEBUG
    static TString CreateExceptionMessage(JNIEnv* Env, jthrowable Exception,
        jmethodID GetCauseMethod, jmethodID GetStackTraceMethod, jmethodID ThrowableToStringMethod,
        jmethodID FrameToStringMethod);
#endif //DEBUG

    jthrowable FException;
    JNIEnv *FEnv;

public:
    explicit JavaException();
    explicit JavaException(JNIEnv *Env, const TString message);
    virtual ~JavaException() throw() {}

    void Rethrow();
};


class JavaStaticMethod {
// Prohibit Heap-Based Classes.
private:
    static void *operator new(size_t size);
    static void operator delete(void *ptr);

private:
    JNIEnv *FEnv;
    jmethodID FMethod;
    jclass FClass;
public:
    JavaStaticMethod(JNIEnv *Env, jclass Class, jmethodID Method);

    void CallVoidMethod(int Count, ...);
    operator jmethodID ();
};


class JavaMethod {
// Prohibit Heap-Based Classes.
private:
    static void *operator new(size_t size);
    static void operator delete(void *ptr);

    JavaMethod(JavaMethod const&); // Don't Implement.
    void operator=(JavaMethod const&); // Don't implement

private:
    JNIEnv *FEnv;
    jmethodID FMethod;
    jobject FObj;
public:
    JavaMethod(JNIEnv *Env, jobject Obj, jmethodID Method);

    void CallVoidMethod(int Count, ...);
    operator jmethodID ();
};


class JavaClass {
// Prohibit Heap-Based Classes.
private:
    static void *operator new(size_t size);
    static void operator delete(void *ptr);

    JavaClass(JavaClass const&); // Don't Implement.
    void operator=(JavaClass const&); // Don't implement

private:
    JNIEnv *FEnv;
    jclass FClass;
    TString FClassName;

public:
    JavaClass(JNIEnv *Env, TString Name);
    ~JavaClass();

    JavaStaticMethod GetStaticMethod(TString Name, TString Signature);
    operator jclass ();
};


class JavaStringArray {
// Prohibit Heap-Based Classes.
private:
    static void *operator new(size_t size);
    static void operator delete(void *ptr);

    JavaStringArray(JavaStringArray const&); // Don't Implement.
    void operator=(JavaStringArray const&); // Don't implement

private:
    JNIEnv *FEnv;
    jobjectArray FData;

    void Initialize(size_t Size);

public:
    JavaStringArray(JNIEnv *Env, size_t Size);
    JavaStringArray(JNIEnv *Env, jobjectArray Data);
    JavaStringArray(JNIEnv *Env, std::list<TString> Array);

    jobjectArray GetData();
    void SetValue(jsize Index, jstring Item);
    jstring GetValue(jsize Index);
    unsigned int Count();
};

#endif //JAVA_H
