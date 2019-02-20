/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jpackage.internal;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Optional;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.module.Configuration;
import java.lang.module.ResolvedModule;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;

import jdk.tools.jlink.internal.packager.AppRuntimeImageBuilder;

final class JLinkBundlerHelper {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MainResources");
    private static final String JRE_MODULES_FILENAME =
            "jdk/jpackage/internal/resources/jre.list";
    private static final String SERVER_JRE_MODULES_FILENAME =
            "jdk/jpackage/internal/resources/jre.module.list";

    private JLinkBundlerHelper() {}

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<String> JLINK_BUILDER =
            new StandardBundlerParam<>(
                    I18N.getString("param.jlink-builder.name"),
                    I18N.getString("param.jlink-builder.description"),
                    "jlink.builder",
                    String.class,
                    null,
                    (s, p) -> s);

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<Integer> DEBUG =
            new StandardBundlerParam<>(
                    "",
                    "",
                    "-J-Xdebug",
                    Integer.class,
                    p -> null,
                    (s, p) -> {
                        return Integer.valueOf(s);
                    });

    static String listOfPathToString(List<Path> value) {
        String result = "";

        for (Path path : value) {
            if (result.length() > 0) {
                result += File.pathSeparator;
            }

            result += path.toString();
        }

        return result;
    }

    static String setOfStringToString(Set<String> value) {
        String result = "";

        for (String element : value) {
            if (result.length() > 0) {
                result += ",";
            }

            result += element;
        }

        return result;
    }

    static File getMainJar(Map<String, ? super Object> params) {
        File result = null;
        RelativeFileSet fileset =
                StandardBundlerParam.MAIN_JAR.fetchFrom(params);

        if (fileset != null) {
            String filename = fileset.getIncludedFiles().iterator().next();
            result = fileset.getBaseDirectory().toPath().
                    resolve(filename).toFile();

            if (result == null || !result.exists()) {
                String srcdir =
                    StandardBundlerParam.SOURCE_DIR.fetchFrom(params);

                if (srcdir != null) {
                    result = new File(srcdir + File.separator + filename);
                }
            }
        }

        return result;
    }

    static String getMainClass(Map<String, ? super Object> params) {
        String result = "";
        String mainModule = StandardBundlerParam.MODULE.fetchFrom(params);
        if (mainModule != null)  {
            int index = mainModule.indexOf("/");
            if (index > 0) {
                result = mainModule.substring(index + 1);
            }
        } else {
            RelativeFileSet fileset =
                    StandardBundlerParam.MAIN_JAR.fetchFrom(params);
            if (fileset != null) {
                result = StandardBundlerParam.MAIN_CLASS.fetchFrom(params);
            } else {
                // possibly app-image
            }
        }

        return result;
    }

    static String getMainModule(Map<String, ? super Object> params) {
        String result = null;
        String mainModule = StandardBundlerParam.MODULE.fetchFrom(params);

        if (mainModule != null) {
            int index = mainModule.indexOf("/");

            if (index > 0) {
                result = mainModule.substring(0, index);
            } else {
                result = mainModule;
            }
        }

        return result;
    }

    static String getJDKVersion(Map<String, ? super Object> params) {
        String result = "";
        List<Path> modulePath =
                StandardBundlerParam.MODULE_PATH.fetchFrom(params);
        Set<String> limitModules =
                StandardBundlerParam.LIMIT_MODULES.fetchFrom(params);
        Path javaBasePath = findPathOfModule(modulePath,
                "java.base.jmod").resolve("java.base.jmod");
        Set<String> addModules = getValidModules(modulePath,
                StandardBundlerParam.ADD_MODULES.fetchFrom(params),
                limitModules);


        if (javaBasePath != null && javaBasePath.toFile().exists()) {
            result = getModuleVersion(javaBasePath.toFile(),
                    modulePath, addModules, limitModules);
        }
        return result;
    }

    static Path getJDKHome(Map<String, ? super Object> params) {
        Path result = null;
        List<Path> modulePath =
                StandardBundlerParam.MODULE_PATH.fetchFrom(params);
        Path javaBasePath = findPathOfModule(modulePath, "java.base.jmod");

        if (javaBasePath != null && javaBasePath.toFile().exists()) {
            result = javaBasePath.getParent();

            // On a developer build the JDK Home isn't where we expect it
            // relative to the jmods directory. Do some extra
            // processing to find it.
            if (result != null) {
                boolean found = false;
                Path bin = result.resolve("bin");

                if (Files.exists(bin)) {
                    final String exe =
                            (Platform.getPlatform() == Platform.WINDOWS) ?
                            ".exe" : "";
                    Path javaExe = bin.resolve("java" + exe);

                    if (Files.exists(javaExe)) {
                        found = true;
                    }
                }

                if (!found) {
                    result = result.resolve(".." + File.separator + "jdk");
                }
            }
        }

        return result;
    }

