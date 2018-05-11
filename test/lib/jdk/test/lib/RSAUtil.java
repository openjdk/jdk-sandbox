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

package jdk.test.lib;

import java.security.*;
import java.security.spec.*;
import java.util.*;

public class RSAUtil {

    public enum SignatureType {
        RSA_PKCS1_5,
        RSA_PSS,
    }    
 
    // collection of all supported RSA PKCS1.5 algorithms
    // note that the entries are ordered by required key sizes
    private static final String[] PKCS1_5_ALGS = {
        // these requires key size 768
        "SHA384withRSA", "SHA512withRSA",
        // these are supported by min key size 512
        "MD2withRSA", "MD5withRSA", "SHA1withRSA",
        "SHA224withRSA", "SHA512/224withRSA",
        "SHA256withRSA", "SHA512/256withRSA"
    };

    private static final int PKCS1_5_INDEX_768 = 0;
    private static final int PKCS1_5_INDEX_512 = 2;

    // collection of all supported RSA-PSS algorithms
    // note that the entries are ordered by required key sizes
    private static final String[] PSS_ALGS = {
        // these requires key size 2048
        "SHA512withRSAandMGF1",
        // these requires key size 1024
        "SHA384withRSAandMGF1",
        // these requires key size 768
        "SHA256withRSAandMGF1", "SHA512/256withRSAandMGF1",
        // these are supported by min key size 512
        "SHA1withRSAandMGF1", "SHA224withRSAandMGF1",
        "SHA512/224withRSAandMGF1"
    };

    private static final int PSS_INDEX_2048 = 0;
    private static final int PSS_INDEX_1024 = 1;
    private static final int PSS_INDEX_768 = 2;
    private static final int PSS_INDEX_512 = 4;
    
    public static Iterable<String> getSignatureAlgorithms(int keysize,
            SignatureType type) throws RuntimeException {
        List<String> algos;
        Iterable<String> result;
        switch (type) {
            case RSA_PKCS1_5:
                algos = new ArrayList<>(Arrays.asList(PKCS1_5_ALGS));
                if (keysize >= 768) {
                    result = algos.subList(PKCS1_5_INDEX_768, PKCS1_5_ALGS.length);
                } else {
                    result = algos.subList(PKCS1_5_INDEX_512, PKCS1_5_ALGS.length);
                }
                break;
            case RSA_PSS:
                algos = new ArrayList<>(Arrays.asList(PSS_ALGS));
                if (keysize >= 2048) {
                    result = algos.subList(PSS_INDEX_2048, PSS_ALGS.length);
                } else if (keysize >= 1024) {
                    result = algos.subList(PSS_INDEX_1024, PSS_ALGS.length);
                } else if (keysize >= 768) {
                    result = algos.subList(PSS_INDEX_768, PSS_ALGS.length);
                } else {
                    result = algos.subList(PSS_INDEX_512, PSS_ALGS.length);
                }
                break;
            default:
                throw new RuntimeException("Unsupported RSA signature algorithm type: " + type);
        }
        return result;
    }

    private static final String RSA_SIG_SUFFIX = "withRSA";
    private static final String RSAPSS_SIG_SUFFIX = "RSA-PSS";
    private static final String RSAPSS_SIG_SUFFIX2 = "withRSAandMGF1";

    public static AlgorithmParameterSpec generateDefaultParameter(String sigalg)
            throws RuntimeException {
        if (sigalg.regionMatches(true,
                sigalg.length() - RSA_SIG_SUFFIX.length(),
                RSA_SIG_SUFFIX, 0, RSA_SIG_SUFFIX.length())) {
            // RSA PKCS#1.5 signatures do not use parameters
            return null;
        } else if (sigalg.regionMatches(true,
                sigalg.length() - RSAPSS_SIG_SUFFIX.length(),
                RSAPSS_SIG_SUFFIX, 0, RSAPSS_SIG_SUFFIX.length())) {
            // no default parameters for RSA-PSS signature.
            // RSA-PSS signature with different PSS parameters
            // are tested under Known Answer Tests
            throw new RuntimeException("No default parameter generation for RSA-PSS");
        } else if (sigalg.regionMatches(true,
                sigalg.length() - RSAPSS_SIG_SUFFIX2.length(),
                RSAPSS_SIG_SUFFIX2, 0, RSAPSS_SIG_SUFFIX2.length())) {
            // should be the expanded/friendly name for RSA-PSS
            // use the same digest algo as in its name and its
            // output length as the salt length
            String digest = sigalg.substring(0,
                sigalg.length() - RSAPSS_SIG_SUFFIX2.length()).toUpperCase();
            if (digest.startsWith("SHA") && digest.indexOf("-") == -1) {
                // convert to standard name
                digest = "SHA-" + digest.substring(3);
            }
            // verify the digest algorithm by trying the getInstance call
            try {
                MessageDigest md = MessageDigest.getInstance(digest);
                System.out.println("Digest in PSS parameter: " + digest);
                return new PSSParameterSpec(digest, "MGF1",
                    new MGF1ParameterSpec(digest), md.getDigestLength(), 1);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Unsupported RSA signature " + sigalg);
        }
    }
}
