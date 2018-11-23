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

import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import jnlp.converter.JNLPConverter;
import jnlp.converter.parser.exception.MissingFieldException;
import jnlp.converter.parser.exception.BadFieldException;
import jnlp.converter.parser.exception.JNLParseException;
import jnlp.converter.parser.xml.XMLEncoding;
import jnlp.converter.parser.xml.XMLParser;
import jnlp.converter.parser.xml.XMLNode;
import jnlp.converter.parser.JNLPDesc.AssociationDesc;
import jnlp.converter.parser.JNLPDesc.IconDesc;
import jnlp.converter.parser.JNLPDesc.InformationDesc;
import jnlp.converter.parser.JNLPDesc.ShortcutDesc;
import jnlp.converter.parser.ResourcesDesc.JARDesc;
import jnlp.converter.parser.ResourcesDesc.JREDesc;
import jnlp.converter.Log;
import jnlp.converter.parser.ResourcesDesc.ExtensionDesc;
import jnlp.converter.parser.ResourcesDesc.PropertyDesc;
import org.xml.sax.SAXParseException;

public class XMLFormat {

    public static XMLNode parseBits(byte[] bits) throws JNLParseException {
        return parse(decode(bits));
    }

    private static String decode(byte[] bits) throws JNLParseException {
        try {
            return XMLEncoding.decodeXML(bits);
        } catch (Exception e) {
            throw new JNLParseException(e,
                "exception determining encoding of jnlp file", 0);
        }
    }

    private static XMLNode parse(String source) throws JNLParseException {
        try {
            return (new XMLParser(source).parse());
        } catch (SAXParseException spe) {
            throw new JNLParseException(spe,
                        "exception parsing jnlp file", spe.getLineNumber());
        } catch (Exception e) {
            throw new JNLParseException(e,
                        "exception parsing jnlp file", 0);
        }
    }

    /**
     * thisCodebase, if set, is used to determine the codebase,
     *     if JNLP codebase is not absolute.
     *
     * @param thisCodebase base URL of this JNLPDesc location
     */
    public static JNLPDesc parse(byte[] bits, URL thisCodebase, String jnlp)
            throws Exception {

        JNLPDesc jnlpd = new JNLPDesc();
        String source = decode(bits).trim();
        XMLNode root = parse(source);

        if (root == null || root.getName() == null) {
            throw new JNLParseException(null, null, 0);
        }

        // Check that root element is a <jnlp> tag
        if (!root.getName().equals("jnlp")) {
            throw (new MissingFieldException(source, "<jnlp>"));
        }

        // Read <jnlp> attributes (path is empty, i.e., "")
        // (spec, version, codebase, href)
        String specVersion = XMLUtils.getAttribute(root, "", "spec", "1.0+");
        jnlpd.setSpecVersion(specVersion);
        String version = XMLUtils.getAttribute(root, "", "version");
        jnlpd.setVersion(version);

        // Make sure the codebase URL ends with a '/'.
        //
        // Regarding the JNLP spec,
        // the thisCodebase is used to determine the codebase.
        //      codebase = new URL(thisCodebase, codebase)
        URL codebase = GeneralUtil.asPathURL(XMLUtils.getAttributeURL(source,
            thisCodebase, root, "", "codebase"));
        if (codebase == null && thisCodebase != null) {
            codebase = thisCodebase;
        }
        jnlpd.setCodebase(codebase.toExternalForm());

        // Get href for JNLP file
        URL href = XMLUtils.getAttributeURL(source, codebase, root, "", "href");
        jnlpd.setHref(href.toExternalForm());

        // Read <security> attributes
        if (XMLUtils.isElementPath(root, "<security><all-permissions>")) {
            jnlpd.setIsSandbox(false);
        } else if (XMLUtils.isElementPath(root,
                "<security><j2ee-application-client-permissions>")) {
            jnlpd.setIsSandbox(false);
        }

        // We can be fxapp, and also be applet, or application, or neither
        boolean isFXApp = false;
        boolean isApplet = false;
        if (XMLUtils.isElementPath(root, "<javafx-desc>")) {
            // no new type for javafx-desc - needs one of the others
            buildFXAppDesc(source, root, "<javafx-desc>", jnlpd);
            jnlpd.setIsFXApp(true);
            isFXApp = true;
        }

        /*
         * Note - the jnlp specification says there must be exactly one of
         * the descriptor types.  This code has always violated (or at least
         * not checked for) that condition.
         * Instead it uses precedent order app, component, installer, applet
         * and ignores any other descriptors given.
         */
        if (XMLUtils.isElementPath(root, "<application-desc>")) {
            buildApplicationDesc(source, root, jnlpd);
        } else if (XMLUtils.isElementPath(root, "<component-desc>")) {
            jnlpd.setIsLibrary(true);
        } else if (XMLUtils.isElementPath(root, "<installer-desc>")) {
            Log.warning("<installer-desc> is not supported and will be ignored in " + jnlp);
            jnlpd.setIsInstaller(true);
        } else if (XMLUtils.isElementPath(root, "<applet-desc>")) {
            isApplet = true;
        } else {
            if (!isFXApp) {
                throw (new MissingFieldException(source,
                    "<jnlp>(<application-desc>|<applet-desc>|" +
                    "<installer-desc>|<component-desc>)"));
            }
        }

        if (isApplet && !isFXApp) {
            Log.error("Applet based applications deployed with <applet-desc> element are not supported.");
        }

        if (!jnlpd.isLibrary() && !jnlpd.isInstaller()) {
            buildInformationDesc(source, codebase, root, jnlpd);
        }

        if (!jnlpd.isInstaller()) {
            buildResourcesDesc(source, codebase, root, false, jnlpd);
        }

        if (!jnlpd.isLibrary() && !jnlpd.isInstaller()) {
            jnlpd.parseResourceDesc();
        }

        if (!jnlpd.isInstaller()) {
            if (jnlpd.isSandbox()) {
                if (jnlpd.isLibrary()) {
                    Log.warning(jnlp + " is sandbox extension. JNLPConverter does not support sandbox environment and converted application will run without security manager.");
                } else {
                    Log.warning("This is sandbox Web-Start application. JNLPConverter does not support sandbox environment and converted application will run without security manager.");
                }
            }
        }

        return jnlpd;
    }

