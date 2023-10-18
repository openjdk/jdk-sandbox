package io.bellsoft.hotcode.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;

import com.sun.tools.attach.VirtualMachine;

import io.bellsoft.hotcode.dcmd.CompilerDirectives;
import io.bellsoft.hotcode.profiling.jfr.JfrOfflineProfiling;
import io.bellsoft.hotcode.profiling.MethodProfilePrinter;

public class HotCodeMain {
    
    private static final String USAGE_TXT = "usage.txt";

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
        }
        var command = args[0];
        checkBasicCommands(command);

        var keyArgs = new Properties();
        var freeArgs = new ArrayList<String>();
        parseCommandArgs(args, keyArgs, freeArgs);

        switch (command) {
        case "convert":
            if (freeArgs.size() < 1 || freeArgs.size() > 2) {
                usage();
            }
            var recordingPath = Path.of(freeArgs.get(0));
            var directiveFilePath = recordingPath.resolveSibling(recordingPath.getFileName() + ".directives");
            if (freeArgs.size() > 1) {
                directiveFilePath = Path.of(freeArgs.get(1));
            }
            convert(keyArgs, recordingPath, directiveFilePath);
            break;
        case "attach":
            if (freeArgs.size() != 1) {
                usage();
            }
            var pid = freeArgs.get(0);
            attach(pid, keyArgs);
            break;
        default:
            usage();
            break;
        }
    }

    private static void attach(String pid, Properties keyArgs) {
        var argumentString = agentArgs(keyArgs);
        var jar = HotCodeMain.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            var vm = VirtualMachine.attach(pid);
            vm.loadAgent(jar, argumentString);
            vm.detach();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void convert(Properties keyArgs, Path recordingPath, Path directiveFilePath) {
        var config = HotCodeAgentConfiguration.from(keyArgs);
        var profile = new JfrOfflineProfiling(config.top, config.maxStackDepth, recordingPath).call();

        new MethodProfilePrinter(System.out).print(profile);

        var hotMethods = profile.getTop();
        var directives = CompilerDirectives.build(hotMethods);
        
        try {
            Files.writeString(directiveFilePath, directives);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void parseCommandArgs(String[] args, Properties keyArgs, ArrayList<String> freeArgs) {
        String key = null;
        
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                // check for key without value
                if (key != null) {
                    usage();
                }
                key = args[i].substring(2);
                if (key.isEmpty()) {
                    usage();
                }
            } else {
                var value = args[i];
                if (key != null) {
                    keyArgs.put(key, value);
                    key = null;
                } else {
                    freeArgs.add(value);
                }
            }
        }
        // check for key without value
        if (key != null) {
            usage();
        }
    }
    
    private static String agentArgs(Properties keyArgs) {
        var sb = new StringBuilder();
        for (var key : keyArgs.keySet()) {
            if (!sb.isEmpty()) {
                sb.append(",");
            }
            sb.append(key).append("=").append(keyArgs.get(key));
        }
        return sb.toString();
    }

    private static void checkBasicCommands(String command) {
        switch (command) {
        case "-h":
        case "-?":
        case "--help":
        case "help":
            usage();
            break;
        case "-v":
        case "--version":
        case "version":
            version();
            break;
        }
    }
 
    static void usage() {
        try (var in = HotCodeMain.class.getResourceAsStream(USAGE_TXT)) {
            System.out.println(new String(in.readAllBytes()));
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void version() {
        System.out.println(HotCodeMain.class.getPackage().getImplementationVersion());
        System.exit(1);
    }
    
}
