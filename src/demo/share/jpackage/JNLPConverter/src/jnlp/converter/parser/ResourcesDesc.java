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
import java.util.Properties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import jnlp.converter.HTTPHelper;

/**
 * This class contains information about the codebase and properties, i.e., how
 * to locate the classes and optional-packages
 */
public class ResourcesDesc implements ResourceType {

    private final List<ResourceType> _list;
    private volatile JNLPDesc _parent = null;

    /**
     * Create empty resource list
     */
    public ResourcesDesc() {
        _list = new CopyOnWriteArrayList<>();
    }

    public JNLPDesc getParent() {
        return _parent;
    }

    void setParent(JNLPDesc parent) {
        _parent = parent;
        for (int i = 0; i < _list.size(); i++) {
            Object o = _list.get(i);
            if (o instanceof JREDesc) {
                JREDesc jredesc = (JREDesc) o;
                if (jredesc.getNestedResources() != null) {
                    jredesc.getNestedResources().setParent(parent);
                }
            }
        }
    }

    public void addResource(ResourceType rd) {
        if (rd != null) {
            _list.add(rd);
        }
    }

    boolean isEmpty() {
        return _list.isEmpty();
    }

    public JARDesc[] getLocalJarDescs() {
        ArrayList<JARDesc> jds = new ArrayList<>(_list.size());
        for (ResourceType rt : _list) {
            if (rt instanceof JARDesc) {
                jds.add((JARDesc) rt);
            }
        }
        return jds.toArray(new JARDesc[jds.size()]);
    }

    public JREDesc getJreDesc() {
        for (ResourceType rt : _list) {
            if (rt instanceof JREDesc) {
                return (JREDesc)rt;
            }
        }

        return null;
    }

    public ExtensionDesc[] getExtensionDescs() throws Exception {
        final ArrayList<ExtensionDesc> extList = new ArrayList<>();
        visit(new ResourceVisitor() {
            @Override
            public void visitExtensionDesc(ExtensionDesc ed) throws Exception {
              // add all extensiondesc recursively
                addExtToList(extList);
            }
        });
        return extList.toArray(new ExtensionDesc[extList.size()]);
    }

    public JARDesc[] getAllJarDescs() throws Exception {
        List<JARDesc> jarList = new ArrayList<>();
        addJarsToList(jarList);
        return jarList.toArray(new JARDesc[jarList.size()]);
    }

    /**
     * Add to a list of all the ExtensionDesc. This method goes recusivly through
     * all ExtensionDesc
     */
    private void addExtToList(final List<ExtensionDesc> list) throws Exception {
        // Iterate through list an add ext jnlp to the list.
        visit(new ResourceVisitor() {
            @Override
            public void visitExtensionDesc(ExtensionDesc ed) throws Exception {
                if (ed.getExtensionDesc() != null) {
                    ed.getExtensionDesc().getMainJar();
                    ResourcesDesc rd = ed.getExtensionDesc().getResourcesDesc();
                    if (rd != null) {
                        rd.addExtToList(list);
                    }
                }
                list.add(ed);
            }
        });
    }

    private void addJarsToList(final List<JARDesc> list) throws Exception {

        // Iterate through list an add resources to the list.
        // The ordering of resources are preserved
        visit(new ResourceVisitor() {
            @Override
            public void visitJARDesc(JARDesc jd) {
                list.add(jd);
            }

            @Override
            public void visitExtensionDesc(ExtensionDesc ed) throws Exception {
                if (ed.getExtensionDesc() != null) {
                    ResourcesDesc rd = ed.getExtensionDesc().getResourcesDesc();
                    if (rd != null) {
                        rd.addJarsToList(list);
                    }
                }
            }
        });
    }

