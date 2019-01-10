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

package jnlp.converter.parser;

import java.net.URL;
import java.net.MalformedURLException;

import jnlp.converter.parser.exception.BadFieldException;
import jnlp.converter.parser.exception.MissingFieldException;
import jnlp.converter.parser.xml.XMLNode;

/** Contains handy methods for looking up information
 *  stored in XMLNodes.
 */
public class XMLUtils {

    /** Returns the value of an integer attribute */
    public static int getIntAttribute(String source, XMLNode root, String path, String name, int defaultvalue)
            throws BadFieldException {
        String value = getAttribute(root, path, name);
        if (value == null) {
            return defaultvalue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            throw new BadFieldException(source, getPathString(root) + path + name, value);
        }
    }

    /** Returns the value of a given attribute, or null if not set */
    public static String getAttribute(XMLNode root, String path, String name)
                                                      throws BadFieldException {
        return getAttribute(root, path, name, null);
    }

    /** Returns the value of a given attribute */
    public static String getRequiredAttributeEmptyOK(String source,
        XMLNode root, String path, String name) throws MissingFieldException {
        String value = null;
        XMLNode elem = findElementPath(root, path);
        if (elem != null) {
            value = elem.getAttribute(name);
        }
        if (value == null) {
            throw new MissingFieldException(source,
                                            getPathString(root)+ path + name);
        }
        return value;
    }

    /*** Returns the value of an attribute, which must be a valid class name */
    public static String getClassName(String source, XMLNode root,
        String path, String name, boolean required)
            throws BadFieldException, MissingFieldException {

        String className;
        if (required) {
            className = getRequiredAttribute(source, root, path, name);
        } else {
            className = getAttribute(root, path, name);
        }
        if (className != null && className.endsWith(".class")) {
            int i = className.lastIndexOf(".class");
            String cname = className.substring(0, i);
            return cname;
        }
        return className;
    }

    /** Returns the value of a given attribute, or null if not set */
    public static String getRequiredAttribute(String source, XMLNode root,
            String path, String name) throws MissingFieldException, BadFieldException {
        String s = getAttribute(root, path, name, null);
        if (s == null) {
            throw new MissingFieldException(source, getPathString(root) + path + name);
        }
        s = s.trim();
        return (s.length() == 0) ? null : s;
    }

    /** Returns the value of a given attribute, or the default value 'def' if not set */
    public static String getAttribute(XMLNode root, String path, String name,
            String def) throws BadFieldException {
        XMLNode elem = findElementPath(root, path);
        if (elem == null) {
            return def;
        }
        String value = elem.getAttribute(name);
        return (value == null || value.length() == 0) ? def : value;
    }

    /** Expands a URL into an absolute URL from a relative URL */
    public static URL getAttributeURL(String source, URL base, XMLNode root, String path, String name) throws BadFieldException {
        String value = getAttribute(root, path, name);
        if (value == null) return null;
        try {
            if (value.startsWith("jar:")) {
                int bang = value.indexOf("!/");
                if (bang > 0) {
                    String entry = value.substring(bang);
                    String urlString = value.substring(4, bang);
                    URL url = (base == null) ?
                        new URL(urlString) : new URL(base, urlString);
                    return new URL("jar:" + url.toString() + entry);
                }
            }
            return (base == null) ? new URL(value) : new URL(base, value);
        } catch(MalformedURLException mue) {
            if (mue.getMessage().contains("https")) {
                throw new BadFieldException(source, "<jnlp>", "https");
            }
            throw new BadFieldException(source, getPathString(root) + path + name, value);
        }
    }

    /** Returns the value of an attribute as a URL or null if not set */
    public static URL getAttributeURL(String source, XMLNode root, String path, String name) throws BadFieldException {
        return getAttributeURL(source, null, root, path, name);
    }

    public static URL getRequiredURL(String source, URL base, XMLNode root, String path, String name) throws BadFieldException, MissingFieldException {
        URL url = getAttributeURL(source, base, root, path, name);
        if (url == null) {
            throw new MissingFieldException(source, getPathString(root) + path + name);
        }
        return url;
    }

    /** Returns the value of an attribute as a URL. Throws a MissingFieldException if the
     *  attribute is not defined
     */
    public static URL getRequiredURL(String source, XMLNode root, String path, String name) throws BadFieldException, MissingFieldException {
        return getRequiredURL(source, null, root, path, name);
    }

    /** Returns true if the path exists in the document, otherwise false */
    public static boolean isElementPath(XMLNode root, String path) {
        return findElementPath(root, path) != null;
    }

    public static URL getElementURL(String source, XMLNode root, String path) throws BadFieldException {
        String value = getElementContents(root, path);
        try {
            return new URL(value);
        } catch(MalformedURLException mue) {
            throw new BadFieldException(source, getPathString(root) + path, value);
        }
    }

