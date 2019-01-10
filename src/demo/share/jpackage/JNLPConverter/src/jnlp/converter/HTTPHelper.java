/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jnlp.converter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jnlp.converter.parser.GeneralUtil;

public class HTTPHelper {

    public static final int BUFFER_SIZE = 4096;

    public static String downloadFile(String url, String destFolder, String destFileName) throws MalformedURLException, IOException {
        HttpURLConnection connection = null;
        String destFile = null;

        try {
            if (url.contains(" ")) {
                url = url.replace(" ", "%20");
            }
            if (url.contains("\\")) {
                url = url.replace("\\", "/");
            }

            URL resource = new URL(url);
            connection = (HttpURLConnection) resource.openConnection();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                destFile = destFolder + File.separator + destFileName;
                Log.verbose("Downloading " + url + " to " + destFile);

                try (InputStream inputStream = connection.getInputStream();
                     OutputStream outputStream = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];

                    int length;
                    do {
                        length = inputStream.read(buffer);
                        if (length > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    } while (length > 0);
                }
            } else {
                HTTPHelperException e = new HTTPHelperException("Error: Cannot download " + url + ". Server response code: " + responseCode);
                e.setResponseCode(responseCode);
                throw e;
            }
        } catch (IOException e) {
            if (e instanceof HTTPHelperException) {
                throw e;
            } else {
                throw new HTTPHelperException("Error: Cannot download " + url + ". " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return destFile;
    }

    public static String copyFile(String url, String destFolder, String destFileName) throws Exception {
        if (url.contains(" ")) {
            url = url.replace(" ", "%20");
        }

        URI sourceURI = new URI(url);

        String sourceFile = sourceURI.getPath();
        File file = new File(sourceFile);
        if (!file.exists()) {
            throw new FileNotFoundException("Error: " + sourceFile + " does not exist.");
        }

        String destFile = destFolder + File.separator + destFileName;
        file = new File(destFile);
        if (file.exists()) {
            file.delete();
        }

        Path sourcePath = Paths.get(sourceURI);
        Path destPath = Paths.get(destFile);
        Log.verbose("Copying " + url + " to " + destFile);
        Files.copy(sourcePath, destPath);

        return destFile;
    }

    public static boolean isHTTPUrl(String url) {
        return (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static byte[] getJNLPBits(String versionedJNLP, String jnlp) throws Exception {
        String jnlpFilePath = null;
        byte[] bits = null;

        if (isHTTPUrl(jnlp)) {
            try {
                jnlpFilePath = downloadFile(versionedJNLP, JNLPConverter.getJnlpDownloadFolderStatic(), getFileNameFromURL(jnlp));
            } catch (HTTPHelperException ex) {
                if (ex.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND &&
                       !versionedJNLP.equals(jnlp)) {
                    Log.warning("Downloading versioned JNLP from " + versionedJNLP + " failed.");
                    Log.warning(ex.getMessage());
                    Log.warning("Downloading " + jnlp + " instead.");
                    jnlpFilePath = downloadFile(jnlp, JNLPConverter.getJnlpDownloadFolderStatic(), getFileNameFromURL(jnlp));
                } else {
                    throw ex;
                }
            }
            JNLPConverter.markFileToDelete(jnlpFilePath);
        } else {
            try {
                jnlpFilePath = copyFile(versionedJNLP, JNLPConverter.getJnlpDownloadFolderStatic(), getFileNameFromURL(jnlp));
            } catch (FileNotFoundException ex) {
                System.out.println("Error copying versioned JNLP from " + versionedJNLP);
                System.out.println(ex.getMessage());
                System.out.println("Copying " + jnlp + " instead.");
                jnlpFilePath = HTTPHelper.copyFile(jnlp, JNLPConverter.getJnlpDownloadFolderStatic(), getFileNameFromURL(jnlp));
            }
            JNLPConverter.markFileToDelete(jnlpFilePath);
        }

        File jnlpFile = new File(jnlpFilePath);
        if (jnlpFile.exists()) {
            bits = GeneralUtil.readBytes(new FileInputStream(jnlpFile), jnlpFile.length());
        }

        return bits;
    }

    public static String getFileNameFromURL(String url) throws IOException {
        int index;
        int index1 = url.lastIndexOf('/');
        int index2 = url.lastIndexOf('\\');

        if (index1 >= index2) {
            index = index1;
        } else {
            index = index2;
        }

        if (index != -1) {
            String name = url.substring(index + 1, url.length());
            name = name.replace("%20", " ");
            if (name.endsWith(".jnlp") || name.endsWith(".jar")) { // JNLP or JAR
                return name;
            } else if (name.endsWith(".ico")) { // Icons
                return name;
            } else {
                throw new IOException("Error: Unsupported file extension for " + url);
            }
        } else {
            throw new IOException("Error: URL (" + url + ") should end with file name.");
        }
    }
}
