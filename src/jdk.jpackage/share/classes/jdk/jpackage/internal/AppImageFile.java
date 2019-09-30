/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static jdk.jpackage.internal.StandardBundlerParam.*;

class AppImageFile {

    // These values will be loaded from AppImage xml file.
    private final String creatorVersion;
    private final String creatorPlatform;
    private final String launcherName;
    private final List<String> addLauncherNames;
    
    final static String XML_FILENAME = ".jpackage.xml";
    
    private final static Map<Platform, String> PLATFORM_LABELS = Map.of(
            Platform.LINUX, "linux", Platform.WINDOWS, "windows", Platform.MAC,
            "macOS");
    

    private AppImageFile() {
        this(null, null, null, null);
    }
    
    private AppImageFile(String launcherName, List<String> addLauncherNames,
            String creatorVersion, String creatorPlatform) {
        this.launcherName = launcherName;
        this.addLauncherNames = addLauncherNames;
        this.creatorVersion = creatorVersion;
        this.creatorPlatform = creatorPlatform;
    }

    /**
     * Would return null to indicate stored command line is invalid.
     */
    List<String> getAddLauncherNames() {
        return addLauncherNames;
    }

    String getLauncherName() {
        return launcherName;
    }
    
    void verifyCompatible() throws ConfigException {
        // Just do nohing for now.
    }

    static void save(Path appImage, Map<String, Object> params)
            throws IOException {
        Path xmlFile = appImage.resolve(XML_FILENAME);
        XMLOutputFactory xmlFactory = XMLOutputFactory.newInstance();

        try (Writer w = new BufferedWriter(new FileWriter(xmlFile.toFile()))) {
            XMLStreamWriter xml = xmlFactory.createXMLStreamWriter(w);

            xml.writeStartDocument();
            xml.writeStartElement("jpackage-state");
            xml.writeAttribute("version", getVersion());
            xml.writeAttribute("platform", getPlatform());
            
            xml.writeStartElement("main-launcher");
            xml.writeCharacters(APP_NAME.fetchFrom(params));
            xml.writeEndElement();
            
            List<Map<String, ? super Object>> addLaunchers =
                ADD_LAUNCHERS.fetchFrom(params);

            for (int i = 0; i < addLaunchers.size(); i++) {
                Map<String, ? super Object> sl = addLaunchers.get(i);
                xml.writeStartElement("add-launcher");
                xml.writeCharacters(APP_NAME.fetchFrom(sl));
                xml.writeEndElement();
            }
            
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            xml.close();

        } catch (XMLStreamException ex) {
            Log.verbose(ex);
            throw new IOException(ex);
        }
    }

    static AppImageFile load(Path appImageDir) throws IOException {
        try {
            Path path = appImageDir.resolve(XML_FILENAME);
            DocumentBuilderFactory dbf =
                    DocumentBuilderFactory.newDefaultInstance();
            dbf.setFeature(
                   "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            DocumentBuilder b = dbf.newDocumentBuilder();
            Document doc = b.parse(new FileInputStream(path.toFile()));

            XPath xPath = XPathFactory.newInstance().newXPath();
            
            String mainLauncher = xpathQueryNullable(xPath,
                    "/jpackage-state/main-launcher/text()", doc);
            if (mainLauncher == null) {
                // No main launcher, this is fatal.
                return new AppImageFile();
            }

            List<String> addLaunchers = new ArrayList<String>();

            String platform = xpathQueryNullable(xPath,
                    "/jpackage-state/@platform", doc);

            String version = xpathQueryNullable(xPath,
                    "/jpackage-state/@version", doc);
            
            NodeList launcherNameNodes = (NodeList) xPath.evaluate(
                    "/jpackage-state/add-launcher/text()", doc,
                    XPathConstants.NODESET);

            for (int i = 0; i != launcherNameNodes.getLength(); i++) {
                addLaunchers.add(launcherNameNodes.item(i).getNodeValue());
            }
            
            AppImageFile file = new AppImageFile(
                    mainLauncher, addLaunchers, version, platform);
            if (!file.isValid()) {
                file = new AppImageFile();
            }
            return file;       
        } catch (ParserConfigurationException | SAXException ex) {
            // Let caller sort this out
            throw new IOException(ex);
        } catch (XPathExpressionException ex) {
            // This should never happen as XPath expressions should be correct
            throw new RuntimeException(ex);
        }
    }
    
    private static String xpathQueryNullable(XPath xPath, String xpathExpr,
            Document xml) throws XPathExpressionException {
        NodeList nodes = (NodeList) xPath.evaluate(xpathExpr, xml,
                XPathConstants.NODESET);
        if (nodes != null && nodes.getLength() > 0) {
            return nodes.item(0).getNodeValue();
        }
        return null;
    }

    private static String getVersion() {
        return System.getProperty("java.version");
    }
    
    private static String getPlatform() {
        return PLATFORM_LABELS.get(Platform.getPlatform());
    }
    
    private boolean isValid() {
        if (launcherName == null || launcherName.length() == 0 ||
            addLauncherNames.indexOf("") != -1) {
            // Some launchers have empty names. This is invalid.
            return false;
        }
        
        // Add more validation.
        
        return true;
    }
    
}
