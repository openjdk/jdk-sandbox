package jdk.internal.event;

import jdk.jfr.Label;

public class DatagramReceiveEvent extends AbstractSocketEvent {
    @Label("Blocking Operations")
    public boolean blocking;

    @Label("Socket Connected")
    public boolean connected;
}
