#!/bin/bash

# Run with e.g. ./run-renaissance.sh fj-kmeans -r 10

set -euo pipefail

# Look around for release JDK image
J_HP=
if [ -d build/linux-x86_64-server-release/images/jdk/ ]; then
  J_HP=build/linux-x86_64-server-release/images/jdk/bin/java
elif [ -d build/linux-aarch64-server-release/images/jdk/ ]; then
  J_HP=build/linux-aarch64-server-release/images/jdk/bin/java
else
  echo "Cannot find JDK"
  exit 1
fi

J_ML=
if [ -d jdk-mainline/ ]; then
  J_ML=jdk-mainline/bin/java
fi

RNS=renaissance-gpl-0.16.1.jar
if [ ! -r $RNS ]; then
  echo "Download Renaissance to $RNS"
  exit 1
fi
W="-jar $RNS $*"


OPTS="-XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions"

# Only C2, only COH
OPTS="$OPTS -XX:-TieredCompilation -XX:+UseCompactObjectHeaders"

# Heap config
OPTS="$OPTS -Xmx10g -Xms10g -XX:+UseTransparentHugePages -XX:+AlwaysPreTouch"

# GC config
OPTS="$OPTS -XX:+UseShenandoahGC"

# Mitigate code cache effects
OPTS="$OPTS -XX:ReservedCodeCacheSize=256M"

OPTS_PASSIVE_NONE="$OPTS -XX:ShenandoahGCMode=passive"
OPTS_PASSIVE_ALL="$OPTS_PASSIVE_NONE -XX:+ShenandoahLoadRefBarrier -XX:+ShenandoahSATBBarrier -XX:+ShenandoahCloneBarrier"

run_with() {
  P=$*
  for I in `seq 1 3`; do
    echo -n " run $I: "
    $P $W 2>&1 | awk '/iteration (.*) completed/ { $s = $(NF-2); gsub(/\(/, "", $s); printf("%s ", int($s)); } END { print "" }' 
  done
}

echo
echo ------
echo $*

echo
echo "Hotpatching: Concurrent"
run_with $J_HP $OPTS

#echo
#echo "Hotpatching: Passive, No barriers"
#run_with $J_HP $OPTS_PASSIVE_NONE

#echo
#echo "Hotpatching: Passive, All barriers"
#run_with $J_HP $OPTS_PASSIVE_ALL

if [ "x" != "x$J_ML" ]; then
  echo
  echo "Mainline: Concurrent"
  run_with $J_ML $OPTS

#  echo
#  echo "Mainline: Passive, No barriers"
#  run_with $J_ML $OPTS_PASSIVE_NONE

#  echo
#  echo "Mainline: Passive, All barriers"
#  run_with $J_ML $OPTS_PASSIVE_ALL
fi


