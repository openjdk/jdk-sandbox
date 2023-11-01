package io.bellsoft.hotcode.dcmd;

import io.bellsoft.hotcode.agent.HotCodeAgent;
import io.bellsoft.hotcode.profiling.Method;

import java.io.IOException;
import java.util.List;

public class CompilerDirectives {    
    private static final String DIRECTIVES_TEMPLATE_TEMPLATE;
    static {
        try (var in = HotCodeAgent.class.getResourceAsStream("compiler-directives.template")) {
            DIRECTIVES_TEMPLATE_TEMPLATE = new String(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String build(List<Method> methods) {
        var sb = new StringBuilder();
        for (var m : methods) {
            var type = m.type().replace('.', '/');
            var signature = m.signature();
            sb.append("\"").append(type).append(" ").append(signature).append("\",\n");
        }
        return String.format(DIRECTIVES_TEMPLATE_TEMPLATE, sb.toString());
    }
}
