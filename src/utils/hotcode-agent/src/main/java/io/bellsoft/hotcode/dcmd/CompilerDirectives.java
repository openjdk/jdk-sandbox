package io.bellsoft.hotcode.dcmd;

import io.bellsoft.hotcode.agent.HotCodeAgent;
import io.bellsoft.hotcode.profiling.Method;

import java.io.IOException;
import java.util.List;

public class CompilerDirectives {

    private static final String DIRECTIVES_TEMPLATE_RESOURCE = "compiler-directives.template";

    private static String getCompilerDirectivesTemplate() {
        try (var in = HotCodeAgent.class.getResourceAsStream(DIRECTIVES_TEMPLATE_RESOURCE)) {
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String build(List<Method> methods) {
        var sb = new StringBuilder();
        for (var m : methods) {
            var type = m.getType().replace('.', '/');
            var signature = m.getSignature();
            sb.append("\"").append(type).append(" ").append(signature).append("\",\n");
        }
        return String.format(getCompilerDirectivesTemplate(), sb.toString());
    }
}
