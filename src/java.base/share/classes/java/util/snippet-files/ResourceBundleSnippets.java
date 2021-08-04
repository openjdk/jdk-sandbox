/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * Snippets used in ResourceBundleSnippets.
 */ 

final class ResourceBundleSnippets {
// @start region=snippet5 :
 // default (English language, United States)
 public class MyResources extends ResourceBundle {
     public Object handleGetObject(String key) {
         if (key.equals("okKey")) return "Ok";
         if (key.equals("cancelKey")) return "Cancel";
         return null;
     }

     public Enumeration<String> getKeys() {
         return Collections.enumeration(keySet());
     }

     // Overrides handleKeySet() so that the getKeys() implementation
     // can rely on the keySet() value.
     protected Set<String> handleKeySet() {
         return new HashSet<String>(Arrays.asList("okKey", "cancelKey"));
     }
 }

 // German language
 public class MyResources_de extends MyResources {
     public Object handleGetObject(String key) {
         // don't need okKey, since parent level handles it.
         if (key.equals("cancelKey")) return "Abbrechen";
         return null;
     }

     protected Set<String> handleKeySet() {
         return new HashSet<String>(Arrays.asList("cancelKey"));
     }
 }
 
// @end snippet5

// @start region=snippet14 :
 ResourceBundle rb = ResourceBundle.getBundle("Messages",
     new ResourceBundle.Control() {
         public List<String> getFormats(String baseName) {
             if (baseName == null)
                 throw new NullPointerException();
             return Arrays.asList("xml");
         }
         public ResourceBundle newBundle(String baseName,
                                         Locale locale,
                                         String format,
                                         ClassLoader loader,
                                         boolean reload)
                          throws IllegalAccessException,
                                 InstantiationException,
                 IOException {
             if (baseName == null || locale == null
                   || format == null || loader == null)
                 throw new NullPointerException();
             ResourceBundle bundle = null;
             if (format.equals("xml")) {
                 String bundleName = toBundleName(baseName, locale);
                 String resourceName = toResourceName(bundleName, format);
                 InputStream stream = null;
                 if (reload) {
                     URL url = loader.getResource(resourceName);
                     if (url != null) {
                         URLConnection connection = url.openConnection();
                         if (connection != null) {
                             // Disable caches to get fresh data for
                             // reloading.
                             connection.setUseCaches(false);
                             stream = connection.getInputStream();
                         }
                     }
                 } else {
                     stream = loader.getResourceAsStream(resourceName);
                 }
                 if (stream != null) {
                     BufferedInputStream bis = new BufferedInputStream(stream);
                     bundle = new XMLResourceBundle(bis);
                     bis.close();
                 }
             }
             return bundle;
         }
     });

    ////@replace regex="//" replacement="..."


    private static class XMLResourceBundle extends ResourceBundle {
     private Properties props;
     XMLResourceBundle(InputStream stream) throws IOException {
         props = new Properties();
         props.loadFromXML(stream);
     }
     protected Object handleGetObject(String key) {
         return props.getProperty(key);
     }
     public Enumeration<String> getKeys() {
         return null;//@replace regex="return null;" replacement="..."
     }
 }
 
// @end snippet14

}
