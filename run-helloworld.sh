#!/bin/bash

# Run with e.g. ./run-helloworld.sh

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

echo
echo "No barriers"
$J -Xcomp -XX:+CITime $OPTS Hello.java 2>&1 | grep "Tier4" | cut -d' ' -f 3,23-

echo
echo "All barriers"
$J -Xcomp -XX:+CITime $OPTS_ALL Hello.java 2>&1 | grep "Tier4" | cut -d' ' -f 3,23-

echo
echo "All barriers, hollow barrier stubs"
$J -Xcomp -XX:+CITime $OPTS_ALL -XX:+ShenandoahHollowBarrierStubs  Hello.java 2>&1 | grep "Tier4" | cut -d' ' -f 3,23-

echo
echo "All barriers, skip barrier stubs altogether"
$J -Xcomp -XX:+CITime $OPTS_ALL -XX:+ShenandoahSkipBarrierStubs  Hello.java 2>&1 | grep "Tier4" | cut -d' ' -f 3,23-


