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

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.bellsw.hotcode.dcmd.CompilerDirectives;
import com.bellsw.hotcode.dcmd.CompilerDirectivesControl;
import com.bellsw.hotcode.dcmd.CompilerDirectivesControl.DirectivesException;
import com.bellsw.hotcode.profiling.Method;
import com.bellsw.hotcode.profiling.MethodProfilePrinter;
import com.bellsw.hotcode.profiling.Profile;
import com.bellsw.hotcode.profiling.Profiling;
import com.bellsw.hotcode.profiling.TopKProfile;
import com.bellsw.hotcode.profiling.jfr.JfrLiveProfiling;
import com.bellsw.hotcode.util.ListUtils;
import com.bellsw.hotcode.util.SimpleThreadFactory;
import com.sun.management.HotSpotDiagnosticMXBean;

public final class HotCodeAgent {

    private static final Logger LOGGER = Logger.getLogger(HotCodeAgent.class.getName());

    private final HotCodeAgentConfiguration config;
    private final ScheduledExecutorService service;
    private final Profile<Method> profile;
    private final Profiling profiling;
    private final MethodProfilePrinter profilePrinter;

    private HotCodeAgent(String argumentString) {
        var settings = new Properties();
        parseArgs(argumentString, settings);
        config = HotCodeAgentConfiguration.from(settings);
        service = Executors.newSingleThreadScheduledExecutor(new SimpleThreadFactory("hotcode-optimization", true));
        profile = new TopKProfile<>(config.top());
        profiling = new JfrLiveProfiling(config.samplingInterval(), config.profilingDuration());
        profilePrinter = new MethodProfilePrinter(System.out);
    }

    public static void premain(String argumentString) {
        new HotCodeAgent(argumentString).run();
    }

    public static void agentmain(String argumentString) {
        new HotCodeAgent(argumentString).run();
    }

    public void run() {
        LOGGER.log(Level.INFO, "Running the agent");

        var diagnosticBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        var hotCodeOption = diagnosticBean.getVMOption("HotCodeHeap");
        if (!Boolean.parseBoolean(hotCodeOption.getValue())) {
            LOGGER.log(Level.SEVERE, "Run the JVM with -XX:+HotCodeHeap");
            return;
        }

        if (config.profilingPeriod().isZero()) {
            service.schedule(new ProfileAndOptimize(), config.profilingDelay().toMillis(), TimeUnit.MILLISECONDS);
        } else {
            service.scheduleAtFixedRate(new ProfileAndOptimize(), config.profilingDelay().toMillis(),
                    config.profilingPeriod().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static void parseArgs(String argumentString, Properties properties) {
        if (argumentString != null) {
            var arguments = argumentString.split(",");
            for (var argument : arguments) {
                int idx = argument.indexOf('=');
                if (idx >= 0) {
                    var key = argument.substring(0, idx);
                    var value = argument.substring(idx + 1);
                    properties.put(key, value);
                } else {
                    properties.put(argument, "");
                }
            }
        }
    }

    private final class ProfileAndOptimize implements Runnable {
        private List<Method> oldTop = List.of();

        private static void update(List<Method> methods, boolean hot, boolean refresh) throws DirectivesException {
            if (!methods.isEmpty()) {
                var directives = CompilerDirectives.build(methods, hot);
                var dcmdResult = CompilerDirectivesControl.add(directives, refresh);
                LOGGER.log(Level.FINE, dcmdResult);
            }
        }

        @Override
        public void run() {
            try {
                LOGGER.log(Level.INFO, "Starting profiling");

                profiling.fill(profile);
                LOGGER.log(Level.INFO, "Determined {0} hottest methods", profile.size());
                if (config.print()) {
                    profilePrinter.print(profile, config.top());
                }

                // reduce initial overhead
                int fetchCount = oldTop.isEmpty() ? config.chunk() : config.top();
                var newTop = profile.getTop(fetchCount);

                var dcmdResult = CompilerDirectivesControl.clear(false); // no refresh
                LOGGER.log(Level.INFO, dcmdResult);

                var methodsToRemove = ListUtils.diff(oldTop, newTop);

                var methodsToKeep = ListUtils.diff(oldTop, methodsToRemove);
                LOGGER.log(Level.INFO, "{0} methods stay hot", methodsToKeep.size());
                update(methodsToKeep, true, false); // hot, no refresh

                LOGGER.log(Level.INFO, "{0} methods became cold", methodsToRemove.size());
                update(methodsToRemove, false, true); // cold, refresh

                var methodsToAdd = ListUtils.diff(newTop, oldTop); // preserve order to select hottest ones
                LOGGER.log(Level.INFO, "{0} new hot methods", methodsToAdd.size());
                methodsToAdd = ListUtils.limit(methodsToAdd, config.chunk());
                LOGGER.log(Level.INFO, "Adding directives for {0} hot methods", methodsToAdd.size());
                update(methodsToAdd, true, true); // hot, refresh

                oldTop = ListUtils.concat(methodsToKeep, methodsToAdd);

                LOGGER.log(Level.INFO, "Replaced compiler directives");
                // profile.clear();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE,
                        "an error occurred during the profiling task execution, further profiling is cancelled", e);
                throw new RuntimeException(e);
            }
        }
    }

}
