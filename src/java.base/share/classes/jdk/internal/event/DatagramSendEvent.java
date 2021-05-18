package jdk.internal.event;

public class DatagramSendEvent extends AbstractSocketEvent {
    public boolean blocking;
    public boolean completed;
}
