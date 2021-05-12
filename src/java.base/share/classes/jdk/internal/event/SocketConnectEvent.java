package jdk.internal.event;

public final class SocketConnectEvent extends Event {
    public String host;
    public String addr;
    public int port;
    public int timeout;
    public Class<?> socketImpl;
}