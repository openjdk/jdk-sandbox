#!/bin/bash

# Run with e.g. ./run-dacapo.sh fop -n 400

# Look around for release JDK image
J=build/linux-x86_64-server-release/images/jdk/bin/java
if [ -d build/linux-x86_64-server-release/images/jdk/ ]; then
  J=build/linux-x86_64-server-release/images/jdk/bin/java
elif [ -d build/linux-aarch64-server-release/images/jdk/ ]; then
  J=build/linux-aarch64-server-release/images/jdk/bin/java
else
  echo "Cannot find JDK"
  exit 1
fi

DACAPO=dacapo
if [ ! -d $DACAPO ]; then
  echo "Download Dacapo to $DACAPO"
  exit 1
fi
W="-jar $DACAPO/dacapo-23.11-MR2-chopin.jar $*"


OPTS="-XX:+UseShenandoahGC -Xmx8g -Xms8g -XX:+AlwaysPreTouch -XX:+UseTransparentHugePages -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:ShenandoahGCMode=passive -XX:+UnlockExperimentalVMOptions"

OPTS_ALL="$OPTS -XX:+ShenandoahLoadRefBarrier -XX:+ShenandoahSATBBarrier -XX:+ShenandoahCASBarrier -XX:+ShenandoahCloneBarrier"

run_with() {
	P=$*
	for I in `seq 1 3`; do
		echo -n " run $I: "
		$J $P $W 2>&1 | awk '/completed/ { printf "%s ", $(NF-2)} END { print "" }'
	done
	echo -n " footprint: "
	$J $P -XX:+CITime $W 2>&1 | grep "Tier4" | cut -d' ' -f 3,23-
}

echo
echo ------
echo $*

echo
echo "No barriers"
run_with $OPTS

echo
echo "All barriers"
run_with $OPTS_ALL

echo
echo "All barriers, nop GC state checks"
run_with $OPTS_ALL -XX:+ShenandoahNopGCState

echo
echo "All barriers, hollow barrier stubs"
run_with $OPTS_ALL -XX:+ShenandoahHollowBarrierStubs

echo
echo "All barriers, hollow barrier stubs and nop GC state checks"
run_with $OPTS_ALL -XX:+ShenandoahNopGCState -XX:+ShenandoahHollowBarrierStubs

echo
echo "All barriers, skip barrier stubs altogether"
run_with $OPTS_ALL -XX:+ShenandoahSkipBarrierStubs

