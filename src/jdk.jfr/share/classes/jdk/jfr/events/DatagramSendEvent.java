package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.internal.MirrorEvent;
import jdk.jfr.internal.Type;

@Name(Type.EVENT_NAME_PREFIX + "DatagramSend")
@Label("Datagram send")
@Category({"Java Development Kit", "Datagram"})
@Description("Sending a Datagram")
@MirrorEvent(className = "jdk.internal.event.DatagramSendEvent")
public class DatagramSendEvent extends AbstractSocketEvent{
    @Label("Blocking Operations")
    public boolean blocking;

    @Label("Completed Send")
    public boolean completed;
}