    /** Returns a string describing the current location in the DOM */
    public static String getPathString(XMLNode e) {
        return (e == null || !(e.isElement())) ? "" : getPathString(e.getParent()) + "<" + e.getName() + ">";
    }

    /** Returns the contents of an element with the given path and an attribute matching a specific value. Returns
     *  NULL if not found
     */
    public static String getElementContentsWithAttribute(XMLNode root, String path, String attr, String val, String defaultvalue)
            throws BadFieldException, MissingFieldException {
        XMLNode e = getElementWithAttribute(root, path, attr, val);
        if (e == null) {
            return defaultvalue;
        }
        return getElementContents(e, "", defaultvalue);
    }

    public static URL getAttributeURLWithAttribute(String source, XMLNode root, String path, String attrcond, String val,
            String name, URL defaultvalue)
            throws BadFieldException, MissingFieldException {
        XMLNode e = getElementWithAttribute(root, path, attrcond, val);
        if (e == null) {
            return defaultvalue;
        }
        URL url = getAttributeURL(source, e, "", name);
        if (url == null) {
            return defaultvalue;
        }
        return url;
    }

    /** Returns an element with the given path and an attribute matching a specific value. Returns
     *  NULL if not found
     */
    public static XMLNode getElementWithAttribute(XMLNode root, String path, final String attr, final String val)
            throws BadFieldException, MissingFieldException {
        final XMLNode[] result = {null};
        visitElements(root, path, new ElementVisitor() {
            public void visitElement(XMLNode e) throws BadFieldException, MissingFieldException {
                if (result[0] == null && e.getAttribute(attr).equals(val)) {
                    result[0] = e;
                }
            }
        });
        return result[0];
    }

    /** Like getElementContents(...) but with a defaultValue of null */
    public static String getElementContents(XMLNode root, String path) {
        return getElementContents(root, path, null);
    }

    /** Returns the value of the last element tag in the path, e.g.,  <..><tag>value</tag>. The DOM is assumes
     *  to be normalized. If no value is found, the defaultvalue is returned
     */
    public static String getElementContents(XMLNode root, String path, String defaultvalue) {
        XMLNode e = findElementPath(root, path);
        if (e == null) {
            return defaultvalue;
        }
        XMLNode n = e.getNested();
        if (n != null && !n.isElement()) {
            return n.getName();
        }
        return defaultvalue;
    }

    /** Parses a path string of the form <tag1><tag2><tag3> and returns the specific Element
     *  node for that tag, or null if it does not exist. If multiple elements exists with same
     *  path the first is returned
     */
    public static XMLNode findElementPath(XMLNode elem, String path) {
        // End condition. Root null -> path does not exist
        if (elem == null) {
            return null;
        }

        // End condition. String empty, return current root
        if (path == null || path.length() == 0) {
            return elem;
        }

        // Strip of first tag
        int idx = path.indexOf('>');
        if (!(path.charAt(0) == '<')) {
            throw new IllegalArgumentException("bad path. Missing begin tag");
        }
        if (idx == -1) {
            throw new IllegalArgumentException("bad path. Missing end tag");
        }
        String head = path.substring(1, idx);
        String tail = path.substring(idx + 1);
        return findElementPath(findChildElement(elem, head), tail);
    }

    /** Returns an child element with the current tag name or null. */
    public static XMLNode findChildElement(XMLNode elem, String tag) {
        XMLNode n = elem.getNested();
        while (n != null) {
            if (n.isElement() && n.getName().equals(tag)) {
                return n;
            }
            n = n.getNext();
        }
        return null;
    }

    /** Iterator class */
    public abstract static class ElementVisitor {
        abstract public void visitElement(XMLNode e) throws BadFieldException, MissingFieldException;
    }

    /** Visits all elements which matches the <path>. The iteration is only
     *  done on the last element in the path.
     */
    public static void visitElements(XMLNode root, String path, ElementVisitor ev)
            throws BadFieldException, MissingFieldException {
        // Get last element in path
        int idx = path.lastIndexOf('<');
        if (idx == -1) {
            throw new IllegalArgumentException(
                    "bad path. Must contain atleast one tag");
        }
        if (path.length() == 0 || path.charAt(path.length() - 1) != '>') {
            throw new IllegalArgumentException("bad path. Must end with a >");
        }
        String head = path.substring(0, idx);
        String tag = path.substring(idx + 1, path.length() - 1);

        XMLNode elem = findElementPath(root, head);
        if (elem == null) {
            return;
        }

        // Iterate through all child nodes
        XMLNode n = elem.getNested();
        while (n != null) {
            if (n.isElement() && n.getName().equals(tag)) {
                ev.visitElement(n);
            }
            n = n.getNext();
        }
    }

    public static void visitChildrenElements(XMLNode elem, ElementVisitor ev)
            throws BadFieldException, MissingFieldException {
        // Iterate through all child nodes
        XMLNode n = elem.getNested();
        while (n != null) {
            if (n.isElement()) {
                ev.visitElement(n);
            }
            n = n.getNext();
        }
    }
}
