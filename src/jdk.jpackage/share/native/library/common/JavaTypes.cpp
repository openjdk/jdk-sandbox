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

#include "JavaTypes.h"
#include "PlatformString.h"

#include <list>


#ifdef DEBUG
TString JavaException::CreateExceptionMessage(JNIEnv* Env,
        jthrowable Exception, jmethodID GetCauseMethod,
        jmethodID GetStackTraceMethod, jmethodID ThrowableToTStringMethod,
        jmethodID FrameToTStringMethod) {

    TString result;
    jobjectArray frames =
            (jobjectArray)Env->CallObjectMethod(Exception, GetStackTraceMethod);

    // Append Throwable.toTString().
    if (0 != frames) {
        jstring jstr = (jstring)Env->CallObjectMethod(Exception,
                ThrowableToTStringMethod);
        const char* str = Env->GetStringUTFChars(jstr, 0);
        result += PlatformString(str).toPlatformString();
        Env->ReleaseStringUTFChars(jstr, str);
        Env->DeleteLocalRef(jstr);
    }

    // Append stack trace if one exists.
    if (Env->GetArrayLength(frames) > 0) {
        jsize i = 0;

        for (i = 0; i < Env->GetArrayLength(frames); i++) {
            // Get the string from the next frame and append it to
            // the error message.
            jobject frame = Env->GetObjectArrayElement(frames, i);
            jstring obj = (jstring)Env->CallObjectMethod(frame,
                    FrameToTStringMethod);
            const char* str = Env->GetStringUTFChars(obj, 0);
            result += _T("\n  ");
            result += PlatformString(str).toPlatformString();
            Env->ReleaseStringUTFChars(obj, str);
            Env->DeleteLocalRef(obj);
            Env->DeleteLocalRef(frame);
        }
    }

    // If Exception has a cause then append the stack trace messages.
    if (0 != frames) {
        jthrowable cause =
                (jthrowable)Env->CallObjectMethod(Exception, GetCauseMethod);

        if (cause != NULL) {
            result += CreateExceptionMessage(Env, cause, GetCauseMethod,
                GetStackTraceMethod, ThrowableToTStringMethod,
                FrameToTStringMethod);
        }
    }

    return result;
}
#endif //DEBUG

JavaException::JavaException() : Exception() {}

//#ifdef WINDOWS
JavaException::JavaException(JNIEnv *Env,
        const TString Message) : Exception(Message) {
//#endif //WINDOWS
//#ifdef POSIX
//JavaException::JavaException(JNIEnv *Env, TString message) {
//#endif //POSIX

    FEnv = Env;
    FException = Env->ExceptionOccurred();
    Env->ExceptionClear();

#ifdef DEBUG
    Platform& platform = Platform::GetInstance();

    if (platform.GetDebugState() == dsNone) {
        jclass ThrowableClass = Env->FindClass("java/lang/Throwable");

        if (FEnv->ExceptionCheck() == JNI_TRUE) {
            Env->ExceptionClear();
            return;
        }

        jmethodID GetCauseMethod = Env->GetMethodID(ThrowableClass,
                "getCause", "()Ljava/lang/Throwable;");

        if (FEnv->ExceptionCheck() == JNI_TRUE) {
            Env->ExceptionClear();
            return;
        }

        jmethodID GetStackTraceMethod = Env->GetMethodID(ThrowableClass,
                 "getStackTrace", "()[Ljava/lang/StackTraceElement;");

        if (FEnv->ExceptionCheck() == JNI_TRUE) {
            Env->ExceptionClear();
            return;
        }

        jmethodID ThrowableToTStringMethod = Env->GetMethodID(ThrowableClass,
                "toString", "()Ljava/lang/String;");

        if (FEnv->ExceptionCheck() == JNI_TRUE) {
            Env->ExceptionClear();
            return;
        }

        jclass FrameClass = Env->FindClass("java/lang/StackTraceElement");

        if (FEnv->ExceptionCheck() == JNI_TRUE) {
            Env->ExceptionClear();
            return;
        }

        jmethodID FrameToTStringMethod = Env->GetMethodID(FrameClass,
                "toString", "()Ljava/lang/String;");

        if (FEnv->ExceptionCheck() == JNI_TRUE) {
            Env->ExceptionClear();
            return;
        }

        TString lmessage = CreateExceptionMessage(Env, FException,
                GetCauseMethod, GetStackTraceMethod, ThrowableToTStringMethod,
                FrameToTStringMethod);
        SetMessage(lmessage);
    }
#endif //DEBUG
}

void JavaException::Rethrow() {
    FEnv->Throw(FException);
}
