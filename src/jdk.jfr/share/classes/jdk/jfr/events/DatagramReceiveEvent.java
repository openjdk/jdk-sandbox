package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.internal.MirrorEvent;
import jdk.jfr.internal.Type;

@Name(Type.EVENT_NAME_PREFIX + "DatagramReceive")
@Label("Datagram receive")
@Category({"Java Development Kit", "Datagram"})
@Description("Receiving a Datagram")
@MirrorEvent(className = "jdk.internal.event.DatagramReceiveEvent")
public class DatagramReceiveEvent extends AbstractSocketEvent {
    @Label("Blocking Operations")
    public boolean blocking;

    @Label("Socket Connected")
    public boolean connected;
}