    /**
     * Get all the resources needed when a specific resource is requested.
     * Returns null if no resource was found
     */
    public JARDesc[] getResource(final URL location) throws Exception {
        final JARDesc[] resources = new JARDesc[1];
        // Find the given resource
        visit(new ResourceVisitor() {
            @Override
            public void visitJARDesc(JARDesc jd) {
                if (GeneralUtil.sameURLs(jd.getLocation(), location)) {
                    resources[0] = jd;
                }
            }
        });

        // Found no resource?
        if (resources[0] == null) {
            return null;
        }

        // No part, so just one resource
        return resources;
    }

    /* Returns the Expected Main Jar
     *    first jar with attribute main="true"
     *    else first jar if none has that attribute
     *    will look in extensions, and nested resource blocks if matching
     */
    protected JARDesc getMainJar() throws Exception {
        // Normal trick to get around final arguments to inner classes
        final JARDesc[] results = new JARDesc[2];

        visit(new ResourceVisitor() {
            @Override
            public void visitJARDesc(JARDesc jd) {
                if (jd.isJavaFile()) {
                    // Keep track of first Java File
                    if (results[0] == null || results[0].isNativeLib()) {
                        results[0] = jd;
                    }
                    // Keep tack of Java File marked main
                    if (jd.isMainJarFile()) {
                        results[1] = jd;
                    }
                } else if (jd.isNativeLib()) {
                    // if jnlp extension has only native lib
                    if (results[0] == null) {
                        results[0] = jd;
                    }
                }
            }

            @Override
            public void visitExtensionDesc(ExtensionDesc ed) throws Exception {
            // only check if no main yet and it is not an installer
                if (results[1] == null && !ed.isInstaller()) {
                    JNLPDesc extLd = ed.getExtensionDesc();
                    if (extLd != null && extLd.isLibrary()) {
                        ResourcesDesc rd = extLd.getResourcesDesc();
                        if (rd != null) {
                          // look for main jar in extension resource
                            rd.visit(this);
                        }
                    }
                }
            }
        });

        // Default is the first, if none is specified as main. This might
        // return NULL if there is no JAR resources.
        JARDesc first = results[0];
        JARDesc main = results[1];

        // if main is null then return first;
        // libraries have no such thing as a main jar, so return first;
        // otherwise return main
        // only returns null if there are no jars.
        return (main == null) ? first : main;
    }

    /*
     *  Get the properties defined for this object
     */
    public Properties getResourceProperties() throws Exception {
        final Properties props = new Properties();
        visit(new ResourceVisitor() {
            @Override
            public void visitPropertyDesc(PropertyDesc pd) {
                props.setProperty(pd.getKey(), pd.getValue());
            }

            @Override
            public void visitExtensionDesc(ExtensionDesc ed) throws Exception {
                JNLPDesc jnlpd = ed.getExtensionDesc();
                ResourcesDesc rd = jnlpd.getResourcesDesc();
                if (rd != null) {
                    Properties extProps = rd.getResourceProperties();
                    Enumeration e = extProps.propertyNames();
                    while (e.hasMoreElements()) {
                        String key = (String) e.nextElement();
                        String value = extProps.getProperty(key);
                        props.setProperty(key, value);
                    }
                }
            }
        });
        return props;
    }

    /*
     *  Get the properties defined for this object, in the right order.
     */
    public List<Property> getResourcePropertyList() throws Exception {
        final LinkedList<Property> propList = new LinkedList<>();
        visit(new ResourceVisitor() {
            @Override
            public void visitPropertyDesc(PropertyDesc pd) {
                propList.add(new Property(pd.getKey(), pd.getValue()));
            }
        });
        return propList;
    }

    /**
     * visitor dispatch
     */
    @Override
    public void visit(ResourceVisitor rv) throws Exception {
        for (int i = 0; i < _list.size(); i++) {
            ResourceType rt = _list.get(i);
            rt.visit(rv);
        }
    }

    public void addNested(ResourcesDesc nested) throws Exception {
        if (nested != null) {
            nested.visit(new ResourceVisitor() {
                @Override
                public void visitJARDesc(JARDesc jd) {
                    _list.add(jd);
                }

                @Override
                public void visitPropertyDesc(PropertyDesc pd) {
                    _list.add(pd);
                }

                @Override
                public void visitExtensionDesc(ExtensionDesc ed) {
                    _list.add(ed);
                }
            });
        }

    }

