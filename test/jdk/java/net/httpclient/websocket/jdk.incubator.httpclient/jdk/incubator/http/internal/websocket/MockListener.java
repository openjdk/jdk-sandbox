package jdk.incubator.http.internal.websocket;

import jdk.incubator.http.WebSocket;
import jdk.incubator.http.WebSocket.MessagePart;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;

public class MockListener implements WebSocket.Listener {

    private final long bufferSize;
    private long count;

    /*
     * Typical buffer sizes: 1, n, Long.MAX_VALUE
     */
    public MockListener(long bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException();
        }
        this.bufferSize = bufferSize;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.printf("onOpen(%s)%n", webSocket);
        replenishDemandIfNeeded(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket,
                                     CharSequence message,
                                     MessagePart part) {
        System.out.printf("onText(%s, %s, %s)%n", webSocket, message, part);
        replenishDemandIfNeeded(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket,
                                       ByteBuffer message,
                                       MessagePart part) {
        System.out.printf("onBinary(%s, %s, %s)%n", webSocket, message, part);
        replenishDemandIfNeeded(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        System.out.printf("onPing(%s, %s)%n", webSocket, message);
        replenishDemandIfNeeded(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        System.out.printf("onPong(%s, %s)%n", webSocket, message);
        replenishDemandIfNeeded(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket,
                                      int statusCode,
                                      String reason) {
        System.out.printf("onClose(%s, %s, %s)%n", webSocket, statusCode, reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.out.printf("onError(%s, %s)%n", webSocket, error);
    }

    private void replenishDemandIfNeeded(WebSocket webSocket) {
        if (--count <= 0) {
            count = bufferSize - bufferSize / 2;
            System.out.printf("request(%s)%n", count);
            webSocket.request(count);
        }
    }
}
