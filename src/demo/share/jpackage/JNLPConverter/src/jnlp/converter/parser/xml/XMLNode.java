/*
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jnlp.converter.parser.xml;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Class that contains information about an XML Node
 */
public class XMLNode {
    private final boolean _isElement;     // Element/PCTEXT
    private final String _name;
    private final XMLAttribute _attr;
    private XMLNode _parent;  // Parent Node
    private XMLNode _nested;  // Nested XML tags
    private XMLNode _next;    // Following XML tag on the same level

    public final static String WILDCARD = "*";

    /** Creates a PCTEXT node */
    public XMLNode(String name) {
        _isElement = false;
        _name = name;
        _attr = null;
        _nested = null;
        _next = null;
        _parent = null;
    }

    /** Creates a ELEMENT node */
    public XMLNode(String name, XMLAttribute attr) {
        _isElement = true;
        _name = stripNameSpace(name);
        _attr = attr;
        _nested = null;
        _next = null;
        _parent = null;
    }

    public String getName() {
        return _name;
    }

    public XMLAttribute getAttributes() {
        return _attr;
    }

    public XMLNode getNested() {
        return _nested;
    }

    public XMLNode getNext() {
        return _next;
    }

    public boolean isElement() {
        return _isElement;
    }

    public void setParent(XMLNode parent) {
        _parent = parent;
    }

    public XMLNode getParent() {
        return _parent;
    }

    public void setNext(XMLNode next) {
        _next = next;
    }

    public void setNested(XMLNode nested) {
        _nested = nested;
    }

    public static String stripNameSpace(String name) {
        if (name != null && !name.startsWith("xmlns:")) {
            int i = name.lastIndexOf(":");
            if (i >= 0 && i < name.length()) {
                return name.substring(i+1);
            }
        }
        return name;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + (this._name != null ? this._name.hashCode() : 0);
        hash = 83 * hash + (this._attr != null ? this._attr.hashCode() : 0);
        hash = 83 * hash + (this._nested != null ? this._nested.hashCode() : 0);
        hash = 83 * hash + (this._next != null ? this._next.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof XMLNode)) return false;
        XMLNode other = (XMLNode)o;
        boolean result =
            match(_name, other._name) &&
            match(_attr, other._attr) &&
            match(_nested, other._nested) &&
            match(_next, other._next);
        return result;
    }

    public String getAttribute(String name) {
        XMLAttribute cur = _attr;
        while(cur != null) {
            if (name.equals(cur.getName())) return cur.getValue();
            cur = cur.getNext();
        }
        return "";
    }

    private static boolean match(Object o1, Object o2) {
        if (o1 == null) {
            return (o2 == null);
        }
        return o1.equals(o2);
    }

    public void printToStream(PrintWriter out) {
        printToStream(out, false);
    }

    public void printToStream(PrintWriter out, boolean trim) {
        printToStream(out, 0, trim);
    }

    public void printToStream(PrintWriter out, int n, boolean trim) {
        if (!isElement()) {
            String value = _name; // value node (where name is data of parent)
            if (trim && value.length() > 512) {
                value = "...";
            }
            out.print(value);
        } else {
            if (_nested == null) {
                String attrString = (_attr == null) ? "" : (" " + _attr.toString());
                lineln(out, n, "<" + _name + attrString + "/>");
            } else {
                String attrString = (_attr == null) ? "" : (" " + _attr.toString());
                lineln(out, n, "<" + _name + attrString + ">");
                _nested.printToStream(out, n + 1, trim);
                if (_nested.isElement()) {
                    lineln(out, n, "</" + _name + ">");
                } else {
                    out.print("</" + _name + ">");
                }
            }
        }
        if (_next != null) {
            _next.printToStream(out, n, trim);
        }
    }

    private static void lineln(PrintWriter out, int indent, String s) {
        out.println("");
        for(int i = 0; i < indent; i++) {
            out.print("  ");
        }
        out.print(s);
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean hideLongElementValue) {
        StringWriter sw = new StringWriter(1000);
        PrintWriter pw = new PrintWriter(sw);
        printToStream(pw, hideLongElementValue);
        pw.close();
        return sw.toString();
    }
}
