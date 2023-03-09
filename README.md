# Classfile Processing API for JDK

##Important Note: This branch has been split into [JDK-8294957](https://bugs.openjdk.org/browse/JDK-8294957) subtasks and does not serve for development anymore.

Provide an API for parsing, generating, and transforming Java class files. This will initially serve as an internal replacement for ASM in the JDK, to be later opened as a public API.

See [JEP](https://bugs.openjdk.java.net/browse/JDK-8280389)
or [online API documentation](https://htmlpreview.github.io/?https://raw.githubusercontent.com/openjdk/jdk-sandbox/classfile-api-javadoc-branch/doc/classfile-api/javadoc/java.base/jdk/internal/classfile/package-summary.html)
for more information about Classfile Processing API.

See <https://openjdk.org/> for more information about the OpenJDK
Community and the JDK and see <https://bugs.openjdk.org> for JDK issue
tracking.

### Sources

Classfile Processing API source are a part of java.base JDK module sources:

- [src/java.base/share/classes/jdk/internal/classfile/](src/java.base/share/classes/jdk/internal/classfile/)

### Building

For build instructions please see the
[online documentation](https://openjdk.org/groups/build/doc/building.html),
or either of these files:

- [doc/building.html](doc/building.html) (html version)
- [doc/building.md](doc/building.md) (markdown version)

### Testing

Classfile Processing API tests are a part of JDK tests:

- [test/jdk/jdk/classfile/](test/jdk/jdk/classfile/)

Test can be selectivelly executed as:

    make test TEST=jdk/classfile

See [online JCov report](https://htmlpreview.github.io/?https://raw.githubusercontent.com/openjdk/jdk-sandbox/classfile-api-javadoc-branch/jcov-report/jdk/classfile/package-summary.html) for more information about Classfile API tests coverage
and [doc/testing.md](doc/testing.md) for more information about JDK testing.

### Benchmarking

Classfile Processing API benchmarks are a part of JDK Microbenchmark Suite:

- [test/micro/org/openjdk/bench/jdk/classfile/](test/micro/org/openjdk/bench/jdk/classfile/)

Benchmarks can be selectively executed as:

    make test TEST=micro:org.openjdk.bench.jdk.classfile.+

See [JEP 230: Microbenchmark Suite](https://bugs.openjdk.java.net/browse/JDK-8050952) for more information about JDK benchmarks.

### Use Cases

Following use cases of Classfile Processing API are included in this JDK branch:

- module `java.base`:
    - java.lang.[Module](src/java.base/share/classes/java/lang/Module.java)
    - java.lang.invoke.[ClassSpecializer](src/java.base/share/classes/java/lang/invoke/ClassSpecializer.java),
    [GenerateJLIClassesHelper](src/java.base/share/classes/java/lang/invoke/GenerateJLIClassesHelper.java),
    [InnerClassLambdaMetafactory](src/java.base/share/classes/java/lang/invoke/InnerClassLambdaMetafactory.java),
    [InvokerBytecodeGenerator](src/java.base/share/classes/java/lang/invoke/InvokerBytecodeGenerator.java),
    [MethodHandleImpl](src/java.base/share/classes/java/lang/invoke/MethodHandleImpl.java),
    [MethodHandles](src/java.base/share/classes/java/lang/invoke/MethodHandles.java),
    [TypeConvertingMethodAdapter](src/java.base/share/classes/java/lang/invoke/TypeConvertingMethodAdapter.java)
    - java.lang.reflect.[ProxyGenerator](src/java.base/share/classes/java/lang/reflect/ProxyGenerator.java)
    - jdk.internal.module.[ModuleInfoExtender](src/java.base/share/classes/jdk/internal/module/ModuleInfoExtender.java),
    [ModuleInfoWriter](src/java.base/share/classes/jdk/internal/module/ModuleInfoWriter.java)
- module `jdk.jartool`:
    - sun.tools.jar.[FingerPrint](src/jdk.jartool/share/classes/sun/tools/jar/FingerPrint.java)
- module `jdk.jdeps`:
    - com.sun.tools.javap.[AttributeWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/AttributeWriter.java),
    [BasicWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/BasicWriter.java),
    [ClassWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/ClassWriter.java),
    [CodeWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/CodeWriter.java),
    [ConstantWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/ConstantWriter.java),
    [InstructionDetailWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/InstructionDetailWriter.java),
    [JavapTask](src/jdk.jdeps/share/classes/com/sun/tools/javap/JavapTask.java),
    [LocalVariableTableWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/LocalVariableTableWriter.java),
    [LocalVariableTypeTableWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/LocalVariableTypeTableWriter.java),
    [Options](src/jdk.jdeps/share/classes/com/sun/tools/javap/Options.java),
    [SourceWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/SourceWriter.java),
    [StackMapWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/StackMapWriter.java),
    [TryBlockWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/TryBlockWriter.java),
    [TypeAnnotationWriter](src/jdk.jdeps/share/classes/com/sun/tools/javap/TypeAnnotationWriter.java)
- module `jdk.jlink`:
    - jdk.tools.jimage.[JImageTask](src/jdk.jlink/share/classes/jdk/tools/jimage/JImageTask.java)
    - jdk.tools.jlink.internal.plugins.[AbstractPlugin](src/jdk.jlink/share/classes/jdk/tools/jlink/internal/plugins/AbstractPlugin.java),
    [IncludeLocalesPlugin](src/jdk.jlink/share/classes/jdk/tools/jlink/internal/plugins/IncludeLocalesPlugin.java),
    [StripJavaDebugAttributesPlugin](src/jdk.jlink/share/classes/jdk/tools/jlink/internal/plugins/StripJavaDebugAttributesPlugin.java),
    [SystemModulesPlugin](src/jdk.jlink/share/classes/jdk/tools/jlink/internal/plugins/SystemModulesPlugin.java),
    [VersionPropsPlugin](src/jdk.jlink/share/classes/jdk/tools/jlink/internal/plugins/VersionPropsPlugin.java)
- module `jdk.jfr`:
    - jdk.jfr.internal.[EventClassBuilder](src/jdk.jfr/share/classes/jdk/jfr/internal/EventClassBuilder.java),
    [EventInstrumentation](src/jdk.jfr/share/classes/jdk/jfr/internal/EventInstrumentation.java),
    [EventWriterMethod](src/jdk.jfr/share/classes/jdk/jfr/internal/EventWriterMethod.java)
    - jdk.jfr.internal.instrument.[ConstructorTracerWriter](src/jdk.jfr/share/classes/jdk/jfr/internal/instrument/ConstructorTracerWriter.java),
    [JIClassInstrumentation](src/jdk.jfr/share/classes/jdk/jfr/internal/instrument/JIClassInstrumentation.java)
- module `jdk.jshell`:
    - jdk.jshell.execution.[LocalExecutionControl](src/jdk.jshell/share/classes/jdk/jshell/execution/LocalExecutionControl.java)
