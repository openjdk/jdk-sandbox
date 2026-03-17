#!/bin/bash

# Run with e.g. ./run-dacapo.sh fop -n 400

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

DACAPO=dacapo
if [ ! -d $DACAPO ]; then
  echo "Download Dacapo to $DACAPO"
  exit 1
fi
W="-jar $DACAPO/dacapo-23.11-MR2-chopin.jar $*"

OPTS="-XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions"

# Only C2, only COH
OPTS="$OPTS -XX:-TieredCompilation -XX:+UseCompactObjectHeaders -XX:+UseCompressedOops"

# Heap config
OPTS="$OPTS -Xmx10g -Xms10g -XX:+UseTransparentHugePages -XX:+AlwaysPreTouch"

# GC config
OPTS="$OPTS -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive"

# Mitigate code cache effects
OPTS="$OPTS -XX:ReservedCodeCacheSize=256M"

# Opts for testing individual barriers
OPTS_LRB="-XX:+ShenandoahLoadRefBarrier"
OPTS_SAT="-XX:+ShenandoahSATBBarrier"
OPTS_CAS="-XX:+ShenandoahCASBarrier"
OPTS_CLN="-XX:+ShenandoahCloneBarrier"

OPTS_ALL="$OPTS $OPTS_LRB $OPTS_SAT $OPTS_CAS $OPTS_CLN"

run_with() {
	P=$*
	for I in `seq 1 3`; do
		echo -n " run $I: "
		$P $W 2>&1 | awk '/completed warmup|PASSED/ { printf "%s ", $(NF-2)} END { print "" }'
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
  echo "Mainline: Only LRB barriers"
  run_with $J_ML $OPTS $OPTS_LRB

  echo
  echo "Mainline: Only SAT barriers"
  run_with $J_ML $OPTS $OPTS_SAT

  echo
  echo "Mainline: Only CAS barriers"
  run_with $J_ML $OPTS $OPTS_CAS

  echo
  echo "Mainline: Only Clone barriers"
  run_with $J_ML $OPTS $OPTS_CLN

  echo
  echo "Mainline: All barriers"
  run_with $J_ML $OPTS_ALL
fi

echo
echo "LBE: No barriers"
run_with $J_LBE $OPTS

echo
echo "LBE: Only LRB barriers"
run_with $J_LBE $OPTS $OPTS_LRB

echo
echo "LBE: Only SAT barriers"
run_with $J_LBE $OPTS $OPTS_SAT

echo
echo "LBE: Only CAS barriers"
run_with $J_LBE $OPTS $OPTS_CAS

echo
echo "LBE: Only Clone barriers"
run_with $J_LBE $OPTS $OPTS_CLN

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

