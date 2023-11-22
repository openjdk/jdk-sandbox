package io.bellsoft.hotcode.agent;

import io.bellsoft.hotcode.dcmd.CompilerDirectives;
import io.bellsoft.hotcode.dcmd.CompilerDirectivesControl;
import io.bellsoft.hotcode.profiling.Method;
import io.bellsoft.hotcode.profiling.MethodProfilePrinter;
import io.bellsoft.hotcode.profiling.Profile;
import io.bellsoft.hotcode.profiling.Profiling;
import io.bellsoft.hotcode.profiling.TopKProfile;
import io.bellsoft.hotcode.profiling.jfr.JfrLiveProfiling;
import io.bellsoft.hotcode.util.SimpleThreadFactory;

import java.lang.management.ManagementFactory;
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
    
    private HotCodeAgent(String argumentString) {
        var settings = new Properties();
        parseArgs(argumentString, settings);
        config = HotCodeAgentConfiguration.from(settings);
        service = Executors.newSingleThreadScheduledExecutor(new SimpleThreadFactory("hotcode-optimization", true));
        profile = new TopKProfile<>();
        profiling = new JfrLiveProfiling(config.maxStackDepth, config.samplingInterval, config.profilingDuration);
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
        @Override
        public void run() {
            try {
                LOGGER.log(Level.INFO, "Starting profiling");
                profiling.fill(profile);
                var hotMethods = profile.getTop(config.top);
                LOGGER.log(Level.INFO, "Determined {0} hottest methods", hotMethods.size());
                if (config.print) {
                    new MethodProfilePrinter(System.out).print(profile, config.top);
                }
                var directives = CompilerDirectives.build(hotMethods);
                CompilerDirectivesControl.replace(directives);
                LOGGER.log(Level.INFO, "Replaced compiler directives");
                //profile.clear();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE,
                        "an error occurred during the profiling task execution, further profiling is cancelled", e);
                throw new RuntimeException(e);
            }
        }
    }

}
