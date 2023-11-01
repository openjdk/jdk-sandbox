package io.bellsoft.hotcode.dcmd;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class CompilerDirectivesControl {

    private static final MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
    private static final ObjectName dcmdName;
    static {
        try {
            dcmdName = new ObjectName("com.sun.management:type=DiagnosticCommand");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }
    private static final String[] replaceSignature = { String[].class.getName() };

    public static void replace(String directives) {
        try {
            var directivesPath = Files.createTempFile("compiler-directives", "");
            try {
                Files.writeString(directivesPath, directives);
                replace(directivesPath);   
            } finally {
                directivesPath.toFile().delete();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String replace(Path directives) {
        Object[] dcmdArgs = { new String[] { "-r", directives.toAbsolutePath().toString() } };
        return invoke("compilerDirectivesReplace", replaceSignature, dcmdArgs);
    }

    private static String invoke(String cmd, String[] signature, Object[] dcmdArgs) {
        try {
            return (String) beanServer.invoke(dcmdName, cmd, dcmdArgs, signature);
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to execute dcmd command: '%s' with args: '%s'", cmd,
                    Arrays.toString(dcmdArgs)), e);
        }
    }
}
