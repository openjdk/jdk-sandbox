#!/bin/bash

# Run with e.g. ./run-renaissance.sh fj-kmeans -r 10

set -euo pipefail

# Look around for release JDK image
J_LBE=
if [ -d build/linux-x86_64-server-release/images/jdk/ ]; then
  J_LBE=build/linux-x86_64-server-release/images/jdk/bin/java
elif [ -d build/linux-aarch64-server-release/images/jdk/ ]; then
  J_LBE=build/linux-aarch64-server-release/images/jdk/bin/java
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
OPTS="$OPTS -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive"

# Mitigate code cache effects
OPTS="$OPTS -XX:ReservedCodeCacheSize=256M"

OPTS_ALL="$OPTS -XX:+ShenandoahLoadRefBarrier -XX:+ShenandoahSATBBarrier -XX:+ShenandoahCASBarrier -XX:+ShenandoahCloneBarrier"

run_with() {
	P=$*
	for I in `seq 1 3`; do
		echo -n " run $I: "
		$P $W 2>&1 | awk '/iteration (.*) completed/ { $s = $(NF-2); gsub(/\(/, "", $s); printf("%s ", int($s)); } END { print "" }' 
	done
	echo -n " stats: "
	$P -XX:+CITime $W 2>&1 | grep Tier4
}

echo
echo ------
echo $*

if [ "x" != "x$J_ML" ]; then
  echo
  echo "Mainline: No barriers"
  run_with $J_ML $OPTS

  echo
  echo "Mainline: All barriers"
  run_with $J_ML $OPTS_ALL
fi

echo
echo "LBE: No barriers"
run_with $J_LBE $OPTS

echo
echo "LBE: All barriers"
run_with $J_LBE $OPTS_ALL

echo
echo "LBE: All barriers, hot-patchable GC state checks in fast-path"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahGCStateCheckHotpatch

echo
echo "LBE: All barriers, remove GC state checks in fast-path"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahGCStateCheckRemove

echo
echo "LBE: All barriers, remove both fast- and slow-path"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahSkipBarriers

