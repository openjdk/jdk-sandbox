/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Small native helper app to revive a process from a core or miniudump,
 * and run a named jcmd command in the revived JVM.
 *
 * Invoked by sun/tools/jcmd/JCmd.java to provide "jcmd on core".
 *
 * LD_USE_LOAD_BIAS=1 is required on Linux.
 */

#include "revival.hpp"

/**
 * Show usage message, and exit with an error status.
 */
void usageExit(const char* s) {
    error("usage: %s [ -L/path/path/libdir ] COREFILE jcmd DCOMMAND...\n", s);
}


int main(int argc, char **argv) {
    char *corename;
    const char *libdir = nullptr;
    char command[BUFLEN];
    char javahome[BUFLEN];
    memset(command, 0, BUFLEN);
    memset(javahome, 0, BUFLEN);
    int n = 1;

    // Deduce JDK home from our executable name.
    // This program is built into the JDK lib directory.
#ifdef WINDOWS
#define MY_NAME "\\lib\\revivalhelper"
#else
#define MY_NAME "/lib/revivalhelper"
#endif
    char *s = strstr(argv[0], MY_NAME);
    if (s != nullptr) {
        strncpy(javahome, argv[0], (s - argv[0]));
        if (verbose) {
            log("revivalhelper: Using JDK home: '%s'\n", javahome);
        }
    } else {
        error("revivalhelper: cannot find JDK home in '%s'.\n", argv[0]);
    }

    if (argc < 4 ) {
        usageExit(argv[0]);
    }
    // -L/libdir
    if (strncmp(argv[n], "-L", 2) == 0) {
        if (strlen(argv[n]) > 2) {
            libdir = argv[n]+2;
            n++;
        } else {
            error("Use -L/path/to/libdir to specify library directory.\n");
        }
    }
    if ((argc - n) < 2 ) {
        usageExit(argv[0]);
    }

    corename = argv[n++];

    // jcmd expected argument:
    if (strcmp(argv[n++], "jcmd") != 0) {
        error("jcmd keyword expected.\n");
    }
    // Build jcmd from all additional arguments:
    for (int i = n; i < argc; i++) {
        if (i != n) {
            // Add a space if not adding the first item.
            strncat(command, " ", BUFLEN - 1);
        }
        strncat(command, argv[i], BUFLEN - 1);
    }

    int e = revive_image(corename, javahome, libdir);

    if (e < 0) {
        fprintf(stderr, "Error: revive failed: %d\n", e);
        // Will call _exit below, don't call error().
    } else {
        e = revival_dcmd(command);
    }

    _exit(e);
}