    public static class JARDesc implements ResourceType {

        private URL _location;
        private String _locationString;
        private String _version;
        private boolean _isNativeLib;
        private boolean _isMainFile;  // Only used for Java JAR files (a main JAR file is implicitly eager)
        private ResourcesDesc _parent;   // Back-pointer to the Resources that contains this JAR

        public JARDesc(URL location, String version, boolean isMainFile, boolean isNativeLib, ResourcesDesc parent) {
            _location = location;
            _locationString = GeneralUtil.toNormalizedString(location);
            _version = version;
            _isMainFile = isMainFile;
            _isNativeLib = isNativeLib;
            _parent = parent;
        }

        /**
         * Type of JAR resource
         */
        public boolean isNativeLib() {
            return _isNativeLib;
        }

        public boolean isJavaFile() {
            return !_isNativeLib;
        }

        /**
         * Returns URL/version for JAR file
         */
        public URL getVersionLocation() throws Exception {
            if (getVersion() == null) {
                return _location;
            } else {
                return GeneralUtil.getEmbeddedVersionURL(getLocation(), getVersion());
            }
        }

        public URL getLocation() {
            return _location;
        }

        public String getVersion() {
            return _version;
        }

        public String getName() {
            // File can be separated by '/' or '\\'
            int index;
            int index1 = _locationString.lastIndexOf('/');
            int index2 = _locationString.lastIndexOf('\\');

            if (index1 >= index2) {
                index = index1;
            } else {
                index = index2;
            }

            if (index != -1) {
                return _locationString.substring(index + 1, _locationString.length());
            }

            return null;
        }

        /**
         * Returns if this is the main JAR file
         */
        public boolean isMainJarFile() {
            return _isMainFile;
        }

        /**
         * Get parent LaunchDesc
         */
        public ResourcesDesc getParent() {
            return _parent;
        }

        /**
         * Visitor dispatch
         */
        public void visit(ResourceVisitor rv) {
            rv.visitJARDesc(this);
        }
    }

    public static class PropertyDesc implements ResourceType {

        private String _key;
        private String _value;

        public PropertyDesc(String key, String value) {
            _key = key;
            _value = value;
        }

        // Accessors
        public String getKey() {
            return _key;
        }

        public String getValue() {
            return _value;
        }

        /**
         * Visitor dispatch
         */
        public void visit(ResourceVisitor rv) {
            rv.visitPropertyDesc(this);
        }

    }

    public static class JREDesc implements ResourceType {

        private String _version;
        private long _maxHeap;
        private long _minHeap;
        private String _vmargs;
        private ResourcesDesc _resourceDesc;
        private JNLPDesc _extensioDesc;
        private String _archList;

        /*
         * Constructor to create new instance based on the requirements from JNLP file.
         */
        public JREDesc(String version, long minHeap, long maxHeap, String vmargs,
                       ResourcesDesc resourcesDesc, String archList) {

            _version = version;
            _maxHeap = maxHeap;
            _minHeap = minHeap;
            _vmargs = vmargs;
            _resourceDesc = resourcesDesc;
            _extensioDesc = null;
            _archList = archList;
        }

        public String[] getArchList() {
            return GeneralUtil.getStringList(_archList);
        }

        public String getVersion() {
            return _version;
        }

        public long getMinHeap() {
            return _minHeap;
        }

        public long getMaxHeap() {
            return _maxHeap;
        }

        public String getVmArgs() {
            return _vmargs;
        }

        public String[] getVmArgsList() {
            return GeneralUtil.getStringList(_vmargs);
        }

        public ResourcesDesc getNestedResources() {
            return _resourceDesc;
        }

        public JNLPDesc getExtensionDesc() {
            return _extensioDesc;
        }

