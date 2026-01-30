#!/bin/bash

JAVA_BIN="/wf/jdk-sandbox/build/linux-aarch64-server-$1/jdk/bin/"

# HACK
cp /wf/hsdis*.so ${JAVA_BIN}/../lib/server/

cmd=(
  "${JAVA_BIN}/java"
  "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED"
  "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
  "-XX:+UnlockDiagnosticVMOptions"
  "-Xmx12g"
  "-XX:+UseShenandoahGC"
  "-XX:ShenandoahGCMode=satb"
  "-XX:+TieredCompilation"
  "-XX:+DoEscapeAnalysis"
  "-XX:ShenandoahGCHeuristics=aggressive"
  "-XX:+ShenandoahAllocFailureALot"
  "-jar"
  "/wf/jars/renaissance-jmh-0.16.1.jar"
  "-f" "1"
  "-wi" "1"
  "-i" "1"
)

echo "Running..."
printf "%q " "${cmd[@]}"
echo    # newline

# Actually run it
"${cmd[@]}"