    /**
     * Create a combine informationDesc in the two informationDesc.
     * The information present in id1 overwrite the information present in id2
     */
    private static InformationDesc combineInformationDesc(
                                   InformationDesc id1, InformationDesc id2) {
        if (id1 == null) {
            return id2;
        }
        if (id2 == null) {
            return id1;
        }

        String t1 = id1.getTitle();
        String title  = (t1 != null && t1.length() > 0) ?
            t1 : id2.getTitle();
        String v1 = id1.getVendor();
        String vendor = (v1 != null && v1.length() > 0) ?
            v1 : id2.getVendor();

        /** Copy descriptions */
        String[] descriptions = new String[InformationDesc.NOF_DESC];
        for (int i = 0; i < descriptions.length; i++) {
            descriptions[i] = (id1.getDescription(i) != null)
                    ? id1.getDescription(i) : id2.getDescription(i);
        }

        /** Icons */
        ArrayList<IconDesc> iconList = new ArrayList<>();
        if (id2.getIcons() != null) {
            iconList.addAll(Arrays.asList(id2.getIcons()));
        }
        if (id1.getIcons() != null) {
            iconList.addAll(Arrays.asList(id1.getIcons()));
        }
        IconDesc[] icons = new IconDesc[iconList.size()];
        icons = iconList.toArray(icons);

        ShortcutDesc hints = (id1.getShortcut() != null) ?
                             id1.getShortcut() : id2.getShortcut();

        AssociationDesc[] asd = ( AssociationDesc[] ) addArrays(
            (Object[])id1.getAssociations(), (Object[])id2.getAssociations());

        return new InformationDesc(title,
                                   vendor,
                                   descriptions,
                                   icons,
                                   hints,
                                   asd);
    }

