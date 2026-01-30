pushd /wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/scratch/1

DOCS_JDK_IMAGE_DIR=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/docs \
HOME=/home/dcsl \
LANG=en_US.UTF-8 \
LC_ALL=C.UTF-8 \
PATH=/bin:/usr/bin:/usr/sbin \
TEST_IMAGE_DIR=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/test \
XDG_RUNTIME_DIR=/run/user/29400407 \
XDG_SESSION_ID=61547 \
_JVM_DWARF_PATH=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/symbols \
CLASSPATH=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/classes/1/gc/shenandoah/compiler/TestReferenceCAS_default.d:/wf/jdk-sandbox/test/hotspot/jtreg/gc/shenandoah/compiler:/wf/tools/jtreg/lib/javatest.jar:/wf/tools/jtreg/lib/jtreg.jar \
    /wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/jdk/bin/java \
        -Dtest.vm.opts='-XX:MaxRAMPercentage=0.78125 -Dtest.boot.jdk=/opt/jdk-25.0.1+8 -Djava.io.tmpdir=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/tmp' \
        -Dtest.tool.vm.opts='-J-XX:MaxRAMPercentage=0.78125 -J-Dtest.boot.jdk=/opt/jdk-25.0.1+8 -J-Djava.io.tmpdir=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/tmp' \
        -Dtest.compiler.opts= \
        -Dtest.java.opts= \
        -Dtest.jdk=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/jdk \
        -Dcompile.jdk=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/jdk \
        -Dtest.timeout.factor=4.0 \
        -Dtest.nativepath=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/test/hotspot/jtreg/native \
        -Dtest.root=/wf/jdk-sandbox/test/hotspot/jtreg \
        -Dtest.name=gc/shenandoah/compiler/TestReferenceCAS.java#default \
        -Dtest.verbose=Verbose[p=SUMMARY,f=FULL,e=FULL,t=false,m=false] \
        -Dtest.file=/wf/jdk-sandbox/test/hotspot/jtreg/gc/shenandoah/compiler/TestReferenceCAS.java \
        -Dtest.main.class=TestReferenceCAS \
        -Dtest.src=/wf/jdk-sandbox/test/hotspot/jtreg/gc/shenandoah/compiler \
        -Dtest.src.path=/wf/jdk-sandbox/test/hotspot/jtreg/gc/shenandoah/compiler \
        -Dtest.classes=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/classes/1/gc/shenandoah/compiler/TestReferenceCAS_default.d \
        -Dtest.class.path=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/classes/1/gc/shenandoah/compiler/TestReferenceCAS_default.d \
        -Dtest.class.path.prefix=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/classes/1/gc/shenandoah/compiler/TestReferenceCAS_default.d:/wf/jdk-sandbox/test/hotspot/jtreg/gc/shenandoah/compiler \
        -Dtest.modules=java.base/jdk.internal.misc:+open \
        --add-modules java.base \
        --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
        --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
        -XX:MaxRAMPercentage=0.78125 \
        -Dtest.boot.jdk=/opt/jdk-25.0.1+8 \
        -Djava.io.tmpdir=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/tmp \
        -Djava.library.path=/wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/images/test/hotspot/jtreg/native \
        -Diters=20000 \
        -XX:+UnlockDiagnosticVMOptions \
        -XX:+UnlockExperimentalVMOptions \
        -XX:ShenandoahGCHeuristics=aggressive \
        -XX:+UseShenandoahGC \
        -XX:-TieredCompilation \
        com.sun.javatest.regtest.agent.MainWrapper /wf/jdk-sandbox/build/linux-aarch64-server-slowdebug/test-support/jtreg_test_hotspot_jtreg_gc_shenandoah_compiler_TestReferenceCAS_java/gc/shenandoah/compiler/TestReferenceCAS_default.d/main.2.jta

popd