        public void setExtensionDesc(JNLPDesc ld) {
            _extensioDesc = ld;
        }

        /* visitor dispatch */
        public void visit(ResourceVisitor rv) {
            rv.visitJREDesc(this);
        }
    }

    public static class Property implements Cloneable {

        public static final String JNLP_VERSION_ENABLED = "jnlp.versionEnabled";

        String key;
        String value;

        public Property(String spec) {
            spec = spec.trim();
            if (!spec.startsWith("-D") || spec.length() < 3) {
                throw new IllegalArgumentException("Property invalid");
            }

            int endKey = spec.indexOf("=");
            if (endKey < 0) {
                // it's legal to have no assignment
                this.key = spec.substring(2); // skip "-D"
                this.value = "";
            } else {
                this.key = spec.substring(2, endKey);
                this.value = spec.substring(endKey + 1);
            }
        }

        public static Property createProperty(String spec) {
            Property prop = null;
            try {
                prop = new Property(spec);
            } catch (IllegalArgumentException iae) {
            }
            return prop;
        }

        public Property(String key, String value) {
            this.key = key;
            if (value != null) {
                this.value = value;
            } else {
                this.value = "";
            }
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        // @return String representation, unquoted, unified presentation
        public String toString() {
            if (value.length() == 0) {
                return "-D" + key;
            }
            return "-D" + key + "=" + value;
        }

        public void addTo(Properties props) {
            props.setProperty(key, value);
        }

        // Hash Object
        public boolean equals(Object o) {
            if (!(o instanceof Property)) {
                return false;
            }
            Property op = (Property) o;
            int hashTheirs = op.hashCode();
            int hashThis = hashCode();
            return hashTheirs == hashThis;
        }

        public int hashCode() {
            return key.hashCode();
        }

        private static List<Object> jnlpProps = Arrays.asList(new Object[]{
            JNLP_VERSION_ENABLED
        });

        public static boolean isJnlpProperty(String spec) {
            try {
                Property p = new Property(spec);
                return isJnlpPropertyKey(p.getKey());
            } catch (Exception e) {
                return false;
            }
        }

        public static boolean isJnlpPropertyKey(String key) {
            return key != null && jnlpProps.contains(key);
        }
    }

    public static class ExtensionDesc implements ResourceType {
        // Tag elements

        private final URL _location;
        private final String _locationString;
        private final String _version;
        private final URL _codebase;

        // Link to launchDesc
        private JNLPDesc _extensionLd; // Link to launchDesc for extension

        public ExtensionDesc(URL location, String version) {
            _location = location;
            _locationString = GeneralUtil.toNormalizedString(location);
            _version = version;
            _codebase = GeneralUtil.asPathURL(GeneralUtil.getBase(location));
            _extensionLd = null;
        }

        public boolean isInstaller() throws Exception {
            if (getExtensionDesc() != null) {
                return _extensionLd.isInstaller();
            }
            return false;
        }

        public URL getLocation() {
            return _location;
        }

        public String getVersionLocation() throws Exception {
            if (getVersion() == null) {
                return _locationString;
            } else {
                return GeneralUtil.toNormalizedString(GeneralUtil.getEmbeddedVersionURL(getLocation(), getVersion()));
            }
        }

        public String getVersion() {
            return _version;
        }

        public URL getCodebase() {
            return _codebase;
        }

        /*
         * Information about the resources
         */
        public JNLPDesc getExtensionDesc() throws Exception {
            if (_extensionLd == null) {
                byte[] bits = HTTPHelper.getJNLPBits(getVersionLocation(), _locationString);
                _extensionLd = XMLFormat.parse(bits, getCodebase(), getVersionLocation());
            }
            return _extensionLd;
        }

        public void setExtensionDesc(JNLPDesc desc) {
            _extensionLd = desc;
        }

        /**
         * Visitor dispatch
         */
        public void visit(ResourceVisitor rv) throws Exception {
            rv.visitExtensionDesc(this);
        }
    }
}
