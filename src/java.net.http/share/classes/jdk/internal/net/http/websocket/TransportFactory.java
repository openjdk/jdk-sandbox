package jdk.internal.net.http.websocket;

import java.util.function.Supplier;

@FunctionalInterface
public interface TransportFactory {

    <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                     MessageStreamConsumer consumer);
}
