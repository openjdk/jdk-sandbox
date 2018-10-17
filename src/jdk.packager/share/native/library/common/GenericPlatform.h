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

#ifndef GENERICPLATFORM_H
#define GENERICPLATFORM_H

#include "FilePath.h"
// #include "Platform.h"

#ifdef WINDOWS
#pragma warning( push )
// C4250 - 'class1' : inherits 'class2::member' via dominance
#pragma warning( disable : 4250 )
#endif

class GenericPlatform : virtual public Platform {
public:
    GenericPlatform(void);
    virtual ~GenericPlatform(void);

    virtual TString GetPackageAppDirectory();
    virtual TString GetPackageLauncherDirectory();

    virtual TString GetConfigFileName();

    virtual std::list<TString> LoadFromFile(TString FileName);
    virtual void SaveToFile(TString FileName,
            std::list<TString> Contents, bool ownerOnly);

#if defined(WINDOWS) || defined(LINUX)
    virtual TString GetAppName();
#endif // WINDOWS || LINUX

    virtual std::map<TString, TString> GetKeys();

#ifdef DEBUG
    virtual DebugState GetDebugState();
#endif // DEBUG
};
#ifdef WINDOWS
#pragma warning( pop ) // C4250
#endif
#endif // GENERICPLATFORM_H
