# Proof-of-concept implementations of [java.net.spi.InetNameServiceProvider](https://bugs.openjdk.java.net/browse/JDK-8263693) interface #

This branch contains two proof-of-concept name service providers to demonstrate and verify
that the provider interface can be used to develop name service implementations alternative
to the name service implementation shipped with the Java platform. 

The following Proof-of-Concept providers can be found in this repository:
- [Netty based PoC provider](NettyBasedPoC/src/main/java/jdk/test/nsp/proof/netty/ProviderImpl.java)
- [JNDI/DNS based PoC provider](JndiBasedPoC/src/main/java/jdk/test/nsp/proof/jndi/ProviderImpl.java)
