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

#include "Platform.h"
#include "Package.h"
#include "PlatformString.h"
#include "PropertyFile.h"
#include "Lock.h"
#include "Java.h"

#include "jni.h"


class UserJVMArgsExports {
private:
    // This is not a class to create an instance of.
    UserJVMArgsExports();

    static jobjectArray MapKeysToJObjectArray(JNIEnv *env, OrderedMap<TString, TString> map) {
        std::vector<TString> keys = map.GetKeys();
        JavaStringArray result(env, keys.size());

        for (unsigned int index = 0; index < keys.size(); index++) {
            PlatformString value(keys[index]);
            try {
                result.SetValue(index, value.toJString(env));
            }
            catch (const JavaException&) {}
        }
        return result.GetData();
    }

public:
    static jstring _getUserJvmOptionDefaultValue(JNIEnv *env, jstring option) {
        if (env == NULL || option == NULL)
            return NULL;

        jstring result = NULL;

        Package& package = Package::GetInstance();
        OrderedMap<TString, TString> defaultuserargs = package.GetDefaultJVMUserArgs();
        TString loption = PlatformString(env, option).toString();

        TString temp;
        defaultuserargs.GetValue(loption, temp);
        PlatformString value = temp;
        try {
            result = value.toJString(env);
        }
        catch (const JavaException&) {
        }

        return result;
    }

    static jobjectArray _getUserJvmOptionDefaultKeys(JNIEnv *env) {
        if (env == NULL)
            return NULL;

        jobjectArray result = NULL;

        Package& package = Package::GetInstance();

        try {
            result = MapKeysToJObjectArray(env, package.GetDefaultJVMUserArgs());
        }
        catch (const JavaException&) {
        }

        return result;
    }

    static jstring _getUserJvmOptionValue(JNIEnv *env, jstring option) {
        if (env == NULL || option == NULL)
            return NULL;

        jstring result = NULL;

        Package& package = Package::GetInstance();
        OrderedMap<TString, TString> userargs = package.GetJVMUserArgs();

        try {
            TString loption = PlatformString(env, option).toString();
            TString temp;
            userargs.GetValue(loption, temp);
            PlatformString value = temp;
            result = value.toJString(env);
        }
        catch (const JavaException&) {
        }

        return result;
    }

    static void _setUserJvmKeysAndValues(JNIEnv *env, jobjectArray options, jobjectArray values) {
        if (env == NULL || options == NULL || values == NULL)
            return;

        Package& package = Package::GetInstance();
        OrderedMap<TString, TString> newMap;

        try {
            JavaStringArray loptions(env, options);
            JavaStringArray lvalues(env, values);

            for (unsigned int index = 0; index < loptions.Count(); index++) {
                TString name = PlatformString(env, loptions.GetValue(index)).toString();
                TString value = PlatformString(env, lvalues.GetValue(index)).toString();
                newMap.Append(name, value);
            }
        }
        catch (const JavaException&) {
            return;
        }

        package.SetJVMUserArgOverrides(newMap);
    }

    static jobjectArray _getUserJvmOptionKeys(JNIEnv *env) {
        if (env == NULL)
            return NULL;

        jobjectArray result = NULL;

        Package& package = Package::GetInstance();

        try {
            result = MapKeysToJObjectArray(env, package.GetJVMUserArgs());
        }
        catch (const JavaException&) {
        }

        return result;
    }
};


extern "C" {
    JNIEXPORT jstring JNICALL Java_jdk_packager_services_userjvmoptions_LauncherUserJvmOptions__1getUserJvmOptionDefaultValue(JNIEnv *env, jclass klass, jstring option) {
        return UserJVMArgsExports::_getUserJvmOptionDefaultValue(env, option);
    }

    JNIEXPORT jobjectArray JNICALL Java_jdk_packager_services_userjvmoptions_LauncherUserJvmOptions__1getUserJvmOptionDefaultKeys(JNIEnv *env, jclass klass) {
        return UserJVMArgsExports::_getUserJvmOptionDefaultKeys(env);
    }

    JNIEXPORT jstring JNICALL Java_jdk_packager_services_userjvmoptions_LauncherUserJvmOptions__1getUserJvmOptionValue(JNIEnv *env, jclass klass, jstring option) {
        return UserJVMArgsExports::_getUserJvmOptionValue(env, option);
    }

    JNIEXPORT void JNICALL Java_jdk_packager_services_userjvmoptions_LauncherUserJvmOptions__1setUserJvmKeysAndValues(JNIEnv *env, jclass klass, jobjectArray options, jobjectArray values) {
        UserJVMArgsExports::_setUserJvmKeysAndValues(env, options, values);
    }

    JNIEXPORT jobjectArray JNICALL Java_jdk_packager_services_userjvmoptions_LauncherUserJvmOptions__1getUserJvmOptionKeys(JNIEnv *env, jclass klass) {
        return UserJVMArgsExports::_getUserJvmOptionKeys(env);
    }
}

#ifdef DEBUG
// Build with debug info. Create a class:
//
// package com;
//
// class DebugExports {
//   static {
//      System.loadLibrary("packager");
//   }
//
//   public static native boolean isdebugged();
//
//   public static native int getpid();
// }
//
// Use the following in Java in the main or somewhere else:
//
// import com.DebugExports;
// import java.util.Arrays;
//
// if (Arrays.asList(args).contains("-debug")) {
//   System.out.println("pid=" + getpid());
//
//   while (true) {
//     if (isdebugged() == true) {
//       break;
//     }
//   }
// }
//
// The call to isdebugger() will wait until a native debugger is attached. The process
// identifier (pid) will be printed to the console for you to attach your debugger to.
extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_DebugExports_isdebugged(JNIEnv *env, jclass klass) {
        jboolean result = false;
        Package& package = Package::GetInstance();

        if (package.Debugging() == dsNative) {
            Platform& platform = Platform::GetInstance();
            result = platform.GetDebugState() != dsNone;
        }

        return result;
    }

    JNIEXPORT jint JNICALL Java_com_DebugExports_getpid(JNIEnv *env, jclass klass) {
        Platform& platform = Platform::GetInstance();
        return platform.GetProcessID();
    }
}
#endif //DEBUG
