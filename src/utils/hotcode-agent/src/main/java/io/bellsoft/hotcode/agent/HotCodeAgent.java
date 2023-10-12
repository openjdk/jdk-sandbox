package io.bellsoft.hotcode.agent;

import io.bellsoft.hotcode.dcmd.CompilerDirectives;
import io.bellsoft.hotcode.dcmd.CompilerDirectivesControl;
import io.bellsoft.hotcode.profiling.ProfilingTask;
import io.bellsoft.hotcode.profiling.jfr.JfrLiveProfiling;

import java.lang.instrument.Instrumentation;
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

    public static void premain(String argumentString, Instrumentation instrumentation) {
        new HotCodeAgent().run(argumentString);
    }

    public static void agentmain(String argumentString, Instrumentation instrumentation) {
        new HotCodeAgent().run(argumentString);
    }

    public void run(String argumentString) {
        configuration = HotCodeAgentConfiguration.from(argumentString);
        var task = new ProfileAndOptimize(new JfrLiveProfiling(configuration.topK, configuration.maxStackDepth,
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
