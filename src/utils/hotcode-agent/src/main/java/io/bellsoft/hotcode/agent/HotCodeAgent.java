package io.bellsoft.hotcode.agent;

import io.bellsoft.hotcode.dcmd.CompilerDirectives;
import io.bellsoft.hotcode.dcmd.CompilerDirectivesControl;
import io.bellsoft.hotcode.dcmd.CompilerDirectivesControl.DirectivesException;
import io.bellsoft.hotcode.profiling.Method;
import io.bellsoft.hotcode.profiling.MethodProfilePrinter;
import io.bellsoft.hotcode.profiling.Profile;
import io.bellsoft.hotcode.profiling.Profiling;
import io.bellsoft.hotcode.profiling.TopKProfile;
import io.bellsoft.hotcode.profiling.jfr.JfrLiveProfiling;
import io.bellsoft.hotcode.util.SimpleThreadFactory;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.management.HotSpotDiagnosticMXBean;

public class HotCodeAgent {

    private final static Logger LOGGER = Logger.getLogger(HotCodeAgent.class.getSimpleName());

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
        profile = new TopKProfile<>(config.top);
        profiling = new JfrLiveProfiling(config.samplingInterval, config.profilingDuration);
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

        if (config.profilingPeriod.isZero()) {
            service.schedule(
                    new ProfileAndOptimize(),
                    config.profilingDelay.toMillis(),
                    TimeUnit.MILLISECONDS);
        } else {
            service.scheduleAtFixedRate(
                    new ProfileAndOptimize(),
                    config.profilingDelay.toMillis(),
                    config.profilingPeriod.toMillis(),
                    TimeUnit.MILLISECONDS);
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

    private class ProfileAndOptimize implements Runnable {
        List<Method> oldTop = List.of();

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
                if (config.print) {
                    profilePrinter.print(profile, config.top);
                }

                // reduce initial overhead
                int fetchCount = oldTop.isEmpty() ? config.chunk : config.top;
                var newTop = profile.getTop(fetchCount);

                var dcmdResult = CompilerDirectivesControl.clear(false); // no refresh
                LOGGER.log(Level.INFO, dcmdResult);

                var diff = new LinkedHashSet<Method>(oldTop);
                diff.removeAll(newTop);
                var methodsToRemove = List.copyOf(diff);

                diff = new LinkedHashSet<Method>(oldTop);
                diff.removeAll(methodsToRemove);
                var methodsToKeep = List.copyOf(diff);
                LOGGER.log(Level.INFO, "{0} methods stay hot", methodsToKeep.size());

                update(methodsToKeep, true, false); // hot, no refresh

                LOGGER.log(Level.INFO, "{0} methods became cold", methodsToRemove.size());

                update(methodsToRemove, false, true); // cold, refresh

                diff = new LinkedHashSet<Method>(newTop); // preserve order to select hottest ones
                diff.removeAll(oldTop);
                var methodsToAdd = List.copyOf(diff);
                LOGGER.log(Level.INFO, "{0} new hot methods", methodsToAdd.size());

                methodsToAdd = methodsToAdd.subList(0, Math.min(config.chunk, methodsToAdd.size()));
                LOGGER.log(Level.INFO, "Adding directives for {0} hot methods", methodsToAdd.size());

                update(methodsToAdd, true, true); // hot, refresh

                oldTop = new ArrayList<Method>(methodsToKeep.size() + methodsToAdd.size());
                oldTop.addAll(methodsToKeep);
                oldTop.addAll(methodsToAdd);

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
