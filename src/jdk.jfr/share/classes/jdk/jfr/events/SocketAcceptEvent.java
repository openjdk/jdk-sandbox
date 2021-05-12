package jdk.jfr.events;

import jdk.jfr.*;
import jdk.jfr.internal.MirrorEvent;

import java.net.InetAddress;
import java.net.SocketImpl;

@Category({"Java Development Kit", "Socket"})
@Label("SocketAccept")
@Name("jdk.SocketAccept")
@Description("Resulting state of successful accept")
@MirrorEvent(className = "jdk.internal.event.SocketAcceptEvent")
public class SocketAcceptEvent extends AbstractJDKEvent {

    @Label("Remote Host")
    public String host;

    @Label("Remote Address")
    public String addr;

    @Label("Remote port")
    public int port;

    @Label("Timeout Value")
    @Timespan(Timespan.MILLISECONDS)
    public int timeout;

    @Label("Socket Implementation")
    public Class<?> socketImpl;
}