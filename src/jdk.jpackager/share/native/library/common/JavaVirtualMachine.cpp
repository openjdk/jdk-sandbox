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

#include "JavaVirtualMachine.h"
#include "Platform.h"
#include "PlatformString.h"
#include "FilePath.h"
#include "Package.h"
#include "JavaTypes.h"
#include "Helpers.h"
#include "Messages.h"
#include "Macros.h"
#include "PlatformThread.h"

#include "jni.h"

#include <map>
#include <list>
#include <sstream>


bool RunVM(JvmLaunchType type) {
    bool result = false;
    JavaVirtualMachine javavm;

    switch (type){
        case USER_APP_LAUNCH:
            result = javavm.StartJVM();
            break;
        case SINGLE_INSTANCE_NOTIFICATION_LAUNCH:
            result = javavm.NotifySingleInstance();
            break;
        default:
            break;
    }

    if (!result) {
        Platform& platform = Platform::GetInstance();
        platform.ShowMessage(_T("Failed to launch JVM\n"));
    }

    return result;
}

JavaLibrary::JavaLibrary() : Library(), FCreateProc(NULL)  {
}

bool JavaLibrary::JavaVMCreate(size_t argc, char *argv[]) {
    if (FCreateProc == NULL) {
        FCreateProc = (JVM_CREATE)GetProcAddress(LAUNCH_FUNC);
    }

    if (FCreateProc == NULL) {
        Platform& platform = Platform::GetInstance();
        Messages& messages = Messages::GetInstance();
        platform.ShowMessage(
                messages.GetMessage(FAILED_LOCATING_JVM_ENTRY_POINT));
        return false;
    }

    return FCreateProc((int)argc, argv,
            0, NULL,
            0, NULL,
            "",
            "",
            "java",
            "java",
            false,
            false,
            false,
            0) == 0;
}

//----------------------------------------------------------------------------

JavaOptions::JavaOptions(): FOptions(NULL) {
}

JavaOptions::~JavaOptions() {
    if (FOptions != NULL) {
        for (unsigned int index = 0; index < GetCount(); index++) {
            delete[] FOptions[index].optionString;
        }

        delete[] FOptions;
    }
}

void JavaOptions::AppendValue(const TString Key, TString Value, void* Extra) {
    JavaOptionItem item;
    item.name = Key;
    item.value = Value;
    item.extraInfo = Extra;
    FItems.push_back(item);
}

void JavaOptions::AppendValue(const TString Key, TString Value) {
    AppendValue(Key, Value, NULL);
}

void JavaOptions::AppendValue(const TString Key) {
    AppendValue(Key, _T(""), NULL);
}

void JavaOptions::AppendValues(OrderedMap<TString, TString> Values) {
    std::vector<TString> orderedKeys = Values.GetKeys();

    for (std::vector<TString>::const_iterator iterator = orderedKeys.begin();
        iterator != orderedKeys.end(); iterator++) {
        TString name = *iterator;
        TString value;

        if (Values.GetValue(name, value) == true) {
            AppendValue(name, value);
        }
    }
}

void JavaOptions::ReplaceValue(const TString Key, TString Value) {
    for (std::list<JavaOptionItem>::iterator iterator = FItems.begin();
        iterator != FItems.end(); iterator++) {

        TString lkey = iterator->name;

        if (lkey == Key) {
            JavaOptionItem item = *iterator;
            item.value = Value;
            iterator = FItems.erase(iterator);
            FItems.insert(iterator, item);
            break;
        }
    }
}

std::list<TString> JavaOptions::ToList() {
    std::list<TString> result;
    Macros& macros = Macros::GetInstance();

    for (std::list<JavaOptionItem>::const_iterator iterator = FItems.begin();
        iterator != FItems.end(); iterator++) {
        TString key = iterator->name;
        TString value = iterator->value;
        TString option = Helpers::NameValueToString(key, value);
        option = macros.ExpandMacros(option);
        result.push_back(option);
    }

    return result;
}

size_t JavaOptions::GetCount() {
    return FItems.size();
}

//----------------------------------------------------------------------------

JavaVirtualMachine::JavaVirtualMachine() {
}

JavaVirtualMachine::~JavaVirtualMachine(void) {
}

