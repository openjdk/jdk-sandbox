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

import java.security.*;
import java.util.Arrays;
import java.util.json.JsonArray;
import java.util.json.JsonNumber;
import java.util.json.JsonObject;
import java.util.json.JsonString;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-sha.html
// and https://pages.nist.gov/ACVP/draft-celi-acvp-sha3.html
public class SHA_Test {

    public static void run(JsonObject kat, Provider provider) throws Exception {
        if (kat.keys().get("algorithm") instanceof JsonString js) {
            var algorithm = js.value();
            final var alg = algorithm.startsWith("SHA2-") ? "SHA-" + algorithm.substring(5) : algorithm;
            var md = provider == null ? MessageDigest.getInstance(alg)
                    : MessageDigest.getInstance(alg, provider);
            if (kat.keys().get("testGroups") instanceof JsonArray ja) {
                ja.values().forEach(t -> {
                    if (t instanceof JsonObject jo) {
                        var testType = ((JsonString)jo.keys().get("testType")).value();

                        switch (testType) {
                            case "AFT" -> {
                                if (jo.keys().get("tests") instanceof JsonArray ja2) {
                                    ja2.values().forEach(c -> {
                                        if (c instanceof JsonObject jo2) {
                                            System.out.print(((JsonNumber)jo2.keys().get("tcId")).value() + " ");
                                            var msg = toByteArray(((JsonString)jo2.keys().get("msg")).value());
                                            var len = Integer.parseInt(((JsonString)jo2.keys().get("len")).value());
                                            if (msg.length * 8 == len) {
                                                Asserts.assertEqualsByteArray(md.digest(msg),
                                                        toByteArray(((JsonString)jo2.keys().get("md")).value()));
                                            } else {
                                                System.out.print("bits ");
                                            }
                                        }
                                    });
                                }
                            }
                            case "MCT" -> {
                                var mctVersion = ((JsonString)jo.keys().get("mctVersion")).value();
                                var trunc = mctVersion.equals("alternate");
                                if (jo.keys().get("tests") instanceof JsonArray ja2) {
                                    ja2.values().forEach(c -> {
                                        if (c instanceof JsonObject jo2) {
                                            System.out.print(((JsonNumber)jo2.keys().get("tcId")).value() + " ");
                                            final byte[][] SEED = {toByteArray(((JsonString) jo2.keys().get("msg")).value())};
                                            var INITIAL_SEED_LENGTH = Integer.parseInt(((JsonString)jo2.keys().get("len")).value());
                                            if (SEED[0].length * 8 == INITIAL_SEED_LENGTH) {
                                                if (jo2.keys().get("resultsArray") instanceof JsonArray ja3) {
                                                    ja3.values().forEach(r -> {
                                                        if (r instanceof JsonObject jo3) {
                                                            if (alg.startsWith("SHA3-")) {
                                                                var MD = SEED[0];
                                                                for (var i = 0; i < 1000; i++) {
                                                                    if (trunc) {
                                                                        MD = Arrays.copyOf(MD, INITIAL_SEED_LENGTH / 8);
                                                                    }
                                                                    MD = md.digest(MD);
                                                                }
                                                                Asserts.assertEqualsByteArray(MD,
                                                                        toByteArray(((JsonString)jo3.keys().get("md")).value()));
                                                                SEED[0] = MD;
                                                            } else {
                                                                var A = SEED[0];
                                                                var B = SEED[0];
                                                                var C = SEED[0];
                                                                byte[] MD = null;
                                                                for (var i = 0; i < 1000; i++) {
                                                                    var MSG = concat(A, B, C);
                                                                    if (trunc) {
                                                                        MSG = Arrays.copyOf(MSG, INITIAL_SEED_LENGTH / 8);
                                                                    }
                                                                    MD = md.digest(MSG);
                                                                    A = B;
                                                                    B = C;
                                                                    C = MD;
                                                                }
                                                                Asserts.assertEqualsByteArray(MD,
                                                                        toByteArray(((JsonString)jo3.keys().get("md")).value()));
                                                                SEED[0] = MD;
                                                            }
                                                        }
                                                    });
                                                }
                                            } else {
                                                System.out.print("bits ");
                                            }
                                        }
                                    });
                                }
                            }
                            case "LDT" -> {
                                if (jo.keys().get("tests") instanceof JsonArray ja2) {
                                    ja2.values().forEach(c -> {
                                        if (c instanceof JsonObject jo2) {
                                            System.out.print(((JsonNumber)jo2.keys().get("tcId")).value() + " ");

                                            if (jo2.keys().get("largeMsg") instanceof JsonObject lm) {
                                                var ct = toByteArray(((JsonString)lm.keys().get("content")).value());
                                                var flen = ((JsonNumber)lm.keys().get("fullLength")).value().longValue();
                                                var clen = ((JsonNumber)lm.keys().get("contentLength")).value().longValue();
                                                var cc = 0L;
                                                while (cc < flen) {
                                                    md.update(ct);
                                                    cc += clen;
                                                }
                                                Asserts.assertEqualsByteArray(md.digest(),
                                                        toByteArray(((JsonString)jo2.keys().get("md")).value()));
                                            }
                                        }
                                    });
                                }
                            }
                            default -> throw new UnsupportedOperationException(
                                    "Unknown testType: " + testType);
                        }
                    }
                });
                System.out.println();
            }
        }
    }

    /////////////

    static byte[] concat(byte[]... input) {
        var sum = 0;
        for (var i : input) {
            sum += i.length;
        }
        var out = new byte[sum];
        sum = 0;
        for (var i : input) {
            System.arraycopy(i, 0, out, sum, i.length);
            sum += i.length;
        }
        return out;
    }
}
