/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jnlp.converter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Options {

    private boolean createImage = false;
    private boolean createInstaller = false;
    private String installerType = null;
    private String jnlp = null;
    private String output = null;
    private String keep = null;
    private boolean help = false;
    private boolean verbose = false;
    private boolean version = false;
    private final List<String> jpackageOptions = new ArrayList<>();
    private boolean isRuntimeImageSet = false;

    private static final String JNLP_OPTION_PREFIX = "--jnlp=";
    private static final String OUTPUT_OPTION_PREFIX = "--output=";
    private static final String KEEP_OPTION_PREFIX = "--keep=";
    private static final String JNLP_OPTION_SHORT_PREFIX = "-j";
    private static final String OUTPUT_OPTION_SHORT_PREFIX = "-o";
    private static final String KEEP_OPTION_SHORT_PREFIX = "-k";

    private static final String [] INSTALLER_TYPES = {"msi", "rpm", "deb",
                                                      "dmg", "pkg", "pkg-app-store"};

    // --output, -o, --input, -i, --files, -f, --main-jar, -j, --class, -c
    private static final String [] BLOCKED_JPACKAGE_OPTIONS = {"--output", "-o", "--input", "-i",
                                                                "--files", "-f", "--main-jar",
                                                                "-j", "--class", "-c"};

    private static final String RUNTIME_IMAGE_OPTION = "--runtime-image";

    private static final String ERR_UNKNOWN_OPTION = "Unknown option: ";
    private static final String ERR_MISSING_VALUE = "Value is required for option ";
    private static final String ERR_MISSING_MODE = "Error: create-image or create-installer mode is required";
    private static final String ERR_MISSING_JNLP = "Error: --jnlp is required";
    private static final String ERR_MISSING_OUTPUT = "Error: --output is required";
    private static final String ERR_OUTPUT_EXISTS = "Error: output folder already exists";
    private static final String ERR_KEEP_EXISTS = "Error: folder for --keep argument already exists";
    private static final String ERR_INVALID_PROTOCOL_JNLP = "Error: Invalid protocol for JNLP file. Only HTTP, HTTPS and FILE protocols are supported.";

    public boolean createImage() {
        return createImage;
    }

    public boolean createInstaller() {
        return createInstaller;
    }

    public String getInstallerType() {
        return installerType;
    }

    public String getJNLP() {
        return jnlp;
    }

    public String getOutput() {
        return output;
    }

    public String keep() {
        return keep;
    }

    public boolean help() {
        return help;
    }

    public boolean verbose() {
        return verbose;
    }

    public boolean version() {
        return version;
    }

    public List<String> getJPackageOptions() {
        return jpackageOptions;
    }

    public boolean isRuntimeImageSet() {
        return isRuntimeImageSet;
    }

    // Helper method to dump all options
    private void display() {
        System.out.println("Options:");
        System.out.println("createImage: " + createImage);
        System.out.println("createInstaller: " + createInstaller);
        System.out.println("installerType: " + installerType);
        System.out.println("jnlp: " + jnlp);
        System.out.println("output: " + output);
        System.out.println("keep: " + keep);
        System.out.println("help: " + help);
        System.out.println("verbose: " + verbose);
        System.out.println("version: " + version);
        for (int i = 0; i < jpackageOptions.size(); i++) {
            System.out.println("jpackageOptions[" + i + "]: " + jpackageOptions.get(i));
        }
    }

    private void validate() {
        if (help || version) {
            return;
        }

        if (!createImage && !createInstaller) {
            optionError(ERR_MISSING_MODE);
        }

        if (jnlp == null) {
            optionError(ERR_MISSING_JNLP);
        } else {
            int index = jnlp.indexOf(":");
            if (index == -1 || index == 0) {
                optionError(ERR_INVALID_PROTOCOL_JNLP);
            } else {
                String protocol = jnlp.substring(0, index);
                if (!protocol.equalsIgnoreCase("http") &&
                    !protocol.equalsIgnoreCase("https") &&
                    !protocol.equalsIgnoreCase("file")) {
                    optionError(ERR_INVALID_PROTOCOL_JNLP);
                }
            }
        }

        if (output == null) {
            optionError(ERR_MISSING_OUTPUT);
        } else {
            File file = new File(output);
            if (file.exists()) {
                optionErrorNoHelp(ERR_OUTPUT_EXISTS);
            }
        }

        if (keep != null) {
            File file = new File(keep);
            if (file.exists()) {
                optionErrorNoHelp(ERR_KEEP_EXISTS);
            }
        }

        jpackageOptions.forEach((option) -> {
            if (isBlockedOption(option)) {
                Log.error(option + " is not allowed via --jpackage-options, since it will conflict with "
                        + "same option generated by JNLPConverter.");
            }
        });
    }

    public boolean isOptionPresent(String option) {
        for (String jpackageOption : jpackageOptions) {
            if (jpackageOption.equalsIgnoreCase(option)) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlockedOption(String option) {
        for (String blockedOption : BLOCKED_JPACKAGE_OPTIONS) {
            if (blockedOption.equalsIgnoreCase(option)) {
                return true;
            }
        }

        return false;
    }

    public static void showHelp() {
//      System.out.println("********* Help should not be longer then 80 characters as per JEP-293 *********");
        System.out.println("Usage: java -jar JNLPConverter.jar <mode> <options>");
        System.out.println("");
        System.out.println("where mode is one of:");
        System.out.println("  create-image");
        System.out.println("          Generates a platform-specific application image.");
        System.out.println("  create-installer <type>");
        System.out.println("          Generates a platform-specific installer for the application.");
        System.out.println("          Valid values for \"type\" are \"msi\", \"rpm\", \"deb\", \"dmg\", \"pkg\",");
        System.out.println("          \"pkg-app-store\". If \"type\" is omitted, all supported types of installable");
        System.out.println("          packages for current platform will be generated.");
        System.out.println("");
        System.out.println("Possible options include:");
        System.out.println("  -j, --jnlp <path>");
        System.out.println("          Full path to JNLP file. Supported protocols are HTTP/HTTPS/FILE.");
        System.out.println("  -o, --output <path>");
        System.out.println("          Name of the directory where generated output files are placed.");
        System.out.println("  -k, --keep <path>");
        System.out.println("          Keep JNLP, JARs and command line arguments for jpackage");
        System.out.println("          in directory provided.");
        System.out.println("      --jpackage-options <options>");
        System.out.println("          Specify additional jpackage options or overwrite provided by JNLPConverter.");
        System.out.println("          All jpackage options can be specified except: --output -o, --input -i,");
        System.out.println("          --files -f, --main-jar -j and --class -c.");
        System.out.println("  -h, --help, -?");
        System.out.println("          Print this help message");
        System.out.println("  -v, --verbose");
        System.out.println("          Enable verbose output.");
        System.out.println("      --version");
        System.out.println("          Version information.");
        System.out.println("To specify an argument for a long option, you can use --<name>=<value> or");
        System.out.println("--<name> <value>.");
        System.out.println("To specify proxy server use standard Java properties http.proxyHost and http.proxyPort.");
    }

    private static boolean isInstallerType(String type) {
        for (String installerType : INSTALLER_TYPES) {
            if (installerType.equals(type)) {
                return true;
            }
        }

        return false;
    }

    public static Options parseArgs(String[] args) {
        Options options = new Options();

        int index = 0;
        if (args.length >= 1) {
            switch (args[0]) {
                case "create-image":
                    options.createImage = true;
                    index = 1;
                    break;
                case "create-installer":
                    options.createInstaller = true;
                    index = 1;
                    if (args.length >= 2) {
                        if (isInstallerType(args[1])) {
                            options.installerType = args[1];
                            index = 2;
                        }
                    }
                    break;
                case "-h":
                case "--help":
                case "-?":
                case "--version":
                    break;
                default:
                    optionError(Options.ERR_MISSING_MODE);
                    break;
            }
        }

        for (int i = index; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--jnlp")) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, "--jnlp");
                }
                options.jnlp = args[i];
            }  else if (arg.startsWith(JNLP_OPTION_PREFIX)) {
                options.jnlp = arg.substring(JNLP_OPTION_PREFIX.length());
            } else if (arg.equals("--output")) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, "--output");
                }
                options.output = args[i];
            } else if (arg.startsWith(OUTPUT_OPTION_PREFIX)) {
                options.output = arg.substring(OUTPUT_OPTION_PREFIX.length());
            } else if (arg.equals("--keep")) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, "--keep");
                }
                options.keep = args[i];
            } else if (arg.startsWith(KEEP_OPTION_PREFIX)) {
                options.keep = arg.substring(KEEP_OPTION_PREFIX.length());
            } else if (arg.equals("--help")) {
                options.help = true;
            } else if (arg.equals("--verbose")) {
                options.verbose = true;
            } else if (arg.equals("--version")) {
                options.version = true;
            } else if (arg.equals("-j")) { // short options
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, "-j");
                }
                options.jnlp = args[i];
            } else if (arg.startsWith(JNLP_OPTION_SHORT_PREFIX)) {
                options.jnlp = arg.substring(JNLP_OPTION_SHORT_PREFIX.length());
            } else if (arg.equals("-o")) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, "-o");
                }
                options.output = args[i];
            } else if (arg.startsWith(OUTPUT_OPTION_SHORT_PREFIX)) {
                options.output = arg.substring(OUTPUT_OPTION_SHORT_PREFIX.length());
            } else if (arg.equals("-k")) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, "-k");
                }
                options.keep = args[i];
            } else if (arg.startsWith(KEEP_OPTION_SHORT_PREFIX)) {
                options.keep = arg.substring(KEEP_OPTION_SHORT_PREFIX.length());
            } else if (arg.equals("-h") || arg.equals("-?")) {
                options.help = true;
            } else if (arg.equals("-v")) {
                options.verbose = true;
            } else if (arg.equals("--jpackage-options")) {
                for (i = (i + 1); i < args.length; i++) {
                    if (!options.isRuntimeImageSet) {
                        if (args[i].equals(RUNTIME_IMAGE_OPTION)) {
                            options.isRuntimeImageSet = true;
                        }
                    }
                    options.jpackageOptions.add(args[i]);
                }
            } else {
                optionError(ERR_UNKNOWN_OPTION, arg);
            }
        }

        //options.display(); // For testing only
        options.validate();

        return options;
    }

    private static void optionErrorNoHelp(String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    private static void optionError(String msg) {
        System.out.println(msg);
        System.out.println();
        showHelp();
        System.exit(1);
    }

    private static void optionError(String msg, String option) {
        System.out.println(msg + option);
        System.out.println();
        showHelp();
        System.exit(1);
    }
}
