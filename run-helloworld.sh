#!/bin/bash

# Run with e.g. ./run-helloworld.sh

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

OPTS="-XX:+UseShenandoahGC -Xmx8g -Xms8g -XX:+AlwaysPreTouch -XX:+UseTransparentHugePages -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:ShenandoahGCMode=passive -XX:+UnlockExperimentalVMOptions"

OPTS_ALL="$OPTS -XX:+ShenandoahLoadRefBarrier -XX:+ShenandoahSATBBarrier -XX:+ShenandoahCASBarrier -XX:+ShenandoahCloneBarrier"

echo
echo ------
echo Hello World footprint:

cat > Hello.java <<EOF
public class Hello {
  public static void main(String... args) {
     System.out.println("Hello World");
  }
}
EOF

run_with() {
        P=$*
        for I in `seq 1 3`; do
                echo -n " run $I: "
		$P -Xcomp -XX:+CITime Hello.java 2>&1 | grep "Tier4"
        done
}

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
echo "LBE: All barriers, nop GC state checks"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahNopGCState

echo
echo "LBE: All barriers, hollow barrier stubs"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahHollowBarrierStubs

echo
echo "LBE: All barriers, hollow barrier stubs and nop GC state checks"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahHollowBarrierStubs -XX:+ShenandoahNopGCState

echo
echo "LBE: All barriers, skip barrier stubs altogether"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahSkipBarrierStubs

