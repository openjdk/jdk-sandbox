http://x0213.org/codetable/sjis-0213-2004-std.txt

https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html

"Usage: java -jar charsetmapping.jar src dst spiType charsets os [template]"

java.base:

java -cp build/macosx-x64/buildtools/jdk_tools_classes build.tools.charsetmapping.Main

open/make/data/charsetmapping
build/macosx-x64/support/gensrc/java.base/sun/nio/cs
stdcs
charsets
stdcs-macosx
open/src/java.base/share/classes/sun/nio/cs/StandardCharsets.java.template
open/src/jdk.charsets/share/classes/sun/nio/cs/ext
open/make/jdk/src/classes/build/tools/charsetmapping

extcs:

java -cp build/macosx-x64/buildtools/jdk_tools_classes build.tools.charsetmapping.Main

open/make/data/charsetmapping
build/macosx-x64/support/gensrc/jdk.charsets/sun/nio/cs/ext
extcs
charsets
stdcs-macosx
open/src/jdk.charsets/share/classes/sun/nio/cs/ext/ExtendedCharsets.java.template
open/src/jdk.charsets/share/classes/sun/nio/cs/ext
open/make/jdk/src/classes/build/tools/charsetmapping

hkscs:

java -cp build/macosx-x64/buildtools/jdk_tools_classes build.tools.charsetmapping.Main

open/make/data/charsetmapping
build/macosx-x64/support/gensrc/jdk.charsets/sun/nio/cs/ext
hkscs
'open/make/jdk/src/classes/build/tools/charsetmapping/HKSCS.java'

euctw:

java -cp build/macosx-x64/buildtools/jdk_tools_classes build.tools.charsetmapping.Main

open/make/data/charsetmapping
build/macosx-x64/support/gensrc/jdk.charsets/sun/nio/cs/ext
euctw
'open/make/jdk/src/classes/build/tools/charsetmapping/EUC_TW.java'

sjis0213:
java -cp build/macosx-x64/buildtools/jdk_tools_classes build.tools.charsetmapping.Main

'open/make/data/charsetmapping/sjis0213.map'
'build/macosx-x64/support/gensrc/jdk.charsets/sun/nio/cs/ext/sjis0213.dat'
sjis0213

