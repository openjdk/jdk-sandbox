package io.bellsoft.hotcode.dcmd;

import javax.management.ObjectName;
import java.io.IOException;
import java.lang.Exception;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class CompilerDirectivesControl {

    public static void replace(String directives) throws DirectivesException {
        try {
            var directivesPath = Files.createTempFile("compiler-directives", "");
            try {
                Files.writeString(directivesPath, directives);
                replace(directivesPath);
            } finally {
                Files.deleteIfExists(directivesPath);
            }
        } catch (IOException e) {
            throw new DirectivesException(e);
        }
    }

    private static String replace(Path directives) throws DirectivesException {
        Object[] dcmdArgs = { new String[] { "-r", directives.toAbsolutePath().toString() } };
        String[] replaceSignature = { String[].class.getName() };
        return invoke("compilerDirectivesReplace", dcmdArgs, replaceSignature);
    }

    private static String invoke(String cmd, Object[] dcmdArgs, String[] signature) throws DirectivesException {
        try {
            var beanServer = ManagementFactory.getPlatformMBeanServer();
            var dcmdName = new ObjectName("com.sun.management:type=DiagnosticCommand");
            return (String) beanServer.invoke(dcmdName, cmd, dcmdArgs, signature);
        } catch (Exception e) {
            throw new DirectivesException(String.format("failed to execute dcmd command: '%s' with args: '%s'", cmd,
                    Arrays.toString(dcmdArgs)), e);
        }
    }

    public static class DirectivesException extends Exception {
        private static final long serialVersionUID = -2646070265645346930L;

        public DirectivesException(String message, Throwable cause) {
            super(message, cause);
        }

        public DirectivesException(Throwable cause) {
            super(cause);
        }
    }
}
