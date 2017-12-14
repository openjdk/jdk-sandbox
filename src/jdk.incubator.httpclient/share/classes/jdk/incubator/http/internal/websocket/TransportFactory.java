package jdk.incubator.http.internal.websocket;

import java.util.function.Supplier;

@FunctionalInterface
public interface TransportFactory {

    <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                     MessageStreamConsumer consumer);
}
