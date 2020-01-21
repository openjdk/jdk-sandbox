/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

    private String type = null;
    private String jnlp = null;
    private String output = null;
    private String keep = null;
    private boolean help = false;
    private boolean verbose = false;
    private boolean version = false;
    private final List<String> jpackageOptions = new ArrayList<>();
    private boolean isRuntimeImageSet = false;

    private static final String TYPE_OPTION = "--type";
    private static final String JNLP_OPTION = "--jnlp";
    private static final String OUTPUT_OPTION = "--dest";
    private static final String KEEP_OPTION = "--keep";
    private static final String JPACKAGE_OPTIONS_OPTION = "--jpackage-options";
    private static final String HELP_OPTION = "--help";
    private static final String VERBOSE_OPTION = "--verbose";
    private static final String VERSION_OPTION = "--version";

    private static final String TYPE_OPTION_SHORT = "-t";
    private static final String JNLP_OPTION_SHORT = "-j";
    private static final String OUTPUT_OPTION_SHORT = "-d";
    private static final String KEEP_OPTION_SHORT = "-k";
    private static final String HELP_OPTION_SHORT = "-h";
    private static final String VERBOSE_OPTION_SHORT = "-v";

    private static final String [] TYPES = {"app-image", "msi", "exe", "dmg", "pkg",
                                            "rpm", "deb"};

    // --dest, -d, --input, -i, --main-jar, --main-class
    private static final String [] BLOCKED_JPACKAGE_OPTIONS = {"--dest", "-d", "--input", "-i",
                                                               "--main-jar", "--main-class"};

    private static final String RUNTIME_IMAGE_OPTION = "--runtime-image";

    private static final String ERR_UNKNOWN_OPTION = "Unknown option: ";
    private static final String ERR_UNKNOWN_TYPE = "Unknown type: ";
    private static final String ERR_MISSING_VALUE = "Value is required for option ";
    private static final String ERR_MISSING_JNLP = "Error: --jnlp is required";
    private static final String ERR_MISSING_OUTPUT = "Error: --dest is required";
    private static final String ERR_OUTPUT_EXISTS = "Error: destination folder already exists";
    private static final String ERR_KEEP_EXISTS = "Error: folder for --keep argument already exists";
    private static final String ERR_INVALID_PROTOCOL_JNLP = "Error: Invalid protocol for JNLP file. Only HTTP, HTTPS and FILE protocols are supported.";

    public String getType() {
        return type;
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
        System.out.println("type: " + type);
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

    private static String getPlatformTypes() {
        String types = "\"app-image\"";
        if (Platform.isWindows()) {
            types += " ,";
            types += "\"msi\"";
            types += " ,";
            types += "\"exe\"";
        } else if (Platform.isMac()) {
            types += " ,";
            types += "\"pkg\"";
            types += " ,";
            types += "\"dmg\"";
        } else if (Platform.isLinux()) {
            types += " ,";
            types += "\"rpm\"";
            types += " ,";
            types += "\"deb\"";
        }

        return types;
    }

    public static void showHelp() {
//      System.out.println("********* Help should not be longer then 80 characters as per JEP-293 *********");
        System.out.println("Usage: java -jar JNLPConverter.jar <options>");
        System.out.println("Possible options include:");
        System.out.println("  -t, --type <type>");
        System.out.println("          The type of package to create.");
        System.out.println("          Valid values are: {" + getPlatformTypes() + "}");
        System.out.println("          If this option is not specified a platform dependent");
        System.out.println("          default type will be created.");
        System.out.println("  -j, --jnlp <path>");
        System.out.println("          Full path to JNLP file.");
        System.out.println("          Supported protocols are HTTP/HTTPS/FILE.");
        System.out.println("  -d, --dest <path>");
        System.out.println("          Name of the directory where generated output files are placed.");
        System.out.println("  -k, --keep <path>");
        System.out.println("          Keep JNLP, JARs and command line arguments for jpackage");
        System.out.println("          in directory provided.");
        System.out.println("      --jpackage-options <options>");
        System.out.println("          Specify additional jpackage options or overwrite provided by JNLPConverter.");
        System.out.println("          All jpackage options can be specified except: --dest -d, --input -i,");
        System.out.println("          --main-jar -j and --class -c.");
        System.out.println("  -h, --help");
        System.out.println("          Print this help message.");
        System.out.println("  -v, --verbose");
        System.out.println("          Enable verbose output.");
        System.out.println("      --version");
        System.out.println("          Version information.");
        System.out.println("To specify an argument for a long option, you can use --<name>=<value> or");
        System.out.println("--<name> <value>.");
        System.out.println("To specify proxy server use standard Java properties http.proxyHost and http.proxyPort.");
    }

    private static boolean isValidType(String t) {
        for (String type : TYPES) {
            if (type.equals(t)) {
                return true;
            }
        }

        return false;
    }

    public static Options parseArgs(String[] args) {
        Options options = new Options();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals(TYPE_OPTION) || arg.equals(TYPE_OPTION_SHORT)) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, arg);
                }
                options.type = args[i];
                if (!isValidType(options.type)) {
                    optionError(Options.ERR_UNKNOWN_TYPE, options.type);
                }
            } else if (arg.startsWith(TYPE_OPTION + "=")) {
                options.type = arg.substring(TYPE_OPTION.length() + 1);
                if (!isValidType(options.type)) {
                    optionError(Options.ERR_UNKNOWN_TYPE, options.type);
                }
            } else if (arg.startsWith(TYPE_OPTION_SHORT)) {
                options.type = arg.substring(TYPE_OPTION_SHORT.length());
                if (!isValidType(options.type)) {
                    optionError(Options.ERR_UNKNOWN_TYPE, options.type);
                }
            } else if (arg.equals(JNLP_OPTION) || arg.equals(JNLP_OPTION_SHORT)) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, arg);
                }
                options.jnlp = args[i];
            } else if (arg.startsWith(JNLP_OPTION + "=")) {
                options.jnlp = arg.substring(JNLP_OPTION.length() + 1);
            } else if (arg.startsWith(JNLP_OPTION_SHORT)) {
                options.jnlp = arg.substring(JNLP_OPTION_SHORT.length());
            } else if (arg.equals(OUTPUT_OPTION) || arg.equals(OUTPUT_OPTION_SHORT)) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, arg);
                }
                options.output = args[i];
            } else if (arg.startsWith(OUTPUT_OPTION + "=")) {
                options.output = arg.substring(OUTPUT_OPTION.length() + 1);
            } else if (arg.startsWith(OUTPUT_OPTION_SHORT)) {
                options.output = arg.substring(OUTPUT_OPTION_SHORT.length());
            } else if (arg.equals(KEEP_OPTION) || arg.equals(KEEP_OPTION_SHORT)) {
                if (++i >= args.length) {
                    optionError(Options.ERR_MISSING_VALUE, arg);
                }
                options.keep = args[i];
            } else if (arg.startsWith(KEEP_OPTION + "=")) {
                options.keep = arg.substring(KEEP_OPTION.length() + 1);
            } else if (arg.startsWith(KEEP_OPTION_SHORT)) {
                options.keep = arg.substring(KEEP_OPTION_SHORT.length());
            } else if (arg.equals(HELP_OPTION) || arg.equals(HELP_OPTION_SHORT)) {
                options.help = true;
            } else if (arg.equals(VERBOSE_OPTION) || arg.equals(VERBOSE_OPTION_SHORT)) {
                options.verbose = true;
            } else if (arg.equals(VERSION_OPTION)) {
                options.version = true;
            } else if (arg.equals(JPACKAGE_OPTIONS_OPTION)) {
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