    /** Extract data from <information> tag */
    private static void buildInformationDesc(final String source, final URL codebase, XMLNode root, JNLPDesc jnlpd)
        throws MissingFieldException, BadFieldException {
        final ArrayList<InformationDesc> list = new ArrayList<>();

        // Iterates over all <information> nodes ignoring the type
        XMLUtils.visitElements(root,
            "<information>", new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode e) throws
                BadFieldException, MissingFieldException {

                // Check for right os, arch, and locale
                String[] os = GeneralUtil.getStringList(
                            XMLUtils.getAttribute(e, "", "os", null));
                String[] arch = GeneralUtil.getStringList(
                            XMLUtils.getAttribute(e, "", "arch", null));
                String[] locale = GeneralUtil.getStringList(
                            XMLUtils.getAttribute(e, "", "locale", null));
                if (GeneralUtil.prefixMatchStringList(
                                os, GeneralUtil.getOSFullName()) &&
                    GeneralUtil.prefixMatchArch(arch) &&
                    matchDefaultLocale(locale))
                {
                    // Title, vendor
                    String title = XMLUtils.getElementContents(e, "<title>");
                    String vendor = XMLUtils.getElementContents(e, "<vendor>");

                    // Descriptions
                    String[] descriptions =
                                new String[InformationDesc.NOF_DESC];
                    descriptions[InformationDesc.DESC_DEFAULT] =
                        XMLUtils.getElementContentsWithAttribute(
                        e, "<description>", "kind", "", null);
                    descriptions[InformationDesc.DESC_ONELINE] =
                        XMLUtils.getElementContentsWithAttribute(
                        e, "<description>", "kind", "one-line", null);
                    descriptions[InformationDesc.DESC_SHORT] =
                        XMLUtils.getElementContentsWithAttribute(
                        e, "<description>", "kind", "short", null);
                    descriptions[InformationDesc.DESC_TOOLTIP] =
                        XMLUtils.getElementContentsWithAttribute(
                        e, "<description>", "kind", "tooltip", null);

                    // Icons
                    IconDesc[] icons = getIconDescs(source, codebase, e);

                    // Shortcut hints
                    ShortcutDesc shortcuts = getShortcutDesc(e);

                    // Association hints
                    AssociationDesc[] associations = getAssociationDesc(
                                                        source, codebase, e);

                    list.add(new InformationDesc(
                        title, vendor, descriptions, icons,
                        shortcuts, associations));
                }
            }
        });

        /* Combine all information desc. information in a single one for
         * the current locale using the following priorities:
         *   1. locale == language_country_variant
         *   2. locale == lauguage_country
         *   3. locale == lauguage
         *   4. no or empty locale
         */
        InformationDesc normId = new InformationDesc(null, null, null, null, null, null);
        for (InformationDesc id : list) {
            normId = combineInformationDesc(id, normId);
        }

        jnlpd.setTitle(normId.getTitle());
        jnlpd.setVendor(normId.getVendor());
        jnlpd.setDescriptions(normId.getDescription());
        jnlpd.setIcons(normId.getIcons());
        jnlpd.setShortcuts(normId.getShortcut());
        jnlpd.setAssociations(normId.getAssociations());
    }

    private static Object[] addArrays (Object[] a1, Object[] a2) {
        if (a1 == null) {
            return a2;
        }
        if (a2 == null) {
            return a1;
        }
        ArrayList<Object> list = new ArrayList<>();
        int i;
        for (i=0; i<a1.length; list.add(a1[i++]));
        for (i=0; i<a2.length; list.add(a2[i++]));
        return list.toArray(a1);
    }

    public static boolean matchDefaultLocale(String[] localeStr) {
        return GeneralUtil.matchLocale(localeStr, GeneralUtil.getDefaultLocale());
    }

    /** Extract data from <resources> tag. There is only one. */
    static void buildResourcesDesc(final String source,
            final URL codebase, XMLNode root, final boolean ignoreJres, JNLPDesc jnlpd)
            throws MissingFieldException, BadFieldException {
        // Extract classpath directives
        final ResourcesDesc rdesc = new ResourcesDesc();

        // Iterate over all entries
        XMLUtils.visitElements(root, "<resources>",
                new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode e)
                    throws MissingFieldException, BadFieldException {
                // Check for right os, archictecture, and locale
                String[] os = GeneralUtil.getStringList(
                        XMLUtils.getAttribute(e, "", "os", null));
                final String arch = XMLUtils.getAttribute(e, "", "arch", null);
                String[] locale = GeneralUtil.getStringList(
                        XMLUtils.getAttribute(e, "", "locale", null));
                if (GeneralUtil.prefixMatchStringList(
                        os, GeneralUtil.getOSFullName())
                        && matchDefaultLocale(locale)) {
                    // Now visit all children in this node
                    XMLUtils.visitChildrenElements(e,
                            new XMLUtils.ElementVisitor() {
                        @Override
                        public void visitElement(XMLNode e2)
                                throws MissingFieldException, BadFieldException {
                            handleResourceElement(source, codebase,
                                    e2, rdesc, ignoreJres, arch, jnlpd);
                        }
                    });
                }
            }
        });

        if (!rdesc.isEmpty()) {
            jnlpd.setResourcesDesc(rdesc);
        }
    }

    private static IconDesc[] getIconDescs(final String source,
            final URL codebase, XMLNode e)
            throws MissingFieldException, BadFieldException {
        final ArrayList<IconDesc> answer = new ArrayList<>();
        XMLUtils.visitElements(e, "<icon>", new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode icon) throws
                    MissingFieldException, BadFieldException {
                String kindStr = XMLUtils.getAttribute(icon, "", "kind", "");
                URL href = XMLUtils.getRequiredURL(source, codebase, icon, "", "href");

                if (href != null) {
                    if (!JNLPConverter.isIconSupported(href.toExternalForm())) {
                        return;
                    }
                }

                int kind;
                if (kindStr == null || kindStr.isEmpty() || kindStr.equals("default")) {
                    kind = IconDesc.ICON_KIND_DEFAULT;
                } else if (kindStr.equals("shortcut")) {
                    kind = IconDesc.ICON_KIND_SHORTCUT;
                } else {
                    Log.warning("Ignoring unsupported icon \"" + href + "\" with kind \"" + kindStr + "\".");
                    return;
                }

                answer.add(new IconDesc(href, kind));
            }
        });
        return answer.toArray(new IconDesc[answer.size()]);
    }

    private static ShortcutDesc getShortcutDesc(XMLNode e)
                throws MissingFieldException, BadFieldException {
        final ArrayList<ShortcutDesc> shortcuts = new ArrayList<>();

        XMLUtils.visitElements(e, "<shortcut>", new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode shortcutNode)
                throws MissingFieldException, BadFieldException {
                boolean desktopHinted =
                    XMLUtils.isElementPath(shortcutNode, "<desktop>");
                boolean menuHinted =
                    XMLUtils.isElementPath(shortcutNode, "<menu>");
                String submenuHinted =
                    XMLUtils.getAttribute(shortcutNode, "<menu>", "submenu");
                shortcuts.add(new ShortcutDesc(desktopHinted, menuHinted, submenuHinted));
            }
        });

        if (shortcuts.size() > 0) {
            return shortcuts.get(0);
        }
        return null;
    }

    private static AssociationDesc[] getAssociationDesc(final String source,
        final URL codebase, XMLNode e)
                throws MissingFieldException, BadFieldException {
        final ArrayList<AssociationDesc> answer = new ArrayList<>();
        XMLUtils.visitElements(e, "<association>",
            new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode node)
                throws MissingFieldException, BadFieldException {

                String extensions = XMLUtils.getAttribute(
                                       node, "", "extensions");

                String mimeType = XMLUtils.getAttribute(
                                       node, "", "mime-type");
                String description = XMLUtils.getElementContents(
                                        node, "<description>");

                URL icon = XMLUtils.getAttributeURL(
                                source, codebase, node, "<icon>", "href");

                if (!JNLPConverter.isIconSupported(icon.toExternalForm())) {
                    icon = null;
                }

                if (extensions == null && mimeType == null) {
                    throw new MissingFieldException(source,
                                 "<association>(<extensions><mime-type>)");
                } else if (extensions == null) {
                    throw new MissingFieldException(source,
                                     "<association><extensions>");
                } else if (mimeType == null) {
                    throw new MissingFieldException(source,
                                     "<association><mime-type>");
                }

                // don't support uppercase extension and mime-type on gnome.
                if ("gnome".equals(System.getProperty("sun.desktop"))) {
                    extensions = extensions.toLowerCase();
                    mimeType = mimeType.toLowerCase();
                }

                answer.add(new AssociationDesc(extensions, mimeType,
                                                description, icon));
            }
        });
        return answer.toArray(
                new AssociationDesc[answer.size()]);
    }

    /** Handle the individual entries in a resource desc */
    private static void handleResourceElement(String source, URL codebase,
        XMLNode e, ResourcesDesc rdesc, boolean ignoreJres, String arch, JNLPDesc jnlpd)
        throws MissingFieldException, BadFieldException {

        String tag = e.getName();

        boolean matchArch = GeneralUtil.prefixMatchArch(
            GeneralUtil.getStringList(arch));


        if (matchArch && (tag.equals("jar") || tag.equals("nativelib"))) {
            /*
             * jar/nativelib elements
             */
            URL href = XMLUtils.getRequiredURL(source, codebase, e, "", "href");
            String version = XMLUtils.getAttribute(e, "", "version", null);

            String mainStr = XMLUtils.getAttribute(e, "", "main");
            boolean isNativeLib = tag.equals("nativelib");

            boolean isMain = "true".equalsIgnoreCase(mainStr);

            JARDesc jd = new JARDesc(href, version, isMain, isNativeLib, rdesc);
            rdesc.addResource(jd);
        } else if (matchArch && tag.equals("property")) {
            /*
             *  property tag
             */
            String name  = XMLUtils.getRequiredAttribute(source, e, "", "name");
            String value = XMLUtils.getRequiredAttributeEmptyOK(
                    source, e, "", "value");

            rdesc.addResource(new PropertyDesc(name, value));
        } else if (matchArch && tag.equals("extension")) {
            URL href = XMLUtils.getRequiredURL(source, codebase, e, "", "href");
            String version = XMLUtils.getAttribute(e, "", "version", null);
            rdesc.addResource(new ExtensionDesc(href, version));
        } else if ((tag.equals("java") || tag.equals("j2se")) && !ignoreJres) {
            /*
             * j2se element
             */
            String version  =
                XMLUtils.getRequiredAttribute(source, e, "", "version");
            String minheapstr =
                XMLUtils.getAttribute(e, "", "initial-heap-size");
            String maxheapstr =
                XMLUtils.getAttribute(e, "", "max-heap-size");

            String vmargs =
                XMLUtils.getAttribute(e, "", "java-vm-args");

            if (jnlpd.isJRESet()) {
                if (vmargs == null) {
                    vmargs = "none";
                }
                Log.warning("Ignoring repeated element <" + tag + "> with version " + version +
                        " and java-vm-args: " + vmargs);
                return;
            }

            long minheap = GeneralUtil.heapValToLong(minheapstr);
            long maxheap = GeneralUtil.heapValToLong(maxheapstr);

            ResourcesDesc cbs = null;
            buildResourcesDesc(source, codebase, e, true, null);

            // JRE
            JREDesc jreDesc = new JREDesc(
                version,
                minheap,
                maxheap,
                vmargs,
                cbs,
                arch);

            rdesc.addResource(jreDesc);

            jnlpd.setIsJRESet(true);
        }
    }

    /** Extract data from the application-desc tag */
    private static void buildApplicationDesc(final String source,
        XMLNode root, JNLPDesc jnlpd) throws MissingFieldException, BadFieldException {

        String mainclass = XMLUtils.getClassName(source, root,
                           "<application-desc>", "main-class", false);
        String appType = XMLUtils.getAttribute(root, "<application-desc>",
                                               "type", "Java");
        String progressclass  = XMLUtils.getClassName(source, root,
                                "<application-desc>", "progress-class", false);
        if (progressclass != null && !progressclass.isEmpty()) {
            Log.warning("JNLPConverter does not support progress indication. \"" + progressclass + "\" will not be loaded and will be ignored.");
        }

        if (!("Java".equalsIgnoreCase(appType) ||
            "JavaFx".equalsIgnoreCase(appType))) {
            throw new BadFieldException(source, XMLUtils.getPathString(root) +
                "<application-desc>type", appType);
        }

        if ("JavaFx".equalsIgnoreCase(appType)) {
            jnlpd.setIsFXApp(true);
        }

        XMLUtils.visitElements(root, "<application-desc><argument>", new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode e) throws MissingFieldException, BadFieldException {
                String arg = XMLUtils.getElementContents(e, "", null);
                if (arg == null) {
                    throw new BadFieldException(source, XMLUtils.getPathString(e), "");
                }
                jnlpd.addArguments(arg);
            }
        });

        XMLUtils.visitElements(root, "<application-desc><param>",
            new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode e) throws MissingFieldException,
                BadFieldException {
                String pn = XMLUtils.getRequiredAttribute(
                            source, e, "", "name");
                String pv = XMLUtils.getRequiredAttributeEmptyOK(
                            source, e, "", "value");
                jnlpd.setProperty(pn, pv);
            }
        });
        jnlpd.setMainClass(mainclass, false);
    }

    /** Extract data from the javafx-desc tag */
    private static void buildFXAppDesc(final String source,
        XMLNode root, String element, JNLPDesc jnlpd)
             throws MissingFieldException, BadFieldException {
        String mainclass = XMLUtils.getClassName(source, root, element,
                                                 "main-class", true);
        String name = XMLUtils.getRequiredAttribute(source, root,
                                        "<javafx-desc>", "name");

        /* extract arguments */
        XMLUtils.visitElements(root, "<javafx-desc><argument>", new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode e) throws MissingFieldException, BadFieldException {
                String arg = XMLUtils.getElementContents(e, "", null);
                if (arg == null) {
                    throw new BadFieldException(source, XMLUtils.getPathString(e), "");
                }
                jnlpd.addArguments(arg);
            }
        });

        /* extract parameters */
        XMLUtils.visitElements(root, "<javafx-desc><param>",
            new XMLUtils.ElementVisitor() {
            @Override
            public void visitElement(XMLNode e) throws MissingFieldException,
                BadFieldException {
                String pn = XMLUtils.getRequiredAttribute(
                            source, e, "", "name");
                String pv = XMLUtils.getRequiredAttributeEmptyOK(
                            source, e, "", "value");
                jnlpd.setProperty(pn, pv);
            }
        });

        jnlpd.setMainClass(mainclass, true);
        jnlpd.setName(name);
    }
}
