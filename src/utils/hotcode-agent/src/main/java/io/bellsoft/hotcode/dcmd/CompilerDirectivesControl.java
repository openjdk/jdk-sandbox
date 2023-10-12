package io.bellsoft.hotcode.dcmd;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class CompilerDirectivesControl {

    private final MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();

    public void replace(String directives) {
        try {
            var directivesPath = Files.createTempFile("compiler-directives", "");
            Files.writeString(directivesPath, directives);
            replace(directivesPath);
            directivesPath.toFile().delete();
        } catch (IOException e) {
            throw new RuntimeException("failed to apply compiler directives due to %s", e);
        }
    }

    private String replace(Path directives) {
        var cmd = "compilerDirectivesReplace";
        String[] signature = { String[].class.getName() };
        Object[] dcmdArgs = { new String[] { "-r", directives.toAbsolutePath().toString() } };
        return invoke(cmd, signature, dcmdArgs);
    }

    private String invoke(String cmd, String[] signature, Object[] dcmdArgs) {
        try {
            var name = new ObjectName("com.sun.management:type=DiagnosticCommand");
            return (String) beanServer.invoke(name, cmd, dcmdArgs, signature);
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to execute dcmd command: '%s' with args: '%s'", cmd,
                    Arrays.toString(dcmdArgs)), e);
        }
    }
}
