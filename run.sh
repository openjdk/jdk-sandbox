JTREG_KEYWORDS="!external-dep & !headful & !printer" \
  make test-tier1 CONF=release \
  TEST_VM_OPTS="-XX:+UnlockDiagnosticVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive"
