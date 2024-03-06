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
package com.bellsw.hotcode.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;

import com.bellsw.hotcode.dcmd.CompilerDirectives;
import com.bellsw.hotcode.profiling.Method;
import com.bellsw.hotcode.profiling.MethodProfilePrinter;
import com.bellsw.hotcode.profiling.TopKProfile;
import com.bellsw.hotcode.profiling.jfr.JfrOfflineProfiling;
import com.sun.tools.attach.VirtualMachine;

public final class HotCodeMain {
    private static final String USAGE_TXT = "usage.txt";

    private final Properties keyArgs = new Properties();
    private final ArrayList<String> freeArgs = new ArrayList<>();
    private final String command;

    private HotCodeMain(String[] args) {
        if (args.length < 1) {
            usage();
        }
        command = args[0];
        parseCommandArgs(args);
    }

    public static void main(String[] args) {
        new HotCodeMain(args).dispatch();
    }

    private void dispatch() {
        switch (command) {
        case "-h", "-?", "--help", "help" -> usage();
        case "-v", "--version", "version" -> version();
        case "convert" -> {
            if (freeArgs.size() < 1 || freeArgs.size() > 2) {
                usage();
            }
            var recordingPath = Path.of(freeArgs.get(0));
            var directivesPath = recordingPath.resolveSibling(recordingPath.getFileName() + ".directives");
            if (freeArgs.size() > 1) {
                directivesPath = Path.of(freeArgs.get(1));
            }
            convert(recordingPath, directivesPath);
        }
        case "attach" -> {
            if (freeArgs.size() != 1) {
                usage();
            }
            var pid = freeArgs.get(0);
            attach(pid);
        }
        default -> usage();
        }
    }

    private void attach(String pid) {
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

    private void convert(Path recordingPath, Path directiveFilePath) {
        var config = HotCodeAgentConfiguration.from(keyArgs);
        var profile = new TopKProfile<Method>(config.top());
        var profiling = new JfrOfflineProfiling(recordingPath);
        try {
            profiling.fill(profile);

            new MethodProfilePrinter(System.out).print(profile, config.top());

            var hotMethods = profile.getTop(config.top());
            var directives = CompilerDirectives.build(hotMethods, true);

            Files.writeString(directiveFilePath, directives);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseCommandArgs(String[] args) {
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
        if (args.length > 1 && key != null) {
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

    static void usage() {
        try (var in = HotCodeMain.class.getResourceAsStream(USAGE_TXT)) {
            in.transferTo(System.out);
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