    private static Set<String> getValidModules(List<Path> modulePath,
            Set<String> addModules, Set<String> limitModules) {
        ModuleHelper moduleHelper = new ModuleHelper(
                modulePath, addModules, limitModules);
        return removeInvalidModules(modulePath, moduleHelper.modules());
    }

    static void execute(Map<String, ? super Object> params,
            AbstractAppImageBuilder imageBuilder)
            throws IOException, Exception {
        List<Path> modulePath =
                StandardBundlerParam.MODULE_PATH.fetchFrom(params);
        Set<String> addModules =
                StandardBundlerParam.ADD_MODULES.fetchFrom(params);
        Set<String> limitModules =
                StandardBundlerParam.LIMIT_MODULES.fetchFrom(params);
        boolean stripNativeCommands =
                StandardBundlerParam.STRIP_NATIVE_COMMANDS.fetchFrom(params);
        Path outputDir = imageBuilder.getRoot();
        String excludeFileList = imageBuilder.getExcludeFileList();
        File mainJar = getMainJar(params);
        ModFile.ModType mainJarType = ModFile.ModType.Unknown;

        if (mainJar != null) {
            mainJarType = new ModFile(mainJar).getModType();
        } else if (StandardBundlerParam.MODULE.fetchFrom(params) == null) {
            // user specified only main class, all jars will be on the classpath
            mainJarType = ModFile.ModType.UnnamedJar;
        }

        // Modules
        String mainModule = getMainModule(params);
        if (mainJarType == ModFile.ModType.UnnamedJar) {
            // The default for an unnamed jar is ALL_DEFAULT
            addModules.add(ModuleHelper.ALL_DEFAULT);
        } else if (mainJarType == ModFile.ModType.Unknown ||
                mainJarType == ModFile.ModType.ModularJar) {
            if (mainModule == null) {
                addModules.add(ModuleHelper.ALL_DEFAULT);
            }
        } 

        Set<String> validModules =
                  getValidModules(modulePath, addModules, limitModules);
        if (mainModule != null) {
            validModules.add(mainModule);
        }

        Log.verbose(MessageFormat.format(
                I18N.getString("message.modules"), validModules.toString()));

        AppRuntimeImageBuilder appRuntimeBuilder = new AppRuntimeImageBuilder();
        appRuntimeBuilder.setOutputDir(outputDir);
        appRuntimeBuilder.setModulePath(modulePath);
        appRuntimeBuilder.setAddModules(validModules);
        appRuntimeBuilder.setLimitModules(limitModules);
        appRuntimeBuilder.setExcludeFileList(excludeFileList);
        appRuntimeBuilder.setStripNativeCommands(stripNativeCommands);
        appRuntimeBuilder.setUserArguments(new HashMap<String,String>());

        appRuntimeBuilder.build();
        imageBuilder.prepareApplicationFiles();
    }

    static void generateJre(Map<String, ? super Object> params,
            AbstractAppImageBuilder imageBuilder)
            throws IOException, Exception {
        List<Path> modulePath =
                StandardBundlerParam.MODULE_PATH.fetchFrom(params);
        Set<String> addModules =
                StandardBundlerParam.ADD_MODULES.fetchFrom(params);
        Set<String> limitModules =
                StandardBundlerParam.LIMIT_MODULES.fetchFrom(params);
        boolean stripNativeCommands =
                StandardBundlerParam.STRIP_NATIVE_COMMANDS.fetchFrom(params);
        Path outputDir = imageBuilder.getRoot();
        addModules.add(ModuleHelper.ALL_MODULE_PATH);
        Set<String> redistModules = getValidModules(modulePath,
                addModules, limitModules);
        addModules.addAll(redistModules);

        Log.verbose(MessageFormat.format(
                I18N.getString("message.modules"), addModules.toString()));

        AppRuntimeImageBuilder appRuntimeBuilder = new AppRuntimeImageBuilder();
        appRuntimeBuilder.setOutputDir(outputDir);
        appRuntimeBuilder.setModulePath(modulePath);
        appRuntimeBuilder.setAddModules(addModules);
        appRuntimeBuilder.setLimitModules(limitModules);
        appRuntimeBuilder.setStripNativeCommands(stripNativeCommands);
        appRuntimeBuilder.setExcludeFileList("");
        appRuntimeBuilder.setUserArguments(new HashMap<String,String>());

        appRuntimeBuilder.build();
        imageBuilder.prepareJreFiles();
    }

    // Returns the path to the JDK modules in the user defined module path.
    static Path findPathOfModule( List<Path> modulePath, String moduleName) {

        for (Path path : modulePath) {
            Path moduleNamePath = path.resolve(moduleName);

            if (Files.exists(moduleNamePath)) {
                return path;
            }
        }

        return null;
    }

