/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.micro.jdk.java.util.zip;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 *
 * @author sfriberg
 */
@State(Scope.Benchmark)
public class ZipFileDecompression {

    public static enum FILES {

        small_txt,
        large_txt,
        very_large_txt,
        small_class,
        large_class,
        large_bin,
        stored_file;
    }

    @Param
    private FILES compressedFile;

    private ZipFile zipFile;

    private final Map<FILES, byte[]> compressedFiles = new HashMap<>();

    // Thread private reusable buffers
    @State(Scope.Thread)
    public static class ThreadLocalBuffers {

        final byte[] bytes = new byte[10 * 1024 * 1024];
    }

    /**
     * Create ZIP file used in benchmark
     * 
     * @throws IOException
     */
    @Setup
    public void setup() throws IOException {

        File file = File.createTempFile(this.getClass().getSimpleName(), ".zip");
        file.deleteOnExit();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file));
                ByteArrayOutputStream baos = new ByteArrayOutputStream(50 * text.length)) {

            // Size of entries in bytes
            //  small_txt      csize 264     size 445
            //  large_txt      csize 282     size 2225
            //  very_large_txt csize 399     size 22250
            //  small_class    csize 418     size 982
            //  large_class    csize 4351    size 7702
            //  large_bin      csize 1048896 size 1048576
            //  stored_file    csize 2053    size 2048
            writeBytes(zos, FILES.small_txt, text);

            for (int i = 0; i < 5; i++) {
                baos.write(text);
            }
            writeBytes(zos, FILES.large_txt, baos.toByteArray());
            baos.reset();

            for (int i = 0; i < 50; i++) {
                baos.write(text);
            }
            writeBytes(zos, FILES.very_large_txt, baos.toByteArray());
            baos.reset();

            writeBytes(zos, FILES.small_class, smallKlass);

            writeBytes(zos, FILES.large_class, largeKlass);

            byte[] largeBinBytes = new byte[1024 * 1024];
            new Random(543210).nextBytes(largeBinBytes);
            writeBytes(zos, FILES.large_bin, largeBinBytes);

            // No compression on this entry
            zos.setLevel(ZipOutputStream.STORED);
            byte[] storedBytes = new byte[2 * 1024];
            new Random(543210).nextBytes(storedBytes);
            writeBytes(zos, FILES.stored_file, storedBytes);
        }

        zipFile = new ZipFile(file);

        verifyZipFile();
    }

    private void writeBytes(ZipOutputStream zos, FILES file, byte[] bytes) throws IOException {
        compressedFiles.put(file, bytes); // Save for verification
        zos.putNextEntry(new ZipEntry(file.name()));
        zos.write(bytes);
        zos.closeEntry();
    }

    @TearDown
    public void teardown() throws IOException {
        verifyZipFile();
        zipFile.close();
    }

    private void verifyZipFile() {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        int count = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            try {
                FILES filename = FILES.valueOf(entry.getName());
                byte[] extractedFile = new byte[(int) entry.getSize()];
                readFully(zipFile.getInputStream(entry), entry.getSize(), extractedFile);
                if (!Arrays.equals(compressedFiles.get(filename), extractedFile)) {
                    throw new IllegalStateException("Uncompressed file differs from file that was compressed file " + entry.getName());
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Error reading Zip " + zipFile.getName());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Generated ZIP should not contain " + entry.getName());
            }
            count++;
        }
        if (count != FILES.values().length) {
            throw new IllegalStateException("Generated ZIP file does not contain all expected files");
        }
    }

    @Benchmark
    public void zipEntryInputStream(ThreadLocalBuffers tbb) throws IOException {
        ZipEntry entry = zipFile.getEntry(compressedFile.name());
        readFully(zipFile.getInputStream(entry), entry.getSize(), tbb.bytes);
    }

    @Benchmark
    public void jarURLEntryInputStream(ThreadLocalBuffers tbb) throws IOException {
        URL url = new URL("jar:file:" + zipFile.getName() + "!/" + compressedFile.name());
        JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
        readFully(jarConnection.getInputStream(), jarConnection.getContentLengthLong(), tbb.bytes);
    }

    private int readFully(InputStream stream, long len, byte[] bytes) throws IOException {
        if (len > bytes.length) {
            throw new IllegalStateException("byte[] too small to read stream");
        }
        int nread = 0, n = 0;
        while (nread < len && (n = stream.read(bytes, nread, bytes.length - nread)) > 0) {
            nread += n;
        }
        return nread;
    }

    // Data for ZIP file creation
    private final byte[] text
            = ("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor "
            + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis "
            + "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. "
            + "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore "
            + "eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt "
            + "in culpa qui officia deserunt mollit anim id est laborum.").getBytes();
    private final byte[] smallKlass = Base64.getDecoder().decode(
            "yv66vgAAADQAJgcAIgcAIwcAJAEAA2FkZAEAFShMamF2YS9sYW5nL09iamVjdDspWgEACVNpZ25h"
            + "dHVyZQEABihURTspWgEABW9mZmVyAQADcHV0AQAVKExqYXZhL2xhbmcvT2JqZWN0OylWAQAKRXhj"
            + "ZXB0aW9ucwcAJQEABihURTspVgEANShMamF2YS9sYW5nL09iamVjdDtKTGphdmEvdXRpbC9jb25j"
            + "dXJyZW50L1RpbWVVbml0OylaAQAmKFRFO0pMamF2YS91dGlsL2NvbmN1cnJlbnQvVGltZVVuaXQ7"
            + "KVoBAAR0YWtlAQAUKClMamF2YS9sYW5nL09iamVjdDsBAAUoKVRFOwEABHBvbGwBADQoSkxqYXZh"
            + "L3V0aWwvY29uY3VycmVudC9UaW1lVW5pdDspTGphdmEvbGFuZy9PYmplY3Q7AQAlKEpMamF2YS91"
            + "dGlsL2NvbmN1cnJlbnQvVGltZVVuaXQ7KVRFOwEAEXJlbWFpbmluZ0NhcGFjaXR5AQADKClJAQAG"
            + "cmVtb3ZlAQAIY29udGFpbnMBAAdkcmFpblRvAQAZKExqYXZhL3V0aWwvQ29sbGVjdGlvbjspSQEA"
            + "HyhMamF2YS91dGlsL0NvbGxlY3Rpb248LVRFOz47KUkBABooTGphdmEvdXRpbC9Db2xsZWN0aW9u"
            + "O0kpSQEAIChMamF2YS91dGlsL0NvbGxlY3Rpb248LVRFOz47SSlJAQA+PEU6TGphdmEvbGFuZy9P"
            + "YmplY3Q7PkxqYXZhL2xhbmcvT2JqZWN0O0xqYXZhL3V0aWwvUXVldWU8VEU7PjsBAApTb3VyY2VG"
            + "aWxlAQASQmxvY2tpbmdRdWV1ZS5qYXZhAQAiamF2YS91dGlsL2NvbmN1cnJlbnQvQmxvY2tpbmdR"
            + "dWV1ZQEAEGphdmEvbGFuZy9PYmplY3QBAA9qYXZhL3V0aWwvUXVldWUBAB5qYXZhL2xhbmcvSW50"
            + "ZXJydXB0ZWRFeGNlcHRpb24GAQABAAIAAQADAAAACwQBAAQABQABAAYAAAACAAcEAQAIAAUAAQAG"
            + "AAAAAgAHBAEACQAKAAIACwAAAAQAAQAMAAYAAAACAA0EAQAIAA4AAgALAAAABAABAAwABgAAAAIA"
            + "DwQBABAAEQACAAsAAAAEAAEADAAGAAAAAgASBAEAEwAUAAIACwAAAAQAAQAMAAYAAAACABUEAQAW"
            + "ABcAAAQBABgABQAABAEAGQAFAAAEAQAaABsAAQAGAAAAAgAcBAEAGgAdAAEABgAAAAIAHgACAAYA"
            + "AAACAB8AIAAAAAIAIQ==");
    private final byte[] largeKlass = Base64.getDecoder().decode(
            "yv66vgAAADQBWAoAngDvBwDwCgACAPEKAAIA8gcA8woAAgD0CgACAPUKAAUA9goAAgD3A4AAAAAK"
            + "AAUA+AoABQD5CgACAPoKAPsA8QoAAgD8CgAFAP0KAAUA/goABQD/Bf/////////kCgACAQAKAAIB"
            + "AQoAAgECCgAFAQMKAAUBBAoAAgEFCgAFAQYKAPsBBwoA+wEICgD7AQkFAAAAAAAAAAwFAAAAAAAA"
            + "AA0HAQoHAQsKACQA7wgBDAoAJAENCgAkAQ4KACQBDwoAIwEQCQAFAREKAPsA8goA+wD0CgAFARIJ"
            + "AAUBEwkABQEUCgACARUKAAIBFgkABQEXCgACARgFAAAAAAAAAW0FAAAAAAAAAAQFAAAAAAAAAGQF"
            + "AAAAAAAAAZAKARkBGgoBGQEbBQAAAAAAAAACCgACARwKAAIBHQoABQEeBQAAAAAAAAAfBQAAAAAA"
            + "AAAcCgAFAR8JAAUBIAcBIQgBIgoASgEjCgACAQcKAAUBJAUAAAAAAAAABwoBGQElBQAAAAAAAjqx"
            + "AwAAjqwKARkBJgoBGQEnCgEoASkDAAr5OwMACvqoAwAK/BUDAAr9gwMACv7wAwALAF0DAAsBygMA"
            + "CwM4AwALBKUDAAsGEgMACwd/AwALCO0DAAsKWgMACwvHAwALDTQDAAsOogMACxAPAwALEXwDAAsS"
            + "6QMACxRXAwALFcQDAAsXMQMACxieAwALGgwDAAsbeQMACxzmAwALHlMDAAsfwQMACyEuAwALIpsD"
            + "AAskCAMACyV2AwALJuMDAAsoUAMACym9AwALKysDAAssmAMACy4FAwALL3IDAAsw4AMACzJNAwAL"
            + "M7oDAAs1JwMACzaVAwALOAIDAAs5bwMACzrcAwALPEoDAAs9twMACz8kAwALQJEDAAtB/wMAC0Ns"
            + "AwALRNkDAAtGRgMAC0e0AwALSSEDAAtKjgMAC0v7AwALTWkDAAtO1gMAC1BDAwALUbADAAtTHgMA"
            + "C1SLAwALVfgDAAtXZQMAC1jTAwALWkADAAtbrQcBKgEABERhdGUBAAxJbm5lckNsYXNzZXMBAAdK"
            + "QU5VQVJZAQABSQEADUNvbnN0YW50VmFsdWUDAAAAAQEACEZFQlJVQVJZAwAAAAIBAAVNQVJDSAMA"
            + "AAADAQAFQVBSSUwDAAAABAEAA01BWQMAAAAFAQAESlVORQMAAAAGAQAESlVMWQMAAAAHAQAGQVVH"
            + "VVNUAwAAAAgBAAlTRVBURU1CRVIDAAAACQEAB09DVE9CRVIDAAAACgEACE5PVkVNQkVSAwAAAAsB"
            + "AAhERUNFTUJFUgMAAAAMAQAGU1VOREFZAQAGTU9OREFZAQAHVFVFU0RBWQEACVdFRE5FU0RBWQEA"
            + "CFRIVVJTREFZAQAGRlJJREFZAQAIU0FUVVJEQVkBAAlCQVNFX1lFQVIDAAAHsgEAC0ZJWEVEX0RB"
            + "VEVTAQACW0kBAA1EQVlTX0lOX01PTlRIAQAZQUNDVU1VTEFURURfREFZU19JTl9NT05USAEAHkFD"
            + "Q1VNVUxBVEVEX0RBWVNfSU5fTU9OVEhfTEVBUAEAEyRhc3NlcnRpb25zRGlzYWJsZWQBAAFaAQAG"
            + "PGluaXQ+AQADKClWAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEACHZhbGlkYXRlAQAjKExzdW4v"
            + "dXRpbC9jYWxlbmRhci9DYWxlbmRhckRhdGU7KVoBAA1TdGFja01hcFRhYmxlBwDwAQAJbm9ybWFs"
            + "aXplBwErBwDzBwEsAQAObm9ybWFsaXplTW9udGgBACMoTHN1bi91dGlsL2NhbGVuZGFyL0NhbGVu"
            + "ZGFyRGF0ZTspVgEADWdldFllYXJMZW5ndGgBACMoTHN1bi91dGlsL2NhbGVuZGFyL0NhbGVuZGFy"
            + "RGF0ZTspSQEAFWdldFllYXJMZW5ndGhJbk1vbnRocwEADmdldE1vbnRoTGVuZ3RoAQAFKElJKUkB"
            + "AAxnZXREYXlPZlllYXIBACMoTHN1bi91dGlsL2NhbGVuZGFyL0NhbGVuZGFyRGF0ZTspSgEABihJ"
            + "SUkpSgEADGdldEZpeGVkRGF0ZQEAKyhJSUlMc3VuL3V0aWwvY2FsZW5kYXIvQmFzZUNhbGVuZGFy"
            + "JERhdGU7KUoBABxnZXRDYWxlbmRhckRhdGVGcm9tRml4ZWREYXRlAQAkKExzdW4vdXRpbC9jYWxl"
            + "bmRhci9DYWxlbmRhckRhdGU7SilWAQAMZ2V0RGF5T2ZXZWVrAQAZZ2V0RGF5T2ZXZWVrRnJvbUZp"
            + "eGVkRGF0ZQEABChKKUkBABRnZXRZZWFyRnJvbUZpeGVkRGF0ZQEAHWdldEdyZWdvcmlhblllYXJG"
            + "cm9tRml4ZWREYXRlAQAKaXNMZWFwWWVhcgEABChJKVoBAAg8Y2xpbml0PgEAClNvdXJjZUZpbGUB"
            + "ABFCYXNlQ2FsZW5kYXIuamF2YQwAywDMAQAjc3VuL3V0aWwvY2FsZW5kYXIvQmFzZUNhbGVuZGFy"
            + "JERhdGUMAS0BLgwBLwEwAQAec3VuL3V0aWwvY2FsZW5kYXIvQmFzZUNhbGVuZGFyDAExATAMATIB"
            + "MAwA3ADdDADlATAMAOUA2gwBMwDQDAE0ATUHASwMATYBNwwBOADfDAE5ANoMANcA2AwBOgE7DAE8"
            + "AT0MAT4BOwwA4QDiDADjAOQMAT8BPQwA6gDrDAFAATUMAUEBPQwBQgE9AQAiamF2YS9sYW5nL0ls"
            + "bGVnYWxBcmd1bWVudEV4Y2VwdGlvbgEAF2phdmEvbGFuZy9TdHJpbmdCdWlsZGVyAQAVSWxsZWdh"
            + "bCBtb250aCB2YWx1ZTogDAFDAUQMAUMBRQwBRgFHDADLAUgMAMYAxQwA3gDgDADIAMUMAMcAxQwB"
            + "SQDrDAFKAUsMAMQAxQwBTAFNBwFODAFPAVAMAU8A3QwBSQFRDAFSATAMAOkA5wwA5gDnDADJAMoB"
            + "ABhqYXZhL2xhbmcvQXNzZXJ0aW9uRXJyb3IBABVuZWdhdGl2ZSBkYXkgb2Ygd2VlayAMAMsBUwwA"
            + "4QDfDAFUAVAMAVQA3QwBVQDrBwFWDAFXAS4BACJzdW4vdXRpbC9jYWxlbmRhci9BYnN0cmFjdENh"
            + "bGVuZGFyAQASamF2YS91dGlsL1RpbWVab25lAQAec3VuL3V0aWwvY2FsZW5kYXIvQ2FsZW5kYXJE"
            + "YXRlAQAMaXNOb3JtYWxpemVkAQADKClaAQAIZ2V0TW9udGgBAAMoKUkBAA1nZXREYXlPZk1vbnRo"
            + "AQARZ2V0Tm9ybWFsaXplZFllYXIBAAx2YWxpZGF0ZVRpbWUBAA1zZXROb3JtYWxpemVkAQAEKFop"
            + "VgEAB2dldFpvbmUBABYoKUxqYXZhL3V0aWwvVGltZVpvbmU7AQAHZ2V0VGltZQEADW5vcm1hbGl6"
            + "ZVRpbWUBAA1zZXREYXlPZk1vbnRoAQAjKEkpTHN1bi91dGlsL2NhbGVuZGFyL0NhbGVuZGFyRGF0"
            + "ZTsBABFzZXROb3JtYWxpemVkWWVhcgEABChJKVYBAAhzZXRNb250aAEADHNldERheU9mV2VlawEA"
            + "C3NldExlYXBZZWFyAQANc2V0Wm9uZU9mZnNldAEAEXNldERheWxpZ2h0U2F2aW5nAQAGYXBwZW5k"
            + "AQAtKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL1N0cmluZ0J1aWxkZXI7AQAcKEkpTGph"
            + "dmEvbGFuZy9TdHJpbmdCdWlsZGVyOwEACHRvU3RyaW5nAQAUKClMamF2YS9sYW5nL1N0cmluZzsB"
            + "ABUoTGphdmEvbGFuZy9TdHJpbmc7KVYBAANoaXQBAA1nZXRDYWNoZWRKYW4xAQADKClKAQAIc2V0"
            + "Q2FjaGUBAAYoSUpJKVYBAB9zdW4vdXRpbC9jYWxlbmRhci9DYWxlbmRhclV0aWxzAQALZmxvb3JE"
            + "aXZpZGUBAAUoSkopSgEABChKKVoBAA1nZXRDYWNoZWRZZWFyAQAVKExqYXZhL2xhbmcvT2JqZWN0"
            + "OylWAQADbW9kAQATaXNHcmVnb3JpYW5MZWFwWWVhcgEAD2phdmEvbGFuZy9DbGFzcwEAFmRlc2ly"
            + "ZWRBc3NlcnRpb25TdGF0dXMEIQAFAJ4AAAAZABkAoQCiAAEAowAAAAIApAAZAKUAogABAKMAAAAC"
            + "AKYAGQCnAKIAAQCjAAAAAgCoABkAqQCiAAEAowAAAAIAqgAZAKsAogABAKMAAAACAKwAGQCtAKIA"
            + "AQCjAAAAAgCuABkArwCiAAEAowAAAAIAsAAZALEAogABAKMAAAACALIAGQCzAKIAAQCjAAAAAgC0"
            + "ABkAtQCiAAEAowAAAAIAtgAZALcAogABAKMAAAACALgAGQC5AKIAAQCjAAAAAgC6ABkAuwCiAAEA"
            + "owAAAAIApAAZALwAogABAKMAAAACAKYAGQC9AKIAAQCjAAAAAgCoABkAvgCiAAEAowAAAAIAqgAZ"
            + "AL8AogABAKMAAAACAKwAGQDAAKIAAQCjAAAAAgCuABkAwQCiAAEAowAAAAIAsAAaAMIAogABAKMA"
            + "AAACAMMAGgDEAMUAAAAYAMYAxQAAABgAxwDFAAAAGADIAMUAABAYAMkAygAAABQAAQDLAMwAAQDN"
            + "AAAAIQABAAEAAAAFKrcAAbEAAAABAM4AAAAKAAIAAAAnAAQAjwABAM8A0AABAM0AAADWAAQABgAA"
            + "AGUrwAACTSy2AAOZAAUErCy2AAQ+HQShAAkdEAykAAUDrCy2AAY2BBUEngARFQQqLLYABx23AAik"
            + "AAUDrCy2AAk2BRUFEgqfAA8VBSostgALnwAFA6wqK7YADJoABQOsLAS2AA0ErAAAAAIAzgAAAEIA"
            + "EAAAAMAABQDBAAwAwgAOAMQAEwDFAB4AxgAgAMgAJgDJADkAygA7AMwAQQDNAFIAzgBUANEAXADS"
            + "AF4A1QBjANYA0QAAABcAB/wADgcA0vwADwEB/AAYAQH8ABgBCQABANMA0AABAM0AAAIJAAcADAAA"
            + "ASkrtgAOmQAFBKwrwAACTSy2AA9OLcYACyortgAQWASsKiy2ABE2BCostgASLLYABoUVBIVhNwUs"
            + "tgAENgcstgAHNggqFQgVB7cACDYJFgUJlJ4ADBYFFQmFlJ4AqhYFCZSdAEQWBRQAE5SeADsqFQiE"
            + "B/8VB7cACDYJFgUVCYVhNwUsFgWItgAVVxUHmgAPEAw2BywVCARktgAWLBUHtgAXV6cAaxYFFQmF"
            + "lJ4APhYFFQkQHGCFlJwAMhYFFQmFZTcFhAcBLBYFiLYAFVcVBxAMpAAOLBUIBGC2ABYENgcsFQe2"
            + "ABdXpwAnFgUqFQgVBwQstgAYYQplNwoqLBYKtgAZpwAMLCostgALtgAaKyostgAHtgAbtgAcKwO2"
            + "AB0rA7YAHiwEtgANBKwAAAACAM4AAACeACcAAADaAAcA2wAJAN4ADgDfABMA4wAXAOQAHQDlAB8A"
            + "6AAmAOkAKwDqADYA6wA8AOwAQgDtAEwA7wBcAPAAbADxAHkA8gCBAPMAiQD0AI4A9QCSAPYAmgD4"
            + "AKQA+QC5APoAwQD7AMQA/ADMAP0A0wD+ANsA/wDeAQEA6AEDAPkBBAEAAQUBAwEHAQwBCQEYAQoB"
            + "HQELASIBDAEnAQ0A0QAAACoACQn9ABUHANIHANT/ADwACQcA1QcA1gcA0gcA1AEEAQEBAAA9CTkJ"
            + "GggAAADXANgAAQDNAAAA1gAGAAgAAAB1K8AAAk0stgAHPiy2AASFNwQWBAmUnQAxChYEZTcGHRYG"
            + "FAAfbQphiGQ+FAAhFgYUAB9xZTcELB22ABYsFgSItgAXV6cAMRYEFAAflJ4AKB0WBAplFAAfbYhg"
            + "PhYECmUUAB9xCmE3BCwdtgAWLBYEiLYAF1exAAAAAgDOAAAAPgAPAAABEQAFARIACgETABEBFAAY"
            + "ARUAHgEWACoBFwA2ARgAOwEZAEMBGgBPARsAWwEcAGcBHQBsAR4AdAEgANEAAAALAAL+AEYHANIB"
            + "BC0AAQDZANoAAQDNAAAAOwACAAIAAAAYKivAAAK2AAe2ABuZAAkRAW6nAAYRAW2sAAAAAgDOAAAA"
            + "BgABAAABLwDRAAAABQACFEIBAAEA2wDaAAEAzQAAABsAAQACAAAAAxAMrAAAAAEAzgAAAAYAAQAA"
            + "ATMAAQDcANoAAQDNAAAAcgAEAAQAAAA6K8AAAk0stgAEPh0EoQAJHRAMpAAeuwAjWbsAJFm3ACUS"
            + "JrYAJx22ACi2ACm3ACq/Kiy2AAcdtwAIrAAAAAIAzgAAABYABQAAAUIABQFDAAoBRAAVAUUAMAFH"
            + "ANEAAAAKAAL9ABUHANIBGgACANwA3QABAM0AAABIAAIABAAAABiyACscLj4cBaAADiobtgAbmQAG"
            + "hAMBHawAAAACAM4AAAASAAQAAAFMAAYBTQATAU4AFgFQANEAAAAGAAH8ABYBAAEA3gDfAAEAzQAA"
            + "ADgABAACAAAAFCorwAACtgAHK7YALCu2AC22AC6tAAAAAQDOAAAAEgAEAAABVAAJAVUADQFWABAB"
            + "VAAQAN4A4AABAM0AAABPAAQABAAAABodhSobtgAbmQALsgAvHC6nAAiyADAcLoVhrQAAAAIAzgAA"
            + "AAoAAgAAAVoABAFbANEAAAATAAJSBP8ABAAEBwDVAQEBAAIEAQABAOEA3wABAM0AAABZAAUAAgAA"
            + "ACQrtgAOmgAIKiu2ABIqK8AAArYAByu2ACwrtgAtK8AAArYAGK0AAAACAM4AAAAaAAYAAAFhAAcB"
            + "YgAMAWQAFQFlABkBZgAgAWQA0QAAAAMAAQwAAQDhAOIAAQDNAAACVwAIAAsAAAFEHASgAAwdBKAA"
            + "BwSnAAQDNgUZBMYAJxkEG7YAMZkAHhUFmQAJGQS2ADKtGQS2ADIqGxwdtgAuYQplrRsRB7JkNgYV"
            + "BpsAShUGsgAzvqIAQbIAMxUGLoU3BxkExgAcGQQbFgcqG7YAG5kACREBbqcABhEBbbYANBUFmQAI"
            + "FgenAA8WByobHB22AC5hCmWtG4UKZTcHHYU3CRYHCZSbADQWCRQANRYHaRYHFAA3bWEWBxQAOW1l"
            + "FgcUADttYREBbxxoEQFqZBAMbIVhYTcJpwA5FgkUADUWB2kWBxQAN7gAPWEWBxQAObgAPWUWBxQA"
            + "O7gAPWERAW8caBEBamQQDLgAPoVhYTcJHAWkABcWCSobtgAbmQAHCqcABhQAP2U3CRkExgAhFQWZ"
            + "ABwZBBsWCSobtgAbmQAJEQFupwAGEQFttgA0FgmtAAAAAgDOAAAAZgAZAAABbAARAW8AHwFwACQB"
            + "cQAqAXMAOgF3AEEBeABPAXkAWAF6AF0BewB2AX0AjQGAAJMBgQCXAYMAngGEAM8BigDcAYsA5QGM"
            + "AO4BjQD9AY4BBQGRAQoBkgEeAZYBKAGXAUEBmgDRAAAAlQASDkAB/AAaAQ//ADUACAcA1QEBAQcA"
            + "0gEBBAADBwDSAQT/AAIACAcA1QEBAQcA0gEBBAAEBwDSAQQBAglLBPoAAP0AQQQENVIE/wACAAkH"
            + "ANUBAQEHANIBAQQEAAIEBAL/ABwACQcA1QEBAQcA0gEBBAQAAwcA0gEE/wACAAkHANUBAQEHANIB"
            + "AQQEAAQHANIBBAECAAEA4wDkAAEAzQAAAjUABQARAAABMSvAAAI6BBkEILYAQZkAHBkEtgBCNgUZ"
            + "BLYAMjcGKhUFtgAbNginADQqILYAQzYFKhUFBAQBtgAYNwYqFQW2ABs2CBkEFQUWBhUImQAJEQFu"
            + "pwAGEQFttgA0IBYGZYg2CRYGFABEYRQARmE3ChUImQAJFgoKYTcKIBYKlJsAEhUJFQiZAAcEpwAE"
            + "BWA2CRAMFQloEQF1YDYMFQyeAA4VDBEBb2w2DKcADRUMEQFvuAA+NgwWBrIAMBUMLoVhNw0VCJkA"
            + "DxUMBqEACRYNCmE3DSAWDWWIBGA2DyC4AEg2ELIASZoAJBUQnQAfuwBKWbsAJFm3ACUSS7YAJxUQ"
            + "tgAotgAptwBMvxkEFQW2ABYZBBUMtgAXVxkEFQ+2ABVXGQQVELYAGhkEFQi2AE0ZBAS2AA2xAAAA"
            + "AgDOAAAAggAgAAABpAAGAagADwGpABYBqgAdAasAKAGwAC8BsQA6AbIAQgG0AFkBtwBgAbgAbAG5"
            + "AHEBugB3AbwAfgG9AI0BvwCYAcAAnQHBAKgBwwCyAcUAvgHGAMkBxwDPAckA2AHKAN4BywEFAcwB"
            + "DAHNARQBzgEcAc8BIwHQASoB0QEwAdIA0QAAAGoADPwAKAcA0v8AKgAHBwDVBwDWBAcA0gEEAQAD"
            + "BwDSAQT/AAIABwcA1QcA1gQHANIBBAEABAcA0gEEAQL9AB0BBFEB/wAAAAkHANUHANYEBwDSAQQB"
            + "AQQAAgEBAvwAGgEJ/AAcBP0ANQEBAAEA5QDaAAEAzQAAACcAAgAEAAAACyortgBOQSC4AEisAAAA"
            + "AQDOAAAACgACAAAB2AAGAdkAGQDmAOcAAQDNAAAAQwAEAAIAAAAaHgmUmwAMHhQAT3GIBGCsHhQA"
            + "T7gAUYgEYKwAAAACAM4AAAAOAAMAAAHeAAYB3wAPAeEA0QAAAAMAAQ8AAQDoAOcAAQDNAAAAHgAD"
            + "AAMAAAAGKh+2AEOsAAAAAQDOAAAABgABAAAB5QAQAOkA5wABAM0AAAFcAAQADgAAAMkfCZSeAEof"
            + "CmVCIRQAUm2INgkhFABScYg2BRUFElRsNgoVBRJUcDYGFQYRBbVsNgsVBhEFtXA2BxUHEQFtbDYM"
            + "FQcRAW1wBGA2CKcAVx8KZUIhFABSuAA9iDYJIRQAUrgAUYg2BRUFElS4AD42ChUFElS4AFU2BhUG"
            + "EQW1uAA+NgsVBhEFtbgAVTYHFQcRAW24AD42DBUHEQFtuABVBGA2CBEBkBUJaBBkFQpoYAcVC2hg"
            + "FQxgNg0VCgefAAwVDAefAAaEDQEVDawAAAACAM4AAABeABcAAAHxAAYB8gAKAfMAEgH0ABoB9QAh"
            + "AfYAKAH3ADAB+AA4AfkAQAH6AE0B/ABRAf0AWwH+AGUB/wBuAgAAdwIBAIECAgCLAgMAlQIEAKEC"
            + "BgC3AgcAwwIIAMYCCgDRAAAAHQAD+wBN/wBTAAsHANUEBAEBAQEBAQEBAAD8ACQBAAQA6gDQAAEA"
            + "zQAAACQAAgACAAAADCorwAACtgAHtgAbrAAAAAEAzgAAAAYAAQAAAhMAAADqAOsAAQDNAAAAHQAB"
            + "AAIAAAAFG7gAVqwAAAABAM4AAAAGAAEAAAIXAAgA7ADMAAEAzQAAAuIABAAAAAACrxIFtgBXmgAH"
            + "BKcABAOzAEkQRrwKWQMSWE9ZBBJZT1kFElpPWQYSW09ZBxJcT1kIEl1PWRAGEl5PWRAHEl9PWRAI"
            + "EmBPWRAJEmFPWRAKEmJPWRALEmNPWRAMEmRPWRANEmVPWRAOEmZPWRAPEmdPWRAQEmhPWRAREmlP"
            + "WRASEmpPWRATEmtPWRAUEmxPWRAVEm1PWRAWEm5PWRAXEm9PWRAYEnBPWRAZEnFPWRAaEnJPWRAb"
            + "EnNPWRAcEnRPWRAdEnVPWRAeEnZPWRAfEndPWRAgEnhPWRAhEnlPWRAiEnpPWRAjEntPWRAkEnxP"
            + "WRAlEn1PWRAmEn5PWRAnEn9PWRAoEoBPWRApEoFPWRAqEoJPWRArEoNPWRAsEoRPWRAtEoVPWRAu"
            + "EoZPWRAvEodPWRAwEohPWRAxEolPWRAyEopPWRAzEotPWRA0EoxPWRA1Eo1PWRA2Eo5PWRA3Eo9P"
            + "WRA4EpBPWRA5EpFPWRA6EpJPWRA7EpNPWRA8EpRPWRA9EpVPWRA+EpZPWRA/EpdPWRBAEphPWRBB"
            + "EplPWRBCEppPWRBDEptPWRBEEpxPWRBFEp1PswAzEA28ClkDEB9PWQQQH09ZBRAcT1kGEB9PWQcQ"
            + "Hk9ZCBAfT1kQBhAeT1kQBxAfT1kQCBAfT1kQCRAeT1kQChAfT1kQCxAeT1kQDBAfT7MAKxANvApZ"
            + "AxDiT1kEA09ZBRAfT1kGEDtPWQcQWk9ZCBB4T1kQBhEAl09ZEAcRALVPWRAIEQDUT1kQCREA809Z"
            + "EAoRARFPWRALEQEwT1kQDBEBTk+zADAQDbwKWQMQ4k9ZBANPWQUQH09ZBhA8T1kHEFtPWQgQeU9Z"
            + "EAYRAJhPWRAHEQC2T1kQCBEA1U9ZEAkRAPRPWRAKEQEST1kQCxEBMU9ZEAwRAU9PswAvsQAAAAIA"
            + "zgAAABYABQAAACcAEABGAbUBNgIEATkCWQE9ANEAAAAFAAIMQAEAAgDtAAAAAgDuAKAAAAAKAAEA"
            + "AgAFAJ8ECQ==");
}
