/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import jnlp.converter.Log;

import jnlp.converter.parser.ResourcesDesc.JARDesc;
import jnlp.converter.parser.ResourcesDesc.JREDesc;

public class JNLPDesc {
    private String specVersion = null;
    private String codebase = null;
    private String version = null;
    private String href = null;
    private String name = null;
    private String title = null;
    private String vendor = null;
    private String mainJar = null;
    private String [] descriptions = null;
    private IconDesc [] icons = null;
    private ShortcutDesc shortcuts = null;
    private AssociationDesc [] associations = null;
    private String mainClass = null;
    private final List<String> arguments  = new ArrayList<>();
    private final List<String> files = new ArrayList<>();
    private final List<JARDesc> resources = new ArrayList<>();
    private final List<String> vmArgs = new ArrayList<>();
    private boolean isLibrary = false;
    private boolean isInstaller = false;
    private boolean isJRESet = false;
    private ResourcesDesc resourcesDesc;
    private boolean isVersionEnabled = false;
    private boolean isSandbox = true;
    private boolean isFXApp = false;

    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;

        // Valid values are 1.0, 1.5, 6.0, 6.0.10, 6.0.18, 7.0, 8.20, 9 or a wildcard such as 1.0+.
        if (!specVersion.startsWith("1.0") &&
            !specVersion.startsWith("1.5") &&
            !specVersion.startsWith("6.0") &&
            !specVersion.startsWith("6.0.10") &&
            !specVersion.startsWith("6.0.18") &&
            !specVersion.startsWith("7.0") &&
            !specVersion.startsWith("8.20") &&
            !specVersion.startsWith("9")) {
                System.out.println("Warning: Invalid version of the JNLP specification found: "
                        + specVersion + ". Valid values are 1.0, 1.5, 6.0, 6.0.10, 6.0.18, 7.0,"
                        + " 8.20, 9 or a wildcard such as 1.0+.");
        }
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public void setCodebase(String codebase) {
        this.codebase = codebase;
    }

