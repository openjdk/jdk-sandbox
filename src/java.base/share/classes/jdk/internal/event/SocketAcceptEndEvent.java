package jdk.internal.event;

public final class SocketAcceptEndEvent extends AbstractSocketEvent {

    private final static SocketAcceptEndEvent EVENT = new SocketAcceptEndEvent();

    /** Returns {@code true} if event is enabled, {@code false} otherwise. */
    public static boolean isTurnedOn() {
        return EVENT.isEnabled();
    }

    public static boolean isTurnedOFF() {
        return !isTurnedOn();
    }

    public int acceptedId;
    public String exceptionMessage;
}
