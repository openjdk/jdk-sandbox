/*
 *     Copyright 2023 BELLSOFT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bellsw.hotcode.profiling;

import java.io.OutputStream;
import java.io.PrintWriter;

public final class MethodProfilePrinter {
    private static final String ROW_SEP = "-".repeat(120);
    private static final String HEADER = String.format("| %-7s | %-9s | %-94s |", "COUNT", "%", "METHOD");
    private static final String ROW_FMT = "| %7d | %9.2f | %-94s |";

    private final PrintWriter printWriter;

    public MethodProfilePrinter(OutputStream output) {
        printWriter = new PrintWriter(output);
    }

    public void print(Profile<Method> profile, int topK) {
        printWriter.println(ROW_SEP);
        printWriter.println(HEADER);
        printWriter.println(ROW_SEP);

        for (var m : profile.getTop(topK)) {
            int count = profile.occurrences(m);
            float ratio = 100.0f * count / profile.total();
            printWriter.println(String.format(ROW_FMT, count, ratio, m.signature()));
        }

        printWriter.println(ROW_SEP);
        printWriter.flush();
    }
}
