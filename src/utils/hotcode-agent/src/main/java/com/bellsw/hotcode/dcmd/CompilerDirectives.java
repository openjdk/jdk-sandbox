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
package com.bellsw.hotcode.dcmd;

import java.util.List;

import com.bellsw.hotcode.profiling.Method;

public final class CompilerDirectives {

    private static final int EST_METHOD_SIZE = 128;

    private CompilerDirectives() {
    }

    public static String build(List<Method> methods, boolean hot) {
        var sb = new StringBuilder(methods.size() * EST_METHOD_SIZE);
        sb.append("[{").append("\n");

        sb.append("match: [").append("\n");
        for (var m : methods) {
            var type = m.type().replace('.', '/');
            var signature = m.signature();
            sb.append("\"").append(type).append(" ").append(signature).append("\",").append("\n");
        }
        sb.append("],").append("\n");

        sb.append("c2: {").append("\n");
        sb.append("Hot: ").append(String.valueOf(hot)).append(",").append("\n");
        // sb.append("BackgroundCompilation:
        // ").append(String.valueOf(bg)).append(",").append("\n");
        sb.append("}").append("\n");

        sb.append("}]");
        return sb.toString();
    }
}
