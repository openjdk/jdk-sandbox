package io.bellsoft.hotcode.profiling.jfr;

import io.bellsoft.hotcode.agent.HotCodeAgentConfiguration;
import io.bellsoft.hotcode.dcmd.CompilerDirectives;
import io.bellsoft.hotcode.profiling.Method;
import io.bellsoft.hotcode.profiling.Profile;

import java.io.OutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class JfrOfflineMain {

    private int topKSamplesCount = (int) HotCodeAgentConfiguration.Option.TOP_K.getDefaultValue();
    private int maxStackDepth = (int) HotCodeAgentConfiguration.Option.MAX_STACK_DEPTH.getDefaultValue();
    private Path recordingPath;
    private Path directiveFilePath;

    public static void main(String[] args) {
        new JfrOfflineMain().run(args);
    }

    void run(String... args) {
        try {
            parseArguments(args);
            var topK = new JfrOfflineProfiling(topKSamplesCount, maxStackDepth, recordingPath).call();
            new Reporter().report(topK, System.out);

            var hotMethods = topK.getTop();
            var directives = CompilerDirectives.build(hotMethods);
            Files.writeString(directiveFilePath, directives);
        } catch (UsageException e) {
            System.err.println("ERROR: " + e.getMessage());
            usage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("ERROR: failed to write report: " + e.getMessage());
            System.exit(1);
        }
    }

    void parseArguments(String... args) throws UsageException {
        boolean keywordArgs = true;
        int i = 0;
        while (i < args.length && keywordArgs) {
            switch (args[i]) {
            case "-h":
            case "--help":
                usage();
                System.exit(1);
            case "-k":
            case "--topk":
                if (i + 1 < args.length) {
                    try {
                        topKSamplesCount = Integer.parseInt(args[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        throw new UsageException("cannot convert value: " + args[i + 1] + " of parameter " + args[i]
                                + " to integer value");
                    }
                }
                i++;
                break;
            case "-d":
            case "--max-stack-depth":
                if (i + 1 < args.length) {
                    try {
                        maxStackDepth = Integer.parseInt(args[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        throw new UsageException("cannot convert value: " + args[i + 1] + " of parameter " + args[i]
                                + " to integer value");
                    }
                }
                i++;
                break;
            default:
                keywordArgs = false;
                break;
            }
        }

        if (i < args.length) {
            recordingPath = Path.of(args[i]);
            i++;
        } else {
            throw new UsageException("recording is not specified");
        }

        if (i < args.length) {
            directiveFilePath = Path.of(args[i]);
        } else {
            directiveFilePath = recordingPath.resolveSibling(recordingPath.getFileName() + ".directives");
        }
    }

    void usage() {
        String text = "USAGE: java -jar hotcode-agent.jar [options] RECORDING [DIRECTIVES_FILE]".concat("\nOptions:")
                .concat("\n -h | --help                 shows this help screen and exits")
                .concat("\n -k | --topk NUM             specifies the NUM of top K execution samples, default is 100;")
                .concat("\n -d | --max-stack-depth NUM  specifies the maximum NUM of the stack frames to be processed, default is -1 (unlimited)")
                .concat("\n").concat("\nArguments:")
                .concat("\n RECORDING        a JFR recording file or repository to be processed")
                .concat("\n DIRECTIVES_FILE  a file where the compiler directives will be written, if omitted")
                .concat("\n                  then the output will be written into RECORDING.directives file")
                .concat("\n");
        System.out.println(text);
    }

    private static class Reporter {

        private String rowSeparator = String.format("%120s", "-").replace(" ", "-");
        private String header = String.format("| %-7s | %-9s | %-94s |", "COUNT", "%", "METHOD");
        private String rowFormat = "| %7d | %9.2f | %-94s |";

        void report(Profile<Method> topK, OutputStream output) throws IOException {
            var printWriter = new PrintWriter(output);
            printWriter.println(rowSeparator);
            printWriter.println(header);
            printWriter.println(rowSeparator);

            for (var m : topK.getTop()) {
                int count = topK.occurrences(m);
                float ratio = 100.0f * count / topK.getTotalUnique();
                printWriter.println(String.format(rowFormat, count, ratio, m.getSignature()));
            }

            printWriter.println(rowSeparator);
            printWriter.flush();
        }

    }

    private class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        public UsageException(String message) {
            super(message);
        }
    }
}
