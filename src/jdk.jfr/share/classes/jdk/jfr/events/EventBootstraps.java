/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.events;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.jfr.Event;
import jdk.jfr.SettingControl;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.handlers.EventHandler;

public final class EventBootstraps {

    private EventBootstraps() { }

    public static CallSite writeBootstrap(MethodHandles.Lookup lookup,
                                          String name,
                                          MethodType methodType,
                                          Class<? extends Event> eventClass,
                                          String writeMethodDescriptor,
                                          MethodHandle eventHandlerFieldGetter)
        throws Throwable
    {
        if (!name.equals("write"))
            throw new IllegalArgumentException("Bad method name: " + name);
        MethodType expectedMT = MethodType.fromMethodDescriptorString(writeMethodDescriptor, null)
                .insertParameterTypes(0, EventHandler.class);
        if (!methodType.equals(expectedMT))
            throw new IllegalArgumentException("Bad method type: " + methodType);

        Class<?> handlerClass = getHandlerClass(eventHandlerFieldGetter);
        assert Utils.getHandler(eventClass).getClass() == handlerClass;

        MethodType mt = MethodType.fromMethodDescriptorString(writeMethodDescriptor, null);
        MethodHandle mh = lookup.findVirtual(handlerClass, name, mt);
        mh = mh.asType(mh.type().changeParameterType(0, EventHandler.class));
        return new ConstantCallSite(mh);
    }

    public static CallSite settingsBootstrap(MethodHandles.Lookup lookup,
                                             String name,
                                             MethodType methodType,
                                             Class<? extends Event> eventClass,
                                             MethodHandle eventHandlerFieldGetter)
        throws Throwable
    {
        if (!name.startsWith("setting"))
            throw new IllegalArgumentException("Bad field name: " + name);
        //if (!type.equals(SettingControl.class))   // TODO: ensure correct methodType
        //    throw new IllegalArgumentException("Bad type: " + type);

        Class<?> handlerClass = getHandlerClass(eventHandlerFieldGetter);
        assert Utils.getHandler(eventClass).getClass() == handlerClass;

        MethodHandle mh = lookup.findGetter(handlerClass, name, SettingControl.class);
        mh = mh.asType(mh.type().changeParameterType(0, EventHandler.class));
        return new ConstantCallSite(mh);
    }

    private static Class<?> getHandlerClass(MethodHandle eventHandlerFieldGetter) throws Throwable {
        Class<?> handlerClass = ((EventHandler)eventHandlerFieldGetter.invokeExact()).getClass();
        if (handlerClass.isAssignableFrom(EventHandler.class))
            throw new IllegalArgumentException("Bad handler type: " + handlerClass);
        return handlerClass;
    }
}
