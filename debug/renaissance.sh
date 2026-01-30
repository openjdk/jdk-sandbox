JAVA_BIN="/wf/jdk-sandbox-baseline/build/linux-aarch64-server-release/jdk/bin/"
JAVA_BIN="/wf/dchuyko/build/linux-aarch64-server-release/jdk/bin/"

opens=(
  "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  "--add-opens=java.base/java.util=ALL-UNNAMED" \
  "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED" \
  "--add-opens=java.base/java.io=ALL-UNNAMED" \
  "--add-opens=java.base/java.net=ALL-UNNAMED" \
  "--add-opens=java.base/java.nio=ALL-UNNAMED" \
  "--add-opens=java.base/java.time=ALL-UNNAMED" \
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" \
  "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" \
  "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED" \
  "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED" \
  "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED" \
  "--add-opens=java.base/jdk.internal.vm.annotation=ALL-UNNAMED" \
  "--add-opens=java.base/jdk.internal.reflect=ALL-UNNAMED" \
  "--add-opens=java.base/jdk.internal.module=ALL-UNNAMED" \
)

${JAVA_BIN}/java ${opens[@]} -XX:+UnlockDiagnosticVMOptions -XX:+UseShenandoahGC -XX:+UseCompressedOops -Xmx2g -XX:ShenandoahGCMode=satb -XX:-DoEscapeAnalysis -XX:ShenandoahGCHeuristics=aggressive -jar /wf/jars/renaissance-jmh-0.16.1.jar -f 1 -wi 10 -i 10 $@
${JAVA_BIN}/java ${opens[@]} -XX:+UnlockDiagnosticVMOptions -XX:+UseShenandoahGC -XX:+UseCompressedOops -Xmx4g                           -XX:-DoEscapeAnalysis -XX:ShenandoahGCHeuristics=aggressive -jar /wf/jars/renaissance-jmh-0.16.1.jar -f 1 -wi 10 -i 10 $@
${JAVA_BIN}/java ${opens[@]} -XX:+UnlockDiagnosticVMOptions -XX:+UseShenandoahGC -XX:+UseCompressedOops -Xmx8g                           -XX:-DoEscapeAnalysis -XX:ShenandoahGCHeuristics=aggressive -jar /wf/jars/renaissance-jmh-0.16.1.jar -f 1 -wi 10 -i 10 $@

