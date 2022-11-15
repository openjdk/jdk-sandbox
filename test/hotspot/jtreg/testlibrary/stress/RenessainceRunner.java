/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Datadog. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package stress;

import java.io.IOException;
import java.lang.ClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

public final class RenessainceRunner {
    private static final String VERSION = "0.14.1";
    private static ClassLoader getClassLoader() throws IOException {
        return new URLClassLoader(new URL[] {new URL("https://github.com/renaissance-benchmarks/renaissance/releases/download/v" + VERSION + "/renaissance-gpl-" + VERSION + ".jar")});
    }

    private static Class<?> getLauncherClass() throws IOException, ClassNotFoundException {
        return getClassLoader().loadClass("org.renaissance.core.Launcher");
    }

    public static boolean runBenchmark(String name, String ... args) {
        try {
            Method m = getLauncherClass().getMethod("main", String[].class);
            String[] invArgs = new String[1 + args.length];
            invArgs[0] = name;
            System.arraycopy(args, 0, invArgs, 1, args.length);
            m.invoke(null, (Object)invArgs);
            return true;
        } catch (IOException | ClassNotFoundException e) {
            return false;
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