    public String getCodebase() {
        return codebase;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getHref() {
        return href;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getVendor() {
        return vendor;
    }

    public void setMainJar(String mainJar) {
        if (this.mainJar == null) {
            this.mainJar = mainJar;
        } else {
            Log.warning("Main jar already set to '" + this.mainJar + "'. "
                    + "Attempt to set main jar to '" + mainJar + "' will be ignored.");
        }
    }

    public String getMainJar() {
        return mainJar;
    }

    public void setDescriptions(String [] descriptions) {
        this.descriptions = descriptions;
    }

    public String getDescription() {
        String description = null;
        if (descriptions != null) {
            if (descriptions[InformationDesc.DESC_DEFAULT] != null) {
                description = descriptions[InformationDesc.DESC_DEFAULT];
            } else if (descriptions[InformationDesc.DESC_SHORT] != null) {
                description = descriptions[InformationDesc.DESC_SHORT];
            } else if (descriptions[InformationDesc.DESC_ONELINE] != null) {
                description = descriptions[InformationDesc.DESC_ONELINE];
            } else if (descriptions[InformationDesc.DESC_TOOLTIP] != null) {
                description = descriptions[InformationDesc.DESC_TOOLTIP];
            }

            if (description != null) {
                if (description.contains("\r") || description.contains("\n")) {
                    Log.warning("Multiple lines of text in description is not supported and description will be converted to single line by replacing new lines with spaces.");
                    Log.warning("Original description:");
                    Log.warning(description);
                    String descs[] = description.split("\n");
                    description = "";
                    for (String desc : descs) {
                        desc = desc.trim();
                        if (desc.endsWith("\r")) { // In case new line was \r\n
                            if (desc.length() != 1) {
                                desc = desc.substring(0, desc.length() - 1);
                            } else {
                                continue;
                            }
                        }

                        if (desc.isEmpty()) {
                            continue;
                        }

                        if (!description.isEmpty()) {
                            description += " ";
                        }

                        description += desc;
                    }
                    Log.warning("Converted description:");
                    Log.warning(description);
                }
            }
        }

        return description;
    }

    public void setIcons(IconDesc [] icons) {
        this.icons = icons;
    }

    public IconDesc getIcon() {
        for (IconDesc icon : icons) {
            if (icon.getKind() == IconDesc.ICON_KIND_DEFAULT) {
                return icon;
            }
        }

        for (IconDesc icon : icons) {
            if (icon.getKind() == IconDesc.ICON_KIND_SHORTCUT) {
                return icon;
            }
        }

        return null;
    }

    public String getIconLocation() {
        IconDesc icon = getIcon();
        if (icon != null) {
            return icon.getLocalLocation();
        }

        return null;
    }

    public void setShortcuts(ShortcutDesc shortcuts) {
        this.shortcuts = shortcuts;
    }

    public boolean isDesktopHint() {
        if (shortcuts != null) {
            return shortcuts.getDesktop();
        }

        return false;
    }

    public boolean isMenuHint() {
        if (shortcuts != null) {
            return shortcuts.getMenu();
        }

        return false;
    }

    public String getSubMenu() {
        if (shortcuts != null) {
            return shortcuts.getSubmenu();
        }

        return null;
    }

    public void setAssociations(AssociationDesc [] associations) {
        this.associations = associations;
    }

    public AssociationDesc [] getAssociations() {
         return associations;
    }

    public void setMainClass(String mainClass, boolean isJavafxDesc) {
        if (isJavafxDesc) {
            this.mainClass = mainClass;
        } else if (this.mainClass == null) {
            this.mainClass = mainClass;
        }
    }

    public String getMainClass() {
        return mainClass;
    }

    public void addArguments(String argument) {
        if (argument != null && !argument.isEmpty()) {
            arguments.add(argument);
        }
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setProperty(String name, String value) {
        if (name.equalsIgnoreCase("jnlp.versionEnabled") && value.equalsIgnoreCase("true")) {
            isVersionEnabled = true;
            return;
        }

        addVMArg("-D" + name + "=" + value);
    }

    public boolean isVersionEnabled() {
        return isVersionEnabled;
    }

    public boolean isSandbox() {
        return isSandbox;
    }

    public void setIsSandbox(boolean value) {
        isSandbox = value;
    }

    public boolean isFXApp() {
        return isFXApp;
    }

    public void setIsFXApp(boolean value) {
        isFXApp = value;
    }

    public void addFile(String file) {
        if (file != null) {
            files.add(file);
        }
    }

    public List<String> getFiles() {
        return files;
    }

    private boolean isResourceExists(JARDesc resource) {
        for (JARDesc r : resources) {
            if (r.getLocation().equals(resource.getLocation())) {
                return true;
            }
        }

        return false;
    }

    public void addResource(JARDesc resource) {
        if (resource != null) {
            if (isResourceExists(resource)) {
                Log.warning("Ignoring repeated resource " + resource.getLocation());
                return;
            }
            resources.add(resource);
        }
    }

    public List<JARDesc> getResources() {
        return resources;
    }

    public void addVMArg(String arg) {
        if (arg != null) {
            vmArgs.add(arg);
        }
    }

    public List<String> getVMArgs() {
        return vmArgs;
    }

    public void setIsLibrary(boolean isLibrary) {
        this.isLibrary = isLibrary;
    }

    public boolean isLibrary() {
        return isLibrary;
    }

    public void setIsInstaller(boolean isInstaller) {
        this.isInstaller = isInstaller;
    }

    public boolean isInstaller() {
        return isInstaller;
    }

    public void setIsJRESet(boolean isJRESet) {
        this.isJRESet = isJRESet;
    }

    public boolean isJRESet() {
        return isJRESet;
    }

    public void setResourcesDesc(ResourcesDesc resourcesDesc) {
        this.resourcesDesc = resourcesDesc;
    }

    public ResourcesDesc getResourcesDesc() {
        return resourcesDesc;
    }

    public void parseResourceDesc() throws Exception {
        if (resourcesDesc != null && !resourcesDesc.isEmpty()) {
            setMainJar(resourcesDesc.getMainJar().getName());

            JARDesc[] jars = resourcesDesc.getAllJarDescs();
            for (JARDesc jar : jars) {
                addResource(jar);
            }

            JREDesc jreDesc = resourcesDesc.getJreDesc();
            if (jreDesc != null) {
                String [] args = jreDesc.getVmArgsList();
                if (args != null) {
                    for (String arg : args) {
                        addVMArg(arg);
                    }
                }

                if (jreDesc.getMinHeap() != -1) {
                    addVMArg("-Xms" + jreDesc.getMinHeap());
                }

                if (jreDesc.getMaxHeap() != -1) {
                    addVMArg("-Xmx" + jreDesc.getMaxHeap());
                }
            }

            Properties props = resourcesDesc.getResourceProperties();
            Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                String value = props.getProperty(key);
                setProperty(key, value);
            }
        }
    }

    public static class InformationDesc {

        private final String _title;
        private final String _vendor;
        private final String[] _descriptions;
        private final IconDesc[] _icons;
        private ShortcutDesc _shortcutHints;
        private AssociationDesc[] _associations;

        public InformationDesc(String title, String vendor,
                String[] descriptions,
                IconDesc[] icons,
                ShortcutDesc shortcutHints,
                AssociationDesc[] associations) {
            _title = (title == null) ? "" : title;
            _vendor = (vendor == null) ? "" : vendor;
            if (descriptions == null) {
                descriptions = new String[NOF_DESC];
            }
            _descriptions = descriptions;
            _icons = icons;
            _shortcutHints = shortcutHints;
            _associations = associations;
        }

        /**
         * Constants for the getInfoDescription
         */
        final public static int DESC_DEFAULT = 0;
        final public static int DESC_SHORT = 1;
        final public static int DESC_ONELINE = 2;
        final public static int DESC_TOOLTIP = 3;
        final public static int NOF_DESC = 4;

        /**
         * Information
         */
        public String getTitle() {
            return _title;
        }

        public String getVendor() {
            return _vendor;
        }

        public IconDesc[] getIcons() {
            return _icons;
        }

        public ShortcutDesc getShortcut() {
            if (_shortcutHints == null) {
                return null;
            }
            return new ShortcutDesc(_shortcutHints.getDesktop(), _shortcutHints.getMenu(), _shortcutHints.getSubmenu());
        }

        public AssociationDesc[] getAssociations() {
            return _associations;
        }

        /**
         * Sets new shortcut hints.
         *
         * @param shortcutDesc the new shortcut hints to set
         */
        public void setShortcut(ShortcutDesc shortcut) {
            _shortcutHints = shortcut;
        }

        /**
         * Sets new associations.
         *
         * @param assoc the association to set
         */
        public void setAssociation(AssociationDesc assoc) {
            if (assoc == null) {
                _associations = null;
            } else {
                _associations = new AssociationDesc[]{assoc};
            }
        }

        /**
         * Returns the description of the given kind. will return null if none
         * there
         */
        public String getDescription(int kind) {
            return _descriptions[kind];
        }

        public String[] getDescription() {
            return _descriptions;
        }
    }

    public static class IconDesc {

        private final String _location;
        private String _localLocation;
        private final int _kind;

        final public static int ICON_KIND_DEFAULT = 0;
        final public static int ICON_KIND_SHORTCUT = 5;

        public IconDesc(URL location, int kind) {
            _location = location.toExternalForm();
            _kind = kind;
        }

        public String getLocation() {
            return _location;
        }

        public void setLocalLocation(String localLocation) {
            _localLocation = localLocation;
        }

        public String getLocalLocation() {
            return _localLocation;
        }

        public int getKind() {
            return _kind;
        }
    }

    public static class ShortcutDesc {

        private final boolean _desktop;
        private final boolean _menu;
        private final String _submenu;

        public ShortcutDesc(boolean desktop, boolean menu, String submenu) {
            _desktop = desktop;
            _menu = menu;
            _submenu = submenu;
        }

        public boolean getDesktop() {
            return _desktop;
        }

        public boolean getMenu() {
            return _menu;
        }

        public String getSubmenu() {
            return _submenu;
        }
    }

    public static class AssociationDesc {

        private final String _extensions;
        private final String _mimeType;
        private final String _description;
        private final String _icon;
        private String _iconLocalLocation;

        public AssociationDesc(String extensions, String mimeType, String description, URL icon) {
            _extensions = extensions;
            _mimeType = mimeType;
            _description = description;
            _icon = (icon != null) ? icon.toExternalForm() : null;
        }

        public void setIconLocalLocation(String localLocation) {
            _iconLocalLocation = localLocation;
        }

        public String getIconLocalLocation() {
            return _iconLocalLocation;
        }

        public String getExtensions() {
            return _extensions;
        }

        public String getMimeType() {
            return _mimeType;
        }

        public String getMimeDescription() {
            return _description;
        }

        public String getIconUrl() {
            return _icon;
        }
    }
}
