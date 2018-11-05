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

#ifndef PLATFORM_H
#define PLATFORM_H

#include "OrderedMap.h"

#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <string>
#include <map>
#include <list>
#include <vector>


#ifdef WIN32
#ifndef WINDOWS
#define WINDOWS
#endif
#endif //WIN32

#ifdef __APPLE__
#define MAC
#define POSIX
#endif //__APPLE__


#ifdef __linux
#ifndef LINUX
#define LINUX
#endif
#endif //__linux

#ifdef LINUX
#define POSIX
#endif //LINUX



#ifdef WINDOWS
// Define Windows compatibility requirements XP or later
#define WINVER 0x0600
#define _WIN32_WINNT 0x0600

#include <Windows.h>
#include <tchar.h>
#include <shlobj.h>
#include <direct.h>
#include <process.h>
#include <malloc.h>

typedef std::wstring TString;
#define StringLength wcslen

#define TRAILING_PATHSEPARATOR '\\'
#define BAD_TRAILING_PATHSEPARATOR '/'
#define PATH_SEPARATOR ';'
#define BAD_PATH_SEPARATOR ':'

typedef ULONGLONG TPlatformNumber;
typedef DWORD TProcessID;

#endif //WINDOWS


#ifdef POSIX
#include <errno.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <libgen.h>

#define _T(x) x

typedef char TCHAR;
typedef std::string TString;
#define StringLength strlen

typedef unsigned long DWORD;

#define TRAILING_PATHSEPARATOR '/'
#define BAD_TRAILING_PATHSEPARATOR '\\'
#define PATH_SEPARATOR ':'
#define BAD_PATH_SEPARATOR ';'
#define MAX_PATH 1000

typedef long TPlatformNumber;
typedef pid_t TProcessID;

#define HMODULE void*
#endif //POSIX


// Config file sections
#define CONFIG_SECTION_APPLICATION       _T("CONFIG_SECTION_APPLICATION")
#define CONFIG_SECTION_JVMOPTIONS        _T("CONFIG_SECTION_JVMOPTIONS")
#define CONFIG_SECTION_APPCDSJVMOPTIONS  _T("CONFIG_SECTION_APPCDSJVMOPTIONS")
#define CONFIG_SECTION_ARGOPTIONS        _T("CONFIG_SECTION_ARGOPTIONS")
#define CONFIG_SECTION_APPCDSGENERATECACHEJVMOPTIONS \
        _T("CONFIG_SECTION_APPCDSGENERATECACHEJVMOPTIONS")

// Config file keys.
#define CONFIG_VERSION            _T("CONFIG_VERSION")
#define CONFIG_MAINJAR_KEY        _T("CONFIG_MAINJAR_KEY")
#define CONFIG_MAINMODULE_KEY     _T("CONFIG_MAINMODULE_KEY")
#define CONFIG_MAINCLASSNAME_KEY  _T("CONFIG_MAINCLASSNAME_KEY")
#define CONFIG_CLASSPATH_KEY      _T("CONFIG_CLASSPATH_KEY")
#define CONFIG_MODULEPATH_KEY     _T("CONFIG_MODULEPATH_KEY")
#define APP_NAME_KEY              _T("APP_NAME_KEY")
#define CONFIG_SPLASH_KEY         _T("CONFIG_SPLASH_KEY")
#define CONFIG_APP_ID_KEY         _T("CONFIG_APP_ID_KEY")
#define CONFIG_APP_MEMORY         _T("CONFIG_APP_MEMORY")
#define CONFIG_APP_DEBUG          _T("CONFIG_APP_DEBUG")
#define CONFIG_APPLICATION_INSTANCE _T("CONFIG_APPLICATION_INSTANCE")

#define JVM_RUNTIME_KEY           _T("JVM_RUNTIME_KEY")
#define JPACKAGER_APP_DATA_DIR     _T("CONFIG_APP_IDENTIFIER")



typedef void* Module;
typedef void* Procedure;


template <typename ObjectType, typename ValueType,
        ValueType (ObjectType::*getter)(void),
        void (ObjectType::*setter)(ValueType)>
class Property {
private:
    ObjectType* FObject;

public:
    Property() {
        FObject = NULL;
    }

    void SetInstance(ObjectType* Value) {
        FObject = Value;
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        assert(FObject != NULL);
        (FObject->*setter)(Value);
        return Value;
    }

