JAVA_BIN="/wf/jdk-sandbox-baseline/build/linux-aarch64-server-fastdebug/images/jdk/bin/"
JAVA_BIN="/wf/dchuyko/build/linux-aarch64-server-fastdebug/jdk/bin/"

shen=(
  "-XX:+UnlockDiagnosticVMOptions"
  "-XX:+UnlockExperimentalVMOptions"
  "-Xms1g"
  "-Xmx1g"
  "-XX:+UseShenandoahGC"
  "-XX:ShenandoahGCHeuristics=aggressive"
  "-XX:+ShenandoahAllocFailureALot"
  "-XX:ParallelGCThreads=32"
  "-XX:ConcGCThreads=32"
  "-XX:-UseDynamicNumberOfGCThreads"
  "-XX:+ShenandoahVerify"
  "-XX:+ShenandoahVerifyOptoBarriers"
  "-XX:ShenandoahRegionSize=1m"
  "-XX:+ShenandoahVerifyOptoBarriers"
)
#  "-XX:+VerifyBeforeGC"
#  "-XX:+VerifyAfterGC"

${JAVA_BIN}/java ${shen[@]} -jar /wf/jars/dacapo-evaluation-git+309e1fa.jar --scratch-directory /tmp/ --no-validation --variance 5 --no-pre-iteration-gc --iterations 10 --size small avrora batik eclipse h2 jython luindex lusearch pmd sunflow xalan tomcat

