About JNLPConverter
===================

JNLPConverter is a standalone tool which uses jpackage to create bundles from
Java Web Start(TM) Applications and helps to migrate from JNLP to jpackage.
JNLPConverter will locate and use the jpackage tool from the same JDK as used
to run JNLPConverter. JNLPConverter supports HTTP/HTTPS and FILE protocol.

Running JNLPConverter
=====================

To run the JNLPConverter:

  java -jar JNLPConverter.jar <mode> <options>

To get help on JNLPConverter options:

  java -jar JNLPConverter.jar --help

These instructions assume that this installation's version of the java command
is in your path. If it isn't, then you should either specify the complete path
to the java command or update your PATH environment variable as described
in the installation instructions for the Java(TM) SE Development Kit.
