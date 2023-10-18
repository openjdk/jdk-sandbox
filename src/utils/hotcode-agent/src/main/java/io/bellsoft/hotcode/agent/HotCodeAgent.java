package io.bellsoft.hotcode.agent;

import io.bellsoft.hotcode.dcmd.CompilerDirectives;
import io.bellsoft.hotcode.dcmd.CompilerDirectivesControl;
import io.bellsoft.hotcode.profiling.ProfilingTask;
import io.bellsoft.hotcode.profiling.jfr.JfrLiveProfiling;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HotCodeAgent {

    private final static Logger LOGGER = Logger.getLogger(HotCodeAgent.class.getName());

    private HotCodeAgentConfiguration configuration;
    private ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
    private final CompilerDirectivesControl directivesControl = new CompilerDirectivesControl();

    public static void premain(String argumentString) {
        new HotCodeAgent().run(argumentString);
    }

    public static void agentmain(String argumentString) {
        new HotCodeAgent().run(argumentString);
    }

    public void run(String argumentString) {
        var settings = new Properties();
        parseArgs(argumentString, settings);
        configuration = HotCodeAgentConfiguration.from(settings);
        var task = new ProfileAndOptimize(new JfrLiveProfiling(configuration.top, configuration.maxStackDepth,
                configuration.samplingInterval, configuration.profilingDuration));
        if (configuration.profilingPeriod.isZero()) {
            service.schedule(
                    task,
                    configuration.profilingDelay.toMillis(),
                    TimeUnit.MILLISECONDS);
        } else {
            service.scheduleAtFixedRate(
                    task,
                    configuration.profilingDelay.toMillis(),
                    configuration.profilingPeriod.toMillis(),
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
        private ProfilingTask profilingTask;

        public ProfileAndOptimize(ProfilingTask profilingTask) {
            this.profilingTask = profilingTask;
        }

        @Override
        public void run() {
            try {
                var hotMethods = profilingTask.call().getTop();
                var directives = CompilerDirectives.build(hotMethods);
                directivesControl.replace(directives);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE,
                        "an error occurred during the profiling task execution, further profiling is cancelled", e);
                throw new RuntimeException(e);
            }
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            var t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    }
}
