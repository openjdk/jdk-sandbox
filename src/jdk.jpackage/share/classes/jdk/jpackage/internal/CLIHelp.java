/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.util.ResourceBundle;
import java.io.File;
import java.text.MessageFormat;


/**
 * CLIHelp
 *
 * Generate and show the command line interface help message(s).
 */
public class CLIHelp {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.HelpResources");

    // generates --help for jpackage's CLI
    public static void showHelp(boolean noArgs) {

        Platform platform = Platform.getPlatform();
        if (noArgs) {
            Log.info(MessageFormat.format(
                     I18N.getString("MSG_Help_no_args"), File.pathSeparator));
        } else {
            Log.info(MessageFormat.format(
                    I18N.getString("MSG_Help_common"), File.pathSeparator));

            switch (platform) {
                case MAC:
                    Log.info(I18N.getString("MSG_Help_mac"));
                    break;
                case LINUX:
                     Log.info(I18N.getString("MSG_Help_linux"));
                    break;
                case WINDOWS:
                     Log.info(I18N.getString("MSG_Help_win"));
                    break;
            }
        }
    }
}
