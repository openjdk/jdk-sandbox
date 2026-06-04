#!/bin/bash

# Run with e.g. ./run-helloworld.sh

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

OPTS="-XX:+UseShenandoahGC -Xmx8g -Xms8g -XX:+AlwaysPreTouch -XX:+UseTransparentHugePages -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:ShenandoahGCMode=passive -XX:+UnlockExperimentalVMOptions"

OPTS_ALL="$OPTS -XX:+ShenandoahLoadRefBarrier -XX:+ShenandoahSATBBarrier -XX:+ShenandoahCloneBarrier"

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

echo
echo ------
echo $*

echo
echo "HP: Passive, No barriers"
run_with $J_HP $OPTS

echo
echo "HP: Passive, All barriers"
run_with $J_HP $OPTS_ALL

if [ "x" != "x$J_ML" ]; then
  echo
  echo "Mainline: Passive, No barriers"
  run_with $J_ML $OPTS

  echo
  echo "Mainline: Passive, All barriers"
  run_with $J_ML $OPTS_ALL
fi

