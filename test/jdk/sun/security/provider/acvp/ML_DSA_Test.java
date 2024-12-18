/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;
import jdk.test.lib.security.FixedSecureRandom;
import sun.security.provider.ML_DSA_Impls;

import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.json.JsonArray;
import java.util.json.JsonBoolean;
import java.util.json.JsonNumber;
import java.util.json.JsonObject;
import java.util.json.JsonString;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-ml-dsa.html
public class ML_DSA_Test {

    public static void run(JsonObject kat, Provider provider) throws Exception {
        var mode = kat.keys().get("mode");
        if (kat.keys().get("mode") instanceof JsonString m) {
            switch (m.value()) {
                case "keyGen" -> keyGenTest(kat, provider);
                case "sigGen" -> sigGenTest(kat, provider);
                case "sigVer" -> sigVerTest(kat, provider);
                default -> throw new UnsupportedOperationException("Unknown mode: " + mode);
            }
        } else {
            throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }

    static void keyGenTest(JsonObject kat, Provider p) throws Exception {
        var g = p == null
                ? KeyPairGenerator.getInstance("ML-DSA")
                : KeyPairGenerator.getInstance("ML-DSA", p);
        var f = p == null
                ? KeyFactory.getInstance("ML-DSA")
                : KeyFactory.getInstance("ML-DSA", p);
        if (kat.keys().get("testGroups") instanceof JsonArray ja) {
            ja.values().forEach(t -> {
                if (t instanceof JsonObject jo) {
                    if (jo.keys().get("parameterSet") instanceof JsonString pname) {
                        var np = new NamedParameterSpec(pname.value());
                        System.out.println(">> " + pname.value());
                        if (jo.keys().get("tests") instanceof JsonArray ja2) {
                            ja2.values().forEach(c -> {
                                if (c instanceof JsonObject jo2) {
                                    System.out.print(((JsonNumber)jo2.keys().get("tcId")).value() + " ");
                                    byte[] pk, sk;
                                    try {
                                        g.initialize(np, new FixedSecureRandom(toByteArray(((JsonString)jo2.keys().get("seed")).value())));
                                        var kp = g.generateKeyPair();
                                        pk = f.getKeySpec(kp.getPublic(), EncodedKeySpec.class).getEncoded();
                                        sk = f.getKeySpec(kp.getPrivate(), EncodedKeySpec.class).getEncoded();
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                    Asserts.assertEqualsByteArray(pk, toByteArray(((JsonString)jo2.keys().get("pk")).value()));
                                    Asserts.assertEqualsByteArray(sk, toByteArray(((JsonString)jo2.keys().get("sk")).value()));
                                }
                            });
                            System.out.println();
                        }
                    }
                }
            });
        }
    }

    static void sigGenTest(JsonObject kat, Provider p) throws Exception {
        var s = p == null
                ? Signature.getInstance("ML-DSA")
                : Signature.getInstance("ML-DSA", p);
        if (kat.keys().get("testGroups") instanceof JsonArray ja) {
            ja.values().forEach(t -> {
                if (t instanceof JsonObject jo) {
                    if (jo.keys().get("parameterSet") instanceof JsonString pname) {
                        var det = ((JsonBoolean)jo.keys().get("deterministic")).value();
                        System.out.println(">> " + pname.value() + " sign");
                        if (jo.keys().get("tests") instanceof JsonArray ja2) {
                            ja2.values().forEach(c -> {
                                if (c instanceof JsonObject jo2) {
                                    System.out.print(((JsonNumber)jo2.keys().get("tcId")).value() + " ");
                                    var sk = new PrivateKey() {
                                        public String getAlgorithm() { return pname.value(); }
                                        public String getFormat() { return "RAW"; }
                                        public byte[] getEncoded() { return toByteArray(((JsonString)jo2.keys().get("sk")).value()); }
                                    };
                                    var sr = new FixedSecureRandom(
                                            det ? new byte[32] : toByteArray(((JsonString)jo2.keys().get("rnd")).value()));
                                    byte[] sig;
                                    try {
                                        s.initSign(sk, sr);
                                        s.update(toByteArray(((JsonString)jo2.keys().get("message")).value()));
                                        sig = s.sign();
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                    Asserts.assertEqualsByteArray(
                                            sig, toByteArray(((JsonString)jo2.keys().get("signature")).value()));
                                }
                            });
                            System.out.println();
                        }
                    }
                }
            });
        }
    }

    static void sigVerTest(JsonObject kat, Provider p) throws Exception {
        var s = p == null
                ? Signature.getInstance("ML-DSA")
                : Signature.getInstance("ML-DSA", p);
        if (kat.keys().get("testGroups") instanceof JsonArray ja) {
            ja.values().forEach(t -> {
                if (t instanceof JsonObject jo) {
                    if (jo.keys().get("parameterSet") instanceof JsonString pname) {
                        var pk = new PublicKey() {
                            public String getAlgorithm() { return pname.value(); }
                            public String getFormat() { return "RAW"; }
                            public byte[] getEncoded() { return toByteArray(((JsonString)jo.keys().get("pk")).value()); }
                        };
                        System.out.println(">> " + pname.value() + " verify");
                        if (jo.keys().get("tests") instanceof JsonArray ja2) {
                            ja2.values().forEach(c -> {
                                if (c instanceof JsonObject jo2) {
                                    System.out.print(((JsonNumber) jo2.keys().get("tcId")).value() + " ");
                                    // Only ML-DSA sigVer has negative tests
                                    var expected = ((JsonBoolean)jo2.keys().get("testPassed")).value();
                                    var actual = true;
                                    try {
                                        s.initVerify(pk);
                                        s.update(toByteArray(((JsonString)jo2.keys().get("message")).value()));
                                        actual = s.verify(toByteArray(((JsonString)jo2.keys().get("signature")).value()));
                                    } catch (InvalidKeyException | SignatureException e) {
                                        actual = false;
                                    }
                                    Asserts.assertEQ(expected, actual);
                                }
                            });
                            System.out.println();
                        }
                    }
                }
            });
        }
    }
}