    /*
     * Returns the set of modules that would be visible by default for
     * a non-modular-aware application consisting of the given elements.
     */
    private static Set<String> getDefaultModules(
            Path[] paths, String[] addModules) {

        // the modules in the run-time image that export an API
        Stream<String> systemRoots = ModuleFinder.ofSystem().findAll().stream()
                .map(ModuleReference::descriptor)
                .filter(descriptor -> exportsAPI(descriptor))
                .map(ModuleDescriptor::name);

        Set<String> roots;
        if (addModules == null || addModules.length == 0) {
            roots = systemRoots.collect(Collectors.toSet());
        } else {
            var extraRoots =  Stream.of(addModules);
            roots = Stream.concat(systemRoots,
                    extraRoots).collect(Collectors.toSet());
        }

        ModuleFinder finder = ModuleFinder.ofSystem();
        if (paths != null && paths.length > 0) {
            finder = ModuleFinder.compose(finder, ModuleFinder.of(paths));
        }
        return Configuration.empty()
                .resolveAndBind(finder, ModuleFinder.of(), roots)
                .modules()
                .stream()
                .map(ResolvedModule::name)
                .collect(Collectors.toSet());
    } 

    /*
     * Returns true if the given module exports an API to all module.
     */
    private static boolean exportsAPI(ModuleDescriptor descriptor) {
        return descriptor.exports()
                .stream()
                .filter(e -> !e.isQualified())
                .findAny()
                .isPresent();
    }

    private static Set<String> removeInvalidModules(
            List<Path> modulePath, Set<String> modules) {
        Set<String> result = new LinkedHashSet<String>();
        ModuleManager mm = new ModuleManager(modulePath);
        List<ModFile> lmodfiles =
                mm.getModules(EnumSet.of(ModuleManager.SearchType.ModularJar,
                        ModuleManager.SearchType.Jmod,
                        ModuleManager.SearchType.ExplodedModule));

        HashMap<String, ModFile> validModules = new HashMap<>();

        for (ModFile modFile : lmodfiles) {
            validModules.put(modFile.getModName(), modFile);
        }

        for (String name : modules) {
            if (validModules.containsKey(name)) {
                result.add(name);
            } else {
                Log.error(MessageFormat.format(
                        I18N.getString("warning.module.does.not.exist"), name));
            }
        }

        return result;
    }

    private static String getModuleVersion(File moduleFile,
            List<Path> modulePath, Set<String> addModules,
            Set<String> limitModules) {
        String result = "";

        ModFile modFile = new ModFile(moduleFile);
        ModuleFinder finder = AppRuntimeImageBuilder.moduleFinder(modulePath,
                addModules, limitModules);
        Optional<ModuleReference> mref = finder.find(modFile.getModName());

        if (mref.isPresent()) {
            ModuleDescriptor descriptor = mref.get().descriptor();

            if (descriptor != null) {
                Optional<ModuleDescriptor.Version> version =
                        descriptor.version();

                if (version.isPresent()) {
                    result = version.get().toString();
                }
            }
        }
        return result;
    }

    private static class ModuleHelper {
        // The token for "all modules on the module path".
        private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";

        // The token for "all valid runtime modules".
        static final String ALL_DEFAULT = "ALL-DEFAULT";

        private final Set<String> modules = new HashSet<>();
        private enum Macros {None, AllModulePath, AllRuntime}

        ModuleHelper(List<Path> paths, Set<String> addModules,
                Set<String> limitModules) {
            boolean addAllModulePath = false;
            boolean addDefaultMods = false;
            
            for (Iterator<String> iterator = addModules.iterator();
                    iterator.hasNext();) {
                String module = iterator.next();

                switch (module) {
                    case ALL_MODULE_PATH:
                        iterator.remove();
                        addAllModulePath = true;
                        break;
                    case ALL_DEFAULT:
                        iterator.remove();
                        addDefaultMods = true;
                        break;
                    default:
                        this.modules.add(module);
                }
            }

            if (addAllModulePath) {
                this.modules.addAll(getModuleNamesFromPath(paths));
            } else if (addDefaultMods) {
                this.modules.addAll(getDefaultModules(
                        paths.toArray(new Path[0]),
                        addModules.toArray(new String[0])));
            }
        }

        Set<String> modules() {
            return modules;
        }

        private static Set<String> getModuleNamesFromPath(List<Path> Value) {
            Set<String> result = new LinkedHashSet<String>();
            ModuleManager mm = new ModuleManager(Value);
            List<ModFile> modFiles = mm.getModules(
                    EnumSet.of(ModuleManager.SearchType.ModularJar,
                    ModuleManager.SearchType.Jmod,
                    ModuleManager.SearchType.ExplodedModule));

            for (ModFile modFile : modFiles) {
                result.add(modFile.getModName());
            }
            return result;
        }
    }
}