    // The Property class is treated as the internal type.
    operator ValueType() {
        assert(FObject != NULL);
        return (FObject->*getter)();
    }
};

template <typename ObjectType, typename ValueType,
        ValueType (ObjectType::*getter)(void)>
class ReadProperty {
private:
    ObjectType* FObject;

public:
    ReadProperty() {
        FObject = NULL;
    }

    void SetInstance(ObjectType* Value) {
        FObject = Value;
    }

    // The Property class is treated as the internal type.
    operator ValueType() {
        assert(FObject != NULL);
        return (FObject->*getter)();
    }
};

template <typename ObjectType, typename ValueType,
        void (ObjectType::*setter)(ValueType)>
class WriteProperty {
private:
    ObjectType* FObject;

public:
    WriteProperty() {
        FObject = NULL;
    }

    void SetInstance(ObjectType* Value) {
        FObject = Value;
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        assert(FObject != NULL);
        (FObject->*setter)(Value);
        return Value;
    }
};

template <typename ValueType,
        ValueType (*getter)(void), void (*setter)(ValueType)>
class StaticProperty {
public:
    StaticProperty() {
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        (*getter)(Value);
        return Value;
    }

    // The Property class is treated as the internal type which is the getter.
    operator ValueType() {
        return (*setter)();
    }
};

template <typename ValueType, ValueType (*getter)(void)>
class StaticReadProperty {
public:
    StaticReadProperty() {
    }

    // The Property class is treated as the internal type which is the getter.
    operator ValueType() {
        return (*getter)();
    }
};

template <typename ValueType, void (*setter)(ValueType)>
class StaticWriteProperty {
public:
    StaticWriteProperty() {
    }

    // To set the value using the set method.
    ValueType operator =(const ValueType& Value) {
        (*setter)(Value);
        return Value;
    }
};


class Process {
protected:
    std::list<TString> FOutput;

public:
    Process() {
        Output.SetInstance(this);
        Input.SetInstance(this);
    }

    virtual ~Process() {}

    virtual bool IsRunning() = 0;
    virtual bool Terminate() = 0;
    virtual bool Execute(const TString Application,
        const std::vector<TString> Arguments, bool AWait = false) = 0;
    virtual bool Wait() = 0;
    virtual TProcessID GetProcessID() = 0;

    virtual std::list<TString> GetOutput() { return FOutput; }
    virtual void SetInput(TString Value) = 0;

    ReadProperty<Process, std::list<TString>, &Process::GetOutput> Output;
    WriteProperty<Process, TString, &Process::SetInput> Input;
};


template <typename T>
class AutoFreePtr {
private:
    T* FObject;

public:
    AutoFreePtr() {
        FObject = NULL;
    }

    AutoFreePtr(T* Value) {
        FObject = Value;
    }

    ~AutoFreePtr() {
        if (FObject != NULL) {
            delete FObject;
        }
    }

    operator T* () const {
        return FObject;
    }

    T& operator* () const {
        return *FObject;
    }

    T* operator->() const {
        return FObject;
    }

    T** operator&() {
        return &FObject;
    }

    T* operator=(const T * rhs) {
        FObject = rhs;
        return FObject;
    }
};


class IPropertyContainer {
public:
    IPropertyContainer(void) {}
    virtual ~IPropertyContainer(void) {}

    virtual bool GetValue(const TString Key, TString& Value) = 0;
    virtual size_t GetCount() = 0;
};

class ISectionalPropertyContainer {
public:
    ISectionalPropertyContainer(void) {}
    virtual ~ISectionalPropertyContainer(void) {}

    virtual bool GetValue(const TString SectionName,
            const TString Key, TString& Value) = 0;
    virtual bool ContainsSection(const TString SectionName) = 0;
    virtual bool GetSection(const TString SectionName,
            OrderedMap<TString, TString> &Data) = 0;
};

class Environment {
private:
    Environment() {
    }

public:
    static TString GetNewLine() {
#ifdef WINDOWS
        return _T("\r\n");
#endif //WINDOWS
#ifdef POSIX
        return _T("\n");
#endif //POSIX
    }

    static StaticReadProperty<TString, &Environment::GetNewLine> NewLine;
};


enum DebugState {dsNone, dsNative, dsJava};
enum MessageResponse {mrOK, mrCancel};
enum AppCDSState {cdsUninitialized, cdsDisabled,
        cdsEnabled, cdsAuto, cdsGenCache};

