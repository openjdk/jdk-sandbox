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

import javax.crypto.KEM;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.NamedParameterSpec;
import jdk.internal.util.json.JsonArray;
import jdk.internal.util.json.JsonObject;
import jdk.internal.util.json.JsonString;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-ml-kem.html
public class ML_KEM_Test {

    public static void run(JsonObject kat, Provider provider) throws Exception {
        var mode = kat.get("mode");
        if (kat.get("mode") instanceof JsonString m) {
            switch (m.value()) {
                case "keyGen" -> keyGenTest(kat, provider);
                case "encapDecap" -> encapDecapTest(kat, provider);
                default -> throw new UnsupportedOperationException("Unknown mode: " + mode);
            }
        } else {
            throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }

    static void keyGenTest(JsonObject kat, Provider p) throws Exception {
        var g = p == null
                ? KeyPairGenerator.getInstance("ML-KEM")
                : KeyPairGenerator.getInstance("ML-KEM", p);
        var f = p == null
                ? KeyFactory.getInstance("ML-KEM")
                : KeyFactory.getInstance("ML-KEM", p);
        if (kat.get("testGroups") instanceof JsonArray ja) {
            ja.stream().forEach(t -> {
                if (t instanceof JsonObject jo) {
                    if (jo.get("parameterSet") instanceof JsonString pname) {
                        var np = new NamedParameterSpec(pname.value());
                        System.out.println(">> " + pname.value());
                        if (jo.get("tests") instanceof JsonArray ja2) {
                            ja2.stream().forEach(c -> {
                                if (c instanceof JsonObject jo2) {
                                    System.out.print(((JsonString)jo2.get("tcId")).value() + " ");
                                    g.initialize(np, new FixedSecureRandom(
                                            toByteArray(((JsonString)jo2.get("d")).value()), toByteArray(((JsonString)jo2.get("z")).value())));
                                    var kp = g.generateKeyPair();
                                    var pk = f.getKeySpec(kp.getPublic(), EncodedKeySpec.class).getEncoded();
                                    var sk = f.getKeySpec(kp.getPrivate(), EncodedKeySpec.class).getEncoded();
                                    Asserts.assertEqualsByteArray(pk, toByteArray(((JsonString)jo2.get("ek")).value()));
                                    Asserts.assertEqualsByteArray(sk, toByteArray(((JsonString)jo2.get("dk")).value()));
                                }
                            });
                            System.out.println();
                        }
                    }
                }
            });
        }
    }

    static void encapDecapTest(JsonObject kat, Provider p) throws Exception {
        var g = p == null
                ? KEM.getInstance("ML-KEM")
                : KEM.getInstance("ML-KEM", p);
        if (kat.get("testGroups") instanceof JsonArray ja) {
            ja.stream().forEach(t -> {
                if (t instanceof JsonObject jo) {
                    if (jo.get("parameterSet") instanceof JsonString pname &&
                        jo.get("function") instanceof JsonString function) {
                        System.out.println(">> " + pname.value() + " " + function.value());
                        if (function.value().equals("encapsulation")) {
                            if (jo.get("tests") instanceof JsonArray ja2) {
                                ja2.stream().forEach(c -> {
                                    if (c instanceof JsonObject jo2) {
                                        System.out.print(((JsonString)jo2.get("tcId")).value() + " ");
                                        var ek = new PublicKey() {
                                            public String getAlgorithm() { return pname.value(); }
                                            public String getFormat() { return "RAW"; }
                                            public byte[] getEncoded() { return toByteArray(((JsonString)jo2.get("ek")).value()); }
                                        };
                                        var e = g.newEncapsulator(
                                                ek, new FixedSecureRandom(toByteArray(((JsonString)jo2.get("m")).value())));
                                        var enc = e.encapsulate();
                                        Asserts.assertEqualsByteArray(
                                                enc.encapsulation(), toByteArray(((JsonString)jo2.get("c")).value()));
                                        Asserts.assertEqualsByteArray(
                                                enc.key().getEncoded(), toByteArray(((JsonString)jo2.get("k")).value()));
                                    }
                                    System.out.println();
                                });
                            }
                        } else if (function.value().equals("decapsulation")) {
                            if (jo.get("tests") instanceof JsonArray ja2) {
                                ja2.stream().forEach(c -> {
                                    if (c instanceof JsonObject jo2) {
                                        System.out.print(((JsonString)jo2.get("tcId")).value() + " ");
                                        var dk = new PrivateKey() {
                                            public String getAlgorithm() { return pname.value(); }
                                            public String getFormat() { return "RAW"; }
                                            public byte[] getEncoded() { return toByteArray(((JsonString)jo2.get("dk")).value()); }
                                        };
                                        var d = g.newDecapsulator(dk);
                                        var k = d.decapsulate(toByteArray(((JsonString)jo2.get("c")).value()));
                                        Asserts.assertEqualsByteArray(k.getEncoded(), toByteArray(((JsonString)jo2.get("k")).value()));
                                    }
                                });
                            }
                            System.out.println();
                        }
                    }
                }
            });
        }
    }
}
