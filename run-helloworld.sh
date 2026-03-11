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

exit

echo
echo "LBE: All barriers, hot-patchable GC state checks in fast-path"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahGCStateCheckHotpatch

echo
echo "LBE: All barriers, remove GC state checks in fast-path"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahGCStateCheckRemove

echo
echo "LBE: All barriers, remove both fast- and slow-path"
run_with $J_LBE $OPTS_ALL -XX:+ShenandoahSkipBarriers


