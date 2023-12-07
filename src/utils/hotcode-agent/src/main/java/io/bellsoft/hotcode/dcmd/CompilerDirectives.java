package io.bellsoft.hotcode.dcmd;

import io.bellsoft.hotcode.profiling.Method;
import java.util.List;

public class CompilerDirectives {

    public static String build(List<Method> methods, boolean hot) {
        var sb = new StringBuilder(methods.size() * 128);
        sb.append("[{").append("\n");
        {
            sb.append("match: [").append("\n");
            for (var m : methods) {
                var type = m.type().replace('.', '/');
                var signature = m.signature();
                sb.append("\"").append(type).append(" ").append(signature).append("\",").append("\n");
            }
            sb.append("],").append("\n");
        }
        {
            sb.append("c2: {").append("\n");
            sb.append("Hot: ").append(String.valueOf(hot)).append(",").append("\n");
            // sb.append("BackgroundCompilation: ").append(String.valueOf(bg)).append(",").append("\n");
            sb.append("}").append("\n");
        }
        sb.append("}]");
        return sb.toString();
    }
}
