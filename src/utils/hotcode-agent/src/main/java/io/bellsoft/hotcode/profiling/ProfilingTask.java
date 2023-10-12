package io.bellsoft.hotcode.profiling;

import java.util.concurrent.Callable;

public interface ProfilingTask extends Callable<Profile<Method>> {

}
