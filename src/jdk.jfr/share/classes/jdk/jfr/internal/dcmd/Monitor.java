package jdk.jfr.internal.dcmd;

import java.io.IOException;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingStream;

public class Monitor {

    private static EventStream stream;

    public static void start() throws IOException {
        stream = new RecordingStream();
        stream.startAsync();
        System.out.println("Monitor started");
    }
}