class Platform {
private:
    AppCDSState FAppCDSState;

protected:
    TProcessID singleInstanceProcessId;

    Platform(void): FAppCDSState(cdsUninitialized), singleInstanceProcessId(0) {
    }

public:
    AppCDSState GetAppCDSState() { return FAppCDSState; }
    void SetAppCDSState(AppCDSState Value) { FAppCDSState = Value; }
    TProcessID GetSingleInstanceProcessId() { return singleInstanceProcessId; }

    static Platform& GetInstance();

    virtual ~Platform(void) {}

public:
    virtual void ShowMessage(TString title, TString description) = 0;
    virtual void ShowMessage(TString description) = 0;
    virtual MessageResponse ShowResponseMessage(TString title,
           TString description) = 0;

    virtual void SetCurrentDirectory(TString Value) = 0;

    // Caller must free result using delete[].
    virtual TCHAR* ConvertStringToFileSystemString(TCHAR* Source,
            bool &release) = 0;

    // Caller must free result using delete[].
    virtual TCHAR* ConvertFileSystemStringToString(TCHAR* Source,
            bool &release) = 0;

    // Returns:
    // Windows=C:\Users\<username>\AppData\Local
    // Linux=~/.local
    // Mac=~/Library/Application Support
    virtual TString GetAppDataDirectory() = 0;

    virtual TString GetPackageAppDirectory() = 0;
    virtual TString GetPackageLauncherDirectory() = 0;
    virtual TString GetPackageRuntimeBinDirectory() = 0;
    virtual TString GetAppName() = 0;

    virtual TString GetConfigFileName() = 0;

    virtual TString GetBundledJVMLibraryFileName(TString RuntimePath) = 0;

    // Caller must free result.
    virtual ISectionalPropertyContainer* GetConfigFile(TString FileName) = 0;

    virtual TString GetModuleFileName() = 0;
    virtual TString GetPackageRootDirectory() = 0;

    virtual Module LoadLibrary(TString FileName) = 0;
    virtual void FreeLibrary(Module Module) = 0;
    virtual Procedure GetProcAddress(Module Module, std::string MethodName) = 0;
    virtual std::vector<TString> GetLibraryImports(const TString FileName) = 0;
    virtual std::vector<TString> FilterOutRuntimeDependenciesForPlatform(
            std::vector<TString> Imports) = 0;

    // Caller must free result.
    virtual Process* CreateProcess() = 0;

    virtual bool IsMainThread() = 0;
    virtual bool CheckForSingleInstance(TString Name) = 0;
    virtual void reactivateAnotherInstance() = 0;

    // Returns megabytes.
    virtual TPlatformNumber GetMemorySize() = 0;

    virtual std::map<TString, TString> GetKeys() = 0;

    virtual std::list<TString> LoadFromFile(TString FileName) = 0;
    virtual void SaveToFile(TString FileName,
             std::list<TString> Contents, bool ownerOnly) = 0;

    virtual TString GetTempDirectory() = 0;

#ifdef DEBUG
    virtual DebugState GetDebugState() = 0;
    virtual int GetProcessID() = 0;
    virtual bool IsNativeDebuggerPresent() = 0;
#endif //DEBUG
};


class Library {
private:
    std::vector<TString> *FDependentLibraryNames;
    std::vector<Library*> *FDependenciesLibraries;
    Module FModule;
    std::string fname;

    void Initialize();
    void InitializeDependencies();
    void LoadDependencies();
    void UnloadDependencies();

public:
    void* GetProcAddress(const std::string& MethodName) const;

public:
    Library();
    Library(const TString &FileName);
    ~Library();

    bool Load(const TString &FileName);
    bool Unload();

    const std::string& GetName() const {
        return fname;
    }

    void AddDependency(const TString &FileName);
    void AddDependencies(const std::vector<TString> &Dependencies);
};


class Exception: public std::exception {
private:
    TString FMessage;

protected:
    void SetMessage(const TString Message) {
        FMessage = Message;
    }

public:
    explicit Exception() : exception() {}
    explicit Exception(const TString Message) : exception() {
        SetMessage(Message);
    }
    virtual ~Exception() throw() {}

    TString GetMessage() { return FMessage; }
};

class FileNotFoundException: public Exception {
public:
    explicit FileNotFoundException(const TString Message) : Exception(Message) {}
};

#endif // PLATFORM_H
