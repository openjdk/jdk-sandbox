package io.bellsoft.hotcode.dcmd;

import javax.management.ObjectName;
import java.io.IOException;
import java.lang.Exception;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.Arrays;

public class CompilerDirectivesControl {

    public static String clear(boolean refresh) throws DirectivesException {
        var args = refresh ? new String[] { "-r" } : new String[] { };
        Object[] dcmdArgs = { args };
        return invoke("compilerDirectivesClear", dcmdArgs);
    }

    public static String add(String directives, boolean refresh) throws DirectivesException {
        try {
            var directivesPath = Files.createTempFile("compiler-directives", "");
            try {
                Files.writeString(directivesPath, directives);

                var path = directivesPath.toAbsolutePath().toString();
                var args = refresh ? new String[] { "-r", path } : new String[] { path };
                Object[] dcmdArgs = { args };
                return invoke("compilerDirectivesAdd", dcmdArgs);
            } finally {
                Files.deleteIfExists(directivesPath);
            }
        } catch (IOException e) {
            throw new DirectivesException(e);
        }
    }

    private static String invoke(String cmd, Object[] dcmdArgs) throws DirectivesException {
        try {
            var beanServer = ManagementFactory.getPlatformMBeanServer();
            var dcmdName = new ObjectName("com.sun.management:type=DiagnosticCommand");
            String[] signature = { String[].class.getName() };
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
