package jdk.internal.event;

public class SocketAcceptEvent extends Event {
    public String host;
    public String addr;
    public int port;
    public int timeout;
    public Class<?> socketImpl;
}
