#!/bin/bash

TARGET=$1
#JAVA_BIN="/wf/jdk-sandbox-baseline/build/linux-aarch64-server-fastdebug/images/jdk/bin/"
JAVA_BIN="/wf/dchuyko/build/linux-aarch64-server-slowdebug/jdk/bin/"
JAVA_BIN="/wf/dchuyko/build/linux-aarch64-server-release/jdk/bin/"

# Recompile if .java is newer
if [[ ${TARGET}.java -nt ${TARGET}.class ]] ; then
    echo "Compiling..."
    ${JAVA_BIN}/javac --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED ${TARGET}.java
fi

# HACK
cp /wf/hsdis*.so ${JAVA_BIN}/../lib/server/

print=(
#  "-XX:+PrintOpto"
#  "-XX:CompileCommand=dontinline,*${TARGET}*::test"
#  "-XX:CompileCommand=compileonly,*${TARGET}*::test"
  "-XX:CompileCommand=option,*${TARGET}*::test,PrintAssembly"
#  "-XX:CompileCommand=option,*${TARGET}*::test,PrintIdeal"
  "-XX:CompileCommand=option,*${TARGET}*::test,PrintOptoAssembly"
)

shen=(
  -XX:+UseShenandoahGC
  -XX:ShenandoahGCHeuristics=aggressive
)

compz=(
  -Xcomp
  -XX:-DoEscapeAnalysis
  -XX:-BackgroundCompilation
  -XX:-PrintCompilation
  -XX:-LogCompilation
  -XX:+TieredCompilation
  -XX:+UseCompressedOops
  -XX:TieredStopAtLevel=4
)

cmd=(
  "${JAVA_BIN}/java"
  -ea
  -esa
  "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED"
  "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
  "-XX:+UnlockDiagnosticVMOptions"
  ${compz[@]}
  ${noprint[@]}
  ${shen[@]}
  "${TARGET}"
)

echo "Running..."
printf "%q " "${cmd[@]}"
echo    # newline

# Actually run it
"${cmd[@]}"