bool JavaVirtualMachine::StartJVM() {
    Platform& platform = Platform::GetInstance();
    Package& package = Package::GetInstance();

    TString classpath = package.GetClassPath();
    TString modulepath = package.GetModulePath();
    JavaOptions options;

    if (modulepath.empty() == false) {
        options.AppendValue(_T("-Djava.module.path"), modulepath);
    }

    options.AppendValue(_T("-Djava.library.path"),
            package.GetPackageAppDirectory() + FilePath::PathSeparator()
            + package.GetPackageLauncherDirectory());
    options.AppendValue(
            _T("-Djava.launcher.path"), package.GetPackageLauncherDirectory());
    options.AppendValue(_T("-Dapp.preferences.id"), package.GetAppID());
    options.AppendValues(package.GetJVMArgs());

#ifdef DEBUG
    if (package.Debugging() == dsJava) {
        options.AppendValue(_T("-Xdebug"), _T(""));
        options.AppendValue(
                _T("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=localhost:5005"),
                _T(""));
        platform.ShowMessage(_T("localhost:5005"));
    }
#endif // DEBUG

    TString maxHeapSizeOption;
    TString minHeapSizeOption;


    if (package.GetMemoryState() == PackageBootFields::msAuto) {
        TPlatformNumber memorySize = package.GetMemorySize();
        TString memory =
                PlatformString((size_t)memorySize).toString() + _T("m");
        maxHeapSizeOption = TString(_T("-Xmx")) + memory;
        options.AppendValue(maxHeapSizeOption, _T(""));

        if (memorySize > 256)
            minHeapSizeOption = _T("-Xms256m");
        else
            minHeapSizeOption = _T("-Xms") + memory;

        options.AppendValue(minHeapSizeOption, _T(""));
    }

    TString mainClassName = package.GetMainClassName();
    TString mainModule = package.GetMainModule();

    if (mainClassName.empty() == true && mainModule.empty() == true) {
        Messages& messages = Messages::GetInstance();
        platform.ShowMessage(messages.GetMessage(NO_MAIN_CLASS_SPECIFIED));
        return false;
    }

    configureLibrary();

    // Initialize the arguments to JLI_Launch()
    //
    // On Mac OS X JLI_Launch spawns a new thread that actually starts the JVM.
    // This new thread simply re-runs main(argc, argv). Therefore we do not
    // want to add new args if we are still in the original main thread so we
    // will treat them as command line args provided by the user ...
    // Only propagate original set of args first time.

    options.AppendValue(_T("-classpath"));
    options.AppendValue(classpath);

    std::list<TString> vmargs;
    vmargs.push_back(package.GetCommandName());

    if (package.HasSplashScreen() == true) {
        options.AppendValue(TString(_T("-splash:"))
                + package.GetSplashScreenFileName(), _T(""));
    }

    if (mainModule.empty() == true) {
        options.AppendValue(Helpers::ConvertJavaPathToId(mainClassName),
                _T(""));
    } else {
        options.AppendValue(_T("-m"));
        options.AppendValue(mainModule);
    }

    return launchVM(options, vmargs, false);
}

bool JavaVirtualMachine::NotifySingleInstance() {
    Package& package = Package::GetInstance();

    std::list<TString> vmargs;
    vmargs.push_back(package.GetCommandName());

    JavaOptions options;
    options.AppendValue(_T("-Djava.library.path"),
            package.GetPackageAppDirectory() + FilePath::PathSeparator()
            + package.GetPackageLauncherDirectory());
    options.AppendValue(_T("-Djava.launcher.path"),
            package.GetPackageLauncherDirectory());
    // launch SingleInstanceNewActivation.main() to pass arguments to
    // another instance
    options.AppendValue(_T("-m"));
    options.AppendValue(
            _T("jdk.jpackager.runtime/jdk.jpackager.runtime.singleton.SingleInstanceNewActivation"));

    configureLibrary();

    return launchVM(options, vmargs, true);
}

void JavaVirtualMachine::configureLibrary() {
    Platform& platform = Platform::GetInstance();
    Package& package = Package::GetInstance();
    // TODO: Clean this up. Because of bug JDK-8131321 the opening of the
    // PE file ails in WindowsPlatform.cpp on the check to
    // if (pNTHeader->Signature == IMAGE_NT_SIGNATURE)
    TString libName = package.GetJVMLibraryFileName();
#ifdef _WIN64
    if (FilePath::FileExists(_T("msvcr100.dll")) == true) {
        javaLibrary.AddDependency(_T("msvcr100.dll"));
    }

    TString runtimeBin = platform.GetPackageRuntimeBinDirectory();
    SetDllDirectory(runtimeBin.c_str());
#else
    javaLibrary.AddDependencies(
            platform.FilterOutRuntimeDependenciesForPlatform(
            platform.GetLibraryImports(libName)));
#endif
    javaLibrary.Load(libName);
}

bool JavaVirtualMachine::launchVM(JavaOptions& options,
        std::list<TString>& vmargs, bool addSiProcessId) {
    Platform& platform = Platform::GetInstance();
    Package& package = Package::GetInstance();

#ifdef MAC
    // Mac adds a ProcessSerialNumber to args when launched from .app
    // filter out the psn since they it's not expected in the app
    if (platform.IsMainThread() == false) {
        std::list<TString> loptions = options.ToList();
        vmargs.splice(vmargs.end(), loptions,
                loptions.begin(), loptions.end());
    }
#else
    std::list<TString> loptions = options.ToList();
    vmargs.splice(vmargs.end(), loptions, loptions.begin(), loptions.end());
#endif

    if (addSiProcessId) {
        // add single instance process ID as a first argument
        TProcessID pid = platform.GetSingleInstanceProcessId();
        std::ostringstream s;
        s << pid;
        std::string procIdStr(s.str());
        vmargs.push_back(TString(procIdStr.begin(), procIdStr.end()));
    }

    std::list<TString> largs = package.GetArgs();
    vmargs.splice(vmargs.end(), largs, largs.begin(), largs.end());

    size_t argc = vmargs.size();
    DynamicBuffer<char*> argv(argc + 1);
    if (argv.GetData() == NULL) {
        return false;
    }

    unsigned int index = 0;
    for (std::list<TString>::const_iterator iterator = vmargs.begin();
        iterator != vmargs.end(); iterator++) {
        TString item = *iterator;
        std::string arg = PlatformString(item).toStdString();
#ifdef DEBUG
        printf("%i %s\n", index, arg.c_str());
#endif // DEBUG
        argv[index] = PlatformString::duplicate(arg.c_str());
        index++;
    }

    argv[argc] = NULL;

// On Mac we can only free the boot fields if the calling thread is
// not the main thread.
#ifdef MAC
    if (platform.IsMainThread() == false) {
        package.FreeBootFields();
    }
#else
    package.FreeBootFields();
#endif // MAC

    if (javaLibrary.JavaVMCreate(argc, argv.GetData()) == true) {
        return true;
    }

    for (index = 0; index < argc; index++) {
        if (argv[index] != NULL) {
            delete[] argv[index];
        }
    }

    return false;
}
