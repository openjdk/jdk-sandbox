# Welcome to the JDK!

For build instructions please see the
[online documentation](https://openjdk.org/groups/build/doc/building.html),
or either of these files:

- [doc/building.html](doc/building.html) (html version)
- [doc/building.md](doc/building.md) (markdown version)

See <https://openjdk.org/> for more information about the OpenJDK
Community and the JDK and see <https://bugs.openjdk.org> for JDK issue
tracking.


## Error
Run the following command to reproduce the error:
```bash
./run_renaissance_till_fail.sh
```

It should also download a `renaissance.jar` in the current directory.

This script should produce something like the following if it fails:
```bash
#  Internal Error (/home/i560383/code/modernizing_jfr/jdk/src/hotspot/share/memory/resourceArea.hpp:113), pid=1723109, tid=1723139
Current thread (0x00007687c0034d40):  JavaThread "C1 CompilerThread1" daemon [_thread_in_vm, id=1723139, stack(0x00007687f2d00000,0x00007687f2e00000) (1024K)]

V  [libjvm.so+0x9062ec]  ResourceArea::rollback_to(ResourceArea::SavedState const&) [clone .part.0]+0x1c  (resourceArea.hpp:113)
V  [libjvm.so+0x90f030]  ciMethodData::prepare_metadata()+0x570  (resourceArea.hpp:113)
V  [libjvm.so+0x90f3d4]  ciMethodData::load_remaining_extra_data()+0x34  (ciMethodData.cpp:142)
V  [libjvm.so+0x910294]  ciMethodData::load_data()+0x5b4  (ciMethodData.cpp:278)
V  [libjvm.so+0x9003aa]  ciMethod::ensure_method_data()+0x11a  (ciMethod.cpp:1009)
```