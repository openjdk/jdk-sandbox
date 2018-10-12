/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jnlp.converter.parser;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;

/*
 * Utility class that knows to handle version strings
 * A version string is of the form:
 *
 *  (version-id ('+'?) ' ') *
 *
 */
public class VersionString {
    private final ArrayList<VersionID> _versionIds;

    /** Constructs a VersionString object from string */
    public VersionString(String vs) {
        _versionIds = new ArrayList<>();
        if (vs != null) {
            StringTokenizer st = new StringTokenizer(vs, " ", false);
            while (st.hasMoreElements()) {
                // Note: The VersionID class takes care of a postfixed '+'
                _versionIds.add(new VersionID(st.nextToken()));
            }
        }
    }

    public VersionString(VersionID id) {
        _versionIds = new ArrayList<>();
        if (id != null) {
            _versionIds.add(id);
        }
    }

    public boolean isSimpleVersion() {
        if (_versionIds.size() == 1) {
            return _versionIds.get(0).isSimpleVersion();
        }
        return false;
    }

    /** Check if this VersionString object contains the VersionID m */
    public boolean contains(VersionID m) {
        for (int i = 0; i < _versionIds.size(); i++) {
            VersionID vi = _versionIds.get(i);
            boolean check = vi.match(m);
            if (check) {
                return true;
            }
        }
        return false;
    }

    /** Check if this VersionString object contains the VersionID m, given as a string */
    public boolean contains(String versionid) {
        return contains(new VersionID(versionid));
    }

    /** Check if this VersionString object contains anything greater than m */
    public boolean containsGreaterThan(VersionID m) {
        for (int i = 0; i < _versionIds.size(); i++) {
            VersionID vi = _versionIds.get(i);
            boolean check = vi.isGreaterThan(m);
            if (check) {
                return true;
            }
        }
        return false;
    }

    /** Check if this VersionString object contains anything greater than the VersionID m, given as a string */
    public boolean containsGreaterThan(String versionid) {
        return containsGreaterThan(new VersionID(versionid));
    }

    /** Check if the versionString 'vs' contains the VersionID 'vi' */
    public static boolean contains(String vs, String vi) {
        return (new VersionString(vs)).contains(vi);
    }

    /** Pretty-print object */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < _versionIds.size(); i++) {
            sb.append(_versionIds.get(i).toString());
            sb.append(' ');
        }
        return sb.toString();
    }

    public List<VersionID> getAllVersionIDs() {
        return _versionIds;
    }
}
